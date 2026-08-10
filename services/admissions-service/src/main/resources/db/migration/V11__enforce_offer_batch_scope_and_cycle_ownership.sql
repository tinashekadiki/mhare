-- Author: Tinashe K

CREATE OR REPLACE FUNCTION validate_selection_decision()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    round_status varchar(30);
    round_cycle_id uuid;
    current_choice_status varchar(30);
    current_application_id uuid;
    current_application_cycle_id uuid;
    existing_application_selection_count bigint;
BEGIN
    SELECT status, admission_cycle_id INTO round_status, round_cycle_id
    FROM selection_rounds WHERE id = NEW.selection_round_id AND deleted_at IS NULL;
    IF round_status IS DISTINCT FROM 'OPEN' THEN
        RAISE EXCEPTION 'Selection decisions can only be recorded in an open selection round';
    END IF;

    SELECT choice.choice_status, choice.application_id, application_record.admission_cycle_id
    INTO current_choice_status, current_application_id, current_application_cycle_id
    FROM application_programme_choices choice
    JOIN applications application_record ON application_record.id = choice.application_id
    WHERE choice.id = NEW.programme_choice_id AND choice.deleted_at IS NULL;
    IF current_application_cycle_id IS NULL OR current_application_cycle_id <> round_cycle_id THEN
        RAISE EXCEPTION 'Programme choice is outside the selection round admission cycle';
    END IF;
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

CREATE OR REPLACE FUNCTION validate_offer_source()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    source_is_approved boolean;
    source_application_id uuid;
    source_programme_id uuid;
    source_programme_version_id uuid;
    source_owning_academic_unit_id uuid;
    source_intake_id uuid;
    batch_scope_type varchar(30);
    batch_scope_id uuid;
BEGIN
    SELECT EXISTS (
        SELECT 1
        FROM selection_decisions decision
        JOIN selection_rounds round_record ON round_record.id = decision.selection_round_id
        JOIN offer_batches batch ON batch.selection_round_id = round_record.id
        WHERE decision.programme_choice_id = NEW.programme_choice_id
          AND decision.decision = 'SELECT'
          AND decision.deleted_at IS NULL
          AND round_record.status = 'APPROVED'
          AND round_record.deleted_at IS NULL
          AND batch.id = NEW.offer_batch_id
          AND batch.status = 'APPROVED'
          AND batch.deleted_at IS NULL
    ) INTO source_is_approved;
    IF NOT source_is_approved THEN
        RAISE EXCEPTION 'Offers require an approved batch and selected decision in its approved selection round';
    END IF;

    SELECT choice.application_id, choice.programme_id, choice.programme_version_id,
           choice.owning_academic_unit_id, cycle.intake_id
    INTO source_application_id, source_programme_id, source_programme_version_id,
         source_owning_academic_unit_id, source_intake_id
    FROM application_programme_choices choice
    JOIN applications application_record ON application_record.id = choice.application_id
    JOIN admission_cycles cycle ON cycle.id = application_record.admission_cycle_id
    WHERE choice.id = NEW.programme_choice_id
      AND choice.choice_status = 'SELECTED'
      AND application_record.status = 'SELECTED';

    IF source_application_id IS NULL
       OR source_application_id <> NEW.application_id
       OR source_programme_id <> NEW.programme_id
       OR source_programme_version_id <> NEW.programme_version_id
       OR source_intake_id <> NEW.intake_id THEN
        RAISE EXCEPTION 'Offer source does not match the selected application and programme snapshot';
    END IF;

    SELECT scope_type, scope_id INTO batch_scope_type, batch_scope_id
    FROM offer_batches WHERE id = NEW.offer_batch_id;
    IF (batch_scope_type = 'PROGRAMME' AND batch_scope_id <> source_programme_id)
       OR (batch_scope_type = 'ACADEMIC_UNIT' AND batch_scope_id <> source_owning_academic_unit_id) THEN
        RAISE EXCEPTION 'Selected programme choice is outside the offer batch scope';
    END IF;
    RETURN NEW;
END;
$$;
