-- Author: Tinashe K

CREATE TABLE uploaded_documents (
    id uuid PRIMARY KEY,
    owner_type varchar(40) NOT NULL,
    owner_id uuid NOT NULL,
    document_type_code varchar(80) NOT NULL,
    original_file_name varchar(255) NOT NULL,
    storage_bucket varchar(100) NOT NULL,
    storage_key varchar(500) NOT NULL,
    storage_object_version varchar(200),
    mime_type varchar(100) NOT NULL,
    file_size_bytes bigint NOT NULL,
    checksum_sha256 varchar(64) NOT NULL,
    uploaded_by_user_id uuid NOT NULL,
    uploaded_at timestamptz NOT NULL,
    verification_status varchar(20) NOT NULL,
    verified_by_user_id uuid,
    verified_at timestamptz,
    verification_comment varchar(1000),
    rejection_reason varchar(1000),
    replaces_document_id uuid REFERENCES uploaded_documents (id),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_uploaded_documents_storage UNIQUE (storage_bucket, storage_key),
    CONSTRAINT ck_uploaded_documents_owner_type CHECK (owner_type IN (
        'APPLICANT', 'APPLICATION', 'STUDENT', 'STAFF', 'FINANCE_RECORD', 'ACADEMIC_WORKFLOW'
    )),
    CONSTRAINT ck_uploaded_documents_file CHECK (
        length(trim(original_file_name)) > 0
        AND length(trim(document_type_code)) > 0
        AND mime_type IN ('application/pdf', 'image/jpeg', 'image/png')
        AND file_size_bytes > 0
        AND length(checksum_sha256) = 64
    ),
    CONSTRAINT ck_uploaded_documents_verification_status CHECK (
        verification_status IN ('PENDING', 'VERIFIED', 'REJECTED')
    ),
    CONSTRAINT ck_uploaded_documents_verification_evidence CHECK (
        (verification_status = 'PENDING'
            AND verified_by_user_id IS NULL AND verified_at IS NULL
            AND verification_comment IS NULL AND rejection_reason IS NULL)
        OR (verification_status = 'VERIFIED'
            AND verified_by_user_id IS NOT NULL AND verified_at IS NOT NULL
            AND rejection_reason IS NULL)
        OR (verification_status = 'REJECTED'
            AND verified_by_user_id IS NOT NULL AND verified_at IS NOT NULL
            AND rejection_reason IS NOT NULL AND length(trim(rejection_reason)) >= 10)
    ),
    CONSTRAINT ck_uploaded_documents_replacement CHECK (replaces_document_id IS NULL OR replaces_document_id <> id),
    CONSTRAINT ck_uploaded_documents_soft_delete CHECK (
        (deleted_at IS NULL AND deleted_by_user_id IS NULL)
        OR (deleted_at IS NOT NULL AND deleted_by_user_id IS NOT NULL)
    )
);

CREATE INDEX idx_uploaded_documents_owner
    ON uploaded_documents (owner_type, owner_id, uploaded_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_uploaded_documents_verification_queue
    ON uploaded_documents (uploaded_at, id)
    WHERE verification_status = 'PENDING' AND deleted_at IS NULL;
CREATE INDEX idx_uploaded_documents_uploader
    ON uploaded_documents (uploaded_by_user_id, uploaded_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_uploaded_documents_replacement
    ON uploaded_documents (replaces_document_id)
    WHERE replaces_document_id IS NOT NULL;

CREATE TABLE uploaded_documents_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    owner_type varchar(40),
    owner_id uuid,
    document_type_code varchar(80),
    original_file_name varchar(255),
    storage_bucket varchar(100),
    storage_key varchar(500),
    storage_object_version varchar(200),
    mime_type varchar(100),
    file_size_bytes bigint,
    checksum_sha256 varchar(64),
    uploaded_by_user_id uuid,
    uploaded_at timestamptz,
    verification_status varchar(20),
    verified_by_user_id uuid,
    verified_at timestamptz,
    verification_comment varchar(1000),
    rejection_reason varchar(1000),
    replaces_document_id uuid,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE integration_outbox (
    id uuid PRIMARY KEY,
    event_type varchar(160) NOT NULL,
    routing_key varchar(160) NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamptz NOT NULL,
    status varchar(20) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    published_at timestamptz,
    last_error varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_documents_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD')),
    CONSTRAINT ck_documents_outbox_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_documents_outbox_dispatch
    ON integration_outbox (next_attempt_at, occurred_at, id)
    WHERE status = 'PENDING';

CREATE OR REPLACE FUNCTION prevent_uploaded_document_content_change()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Uploaded document evidence cannot be physically deleted';
    END IF;
    IF to_jsonb(NEW) - ARRAY[
        'verification_status', 'verified_by_user_id', 'verified_at',
        'verification_comment', 'rejection_reason', 'updated_at',
        'modified_by_user_id', 'deleted_at', 'deleted_by_user_id', 'version'
    ] IS DISTINCT FROM to_jsonb(OLD) - ARRAY[
        'verification_status', 'verified_by_user_id', 'verified_at',
        'verification_comment', 'rejection_reason', 'updated_at',
        'modified_by_user_id', 'deleted_at', 'deleted_by_user_id', 'version'
    ] THEN
        RAISE EXCEPTION 'Uploaded document content and ownership evidence is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_uploaded_document_content_immutable
    BEFORE UPDATE OR DELETE ON uploaded_documents
    FOR EACH ROW EXECUTE FUNCTION prevent_uploaded_document_content_change();

GRANT SELECT, INSERT, UPDATE ON TABLE
    uploaded_documents,
    uploaded_documents_aud,
    integration_outbox
TO emhare_service;
