-- Author: Tinashe K

CREATE FUNCTION enforce_student_conversion_request_integrity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    student_offer_id uuid;
    enrolment_student_id uuid;
    enrolment_offer_id uuid;
BEGIN
    SELECT source_offer_id
      INTO student_offer_id
      FROM students
     WHERE id = NEW.student_id;

    SELECT student_id, source_offer_id
      INTO enrolment_student_id, enrolment_offer_id
      FROM student_programme_enrolments
     WHERE id = NEW.programme_enrolment_id;

    IF student_offer_id IS NULL OR enrolment_student_id IS NULL THEN
        RAISE EXCEPTION 'Student conversion references missing conversion records';
    END IF;

    IF student_offer_id <> NEW.source_offer_id
            OR enrolment_offer_id <> NEW.source_offer_id
            OR enrolment_student_id <> NEW.student_id THEN
        RAISE EXCEPTION 'Student conversion source offer, student, and programme enrolment must agree';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_student_conversion_request_integrity
BEFORE INSERT OR UPDATE OF source_offer_id, student_id, programme_enrolment_id
ON student_conversion_requests
FOR EACH ROW
EXECUTE FUNCTION enforce_student_conversion_request_integrity();

CREATE FUNCTION prevent_student_conversion_source_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF ROW(
            NEW.student_number,
            NEW.user_id,
            NEW.source_applicant_id,
            NEW.source_applicant_number,
            NEW.source_application_id,
            NEW.source_offer_id
        ) IS DISTINCT FROM ROW(
            OLD.student_number,
            OLD.user_id,
            OLD.source_applicant_id,
            OLD.source_applicant_number,
            OLD.source_application_id,
            OLD.source_offer_id
        ) THEN
        RAISE EXCEPTION 'Student conversion source identity is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_students_conversion_source_immutable
BEFORE UPDATE ON students
FOR EACH ROW
EXECUTE FUNCTION prevent_student_conversion_source_mutation();

CREATE FUNCTION prevent_student_enrolment_source_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF ROW(
            NEW.student_id,
            NEW.source_offer_id,
            NEW.source_programme_choice_id,
            NEW.programme_id,
            NEW.programme_version_id,
            NEW.programme_code,
            NEW.programme_name,
            NEW.intake_id,
            NEW.commencement_date
        ) IS DISTINCT FROM ROW(
            OLD.student_id,
            OLD.source_offer_id,
            OLD.source_programme_choice_id,
            OLD.programme_id,
            OLD.programme_version_id,
            OLD.programme_code,
            OLD.programme_name,
            OLD.intake_id,
            OLD.commencement_date
        ) THEN
        RAISE EXCEPTION 'Accepted-offer programme snapshot is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_student_enrolment_source_immutable
BEFORE UPDATE ON student_programme_enrolments
FOR EACH ROW
EXECUTE FUNCTION prevent_student_enrolment_source_mutation();

CREATE FUNCTION prevent_student_conversion_request_source_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF ROW(
            NEW.source_event_id,
            NEW.source_application_id,
            NEW.source_offer_id,
            NEW.student_id,
            NEW.programme_enrolment_id,
            NEW.requested_at
        ) IS DISTINCT FROM ROW(
            OLD.source_event_id,
            OLD.source_application_id,
            OLD.source_offer_id,
            OLD.student_id,
            OLD.programme_enrolment_id,
            OLD.requested_at
        ) THEN
        RAISE EXCEPTION 'Student conversion request source is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_student_conversion_request_source_immutable
BEFORE UPDATE ON student_conversion_requests
FOR EACH ROW
EXECUTE FUNCTION prevent_student_conversion_request_source_mutation();
