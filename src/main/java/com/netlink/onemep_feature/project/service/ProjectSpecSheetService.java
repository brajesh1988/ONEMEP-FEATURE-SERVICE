package com.netlink.onemep_feature.project.service;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ProjectSpecSheetService {

  ApiResponse<?> upload(Long projectId, MultipartFile file);

  ApiResponse<?> list(Long projectId);

  DownloadedFile download(Long projectId, Long sheetId);

  ApiResponse<?> delete(Long projectId, Long sheetId);

  /** Internal carrier for a downloaded file (not serialized as JSON). */
  record DownloadedFile(String fileName, String contentType, byte[] data) {}
}
