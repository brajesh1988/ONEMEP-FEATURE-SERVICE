-- ============================================================================
-- V4 - Seed the standard project Categories with their series codes
--      (Jira ONEMEP-12/13/14 Project ID rule).
--
-- series_code is the numeric prefix used to build confirmed Project IDs
-- (e.g. Hotel series 4 → project number 40012). Non-Confirmed is NOT a
-- category — it is the NC project *type*, handled in the service layer.
--
-- Wipes any existing categories first, then seeds the standard set. The
-- identity sequence is realigned afterwards so service-created categories
-- continue from CAT-00011 onwards.
--
-- NOTE: this DELETE only succeeds when no project_master rows reference a
-- category (FK fk_project_category). It is intended to run on a fresh setup
-- before any projects exist.
-- ============================================================================

DELETE FROM category_master;

INSERT INTO category_master (id, category_number, name, prefix, is_active, series_code, created_date)
VALUES
    (1,  'CAT-00001', 'Commercial',     'COM',  TRUE, 1,  now()),
    (2,  'CAT-00002', 'IT-Data Centre', 'ITDC', TRUE, 2,  now()),
    (3,  'CAT-00003', 'Hospital',       'HOSP', TRUE, 3,  now()),
    (4,  'CAT-00004', 'Hotel',          'HTL',  TRUE, 4,  now()),
    (5,  'CAT-00005', 'Residential',    'RES',  TRUE, 5,  now()),
    (6,  'CAT-00006', 'Infrastructure', 'INF',  TRUE, 6,  now()),
    (7,  'CAT-00007', 'Industrial',     'IND',  TRUE, 7,  now()),
    (8,  'CAT-00008', 'Educational',    'EDU',  TRUE, 8,  now()),
    (9,  'CAT-00009', 'Fitouts',        'FIT',  TRUE, 9,  now()),
    (10, 'CAT-00010', 'Mixed-Use',      'MIX',  TRUE, 10, now());

-- Realign the IDENTITY sequence so the next generated id is MAX(id)+1.
SELECT setval(
    pg_get_serial_sequence('category_master', 'id'),
    GREATEST((SELECT MAX(id) FROM category_master), 1)
);
