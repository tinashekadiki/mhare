-- Author: Tinashe K
-- Intakes advertise programmes through governed Programme Type targets, with an
-- optional specific Programme whitelist for intentionally narrow cohorts.

CREATE TABLE intake_programme_type_targets (
    id uuid PRIMARY KEY,
    intake_id uuid NOT NULL REFERENCES intakes (id),
    programme_type_id uuid NOT NULL REFERENCES programme_types (id),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);

CREATE UNIQUE INDEX uk_intake_programme_type_targets_active
    ON intake_programme_type_targets (intake_id, programme_type_id)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_intake_programme_type_targets_intake
    ON intake_programme_type_targets (intake_id)
    WHERE deleted_at IS NULL;

CREATE TABLE intake_programme_targets (
    id uuid PRIMARY KEY,
    intake_id uuid NOT NULL REFERENCES intakes (id),
    programme_id uuid NOT NULL REFERENCES programmes (id),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);

CREATE UNIQUE INDEX uk_intake_programme_targets_active
    ON intake_programme_targets (intake_id, programme_id)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_intake_programme_targets_intake
    ON intake_programme_targets (intake_id)
    WHERE deleted_at IS NULL;

CREATE TABLE intake_programme_type_targets_aud AS TABLE intake_programme_type_targets WITH NO DATA;
ALTER TABLE intake_programme_type_targets_aud
    ADD COLUMN rev integer NOT NULL REFERENCES revinfo (rev),
    ADD COLUMN revtype smallint,
    ADD PRIMARY KEY (id, rev);

CREATE TABLE intake_programme_targets_aud AS TABLE intake_programme_targets WITH NO DATA;
ALTER TABLE intake_programme_targets_aud
    ADD COLUMN rev integer NOT NULL REFERENCES revinfo (rev),
    ADD COLUMN revtype smallint,
    ADD PRIMARY KEY (id, rev);

INSERT INTO intake_programme_type_targets (
    id, intake_id, programme_type_id, created_at, updated_at,
    created_by_user_id, modified_by_user_id, version
)
SELECT gen_random_uuid(), intake.id, programme_type.id, now(), now(),
       intake.created_by_user_id, intake.modified_by_user_id, 0
FROM intakes intake
CROSS JOIN programme_types programme_type
WHERE intake.deleted_at IS NULL
  AND programme_type.deleted_at IS NULL
  AND programme_type.status = 'ACTIVE';

CREATE OR REPLACE FUNCTION protect_intake_programme_target_draft()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    governed_intake_id uuid;
    governed_intake_status varchar(20);
BEGIN
    governed_intake_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.intake_id ELSE NEW.intake_id END;
    SELECT status INTO governed_intake_status
    FROM intakes
    WHERE id = governed_intake_id AND deleted_at IS NULL;

    IF governed_intake_status IS NULL THEN
        RAISE EXCEPTION 'The governed intake does not exist.' USING ERRCODE = '23503';
    END IF;
    IF governed_intake_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Programme eligibility can only be changed while the intake is in draft.' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.intake_id IS DISTINCT FROM OLD.intake_id THEN
        RAISE EXCEPTION 'An intake programme target cannot move to another intake.' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' AND TG_TABLE_NAME = 'intake_programme_type_targets'
       AND NEW.programme_type_id IS DISTINCT FROM OLD.programme_type_id THEN
        RAISE EXCEPTION 'An intake Programme Type target identity cannot change.' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' AND TG_TABLE_NAME = 'intake_programme_targets'
       AND NEW.programme_id IS DISTINCT FROM OLD.programme_id THEN
        RAISE EXCEPTION 'An intake Programme target identity cannot change.' USING ERRCODE = '23514';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

CREATE TRIGGER trg_protect_intake_programme_type_target_draft
BEFORE INSERT OR UPDATE OR DELETE ON intake_programme_type_targets
FOR EACH ROW EXECUTE FUNCTION protect_intake_programme_target_draft();

CREATE TRIGGER trg_protect_intake_programme_target_draft
BEFORE INSERT OR UPDATE OR DELETE ON intake_programme_targets
FOR EACH ROW EXECUTE FUNCTION protect_intake_programme_target_draft();

CREATE OR REPLACE FUNCTION validate_intake_specific_programme_target()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    selected_programme_type_id uuid;
BEGIN
    IF NEW.deleted_at IS NOT NULL THEN
        RETURN NEW;
    END IF;

    SELECT programme_type_id INTO selected_programme_type_id
    FROM programmes
    WHERE id = NEW.programme_id
      AND deleted_at IS NULL
      AND status = 'ACTIVE';

    IF selected_programme_type_id IS NULL THEN
        RAISE EXCEPTION 'A specific intake Programme must be active.' USING ERRCODE = '23514';
    END IF;
    IF NOT EXISTS (
        SELECT 1
        FROM intake_programme_type_targets target
        WHERE target.intake_id = NEW.intake_id
          AND target.programme_type_id = selected_programme_type_id
          AND target.deleted_at IS NULL
    ) THEN
        RAISE EXCEPTION 'A specific Programme must belong to a selected intake Programme Type.' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_intake_specific_programme_target
BEFORE INSERT OR UPDATE ON intake_programme_targets
FOR EACH ROW EXECUTE FUNCTION validate_intake_specific_programme_target();

CREATE OR REPLACE FUNCTION protect_intake_programme_type_target_removal()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.deleted_at IS NULL AND (TG_OP = 'DELETE' OR NEW.deleted_at IS NOT NULL)
       AND EXISTS (
           SELECT 1
           FROM intake_programme_targets specific_target
           JOIN programmes programme ON programme.id = specific_target.programme_id
           WHERE specific_target.intake_id = OLD.intake_id
             AND specific_target.deleted_at IS NULL
             AND programme.programme_type_id = OLD.programme_type_id
             AND programme.deleted_at IS NULL
       ) THEN
        RAISE EXCEPTION 'Remove specific Programmes before removing their Programme Type from the intake.' USING ERRCODE = '23514';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

CREATE TRIGGER trg_protect_intake_programme_type_target_removal
BEFORE UPDATE OR DELETE ON intake_programme_type_targets
FOR EACH ROW EXECUTE FUNCTION protect_intake_programme_type_target_removal();

CREATE OR REPLACE FUNCTION require_intake_programme_type_before_open()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.status = 'OPEN' AND OLD.status <> 'OPEN'
       AND NOT EXISTS (
           SELECT 1
           FROM intake_programme_type_targets target
           WHERE target.intake_id = NEW.id AND target.deleted_at IS NULL
       ) THEN
        RAISE EXCEPTION 'Select at least one Programme Type before opening the intake.' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_require_intake_programme_type_before_open
BEFORE UPDATE OF status ON intakes
FOR EACH ROW EXECUTE FUNCTION require_intake_programme_type_before_open();

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO emhare_service;
