-- ============================================================================
-- V13 - Drop the "Project identification" head altogether (ONEMEP-29).
--
-- V11 left it holding only "Current stage"; that dropdown is owned by the UI,
-- so the head no longer earns a place in the catalog. Every remaining head is
-- renumbered so section_order stays contiguous per series (new heads are
-- appended at count + 1).
-- ============================================================================

DELETE FROM project_technical_field_value
WHERE field_key = 'project_identification__current_stage_con_sd_dd_ts_gfc_ab';

DELETE FROM tm_field
WHERE section_id IN (SELECT id FROM tm_section WHERE title = 'Project identification');

DELETE FROM tm_section WHERE title = 'Project identification';

WITH renumbered AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY series_code ORDER BY section_order, id) AS rn
    FROM tm_section
)
UPDATE tm_section s
SET section_order = r.rn
FROM renumbered r
WHERE r.id = s.id;
