package com.netlink.onemep_feature.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Storage configuration. {@code provider} selects which {@link FileStorage} bean is created; the
 * nested blocks hold only that provider's settings.
 *
 * <p>Credentials are never listed here. The AWS and Azure SDKs both resolve them from the ambient
 * environment (instance role, container credentials, environment variables), which keeps secrets
 * out of application config entirely.
 *
 * @param provider {@code local}, {@code s3} or {@code azure-blob}
 * @param presignedUrlTtlSeconds lifetime of a generated download URL; short by design
 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(String provider, Long presignedUrlTtlSeconds, Local local, S3 s3) {

  public StorageProperties {
    provider = provider == null || provider.isBlank() ? "local" : provider.trim().toLowerCase();
    presignedUrlTtlSeconds =
        presignedUrlTtlSeconds == null || presignedUrlTtlSeconds <= 0
            ? 300L
            : presignedUrlTtlSeconds;
    local = local == null ? new Local(null) : local;
    s3 = s3 == null ? new S3(null, null, null, null) : s3;
  }

  /**
   * @param root directory beneath which objects are written
   */
  public record Local(String root) {
    public Local {
      root = root == null || root.isBlank() ? "./var/uploads" : root.trim();
    }
  }

  /**
   * @param bucket target bucket
   * @param region AWS region — must be the bucket's own region. S3 answers 301 Moved Permanently
   *     for a request signed against the wrong one, which surfaces as an opaque failure on the
   *     first upload rather than at startup.
   * @param prefix key prefix inside the bucket, so this service's objects can share a bucket with
   *     other things. Blank means write at the bucket root.
   * @param endpoint override for S3-compatible stores (MinIO, LocalStack) ONLY. It is an HTTP(S)
   *     service endpoint, never a bucket or a path — an {@code s3://} URI here fails every call
   *     with "Custom endpoint ... was not a valid URI". Leave blank for real AWS, which derives the
   *     endpoint from the region.
   */
  public record S3(String bucket, String region, String prefix, String endpoint) {

    public S3 {
      // Normalised once, here, so every caller sees the same shape: no leading or trailing slash,
      // and blank rather than null. StorageKey forbids both '//' and a leading '/', so an
      // un-normalised prefix would fail its constructor rather than doing something sensible.
      prefix = prefix == null ? "" : prefix.trim();
      while (prefix.startsWith("/")) {
        prefix = prefix.substring(1);
      }
      while (prefix.endsWith("/")) {
        prefix = prefix.substring(0, prefix.length() - 1);
      }
    }
  }
}
