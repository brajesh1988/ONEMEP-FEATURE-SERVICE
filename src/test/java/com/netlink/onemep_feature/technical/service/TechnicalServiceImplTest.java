package com.netlink.onemep_feature.technical.service;

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
import com.netlink.onemep_feature.technical.dto.TechnicalDto;
import com.netlink.onemep_feature.technical.model.TechnicalMaster;
import com.netlink.onemep_feature.technical.repo.TechnicalMasterRepo;
import com.netlink.onemep_feature.unit.model.UnitMaster;
import com.netlink.onemep_feature.unit.repo.UnitRepo;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Business-logic unit tests for technical master (provisional ONEMEP-29). */
@ExtendWith(MockitoExtension.class)
class TechnicalServiceImplTest {

  @Mock private TechnicalMasterRepo technicalMasterRepo;
  @Mock private UnitRepo unitRepo;
  private TechnicalServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new TechnicalServiceImpl(technicalMasterRepo, unitRepo, new ApiResponseAdaptor());
  }

  @Test
  void create_withUnit_resolvesUnitAndPersists() {
    UnitMaster unit = new UnitMaster();
    unit.setId(2L);
    unit.setSymbol("kg");
    when(technicalMasterRepo.findByNameIgnoreCase("Max Load")).thenReturn(Optional.empty());
    when(unitRepo.findById(2L)).thenReturn(Optional.of(unit));
    when(technicalMasterRepo.save(any(TechnicalMaster.class)))
        .thenAnswer(
            inv -> {
              TechnicalMaster t = inv.getArgument(0);
              t.setId(5L);
              return t;
            });

    ApiResponse<?> response = service.create(new TechnicalDto.CreateRequest("Max Load", 2L, null));

    TechnicalDto.Response data = (TechnicalDto.Response) response.getData();
    assertThat(data.unitId()).isEqualTo(2L);
    assertThat(data.unitSymbol()).isEqualTo("kg");
  }

  @Test
  void create_withoutUnit_isAllowed() {
    when(technicalMasterRepo.findByNameIgnoreCase("NoUnit")).thenReturn(Optional.empty());
    when(technicalMasterRepo.save(any(TechnicalMaster.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    ApiResponse<?> response = service.create(new TechnicalDto.CreateRequest("NoUnit", null, true));

    TechnicalDto.Response data = (TechnicalDto.Response) response.getData();
    assertThat(data.unitId()).isNull();
    assertThat(data.unitSymbol()).isNull();
  }

  @Test
  void create_nonExistentUnit_throwsNotFound() {
    when(technicalMasterRepo.findByNameIgnoreCase("Bad")).thenReturn(Optional.empty());
    when(unitRepo.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(new TechnicalDto.CreateRequest("Bad", 99L, true)))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(technicalMasterRepo, never()).save(any());
  }

  @Test
  void create_duplicateName_throwsDuplicate() {
    when(technicalMasterRepo.findByNameIgnoreCase("Dup"))
        .thenReturn(Optional.of(new TechnicalMaster()));

    assertThatThrownBy(() -> service.create(new TechnicalDto.CreateRequest("Dup", null, true)))
        .isInstanceOf(DuplicateResourceException.class);
  }
}
