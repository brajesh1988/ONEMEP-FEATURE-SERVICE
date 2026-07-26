-- ============================================================================
-- V5 - Retire project_lead_mapping (Jira ONEMEP-12/13/14/15)
--
-- Project leads are no longer a separate assignment. A lead is now simply a
-- project member whose team role is "Project Lead" (project_member_mapping +
-- team_role_master). The dedicated mapping table and its explicit leadUserIds
-- input have been removed; leads (and the change notifications sent to them)
-- are derived from members at read time.
--
-- Dropping the table cascades away its unique constraint, FKs, and indexes.
-- ============================================================================

DROP TABLE IF EXISTS project_lead_mapping;
