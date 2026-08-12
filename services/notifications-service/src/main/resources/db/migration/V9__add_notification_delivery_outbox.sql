-- Author: Tinashe K

CREATE TABLE notification_delivery_outbox (
    id uuid PRIMARY KEY,
    event_type varchar(160) NOT NULL,
    routing_key varchar(160) NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamptz NOT NULL,
    status varchar(20) NOT NULL,
    attempt_count integer NOT NULL,
    next_attempt_at timestamptz NOT NULL,
    published_at timestamptz,
    last_error varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL
);
CREATE INDEX idx_notification_delivery_outbox_dispatch ON notification_delivery_outbox(status,next_attempt_at,occurred_at);
GRANT SELECT,INSERT,UPDATE,DELETE ON TABLE notification_delivery_outbox TO emhare_service;
