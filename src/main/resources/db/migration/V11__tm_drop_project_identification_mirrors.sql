-- ============================================================================
-- V11 - Drop the "Project identification" fields that mirror project_master
--       (ONEMEP-29). Project code / name / category / client / city / handling
--       office / PTMS version are owned by the project record and shown
--       read-only on the sheet, so an editable copy here could only drift.
--
-- "Current stage" stays: it has no column on project_master and is the one
-- field of that head the user actually edits. The head itself is kept (system
-- head, series-wide) so the sheet still has a place for it.
--
-- field_key is unique per series, so the key list covers all seeded categories.
-- ============================================================================

DELETE FROM project_technical_field_value
WHERE field_key IN (
    'project_identification__project_code_5_digit_nc',
    'project_identification__project_name',
    'project_identification__category',
    'project_identification__client',
    'project_identification__city_location',
    'project_identification__handling_office',
    'project_identification__ptms_version_date_prepared_by'
);

DELETE FROM tm_field
WHERE field_key IN (
    'project_identification__project_code_5_digit_nc',
    'project_identification__project_name',
    'project_identification__category',
    'project_identification__client',
    'project_identification__city_location',
    'project_identification__handling_office',
    'project_identification__ptms_version_date_prepared_by'
);

UPDATE tm_field
SET field_order = 1
WHERE field_key = 'project_identification__current_stage_con_sd_dd_ts_gfc_ab';
