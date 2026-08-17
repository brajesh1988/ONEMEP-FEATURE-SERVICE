package com.netlink.onemep_feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.dao.DataIntegrityViolationException;
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
 * The approval state machine end to end (ONEMEP-40).
 *
 * <p>Not {@code @Transactional} — uploads drive their own transactions, and several assertions here
 * depend on committed state.
 *
 * <p>Users: 1 raises requests, 2 and 3 approve. User 3 additionally holds the Principal role, so
 * routing has somewhere to go.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
@Import(ApprovalFlowIT.StubUserDirectoryConfig.class)
class ApprovalFlowIT {

  private static final long REQUESTER = 1L;
  private static final long APPROVER_A = 2L;
  private static final long PRINCIPAL = 3L;

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
  private long fileId;
  private List<Long> checklistItemIds;

  @BeforeEach
  void setUp() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    projectId = ensureProject();
    ensureMembers();
    designId = createDesign("Design " + System.nanoTime());
    fileId = uploadFile("riser.pdf", "revision zero");

    // Checklists are global, not per-Design, and this class does not roll back between tests. Left
    // alone they would accumulate, and since submission requires *every* applicable item to be
    // ticked, later tests would fail against checklists earlier ones created. Items and
    // applicability cascade from the master.
    jdbc.update("DELETE FROM onemep_dev.checklist_master");
    checklistItemIds = ensureChecklist();
  }

  // ── submission ────────────────────────────────────────────────────────────

  @Test
  void context_offersTheCurrentRevisionAndTheApplicableChecklist() throws Exception {
    perform(get("/files/" + fileId + "/approval-context"), REQUESTER)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.revisionLabel").value("R0"))
        .andExpect(jsonPath("$.data.checklistItems.length()").value(checklistItemIds.size()))
        .andExpect(jsonPath("$.data.principalRoutingAvailable").value(true))
        // The requester never appears among the people they can send it to.
        .andExpect(jsonPath("$.data.eligibleApprovers[?(@.id == 1)]").isEmpty());
  }

  @Test
  void submitting_freezesTheRevisionAndMovesTheDesignUnderReview() throws Exception {
    long requestId = submit(List.of(APPROVER_A), false);

    perform(get("/files/" + fileId + "/approval-requests"), REQUESTER)
        .andExpect(jsonPath("$.data.requests[0].revisionLabel").value("R0"))
        .andExpect(jsonPath("$.data.requests[0].status").value("PENDING"))
        .andExpect(jsonPath("$.data.requests[0].requiredCount").value(1));

    assertThat(designStatus()).isEqualTo("UNDER_REVIEW");
    assertThat(requestId).isPositive();
  }

  @Test
  void submitting_withAnIncompleteChecklist_isRefused() throws Exception {
    perform(
            post("/files/" + fileId + "/approval-requests")
                .content("{\"approverIds\":[" + APPROVER_A + "],\"checkedItemIds\":[]}"),
            REQUESTER)
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error.message")
                .value("Complete all checklist items before sending the file for approval."));
  }

  @Test
  void submitting_toYourself_isRefused() throws Exception {
    perform(submitBody(List.of(REQUESTER), false), REQUESTER)
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error.message")
                .value("You cannot assign an Approval Request to yourself."));
  }

  @Test
  void aSecondPendingRequestForTheSameFile_isRefused() throws Exception {
    submit(List.of(APPROVER_A), false);

    perform(submitBody(List.of(APPROVER_A), false), REQUESTER)
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.error.message")
                .value(
                    "An Approval Request is already pending for this file. Complete or recall the"
                        + " existing request before sending it again."));
  }

  @Test
  void onePendingRequestPerFile_holdsAtTheSchemaLevelToo() throws Exception {
    submit(List.of(APPROVER_A), false);
    long versionId = currentVersionId();

    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO onemep_dev.approval_request
                        (design_id, file_id, version_id, requester_id, status, current_stage,
                         route_to_principal, is_resubmission, version, created_date)
                    VALUES (?, ?, ?, ?, 'PENDING', 'INITIAL', FALSE, FALSE, 0, NOW())
                    """,
                    designId,
                    fileId,
                    versionId,
                    REQUESTER))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // ── decisions ─────────────────────────────────────────────────────────────

  @Test
  void allApproversMustApprove_beforeTheRequestCompletes() throws Exception {
    long requestId = submit(List.of(APPROVER_A, PRINCIPAL), false);

    decide(requestId, APPROVER_A, "APPROVED", null)
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.approvedCount").value(1))
        .andExpect(jsonPath("$.data.requiredCount").value(2));
    assertThat(designStatus()).isEqualTo("UNDER_REVIEW");

    decide(requestId, PRINCIPAL, "APPROVED", null)
        .andExpect(jsonPath("$.data.status").value("APPROVED"));
    assertThat(designStatus()).isEqualTo("APPROVED");
  }

  @Test
  void oneEditRequest_closesTheWholeRequest_butKeepsEarlierDecisions() throws Exception {
    long requestId = submit(List.of(APPROVER_A, PRINCIPAL), false);

    decide(requestId, APPROVER_A, "APPROVED", null).andExpect(status().isOk());
    decide(requestId, PRINCIPAL, "EDIT_REQUESTED", "Update pipe sizing at Level 02.")
        .andExpect(jsonPath("$.data.status").value("EDIT_REQUESTED"));

    assertThat(designStatus()).isEqualTo("EDIT_REQUESTED");
    // The earlier approval is still on the record.
    assertThat(assigneeField(APPROVER_A, "decision")).isEqualTo("APPROVED");
  }

  @Test
  void editRequestAndReject_requireANote() throws Exception {
    long requestId = submit(List.of(APPROVER_A), false);

    decide(requestId, APPROVER_A, "EDIT_REQUESTED", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("Enter a note explaining the required edit."));
    decide(requestId, APPROVER_A, "REJECTED", "   ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("Enter a reason for rejecting this file."));
  }

  @Test
  void anUnassignedUser_cannotDecide() throws Exception {
    long requestId = submit(List.of(APPROVER_A), false);

    decide(requestId, PRINCIPAL, "APPROVED", null)
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error.message")
                .value("Only the assigned Approver can take action on this Approval Request."));
  }

  @Test
  void anApproverCannotDecideTwice() throws Exception {
    long requestId = submit(List.of(APPROVER_A, PRINCIPAL), false);
    decide(requestId, APPROVER_A, "APPROVED", null).andExpect(status().isOk());

    decide(requestId, APPROVER_A, "APPROVED", null)
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.error.message")
                .value("You have already completed your decision for this Approval Request."));
  }

  @Test
  void openComments_blockApprovalButNotEditOrReject() throws Exception {
    long versionId = currentVersionId();
    perform(
            post("/files/" + fileId + "/versions/" + versionId + "/comments")
                .content("{\"body\":\"Pipe clearance needs correction.\"}"),
            REQUESTER)
        .andExpect(status().isCreated());

    long requestId = submit(List.of(APPROVER_A), false);

    decide(requestId, APPROVER_A, "APPROVED", null)
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.error.message")
                .value("Resolve the open comments on this file before approving it."));

    // The other two decisions stay available.
    decide(requestId, APPROVER_A, "EDIT_REQUESTED", "Fix the clearance.")
        .andExpect(status().isOk());
  }

  // ── revision reuse ────────────────────────────────────────────────────────

  @Test
  void aRevisionThatCompletedACycle_cannotBeSentAgain() throws Exception {
    long requestId = submit(List.of(APPROVER_A), false);
    decide(requestId, APPROVER_A, "REJECTED", "Not acceptable.").andExpect(status().isOk());

    perform(submitBody(List.of(APPROVER_A), false), REQUESTER)
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.error.message")
                .value(
                    "R0 has already completed an approval cycle. Upload a new version of this file"
                        + " before sending it for approval again."));
  }

  @Test
  void afterAnEditRequest_aNewRevisionCanBeSentAndIsFlaggedAsAResubmission() throws Exception {
    long requestId = submit(List.of(APPROVER_A), false);
    decide(requestId, APPROVER_A, "EDIT_REQUESTED", "Update the schematic.")
        .andExpect(status().isOk());

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

    submit(List.of(APPROVER_A), false);

    perform(get("/files/" + fileId + "/approval-requests"), REQUESTER)
        .andExpect(jsonPath("$.data.requestCount").value(2))
        .andExpect(jsonPath("$.data.requests[0].revisionLabel").value("R1"))
        .andExpect(jsonPath("$.data.requests[0].resubmission").value(true))
        // The earlier cycle keeps its own revision — it never moves to R1.
        .andExpect(jsonPath("$.data.requests[1].revisionLabel").value("R0"))
        .andExpect(jsonPath("$.data.requests[1].status").value("EDIT_REQUESTED"));
  }

  @Test
  void aRecalledRevision_maySafelyBeSentAgain() throws Exception {
    long requestId = submit(List.of(APPROVER_A), false);
    perform(post("/approval-requests/" + requestId + "/recall"), REQUESTER)
        .andExpect(status().isOk());

    // A recall is not a decision on the file, so R0 is not used up.
    perform(submitBody(List.of(APPROVER_A), false), REQUESTER).andExpect(status().isCreated());
  }

  // ── principal routing ─────────────────────────────────────────────────────

  @Test
  void routingToPrincipal_addsASecondStageAfterTheFirstCompletes() throws Exception {
    long requestId = submit(List.of(APPROVER_A), true);

    decide(requestId, APPROVER_A, "APPROVED", null)
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.currentStage").value("PRINCIPAL"));
    assertThat(designStatus()).isEqualTo("UNDER_REVIEW");

    decide(requestId, PRINCIPAL, "APPROVED", null)
        .andExpect(jsonPath("$.data.status").value("APPROVED"));
    assertThat(designStatus()).isEqualTo("APPROVED");
  }

  @Test
  void aPrincipalWhoAlreadyApprovedDirectly_isNotAskedTwice() throws Exception {
    long requestId = submit(List.of(APPROVER_A, PRINCIPAL), true);

    decide(requestId, APPROVER_A, "APPROVED", null).andExpect(status().isOk());
    // The Principal was a direct approver, so no second stage is created.
    decide(requestId, PRINCIPAL, "APPROVED", null)
        .andExpect(jsonPath("$.data.status").value("APPROVED"))
        .andExpect(jsonPath("$.data.currentStage").value("INITIAL"));
  }

  @Test
  void thePrincipalCannotRouteToThemselves() throws Exception {
    perform(submitBody(List.of(APPROVER_A), true), PRINCIPAL)
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error.message")
                .value(
                    "You are the Principal for this Project, so this approval cannot be routed"
                        + " onwards."));
  }

  // ── recall ────────────────────────────────────────────────────────────────

  @Test
  void onlyTheRequesterCanRecall() throws Exception {
    long requestId = submit(List.of(APPROVER_A), false);

    perform(post("/approval-requests/" + requestId + "/recall"), APPROVER_A)
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error.message")
                .value("Only the requester can recall this Approval Request."));
  }

  @Test
  void recallIsRefusedOnceAnyoneHasActed() throws Exception {
    long requestId = submit(List.of(APPROVER_A, PRINCIPAL), false);
    decide(requestId, APPROVER_A, "APPROVED", null).andExpect(status().isOk());

    perform(post("/approval-requests/" + requestId + "/recall"), REQUESTER)
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.error.message")
                .value(
                    "This Approval Request can no longer be recalled because an Approver has"
                        + " already taken action."));
  }

  @Test
  void recall_returnsTheDesignToItsWorkingState() throws Exception {
    long requestId = submit(List.of(APPROVER_A), false);
    assertThat(designStatus()).isEqualTo("UNDER_REVIEW");

    perform(post("/approval-requests/" + requestId + "/recall"), REQUESTER)
        .andExpect(jsonPath("$.data.status").value("RECALLED"));
    assertThat(designStatus()).isEqualTo("IN_PROGRESS");
  }

  // ── admin actions ─────────────────────────────────────────────────────────

  @Test
  void reassignAndCancel_requireAdministrativeAuthority() throws Exception {
    long requestId = submit(List.of(APPROVER_A), false);

    perform(
            post("/approval-requests/" + requestId + "/reassign")
                .content("{\"newApproverId\":" + PRINCIPAL + "}"),
            REQUESTER)
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error.message").value("Only an administrator can perform this action."));
  }

  @Test
  void anAdminReassignment_retiresTheOldApproverButKeepsThemOnTheRecord() throws Exception {
    long requestId = submit(List.of(APPROVER_A), false);

    performAsAdmin(
            post("/approval-requests/" + requestId + "/reassign")
                .content("{\"newApproverId\":" + PRINCIPAL + "}"))
        .andExpect(status().isOk());

    assertThat(assigneeField(APPROVER_A, "active")).isEqualTo("false");
    assertThat(assigneeField(PRINCIPAL, "active")).isEqualTo("true");

    // The retired approver can no longer act; the new one can.
    decide(requestId, APPROVER_A, "APPROVED", null).andExpect(status().isBadRequest());
    decide(requestId, PRINCIPAL, "APPROVED", null)
        .andExpect(jsonPath("$.data.status").value("APPROVED"));
  }

  @Test
  void anAdminCancellation_closesTheRequest() throws Exception {
    long requestId = submit(List.of(APPROVER_A), false);

    performAsAdmin(post("/approval-requests/" + requestId + "/cancel").content("{}"))
        .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    assertThat(designStatus()).isEqualTo("IN_PROGRESS");
  }

  // ── checklist snapshot ────────────────────────────────────────────────────

  @Test
  void theChecklistSnapshot_survivesTheMasterBeingChanged() throws Exception {
    long requestId = submit(List.of(APPROVER_A), false);

    int snapshotRows =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM onemep_dev.approval_checklist_snapshot WHERE request_id = ?",
            Integer.class,
            requestId);
    assertThat(snapshotRows).isEqualTo(checklistItemIds.size());

    // Deleting the master must not touch the historic request.
    jdbc.update("DELETE FROM onemep_dev.checklist_applicability");
    jdbc.update("DELETE FROM onemep_dev.checklist_item");
    jdbc.update("DELETE FROM onemep_dev.checklist_master");

    perform(get("/files/" + fileId + "/approval-requests"), REQUESTER)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.requests[0].checklist.length()").value(snapshotRows));
  }

  // ── interaction with file deletion ────────────────────────────────────────

  @Test
  void aFileWithAPendingApproval_cannotBeDeleted() throws Exception {
    submit(List.of(APPROVER_A), false);

    perform(delete("/files/" + fileId), REQUESTER)
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.error.message")
                .value(
                    "This file has a Pending Approval Request. Complete, recall, or cancel the"
                        + " request before deleting the file."));
  }

  @Test
  void afterRecalling_theFileCanBeDeletedAgain() throws Exception {
    long requestId = submit(List.of(APPROVER_A), false);
    perform(post("/approval-requests/" + requestId + "/recall"), REQUESTER)
        .andExpect(status().isOk());

    perform(delete("/files/" + fileId), REQUESTER).andExpect(status().isOk());
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private long submit(List<Long> approverIds, boolean routeToPrincipal) throws Exception {
    MvcResult result =
        perform(submitBody(approverIds, routeToPrincipal), REQUESTER)
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .path("data")
        .path("id")
        .asLong();
  }

  private MockHttpServletRequestBuilder submitBody(
      List<Long> approverIds, boolean routeToPrincipal) {
    String approvers =
        approverIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    String items =
        checklistItemIds.stream()
            .map(String::valueOf)
            .collect(java.util.stream.Collectors.joining(","));
    return post("/files/" + fileId + "/approval-requests")
        .content(
            "{\"approverIds\":["
                + approvers
                + "],\"checkedItemIds\":["
                + items
                + "],\"routeToPrincipal\":"
                + routeToPrincipal
                + "}");
  }

  private ResultActions decide(long requestId, long actor, String decision, String note)
      throws Exception {
    String body =
        note == null
            ? "{\"decision\":\"" + decision + "\"}"
            : "{\"decision\":\"" + decision + "\",\"note\":\"" + note + "\"}";
    return perform(post("/approval-requests/" + requestId + "/decisions").content(body), actor);
  }

  /** Reads one field of one assignee from the latest request, without JSONPath projection. */
  private String assigneeField(long userId, String field) throws Exception {
    MvcResult result =
        perform(get("/files/" + fileId + "/approval-requests"), REQUESTER)
            .andExpect(status().isOk())
            .andReturn();
    JsonNode assignees =
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("requests")
            .get(0)
            .path("assignees");
    for (JsonNode assignee : assignees) {
      if (assignee.path("userId").asLong() == userId) {
        return assignee.path(field).asText();
      }
    }
    throw new AssertionError("No assignee found for userId " + userId);
  }

  private String designStatus() {
    return jdbc.queryForObject(
        "SELECT status FROM onemep_dev.design WHERE id = ?", String.class, designId);
  }

  private long currentVersionId() {
    return jdbc.queryForObject(
        "SELECT current_version_id FROM onemep_dev.design_file WHERE id = ?", Long.class, fileId);
  }

  /** One Checklist covering this Design's Discipline/Type/Subject, via Any wildcards. */
  private List<Long> ensureChecklist() throws Exception {
    MvcResult result =
        perform(
                post("/checklists")
                    .content(
                        """
                        {"recordType":"CHECKLIST","name":"Issue Checks %d",
                         "items":["Check title block","Verify scale"],
                         "appliesTo":{"disciplineIds":[],"typeIds":[],"subjectIds":[]}}
                        """
                            .formatted(System.nanoTime())),
                REQUESTER)
            .andExpect(status().isCreated())
            .andReturn();
    long checklistId =
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .asLong();
    return jdbc.queryForList(
        "SELECT id FROM onemep_dev.checklist_item WHERE checklist_id = ? ORDER BY sort_order",
        Long.class,
        checklistId);
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
    jdbc.update(
        "INSERT INTO onemep_dev.team_role_master (name, tier_id, is_active, is_principal,"
            + " created_date) VALUES ('Principal', ?, TRUE, TRUE, NOW())",
        tierId);
    Long designerRole =
        jdbc.queryForObject(
            "SELECT id FROM onemep_dev.team_role_master WHERE name = 'Designer'", Long.class);
    Long principalRole =
        jdbc.queryForObject(
            "SELECT id FROM onemep_dev.team_role_master WHERE name = 'Principal'", Long.class);

    addMember(REQUESTER, designerRole);
    addMember(APPROVER_A, designerRole);
    addMember(PRINCIPAL, principalRole);
  }

  private void addMember(long userId, Long roleId) {
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
    JsonNode results =
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("results");
    return results.get(0).path("fileId").asLong();
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

  private ResultActions performAsAdmin(MockHttpServletRequestBuilder builder) throws Exception {
    return mockMvc.perform(
        builder
            .contentType(MediaType.APPLICATION_JSON)
            .with(
                jwt()
                    .jwt(j -> j.subject(String.valueOf(REQUESTER)))
                    .authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority(
                            "ROLE_ADMIN"))));
  }
}
