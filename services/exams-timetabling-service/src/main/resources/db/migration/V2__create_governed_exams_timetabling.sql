-- Author: Tinashe K

CREATE TABLE exam_registration_imports (
    id uuid PRIMARY KEY,
    source_event_id uuid NOT NULL UNIQUE,
    registration_session_id uuid NOT NULL UNIQUE,
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
    imported_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_exam_registration_period_dates CHECK (academic_period_ends_on >= academic_period_starts_on)
);

CREATE TABLE exam_candidate_modules (
    id uuid PRIMARY KEY,
    registration_import_id uuid NOT NULL REFERENCES exam_registration_imports(id),
    registration_module_id uuid NOT NULL UNIQUE,
    curriculum_module_id uuid NOT NULL,
    module_id uuid NOT NULL,
    module_code varchar(50) NOT NULL,
    module_name varchar(200) NOT NULL,
    eligibility_status varchar(20) NOT NULL DEFAULT 'ELIGIBLE',
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT uk_exam_candidate_module UNIQUE (registration_import_id, module_id),
    CONSTRAINT ck_exam_candidate_eligibility CHECK (eligibility_status IN ('ELIGIBLE', 'WITHDRAWN'))
);

CREATE TABLE exam_venue_types (
    id uuid PRIMARY KEY,
    code varchar(30) NOT NULL,
    name varchar(120) NOT NULL,
    description varchar(500),
    active boolean NOT NULL,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL
);
CREATE UNIQUE INDEX uk_exam_venue_type_code ON exam_venue_types(lower(code)) WHERE deleted_at IS NULL;

CREATE TABLE exam_venues (
    id uuid PRIMARY KEY,
    venue_type_id uuid NOT NULL REFERENCES exam_venue_types(id),
    code varchar(40) NOT NULL,
    name varchar(150) NOT NULL,
    campus_name varchar(150) NOT NULL,
    building_name varchar(150),
    room_name varchar(100),
    examination_capacity integer NOT NULL,
    accessibility_notes varchar(500),
    active boolean NOT NULL,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_exam_venue_capacity CHECK (examination_capacity > 0)
);
CREATE UNIQUE INDEX uk_exam_venue_code ON exam_venues(lower(code)) WHERE deleted_at IS NULL;

CREATE TABLE exam_venue_availability_windows (
    id uuid PRIMARY KEY,
    venue_id uuid NOT NULL REFERENCES exam_venues(id),
    available_from timestamptz NOT NULL,
    available_until timestamptz NOT NULL,
    notes varchar(500),
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_exam_venue_availability_window CHECK (available_until > available_from)
);
CREATE INDEX idx_exam_venue_availability ON exam_venue_availability_windows(venue_id, available_from, available_until)
    WHERE deleted_at IS NULL;

CREATE TABLE exam_sessions (
    id uuid PRIMARY KEY,
    academic_period_id uuid NOT NULL,
    academic_period_code varchar(50) NOT NULL,
    code varchar(40) NOT NULL,
    name varchar(150) NOT NULL,
    assessment_type varchar(30) NOT NULL,
    starts_on date NOT NULL,
    ends_on date NOT NULL,
    status varchar(20) NOT NULL,
    approved_by_user_id uuid,
    approved_at timestamptz,
    approval_reason varchar(1000),
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_exam_session_dates CHECK (ends_on >= starts_on),
    CONSTRAINT ck_exam_session_type CHECK (assessment_type IN ('FINAL_EXAM', 'SUPPLEMENTARY', 'DEFERRED', 'SPECIAL')),
    CONSTRAINT ck_exam_session_status CHECK (status IN ('DRAFT', 'APPROVED', 'CLOSED')),
    CONSTRAINT ck_exam_session_approval CHECK (
      (status='DRAFT' AND approved_by_user_id IS NULL AND approved_at IS NULL AND approval_reason IS NULL)
      OR (status IN ('APPROVED','CLOSED') AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL
          AND length(trim(approval_reason)) > 0))
);
CREATE UNIQUE INDEX uk_exam_session_code ON exam_sessions(academic_period_id, lower(code)) WHERE deleted_at IS NULL;

CREATE TABLE exam_session_slots (
    id uuid PRIMARY KEY,
    exam_session_id uuid NOT NULL REFERENCES exam_sessions(id),
    code varchar(40) NOT NULL,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT uk_exam_session_slot_code UNIQUE(exam_session_id, code),
    CONSTRAINT ck_exam_session_slot_window CHECK (ends_at > starts_at)
);
CREATE INDEX idx_exam_session_slots_time ON exam_session_slots(exam_session_id, starts_at) WHERE deleted_at IS NULL;

