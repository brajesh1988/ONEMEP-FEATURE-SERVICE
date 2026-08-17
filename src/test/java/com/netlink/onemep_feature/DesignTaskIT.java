package com.netlink.onemep_feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.Collection;
import java.util.HashMap;
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

/** Design task details end to end (ONEMEP-38). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Tag("integration")
@Import(DesignTaskIT.StubUserDirectoryConfig.class)
class DesignTaskIT {

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
      return new UserDirectoryClient() {
        @Override
        public Map<Long, UserSummary> resolve(Collection<Long> ids) {
          Map<Long, UserSummary> result = new HashMap<>();
          if (ids != null) {
            ids.stream()
                .filter(java.util.Objects::nonNull)
                .forEach(id -> result.put(id, new UserSummary(id, "User " + id, null)));
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
  private long memberUserId;
  private long outsiderUserId;

  @BeforeEach
  void setUp() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    projectId = createProject();
    memberUserId = existingUserId(0);
    outsiderUserId = existingUserId(1);
    addProjectMember(projectId, memberUserId);
    designId = createDesign();
  }

  // ── defaults ──────────────────────────────────────────────────────────────

  @Test
  void newDesign_startsWithTheDocumentedTaskDefaults() throws Exception {
    perform(get("/designs/" + designId + "/task"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.owner").doesNotExist())
        .andExpect(jsonPath("$.data.priority").value("MEDIUM"))
        .andExpect(jsonPath("$.data.completionPct").value(0))
        .andExpect(jsonPath("$.data.reminder").value("NONE"))
        .andExpect(jsonPath("$.data.source").value("MANUAL"))
        .andExpect(jsonPath("$.data.status").value("DRAFT"))
        .andExpect(jsonPath("$.data.durationDays").doesNotExist())
        .andExpect(jsonPath("$.data.tags.length()").value(0));
  }

  @Test
  void designDetail_carriesTheTaskSection_soTheScreenNeedsOneFetch() throws Exception {
    perform(get("/designs/" + designId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.designNumber").exists())
        .andExpect(jsonPath("$.data.task.priority").value("MEDIUM"))
        .andExpect(jsonPath("$.data.task.source").value("MANUAL"));
  }

  // ── owner ─────────────────────────────────────────────────────────────────

  @Test
  void assigningAProjectMemberAsOwner_succeeds() throws Exception {
    perform(patch("/designs/" + designId + "/task").content("{\"ownerId\":" + memberUserId + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.owner.id").value(memberUserId))
        .andExpect(jsonPath("$.data.owner.displayName").value("User " + memberUserId));
  }

  @Test
  void assigningSomeoneOutsideTheProject_isRejected() throws Exception {
    perform(patch("/designs/" + designId + "/task").content("{\"ownerId\":" + outsiderUserId + "}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error.message")
                .value("The selected Owner is no longer available. Select another Project user."));
  }

  // ── completion and dates ──────────────────────────────────────────────────

  @Test
  void completionOutsideRange_isRejectedByTheSchemaToo() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE onemep_dev.design SET completion_pct = 101 WHERE id = ?", designId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void dueBeforeStart_isRejectedByTheSchemaToo() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    UPDATE onemep_dev.design
                    SET start_date = DATE '2026-07-10', due_date = DATE '2026-06-23'
                    WHERE id = ?
                    """,
                    designId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void settingBothDates_derivesDurationAndReminderDate() throws Exception {
    perform(
            patch("/designs/" + designId + "/task")
                .content(
                    """
                    {"startDate":"2026-06-23","dueDate":"2026-07-10",
                     "reminder":"THREE_DAYS_BEFORE"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.durationDays").value(17))
        .andExpect(jsonPath("$.data.reminderDate").value("2026-07-07"));
  }

  @Test
  void movingTheDueDate_movesTheReminderWithIt() throws Exception {
    perform(
            patch("/designs/" + designId + "/task")
                .content(
                    """
                    {"startDate":"2026-06-23","dueDate":"2026-07-10","reminder":"ONE_WEEK_BEFORE"}
                    """))
        .andExpect(jsonPath("$.data.reminderDate").value("2026-07-03"));

    perform(patch("/designs/" + designId + "/task").content("{\"dueDate\":\"2026-07-17\"}"))
        .andExpect(jsonPath("$.data.reminderDate").value("2026-07-10"))
        .andExpect(jsonPath("$.data.durationDays").value(24));
  }

  // ── tags ──────────────────────────────────────────────────────────────────

  @Test
  void addingATag_thenADuplicateDifferingOnlyByCase_isRejected() throws Exception {
    perform(post("/designs/" + designId + "/tags").content("{\"label\":\"Plant Room\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.tags[0].label").value("Plant Room"));

    perform(post("/designs/" + designId + "/tags").content("{\"label\":\"plant room\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.message").value("This Tag is already added to the Design."));
  }

  @Test
  void tagUniqueness_holdsAtTheSchemaLevelToo() throws Exception {
    perform(post("/designs/" + designId + "/tags").content("{\"label\":\"Plant Room\"}"))
        .andExpect(status().isCreated());

    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO onemep_dev.design_tag
                        (design_id, label, label_normalized, created_date)
                    VALUES (?, 'PLANT ROOM', 'plant room', NOW())
                    """,
                    designId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void removingATag_takesItOffTheDesign() throws Exception {
    MvcResult created =
        perform(post("/designs/" + designId + "/tags").content("{\"label\":\"Coordination\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    long tagId =
        objectMapper
            .readTree(created.getResponse().getContentAsString())
            .path("data")
            .path("tags")
            .get(0)
            .path("id")
            .asLong();

    perform(delete("/designs/" + designId + "/tags/" + tagId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.tags.length()").value(0));
  }

  @Test
  void removingATagFromAnotherDesign_isNotFound() throws Exception {
    perform(delete("/designs/" + designId + "/tags/999999")).andExpect(status().isNotFound());
  }

  // ── audit wiring ──────────────────────────────────────────────────────────

  @Test
  void taskChanges_appearInTheActivityTrail() throws Exception {
    perform(
            patch("/designs/" + designId + "/task")
                .content(
                    """
                    {"ownerId":%d,"priority":"HIGH","completionPct":60}
                    """
                        .formatted(memberUserId)))
        .andExpect(status().isOk());
    perform(post("/designs/" + designId + "/tags").content("{\"label\":\"Coordination\"}"))
        .andExpect(status().isCreated());

    assertThat(activityDetails())
        .contains(
            "Owner changed from (unassigned) to User " + memberUserId,
            "Priority changed from Medium to High",
            "Completion updated from 0% to 60%",
            "Tag 'Coordination' added");
  }

  @Test
  void aRejectedTaskUpdate_leavesNoAuditTrace() throws Exception {
    int before = activityDetails().size();

    perform(patch("/designs/" + designId + "/task").content("{\"completionPct\":101}"))
        .andExpect(status().isBadRequest());

    assertThat(activityDetails()).hasSize(before);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private java.util.List<String> activityDetails() {
    return jdbc.queryForList(
        "SELECT detail FROM onemep_dev.design_activity_log WHERE design_id = ?",
        String.class,
        designId);
  }

  private long existingUserId(int offset) {
    return jdbc.queryForList("SELECT id FROM onemep_dev.user_master ORDER BY id", Long.class)
        .get(offset);
  }

  /** Neither tier_master nor team_role_master is seeded by a migration, so create the chain. */
  private void addProjectMember(long project, long userId) {
    jdbc.update(
        "INSERT INTO onemep_dev.tier_master (name, is_active, created_date)"
            + " VALUES ('IT Tier', TRUE, NOW())");
    Long tierId =
        jdbc.queryForObject(
            "SELECT id FROM onemep_dev.tier_master WHERE name = 'IT Tier'", Long.class);

    jdbc.update(
        "INSERT INTO onemep_dev.team_role_master (name, tier_id, is_active, created_date)"
            + " VALUES ('IT Designer', ?, TRUE, NOW())",
        tierId);
    Long teamRoleId =
        jdbc.queryForObject(
            "SELECT id FROM onemep_dev.team_role_master WHERE name = 'IT Designer'", Long.class);

    jdbc.update(
        """
        INSERT INTO onemep_dev.project_member_mapping
            (project_id, user_id, team_role_id, created_date)
        VALUES (?, ?, ?, NOW())
        """,
        project,
        userId,
        teamRoleId);
  }

  private long createProject() {
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
    Long id =
        jdbc.queryForObject(
            "SELECT id FROM onemep_dev.project_master WHERE project_number = '40012'", Long.class);
    return id == null ? 0L : id;
  }

  private long createDesign() throws Exception {
    MvcResult result =
        perform(
                post("/projects/" + projectId + "/designs")
                    .content(
                        """
                        {"zoneCode":"%s","disciplineId":%d,"typeId":%d,"subjectId":%d,
                         "floorId":%d,"stageId":%d,"title":"Chilled water schematic"}
                        """
                            .formatted(
                                nextZone(),
                                lookupId("DISCIPLINE", "M"),
                                lookupId("DESIGN_TYPE", "SCH"),
                                lookupId("SUBJECT", "CHW"),
                                lookupId("FLOOR", "00"),
                                lookupId("STAGE", "DD"))))
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

  private ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
    return mockMvc.perform(
        builder.contentType(MediaType.APPLICATION_JSON).with(jwt().jwt(j -> j.subject("1"))));
  }
}
