package com.netlink.onemep_feature.designimport.service;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk Design import (ONEMEP-35).
 *
 * <p>Two operations because the work is asynchronous: one to hand the files over, one to ask how it
 * went. There is no synchronous variant, and deliberately so — parsing several 150 MB spreadsheets
 * inside a request holds a connection open for minutes and times out at the gateway.
 */
public interface DesignImportService {

  /**
   * Accepts a batch and queues it. Returns as soon as the bytes are safely stored, before any row
   * has been read, so the caller gets a batch id rather than a result.
   */
  ApiResponse<?> submit(Long projectId, List<MultipartFile> files);

  /** Per-file state and per-row validation errors for a submitted batch. */
  ApiResponse<?> status(Long batchId);
}
