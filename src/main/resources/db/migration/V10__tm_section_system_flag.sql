-- ============================================================================
-- V10 - Mark seeded Technical Master heads as "system" (ONEMEP-29).
--
-- Standard (seeded) heads can be toggled off ("In project") but not deleted;
-- only user-added heads (is_system = FALSE) may be deleted. Existing rows are
-- seeded heads, so they default to TRUE.
-- ============================================================================

ALTER TABLE tm_section ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT TRUE;