CREATE TABLE module_exam_requirements (
    id uuid PRIMARY KEY,
    academic_period_id uuid NOT NULL,
    module_id uuid NOT NULL,
    module_code varchar(50) NOT NULL,
    module_name varchar(200) NOT NULL,
    requirement_version integer NOT NULL,
    duration_minutes integer NOT NULL,
    reading_time_minutes integer NOT NULL DEFAULT 0,
    required_venue_type_id uuid REFERENCES exam_venue_types(id),
    special_requirements varchar(1000),
    status varchar(20) NOT NULL,
    approved_by_user_id uuid,
    approved_at timestamptz,
    approval_reason varchar(1000),
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT uk_module_exam_requirement_version UNIQUE(academic_period_id, module_id, requirement_version),
    CONSTRAINT ck_module_exam_requirement_version CHECK (requirement_version > 0),
    CONSTRAINT ck_module_exam_duration CHECK (duration_minutes BETWEEN 15 AND 480),
    CONSTRAINT ck_module_exam_reading_time CHECK (reading_time_minutes BETWEEN 0 AND 120),
    CONSTRAINT ck_module_exam_requirement_status CHECK (status IN ('DRAFT', 'APPROVED', 'SUPERSEDED')),
    CONSTRAINT ck_module_exam_requirement_approval CHECK (
      (status='DRAFT' AND approved_by_user_id IS NULL AND approved_at IS NULL AND approval_reason IS NULL)
      OR (status IN ('APPROVED','SUPERSEDED') AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL
          AND length(trim(approval_reason)) > 0))
);
CREATE UNIQUE INDEX uk_module_exam_requirement_approved ON module_exam_requirements(academic_period_id, module_id)
    WHERE status='APPROVED' AND deleted_at IS NULL;

CREATE TABLE exam_timetable_generation_runs (
    id uuid PRIMARY KEY,
    exam_session_id uuid NOT NULL REFERENCES exam_sessions(id),
    run_number varchar(60) NOT NULL UNIQUE,
    status varchar(20) NOT NULL,
    candidate_count integer NOT NULL,
    module_count integer NOT NULL,
    timetable_entry_count integer NOT NULL,
    conflict_count integer NOT NULL,
    generation_policy jsonb NOT NULL,
    generated_by_user_id uuid NOT NULL,
    generated_at timestamptz NOT NULL,
    reviewed_by_user_id uuid, reviewed_at timestamptz, review_reason varchar(1000),
    approved_by_user_id uuid, approved_at timestamptz, approval_reason varchar(1000),
    published_by_user_id uuid, published_at timestamptz, publication_reason varchar(1000),
    rejected_by_user_id uuid, rejected_at timestamptz, rejection_reason varchar(1000),
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_exam_timetable_run_status CHECK (status IN ('GENERATED','REVIEWED','APPROVED','PUBLISHED','REJECTED')),
    CONSTRAINT ck_exam_timetable_run_counts CHECK (candidate_count >= 0 AND module_count > 0
        AND timetable_entry_count >= 0 AND conflict_count >= 0),
    CONSTRAINT ck_exam_timetable_run_review CHECK ((status='GENERATED' AND reviewed_by_user_id IS NULL)
        OR status IN ('REVIEWED','APPROVED','PUBLISHED','REJECTED')),
    CONSTRAINT ck_exam_timetable_run_approval CHECK ((status IN ('GENERATED','REVIEWED') AND approved_by_user_id IS NULL)
        OR status IN ('APPROVED','PUBLISHED','REJECTED')),
    CONSTRAINT ck_exam_timetable_run_publication CHECK ((status <> 'PUBLISHED' AND published_by_user_id IS NULL)
        OR (status='PUBLISHED' AND published_by_user_id IS NOT NULL AND published_at IS NOT NULL
            AND length(trim(publication_reason)) > 0)),
    CONSTRAINT ck_exam_timetable_actor_separation CHECK (
        (reviewed_by_user_id IS NULL OR reviewed_by_user_id <> generated_by_user_id)
        AND (approved_by_user_id IS NULL OR (approved_by_user_id <> generated_by_user_id
             AND approved_by_user_id IS DISTINCT FROM reviewed_by_user_id))
        AND (published_by_user_id IS NULL OR (published_by_user_id <> generated_by_user_id
             AND published_by_user_id IS DISTINCT FROM reviewed_by_user_id
             AND published_by_user_id IS DISTINCT FROM approved_by_user_id)))
);
CREATE UNIQUE INDEX uk_exam_timetable_one_active ON exam_timetable_generation_runs(exam_session_id)
    WHERE status IN ('GENERATED','REVIEWED','APPROVED') AND deleted_at IS NULL;

