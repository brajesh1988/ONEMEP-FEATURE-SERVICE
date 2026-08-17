-- ============================================================================
-- V24 - Design uniqueness: two independent rules, not one composite key.
--
-- V18 implemented ONEMEP-36 literally, which states that two Designs may share
-- a Design Number when their Titles differ, and gives a worked example. The
-- business has since ruled that reading incorrect and confirmed ONEMEP-35's:
--
--   * the generated Design Number must be unique — reject a duplicate number
--     even when the Title differs;
--   * the Design Title must also be unique — reject a duplicate Title even when
--     the Design Number differs.
--
-- So the two are separate validations with separate messages, never a composite
-- identity. Only a row differing in BOTH is valid.
--
-- Both are scoped to the Project, matching ONEMEP-36's "already exists in this
-- Project" throughout. The Design Number embeds the Project Code, so in practice
-- it is globally unique as well; the constraint is written per-Project so it
-- stays correct if the numbering format ever changes.
--
-- NOTE: these constraints are strictly stronger than the one they replace. In an
-- environment already holding Designs that share a number or a title, this
-- migration will fail — which is the correct outcome: the conflict has to be
-- resolved rather than carried forward silently.
-- ============================================================================

ALTER TABLE design DROP CONSTRAINT uq_design_identity;

ALTER TABLE design
    ADD CONSTRAINT uq_design_number UNIQUE (project_id, design_number),
    ADD CONSTRAINT uq_design_title  UNIQUE (project_id, title_normalized);
