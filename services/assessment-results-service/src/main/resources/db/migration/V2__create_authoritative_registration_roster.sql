-- Author: Tinashe K

CREATE TABLE registration_roster_imports (
    id uuid PRIMARY KEY,
    source_event_id uuid NOT NULL,
    registration_session_id uuid NOT NULL,
    student_id uuid NOT NULL,
    student_number varchar(40) NOT NULL,
    programme_enrolment_id uuid NOT NULL,
    programme_id uuid NOT NULL,
    programme_version_id uuid NOT NULL,
    academic_period_id uuid NOT NULL,
    academic_period_code varchar(50) NOT NULL,
    academic_period_name varchar(150) NOT NULL,
    academic_period_starts_on date NOT NULL,
    academic_period_ends_on date NOT NULL,
    programme_period_number integer NOT NULL,
    imported_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_registration_roster_imports_event UNIQUE (source_event_id),
    CONSTRAINT uk_registration_roster_imports_session UNIQUE (registration_session_id),
    CONSTRAINT ck_registration_roster_imports_period CHECK (programme_period_number > 0),
    CONSTRAINT ck_registration_roster_imports_dates CHECK (academic_period_ends_on >= academic_period_starts_on)
);

CREATE INDEX idx_registration_roster_imports_operations
    ON registration_roster_imports (academic_period_id, student_number)
    WHERE deleted_at IS NULL;

CREATE TABLE assessment_roster_entries (
    id uuid PRIMARY KEY,
    roster_import_id uuid NOT NULL REFERENCES registration_roster_imports (id),
    registration_module_id uuid NOT NULL,
    curriculum_module_id uuid NOT NULL,
    module_id uuid NOT NULL,
    module_code varchar(50) NOT NULL,
    module_name varchar(200) NOT NULL,
    curriculum_module_type varchar(20) NOT NULL,
    credit_value numeric(6,2) NOT NULL,
    minimum_mark_required numeric(5,2),
    eligibility_status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_assessment_roster_registration_module UNIQUE (registration_module_id),
    CONSTRAINT uk_assessment_roster_import_module UNIQUE (roster_import_id, module_id),
    CONSTRAINT ck_assessment_roster_module_type CHECK (curriculum_module_type IN ('COMPULSORY', 'ELECTIVE')),
    CONSTRAINT ck_assessment_roster_credit CHECK (credit_value > 0),
    CONSTRAINT ck_assessment_roster_minimum_mark CHECK (
        minimum_mark_required IS NULL OR minimum_mark_required BETWEEN 0 AND 100
    ),
    CONSTRAINT ck_assessment_roster_eligibility CHECK (eligibility_status IN ('ELIGIBLE', 'WITHDRAWN'))
);

CREATE INDEX idx_assessment_roster_module_period
    ON assessment_roster_entries (module_id, roster_import_id)
    WHERE deleted_at IS NULL AND eligibility_status = 'ELIGIBLE';

CREATE TABLE registration_roster_imports_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    source_event_id uuid, registration_session_id uuid, student_id uuid, student_number varchar(40),
    programme_enrolment_id uuid, programme_id uuid, programme_version_id uuid,
    academic_period_id uuid, academic_period_code varchar(50), academic_period_name varchar(150),
    academic_period_starts_on date, academic_period_ends_on date, programme_period_number integer,
    imported_at timestamptz, created_at timestamptz, updated_at timestamptz,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE assessment_roster_entries_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    roster_import_id uuid, registration_module_id uuid, curriculum_module_id uuid, module_id uuid,
    module_code varchar(50), module_name varchar(200), curriculum_module_type varchar(20),
    credit_value numeric(6,2), minimum_mark_required numeric(5,2), eligibility_status varchar(20),
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid,
    modified_by_user_id uuid, deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE integration_inbox (
    event_id uuid PRIMARY KEY,
    event_type varchar(160) NOT NULL,
    source_service varchar(100) NOT NULL,
    payload jsonb NOT NULL,
    received_at timestamptz NOT NULL,
    processed_at timestamptz
);

CREATE INDEX idx_assessment_results_inbox_processed_at ON integration_inbox (processed_at);

CREATE OR REPLACE FUNCTION prevent_roster_import_identity_change()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.source_event_id IS DISTINCT FROM OLD.source_event_id
       OR NEW.registration_session_id IS DISTINCT FROM OLD.registration_session_id
       OR NEW.student_id IS DISTINCT FROM OLD.student_id
       OR NEW.student_number IS DISTINCT FROM OLD.student_number
       OR NEW.programme_enrolment_id IS DISTINCT FROM OLD.programme_enrolment_id
       OR NEW.programme_id IS DISTINCT FROM OLD.programme_id
       OR NEW.programme_version_id IS DISTINCT FROM OLD.programme_version_id
       OR NEW.academic_period_id IS DISTINCT FROM OLD.academic_period_id
       OR NEW.academic_period_code IS DISTINCT FROM OLD.academic_period_code
       OR NEW.academic_period_name IS DISTINCT FROM OLD.academic_period_name
       OR NEW.academic_period_starts_on IS DISTINCT FROM OLD.academic_period_starts_on
       OR NEW.academic_period_ends_on IS DISTINCT FROM OLD.academic_period_ends_on
       OR NEW.programme_period_number IS DISTINCT FROM OLD.programme_period_number THEN
        RAISE EXCEPTION 'Imported registration roster identity is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_registration_roster_import_identity_immutable
    BEFORE UPDATE ON registration_roster_imports
    FOR EACH ROW EXECUTE FUNCTION prevent_roster_import_identity_change();

CREATE OR REPLACE FUNCTION prevent_assessment_roster_snapshot_change()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.roster_import_id IS DISTINCT FROM OLD.roster_import_id
       OR NEW.registration_module_id IS DISTINCT FROM OLD.registration_module_id
       OR NEW.curriculum_module_id IS DISTINCT FROM OLD.curriculum_module_id
       OR NEW.module_id IS DISTINCT FROM OLD.module_id
       OR NEW.module_code IS DISTINCT FROM OLD.module_code
       OR NEW.module_name IS DISTINCT FROM OLD.module_name
       OR NEW.curriculum_module_type IS DISTINCT FROM OLD.curriculum_module_type
       OR NEW.credit_value IS DISTINCT FROM OLD.credit_value
       OR NEW.minimum_mark_required IS DISTINCT FROM OLD.minimum_mark_required THEN
        RAISE EXCEPTION 'Assessment roster source snapshot is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_assessment_roster_snapshot_immutable
    BEFORE UPDATE ON assessment_roster_entries
    FOR EACH ROW EXECUTE FUNCTION prevent_assessment_roster_snapshot_change();
