package com.netlink.onemep_feature.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Runs the {@link FileStorage} contract against a real S3 API (LocalStack), so the provider is
 * proven rather than assumed. The behaviours that differ from local disk — presigned URLs, and
 * delete reporting whether anything was actually removed — are the point of the exercise.
 */
@Testcontainers
@Tag("integration")
class S3FileStorageIT {

  private static final String BUCKET = "onemep-designs";

  @Container
  static final LocalStackContainer LOCALSTACK =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.5"))
          .withServices(LocalStackContainer.Service.S3);

  private static S3Client client;
  private static S3Presigner presigner;
  private static S3FileStorage storage;

  /** Same bucket, same client — but writing beneath a configured key prefix. */
  private static S3FileStorage prefixed;

  @BeforeAll
  static void startStorage() {
    URI endpoint = LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3);
    var credentials =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey()));
    S3Configuration pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();

    client =
        S3Client.builder()
            .endpointOverride(endpoint)
            .credentialsProvider(credentials)
            .region(Region.of(LOCALSTACK.getRegion()))
            .serviceConfiguration(pathStyle)
            .build();
    presigner =
        S3Presigner.builder()
            .endpointOverride(endpoint)
            .credentialsProvider(credentials)
            .region(Region.of(LOCALSTACK.getRegion()))
            .serviceConfiguration(pathStyle)
            .build();

    client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    storage = new S3FileStorage(client, presigner, BUCKET);
    prefixed = new S3FileStorage(client, presigner, BUCKET, "OneMep");
  }

  @AfterAll
  static void closeClients() {
    if (presigner != null) {
      presigner.close();
    }
    if (client != null) {
      client.close();
    }
  }

  // ── configured key prefix ─────────────────────────────────────────────────

  /**
   * The prefix has to be applied to the real object key, not merely remembered. This asserts
   * against the raw S3 key rather than through the storage abstraction, because the whole risk is
   * that reads and writes agree with each other while both ignore the prefix.
   */
  @Test
  void put_withAConfiguredPrefix_writesBeneathThatPrefixInTheBucket() throws IOException {
    StorageKey key = StorageKey.of("designs", 90L, "files", 1L, "r0");
    prefixed.put(
        key,
        new ByteArrayInputStream("prefixed bytes".getBytes(StandardCharsets.UTF_8)),
        14L,
        "text/plain");

    List<String> keys =
        client
            .listObjectsV2(ListObjectsV2Request.builder().bucket(BUCKET).prefix("OneMep/").build())
            .contents()
            .stream()
            .map(S3Object::key)
            .toList();

    assertThat(keys).contains("OneMep/designs/90/files/1/r0");
  }

  @Test
  void prefixedStorage_roundTripsThroughEveryOperation() throws IOException {
    StorageKey key = StorageKey.of("designs", 91L, "r0");

    prefixed.put(
        key,
        new ByteArrayInputStream("round trip".getBytes(StandardCharsets.UTF_8)),
        10L,
        "text/plain");

    assertThat(prefixed.exists(key)).isTrue();
    try (InputStream in = prefixed.open(key)) {
      assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("round trip");
    }
    assertThat(prefixed.delete(key)).isTrue();
    assertThat(prefixed.exists(key)).isFalse();
  }

  /**
   * A presigned URL that ignored the prefix would 404 for the viewer while everything else worked.
   */
  @Test
  void presignedUrl_fromPrefixedStorage_resolvesThePrefixedObject() throws Exception {
    StorageKey key = StorageKey.of("designs", 92L, "r0");
    prefixed.put(
        key,
        new ByteArrayInputStream("signed and prefixed".getBytes(StandardCharsets.UTF_8)),
        19L,
        "text/plain");

    URI url = prefixed.presignedGetUrl(key, Duration.ofMinutes(2));

    assertThat(url.toString()).contains("OneMep/designs/92/r0");
    try (InputStream in = url.toURL().openStream()) {
      assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8))
          .isEqualTo("signed and prefixed");
    }
  }

  /** The two must not collide: the same logical key is a different object under a prefix. */
  @Test
  void prefixedAndUnprefixedStorage_addressDifferentObjects() throws IOException {
    StorageKey key = StorageKey.of("designs", 93L, "r0");

    storage.put(
        key, new ByteArrayInputStream("root".getBytes(StandardCharsets.UTF_8)), 4L, "text/plain");

    assertThat(storage.exists(key)).isTrue();
    assertThat(prefixed.exists(key)).isFalse();
  }

  @Test
  void put_thenOpen_roundTripsBytes() throws IOException {
    StorageKey key = StorageKey.of("designs", 1L, "files", 2L, "v0");
    byte[] payload = "riser schematic".getBytes(StandardCharsets.UTF_8);

    StoredObject stored =
        storage.put(key, new ByteArrayInputStream(payload), payload.length, "application/pdf");

    assertThat(stored.key()).isEqualTo(key);
    assertThat(stored.sizeBytes()).isEqualTo(payload.length);
    assertThat(stored.contentType()).isEqualTo("application/pdf");

    try (InputStream in = storage.open(key)) {
      assertThat(in.readAllBytes()).isEqualTo(payload);
    }
  }

  @Test
  void put_overwritesTheSameKey() throws IOException {
    StorageKey key = StorageKey.of("designs", 2L, "v0");
    storage.put(key, stream("first"), 5, "text/plain");
    storage.put(key, stream("second"), 6, "text/plain");

    try (InputStream in = storage.open(key)) {
      assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("second");
    }
  }

  @Test
  void open_missingObject_throwsStorageException() {
    assertThatThrownBy(() -> storage.open(StorageKey.of("designs", 404L, "nope")))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void exists_reflectsWhetherTheObjectIsThere() {
    StorageKey key = StorageKey.of("designs", 3L, "v0");
    assertThat(storage.exists(key)).isFalse();

    storage.put(key, stream("x"), 1, "text/plain");
    assertThat(storage.exists(key)).isTrue();
  }

  /**
   * S3 reports success when deleting a key that never existed. The provider checks first so callers
   * get the same answer they would from local disk.
   */
  @Test
  void delete_reportsWhetherAnythingWasActuallyRemoved() {
    StorageKey key = StorageKey.of("designs", 4L, "v0");
    storage.put(key, stream("x"), 1, "text/plain");

    assertThat(storage.delete(key)).isTrue();
    assertThat(storage.delete(key)).isFalse();
    assertThat(storage.exists(key)).isFalse();
  }

  @Test
  void presignedUrl_isSupported_andDownloadsWithoutTheServiceInThePath() throws Exception {
    StorageKey key = StorageKey.of("designs", 5L, "v0");
    byte[] payload = "presigned payload".getBytes(StandardCharsets.UTF_8);
    storage.put(key, new ByteArrayInputStream(payload), payload.length, "text/plain");

    assertThat(storage.supportsPresignedUrls()).isTrue();
    URI url = storage.presignedGetUrl(key, Duration.ofMinutes(5));

    HttpURLConnection connection = (HttpURLConnection) new URL(url.toString()).openConnection();
    try (InputStream in = connection.getInputStream()) {
      assertThat(connection.getResponseCode()).isEqualTo(200);
      assertThat(in.readAllBytes()).isEqualTo(payload);
    } finally {
      connection.disconnect();
    }
  }

  @Test
  void presignedUrl_carriesItsOwnCredentials_soItIsTimeLimitedNotOpen() {
    StorageKey key = StorageKey.of("designs", 6L, "v0");
    storage.put(key, stream("x"), 1, "text/plain");

    String url = storage.presignedGetUrl(key, Duration.ofMinutes(1)).toString();

    // The signature and its expiry are in the URL — which is exactly why the caller must be
    // authorised before one is handed out, and why the TTL is kept short.
    assertThat(url).contains("X-Amz-Signature").contains("X-Amz-Expires");
  }

  private static InputStream stream(String value) {
    return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
  }
}
