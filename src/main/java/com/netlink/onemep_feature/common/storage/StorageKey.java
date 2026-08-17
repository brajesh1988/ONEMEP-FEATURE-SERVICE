package com.netlink.onemep_feature.common.storage;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Opaque path of a stored object, e.g. {@code designs/42/files/7/v3}.
 *
 * <p>Deliberately never derived from a user-supplied filename: uploaded names collide, carry
 * unicode the object store may normalise, and are the classic vector for path traversal. The
 * original name is kept as metadata on the owning row instead.
 */
public record StorageKey(String value) {

  private static final int MAX_LENGTH = 1024;

  public StorageKey {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Storage key must not be blank.");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("Storage key must not exceed " + MAX_LENGTH + " chars.");
    }
    if (value.startsWith("/") || value.endsWith("/")) {
      throw new IllegalArgumentException("Storage key must not start or end with '/'.");
    }
    if (value.contains("//")) {
      throw new IllegalArgumentException("Storage key must not contain an empty segment.");
    }
    if (value.indexOf('\\') >= 0 || value.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("Storage key contains an illegal character.");
    }
    for (String segment : value.split("/")) {
      if (".".equals(segment) || "..".equals(segment)) {
        throw new IllegalArgumentException("Storage key must not contain relative segments.");
      }
    }
  }

  /** Joins pre-validated segments, e.g. {@code of("designs", designId, "files", fileId)}. */
  public static StorageKey of(Object... segments) {
    return new StorageKey(
        Arrays.stream(segments).map(String::valueOf).collect(Collectors.joining("/")));
  }

  @Override
  public String toString() {
    return value;
  }
}
