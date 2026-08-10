-- Author: Tinashe K

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM registration_sessions) THEN
        RAISE EXCEPTION 'Existing registrations must be enriched with academic unit and programme level before V8 can be applied';
    END IF;
END $$;

ALTER TABLE registration_sessions
    ADD COLUMN owning_academic_unit_id uuid NOT NULL,
    ADD COLUMN owning_academic_unit_code varchar(80) NOT NULL,
    ADD COLUMN owning_academic_unit_name varchar(200) NOT NULL,
    ADD COLUMN programme_level_id uuid NOT NULL,
    ADD COLUMN programme_level_code varchar(80) NOT NULL,
    ADD COLUMN programme_level_name varchar(200) NOT NULL,
    ADD CONSTRAINT ck_registration_academic_unit_snapshot CHECK (
        length(trim(owning_academic_unit_code)) > 0 AND length(trim(owning_academic_unit_name)) > 0),
    ADD CONSTRAINT ck_registration_programme_level_snapshot CHECK (
        programme_level_code IN ('UG','PG') AND length(trim(programme_level_name)) > 0);

ALTER TABLE registration_sessions_aud
    ADD COLUMN owning_academic_unit_id uuid,
    ADD COLUMN owning_academic_unit_code varchar(80),
    ADD COLUMN owning_academic_unit_name varchar(200),
    ADD COLUMN programme_level_id uuid,
    ADD COLUMN programme_level_code varchar(80),
    ADD COLUMN programme_level_name varchar(200);
