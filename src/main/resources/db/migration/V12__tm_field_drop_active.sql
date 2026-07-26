-- ============================================================================
-- V12 - Drop the per-field "active" flag (ONEMEP-29).
--
-- Inclusion in a project's sheet is decided per head (tm_section.active); a
-- field is either part of its head or deleted. Two levels of the same switch
-- only made the sheet ambiguous.
-- ============================================================================

ALTER TABLE tm_field DROP COLUMN active;
