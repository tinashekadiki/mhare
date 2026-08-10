-- Author: Tinashe K

CREATE FUNCTION prevent_student_finance_account_identity_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF ROW(
            NEW.account_number,
            NEW.student_id,
            NEW.student_number,
            NEW.user_id,
            NEW.source_offer_id,
            NEW.base_currency_code,
            NEW.opened_at
        ) IS DISTINCT FROM ROW(
            OLD.account_number,
            OLD.student_id,
            OLD.student_number,
            OLD.user_id,
            OLD.source_offer_id,
            OLD.base_currency_code,
            OLD.opened_at
        ) THEN
        RAISE EXCEPTION 'Student finance account identity and USD base currency are immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_student_finance_account_identity_immutable
BEFORE UPDATE ON student_finance_accounts
FOR EACH ROW
EXECUTE FUNCTION prevent_student_finance_account_identity_mutation();

CREATE FUNCTION enforce_student_finance_account_status_transition()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status = 'CLOSED' AND NEW.status <> 'CLOSED' THEN
        RAISE EXCEPTION 'A closed student finance account cannot be reopened by mutation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_student_finance_account_status_transition
BEFORE UPDATE OF status ON student_finance_accounts
FOR EACH ROW
EXECUTE FUNCTION enforce_student_finance_account_status_transition();
