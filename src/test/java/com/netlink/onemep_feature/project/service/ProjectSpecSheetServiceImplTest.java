package com.netlink.onemep_feature.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.project.dto.SpecSheetDto;
import com.netlink.onemep_feature.project.model.ProjectMaster;
import com.netlink.onemep_feature.project.model.ProjectSpecSheet;
import com.netlink.onemep_feature.project.repo.ProjectActivityLogRepo;
import com.netlink.onemep_feature.project.repo.ProjectRepo;
import com.netlink.onemep_feature.project.repo.ProjectSpecSheetRepo;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/** Unit tests for specs-sheet upload rules (ONEMEP-15): extension + size + not-found guards. */
@ExtendWith(MockitoExtension.class)
class ProjectSpecSheetServiceImplTest {

  @Mock private ProjectSpecSheetRepo specSheetRepo;
  @Mock private ProjectRepo projectRepo;
  @Mock private ProjectActivityLogRepo activityRepo;

  private ProjectSpecSheetServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new ProjectSpecSheetServiceImpl(
            specSheetRepo, projectRepo, activityRepo, new ApiResponseAdaptor());
  }

  @Test
  void upload_validPdf_persistsAndReturnsMetadata() {
    when(projectRepo.findById(1L)).thenReturn(Optional.of(project()));
    when(specSheetRepo.save(any(ProjectSpecSheet.class)))
        .thenAnswer(
            inv -> {
              ProjectSpecSheet s = inv.getArgument(0);
              s.setId(50L);
              return s;
            });
    MultipartFile file =
        new MockMultipartFile("file", "design.pdf", "application/pdf", new byte[] {1, 2, 3, 4, 5});

    ApiResponse<?> response = service.upload(1L, file);

    SpecSheetDto.Metadata data = (SpecSheetDto.Metadata) response.getData();
    assertThat(data.id()).isEqualTo(50L);
    assertThat(data.fileName()).isEqualTo("design.pdf");
    assertThat(data.fileExtension()).isEqualTo("pdf");
    assertThat(data.fileSize()).isEqualTo(5L);
  }

  @Test
  void upload_disallowedExtension_throws() {
    when(projectRepo.findById(1L)).thenReturn(Optional.of(project()));
    MultipartFile file =
        new MockMultipartFile("file", "malware.exe", "application/octet-stream", new byte[] {1});

    assertThatThrownBy(() -> service.upload(1L, file)).isInstanceOf(ApplicationException.class);
    verify(specSheetRepo, never()).save(any());
  }

  @Test
  void upload_emptyFile_throws() {
    when(projectRepo.findById(1L)).thenReturn(Optional.of(project()));
    MultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

    assertThatThrownBy(() -> service.upload(1L, file)).isInstanceOf(ApplicationException.class);
    verify(specSheetRepo, never()).save(any());
  }

  @Test
  void upload_overSizeLimit_throws() {
    when(projectRepo.findById(1L)).thenReturn(Optional.of(project()));
    MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getOriginalFilename()).thenReturn("huge.pdf");
    when(file.getSize()).thenReturn(151L * 1024 * 1024);

    assertThatThrownBy(() -> service.upload(1L, file)).isInstanceOf(ApplicationException.class);
    verify(specSheetRepo, never()).save(any());
  }

  @Test
  void download_missing_throwsNotFound() {
    lenient().when(projectRepo.findById(1L)).thenReturn(Optional.of(project()));
    when(specSheetRepo.findByIdAndProject_Id(99L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.download(1L, 99L))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  private static ProjectMaster project() {
    ProjectMaster p = new ProjectMaster();
    p.setId(1L);
    p.setName("Apollo");
    return p;
  }
}
