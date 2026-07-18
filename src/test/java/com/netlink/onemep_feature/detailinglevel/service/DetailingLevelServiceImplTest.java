package com.netlink.onemep_feature.detailinglevel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.detailinglevel.dto.DetailingLevelDto;
import com.netlink.onemep_feature.detailinglevel.model.DetailingLevelMaster;
import com.netlink.onemep_feature.detailinglevel.repo.DetailingLevelRepo;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for the Detailing Level master (ONEMEP-13/14/15 supporting data). */
@ExtendWith(MockitoExtension.class)
class DetailingLevelServiceImplTest {

  @Mock private DetailingLevelRepo repo;
  private DetailingLevelServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new DetailingLevelServiceImpl(repo, new ApiResponseAdaptor());
  }

  @Test
  void create_trimsName_defaultsActive_andPersists() {
    when(repo.findByNameIgnoreCase("LOD 400")).thenReturn(Optional.empty());
    when(repo.save(any(DetailingLevelMaster.class)))
        .thenAnswer(
            inv -> {
              DetailingLevelMaster d = inv.getArgument(0);
              d.setId(5L);
              return d;
            });

    ApiResponse<?> response =
        service.create(new DetailingLevelDto.CreateRequest("  LOD 400  ", null));

    DetailingLevelDto.Response data = (DetailingLevelDto.Response) response.getData();
    assertThat(data.id()).isEqualTo(5L);
    assertThat(data.name()).isEqualTo("LOD 400");
    assertThat(data.active()).isTrue();
  }

  @Test
  void create_duplicateName_throwsDuplicate() {
    when(repo.findByNameIgnoreCase("LOD 400")).thenReturn(Optional.of(new DetailingLevelMaster()));

    assertThatThrownBy(() -> service.create(new DetailingLevelDto.CreateRequest("LOD 400", true)))
        .isInstanceOf(DuplicateResourceException.class);
    verify(repo, never()).save(any());
  }

  @Test
  void update_missing_throwsNotFound() {
    when(repo.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update(99L, new DetailingLevelDto.UpdateRequest("X", true)))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
