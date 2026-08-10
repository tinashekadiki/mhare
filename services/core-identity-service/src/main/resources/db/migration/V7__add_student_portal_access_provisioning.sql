-- Author: Tinashe K

CREATE TABLE student_portal_access_provisioning (
    id uuid PRIMARY KEY,
    conversion_request_id uuid NOT NULL,
    student_id uuid NOT NULL,
    student_number varchar(40) NOT NULL,
    user_id uuid NOT NULL REFERENCES users (id),
    role_assignment_id uuid NOT NULL REFERENCES user_role_assignments (id),
    status varchar(30) NOT NULL,
    provisioned_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_student_portal_access_conversion UNIQUE (conversion_request_id),
    CONSTRAINT uk_student_portal_access_student UNIQUE (student_id),
    CONSTRAINT ck_student_portal_access_status CHECK (status IN ('PROVISIONED', 'REVOKED'))
);

CREATE TABLE student_portal_access_provisioning_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    conversion_request_id uuid, student_id uuid, student_number varchar(40), user_id uuid,
    role_assignment_id uuid, status varchar(30), provisioned_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
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
    CONSTRAINT ck_core_identity_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD')),
    CONSTRAINT ck_core_identity_outbox_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_core_identity_outbox_dispatch
    ON integration_outbox (next_attempt_at, occurred_at) WHERE status = 'PENDING';

CREATE TABLE integration_inbox (
    event_id uuid PRIMARY KEY,
    event_type varchar(160) NOT NULL,
    source_service varchar(100) NOT NULL,
    payload jsonb NOT NULL,
    received_at timestamptz NOT NULL,
    processed_at timestamptz
);

CREATE INDEX idx_core_identity_inbox_processed_at ON integration_inbox (processed_at);
