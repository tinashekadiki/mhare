-- Author: Tinashe K

CREATE TABLE admission_quotas (
    id uuid PRIMARY KEY,
    intake_id uuid NOT NULL,
    programme_id uuid NOT NULL,
    programme_code varchar(50) NOT NULL,
    programme_name varchar(200) NOT NULL,
    quota_type_code varchar(50) NOT NULL,
    capacity integer NOT NULL,
    reserved_capacity integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_admission_quota_capacity CHECK (capacity > 0),
    CONSTRAINT ck_admission_quota_reserved_capacity CHECK (
        reserved_capacity >= 0 AND reserved_capacity <= capacity
    )
);

CREATE UNIQUE INDEX uk_admission_quotas_current_scope
    ON admission_quotas (intake_id, programme_id, quota_type_code)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_admission_quotas_intake_programme
    ON admission_quotas (intake_id, programme_id)
    WHERE deleted_at IS NULL;

CREATE TABLE admission_quotas_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    intake_id uuid,
    programme_id uuid,
    programme_code varchar(50),
    programme_name varchar(200),
    quota_type_code varchar(50),
    capacity integer,
    reserved_capacity integer,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON admission_quotas, admission_quotas_aud TO emhare_service;
