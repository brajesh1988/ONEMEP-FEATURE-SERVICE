package com.netlink.onemep_feature.exception;

import lombok.Getter;

/** Entity cannot be deleted/deactivated because it is still referenced → HTTP 409. */
@Getter
public class ResourceInUseException extends RuntimeException {
  public ResourceInUseException(String message) {
    super(message);
  }
}
