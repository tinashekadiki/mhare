-- Author: Tinashe K
-- Canonical clean-slate baseline for student-records-service.

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
-- Name: enforce_registration_session_integrity(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_registration_session_integrity() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


--
-- Name: enforce_student_conversion_request_integrity(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_student_conversion_request_integrity() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    student_offer_id uuid;
    enrolment_student_id uuid;
    enrolment_offer_id uuid;
BEGIN
    SELECT source_offer_id
      INTO student_offer_id
      FROM students
     WHERE id = NEW.student_id;

    SELECT student_id, source_offer_id
      INTO enrolment_student_id, enrolment_offer_id
      FROM student_programme_enrolments
     WHERE id = NEW.programme_enrolment_id;

    IF student_offer_id IS NULL OR enrolment_student_id IS NULL THEN
        RAISE EXCEPTION 'Student conversion references missing conversion records';
    END IF;

    IF student_offer_id <> NEW.source_offer_id
            OR enrolment_offer_id <> NEW.source_offer_id
            OR enrolment_student_id <> NEW.student_id THEN
        RAISE EXCEPTION 'Student conversion source offer, student, and programme enrolment must agree';
    END IF;

    RETURN NEW;
END;
$$;


--
-- Name: enforce_student_registration_number(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_student_registration_number() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    expected_registration_number varchar(40);
BEGIN
    SELECT student_number INTO expected_registration_number
    FROM students
    WHERE id = NEW.student_id;

    IF expected_registration_number IS NULL
            OR NEW.registration_number <> expected_registration_number THEN
        RAISE EXCEPTION 'Registration number must equal the student number';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: prevent_registration_module_snapshot_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_registration_module_snapshot_change() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


--
-- Name: prevent_student_conversion_request_source_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_student_conversion_request_source_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF ROW(
            NEW.source_event_id,
            NEW.source_application_id,
            NEW.source_offer_id,
            NEW.student_id,
            NEW.programme_enrolment_id,
            NEW.requested_at
        ) IS DISTINCT FROM ROW(
            OLD.source_event_id,
            OLD.source_application_id,
            OLD.source_offer_id,
            OLD.student_id,
            OLD.programme_enrolment_id,
            OLD.requested_at
        ) THEN
        RAISE EXCEPTION 'Student conversion request source is immutable';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: prevent_student_conversion_source_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_student_conversion_source_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF ROW(
            NEW.student_number,
            NEW.user_id,
            NEW.source_applicant_id,
            NEW.source_applicant_number,
            NEW.source_application_id,
            NEW.source_offer_id
        ) IS DISTINCT FROM ROW(
            OLD.student_number,
            OLD.user_id,
            OLD.source_applicant_id,
            OLD.source_applicant_number,
            OLD.source_application_id,
            OLD.source_offer_id
        ) THEN
        RAISE EXCEPTION 'Student conversion source identity is immutable';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: prevent_student_enrolment_source_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_student_enrolment_source_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF ROW(
            NEW.student_id,
            NEW.source_offer_id,
            NEW.source_programme_choice_id,
            NEW.programme_id,
            NEW.programme_version_id,
            NEW.programme_code,
            NEW.programme_name,
            NEW.intake_id,
            NEW.commencement_date
        ) IS DISTINCT FROM ROW(
            OLD.student_id,
            OLD.source_offer_id,
            OLD.source_programme_choice_id,
            OLD.programme_id,
            OLD.programme_version_id,
            OLD.programme_code,
            OLD.programme_name,
            OLD.intake_id,
            OLD.commencement_date
        ) THEN
        RAISE EXCEPTION 'Accepted-offer programme snapshot is immutable';
    END IF;
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

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
-- Name: integration_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.integration_outbox (
    id uuid NOT NULL,
    event_type character varying(160) NOT NULL,
    routing_key character varying(160) NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    status character varying(20) NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone NOT NULL,
    published_at timestamp with time zone,
    last_error character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_student_records_outbox_attempt_count CHECK ((attempt_count >= 0)),
    CONSTRAINT ck_student_records_outbox_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PUBLISHED'::character varying, 'DEAD'::character varying])::text[])))
);


--
-- Name: registration_modules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.registration_modules (
    id uuid NOT NULL,
    registration_session_id uuid NOT NULL,
    curriculum_module_id uuid NOT NULL,
    module_id uuid NOT NULL,
    module_code character varying(50) NOT NULL,
    module_name character varying(200) NOT NULL,
    curriculum_module_type character varying(20) NOT NULL,
    credit_value numeric(6,2) NOT NULL,
    minimum_mark_required numeric(5,2),
    selection_source character varying(30) NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_registration_modules_credit CHECK ((credit_value > (0)::numeric)),
    CONSTRAINT ck_registration_modules_minimum_mark CHECK (((minimum_mark_required IS NULL) OR ((minimum_mark_required >= (0)::numeric) AND (minimum_mark_required <= (100)::numeric)))),
    CONSTRAINT ck_registration_modules_source CHECK (((selection_source)::text = ANY ((ARRAY['AUTO_COMPULSORY'::character varying, 'STUDENT_ELECTIVE'::character varying, 'STAFF_ELECTIVE'::character varying, 'CARRY'::character varying, 'REPEAT'::character varying])::text[]))),
    CONSTRAINT ck_registration_modules_type CHECK (((curriculum_module_type)::text = ANY ((ARRAY['COMPULSORY'::character varying, 'ELECTIVE'::character varying, 'OPTIONAL'::character varying])::text[])))
);


--
-- Name: registration_modules_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.registration_modules_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    registration_session_id uuid,
    curriculum_module_id uuid,
    module_id uuid,
    module_code character varying(50),
    module_name character varying(200),
    curriculum_module_type character varying(20),
    credit_value numeric(6,2),
    minimum_mark_required numeric(5,2),
    selection_source character varying(30),
    sort_order integer,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: registration_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.registration_sessions (
    id uuid NOT NULL,
    student_id uuid NOT NULL,
    programme_enrolment_id uuid NOT NULL,
    academic_period_id uuid NOT NULL,
    academic_period_code character varying(50) NOT NULL,
    academic_period_name character varying(150) NOT NULL,
    academic_period_starts_on date NOT NULL,
    academic_period_ends_on date NOT NULL,
    programme_version_id uuid NOT NULL,
    programme_period_number integer NOT NULL,
    registration_type character varying(20) NOT NULL,
    status character varying(30) NOT NULL,
    status_reason character varying(1000) NOT NULL,
    initiated_at timestamp with time zone NOT NULL,
    submitted_at timestamp with time zone,
    academic_approved_by_user_id uuid,
    academic_approved_at timestamp with time zone,
    confirmed_by_user_id uuid,
    confirmed_at timestamp with time zone,
    rejected_by_user_id uuid,
    rejected_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    registration_number character varying(50) NOT NULL,
    owning_academic_unit_id uuid NOT NULL,
    owning_academic_unit_code character varying(80) NOT NULL,
    owning_academic_unit_name character varying(200) NOT NULL,
    programme_level_id uuid NOT NULL,
    programme_level_code character varying(80) NOT NULL,
    programme_level_name character varying(200) NOT NULL,
    CONSTRAINT ck_registration_academic_unit_snapshot CHECK (((length(TRIM(BOTH FROM owning_academic_unit_code)) > 0) AND (length(TRIM(BOTH FROM owning_academic_unit_name)) > 0))),
    CONSTRAINT ck_registration_programme_level_snapshot CHECK ((((programme_level_code)::text = ANY ((ARRAY['UG'::character varying, 'PG'::character varying])::text[])) AND (length(TRIM(BOTH FROM programme_level_name)) > 0))),
    CONSTRAINT ck_registration_sessions_academic_actor CHECK ((((academic_approved_at IS NULL) AND (academic_approved_by_user_id IS NULL)) OR ((academic_approved_at IS NOT NULL) AND (academic_approved_by_user_id IS NOT NULL)))),
    CONSTRAINT ck_registration_sessions_confirmation_actor CHECK ((((confirmed_at IS NULL) AND (confirmed_by_user_id IS NULL)) OR ((confirmed_at IS NOT NULL) AND (confirmed_by_user_id IS NOT NULL)))),
    CONSTRAINT ck_registration_sessions_period_dates CHECK ((academic_period_ends_on >= academic_period_starts_on)),
    CONSTRAINT ck_registration_sessions_period_number CHECK ((programme_period_number > 0)),
    CONSTRAINT ck_registration_sessions_rejection_actor CHECK ((((rejected_at IS NULL) AND (rejected_by_user_id IS NULL)) OR ((rejected_at IS NOT NULL) AND (rejected_by_user_id IS NOT NULL)))),
    CONSTRAINT ck_registration_sessions_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'SUBMITTED'::character varying, 'ACADEMIC_APPROVED'::character varying, 'CONFIRMED'::character varying, 'REJECTED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT ck_registration_sessions_timestamps CHECK (((((status)::text = 'DRAFT'::text) AND (submitted_at IS NULL) AND (academic_approved_at IS NULL) AND (confirmed_at IS NULL) AND (rejected_at IS NULL)) OR (((status)::text = 'SUBMITTED'::text) AND (submitted_at IS NOT NULL) AND (academic_approved_at IS NULL) AND (confirmed_at IS NULL) AND (rejected_at IS NULL)) OR (((status)::text = 'ACADEMIC_APPROVED'::text) AND (submitted_at IS NOT NULL) AND (academic_approved_at IS NOT NULL) AND (confirmed_at IS NULL) AND (rejected_at IS NULL)) OR (((status)::text = 'CONFIRMED'::text) AND (submitted_at IS NOT NULL) AND (academic_approved_at IS NOT NULL) AND (confirmed_at IS NOT NULL) AND (rejected_at IS NULL)) OR (((status)::text = 'REJECTED'::text) AND (submitted_at IS NOT NULL) AND (confirmed_at IS NULL) AND (rejected_at IS NOT NULL)) OR (((status)::text = 'CANCELLED'::text) AND (confirmed_at IS NULL)))),
    CONSTRAINT ck_registration_sessions_type CHECK (((registration_type)::text = ANY ((ARRAY['NORMAL'::character varying, 'LATE'::character varying, 'AMENDMENT'::character varying])::text[])))
);


--
-- Name: registration_sessions_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.registration_sessions_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    student_id uuid,
    programme_enrolment_id uuid,
    academic_period_id uuid,
    academic_period_code character varying(50),
    academic_period_name character varying(150),
    academic_period_starts_on date,
    academic_period_ends_on date,
    programme_version_id uuid,
    programme_period_number integer,
    registration_type character varying(20),
    status character varying(30),
    status_reason character varying(1000),
    initiated_at timestamp with time zone,
    submitted_at timestamp with time zone,
    academic_approved_by_user_id uuid,
    academic_approved_at timestamp with time zone,
    confirmed_by_user_id uuid,
    confirmed_at timestamp with time zone,
    rejected_by_user_id uuid,
    rejected_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    registration_number character varying(50),
    owning_academic_unit_id uuid,
    owning_academic_unit_code character varying(80),
    owning_academic_unit_name character varying(200),
    programme_level_id uuid,
    programme_level_code character varying(80),
    programme_level_name character varying(200)
);


--
-- Name: registration_status_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.registration_status_events (
    id uuid NOT NULL,
    registration_session_id uuid NOT NULL,
    from_status character varying(30),
    to_status character varying(30) NOT NULL,
    reason character varying(1000) NOT NULL,
    changed_by_user_id uuid NOT NULL,
    changed_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_registration_status_events_from CHECK (((from_status IS NULL) OR ((from_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'SUBMITTED'::character varying, 'ACADEMIC_APPROVED'::character varying, 'CONFIRMED'::character varying, 'REJECTED'::character varying, 'CANCELLED'::character varying])::text[])))),
    CONSTRAINT ck_registration_status_events_to CHECK (((to_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'SUBMITTED'::character varying, 'ACADEMIC_APPROVED'::character varying, 'CONFIRMED'::character varying, 'REJECTED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: registration_status_events_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.registration_status_events_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    registration_session_id uuid,
    from_status character varying(30),
    to_status character varying(30),
    reason character varying(1000),
    changed_by_user_id uuid,
    changed_at timestamp with time zone,
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
    service_name character varying(100) DEFAULT 'student-records-service'::character varying NOT NULL,
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
-- Name: student_conversion_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_conversion_requests (
    id uuid NOT NULL,
    source_event_id uuid NOT NULL,
    source_application_id uuid NOT NULL,
    source_offer_id uuid NOT NULL,
    student_id uuid NOT NULL,
    programme_enrolment_id uuid NOT NULL,
    status character varying(30) NOT NULL,
    finance_provisioning_status character varying(30) CONSTRAINT student_conversion_requests_finance_provisioning_statu_not_null NOT NULL,
    portal_provisioning_status character varying(30) NOT NULL,
    requested_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    failure_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    retry_count integer DEFAULT 0 NOT NULL,
    last_retry_at timestamp with time zone,
    last_retry_by_user_id uuid,
    last_retry_reason character varying(1000),
    CONSTRAINT ck_student_conversion_requests_completion CHECK (((((status)::text = 'COMPLETED'::text) AND (completed_at IS NOT NULL) AND ((finance_provisioning_status)::text = 'COMPLETED'::text) AND ((portal_provisioning_status)::text = 'COMPLETED'::text)) OR (((status)::text <> 'COMPLETED'::text) AND (completed_at IS NULL)))),
    CONSTRAINT ck_student_conversion_requests_failure_reason CHECK (((((status)::text = 'FAILED'::text) AND (failure_reason IS NOT NULL)) OR (((status)::text <> 'FAILED'::text) AND (failure_reason IS NULL)))),
    CONSTRAINT ck_student_conversion_requests_finance CHECK (((finance_provisioning_status)::text = ANY ((ARRAY['PENDING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT ck_student_conversion_requests_portal CHECK (((portal_provisioning_status)::text = ANY ((ARRAY['PENDING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT ck_student_conversion_requests_retry_count CHECK ((retry_count >= 0)),
    CONSTRAINT ck_student_conversion_requests_retry_evidence CHECK ((((retry_count = 0) AND (last_retry_at IS NULL) AND (last_retry_by_user_id IS NULL) AND (last_retry_reason IS NULL)) OR ((retry_count > 0) AND (last_retry_at IS NOT NULL) AND (last_retry_by_user_id IS NOT NULL) AND (last_retry_reason IS NOT NULL)))),
    CONSTRAINT ck_student_conversion_requests_status CHECK (((status)::text = ANY ((ARRAY['PROVISIONING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: student_conversion_requests_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_conversion_requests_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    source_event_id uuid,
    source_application_id uuid,
    source_offer_id uuid,
    student_id uuid,
    programme_enrolment_id uuid,
    status character varying(30),
    finance_provisioning_status character varying(30),
    portal_provisioning_status character varying(30),
    requested_at timestamp with time zone,
    completed_at timestamp with time zone,
    failure_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    retry_count integer,
    last_retry_at timestamp with time zone,
    last_retry_by_user_id uuid,
    last_retry_reason character varying(1000)
);


--
-- Name: student_entry_option_preferences; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_entry_option_preferences (
    id uuid NOT NULL,
    programme_enrolment_id uuid CONSTRAINT student_entry_option_preference_programme_enrolment_id_not_null NOT NULL,
    entry_option_id uuid NOT NULL,
    entry_option_code character varying(50) NOT NULL,
    entry_option_name character varying(200) NOT NULL,
    preference_rank integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_student_entry_option_preference_rank CHECK ((preference_rank > 0))
);


--
-- Name: student_entry_option_preferences_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_entry_option_preferences_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    programme_enrolment_id uuid,
    entry_option_id uuid,
    entry_option_code character varying(50),
    entry_option_name character varying(200),
    preference_rank integer,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: student_number_counters; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_number_counters (
    number_prefix character varying(12) NOT NULL,
    cohort_year integer NOT NULL,
    next_value bigint NOT NULL,
    CONSTRAINT ck_student_number_counters_next_value CHECK ((next_value >= 0))
);


--
-- Name: student_programme_enrolments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_programme_enrolments (
    id uuid NOT NULL,
    student_id uuid NOT NULL,
    source_offer_id uuid NOT NULL,
    source_programme_choice_id uuid CONSTRAINT student_programme_enrolment_source_programme_choice_id_not_null NOT NULL,
    programme_id uuid NOT NULL,
    programme_version_id uuid NOT NULL,
    programme_code character varying(50) NOT NULL,
    programme_name character varying(200) NOT NULL,
    intake_id uuid NOT NULL,
    commencement_date date NOT NULL,
    status character varying(30) NOT NULL,
    status_reason character varying(1000) NOT NULL,
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    ended_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_student_programme_enrolments_end CHECK (((((status)::text = ANY ((ARRAY['TRANSFERRED'::character varying, 'WITHDRAWN'::character varying, 'COMPLETED'::character varying])::text[])) AND (ended_at IS NOT NULL)) OR (((status)::text <> ALL ((ARRAY['TRANSFERRED'::character varying, 'WITHDRAWN'::character varying, 'COMPLETED'::character varying])::text[])) AND (ended_at IS NULL)))),
    CONSTRAINT ck_student_programme_enrolments_status CHECK (((status)::text = ANY ((ARRAY['PROVISIONING'::character varying, 'ACTIVE'::character varying, 'DEFERRED'::character varying, 'SUSPENDED'::character varying, 'TRANSFERRED'::character varying, 'WITHDRAWN'::character varying, 'COMPLETED'::character varying])::text[])))
);


--
-- Name: student_programme_enrolments_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_programme_enrolments_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    student_id uuid,
    source_offer_id uuid,
    source_programme_choice_id uuid,
    programme_id uuid,
    programme_version_id uuid,
    programme_code character varying(50),
    programme_name character varying(200),
    intake_id uuid,
    commencement_date date,
    status character varying(30),
    status_reason character varying(1000),
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    ended_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: student_status_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_status_events (
    id uuid NOT NULL,
    student_id uuid NOT NULL,
    from_status character varying(30),
    to_status character varying(30) NOT NULL,
    reason character varying(1000) NOT NULL,
    changed_by_user_id uuid,
    changed_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_student_status_events_status CHECK ((((from_status IS NULL) OR ((from_status)::text = ANY ((ARRAY['PROVISIONING'::character varying, 'ACTIVE'::character varying, 'SUSPENDED'::character varying, 'WITHDRAWN'::character varying, 'INACTIVE'::character varying])::text[]))) AND ((to_status)::text = ANY ((ARRAY['PROVISIONING'::character varying, 'ACTIVE'::character varying, 'SUSPENDED'::character varying, 'WITHDRAWN'::character varying, 'INACTIVE'::character varying])::text[]))))
);


--
-- Name: student_status_events_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_status_events_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    student_id uuid,
    from_status character varying(30),
    to_status character varying(30),
    reason character varying(1000),
    changed_by_user_id uuid,
    changed_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: students; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.students (
    id uuid NOT NULL,
    student_number character varying(40) NOT NULL,
    user_id uuid NOT NULL,
    source_applicant_id uuid NOT NULL,
    source_applicant_number character varying(40) NOT NULL,
    source_application_id uuid NOT NULL,
    source_offer_id uuid NOT NULL,
    applicant_category_code character varying(30) NOT NULL,
    first_name character varying(100) NOT NULL,
    middle_names character varying(150),
    last_name character varying(100) NOT NULL,
    date_of_birth date,
    gender_code character varying(30),
    national_id_number character varying(50),
    passport_number character varying(50),
    primary_email character varying(200) NOT NULL,
    primary_phone character varying(50),
    postal_address character varying(500),
    residential_address character varying(500),
    disability_status_code character varying(30),
    special_needs character varying(1000),
    sponsor_type_code character varying(30),
    sponsor_details jsonb,
    status character varying(30) NOT NULL,
    activated_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_students_activation CHECK (((((status)::text = 'PROVISIONING'::text) AND (activated_at IS NULL)) OR (((status)::text <> 'PROVISIONING'::text) AND (activated_at IS NOT NULL)))),
    CONSTRAINT ck_students_status CHECK (((status)::text = ANY ((ARRAY['PROVISIONING'::character varying, 'ACTIVE'::character varying, 'SUSPENDED'::character varying, 'WITHDRAWN'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: students_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.students_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    student_number character varying(40),
    user_id uuid,
    source_applicant_id uuid,
    source_applicant_number character varying(40),
    source_application_id uuid,
    source_offer_id uuid,
    applicant_category_code character varying(30),
    first_name character varying(100),
    middle_names character varying(150),
    last_name character varying(100),
    date_of_birth date,
    gender_code character varying(30),
    national_id_number character varying(50),
    passport_number character varying(50),
    primary_email character varying(200),
    primary_phone character varying(50),
    postal_address character varying(500),
    residential_address character varying(500),
    disability_status_code character varying(30),
    special_needs character varying(1000),
    sponsor_type_code character varying(30),
    sponsor_details jsonb,
    status character varying(30),
    activated_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Data for Name: integration_inbox; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: integration_outbox; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: registration_modules; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: registration_modules_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: registration_sessions; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: registration_sessions_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: registration_status_events; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: registration_status_events_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: revinfo; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_conversion_requests; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_conversion_requests_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_entry_option_preferences; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_entry_option_preferences_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_number_counters; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_programme_enrolments; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_programme_enrolments_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_status_events; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_status_events_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: students; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: students_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Name: revinfo_rev_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.revinfo_rev_seq', 1, false);


--
-- Name: integration_inbox integration_inbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_inbox
    ADD CONSTRAINT integration_inbox_pkey PRIMARY KEY (event_id);


--
-- Name: integration_outbox integration_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_outbox
    ADD CONSTRAINT integration_outbox_pkey PRIMARY KEY (id);


--
-- Name: registration_modules_aud registration_modules_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_modules_aud
    ADD CONSTRAINT registration_modules_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: registration_modules registration_modules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_modules
    ADD CONSTRAINT registration_modules_pkey PRIMARY KEY (id);


--
-- Name: registration_sessions_aud registration_sessions_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_sessions_aud
    ADD CONSTRAINT registration_sessions_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: registration_sessions registration_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_sessions
    ADD CONSTRAINT registration_sessions_pkey PRIMARY KEY (id);


--
-- Name: registration_status_events_aud registration_status_events_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_status_events_aud
    ADD CONSTRAINT registration_status_events_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: registration_status_events registration_status_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_status_events
    ADD CONSTRAINT registration_status_events_pkey PRIMARY KEY (id);


--
-- Name: revinfo revinfo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revinfo
    ADD CONSTRAINT revinfo_pkey PRIMARY KEY (rev);


--
-- Name: student_conversion_requests_aud student_conversion_requests_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_conversion_requests_aud
    ADD CONSTRAINT student_conversion_requests_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: student_conversion_requests student_conversion_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_conversion_requests
    ADD CONSTRAINT student_conversion_requests_pkey PRIMARY KEY (id);


--
-- Name: student_entry_option_preferences_aud student_entry_option_preferences_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_entry_option_preferences_aud
    ADD CONSTRAINT student_entry_option_preferences_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: student_entry_option_preferences student_entry_option_preferences_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_entry_option_preferences
    ADD CONSTRAINT student_entry_option_preferences_pkey PRIMARY KEY (id);


--
-- Name: student_number_counters student_number_counters_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_number_counters
    ADD CONSTRAINT student_number_counters_pkey PRIMARY KEY (number_prefix, cohort_year);


--
-- Name: student_programme_enrolments_aud student_programme_enrolments_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_programme_enrolments_aud
    ADD CONSTRAINT student_programme_enrolments_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: student_programme_enrolments student_programme_enrolments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_programme_enrolments
    ADD CONSTRAINT student_programme_enrolments_pkey PRIMARY KEY (id);


--
-- Name: student_status_events_aud student_status_events_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_status_events_aud
    ADD CONSTRAINT student_status_events_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: student_status_events student_status_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_status_events
    ADD CONSTRAINT student_status_events_pkey PRIMARY KEY (id);


--
-- Name: students_aud students_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.students_aud
    ADD CONSTRAINT students_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: students students_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.students
    ADD CONSTRAINT students_pkey PRIMARY KEY (id);


--
-- Name: registration_modules uk_registration_modules_curriculum; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_modules
    ADD CONSTRAINT uk_registration_modules_curriculum UNIQUE (registration_session_id, curriculum_module_id);


--
-- Name: registration_modules uk_registration_modules_module; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_modules
    ADD CONSTRAINT uk_registration_modules_module UNIQUE (registration_session_id, module_id);


--
-- Name: registration_sessions uk_registration_sessions_number_period_programme; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_sessions
    ADD CONSTRAINT uk_registration_sessions_number_period_programme UNIQUE (registration_number, academic_period_id, programme_version_id);


--
-- Name: student_conversion_requests uk_student_conversion_requests_event; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_conversion_requests
    ADD CONSTRAINT uk_student_conversion_requests_event UNIQUE (source_event_id);


--
-- Name: student_conversion_requests uk_student_conversion_requests_offer; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_conversion_requests
    ADD CONSTRAINT uk_student_conversion_requests_offer UNIQUE (source_offer_id);


--
-- Name: student_entry_option_preferences uk_student_entry_option_preference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_entry_option_preferences
    ADD CONSTRAINT uk_student_entry_option_preference UNIQUE (programme_enrolment_id, entry_option_id);


--
-- Name: student_programme_enrolments uk_student_programme_enrolments_offer; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_programme_enrolments
    ADD CONSTRAINT uk_student_programme_enrolments_offer UNIQUE (source_offer_id);


--
-- Name: students uk_students_source_offer; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.students
    ADD CONSTRAINT uk_students_source_offer UNIQUE (source_offer_id);


--
-- Name: students uk_students_student_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.students
    ADD CONSTRAINT uk_students_student_number UNIQUE (student_number);


--
-- Name: students uk_students_user_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.students
    ADD CONSTRAINT uk_students_user_id UNIQUE (user_id);


--
-- Name: idx_registration_sessions_operations; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_registration_sessions_operations ON public.registration_sessions USING btree (status, academic_period_starts_on, student_id) WHERE (deleted_at IS NULL);


--
-- Name: idx_registration_status_events_history; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_registration_status_events_history ON public.registration_status_events USING btree (registration_session_id, changed_at, id);


--
-- Name: idx_student_records_inbox_processed_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_student_records_inbox_processed_at ON public.integration_inbox USING btree (processed_at);


--
-- Name: idx_student_records_outbox_dispatch; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_student_records_outbox_dispatch ON public.integration_outbox USING btree (next_attempt_at, occurred_at) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: uk_registration_sessions_student_period; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_registration_sessions_student_period ON public.registration_sessions USING btree (student_id, academic_period_id) WHERE ((deleted_at IS NULL) AND ((status)::text <> 'CANCELLED'::text));


--
-- Name: uk_student_programme_enrolments_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_student_programme_enrolments_active ON public.student_programme_enrolments USING btree (student_id) WHERE ((deleted_at IS NULL) AND ((status)::text = ANY ((ARRAY['PROVISIONING'::character varying, 'ACTIVE'::character varying, 'DEFERRED'::character varying, 'SUSPENDED'::character varying])::text[])));


--
-- Name: registration_modules trg_registration_module_snapshot_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_registration_module_snapshot_immutable BEFORE UPDATE ON public.registration_modules FOR EACH ROW EXECUTE FUNCTION public.prevent_registration_module_snapshot_change();


--
-- Name: registration_sessions trg_registration_session_integrity; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_registration_session_integrity BEFORE INSERT OR UPDATE ON public.registration_sessions FOR EACH ROW EXECUTE FUNCTION public.enforce_registration_session_integrity();


--
-- Name: registration_sessions trg_registration_sessions_student_number; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_registration_sessions_student_number BEFORE INSERT OR UPDATE OF registration_number, student_id ON public.registration_sessions FOR EACH ROW EXECUTE FUNCTION public.enforce_student_registration_number();


--
-- Name: student_conversion_requests trg_student_conversion_request_integrity; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_student_conversion_request_integrity BEFORE INSERT OR UPDATE OF source_offer_id, student_id, programme_enrolment_id ON public.student_conversion_requests FOR EACH ROW EXECUTE FUNCTION public.enforce_student_conversion_request_integrity();


--
-- Name: student_conversion_requests trg_student_conversion_request_source_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_student_conversion_request_source_immutable BEFORE UPDATE ON public.student_conversion_requests FOR EACH ROW EXECUTE FUNCTION public.prevent_student_conversion_request_source_mutation();


--
-- Name: student_programme_enrolments trg_student_enrolment_source_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_student_enrolment_source_immutable BEFORE UPDATE ON public.student_programme_enrolments FOR EACH ROW EXECUTE FUNCTION public.prevent_student_enrolment_source_mutation();


--
-- Name: students trg_students_conversion_source_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_students_conversion_source_immutable BEFORE UPDATE ON public.students FOR EACH ROW EXECUTE FUNCTION public.prevent_student_conversion_source_mutation();


--
-- Name: registration_modules_aud registration_modules_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_modules_aud
    ADD CONSTRAINT registration_modules_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: registration_modules registration_modules_registration_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_modules
    ADD CONSTRAINT registration_modules_registration_session_id_fkey FOREIGN KEY (registration_session_id) REFERENCES public.registration_sessions(id);


--
-- Name: registration_sessions_aud registration_sessions_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_sessions_aud
    ADD CONSTRAINT registration_sessions_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: registration_sessions registration_sessions_programme_enrolment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_sessions
    ADD CONSTRAINT registration_sessions_programme_enrolment_id_fkey FOREIGN KEY (programme_enrolment_id) REFERENCES public.student_programme_enrolments(id);


--
-- Name: registration_sessions registration_sessions_student_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_sessions
    ADD CONSTRAINT registration_sessions_student_id_fkey FOREIGN KEY (student_id) REFERENCES public.students(id);


--
-- Name: registration_status_events_aud registration_status_events_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_status_events_aud
    ADD CONSTRAINT registration_status_events_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: registration_status_events registration_status_events_registration_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_status_events
    ADD CONSTRAINT registration_status_events_registration_session_id_fkey FOREIGN KEY (registration_session_id) REFERENCES public.registration_sessions(id);


--
-- Name: student_conversion_requests_aud student_conversion_requests_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_conversion_requests_aud
    ADD CONSTRAINT student_conversion_requests_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: student_conversion_requests student_conversion_requests_programme_enrolment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_conversion_requests
    ADD CONSTRAINT student_conversion_requests_programme_enrolment_id_fkey FOREIGN KEY (programme_enrolment_id) REFERENCES public.student_programme_enrolments(id);


--
-- Name: student_conversion_requests student_conversion_requests_student_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_conversion_requests
    ADD CONSTRAINT student_conversion_requests_student_id_fkey FOREIGN KEY (student_id) REFERENCES public.students(id);


--
-- Name: student_entry_option_preferences_aud student_entry_option_preferences_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_entry_option_preferences_aud
    ADD CONSTRAINT student_entry_option_preferences_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: student_entry_option_preferences student_entry_option_preferences_programme_enrolment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_entry_option_preferences
    ADD CONSTRAINT student_entry_option_preferences_programme_enrolment_id_fkey FOREIGN KEY (programme_enrolment_id) REFERENCES public.student_programme_enrolments(id);


--
-- Name: student_programme_enrolments_aud student_programme_enrolments_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_programme_enrolments_aud
    ADD CONSTRAINT student_programme_enrolments_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: student_programme_enrolments student_programme_enrolments_student_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_programme_enrolments
    ADD CONSTRAINT student_programme_enrolments_student_id_fkey FOREIGN KEY (student_id) REFERENCES public.students(id);


--
-- Name: student_status_events_aud student_status_events_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_status_events_aud
    ADD CONSTRAINT student_status_events_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: student_status_events student_status_events_student_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_status_events
    ADD CONSTRAINT student_status_events_student_id_fkey FOREIGN KEY (student_id) REFERENCES public.students(id);


--
-- Name: students_aud students_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.students_aud
    ADD CONSTRAINT students_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- PostgreSQL database dump complete
--


