-- ============================================================================
-- V27 - DID green rating target options aligned to the approved prototype.
--
-- UAT raised that the "Green rating target" dropdown offered the generic
-- IGBC / GRIHA / LEED bodies instead of the specific rating levels signed off
-- in the prototype. The dropdown is fed by did_green_rating_option (V15), so
-- the correction belongs in the reference data, not the UI.
--
-- IMPORTANT — why the codes contain spaces:
-- The web client submits the option *label* as green_rating_target, and
-- ProjectDidSpecificationServiceImpl#normalizeGreenRating upper-cases that
-- value before matching it against `code`. Codes must therefore be exactly
-- UPPER(label) or every save fails validation. Do not "tidy" these into
-- snake_case without first changing the client to submit the code.
-- ============================================================================

-- ── Retire the generic body-only options ───────────────────────────────────
-- Deactivated rather than deleted: project_did_specification.green_rating_target
-- is a plain string column, so historic rows keep their recorded value.
UPDATE did_green_rating_option
   SET active = FALSE
 WHERE code IN ('IGBC', 'GRIHA', 'LEED');

-- ── Seed the prototype rating levels ───────────────────────────────────────
INSERT INTO did_green_rating_option (code, label, option_order, active) VALUES
    ('IGBC SILVER TARGET',   'IGBC Silver target',   2, TRUE),
    ('IGBC GOLD TARGET',     'IGBC Gold target',     3, TRUE),
    ('IGBC PLATINUM TARGET', 'IGBC Platinum target', 4, TRUE),
    ('LEED GOLD TARGET',     'LEED Gold target',     5, TRUE),
    ('GRIHA 4-STAR',         'GRIHA 4-star',         6, TRUE)
ON CONFLICT (code) DO UPDATE
   SET label        = EXCLUDED.label,
       option_order = EXCLUDED.option_order,
       active       = TRUE;

-- "None" stays as seeded in V15 (code NONE, order 1) — it leads the prototype list.

-- ── Clear now-invalid references so those DIDs stay saveable ───────────────
-- A project holding the retired generic code would fail normalizeGreenRating on
-- its next save. There is no unambiguous generic -> level mapping, so the field
-- is reset and the user re-picks a specific target.
UPDATE project_did_specification
   SET green_rating_target = NULL
 WHERE green_rating_target IN ('IGBC', 'GRIHA', 'LEED');
