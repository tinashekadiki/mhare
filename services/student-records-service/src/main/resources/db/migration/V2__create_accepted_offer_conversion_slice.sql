-- Author: Tinashe K

CREATE SEQUENCE student_number_sequence START WITH 1 INCREMENT BY 1;

CREATE TABLE students (
    id uuid PRIMARY KEY,
    student_number varchar(40) NOT NULL,
    user_id uuid NOT NULL,
    source_applicant_id uuid NOT NULL,
    source_applicant_number varchar(40) NOT NULL,
    source_application_id uuid NOT NULL,
    source_offer_id uuid NOT NULL,
    applicant_category_code varchar(30) NOT NULL,
    first_name varchar(100) NOT NULL,
    middle_names varchar(150),
    last_name varchar(100) NOT NULL,
    date_of_birth date,
    gender_code varchar(30),
    national_id_number varchar(50),
    passport_number varchar(50),
    primary_email varchar(200) NOT NULL,
    primary_phone varchar(50),
    postal_address varchar(500),
    residential_address varchar(500),
    disability_status_code varchar(30),
    special_needs varchar(1000),
    sponsor_type_code varchar(30),
    sponsor_details jsonb,
    status varchar(30) NOT NULL,
    activated_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_students_student_number UNIQUE (student_number),
    CONSTRAINT uk_students_user_id UNIQUE (user_id),
    CONSTRAINT uk_students_source_offer UNIQUE (source_offer_id),
    CONSTRAINT ck_students_status CHECK (status IN ('PROVISIONING', 'ACTIVE', 'SUSPENDED', 'WITHDRAWN', 'INACTIVE')),
    CONSTRAINT ck_students_activation CHECK (
        (status = 'PROVISIONING' AND activated_at IS NULL)
        OR (status <> 'PROVISIONING' AND activated_at IS NOT NULL)
    )
);

CREATE TABLE student_status_events (
    id uuid PRIMARY KEY,
    student_id uuid NOT NULL REFERENCES students (id),
    from_status varchar(30),
    to_status varchar(30) NOT NULL,
    reason varchar(1000) NOT NULL,
    changed_by_user_id uuid,
    changed_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_student_status_events_status CHECK (
        (from_status IS NULL OR from_status IN ('PROVISIONING', 'ACTIVE', 'SUSPENDED', 'WITHDRAWN', 'INACTIVE'))
        AND to_status IN ('PROVISIONING', 'ACTIVE', 'SUSPENDED', 'WITHDRAWN', 'INACTIVE')
    )
);

CREATE TABLE student_programme_enrolments (
    id uuid PRIMARY KEY,
    student_id uuid NOT NULL REFERENCES students (id),
    source_offer_id uuid NOT NULL,
    source_programme_choice_id uuid NOT NULL,
    programme_id uuid NOT NULL,
    programme_version_id uuid NOT NULL,
    programme_code varchar(50) NOT NULL,
    programme_name varchar(200) NOT NULL,
    intake_id uuid NOT NULL,
    commencement_date date NOT NULL,
    status varchar(30) NOT NULL,
    status_reason varchar(1000) NOT NULL,
    approved_by_user_id uuid,
    approved_at timestamptz,
    ended_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_student_programme_enrolments_offer UNIQUE (source_offer_id),
    CONSTRAINT ck_student_programme_enrolments_status CHECK (
        status IN ('PROVISIONING', 'ACTIVE', 'DEFERRED', 'SUSPENDED', 'TRANSFERRED', 'WITHDRAWN', 'COMPLETED')
    ),
    CONSTRAINT ck_student_programme_enrolments_end CHECK (
        (status IN ('TRANSFERRED', 'WITHDRAWN', 'COMPLETED') AND ended_at IS NOT NULL)
        OR (status NOT IN ('TRANSFERRED', 'WITHDRAWN', 'COMPLETED') AND ended_at IS NULL)
    )
);

CREATE UNIQUE INDEX uk_student_programme_enrolments_active
    ON student_programme_enrolments (student_id)
    WHERE deleted_at IS NULL AND status IN ('PROVISIONING', 'ACTIVE', 'DEFERRED', 'SUSPENDED');

CREATE TABLE student_conversion_requests (
    id uuid PRIMARY KEY,
    source_event_id uuid NOT NULL,
    source_application_id uuid NOT NULL,
    source_offer_id uuid NOT NULL,
    student_id uuid NOT NULL REFERENCES students (id),
    programme_enrolment_id uuid NOT NULL REFERENCES student_programme_enrolments (id),
    status varchar(30) NOT NULL,
    finance_provisioning_status varchar(30) NOT NULL,
    portal_provisioning_status varchar(30) NOT NULL,
    requested_at timestamptz NOT NULL,
    completed_at timestamptz,
    failure_reason varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_student_conversion_requests_event UNIQUE (source_event_id),
    CONSTRAINT uk_student_conversion_requests_offer UNIQUE (source_offer_id),
    CONSTRAINT ck_student_conversion_requests_status CHECK (status IN ('PROVISIONING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_student_conversion_requests_finance CHECK (finance_provisioning_status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_student_conversion_requests_portal CHECK (portal_provisioning_status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_student_conversion_requests_completion CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL
            AND finance_provisioning_status = 'COMPLETED' AND portal_provisioning_status = 'COMPLETED')
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    )
);

CREATE TABLE students_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    student_number varchar(40), user_id uuid, source_applicant_id uuid, source_applicant_number varchar(40),
    source_application_id uuid, source_offer_id uuid, applicant_category_code varchar(30),
    first_name varchar(100), middle_names varchar(150), last_name varchar(100), date_of_birth date,
    gender_code varchar(30), national_id_number varchar(50), passport_number varchar(50),
    primary_email varchar(200), primary_phone varchar(50), postal_address varchar(500),
    residential_address varchar(500), disability_status_code varchar(30), special_needs varchar(1000),
    sponsor_type_code varchar(30), sponsor_details jsonb, status varchar(30), activated_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE student_status_events_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    student_id uuid, from_status varchar(30), to_status varchar(30), reason varchar(1000),
    changed_by_user_id uuid, changed_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE student_programme_enrolments_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    student_id uuid, source_offer_id uuid, source_programme_choice_id uuid, programme_id uuid,
    programme_version_id uuid, programme_code varchar(50), programme_name varchar(200), intake_id uuid,
    commencement_date date, status varchar(30), status_reason varchar(1000), approved_by_user_id uuid,
    approved_at timestamptz, ended_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE student_conversion_requests_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    source_event_id uuid, source_application_id uuid, source_offer_id uuid, student_id uuid,
    programme_enrolment_id uuid, status varchar(30), finance_provisioning_status varchar(30),
    portal_provisioning_status varchar(30), requested_at timestamptz, completed_at timestamptz,
    failure_reason varchar(1000),
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
    CONSTRAINT ck_student_records_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD')),
    CONSTRAINT ck_student_records_outbox_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_student_records_outbox_dispatch
    ON integration_outbox (next_attempt_at, occurred_at) WHERE status = 'PENDING';

CREATE TABLE integration_inbox (
    event_id uuid PRIMARY KEY,
    event_type varchar(160) NOT NULL,
    source_service varchar(100) NOT NULL,
    payload jsonb NOT NULL,
    received_at timestamptz NOT NULL,
    processed_at timestamptz
);

CREATE INDEX idx_student_records_inbox_processed_at ON integration_inbox (processed_at);