CREATE TABLE exam_master_timetable_entries (
    id uuid PRIMARY KEY,
    generation_run_id uuid NOT NULL REFERENCES exam_timetable_generation_runs(id),
    exam_session_slot_id uuid NOT NULL REFERENCES exam_session_slots(id),
    module_exam_requirement_id uuid NOT NULL REFERENCES module_exam_requirements(id),
    module_id uuid NOT NULL,
    module_code varchar(50) NOT NULL,
    module_name varchar(200) NOT NULL,
    candidate_count integer NOT NULL,
    scheduled_starts_at timestamptz NOT NULL,
    scheduled_ends_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT uk_exam_master_run_module UNIQUE(generation_run_id, module_id),
    CONSTRAINT ck_exam_master_candidates CHECK(candidate_count > 0),
    CONSTRAINT ck_exam_master_window CHECK(scheduled_ends_at > scheduled_starts_at)
);

CREATE TABLE exam_timetable_venue_allocations (
    id uuid PRIMARY KEY,
    master_timetable_entry_id uuid NOT NULL REFERENCES exam_master_timetable_entries(id),
    venue_id uuid NOT NULL REFERENCES exam_venues(id),
    allocated_capacity integer NOT NULL,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT uk_exam_entry_venue UNIQUE(master_timetable_entry_id, venue_id),
    CONSTRAINT ck_exam_allocation_capacity CHECK(allocated_capacity > 0)
);

CREATE TABLE exam_student_timetable_entries (
    id uuid PRIMARY KEY,
    generation_run_id uuid NOT NULL REFERENCES exam_timetable_generation_runs(id),
    master_timetable_entry_id uuid NOT NULL REFERENCES exam_master_timetable_entries(id),
    venue_allocation_id uuid NOT NULL REFERENCES exam_timetable_venue_allocations(id),
    registration_import_id uuid NOT NULL REFERENCES exam_registration_imports(id),
    candidate_module_id uuid NOT NULL REFERENCES exam_candidate_modules(id),
    student_id uuid NOT NULL,
    student_number varchar(40) NOT NULL,
    module_id uuid NOT NULL,
    module_code varchar(50) NOT NULL,
    scheduled_starts_at timestamptz NOT NULL,
    scheduled_ends_at timestamptz NOT NULL,
    seat_number integer NOT NULL,
    attendance_status varchar(20) NOT NULL DEFAULT 'EXPECTED',
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT uk_exam_student_run_module UNIQUE(generation_run_id, student_id, module_id),
    CONSTRAINT uk_exam_student_venue_seat UNIQUE(venue_allocation_id, seat_number),
    CONSTRAINT ck_exam_student_window CHECK(scheduled_ends_at > scheduled_starts_at),
    CONSTRAINT ck_exam_student_seat CHECK(seat_number > 0),
    CONSTRAINT ck_exam_attendance_status CHECK(attendance_status IN ('EXPECTED','PRESENT','ABSENT','EXCUSED'))
);
CREATE INDEX idx_exam_student_timetable_lookup ON exam_student_timetable_entries(student_id, scheduled_starts_at)
    WHERE deleted_at IS NULL;

CREATE TABLE exam_timetable_run_events (
    id uuid PRIMARY KEY,
    generation_run_id uuid NOT NULL REFERENCES exam_timetable_generation_runs(id),
    previous_status varchar(20),
    new_status varchar(20) NOT NULL,
    reason varchar(1000) NOT NULL,
    actor_user_id uuid NOT NULL,
    occurred_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL
);

CREATE TABLE integration_inbox (
    event_id uuid PRIMARY KEY,
    event_type varchar(160) NOT NULL,
    source_service varchar(100) NOT NULL,
    payload jsonb NOT NULL,
    received_at timestamptz NOT NULL,
    processed_at timestamptz
);

-- Envers audit tables retain every business-state revision.
CREATE TABLE exam_registration_imports_aud (
 id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint,
 source_event_id uuid, registration_session_id uuid, student_id uuid, student_number varchar(40),
 programme_enrolment_id uuid, programme_id uuid, programme_version_id uuid, academic_period_id uuid,
 academic_period_code varchar(50), academic_period_name varchar(150), academic_period_starts_on date,
 academic_period_ends_on date, imported_at timestamptz, created_at timestamptz, updated_at timestamptz,
 created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
 PRIMARY KEY(id,rev));
