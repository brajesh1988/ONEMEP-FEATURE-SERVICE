package com.netlink.onemep_feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netlink.onemep_feature.user.client.UserDirectoryClient;
import com.netlink.onemep_feature.user.dto.UserSummary;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Discussion thread (ONEMEP-41) and time tracking (ONEMEP-42) end to end.
 *
 * <p>Users 1 "Ava Lead" and 2 "Ben Member" are both Project members, which is what makes mention
 * resolution and own-entry-only deletion observable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Tag("integration")
@Import(DiscussionAndTimeIT.StubUserDirectoryConfig.class)
class DiscussionAndTimeIT {

  private static final long AVA = 1L;
  private static final long BEN = 2L;

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16").withInitScript("testcontainers-init.sql");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("jwt.public-key", () -> "src/test/resources/keys/jwt-public.pem");
    registry.add("eureka.client.enabled", () -> "false");
    registry.add("spring.cloud.config.enabled", () -> "false");
    registry.add("spring.cloud.discovery.enabled", () -> "false");
    registry.add("feature.notifications.enabled", () -> "false");
    registry.add("grpc.client.identity-service.address", () -> "static://localhost:1");
  }

  @TestConfiguration
  static class StubUserDirectoryConfig {
    @Bean
    @Primary
    UserDirectoryClient stubUserDirectoryClient() {
      Map<Long, UserSummary> directory =
          Map.of(
              AVA,
              new UserSummary(AVA, "Ava Lead", "ava.lead@onemep.local"),
              BEN,
              new UserSummary(BEN, "Ben Member", "ben.member@onemep.local"),
              3L,
              new UserSummary(3L, "Cara Lead", "cara.lead@onemep.local"));
      return new UserDirectoryClient() {
        @Override
        public Map<Long, UserSummary> resolve(Collection<Long> ids) {
          Map<Long, UserSummary> result = new HashMap<>();
          if (ids != null) {
            ids.stream()
                .filter(java.util.Objects::nonNull)
                .filter(directory::containsKey)
                .forEach(id -> result.put(id, directory.get(id)));
          }
          return result;
        }

        @Override
        public Set<Long> findMissing(Collection<Long> ids) {
          return Set.of();
        }
      };
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DataSource dataSource;

  private static final java.util.concurrent.atomic.AtomicInteger ZONE_SEQ =
      new java.util.concurrent.atomic.AtomicInteger();

  private JdbcTemplate jdbc;
  private long projectId;
  private long designId;

  @BeforeEach
  void setUp() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    projectId = ensureProject();
    ensureMembers();
    designId = createDesign("Chilled water schematic");
  }

  // ── discussion ────────────────────────────────────────────────────────────

  @Test
  void postingAMessage_recordsAuthorAndTimestamp() throws Exception {
    perform(
            post("/designs/" + designId + "/messages").content("{\"body\":\"  Please review.  \"}"),
            AVA)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.message").value("Message posted."))
        .andExpect(jsonPath("$.data.body").value("Please review."))
        .andExpect(jsonPath("$.data.author").value("Ava Lead"))
        .andExpect(jsonPath("$.data.postedAt").exists())
        .andExpect(jsonPath("$.data.mentions.length()").value(0));
  }

  @Test
  void anEmptyOrWhitespaceOnlyMessage_isRejected() throws Exception {
    perform(post("/designs/" + designId + "/messages").content("{\"body\":\"   \"}"), AVA)
        .andExpect(status().isBadRequest());
  }

  @Test
  void mentioningATeammate_notifiesThemOnce() throws Exception {
    perform(
            post("/designs/" + designId + "/messages")
                .content("{\"body\":\"@Ben please align the shaft penetrations. @Ben urgent.\"}"),
            AVA)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.message").value("Message posted and Ben Member notified."))
        .andExpect(jsonPath("$.data.mentions.length()").value(1))
        .andExpect(jsonPath("$.data.mentions[0].displayName").value("Ben Member"));

    // Mentioned twice in one message, notified once.
    perform(get("/notifications/unread-count"), BEN).andExpect(jsonPath("$.data.unread").value(1));
  }

  @Test
  void mentioningYourself_neverNotifiesYou() throws Exception {
    perform(
            post("/designs/" + designId + "/messages")
                .content("{\"body\":\"@Ava noting this myself\"}"),
            AVA)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.message").value("Message posted."));

    perform(get("/notifications/unread-count"), AVA).andExpect(jsonPath("$.data.unread").value(0));
  }

  /** Only Project members are candidates, so a name from outside can never be notified. */
  @Test
  void mentioningSomeoneOutsideTheProject_staysOrdinaryText() throws Exception {
    perform(
            post("/designs/" + designId + "/messages").content("{\"body\":\"@Cara take a look\"}"),
            AVA)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.mentions.length()").value(0));

    perform(get("/notifications/unread-count"), 3L).andExpect(jsonPath("$.data.unread").value(0));
  }

  @Test
  void aNotificationCarriesEnoughContextToNavigateBack() throws Exception {
    perform(
            post("/designs/" + designId + "/messages").content("{\"body\":\"@Ben review please\"}"),
            AVA)
        .andExpect(status().isCreated());

    perform(post("/notifications/list").content("{}"), BEN)
        .andExpect(
            jsonPath("$.data.content[0].title")
                .value("Ava Lead mentioned you in Chilled water schematic"))
        .andExpect(jsonPath("$.data.content[0].designId").value(designId))
        .andExpect(jsonPath("$.data.content[0].read").value(false))
        .andExpect(
            jsonPath("$.data.content[0].body")
                .value(org.hamcrest.Matchers.containsString("ONEMEP-40012")));
  }

  @Test
  void notificationsCanBeMarkedRead_individuallyAndAllAtOnce() throws Exception {
    perform(post("/designs/" + designId + "/messages").content("{\"body\":\"@Ben one\"}"), AVA)
        .andExpect(status().isCreated());
    perform(post("/designs/" + designId + "/messages").content("{\"body\":\"@Ben two\"}"), AVA)
        .andExpect(status().isCreated());

    perform(get("/notifications/unread-count"), BEN).andExpect(jsonPath("$.data.unread").value(2));

    Long first =
        jdbc.queryForList(
                "SELECT id FROM onemep_dev.user_notification WHERE user_id = ? ORDER BY id",
                Long.class,
                BEN)
            .get(0);
    perform(patch("/notifications/" + first + "/read"), BEN).andExpect(status().isOk());
    perform(get("/notifications/unread-count"), BEN).andExpect(jsonPath("$.data.unread").value(1));

    perform(patch("/notifications/read-all"), BEN).andExpect(status().isOk());
    perform(get("/notifications/unread-count"), BEN).andExpect(jsonPath("$.data.unread").value(0));
  }

  @Test
  void oneUserCannotMarkAnotherUsersNotificationRead() throws Exception {
    perform(post("/designs/" + designId + "/messages").content("{\"body\":\"@Ben review\"}"), AVA)
        .andExpect(status().isCreated());
    Long bensNotification =
        jdbc.queryForObject(
            "SELECT id FROM onemep_dev.user_notification WHERE user_id = ?", Long.class, BEN);

    perform(patch("/notifications/" + bensNotification + "/read"), AVA)
        .andExpect(status().isNotFound());
  }

  @Test
  void messagesAreScopedToTheirOwnDesign() throws Exception {
    long other = createDesign("Another design");
    perform(post("/designs/" + designId + "/messages").content("{\"body\":\"First design\"}"), AVA)
        .andExpect(status().isCreated());

    perform(post("/designs/" + other + "/messages/list").content("{}"), AVA)
        .andExpect(jsonPath("$.data.totalElements").value(0))
        .andExpect(jsonPath("$.message").value("No messages yet. Start the discussion below."));
    perform(post("/designs/" + designId + "/messages/list").content("{}"), AVA)
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void postingAppearsInTheActivityTrail() throws Exception {
    perform(post("/designs/" + designId + "/messages").content("{\"body\":\"Hello\"}"), AVA)
        .andExpect(status().isCreated());

    assertThat(activityDetails()).contains("Posted a message in Discussion");
  }

  // ── time tracking ─────────────────────────────────────────────────────────

  @Test
  void loggingTime_keepsWorkDateAndLoggedAtSeparate() throws Exception {
    LocalDate yesterday = LocalDate.now().minusDays(1);

    perform(logBody(7, yesterday, "Coordination review"), AVA)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.totalHours").value(7))
        .andExpect(jsonPath("$.data.people").value(1))
        .andExpect(jsonPath("$.data.groups[0].workDate").value(yesterday.toString()))
        .andExpect(jsonPath("$.data.groups[0].entries[0].loggedAt").exists())
        .andExpect(jsonPath("$.data.groups[0].entries[0].note").value("Coordination review"));
  }

  /**
   * ONEMEP-42 is explicit that same-day entries stay separate rows, each with its own timestamp.
   */
  @Test
  void twoEntriesOnOneDay_groupTogetherWithoutBeingMerged() throws Exception {
    LocalDate day = LocalDate.now().minusDays(1);
    perform(logBody(3, day, "Coordination review"), AVA).andExpect(status().isCreated());
    perform(logBody(5, day, "Drawing updates"), AVA).andExpect(status().isCreated());

    perform(get("/designs/" + designId + "/time-entries"), AVA)
        .andExpect(jsonPath("$.data.groups.length()").value(1))
        .andExpect(jsonPath("$.data.groups[0].totalHours").value(8))
        .andExpect(jsonPath("$.data.groups[0].entryCount").value(2))
        .andExpect(jsonPath("$.data.groups[0].entries.length()").value(2))
        .andExpect(jsonPath("$.data.groups[0].entries[0].note").value("Coordination review"))
        .andExpect(jsonPath("$.data.groups[0].entries[1].note").value("Drawing updates"));
  }

  /** The same person on two days is two rows, which the ticket calls expected behaviour. */
  @Test
  void onePersonOnTwoDays_producesTwoRowsButCountsAsOnePerson() throws Exception {
    perform(logBody(8, LocalDate.now().minusDays(1), null), AVA).andExpect(status().isCreated());
    perform(logBody(7, LocalDate.now().minusDays(9), null), AVA).andExpect(status().isCreated());

    perform(get("/designs/" + designId + "/time-entries"), AVA)
        .andExpect(jsonPath("$.data.groups.length()").value(2))
        .andExpect(jsonPath("$.data.people").value(1))
        .andExpect(jsonPath("$.data.totalHours").value(15))
        // Latest work date first.
        .andExpect(
            jsonPath("$.data.groups[0].workDate").value(LocalDate.now().minusDays(1).toString()));
  }

  @Test
  void twoPeopleOnOneDay_stayInSeparateRows() throws Exception {
    LocalDate day = LocalDate.now().minusDays(1);
    perform(logBody(8, day, null), AVA).andExpect(status().isCreated());
    perform(logBody(5, day, null), BEN).andExpect(status().isCreated());

    perform(get("/designs/" + designId + "/time-entries"), AVA)
        .andExpect(jsonPath("$.data.groups.length()").value(2))
        .andExpect(jsonPath("$.data.people").value(2))
        .andExpect(jsonPath("$.data.totalHours").value(13));
  }

  @Test
  void theDailyTotalCannotExceedTwentyFourHours() throws Exception {
    LocalDate day = LocalDate.now().minusDays(1);
    perform(logBody(18, day, null), AVA).andExpect(status().isCreated());

    perform(logBody(7, day, null), AVA)
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error.message")
                .value(
                    "This entry would exceed 24 hours for "
                        + day
                        + ". You already have 18 hours logged."));

    // The remaining six still fit.
    perform(logBody(6, day, null), AVA).andExpect(status().isCreated());
  }

  @Test
  void zeroOrNegativeHours_areRejected() throws Exception {
    LocalDate day = LocalDate.now().minusDays(1);
    perform(logBody(0, day, null), AVA)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("Enter hours greater than 0."));
    perform(logBody(-1, day, null), AVA).andExpect(status().isBadRequest());
  }

  @Test
  void hoursAboveTwentyFourInOneEntry_areRejectedBySchemaToo() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO onemep_dev.design_time_entry
                        (design_id, user_id, work_date, hours, logged_at, created_date)
                    VALUES (?, ?, CURRENT_DATE, 25, NOW(), NOW())
                    """,
                    designId,
                    AVA))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aFutureWorkDate_isRejected() throws Exception {
    perform(logBody(4, LocalDate.now().plusDays(1), null), AVA)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("The work date cannot be in the future."));
  }

  @Test
  void deletingYourOwnEntry_recalculatesTheTotals() throws Exception {
    LocalDate day = LocalDate.now().minusDays(1);
    MvcResult result =
        perform(logBody(3, day, null), AVA).andExpect(status().isCreated()).andReturn();
    perform(logBody(5, day, null), AVA).andExpect(status().isCreated());

    long entryId =
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("groups")
            .get(0)
            .path("entries")
            .get(0)
            .path("id")
            .asLong();

    perform(delete("/designs/" + designId + "/time-entries/" + entryId), AVA)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalHours").value(5))
        .andExpect(jsonPath("$.data.groups[0].entryCount").value(1));
  }

  @Test
  void deletingSomeoneElsesEntry_isRefused() throws Exception {
    LocalDate day = LocalDate.now().minusDays(1);
    MvcResult result =
        perform(logBody(4, day, null), BEN).andExpect(status().isCreated()).andReturn();
    long bensEntry =
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("groups")
            .get(0)
            .path("entries")
            .get(0)
            .path("id")
            .asLong();

    perform(delete("/designs/" + designId + "/time-entries/" + bensEntry), AVA)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("You can only manage your own time entries."));
  }

  @Test
  void anotherPersonsEntriesAreNotMarkedDeletable() throws Exception {
    perform(logBody(4, LocalDate.now().minusDays(1), null), BEN).andExpect(status().isCreated());

    perform(get("/designs/" + designId + "/time-entries"), AVA)
        .andExpect(jsonPath("$.data.groups[0].currentUser").value(false))
        .andExpect(jsonPath("$.data.groups[0].entries[0].deletable").value(false));
  }

  @Test
  void loggingAndDeleting_bothAppearInTheActivityTrail() throws Exception {
    LocalDate day = LocalDate.now().minusDays(1);
    MvcResult result =
        perform(logBody(8, day, null), AVA).andExpect(status().isCreated()).andReturn();
    long entryId =
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("groups")
            .get(0)
            .path("entries")
            .get(0)
            .path("id")
            .asLong();
    perform(delete("/designs/" + designId + "/time-entries/" + entryId), AVA)
        .andExpect(status().isOk());

    // Both events survive — the trail must not imply the logging never happened.
    assertThat(activityDetails())
        .contains("Logged 8 h on " + day, "Deleted a 8 h time entry for " + day);
  }

  @Test
  void anEmptyDesign_reportsTheEmptyState() throws Exception {
    perform(get("/designs/" + designId + "/time-entries"), AVA)
        .andExpect(
            jsonPath("$.message").value("No time logged yet — Log time to add your first entry."))
        .andExpect(jsonPath("$.data.totalHours").value(0))
        .andExpect(jsonPath("$.data.people").value(0));
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private MockHttpServletRequestBuilder logBody(int hours, LocalDate workDate, String note) {
    String body =
        note == null
            ? "{\"hours\":%d,\"workDate\":\"%s\"}".formatted(hours, workDate)
            : "{\"hours\":%d,\"workDate\":\"%s\",\"note\":\"%s\"}".formatted(hours, workDate, note);
    return post("/designs/" + designId + "/time-entries").content(body);
  }

  private List<String> activityDetails() {
    return jdbc.queryForList(
        "SELECT detail FROM onemep_dev.design_activity_log WHERE design_id = ?",
        String.class,
        designId);
  }

  private void ensureMembers() {
    Integer existing =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM onemep_dev.project_member_mapping WHERE project_id = ?",
            Integer.class,
            projectId);
    if (existing != null && existing > 0) {
      return;
    }
    jdbc.update(
        "INSERT INTO onemep_dev.tier_master (name, is_active, created_date)"
            + " VALUES ('Delivery', TRUE, NOW())");
    Long tierId =
        jdbc.queryForObject(
            "SELECT id FROM onemep_dev.tier_master WHERE name = 'Delivery'", Long.class);
    jdbc.update(
        "INSERT INTO onemep_dev.team_role_master (name, tier_id, is_active, created_date)"
            + " VALUES ('Designer', ?, TRUE, NOW())",
        tierId);
    Long roleId =
        jdbc.queryForObject(
            "SELECT id FROM onemep_dev.team_role_master WHERE name = 'Designer'", Long.class);
    // User 3 is deliberately left out, so mentions of them cannot resolve.
    for (long userId : new long[] {AVA, BEN}) {
      jdbc.update(
          """
          INSERT INTO onemep_dev.project_member_mapping
              (project_id, user_id, team_role_id, created_date)
          VALUES (?, ?, ?, NOW())
          """,
          projectId,
          userId,
          roleId);
    }
  }

  private long ensureProject() {
    Long categoryId =
        jdbc.queryForObject(
            "SELECT id FROM onemep_dev.category_master ORDER BY id LIMIT 1", Long.class);
    jdbc.update(
        """
        INSERT INTO onemep_dev.project_master
            (project_number, name, category_id, lifecycle_status, priority, is_active, created_date)
        VALUES ('40012', 'Grandview Hotel Expansion', ?, 'ACTIVE', 'MEDIUM', TRUE, NOW())
        """,
        categoryId);
    return jdbc.queryForObject(
        "SELECT id FROM onemep_dev.project_master WHERE project_number = '40012'", Long.class);
  }

  private long createDesign(String title) throws Exception {
    MvcResult result =
        perform(
                post("/projects/" + projectId + "/designs")
                    .content(
                        """
                        {"zoneCode":"%s","disciplineId":%d,"typeId":%d,"subjectId":%d,
                         "floorId":%d,"stageId":%d,"title":"%s"}
                        """
                            .formatted(
                                nextZone(),
                                lookupId("DISCIPLINE", "M"),
                                lookupId("DESIGN_TYPE", "SCH"),
                                lookupId("SUBJECT", "CHW"),
                                lookupId("FLOOR", "00"),
                                lookupId("STAGE", "DD"),
                                title)),
                AVA)
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .path("data")
        .path("id")
        .asLong();
  }

  /** A distinct zone per Design, so each gets its own Design Number. */
  private static String nextZone() {
    return "Z" + ZONE_SEQ.incrementAndGet();
  }

  private long lookupId(String type, String code) {
    Long id =
        jdbc.queryForObject(
            "SELECT id FROM onemep_dev.lookup_value WHERE lookup_type = ? AND code = ?",
            Long.class,
            type,
            code);
    return id == null ? 0L : id;
  }

  private ResultActions perform(MockHttpServletRequestBuilder builder, long userId)
      throws Exception {
    return mockMvc.perform(
        builder
            .contentType(MediaType.APPLICATION_JSON)
            .with(jwt().jwt(j -> j.subject(String.valueOf(userId)))));
  }
}
