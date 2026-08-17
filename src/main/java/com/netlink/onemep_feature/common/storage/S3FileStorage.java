package com.netlink.onemep_feature.common.storage;

import com.netlink.onemep_feature.common.util.DateUtils;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * S3-backed storage, and the provider this service is meant to run on outside development.
 *
 * <p>Uploads stream with an explicit content length rather than being buffered: at the 150 MB
 * ceiling this service allows, reading a file into memory to discover its size would be a reliable
 * way to exhaust the heap.
 *
 * <p>Objects are written beneath an optional configured key prefix, so this service can share a
 * bucket with other things. The prefix is applied here and only here — the {@link StorageKey} the
 * rest of the application passes around, and the {@code storage_key} persisted against each row,
 * stay prefix-free. That is deliberate: the prefix is a deployment detail, and baking it into
 * stored keys would strand every existing row the day it changes.
 *
 * <p>Downloads should go through {@link #presignedGetUrl} wherever the caller can follow a redirect
 * — it hands the transfer to S3 instead of occupying a request thread for its duration. Authorise
 * before issuing one: a presigned URL carries its own credentials and never touches this service's
 * security filters again.
 */
@Slf4j
public class S3FileStorage implements FileStorage {

  private final S3Client client;
  private final S3Presigner presigner;
  private final String bucket;
  private final String prefix;

  public S3FileStorage(S3Client client, S3Presigner presigner, String bucket) {
    this(client, presigner, bucket, "");
  }

  public S3FileStorage(S3Client client, S3Presigner presigner, String bucket, String prefix) {
    this.client = client;
    this.presigner = presigner;
    this.bucket = bucket;
    this.prefix = normalize(prefix);
    log.info(
        "S3 file storage using bucket {}{}",
        bucket,
        this.prefix.isEmpty() ? "" : " under prefix '" + this.prefix + "/'");
  }

  /** Belt and braces over {@code StorageProperties.S3}, which normalises the configured value. */
  private static String normalize(String raw) {
    String value = raw == null ? "" : raw.trim();
    while (value.startsWith("/")) {
      value = value.substring(1);
    }
    while (value.endsWith("/")) {
      value = value.substring(0, value.length() - 1);
    }
    return value;
  }

  /** The object key actually sent to S3: the logical key beneath the configured prefix. */
  private String objectKey(StorageKey key) {
    return prefix.isEmpty() ? key.value() : prefix + "/" + key.value();
  }

  @Override
  public StoredObject put(StorageKey key, InputStream data, long sizeBytes, String contentType) {
    try {
      PutObjectRequest.Builder request =
          PutObjectRequest.builder().bucket(bucket).key(objectKey(key));
      if (contentType != null && !contentType.isBlank()) {
        request.contentType(contentType);
      }
      client.putObject(request.build(), RequestBody.fromInputStream(data, sizeBytes));
      return new StoredObject(key, sizeBytes, contentType, DateUtils.getCurrentUtcTime());
    } catch (S3Exception e) {
      throw new StorageException("Unable to store the file.", e);
    }
  }

  @Override
  public InputStream open(StorageKey key) {
    try {
      return client.getObject(
          GetObjectRequest.builder().bucket(bucket).key(objectKey(key)).build());
    } catch (NoSuchKeyException e) {
      throw new StorageException("Stored file not found: " + key, e);
    } catch (S3Exception e) {
      throw new StorageException("Unable to read the stored file.", e);
    }
  }

  /**
   * S3 deletes are idempotent and report success for a key that was never there, so existence is
   * checked first to keep the {@code boolean} contract meaningful to callers.
   */
  @Override
  public boolean delete(StorageKey key) {
    if (!exists(key)) {
      return false;
    }
    try {
      client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey(key)).build());
      return true;
    } catch (S3Exception e) {
      throw new StorageException("Unable to delete the stored file.", e);
    }
  }

  @Override
  public boolean exists(StorageKey key) {
    try {
      client.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey(key)).build());
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    } catch (S3Exception e) {
      // A 404 arrives as a plain S3Exception on HEAD rather than NoSuchKeyException.
      if (e.statusCode() == 404) {
        return false;
      }
      throw new StorageException("Unable to check the stored file.", e);
    }
  }

  @Override
  public boolean supportsPresignedUrls() {
    return true;
  }

  @Override
  public URI presignedGetUrl(StorageKey key, Duration ttl) {
    try {
      return presigner
          .presignGetObject(
              GetObjectPresignRequest.builder()
                  .signatureDuration(ttl)
                  .getObjectRequest(
                      GetObjectRequest.builder().bucket(bucket).key(objectKey(key)).build())
                  .build())
          .url()
          .toURI();
    } catch (Exception e) {
      throw new StorageException("Unable to generate a download link.", e);
    }
  }
}
