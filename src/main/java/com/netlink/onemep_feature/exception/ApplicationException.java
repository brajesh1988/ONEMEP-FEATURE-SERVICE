package com.netlink.onemep_feature.exception;

import lombok.Getter;

/** Business-rule violation → HTTP 400. Carries a user-facing message. */
@Getter
public class ApplicationException extends RuntimeException {
  public ApplicationException(String message) {
    super(message);
  }
}
