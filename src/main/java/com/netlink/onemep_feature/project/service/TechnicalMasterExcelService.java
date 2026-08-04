package com.netlink.onemep_feature.project.service;

import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto.ArchitectTeam;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto.ClientInformation;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto.ContactRow;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto.DeliveryStage;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto.DesignIntentBrief;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto.StructureConsultantTeam;
import com.netlink.onemep_feature.project.dto.TechnicalMasterDto;
import com.netlink.onemep_feature.project.model.ProjectMaster;
import com.netlink.onemep_feature.project.repo.ProjectRepo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the Technical Master / DID {@code .xlsx} workbooks for "Download Template" (blank Value
 * column) and "Export" (filled, two worksheets) — ONEMEP-31. Reads only through the existing public
 * read APIs ({@link ProjectTechnicalMasterService}, {@link ProjectDidSpecificationService}) so this
 * never duplicates data-access logic.
 */
@Service
@RequiredArgsConstructor
public class TechnicalMasterExcelService {

  private static final String[] FIELD_HEADERS = {"Section", "Field Label", "Unit", "Core", "Value"};
  private static final String[] CONTACT_HEADERS = {"Designation", "Name", "Mail ID", "Contact No."};

  private final ProjectRepo projectRepo;
  private final ProjectTechnicalMasterService technicalMasterService;
  private final ProjectDidSpecificationService didSpecificationService;

