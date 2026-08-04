package com.netlink.onemep_feature.project.service;

import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.project.config.DidDefaultsProperties;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto.ArchitectTeam;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto.ClientInformation;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto.ContactRow;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto.DeliveryStage;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto.DesignIntentBrief;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto.GreenRatingOption;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto.Response;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto.StructureConsultantTeam;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto.UpsertRequest;
import com.netlink.onemep_feature.project.model.DidPartyType;
import com.netlink.onemep_feature.project.model.ProjectDeliverySchedule;
import com.netlink.onemep_feature.project.model.ProjectDidArchitectTeam;
import com.netlink.onemep_feature.project.model.ProjectDidClientInfo;
import com.netlink.onemep_feature.project.model.ProjectDidContact;
import com.netlink.onemep_feature.project.model.ProjectDidSpecification;
import com.netlink.onemep_feature.project.model.ProjectDidStructureConsultantTeam;
import com.netlink.onemep_feature.project.model.ProjectMaster;
import com.netlink.onemep_feature.project.model.ProjectTechnicalMaster;
import com.netlink.onemep_feature.project.repo.DidGreenRatingOptionRepo;
import com.netlink.onemep_feature.project.repo.ProjectDeliveryScheduleRepo;
import com.netlink.onemep_feature.project.repo.ProjectDidArchitectTeamRepo;
import com.netlink.onemep_feature.project.repo.ProjectDidClientInfoRepo;
import com.netlink.onemep_feature.project.repo.ProjectDidContactRepo;
import com.netlink.onemep_feature.project.repo.ProjectDidSpecificationRepo;
import com.netlink.onemep_feature.project.repo.ProjectDidStructureConsultantTeamRepo;
import com.netlink.onemep_feature.project.repo.ProjectRepo;
import com.netlink.onemep_feature.project.repo.ProjectTechnicalMasterRepo;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectDidSpecificationServiceImpl implements ProjectDidSpecificationService {

  private final ProjectRepo projectRepo;
  private final ProjectTechnicalMasterRepo technicalMasterRepo;
  private final ProjectDidSpecificationRepo didSpecRepo;
  private final DidGreenRatingOptionRepo greenRatingOptionRepo;
  private final ProjectDeliveryScheduleRepo deliveryScheduleRepo;
  private final ProjectDidClientInfoRepo clientInfoRepo;
  private final ProjectDidArchitectTeamRepo architectTeamRepo;
  private final ProjectDidStructureConsultantTeamRepo structureTeamRepo;
  private final ProjectDidContactRepo contactRepo;
  private final DidDefaultsProperties didDefaults;
  private final ApiResponseAdaptor apiResponseAdaptor;

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> get(Long projectId) {
    Response response = getResponseData(projectId);
    return apiResponseAdaptor.success(
        response.exists()
            ? "DID specification fetched successfully."
            : "DID specification has not been created for this project yet.",
        response);
  }

  @Override
  @Transactional(readOnly = true)
  public Response getResponseData(Long projectId) {
    ProjectMaster project = requireProject(projectId);
    ProjectTechnicalMaster master = technicalMasterRepo.findByProject_Id(projectId).orElse(null);
    return buildResponse(project, master);
  }

  @Override
  @Transactional
  public Response applyUpsert(
      ProjectMaster project,
      ProjectTechnicalMaster master,
      UpsertRequest request,
      Long currentUser) {
    if (request == null) {
      throw new ApplicationException("DID information is required.");
    }
    saveDesignIntentBrief(master, request.designIntentBrief(), currentUser);
    saveDeliverySchedule(project, request.deliverySchedule(), currentUser);
    saveClientInformation(master, request.clientInformation(), currentUser);
    saveArchitectTeam(master, request.architectTeam(), currentUser);
    saveStructureConsultantTeam(master, request.structureConsultantTeam(), currentUser);
    return buildResponse(project, master);
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> listGreenRatingOptions() {
    List<GreenRatingOption> options =
        greenRatingOptionRepo.findByActiveTrueOrderByOptionOrderAsc().stream()
            .map(o -> new GreenRatingOption(o.getCode(), o.getLabel()))
            .toList();
    return apiResponseAdaptor.success("Green rating options fetched successfully.", options);
  }

  // ── Design Intent & Brief ────────────────────────────────────────────────────

  private void saveDesignIntentBrief(
      ProjectTechnicalMaster master, DesignIntentBrief dto, Long currentUser) {
    if (dto == null) {
      throw new ApplicationException("Design intent & brief is required.");
    }
    String lockedDesignIntent = trimToNull(dto.lockedDesignIntent());
    if (lockedDesignIntent == null) {
      throw new ApplicationException("Design brief is required.");
    }
    String greenRating = normalizeGreenRating(dto.greenRatingTarget());

    ProjectDidSpecification spec =
        didSpecRepo
            .findByTechnicalMaster_Id(master.getId())
            .orElseGet(
                () -> {
                  ProjectDidSpecification s = new ProjectDidSpecification();
                  s.setTechnicalMaster(master);
                  s.setCreatedBy(currentUser);
                  return s;
                });
    spec.setLockedDesignIntent(lockedDesignIntent);
    spec.setInitialClientRfiResponse(trimToNull(dto.initialClientRfiResponse()));
    spec.setGreenRatingTarget(greenRating);
    spec.setSustainabilityMandates(trimToNull(dto.sustainabilityMandates()));
    spec.setUpdatedBy(currentUser);
    didSpecRepo.save(spec);
  }

  private String normalizeGreenRating(String raw) {
    String value = trimToNull(raw);
    if (value == null) {
      return null;
    }
    String code = value.toUpperCase();
    if (greenRatingOptionRepo.findByCodeAndActiveTrue(code).isEmpty()) {
      throw new ApplicationException("Green rating target must be one of the configured options.");
    }
    return code;
  }

  // ── Delivery Schedule ─────────────────────────────────────────────────────────

  private void saveDeliverySchedule(
      ProjectMaster project, List<DeliveryStage> stages, Long currentUser) {
    List<ProjectDeliverySchedule> existing =
        deliveryScheduleRepo.findByProject_IdAndDidStageTrueOrderByStageOrderAscIdAsc(
            project.getId());
    Map<Long, ProjectDeliverySchedule> byId =
        existing.stream().collect(Collectors.toMap(ProjectDeliverySchedule::getId, e -> e));

    Set<Long> keepIds = new HashSet<>();
    int order = 0;
    for (DeliveryStage row : stages == null ? List.<DeliveryStage>of() : stages) {
      String name = trimToNull(row.stageName());
      if (name == null && row.startDate() == null && row.endDate() == null) {
        continue; // blank trailing row — silently dropped
      }
      if (name == null) {
        throw new ApplicationException("Every delivery stage needs a name.");
      }
      if (row.endDate() != null && row.startDate() == null) {
        throw new ApplicationException("Start date is required when an end date is set.");
      }
      if (row.startDate() != null
          && row.endDate() != null
          && row.endDate().isBefore(row.startDate())) {
        throw new ApplicationException("End date cannot be earlier than start date.");
      }

      ProjectDeliverySchedule entity;
      if (row.id() != null) {
        entity = byId.get(row.id());
        if (entity == null) {
          throw new ResourceNotFoundException("Delivery schedule stage not found: " + row.id());
        }
      } else {
        entity = new ProjectDeliverySchedule();
        entity.setProject(project);
        entity.setDidStage(Boolean.TRUE);
        entity.setStatus("PENDING");
        entity.setCreatedBy(currentUser);
      }
      entity.setMilestone(name);
      entity.setStartDate(row.startDate());
      entity.setEndDate(row.endDate());
      entity.setStageOrder(order++);
      entity.setUpdatedBy(currentUser);
      entity = deliveryScheduleRepo.save(entity);
      keepIds.add(entity.getId());
    }

    for (ProjectDeliverySchedule e : existing) {
      if (!keepIds.contains(e.getId())) {
        deliveryScheduleRepo.delete(e);
      }
    }
  }

  // ── Client / Architect / Structure ───────────────────────────────────────────

  private void saveClientInformation(
      ProjectTechnicalMaster master, ClientInformation dto, Long currentUser) {
    ClientInformation data = dto == null ? new ClientInformation(null, null, List.of()) : dto;
    ProjectDidClientInfo info =
        clientInfoRepo
            .findByTechnicalMaster_Id(master.getId())
            .orElseGet(
                () -> {
                  ProjectDidClientInfo i = new ProjectDidClientInfo();
                  i.setTechnicalMaster(master);
                  i.setCreatedBy(currentUser);
                  return i;
                });
    info.setClientName(trimToNull(data.clientName()));
    info.setClientCompany(trimToNull(data.clientCompany()));
    info.setUpdatedBy(currentUser);
    clientInfoRepo.save(info);
    saveContacts(master, DidPartyType.CLIENT, data.contacts(), currentUser);
  }

  private void saveArchitectTeam(
      ProjectTechnicalMaster master, ArchitectTeam dto, Long currentUser) {
    ArchitectTeam data = dto == null ? new ArchitectTeam(null, List.of()) : dto;
    ProjectDidArchitectTeam team =
        architectTeamRepo
            .findByTechnicalMaster_Id(master.getId())
            .orElseGet(
                () -> {
                  ProjectDidArchitectTeam t = new ProjectDidArchitectTeam();
                  t.setTechnicalMaster(master);
                  t.setCreatedBy(currentUser);
                  return t;
                });
    team.setArchitectureFirm(trimToNull(data.architectureFirm()));
    team.setUpdatedBy(currentUser);
    architectTeamRepo.save(team);
    saveContacts(master, DidPartyType.ARCHITECT, data.contacts(), currentUser);
  }

  private void saveStructureConsultantTeam(
      ProjectTechnicalMaster master, StructureConsultantTeam dto, Long currentUser) {
    StructureConsultantTeam data = dto == null ? new StructureConsultantTeam(null, List.of()) : dto;
    ProjectDidStructureConsultantTeam team =
        structureTeamRepo
            .findByTechnicalMaster_Id(master.getId())
            .orElseGet(
                () -> {
                  ProjectDidStructureConsultantTeam t = new ProjectDidStructureConsultantTeam();
                  t.setTechnicalMaster(master);
                  t.setCreatedBy(currentUser);
                  return t;
                });
    team.setStructuralConsultancy(trimToNull(data.structuralConsultancy()));
    team.setUpdatedBy(currentUser);
    structureTeamRepo.save(team);
    saveContacts(master, DidPartyType.STRUCTURE, data.contacts(), currentUser);
  }

  /** Diff-replace the contact rows of one DID subsection; default rows cannot be removed. */
  private void saveContacts(
      ProjectTechnicalMaster master,
      DidPartyType partyType,
      List<ContactRow> rows,
      Long currentUser) {
    List<ProjectDidContact> existing =
        contactRepo.findByTechnicalMaster_IdAndPartyTypeOrderByContactOrderAscIdAsc(
            master.getId(), partyType);
    Map<Long, ProjectDidContact> byId =
        existing.stream().collect(Collectors.toMap(ProjectDidContact::getId, c -> c));

    Set<Long> keepIds = new HashSet<>();
    int order = 0;
    for (ContactRow row : rows == null ? List.<ContactRow>of() : rows) {
      String designation = trimToNull(row.designation());
      String name = trimToNull(row.name());
      String mailId = trimToNull(row.mailId());
      String contactNo = trimToNull(row.contactNo());
      if (designation == null && name == null && mailId == null && contactNo == null) {
        continue; // blank row — silently dropped
      }

      ProjectDidContact entity;
      if (row.id() != null) {
        entity = byId.get(row.id());
        if (entity == null) {
          throw new ResourceNotFoundException("Contact not found: " + row.id());
        }
      } else {
        entity = new ProjectDidContact();
        entity.setTechnicalMaster(master);
        entity.setPartyType(partyType);
        entity.setCreatedBy(currentUser);
        entity.setDefaultRow(
            partyType == DidPartyType.CLIENT
                && row.isDefault()
                && didDefaults.clientContactDesignations().contains(designation));
      }
      entity.setDesignation(designation);
      entity.setName(name);
      entity.setMailId(mailId);
      entity.setContactNo(contactNo);
      entity.setContactOrder(order++);
      entity.setUpdatedBy(currentUser);
      entity = contactRepo.save(entity);
      keepIds.add(entity.getId());
    }

    for (ProjectDidContact e : existing) {
      if (!keepIds.contains(e.getId())) {
        if (Boolean.TRUE.equals(e.getDefaultRow())) {
          throw new ApplicationException(
              "Default contact rows cannot be removed: " + e.getDesignation());
        }
        contactRepo.delete(e);
      }
    }
  }

  // ── Response assembly ─────────────────────────────────────────────────────────

  private Response buildResponse(ProjectMaster project, ProjectTechnicalMaster master) {
    DesignIntentBrief designIntentBrief =
        master == null
            ? null
            : didSpecRepo
                .findByTechnicalMaster_Id(master.getId())
                .map(ProjectDidSpecificationServiceImpl::toDesignIntentBrief)
                .orElse(null);

    return new Response(
        master != null,
        project.getId(),
        master == null ? null : master.getId(),
        designIntentBrief,
        buildDeliverySchedule(project.getId()),
        buildClientInformation(master),
        buildArchitectTeam(master),
        buildStructureConsultantTeam(master),
        master == null ? null : master.getCreatedBy(),
        master == null ? null : master.getCreatedDate(),
        master == null ? null : master.getUpdatedBy(),
        master == null ? null : master.getUpdatedDate());
  }

  private List<DeliveryStage> buildDeliverySchedule(Long projectId) {
    List<ProjectDeliverySchedule> rows =
        deliveryScheduleRepo.findByProject_IdAndDidStageTrueOrderByStageOrderAscIdAsc(projectId);
    if (!rows.isEmpty()) {
      return rows.stream().map(ProjectDidSpecificationServiceImpl::toDeliveryStage).toList();
    }
    return didDefaults.deliveryStages().stream()
        .map(name -> new DeliveryStage(null, name, null, null))
        .toList();
  }

  private ClientInformation buildClientInformation(ProjectTechnicalMaster master) {
    ProjectDidClientInfo info =
        master == null
            ? null
            : clientInfoRepo.findByTechnicalMaster_Id(master.getId()).orElse(null);
    List<ContactRow> contacts =
        master == null ? List.of() : contactsFor(master.getId(), DidPartyType.CLIENT);
    if (contacts.isEmpty()) {
      contacts =
          didDefaults.clientContactDesignations().stream()
              .map(designation -> new ContactRow(null, designation, null, null, null, true))
              .toList();
    }
    return new ClientInformation(
        info == null ? null : info.getClientName(),
        info == null ? null : info.getClientCompany(),
        contacts);
  }

  private ArchitectTeam buildArchitectTeam(ProjectTechnicalMaster master) {
    ProjectDidArchitectTeam team =
        master == null
            ? null
            : architectTeamRepo.findByTechnicalMaster_Id(master.getId()).orElse(null);
    List<ContactRow> contacts =
        master == null ? List.of() : contactsFor(master.getId(), DidPartyType.ARCHITECT);
    return new ArchitectTeam(team == null ? null : team.getArchitectureFirm(), contacts);
  }

  private StructureConsultantTeam buildStructureConsultantTeam(ProjectTechnicalMaster master) {
    ProjectDidStructureConsultantTeam team =
        master == null
            ? null
            : structureTeamRepo.findByTechnicalMaster_Id(master.getId()).orElse(null);
    List<ContactRow> contacts =
        master == null ? List.of() : contactsFor(master.getId(), DidPartyType.STRUCTURE);
    return new StructureConsultantTeam(
        team == null ? null : team.getStructuralConsultancy(), contacts);
  }

  private List<ContactRow> contactsFor(Long technicalMasterId, DidPartyType partyType) {
    return contactRepo
        .findByTechnicalMaster_IdAndPartyTypeOrderByContactOrderAscIdAsc(
            technicalMasterId, partyType)
        .stream()
        .map(ProjectDidSpecificationServiceImpl::toContactRow)
        .toList();
  }

  private static DesignIntentBrief toDesignIntentBrief(ProjectDidSpecification spec) {
    return new DesignIntentBrief(
        spec.getLockedDesignIntent(),
        spec.getInitialClientRfiResponse(),
        spec.getGreenRatingTarget(),
        spec.getSustainabilityMandates());
  }

  private static DeliveryStage toDeliveryStage(ProjectDeliverySchedule d) {
    return new DeliveryStage(d.getId(), d.getMilestone(), d.getStartDate(), d.getEndDate());
  }

  private static ContactRow toContactRow(ProjectDidContact c) {
    return new ContactRow(
        c.getId(),
        c.getDesignation(),
        c.getName(),
        c.getMailId(),
        c.getContactNo(),
        Boolean.TRUE.equals(c.getDefaultRow()));
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private ProjectMaster requireProject(Long projectId) {
    return projectRepo
        .findById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project not found."));
  }

  private static String trimToNull(String raw) {
    if (raw == null) {
      return null;
    }
    String trimmed = raw.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
