package com.netlink.onemep_feature.user.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.netlink.kofax_identity_service.grpc.UserResponse;
import com.netlink.kofax_identity_service.grpc.UserServiceGrpc;
import com.netlink.kofax_identity_service.grpc.UsersRequest;
import com.netlink.kofax_identity_service.grpc.UsersResponse;
import com.netlink.onemep_feature.user.dto.UserSummary;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Edge-case coverage for the gRPC user-directory client: mapping, caching, and degradation. */
class GrpcUserDirectoryClientTest {

  private UserServiceGrpc.UserServiceBlockingStub stub;
  private Cache<Long, UserSummary> cache;
  private GrpcUserDirectoryClient client;

  @BeforeEach
  void setUp() {
    stub = mock(UserServiceGrpc.UserServiceBlockingStub.class);
    when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
    cache = Caffeine.newBuilder().maximumSize(100).build();
    client = new GrpcUserDirectoryClient(stub, cache, CircuitBreaker.ofDefaults("test"));
  }

  @Test
  void resolve_mapsFirstAndLastNameToDisplayName() {
    when(stub.getUsersByIds(any()))
        .thenReturn(
            UsersResponse.newBuilder()
                .addUsers(user(63L, "Ada", "Lovelace", "ada@onemep.local"))
                .addUsers(user(2L, "Alan", "Turing", "alan@onemep.local"))
                .build());

    Map<Long, UserSummary> result = client.resolve(List.of(63L, 2L));

    assertThat(result.get(63L).displayName()).isEqualTo("Ada Lovelace");
    assertThat(result.get(63L).email()).isEqualTo("ada@onemep.local");
    assertThat(result.get(2L).displayName()).isEqualTo("Alan Turing");
  }

  @Test
  void resolve_blankNames_fallsBackToEmailThenId() {
    when(stub.getUsersByIds(any()))
        .thenReturn(
            UsersResponse.newBuilder()
                .addUsers(user(5L, "", "", "only-email@onemep.local"))
                .addUsers(user(6L, "", "", ""))
                .build());

    Map<Long, UserSummary> result = client.resolve(List.of(5L, 6L));

    assertThat(result.get(5L).displayName()).isEqualTo("only-email@onemep.local");
    assertThat(result.get(6L).displayName()).isEqualTo("User #6");
    assertThat(result.get(6L).email()).isNull();
  }

  @Test
  void resolve_secondCall_servedFromCache() {
    when(stub.getUsersByIds(any()))
        .thenReturn(UsersResponse.newBuilder().addUsers(user(1L, "A", "B", "a@b.io")).build());

    client.resolve(List.of(1L));
    client.resolve(List.of(1L));

    verify(stub, times(1)).getUsersByIds(any());
  }

  @Test
  void resolve_onlyFetchesUncachedIds() {
    when(stub.getUsersByIds(any()))
        .thenReturn(UsersResponse.newBuilder().addUsers(user(1L, "A", "B", "a@b.io")).build());
    client.resolve(List.of(1L)); // warms cache for id 1

    when(stub.getUsersByIds(any()))
        .thenReturn(UsersResponse.newBuilder().addUsers(user(9L, "C", "D", "c@d.io")).build());
    Map<Long, UserSummary> result = client.resolve(List.of(1L, 9L));

    assertThat(result).containsKeys(1L, 9L);
    verify(stub, times(1)).getUsersByIds(argThatContainsOnly(9L));
  }

  @Test
  void resolve_ignoresNullAndDuplicateIds() {
    when(stub.getUsersByIds(any()))
        .thenReturn(UsersResponse.newBuilder().addUsers(user(1L, "A", "B", "a@b.io")).build());

    Map<Long, UserSummary> result = client.resolve(java.util.Arrays.asList(1L, null, 1L));

    assertThat(result).containsOnlyKeys(1L);
  }

  @Test
  void resolve_emptyInput_shortCircuits() {
    assertThat(client.resolve(List.of())).isEmpty();
    verify(stub, times(0)).getUsersByIds(any());
  }

  @Test
  void resolve_whenIdentityDown_returnsEmptySoCallersFallBack() {
    when(stub.getUsersByIds(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

    assertThat(client.resolve(List.of(1L, 2L))).isEmpty();
  }

  @Test
  void findMissing_returnsIdsWithNoUser() {
    when(stub.getUsersByIds(any()))
        .thenReturn(UsersResponse.newBuilder().addUsers(user(1L, "A", "B", "a@b.io")).build());

    Set<Long> missing = client.findMissing(List.of(1L, 2L));

    assertThat(missing).containsExactly(2L);
  }

  @Test
  void findMissing_allPresent_returnsEmpty() {
    when(stub.getUsersByIds(any()))
        .thenReturn(
            UsersResponse.newBuilder()
                .addUsers(user(1L, "A", "B", "a@b.io"))
                .addUsers(user(2L, "C", "D", "c@d.io"))
                .build());

    assertThat(client.findMissing(List.of(1L, 2L))).isEmpty();
  }

  @Test
  void findMissing_whenIdentityDown_returnsEmptyToNotBlockWrites() {
    when(stub.getUsersByIds(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

    assertThat(client.findMissing(List.of(1L, 2L))).isEmpty();
  }

  @Test
  void findMissing_emptyInput_returnsEmpty() {
    assertThat(client.findMissing(List.of())).isEmpty();
    verify(stub, times(0)).getUsersByIds(any());
  }

  private static UserResponse user(long id, String first, String last, String email) {
    return UserResponse.newBuilder()
        .setUserId(id)
        .setFirstName(first)
        .setLastName(last)
        .setEmail(email)
        .build();
  }

  private static UsersRequest argThatContainsOnly(long id) {
    return org.mockito.ArgumentMatchers.argThat(
        req -> req.getUserIdsList().size() == 1 && req.getUserIdsList().get(0) == id);
  }
}
