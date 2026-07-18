package com.netlink.onemep_feature.exception;

import lombok.Getter;

/** Unique-constraint style violation (name/prefix already taken) → HTTP 409. */
@Getter
public class DuplicateResourceException extends RuntimeException {
  public DuplicateResourceException(String message) {
    super(message);
  }
}
