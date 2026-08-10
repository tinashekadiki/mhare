-- Author: Tinashe K
-- Approved curricula remain operationally amendable. The application service
-- verifies Student Records and Results before performing a soft removal.

CREATE OR REPLACE FUNCTION protect_approved_curriculum()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    parent_version_id uuid;
    parent_status varchar(20);
BEGIN
    parent_version_id := CASE
        WHEN TG_OP = 'INSERT' THEN NEW.programme_version_id
        ELSE OLD.programme_version_id
    END;

    SELECT status INTO parent_status
    FROM programme_versions
    WHERE id = parent_version_id;

    IF parent_status IS NULL THEN
        RAISE EXCEPTION 'The parent programme version does not exist.' USING ERRCODE = '23503';
    END IF;

    IF parent_status = 'RETIRED' THEN
        RAISE EXCEPTION 'A retired curriculum is immutable.' USING ERRCODE = '23514';
    END IF;

    IF parent_status NOT IN ('DRAFT', 'APPROVED') THEN
        RAISE EXCEPTION 'Curriculum records can only change while the programme version is DRAFT or APPROVED.' USING ERRCODE = '23514';
    END IF;

    IF TG_OP = 'UPDATE' AND (
        NEW.programme_version_id IS DISTINCT FROM OLD.programme_version_id
        OR NEW.module_id IS DISTINCT FROM OLD.module_id
    ) THEN
        RAISE EXCEPTION 'A curriculum Module identity cannot be changed; amend its governed placement instead.' USING ERRCODE = '23514';
    END IF;

    IF TG_OP = 'DELETE' AND parent_status = 'APPROVED' THEN
        RAISE EXCEPTION 'Approved curriculum Modules must use the governed soft-removal workflow.' USING ERRCODE = '23514';
    END IF;

    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;
