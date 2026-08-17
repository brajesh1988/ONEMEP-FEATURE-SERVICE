package com.netlink.onemep_feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Checklist Master against a real Postgres (ONEMEP-32/33/34).
 *
 * <p>The applicability table is the first real consumer of the V16 composite-FK pattern, so the
 * cross-catalogue rejection is proven here with raw SQL — the service layer cannot be the only
 * thing standing between a Subject id and a Discipline column.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Tag("integration")
class ChecklistMasterIT {

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

  private JdbcTemplate jdbc;
  private long mechanical;
  private long electrical;
  private long schematic;
  private long plan;
  private long chilledWater;

  @BeforeEach
  void resolveSeededLookups() {
    jdbc = new JdbcTemplate(dataSource);
    mechanical = lookupId("DISCIPLINE", "M");
    electrical = lookupId("DISCIPLINE", "E");
    schematic = lookupId("DESIGN_TYPE", "SCH");
    plan = lookupId("DESIGN_TYPE", "PLN");
    chilledWater = lookupId("SUBJECT", "CHW");
  }

  // ── schema guarantees ─────────────────────────────────────────────────────

  @Test
  void applicability_cannotReferenceAValueFromAnotherCatalogue() throws Exception {
    long checklistId = createChecklist("FK guard", "Check something");

    // value_type says DISCIPLINE, but the id belongs to the SUBJECT catalogue. The composite
    // foreign key has no matching (id, lookup_type) row, so Postgres rejects it outright.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO onemep_dev.checklist_applicability
                        (checklist_id, segment, value_id, value_type, created_date)
                    VALUES (?, 'DISCIPLINE', ?, 'DISCIPLINE', NOW())
                    """,
                    checklistId,
                    chilledWater))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void applicability_valueTypeMustMatchItsSegment() throws Exception {
    long checklistId = createChecklist("Pair guard", "Check something");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO onemep_dev.checklist_applicability
                        (checklist_id, segment, value_id, value_type, created_date)
                    VALUES (?, 'DISCIPLINE', ?, 'SUBJECT', NOW())
                    """,
                    checklistId,
                    chilledWater))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void singleItemRecord_cannotCarryAName_atTheSchemaLevel() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO onemep_dev.checklist_master
                        (record_type, name, is_active, version, created_date)
                    VALUES ('SINGLE_ITEM', 'Not allowed', TRUE, 0, NOW())
                    """))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void checklistRecord_mustCarryAName_atTheSchemaLevel() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO onemep_dev.checklist_master
                        (record_type, name, is_active, version, created_date)
                    VALUES ('CHECKLIST', NULL, TRUE, 0, NOW())
                    """))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void checklistName_isUniqueCaseInsensitively_atTheSchemaLevel() throws Exception {
    createChecklist("Riser Checks", "Check something");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO onemep_dev.checklist_master
                        (record_type, name, is_active, version, created_date)
                    VALUES ('CHECKLIST', 'riser checks', TRUE, 0, NOW())
                    """))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void deletingAChecklist_cascadesToItemsAndApplicability() throws Exception {
    long checklistId = createChecklist("To be removed", "Check something");
    assertThat(childRows("checklist_item", checklistId)).isEqualTo(1);
    assertThat(childRows("checklist_applicability", checklistId)).isEqualTo(3);

    perform(delete("/checklists/" + checklistId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Record deleted successfully."));

    // The test shares its transaction with the service, so Hibernate's DELETEs are still pending;
    // JdbcTemplate bypasses the persistence context and would otherwise read the stale rows.
    entityManager.flush();

    assertThat(childRows("checklist_item", checklistId)).isZero();
    assertThat(childRows("checklist_applicability", checklistId)).isZero();
  }

  // ── applicability matching ────────────────────────────────────────────────

  @Test
  void matching_usesOrWithinASegmentAndAndAcrossThem() throws Exception {
    // (M or E) and (SCH) and (CHW)
    createChecklist(
        "Mechanical or electrical schematics",
        "Check something",
        list(mechanical, electrical),
        list(schematic),
        list(chilledWater));

    perform(applicableFor(mechanical, schematic, chilledWater))
        .andExpect(jsonPath("$.data.length()").value(1));
    perform(applicableFor(electrical, schematic, chilledWater))
        .andExpect(jsonPath("$.data.length()").value(1));

    // Right discipline, wrong type — the AND across segments must fail.
    perform(applicableFor(mechanical, plan, chilledWater))
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void matching_treatsAnEmptySegmentAsAWildcard() throws Exception {
    // Any discipline, SCH, CHW
    createChecklist(
        "Any discipline schematics",
        "Check something",
        list(),
        list(schematic),
        list(chilledWater));

    perform(applicableFor(mechanical, schematic, chilledWater))
        .andExpect(jsonPath("$.data.length()").value(1));
    perform(applicableFor(electrical, schematic, chilledWater))
        .andExpect(jsonPath("$.data.length()").value(1));
    perform(applicableFor(mechanical, plan, chilledWater))
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void matching_excludesInactiveRecords() throws Exception {
    long id =
        createChecklist(
            "Inactive one",
            "Check something",
            list(mechanical),
            list(schematic),
            list(chilledWater));

    perform(applicableFor(mechanical, schematic, chilledWater))
        .andExpect(jsonPath("$.data.length()").value(1));

    perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/checklists/" + id + "/status")
                .param("active", "false"))
        .andExpect(status().isOk());

    perform(applicableFor(mechanical, schematic, chilledWater))
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void applicable_whenNothingMatches_returnsTheTicketsMessage() throws Exception {
    perform(applicableFor(mechanical, schematic, chilledWater))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.message")
                .value(
                    "No checklist is configured for this Discipline, Type and Subject"
                        + " combination."));
  }

  /** {@code /checklists/applicable} must win over {@code /checklists/{id}}. */
  @Test
  void applicableRoute_doesNotCollideWithTheFetchByIdRoute() throws Exception {
    perform(applicableFor(mechanical, schematic, chilledWater)).andExpect(status().isOk());
  }

  // ── HTTP behaviour ────────────────────────────────────────────────────────

  @Test
  void create_rejectsADisciplineIdTakenFromAnotherCatalogue() throws Exception {
    // chilledWater is a SUBJECT; offering it as a Discipline must be refused by the service.
    perform(
            post("/checklists")
                .content(
                    createBody(
                        "Wrong catalogue",
                        "Check something",
                        list(chilledWater),
                        list(schematic),
                        list(chilledWater))))
        .andExpect(status().isNotFound());
  }

  @Test
  void update_cannotChangeTheRecordType() throws Exception {
    long id = createChecklist("Immutable type", "Check something");

    perform(
            put("/checklists/" + id)
                .content(
                    """
                    {"recordType":"SINGLE_ITEM","name":"Immutable type","items":["Check something"],
                     "appliesTo":{"disciplineIds":[%d],"typeIds":[%d],"subjectIds":[%d]}}
                    """
                        .formatted(mechanical, schematic, chilledWater)))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error.message").value("Record type cannot be changed after creation."));
  }

  @Test
  void update_incrementsTheVersionSoConcurrentEditsCanBeDetected() throws Exception {
    long id = createChecklist("Versioned", "Check something");
    assertThat(version(id)).isZero();

    perform(
            put("/checklists/" + id)
                .content(
                    """
                    {"name":"Versioned","items":["Check something else"],
                     "appliesTo":{"disciplineIds":[%d],"typeIds":[%d],"subjectIds":[%d]}}
                    """
                        .formatted(mechanical, schematic, chilledWater)))
        .andExpect(status().isOk());

    entityManager.flush();
    assertThat(version(id)).isEqualTo(1);
  }

  @Test
  void singleItem_listsItsItemTextAsTheEntryName() throws Exception {
    perform(
            post("/checklists")
                .content(
                    """
                    {"recordType":"SINGLE_ITEM","items":["Verify equipment clearance"],
                     "appliesTo":{"disciplineIds":[%d],"typeIds":[%d],"subjectIds":[%d]}}
                    """
                        .formatted(mechanical, schematic, chilledWater)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.message").value("Single Item created successfully."));

    perform(post("/checklists/list").content("{\"filters\":{\"search\":\"clearance\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].entryName").value("Verify equipment clearance"))
        .andExpect(jsonPath("$.data.content[0].itemCount").value(1))
        .andExpect(jsonPath("$.data.content[0].recordType").value("SINGLE_ITEM"));
  }

  @Test
  void search_matchesAnAppliesToCodeAndLabel() throws Exception {
    createChecklist("Findable by applicability", "Check something");

    perform(post("/checklists/list").content("{\"filters\":{\"search\":\"chw\"}}"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
    perform(post("/checklists/list").content("{\"filters\":{\"search\":\"chilled\"}}"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
    perform(post("/checklists/list").content("{\"filters\":{\"search\":\"schematic\"}}"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void list_showsWildcardSegmentsAsAny() throws Exception {
    createChecklist("Wildcards", "Check something", list(), list(schematic), list());

    perform(post("/checklists/list").content("{\"filters\":{\"search\":\"Wildcards\"}}"))
        .andExpect(jsonPath("$.data.content[0].appliesTo.disciplines.any").value(true))
        .andExpect(jsonPath("$.data.content[0].appliesTo.disciplines.values.length()").value(0))
        .andExpect(jsonPath("$.data.content[0].appliesTo.types.any").value(false))
        .andExpect(jsonPath("$.data.content[0].appliesTo.types.values[0].code").value("SCH"))
        .andExpect(jsonPath("$.data.content[0].appliesTo.subjects.any").value(true));
  }

  @Test
  void impact_reportsZeroUntilTheDesignRegisterExists() throws Exception {
    long id = createChecklist("Impact", "Check something");

    perform(get("/checklists/" + id + "/impact"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.entryName").value("Impact"))
        .andExpect(jsonPath("$.data.matchingDesignCount").value(0));
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private long createChecklist(String name, String item) throws Exception {
    return createChecklist(name, item, list(mechanical), list(schematic), list(chilledWater));
  }

  private long createChecklist(
      String name, String item, String disciplines, String types, String subjects)
      throws Exception {
    MvcResult result =
        perform(post("/checklists").content(createBody(name, item, disciplines, types, subjects)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .path("data")
        .path("id")
        .asLong();
  }

  private static String createBody(
      String name, String item, String disciplines, String types, String subjects) {
    return """
    {"recordType":"CHECKLIST","name":"%s","items":["%s"],
     "appliesTo":{"disciplineIds":[%s],"typeIds":[%s],"subjectIds":[%s]}}
    """
        .formatted(name, item, disciplines, types, subjects);
  }

  private static String list(long... ids) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < ids.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(ids[i]);
    }
    return sb.toString();
  }

  private MockHttpServletRequestBuilder applicableFor(long discipline, long type, long subject) {
    return get("/checklists/applicable")
        .param("disciplineId", String.valueOf(discipline))
        .param("typeId", String.valueOf(type))
        .param("subjectId", String.valueOf(subject));
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

  private int version(long checklistId) {
    Integer v =
        jdbc.queryForObject(
            "SELECT version FROM onemep_dev.checklist_master WHERE id = ?",
            Integer.class,
            checklistId);
    return v == null ? -1 : v;
  }

  private int childRows(String table, long checklistId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM onemep_dev." + table + " WHERE checklist_id = ?",
            Integer.class,
            checklistId);
    return count == null ? 0 : count;
  }

  private ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
    return mockMvc.perform(
        builder.contentType(MediaType.APPLICATION_JSON).with(jwt().jwt(j -> j.subject("1"))));
  }
}
