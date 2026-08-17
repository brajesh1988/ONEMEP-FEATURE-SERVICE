package com.netlink.onemep_feature;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netlink.onemep_feature.user.client.UserDirectoryClient;
import com.netlink.onemep_feature.user.dto.UserSummary;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Central Approval Listing (ONEMEP-44).
 *
 * <p>User 1 raises requests, user 2 reviews them. That split is what makes the two relationships —
 * and the deliberate gap between the sidebar badge and the Pending tab count — observable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
@Import(ApprovalListingIT.StubUserDirectoryConfig.class)
class ApprovalListingIT {

  private static final long REQUESTER = 1L;
  private static final long REVIEWER = 2L;

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16").withInitScript("testcontainers-init.sql");

  @TempDir static Path storageRoot;

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
    registry.add("storage.provider", () -> "local");
    registry.add("storage.local.root", () -> storageRoot.toAbsolutePath().toString());
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

  @BeforeEach
  void setUp() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    // Approvals accumulate across tests in this non-transactional class, and the listing is
    // personalised rather than scoped to a Design — so counts must start from a clean slate.
    jdbc.update("DELETE FROM onemep_dev.approval_request");
    jdbc.update("DELETE FROM onemep_dev.checklist_master");

    projectId = ensureProject();
    ensureMembers();
    designId = createDesign("Design " + System.nanoTime());
  }

  // ── tabs ──────────────────────────────────────────────────────────────────

  @Test
  void aRaisedRequest_appearsForBothPartiesWithOppositeRoles() throws Exception {
    raiseApproval("riser.pdf");

    // The requester sees it as their own request, waiting on the reviewer.
    JsonNode mine = rows(REQUESTER, "PENDING");
    org.assertj.core.api.Assertions.assertThat(mine).hasSize(1);
    org.assertj.core.api.Assertions.assertThat(mine.get(0).path("role").asText())
        .isEqualTo("YOUR_REQUEST");
    org.assertj.core.api.Assertions.assertThat(mine.get(0).path("counterparty").asText())
        .isEqualTo("User 2");

    // The reviewer sees the same request, counterparty flipped to whoever asked.
    JsonNode theirs = rows(REVIEWER, "PENDING");
    org.assertj.core.api.Assertions.assertThat(theirs).hasSize(1);
    org.assertj.core.api.Assertions.assertThat(theirs.get(0).path("role").asText())
        .isEqualTo("TO_REVIEW");
    org.assertj.core.api.Assertions.assertThat(theirs.get(0).path("counterparty").asText())
        .isEqualTo("User 1");

    // Same underlying record, not a copy — ONEMEP-44 is explicit about this.
    org.assertj.core.api.Assertions.assertThat(mine.get(0).path("approvalRequestId").asLong())
        .isEqualTo(theirs.get(0).path("approvalRequestId").asLong());
  }

  @Test
  void decidingARequest_movesItFromPendingToCompleted() throws Exception {
    long requestId = raiseApproval("riser.pdf");

    perform(post("/approvals/list").content("{\"filters\":{\"tab\":\"COMPLETED\"}}"), REQUESTER)
        .andExpect(jsonPath("$.data.totalElements").value(0))
        .andExpect(jsonPath("$.message").value("Nothing completed yet."));

    decide(requestId, "APPROVED", null).andExpect(status().isOk());

    perform(post("/approvals/list").content("{\"filters\":{\"tab\":\"PENDING\"}}"), REQUESTER)
        .andExpect(jsonPath("$.data.totalElements").value(0))
        .andExpect(jsonPath("$.message").value("Nothing pending right now."));
    perform(post("/approvals/list").content("{\"filters\":{\"tab\":\"COMPLETED\"}}"), REQUESTER)
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].statusLabel").value("Approved"));
  }

  @Test
  void pendingIsTheDefaultTab() throws Exception {
    raiseApproval("riser.pdf");

    perform(post("/approvals/list").content("{}"), REQUESTER)
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));
  }

  @Test
  void theListingIsPersonalised_soUnrelatedUsersSeeNothing() throws Exception {
    raiseApproval("riser.pdf");

    // User 3 is a project member but neither raised nor was asked to review this.
    perform(post("/approvals/list").content("{}"), 3L)
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  // ── badge versus tab count ────────────────────────────────────────────────

  /**
   * ONEMEP-44's worked example: the sidebar badge counts only what awaits you, while the Pending
   * tab also carries requests you raised and are waiting on someone else.
   */
  @Test
  void theSidebarBadgeCountsOnlyWhatAwaitsYou_notEverythingPending() throws Exception {
    raiseApproval("riser.pdf");
    raiseApproval("calc.docx");

    // The requester has two pending, but nothing is waiting on them.
    perform(get("/approvals/summary"), REQUESTER)
        .andExpect(jsonPath("$.data.pendingCount").value(2))
        .andExpect(jsonPath("$.data.actionRequiredCount").value(0));

    // The reviewer has the same two pending, and both await their decision.
    perform(get("/approvals/summary"), REVIEWER)
        .andExpect(jsonPath("$.data.pendingCount").value(2))
        .andExpect(jsonPath("$.data.actionRequiredCount").value(2));
  }

  @Test
  void onceDecided_theRequestLeavesTheBadgeAndTheReviewersPendingTab() throws Exception {
    long requestId = raiseApproval("riser.pdf");
    decide(requestId, "APPROVED", null).andExpect(status().isOk());

    perform(get("/approvals/summary"), REVIEWER)
        .andExpect(jsonPath("$.data.actionRequiredCount").value(0))
        .andExpect(jsonPath("$.data.pendingCount").value(0))
        .andExpect(jsonPath("$.data.completedCount").value(1));
  }

  // ── row content ───────────────────────────────────────────────────────────

  @Test
  void aRowCarriesTheDesignFileAndProjectContextNeededToNavigate() throws Exception {
    raiseApproval("riser.pdf");

    perform(post("/approvals/list").content("{}"), REVIEWER)
        .andExpect(
            // The zone varies per Design so each gets its own number; the rest is fixed.
            jsonPath("$.data.content[0].designNumber")
                .value(org.hamcrest.Matchers.matchesPattern("ONEMEP-40012-Z\\d+-M-SCH-CHW-00-DD")))
        .andExpect(jsonPath("$.data.content[0].fileName").value("riser"))
        .andExpect(jsonPath("$.data.content[0].fileExtension").value("pdf"))
        .andExpect(jsonPath("$.data.content[0].revisionLabel").value("R0"))
        .andExpect(jsonPath("$.data.content[0].projectNumber").value("40012"))
        .andExpect(jsonPath("$.data.content[0].designId").exists())
        .andExpect(jsonPath("$.data.content[0].fileId").exists());
  }

  /** A completed row keeps the revision it was raised against, whatever has been uploaded since. */
  @Test
  void aCompletedRow_keepsItsOwnRevisionAfterANewerUpload() throws Exception {
    long fileId = uploadFile("riser.pdf", "revision zero");
    long requestId = submit(fileId);
    decide(requestId, "EDIT_REQUESTED", "Update the schematic.").andExpect(status().isOk());

    perform(
            multipart("/files/" + fileId + "/versions")
                .file(
                    new MockMultipartFile(
                        "file",
                        "riser.pdf",
                        "application/pdf",
                        "revision one".getBytes(StandardCharsets.UTF_8))),
            REQUESTER)
        .andExpect(status().isCreated());

    perform(post("/approvals/list").content("{\"filters\":{\"tab\":\"COMPLETED\"}}"), REQUESTER)
        .andExpect(jsonPath("$.data.content[0].revisionLabel").value("R0"))
        .andExpect(jsonPath("$.data.content[0].statusLabel").value("Edit Requested"));
  }

  @Test
  void statusLabel_readsAsProseForEachTerminalState() throws Exception {
    long requestId = raiseApproval("riser.pdf");
    perform(post("/approval-requests/" + requestId + "/recall"), REQUESTER)
        .andExpect(status().isOk());

    perform(post("/approvals/list").content("{\"filters\":{\"tab\":\"COMPLETED\"}}"), REQUESTER)
        .andExpect(jsonPath("$.data.content[0].statusLabel").value("Recalled"));
  }

  // ── pagination ────────────────────────────────────────────────────────────

  @Test
  void paginationAppliesPerTab() throws Exception {
    for (int i = 0; i < 3; i++) {
      raiseApproval("file" + i + ".pdf");
    }

    perform(
            post("/approvals/list")
                .content("{\"paginationAndSorting\":{\"pageNumber\":0,\"pageSize\":2}}"),
            REVIEWER)
        .andExpect(jsonPath("$.data.content.length()").value(2))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(2))
        .andExpect(jsonPath("$.data.pageNumber").value(0));

    perform(
            post("/approvals/list")
                .content("{\"paginationAndSorting\":{\"pageNumber\":1,\"pageSize\":2}}"),
            REVIEWER)
        .andExpect(jsonPath("$.data.content.length()").value(1));
  }

  // ── read-only ─────────────────────────────────────────────────────────────

  /** ONEMEP-44 puts every workflow action out of scope here; the absence is the requirement. */
  @Test
  void theListingExposesNoDecisionRoutes() throws Exception {
    raiseApproval("riser.pdf");

    perform(post("/approvals/decisions").content("{}"), REVIEWER).andExpect(status().isNotFound());
    perform(post("/approvals/recall").content("{}"), REQUESTER).andExpect(status().isNotFound());
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private JsonNode rows(long userId, String tab) throws Exception {
    MvcResult result =
        perform(post("/approvals/list").content("{\"filters\":{\"tab\":\"" + tab + "\"}}"), userId)
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .path("data")
        .path("content");
  }

  private long raiseApproval(String filename) throws Exception {
    return submit(uploadFile(filename, "content of " + filename));
  }

  private long submit(long fileId) throws Exception {
    String items =
        ensureChecklistItems().stream().map(String::valueOf).collect(Collectors.joining(","));
    MvcResult result =
        perform(
                post("/files/" + fileId + "/approval-requests")
                    .content(
                        "{\"approverIds\":["
                            + REVIEWER
                            + "],\"checkedItemIds\":["
                            + items
                            + "],\"routeToPrincipal\":false}"),
                REQUESTER)
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .path("data")
        .path("id")
        .asLong();
  }

  private ResultActions decide(long requestId, String decision, String note) throws Exception {
    String body =
        note == null
            ? "{\"decision\":\"" + decision + "\"}"
            : "{\"decision\":\"" + decision + "\",\"note\":\"" + note + "\"}";
    return perform(post("/approval-requests/" + requestId + "/decisions").content(body), REVIEWER);
  }

  private List<Long> ensureChecklistItems() throws Exception {
    List<Long> existing =
        jdbc.queryForList("SELECT id FROM onemep_dev.checklist_item ORDER BY id", Long.class);
    if (!existing.isEmpty()) {
      return existing;
    }
    perform(
            post("/checklists")
                .content(
                    """
                    {"recordType":"CHECKLIST","name":"Issue Checks","items":["Check title block"],
                     "appliesTo":{"disciplineIds":[],"typeIds":[],"subjectIds":[]}}
                    """),
            REQUESTER)
        .andExpect(status().isCreated());
    return jdbc.queryForList("SELECT id FROM onemep_dev.checklist_item ORDER BY id", Long.class);
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
        "INSERT INTO onemep_dev.team_role_master (name, tier_id, is_active, is_principal,"
            + " created_date) VALUES ('Designer', ?, TRUE, FALSE, NOW())",
        tierId);
    Long roleId =
        jdbc.queryForObject(
            "SELECT id FROM onemep_dev.team_role_master WHERE name = 'Designer'", Long.class);
    for (long userId : new long[] {REQUESTER, REVIEWER, 3L}) {
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
    Long existing =
        jdbc.query(
            "SELECT id FROM onemep_dev.project_master WHERE project_number = '40012'",
            rs -> rs.next() ? rs.getLong(1) : null);
    if (existing != null) {
      return existing;
    }
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
                REQUESTER)
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .path("data")
        .path("id")
        .asLong();
  }

  private long uploadFile(String filename, String content) throws Exception {
    MvcResult result =
        perform(
                multipart("/designs/" + designId + "/files")
                    .file(
                        new MockMultipartFile(
                            "files",
                            filename,
                            "application/pdf",
                            content.getBytes(StandardCharsets.UTF_8))),
                REQUESTER)
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .path("data")
        .path("results")
        .get(0)
        .path("fileId")
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
    if (!(builder
        instanceof
        org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder)) {
      builder.contentType(MediaType.APPLICATION_JSON);
    }
    return mockMvc.perform(builder.with(jwt().jwt(j -> j.subject(String.valueOf(userId)))));
  }
}
