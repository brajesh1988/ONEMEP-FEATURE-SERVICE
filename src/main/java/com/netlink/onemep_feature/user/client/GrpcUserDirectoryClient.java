package com.netlink.onemep_feature.user.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.netlink.kofax_identity_service.grpc.UserResponse;
import com.netlink.kofax_identity_service.grpc.UserServiceGrpc;
import com.netlink.kofax_identity_service.grpc.UsersRequest;
import com.netlink.kofax_identity_service.grpc.UsersResponse;
import com.netlink.onemep_feature.user.dto.UserSummary;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

/**
 * gRPC-backed {@link UserDirectoryClient}. Resolves ids against the identity service's {@code
 * UserService.GetUsersByIds} batch RPC, caches results briefly (names change rarely), and degrades
 * gracefully behind a circuit breaker so a slow/down identity service never breaks feature reads.
 */
@Service
@Slf4j
public class GrpcUserDirectoryClient implements UserDirectoryClient {

  private static final long RPC_DEADLINE_MS = 3_000L;

  @GrpcClient("identity-service")
  private UserServiceGrpc.UserServiceBlockingStub userStub;

  private final Cache<Long, UserSummary> cache;
  private final CircuitBreaker circuitBreaker;

  public GrpcUserDirectoryClient() {
    this(
        Caffeine.newBuilder().maximumSize(5_000).expireAfterWrite(Duration.ofSeconds(60)).build(),
        CircuitBreaker.of(
            "userDirectory",
            CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .build()));
  }

  /** Test seam: inject the stub, cache, and circuit breaker directly. */
  GrpcUserDirectoryClient(
      UserServiceGrpc.UserServiceBlockingStub userStub,
      Cache<Long, UserSummary> cache,
      CircuitBreaker circuitBreaker) {
    this.userStub = userStub;
    this.cache = cache;
    this.circuitBreaker = circuitBreaker;
  }

  private GrpcUserDirectoryClient(Cache<Long, UserSummary> cache, CircuitBreaker circuitBreaker) {
    this.cache = cache;
    this.circuitBreaker = circuitBreaker;
  }

  @Override
  public Map<Long, UserSummary> resolve(Collection<Long> ids) {
    Set<Long> distinct = distinctNonNull(ids);
    if (distinct.isEmpty()) {
      return Map.of();
    }
    Map<Long, UserSummary> result = new HashMap<>();
    List<Long> misses = new ArrayList<>();
    for (Long id : distinct) {
      UserSummary cached = cache.getIfPresent(id);
      if (cached != null) {
        result.put(id, cached);
      } else {
        misses.add(id);
      }
    }
    if (!misses.isEmpty()) {
      try {
        Map<Long, UserSummary> fetched = fetch(misses);
        fetched.forEach(
            (id, summary) -> {
              cache.put(id, summary);
              result.put(id, summary);
            });
      } catch (RuntimeException ex) {
        // Read path: swallow and let callers fall back to UserSummary.unknown(id).
        log.warn(
            "User directory lookup failed for {} id(s); names will fall back. cause={}",
            misses.size(),
            ex.toString());
      }
    }
    return result;
  }

  @Override
  public Set<Long> findMissing(Collection<Long> ids) {
    Set<Long> distinct = distinctNonNull(ids);
    if (distinct.isEmpty()) {
      return Set.of();
    }
    List<Long> unresolved = new ArrayList<>();
    for (Long id : distinct) {
      if (cache.getIfPresent(id) == null) {
        unresolved.add(id);
      }
    }
    if (unresolved.isEmpty()) {
      return Set.of();
    }
    Map<Long, UserSummary> fetched;
    try {
      fetched = fetch(unresolved);
    } catch (RuntimeException ex) {
      // Write path: cannot validate because identity is unreachable. Do NOT block the write —
      // the database foreign key to user_master is the ultimate integrity guard.
      log.warn("User existence check skipped (directory unavailable): {}", ex.toString());
      return Set.of();
    }
    fetched.forEach(cache::put);
    Set<Long> missing = new LinkedHashSet<>();
    for (Long id : unresolved) {
      if (!fetched.containsKey(id)) {
        missing.add(id);
      }
    }
    return missing;
  }

  private Map<Long, UserSummary> fetch(Collection<Long> ids) {
    return circuitBreaker.executeSupplier(
        () -> {
          UsersResponse response =
              userStub
                  .withDeadlineAfter(RPC_DEADLINE_MS, TimeUnit.MILLISECONDS)
                  .getUsersByIds(UsersRequest.newBuilder().addAllUserIds(ids).build());
          Map<Long, UserSummary> map = new HashMap<>();
          for (UserResponse user : response.getUsersList()) {
            map.put(user.getUserId(), toSummary(user));
          }
          return map;
        });
  }

  private static UserSummary toSummary(UserResponse user) {
    String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
    String last = user.getLastName() == null ? "" : user.getLastName().trim();
    String fullName = (first + " " + last).trim();
    String email =
        user.getEmail() == null || user.getEmail().isBlank() ? null : user.getEmail().trim();
    String display =
        !fullName.isEmpty() ? fullName : email != null ? email : "User #" + user.getUserId();
    return new UserSummary(user.getUserId(), display, email);
  }

  private static Set<Long> distinctNonNull(Collection<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return Set.of();
    }
    Set<Long> distinct = new LinkedHashSet<>();
    for (Long id : ids) {
      if (id != null) {
        distinct.add(id);
      }
    }
    return distinct;
  }
}
