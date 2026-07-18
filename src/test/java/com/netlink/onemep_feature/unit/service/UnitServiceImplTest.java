package com.netlink.onemep_feature.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import com.netlink.onemep_feature.exception.ResourceInUseException;
import com.netlink.onemep_feature.technical.repo.TechnicalMasterRepo;
import com.netlink.onemep_feature.unit.dto.UnitDto;
import com.netlink.onemep_feature.unit.model.UnitMaster;
import com.netlink.onemep_feature.unit.repo.UnitRepo;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Business-logic unit tests for unit rules incl. enum validation + delete guard (ONEMEP-26/27/28).
 */
@ExtendWith(MockitoExtension.class)
class UnitServiceImplTest {

  @Mock private UnitRepo unitRepo;
  @Mock private TechnicalMasterRepo technicalMasterRepo;
  private UnitServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new UnitServiceImpl(unitRepo, technicalMasterRepo, new ApiResponseAdaptor());
  }

  @Test
  void create_normalizesInputTypeToUpper_andPersists() {
    when(unitRepo.findBySymbolIgnoreCase("kg")).thenReturn(Optional.empty());
    when(unitRepo.save(any(UnitMaster.class)))
        .thenAnswer(
            inv -> {
              UnitMaster u = inv.getArgument(0);
              u.setId(9L);
              return u;
            });

    ApiResponse<?> response =
        service.create(new UnitDto.CreateRequest("Kilogram", "kg", "decimal", null));

    UnitDto.Response data = (UnitDto.Response) response.getData();
    assertThat(data.acceptedInputType()).isEqualTo("DECIMAL");
    assertThat(data.symbol()).isEqualTo("kg");
  }

  @Test
  void create_badInputType_throwsApplicationException() {
    assertThatThrownBy(() -> service.create(new UnitDto.CreateRequest("Bad", "x", "FLOAT", true)))
        .isInstanceOf(ApplicationException.class);
    verify(unitRepo, never()).save(any());
  }

  @Test
  void create_duplicateSymbol_throwsDuplicate() {
    when(unitRepo.findBySymbolIgnoreCase("kg")).thenReturn(Optional.of(new UnitMaster()));

    assertThatThrownBy(
            () -> service.create(new UnitDto.CreateRequest("Kilogram", "kg", "TEXT", true)))
        .isInstanceOf(DuplicateResourceException.class);
  }

  @Test
  void delete_whenReferencedByTechnicalMaster_throwsResourceInUse() {
    UnitMaster unit = new UnitMaster();
    unit.setId(3L);
    when(unitRepo.findById(3L)).thenReturn(Optional.of(unit));
    when(technicalMasterRepo.countByUnit_Id(3L)).thenReturn(2L);

    assertThatThrownBy(() -> service.delete(3L)).isInstanceOf(ResourceInUseException.class);
    verify(unitRepo, never()).delete(any(UnitMaster.class));
  }

  @Test
  void delete_whenUnused_deletes() {
    UnitMaster unit = new UnitMaster();
    unit.setId(3L);
    when(unitRepo.findById(3L)).thenReturn(Optional.of(unit));
    when(technicalMasterRepo.countByUnit_Id(3L)).thenReturn(0L);

    ApiResponse<?> response = service.delete(3L);

    assertThat(response.isSuccess()).isTrue();
    verify(unitRepo).delete(unit);
  }
}
