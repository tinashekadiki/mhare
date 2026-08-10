-- Author: Tinashe K

ALTER TABLE academic_years
    ADD COLUMN change_reason varchar(1000) NOT NULL DEFAULT 'Initial record creation.';
ALTER TABLE academic_period_types
    ADD COLUMN change_reason varchar(1000) NOT NULL DEFAULT 'Initial record creation.';
ALTER TABLE academic_periods
    ADD COLUMN change_reason varchar(1000) NOT NULL DEFAULT 'Initial record creation.';
ALTER TABLE intakes
    ADD COLUMN change_reason varchar(1000) NOT NULL DEFAULT 'Initial record creation.';

ALTER TABLE academic_years
    ADD CONSTRAINT ck_academic_year_change_reason CHECK (length(trim(change_reason)) >= 10);
ALTER TABLE academic_period_types
    ADD CONSTRAINT ck_academic_period_type_change_reason CHECK (length(trim(change_reason)) >= 10);
ALTER TABLE academic_periods
    ADD CONSTRAINT ck_academic_period_change_reason CHECK (length(trim(change_reason)) >= 10);
ALTER TABLE intakes
    ADD CONSTRAINT ck_intake_change_reason CHECK (length(trim(change_reason)) >= 10);

ALTER TABLE academic_years_aud ADD COLUMN change_reason varchar(1000);
ALTER TABLE academic_period_types_aud ADD COLUMN change_reason varchar(1000);
ALTER TABLE academic_periods_aud ADD COLUMN change_reason varchar(1000);
ALTER TABLE intakes_aud ADD COLUMN change_reason varchar(1000);

CREATE OR REPLACE FUNCTION protect_active_calendar_identity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_TABLE_NAME = 'academic_periods'
       AND OLD.status <> 'DRAFT'
       AND (NEW.academic_year_id <> OLD.academic_year_id
            OR NEW.academic_period_type_id <> OLD.academic_period_type_id
            OR NEW.code <> OLD.code) THEN
        RAISE EXCEPTION 'An open or closed academic period cannot change year, type, or code.' USING ERRCODE = '23514';
    END IF;
    IF TG_TABLE_NAME = 'intakes'
       AND OLD.status <> 'DRAFT'
       AND (NEW.academic_year_id <> OLD.academic_year_id OR NEW.code <> OLD.code) THEN
        RAISE EXCEPTION 'An open or closed intake cannot change academic year or code.' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_protect_academic_period_identity
BEFORE UPDATE ON academic_periods
FOR EACH ROW EXECUTE FUNCTION protect_active_calendar_identity();

CREATE TRIGGER trg_protect_intake_identity
BEFORE UPDATE ON intakes
FOR EACH ROW EXECUTE FUNCTION protect_active_calendar_identity();

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO emhare_service;
