package com.netlink.onemep_feature;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netlink.onemep_feature.user.client.UserDirectoryClient;
import com.netlink.onemep_feature.user.dto.UserSummary;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end integration test: real Spring context + real Postgres (Testcontainers) driving the
 * controllers → services → repositories against Flyway-migrated feature tables. Verifies the
 * master-data → project flow, id-derived project number, and the delete-in-use guard. The shared
 * {@code user_master} is created by {@code testcontainers-init.sql} before Flyway runs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
@Import(FeatureFlowIT.StubUserDirectoryConfig.class)
class FeatureFlowIT {

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
    // No real identity gRPC server in this test; the stub client below replaces it.
    registry.add("grpc.client.identity-service.address", () -> "static://localhost:1");
  }

  /**
   * Replaces the gRPC-backed client with an in-memory stub so the flow can exercise user-id
   * enrichment (MEP Team / activity / leads) without a live identity service.
   */
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
                .filter(id -> id != null)
                .forEach(
                    id ->
                        result.put(
                            id, new UserSummary(id, "User " + id, "user" + id + "@onemep.local")));
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

  @Test
  void projectEpicFlow_typeIdScheme_confirm_lifecycleReason_andGuards() throws Exception {
    long categoryId =
        idOf(
            perform(
                    post("/categories")
                        .content(
                            "{\"name\":\"Infrastructure\",\"prefix\":\"inf\",\"seriesCode\":6}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.prefix").value("INF"))
                .andExpect(jsonPath("$.data.seriesCode").value(6))
                .andExpect(jsonPath("$.data.categoryNumber").value("CAT-00001"))
                .andReturn());

    long tierId =
        idOf(
            perform(post("/tiers").content("{\"name\":\"Tier 1\"}"))
                .andExpect(status().isCreated())
                .andReturn());

    long teamRoleId =
        idOf(
            perform(
                    post("/team-roles")
                        .content("{\"name\":\"Lead Engineer\",\"tierId\":" + tierId + "}"))
                .andExpect(status().isCreated())
                .andReturn());

    long officeId =
        idOf(
            perform(post("/handling-offices").content("{\"name\":\"Dubai Office\"}"))
                .andExpect(status().isCreated())
                .andReturn());

    long levelId =
        idOf(
            perform(post("/detailing-levels").content("{\"name\":\"LOD 400\"}"))
                .andExpect(status().isCreated())
                .andReturn());

    // Non-confirmed project → NC-prefixed number, lifecycle defaults to ACTIVE.
    long projectId =
        idOf(
            perform(
                    post("/projects")
                        .content(
                            "{\"name\":\"Apollo\",\"categoryId\":"
                                + categoryId
                                + ",\"type\":\"NON_CONFIRMED\",\"priority\":\"MEDIUM\",\"client\":\"Acme\","
                                + "\"location\":\"Dubai\",\"handlingOfficeId\":"
                                + officeId
                                + ",\"detailingLevelId\":"
                                + levelId
                                + ",\"leadUserIds\":[1],\"members\":[{\"userId\":2,\"teamRoleId\":"
                                + teamRoleId
                                + "}]}"))
                .andExpect(status().isCreated())
                .andExpect(
                    jsonPath("$.data.projectNumber")
                        .value(org.hamcrest.Matchers.matchesPattern("NC\\d{4}")))
                .andExpect(jsonPath("$.data.type").value("NON_CONFIRMED"))
                .andExpect(jsonPath("$.data.lifecycleStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.handlingOfficeName").value("Dubai Office"))
                .andExpect(jsonPath("$.data.leadUserIds[0]").value(1))
                .andReturn());

    // Name character rule: '#' is not allowed.
    perform(
            post("/projects")
                .content(
                    "{\"name\":\"Bad#Name\",\"categoryId\":"
                        + categoryId
                        + ",\"type\":\"NON_CONFIRMED\",\"priority\":\"LOW\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

    // Confirm the project → Project ID reassigned from series 6, type locked.
    perform(patch("/projects/" + projectId + "/type").param("type", "CONFIRMED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.type").value("CONFIRMED"))
        .andExpect(jsonPath("$.data.typeLocked").value(true))
        .andExpect(
            jsonPath("$.data.projectNumber")
                .value(org.hamcrest.Matchers.matchesPattern("6\\d{4}")));

    // Confirmed project cannot revert to Non-confirmed.
    perform(patch("/projects/" + projectId + "/type").param("type", "NON_CONFIRMED"))
        .andExpect(status().isBadRequest());

    // Lifecycle → ON_HOLD requires a reason.
    perform(patch("/projects/" + projectId + "/lifecycle").param("lifecycleStatus", "ON_HOLD"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    perform(
            patch("/projects/" + projectId + "/lifecycle")
                .param("lifecycleStatus", "ON_HOLD")
                .param("reason", "Awaiting client sign-off"))
        .andExpect(status().isOk());

    // ONEMEP-15 overview sections: specs sheet upload, delivery schedule, stakeholders.
    MockMultipartFile specFile =
        new MockMultipartFile(
            "file", "design-basis.pdf", "application/pdf", "PDF-CONTENT".getBytes());
    long sheetId =
        idOf(
            mockMvc
                .perform(
                    multipart("/projects/" + projectId + "/spec-sheets")
                        .file(specFile)
                        .with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fileExtension").value("pdf"))
                .andReturn());

    // Disallowed extension is rejected.
    mockMvc
        .perform(
            multipart("/projects/" + projectId + "/spec-sheets")
                .file(new MockMultipartFile("file", "notes.txt", "text/plain", "x".getBytes()))
                .with(jwt().jwt(j -> j.subject("1"))))
        .andExpect(status().isBadRequest());

    // Download returns the stored bytes.
    mockMvc
        .perform(
            get("/projects/" + projectId + "/spec-sheets/" + sheetId + "/download")
                .with(jwt().jwt(j -> j.subject("1"))))
        .andExpect(status().isOk())
        .andExpect(content().bytes("PDF-CONTENT".getBytes()));

    perform(
            post("/projects/" + projectId + "/delivery-schedule")
                .content(
                    "{\"milestone\":\"Design Freeze\",\"deliverable\":\"IFC drawings\","
                        + "\"plannedDate\":\"2026-09-01\",\"status\":\"IN_PROGRESS\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

    perform(
            post("/projects/" + projectId + "/stakeholders")
                .content(
                    "{\"role\":\"PROJECT_HEAD\",\"name\":\"Jane Doe\","
                        + "\"organization\":\"Acme\",\"email\":\"jane@acme.com\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.role").value("PROJECT_HEAD"));

    // Overview aggregates every section + the activity log, with user ids enriched to names.
    perform(get("/projects/" + projectId + "/overview"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.project.lifecycleStatus").value("ON_HOLD"))
        .andExpect(jsonPath("$.data.specSheets[0].fileName").value("design-basis.pdf"))
        .andExpect(jsonPath("$.data.deliverySchedule[0].milestone").value("Design Freeze"))
        .andExpect(jsonPath("$.data.stakeholders[0].name").value("Jane Doe"))
        .andExpect(jsonPath("$.data.activity").isNotEmpty())
        // MEP Team member id 2 → resolved display name.
        .andExpect(jsonPath("$.data.project.members[0].userId").value(2))
        .andExpect(jsonPath("$.data.project.members[0].userName").value("User 2"))
        // Lead id 1 → resolved display name.
        .andExpect(jsonPath("$.data.project.leads[0].userName").value("User 1"))
        // Activity actor (subject "1") → resolved display name.
        .andExpect(jsonPath("$.data.activity[0].performedByName").value("User 1"));

    // Structured Type filter returns the confirmed project.
    perform(post("/projects/list").content("{\"filters\":{\"type\":\"CONFIRMED\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1));

    // Delete guard: the team role is assigned to a project member and must not be deletable.
    mockMvc
        .perform(delete("/team-roles/" + teamRoleId).with(jwt().jwt(j -> j.subject("1"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_IN_USE"));
  }

  private org.springframework.test.web.servlet.ResultActions perform(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder)
      throws Exception {
    return mockMvc.perform(
        builder.contentType(MediaType.APPLICATION_JSON).with(jwt().jwt(j -> j.subject("1"))));
  }

  private long idOf(MvcResult result) throws Exception {
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .path("data")
        .path("id")
        .asLong();
  }
}
