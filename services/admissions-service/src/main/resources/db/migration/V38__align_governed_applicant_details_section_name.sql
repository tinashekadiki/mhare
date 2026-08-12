-- Author: Tinashe K

UPDATE application_type_sections
SET section_name = 'Applicant details',
    updated_at = now()
WHERE section_code = 'PERSONAL_DETAILS'
  AND section_name = 'Personal details'
  AND deleted_at IS NULL;
