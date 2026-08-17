package com.netlink.onemep_feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
 * Design Activity audit trail end to end (ONEMEP-43).
 *
 * <p>The properties worth proving are the negative ones: a failed operation leaves no trace, an
 * unchanged save invents nothing, and history is never rewritten when the same field moves twice.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Tag("integration")
class DesignActivityIT {

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

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DataSource dataSource;

  @PersistenceContext private EntityManager entityManager;

  private static final java.util.concurrent.atomic.AtomicInteger ZONE_SEQ =
      new java.util.concurrent.atomic.AtomicInteger();

  private JdbcTemplate jdbc;
  private long projectId;
  private long mechanical;
  private long schematic;
  private long chilledWater;
  private long ground;
  private long detailedDesign;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
    mechanical = lookupId("DISCIPLINE", "M");
    schematic = lookupId("DESIGN_TYPE", "SCH");
    chilledWater = lookupId("SUBJECT", "CHW");
    ground = lookupId("FLOOR", "00");
    detailedDesign = lookupId("STAGE", "DD");
    projectId = createProject();
  }

  @Test
  void creatingADesign_recordsEntryCreatedAsTheFirstEvent() throws Exception {
    long designId = createDesign("Chilled water schematic");

    perform(post("/designs/" + designId + "/activities/list").content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].detail").value("Entry created"))
        .andExpect(jsonPath("$.data.content[0].action").value("DESIGN_CREATED"))
        .andExpect(jsonPath("$.data.content[0].updatedBy").value("User 1"))
        .andExpect(jsonPath("$.data.content[0].updatedById").value(1));
  }

  @Test
  void editing_recordsOneEventPerChangedFieldNamingBothSides() throws Exception {
    long designId = createDesign("Original title");

    perform(
            put("/designs/" + designId)
                .content(
                    """
                    {"title":"Revised title","sheetSize":"A1","workProgress":"IN_PROGRESS"}
                    """))
        .andExpect(status().isOk());

    // Entry created + three field changes.
    perform(post("/designs/" + designId + "/activities/list").content("{}"))
        .andExpect(jsonPath("$.data.totalElements").value(4));

    assertThat(details(designId))
        .contains(
            "Title changed from 'Original title' to 'Revised title'",
            "Sheet Size changed from (empty) to 'A1'",
            "Work Progress changed from 'NOT_STARTED' to 'IN_PROGRESS'");
  }

  @Test
  void savingWithNothingChanged_recordsNoEvent() throws Exception {
    long designId = createDesign("Unchanged");

    perform(put("/designs/" + designId).content("{\"title\":\"Unchanged\"}"))
        .andExpect(status().isOk());

    perform(post("/designs/" + designId + "/activities/list").content("{}"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void aFailedOperation_leavesNoTrace() throws Exception {
    long designId = createDesign("First");
    long before = activityCount(designId);

    // A title that would duplicate another Design is rejected; nothing may be logged for it.
    createDesign("Second");
    perform(put("/designs/" + designId).content("{\"title\":\"Second\"}"))
        .andExpect(status().isConflict());

    assertThat(activityCount(designId)).isEqualTo(before);
  }

  @Test
  void changingTheSameFieldTwice_appendsRatherThanRewritingHistory() throws Exception {
    long designId = createDesign("Version one");

    perform(put("/designs/" + designId).content("{\"title\":\"Version two\"}"))
        .andExpect(status().isOk());
    perform(put("/designs/" + designId).content("{\"title\":\"Version three\"}"))
        .andExpect(status().isOk());

    // ONEMEP-43: the earlier row keeps saying what it said at the time.
    assertThat(details(designId))
        .contains(
            "Title changed from 'Version one' to 'Version two'",
            "Title changed from 'Version two' to 'Version three'");
  }

  @Test
  void activity_isNewestFirst() throws Exception {
    long designId = createDesign("Ordering");
    perform(put("/designs/" + designId).content("{\"title\":\"Ordering v2\"}"))
        .andExpect(status().isOk());

    perform(post("/designs/" + designId + "/activities/list").content("{}"))
        .andExpect(jsonPath("$.data.content[0].action").value("DESIGN_UPDATED"))
        .andExpect(jsonPath("$.data.content[1].detail").value("Entry created"));
  }

  @Test
  void activity_isScopedToItsOwnDesign() throws Exception {
    long first = createDesign("First design");
    long second = createDesign("Second design");

    perform(put("/designs/" + first).content("{\"title\":\"First design edited\"}"))
        .andExpect(status().isOk());

    perform(post("/designs/" + first + "/activities/list").content("{}"))
        .andExpect(jsonPath("$.data.totalElements").value(2));
    perform(post("/designs/" + second + "/activities/list").content("{}"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void activity_forAMissingDesign_returns404() throws Exception {
    perform(post("/designs/999999/activities/list").content("{}")).andExpect(status().isNotFound());
  }

  @Test
  void activityRows_cannotBeEditedOrDeletedThroughTheApi() throws Exception {
    long designId = createDesign("Read only");

    // There is no write route at all — not a permission check, an absent endpoint.
    perform(delete("/designs/" + designId + "/activities/1")).andExpect(status().isNotFound());
    perform(put("/designs/" + designId + "/activities/1").content("{}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void deletingADesign_takesItsTrailWithIt() throws Exception {
    long designId = createDesign("Doomed");
    assertThat(activityCount(designId)).isEqualTo(1);

    // Creation and deletion share one transaction here, which they never do in production. Clearing
    // the persistence context reproduces the real shape — a delete arriving with nothing of this
    // Design already loaded — and lets Postgres' ON DELETE CASCADE do the work.
    entityManager.flush();
    entityManager.clear();

    perform(delete("/designs/" + designId)).andExpect(status().isOk());
    entityManager.flush();

    assertThat(activityCount(designId)).isZero();
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private java.util.List<String> details(long designId) {
    return jdbc.queryForList(
        "SELECT detail FROM onemep_dev.design_activity_log WHERE design_id = ?",
        String.class,
        designId);
  }

  private int activityCount(long designId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM onemep_dev.design_activity_log WHERE design_id = ?",
            Integer.class,
            designId);
    return count == null ? 0 : count;
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
                                mechanical,
                                schematic,
                                chilledWater,
                                ground,
                                detailedDesign,
                                title)))
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
