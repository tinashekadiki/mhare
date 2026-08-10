-- Author: Tinashe K

CREATE TABLE integration_outbox (
    id uuid PRIMARY KEY,
    event_type varchar(160) NOT NULL,
    routing_key varchar(160) NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamptz NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'PENDING',
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    published_at timestamptz,
    last_error varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT integration_outbox_status_check
        CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD')),
    CONSTRAINT integration_outbox_attempt_count_check
        CHECK (attempt_count >= 0),
    CONSTRAINT integration_outbox_publication_check
        CHECK ((status = 'PUBLISHED' AND published_at IS NOT NULL)
            OR (status <> 'PUBLISHED' AND published_at IS NULL))
);

CREATE INDEX integration_outbox_dispatch_idx
    ON integration_outbox (next_attempt_at, occurred_at, id)
    WHERE status = 'PENDING';

CREATE INDEX integration_outbox_event_type_idx
    ON integration_outbox (event_type, occurred_at DESC);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE integration_outbox TO emhare_service;