CREATE TABLE exam_candidate_modules_aud (
 id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint,
 registration_import_id uuid, registration_module_id uuid, curriculum_module_id uuid, module_id uuid,
 module_code varchar(50), module_name varchar(200), eligibility_status varchar(20), created_at timestamptz,
 updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
 deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev));
CREATE TABLE exam_venue_types_aud (
 id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint, code varchar(30), name varchar(120),
 description varchar(500), active boolean, created_at timestamptz, updated_at timestamptz, created_by_user_id uuid,
 modified_by_user_id uuid, deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev));
CREATE TABLE exam_venues_aud (
 id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint, venue_type_id uuid, code varchar(40),
 name varchar(150), campus_name varchar(150), building_name varchar(150), room_name varchar(100),
 examination_capacity integer, accessibility_notes varchar(500), active boolean, created_at timestamptz,
 updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
 deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev));
CREATE TABLE exam_venue_availability_windows_aud (
 id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint, venue_id uuid,
 available_from timestamptz, available_until timestamptz, notes varchar(500), created_at timestamptz,
 updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
 deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev));
CREATE TABLE exam_sessions_aud (
 id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint, academic_period_id uuid,
 academic_period_code varchar(50), code varchar(40), name varchar(150), assessment_type varchar(30), starts_on date,
 ends_on date, status varchar(20), approved_by_user_id uuid, approved_at timestamptz, approval_reason varchar(1000),
 created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
 deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev));
CREATE TABLE exam_session_slots_aud (
 id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint, exam_session_id uuid, code varchar(40),
 starts_at timestamptz, ends_at timestamptz, created_at timestamptz, updated_at timestamptz,
 created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
 PRIMARY KEY(id,rev));
CREATE TABLE module_exam_requirements_aud (
 id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint, academic_period_id uuid, module_id uuid,
 module_code varchar(50), module_name varchar(200), requirement_version integer, duration_minutes integer,
 reading_time_minutes integer, required_venue_type_id uuid, special_requirements varchar(1000), status varchar(20),
 approved_by_user_id uuid, approved_at timestamptz, approval_reason varchar(1000), created_at timestamptz,
 updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
 deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev));
CREATE TABLE exam_timetable_generation_runs_aud (
 id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint, exam_session_id uuid,
 run_number varchar(60), status varchar(20), candidate_count integer, module_count integer, timetable_entry_count integer,
 conflict_count integer, generation_policy jsonb, generated_by_user_id uuid, generated_at timestamptz,
 reviewed_by_user_id uuid, reviewed_at timestamptz, review_reason varchar(1000), approved_by_user_id uuid,
 approved_at timestamptz, approval_reason varchar(1000), published_by_user_id uuid, published_at timestamptz,
 publication_reason varchar(1000), rejected_by_user_id uuid, rejected_at timestamptz, rejection_reason varchar(1000),
 created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
 deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev));
CREATE TABLE exam_master_timetable_entries_aud (
 id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint, generation_run_id uuid,
 exam_session_slot_id uuid, module_exam_requirement_id uuid, module_id uuid, module_code varchar(50),
 module_name varchar(200), candidate_count integer, scheduled_starts_at timestamptz, scheduled_ends_at timestamptz,
 created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
 deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev));
CREATE TABLE exam_timetable_venue_allocations_aud (
 id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint, master_timetable_entry_id uuid,
 venue_id uuid, allocated_capacity integer, created_at timestamptz, updated_at timestamptz, created_by_user_id uuid,
 modified_by_user_id uuid, deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev));
CREATE TABLE exam_student_timetable_entries_aud (
 id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint, generation_run_id uuid,
 master_timetable_entry_id uuid, venue_allocation_id uuid, registration_import_id uuid, candidate_module_id uuid,
 student_id uuid, student_number varchar(40), module_id uuid, module_code varchar(50),
 scheduled_starts_at timestamptz, scheduled_ends_at timestamptz, seat_number integer, attendance_status varchar(20),
 created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
 deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev));
CREATE TABLE exam_timetable_run_events_aud (
 id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint, generation_run_id uuid,
 previous_status varchar(20), new_status varchar(20), reason varchar(1000), actor_user_id uuid, occurred_at timestamptz,
 created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
 deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev));

