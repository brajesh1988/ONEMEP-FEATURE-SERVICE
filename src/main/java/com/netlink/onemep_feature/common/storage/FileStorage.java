package com.netlink.onemep_feature.common.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

/**
 * Where uploaded bytes live. One seam behind local disk, S3 or Azure Blob, selected by the {@code
 * storage.provider} property.
 *
 * <p>Two rules for callers, both of which matter at the 150 MB ceiling this service allows:
 *
 * <ul>
 *   <li><b>Never write inside a database transaction.</b> Upload first, then commit the owning row;
 *       delete the object again if the commit rolls back. A multi-hundred-megabyte PUT inside a
 *       transaction pins a Hikari connection for its whole duration.
 *   <li><b>Prefer a presigned URL to streaming.</b> Streaming a large download through the service
 *       occupies a request thread for the transfer. Ask {@link #supportsPresignedUrls()} and fall
 *       back to {@link #open} only when the provider cannot sign — but authorise the caller
 *       <em>before</em> issuing a URL, because a signed URL bypasses the security filter chain
 *       entirely, and keep the TTL short.
 * </ul>
 */
public interface FileStorage {

  /**
   * Writes {@code data} at {@code key}, replacing anything already there. The caller owns the
   * stream and is responsible for closing it.
   */
  StoredObject put(StorageKey key, InputStream data, long sizeBytes, String contentType);

  /** Opens the stored object for reading. The caller must close the returned stream. */
  InputStream open(StorageKey key);

  /** Returns {@code true} if an object was removed, {@code false} if it was already absent. */
  boolean delete(StorageKey key);

  boolean exists(StorageKey key);

  /** Whether {@link #presignedGetUrl} is usable. Local disk cannot sign; object stores can. */
  default boolean supportsPresignedUrls() {
    return false;
  }

  /**
   * Time-limited direct-download URL. Only call after authorising the caller — the URL carries its
   * own credentials and is not subject to this service's security filters.
   */
  default URI presignedGetUrl(StorageKey key, Duration ttl) {
    throw new UnsupportedOperationException(
        "This storage provider does not support presigned URLs.");
  }
}
