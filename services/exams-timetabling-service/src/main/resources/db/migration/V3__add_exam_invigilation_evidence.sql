-- Author: Tinashe K

CREATE TABLE exam_attendance_sessions (
    id uuid PRIMARY KEY,
    venue_allocation_id uuid NOT NULL UNIQUE REFERENCES exam_timetable_venue_allocations(id),
    status varchar(20) NOT NULL,
    expected_candidate_count integer NOT NULL,
    present_candidate_count integer NOT NULL DEFAULT 0,
    absent_candidate_count integer NOT NULL DEFAULT 0,
    excused_candidate_count integer NOT NULL DEFAULT 0,
    opened_by_user_id uuid NOT NULL,
    opened_at timestamptz NOT NULL,
    opening_reason varchar(1000) NOT NULL,
    closed_by_user_id uuid,
    closed_at timestamptz,
    closure_reason varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_exam_attendance_session_status CHECK (status IN ('OPEN','CLOSED')),
    CONSTRAINT ck_exam_attendance_session_counts CHECK (
        expected_candidate_count > 0
        AND present_candidate_count >= 0
        AND absent_candidate_count >= 0
        AND excused_candidate_count >= 0
        AND present_candidate_count + absent_candidate_count + excused_candidate_count <= expected_candidate_count
    ),
    CONSTRAINT ck_exam_attendance_session_opening CHECK (length(trim(opening_reason)) > 0),
    CONSTRAINT ck_exam_attendance_session_closure CHECK (
        (status='OPEN' AND closed_by_user_id IS NULL AND closed_at IS NULL AND closure_reason IS NULL)
        OR (status='CLOSED' AND closed_by_user_id IS NOT NULL AND closed_at IS NOT NULL
            AND length(trim(closure_reason)) > 0
            AND present_candidate_count + absent_candidate_count + excused_candidate_count = expected_candidate_count)
    )
);

CREATE TABLE exam_attendance_records (
    id uuid PRIMARY KEY,
    attendance_session_id uuid NOT NULL REFERENCES exam_attendance_sessions(id),
    student_timetable_entry_id uuid NOT NULL REFERENCES exam_student_timetable_entries(id),
    attendance_status varchar(20) NOT NULL,
    recorded_by_user_id uuid,
    recorded_at timestamptz,
    evidence_notes varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_exam_attendance_candidate UNIQUE(attendance_session_id, student_timetable_entry_id),
    CONSTRAINT ck_exam_attendance_record_status CHECK (attendance_status IN ('EXPECTED','PRESENT','ABSENT','EXCUSED')),
    CONSTRAINT ck_exam_attendance_record_evidence CHECK (
        (attendance_status='EXPECTED' AND recorded_by_user_id IS NULL AND recorded_at IS NULL AND evidence_notes IS NULL)
        OR (attendance_status='PRESENT' AND recorded_by_user_id IS NOT NULL AND recorded_at IS NOT NULL)
        OR (attendance_status IN ('ABSENT','EXCUSED') AND recorded_by_user_id IS NOT NULL AND recorded_at IS NOT NULL
            AND length(trim(evidence_notes)) > 0)
    )
);
CREATE INDEX idx_exam_attendance_records_session_status
    ON exam_attendance_records(attendance_session_id, attendance_status) WHERE deleted_at IS NULL;

