package com.netlink.onemep_feature.common.storage;

import java.net.URI;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Selects the storage provider from {@code storage.provider}.
 *
 * <p>Adding a provider means adding one {@code @Bean} guarded by {@code havingValue} and its {@link
 * FileStorage} implementation — nothing that calls the interface changes. The {@code local} bean is
 * also the fallback when the property is absent, so the service starts with working storage out of
 * the box.
 *
 * <p>No AWS credentials appear here or in application config. The SDK's default provider chain
 * resolves them from the environment — instance role, container credentials, or environment
 * variables — which keeps them out of the repository entirely.
 */
@Configuration
@Slf4j
public class StorageConfig {

  @Bean
  @ConditionalOnProperty(name = "storage.provider", havingValue = "local", matchIfMissing = true)
  FileStorage localFileStorage(StorageProperties properties) {
    return new LocalFileStorage(Path.of(properties.local().root()));
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(name = "storage.provider", havingValue = "s3")
  S3Client s3Client(StorageProperties properties) {
    return applyEndpoint(S3Client.builder(), properties).build();
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(name = "storage.provider", havingValue = "s3")
  S3Presigner s3Presigner(StorageProperties properties) {
    S3Presigner.Builder builder = S3Presigner.builder().region(Region.of(properties.s3().region()));
    String endpoint = properties.s3().endpoint();
    if (endpoint != null && !endpoint.isBlank()) {
      builder.endpointOverride(URI.create(endpoint));
      builder.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
    }
    return builder.build();
  }

  @Bean
  @ConditionalOnProperty(name = "storage.provider", havingValue = "s3")
  FileStorage s3FileStorage(
      S3Client s3Client, S3Presigner s3Presigner, StorageProperties properties) {
    String bucket = properties.s3().bucket();
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalStateException(
          "storage.provider=s3 requires storage.s3.bucket to be set (S3_BUCKET).");
    }
    return new S3FileStorage(s3Client, s3Presigner, bucket);
  }

  /**
   * Guard against a typo in {@code storage.provider} silently leaving the context with no storage
   * bean and failing much later with an obscure injection error.
   */
  @Bean
  @ConditionalOnMissingBean(FileStorage.class)
  FileStorage unconfiguredFileStorage(StorageProperties properties) {
    throw new IllegalStateException(
        "storage.provider='"
            + properties.provider()
            + "' is not a configured storage provider. Supported: local, s3"
            + " (azure-blob requires its provider module).");
  }

  /**
   * An {@code endpoint} override points the client at an S3-compatible store — MinIO, or LocalStack
   * in tests. Those need path-style addressing, since {@code bucket.host} virtual-host style does
   * not resolve for them.
   */
  private static S3ClientBuilder applyEndpoint(
      S3ClientBuilder builder, StorageProperties properties) {
    builder.region(Region.of(properties.s3().region()));
    String endpoint = properties.s3().endpoint();
    if (endpoint != null && !endpoint.isBlank()) {
      builder.endpointOverride(URI.create(endpoint));
      builder.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
    }
    return builder;
  }
}