CREATE OR REPLACE FUNCTION enforce_exam_source_snapshot_immutability() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.source_event_id,NEW.registration_session_id,NEW.student_id,NEW.student_number,
   NEW.programme_enrolment_id,NEW.programme_id,NEW.programme_version_id,NEW.academic_period_id,
   NEW.academic_period_code,NEW.academic_period_name,NEW.academic_period_starts_on,NEW.academic_period_ends_on)
   IS DISTINCT FROM ROW(OLD.source_event_id,OLD.registration_session_id,OLD.student_id,OLD.student_number,
   OLD.programme_enrolment_id,OLD.programme_id,OLD.programme_version_id,OLD.academic_period_id,
   OLD.academic_period_code,OLD.academic_period_name,OLD.academic_period_starts_on,OLD.academic_period_ends_on) THEN
   RAISE EXCEPTION 'Confirmed exam registration source snapshot is immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_exam_registration_snapshot_immutable BEFORE UPDATE ON exam_registration_imports
 FOR EACH ROW EXECUTE FUNCTION enforce_exam_source_snapshot_immutability();

CREATE OR REPLACE FUNCTION enforce_exam_candidate_snapshot_immutability() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.registration_import_id,NEW.registration_module_id,NEW.curriculum_module_id,NEW.module_id,NEW.module_code,NEW.module_name)
   IS DISTINCT FROM ROW(OLD.registration_import_id,OLD.registration_module_id,OLD.curriculum_module_id,OLD.module_id,OLD.module_code,OLD.module_name) THEN
   RAISE EXCEPTION 'Exam candidate Module source snapshot is immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_exam_candidate_snapshot_immutable BEFORE UPDATE ON exam_candidate_modules
 FOR EACH ROW EXECUTE FUNCTION enforce_exam_candidate_snapshot_immutability();

CREATE OR REPLACE FUNCTION enforce_exam_slot_within_session() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE session_start date; session_end date; session_status varchar(20);
BEGIN
 SELECT starts_on,ends_on,status INTO session_start,session_end,session_status FROM exam_sessions WHERE id=NEW.exam_session_id;
 IF NEW.starts_at::date < session_start OR NEW.ends_at::date > session_end THEN
   RAISE EXCEPTION 'Exam slot must be within the exam session date range'; END IF;
 IF TG_OP='INSERT' AND session_status <> 'DRAFT' THEN RAISE EXCEPTION 'Slots can only be added to a draft exam session'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_exam_slot_within_session BEFORE INSERT OR UPDATE ON exam_session_slots
 FOR EACH ROW EXECUTE FUNCTION enforce_exam_slot_within_session();

CREATE OR REPLACE FUNCTION enforce_approved_exam_requirement_immutability() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.status IN ('APPROVED','SUPERSEDED') AND ROW(NEW.academic_period_id,NEW.module_id,NEW.module_code,NEW.module_name,
   NEW.requirement_version,NEW.duration_minutes,NEW.reading_time_minutes,NEW.required_venue_type_id,NEW.special_requirements)
   IS DISTINCT FROM ROW(OLD.academic_period_id,OLD.module_id,OLD.module_code,OLD.module_name,
   OLD.requirement_version,OLD.duration_minutes,OLD.reading_time_minutes,OLD.required_venue_type_id,OLD.special_requirements) THEN
   RAISE EXCEPTION 'Approved Module exam requirement evidence is immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_approved_exam_requirement_immutable BEFORE UPDATE ON module_exam_requirements
 FOR EACH ROW EXECUTE FUNCTION enforce_approved_exam_requirement_immutability();