CREATE TABLE exam_incident_reports (
    id uuid PRIMARY KEY,
    attendance_session_id uuid NOT NULL REFERENCES exam_attendance_sessions(id),
    student_timetable_entry_id uuid REFERENCES exam_student_timetable_entries(id),
    incident_number varchar(70) NOT NULL UNIQUE,
    incident_type varchar(30) NOT NULL,
    severity varchar(20) NOT NULL,
    description varchar(2000) NOT NULL,
    occurred_at timestamptz NOT NULL,
    status varchar(20) NOT NULL,
    reported_by_user_id uuid NOT NULL,
    reported_at timestamptz NOT NULL,
    reviewed_by_user_id uuid,
    reviewed_at timestamptz,
    review_reason varchar(1000),
    resolved_by_user_id uuid,
    resolved_at timestamptz,
    resolution varchar(2000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_exam_incident_type CHECK (incident_type IN (
        'LATE_ARRIVAL','SUSPECTED_MISCONDUCT','MEDICAL','EVACUATION','DISRUPTION','OTHER')),
    CONSTRAINT ck_exam_incident_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_exam_incident_status CHECK (status IN ('REPORTED','REVIEWED','RESOLVED')),
    CONSTRAINT ck_exam_incident_description CHECK (length(trim(description)) > 0),
    CONSTRAINT ck_exam_incident_workflow_evidence CHECK (
        (status='REPORTED' AND reviewed_by_user_id IS NULL AND reviewed_at IS NULL AND review_reason IS NULL
            AND resolved_by_user_id IS NULL AND resolved_at IS NULL AND resolution IS NULL)
        OR (status='REVIEWED' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL
            AND length(trim(review_reason)) > 0 AND resolved_by_user_id IS NULL AND resolved_at IS NULL AND resolution IS NULL)
        OR (status='RESOLVED' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL
            AND length(trim(review_reason)) > 0 AND resolved_by_user_id IS NOT NULL AND resolved_at IS NOT NULL
            AND length(trim(resolution)) > 0)
    ),
    CONSTRAINT ck_exam_incident_actor_separation CHECK (
        (reviewed_by_user_id IS NULL OR reviewed_by_user_id <> reported_by_user_id)
        AND (resolved_by_user_id IS NULL OR (resolved_by_user_id <> reported_by_user_id
            AND resolved_by_user_id IS DISTINCT FROM reviewed_by_user_id))
    )
);
CREATE INDEX idx_exam_incident_session_status
    ON exam_incident_reports(attendance_session_id, status) WHERE deleted_at IS NULL;

CREATE TABLE exam_attendance_sessions_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint,
    venue_allocation_id uuid, status varchar(20), expected_candidate_count integer,
    present_candidate_count integer, absent_candidate_count integer, excused_candidate_count integer,
    opened_by_user_id uuid, opened_at timestamptz, opening_reason varchar(1000), closed_by_user_id uuid,
    closed_at timestamptz, closure_reason varchar(1000), created_at timestamptz, updated_at timestamptz,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY(id,rev)
);
CREATE TABLE exam_attendance_records_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint,
    attendance_session_id uuid, student_timetable_entry_id uuid, attendance_status varchar(20),
    recorded_by_user_id uuid, recorded_at timestamptz, evidence_notes varchar(1000), created_at timestamptz,
    updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev)
);
CREATE TABLE exam_incident_reports_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint,
    attendance_session_id uuid, student_timetable_entry_id uuid, incident_number varchar(70), incident_type varchar(30),
    severity varchar(20), description varchar(2000), occurred_at timestamptz, status varchar(20),
    reported_by_user_id uuid, reported_at timestamptz, reviewed_by_user_id uuid, reviewed_at timestamptz,
    review_reason varchar(1000), resolved_by_user_id uuid, resolved_at timestamptz, resolution varchar(2000),
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev)
);

CREATE OR REPLACE FUNCTION validate_exam_attendance_session() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE published_status varchar(20); roster_count integer; expected_count integer;
DECLARE present_count integer; absent_count integer; excused_count integer; total_count integer;
BEGIN
    IF TG_OP='INSERT' THEN
        SELECT run.status, count(student_entry.id)::integer
          INTO published_status, roster_count
          FROM exam_timetable_venue_allocations allocation
          JOIN exam_master_timetable_entries master_entry ON master_entry.id=allocation.master_timetable_entry_id
          JOIN exam_timetable_generation_runs run ON run.id=master_entry.generation_run_id
          LEFT JOIN exam_student_timetable_entries student_entry
            ON student_entry.venue_allocation_id=allocation.id AND student_entry.deleted_at IS NULL
         WHERE allocation.id=NEW.venue_allocation_id
         GROUP BY run.status;
        IF published_status IS DISTINCT FROM 'PUBLISHED' THEN
            RAISE EXCEPTION 'Attendance can only be opened for a published exam timetable allocation';
        END IF;
        IF roster_count IS NULL OR roster_count=0 OR NEW.expected_candidate_count<>roster_count THEN
            RAISE EXCEPTION 'Attendance expected count must match the complete published venue roster';
        END IF;
    ELSIF OLD.status='CLOSED' THEN
        RAISE EXCEPTION 'Closed exam attendance evidence is immutable';
    ELSIF NEW.status='CLOSED' THEN
        SELECT count(*) FILTER (WHERE attendance_status='EXPECTED')::integer,
               count(*) FILTER (WHERE attendance_status='PRESENT')::integer,
               count(*) FILTER (WHERE attendance_status='ABSENT')::integer,
               count(*) FILTER (WHERE attendance_status='EXCUSED')::integer,
               count(*)::integer
          INTO expected_count,present_count,absent_count,excused_count,total_count
          FROM exam_attendance_records
         WHERE attendance_session_id=NEW.id AND deleted_at IS NULL;
        IF expected_count<>0 OR total_count<>NEW.expected_candidate_count THEN
            RAISE EXCEPTION 'Every expected candidate must have a recorded attendance outcome before closure';
        END IF;
        NEW.present_candidate_count=present_count;
        NEW.absent_candidate_count=absent_count;
        NEW.excused_candidate_count=excused_count;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_validate_exam_attendance_session BEFORE INSERT OR UPDATE ON exam_attendance_sessions
    FOR EACH ROW EXECUTE FUNCTION validate_exam_attendance_session();

