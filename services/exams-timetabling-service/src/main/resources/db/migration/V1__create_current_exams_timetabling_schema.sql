-- Author: Tinashe K
-- Canonical clean-slate baseline for exams-timetabling-service.

--
--


-- Dumped from database version 18.4 (Debian 18.4-1.pgdg13+1)
-- Dumped by pg_dump version 18.4 (Debian 18.4-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: enforce_approved_exam_requirement_immutability(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_approved_exam_requirement_immutability() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
 IF OLD.status IN ('APPROVED','SUPERSEDED') AND ROW(NEW.academic_period_id,NEW.module_id,NEW.module_code,NEW.module_name,
   NEW.requirement_version,NEW.duration_minutes,NEW.reading_time_minutes,NEW.required_venue_type_id,NEW.special_requirements)
   IS DISTINCT FROM ROW(OLD.academic_period_id,OLD.module_id,OLD.module_code,OLD.module_name,
   OLD.requirement_version,OLD.duration_minutes,OLD.reading_time_minutes,OLD.required_venue_type_id,OLD.special_requirements) THEN
   RAISE EXCEPTION 'Approved Module exam requirement evidence is immutable'; END IF;
 RETURN NEW;
END $$;


--
-- Name: enforce_exam_candidate_snapshot_immutability(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_exam_candidate_snapshot_immutability() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
 IF ROW(NEW.registration_import_id,NEW.registration_module_id,NEW.curriculum_module_id,NEW.module_id,NEW.module_code,NEW.module_name)
   IS DISTINCT FROM ROW(OLD.registration_import_id,OLD.registration_module_id,OLD.curriculum_module_id,OLD.module_id,OLD.module_code,OLD.module_name) THEN
   RAISE EXCEPTION 'Exam candidate Module source snapshot is immutable'; END IF;
 RETURN NEW;
END $$;


--
-- Name: enforce_exam_slot_within_session(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_exam_slot_within_session() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE session_start date; session_end date; session_status varchar(20);
BEGIN
 SELECT starts_on,ends_on,status INTO session_start,session_end,session_status FROM exam_sessions WHERE id=NEW.exam_session_id;
 IF NEW.starts_at::date < session_start OR NEW.ends_at::date > session_end THEN
   RAISE EXCEPTION 'Exam slot must be within the exam session date range'; END IF;
 IF TG_OP='INSERT' AND session_status <> 'DRAFT' THEN RAISE EXCEPTION 'Slots can only be added to a draft exam session'; END IF;
 RETURN NEW;
END $$;


--
-- Name: enforce_exam_source_snapshot_immutability(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_exam_source_snapshot_immutability() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


--
-- Name: enforce_exam_timetable_evidence_immutability(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_exam_timetable_evidence_immutability() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE run_status varchar(20);
BEGIN
 SELECT status INTO run_status FROM exam_timetable_generation_runs WHERE id=COALESCE(NEW.generation_run_id,OLD.generation_run_id);
 IF run_status <> 'GENERATED' THEN RAISE EXCEPTION 'Reviewed exam timetable evidence is immutable'; END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END $$;


--
-- Name: validate_exam_attendance_record(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_exam_attendance_record() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


--
-- Name: validate_exam_attendance_session(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_exam_attendance_session() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


--
-- Name: validate_exam_incident_report(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_exam_incident_report() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


--
-- Name: validate_exam_student_allocation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_exam_student_allocation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


--
-- Name: validate_exam_venue_allocation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_exam_venue_allocation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: exam_attendance_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_attendance_records (
    id uuid NOT NULL,
    attendance_session_id uuid NOT NULL,
    student_timetable_entry_id uuid NOT NULL,
    attendance_status character varying(20) NOT NULL,
    recorded_by_user_id uuid,
    recorded_at timestamp with time zone,
    evidence_notes character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_exam_attendance_record_evidence CHECK (((((attendance_status)::text = 'EXPECTED'::text) AND (recorded_by_user_id IS NULL) AND (recorded_at IS NULL) AND (evidence_notes IS NULL)) OR (((attendance_status)::text = 'PRESENT'::text) AND (recorded_by_user_id IS NOT NULL) AND (recorded_at IS NOT NULL)) OR (((attendance_status)::text = ANY ((ARRAY['ABSENT'::character varying, 'EXCUSED'::character varying])::text[])) AND (recorded_by_user_id IS NOT NULL) AND (recorded_at IS NOT NULL) AND (length(TRIM(BOTH FROM evidence_notes)) > 0)))),
    CONSTRAINT ck_exam_attendance_record_status CHECK (((attendance_status)::text = ANY ((ARRAY['EXPECTED'::character varying, 'PRESENT'::character varying, 'ABSENT'::character varying, 'EXCUSED'::character varying])::text[])))
);


--
-- Name: exam_attendance_records_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_attendance_records_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    attendance_session_id uuid,
    student_timetable_entry_id uuid,
    attendance_status character varying(20),
    recorded_by_user_id uuid,
    recorded_at timestamp with time zone,
    evidence_notes character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: exam_attendance_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_attendance_sessions (
    id uuid NOT NULL,
    venue_allocation_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    expected_candidate_count integer NOT NULL,
    present_candidate_count integer DEFAULT 0 NOT NULL,
    absent_candidate_count integer DEFAULT 0 NOT NULL,
    excused_candidate_count integer DEFAULT 0 NOT NULL,
    opened_by_user_id uuid NOT NULL,
    opened_at timestamp with time zone NOT NULL,
    opening_reason character varying(1000) NOT NULL,
    closed_by_user_id uuid,
    closed_at timestamp with time zone,
    closure_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_exam_attendance_session_closure CHECK (((((status)::text = 'OPEN'::text) AND (closed_by_user_id IS NULL) AND (closed_at IS NULL) AND (closure_reason IS NULL)) OR (((status)::text = 'CLOSED'::text) AND (closed_by_user_id IS NOT NULL) AND (closed_at IS NOT NULL) AND (length(TRIM(BOTH FROM closure_reason)) > 0) AND (((present_candidate_count + absent_candidate_count) + excused_candidate_count) = expected_candidate_count)))),
    CONSTRAINT ck_exam_attendance_session_counts CHECK (((expected_candidate_count > 0) AND (present_candidate_count >= 0) AND (absent_candidate_count >= 0) AND (excused_candidate_count >= 0) AND (((present_candidate_count + absent_candidate_count) + excused_candidate_count) <= expected_candidate_count))),
    CONSTRAINT ck_exam_attendance_session_opening CHECK ((length(TRIM(BOTH FROM opening_reason)) > 0)),
    CONSTRAINT ck_exam_attendance_session_status CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'CLOSED'::character varying])::text[])))
);


--
-- Name: exam_attendance_sessions_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_attendance_sessions_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    venue_allocation_id uuid,
    status character varying(20),
    expected_candidate_count integer,
    present_candidate_count integer,
    absent_candidate_count integer,
    excused_candidate_count integer,
    opened_by_user_id uuid,
    opened_at timestamp with time zone,
    opening_reason character varying(1000),
    closed_by_user_id uuid,
    closed_at timestamp with time zone,
    closure_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: exam_candidate_modules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_candidate_modules (
    id uuid NOT NULL,
    registration_import_id uuid NOT NULL,
    registration_module_id uuid NOT NULL,
    curriculum_module_id uuid NOT NULL,
    module_id uuid NOT NULL,
    module_code character varying(50) NOT NULL,
    module_name character varying(200) NOT NULL,
    eligibility_status character varying(20) DEFAULT 'ELIGIBLE'::character varying NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_exam_candidate_eligibility CHECK (((eligibility_status)::text = ANY ((ARRAY['ELIGIBLE'::character varying, 'WITHDRAWN'::character varying])::text[])))
);


--
-- Name: exam_candidate_modules_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_candidate_modules_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    registration_import_id uuid,
    registration_module_id uuid,
    curriculum_module_id uuid,
    module_id uuid,
    module_code character varying(50),
    module_name character varying(200),
    eligibility_status character varying(20),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: exam_incident_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_incident_reports (
    id uuid NOT NULL,
    attendance_session_id uuid NOT NULL,
    student_timetable_entry_id uuid,
    incident_number character varying(70) NOT NULL,
    incident_type character varying(30) NOT NULL,
    severity character varying(20) NOT NULL,
    description character varying(2000) NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    status character varying(20) NOT NULL,
    reported_by_user_id uuid NOT NULL,
    reported_at timestamp with time zone NOT NULL,
    reviewed_by_user_id uuid,
    reviewed_at timestamp with time zone,
    review_reason character varying(1000),
    resolved_by_user_id uuid,
    resolved_at timestamp with time zone,
    resolution character varying(2000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_exam_incident_actor_separation CHECK ((((reviewed_by_user_id IS NULL) OR (reviewed_by_user_id <> reported_by_user_id)) AND ((resolved_by_user_id IS NULL) OR ((resolved_by_user_id <> reported_by_user_id) AND (resolved_by_user_id IS DISTINCT FROM reviewed_by_user_id))))),
    CONSTRAINT ck_exam_incident_description CHECK ((length(TRIM(BOTH FROM description)) > 0)),
    CONSTRAINT ck_exam_incident_severity CHECK (((severity)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying])::text[]))),
    CONSTRAINT ck_exam_incident_status CHECK (((status)::text = ANY ((ARRAY['REPORTED'::character varying, 'REVIEWED'::character varying, 'RESOLVED'::character varying])::text[]))),
    CONSTRAINT ck_exam_incident_type CHECK (((incident_type)::text = ANY ((ARRAY['LATE_ARRIVAL'::character varying, 'SUSPECTED_MISCONDUCT'::character varying, 'MEDICAL'::character varying, 'EVACUATION'::character varying, 'DISRUPTION'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT ck_exam_incident_workflow_evidence CHECK (((((status)::text = 'REPORTED'::text) AND (reviewed_by_user_id IS NULL) AND (reviewed_at IS NULL) AND (review_reason IS NULL) AND (resolved_by_user_id IS NULL) AND (resolved_at IS NULL) AND (resolution IS NULL)) OR (((status)::text = 'REVIEWED'::text) AND (reviewed_by_user_id IS NOT NULL) AND (reviewed_at IS NOT NULL) AND (length(TRIM(BOTH FROM review_reason)) > 0) AND (resolved_by_user_id IS NULL) AND (resolved_at IS NULL) AND (resolution IS NULL)) OR (((status)::text = 'RESOLVED'::text) AND (reviewed_by_user_id IS NOT NULL) AND (reviewed_at IS NOT NULL) AND (length(TRIM(BOTH FROM review_reason)) > 0) AND (resolved_by_user_id IS NOT NULL) AND (resolved_at IS NOT NULL) AND (length(TRIM(BOTH FROM resolution)) > 0))))
);


--
-- Name: exam_incident_reports_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_incident_reports_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    attendance_session_id uuid,
    student_timetable_entry_id uuid,
    incident_number character varying(70),
    incident_type character varying(30),
    severity character varying(20),
    description character varying(2000),
    occurred_at timestamp with time zone,
    status character varying(20),
    reported_by_user_id uuid,
    reported_at timestamp with time zone,
    reviewed_by_user_id uuid,
    reviewed_at timestamp with time zone,
    review_reason character varying(1000),
    resolved_by_user_id uuid,
    resolved_at timestamp with time zone,
    resolution character varying(2000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: exam_master_timetable_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_master_timetable_entries (
    id uuid NOT NULL,
    generation_run_id uuid NOT NULL,
    exam_session_slot_id uuid NOT NULL,
    module_exam_requirement_id uuid CONSTRAINT exam_master_timetable_entri_module_exam_requirement_id_not_null NOT NULL,
    module_id uuid NOT NULL,
    module_code character varying(50) NOT NULL,
    module_name character varying(200) NOT NULL,
    candidate_count integer NOT NULL,
    scheduled_starts_at timestamp with time zone NOT NULL,
    scheduled_ends_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_exam_master_candidates CHECK ((candidate_count > 0)),
    CONSTRAINT ck_exam_master_window CHECK ((scheduled_ends_at > scheduled_starts_at))
);


--
-- Name: exam_master_timetable_entries_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_master_timetable_entries_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    generation_run_id uuid,
    exam_session_slot_id uuid,
    module_exam_requirement_id uuid,
    module_id uuid,
    module_code character varying(50),
    module_name character varying(200),
    candidate_count integer,
    scheduled_starts_at timestamp with time zone,
    scheduled_ends_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: exam_registration_imports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_registration_imports (
    id uuid NOT NULL,
    source_event_id uuid NOT NULL,
    registration_session_id uuid NOT NULL,
    student_id uuid NOT NULL,
    student_number character varying(40) NOT NULL,
    programme_enrolment_id uuid NOT NULL,
    programme_id uuid NOT NULL,
    programme_version_id uuid NOT NULL,
    academic_period_id uuid NOT NULL,
    academic_period_code character varying(50) NOT NULL,
    academic_period_name character varying(150) NOT NULL,
    academic_period_starts_on date NOT NULL,
    academic_period_ends_on date NOT NULL,
    imported_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_exam_registration_period_dates CHECK ((academic_period_ends_on >= academic_period_starts_on))
);


--
-- Name: exam_registration_imports_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_registration_imports_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    source_event_id uuid,
    registration_session_id uuid,
    student_id uuid,
    student_number character varying(40),
    programme_enrolment_id uuid,
    programme_id uuid,
    programme_version_id uuid,
    academic_period_id uuid,
    academic_period_code character varying(50),
    academic_period_name character varying(150),
    academic_period_starts_on date,
    academic_period_ends_on date,
    imported_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: exam_session_slots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_session_slots (
    id uuid NOT NULL,
    exam_session_id uuid NOT NULL,
    code character varying(40) NOT NULL,
    starts_at timestamp with time zone NOT NULL,
    ends_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_exam_session_slot_window CHECK ((ends_at > starts_at))
);


--
-- Name: exam_session_slots_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_session_slots_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    exam_session_id uuid,
    code character varying(40),
    starts_at timestamp with time zone,
    ends_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: exam_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_sessions (
    id uuid NOT NULL,
    academic_period_id uuid NOT NULL,
    academic_period_code character varying(50) NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(150) NOT NULL,
    assessment_type character varying(30) NOT NULL,
    starts_on date NOT NULL,
    ends_on date NOT NULL,
    status character varying(20) NOT NULL,
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    approval_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_exam_session_approval CHECK (((((status)::text = 'DRAFT'::text) AND (approved_by_user_id IS NULL) AND (approved_at IS NULL) AND (approval_reason IS NULL)) OR (((status)::text = ANY ((ARRAY['APPROVED'::character varying, 'CLOSED'::character varying])::text[])) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (length(TRIM(BOTH FROM approval_reason)) > 0)))),
    CONSTRAINT ck_exam_session_dates CHECK ((ends_on >= starts_on)),
    CONSTRAINT ck_exam_session_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'APPROVED'::character varying, 'CLOSED'::character varying])::text[]))),
    CONSTRAINT ck_exam_session_type CHECK (((assessment_type)::text = ANY ((ARRAY['FINAL_EXAM'::character varying, 'SUPPLEMENTARY'::character varying, 'DEFERRED'::character varying, 'SPECIAL'::character varying])::text[])))
);


--
-- Name: exam_sessions_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_sessions_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    academic_period_id uuid,
    academic_period_code character varying(50),
    code character varying(40),
    name character varying(150),
    assessment_type character varying(30),
    starts_on date,
    ends_on date,
    status character varying(20),
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    approval_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: exam_student_timetable_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_student_timetable_entries (
    id uuid NOT NULL,
    generation_run_id uuid NOT NULL,
    master_timetable_entry_id uuid CONSTRAINT exam_student_timetable_entri_master_timetable_entry_id_not_null NOT NULL,
    venue_allocation_id uuid NOT NULL,
    registration_import_id uuid NOT NULL,
    candidate_module_id uuid NOT NULL,
    student_id uuid NOT NULL,
    student_number character varying(40) NOT NULL,
    module_id uuid NOT NULL,
    module_code character varying(50) NOT NULL,
    scheduled_starts_at timestamp with time zone NOT NULL,
    scheduled_ends_at timestamp with time zone NOT NULL,
    seat_number integer NOT NULL,
    attendance_status character varying(20) DEFAULT 'EXPECTED'::character varying NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_exam_attendance_status CHECK (((attendance_status)::text = ANY ((ARRAY['EXPECTED'::character varying, 'PRESENT'::character varying, 'ABSENT'::character varying, 'EXCUSED'::character varying])::text[]))),
    CONSTRAINT ck_exam_student_seat CHECK ((seat_number > 0)),
    CONSTRAINT ck_exam_student_window CHECK ((scheduled_ends_at > scheduled_starts_at))
);


--
-- Name: exam_student_timetable_entries_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_student_timetable_entries_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    generation_run_id uuid,
    master_timetable_entry_id uuid,
    venue_allocation_id uuid,
    registration_import_id uuid,
    candidate_module_id uuid,
    student_id uuid,
    student_number character varying(40),
    module_id uuid,
    module_code character varying(50),
    scheduled_starts_at timestamp with time zone,
    scheduled_ends_at timestamp with time zone,
    seat_number integer,
    attendance_status character varying(20),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: exam_timetable_generation_runs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_timetable_generation_runs (
    id uuid NOT NULL,
    exam_session_id uuid NOT NULL,
    run_number character varying(60) NOT NULL,
    status character varying(20) NOT NULL,
    candidate_count integer NOT NULL,
    module_count integer NOT NULL,
    timetable_entry_count integer NOT NULL,
    conflict_count integer NOT NULL,
    generation_policy jsonb NOT NULL,
    generated_by_user_id uuid NOT NULL,
    generated_at timestamp with time zone NOT NULL,
    reviewed_by_user_id uuid,
    reviewed_at timestamp with time zone,
    review_reason character varying(1000),
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    approval_reason character varying(1000),
    published_by_user_id uuid,
    published_at timestamp with time zone,
    publication_reason character varying(1000),
    rejected_by_user_id uuid,
    rejected_at timestamp with time zone,
    rejection_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_exam_timetable_actor_separation CHECK ((((reviewed_by_user_id IS NULL) OR (reviewed_by_user_id <> generated_by_user_id)) AND ((approved_by_user_id IS NULL) OR ((approved_by_user_id <> generated_by_user_id) AND (approved_by_user_id IS DISTINCT FROM reviewed_by_user_id))) AND ((published_by_user_id IS NULL) OR ((published_by_user_id <> generated_by_user_id) AND (published_by_user_id IS DISTINCT FROM reviewed_by_user_id) AND (published_by_user_id IS DISTINCT FROM approved_by_user_id))))),
    CONSTRAINT ck_exam_timetable_run_approval CHECK (((((status)::text = ANY ((ARRAY['GENERATED'::character varying, 'REVIEWED'::character varying])::text[])) AND (approved_by_user_id IS NULL)) OR ((status)::text = ANY ((ARRAY['APPROVED'::character varying, 'PUBLISHED'::character varying, 'REJECTED'::character varying])::text[])))),
    CONSTRAINT ck_exam_timetable_run_counts CHECK (((candidate_count >= 0) AND (module_count > 0) AND (timetable_entry_count >= 0) AND (conflict_count >= 0))),
    CONSTRAINT ck_exam_timetable_run_publication CHECK (((((status)::text <> 'PUBLISHED'::text) AND (published_by_user_id IS NULL)) OR (((status)::text = 'PUBLISHED'::text) AND (published_by_user_id IS NOT NULL) AND (published_at IS NOT NULL) AND (length(TRIM(BOTH FROM publication_reason)) > 0)))),
    CONSTRAINT ck_exam_timetable_run_review CHECK (((((status)::text = 'GENERATED'::text) AND (reviewed_by_user_id IS NULL)) OR ((status)::text = ANY ((ARRAY['REVIEWED'::character varying, 'APPROVED'::character varying, 'PUBLISHED'::character varying, 'REJECTED'::character varying])::text[])))),
    CONSTRAINT ck_exam_timetable_run_status CHECK (((status)::text = ANY ((ARRAY['GENERATED'::character varying, 'REVIEWED'::character varying, 'APPROVED'::character varying, 'PUBLISHED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: exam_timetable_generation_runs_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_timetable_generation_runs_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    exam_session_id uuid,
    run_number character varying(60),
    status character varying(20),
    candidate_count integer,
    module_count integer,
    timetable_entry_count integer,
    conflict_count integer,
    generation_policy jsonb,
    generated_by_user_id uuid,
    generated_at timestamp with time zone,
    reviewed_by_user_id uuid,
    reviewed_at timestamp with time zone,
    review_reason character varying(1000),
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    approval_reason character varying(1000),
    published_by_user_id uuid,
    published_at timestamp with time zone,
    publication_reason character varying(1000),
    rejected_by_user_id uuid,
    rejected_at timestamp with time zone,
    rejection_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: exam_timetable_run_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_timetable_run_events (
    id uuid NOT NULL,
    generation_run_id uuid NOT NULL,
    previous_status character varying(20),
    new_status character varying(20) NOT NULL,
    reason character varying(1000) NOT NULL,
    actor_user_id uuid NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: exam_timetable_run_events_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_timetable_run_events_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    generation_run_id uuid,
    previous_status character varying(20),
    new_status character varying(20),
    reason character varying(1000),
    actor_user_id uuid,
    occurred_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: exam_timetable_venue_allocations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_timetable_venue_allocations (
    id uuid NOT NULL,
    master_timetable_entry_id uuid CONSTRAINT exam_timetable_venue_allocat_master_timetable_entry_id_not_null NOT NULL,
    venue_id uuid NOT NULL,
    allocated_capacity integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_exam_allocation_capacity CHECK ((allocated_capacity > 0))
);


--
-- Name: exam_timetable_venue_allocations_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_timetable_venue_allocations_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    master_timetable_entry_id uuid,
    venue_id uuid,
    allocated_capacity integer,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: exam_venue_availability_windows; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_venue_availability_windows (
    id uuid NOT NULL,
    venue_id uuid NOT NULL,
    available_from timestamp with time zone NOT NULL,
    available_until timestamp with time zone NOT NULL,
    notes character varying(500),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_exam_venue_availability_window CHECK ((available_until > available_from))
);


--
-- Name: exam_venue_availability_windows_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_venue_availability_windows_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    venue_id uuid,
    available_from timestamp with time zone,
    available_until timestamp with time zone,
    notes character varying(500),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: exam_venue_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_venue_types (
    id uuid NOT NULL,
    code character varying(30) NOT NULL,
    name character varying(120) NOT NULL,
    description character varying(500),
    active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: exam_venue_types_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_venue_types_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code character varying(30),
    name character varying(120),
    description character varying(500),
    active boolean,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: exam_venues; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_venues (
    id uuid NOT NULL,
    venue_type_id uuid NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(150) NOT NULL,
    campus_name character varying(150) NOT NULL,
    building_name character varying(150),
    room_name character varying(100),
    examination_capacity integer NOT NULL,
    accessibility_notes character varying(500),
    active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_exam_venue_capacity CHECK ((examination_capacity > 0))
);


--
-- Name: exam_venues_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_venues_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    venue_type_id uuid,
    code character varying(40),
    name character varying(150),
    campus_name character varying(150),
    building_name character varying(150),
    room_name character varying(100),
    examination_capacity integer,
    accessibility_notes character varying(500),
    active boolean,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: integration_inbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.integration_inbox (
    event_id uuid NOT NULL,
    event_type character varying(160) NOT NULL,
    source_service character varying(100) NOT NULL,
    payload jsonb NOT NULL,
    received_at timestamp with time zone NOT NULL,
    processed_at timestamp with time zone
);


--
-- Name: module_exam_requirements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.module_exam_requirements (
    id uuid NOT NULL,
    academic_period_id uuid NOT NULL,
    module_id uuid NOT NULL,
    module_code character varying(50) NOT NULL,
    module_name character varying(200) NOT NULL,
    requirement_version integer NOT NULL,
    duration_minutes integer NOT NULL,
    reading_time_minutes integer DEFAULT 0 NOT NULL,
    required_venue_type_id uuid,
    special_requirements character varying(1000),
    status character varying(20) NOT NULL,
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    approval_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_module_exam_duration CHECK (((duration_minutes >= 15) AND (duration_minutes <= 480))),
    CONSTRAINT ck_module_exam_reading_time CHECK (((reading_time_minutes >= 0) AND (reading_time_minutes <= 120))),
    CONSTRAINT ck_module_exam_requirement_approval CHECK (((((status)::text = 'DRAFT'::text) AND (approved_by_user_id IS NULL) AND (approved_at IS NULL) AND (approval_reason IS NULL)) OR (((status)::text = ANY ((ARRAY['APPROVED'::character varying, 'SUPERSEDED'::character varying])::text[])) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (length(TRIM(BOTH FROM approval_reason)) > 0)))),
    CONSTRAINT ck_module_exam_requirement_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'APPROVED'::character varying, 'SUPERSEDED'::character varying])::text[]))),
    CONSTRAINT ck_module_exam_requirement_version CHECK ((requirement_version > 0))
);


--
-- Name: module_exam_requirements_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.module_exam_requirements_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    academic_period_id uuid,
    module_id uuid,
    module_code character varying(50),
    module_name character varying(200),
    requirement_version integer,
    duration_minutes integer,
    reading_time_minutes integer,
    required_venue_type_id uuid,
    special_requirements character varying(1000),
    status character varying(20),
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    approval_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: revinfo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.revinfo (
    rev integer NOT NULL,
    revtstmp bigint NOT NULL,
    actor_user_id uuid,
    service_name character varying(100) DEFAULT 'exams-timetabling-service'::character varying NOT NULL,
    correlation_id character varying(100),
    reason character varying(500)
);


--
-- Name: revinfo_rev_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.revinfo ALTER COLUMN rev ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.revinfo_rev_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Data for Name: exam_attendance_records; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_attendance_records_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_attendance_sessions; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_attendance_sessions_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_candidate_modules; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_candidate_modules_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_incident_reports; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_incident_reports_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_master_timetable_entries; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_master_timetable_entries_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_registration_imports; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_registration_imports_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_session_slots; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_session_slots_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_sessions; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_sessions_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_student_timetable_entries; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_student_timetable_entries_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_timetable_generation_runs; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_timetable_generation_runs_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_timetable_run_events; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_timetable_run_events_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_timetable_venue_allocations; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_timetable_venue_allocations_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_venue_availability_windows; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_venue_availability_windows_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_venue_types; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_venue_types_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_venues; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_venues_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: integration_inbox; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: module_exam_requirements; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: module_exam_requirements_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: revinfo; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Name: revinfo_rev_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.revinfo_rev_seq', 1, false);


--
-- Name: exam_attendance_records_aud exam_attendance_records_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_attendance_records_aud
    ADD CONSTRAINT exam_attendance_records_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: exam_attendance_records exam_attendance_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_attendance_records
    ADD CONSTRAINT exam_attendance_records_pkey PRIMARY KEY (id);


--
-- Name: exam_attendance_sessions_aud exam_attendance_sessions_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_attendance_sessions_aud
    ADD CONSTRAINT exam_attendance_sessions_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: exam_attendance_sessions exam_attendance_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_attendance_sessions
    ADD CONSTRAINT exam_attendance_sessions_pkey PRIMARY KEY (id);


--
-- Name: exam_attendance_sessions exam_attendance_sessions_venue_allocation_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_attendance_sessions
    ADD CONSTRAINT exam_attendance_sessions_venue_allocation_id_key UNIQUE (venue_allocation_id);


--
-- Name: exam_candidate_modules_aud exam_candidate_modules_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_candidate_modules_aud
    ADD CONSTRAINT exam_candidate_modules_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: exam_candidate_modules exam_candidate_modules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_candidate_modules
    ADD CONSTRAINT exam_candidate_modules_pkey PRIMARY KEY (id);


--
-- Name: exam_candidate_modules exam_candidate_modules_registration_module_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_candidate_modules
    ADD CONSTRAINT exam_candidate_modules_registration_module_id_key UNIQUE (registration_module_id);


--
-- Name: exam_incident_reports_aud exam_incident_reports_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_incident_reports_aud
    ADD CONSTRAINT exam_incident_reports_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: exam_incident_reports exam_incident_reports_incident_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_incident_reports
    ADD CONSTRAINT exam_incident_reports_incident_number_key UNIQUE (incident_number);


--
-- Name: exam_incident_reports exam_incident_reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_incident_reports
    ADD CONSTRAINT exam_incident_reports_pkey PRIMARY KEY (id);


--
-- Name: exam_master_timetable_entries_aud exam_master_timetable_entries_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_master_timetable_entries_aud
    ADD CONSTRAINT exam_master_timetable_entries_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: exam_master_timetable_entries exam_master_timetable_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_master_timetable_entries
    ADD CONSTRAINT exam_master_timetable_entries_pkey PRIMARY KEY (id);


--
-- Name: exam_registration_imports_aud exam_registration_imports_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_registration_imports_aud
    ADD CONSTRAINT exam_registration_imports_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: exam_registration_imports exam_registration_imports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_registration_imports
    ADD CONSTRAINT exam_registration_imports_pkey PRIMARY KEY (id);


--
-- Name: exam_registration_imports exam_registration_imports_registration_session_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_registration_imports
    ADD CONSTRAINT exam_registration_imports_registration_session_id_key UNIQUE (registration_session_id);


--
-- Name: exam_registration_imports exam_registration_imports_source_event_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_registration_imports
    ADD CONSTRAINT exam_registration_imports_source_event_id_key UNIQUE (source_event_id);


--
-- Name: exam_session_slots_aud exam_session_slots_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_session_slots_aud
    ADD CONSTRAINT exam_session_slots_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: exam_session_slots exam_session_slots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_session_slots
    ADD CONSTRAINT exam_session_slots_pkey PRIMARY KEY (id);


--
-- Name: exam_sessions_aud exam_sessions_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_sessions_aud
    ADD CONSTRAINT exam_sessions_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: exam_sessions exam_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_sessions
    ADD CONSTRAINT exam_sessions_pkey PRIMARY KEY (id);


--
-- Name: exam_student_timetable_entries_aud exam_student_timetable_entries_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_student_timetable_entries_aud
    ADD CONSTRAINT exam_student_timetable_entries_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: exam_student_timetable_entries exam_student_timetable_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_student_timetable_entries
    ADD CONSTRAINT exam_student_timetable_entries_pkey PRIMARY KEY (id);


--
-- Name: exam_timetable_generation_runs_aud exam_timetable_generation_runs_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_timetable_generation_runs_aud
    ADD CONSTRAINT exam_timetable_generation_runs_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: exam_timetable_generation_runs exam_timetable_generation_runs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_timetable_generation_runs
    ADD CONSTRAINT exam_timetable_generation_runs_pkey PRIMARY KEY (id);


--
-- Name: exam_timetable_generation_runs exam_timetable_generation_runs_run_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_timetable_generation_runs
    ADD CONSTRAINT exam_timetable_generation_runs_run_number_key UNIQUE (run_number);


--
-- Name: exam_timetable_run_events_aud exam_timetable_run_events_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_timetable_run_events_aud
    ADD CONSTRAINT exam_timetable_run_events_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: exam_timetable_run_events exam_timetable_run_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_timetable_run_events
    ADD CONSTRAINT exam_timetable_run_events_pkey PRIMARY KEY (id);


--
-- Name: exam_timetable_venue_allocations_aud exam_timetable_venue_allocations_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_timetable_venue_allocations_aud
    ADD CONSTRAINT exam_timetable_venue_allocations_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: exam_timetable_venue_allocations exam_timetable_venue_allocations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_timetable_venue_allocations
    ADD CONSTRAINT exam_timetable_venue_allocations_pkey PRIMARY KEY (id);


--
-- Name: exam_venue_availability_windows_aud exam_venue_availability_windows_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_venue_availability_windows_aud
    ADD CONSTRAINT exam_venue_availability_windows_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: exam_venue_availability_windows exam_venue_availability_windows_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_venue_availability_windows
    ADD CONSTRAINT exam_venue_availability_windows_pkey PRIMARY KEY (id);


--
-- Name: exam_venue_types_aud exam_venue_types_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_venue_types_aud
    ADD CONSTRAINT exam_venue_types_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: exam_venue_types exam_venue_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_venue_types
    ADD CONSTRAINT exam_venue_types_pkey PRIMARY KEY (id);


--
-- Name: exam_venues_aud exam_venues_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_venues_aud
    ADD CONSTRAINT exam_venues_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: exam_venues exam_venues_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_venues
    ADD CONSTRAINT exam_venues_pkey PRIMARY KEY (id);


--
-- Name: integration_inbox integration_inbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_inbox
    ADD CONSTRAINT integration_inbox_pkey PRIMARY KEY (event_id);


--
-- Name: module_exam_requirements_aud module_exam_requirements_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.module_exam_requirements_aud
    ADD CONSTRAINT module_exam_requirements_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: module_exam_requirements module_exam_requirements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.module_exam_requirements
    ADD CONSTRAINT module_exam_requirements_pkey PRIMARY KEY (id);


--
-- Name: revinfo revinfo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revinfo
    ADD CONSTRAINT revinfo_pkey PRIMARY KEY (rev);


--
-- Name: exam_attendance_records uk_exam_attendance_candidate; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_attendance_records
    ADD CONSTRAINT uk_exam_attendance_candidate UNIQUE (attendance_session_id, student_timetable_entry_id);


--
-- Name: exam_candidate_modules uk_exam_candidate_module; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_candidate_modules
    ADD CONSTRAINT uk_exam_candidate_module UNIQUE (registration_import_id, module_id);


--
-- Name: exam_timetable_venue_allocations uk_exam_entry_venue; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_timetable_venue_allocations
    ADD CONSTRAINT uk_exam_entry_venue UNIQUE (master_timetable_entry_id, venue_id);


--
-- Name: exam_master_timetable_entries uk_exam_master_run_module; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_master_timetable_entries
    ADD CONSTRAINT uk_exam_master_run_module UNIQUE (generation_run_id, module_id);


--
-- Name: exam_session_slots uk_exam_session_slot_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_session_slots
    ADD CONSTRAINT uk_exam_session_slot_code UNIQUE (exam_session_id, code);


--
-- Name: exam_student_timetable_entries uk_exam_student_run_module; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_student_timetable_entries
    ADD CONSTRAINT uk_exam_student_run_module UNIQUE (generation_run_id, student_id, module_id);


--
-- Name: exam_student_timetable_entries uk_exam_student_venue_seat; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_student_timetable_entries
    ADD CONSTRAINT uk_exam_student_venue_seat UNIQUE (venue_allocation_id, seat_number);


--
-- Name: module_exam_requirements uk_module_exam_requirement_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.module_exam_requirements
    ADD CONSTRAINT uk_module_exam_requirement_version UNIQUE (academic_period_id, module_id, requirement_version);


--
-- Name: idx_exam_attendance_records_session_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_exam_attendance_records_session_status ON public.exam_attendance_records USING btree (attendance_session_id, attendance_status) WHERE (deleted_at IS NULL);


--
-- Name: idx_exam_inbox_processed_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_exam_inbox_processed_at ON public.integration_inbox USING btree (processed_at);


--
-- Name: idx_exam_incident_session_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_exam_incident_session_status ON public.exam_incident_reports USING btree (attendance_session_id, status) WHERE (deleted_at IS NULL);


--
-- Name: idx_exam_session_slots_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_exam_session_slots_time ON public.exam_session_slots USING btree (exam_session_id, starts_at) WHERE (deleted_at IS NULL);


--
-- Name: idx_exam_student_timetable_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_exam_student_timetable_lookup ON public.exam_student_timetable_entries USING btree (student_id, scheduled_starts_at) WHERE (deleted_at IS NULL);


--
-- Name: idx_exam_venue_availability; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_exam_venue_availability ON public.exam_venue_availability_windows USING btree (venue_id, available_from, available_until) WHERE (deleted_at IS NULL);


--
-- Name: uk_exam_session_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_exam_session_code ON public.exam_sessions USING btree (academic_period_id, lower((code)::text)) WHERE (deleted_at IS NULL);


--
-- Name: uk_exam_timetable_one_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_exam_timetable_one_active ON public.exam_timetable_generation_runs USING btree (exam_session_id) WHERE (((status)::text = ANY ((ARRAY['GENERATED'::character varying, 'REVIEWED'::character varying, 'APPROVED'::character varying])::text[])) AND (deleted_at IS NULL));


--
-- Name: uk_exam_venue_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_exam_venue_code ON public.exam_venues USING btree (lower((code)::text)) WHERE (deleted_at IS NULL);


--
-- Name: uk_exam_venue_type_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_exam_venue_type_code ON public.exam_venue_types USING btree (lower((code)::text)) WHERE (deleted_at IS NULL);


--
-- Name: uk_module_exam_requirement_approved; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_module_exam_requirement_approved ON public.module_exam_requirements USING btree (academic_period_id, module_id) WHERE (((status)::text = 'APPROVED'::text) AND (deleted_at IS NULL));


--
-- Name: module_exam_requirements trg_approved_exam_requirement_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_approved_exam_requirement_immutable BEFORE UPDATE ON public.module_exam_requirements FOR EACH ROW EXECUTE FUNCTION public.enforce_approved_exam_requirement_immutability();


--
-- Name: exam_candidate_modules trg_exam_candidate_snapshot_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_exam_candidate_snapshot_immutable BEFORE UPDATE ON public.exam_candidate_modules FOR EACH ROW EXECUTE FUNCTION public.enforce_exam_candidate_snapshot_immutability();


--
-- Name: exam_master_timetable_entries trg_exam_master_evidence_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_exam_master_evidence_immutable BEFORE DELETE OR UPDATE ON public.exam_master_timetable_entries FOR EACH ROW EXECUTE FUNCTION public.enforce_exam_timetable_evidence_immutability();


--
-- Name: exam_registration_imports trg_exam_registration_snapshot_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_exam_registration_snapshot_immutable BEFORE UPDATE ON public.exam_registration_imports FOR EACH ROW EXECUTE FUNCTION public.enforce_exam_source_snapshot_immutability();


--
-- Name: exam_session_slots trg_exam_slot_within_session; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_exam_slot_within_session BEFORE INSERT OR UPDATE ON public.exam_session_slots FOR EACH ROW EXECUTE FUNCTION public.enforce_exam_slot_within_session();


--
-- Name: exam_student_timetable_entries trg_exam_student_evidence_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_exam_student_evidence_immutable BEFORE DELETE OR UPDATE ON public.exam_student_timetable_entries FOR EACH ROW EXECUTE FUNCTION public.enforce_exam_timetable_evidence_immutability();


--
-- Name: exam_attendance_records trg_validate_exam_attendance_record; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_exam_attendance_record BEFORE INSERT OR DELETE OR UPDATE ON public.exam_attendance_records FOR EACH ROW EXECUTE FUNCTION public.validate_exam_attendance_record();


--
-- Name: exam_attendance_sessions trg_validate_exam_attendance_session; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_exam_attendance_session BEFORE INSERT OR UPDATE ON public.exam_attendance_sessions FOR EACH ROW EXECUTE FUNCTION public.validate_exam_attendance_session();


--
-- Name: exam_incident_reports trg_validate_exam_incident_report; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_exam_incident_report BEFORE INSERT OR DELETE OR UPDATE ON public.exam_incident_reports FOR EACH ROW EXECUTE FUNCTION public.validate_exam_incident_report();


--
-- Name: exam_student_timetable_entries trg_validate_exam_student_allocation; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_exam_student_allocation BEFORE INSERT OR UPDATE ON public.exam_student_timetable_entries FOR EACH ROW EXECUTE FUNCTION public.validate_exam_student_allocation();


--
-- Name: exam_timetable_venue_allocations trg_validate_exam_venue_allocation; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_exam_venue_allocation BEFORE INSERT OR UPDATE ON public.exam_timetable_venue_allocations FOR EACH ROW EXECUTE FUNCTION public.validate_exam_venue_allocation();


--
-- Name: exam_attendance_records exam_attendance_records_attendance_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_attendance_records
    ADD CONSTRAINT exam_attendance_records_attendance_session_id_fkey FOREIGN KEY (attendance_session_id) REFERENCES public.exam_attendance_sessions(id);


--
-- Name: exam_attendance_records_aud exam_attendance_records_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_attendance_records_aud
    ADD CONSTRAINT exam_attendance_records_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: exam_attendance_records exam_attendance_records_student_timetable_entry_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_attendance_records
    ADD CONSTRAINT exam_attendance_records_student_timetable_entry_id_fkey FOREIGN KEY (student_timetable_entry_id) REFERENCES public.exam_student_timetable_entries(id);


--
-- Name: exam_attendance_sessions_aud exam_attendance_sessions_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_attendance_sessions_aud
    ADD CONSTRAINT exam_attendance_sessions_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: exam_attendance_sessions exam_attendance_sessions_venue_allocation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_attendance_sessions
    ADD CONSTRAINT exam_attendance_sessions_venue_allocation_id_fkey FOREIGN KEY (venue_allocation_id) REFERENCES public.exam_timetable_venue_allocations(id);


--
-- Name: exam_candidate_modules_aud exam_candidate_modules_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_candidate_modules_aud
    ADD CONSTRAINT exam_candidate_modules_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: exam_candidate_modules exam_candidate_modules_registration_import_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_candidate_modules
    ADD CONSTRAINT exam_candidate_modules_registration_import_id_fkey FOREIGN KEY (registration_import_id) REFERENCES public.exam_registration_imports(id);


--
-- Name: exam_incident_reports exam_incident_reports_attendance_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_incident_reports
    ADD CONSTRAINT exam_incident_reports_attendance_session_id_fkey FOREIGN KEY (attendance_session_id) REFERENCES public.exam_attendance_sessions(id);


--
-- Name: exam_incident_reports_aud exam_incident_reports_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_incident_reports_aud
    ADD CONSTRAINT exam_incident_reports_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: exam_incident_reports exam_incident_reports_student_timetable_entry_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_incident_reports
    ADD CONSTRAINT exam_incident_reports_student_timetable_entry_id_fkey FOREIGN KEY (student_timetable_entry_id) REFERENCES public.exam_student_timetable_entries(id);


--
-- Name: exam_master_timetable_entries_aud exam_master_timetable_entries_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_master_timetable_entries_aud
    ADD CONSTRAINT exam_master_timetable_entries_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: exam_master_timetable_entries exam_master_timetable_entries_exam_session_slot_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_master_timetable_entries
    ADD CONSTRAINT exam_master_timetable_entries_exam_session_slot_id_fkey FOREIGN KEY (exam_session_slot_id) REFERENCES public.exam_session_slots(id);


--
-- Name: exam_master_timetable_entries exam_master_timetable_entries_generation_run_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_master_timetable_entries
    ADD CONSTRAINT exam_master_timetable_entries_generation_run_id_fkey FOREIGN KEY (generation_run_id) REFERENCES public.exam_timetable_generation_runs(id);


--
-- Name: exam_master_timetable_entries exam_master_timetable_entries_module_exam_requirement_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_master_timetable_entries
    ADD CONSTRAINT exam_master_timetable_entries_module_exam_requirement_id_fkey FOREIGN KEY (module_exam_requirement_id) REFERENCES public.module_exam_requirements(id);


--
-- Name: exam_registration_imports_aud exam_registration_imports_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_registration_imports_aud
    ADD CONSTRAINT exam_registration_imports_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: exam_session_slots_aud exam_session_slots_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_session_slots_aud
    ADD CONSTRAINT exam_session_slots_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: exam_session_slots exam_session_slots_exam_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_session_slots
    ADD CONSTRAINT exam_session_slots_exam_session_id_fkey FOREIGN KEY (exam_session_id) REFERENCES public.exam_sessions(id);


--
-- Name: exam_sessions_aud exam_sessions_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_sessions_aud
    ADD CONSTRAINT exam_sessions_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: exam_student_timetable_entries_aud exam_student_timetable_entries_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_student_timetable_entries_aud
    ADD CONSTRAINT exam_student_timetable_entries_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: exam_student_timetable_entries exam_student_timetable_entries_candidate_module_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_student_timetable_entries
    ADD CONSTRAINT exam_student_timetable_entries_candidate_module_id_fkey FOREIGN KEY (candidate_module_id) REFERENCES public.exam_candidate_modules(id);


--
-- Name: exam_student_timetable_entries exam_student_timetable_entries_generation_run_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_student_timetable_entries
    ADD CONSTRAINT exam_student_timetable_entries_generation_run_id_fkey FOREIGN KEY (generation_run_id) REFERENCES public.exam_timetable_generation_runs(id);


--
-- Name: exam_student_timetable_entries exam_student_timetable_entries_master_timetable_entry_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_student_timetable_entries
    ADD CONSTRAINT exam_student_timetable_entries_master_timetable_entry_id_fkey FOREIGN KEY (master_timetable_entry_id) REFERENCES public.exam_master_timetable_entries(id);


--
-- Name: exam_student_timetable_entries exam_student_timetable_entries_registration_import_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_student_timetable_entries
    ADD CONSTRAINT exam_student_timetable_entries_registration_import_id_fkey FOREIGN KEY (registration_import_id) REFERENCES public.exam_registration_imports(id);


--
-- Name: exam_student_timetable_entries exam_student_timetable_entries_venue_allocation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_student_timetable_entries
    ADD CONSTRAINT exam_student_timetable_entries_venue_allocation_id_fkey FOREIGN KEY (venue_allocation_id) REFERENCES public.exam_timetable_venue_allocations(id);


--
-- Name: exam_timetable_generation_runs_aud exam_timetable_generation_runs_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_timetable_generation_runs_aud
    ADD CONSTRAINT exam_timetable_generation_runs_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: exam_timetable_generation_runs exam_timetable_generation_runs_exam_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_timetable_generation_runs
    ADD CONSTRAINT exam_timetable_generation_runs_exam_session_id_fkey FOREIGN KEY (exam_session_id) REFERENCES public.exam_sessions(id);


--
-- Name: exam_timetable_run_events_aud exam_timetable_run_events_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_timetable_run_events_aud
    ADD CONSTRAINT exam_timetable_run_events_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: exam_timetable_run_events exam_timetable_run_events_generation_run_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_timetable_run_events
    ADD CONSTRAINT exam_timetable_run_events_generation_run_id_fkey FOREIGN KEY (generation_run_id) REFERENCES public.exam_timetable_generation_runs(id);


--
-- Name: exam_timetable_venue_allocations_aud exam_timetable_venue_allocations_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_timetable_venue_allocations_aud
    ADD CONSTRAINT exam_timetable_venue_allocations_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: exam_timetable_venue_allocations exam_timetable_venue_allocations_master_timetable_entry_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_timetable_venue_allocations
    ADD CONSTRAINT exam_timetable_venue_allocations_master_timetable_entry_id_fkey FOREIGN KEY (master_timetable_entry_id) REFERENCES public.exam_master_timetable_entries(id);


--
-- Name: exam_timetable_venue_allocations exam_timetable_venue_allocations_venue_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_timetable_venue_allocations
    ADD CONSTRAINT exam_timetable_venue_allocations_venue_id_fkey FOREIGN KEY (venue_id) REFERENCES public.exam_venues(id);


--
-- Name: exam_venue_availability_windows_aud exam_venue_availability_windows_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_venue_availability_windows_aud
    ADD CONSTRAINT exam_venue_availability_windows_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: exam_venue_availability_windows exam_venue_availability_windows_venue_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_venue_availability_windows
    ADD CONSTRAINT exam_venue_availability_windows_venue_id_fkey FOREIGN KEY (venue_id) REFERENCES public.exam_venues(id);


--
-- Name: exam_venue_types_aud exam_venue_types_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_venue_types_aud
    ADD CONSTRAINT exam_venue_types_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: exam_venues_aud exam_venues_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_venues_aud
    ADD CONSTRAINT exam_venues_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: exam_venues exam_venues_venue_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_venues
    ADD CONSTRAINT exam_venues_venue_type_id_fkey FOREIGN KEY (venue_type_id) REFERENCES public.exam_venue_types(id);


--
-- Name: module_exam_requirements_aud module_exam_requirements_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.module_exam_requirements_aud
    ADD CONSTRAINT module_exam_requirements_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: module_exam_requirements module_exam_requirements_required_venue_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.module_exam_requirements
    ADD CONSTRAINT module_exam_requirements_required_venue_type_id_fkey FOREIGN KEY (required_venue_type_id) REFERENCES public.exam_venue_types(id);


--
-- PostgreSQL database dump complete
--