CREATE OR REPLACE FUNCTION validate_exam_venue_allocation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE venue_capacity integer; required_type uuid; actual_type uuid; slot_start timestamptz; slot_end timestamptz;
BEGIN
 SELECT v.examination_capacity,v.venue_type_id INTO venue_capacity,actual_type FROM exam_venues v
   WHERE v.id=NEW.venue_id AND v.active AND v.deleted_at IS NULL;
 IF venue_capacity IS NULL OR NEW.allocated_capacity > venue_capacity THEN RAISE EXCEPTION 'Exam venue allocation exceeds active venue capacity'; END IF;
 SELECT r.required_venue_type_id,e.scheduled_starts_at,e.scheduled_ends_at INTO required_type,slot_start,slot_end
   FROM exam_master_timetable_entries e JOIN module_exam_requirements r ON r.id=e.module_exam_requirement_id
   WHERE e.id=NEW.master_timetable_entry_id;
 IF required_type IS NOT NULL AND required_type <> actual_type THEN RAISE EXCEPTION 'Exam venue type does not meet Module requirement'; END IF;
 IF NOT EXISTS (SELECT 1 FROM exam_venue_availability_windows a WHERE a.venue_id=NEW.venue_id AND a.deleted_at IS NULL
      AND a.available_from <= slot_start AND a.available_until >= slot_end) THEN RAISE EXCEPTION 'Exam venue is unavailable for the scheduled window'; END IF;
 IF EXISTS (SELECT 1 FROM exam_timetable_venue_allocations a JOIN exam_master_timetable_entries other_entry
      ON other_entry.id=a.master_timetable_entry_id JOIN exam_timetable_generation_runs other_run ON other_run.id=other_entry.generation_run_id
      WHERE a.venue_id=NEW.venue_id AND a.id<>NEW.id AND a.deleted_at IS NULL
      AND other_run.status IN ('GENERATED','REVIEWED','APPROVED','PUBLISHED')
      AND tstzrange(other_entry.scheduled_starts_at,other_entry.scheduled_ends_at,'[)') && tstzrange(slot_start,slot_end,'[)')) THEN
   RAISE EXCEPTION 'Exam venue has a conflicting timetable allocation'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_validate_exam_venue_allocation BEFORE INSERT OR UPDATE ON exam_timetable_venue_allocations
 FOR EACH ROW EXECUTE FUNCTION validate_exam_venue_allocation();

CREATE OR REPLACE FUNCTION validate_exam_student_allocation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE source_student uuid; source_module uuid; entry_module uuid; entry_start timestamptz; entry_end timestamptz;
BEGIN
 SELECT r.student_id,c.module_id INTO source_student,source_module FROM exam_candidate_modules c
   JOIN exam_registration_imports r ON r.id=c.registration_import_id WHERE c.id=NEW.candidate_module_id AND c.eligibility_status='ELIGIBLE';
 SELECT module_id,scheduled_starts_at,scheduled_ends_at INTO entry_module,entry_start,entry_end
   FROM exam_master_timetable_entries WHERE id=NEW.master_timetable_entry_id;
 IF source_student IS NULL OR source_student<>NEW.student_id OR source_module<>NEW.module_id OR entry_module<>NEW.module_id
    OR entry_start<>NEW.scheduled_starts_at OR entry_end<>NEW.scheduled_ends_at THEN
   RAISE EXCEPTION 'Student exam entry does not match confirmed candidate and master timetable evidence'; END IF;
 IF EXISTS (SELECT 1 FROM exam_student_timetable_entries other_entry
      WHERE other_entry.generation_run_id=NEW.generation_run_id AND other_entry.student_id=NEW.student_id
      AND other_entry.id<>NEW.id AND other_entry.deleted_at IS NULL
      AND tstzrange(other_entry.scheduled_starts_at,other_entry.scheduled_ends_at,'[)') &&
          tstzrange(NEW.scheduled_starts_at,NEW.scheduled_ends_at,'[)')) THEN
   RAISE EXCEPTION 'Student has an exam timetable clash'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_validate_exam_student_allocation BEFORE INSERT OR UPDATE ON exam_student_timetable_entries
 FOR EACH ROW EXECUTE FUNCTION validate_exam_student_allocation();

CREATE OR REPLACE FUNCTION enforce_exam_timetable_evidence_immutability() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE run_status varchar(20);
BEGIN
 SELECT status INTO run_status FROM exam_timetable_generation_runs WHERE id=COALESCE(NEW.generation_run_id,OLD.generation_run_id);
 IF run_status <> 'GENERATED' THEN RAISE EXCEPTION 'Reviewed exam timetable evidence is immutable'; END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END $$;
CREATE TRIGGER trg_exam_master_evidence_immutable BEFORE UPDATE OR DELETE ON exam_master_timetable_entries
 FOR EACH ROW EXECUTE FUNCTION enforce_exam_timetable_evidence_immutability();
CREATE TRIGGER trg_exam_student_evidence_immutable BEFORE UPDATE OR DELETE ON exam_student_timetable_entries
 FOR EACH ROW EXECUTE FUNCTION enforce_exam_timetable_evidence_immutability();

CREATE INDEX idx_exam_inbox_processed_at ON integration_inbox(processed_at);

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO emhare_service;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO emhare_service;
