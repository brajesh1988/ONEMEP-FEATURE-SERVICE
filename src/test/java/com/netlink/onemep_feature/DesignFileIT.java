package com.netlink.onemep_feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netlink.onemep_feature.common.storage.FileStorage;
import com.netlink.onemep_feature.common.storage.StorageKey;
import com.netlink.onemep_feature.file.service.DesignFileService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
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
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Uploaded files and version history end to end (ONEMEP-39).
 *
 * <p>Deliberately <b>not</b> {@code @Transactional}. The upload path drives its own short
 * transactions so bytes are written outside one; wrapping the test in a single transaction would
 * make every step join it, neutralise the row lock, and test something the application never does.
 * Each test therefore works against its own Design instead of relying on rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
class DesignFileIT {

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

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DataSource dataSource;
  @Autowired private FileStorage fileStorage;
  @Autowired private DesignFileService designFileService;

  private static final java.util.concurrent.atomic.AtomicInteger ZONE_SEQ =
      new java.util.concurrent.atomic.AtomicInteger();

  private JdbcTemplate jdbc;
  private long projectId;
  private long designId;

  @BeforeEach
  void setUp() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    projectId = ensureProject();
    designId = createDesign("Design " + System.nanoTime());
  }

  // ── upload ────────────────────────────────────────────────────────────────

  @Test
  void uploadingOneFile_createsALogicalFileAtR0() throws Exception {
    perform(upload(designId, pdf("riser.pdf", "first revision")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.message").value("File uploaded successfully."))
        .andExpect(jsonPath("$.data.uploaded").value(1))
        .andExpect(jsonPath("$.data.results[0].revisionLabel").value("R0"));

    perform(get("/designs/" + designId + "/files"))
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].displayName").value("riser"))
        .andExpect(jsonPath("$.data[0].currentRevisionLabel").value("R0"))
        .andExpect(jsonPath("$.data[0].versionCount").value(1))
        .andExpect(jsonPath("$.data[0].fileExtension").value("pdf"));
  }

  @Test
  void uploadingThreeFiles_createsThreeIndependentLogicalFiles() throws Exception {
    perform(upload(designId, pdf("riser.pdf", "a"), pdf("calc.docx", "b"), pdf("model.dwg", "c")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.uploaded").value(3))
        .andExpect(jsonPath("$.data.failed").value(0));

    perform(get("/designs/" + designId + "/files")).andExpect(jsonPath("$.data.length()").value(3));
  }

  /** ONEMEP-39's partial-success requirement: one bad file must not discard the good ones. */
  @Test
  void aBadFileInABatch_doesNotStopTheOthers() throws Exception {
    perform(
            upload(
                designId,
                pdf("riser.pdf", "good"),
                pdf("notes.txt", "unsupported"),
                pdf("model.dwg", "good")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.message").value("2 of 3 files uploaded successfully."))
        .andExpect(jsonPath("$.data.uploaded").value(2))
        .andExpect(jsonPath("$.data.failed").value(1))
        .andExpect(
            jsonPath("$.data.results[1].error").value("notes.txt is not a supported file type."));

    perform(get("/designs/" + designId + "/files")).andExpect(jsonPath("$.data.length()").value(2));
  }

  @Test
  void theSameFileSelectedTwiceInOneBatch_isCaughtBeforeAnythingIsWritten() throws Exception {
    perform(upload(designId, pdf("riser.pdf", "a"), pdf("riser.pdf", "b")))
        .andExpect(jsonPath("$.data.uploaded").value(1))
        .andExpect(
            jsonPath("$.data.results[1].error").value("riser.pdf has already been selected."));
  }

  @Test
  void reUploadingAnExistingNameAsANewFile_pointsAtTheNewVersionRouteInstead() throws Exception {
    perform(upload(designId, pdf("riser.pdf", "a"))).andExpect(status().isCreated());

    perform(upload(designId, pdf("riser.pdf", "b")))
        .andExpect(jsonPath("$.data.uploaded").value(0))
        .andExpect(
            jsonPath("$.data.results[0].error")
                .value(
                    "A file with this name already exists on the Design. Upload it as a new version"
                        + " instead."));
  }

  // ── versions ──────────────────────────────────────────────────────────────

  @Test
  void uploadingANewVersion_staysOneRowAndRetainsTheEarlierRevision() throws Exception {
    long fileId = uploadOne("riser.pdf", "revision zero");

    perform(newVersion(fileId, pdf("riser.pdf", "revision one")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.revisionLabel").value("R1"));

    // Still one top-level row, now showing R1 of 2.
    perform(get("/designs/" + designId + "/files"))
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].currentRevisionLabel").value("R1"))
        .andExpect(jsonPath("$.data[0].versionCount").value(2));

    perform(get("/files/" + fileId + "/versions"))
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].revisionLabel").value("R1"))
        .andExpect(jsonPath("$.data[0].current").value(true))
        .andExpect(jsonPath("$.data[1].revisionLabel").value("R0"))
        .andExpect(jsonPath("$.data[1].current").value(false));
  }

  @Test
  void downloadingARetainedVersion_returnsThatVersionNotTheCurrentOne() throws Exception {
    long fileId = uploadOne("riser.pdf", "revision zero");
    perform(newVersion(fileId, pdf("riser.pdf", "revision one"))).andExpect(status().isCreated());

    JsonNode versions = dataOf(perform(get("/files/" + fileId + "/versions")).andReturn());
    long r1 = versions.get(0).path("id").asLong();
    long r0 = versions.get(1).path("id").asLong();

    assertThat(download(fileId, r0)).isEqualTo("revision zero");
    assertThat(download(fileId, r1)).isEqualTo("revision one");
  }

  /**
   * The property ONEMEP-39 calls out explicitly: two simultaneous uploads must not be handed the
   * same revision id. The allocator takes a row lock, so the second waits and gets the next number.
   */
  @Test
  void twoConcurrentVersionUploads_receiveDistinctRevisions() throws Exception {
    long fileId = uploadOne("riser.pdf", "revision zero");

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Callable<String> upload =
          () ->
              designFileService
                  .uploadNewVersion(fileId, pdf("riser.pdf", "concurrent"), null)
                  .getData()
                  .toString();

      Future<String> first = pool.submit(upload);
      Future<String> second = pool.submit(upload);
      first.get();
      second.get();
    } finally {
      pool.shutdownNow();
    }

    List<String> labels =
        jdbc.queryForList(
            "SELECT revision_label FROM onemep_dev.design_file_version WHERE file_id = ?"
                + " ORDER BY revision_no",
            String.class,
            fileId);
    assertThat(labels).containsExactly("R0", "R1", "R2");
  }

  // ── deletion ──────────────────────────────────────────────────────────────

  @Test
  void theCurrentVersion_cannotBeDeletedOnItsOwn() throws Exception {
    long fileId = uploadOne("riser.pdf", "only revision");
    long current =
        dataOf(perform(get("/files/" + fileId + "/versions")).andReturn())
            .get(0)
            .path("id")
            .asLong();

    perform(delete("/files/" + fileId + "/versions/" + current))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.error.message")
                .value("The current version cannot be deleted. Delete the file instead."));
  }

  @Test
  void aRetainedVersionCarryingComments_cannotBeDeleted() throws Exception {
    long fileId = uploadOne("riser.pdf", "revision zero");
    perform(newVersion(fileId, pdf("riser.pdf", "revision one"))).andExpect(status().isCreated());

    JsonNode versions = dataOf(perform(get("/files/" + fileId + "/versions")).andReturn());
    long r0 = versions.get(1).path("id").asLong();

    perform(
            post("/files/" + fileId + "/versions/" + r0 + "/comments")
                .content("{\"body\":\"Pipe clearance needs correction.\"}"))
        .andExpect(status().isCreated());

    perform(delete("/files/" + fileId + "/versions/" + r0))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.error.message")
                .value("This version is referenced by workflow history and cannot be deleted."));
  }

  @Test
  void deletingARetainedVersion_removesItsBytesToo() throws Exception {
    long fileId = uploadOne("riser.pdf", "revision zero");
    perform(newVersion(fileId, pdf("riser.pdf", "revision one"))).andExpect(status().isCreated());

    JsonNode versions = dataOf(perform(get("/files/" + fileId + "/versions")).andReturn());
    long r0 = versions.get(1).path("id").asLong();
    StorageKey key = new StorageKey(storageKeyOf(r0));
    assertThat(fileStorage.exists(key)).isTrue();

    perform(delete("/files/" + fileId + "/versions/" + r0)).andExpect(status().isOk());

    assertThat(fileStorage.exists(key)).isFalse();
    perform(get("/files/" + fileId + "/versions")).andExpect(jsonPath("$.data.length()").value(1));
  }

  @Test
  void deletingTheLogicalFile_removesEveryVersionAndItsBytes() throws Exception {
    long fileId = uploadOne("riser.pdf", "revision zero");
    perform(newVersion(fileId, pdf("riser.pdf", "revision one"))).andExpect(status().isCreated());

    List<String> keys =
        jdbc.queryForList(
            "SELECT storage_key FROM onemep_dev.design_file_version WHERE file_id = ?",
            String.class,
            fileId);
    assertThat(keys).hasSize(2);

    perform(delete("/files/" + fileId)).andExpect(status().isOk());

    keys.forEach(k -> assertThat(fileStorage.exists(new StorageKey(k))).isFalse());
    perform(get("/designs/" + designId + "/files")).andExpect(jsonPath("$.data.length()").value(0));
  }

  // ── comments ──────────────────────────────────────────────────────────────

  @Test
  void commentsStayWithTheVersionTheyWereRaisedAgainst() throws Exception {
    long fileId = uploadOne("riser.pdf", "revision zero");
    JsonNode v0 = dataOf(perform(get("/files/" + fileId + "/versions")).andReturn());
    long r0 = v0.get(0).path("id").asLong();

    perform(
            post("/files/" + fileId + "/versions/" + r0 + "/comments")
                .content("{\"body\":\"Initial review complete.\"}"))
        .andExpect(status().isCreated());

    // A newer revision must not drag the R0 comment forward onto it.
    perform(newVersion(fileId, pdf("riser.pdf", "revision one"))).andExpect(status().isCreated());

    perform(get("/files/" + fileId + "/comments"))
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].revisionLabel").value("R1"))
        .andExpect(jsonPath("$.data[0].comments.length()").value(0))
        .andExpect(jsonPath("$.data[1].revisionLabel").value("R0"))
        .andExpect(jsonPath("$.data[1].comments.length()").value(1))
        .andExpect(jsonPath("$.data[1].comments[0].status").value("OPEN"));
  }

  @Test
  void openCommentCount_drivesTheBadgeAndFallsWhenResolved() throws Exception {
    long fileId = uploadOne("riser.pdf", "revision zero");
    long r0 =
        dataOf(perform(get("/files/" + fileId + "/versions")).andReturn())
            .get(0)
            .path("id")
            .asLong();

    MvcResult created =
        perform(
                post("/files/" + fileId + "/versions/" + r0 + "/comments")
                    .content("{\"body\":\"Confirm plant-room dimensions.\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    assertThat(created).isNotNull();

    perform(get("/designs/" + designId + "/files"))
        .andExpect(jsonPath("$.data[0].openCommentCount").value(1));

    Long commentId =
        jdbc.queryForObject(
            "SELECT id FROM onemep_dev.design_file_comment WHERE version_id = ?", Long.class, r0);
    perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/file-comments/" + commentId)
                .content("{\"status\":\"CLOSED\"}"))
        .andExpect(status().isOk());

    perform(get("/designs/" + designId + "/files"))
        .andExpect(jsonPath("$.data[0].openCommentCount").value(0));
  }

  @Test
  void aWhitespaceOnlyComment_isRejected() throws Exception {
    long fileId = uploadOne("riser.pdf", "revision zero");
    long r0 =
        dataOf(perform(get("/files/" + fileId + "/versions")).andReturn())
            .get(0)
            .path("id")
            .asLong();

    perform(
            post("/files/" + fileId + "/versions/" + r0 + "/comments")
                .content("{\"body\":\"   \"}"))
        .andExpect(status().isBadRequest());
  }

  // ── wiring ────────────────────────────────────────────────────────────────

  @Test
  void designRegisterDocumentCount_reflectsLogicalFilesNotRevisions() throws Exception {
    perform(post("/projects/" + projectId + "/designs/list").content("{}"))
        .andExpect(jsonPath("$.data.content[0].documentCount").value(0));

    long fileId = uploadOne("riser.pdf", "revision zero");
    perform(newVersion(fileId, pdf("riser.pdf", "revision one"))).andExpect(status().isCreated());

    // Two revisions of one file is still one document.
    perform(post("/projects/" + projectId + "/designs/list").content("{}"))
        .andExpect(jsonPath("$.data.content[0].documentCount").value(1));
  }

  @Test
  void uploadsAndCommentsAppearInTheActivityTrail() throws Exception {
    long fileId = uploadOne("riser.pdf", "revision zero");
    perform(newVersion(fileId, pdf("riser.pdf", "revision one"))).andExpect(status().isCreated());

    List<String> details =
        jdbc.queryForList(
            "SELECT detail FROM onemep_dev.design_activity_log WHERE design_id = ?",
            String.class,
            designId);
    assertThat(details).contains("Uploaded 'riser.pdf'", "Uploaded R1 of 'riser'");
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private String storageKeyOf(long versionId) {
    return jdbc.queryForObject(
        "SELECT storage_key FROM onemep_dev.design_file_version WHERE id = ?",
        String.class,
        versionId);
  }

  private String download(long fileId, long versionId) throws Exception {
    return perform(get("/files/" + fileId + "/versions/" + versionId + "/content"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private long uploadOne(String filename, String content) throws Exception {
    MvcResult result =
        perform(upload(designId, pdf(filename, content)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .path("data")
        .path("results")
        .get(0)
        .path("fileId")
        .asLong();
  }

  private JsonNode dataOf(MvcResult result) throws IOException {
    return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
  }

  private static MockMultipartFile pdf(String filename, String content) {
    return new MockMultipartFile(
        "files", filename, "application/pdf", content.getBytes(StandardCharsets.UTF_8));
  }

  private static MockHttpServletRequestBuilder upload(long designId, MockMultipartFile... files) {
    var builder = multipart("/designs/" + designId + "/files");
    for (MockMultipartFile file : files) {
      builder.file(file);
    }
    return builder;
  }

  private static MockHttpServletRequestBuilder newVersion(long fileId, MockMultipartFile file) {
    return multipart("/files/" + fileId + "/versions")
        .file(
            new MockMultipartFile(
                "file", file.getOriginalFilename(), file.getContentType(), bytes(file)));
  }

  private static byte[] bytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private long ensureProject() {
    Long existing =
        jdbc.query(
            "SELECT id FROM onemep_dev.project_master WHERE project_number = '40012'",
            rs -> rs.next() ? rs.getLong(1) : null);
    if (existing != null) {
      return existing;
    }
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
    return jdbc.queryForObject(
        "SELECT id FROM onemep_dev.project_master WHERE project_number = '40012'", Long.class);
  }

  private long createDesign(String title) throws Exception {
    MvcResult result =
        perform(
                post("/projects/" + projectId + "/designs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"zoneCode":"%s","disciplineId":%d,"typeId":%d,"subjectId":%d,
                         "floorId":%d,"stageId":%d,"title":"%s"}
                        """
                            .formatted(
                                nextZone(),
                                lookupId("DISCIPLINE", "M"),
                                lookupId("DESIGN_TYPE", "SCH"),
                                lookupId("SUBJECT", "CHW"),
                                lookupId("FLOOR", "00"),
                                lookupId("STAGE", "DD"),
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
    if (!(builder
        instanceof
        org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder)) {
      builder.contentType(MediaType.APPLICATION_JSON);
    }
    return mockMvc.perform(builder.with(jwt().jwt(j -> j.subject("1"))));
  }
}
