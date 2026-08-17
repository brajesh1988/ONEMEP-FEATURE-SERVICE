package com.netlink.onemep_feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the V16 reference-data catalogue against a real Postgres: that the migration applies and
 * seeds, that the HTTP surface serves and maintains it, and — the part that matters most — that the
 * schema constraints hold under conditions the service layer alone could not guarantee.
 *
 * <p>Transactional so each test rolls back: the container is shared across the class, and the
 * constraint tests insert rows that would otherwise skew the seed-count assertions depending on
 * execution order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Tag("integration")
class LookupCatalogueIT {

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
  @Autowired private DataSource dataSource;

  @Test
  void migration_seedsTheConfirmedCodesForEachCatalogue() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    assertThat(count(jdbc, "STAGE")).isEqualTo(6);
    assertThat(count(jdbc, "DISCIPLINE")).isEqualTo(3);
    assertThat(count(jdbc, "DESIGN_TYPE")).isEqualTo(2);
    assertThat(count(jdbc, "SUBJECT")).isEqualTo(1);

    // V18 added the two floor codes the Design tickets actually show ('00' and 'L01'); the full
    // floor scheme is still outstanding, so nothing beyond those was invented.
    assertThat(count(jdbc, "FLOOR")).isEqualTo(2);

    // ZONE stays unseeded: ONEMEP-36 describes Zone as typed rather than selected, so whether it
    // is a catalogue at all is still an open question.
    assertThat(count(jdbc, "ZONE")).isZero();
  }

  @Test
  void stageCatalogue_isOrderedAsAProgressionNotAlphabetically() throws Exception {
    perform(get("/lookups/stages"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].code").value("CON"))
        .andExpect(jsonPath("$.data[1].code").value("SD"))
        .andExpect(jsonPath("$.data[2].code").value("DD"))
        .andExpect(jsonPath("$.data[5].code").value("AB"));
  }

  @Test
  void listOptions_acceptsPluralHyphenatedAndEnumForms() throws Exception {
    perform(get("/lookups/design-types")).andExpect(status().isOk());
    perform(get("/lookups/DESIGN_TYPE")).andExpect(status().isOk());
    perform(get("/lookups/disciplines")).andExpect(status().isOk());
  }

  @Test
  void listOptions_unknownCatalogue_returns404() throws Exception {
    perform(get("/lookups/sprockets")).andExpect(status().isNotFound());
  }

  @Test
  void uniqueConstraint_isScopedPerCatalogue_soTheSameCodeMayExistInTwo() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    // 'M' already exists as a DISCIPLINE seed. The same code under FLOOR must be allowed.
    jdbc.update(
        "INSERT INTO onemep_dev.lookup_value (lookup_type, code, label, sort_order, is_active,"
            + " created_date) VALUES ('FLOOR', 'M', 'Mezzanine', 1, TRUE, NOW())");

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM onemep_dev.lookup_value WHERE code = 'M'", Integer.class))
        .isEqualTo(2);

    // ...but a second 'M' within FLOOR must not be.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO onemep_dev.lookup_value (lookup_type, code, label, sort_order,"
                        + " is_active, created_date) VALUES ('FLOOR', 'M', 'Duplicate', 2, TRUE,"
                        + " NOW())"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void checkConstraint_rejectsAnUnknownCatalogueName() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO onemep_dev.lookup_value (lookup_type, code, label, sort_order,"
                        + " is_active, created_date) VALUES ('SPROCKET', 'X', 'X', 1, TRUE,"
                        + " NOW())"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /**
   * The composite-FK enabler. A consumer pins its reference with {@code FOREIGN KEY (value_id,
   * value_type) REFERENCES lookup_value (id, lookup_type)}, which Postgres only permits because of
   * this constraint. Asserting it exists keeps a future migration from dropping it as redundant —
   * the first real consumer arrives with the design table.
   */
  @Test
  void compositeForeignKeyTarget_existsOnIdAndType() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    Integer matching =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
              ON tc.constraint_name = kcu.constraint_name
             AND tc.table_schema = kcu.table_schema
            WHERE tc.table_name = 'lookup_value'
              AND tc.table_schema = 'onemep_dev'
              AND tc.constraint_type = 'UNIQUE'
              AND tc.constraint_name = 'uq_lookup_id_type'
              AND kcu.column_name IN ('id', 'lookup_type')
            """,
            Integer.class);

    assertThat(matching).isEqualTo(2);
  }

  @Test
  void deactivatedValue_disappearsFromOptionsButRemainsFetchable() throws Exception {
    String created =
        perform(
                post("/lookups/subjects")
                    .content("{\"code\":\"hw\",\"label\":\"Hot Water\",\"sortOrder\":2}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.code").value("HW"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

    perform(get("/lookups/subjects")).andExpect(jsonPath("$.data[?(@.code=='HW')]").isNotEmpty());

    perform(patch("/lookups/entries/" + id + "/status").param("active", "false"))
        .andExpect(status().isOk());

    perform(get("/lookups/subjects")).andExpect(jsonPath("$.data[?(@.code=='HW')]").isEmpty());
    perform(get("/lookups/entries/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.active").value(false));
  }

  @Test
  void create_duplicateCodeInSameCatalogue_returns409() throws Exception {
    perform(post("/lookups/disciplines").content("{\"code\":\"m\",\"label\":\"Mechanical\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void create_rejectsACodeThatWouldCorruptAGeneratedDesignNumber() throws Exception {
    perform(post("/lookups/disciplines").content("{\"code\":\"A-B/C\",\"label\":\"Bad\"}"))
        .andExpect(status().isBadRequest());
  }

  private static int count(JdbcTemplate jdbc, String type) {
    Integer result =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM onemep_dev.lookup_value WHERE lookup_type = ?",
            Integer.class,
            type);
    return result == null ? 0 : result;
  }

  private ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
    return mockMvc.perform(
        builder.contentType(MediaType.APPLICATION_JSON).with(jwt().jwt(j -> j.subject("1"))));
  }
}
