package com.netlink.onemep_feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * Bulk Design import end to end (ONEMEP-35).
 *
 * <p>Deliberately <b>not</b> {@code @Transactional}. The importer drives its own transactions — one
 * per row, which is what makes partial success possible — and bytes are written outside all of
 * them. A test transaction would make every step join it, so a "partially committed" batch would be
 * indistinguishable from a fully rolled-back one and the test would be asserting nothing.
 *
 * <p>Each test therefore works against freshly created data rather than relying on rollback, and
 * every Design needs a distinct Zone: the Design Number rule is per-Project, so two Designs sharing
 * every segment collide whatever their Titles say.
 *
 * <p>Submission is asynchronous, so the assertions poll the status endpoint rather than reading
 * straight after POST. That is what a caller does, and it exercises the 202-then-poll contract
 * instead of quietly bypassing it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
class DesignImportIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16").withInitScript("testcontainers-init.sql");

  @TempDir static Path storageRoot;

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
    registry.add("storage.provider", () -> "local");
    registry.add("storage.local.root", () -> storageRoot.toAbsolutePath().toString());
  }

  private static final Duration IMPORT_TIMEOUT = Duration.ofSeconds(30);

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DataSource dataSource;

  private static final java.util.concurrent.atomic.AtomicInteger PROJECT_SEQ =
      new java.util.concurrent.atomic.AtomicInteger(40000);

  private JdbcTemplate jdbc;
  private long projectId;
  private String projectNumber;

  /**
   * A fresh Project per test. Both duplicate rules are scoped to the Project, so without this one
   * test's Designs would count as another's duplicates and the tests would depend on their order.
   */
  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
    projectNumber = String.valueOf(PROJECT_SEQ.incrementAndGet());
    projectId = createProject(projectNumber);
  }

  /** The number the importer will generate for a row with these segments. */
  private String designNumber(String zone) {
    return "ONEMEP-" + projectNumber + "-" + zone + "-M-SCH-CHW-00-DD";
  }

  // ── partial success ───────────────────────────────────────────────────────

  /**
   * The headline requirement: valid rows import, invalid rows are reported, neither blocks the
   * other.
   */
  @Test
  void aFileWithGoodAndBadRows_importsTheGoodOnesAndReportsTheRest() throws Exception {
    String csv =
        header()
            + row("Z01", "M", "SCH", "CHW", "00", "DD", "Chilled water schematic")
            + row("Z02", "M", "SCH", "CHW", "00", "DD", "Condenser water schematic")
            // no Discipline
            + row("Z03", "", "SCH", "CHW", "00", "DD", "Missing discipline")
            // Subject that is not in the catalogue
            + row("Z04", "M", "SCH", "NOPE", "00", "DD", "Unknown subject")
            + row("Z05", "M", "SCH", "CHW", "00", "DD", "Riser layout");

    JsonNode batch = runImport(csv("designs.csv", csv));

    assertThat(batch.path("summary").asText())
        .isEqualTo("3 of 5 Designs imported. 2 rows require correction.");
    assertThat(batch.path("status").asText()).isEqualTo("COMPLETED_WITH_ERRORS");
    assertThat(batch.path("statusLabel").asText()).isEqualTo("Completed with errors");
    assertThat(batch.path("importedRows").asInt()).isEqualTo(3);
    assertThat(batch.path("failedRows").asInt()).isEqualTo(2);

    JsonNode file = batch.path("files").get(0);
    assertThat(file.path("status").asText()).isEqualTo("COMPLETED_WITH_ERRORS");
    assertThat(messagesOf(file))
        .containsExactly(
            "Row 4 — Discipline is required.", "Row 5 — Subject 'NOPE' is not configured.");

    assertThat(designTitles())
        .containsExactlyInAnyOrder(
            "Chilled water schematic", "Condenser water schematic", "Riser layout");
  }

  @Test
  void everyImportedDesign_isMarkedAsComingFromTheImporter() throws Exception {
    runImport(
        csv(
            "designs.csv",
            header() + row("Z01", "M", "SCH", "CHW", "00", "DD", "Imported design")));

    assertThat(
            jdbc.queryForList(
                "SELECT source FROM onemep_dev.design WHERE project_id = ?",
                String.class,
                projectId))
        .containsExactly("IMPORT");
  }

  @Test
  void everyImportedDesign_opensItsActivityTrail() throws Exception {
    runImport(
        csv("designs.csv", header() + row("Z01", "M", "SCH", "CHW", "00", "DD", "Audited design")));

    List<String> trail =
        jdbc.queryForList(
            """
            SELECT a.action || '|' || a.detail
            FROM onemep_dev.design_activity_log a
            JOIN onemep_dev.design d ON d.id = a.design_id
            WHERE d.project_id = ?
            """,
            String.class,
            projectId);

    assertThat(trail).containsExactly("DESIGN_IMPORTED|Imported from 'designs.csv' (row 2)");
  }

  // ── duplicate rule one: Design Number ─────────────────────────────────────

  /** Same number, different Title — rejected. The two rules are not a composite key. */
  @Test
  void aRowRepeatingAnExistingDesignNumber_isRejectedEvenThoughItsTitleIsDifferent()
      throws Exception {
    createDesign("Z01", "Original title");

    JsonNode batch =
        runImport(
            csv(
                "designs.csv",
                header()
                    + row("Z01", "M", "SCH", "CHW", "00", "DD", "A completely different title")));

    assertThat(batch.path("importedRows").asInt()).isZero();
    assertThat(messagesOf(batch.path("files").get(0)))
        .containsExactly(
            "Row 2 — Design Number '" + designNumber("Z01") + "' already exists in this Project.");
  }

  // ── duplicate rule two: Title ─────────────────────────────────────────────

  /** Different number, same Title — also rejected. */
  @Test
  void aRowRepeatingAnExistingTitle_isRejectedEvenThoughItsDesignNumberIsDifferent()
      throws Exception {
    createDesign("Z01", "Chilled water schematic");

    JsonNode batch =
        runImport(
            csv(
                "designs.csv",
                header() + row("Z99", "M", "SCH", "CHW", "00", "DD", "Chilled water schematic")));

    assertThat(batch.path("importedRows").asInt()).isZero();
    assertThat(messagesOf(batch.path("files").get(0)))
        .containsExactly("Row 2 — A Design with this Title already exists in this Project.");
  }

  /** Different in both — the only combination that imports. */
  @Test
  void aRowDifferingInBothNumberAndTitle_imports() throws Exception {
    createDesign("Z01", "Chilled water schematic");

    JsonNode batch =
        runImport(
            csv(
                "designs.csv",
                header() + row("Z99", "M", "SCH", "CHW", "00", "DD", "Riser layout")));

    assertThat(batch.path("importedRows").asInt()).isEqualTo(1);
    assertThat(batch.path("status").asText()).isEqualTo("IMPORTED");
  }

  /** A row breaking both rules is told about both, not just the first one checked. */
  @Test
  void aRowRepeatingBothNumberAndTitle_reportsBothRulesSeparately() throws Exception {
    createDesign("Z01", "Chilled water schematic");

    JsonNode batch =
        runImport(
            csv(
                "designs.csv",
                header() + row("Z01", "M", "SCH", "CHW", "00", "DD", "Chilled water schematic")));

    assertThat(messagesOf(batch.path("files").get(0)))
        .containsExactly(
            "Row 2 — Design Number '" + designNumber("Z01") + "' already exists in this Project.",
            "Row 2 — A Design with this Title already exists in this Project.");
  }

  // ── duplicates inside one batch ───────────────────────────────────────────

  @Test
  void twoRowsOfOneFileSharingADesignNumber_nameBothRows() throws Exception {
    String csv =
        header()
            + row("Z01", "M", "SCH", "CHW", "00", "DD", "First title")
            + row("Z01", "M", "SCH", "CHW", "00", "DD", "Second title");

    JsonNode batch = runImport(csv("designs.csv", csv));

    assertThat(batch.path("importedRows").asInt()).isEqualTo(1);
    assertThat(messagesOf(batch.path("files").get(0)))
        .containsExactly("Rows 2 and 3 contain the same Design Number.");
  }

  @Test
  void twoRowsOfOneFileSharingATitle_nameBothRows() throws Exception {
    String csv =
        header()
            + row("Z01", "M", "SCH", "CHW", "00", "DD", "Shared title")
            + row("Z02", "M", "SCH", "CHW", "00", "DD", "Shared title");

    JsonNode batch = runImport(csv("designs.csv", csv));

    assertThat(messagesOf(batch.path("files").get(0)))
        .containsExactly("Rows 2 and 3 contain the same Title.");
  }

  /** Two files uploaded together are one submission, so a duplicate across them is a duplicate. */
  @Test
  void twoFilesInOneBatchSharingADesignNumber_areCaughtAcrossFiles() throws Exception {
    JsonNode batch =
        runImport(
            csv(
                "zone-a.csv",
                header() + row("Z01", "M", "SCH", "CHW", "00", "DD", "Title from the first file")),
            csv(
                "zone-b.csv",
                header()
                    + row("Z01", "M", "SCH", "CHW", "00", "DD", "Title from the second file")));

    assertThat(batch.path("importedRows").asInt()).isEqualTo(1);
    assertThat(batch.path("failedRows").asInt()).isEqualTo(1);

    assertThat(batch.path("files").get(0).path("status").asText()).isEqualTo("IMPORTED");
    assertThat(messagesOf(batch.path("files").get(1)))
        .containsExactly("Rows 2 of 'zone-a.csv' and 2 contain the same Design Number.");
  }

  // ── unreadable files ──────────────────────────────────────────────────────

  @Test
  void aCorruptWorkbook_failsOnlyItsOwnFile() throws Exception {
    JsonNode batch =
        runImport(
            csv("good.csv", header() + row("Z01", "M", "SCH", "CHW", "00", "DD", "A real design")),
            new MockMultipartFile(
                "files",
                "corrupt.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "this is definitely not a workbook".getBytes(StandardCharsets.UTF_8)));

    assertThat(batch.path("importedRows").asInt()).isEqualTo(1);
    assertThat(batch.path("status").asText()).isEqualTo("COMPLETED_WITH_ERRORS");

    assertThat(batch.path("files").get(0).path("status").asText()).isEqualTo("IMPORTED");
    JsonNode corrupt = batch.path("files").get(1);
    assertThat(corrupt.path("status").asText()).isEqualTo("FAILED");
    assertThat(corrupt.path("statusLabel").asText()).isEqualTo("Failed");
    assertThat(corrupt.path("message").asText()).contains("could not be read as an Excel workbook");

    assertThat(designTitles()).containsExactly("A real design");
  }

  @Test
  void aFileMissingARequiredColumn_failsWithThatColumnNamed() throws Exception {
    JsonNode batch =
        runImport(
            csv("no-title.csv", "Zone,Discipline,Type,Subject,Floor,Stage\nZ01,M,SCH,CHW,00,DD\n"));

    JsonNode file = batch.path("files").get(0);
    assertThat(file.path("status").asText()).isEqualTo("FAILED");
    assertThat(file.path("message").asText())
        .isEqualTo("The file is missing required column Title.");
    assertThat(batch.path("status").asText()).isEqualTo("FAILED");
  }

  /**
   * A correct template with nothing filled in is not a failure — and the batch must not contradict
   * the file it contains by calling it failed while the file reads as imported.
   */
  @Test
  void aValidFileWithNoDataRows_finishesCleanlyRatherThanFailing() throws Exception {
    JsonNode batch = runImport(csv("template.csv", header()));

    assertThat(batch.path("status").asText()).isEqualTo("IMPORTED");
    assertThat(batch.path("files").get(0).path("status").asText()).isEqualTo("IMPORTED");
    assertThat(batch.path("files").get(0).path("message").asText())
        .isEqualTo("No Design rows were found.");
  }

  // ── xlsx ──────────────────────────────────────────────────────────────────

  @Test
  void anXlsxWorkbook_importsTheSameWayACsvDoes() throws Exception {
    MockMultipartFile workbook =
        xlsx(
            "designs.xlsx",
            List.of("Zone", "Discipline", "Type", "Subject", "Floor", "Stage", "Title"),
            List.of("Z01", "M", "SCH", "CHW", "00", "DD", "Schematic from a workbook"),
            List.of("Z02", "M", "SCH", "CHW", "00", "DD", "Layout from a workbook"));

    JsonNode batch = runImport(workbook);

    assertThat(batch.path("status").asText()).isEqualTo("IMPORTED");
    assertThat(batch.path("summary").asText()).isEqualTo("2 Designs imported.");
    assertThat(designTitles())
        .containsExactlyInAnyOrder("Schematic from a workbook", "Layout from a workbook");
  }

  // ── the sample files shipped with the Postman collection ──────────────────

  /**
   * The Postman collection tells the tester exactly what these files should produce. That makes the
   * wording documentation, and documentation drifts — so the shipped files are run through the real
   * importer here and the documented outcome is asserted literally.
   */
  @Test
  void thePostmanErrorSample_producesTheOutcomeTheCollectionDocuments() throws Exception {
    JsonNode batch = runImport(fromDisk("postman/design-import-with-errors.csv"));

    assertThat(batch.path("summary").asText())
        .isEqualTo("2 of 8 Designs imported. 6 rows require correction.");
    assertThat(messagesOf(batch.path("files").get(0)))
        .containsExactly(
            "Row 3 — Discipline is required.",
            "Row 4 — Subject 'XYZ' is not configured.",
            "Row 5 — Title must contain at least one letter and cannot consist only of numbers,"
                + " spaces, or special characters.",
            "Rows 2 and 6 contain the same Design Number.",
            "Rows 2 and 7 contain the same Title.",
            "Row 8 — Work Progress 'Nearly done' is not valid. Use one of: Not Started, In"
                + " Progress, Issued, Completed.");
  }

  @Test
  void thePostmanCrossFileSamples_collideAcrossTheTwoFilesAsDocumented() throws Exception {
    JsonNode batch =
        runImport(
            fromDisk("postman/design-import-crossfile-a.csv"),
            fromDisk("postman/design-import-crossfile-b.csv"));

    assertThat(batch.path("summary").asText())
        .isEqualTo("3 of 4 Designs imported. 1 row requires correction.");
    assertThat(batch.path("files").get(0).path("status").asText()).isEqualTo("IMPORTED");
    assertThat(messagesOf(batch.path("files").get(1)))
        .containsExactly(
            "Rows 2 of 'design-import-crossfile-a.csv' and 2 contain the same Design Number.");
  }

  /**
   * The companion file the corrupt-workbook scenario ships with has to keep importing cleanly even
   * after the rest of the collection has run against the same Project — otherwise the scenario
   * silently stops demonstrating that an unreadable file fails only itself.
   */
  @Test
  void thePostmanCorruptCompanionSample_importsBesideAnUnreadableFile() throws Exception {
    JsonNode batch =
        runImport(
            new MockMultipartFile(
                "files",
                "corrupt.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "this is definitely not a workbook".getBytes(StandardCharsets.UTF_8)),
            fromDisk("postman/design-import-alongside-corrupt.csv"));

    assertThat(batch.path("files").get(0).path("status").asText()).isEqualTo("FAILED");
    assertThat(batch.path("files").get(1).path("status").asText()).isEqualTo("IMPORTED");
    assertThat(batch.path("files").get(1).path("message").asText())
        .isEqualTo("2 Designs imported.");
  }

  /** The clean sample must stay clean, or the collection's happy path stops being happy. */
  @Test
  void thePostmanHappySample_importsEveryRow() throws Exception {
    JsonNode batch = runImport(fromDisk("postman/design-import-sample.csv"));

    assertThat(batch.path("status").asText()).isEqualTo("IMPORTED");
    assertThat(batch.path("summary").asText()).isEqualTo("5 Designs imported.");
  }

  // ── submission-time rejections ────────────────────────────────────────────

  @Test
  void submittingAnUnsupportedFileType_isRejectedImmediately() throws Exception {
    perform(
            uploadBuilder(
                new MockMultipartFile(
                    "files",
                    "notes.docx",
                    "application/msword",
                    "x".getBytes(StandardCharsets.UTF_8))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void submittingNoFiles_isRejectedImmediately() throws Exception {
    perform(uploadBuilder(new MockMultipartFile("files", "empty.csv", "text/csv", new byte[0])))
        .andExpect(status().isBadRequest());
  }

  @Test
  void fetchingAnUnknownBatch_is404() throws Exception {
    perform(get("/design-imports/999999")).andExpect(status().isNotFound());
  }

  // ── driving the async pipeline ────────────────────────────────────────────

  /** Submits a batch and polls until it reaches a terminal state, as a real caller would. */
  private JsonNode runImport(MockMultipartFile... files) throws Exception {
    MvcResult accepted = perform(uploadBuilder(files)).andExpect(status().isAccepted()).andReturn();

    long batchId = dataOf(accepted).path("batchId").asLong();
    assertThat(batchId).isPositive();

    Instant deadline = Instant.now().plus(IMPORT_TIMEOUT);
    JsonNode status;
    do {
      status = dataOf(perform(get("/design-imports/" + batchId)).andReturn());
      if (isTerminal(status.path("status").asText())) {
        return status;
      }
      Thread.sleep(100);
    } while (Instant.now().isBefore(deadline));

    throw new AssertionError(
        "Import batch " + batchId + " did not finish within " + IMPORT_TIMEOUT + ": " + status);
  }

  private static boolean isTerminal(String status) {
    return List.of("IMPORTED", "COMPLETED_WITH_ERRORS", "FAILED").contains(status);
  }

  private static List<String> messagesOf(JsonNode file) {
    List<String> messages = new ArrayList<>();
    file.path("errors").forEach(error -> messages.add(error.path("message").asText()));
    return messages;
  }

  // ── fixtures ──────────────────────────────────────────────────────────────

  private static String header() {
    return "Zone,Discipline,Type,Subject,Floor,Stage,Title\n";
  }

  private static String row(String... cells) {
    return String.join(",", cells) + "\n";
  }

  private static MockMultipartFile csv(String filename, String content) {
    return new MockMultipartFile(
        "files", filename, "text/csv", content.getBytes(StandardCharsets.UTF_8));
  }

  /** Reads a real file from the repository, relative to the module root the build runs from. */
  private static MockMultipartFile fromDisk(String relativePath) throws IOException {
    Path file = Path.of(relativePath);
    assertThat(file).as("sample file shipped with the Postman collection").exists();
    return new MockMultipartFile(
        "files", file.getFileName().toString(), "text/csv", java.nio.file.Files.readAllBytes(file));
  }

  @SafeVarargs
  private static MockMultipartFile xlsx(String filename, List<String>... rows) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Designs");
      for (int r = 0; r < rows.length; r++) {
        Row row = sheet.createRow(r);
        List<String> cells = rows[r];
        for (int c = 0; c < cells.size(); c++) {
          row.createCell(c).setCellValue(cells.get(c));
        }
      }
      wb.write(bytes);
    }
    return new MockMultipartFile(
        "files",
        filename,
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        bytes.toByteArray());
  }

  private MockHttpServletRequestBuilder uploadBuilder(MockMultipartFile... files) {
    var builder = multipart("/projects/" + projectId + "/design-imports");
    for (MockMultipartFile file : files) {
      builder.file(file);
    }
    return builder;
  }

  private List<String> designTitles() {
    return jdbc.queryForList(
        "SELECT title FROM onemep_dev.design WHERE project_id = ?", String.class, projectId);
  }

  /** {@code project_number} is unique service-wide, so each test's Project gets its own. */
  private long createProject(String number) {
    Long categoryId =
        jdbc.queryForObject(
            "SELECT id FROM onemep_dev.category_master ORDER BY id LIMIT 1", Long.class);
    jdbc.update(
        """
        INSERT INTO onemep_dev.project_master
            (project_number, name, category_id, lifecycle_status, priority, is_active, created_date)
        VALUES (?, 'Grandview Hotel Expansion', ?, 'ACTIVE', 'MEDIUM', TRUE, NOW())
        """,
        number,
        categoryId);
    return jdbc.queryForObject(
        "SELECT id FROM onemep_dev.project_master WHERE project_number = ?", Long.class, number);
  }

  /**
   * An existing Design, created through the ordinary Add Design route the importer must agree with.
   */
  private void createDesign(String zone, String title) throws Exception {
    perform(
            post("/projects/" + projectId + "/designs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"zoneCode":"%s","disciplineId":%d,"typeId":%d,"subjectId":%d,
                     "floorId":%d,"stageId":%d,"title":"%s"}
                    """
                        .formatted(
                            zone,
                            lookupId("DISCIPLINE", "M"),
                            lookupId("DESIGN_TYPE", "SCH"),
                            lookupId("SUBJECT", "CHW"),
                            lookupId("FLOOR", "00"),
                            lookupId("STAGE", "DD"),
                            title)))
        .andExpect(status().isCreated());
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

  private JsonNode dataOf(MvcResult result) throws IOException {
    return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
  }

  private ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
    if (!(builder
        instanceof
        org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder)) {
      builder.contentType(MediaType.APPLICATION_JSON);
    }
    return mockMvc.perform(builder.with(jwt().jwt(j -> j.subject("1"))));
  }
}
