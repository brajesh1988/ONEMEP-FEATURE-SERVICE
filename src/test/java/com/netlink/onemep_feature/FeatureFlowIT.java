package com.netlink.onemep_feature;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void masterDataToProjectFlow_generatesNumber_andEnforcesDeleteGuard() throws Exception {
    long categoryId =
        idOf(
            perform(post("/categories").content("{\"name\":\"Infrastructure\",\"prefix\":\"inf\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.prefix").value("INF"))
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

    long projectId =
        idOf(
            perform(
                    post("/projects")
                        .content(
                            "{\"name\":\"Apollo\",\"categoryId\":"
                                + categoryId
                                + ",\"leadUserIds\":[1],\"members\":[{\"userId\":2,\"teamRoleId\":"
                                + teamRoleId
                                + "}]}"))
                .andExpect(status().isCreated())
                .andExpect(
                    jsonPath("$.data.projectNumber")
                        .value(org.hamcrest.Matchers.matchesPattern("INF-\\d{5}")))
                .andExpect(jsonPath("$.data.lifecycleStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.leadUserIds[0]").value(1))
                .andReturn());

    perform(get("/projects/" + projectId)).andExpect(status().isOk());

    // Structured filter: the project defaults to MEDIUM priority, so it must be returned.
    perform(post("/projects/list").content("{\"filters\":{\"priority\":\"MEDIUM\"}}"))
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
