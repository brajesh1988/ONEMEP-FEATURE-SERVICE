package com.netlink.onemep_feature.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Storage configuration normalisation and the guards that stop a misconfiguration reaching S3. */
class StoragePropertiesTest {

  // ── key prefix ────────────────────────────────────────────────────────────

  @ParameterizedTest
  @ValueSource(strings = {"OneMep", "/OneMep", "OneMep/", "/OneMep/", "  OneMep  ", "//OneMep//"})
  void s3Prefix_isNormalisedToBareSegments(String configured) {
    assertThat(new StorageProperties.S3("b", "eu-north-1", configured, null).prefix())
        .isEqualTo("OneMep");
  }

  @ParameterizedTest
  @NullAndEmptySource
  void s3Prefix_whenUnset_isBlankRatherThanNull(String configured) {
    assertThat(new StorageProperties.S3("b", "eu-north-1", configured, null).prefix()).isEmpty();
  }

  @Test
  void s3Prefix_keepsNestedSegments() {
    assertThat(new StorageProperties.S3("b", "eu-north-1", "/OneMep/designs/", null).prefix())
        .isEqualTo("OneMep/designs");
  }

  /** The whole storage block may be absent; the defaults must still be usable. */
  @Test
  void properties_withNothingConfigured_fallBackToLocal() {
    StorageProperties properties = new StorageProperties(null, null, null, null);

    assertThat(properties.provider()).isEqualTo("local");
    assertThat(properties.local().root()).isEqualTo("./var/uploads");
    assertThat(properties.s3().prefix()).isEmpty();
    assertThat(properties.presignedUrlTtlSeconds()).isEqualTo(300L);
  }

  // ── endpoint guard ────────────────────────────────────────────────────────

  @ParameterizedTest
  @ValueSource(
      strings = {"http://localhost:4566", "https://minio.internal:9000", " http://127.0.0.1:9000 "})
  void endpoint_acceptsAnHttpServiceAddress(String endpoint) {
    assertThat(StorageConfig.requireHttpEndpoint(endpoint)).isInstanceOf(URI.class);
  }

  /**
   * The exact misconfiguration this guard exists for: a bucket URI in the endpoint field builds a
   * client without complaint and then fails every upload at runtime.
   */
  @Test
  void endpoint_withABucketUri_isRejectedAtStartupNamingTheRealFix() {
    assertThatThrownBy(
            () -> StorageConfig.requireHttpEndpoint("s3://onemep-project-files-2026/OneMep/"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must be an http(s) service endpoint")
        .hasMessageContaining("leave it blank")
        .hasMessageContaining("S3_PREFIX");
  }

  @Test
  void endpoint_withNoScheme_isRejected() {
    assertThatThrownBy(() -> StorageConfig.requireHttpEndpoint("onemep-project-files-2026"))
        .isInstanceOf(IllegalStateException.class);
  }
}
