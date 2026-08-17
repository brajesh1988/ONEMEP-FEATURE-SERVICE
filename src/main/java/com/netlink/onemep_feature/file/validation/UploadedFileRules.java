package com.netlink.onemep_feature.file.validation;

import com.netlink.onemep_feature.exception.ApplicationException;
import java.util.Locale;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/**
 * File-level validation for design uploads (ONEMEP-39).
 *
 * <p>The supported-type catalogue is PROVISIONAL. ONEMEP-39 defers it to "the application's
 * document-management configuration", which has not been supplied; these are the types the ticket
 * itself names. Widen via configuration once the business confirms the real list.
 */
public final class UploadedFileRules {
  private UploadedFileRules() {}

  /** Matches the service's configured multipart ceiling. */
  public static final long MAX_FILE_SIZE_BYTES = 150L * 1024 * 1024;

  private static final Set<String> ALLOWED_EXTENSIONS =
      Set.of("pdf", "doc", "docx", "xls", "xlsx", "csv", "dwg", "dxf", "zip", "png", "jpg", "jpeg");

  /** Validates one selected file, naming it in the message as the ticket's wording requires. */
  public static void validate(MultipartFile file) {
    String name = originalFilename(file);

    if (file.isEmpty() || file.getSize() <= 0) {
      throw new ApplicationException(name + " is empty and cannot be uploaded.");
    }
    if (file.getSize() > MAX_FILE_SIZE_BYTES) {
      throw new ApplicationException(name + " exceeds the maximum file size of 150 MB.");
    }
    if (!ALLOWED_EXTENSIONS.contains(extensionOf(name))) {
      throw new ApplicationException(name + " is not a supported file type.");
    }
  }

  public static String originalFilename(MultipartFile file) {
    String raw = file.getOriginalFilename();
    if (raw == null || raw.isBlank()) {
      return "(unnamed file)";
    }
    // Some clients send a full path; keep only the leaf, and never trust it as a path afterwards.
    String leaf = raw.replace('\\', '/');
    int slash = leaf.lastIndexOf('/');
    return slash >= 0 ? leaf.substring(slash + 1) : leaf;
  }

  /**
   * Derived from the filename, never chosen by the user — ONEMEP-39 requires the type badge to
   * reflect what was actually uploaded.
   */
  public static String extensionOf(String filename) {
    int dot = filename.lastIndexOf('.');
    if (dot < 0 || dot == filename.length() - 1) {
      return "";
    }
    return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  /** Display name for the logical file: the filename without its extension. */
  public static String displayNameOf(String filename) {
    int dot = filename.lastIndexOf('.');
    String base = dot > 0 ? filename.substring(0, dot) : filename;
    return base.isBlank() ? filename : base;
  }
}
