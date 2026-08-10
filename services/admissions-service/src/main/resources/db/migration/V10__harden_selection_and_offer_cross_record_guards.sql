-- Author: Tinashe K

CREATE OR REPLACE FUNCTION validate_selection_decision()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    round_status varchar(30);
    current_choice_status varchar(30);
    current_application_id uuid;
    existing_application_selection_count bigint;
BEGIN
    SELECT status INTO round_status FROM selection_rounds WHERE id = NEW.selection_round_id AND deleted_at IS NULL;
    IF round_status IS DISTINCT FROM 'OPEN' THEN
        RAISE EXCEPTION 'Selection decisions can only be recorded in an open selection round';
    END IF;

    SELECT choice_status, application_id INTO current_choice_status, current_application_id
    FROM application_programme_choices WHERE id = NEW.programme_choice_id AND deleted_at IS NULL;
    IF NEW.decision IN ('SHORTLIST', 'SELECT', 'WAITLIST')
       AND current_choice_status NOT IN ('ELIGIBLE', 'SHORTLISTED', 'WAITLISTED') THEN
        RAISE EXCEPTION 'Only eligible programme choices can be shortlisted, selected, or waitlisted';
    END IF;

    IF NEW.decision = 'SELECT' THEN
        SELECT count(*) INTO existing_application_selection_count
        FROM selection_decisions existing_decision
        JOIN application_programme_choices existing_choice
          ON existing_choice.id = existing_decision.programme_choice_id
        WHERE existing_choice.application_id = current_application_id
          AND existing_decision.decision = 'SELECT'
          AND existing_decision.deleted_at IS NULL
          AND existing_decision.id <> NEW.id;
        IF existing_application_selection_count > 0 THEN
            RAISE EXCEPTION 'An application can only have one selected programme choice';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION validate_offer_batch_source()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    round_cycle_id uuid;
BEGIN
    SELECT admission_cycle_id INTO round_cycle_id
    FROM selection_rounds
    WHERE id = NEW.selection_round_id AND deleted_at IS NULL;
    IF round_cycle_id IS NULL OR round_cycle_id <> NEW.admission_cycle_id THEN
        RAISE EXCEPTION 'Offer batch and selection round must belong to the same admission cycle';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_offer_batch_source_guard
BEFORE INSERT OR UPDATE OF admission_cycle_id, selection_round_id ON offer_batches
FOR EACH ROW EXECUTE FUNCTION validate_offer_batch_source();
