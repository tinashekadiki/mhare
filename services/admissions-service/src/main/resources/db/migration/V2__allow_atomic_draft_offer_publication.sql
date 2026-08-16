-- Author: Tinashe K
-- Publish and send is one governed command that records approval and dispatch together.
CREATE OR REPLACE FUNCTION public.validate_offer_transition() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.status = NEW.status THEN
        RETURN NEW;
    END IF;
    IF NOT (
        (OLD.status = 'DRAFT' AND NEW.status IN ('APPROVED', 'SENT', 'WITHDRAWN'))
        OR (OLD.status = 'APPROVED' AND NEW.status IN ('SENT', 'WITHDRAWN'))
        OR (OLD.status = 'SENT' AND NEW.status IN ('ACCEPTED', 'DECLINED', 'EXPIRED', 'WITHDRAWN'))
        OR (OLD.status = 'ACCEPTED' AND NEW.status = 'CONVERTED')
    ) THEN
        RAISE EXCEPTION 'Invalid offer transition from % to %', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$;
