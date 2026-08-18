package com.netlink.onemep_feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

/**
 * Design Register against a real Postgres (ONEMEP-35/36/37).
 *
 * <p>The two rules the tickets insist the backend enforce on its own — locked Design Number
 * segments and the compound duplicate rule — are proven here at the schema level, not just through
 * the service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Tag("integration")
class DesignRegisterIT {

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

  private JdbcTemplate jdbc;
  private long projectId;
  private long mechanical;
  private long electrical;
  private long schematic;
  private long plan;
  private long chilledWater;
  private long ground;
  private long detailedDesign;

  @BeforeEach
  void setUp() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    mechanical = lookupId("DISCIPLINE", "M");
    electrical = lookupId("DISCIPLINE", "E");
    schematic = lookupId("DESIGN_TYPE", "SCH");
    plan = lookupId("DESIGN_TYPE", "PLN");
    chilledWater = lookupId("SUBJECT", "CHW");
    ground = lookupId("FLOOR", "00");
    detailedDesign = lookupId("STAGE", "DD");
    projectId = createProject();
  }

  // ── Design Number generation ──────────────────────────────────────────────

  @Test
  void create_generatesTheNumberFromSegmentCodes() throws Exception {
    perform(
            post("/projects/" + projectId + "/designs")
                .content(
                    body("Z01", mechanical, schematic, chilledWater, "Chilled water schematic")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.designNumber").value("ONEMEP-40012-Z01-M-SCH-CHW-00-DD"))
        .andExpect(jsonPath("$.data.workProgress").value("NOT_STARTED"))
        .andExpect(jsonPath("$.data.status").value("DRAFT"));
  }

  @Test
  void create_withoutAZone_usesXx() throws Exception {
    perform(
            post("/projects/" + projectId + "/designs")
                .content(body(null, mechanical, schematic, chilledWater, "No zone here")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.designNumber").value("ONEMEP-40012-XX-M-SCH-CHW-00-DD"))
        .andExpect(jsonPath("$.data.zoneCode").value("XX"));
  }

  @Test
  void numberPreview_doesNotCreateAnything() throws Exception {
    perform(
            post("/projects/" + projectId + "/designs/number-preview")
                .content(body("Z01", mechanical, schematic, chilledWater, "Preview only")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.designNumber").value("ONEMEP-40012-Z01-M-SCH-CHW-00-DD"));

    assertThat(designCount()).isZero();
  }

  // ── uniqueness ────────────────────────────────────────────────────────────

  /**
   * Business ruling (superseding ONEMEP-36's worked example): the Design Number is unique on its
   * own, so a differing Title does not make a second Design with the same number acceptable.
   */
  @Test
  void create_sameNumberDifferentTitle_isRejectedAsADuplicateNumber() throws Exception {
    perform(
            post("/projects/" + projectId + "/designs")
                .content(
                    body("Z01", mechanical, schematic, chilledWater, "Chilled water schematic")))
        .andExpect(status().isCreated());

    perform(
            post("/projects/" + projectId + "/designs")
                .content(
                    body(
                        "Z01",
                        mechanical,
                        schematic,
                        chilledWater,
                        "Chilled water schematic — roof")))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.error.message")
                .value(
                    "Design Number 'ONEMEP-40012-Z01-M-SCH-CHW-00-DD' already exists in this"
                        + " Project."));

    assertThat(designCount()).isEqualTo(1);
  }

  /**
   * A rejected payload has to say WHICH field is wrong.
   *
   * <p>This endpoint mixes a constrained {@code @PathVariable} with a validated body, so Spring
   * raises HandlerMethodValidationException rather than MethodArgumentNotValidException — and that
   * handler used to answer a bare "Invalid request parameters." with an empty details array,
   * discarding every message the DTO defines. A caller integrating against it learned only that
   * something was wrong. Most endpoints in this service have that same shape, so the gap was wide.
   */
  @Test
  void create_withMissingSegments_namesEveryFieldThatIsWrong() throws Exception {
    perform(
            post("/projects/" + projectId + "/designs")
                .content("{\"title\":\"test test\",\"zoneCode\":\"Z09\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("Discipline is required."))
        // Order is asserted, not just membership: the handler sorts precisely so the one message
        // a user sees cannot vary between identical requests.
        .andExpect(
            jsonPath("$.error.details")
                .value(
                    org.hamcrest.Matchers.contains(
                        "Discipline is required.",
                        "Floor is required.",
                        "Stage is required.",
                        "Subject is required.",
                        "Type is required.")));
  }

  /** The exact payload a frontend sent while integrating: segment CODES instead of ids. */
  @Test
  void create_withSegmentCodesInsteadOfIds_explainsWhatIsMissing() throws Exception {
    perform(
            post("/projects/" + projectId + "/designs")
                .content(
                    """
                    {"title":"test test","zone":"ZONE","discipline":"M","type":"PLN",
                     "subject":"CHW","floor":"00","stage":"SD","progress":"In progress"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.details", org.hamcrest.Matchers.hasSize(5)))
        .andExpect(
            jsonPath("$.error.details")
                .value(org.hamcrest.Matchers.hasItem("Discipline is required.")));
  }

  /** The mirror rule: a Title already in use is a duplicate even under a different number. */
  @Test
  void create_sameTitleDifferentDiscipline_isRejectedAsADuplicateTitle() throws Exception {
    perform(
            post("/projects/" + projectId + "/designs")
                .content(body("Z01", mechanical, schematic, chilledWater, "Plant room layout")))
        .andExpect(status().isCreated());

    // A different Discipline yields a different Design Number, but the Title collides.
    perform(
            post("/projects/" + projectId + "/designs")
                .content(body("Z01", electrical, schematic, chilledWater, "Plant room layout")))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.error.message")
                .value("A Design with this Title already exists in this Project."));

    assertThat(designCount()).isEqualTo(1);
  }

  /** Only a row differing in both is acceptable. */
  @Test
  void create_differentNumberAndDifferentTitle_isAllowed() throws Exception {
    perform(
            post("/projects/" + projectId + "/designs")
                .content(body("Z01", mechanical, schematic, chilledWater, "Mechanical schematic")))
        .andExpect(status().isCreated());
    perform(
            post("/projects/" + projectId + "/designs")
                .content(body("Z01", electrical, schematic, chilledWater, "Electrical schematic")))
        .andExpect(status().isCreated());

    assertThat(designCount()).isEqualTo(2);
  }

  @Test
  void create_identicalSegmentsAndTitle_isRejected() throws Exception {
    perform(
            post("/projects/" + projectId + "/designs")
                .content(
                    body("Z01", mechanical, schematic, chilledWater, "Chilled water schematic")))
        .andExpect(status().isCreated());

    perform(
            post("/projects/" + projectId + "/designs")
                .content(
                    body("Z01", mechanical, schematic, chilledWater, "Chilled water schematic")))
        .andExpect(status().isConflict())
        // Both rules are broken; the Design Number is checked first, so that is what is reported.
        .andExpect(
            jsonPath("$.error.message")
                .value(
                    "Design Number 'ONEMEP-40012-Z01-M-SCH-CHW-00-DD' already exists in this"
                        + " Project."));
  }

  @Test
  void create_titleDifferingOnlyBySurroundingSpaceOrCase_isStillADuplicate() throws Exception {
    perform(
            post("/projects/" + projectId + "/designs")
                .content(body("Z01", mechanical, schematic, chilledWater, "Plant room layout")))
        .andExpect(status().isCreated());

    perform(
            post("/projects/" + projectId + "/designs")
                .content(body("Z01", mechanical, schematic, chilledWater, "  Plant Room Layout  ")))
        .andExpect(status().isConflict());
  }

  /** The service check can race; the constraint cannot. */
  @Test
  void duplicateDesignNumber_isRejectedBySchemaNotOnlyByTheService() throws Exception {
    long id = createDesign("Z01", mechanical, schematic, chilledWater, "Chilled water schematic");
    assertThat(id).isPositive();
    // Same number, deliberately different title — still a duplicate.

    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO onemep_dev.design
                        (project_id, zone_code, discipline_id, type_id, subject_id, floor_id,
                         stage_id, design_number, title, title_normalized, work_progress, status,
                         version, created_date)
                    VALUES (?, 'Z01', ?, ?, ?, ?, ?, 'ONEMEP-40012-Z01-M-SCH-CHW-00-DD',
                            'A different title entirely', 'a different title entirely',
                            'NOT_STARTED', 'DRAFT', 0, NOW())
                    """,
                    projectId,
                    mechanical,
                    schematic,
                    chilledWater,
                    ground,
                    detailedDesign))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // ── segment type pinning ──────────────────────────────────────────────────

  @Test
  void create_rejectsASubjectIdOfferedAsADiscipline() throws Exception {
    perform(
            post("/projects/" + projectId + "/designs")
                .content(body("Z01", chilledWater, schematic, chilledWater, "Wrong catalogue")))
        .andExpect(status().isNotFound());
  }

  @Test
  void disciplineColumn_cannotHoldASubjectValue_atTheSchemaLevel() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO onemep_dev.design
                        (project_id, zone_code, discipline_id, type_id, subject_id, floor_id,
                         stage_id, design_number, title, title_normalized, work_progress, status,
                         version, created_date)
                    VALUES (?, 'Z01', ?, ?, ?, ?, ?, 'ONEMEP-40012-Z01-X-SCH-CHW-00-DD',
                            'Bad', 'bad', 'NOT_STARTED', 'DRAFT', 0, NOW())
                    """,
                    projectId,
                    chilledWater, // a SUBJECT id in the discipline column
                    schematic,
                    chilledWater,
                    ground,
                    detailedDesign))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // ── edit ──────────────────────────────────────────────────────────────────

  @Test
  void update_changesTitleWithoutTouchingTheDesignNumber() throws Exception {
    long id = createDesign("Z01", mechanical, schematic, chilledWater, "Chilled water schematic");

    perform(
            put("/designs/" + id)
                .content(
                    """
                    {"title":"Chilled water schematic — roof","sheetSize":"A1","scale":"1:100",
                     "preparedBy":"J. Lee","workProgress":"IN_PROGRESS"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.designNumber").value("ONEMEP-40012-Z01-M-SCH-CHW-00-DD"))
        .andExpect(jsonPath("$.data.title").value("Chilled water schematic — roof"))
        .andExpect(jsonPath("$.data.workProgress").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.data.discipline.code").value("M"));
  }

  /**
   * The update payload has no segment fields at all, so a tampered request cannot express a change.
   * This asserts the extra belt: the mapping marks them non-updatable, so even a service bug could
   * not rewrite them.
   */
  @Test
  void update_leavesEverySegmentUntouched() throws Exception {
    long id = createDesign("Z01", mechanical, schematic, chilledWater, "Original");

    perform(put("/designs/" + id).content("{\"title\":\"Renamed\"}")).andExpect(status().isOk());

    assertThat(column(id, "discipline_id", Long.class)).isEqualTo(mechanical);
    assertThat(column(id, "type_id", Long.class)).isEqualTo(schematic);
    assertThat(column(id, "zone_code", String.class)).isEqualTo("Z01");
    assertThat(column(id, "design_number", String.class))
        .isEqualTo("ONEMEP-40012-Z01-M-SCH-CHW-00-DD");
  }

  @Test
  void update_keepingItsOwnTitle_isNotADuplicate() throws Exception {
    long id = createDesign("Z01", mechanical, schematic, chilledWater, "Chilled water schematic");

    perform(put("/designs/" + id).content("{\"title\":\"Chilled water schematic\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void update_toAnotherDesignsIdentity_isRejected() throws Exception {
    createDesign("Z01", mechanical, schematic, chilledWater, "First");
    // A different Discipline gives this one its own Design Number, so both can exist.
    long second = createDesign("Z01", electrical, schematic, chilledWater, "Second");

    perform(put("/designs/" + second).content("{\"title\":\"First\"}"))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.error.message")
                .value("A Design with this Title already exists in this Project."));
  }

  // ── listing ───────────────────────────────────────────────────────────────

  @Test
  void list_isScopedToItsOwnProject() throws Exception {
    createDesign("Z01", mechanical, schematic, chilledWater, "In this project");
    long otherProject = createProject("40013", "Other project");

    perform(post("/projects/" + otherProject + "/designs/list").content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));

    perform(post("/projects/" + projectId + "/designs/list").content("{}"))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].documentCount").value(0));
  }

  @Test
  void list_searchMatchesNumberSegmentsTitleAndDiscipline() throws Exception {
    createDesign("Z01", mechanical, schematic, chilledWater, "Chilled water schematic");

    for (String term :
        new String[] {"40012", "M-SCH", "CHW", "ONEMEP-40012", "chilled", "Mechanical"}) {
      perform(
              post("/projects/" + projectId + "/designs/list")
                  .content("{\"filters\":{\"search\":\"" + term + "\"}}"))
          .andExpect(jsonPath("$.data.totalElements").value(1));
    }
  }

  @Test
  void list_appliesFiltersAndSearchTogether() throws Exception {
    createDesign("Z01", mechanical, schematic, chilledWater, "Mechanical schematic");
    createDesign("Z01", electrical, plan, chilledWater, "Electrical plan");

    perform(
            post("/projects/" + projectId + "/designs/list")
                .content("{\"filters\":{\"disciplineId\":" + mechanical + "}}"))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].disciplineCode").value("M"));

    // Discipline matches but the search does not — AND, not OR.
    perform(
            post("/projects/" + projectId + "/designs/list")
                .content(
                    "{\"filters\":{\"disciplineId\":"
                        + mechanical
                        + ",\"search\":\"electrical\"}}"))
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  @Test
  void list_filtersByWorkProgress() throws Exception {
    long id = createDesign("Z01", mechanical, schematic, chilledWater, "Progressing");
    perform(
            put("/designs/" + id)
                .content("{\"title\":\"Progressing\",\"workProgress\":\"ISSUED\"}"))
        .andExpect(status().isOk());

    perform(
            post("/projects/" + projectId + "/designs/list")
                .content("{\"filters\":{\"workProgress\":\"ISSUED\"}}"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
    perform(
            post("/projects/" + projectId + "/designs/list")
                .content("{\"filters\":{\"workProgress\":\"COMPLETED\"}}"))
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private long createProject() throws Exception {
    return createProject("40012", "Grandview Hotel Expansion");
  }

  private long createProject(String number, String name) {
    Long categoryId =
        jdbc.queryForObject(
            "SELECT id FROM onemep_dev.category_master ORDER BY id LIMIT 1", Long.class);
    jdbc.update(
        """
        INSERT INTO onemep_dev.project_master
            (project_number, name, category_id, lifecycle_status, priority, is_active, created_date)
        VALUES (?, ?, ?, 'ACTIVE', 'MEDIUM', TRUE, NOW())
        """,
        number,
        name,
        categoryId);
    Long id =
        jdbc.queryForObject(
            "SELECT id FROM onemep_dev.project_master WHERE project_number = ?",
            Long.class,
            number);
    return id == null ? 0L : id;
  }

  private long createDesign(String zone, long discipline, long type, long subject, String title)
      throws Exception {
    MvcResult result =
        perform(
                post("/projects/" + projectId + "/designs")
                    .content(body(zone, discipline, type, subject, title)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .path("data")
        .path("id")
        .asLong();
  }

  private String body(String zone, long discipline, long type, long subject, String title) {
    return """
    {"zoneCode":%s,"disciplineId":%d,"typeId":%d,"subjectId":%d,"floorId":%d,"stageId":%d,
     "title":"%s"}
    """
        .formatted(
            zone == null ? "null" : "\"" + zone + "\"",
            discipline,
            type,
            subject,
            ground,
            detailedDesign,
            title);
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

  private int designCount() {
    Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM onemep_dev.design", Integer.class);
    return count == null ? 0 : count;
  }

  private <T> T column(long designId, String name, Class<T> asType) {
    return jdbc.queryForObject(
        "SELECT " + name + " FROM onemep_dev.design WHERE id = ?", asType, designId);
  }

  private ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
    return mockMvc.perform(
        builder.contentType(MediaType.APPLICATION_JSON).with(jwt().jwt(j -> j.subject("1"))));
  }
}
