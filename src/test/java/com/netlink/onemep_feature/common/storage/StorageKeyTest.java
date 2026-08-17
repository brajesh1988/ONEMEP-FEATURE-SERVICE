package com.netlink.onemep_feature.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Key validation is the first line of defence against path traversal in file storage. */
class StorageKeyTest {

  @Test
  void of_joinsSegmentsWithSlashes() {
    assertThat(StorageKey.of("designs", 42L, "files", 7L, "v3").value())
        .isEqualTo("designs/42/files/7/v3");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "../etc/passwd",
        "designs/../../etc/passwd",
        "designs/./42",
        "/designs/42",
        "designs/42/",
        "designs//42",
        "designs\\42"
      })
  void constructor_rejectsTraversalAndMalformedKeys(String candidate) {
    assertThatThrownBy(() -> new StorageKey(candidate))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructor_rejectsBlank() {
    assertThatThrownBy(() -> new StorageKey("  ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new StorageKey(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructor_rejectsNullByte() {
    String withNullByte = "designs/4" + '\0' + "2";
    assertThatThrownBy(() -> new StorageKey(withNullByte))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructor_rejectsOverlongKey() {
    assertThatThrownBy(() -> new StorageKey("a".repeat(1025)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructor_acceptsDottedFilenameThatIsNotTraversal() {
    assertThat(new StorageKey("designs/42/riser.v2.pdf").value())
        .isEqualTo("designs/42/riser.v2.pdf");
  }

  @Test
  void constructor_acceptsSpacesWithinASegment() {
    assertThat(new StorageKey("designs/42/riser plan.pdf").value())
        .isEqualTo("designs/42/riser plan.pdf");
  }
}
