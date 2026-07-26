-- ============================================================================
-- V14 - Current design stage on the project (ONEMEP-29).
--
-- CON / SD / DD / TS / GFC / AB. Previously a Technical Master field; it is a
-- project attribute, so it lives with the project and is read-only on the
-- sheet. Nullable: existing projects have no recorded stage.
-- ============================================================================

ALTER TABLE project_master ADD COLUMN current_stage VARCHAR(10);
