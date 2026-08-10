-- Author: Tinashe K

ALTER TABLE admission_cycles
    ADD COLUMN maximum_programme_choices integer NOT NULL DEFAULT 3,
    ADD CONSTRAINT ck_admission_cycles_maximum_programme_choices
        CHECK (maximum_programme_choices BETWEEN 1 AND 20);

ALTER TABLE admission_cycles_aud
    ADD COLUMN maximum_programme_choices integer;

ALTER TABLE application_programme_choices
    ADD COLUMN programme_version_id uuid,
    ADD COLUMN programme_code varchar(50),
    ADD COLUMN programme_name varchar(200),
    ADD COLUMN award_name varchar(200),
    ADD COLUMN owning_academic_unit_id uuid,
    ADD COLUMN owning_academic_unit_name varchar(180),
    ADD COLUMN programme_version_code varchar(40),
    ADD COLUMN catalogue_snapshot_status varchar(30) NOT NULL DEFAULT 'LEGACY_UNRESOLVED',
    ADD CONSTRAINT ck_application_choice_catalogue_snapshot_status
        CHECK (catalogue_snapshot_status IN ('VALIDATED', 'LEGACY_UNRESOLVED')),
    ADD CONSTRAINT ck_application_choice_validated_snapshot
        CHECK (
            catalogue_snapshot_status = 'LEGACY_UNRESOLVED'
            OR (
                programme_version_id IS NOT NULL
                AND programme_code IS NOT NULL
                AND programme_name IS NOT NULL
                AND award_name IS NOT NULL
                AND owning_academic_unit_id IS NOT NULL
                AND owning_academic_unit_name IS NOT NULL
                AND programme_version_code IS NOT NULL
            )
        );

ALTER TABLE application_programme_choices_aud
    ADD COLUMN programme_version_id uuid,
    ADD COLUMN programme_code varchar(50),
    ADD COLUMN programme_name varchar(200),
    ADD COLUMN award_name varchar(200),
    ADD COLUMN owning_academic_unit_id uuid,
    ADD COLUMN owning_academic_unit_name varchar(180),
    ADD COLUMN programme_version_code varchar(40),
    ADD COLUMN catalogue_snapshot_status varchar(30);

CREATE OR REPLACE FUNCTION enforce_application_programme_choice_governance()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    application_status varchar(30);
    maximum_choices integer;
BEGIN
    SELECT application.status, cycle.maximum_programme_choices
      INTO application_status, maximum_choices
      FROM applications application
      JOIN admission_cycles cycle ON cycle.id = application.admission_cycle_id
     WHERE application.id = NEW.application_id
       AND application.deleted_at IS NULL
       AND cycle.deleted_at IS NULL;

    IF application_status IS NULL THEN
        RAISE EXCEPTION 'Application or admission cycle is unavailable for programme choice capture.';
    END IF;
    IF application_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Programme choices can only be captured while the application is in DRAFT status.';
    END IF;
    IF NEW.choice_rank < 1 OR NEW.choice_rank > maximum_choices THEN
        RAISE EXCEPTION 'Programme choice rank % exceeds the configured cycle maximum of %.', NEW.choice_rank, maximum_choices;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_application_programme_choice_governance
BEFORE INSERT OR UPDATE OF application_id, programme_id, choice_rank
ON application_programme_choices
FOR EACH ROW
EXECUTE FUNCTION enforce_application_programme_choice_governance();
