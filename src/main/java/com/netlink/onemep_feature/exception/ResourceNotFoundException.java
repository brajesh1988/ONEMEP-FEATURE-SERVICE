package com.netlink.onemep_feature.exception;

import lombok.Getter;

/** Requested entity does not exist → HTTP 404. */
@Getter
public class ResourceNotFoundException extends RuntimeException {
  public ResourceNotFoundException(String message) {
    super(message);
  }
}
