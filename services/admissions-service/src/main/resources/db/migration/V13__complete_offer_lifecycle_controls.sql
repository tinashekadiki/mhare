-- Author: Tinashe K

ALTER TABLE offers
    ADD COLUMN expired_at timestamptz,
    ADD COLUMN expiry_reason varchar(1000);

ALTER TABLE offers
    ADD CONSTRAINT ck_offers_expiry CHECK (
        (status = 'EXPIRED' AND expired_at IS NOT NULL AND length(trim(coalesce(expiry_reason, ''))) > 0)
        OR (status <> 'EXPIRED' AND expired_at IS NULL AND expiry_reason IS NULL)
    );

ALTER TABLE offers_aud
    ADD COLUMN expired_at timestamptz,
    ADD COLUMN expiry_reason varchar(1000);

CREATE OR REPLACE FUNCTION validate_offer_batch_transition()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status = NEW.status THEN
        RETURN NEW;
    END IF;
    IF NOT (
        (OLD.status = 'DRAFT' AND NEW.status = 'APPROVED')
        OR (OLD.status = 'APPROVED' AND NEW.status = 'DISPATCHED')
        OR (OLD.status = 'DISPATCHED' AND NEW.status = 'CLOSED')
    ) THEN
        RAISE EXCEPTION 'Invalid offer batch transition from % to %', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION validate_offer_condition_transition()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status = NEW.status THEN
        RETURN NEW;
    END IF;
    IF OLD.status <> 'PENDING' OR NEW.status NOT IN ('SATISFIED', 'WAIVED') THEN
        RAISE EXCEPTION 'Invalid offer condition transition from % to %', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_offer_condition_transition
BEFORE UPDATE OF status ON offer_conditions
FOR EACH ROW EXECUTE FUNCTION validate_offer_condition_transition();
