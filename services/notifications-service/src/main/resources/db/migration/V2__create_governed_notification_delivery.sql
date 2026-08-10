-- Author: Tinashe K

CREATE TABLE notification_templates (
    id uuid PRIMARY KEY,
    code varchar(80) NOT NULL,
    template_version integer NOT NULL,
    name varchar(180) NOT NULL,
    event_type varchar(120) NOT NULL,
    channel varchar(20) NOT NULL,
    category varchar(30) NOT NULL,
    locale varchar(20) NOT NULL DEFAULT 'en-ZW',
    subject_template varchar(500),
    body_template text NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'DRAFT',
    prepared_by_user_id uuid NOT NULL,
    approved_by_user_id uuid,
    approved_at timestamptz,
    approval_reason varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_notification_template_version UNIQUE (code, template_version),
    CONSTRAINT ck_notification_template_version CHECK (template_version > 0),
    CONSTRAINT ck_notification_template_channel CHECK (channel IN ('EMAIL','SMS','IN_APP')),
    CONSTRAINT ck_notification_template_category CHECK (category IN ('TRANSACTIONAL','WORKFLOW','SECURITY','MARKETING')),
    CONSTRAINT ck_notification_template_status CHECK (status IN ('DRAFT','ACTIVE','RETIRED')),
    CONSTRAINT ck_notification_template_approval CHECK (
        (status='DRAFT' AND approved_by_user_id IS NULL AND approved_at IS NULL AND approval_reason IS NULL)
        OR (status<>'DRAFT' AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL
            AND length(trim(approval_reason)) > 0 AND approved_by_user_id <> prepared_by_user_id)
    )
);
CREATE UNIQUE INDEX uk_active_notification_template ON notification_templates(lower(code),channel,locale)
    WHERE status='ACTIVE' AND deleted_at IS NULL;
CREATE INDEX idx_notification_template_event ON notification_templates(event_type,channel,status) WHERE deleted_at IS NULL;

CREATE TABLE notification_consents (
    id uuid PRIMARY KEY,
    recipient_user_id uuid,
    recipient_key varchar(160) NOT NULL,
    channel varchar(20) NOT NULL,
    category varchar(30) NOT NULL,
    status varchar(20) NOT NULL,
    source varchar(80) NOT NULL,
    evidence_reference varchar(300),
    effective_from timestamptz NOT NULL,
    effective_until timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_notification_consent_channel CHECK (channel IN ('EMAIL','SMS','IN_APP')),
    CONSTRAINT ck_notification_consent_category CHECK (category IN ('TRANSACTIONAL','WORKFLOW','SECURITY','MARKETING')),
    CONSTRAINT ck_notification_consent_status CHECK (status IN ('OPTED_IN','OPTED_OUT','NOT_REQUIRED')),
    CONSTRAINT ck_notification_consent_window CHECK (effective_until IS NULL OR effective_until > effective_from)
);
CREATE UNIQUE INDEX uk_current_notification_consent ON notification_consents(lower(recipient_key),channel,category)
    WHERE effective_until IS NULL AND deleted_at IS NULL;

