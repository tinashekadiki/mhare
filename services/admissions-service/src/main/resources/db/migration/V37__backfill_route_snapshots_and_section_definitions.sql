-- Author: Tinashe K

ALTER TABLE application_types
    ADD COLUMN fee_policy_status varchar(30) NOT NULL DEFAULT 'UNCONFIGURED',
    ADD COLUMN fee_free_reason varchar(1000),
    ADD COLUMN fee_policy_decided_by_user_id uuid,
    ADD COLUMN fee_policy_decided_at timestamptz,
    ADD CONSTRAINT ck_application_type_fee_policy_status CHECK (
        fee_policy_status IN ('UNCONFIGURED', 'FEE_STRUCTURE', 'FEE_FREE', 'LEGACY_CONFIGURED')
    ),
    ADD CONSTRAINT ck_application_type_fee_free_reason CHECK (
        fee_policy_status <> 'FEE_FREE' OR length(trim(fee_free_reason)) >= 10
    );

UPDATE application_types
SET fee_policy_status = 'FEE_STRUCTURE'
WHERE finance_fee_structure_id IS NOT NULL;

UPDATE application_types application_type
SET fee_policy_status = 'LEGACY_CONFIGURED'
WHERE fee_policy_status = 'UNCONFIGURED'
  AND EXISTS (
      SELECT 1 FROM application_fees fee
      WHERE fee.application_type_id = application_type.id
        AND fee.is_active AND fee.deleted_at IS NULL
  );

ALTER TABLE application_types_aud
    ADD COLUMN fee_policy_status varchar(30),
    ADD COLUMN fee_free_reason varchar(1000),
    ADD COLUMN fee_policy_decided_by_user_id uuid,
    ADD COLUMN fee_policy_decided_at timestamptz;

INSERT INTO application_type_sections (
    id, application_type_id, section_code, section_name, is_required, is_repeatable,
    minimum_records, sort_order, is_active, created_at, updated_at, version
)
SELECT gen_random_uuid(), application_type.id, definition.section_code, definition.section_name,
       definition.is_required, definition.is_repeatable, definition.minimum_records,
       definition.sort_order, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM application_types application_type
