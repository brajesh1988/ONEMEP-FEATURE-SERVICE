package com.netlink.onemep_feature.common.util;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Reads the authenticated principal out of the security context (JWT subject = user id). */
public final class SecurityUtils {
  private SecurityUtils() {}

  public static Optional<Authentication> getAuthentication() {
    return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication());
  }

  public static Optional<Jwt> getJwt() {
    return getAuthentication()
        .filter(auth -> auth instanceof JwtAuthenticationToken)
        .map(auth -> ((JwtAuthenticationToken) auth).getToken());
  }

  /** Current user id, taken from the JWT subject claim. */
  public static Optional<Long> getUserId() {
    return getJwt()
        .map(Jwt::getSubject)
        .filter(sub -> sub != null && sub.matches("\\d+"))
        .map(Long::valueOf);
  }

  public static Optional<String> getEmail() {
    return getJwt().map(jwt -> jwt.getClaimAsString("email"));
  }
}
