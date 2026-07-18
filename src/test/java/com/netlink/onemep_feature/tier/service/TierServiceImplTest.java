package com.netlink.onemep_feature.tier.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.tier.dto.TierDto;
import com.netlink.onemep_feature.tier.model.TierMaster;
import com.netlink.onemep_feature.tier.repo.TierRepo;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Business-logic unit tests for tier creation/edit rules (ONEMEP-16/17/18). */
@ExtendWith(MockitoExtension.class)
class TierServiceImplTest {

  @Mock private TierRepo tierRepo;
  private TierServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new TierServiceImpl(tierRepo, new ApiResponseAdaptor());
  }

  @Test
  void create_trimsName_defaultsActiveTrue_andPersists() {
    when(tierRepo.findByNameIgnoreCase("Tier 1")).thenReturn(Optional.empty());
    when(tierRepo.save(any(TierMaster.class)))
        .thenAnswer(
            inv -> {
              TierMaster t = inv.getArgument(0);
              t.setId(7L);
              return t;
            });

    ApiResponse<?> response = service.create(new TierDto.CreateRequest("  Tier 1  ", null));

    assertThat(response.isSuccess()).isTrue();
    TierDto.Response data = (TierDto.Response) response.getData();
    assertThat(data.id()).isEqualTo(7L);
    assertThat(data.name()).isEqualTo("Tier 1");
    assertThat(data.active()).isTrue();
  }

  @Test
  void create_duplicateName_throwsDuplicate() {
    when(tierRepo.findByNameIgnoreCase("Tier 1")).thenReturn(Optional.of(new TierMaster()));

    assertThatThrownBy(() -> service.create(new TierDto.CreateRequest("Tier 1", true)))
        .isInstanceOf(DuplicateResourceException.class);
    verify(tierRepo, never()).save(any());
  }

  @Test
  void update_missingTier_throwsNotFound() {
    when(tierRepo.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update(99L, new TierDto.UpdateRequest("X", true)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void update_duplicateNameOfAnotherTier_throwsDuplicate() {
    TierMaster existing = tier(3L, "Tier A");
    when(tierRepo.findById(3L)).thenReturn(Optional.of(existing));
    when(tierRepo.findByNameIgnoreCaseAndIdNot("Tier B", 3L))
        .thenReturn(Optional.of(tier(4L, "Tier B")));

    assertThatThrownBy(() -> service.update(3L, new TierDto.UpdateRequest("Tier B", true)))
        .isInstanceOf(DuplicateResourceException.class);
  }

  @Test
  void updateStatus_deactivates_andReturnsMessage() {
    TierMaster existing = tier(3L, "Tier A");
    when(tierRepo.findById(3L)).thenReturn(Optional.of(existing));

    ApiResponse<?> response = service.updateStatus(3L, false);

    assertThat(existing.getActive()).isFalse();
    assertThat(response.getMessage()).contains("deactivated");
  }

  private static TierMaster tier(long id, String name) {
    TierMaster t = new TierMaster();
    t.setId(id);
    t.setName(name);
    t.setActive(true);
    return t;
  }
}