JOIN (VALUES
    ('UNDERGRAD', 'PERSONAL_DETAILS', 'Personal details', true, false, 0, 10),
    ('UNDERGRAD', 'NEXT_OF_KIN', 'Next of kin', true, true, 1, 20),
    ('UNDERGRAD', 'QUALIFICATIONS', 'Qualifications', true, true, 1, 30),
    ('UNDERGRAD', 'PROGRAMME_CHOICES', 'Programme choices', true, true, 1, 60),
    ('UNDERGRAD', 'DOCUMENTS', 'Supporting documents', true, true, 0, 70),
    ('UNDERGRAD', 'PAYMENT', 'Application fee', true, false, 0, 80),
    ('UNDERGRAD', 'REVIEW_DECLARATION', 'Review and declaration', true, false, 0, 90),

    ('POSTGRAD', 'PERSONAL_DETAILS', 'Personal details', true, false, 0, 10),
    ('POSTGRAD', 'NEXT_OF_KIN', 'Next of kin', true, true, 1, 20),
    ('POSTGRAD', 'QUALIFICATIONS', 'Qualifications', true, true, 1, 30),
    ('POSTGRAD', 'EMPLOYMENT_HISTORY', 'Employment history', true, true, 1, 40),
    ('POSTGRAD', 'REFEREES', 'Confidential references', true, true, 2, 50),
    ('POSTGRAD', 'PROGRAMME_CHOICES', 'Programme choices', true, true, 1, 60),
    ('POSTGRAD', 'DOCUMENTS', 'Supporting documents', true, true, 0, 70),
    ('POSTGRAD', 'PAYMENT', 'Application fee', true, false, 0, 80),
    ('POSTGRAD', 'REVIEW_DECLARATION', 'Review and declaration', true, false, 0, 90),

    ('MBA', 'PERSONAL_DETAILS', 'Personal details', true, false, 0, 10),
    ('MBA', 'NEXT_OF_KIN', 'Next of kin', true, true, 1, 20),
    ('MBA', 'QUALIFICATIONS', 'Qualifications', true, true, 1, 30),
    ('MBA', 'PRIOR_UZ_STUDY', 'Prior UZ study', true, false, 1, 35),
    ('MBA', 'PROFESSIONAL_ACHIEVEMENTS', 'Professional achievements', true, true, 1, 38),
    ('MBA', 'EMPLOYMENT_HISTORY', 'Employment history', true, true, 1, 40),
    ('MBA', 'REFEREES', 'Confidential references', true, true, 3, 50),
    ('MBA', 'PROGRAMME_CHOICES', 'Programme choices', true, true, 1, 60),
    ('MBA', 'DOCUMENTS', 'Supporting documents', true, true, 0, 70),
    ('MBA', 'PAYMENT', 'Application fee', true, false, 0, 80),
    ('MBA', 'REVIEW_DECLARATION', 'Review and declaration', true, false, 0, 90),

    ('EDUCATION', 'PERSONAL_DETAILS', 'Personal details', true, false, 0, 10),
    ('EDUCATION', 'NEXT_OF_KIN', 'Next of kin', true, true, 1, 20),
    ('EDUCATION', 'QUALIFICATIONS', 'Qualifications', true, true, 1, 30),
    ('EDUCATION', 'EMPLOYMENT_HISTORY', 'Employment history', true, true, 1, 40),
    ('EDUCATION', 'REFEREES', 'Confidential references', true, true, 3, 50),
    ('EDUCATION', 'PROGRAMME_CHOICES', 'Programme choices', true, true, 1, 60),
    ('EDUCATION', 'DOCUMENTS', 'Supporting documents', true, true, 0, 70),
    ('EDUCATION', 'PAYMENT', 'Application fee', true, false, 0, 80),
    ('EDUCATION', 'REVIEW_DECLARATION', 'Review and declaration', true, false, 0, 90)
) AS definition(
    route_code, section_code, section_name, is_required, is_repeatable,
    minimum_records, sort_order
) ON definition.route_code = application_type.code
WHERE application_type.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM application_type_sections existing
      WHERE existing.application_type_id = application_type.id
        AND existing.section_code = definition.section_code
  );

INSERT INTO application_programme_option_snapshots (
    id, application_id, programme_id, programme_version_id, programme_code, programme_name,
    award_name, owning_academic_unit_id, owning_academic_unit_name, programme_version_code,
    minimum_entry_option_selections, maximum_entry_option_selections, entry_options_json,
    created_at, updated_at, created_by_user_id, modified_by_user_id, version
)
SELECT gen_random_uuid(), choice.application_id, choice.programme_id, choice.programme_version_id,
       choice.programme_code, choice.programme_name, choice.award_name,
       choice.owning_academic_unit_id, choice.owning_academic_unit_name, choice.programme_version_code,
       0, 0, '[]'::jsonb, choice.created_at, CURRENT_TIMESTAMP,
       choice.created_by_user_id, choice.modified_by_user_id, 0
FROM application_programme_choices choice
WHERE choice.deleted_at IS NULL
  AND choice.catalogue_snapshot_status = 'VALIDATED'
  AND NOT EXISTS (
      SELECT 1 FROM application_programme_option_snapshots existing
      WHERE existing.application_id = choice.application_id
        AND existing.programme_id = choice.programme_id
  );

INSERT INTO application_document_requirement_snapshots (
    id, application_id, requirement_code, requirement_name, is_required, sort_order,
    created_at, updated_at, created_by_user_id, modified_by_user_id, version
)
SELECT gen_random_uuid(), application.id, requirement.requirement_code, requirement.requirement_name,
       requirement.is_required, requirement.sort_order, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
       application.created_by_user_id, application.modified_by_user_id, 0
FROM applications application
JOIN application_type_document_requirements requirement
  ON requirement.application_type_id = application.application_type_id
WHERE application.deleted_at IS NULL
  AND requirement.deleted_at IS NULL
  AND requirement.is_active
  AND NOT EXISTS (
      SELECT 1 FROM application_document_requirement_snapshots existing
      WHERE existing.application_id = application.id
        AND existing.requirement_code = requirement.requirement_code
  );
