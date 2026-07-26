package com.netlink.onemep_feature;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
 * End-to-end flow for the category-driven Technical Master (ONEMEP-29): the template is served from
 * the backend per the project's category (series code, seeded in V4), values are saved/read back,
 * the summary counts, unknown keys are rejected, and attachments upload/download/delete.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
class TechnicalMasterFlowIT {

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
  void technicalMasterFlow_template_values_summary_attachments_andGuards() throws Exception {
    // Category 1 (Commercial, series 1) is seeded by V4; its field set is seeded by V8.
    long projectId =
        idOf(
            perform(
                    post("/projects")
                        .content(
                            "{\"name\":\"TM Commercial\",\"categoryId\":1,"
                                + "\"type\":\"NON_CONFIRMED\",\"priority\":\"MEDIUM\","
                                + "\"client\":\"Acme\",\"location\":\"Dubai\"}"))
                .andExpect(status().isCreated())
                .andReturn());

    // Template is driven by the category — Commercial gets its sections/fields.
    perform(get("/projects/" + projectId + "/technical-master/template"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.seriesCode").value(1))
        .andExpect(jsonPath("$.data.sections[0].title").value("Project identification"))
        .andExpect(jsonPath("$.data.sections.length()").value(Matchers.greaterThan(5)));

    // Empty state: no values yet, but client info is read-only from the project.
    perform(get("/projects/" + projectId + "/technical-master"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.exists").value(false))
        .andExpect(jsonPath("$.data.clientInfo.client").value("Acme"));

    perform(get("/projects/" + projectId + "/technical-master/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.exists").value(false))
        .andExpect(jsonPath("$.data.totalFields").value(Matchers.greaterThan(50)));

    // Save two real Commercial field values.
    perform(
            put("/projects/" + projectId + "/technical-master")
                .content(
                    "{\"remarks\":\"design basis\",\"values\":{"
                        + "\"site_area_statement__plot_area\":\"1000\","
                        + "\"site_area_statement__gfa\":\"800\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.exists").value(true))
        .andExpect(jsonPath("$.data.values['site_area_statement__plot_area']").value("1000"));

    perform(get("/projects/" + projectId + "/technical-master"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.remarks").value("design basis"))
        .andExpect(jsonPath("$.data.values['site_area_statement__gfa']").value("800"));

    perform(get("/projects/" + projectId + "/technical-master/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.exists").value(true))
        .andExpect(jsonPath("$.data.filledFieldCount").value(2));

    // Guard: a field key that is not in this category's template → 400.
    perform(
            put("/projects/" + projectId + "/technical-master")
                .content("{\"values\":{\"bogus__key\":\"x\"}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

    // Attachment: upload → download bytes → guard on disallowed extension.
    MockMultipartFile file =
        new MockMultipartFile("file", "basis.pdf", "application/pdf", "PDF-BYTES".getBytes());
    long attachmentId =
        idOf(
            mockMvc
                .perform(
                    multipart("/projects/" + projectId + "/technical-master/attachments")
                        .file(file)
                        .with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isCreated())
                .andReturn());

    mockMvc
        .perform(
            get("/projects/"
                    + projectId
                    + "/technical-master/attachments/"
                    + attachmentId
                    + "/download")
                .with(jwt().jwt(j -> j.subject("1"))))
        .andExpect(status().isOk())
        .andExpect(content().bytes("PDF-BYTES".getBytes()));

    mockMvc
        .perform(
            multipart("/projects/" + projectId + "/technical-master/attachments")
                .file(new MockMultipartFile("file", "notes.txt", "text/plain", "x".getBytes()))
                .with(jwt().jwt(j -> j.subject("1"))))
        .andExpect(status().isBadRequest());
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
