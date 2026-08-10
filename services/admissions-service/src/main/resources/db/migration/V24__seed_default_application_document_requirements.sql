-- Author: Tinashe K

INSERT INTO application_type_document_requirements (
    id, application_type_id, requirement_code, requirement_name, is_required,
    sort_order, is_active, created_at, updated_at, version
)
SELECT gen_random_uuid(), application_type.id, requirement.requirement_code,
       requirement.requirement_name, true, requirement.sort_order, true, now(), now(), 0
FROM application_types application_type
CROSS JOIN (VALUES
    ('IDENTITY_DOCUMENT', 'Identity document', 10),
    ('ACADEMIC_QUALIFICATION_EVIDENCE', 'Academic qualification evidence', 20)
) AS requirement(requirement_code, requirement_name, sort_order)
WHERE application_type.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM application_type_document_requirements existing_requirement
      WHERE existing_requirement.application_type_id = application_type.id
        AND existing_requirement.is_active
        AND existing_requirement.deleted_at IS NULL
  );

UPDATE application_sections section
SET is_required = true,
    status = CASE
        WHEN NOT EXISTS (
            SELECT 1
            FROM application_type_document_requirements requirement
            WHERE requirement.application_type_id = application.application_type_id
              AND requirement.is_required
              AND requirement.is_active
              AND requirement.deleted_at IS NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM application_documents document
                  WHERE document.application_id = application.id
                    AND document.requirement_code = requirement.requirement_code
                    AND document.is_current
                    AND document.deleted_at IS NULL
                    AND document.status <> 'REJECTED'
              )
        ) THEN 'COMPLETE'
        ELSE 'IN_PROGRESS'
    END,
    completion_summary = CASE
        WHEN NOT EXISTS (
            SELECT 1
            FROM application_type_document_requirements requirement
            WHERE requirement.application_type_id = application.application_type_id
              AND requirement.is_required
              AND requirement.is_active
              AND requirement.deleted_at IS NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM application_documents document
                  WHERE document.application_id = application.id
                    AND document.requirement_code = requirement.requirement_code
                    AND document.is_current
                    AND document.deleted_at IS NULL
                    AND document.status <> 'REJECTED'
              )
        ) THEN 'Required documents uploaded.'
        ELSE 'Required documents are missing or rejected.'
    END,
    updated_at = now()
FROM applications application
WHERE section.application_id = application.id
  AND section.section_code = 'DOCUMENTS'
  AND section.deleted_at IS NULL
  AND application.deleted_at IS NULL;
