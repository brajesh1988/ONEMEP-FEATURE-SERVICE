package com.netlink.onemep_feature.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

  @TempDir Path root;
  private LocalFileStorage storage;

  @BeforeEach
  void setUp() {
    storage = new LocalFileStorage(root);
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
    assertThat(stored.storedAt()).isNotNull();

    try (InputStream in = storage.open(key)) {
      assertThat(in.readAllBytes()).isEqualTo(payload);
    }
  }

  @Test
  void put_createsNestedDirectories() {
    StorageKey key = StorageKey.of("a", "b", "c", "d", "object");
    storage.put(key, new ByteArrayInputStream(new byte[] {1}), 1, "application/octet-stream");

    assertThat(root.resolve("a/b/c/d/object")).exists();
  }

  @Test
  void put_overwritesExistingObjectAtSameKey() throws IOException {
    StorageKey key = StorageKey.of("designs", 1L, "v0");
    storage.put(
        key, new ByteArrayInputStream("first".getBytes(StandardCharsets.UTF_8)), 5, "text/plain");
    storage.put(
        key, new ByteArrayInputStream("second".getBytes(StandardCharsets.UTF_8)), 6, "text/plain");

    try (InputStream in = storage.open(key)) {
      assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("second");
    }
  }

  @Test
  void put_leavesNoTemporaryFilesBehind() throws IOException {
    storage.put(
        StorageKey.of("designs", 1L, "v0"),
        new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)),
        1,
        "text/plain");

    try (Stream<Path> tree = Files.walk(root)) {
      assertThat(tree.filter(Files::isRegularFile).map(p -> p.getFileName().toString()))
          .noneMatch(name -> name.startsWith(".upload-"));
    }
  }

  @Test
  void open_missingObject_throwsStorageException() {
    assertThatThrownBy(() -> storage.open(StorageKey.of("nope")))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void delete_reportsWhetherAnythingWasRemoved() {
    StorageKey key = StorageKey.of("designs", 1L, "v0");
    storage.put(key, new ByteArrayInputStream(new byte[] {1}), 1, "text/plain");

    assertThat(storage.delete(key)).isTrue();
    assertThat(storage.delete(key)).isFalse();
    assertThat(storage.exists(key)).isFalse();
  }

  @Test
  void constructor_createsRootWhenAbsent() {
    Path nested = root.resolve("does/not/exist/yet");
    new LocalFileStorage(nested);
    assertThat(nested).isDirectory();
  }

  @Test
  void localProvider_doesNotClaimPresignedUrlSupport() {
    assertThat(storage.supportsPresignedUrls()).isFalse();
    assertThatThrownBy(
            () -> storage.presignedGetUrl(StorageKey.of("x"), java.time.Duration.ofMinutes(5)))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
