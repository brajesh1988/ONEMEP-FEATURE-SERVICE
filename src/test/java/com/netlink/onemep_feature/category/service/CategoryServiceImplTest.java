package com.netlink.onemep_feature.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netlink.onemep_feature.category.dto.CategoryDto;
import com.netlink.onemep_feature.category.model.CategoryMaster;
import com.netlink.onemep_feature.category.repo.CategoryRepo;
import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Business-logic unit tests for category rules incl. prefix uppercasing + number gen. */
@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

  @Mock private CategoryRepo categoryRepo;
  private CategoryServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new CategoryServiceImpl(categoryRepo, new ApiResponseAdaptor());
  }

  @Test
  void create_uppercasesPrefix_andGeneratesIdDerivedNumber() {
    when(categoryRepo.findByNameIgnoreCase("Infra")).thenReturn(Optional.empty());
    when(categoryRepo.findByPrefixIgnoreCase("INF")).thenReturn(Optional.empty());
    when(categoryRepo.saveAndFlush(any(CategoryMaster.class)))
        .thenAnswer(
            inv -> {
              CategoryMaster c = inv.getArgument(0);
              c.setId(42L);
              return c;
            });
    when(categoryRepo.save(any(CategoryMaster.class))).thenAnswer(inv -> inv.getArgument(0));

    ApiResponse<?> response =
        service.create(new CategoryDto.CreateRequest("Infra", "inf", null, null));

    CategoryDto.Response data = (CategoryDto.Response) response.getData();
    assertThat(data.prefix()).isEqualTo("INF");
    assertThat(data.categoryNumber()).isEqualTo("CAT-00042");
    assertThat(data.active()).isTrue();
  }

  @Test
  void create_duplicatePrefix_throwsDuplicate() {
    when(categoryRepo.findByNameIgnoreCase("Infra")).thenReturn(Optional.empty());
    when(categoryRepo.findByPrefixIgnoreCase("INF")).thenReturn(Optional.of(new CategoryMaster()));

    assertThatThrownBy(
            () -> service.create(new CategoryDto.CreateRequest("Infra", "INF", null, true)))
        .isInstanceOf(DuplicateResourceException.class);
    verify(categoryRepo, never()).saveAndFlush(any());
  }

  @Test
  void update_keepsPrefixAndNumberLocked() {
    CategoryMaster existing = new CategoryMaster();
    existing.setId(5L);
    existing.setName("Old");
    existing.setPrefix("INF");
    existing.setCategoryNumber("CAT-00005");
    existing.setActive(true);
    when(categoryRepo.findById(5L)).thenReturn(Optional.of(existing));
    when(categoryRepo.findByNameIgnoreCaseAndIdNot("New Name", 5L)).thenReturn(Optional.empty());
    when(categoryRepo.save(any(CategoryMaster.class))).thenAnswer(inv -> inv.getArgument(0));

    ApiResponse<?> response = service.update(5L, new CategoryDto.UpdateRequest("New Name", true));

    CategoryDto.Response data = (CategoryDto.Response) response.getData();
    assertThat(data.name()).isEqualTo("New Name");
    assertThat(data.prefix()).isEqualTo("INF");
    assertThat(data.categoryNumber()).isEqualTo("CAT-00005");
  }
}
