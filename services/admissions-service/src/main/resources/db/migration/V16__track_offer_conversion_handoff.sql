-- Author: Tinashe K

ALTER TABLE offers
    ADD COLUMN conversion_event_id uuid,
    ADD COLUMN conversion_requested_at timestamptz,
    ADD CONSTRAINT uk_offers_conversion_event UNIQUE (conversion_event_id),
    ADD CONSTRAINT ck_offers_conversion_handoff CHECK (
        (conversion_event_id IS NULL AND conversion_requested_at IS NULL)
        OR (conversion_event_id IS NOT NULL AND conversion_requested_at IS NOT NULL AND status IN ('ACCEPTED', 'CONVERTED'))
    );

ALTER TABLE offers_aud
    ADD COLUMN conversion_event_id uuid,
    ADD COLUMN conversion_requested_at timestamptz;