CREATE TABLE notification_requests (
    id uuid PRIMARY KEY,
    request_number varchar(60) NOT NULL UNIQUE,
    idempotency_key varchar(160) NOT NULL UNIQUE,
    source_service varchar(80) NOT NULL,
    source_event_id uuid,
    event_type varchar(120) NOT NULL,
    template_id uuid NOT NULL REFERENCES notification_templates(id),
    template_code varchar(80) NOT NULL,
    template_version integer NOT NULL,
    channel varchar(20) NOT NULL,
    category varchar(30) NOT NULL,
    recipient_user_id uuid,
    recipient_key varchar(160) NOT NULL,
    recipient_address varchar(320) NOT NULL,
    subject varchar(500),
    body text NOT NULL,
    priority varchar(20) NOT NULL DEFAULT 'NORMAL',
    status varchar(20) NOT NULL DEFAULT 'QUEUED',
    consent_decision varchar(30) NOT NULL,
    scheduled_at timestamptz NOT NULL,
    next_attempt_at timestamptz,
    attempt_count integer NOT NULL DEFAULT 0,
    max_attempts integer NOT NULL DEFAULT 5,
    provider_code varchar(80),
    provider_message_id varchar(240),
    sent_at timestamptz,
    failed_at timestamptz,
    last_error_code varchar(100),
    last_error_message varchar(1000),
    cancelled_by_user_id uuid,
    cancelled_at timestamptz,
    cancellation_reason varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_notification_request_channel CHECK (channel IN ('EMAIL','SMS','IN_APP')),
    CONSTRAINT ck_notification_request_category CHECK (category IN ('TRANSACTIONAL','WORKFLOW','SECURITY','MARKETING')),
    CONSTRAINT ck_notification_request_priority CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
    CONSTRAINT ck_notification_request_status CHECK (status IN ('QUEUED','PROCESSING','SENT','RETRY_SCHEDULED','FAILED','SUPPRESSED','CANCELLED')),
    CONSTRAINT ck_notification_request_attempts CHECK (attempt_count >= 0 AND max_attempts > 0 AND attempt_count <= max_attempts),
    CONSTRAINT ck_notification_request_sent CHECK ((status='SENT' AND sent_at IS NOT NULL AND provider_message_id IS NOT NULL) OR status<>'SENT'),
    CONSTRAINT ck_notification_request_failure CHECK ((status='FAILED' AND failed_at IS NOT NULL AND last_error_message IS NOT NULL) OR status<>'FAILED'),
    CONSTRAINT ck_notification_request_cancel CHECK ((status='CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by_user_id IS NOT NULL AND length(trim(cancellation_reason)) > 0) OR status<>'CANCELLED')
);
CREATE INDEX idx_notification_dispatch_queue ON notification_requests(priority,next_attempt_at,scheduled_at,id)
    WHERE status IN ('QUEUED','RETRY_SCHEDULED') AND deleted_at IS NULL;
CREATE INDEX idx_notification_recipient_history ON notification_requests(lower(recipient_key),created_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE notification_delivery_attempts (
    id uuid PRIMARY KEY,
    notification_request_id uuid NOT NULL REFERENCES notification_requests(id),
    attempt_number integer NOT NULL,
    provider_code varchar(80) NOT NULL,
    started_at timestamptz NOT NULL,
    completed_at timestamptz NOT NULL,
    outcome varchar(20) NOT NULL,
    provider_message_id varchar(240),
    error_code varchar(100),
    error_message varchar(1000),
    response_metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_notification_attempt UNIQUE(notification_request_id,attempt_number),
    CONSTRAINT ck_notification_attempt_number CHECK (attempt_number > 0),
    CONSTRAINT ck_notification_attempt_outcome CHECK (outcome IN ('SENT','RETRYABLE_FAILURE','PERMANENT_FAILURE')),
    CONSTRAINT ck_notification_attempt_result CHECK (
        (outcome='SENT' AND provider_message_id IS NOT NULL AND error_message IS NULL)
        OR (outcome<>'SENT' AND error_message IS NOT NULL)
    )
);

CREATE TABLE notification_event_inbox (
    id uuid PRIMARY KEY,
    source_service varchar(80) NOT NULL,
    source_event_id uuid NOT NULL,
    event_type varchar(120) NOT NULL,
    payload jsonb NOT NULL,
    received_at timestamptz NOT NULL,
    processed_at timestamptz,
    status varchar(20) NOT NULL DEFAULT 'RECEIVED',
    processing_error varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_notification_inbox_event UNIQUE(source_service,source_event_id),
    CONSTRAINT ck_notification_inbox_status CHECK (status IN ('RECEIVED','PROCESSED','FAILED'))
);

CREATE TABLE notification_templates_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,code varchar(80),template_version integer,name varchar(180),event_type varchar(120),channel varchar(20),category varchar(30),locale varchar(20),subject_template varchar(500),body_template text,status varchar(20),prepared_by_user_id uuid,approved_by_user_id uuid,approved_at timestamptz,approval_reason varchar(1000),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE notification_consents_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,recipient_user_id uuid,recipient_key varchar(160),channel varchar(20),category varchar(30),status varchar(20),source varchar(80),evidence_reference varchar(300),effective_from timestamptz,effective_until timestamptz,created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE notification_requests_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,request_number varchar(60),idempotency_key varchar(160),source_service varchar(80),source_event_id uuid,event_type varchar(120),template_id uuid,template_code varchar(80),template_version integer,channel varchar(20),category varchar(30),recipient_user_id uuid,recipient_key varchar(160),recipient_address varchar(320),subject varchar(500),body text,priority varchar(20),status varchar(20),consent_decision varchar(30),scheduled_at timestamptz,next_attempt_at timestamptz,attempt_count integer,max_attempts integer,provider_code varchar(80),provider_message_id varchar(240),sent_at timestamptz,failed_at timestamptz,last_error_code varchar(100),last_error_message varchar(1000),cancelled_by_user_id uuid,cancelled_at timestamptz,cancellation_reason varchar(1000),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE notification_delivery_attempts_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,notification_request_id uuid,attempt_number integer,provider_code varchar(80),started_at timestamptz,completed_at timestamptz,outcome varchar(20),provider_message_id varchar(240),error_code varchar(100),error_message varchar(1000),response_metadata jsonb,created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));

CREATE OR REPLACE FUNCTION protect_notification_attempt_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Notification delivery attempt evidence is append-only and immutable'; END $$;
CREATE TRIGGER trg_protect_notification_attempt BEFORE UPDATE OR DELETE ON notification_delivery_attempts
    FOR EACH ROW EXECUTE FUNCTION protect_notification_attempt_evidence();

GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO emhare_service;
