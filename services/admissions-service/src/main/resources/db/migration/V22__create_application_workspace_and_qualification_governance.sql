ALTER TABLE applications
    ADD COLUMN sections_complete boolean NOT NULL DEFAULT false,
    ADD COLUMN declaration_accepted_at timestamptz,
    ADD COLUMN declaration_accepted_by_user_id uuid,
    ADD COLUMN declaration_version varchar(50);

ALTER TABLE applications_aud
    ADD COLUMN sections_complete boolean,
    ADD COLUMN declaration_accepted_at timestamptz,
    ADD COLUMN declaration_accepted_by_user_id uuid,
    ADD COLUMN declaration_version varchar(50);

CREATE TABLE application_type_sections (
    id uuid PRIMARY KEY,
    application_type_id uuid NOT NULL REFERENCES application_types (id),
    section_code varchar(60) NOT NULL,
    section_name varchar(150) NOT NULL,
    is_required boolean NOT NULL,
    is_repeatable boolean NOT NULL,
    minimum_records integer NOT NULL DEFAULT 0,
    sort_order integer NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_application_type_sections_code UNIQUE (application_type_id, section_code),
    CONSTRAINT ck_application_type_sections_minimum_records CHECK (minimum_records >= 0),
    CONSTRAINT ck_application_type_sections_sort_order CHECK (sort_order > 0)
);

CREATE TABLE application_sections (
    id uuid PRIMARY KEY,
    application_id uuid NOT NULL REFERENCES applications (id),
    section_code varchar(60) NOT NULL,
    section_name varchar(150) NOT NULL,
    is_required boolean NOT NULL,
    is_repeatable boolean NOT NULL,
    minimum_records integer NOT NULL DEFAULT 0,
    sort_order integer NOT NULL,
    status varchar(30) NOT NULL,
    completed_at timestamptz,
    completion_summary varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_application_sections_code UNIQUE (application_id, section_code),
    CONSTRAINT ck_application_sections_status CHECK (status IN (
        'NOT_STARTED', 'IN_PROGRESS', 'COMPLETE', 'VERIFIED', 'REJECTED', 'CORRECTION_REQUIRED'
    )),
    CONSTRAINT ck_application_sections_minimum_records CHECK (minimum_records >= 0),
    CONSTRAINT ck_application_sections_sort_order CHECK (sort_order > 0)
);

CREATE INDEX idx_application_sections_application_status
    ON application_sections (application_id, status)
    WHERE deleted_at IS NULL;

CREATE TABLE applicant_next_of_kin (
    id uuid PRIMARY KEY,
    applicant_id uuid NOT NULL REFERENCES applicants (id),
    full_name varchar(200) NOT NULL,
    relationship_code varchar(50) NOT NULL,
    phone_number varchar(50) NOT NULL,
    email varchar(200),
    address varchar(500),
    is_primary boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);

CREATE UNIQUE INDEX uk_applicant_next_of_kin_primary
    ON applicant_next_of_kin (applicant_id)
    WHERE is_primary AND deleted_at IS NULL;

