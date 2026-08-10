-- Author: Tinashe K

ALTER TABLE admission_cycles
    ADD COLUMN application_type_id uuid REFERENCES application_types (id);
ALTER TABLE admission_cycles_aud
    ADD COLUMN application_type_id uuid;

CREATE TABLE admission_cycle_archive_summaries (
    id uuid PRIMARY KEY,
    admission_cycle_id uuid NOT NULL REFERENCES admission_cycles (id),
    total_applications integer NOT NULL,
    submitted_applications integer NOT NULL,
    eligible_applications integer NOT NULL,
    selected_applications integer NOT NULL,
    offered_applications integer NOT NULL,
    accepted_applications integer NOT NULL,
    converted_applications integer NOT NULL,
    archived_by_user_id uuid NOT NULL,
    archived_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_admission_cycle_archive_summaries_cycle UNIQUE (admission_cycle_id)
);

CREATE TABLE admission_cycle_archive_summaries_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    admission_cycle_id uuid,
    total_applications integer,
    submitted_applications integer,
    eligible_applications integer,
    selected_applications integer,
    offered_applications integer,
    accepted_applications integer,
    converted_applications integer,
    archived_by_user_id uuid,
    archived_at timestamptz,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    admission_cycle_archive_summaries,
    admission_cycle_archive_summaries_aud
TO emhare_service;
