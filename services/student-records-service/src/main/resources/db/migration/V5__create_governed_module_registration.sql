-- Author: Tinashe K

CREATE TABLE registration_sessions (
    id uuid PRIMARY KEY,
    student_id uuid NOT NULL REFERENCES students (id),
    programme_enrolment_id uuid NOT NULL REFERENCES student_programme_enrolments (id),
    academic_period_id uuid NOT NULL,
    academic_period_code varchar(50) NOT NULL,
    academic_period_name varchar(150) NOT NULL,
    academic_period_starts_on date NOT NULL,
    academic_period_ends_on date NOT NULL,
    programme_version_id uuid NOT NULL,
    programme_period_number integer NOT NULL,
    registration_type varchar(20) NOT NULL,
    status varchar(30) NOT NULL,
    status_reason varchar(1000) NOT NULL,
    initiated_at timestamptz NOT NULL,
    submitted_at timestamptz,
    academic_approved_by_user_id uuid,
    academic_approved_at timestamptz,
    confirmed_by_user_id uuid,
    confirmed_at timestamptz,
    rejected_by_user_id uuid,
    rejected_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_registration_sessions_period_number CHECK (programme_period_number > 0),
    CONSTRAINT ck_registration_sessions_period_dates CHECK (academic_period_ends_on >= academic_period_starts_on),
    CONSTRAINT ck_registration_sessions_type CHECK (registration_type IN ('NORMAL', 'LATE', 'AMENDMENT')),
    CONSTRAINT ck_registration_sessions_status CHECK (
        status IN ('DRAFT', 'SUBMITTED', 'ACADEMIC_APPROVED', 'CONFIRMED', 'REJECTED', 'CANCELLED')
    ),
    CONSTRAINT ck_registration_sessions_timestamps CHECK (
        (status = 'DRAFT' AND submitted_at IS NULL AND academic_approved_at IS NULL AND confirmed_at IS NULL AND rejected_at IS NULL)
        OR (status = 'SUBMITTED' AND submitted_at IS NOT NULL AND academic_approved_at IS NULL AND confirmed_at IS NULL AND rejected_at IS NULL)
        OR (status = 'ACADEMIC_APPROVED' AND submitted_at IS NOT NULL AND academic_approved_at IS NOT NULL AND confirmed_at IS NULL AND rejected_at IS NULL)
        OR (status = 'CONFIRMED' AND submitted_at IS NOT NULL AND academic_approved_at IS NOT NULL AND confirmed_at IS NOT NULL AND rejected_at IS NULL)
        OR (status = 'REJECTED' AND submitted_at IS NOT NULL AND confirmed_at IS NULL AND rejected_at IS NOT NULL)
        OR (status = 'CANCELLED' AND confirmed_at IS NULL)
    ),
    CONSTRAINT ck_registration_sessions_academic_actor CHECK (
        (academic_approved_at IS NULL AND academic_approved_by_user_id IS NULL)
        OR (academic_approved_at IS NOT NULL AND academic_approved_by_user_id IS NOT NULL)
    ),
    CONSTRAINT ck_registration_sessions_confirmation_actor CHECK (
        (confirmed_at IS NULL AND confirmed_by_user_id IS NULL)
        OR (confirmed_at IS NOT NULL AND confirmed_by_user_id IS NOT NULL)
    ),
    CONSTRAINT ck_registration_sessions_rejection_actor CHECK (
        (rejected_at IS NULL AND rejected_by_user_id IS NULL)
        OR (rejected_at IS NOT NULL AND rejected_by_user_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_registration_sessions_student_period
    ON registration_sessions (student_id, academic_period_id)
    WHERE deleted_at IS NULL AND status <> 'CANCELLED';

CREATE INDEX idx_registration_sessions_operations
    ON registration_sessions (status, academic_period_starts_on, student_id)
    WHERE deleted_at IS NULL;

CREATE TABLE registration_modules (
    id uuid PRIMARY KEY,
    registration_session_id uuid NOT NULL REFERENCES registration_sessions (id),
    curriculum_module_id uuid NOT NULL,
    module_id uuid NOT NULL,
    module_code varchar(50) NOT NULL,
    module_name varchar(200) NOT NULL,
    curriculum_module_type varchar(20) NOT NULL,
    credit_value numeric(6,2) NOT NULL,
    minimum_mark_required numeric(5,2),
    selection_source varchar(30) NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_registration_modules_curriculum UNIQUE (registration_session_id, curriculum_module_id),
    CONSTRAINT uk_registration_modules_module UNIQUE (registration_session_id, module_id),
    CONSTRAINT ck_registration_modules_type CHECK (curriculum_module_type IN ('COMPULSORY', 'ELECTIVE')),
    CONSTRAINT ck_registration_modules_source CHECK (
        selection_source IN ('AUTO_COMPULSORY', 'STUDENT_ELECTIVE', 'STAFF_ELECTIVE', 'CARRY', 'REPEAT')
    ),
    CONSTRAINT ck_registration_modules_credit CHECK (credit_value > 0),
    CONSTRAINT ck_registration_modules_minimum_mark CHECK (
        minimum_mark_required IS NULL OR minimum_mark_required BETWEEN 0 AND 100
    )
);

CREATE TABLE registration_status_events (
    id uuid PRIMARY KEY,
    registration_session_id uuid NOT NULL REFERENCES registration_sessions (id),
    from_status varchar(30),
    to_status varchar(30) NOT NULL,
    reason varchar(1000) NOT NULL,
    changed_by_user_id uuid NOT NULL,
    changed_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_registration_status_events_from CHECK (
        from_status IS NULL OR from_status IN ('DRAFT', 'SUBMITTED', 'ACADEMIC_APPROVED', 'CONFIRMED', 'REJECTED', 'CANCELLED')
    ),
    CONSTRAINT ck_registration_status_events_to CHECK (
        to_status IN ('DRAFT', 'SUBMITTED', 'ACADEMIC_APPROVED', 'CONFIRMED', 'REJECTED', 'CANCELLED')
    )
);

CREATE INDEX idx_registration_status_events_history
    ON registration_status_events (registration_session_id, changed_at, id);

CREATE TABLE registration_sessions_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    student_id uuid, programme_enrolment_id uuid, academic_period_id uuid,
    academic_period_code varchar(50), academic_period_name varchar(150),
    academic_period_starts_on date, academic_period_ends_on date, programme_version_id uuid,
    programme_period_number integer, registration_type varchar(20), status varchar(30),
    status_reason varchar(1000), initiated_at timestamptz, submitted_at timestamptz,
    academic_approved_by_user_id uuid, academic_approved_at timestamptz,
    confirmed_by_user_id uuid, confirmed_at timestamptz,
    rejected_by_user_id uuid, rejected_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid,
    modified_by_user_id uuid, deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE registration_modules_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    registration_session_id uuid, curriculum_module_id uuid, module_id uuid,
    module_code varchar(50), module_name varchar(200), curriculum_module_type varchar(20),
    credit_value numeric(6,2), minimum_mark_required numeric(5,2),
    selection_source varchar(30), sort_order integer,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid,
    modified_by_user_id uuid, deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE registration_status_events_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    registration_session_id uuid, from_status varchar(30), to_status varchar(30),
    reason varchar(1000), changed_by_user_id uuid, changed_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid,
    modified_by_user_id uuid, deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

CREATE OR REPLACE FUNCTION enforce_registration_session_integrity()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM student_programme_enrolments enrolment
        WHERE enrolment.id = NEW.programme_enrolment_id
          AND enrolment.student_id = NEW.student_id
          AND enrolment.programme_version_id = NEW.programme_version_id
          AND enrolment.deleted_at IS NULL
    ) THEN
        RAISE EXCEPTION 'Registration session student and programme enrolment are inconsistent';
    END IF;
    IF TG_OP = 'UPDATE' AND (
        NEW.student_id IS DISTINCT FROM OLD.student_id
        OR NEW.programme_enrolment_id IS DISTINCT FROM OLD.programme_enrolment_id
        OR NEW.academic_period_id IS DISTINCT FROM OLD.academic_period_id
        OR NEW.programme_version_id IS DISTINCT FROM OLD.programme_version_id
        OR NEW.programme_period_number IS DISTINCT FROM OLD.programme_period_number
        OR NEW.registration_type IS DISTINCT FROM OLD.registration_type
    ) THEN
        RAISE EXCEPTION 'Registration source identity is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_registration_session_integrity
    BEFORE INSERT OR UPDATE ON registration_sessions
    FOR EACH ROW EXECUTE FUNCTION enforce_registration_session_integrity();

CREATE OR REPLACE FUNCTION prevent_registration_module_snapshot_change()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.registration_session_id IS DISTINCT FROM OLD.registration_session_id
       OR NEW.curriculum_module_id IS DISTINCT FROM OLD.curriculum_module_id
       OR NEW.module_id IS DISTINCT FROM OLD.module_id
       OR NEW.module_code IS DISTINCT FROM OLD.module_code
       OR NEW.module_name IS DISTINCT FROM OLD.module_name
       OR NEW.curriculum_module_type IS DISTINCT FROM OLD.curriculum_module_type
       OR NEW.credit_value IS DISTINCT FROM OLD.credit_value
       OR NEW.minimum_mark_required IS DISTINCT FROM OLD.minimum_mark_required
       OR NEW.selection_source IS DISTINCT FROM OLD.selection_source
       OR NEW.sort_order IS DISTINCT FROM OLD.sort_order THEN
        RAISE EXCEPTION 'Registered Module snapshot is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_registration_module_snapshot_immutable
    BEFORE UPDATE ON registration_modules
    FOR EACH ROW EXECUTE FUNCTION prevent_registration_module_snapshot_change();
