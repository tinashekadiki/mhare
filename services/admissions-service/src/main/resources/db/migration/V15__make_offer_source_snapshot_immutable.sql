-- Author: Tinashe K

DROP TRIGGER trg_offer_source_guard ON offers;

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
    IF TG_OP = 'UPDATE' THEN
        IF OLD.application_id IS DISTINCT FROM NEW.application_id
           OR OLD.programme_choice_id IS DISTINCT FROM NEW.programme_choice_id
           OR OLD.offer_batch_id IS DISTINCT FROM NEW.offer_batch_id
           OR OLD.programme_id IS DISTINCT FROM NEW.programme_id
           OR OLD.programme_version_id IS DISTINCT FROM NEW.programme_version_id
           OR OLD.programme_code IS DISTINCT FROM NEW.programme_code
           OR OLD.programme_name IS DISTINCT FROM NEW.programme_name
           OR OLD.intake_id IS DISTINCT FROM NEW.intake_id
           OR OLD.offer_number IS DISTINCT FROM NEW.offer_number THEN
            RAISE EXCEPTION 'An offer source snapshot is immutable after creation';
        END IF;

        IF (OLD.offer_type IS DISTINCT FROM NEW.offer_type
            OR OLD.conditions_text IS DISTINCT FROM NEW.conditions_text
            OR OLD.acceptance_deadline IS DISTINCT FROM NEW.acceptance_deadline
            OR OLD.registration_date IS DISTINCT FROM NEW.registration_date
            OR OLD.orientation_date IS DISTINCT FROM NEW.orientation_date
            OR OLD.commencement_date IS DISTINCT FROM NEW.commencement_date
            OR OLD.generated_document_id IS DISTINCT FROM NEW.generated_document_id)
           AND (OLD.status <> 'DRAFT' OR NEW.status <> 'DRAFT') THEN
            RAISE EXCEPTION 'Approved offer terms and document references are immutable';
        END IF;
        RETURN NEW;
    END IF;

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

CREATE TRIGGER trg_offer_source_guard
BEFORE INSERT OR UPDATE ON offers
FOR EACH ROW EXECUTE FUNCTION validate_offer_source();
