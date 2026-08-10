-- Author: Tinashe K

DROP TRIGGER trg_protect_academic_period_identity ON academic_periods;
DROP TRIGGER trg_protect_intake_identity ON intakes;
DROP FUNCTION protect_active_calendar_identity();

CREATE FUNCTION protect_active_academic_period_identity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status <> 'DRAFT'
       AND (NEW.academic_year_id <> OLD.academic_year_id
            OR NEW.academic_period_type_id <> OLD.academic_period_type_id
            OR NEW.code <> OLD.code) THEN
        RAISE EXCEPTION 'An open or closed academic period cannot change year, type, or code.' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION protect_active_intake_identity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status <> 'DRAFT'
       AND (NEW.academic_year_id <> OLD.academic_year_id OR NEW.code <> OLD.code) THEN
        RAISE EXCEPTION 'An open or closed intake cannot change academic year or code.' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_protect_academic_period_identity
BEFORE UPDATE ON academic_periods
FOR EACH ROW EXECUTE FUNCTION protect_active_academic_period_identity();

CREATE TRIGGER trg_protect_intake_identity
BEFORE UPDATE ON intakes
FOR EACH ROW EXECUTE FUNCTION protect_active_intake_identity();
