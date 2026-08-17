package com.netlink.onemep_feature.activity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netlink.onemep_feature.activity.model.ActivityAction;
import com.netlink.onemep_feature.activity.model.DesignActivityLog;
import com.netlink.onemep_feature.activity.repo.DesignActivityLogRepo;
import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.design.model.Design;
import com.netlink.onemep_feature.design.repo.DesignRepo;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Audit-entry construction (ONEMEP-43). */
@ExtendWith(MockitoExtension.class)
class DesignActivityServiceImplTest {

  @Mock private DesignActivityLogRepo designActivityLogRepo;
  @Mock private DesignRepo designRepo;
  private DesignActivityServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new DesignActivityServiceImpl(designActivityLogRepo, designRepo, new ApiResponseAdaptor());
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void record_prefersTheNameClaimForTheActorLabel() {
    authenticate(jwtWith("7", "Alex Carter", "alex@onemep.local"));

    service.record(new Design(), ActivityAction.DESIGN_CREATED, "Entry created");

    DesignActivityLog saved = captureSaved();
    assertThat(saved.getActorLabel()).isEqualTo("Alex Carter");
    assertThat(saved.getCreatedBy()).isEqualTo(7L);
    assertThat(saved.getDetail()).isEqualTo("Entry created");
    assertThat(saved.getAction()).isEqualTo(ActivityAction.DESIGN_CREATED);
  }

  @Test
  void record_fallsBackToEmailWhenNoNameClaimIsPresent() {
    authenticate(jwtWith("7", null, "alex@onemep.local"));

    service.record(new Design(), ActivityAction.DESIGN_UPDATED, "Title changed");

    assertThat(captureSaved().getActorLabel()).isEqualTo("alex@onemep.local");
  }

  @Test
  void record_fallsBackToTheUserIdWhenTheTokenCarriesNeither() {
    authenticate(jwtWith("7", null, null));

    service.record(new Design(), ActivityAction.DESIGN_UPDATED, "Title changed");

    assertThat(captureSaved().getActorLabel()).isEqualTo("User 7");
  }

  @Test
  void record_withNoAuthenticatedUser_isAttributedToTheSystem() {
    service.record(new Design(), ActivityAction.STATUS_CHANGED, "Status changed");

    DesignActivityLog saved = captureSaved();
    assertThat(saved.getActorLabel()).isNull();
    assertThat(saved.getCreatedBy()).isNull();
  }

  @Test
  void record_blankDetail_stillProducesAReadableRow() {
    service.record(new Design(), ActivityAction.DESIGN_UPDATED, "   ");

    assertThat(captureSaved().getDetail()).isEqualTo("(no detail recorded)");
  }

  @Test
  void record_overlongDetail_isTruncatedRatherThanFailingTheWrite() {
    // An audit write must never be the thing that breaks a business operation over formatting.
    service.record(new Design(), ActivityAction.DESIGN_UPDATED, "A".repeat(2000));

    String detail = captureSaved().getDetail();
    assertThat(detail).hasSize(1000).endsWith("…");
  }

  @Test
  void list_forAMissingDesign_reportsItIsNoLongerAvailable() {
    when(designRepo.existsById(99L)).thenReturn(false);

    assertThatThrownBy(() -> service.list(99L, null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("no longer available");
  }

  private DesignActivityLog captureSaved() {
    ArgumentCaptor<DesignActivityLog> captor = ArgumentCaptor.forClass(DesignActivityLog.class);
    verify(designActivityLogRepo).save(captor.capture());
    return captor.getValue();
  }

  private static Jwt jwtWith(String subject, String name, String email) {
    Jwt.Builder builder =
        Jwt.withTokenValue("token").header("alg", "none").subject(subject).claim("sub", subject);
    if (name != null) {
      builder.claim("name", name);
    }
    if (email != null) {
      builder.claim("email", email);
    }
    return builder.build();
  }

  private static void authenticate(Jwt jwt) {
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  @Test
  void record_neverDeclaresItsOwnTransaction() throws NoSuchMethodException {
    // The propagation guarantee in ONEMEP-43 depends on record() joining its caller's transaction.
    // An @Transactional added here later would quietly break that, so assert it stays absent.
    assertThat(
            DesignActivityServiceImpl.class
                .getMethod("record", Design.class, ActivityAction.class, String.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class))
        .isNull();
  }
}
