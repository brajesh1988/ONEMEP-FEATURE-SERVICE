package com.netlink.onemep_feature;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
 * End-to-end flow for the editable, category-driven Technical Master (ONEMEP-29): the seeded
 * template per category, building a form from scratch (add head + fields) on a custom category,
 * saving values, the mandatory-field save block, switching a head out of the project or deleting
 * the field to unblock, and attachments.
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
  void technicalMaster_seededTemplate_dynamicBuild_validation_andAttachments() throws Exception {
    // Seeded category 1 (Commercial) has a non-trivial template.
    long commercialProject =
        idOf(
            perform(
                    post("/projects")
                        .content(
                            "{\"name\":\"Commercial"
                                + " One\",\"categoryId\":1,\"type\":\"NON_CONFIRMED\","
                                + "\"priority\":\"MEDIUM\"}"))
                .andExpect(status().isCreated())
                .andReturn());
    perform(get("/projects/" + commercialProject + "/technical-master/template"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.seriesCode").value(1))
        .andExpect(jsonPath("$.data.sections.length()").value(Matchers.greaterThan(5)));

    // A custom category (series 99) starts with no fields — build the form from scratch.
    long customCat =
        idOf(
            perform(
                    post("/categories")
                        .content("{\"name\":\"Custom\",\"prefix\":\"cst\",\"seriesCode\":99}"))
                .andExpect(status().isCreated())
                .andReturn());
    long projectId =
        idOf(
            perform(
                    post("/projects")
                        .content(
                            "{\"name\":\"Custom Build\",\"categoryId\":"
                                + customCat
                                + ",\"type\":\"NON_CONFIRMED\",\"priority\":\"MEDIUM\","
                                + "\"client\":\"Acme\",\"location\":\"Dubai\"}"))
                .andExpect(status().isCreated())
                .andReturn());

    perform(get("/projects/" + projectId + "/technical-master/template"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sections.length()").value(0));

    // Add head.
    JsonNode t1 =
        dataOf(
            perform(
                    post("/projects/" + projectId + "/technical-master/sections")
                        .content("{\"title\":\"General\"}"))
                .andExpect(status().isCreated())
                .andReturn());
    long sectionId = t1.get("sections").get(0).get("id").asLong();

    // Add an optional field under the head.
    JsonNode t2 =
        dataOf(
            perform(
                    post("/projects/" + projectId + "/technical-master/fields")
                        .content(
                            "{\"sectionId\":"
                                + sectionId
                                + ",\"label\":\"Plot"
                                + " area\",\"unit\":\"m²\",\"dataType\":\"NUMBER\","
                                + "\"required\":false}"))
                .andExpect(status().isCreated())
                .andReturn());
    String plotKey = t2.get("sections").get(0).get("fields").get(0).get("key").asText();

    // Save a value.
    perform(
            put("/projects/" + projectId + "/technical-master")
                .content("{\"values\":{\"" + plotKey + "\":\"1000\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.exists").value(true))
        .andExpect(jsonPath("$.data.values['" + plotKey + "']").value("1000"));

    // Add a MANDATORY field.
    JsonNode t3 =
        dataOf(
            perform(
                    post("/projects/" + projectId + "/technical-master/fields")
                        .content(
                            "{\"sectionId\":"
                                + sectionId
                                + ",\"label\":\"Mandatory"
                                + " field\",\"dataType\":\"TEXT\",\"required\":true}"))
                .andExpect(status().isCreated())
                .andReturn());
    JsonNode fields = t3.get("sections").get(0).get("fields");
    long mandatoryId = -1;
    for (JsonNode f : fields) {
      if (f.get("required").asBoolean()) {
        mandatoryId = f.get("id").asLong();
      }
    }

    // Save now blocked: the mandatory field is empty.
    perform(
            put("/projects/" + projectId + "/technical-master")
                .content("{\"values\":{\"" + plotKey + "\":\"1000\"}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

    // Switch the head out of the project → its mandatory field stops blocking the save.
    perform(
            patch("/projects/" + projectId + "/technical-master/sections/" + sectionId)
                .content("{\"active\":false}"))
        .andExpect(status().isOk());
    perform(
            put("/projects/" + projectId + "/technical-master")
                .content("{\"values\":{\"" + plotKey + "\":\"1000\"}}"))
        .andExpect(status().isOk());

    // Back in the project the block returns; deleting the field clears it for good.
    perform(
            patch("/projects/" + projectId + "/technical-master/sections/" + sectionId)
                .content("{\"active\":true}"))
        .andExpect(status().isOk());
    perform(
            put("/projects/" + projectId + "/technical-master")
                .content("{\"values\":{\"" + plotKey + "\":\"1000\"}}"))
        .andExpect(status().isBadRequest());
    perform(delete("/projects/" + projectId + "/technical-master/fields/" + mandatoryId))
        .andExpect(status().isOk());
    perform(
            put("/projects/" + projectId + "/technical-master")
                .content("{\"values\":{\"" + plotKey + "\":\"1000\"}}"))
        .andExpect(status().isOk());

    // Attachment upload + type guard.
    mockMvc
        .perform(
            multipart("/projects/" + projectId + "/technical-master/attachments")
                .file(
                    new MockMultipartFile("file", "basis.pdf", "application/pdf", "PDF".getBytes()))
                .with(jwt().jwt(j -> j.subject("1"))))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            multipart("/projects/" + projectId + "/technical-master/attachments")
                .file(new MockMultipartFile("file", "x.txt", "text/plain", "x".getBytes()))
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

  private JsonNode dataOf(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
  }
}