CREATE OR REPLACE FUNCTION validate_exam_attendance_record() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE session_status varchar(20); session_allocation_id uuid; entry_allocation_id uuid;
BEGIN
    SELECT status,venue_allocation_id INTO session_status,session_allocation_id
      FROM exam_attendance_sessions WHERE id=COALESCE(NEW.attendance_session_id,OLD.attendance_session_id);
    IF session_status<>'OPEN' THEN RAISE EXCEPTION 'Attendance records are immutable after session closure'; END IF;
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Exam attendance evidence cannot be deleted'; END IF;
    SELECT venue_allocation_id INTO entry_allocation_id FROM exam_student_timetable_entries
      WHERE id=NEW.student_timetable_entry_id AND deleted_at IS NULL;
    IF entry_allocation_id IS NULL OR entry_allocation_id<>session_allocation_id THEN
        RAISE EXCEPTION 'Attendance candidate must belong to the exact published venue allocation';
    END IF;
    IF TG_OP='UPDATE' AND ROW(NEW.attendance_session_id,NEW.student_timetable_entry_id)
       IS DISTINCT FROM ROW(OLD.attendance_session_id,OLD.student_timetable_entry_id) THEN
        RAISE EXCEPTION 'Attendance source roster evidence is immutable';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_validate_exam_attendance_record BEFORE INSERT OR UPDATE OR DELETE ON exam_attendance_records
    FOR EACH ROW EXECUTE FUNCTION validate_exam_attendance_record();

CREATE OR REPLACE FUNCTION validate_exam_incident_report() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE session_allocation_id uuid; entry_allocation_id uuid;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Exam incident evidence cannot be deleted'; END IF;
    SELECT venue_allocation_id INTO session_allocation_id FROM exam_attendance_sessions WHERE id=NEW.attendance_session_id;
    IF session_allocation_id IS NULL THEN RAISE EXCEPTION 'Exam attendance session was not found for incident report'; END IF;
    IF NEW.student_timetable_entry_id IS NOT NULL THEN
        SELECT venue_allocation_id INTO entry_allocation_id FROM exam_student_timetable_entries
          WHERE id=NEW.student_timetable_entry_id AND deleted_at IS NULL;
        IF entry_allocation_id IS NULL OR entry_allocation_id<>session_allocation_id THEN
            RAISE EXCEPTION 'Incident candidate must belong to the exact attendance venue allocation';
        END IF;
    END IF;
    IF TG_OP='UPDATE' THEN
        IF ROW(NEW.attendance_session_id,NEW.student_timetable_entry_id,NEW.incident_number,NEW.incident_type,
               NEW.severity,NEW.description,NEW.occurred_at,NEW.reported_by_user_id,NEW.reported_at)
           IS DISTINCT FROM ROW(OLD.attendance_session_id,OLD.student_timetable_entry_id,OLD.incident_number,OLD.incident_type,
               OLD.severity,OLD.description,OLD.occurred_at,OLD.reported_by_user_id,OLD.reported_at) THEN
            RAISE EXCEPTION 'Original exam incident report evidence is immutable';
        END IF;
        IF OLD.status='RESOLVED' THEN RAISE EXCEPTION 'Resolved exam incident evidence is immutable'; END IF;
        IF NOT ((OLD.status='REPORTED' AND NEW.status='REVIEWED') OR (OLD.status='REVIEWED' AND NEW.status='RESOLVED')) THEN
            RAISE EXCEPTION 'Exam incident workflow must advance from REPORTED to REVIEWED to RESOLVED';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_validate_exam_incident_report BEFORE INSERT OR UPDATE OR DELETE ON exam_incident_reports
    FOR EACH ROW EXECUTE FUNCTION validate_exam_incident_report();

GRANT SELECT, INSERT, UPDATE, DELETE ON exam_attendance_sessions,exam_attendance_records,exam_incident_reports TO emhare_service;
