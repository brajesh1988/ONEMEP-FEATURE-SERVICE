package com.netlink.onemep_feature;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end flow for the editable, category-driven Technical Master (ONEMEP-29) and its combined
 * DID tab (ONEMEP-31): the seeded template per category, building a form from scratch (add head +
 * fields) on a custom category, saving values, the mandatory-field save block, switching a head out
 * of the project or deleting the field to unblock, attachments, and the combined Technical Master +
 * DID save (one action, atomic across both tabs, no standalone DID save route).
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

  /**
   * Minimal always-valid DID payload, appended to Technical Master saves that don't care about DID.
   */
  private static final String MINIMAL_DID =
      "\"did\":{\"designIntentBrief\":{\"lockedDesignIntent\":\"Design brief\"},"
          + "\"deliverySchedule\":[],\"clientInformation\":{\"contacts\":[]},"
          + "\"architectTeam\":{\"contacts\":[]},\"structureConsultantTeam\":{\"contacts\":[]}}";

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

    // Save a value — every Technical Master save now also carries the DID payload (ONEMEP-31).
    perform(
            put("/projects/" + projectId + "/technical-master")
                .content(withDid("{\"values\":{\"" + plotKey + "\":\"1000\"}}")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.exists").value(true))
        .andExpect(jsonPath("$.data.values['" + plotKey + "']").value("1000"))
        .andExpect(
            jsonPath("$.data.did.designIntentBrief.lockedDesignIntent").value("Design brief"));

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
                .content(withDid("{\"values\":{\"" + plotKey + "\":\"1000\"}}")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

    // Switch the head out of the project → its mandatory field stops blocking the save.
    perform(
            patch("/projects/" + projectId + "/technical-master/sections/" + sectionId)
                .content("{\"active\":false}"))
        .andExpect(status().isOk());
    perform(
            put("/projects/" + projectId + "/technical-master")
                .content(withDid("{\"values\":{\"" + plotKey + "\":\"1000\"}}")))
        .andExpect(status().isOk());

    // Back in the project the block returns; deleting the field clears it for good.
    perform(
            patch("/projects/" + projectId + "/technical-master/sections/" + sectionId)
                .content("{\"active\":true}"))
        .andExpect(status().isOk());
    perform(
            put("/projects/" + projectId + "/technical-master")
                .content(withDid("{\"values\":{\"" + plotKey + "\":\"1000\"}}")))
        .andExpect(status().isBadRequest());
    perform(delete("/projects/" + projectId + "/technical-master/fields/" + mandatoryId))
        .andExpect(status().isOk());
    perform(
            put("/projects/" + projectId + "/technical-master")
                .content(withDid("{\"values\":{\"" + plotKey + "\":\"1000\"}}")))
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

  @Test
  void combinedSave_savesBothTabsTogether_atomicallyAndWithNoStandaloneDidRoute() throws Exception {
    long customCat =
        idOf(
            perform(
                    post("/categories")
                        .content("{\"name\":\"DidCombined\",\"prefix\":\"dc2\",\"seriesCode\":98}"))
                .andExpect(status().isCreated())
                .andReturn());
    long projectId =
        idOf(
            perform(
                    post("/projects")
                        .content(
                            "{\"name\":\"DID Combined Flow\",\"categoryId\":"
                                + customCat
                                + ",\"type\":\"NON_CONFIRMED\",\"priority\":\"MEDIUM\"}"))
                .andExpect(status().isCreated())
                .andReturn());

    // Nothing saved yet: configured DID defaults are synthesized, not persisted.
    perform(get("/projects/" + projectId + "/technical-master/did"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.exists").value(false))
        .andExpect(jsonPath("$.data.designIntentBrief").doesNotExist())
        .andExpect(jsonPath("$.data.deliverySchedule.length()").value(5))
        .andExpect(jsonPath("$.data.deliverySchedule[0].id").doesNotExist())
        .andExpect(jsonPath("$.data.clientInformation.contacts.length()").value(3))
        .andExpect(jsonPath("$.data.architectTeam.contacts.length()").value(0));

    perform(get("/projects/" + projectId + "/technical-master/did/green-rating-options"))
        .andExpect(status().isOk())
        // The dropdown must offer the prototype rating levels, not the generic
        // certification bodies that V15 originally seeded (retired in V27).
        .andExpect(jsonPath("$.data[*].label", Matchers.contains(
            "None",
            "IGBC Silver target",
            "IGBC Gold target",
            "IGBC Platinum target",
            "LEED Gold target",
            "GRIHA 4-star")))
        .andExpect(jsonPath("$.data[*].code", Matchers.not(Matchers.hasItem("IGBC"))));

    // No standalone DID save route exists — the endpoint is gone entirely (ONEMEP-31).
    perform(put("/projects/" + projectId + "/technical-master/did").content("{}"))
        .andExpect(status().isMethodNotAllowed());

    // ── One combined save: Technical Master remarks + full DID payload, one action ──────────
    String firstSavePayload =
        "{\"remarks\":\"Initial"
            + " remarks\",\"values\":{},\"did\":{\"designIntentBrief\":{\"lockedDesignIntent\":\"Locked"
            + " intent text\",\"initialClientRfiResponse\":\"RFI"
            + " response\",\"greenRatingTarget\":\"IGBC Gold target\",\"sustainabilityMandates\":\"Solar,"
            + " RWH\"},\"deliverySchedule\":[{\"stageName\":\"MEP Space Plan & DBR / Sanction"
            + " Drawings\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-02-01\"},"
            + "{\"stageName\":null,\"startDate\":null,\"endDate\":null}],\"clientInformation\":{\"clientName\":\"Acme\",\"clientCompany\":\"Acme"
            + " Co\",\"contacts\":[{\"designation\":\"Project"
            + " Owner\",\"isDefault\":true},{\"designation\":\"Project"
            + " Head\",\"isDefault\":true},{\"designation\":\"Project"
            + " Coordinator\",\"isDefault\":true},{\"designation\":\"Site Lead\",\"name\":\"Jane"
            // No space in the number: contactNo permits digits plus "+" and "-" only, so the
            // "+91 9876543210" this fixture used before is now correctly a 400.
            + " Doe\",\"mailId\":\"jane@acme.com\",\"contactNo\":\"+919876543210\",\"isDefault\":false}]},"
            // Blank contactNo sent as "" (not null) — regression check: @Pattern must accept an
            // empty string on an optional field, matching how the frontend leaves it unfilled.
            + "\"architectTeam\":{\"architectureFirm\":\"ArchCo\",\"contacts\":[{\"designation\":\"Lead"
            + " Architect\",\"name\":\"John"
            + " Roe\",\"mailId\":\"john@archco.com\",\"contactNo\":\"\",\"isDefault\":false}]},"
            + "\"structureConsultantTeam\":{\"structuralConsultancy\":\"StructCo\",\"contacts\":[]}}"
            + "}";

    JsonNode saved =
        dataOf(
            perform(put("/projects/" + projectId + "/technical-master").content(firstSavePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.remarks").value("Initial remarks"))
                .andExpect(
                    jsonPath("$.data.did.designIntentBrief.lockedDesignIntent")
                        .value("Locked intent text"))
                // A prototype rating level must survive the save; before V27 the
                // option table only held the generic bodies and this was rejected.
                .andExpect(
                    jsonPath("$.data.did.designIntentBrief.greenRatingTarget")
                        .value("IGBC GOLD TARGET"))
                .andExpect(jsonPath("$.data.did.deliverySchedule.length()").value(1))
                .andExpect(jsonPath("$.data.did.clientInformation.contacts.length()").value(4))
                .andReturn());
    long projectOwnerContactId = -1;
    for (JsonNode c : saved.path("did").path("clientInformation").path("contacts")) {
      if ("Project Owner".equals(c.path("designation").asText())) {
        projectOwnerContactId = c.path("id").asLong();
      }
    }
    assertThat(projectOwnerContactId).isPositive();

    // Reload: persisted values come back on both the DID GET and the base Technical Master GET.
    perform(get("/projects/" + projectId + "/technical-master/did"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.exists").value(true))
        .andExpect(jsonPath("$.data.deliverySchedule.length()").value(1))
        .andExpect(
            jsonPath("$.data.deliverySchedule[0].stageName")
                .value("MEP Space Plan & DBR / Sanction Drawings"))
        .andExpect(jsonPath("$.data.architectTeam.architectureFirm").value("ArchCo"));

    // Same underlying table as ONEMEP-15's delivery schedule — the saved stage shows up there too,
    // start/end included (ONEMEP-31: Project Overview's Delivery Schedule card reads these).
    perform(get("/projects/" + projectId + "/technical-master"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.remarks").value("Initial remarks"))
        .andExpect(
            jsonPath("$.data.deliverySchedule[*].milestone")
                .value(Matchers.hasItem("MEP Space Plan & DBR / Sanction Drawings")))
        .andExpect(jsonPath("$.data.deliverySchedule[0].start").value("2026-01-01"))
        .andExpect(jsonPath("$.data.deliverySchedule[0].end").value("2026-02-01"));

    // Project Overview surfaces the exact same start/end (this is what the Overview screen's
    // Delivery Schedule card renders — it was showing "—" before start/end were added to the DTO).
    perform(get("/projects/" + projectId + "/overview"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.deliverySchedule[*].milestone")
                .value(Matchers.hasItem("MEP Space Plan & DBR / Sanction Drawings")))
        .andExpect(jsonPath("$.data.deliverySchedule[0].start").value("2026-01-01"))
        .andExpect(jsonPath("$.data.deliverySchedule[0].end").value("2026-02-01"));

    // ── Atomicity: a DID-only validation failure rolls back the Technical Master side too ────
    String failingPayload =
        "{\"remarks\":\"SHOULD_NOT_PERSIST\",\"values\":{},"
            + "\"did\":{\"designIntentBrief\":{\"lockedDesignIntent\":\"Still valid\"},"
            + "\"deliverySchedule\":[{\"stageName\":\"Bad Stage\",\"startDate\":\"2026-05-10\","
            + "\"endDate\":\"2026-05-01\"}],"
            + "\"clientInformation\":{\"contacts\":[]},"
            + "\"architectTeam\":{\"contacts\":[]},"
            + "\"structureConsultantTeam\":{\"contacts\":[]}}"
            + "}";
    perform(put("/projects/" + projectId + "/technical-master").content(failingPayload))
        .andExpect(status().isBadRequest());

    // Nothing partially applied: remarks is still the value from the earlier successful save.
    perform(get("/projects/" + projectId + "/technical-master"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.remarks").value("Initial remarks"));

    // Dropping a default contact row (omitting its id) is rejected, not silently deleted.
    String removeDefaultPayload =
        "{\"values\":{},"
            + "\"did\":{\"designIntentBrief\":{\"lockedDesignIntent\":\"Locked intent text\"},"
            + "\"deliverySchedule\":[],"
            + "\"clientInformation\":{\"clientName\":\"Acme\",\"clientCompany\":\"Acme Co\","
            + "\"contacts\":[]},"
            + "\"architectTeam\":{\"contacts\":[]},"
            + "\"structureConsultantTeam\":{\"contacts\":[]}}"
            + "}";
    perform(put("/projects/" + projectId + "/technical-master").content(removeDefaultPayload))
        .andExpect(status().isBadRequest());
  }

  private String withDid(String valuesOnlyJson) {
    // valuesOnlyJson looks like {"values":{...}} — splice MINIMAL_DID in as a sibling key.
    String body = valuesOnlyJson.trim();
    return body.substring(0, body.length() - 1) + "," + MINIMAL_DID + "}";
  }

  private ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
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
