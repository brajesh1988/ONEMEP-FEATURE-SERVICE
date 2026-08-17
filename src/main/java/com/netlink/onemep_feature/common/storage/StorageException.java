package com.netlink.onemep_feature.common.storage;

/**
 * The backing store failed.
 *
 * <p>Deliberately not an {@code ApplicationException}: that maps to HTTP 400, which would report an
 * S3 outage as the caller's mistake. This falls through to the generic handler in {@code
 * GlobalExceptionHandler}, which answers 500, logs the stack trace server-side, and returns a
 * message carrying no provider internals.
 */
public class StorageException extends RuntimeException {

  public StorageException(String message) {
    super(message);
  }

  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
