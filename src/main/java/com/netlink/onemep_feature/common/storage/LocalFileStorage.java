package com.netlink.onemep_feature.common.storage;

import com.netlink.onemep_feature.common.util.DateUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;

/**
 * Filesystem-backed storage for local development and single-node deployments.
 *
 * <p>Every resolved path is re-checked against the configured root after normalisation, so a key
 * that escapes the root cannot be written or read even if {@link StorageKey}'s own validation is
 * ever loosened. Writes land on a temporary file and are then moved into place, so a failed or
 * interrupted upload never leaves a half-written object readable at its final key.
 */
@Slf4j
public class LocalFileStorage implements FileStorage {

  private final Path root;

  public LocalFileStorage(Path root) {
    this.root = root.toAbsolutePath().normalize();
    try {
      Files.createDirectories(this.root);
    } catch (IOException e) {
      throw new StorageException("Unable to create the local storage root: " + this.root, e);
    }
    log.info("Local file storage rooted at {}", this.root);
  }

  @Override
  public StoredObject put(StorageKey key, InputStream data, long sizeBytes, String contentType) {
    Path target = resolve(key);
    Path temp = null;
    try {
      Files.createDirectories(target.getParent());
      temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
      long written = Files.copy(data, temp, StandardCopyOption.REPLACE_EXISTING);
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      temp = null;
      return new StoredObject(key, written, contentType, DateUtils.getCurrentUtcTime());
    } catch (IOException e) {
      throw new StorageException("Unable to store the file.", e);
    } finally {
      deleteQuietly(temp);
    }
  }

  @Override
  public InputStream open(StorageKey key) {
    Path target = resolve(key);
    if (!Files.isRegularFile(target)) {
      throw new StorageException("Stored file not found: " + key);
    }
    try {
      return Files.newInputStream(target);
    } catch (IOException e) {
      throw new StorageException("Unable to read the stored file.", e);
    }
  }

  @Override
  public boolean delete(StorageKey key) {
    try {
      return Files.deleteIfExists(resolve(key));
    } catch (IOException e) {
      throw new StorageException("Unable to delete the stored file.", e);
    }
  }

  @Override
  public boolean exists(StorageKey key) {
    return Files.isRegularFile(resolve(key));
  }

  /**
   * Resolves a key beneath the root, rejecting anything that normalises outside it. Belt and braces
   * over {@link StorageKey}'s constructor.
   */
  private Path resolve(StorageKey key) {
    Path candidate = root.resolve(key.value()).normalize();
    if (!candidate.startsWith(root)) {
      throw new StorageException("Resolved path escapes the storage root.");
    }
    return candidate;
  }

  private static void deleteQuietly(Path path) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      log.warn("Could not remove temporary upload file {}", path, e);
    }
  }
}
