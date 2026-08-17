package com.netlink.onemep_feature.file.service;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.file.dto.DesignFileDto;
import com.netlink.onemep_feature.file.model.DesignFileVersion;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** Uploaded files and version history (ONEMEP-39). */
public interface DesignFileService {

  ApiResponse<?> listFiles(Long designId);

  /**
   * Uploads one or more files, each becoming its own logical file.
   *
   * <p>Files succeed or fail independently: ONEMEP-39 requires "2 of 3 files uploaded successfully"
   * rather than one bad file discarding the good ones.
   */
  ApiResponse<?> upload(Long designId, List<MultipartFile> files, String note);

  /** Adds the next revision under an existing logical file, leaving earlier ones untouched. */
  ApiResponse<?> uploadNewVersion(Long fileId, MultipartFile file, String note);

  ApiResponse<?> listVersions(Long fileId);

  /** Resolves a version for download; the controller decides between a redirect and a stream. */
  DesignFileVersion requireVersionForDownload(Long fileId, Long versionId);

  ApiResponse<?> deleteFile(Long fileId);

  ApiResponse<?> deleteVersion(Long fileId, Long versionId);

  ApiResponse<?> listComments(Long fileId);

  ApiResponse<?> addComment(Long fileId, Long versionId, DesignFileDto.AddCommentRequest request);

  ApiResponse<?> updateComment(Long commentId, DesignFileDto.UpdateCommentRequest request);
}
