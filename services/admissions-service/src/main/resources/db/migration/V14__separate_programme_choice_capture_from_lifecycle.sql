-- Author: Tinashe K

DROP TRIGGER trg_application_programme_choice_governance ON application_programme_choices;

CREATE OR REPLACE FUNCTION enforce_application_programme_choice_governance()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    application_status varchar(30);
    maximum_choices integer;
    capture_snapshot_changed boolean;
BEGIN
    SELECT application.status, cycle.maximum_programme_choices
      INTO application_status, maximum_choices
      FROM applications application
      JOIN admission_cycles cycle ON cycle.id = application.admission_cycle_id
     WHERE application.id = NEW.application_id
       AND application.deleted_at IS NULL
       AND cycle.deleted_at IS NULL;

    IF application_status IS NULL THEN
        RAISE EXCEPTION 'Application or admission cycle is unavailable for programme choice governance.';
    END IF;

    capture_snapshot_changed := TG_OP = 'INSERT' OR (
        OLD.application_id IS DISTINCT FROM NEW.application_id
        OR OLD.programme_id IS DISTINCT FROM NEW.programme_id
        OR OLD.programme_version_id IS DISTINCT FROM NEW.programme_version_id
        OR OLD.programme_code IS DISTINCT FROM NEW.programme_code
        OR OLD.programme_name IS DISTINCT FROM NEW.programme_name
        OR OLD.award_name IS DISTINCT FROM NEW.award_name
        OR OLD.owning_academic_unit_id IS DISTINCT FROM NEW.owning_academic_unit_id
        OR OLD.owning_academic_unit_name IS DISTINCT FROM NEW.owning_academic_unit_name
        OR OLD.programme_version_code IS DISTINCT FROM NEW.programme_version_code
        OR OLD.catalogue_snapshot_status IS DISTINCT FROM NEW.catalogue_snapshot_status
        OR OLD.choice_rank IS DISTINCT FROM NEW.choice_rank
    );

    IF capture_snapshot_changed AND application_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Programme choice capture snapshots can only change while the application is in DRAFT status.';
    END IF;
    IF NEW.choice_rank < 1 OR NEW.choice_rank > maximum_choices THEN
        RAISE EXCEPTION 'Programme choice rank % exceeds the configured cycle maximum of %.', NEW.choice_rank, maximum_choices;
    END IF;

    IF TG_OP = 'UPDATE'
       AND OLD.choice_status IS DISTINCT FROM NEW.choice_status
       AND NOT (
           (OLD.choice_status = 'PENDING' AND NEW.choice_status IN ('ELIGIBLE', 'INELIGIBLE', 'REQUIRES_REVIEW'))
           OR (OLD.choice_status = 'REQUIRES_REVIEW' AND NEW.choice_status IN ('ELIGIBLE', 'INELIGIBLE'))
           OR (OLD.choice_status IN ('ELIGIBLE', 'INELIGIBLE') AND NEW.choice_status IN ('ELIGIBLE', 'INELIGIBLE', 'REQUIRES_REVIEW'))
           OR (OLD.choice_status = 'ELIGIBLE' AND NEW.choice_status IN ('SHORTLISTED', 'WAITLISTED', 'SELECTED', 'REJECTED'))
           OR (OLD.choice_status = 'SHORTLISTED' AND NEW.choice_status IN ('WAITLISTED', 'SELECTED', 'REJECTED'))
           OR (OLD.choice_status = 'WAITLISTED' AND NEW.choice_status IN ('SELECTED', 'REJECTED'))
           OR (OLD.choice_status = 'SELECTED' AND NEW.choice_status = 'OFFERED')
           OR (OLD.choice_status = 'OFFERED' AND NEW.choice_status IN ('SELECTED', 'CONVERTED', 'REJECTED'))
       ) THEN
        RAISE EXCEPTION 'Invalid programme choice transition from % to %.', OLD.choice_status, NEW.choice_status;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_application_programme_choice_governance
BEFORE INSERT OR UPDATE ON application_programme_choices
FOR EACH ROW
EXECUTE FUNCTION enforce_application_programme_choice_governance();
