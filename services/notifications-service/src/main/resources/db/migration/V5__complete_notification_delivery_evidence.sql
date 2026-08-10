-- Author: Tinashe K

ALTER TABLE notification_requests
    ADD COLUMN manual_retry_by_user_id uuid,
    ADD COLUMN manual_retry_at timestamptz,
    ADD COLUMN manual_retry_reason varchar(1000);
ALTER TABLE notification_requests
    ADD CONSTRAINT ck_notification_request_retry_evidence CHECK (
        (manual_retry_by_user_id IS NULL AND manual_retry_at IS NULL AND manual_retry_reason IS NULL)
        OR (manual_retry_by_user_id IS NOT NULL AND manual_retry_at IS NOT NULL
            AND length(trim(manual_retry_reason)) >= 10));
ALTER TABLE notification_requests_aud
    ADD COLUMN manual_retry_by_user_id uuid,
    ADD COLUMN manual_retry_at timestamptz,
    ADD COLUMN manual_retry_reason varchar(1000);

ALTER TABLE notification_event_inbox
    ADD COLUMN manual_retry_by_user_id uuid,
    ADD COLUMN manual_retry_at timestamptz,
    ADD COLUMN manual_retry_reason varchar(1000);
ALTER TABLE notification_event_inbox
    ADD CONSTRAINT ck_notification_inbox_retry_evidence CHECK (
        (manual_retry_by_user_id IS NULL AND manual_retry_at IS NULL AND manual_retry_reason IS NULL)
        OR (manual_retry_by_user_id IS NOT NULL AND manual_retry_at IS NOT NULL
            AND length(trim(manual_retry_reason)) >= 10));
ALTER TABLE notification_event_inbox_aud
    ADD COLUMN manual_retry_by_user_id uuid,
    ADD COLUMN manual_retry_at timestamptz,
    ADD COLUMN manual_retry_reason varchar(1000);

ALTER TABLE notification_provider_callbacks
    ADD CONSTRAINT ck_notification_callback_payload_object CHECK (
        jsonb_typeof(callback_payload) = 'object');

ALTER TABLE notification_requests
    ADD CONSTRAINT ck_notification_provider_status_evidence CHECK (
        (provider_delivery_status IS NULL AND provider_status_at IS NULL)
        OR (provider_delivery_status IS NOT NULL AND provider_status_at IS NOT NULL));

CREATE INDEX idx_notification_requests_provider_status
    ON notification_requests(provider_delivery_status, provider_status_at DESC)
    WHERE provider_delivery_status IS NOT NULL AND deleted_at IS NULL;

