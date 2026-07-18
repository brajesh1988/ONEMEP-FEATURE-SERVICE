package com.netlink.onemep_feature.teamrole.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import com.netlink.onemep_feature.exception.ResourceInUseException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.project.repo.ProjectMemberMappingRepo;
import com.netlink.onemep_feature.teamrole.dto.TeamRoleDto;
import com.netlink.onemep_feature.teamrole.model.TeamRoleMaster;
import com.netlink.onemep_feature.teamrole.repo.TeamRoleRepo;
import com.netlink.onemep_feature.tier.model.TierMaster;
import com.netlink.onemep_feature.tier.repo.TierRepo;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Business-logic unit tests for team-role rules incl. tier link + delete guard (ONEMEP-19/20/21).
 */
@ExtendWith(MockitoExtension.class)
class TeamRoleServiceImplTest {

  @Mock private TeamRoleRepo teamRoleRepo;
  @Mock private TierRepo tierRepo;
  @Mock private ProjectMemberMappingRepo projectMemberMappingRepo;
  private TeamRoleServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new TeamRoleServiceImpl(
            teamRoleRepo, tierRepo, projectMemberMappingRepo, new ApiResponseAdaptor());
  }

  @Test
  void create_mapsToTier_andPersists() {
    when(tierRepo.findById(1L)).thenReturn(Optional.of(tier(1L, "Tier 1")));
    when(teamRoleRepo.findByNameIgnoreCase("Lead")).thenReturn(Optional.empty());
    when(teamRoleRepo.save(any(TeamRoleMaster.class)))
        .thenAnswer(
            inv -> {
              TeamRoleMaster r = inv.getArgument(0);
              r.setId(11L);
              return r;
            });

    ApiResponse<?> response = service.create(new TeamRoleDto.CreateRequest("Lead", 1L, null));

    TeamRoleDto.Response data = (TeamRoleDto.Response) response.getData();
    assertThat(data.id()).isEqualTo(11L);
    assertThat(data.tierId()).isEqualTo(1L);
    assertThat(data.tierName()).isEqualTo("Tier 1");
  }

  @Test
  void create_nonExistentTier_throwsNotFound() {
    when(tierRepo.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(new TeamRoleDto.CreateRequest("Ghost", 99L, true)))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(teamRoleRepo, never()).save(any());
  }

  @Test
  void create_duplicateName_throwsDuplicate() {
    when(tierRepo.findById(1L)).thenReturn(Optional.of(tier(1L, "Tier 1")));
    when(teamRoleRepo.findByNameIgnoreCase("Lead")).thenReturn(Optional.of(new TeamRoleMaster()));

    assertThatThrownBy(() -> service.create(new TeamRoleDto.CreateRequest("Lead", 1L, true)))
        .isInstanceOf(DuplicateResourceException.class);
  }

  @Test
  void delete_whenAssignedToMembers_throwsResourceInUse() {
    TeamRoleMaster role = new TeamRoleMaster();
    role.setId(5L);
    when(teamRoleRepo.findById(5L)).thenReturn(Optional.of(role));
    when(projectMemberMappingRepo.countByTeamRole_Id(5L)).thenReturn(1L);

    assertThatThrownBy(() -> service.delete(5L)).isInstanceOf(ResourceInUseException.class);
    verify(teamRoleRepo, never()).delete(any(TeamRoleMaster.class));
  }

  private static TierMaster tier(long id, String name) {
    TierMaster t = new TierMaster();
    t.setId(id);
    t.setName(name);
    t.setActive(true);
    return t;
  }
}
