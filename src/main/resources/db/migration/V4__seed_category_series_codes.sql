-- ============================================================================
-- V4 - Standard Category → series-code mapping (Jira ONEMEP-12/13/14 rule)
--
-- Sets the numeric series_code used to build confirmed Project IDs
-- (e.g. Hotel series 4 → project number 40012). This ONLY updates categories
-- that already exist (matched by name, case-insensitive, dash/spelling variants);
-- it never inserts new categories. Missing categories are a no-op.
--
-- Non-Confirmed is intentionally absent — it is the NC project *type*, not a
-- category, and is handled in the service layer.
-- ============================================================================

UPDATE category_master SET series_code = 1
    WHERE LOWER(name) = 'commercial';

UPDATE category_master SET series_code = 2
    WHERE LOWER(name) IN ('it-data centre', 'it–data centre', 'it data centre',
                          'it-data center', 'it–data center', 'it data center',
                          'it - data centre', 'it – data centre');

UPDATE category_master SET series_code = 3
    WHERE LOWER(name) = 'hospital';

UPDATE category_master SET series_code = 4
    WHERE LOWER(name) = 'hotel';

UPDATE category_master SET series_code = 5
    WHERE LOWER(name) = 'residential';

UPDATE category_master SET series_code = 6
    WHERE LOWER(name) = 'infrastructure';

UPDATE category_master SET series_code = 7
    WHERE LOWER(name) = 'industrial';

UPDATE category_master SET series_code = 8
    WHERE LOWER(name) = 'educational';

UPDATE category_master SET series_code = 9
    WHERE LOWER(name) = 'fitouts';

UPDATE category_master SET series_code = 10
    WHERE LOWER(name) IN ('mixed-use', 'mixed–use', 'mixed use');