CREATE TABLE applicant_employment_histories (
    id uuid PRIMARY KEY,
    applicant_id uuid NOT NULL REFERENCES applicants (id),
    employer_name varchar(200) NOT NULL,
    position_title varchar(150) NOT NULL,
    started_on date NOT NULL,
    ended_on date,
    is_current boolean NOT NULL,
    responsibilities varchar(2000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_applicant_employment_dates CHECK (ended_on IS NULL OR ended_on >= started_on),
    CONSTRAINT ck_applicant_current_employment_end CHECK (NOT is_current OR ended_on IS NULL)
);

CREATE TABLE applicant_referees (
    id uuid PRIMARY KEY,
    applicant_id uuid NOT NULL REFERENCES applicants (id),
    full_name varchar(200) NOT NULL,
    title varchar(100),
    organisation varchar(200) NOT NULL,
    position_title varchar(150),
    email varchar(200) NOT NULL,
    phone_number varchar(50),
    verification_status varchar(30) NOT NULL,
    reference_document_id uuid,
    verified_by_user_id uuid,
    verified_at timestamptz,
    rejection_reason varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_applicant_referee_status CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED'))
);

ALTER TABLE applicant_qualification_sittings
    ADD COLUMN verification_status varchar(30) NOT NULL DEFAULT 'CAPTURED',
    ADD COLUMN verified_by_user_id uuid,
    ADD COLUMN verified_at timestamptz,
    ADD COLUMN rejection_reason varchar(1000),
    ADD CONSTRAINT ck_qualification_sitting_verification_status
        CHECK (verification_status IN ('CAPTURED', 'VERIFIED', 'REJECTED'));

ALTER TABLE applicant_qualification_sittings_aud
    ADD COLUMN verification_status varchar(30),
    ADD COLUMN verified_by_user_id uuid,
    ADD COLUMN verified_at timestamptz,
    ADD COLUMN rejection_reason varchar(1000);

CREATE TABLE application_type_sections_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    application_type_id uuid,
    section_code varchar(60),
    section_name varchar(150),
    is_required boolean,
    is_repeatable boolean,
    minimum_records integer,
    sort_order integer,
    is_active boolean,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE application_sections_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    application_id uuid,
    section_code varchar(60),
    section_name varchar(150),
    is_required boolean,
    is_repeatable boolean,
    minimum_records integer,
    sort_order integer,
    status varchar(30),
    completed_at timestamptz,
    completion_summary varchar(1000),
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE applicant_next_of_kin_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    applicant_id uuid,
    full_name varchar(200),
    relationship_code varchar(50),
    phone_number varchar(50),
    email varchar(200),
    address varchar(500),
    is_primary boolean,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE applicant_employment_histories_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    applicant_id uuid,
    employer_name varchar(200),
    position_title varchar(150),
    started_on date,
    ended_on date,
    is_current boolean,
    responsibilities varchar(2000),
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE applicant_referees_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    applicant_id uuid,
    full_name varchar(200),
    title varchar(100),
    organisation varchar(200),
    position_title varchar(150),
    email varchar(200),
    phone_number varchar(50),
    verification_status varchar(30),
    reference_document_id uuid,
    verified_by_user_id uuid,
    verified_at timestamptz,
    rejection_reason varchar(1000),
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

INSERT INTO application_type_sections (
    id, application_type_id, section_code, section_name, is_required, is_repeatable,
    minimum_records, sort_order, is_active, created_at, updated_at, version
)
SELECT gen_random_uuid(), application_type.id, definition.section_code, definition.section_name,
       true, definition.is_repeatable, definition.minimum_records, definition.sort_order,
       true, now(), now(), 0
FROM application_types application_type
CROSS JOIN (VALUES
    ('PERSONAL_DETAILS', 'Personal details', false, 0, 10),
    ('NEXT_OF_KIN', 'Next of kin', true, 1, 20),
    ('QUALIFICATIONS', 'Qualifications', true, 1, 30),
    ('PROGRAMME_CHOICES', 'Programme choices', true, 1, 60),
    ('DOCUMENTS', 'Supporting documents', true, 0, 70),
    ('PAYMENT', 'Application fee', false, 0, 80),
    ('REVIEW_DECLARATION', 'Review and declaration', false, 0, 90)
) AS definition(section_code, section_name, is_repeatable, minimum_records, sort_order)
WHERE application_type.deleted_at IS NULL;

INSERT INTO application_type_sections (
    id, application_type_id, section_code, section_name, is_required, is_repeatable,
    minimum_records, sort_order, is_active, created_at, updated_at, version
)
SELECT gen_random_uuid(), id, 'EMPLOYMENT_HISTORY', 'Employment history', true, true,
       1, 40, true, now(), now(), 0
FROM application_types
WHERE requires_employment_history AND deleted_at IS NULL;

INSERT INTO application_type_sections (
    id, application_type_id, section_code, section_name, is_required, is_repeatable,
    minimum_records, sort_order, is_active, created_at, updated_at, version
)
SELECT gen_random_uuid(), id, 'REFEREES', 'Referees', true, true,
       2, 50, true, now(), now(), 0
FROM application_types
WHERE requires_referees AND deleted_at IS NULL;

INSERT INTO application_sections (
    id, application_id, section_code, section_name, is_required, is_repeatable,
    minimum_records, sort_order, status, completed_at, completion_summary,
    created_at, updated_at, version
)
SELECT gen_random_uuid(), application.id, definition.section_code, definition.section_name,
       CASE
           WHEN definition.section_code = 'DOCUMENTS' THEN EXISTS (
               SELECT 1 FROM application_type_document_requirements requirement
               WHERE requirement.application_type_id = application.application_type_id
                 AND requirement.is_required AND requirement.is_active AND requirement.deleted_at IS NULL
           )
           WHEN definition.section_code = 'PAYMENT' THEN application.payment_required
           ELSE definition.is_required
       END,
       definition.is_repeatable, definition.minimum_records, definition.sort_order,
       CASE
           WHEN definition.section_code = 'PROGRAMME_CHOICES' AND EXISTS (
               SELECT 1 FROM application_programme_choices choice
               WHERE choice.application_id = application.id AND choice.deleted_at IS NULL
           ) THEN 'COMPLETE'
           WHEN definition.section_code = 'PAYMENT' AND NOT application.payment_required THEN 'COMPLETE'
           WHEN definition.section_code = 'PAYMENT'
                AND (application.payment_confirmed_at IS NOT NULL OR application.payment_override_by_user_id IS NOT NULL)
               THEN 'COMPLETE'
           ELSE 'NOT_STARTED'
       END,
       NULL, NULL, now(), now(), 0
FROM applications application
JOIN application_type_sections definition
  ON definition.application_type_id = application.application_type_id
WHERE application.deleted_at IS NULL AND definition.deleted_at IS NULL AND definition.is_active;
