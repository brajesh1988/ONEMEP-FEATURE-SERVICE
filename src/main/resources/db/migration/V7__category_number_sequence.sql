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
-- ============================================================================

ALTER TABLE category_master ADD COLUMN type        VARCHAR(30);
ALTER TABLE category_master ADD COLUMN suffix      VARCHAR(10);
ALTER TABLE category_master ADD COLUMN last_number INTEGER;
