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
