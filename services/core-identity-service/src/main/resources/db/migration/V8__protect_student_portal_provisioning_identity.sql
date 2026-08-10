-- Author: Tinashe K

CREATE FUNCTION prevent_student_portal_provisioning_identity_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF ROW(
            NEW.conversion_request_id,
            NEW.student_id,
            NEW.student_number,
            NEW.user_id,
            NEW.role_assignment_id,
            NEW.provisioned_at
        ) IS DISTINCT FROM ROW(
            OLD.conversion_request_id,
            OLD.student_id,
            OLD.student_number,
            OLD.user_id,
            OLD.role_assignment_id,
            OLD.provisioned_at
        ) THEN
        RAISE EXCEPTION 'Student portal provisioning identity is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_student_portal_provisioning_identity_immutable
BEFORE UPDATE ON student_portal_access_provisioning
FOR EACH ROW
EXECUTE FUNCTION prevent_student_portal_provisioning_identity_mutation();
