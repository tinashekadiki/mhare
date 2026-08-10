-- Author: Tinashe K

ALTER TABLE programmes
    ADD COLUMN change_reason varchar(1000) NOT NULL DEFAULT 'Initial record creation.';
ALTER TABLE programmes
    ADD CONSTRAINT ck_programmes_change_reason CHECK (length(trim(change_reason)) >= 10);

ALTER TABLE programmes_aud ADD COLUMN change_reason varchar(1000);

CREATE FUNCTION protect_active_programme_identity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status <> 'DRAFT'
       AND (NEW.owning_academic_unit_id <> OLD.owning_academic_unit_id OR NEW.code <> OLD.code) THEN
        RAISE EXCEPTION 'A programme that has left draft cannot change owning academic unit or code.' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_protect_programme_identity
BEFORE UPDATE ON programmes
FOR EACH ROW EXECUTE FUNCTION protect_active_programme_identity();
