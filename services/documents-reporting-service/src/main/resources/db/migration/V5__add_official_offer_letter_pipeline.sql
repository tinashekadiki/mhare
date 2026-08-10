-- Author: Tinashe K

CREATE TABLE offer_letter_projections (
    id uuid PRIMARY KEY,
    source_event_id uuid NOT NULL UNIQUE,
    offer_id uuid NOT NULL,
    offer_version bigint NOT NULL,
    offer_number varchar(60) NOT NULL,
    application_id uuid NOT NULL,
    application_number varchar(60) NOT NULL,
    applicant_number varchar(60) NOT NULL,
    applicant_name varchar(240) NOT NULL,
    applicant_email varchar(250) NOT NULL,
    programme_id uuid NOT NULL,
    programme_code varchar(50) NOT NULL,
    programme_name varchar(200) NOT NULL,
    offer_type varchar(30) NOT NULL,
    conditions_text varchar(4000),
    acceptance_deadline timestamptz NOT NULL,
    registration_date date,
    orientation_date date,
    commencement_date date NOT NULL,
    requested_by_user_id uuid NOT NULL,
    requested_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_offer_letter_projection_version UNIQUE (offer_id, offer_version),
    CONSTRAINT ck_offer_letter_projection_type CHECK (offer_type IN ('FIRM', 'CONDITIONAL'))
);

ALTER TABLE generated_documents
    ADD COLUMN offer_letter_projection_id uuid REFERENCES offer_letter_projections (id);
ALTER TABLE generated_documents ALTER COLUMN student_id DROP NOT NULL;
ALTER TABLE generated_documents ALTER COLUMN student_number DROP NOT NULL;
ALTER TABLE generated_documents ALTER COLUMN programme_version_id DROP NOT NULL;
ALTER TABLE generated_documents ALTER COLUMN academic_period_id DROP NOT NULL;
ALTER TABLE generated_documents ALTER COLUMN academic_period_code DROP NOT NULL;
ALTER TABLE generated_documents ALTER COLUMN source_progression_decision_id DROP NOT NULL;
ALTER TABLE generated_documents ALTER COLUMN progression_decision_projection_id DROP NOT NULL;
ALTER TABLE generated_documents DROP CONSTRAINT ck_generated_document_type;
ALTER TABLE generated_documents ADD CONSTRAINT ck_generated_document_type
    CHECK (document_type IN ('RESULT_SLIP', 'OFFER_LETTER'));
ALTER TABLE generated_documents DROP CONSTRAINT ck_generated_document_versions;
ALTER TABLE generated_documents ADD CONSTRAINT ck_generated_document_versions CHECK (
    source_progression_decision_version > 0 AND template_version > 0 AND generation_attempt_count >= 0
);
ALTER TABLE generated_documents ADD CONSTRAINT ck_generated_document_source_kind CHECK (
    (document_type = 'RESULT_SLIP' AND progression_decision_projection_id IS NOT NULL
        AND source_progression_decision_id IS NOT NULL AND offer_letter_projection_id IS NULL)
    OR (document_type = 'OFFER_LETTER' AND offer_letter_projection_id IS NOT NULL
        AND progression_decision_projection_id IS NULL AND source_progression_decision_id IS NULL)
);
CREATE UNIQUE INDEX uk_generated_offer_letter
    ON generated_documents (offer_letter_projection_id)
    WHERE offer_letter_projection_id IS NOT NULL AND deleted_at IS NULL;

ALTER TABLE generated_documents_aud ADD COLUMN offer_letter_projection_id uuid;

CREATE TABLE offer_letter_projections_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    source_event_id uuid, offer_id uuid, offer_version bigint, offer_number varchar(60),
    application_id uuid, application_number varchar(60), applicant_number varchar(60),
    applicant_name varchar(240), applicant_email varchar(250), programme_id uuid,
    programme_code varchar(50), programme_name varchar(200), offer_type varchar(30),
    conditions_text varchar(4000), acceptance_deadline timestamptz, registration_date date,
    orientation_date date, commencement_date date, requested_by_user_id uuid, requested_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    offer_letter_projections, offer_letter_projections_aud TO emhare_service;
