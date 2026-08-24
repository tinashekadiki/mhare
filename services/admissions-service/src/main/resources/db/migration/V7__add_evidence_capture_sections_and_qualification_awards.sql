-- Author: Tinashe K
-- Evidence placement, category applicability, and higher-qualification award metadata.

ALTER TABLE application_type_document_requirements
    ADD COLUMN capture_section_code varchar(60) NOT NULL DEFAULT 'SUPPORTING_DOCUMENTS';

ALTER TABLE application_type_document_requirements_aud
    ADD COLUMN capture_section_code varchar(60);

ALTER TABLE application_document_requirement_snapshots
    ADD COLUMN capture_section_code varchar(60) NOT NULL DEFAULT 'SUPPORTING_DOCUMENTS',
    ADD COLUMN applicant_category_codes varchar(30)[] NOT NULL DEFAULT ARRAY[]::varchar(30)[];

ALTER TABLE application_document_requirement_snapshots_aud
    ADD COLUMN capture_section_code varchar(60),
    ADD COLUMN applicant_category_codes varchar(30)[];

CREATE TABLE application_type_document_requirement_categories (
    id uuid PRIMARY KEY,
    document_requirement_id uuid NOT NULL REFERENCES application_type_document_requirements(id),
    applicant_category_code varchar(30) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_document_requirement_category_code CHECK (applicant_category_code IN ('LOCAL', 'SADC', 'INTERNATIONAL', 'CLE')),
    CONSTRAINT ck_document_requirement_category_soft_delete CHECK (
        (deleted_at IS NULL AND deleted_by_user_id IS NULL)
        OR (deleted_at IS NOT NULL AND deleted_by_user_id IS NOT NULL))
);

CREATE UNIQUE INDEX uk_document_requirement_category_active
    ON application_type_document_requirement_categories (document_requirement_id, applicant_category_code)
    WHERE deleted_at IS NULL;

CREATE TABLE application_type_document_requirement_categories_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo(rev),
    revtype smallint,
    document_requirement_id uuid,
    applicant_category_code varchar(30),
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

CREATE INDEX idx_document_requirement_categories_active
    ON application_type_document_requirement_categories(document_requirement_id, applicant_category_code)
    WHERE deleted_at IS NULL;

ALTER TABLE applicant_qualification_sittings
    ADD COLUMN award_type_code varchar(30),
    ADD COLUMN qualification_name varchar(200);

ALTER TABLE applicant_qualification_sittings_aud
    ADD COLUMN award_type_code varchar(30),
    ADD COLUMN qualification_name varchar(200);

ALTER TABLE applicant_qualification_sittings
    ADD CONSTRAINT ck_qualification_award_type CHECK (
        award_type_code IS NULL OR award_type_code IN
        ('DIPLOMA', 'CERTIFICATE', 'DEGREE', 'MASTERS', 'PROFESSIONAL', 'OTHER'));

-- Existing route definitions remain compatible. New defaults are converted to explicit evidence placement.
UPDATE application_type_document_requirements
SET capture_section_code = CASE
    WHEN requirement_code IN ('IDENTITY_DOCUMENT', 'NATIONAL_ID', 'BIRTH_CERTIFICATE', 'PASSPORT') THEN 'PERSONAL_DETAILS'
    WHEN requirement_code IN ('ACADEMIC_QUALIFICATIONS', 'ACADEMIC_QUALIFICATION_EVIDENCE') THEN 'QUALIFICATIONS'
    ELSE 'SUPPORTING_DOCUMENTS'
END;

UPDATE application_document_requirement_snapshots
SET capture_section_code = CASE
    WHEN requirement_code IN ('IDENTITY_DOCUMENT', 'NATIONAL_ID', 'BIRTH_CERTIFICATE', 'PASSPORT') THEN 'PERSONAL_DETAILS'
    WHEN requirement_code IN ('ACADEMIC_QUALIFICATIONS', 'ACADEMIC_QUALIFICATION_EVIDENCE') THEN 'QUALIFICATIONS'
    ELSE 'SUPPORTING_DOCUMENTS'
END;
