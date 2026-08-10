-- Author: Tinashe K
-- The shared trigger must inspect table-specific identity columns without
-- resolving a column that does not exist on the other target table.

CREATE OR REPLACE FUNCTION protect_intake_programme_target_draft()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    governed_intake_id uuid;
    governed_intake_status varchar(20);
BEGIN
    governed_intake_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.intake_id ELSE NEW.intake_id END;
    SELECT status INTO governed_intake_status
    FROM intakes
    WHERE id = governed_intake_id AND deleted_at IS NULL;

    IF governed_intake_status IS NULL THEN
        RAISE EXCEPTION 'The governed intake does not exist.' USING ERRCODE = '23503';
    END IF;
    IF governed_intake_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Programme eligibility can only be changed while the intake is in draft.' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.intake_id IS DISTINCT FROM OLD.intake_id THEN
        RAISE EXCEPTION 'An intake programme target cannot move to another intake.' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' AND TG_TABLE_NAME = 'intake_programme_type_targets'
       AND (to_jsonb(NEW) ->> 'programme_type_id')
           IS DISTINCT FROM (to_jsonb(OLD) ->> 'programme_type_id') THEN
        RAISE EXCEPTION 'An intake Programme Type target identity cannot change.' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' AND TG_TABLE_NAME = 'intake_programme_targets'
       AND (to_jsonb(NEW) ->> 'programme_id')
           IS DISTINCT FROM (to_jsonb(OLD) ->> 'programme_id') THEN
        RAISE EXCEPTION 'An intake Programme target identity cannot change.' USING ERRCODE = '23514';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;
