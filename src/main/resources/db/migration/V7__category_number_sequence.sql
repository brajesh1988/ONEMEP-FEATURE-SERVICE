-- ============================================================================
-- V7 - Category-driven confirmed project numbering
--
-- Confirmed project numbers are now built from the category's prefix + a
-- per-category running counter (+ optional suffix), instead of series_code + id:
--   prefix + lpad(last_number, 4) + suffix   e.g. "HTL" + 0001 → "HTL0001"
--
--   * type        : free-form category classification (metadata only)
--   * suffix      : optional trailing token appended after the counter
--   * last_number : running counter; NULL until the first confirmed project,
--                   then starts at 1 and increments per category
--
-- Existing categories keep last_number NULL, so each starts fresh at 1. Old
-- confirmed numbers (series_code + id, e.g. 40012) use a different shape and
-- will not collide with the new prefix-based codes.
--
-- IF NOT EXISTS: this migration first shipped as V6 and was applied to the
-- shared dev DB before project_technical_master claimed the V6 slot. Making the
-- ADDs idempotent lets that DB re-record this as V7 without failing on columns
-- it already has, and is harmless on a fresh database.
-- ============================================================================

ALTER TABLE category_master ADD COLUMN IF NOT EXISTS type        VARCHAR(30);
ALTER TABLE category_master ADD COLUMN IF NOT EXISTS suffix      VARCHAR(10);
ALTER TABLE category_master ADD COLUMN IF NOT EXISTS last_number INTEGER;
