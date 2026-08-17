package com.netlink.onemep_feature.file.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netlink.onemep_feature.exception.ApplicationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

/** File-level upload rules (ONEMEP-39). */
class UploadedFileRulesTest {

  @ParameterizedTest
  @CsvSource({
    "riser.pdf, pdf",
    "Design Model.DWG, dwg",
    "sheet.v2.xlsx, xlsx",
    "archive.tar.gz, gz",
    "noextension, ''",
    "trailingdot., ''"
  })
  void extensionOf_takesTheLastSegmentInLowerCase(String filename, String expected) {
    assertThat(UploadedFileRules.extensionOf(filename)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "riser.pdf, riser",
    "Domestic_Water_Riser.dwg, Domestic_Water_Riser",
    "sheet.v2.xlsx, sheet.v2",
    "noextension, noextension"
  })
  void displayNameOf_dropsOnlyTheExtension(String filename, String expected) {
    assertThat(UploadedFileRules.displayNameOf(filename)).isEqualTo(expected);
  }

  /** Some clients send a full path; only the leaf is kept, and never treated as a path after. */
  @ParameterizedTest
  @CsvSource({
    "'C:\\drawings\\riser.pdf', riser.pdf",
    "'/home/user/riser.pdf', riser.pdf",
    "'riser.pdf', riser.pdf"
  })
  void originalFilename_keepsOnlyTheLeaf(String sent, String expected) {
    assertThat(UploadedFileRules.originalFilename(file(sent, 10))).isEqualTo(expected);
  }

  @Test
  void validate_acceptsASupportedTypeWithinTheSizeLimit() {
    UploadedFileRules.validate(file("riser.pdf", 1024));
  }

  @Test
  void validate_atExactlyTheSizeLimit_isAccepted() {
    UploadedFileRules.validate(file("riser.pdf", UploadedFileRules.MAX_FILE_SIZE_BYTES));
  }

  @Test
  void validate_overTheSizeLimit_isRejectedByName() {
    assertThatThrownBy(
            () ->
                UploadedFileRules.validate(
                    file("riser.pdf", UploadedFileRules.MAX_FILE_SIZE_BYTES + 1)))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("riser.pdf exceeds the maximum file size of 150 MB.");
  }

  @Test
  void validate_emptyFile_isRejected() {
    assertThatThrownBy(() -> UploadedFileRules.validate(file("riser.pdf", 0)))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("riser.pdf is empty and cannot be uploaded.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"notes.txt", "script.sh", "photo.gif", "noextension"})
  void validate_unsupportedType_isRejectedByName(String filename) {
    assertThatThrownBy(() -> UploadedFileRules.validate(file(filename, 100)))
        .isInstanceOf(ApplicationException.class)
        .hasMessage(filename + " is not a supported file type.");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "a.pdf", "a.doc", "a.docx", "a.xls", "a.xlsx", "a.csv",
        "a.dwg", "a.dxf", "a.zip", "a.png", "a.jpg", "a.jpeg"
      })
  void validate_acceptsEveryTypeTheTicketNames(String filename) {
    UploadedFileRules.validate(file(filename, 100));
  }

  @Test
  void validate_isCaseInsensitiveAboutTheExtension() {
    UploadedFileRules.validate(file("RISER.PDF", 100));
  }

  private static MockMultipartFile file(String filename, long size) {
    return new MockMultipartFile("file", filename, null, new byte[(int) Math.min(size, 4096)]) {
      @Override
      public long getSize() {
        return size;
      }

      @Override
      public boolean isEmpty() {
        return size == 0;
      }
    };
  }
}
