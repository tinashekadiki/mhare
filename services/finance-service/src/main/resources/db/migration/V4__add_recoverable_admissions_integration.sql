-- Author: Tinashe K

ALTER TABLE application_payment_references
    ADD COLUMN state_sequence bigint NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_application_payment_references_state_sequence CHECK (state_sequence >= 0);

ALTER TABLE application_payment_references_aud
    ADD COLUMN state_sequence bigint;

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
    CONSTRAINT ck_finance_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD')),
    CONSTRAINT ck_finance_outbox_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_finance_outbox_dispatch
    ON integration_outbox (next_attempt_at, occurred_at)
    WHERE status = 'PENDING';

CREATE TABLE integration_inbox (
    event_id uuid PRIMARY KEY,
    event_type varchar(160) NOT NULL,
    source_service varchar(100) NOT NULL,
    payload jsonb NOT NULL,
    received_at timestamptz NOT NULL,
    processed_at timestamptz
);

CREATE INDEX idx_finance_inbox_processed_at
    ON integration_inbox (processed_at);
