-- Consolidate the personal-details section under the applicant-details journey step.
-- Author: Tinashe K

UPDATE application_type_sections
SET section_name = 'Applicant details',
    updated_at = now(),
    version = version + 1
WHERE section_code = 'PERSONAL_DETAILS'
  AND section_name <> 'Applicant details'
  AND deleted_at IS NULL;

UPDATE application_sections
SET section_name = 'Applicant details',
    completion_summary = CASE
        WHEN completion_summary = 'Personal details complete.' THEN 'Applicant details complete.'
        ELSE completion_summary
    END,
    updated_at = now(),
    version = version + 1
WHERE section_code = 'PERSONAL_DETAILS'
  AND deleted_at IS NULL;
