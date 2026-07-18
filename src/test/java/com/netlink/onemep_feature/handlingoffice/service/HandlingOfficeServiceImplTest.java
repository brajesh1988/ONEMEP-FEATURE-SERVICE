package com.netlink.onemep_feature.handlingoffice.service;

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
import com.netlink.onemep_feature.handlingoffice.dto.HandlingOfficeDto;
import com.netlink.onemep_feature.handlingoffice.model.HandlingOfficeMaster;
import com.netlink.onemep_feature.handlingoffice.repo.HandlingOfficeRepo;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for the Handling Office master (ONEMEP-13/14/15 supporting data). */
@ExtendWith(MockitoExtension.class)
class HandlingOfficeServiceImplTest {

  @Mock private HandlingOfficeRepo repo;
  private HandlingOfficeServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new HandlingOfficeServiceImpl(repo, new ApiResponseAdaptor());
  }

  @Test
  void create_trimsName_defaultsActive_andPersists() {
    when(repo.findByNameIgnoreCase("Dubai Office")).thenReturn(Optional.empty());
    when(repo.save(any(HandlingOfficeMaster.class)))
        .thenAnswer(
            inv -> {
              HandlingOfficeMaster h = inv.getArgument(0);
              h.setId(3L);
              return h;
            });

    ApiResponse<?> response =
        service.create(new HandlingOfficeDto.CreateRequest("  Dubai Office  ", null));

    HandlingOfficeDto.Response data = (HandlingOfficeDto.Response) response.getData();
    assertThat(data.id()).isEqualTo(3L);
    assertThat(data.name()).isEqualTo("Dubai Office");
    assertThat(data.active()).isTrue();
  }

  @Test
  void create_duplicateName_throwsDuplicate() {
    when(repo.findByNameIgnoreCase("Dubai Office"))
        .thenReturn(Optional.of(new HandlingOfficeMaster()));

    assertThatThrownBy(
            () -> service.create(new HandlingOfficeDto.CreateRequest("Dubai Office", true)))
        .isInstanceOf(DuplicateResourceException.class);
    verify(repo, never()).save(any());
  }

  @Test
  void update_missing_throwsNotFound() {
    when(repo.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update(99L, new HandlingOfficeDto.UpdateRequest("X", true)))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
