-- Author: Tinashe K

CREATE TABLE application_type_document_requirements (
    id uuid PRIMARY KEY,
    application_type_id uuid NOT NULL REFERENCES application_types (id),
    requirement_code varchar(80) NOT NULL,
    requirement_name varchar(150) NOT NULL,
    is_required boolean NOT NULL,
    sort_order integer NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_application_type_document_requirement UNIQUE (application_type_id, requirement_code),
    CONSTRAINT ck_application_type_document_requirement_values CHECK (
        length(trim(requirement_code)) > 0
        AND length(trim(requirement_name)) > 0
        AND sort_order > 0
    ),
    CONSTRAINT ck_application_type_document_requirement_soft_delete CHECK (
        (deleted_at IS NULL AND deleted_by_user_id IS NULL)
        OR (deleted_at IS NOT NULL AND deleted_by_user_id IS NOT NULL)
    )
);

CREATE INDEX idx_application_type_document_requirements_active
    ON application_type_document_requirements (application_type_id, sort_order, requirement_code)
    WHERE is_active AND deleted_at IS NULL;

CREATE TABLE application_type_document_requirements_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    application_type_id uuid,
    requirement_code varchar(80),
    requirement_name varchar(150),
    is_required boolean,
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

ALTER TABLE application_documents
    ADD COLUMN document_file_name varchar(255),
    ADD COLUMN document_mime_type varchar(100),
    ADD COLUMN document_checksum_sha256 varchar(64),
    ADD COLUMN linked_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN is_current boolean NOT NULL DEFAULT true,
    ADD COLUMN supersedes_application_document_id uuid REFERENCES application_documents (id),
    ADD COLUMN verified_by_user_id uuid,
    ADD COLUMN verified_at timestamptz,
    ADD COLUMN rejection_reason varchar(1000),
    ADD COLUMN last_verification_event_id uuid,
    ADD COLUMN last_document_version bigint NOT NULL DEFAULT 0;

ALTER TABLE application_documents_aud
    ADD COLUMN document_file_name varchar(255),
    ADD COLUMN document_mime_type varchar(100),
    ADD COLUMN document_checksum_sha256 varchar(64),
    ADD COLUMN linked_at timestamptz,
    ADD COLUMN is_current boolean,
    ADD COLUMN supersedes_application_document_id uuid,
    ADD COLUMN verified_by_user_id uuid,
    ADD COLUMN verified_at timestamptz,
    ADD COLUMN rejection_reason varchar(1000),
    ADD COLUMN last_verification_event_id uuid,
    ADD COLUMN last_document_version bigint;

ALTER TABLE application_documents
    ADD CONSTRAINT ck_application_documents_status CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    ADD CONSTRAINT ck_application_documents_metadata CHECK (
        (document_file_name IS NULL AND document_mime_type IS NULL AND document_checksum_sha256 IS NULL)
        OR (document_file_name IS NOT NULL AND document_mime_type IS NOT NULL
            AND document_checksum_sha256 IS NOT NULL AND length(document_checksum_sha256) = 64)
    ),
    ADD CONSTRAINT ck_application_documents_verification_evidence CHECK (
        (status = 'PENDING' AND verified_by_user_id IS NULL AND verified_at IS NULL AND rejection_reason IS NULL)
        OR (status = 'VERIFIED' AND verified_by_user_id IS NOT NULL AND verified_at IS NOT NULL
            AND rejection_reason IS NULL)
        OR (status = 'REJECTED' AND verified_by_user_id IS NOT NULL AND verified_at IS NOT NULL
            AND rejection_reason IS NOT NULL AND length(trim(rejection_reason)) >= 10)
    ),
    ADD CONSTRAINT ck_application_documents_projection_version CHECK (last_document_version >= 0),
    ADD CONSTRAINT ck_application_documents_supersession CHECK (
        supersedes_application_document_id IS NULL OR supersedes_application_document_id <> id
    );

CREATE UNIQUE INDEX uk_application_documents_current_requirement
    ON application_documents (application_id, requirement_code)
    WHERE is_current AND deleted_at IS NULL;
CREATE UNIQUE INDEX uk_application_documents_last_verification_event
    ON application_documents (last_verification_event_id)
    WHERE last_verification_event_id IS NOT NULL;
CREATE INDEX idx_application_documents_verification_status
    ON application_documents (application_id, status, requirement_code)
    WHERE is_current AND deleted_at IS NULL;

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    application_type_document_requirements,
    application_type_document_requirements_aud
TO emhare_service;
