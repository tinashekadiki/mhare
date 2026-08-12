-- Author: Tinashe K

ALTER TABLE offer_letter_projections
    ADD COLUMN document_version integer,
    ADD COLUMN intake_id uuid,
    ADD COLUMN applicant_user_id uuid;
UPDATE offer_letter_projections SET document_version = 1 WHERE document_version IS NULL;
ALTER TABLE offer_letter_projections ALTER COLUMN document_version SET NOT NULL;
ALTER TABLE offer_letter_projections ADD CONSTRAINT ck_offer_letter_document_version CHECK (document_version > 0);
ALTER TABLE offer_letter_projections DROP CONSTRAINT uk_offer_letter_projection_version;
ALTER TABLE offer_letter_projections ADD CONSTRAINT uk_offer_letter_projection_document_version
    UNIQUE (offer_id, document_version);
ALTER TABLE offer_letter_projections_aud
    ADD COLUMN document_version integer,
    ADD COLUMN intake_id uuid,
    ADD COLUMN applicant_user_id uuid;

CREATE TABLE published_offer_letter_projections (
    id uuid PRIMARY KEY,
    source_event_id uuid NOT NULL UNIQUE,
    offer_id uuid NOT NULL,
    offer_status varchar(30) NOT NULL,
    generated_document_id uuid NOT NULL REFERENCES generated_documents (id),
    document_version integer NOT NULL,
    offer_number varchar(60) NOT NULL,
    application_id uuid NOT NULL,
    application_number varchar(60) NOT NULL,
    applicant_user_id uuid NOT NULL,
    applicant_name varchar(240) NOT NULL,
    intake_id uuid NOT NULL,
    programme_id uuid NOT NULL,
    programme_code varchar(50) NOT NULL,
    programme_name varchar(200) NOT NULL,
    published_at timestamptz NOT NULL,
    current_publication boolean NOT NULL,
    superseded_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_published_offer_version UNIQUE (offer_id, document_version),
    CONSTRAINT ck_published_offer_status CHECK (
        offer_status IN ('SENT', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'CONVERTED', 'WITHDRAWN')),
    CONSTRAINT ck_published_offer_current CHECK (
        (current_publication AND superseded_at IS NULL)
        OR (NOT current_publication AND superseded_at IS NOT NULL)
    )
);
CREATE UNIQUE INDEX uk_published_offer_current
    ON published_offer_letter_projections (offer_id)
    WHERE current_publication AND deleted_at IS NULL;
CREATE INDEX idx_published_offer_export
    ON published_offer_letter_projections (intake_id, programme_id, applicant_name, application_number)
    WHERE current_publication AND deleted_at IS NULL AND offer_status <> 'WITHDRAWN';

CREATE TABLE offer_letter_export_audits (
    id uuid PRIMARY KEY,
    requested_by_user_id uuid NOT NULL,
    intake_id uuid NOT NULL,
    programme_id uuid NOT NULL,
    export_format varchar(30) NOT NULL,
    included_document_count integer NOT NULL,
    requested_at timestamptz NOT NULL,
    completed_at timestamptz,
    checksum_sha256 varchar(64),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_offer_letter_export_format CHECK (export_format IN ('MERGED_PDF', 'ZIP')),
    CONSTRAINT ck_offer_letter_export_count CHECK (included_document_count >= 0)
);

CREATE TABLE published_offer_letter_projections_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    source_event_id uuid, offer_id uuid, offer_status varchar(30), generated_document_id uuid,
    document_version integer, offer_number varchar(60), application_id uuid, application_number varchar(60),
    applicant_user_id uuid, applicant_name varchar(240), intake_id uuid, programme_id uuid,
    programme_code varchar(50), programme_name varchar(200), published_at timestamptz,
    current_publication boolean, superseded_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);
CREATE TABLE offer_letter_export_audits_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    requested_by_user_id uuid, intake_id uuid, programme_id uuid, export_format varchar(30),
    included_document_count integer, requested_at timestamptz, completed_at timestamptz,
    checksum_sha256 varchar(64), created_at timestamptz, updated_at timestamptz,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    published_offer_letter_projections, published_offer_letter_projections_aud,
    offer_letter_export_audits, offer_letter_export_audits_aud
TO emhare_service;
