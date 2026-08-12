-- Author: Tinashe K

ALTER TABLE programme_versions
    ADD COLUMN minimum_entry_option_selections integer NOT NULL DEFAULT 0,
    ADD COLUMN maximum_entry_option_selections integer NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_programme_version_entry_option_limits CHECK (
        minimum_entry_option_selections >= 0
        AND maximum_entry_option_selections >= minimum_entry_option_selections
    );

ALTER TABLE programme_versions_aud
    ADD COLUMN minimum_entry_option_selections integer,
    ADD COLUMN maximum_entry_option_selections integer;

CREATE TABLE programme_entry_options (
    id uuid PRIMARY KEY,
    programme_version_id uuid NOT NULL REFERENCES programme_versions (id),
    code varchar(50) NOT NULL,
    name varchar(200) NOT NULL,
    description varchar(1000),
    sort_order integer NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_programme_entry_options_code UNIQUE (programme_version_id, code),
    CONSTRAINT uk_programme_entry_options_sort UNIQUE (programme_version_id, sort_order),
    CONSTRAINT ck_programme_entry_options_code CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]*$'),
    CONSTRAINT ck_programme_entry_options_sort CHECK (sort_order > 0)
);

CREATE INDEX ix_programme_entry_options_version
    ON programme_entry_options (programme_version_id, sort_order)
    WHERE deleted_at IS NULL AND is_active;

CREATE TABLE programme_entry_options_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    programme_version_id uuid,
    code varchar(50),
    name varchar(200),
    description varchar(1000),
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

GRANT SELECT, INSERT, UPDATE, DELETE ON programme_entry_options TO emhare_service;
GRANT SELECT, INSERT, UPDATE, DELETE ON programme_entry_options_aud TO emhare_service;
