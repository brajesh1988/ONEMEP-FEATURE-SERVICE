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
    s3 = s3 == null ? new S3(null, null, null) : s3;
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
   * @param region AWS region
   * @param endpoint optional override for S3-compatible stores (MinIO, LocalStack)
   */
  public record S3(String bucket, String region, String endpoint) {}
}
