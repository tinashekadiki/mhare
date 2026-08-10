-- Author: Tinashe K

ALTER TABLE notification_event_inbox
    DROP CONSTRAINT ck_notification_inbox_status;
ALTER TABLE notification_event_inbox
    ADD COLUMN raw_payload text,
    ADD COLUMN attempt_count integer NOT NULL DEFAULT 0,
    ADD COLUMN max_attempts integer NOT NULL DEFAULT 10,
    ADD COLUMN next_attempt_at timestamptz,
    ADD COLUMN last_attempt_at timestamptz,
    ADD COLUMN created_by_user_id uuid,
    ADD COLUMN modified_by_user_id uuid,
    ADD COLUMN deleted_at timestamptz,
    ADD COLUMN deleted_by_user_id uuid;
UPDATE notification_event_inbox
SET raw_payload = payload::text,
    next_attempt_at = COALESCE(processed_at, received_at);
ALTER TABLE notification_event_inbox
    ALTER COLUMN raw_payload SET NOT NULL,
    ALTER COLUMN payload DROP NOT NULL;
ALTER TABLE notification_event_inbox
    ADD CONSTRAINT ck_notification_inbox_status CHECK (
        status IN ('RECEIVED','PROCESSING','PROCESSED','RETRY_SCHEDULED','DEAD')),
    ADD CONSTRAINT ck_notification_inbox_attempts CHECK (
        attempt_count >= 0 AND max_attempts > 0 AND attempt_count <= max_attempts),
    ADD CONSTRAINT ck_notification_inbox_delete_pair CHECK (
        (deleted_at IS NULL AND deleted_by_user_id IS NULL)
        OR (deleted_at IS NOT NULL AND deleted_by_user_id IS NOT NULL));
CREATE INDEX idx_notification_inbox_processing
    ON notification_event_inbox(next_attempt_at, received_at, id)
    WHERE status IN ('RECEIVED','RETRY_SCHEDULED') AND deleted_at IS NULL;

ALTER TABLE notification_requests
    ADD COLUMN provider_delivery_status varchar(30),
    ADD COLUMN provider_status_at timestamptz,
    ADD COLUMN provider_status_detail varchar(1000);
ALTER TABLE notification_requests
    ADD CONSTRAINT ck_notification_provider_delivery_status CHECK (
        provider_delivery_status IS NULL
        OR provider_delivery_status IN ('ACCEPTED','DELIVERED','BOUNCED','FAILED'));
ALTER TABLE notification_requests_aud
    ADD COLUMN provider_delivery_status varchar(30),
    ADD COLUMN provider_status_at timestamptz,
    ADD COLUMN provider_status_detail varchar(1000);
CREATE UNIQUE INDEX uk_notification_provider_message
    ON notification_requests(provider_code, provider_message_id)
    WHERE provider_code IS NOT NULL AND provider_message_id IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE notification_provider_callbacks (
    id uuid PRIMARY KEY,
    provider_code varchar(80) NOT NULL,
    provider_event_id varchar(240) NOT NULL,
    provider_message_id varchar(240) NOT NULL,
    delivery_status varchar(30) NOT NULL,
    occurred_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL,
    notification_request_id uuid REFERENCES notification_requests(id),
    error_code varchar(100),
    error_message varchar(1000),
    callback_payload jsonb NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_notification_provider_callback UNIQUE(provider_code, provider_event_id),
    CONSTRAINT ck_notification_callback_status CHECK (
        delivery_status IN ('DELIVERED','BOUNCED','FAILED')),
    CONSTRAINT ck_notification_callback_delete_pair CHECK (
        (deleted_at IS NULL AND deleted_by_user_id IS NULL)
        OR (deleted_at IS NOT NULL AND deleted_by_user_id IS NOT NULL))
);
CREATE INDEX idx_notification_callback_message
    ON notification_provider_callbacks(provider_code, provider_message_id, received_at DESC);

CREATE TABLE in_app_notifications (
    id uuid PRIMARY KEY,
    notification_request_id uuid NOT NULL UNIQUE REFERENCES notification_requests(id),
    recipient_user_id uuid,
    recipient_key varchar(160) NOT NULL,
    title varchar(500),
    body text NOT NULL,
    delivered_at timestamptz NOT NULL,
    read_at timestamptz,
    read_by_user_id uuid,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_in_app_read_evidence CHECK (
        (read_at IS NULL AND read_by_user_id IS NULL)
        OR (read_at IS NOT NULL AND read_by_user_id IS NOT NULL)),
    CONSTRAINT ck_in_app_delete_pair CHECK (
        (deleted_at IS NULL AND deleted_by_user_id IS NULL)
        OR (deleted_at IS NOT NULL AND deleted_by_user_id IS NOT NULL))
);
CREATE INDEX idx_in_app_notification_recipient
    ON in_app_notifications(recipient_user_id, delivered_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE notification_event_inbox_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo(rev),
    revtype smallint,
    source_service varchar(80),
    source_event_id uuid,
    event_type varchar(120),
    raw_payload text,
    received_at timestamptz,
    processed_at timestamptz,
    status varchar(20),
    processing_error varchar(1000),
    attempt_count integer,
    max_attempts integer,
    next_attempt_at timestamptz,
    last_attempt_at timestamptz,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY(id, rev)
);
CREATE TABLE notification_provider_callbacks_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo(rev),
    revtype smallint,
    provider_code varchar(80),
    provider_event_id varchar(240),
    provider_message_id varchar(240),
    delivery_status varchar(30),
    occurred_at timestamptz,
    received_at timestamptz,
    notification_request_id uuid,
    error_code varchar(100),
    error_message varchar(1000),
    callback_payload jsonb,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY(id, rev)
);
CREATE TABLE in_app_notifications_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo(rev),
    revtype smallint,
    notification_request_id uuid,
    recipient_user_id uuid,
    recipient_key varchar(160),
    title varchar(500),
    body text,
    delivered_at timestamptz,
    read_at timestamptz,
    read_by_user_id uuid,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY(id, rev)
);

CREATE OR REPLACE FUNCTION protect_notification_callback_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Notification provider callback evidence is append-only and immutable'; END $$;
CREATE TRIGGER trg_protect_notification_callback
    BEFORE UPDATE OR DELETE ON notification_provider_callbacks
    FOR EACH ROW EXECUTE FUNCTION protect_notification_callback_evidence();

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO emhare_service;
