CREATE TABLE institution_profile (
    id uuid PRIMARY KEY,
    code varchar(50) NOT NULL,
    name varchar(200) NOT NULL,
    legal_name varchar(250) NOT NULL,
    default_currency_code varchar(3) NOT NULL DEFAULT 'USD',
    country_code varchar(2) NOT NULL,
    timezone varchar(80) NOT NULL,
    contact_details_json jsonb,
    branding_json jsonb,
    legacy_code varchar(50),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_institution_profile_code UNIQUE (code)
);

CREATE TABLE users (
    id uuid PRIMARY KEY,
    keycloak_user_id uuid,
    username varchar(150) NOT NULL,
    email varchar(200) NOT NULL,
    phone_number varchar(50),
    display_name varchar(200) NOT NULL,
    status varchar(30) NOT NULL,
    last_login_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_users_keycloak_user_id UNIQUE (keycloak_user_id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE roles (
    id uuid PRIMARY KEY,
    code varchar(80) NOT NULL,
    name varchar(150) NOT NULL,
    scope varchar(30) NOT NULL,
    is_system_managed boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_roles_code UNIQUE (code)
);

CREATE TABLE permissions (
    id uuid PRIMARY KEY,
    code varchar(120) NOT NULL,
    name varchar(180) NOT NULL,
    category varchar(40) NOT NULL,
    description varchar(500),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_permissions_code UNIQUE (code)
);

CREATE TABLE role_permissions (
    id uuid PRIMARY KEY,
    role_id uuid NOT NULL REFERENCES roles (id),
    permission_id uuid NOT NULL REFERENCES permissions (id),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_role_permissions_role_permission UNIQUE (role_id, permission_id)
);

CREATE TABLE user_role_assignments (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users (id),
    role_id uuid NOT NULL REFERENCES roles (id),
    academic_unit_id uuid,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);

CREATE UNIQUE INDEX uk_user_role_assignments_active
    ON user_role_assignments (user_id, role_id, academic_unit_id)
    WHERE ends_at IS NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX uk_user_role_assignments_active_system
    ON user_role_assignments (user_id, role_id)
    WHERE academic_unit_id IS NULL AND ends_at IS NULL AND deleted_at IS NULL;

CREATE TABLE institution_profile_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    code varchar(50),
    name varchar(200),
    legal_name varchar(250),
    default_currency_code varchar(3),
    country_code varchar(2),
    timezone varchar(80),
    contact_details_json jsonb,
    branding_json jsonb,
    legacy_code varchar(50),
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE users_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    keycloak_user_id uuid,
    username varchar(150),
    email varchar(200),
    phone_number varchar(50),
    display_name varchar(200),
    status varchar(30),
    last_login_at timestamptz,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE roles_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    code varchar(80),
    name varchar(150),
    scope varchar(30),
    is_system_managed boolean,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE permissions_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    code varchar(120),
    name varchar(180),
    category varchar(40),
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

CREATE TABLE role_permissions_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    role_id uuid,
    permission_id uuid,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE user_role_assignments_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    user_id uuid,
    role_id uuid,
    academic_unit_id uuid,
    starts_at timestamptz,
    ends_at timestamptz,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);