  @Transactional(readOnly = true)
  public byte[] buildTemplate(Long projectId) {
    ProjectMaster project = requireProject(projectId);
    TechnicalMasterDto.Template template = technicalMasterService.getTemplateData(projectId);
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      CellStyle bold = boldStyle(workbook);
      Sheet sheet = workbook.createSheet("Technical Master");
      int row = writeProjectHeader(sheet, bold, project, null);
      writeFieldRows(sheet, bold, row, template, Map.of());
      autoSizeColumns(sheet, FIELD_HEADERS.length);
      return toBytes(workbook);
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to build Technical Master template.", ex);
    }
  }

  @Transactional(readOnly = true)
  public byte[] buildExport(Long projectId) {
    ProjectMaster project = requireProject(projectId);
    TechnicalMasterDto.Template template = technicalMasterService.getTemplateData(projectId);
    TechnicalMasterDto.Response tmResponse = technicalMasterService.getResponseData(projectId);
    DidSpecificationDto.Response didResponse = didSpecificationService.getResponseData(projectId);
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      CellStyle bold = boldStyle(workbook);

      Sheet tmSheet = workbook.createSheet("Technical Master");
      int row = writeProjectHeader(tmSheet, bold, project, tmResponse.remarks());
      writeFieldRows(tmSheet, bold, row, template, tmResponse.values());
      autoSizeColumns(tmSheet, FIELD_HEADERS.length);

      Sheet didSheet = workbook.createSheet("DID");
      writeDidSheet(didSheet, bold, didResponse);
      autoSizeColumns(didSheet, CONTACT_HEADERS.length);

      return toBytes(workbook);
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to build Technical Master export.", ex);
    }
  }

  // ── Technical Master sheet ───────────────────────────────────────────────────

  private int writeProjectHeader(
      Sheet sheet, CellStyle bold, ProjectMaster project, String remarks) {
    int r = 0;
    r = writeKeyValueRow(sheet, bold, r, "Project Number", project.getProjectNumber());
    r = writeKeyValueRow(sheet, bold, r, "Project Name", project.getName());
    r =
        writeKeyValueRow(
            sheet,
            bold,
            r,
            "Category",
            project.getCategory() == null ? null : project.getCategory().getName());
    r = writeKeyValueRow(sheet, bold, r, "Current Stage", project.getCurrentStage());
    if (remarks != null) {
      r = writeKeyValueRow(sheet, bold, r, "Remarks", remarks);
    }
    return r + 1; // blank separator row
  }

  private void writeFieldRows(
      Sheet sheet,
      CellStyle bold,
      int startRow,
      TechnicalMasterDto.Template template,
      Map<String, String> values) {
    int r = writeTableHeader(sheet, bold, startRow, FIELD_HEADERS);
    for (TechnicalMasterDto.Section section : template.sections()) {
      for (TechnicalMasterDto.Field field : section.fields()) {
        Row row = sheet.createRow(r++);
        row.createCell(0).setCellValue(section.title());
        row.createCell(1).setCellValue(field.label());
        row.createCell(2).setCellValue(nullToEmpty(field.unit()));
        row.createCell(3).setCellValue(field.required() ? "Core" : "");
        row.createCell(4).setCellValue(values.getOrDefault(field.key(), ""));
      }
    }
  }

  // ── DID sheet ─────────────────────────────────────────────────────────────────

  private void writeDidSheet(Sheet sheet, CellStyle bold, DidSpecificationDto.Response did) {
    DesignIntentBrief brief = did.designIntentBrief();
    int r = 0;
    r =
        writeKeyValueRow(
            sheet,
            bold,
            r,
            "Locked Design Intent",
            brief == null ? null : brief.lockedDesignIntent());
    r =
        writeKeyValueRow(
            sheet,
            bold,
            r,
            "Initial Client RFI Response",
            brief == null ? null : brief.initialClientRfiResponse());
    r =
        writeKeyValueRow(
            sheet,
            bold,
            r,
            "Green Rating Target",
            brief == null ? null : brief.greenRatingTarget());
    r =
        writeKeyValueRow(
            sheet,
            bold,
            r,
            "Sustainability Mandates",
            brief == null ? null : brief.sustainabilityMandates());
    r++;

    r = writeSectionHeading(sheet, bold, r, "Delivery Schedule");
    r = writeTableHeader(sheet, bold, r, "Stage", "Start Date", "End Date");
    for (DeliveryStage stage : did.deliverySchedule()) {
      Row row = sheet.createRow(r++);
      row.createCell(0).setCellValue(nullToEmpty(stage.stageName()));
      row.createCell(1).setCellValue(stage.startDate() == null ? "" : stage.startDate().toString());
      row.createCell(2).setCellValue(stage.endDate() == null ? "" : stage.endDate().toString());
    }
    r++;

    ClientInformation client = did.clientInformation();
    r = writeSectionHeading(sheet, bold, r, "Client Information");
    r =
        writeKeyValueRow(
            sheet, bold, r, "Client Name", client == null ? null : client.clientName());
    r =
        writeKeyValueRow(
            sheet, bold, r, "Client Company", client == null ? null : client.clientCompany());
    r = writeContactsTable(sheet, bold, r, client == null ? List.of() : client.contacts());
    r++;

    ArchitectTeam architect = did.architectTeam();
    r = writeSectionHeading(sheet, bold, r, "Architect Team");
    r =
        writeKeyValueRow(
            sheet,
            bold,
            r,
            "Architecture Firm",
            architect == null ? null : architect.architectureFirm());
    r = writeContactsTable(sheet, bold, r, architect == null ? List.of() : architect.contacts());
    r++;

    StructureConsultantTeam structure = did.structureConsultantTeam();
    r = writeSectionHeading(sheet, bold, r, "Structure Consultant Team");
    r =
        writeKeyValueRow(
            sheet,
            bold,
            r,
            "Structural Consultancy",
            structure == null ? null : structure.structuralConsultancy());
    writeContactsTable(sheet, bold, r, structure == null ? List.of() : structure.contacts());
  }

  private int writeContactsTable(
      Sheet sheet, CellStyle bold, int startRow, List<ContactRow> contacts) {
    int r = writeTableHeader(sheet, bold, startRow, CONTACT_HEADERS);
    for (ContactRow contact : contacts) {
      Row row = sheet.createRow(r++);
      row.createCell(0).setCellValue(nullToEmpty(contact.designation()));
      row.createCell(1).setCellValue(nullToEmpty(contact.name()));
      row.createCell(2).setCellValue(nullToEmpty(contact.mailId()));
      row.createCell(3).setCellValue(nullToEmpty(contact.contactNo()));
    }
    return r;
  }

  // ── sheet-writing helpers ────────────────────────────────────────────────────

  private int writeKeyValueRow(Sheet sheet, CellStyle bold, int rowIdx, String key, String value) {
    Row row = sheet.createRow(rowIdx);
    Cell keyCell = row.createCell(0);
    keyCell.setCellValue(key);
    keyCell.setCellStyle(bold);
    row.createCell(1).setCellValue(nullToEmpty(value));
    return rowIdx + 1;
  }

  private int writeSectionHeading(Sheet sheet, CellStyle bold, int rowIdx, String title) {
    Row row = sheet.createRow(rowIdx);
    Cell cell = row.createCell(0);
    cell.setCellValue(title);
    cell.setCellStyle(bold);
    return rowIdx + 1;
  }

  private int writeTableHeader(Sheet sheet, CellStyle bold, int rowIdx, String... headers) {
    Row row = sheet.createRow(rowIdx);
    for (int i = 0; i < headers.length; i++) {
      Cell cell = row.createCell(i);
      cell.setCellValue(headers[i]);
      cell.setCellStyle(bold);
    }
    return rowIdx + 1;
  }

  private static void autoSizeColumns(Sheet sheet, int columnCount) {
    for (int i = 0; i < columnCount; i++) {
      sheet.autoSizeColumn(i);
    }
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static CellStyle boldStyle(XSSFWorkbook workbook) {
    Font font = workbook.createFont();
    font.setBold(true);
    CellStyle style = workbook.createCellStyle();
    style.setFont(font);
    return style;
  }

  private static byte[] toBytes(XSSFWorkbook workbook) throws IOException {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      workbook.write(out);
      return out.toByteArray();
    }
  }

  private ProjectMaster requireProject(Long projectId) {
    return projectRepo
        .findById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project not found."));
  }
}
