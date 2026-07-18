package com.netlink.onemep_feature.teamrole.service;

import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.common.dto.PageResponse;
import com.netlink.onemep_feature.common.util.PageableFactory;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import com.netlink.onemep_feature.exception.ResourceInUseException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.project.repo.ProjectMemberMappingRepo;
import com.netlink.onemep_feature.teamrole.dto.TeamRoleDto;
import com.netlink.onemep_feature.teamrole.model.TeamRoleMaster;
import com.netlink.onemep_feature.teamrole.repo.TeamRoleRepo;
import com.netlink.onemep_feature.tier.model.TierMaster;
import com.netlink.onemep_feature.tier.repo.TierRepo;
import jakarta.persistence.criteria.Join;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamRoleServiceImpl implements TeamRoleService {

  private static final Set<String> SORTABLE =
      Set.of("name", "active", "createdDate", "updatedDate");

  private final TeamRoleRepo teamRoleRepo;
  private final TierRepo tierRepo;
  private final ProjectMemberMappingRepo projectMemberMappingRepo;
  private final ApiResponseAdaptor apiResponseAdaptor;

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> list(GenericListRequestDTO request) {
    String search = PageableFactory.search(request);
    Page<TeamRoleMaster> page =
        teamRoleRepo.findAll(searchSpec(search), PageableFactory.of(request, SORTABLE));
    List<TeamRoleDto.Response> content = page.getContent().stream().map(this::toResponse).toList();
    return apiResponseAdaptor.success(
        "Team roles fetched successfully.", new PageResponse<>(page, content));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> listActive() {
    List<TeamRoleDto.ActiveItem> items =
        teamRoleRepo.findAllActive().stream()
            .map(
                tr ->
                    new TeamRoleDto.ActiveItem(
                        tr.getId(), tr.getName(), tr.getTier().getId(), tr.getTier().getName()))
            .toList();
    return apiResponseAdaptor.success("Active team roles fetched successfully.", items);
  }

  @Override
  @Transactional
  public ApiResponse<?> create(TeamRoleDto.CreateRequest request) {
    String name = normalize(request.name());
    TierMaster tier = requireTier(request.tierId());
    teamRoleRepo
        .findByNameIgnoreCase(name)
        .ifPresent(
            tr -> {
              throw new DuplicateResourceException("A team role with this name already exists.");
            });

    TeamRoleMaster role = new TeamRoleMaster();
    role.setName(name);
    role.setTier(tier);
    role.setActive(request.active() == null ? Boolean.TRUE : request.active());
    role.setCreatedBy(SecurityUtils.getUserId().orElse(null));
    role = teamRoleRepo.save(role);
    log.info("Created teamRoleId={} name={}", role.getId(), name);
    return apiResponseAdaptor.success("Team role created successfully.", toResponse(role));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> get(Long id) {
    return apiResponseAdaptor.success("Team role fetched successfully.", toResponse(require(id)));
  }

  @Override
  @Transactional
  public ApiResponse<?> update(Long id, TeamRoleDto.UpdateRequest request) {
    TeamRoleMaster role = require(id);
    String name = normalize(request.name());
    TierMaster tier = requireTier(request.tierId());
    teamRoleRepo
        .findByNameIgnoreCaseAndIdNot(name, id)
        .ifPresent(
            tr -> {
              throw new DuplicateResourceException("A team role with this name already exists.");
            });
    role.setName(name);
    role.setTier(tier);
    if (request.active() != null) {
      role.setActive(request.active());
    }
    role.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    teamRoleRepo.save(role);
    log.info("Updated teamRoleId={}", id);
    return apiResponseAdaptor.success("Team role updated successfully.", toResponse(role));
  }

  @Override
  @Transactional
  public ApiResponse<?> updateStatus(Long id, Boolean active) {
    TeamRoleMaster role = require(id);
    role.setActive(active);
    role.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    teamRoleRepo.save(role);
    return apiResponseAdaptor.success(
        Boolean.TRUE.equals(active)
            ? "Team role activated successfully."
            : "Team role deactivated successfully.");
  }

  @Override
  @Transactional
  public ApiResponse<?> delete(Long id) {
    TeamRoleMaster role = require(id);
    if (projectMemberMappingRepo.countByTeamRole_Id(id) > 0) {
      throw new ResourceInUseException(
          "This team role is assigned to project members and cannot be deleted.");
    }
    teamRoleRepo.delete(role);
    log.info("Deleted teamRoleId={}", id);
    return apiResponseAdaptor.success("Team role deleted successfully.");
  }

  private TeamRoleMaster require(Long id) {
    return teamRoleRepo
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Team role not found."));
  }

  private TierMaster requireTier(Long tierId) {
    return tierRepo
        .findById(tierId)
        .orElseThrow(() -> new ResourceNotFoundException("Tier not found."));
  }

  private static String normalize(String raw) {
    return raw == null ? "" : raw.trim();
  }

  private Specification<TeamRoleMaster> searchSpec(String search) {
    return (root, query, cb) -> {
      if (search == null) {
        return cb.conjunction();
      }
      String like = "%" + search.toLowerCase() + "%";
      Join<Object, Object> tier = root.join("tier");
      return cb.or(
          cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(tier.get("name")), like));
    };
  }

  private TeamRoleDto.Response toResponse(TeamRoleMaster tr) {
    return new TeamRoleDto.Response(
        tr.getId(),
        tr.getName(),
        tr.getTier().getId(),
        tr.getTier().getName(),
        tr.getActive(),
        tr.getUpdatedBy(),
        tr.getUpdatedDate());
  }
}
