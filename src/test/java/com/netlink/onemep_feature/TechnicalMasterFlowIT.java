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
 * End-to-end flow for the project-level Technical Master (ONEMEP-29): empty-state shell → create
 * with parameters + DID → consolidated read (with read-only client info) → attachment
 * upload/download → replace → and the 400/404/409 guards.
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
  void technicalMasterFlow_shell_upsert_attachment_replace_andGuards() throws Exception {
    long categoryId =
        idOf(
            perform(post("/categories").content("{\"name\":\"Electrical\",\"prefix\":\"el\"}"))
                .andExpect(status().isCreated())
                .andReturn());

    long projectId =
        idOf(
            perform(
                    post("/projects")
                        .content(
                            "{\"name\":\"Helios\",\"categoryId\":"
                                + categoryId
                                + ",\"type\":\"NON_CONFIRMED\",\"priority\":\"MEDIUM\","
                                + "\"client\":\"Acme\",\"location\":\"Dubai\"}"))
                .andExpect(status().isCreated())
                .andReturn());

    // Catalog technical field the parameters will reference (reusable across projects).
    long fieldId =
        idOf(
            perform(post("/technical-master").content("{\"name\":\"Voltage\"}"))
                .andExpect(status().isCreated())
                .andReturn());

    // Empty-state: GET before creation returns a 200 shell, not 404, with read-only client info.
    perform(get("/projects/" + projectId + "/technical-master"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.exists").value(false))
        .andExpect(jsonPath("$.data.clientInfo.client").value("Acme"))
        .andExpect(jsonPath("$.data.commonParameters").isEmpty());

    // ONEMEP-30 summary before creation: exists:false shell with zero counts.
    perform(get("/projects/" + projectId + "/technical-master/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.exists").value(false))
        .andExpect(jsonPath("$.data.editable").value(false))
        .andExpect(jsonPath("$.data.commonParameterCount").value(0));

    // Create/maintain: upsert with a common parameter + a DID specification.
    perform(
            put("/projects/" + projectId + "/technical-master")
                .content(
                    "{\"remarks\":\"general inputs\",\"parameters\":[{\"scope\":\"COMMON\","
                        + "\"technicalFieldId\":"
                        + fieldId
                        + ",\"value\":\"230\"}],\"didSpecifications\":[{\"name\":\"Input Voltage\","
                        + "\"specification\":\"230V\"}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.exists").value(true))
        .andExpect(jsonPath("$.data.commonParameters[0].technicalFieldName").value("Voltage"))
        .andExpect(jsonPath("$.data.commonParameters[0].value").value("230"))
        .andExpect(jsonPath("$.data.didSpecifications[0].name").value("Input Voltage"));

    // Read back the consolidated form.
    perform(get("/projects/" + projectId + "/technical-master"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.exists").value(true))
        .andExpect(jsonPath("$.data.remarks").value("general inputs"))
        .andExpect(jsonPath("$.data.clientInfo.location").value("Dubai"));

    // ONEMEP-30 summary after creation: counts + version details.
    perform(get("/projects/" + projectId + "/technical-master/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.exists").value(true))
        .andExpect(jsonPath("$.data.editable").value(true))
        .andExpect(jsonPath("$.data.commonParameterCount").value(1))
        .andExpect(jsonPath("$.data.didSpecificationCount").value(1))
        .andExpect(jsonPath("$.data.version").isNumber());

    // Summary for an unknown project → 404.
    perform(get("/projects/999999/technical-master/summary"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));

    // Attachment: upload → download bytes → guard on disallowed extension.
    MockMultipartFile file =
        new MockMultipartFile("file", "calc.pdf", "application/pdf", "PDF-BYTES".getBytes());
    long attachmentId =
        idOf(
            mockMvc
                .perform(
                    multipart("/projects/" + projectId + "/technical-master/attachments")
                        .file(file)
                        .with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fileExtension").value("pdf"))
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

    // Guard: unknown technical field → 404.
    perform(
            put("/projects/" + projectId + "/technical-master")
                .content("{\"parameters\":[{\"scope\":\"COMMON\",\"technicalFieldId\":999999}]}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));

    // Guard: invalid scope → 400.
    perform(
            put("/projects/" + projectId + "/technical-master")
                .content(
                    "{\"parameters\":[{\"scope\":\"BOGUS\",\"technicalFieldId\":"
                        + fieldId
                        + "}]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

    // Guard: duplicate parameter (same scope + field) → 409.
    perform(
            put("/projects/" + projectId + "/technical-master")
                .content(
                    "{\"parameters\":[{\"scope\":\"COMMON\",\"technicalFieldId\":"
                        + fieldId
                        + "},{\"scope\":\"COMMON\",\"technicalFieldId\":"
                        + fieldId
                        + "}]}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));

    // Replace with an empty form → parameters cleared, still exists.
    perform(
            put("/projects/" + projectId + "/technical-master")
                .content("{\"parameters\":[],\"didSpecifications\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.commonParameters").isEmpty())
        .andExpect(jsonPath("$.data.didSpecifications").isEmpty());
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
