package com.netlink.onemep_feature.common.storage;

import java.time.LocalDateTime;

/**
 * Receipt for a completed write. The caller persists these fields against the owning row, so the
 * database always knows where the bytes went and how big they were without re-reading them.
 */
public record StoredObject(
    StorageKey key, long sizeBytes, String contentType, LocalDateTime storedAt) {}
