package com.netlink.onemep_feature.lookup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.lookup.dto.LookupDto;
import com.netlink.onemep_feature.lookup.model.LookupType;
import com.netlink.onemep_feature.lookup.model.LookupValue;
import com.netlink.onemep_feature.lookup.repo.LookupValueRepo;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Business-logic unit tests for the reference-data catalogue. The type guard in {@code require*} is
 * the important part: it is what stops a caller passing a Floor id where a Discipline is expected.
 */
@ExtendWith(MockitoExtension.class)
class LookupServiceImplTest {

  @Mock private LookupValueRepo lookupValueRepo;
  private LookupServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new LookupServiceImpl(lookupValueRepo, new ApiResponseAdaptor());
  }

  @Test
  void create_upperCasesAndTrimsCode_defaultsActiveTrue() {
    when(lookupValueRepo.findByTypeAndCode(LookupType.DISCIPLINE, "HVAC"))
        .thenReturn(Optional.empty());
    when(lookupValueRepo.save(any(LookupValue.class)))
        .thenAnswer(
            inv -> {
              LookupValue v = inv.getArgument(0);
              v.setId(11L);
              return v;
            });

    ApiResponse<?> response =
        service.create(
            LookupType.DISCIPLINE,
            new LookupDto.CreateRequest("  hvac  ", "  Mechanical / HVAC  ", null, null));

    LookupDto.Response data = (LookupDto.Response) response.getData();
    assertThat(data.id()).isEqualTo(11L);
    assertThat(data.code()).isEqualTo("HVAC");
    assertThat(data.label()).isEqualTo("Mechanical / HVAC");
    assertThat(data.sortOrder()).isZero();
    assertThat(data.active()).isTrue();
    assertThat(data.type()).isEqualTo("DISCIPLINE");
  }

  @Test
  void create_duplicateCodeWithinSameType_throwsDuplicate() {
    when(lookupValueRepo.findByTypeAndCode(LookupType.DISCIPLINE, "M"))
        .thenReturn(Optional.of(value(1L, LookupType.DISCIPLINE, "M")));

    assertThatThrownBy(
            () ->
                service.create(
                    LookupType.DISCIPLINE,
                    new LookupDto.CreateRequest("m", "Mechanical", null, null)))
        .isInstanceOf(DuplicateResourceException.class);
    verify(lookupValueRepo, never()).save(any());
  }

  @Test
  void update_doesNotChangeLookupType() {
    LookupValue existing = value(4L, LookupType.SUBJECT, "CHW");
    when(lookupValueRepo.findById(4L)).thenReturn(Optional.of(existing));
    when(lookupValueRepo.findByTypeAndCodeExcluding(LookupType.SUBJECT, "HW", 4L))
        .thenReturn(Optional.empty());

    service.update(4L, new LookupDto.UpdateRequest("hw", "Hot Water", 3, false));

    assertThat(existing.getLookupType()).isEqualTo(LookupType.SUBJECT);
    assertThat(existing.getCode()).isEqualTo("HW");
    assertThat(existing.getSortOrder()).isEqualTo(3);
    assertThat(existing.getActive()).isFalse();
  }

  @Test
  void requireActive_idFromAnotherCatalogue_throwsNotFound() {
    when(lookupValueRepo.findById(9L)).thenReturn(Optional.of(value(9L, LookupType.FLOOR, "L01")));

    assertThatThrownBy(() -> service.requireActive(LookupType.DISCIPLINE, 9L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("discipline");
  }

  @Test
  void requireActive_inactiveValue_throwsNoLongerAvailable() {
    LookupValue inactive = value(5L, LookupType.DISCIPLINE, "M");
    inactive.setActive(false);
    when(lookupValueRepo.findById(5L)).thenReturn(Optional.of(inactive));

    assertThatThrownBy(() -> service.requireActive(LookupType.DISCIPLINE, 5L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("no longer available");
  }

  @Test
  void requireActive_activeValueOfCorrectType_returnsIt() {
    when(lookupValueRepo.findById(2L))
        .thenReturn(Optional.of(value(2L, LookupType.DISCIPLINE, "M")));

    assertThat(service.requireActive(LookupType.DISCIPLINE, 2L).getCode()).isEqualTo("M");
  }

  @Test
  void requireAllActive_deduplicatesIdsAndPreservesOrder() {
    when(lookupValueRepo.findAllByTypeAndIdIn(eq(LookupType.DESIGN_TYPE), anyList()))
        .thenReturn(
            List.of(
                value(1L, LookupType.DESIGN_TYPE, "PLN"),
                value(2L, LookupType.DESIGN_TYPE, "SCH")));

    List<LookupValue> resolved =
        service.requireAllActive(LookupType.DESIGN_TYPE, List.of(2L, 1L, 2L));

    assertThat(resolved).extracting(LookupValue::getCode).containsExactly("SCH", "PLN");
  }

  @Test
  void requireAllActive_oneIdMissing_throwsNotFound() {
    when(lookupValueRepo.findAllByTypeAndIdIn(eq(LookupType.DESIGN_TYPE), anyList()))
        .thenReturn(List.of(value(1L, LookupType.DESIGN_TYPE, "PLN")));

    assertThatThrownBy(() -> service.requireAllActive(LookupType.DESIGN_TYPE, List.of(1L, 99L)))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("no longer available");
  }

  @Test
  void requireAllActive_emptyInput_returnsEmptyWithoutQuerying() {
    assertThat(service.requireAllActive(LookupType.SUBJECT, List.of())).isEmpty();
    verify(lookupValueRepo, never()).findAllByTypeAndIdIn(any(), anyList());
  }

  @Test
  void requireActiveByCode_matchesCaseInsensitively() {
    when(lookupValueRepo.findByTypeAndCode(LookupType.STAGE, "DD"))
        .thenReturn(Optional.of(value(3L, LookupType.STAGE, "DD")));

    assertThat(service.requireActiveByCode(LookupType.STAGE, " dd ").getId()).isEqualTo(3L);
  }

  @Test
  void requireActiveByCode_unknownCode_throwsNotConfigured() {
    when(lookupValueRepo.findByTypeAndCode(LookupType.DISCIPLINE, "ZZ"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.requireActiveByCode(LookupType.DISCIPLINE, "ZZ"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("not configured");
  }

  private static LookupValue value(long id, LookupType type, String code) {
    LookupValue v = new LookupValue();
    v.setId(id);
    v.setLookupType(type);
    v.setCode(code);
    v.setLabel(code + " label");
    v.setSortOrder(0);
    v.setActive(true);
    return v;
  }
}
