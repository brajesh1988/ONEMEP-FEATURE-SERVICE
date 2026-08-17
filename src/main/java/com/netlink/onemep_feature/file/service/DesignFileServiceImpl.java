package com.netlink.onemep_feature.file.service;

import com.netlink.onemep_feature.activity.model.ActivityAction;
import com.netlink.onemep_feature.activity.service.DesignActivityService;
import com.netlink.onemep_feature.approval.service.ApprovalService;
import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.storage.FileStorage;
import com.netlink.onemep_feature.common.storage.StorageKey;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.design.model.Design;
import com.netlink.onemep_feature.design.repo.DesignRepo;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import com.netlink.onemep_feature.exception.ResourceInUseException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.file.dto.DesignFileDto;
import com.netlink.onemep_feature.file.model.CommentStatus;
import com.netlink.onemep_feature.file.model.DesignFile;
import com.netlink.onemep_feature.file.model.DesignFileComment;
import com.netlink.onemep_feature.file.model.DesignFileVersion;
import com.netlink.onemep_feature.file.repo.DesignFileCommentRepo;
import com.netlink.onemep_feature.file.repo.DesignFileRepo;
import com.netlink.onemep_feature.file.repo.DesignFileVersionRepo;
import com.netlink.onemep_feature.file.validation.UploadedFileRules;
import com.netlink.onemep_feature.user.client.UserDirectoryClient;
import com.netlink.onemep_feature.user.dto.UserSummary;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * File and version management (ONEMEP-39).
 *
 * <p>The upload paths deliberately do <em>not</em> carry {@code @Transactional}. A single
 * transaction spanning the whole operation would hold a database connection open for the duration
 * of a transfer that may be 150 MB, and would make one bad file in a batch roll back the good ones.
 * Instead each file runs as three steps:
 *
 * <ol>
 *   <li><b>allocate</b> — short transaction: resolve the logical file and take the next revision
 *       number under a row lock;
 *   <li><b>store</b> — no transaction: write the bytes to object storage;
 *   <li><b>persist</b> — short transaction: record the version and make it current.
 * </ol>
 *
 * <p>If step 3 fails, step 2's object is removed so a failed upload leaves no orphan behind.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DesignFileServiceImpl implements DesignFileService {

  private final DesignRepo designRepo;
  private final DesignFileRepo designFileRepo;
  private final DesignFileVersionRepo designFileVersionRepo;
  private final DesignFileCommentRepo designFileCommentRepo;
  private final FileStorage fileStorage;
  private final UserDirectoryClient userDirectoryClient;
  private final DesignActivityService designActivityService;
  private final ApprovalService approvalService;
  private final ApiResponseAdaptor apiResponseAdaptor;
  private final TransactionTemplate transactionTemplate;

  // ── reads ─────────────────────────────────────────────────────────────────

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> listFiles(Long designId) {
    requireDesign(designId);
    List<DesignFileDto.FileSummary> files =
        designFileRepo.findForDesign(designId).stream().map(this::toSummary).toList();
    return apiResponseAdaptor.success(
        files.isEmpty()
            ? "No files have been uploaded for this Design yet."
            : "Files fetched successfully.",
        files);
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> listVersions(Long fileId) {
    DesignFile file = requireFile(fileId);
    List<DesignFileVersion> versions = designFileVersionRepo.findForFile(fileId);
    Map<Long, UserSummary> users = resolveUploaders(versions);

    List<DesignFileDto.VersionView> views =
        versions.stream().map(v -> toVersionView(file, v, users)).toList();
    return apiResponseAdaptor.success("File versions fetched successfully.", views);
  }

  @Override
  @Transactional(readOnly = true)
  public DesignFileVersion requireVersionForDownload(Long fileId, Long versionId) {
    requireFile(fileId);
    return designFileVersionRepo
        .findByIdAndFile(versionId, fileId)
        .orElseThrow(
            () -> new ResourceNotFoundException("This file version is no longer available."));
  }

  // ── uploads ───────────────────────────────────────────────────────────────

  @Override
  public ApiResponse<?> upload(Long designId, List<MultipartFile> files, String note) {
    if (files == null || files.isEmpty()) {
      throw new ApplicationException("Choose one or more files to upload.");
    }
    transactionTemplate.execute(status -> requireDesign(designId));

    String trimmedNote = trimToNull(note);
    Set<String> seenInBatch = new java.util.HashSet<>();
    List<DesignFileDto.UploadResult> results = new ArrayList<>();

    for (MultipartFile file : files) {
      String filename = UploadedFileRules.originalFilename(file);
      try {
        // ONEMEP-39: the same file selected twice in one batch is caught before anything is
        // written.
        if (!seenInBatch.add(filename.toLowerCase(Locale.ROOT))) {
          throw new DuplicateResourceException(filename + " has already been selected.");
        }
        UploadedFileRules.validate(file);
        results.add(storeNewLogicalFile(designId, file, filename, trimmedNote));
      } catch (RuntimeException e) {
        log.warn("Upload failed for {} on designId={}: {}", filename, designId, e.getMessage());
        results.add(new DesignFileDto.UploadResult(filename, false, null, null, e.getMessage()));
      }
    }

    return apiResponseAdaptor.success(summaryMessage(results), summarise(results));
  }

  @Override
  public ApiResponse<?> uploadNewVersion(Long fileId, MultipartFile file, String note) {
    if (file == null || file.isEmpty()) {
      throw new ApplicationException("Choose one or more files to upload.");
    }
    String filename = UploadedFileRules.originalFilename(file);
    UploadedFileRules.validate(file);

    Allocation allocation =
        transactionTemplate.execute(status -> allocateExisting(fileId, filename));

    DesignFileVersion version = storeAndPersist(allocation, file, trimToNull(note));
    return apiResponseAdaptor.success(
        "File uploaded successfully.",
        new DesignFileDto.UploadResult(
            filename, true, allocation.fileId(), version.getRevisionLabel(), null));
  }

  private DesignFileDto.UploadResult storeNewLogicalFile(
      Long designId, MultipartFile file, String filename, String note) {
    Allocation allocation = transactionTemplate.execute(status -> allocateNew(designId, filename));
    DesignFileVersion version = storeAndPersist(allocation, file, note);
    return new DesignFileDto.UploadResult(
        filename, true, allocation.fileId(), version.getRevisionLabel(), null);
  }

  /** Step 2 and 3, with the object removed again if the metadata never commits. */
  private DesignFileVersion storeAndPersist(
      Allocation allocation, MultipartFile file, String note) {
    String filename = UploadedFileRules.originalFilename(file);

    try (InputStream in = file.getInputStream()) {
      fileStorage.put(allocation.key(), in, file.getSize(), file.getContentType());
    } catch (IOException e) {
      throw new ApplicationException(
          filename + " could not be processed. Verify the file and try again.");
    }

    try {
      return transactionTemplate.execute(status -> persistVersion(allocation, file, note));
    } catch (RuntimeException e) {
      // The bytes are already in storage but their metadata will never commit — remove them rather
      // than leaving an object nothing references.
      safeDelete(allocation.key());
      throw e;
    }
  }

  /** Step 1 for a brand-new logical file. */
  private Allocation allocateNew(Long designId, String filename) {
    Design design = requireDesign(designId);
    String displayName = UploadedFileRules.displayNameOf(filename);
    String normalized = displayName.toLowerCase(Locale.ROOT);

    designFileRepo
        .findByDesignAndNormalizedName(designId, normalized)
        .ifPresent(
            existing -> {
              throw new DuplicateResourceException(
                  "A file with this name already exists on the Design. Upload it as a new version"
                      + " instead.");
            });

    DesignFile designFile = new DesignFile();
    designFile.setDesign(design);
    designFile.setDisplayName(displayName);
    designFile.setDisplayNameNormalized(normalized);
    designFile.setCreatedBy(SecurityUtils.getUserId().orElse(null));
    int revisionNo = designFile.allocateRevisionNo();
    DesignFile saved = designFileRepo.save(designFile);

    return new Allocation(
        saved.getId(), designId, revisionNo, keyFor(designId, saved.getId(), revisionNo));
  }

  /**
   * Step 1 for a further revision. The row lock is what stops two concurrent uploads receiving the
   * same R-number — the second blocks here until the first commits.
   */
  private Allocation allocateExisting(Long fileId, String filename) {
    DesignFile designFile =
        designFileRepo
            .findByIdForUpdate(fileId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "This file is no longer available. Refresh the Design details."));

    int revisionNo = designFile.allocateRevisionNo();
    designFile.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    designFileRepo.save(designFile);

    Long designId = designFile.getDesign().getId();
    return new Allocation(fileId, designId, revisionNo, keyFor(designId, fileId, revisionNo));
  }

  /** Step 3: record the revision and promote it to Current. */
  private DesignFileVersion persistVersion(Allocation allocation, MultipartFile file, String note) {
    DesignFile designFile =
        designFileRepo
            .findById(allocation.fileId())
            .orElseThrow(() -> new ResourceNotFoundException("This file is no longer available."));

    String filename = UploadedFileRules.originalFilename(file);

    DesignFileVersion version = new DesignFileVersion();
    version.setFile(designFile);
    version.setRevisionNo(allocation.revisionNo());
    version.setRevisionLabel(DesignFileVersion.labelFor(allocation.revisionNo()));
    version.setStorageKey(allocation.key().value());
    version.setOriginalFilename(filename);
    version.setFileExtension(UploadedFileRules.extensionOf(filename));
    version.setContentType(file.getContentType());
    version.setSizeBytes(file.getSize());
    version.setNote(note);
    version.setCreatedBy(SecurityUtils.getUserId().orElse(null));
    DesignFileVersion saved = designFileVersionRepo.save(version);

    // Promoting the new revision is the only thing that touches earlier ones — and it only moves a
    // pointer. No previous version row is modified.
    designFile.setCurrentVersion(saved);
    designFileRepo.save(designFile);

    boolean first = allocation.revisionNo() == 0;
    designActivityService.record(
        designFile.getDesign(),
        first ? ActivityAction.FILE_UPLOADED : ActivityAction.FILE_VERSION_UPLOADED,
        first
            ? "Uploaded '" + filename + "'"
            : "Uploaded " + saved.getRevisionLabel() + " of '" + designFile.getDisplayName() + "'");

    return saved;
  }

  // ── deletion ──────────────────────────────────────────────────────────────

  @Override
  @Transactional
  public ApiResponse<?> deleteFile(Long fileId) {
    DesignFile file = requireFile(fileId);
    requireNoPendingApproval(file);

    // Keys only — loading the version entities would leave managed children in the persistence
    // context pointing at a parent this method is about to remove, which fails on flush.
    List<StorageKey> keys =
        designFileVersionRepo.findStorageKeysForFile(fileId).stream().map(StorageKey::new).toList();
    String displayName = file.getDisplayName();
    Design design = file.getDesign();

    // Break the pointer first, or the FK from design_file to its current version blocks the delete.
    file.setCurrentVersion(null);
    designFileRepo.saveAndFlush(file);

    // Children first, in dependency order: a comment references a version, a version references the
    // file, and neither mapping cascades — the cascades live in the schema, which Hibernate does
    // not know about.
    designFileCommentRepo.deleteAllForFile(fileId);
    designFileVersionRepo.deleteAllForFile(fileId);
    designFileRepo.delete(file);
    designFileRepo.flush();

    designActivityService.record(
        design, ActivityAction.FILE_DELETED, "Deleted '" + displayName + "' and all its versions");

    // Objects are removed only once the metadata delete has committed. An object left behind by a
    // failure here is harmless and reclaimable; a row pointing at bytes that are gone is not.
    registerAfterCommit(keys);

    return apiResponseAdaptor.success("File deleted successfully.");
  }

  @Override
  @Transactional
  public ApiResponse<?> deleteVersion(Long fileId, Long versionId) {
    DesignFile file = requireFile(fileId);
    DesignFileVersion version =
        designFileVersionRepo
            .findByIdAndFile(versionId, fileId)
            .orElseThrow(
                () -> new ResourceNotFoundException("This file version is no longer available."));

    if (file.getCurrentVersion() != null
        && Objects.equals(file.getCurrentVersion().getId(), versionId)) {
      throw new ResourceInUseException(
          "The current version cannot be deleted. Delete the file instead.");
    }
    if (designFileCommentRepo.countForVersion(versionId) > 0) {
      throw new ResourceInUseException(
          "This version is referenced by workflow history and cannot be deleted.");
    }
    requireNoPendingApproval(file);

    StorageKey key = version.key();
    String label = version.getRevisionLabel();
    designFileVersionRepo.delete(version);

    designActivityService.record(
        file.getDesign(),
        ActivityAction.FILE_VERSION_DELETED,
        "Deleted version " + label + " of '" + file.getDisplayName() + "'");

    registerAfterCommit(List.of(key));
    return apiResponseAdaptor.success("File version deleted successfully.");
  }

  /** ONEMEP-39 blocks deletion while an Approval Request is pending, for a file or any version. */
  private void requireNoPendingApproval(DesignFile file) {
    if (approvalService.hasPendingApproval(file.getId())) {
      throw new ResourceInUseException(
          "This file has a Pending Approval Request. Complete, recall, or cancel the request before"
              + " deleting the file.");
    }
  }

  // ── comments ──────────────────────────────────────────────────────────────

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> listComments(Long fileId) {
    DesignFile file = requireFile(fileId);
    List<DesignFileComment> comments = designFileCommentRepo.findForFile(fileId);
    Map<Long, UserSummary> authors =
        resolveUsers(comments.stream().map(DesignFileComment::getCreatedBy).toList());

    // Grouped by the revision they were raised against, newest revision first.
    Map<Long, List<DesignFileComment>> byVersion =
        comments.stream()
            .collect(
                Collectors.groupingBy(
                    c -> c.getVersion().getId(), LinkedHashMap::new, Collectors.toList()));

    List<DesignFileDto.VersionComments> grouped =
        designFileVersionRepo.findForFile(fileId).stream()
            .map(
                version ->
                    new DesignFileDto.VersionComments(
                        version.getId(),
                        version.getRevisionLabel(),
                        isCurrent(file, version),
                        byVersion.getOrDefault(version.getId(), List.of()).stream()
                            .map(c -> toCommentView(c, authors))
                            .toList()))
            .toList();

    return apiResponseAdaptor.success("File comments fetched successfully.", grouped);
  }

  @Override
  @Transactional
  public ApiResponse<?> addComment(
      Long fileId, Long versionId, DesignFileDto.AddCommentRequest request) {
    DesignFile file = requireFile(fileId);
    DesignFileVersion version =
        designFileVersionRepo
            .findByIdAndFile(versionId, fileId)
            .orElseThrow(
                () -> new ResourceNotFoundException("This file version is no longer available."));

    String body = request.body() == null ? "" : request.body().trim();
    if (body.isEmpty()) {
      throw new ApplicationException("Enter a comment.");
    }

    DesignFileComment comment = new DesignFileComment();
    comment.setVersion(version);
    comment.setBody(body);
    comment.setStatus(CommentStatus.OPEN);
    comment.setCreatedBy(SecurityUtils.getUserId().orElse(null));
    designFileCommentRepo.save(comment);

    designActivityService.record(
        file.getDesign(),
        ActivityAction.FILE_COMMENT_ADDED,
        "Comment added on " + version.getRevisionLabel() + " of '" + file.getDisplayName() + "'");

    return apiResponseAdaptor.success("Comment added successfully.");
  }

  @Override
  @Transactional
  public ApiResponse<?> updateComment(Long commentId, DesignFileDto.UpdateCommentRequest request) {
    DesignFileComment comment =
        designFileCommentRepo
            .findById(commentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("This comment is no longer available."));

    if (request.status() == null || request.status() == comment.getStatus()) {
      return apiResponseAdaptor.success("No changes to save.");
    }

    comment.setStatus(request.status());
    comment.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    designFileCommentRepo.save(comment);

    DesignFile file = comment.getVersion().getFile();
    designActivityService.record(
        file.getDesign(),
        ActivityAction.FILE_COMMENT_RESOLVED,
        "Comment "
            + (request.status() == CommentStatus.CLOSED ? "resolved" : "reopened")
            + " on "
            + comment.getVersion().getRevisionLabel()
            + " of '"
            + file.getDisplayName()
            + "'");

    return apiResponseAdaptor.success("Comment updated successfully.");
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  /** What step 1 hands to steps 2 and 3. */
  private record Allocation(Long fileId, Long designId, int revisionNo, StorageKey key) {}

  private static StorageKey keyFor(Long designId, Long fileId, int revisionNo) {
    return StorageKey.of("designs", designId, "files", fileId, "r" + revisionNo);
  }

  private Design requireDesign(Long designId) {
    return designRepo
        .findById(designId)
        .orElseThrow(() -> new ResourceNotFoundException("This Design is no longer available."));
  }

  private DesignFile requireFile(Long fileId) {
    return designFileRepo
        .findById(fileId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "This file is no longer available. Refresh the Design details."));
  }

  private static boolean isCurrent(DesignFile file, DesignFileVersion version) {
    return file.getCurrentVersion() != null
        && Objects.equals(file.getCurrentVersion().getId(), version.getId());
  }

  /** Deferred until the surrounding transaction commits, so a rollback keeps the bytes. */
  private void registerAfterCommit(List<StorageKey> keys) {
    org.springframework.transaction.support.TransactionSynchronizationManager
        .registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
              @Override
              public void afterCommit() {
                keys.forEach(DesignFileServiceImpl.this::safeDelete);
              }
            });
  }

  /** Storage cleanup must never be the reason an operation reports failure. */
  private void safeDelete(StorageKey key) {
    try {
      fileStorage.delete(key);
    } catch (RuntimeException e) {
      log.error("Orphaned object left in storage at key {}", key, e);
    }
  }

  private static String trimToNull(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim();
    return value.isEmpty() ? null : value;
  }

  private static DesignFileDto.UploadSummary summarise(List<DesignFileDto.UploadResult> results) {
    int uploaded = (int) results.stream().filter(DesignFileDto.UploadResult::uploaded).count();
    return new DesignFileDto.UploadSummary(
        results.size(), uploaded, results.size() - uploaded, results);
  }

  private static String summaryMessage(List<DesignFileDto.UploadResult> results) {
    int uploaded = (int) results.stream().filter(DesignFileDto.UploadResult::uploaded).count();
    if (uploaded == results.size()) {
      return results.size() == 1
          ? "File uploaded successfully."
          : results.size() + " files uploaded successfully.";
    }
    if (uploaded == 0) {
      return "No files were uploaded. Review the errors and try again.";
    }
    return uploaded + " of " + results.size() + " files uploaded successfully.";
  }

  private Map<Long, UserSummary> resolveUploaders(List<DesignFileVersion> versions) {
    return resolveUsers(versions.stream().map(DesignFileVersion::getCreatedBy).toList());
  }

  private Map<Long, UserSummary> resolveUsers(List<Long> ids) {
    List<Long> present = ids.stream().filter(Objects::nonNull).distinct().toList();
    return present.isEmpty() ? Map.of() : userDirectoryClient.resolve(present);
  }

  private static String nameOf(Map<Long, UserSummary> users, Long userId) {
    if (userId == null) {
      return "System";
    }
    UserSummary summary = users.get(userId);
    return summary == null ? UserSummary.unknown(userId).displayName() : summary.displayName();
  }

  private DesignFileDto.FileSummary toSummary(DesignFile file) {
    DesignFileVersion current = file.getCurrentVersion();
    return new DesignFileDto.FileSummary(
        file.getId(),
        file.getDisplayName(),
        current == null ? null : current.getRevisionLabel(),
        current == null ? null : current.getFileExtension(),
        designFileVersionRepo.countForFile(file.getId()),
        designFileCommentRepo.countOpenForFile(file.getId()),
        file.getUpdatedDate());
  }

  private DesignFileDto.VersionView toVersionView(
      DesignFile file, DesignFileVersion version, Map<Long, UserSummary> users) {
    return new DesignFileDto.VersionView(
        version.getId(),
        version.getRevisionLabel(),
        isCurrent(file, version),
        version.getOriginalFilename(),
        version.getFileExtension(),
        version.getContentType(),
        version.getSizeBytes(),
        version.getNote(),
        nameOf(users, version.getCreatedBy()),
        version.getCreatedBy(),
        version.getCreatedDate(),
        designFileCommentRepo.countForVersion(version.getId()));
  }

  private static DesignFileDto.CommentView toCommentView(
      DesignFileComment comment, Map<Long, UserSummary> authors) {
    return new DesignFileDto.CommentView(
        comment.getId(),
        comment.getBody(),
        comment.getStatus(),
        nameOf(authors, comment.getCreatedBy()),
        comment.getCreatedBy(),
        comment.getCreatedDate());
  }
}
