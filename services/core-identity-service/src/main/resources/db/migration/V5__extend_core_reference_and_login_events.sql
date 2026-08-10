CREATE TABLE countries (
    id uuid PRIMARY KEY,
    iso2_code varchar(2) NOT NULL,
    iso3_code varchar(3) NOT NULL,
    name varchar(150) NOT NULL,
    nationality_name varchar(150) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_countries_iso2_code UNIQUE (iso2_code),
    CONSTRAINT uk_countries_iso3_code UNIQUE (iso3_code)
);

CREATE TABLE lookup_sets (
    id uuid PRIMARY KEY,
    code varchar(80) NOT NULL,
    name varchar(150) NOT NULL,
    description varchar(500),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_lookup_sets_code UNIQUE (code)
);

CREATE TABLE lookup_values (
    id uuid PRIMARY KEY,
    lookup_set_id uuid NOT NULL REFERENCES lookup_sets (id),
    code varchar(80) NOT NULL,
    name varchar(150) NOT NULL,
    sort_order integer NOT NULL DEFAULT 0,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_lookup_values_set_code UNIQUE (lookup_set_id, code)
);

CREATE TABLE login_events (
    id uuid PRIMARY KEY,
    user_id uuid REFERENCES users (id),
    keycloak_user_id uuid,
    username varchar(150),
    email varchar(200),
    occurred_at timestamptz NOT NULL,
    ip_address varchar(80),
    user_agent varchar(500),
    outcome varchar(30) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);

CREATE INDEX ix_lookup_values_lookup_set_id ON lookup_values (lookup_set_id);
CREATE INDEX ix_login_events_user_id_occurred_at ON login_events (user_id, occurred_at DESC);
CREATE INDEX ix_login_events_keycloak_user_id_occurred_at ON login_events (keycloak_user_id, occurred_at DESC);

CREATE TABLE countries_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    iso2_code varchar(2),
    iso3_code varchar(3),
    name varchar(150),
    nationality_name varchar(150),
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE lookup_sets_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    code varchar(80),
    name varchar(150),
    description varchar(500),
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE lookup_values_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    lookup_set_id uuid,
    code varchar(80),
    name varchar(150),
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

CREATE TABLE login_events_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    user_id uuid,
    keycloak_user_id uuid,
    username varchar(150),
    email varchar(200),
    occurred_at timestamptz,
    ip_address varchar(80),
    user_agent varchar(500),
    outcome varchar(30),
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);
