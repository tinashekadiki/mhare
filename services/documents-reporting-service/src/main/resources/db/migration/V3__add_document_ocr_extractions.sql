-- Author: Tinashe K
-- Durable asynchronous OCR extraction evidence owned by Documents and Reporting.

CREATE TABLE document_ocr_extractions (
    id uuid PRIMARY KEY,
    uploaded_document_id uuid NOT NULL REFERENCES uploaded_documents(id),
    status varchar(20) NOT NULL,
    engine_name varchar(80) NOT NULL,
    engine_version varchar(40) NOT NULL,
    structured_extraction_json jsonb,
    proposed_facts_json jsonb,
    confidence_json jsonb,
    warnings_json jsonb,
    attempt_count integer NOT NULL,
    next_attempt_at timestamptz NOT NULL,
    queued_at timestamptz NOT NULL,
    started_at timestamptz,
    completed_at timestamptz,
    last_failure_code varchar(80),
    last_failure_message varchar(500),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_document_ocr_extraction_document UNIQUE (uploaded_document_id),
    CONSTRAINT ck_document_ocr_status CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED', 'UNSUPPORTED')),
    CONSTRAINT ck_document_ocr_attempts CHECK (attempt_count BETWEEN 0 AND 3),
    CONSTRAINT ck_document_ocr_soft_delete CHECK (
        (deleted_at IS NULL AND deleted_by_user_id IS NULL)
        OR (deleted_at IS NOT NULL AND deleted_by_user_id IS NOT NULL))
);

CREATE TABLE document_ocr_extractions_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo(rev),
    revtype smallint,
    uploaded_document_id uuid,
    status varchar(20),
    engine_name varchar(80),
    engine_version varchar(40),
    structured_extraction_json jsonb,
    proposed_facts_json jsonb,
    confidence_json jsonb,
    warnings_json jsonb,
    attempt_count integer,
    next_attempt_at timestamptz,
    queued_at timestamptz,
    started_at timestamptz,
    completed_at timestamptz,
    last_failure_code varchar(80),
    last_failure_message varchar(500),
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

CREATE INDEX idx_document_ocr_ready
    ON document_ocr_extractions(status, next_attempt_at, queued_at)
    WHERE deleted_at IS NULL;

