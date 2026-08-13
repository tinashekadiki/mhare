-- Author: Tinashe K
-- Canonical clean-slate baseline for dining-service.

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
-- Name: protect_dining_evidence(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_dining_evidence() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN RAISE EXCEPTION 'Dining attendance evidence is append-only and immutable'; END $$;


--
-- Name: protect_dining_workflow_evidence(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_dining_workflow_evidence() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN RAISE EXCEPTION 'Dining workflow evidence is append-only and immutable'; END $$;


--
-- Name: validate_meal_attendance(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_meal_attendance() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE session_status varchar(20); hall_id uuid; option_id uuid; service_day date; hall_capacity integer;
 assignment_status varchar(20); assignment_hall uuid; assignment_plan uuid; assignment_start date; assignment_end date;
BEGIN
 SELECT s.status,s.dining_hall_id,s.meal_option_id,s.service_date,h.service_capacity
 INTO session_status,hall_id,option_id,service_day,hall_capacity
 FROM meal_service_sessions s JOIN dining_halls h ON h.id=s.dining_hall_id
 WHERE s.id=NEW.meal_service_session_id AND s.deleted_at IS NULL FOR UPDATE OF s;
 IF session_status<>'OPEN' THEN RAISE EXCEPTION 'Meal attendance can only be captured for an open service session'; END IF;
 IF NEW.outcome='ADMITTED' THEN
   SELECT a.status,a.dining_hall_id,a.dining_plan_id,a.effective_from,a.effective_until
   INTO assignment_status,assignment_hall,assignment_plan,assignment_start,assignment_end
   FROM student_dining_assignments a WHERE a.id=NEW.student_dining_assignment_id AND a.student_id=NEW.student_id AND a.deleted_at IS NULL;
   IF assignment_status<>'ACTIVE' OR assignment_hall<>hall_id OR service_day NOT BETWEEN assignment_start AND assignment_end THEN
     RAISE EXCEPTION 'Student does not have an active assignment for this dining hall and service date'; END IF;
   IF NOT EXISTS (SELECT 1 FROM dining_plan_meals pm WHERE pm.dining_plan_id=assignment_plan AND pm.meal_option_id=option_id
       AND pm.deleted_at IS NULL AND CASE extract(isodow FROM service_day)::integer WHEN 1 THEN pm.monday WHEN 2 THEN pm.tuesday
       WHEN 3 THEN pm.wednesday WHEN 4 THEN pm.thursday WHEN 5 THEN pm.friday WHEN 6 THEN pm.saturday ELSE pm.sunday END) THEN
     RAISE EXCEPTION 'Dining plan does not include this meal option on the service day'; END IF;
   IF (SELECT count(*) FROM meal_attendance_events e WHERE e.meal_service_session_id=NEW.meal_service_session_id
       AND e.outcome='ADMITTED' AND e.deleted_at IS NULL) >= hall_capacity THEN
     RAISE EXCEPTION 'Dining hall service capacity has been reached'; END IF;
 END IF;
 RETURN NEW;
END $$;


--
-- Name: dining_assignment_number_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dining_assignment_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: dining_attendant_assignments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dining_attendant_assignments (
    id uuid NOT NULL,
    dining_hall_id uuid NOT NULL,
    staff_id uuid NOT NULL,
    staff_number character varying(40) NOT NULL,
    staff_name character varying(200) NOT NULL,
    effective_from date NOT NULL,
    effective_until date,
    role_code character varying(30) DEFAULT 'ATTENDANT'::character varying NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_dining_attendant_role CHECK (((role_code)::text = ANY ((ARRAY['ATTENDANT'::character varying, 'SUPERVISOR'::character varying, 'MANAGER'::character varying])::text[]))),
    CONSTRAINT ck_dining_attendant_window CHECK (((effective_until IS NULL) OR (effective_until >= effective_from)))
);


--
-- Name: dining_attendant_assignments_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dining_attendant_assignments_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    dining_hall_id uuid,
    staff_id uuid,
    staff_number character varying(40),
    staff_name character varying(200),
    effective_from date,
    effective_until date,
    role_code character varying(30),
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
-- Name: dining_hall_assignment_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dining_hall_assignment_rules (
    id uuid NOT NULL,
    dining_hall_id uuid NOT NULL,
    rule_dimension character varying(30) NOT NULL,
    comparison_operator character varying(20) NOT NULL,
    comparison_value character varying(200) NOT NULL,
    priority_rank integer DEFAULT 100 NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_dining_assignment_rule_dimension CHECK (((rule_dimension)::text = ANY ((ARRAY['SURNAME_PREFIX'::character varying, 'RESIDENCE_HALL'::character varying, 'PROGRAMME'::character varying, 'STUDENT_GROUP'::character varying])::text[]))),
    CONSTRAINT ck_dining_assignment_rule_operator CHECK (((comparison_operator)::text = ANY ((ARRAY['EQUALS'::character varying, 'STARTS_WITH'::character varying, 'IN'::character varying])::text[]))),
    CONSTRAINT ck_dining_assignment_rule_priority CHECK ((priority_rank > 0))
);


--
-- Name: dining_hall_assignment_rules_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dining_hall_assignment_rules_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    dining_hall_id uuid,
    rule_dimension character varying(30),
    comparison_operator character varying(20),
    comparison_value character varying(200),
    priority_rank integer,
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
-- Name: dining_halls; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dining_halls (
    id uuid NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(160) NOT NULL,
    location_description character varying(300) NOT NULL,
    service_capacity integer NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_dining_hall_capacity CHECK ((service_capacity > 0))
);


--
-- Name: dining_halls_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dining_halls_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code character varying(40),
    name character varying(160),
    location_description character varying(300),
    service_capacity integer,
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
-- Name: dining_plan_meals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dining_plan_meals (
    id uuid NOT NULL,
    dining_plan_id uuid NOT NULL,
    meal_option_id uuid NOT NULL,
    servings_per_service integer DEFAULT 1 NOT NULL,
    monday boolean DEFAULT true NOT NULL,
    tuesday boolean DEFAULT true NOT NULL,
    wednesday boolean DEFAULT true NOT NULL,
    thursday boolean DEFAULT true NOT NULL,
    friday boolean DEFAULT true NOT NULL,
    saturday boolean DEFAULT true NOT NULL,
    sunday boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_dining_plan_servings CHECK ((servings_per_service > 0))
);


--
-- Name: dining_plan_meals_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dining_plan_meals_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    dining_plan_id uuid,
    meal_option_id uuid,
    servings_per_service integer,
    monday boolean,
    tuesday boolean,
    wednesday boolean,
    thursday boolean,
    friday boolean,
    saturday boolean,
    sunday boolean,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: dining_plans; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dining_plans (
    id uuid NOT NULL,
    code character varying(40) NOT NULL,
    plan_version integer NOT NULL,
    name character varying(160) NOT NULL,
    description character varying(500),
    finance_fee_catalogue_id uuid,
    valid_from date NOT NULL,
    valid_until date,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    prepared_by_user_id uuid NOT NULL,
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
    CONSTRAINT ck_dining_plan_approval CHECK (((((status)::text = 'DRAFT'::text) AND (approved_by_user_id IS NULL) AND (approved_at IS NULL) AND (approval_reason IS NULL)) OR (((status)::text <> 'DRAFT'::text) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (length(TRIM(BOTH FROM approval_reason)) > 0) AND (approved_by_user_id <> prepared_by_user_id)))),
    CONSTRAINT ck_dining_plan_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'RETIRED'::character varying])::text[]))),
    CONSTRAINT ck_dining_plan_window CHECK (((plan_version > 0) AND ((valid_until IS NULL) OR (valid_until >= valid_from))))
);


--
-- Name: dining_plans_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dining_plans_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code character varying(40),
    plan_version integer,
    name character varying(160),
    description character varying(500),
    finance_fee_catalogue_id uuid,
    valid_from date,
    valid_until date,
    status character varying(20),
    prepared_by_user_id uuid,
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
-- Name: dining_workflow_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dining_workflow_events (
    id uuid NOT NULL,
    aggregate_type character varying(40) NOT NULL,
    aggregate_id uuid NOT NULL,
    previous_state character varying(30),
    new_state character varying(30) NOT NULL,
    event_type character varying(40) NOT NULL,
    reason character varying(1000) NOT NULL,
    actor_user_id uuid NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_dining_workflow_aggregate CHECK (((aggregate_type)::text = ANY ((ARRAY['DINING_ASSIGNMENT'::character varying, 'DIETARY_REQUIREMENT'::character varying, 'MEAL_SESSION'::character varying])::text[]))),
    CONSTRAINT ck_dining_workflow_reason CHECK ((length(TRIM(BOTH FROM reason)) > 0))
);


--
-- Name: dining_workflow_events_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dining_workflow_events_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    aggregate_type character varying(40),
    aggregate_id uuid,
    previous_state character varying(30),
    new_state character varying(30),
    event_type character varying(40),
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
-- Name: integration_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.integration_outbox (
    id uuid NOT NULL,
    event_type character varying(160) NOT NULL,
    routing_key character varying(160) NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone NOT NULL,
    published_at timestamp with time zone,
    last_error character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_dining_outbox_attempts CHECK ((attempt_count >= 0)),
    CONSTRAINT ck_dining_outbox_publication CHECK (((((status)::text = 'PUBLISHED'::text) AND (published_at IS NOT NULL)) OR (((status)::text <> 'PUBLISHED'::text) AND (published_at IS NULL)))),
    CONSTRAINT ck_dining_outbox_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PUBLISHED'::character varying, 'DEAD'::character varying])::text[])))
);


--
-- Name: meal_attendance_event_number_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.meal_attendance_event_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: meal_attendance_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.meal_attendance_events (
    id uuid NOT NULL,
    event_number character varying(60) NOT NULL,
    meal_service_session_id uuid NOT NULL,
    student_dining_assignment_id uuid,
    student_id uuid NOT NULL,
    student_number character varying(40) NOT NULL,
    student_name character varying(200) NOT NULL,
    outcome character varying(20) NOT NULL,
    denial_reason_code character varying(50),
    denial_reason character varying(1000),
    captured_by_user_id uuid NOT NULL,
    captured_at timestamp with time zone NOT NULL,
    capture_channel character varying(20) NOT NULL,
    device_id character varying(100),
    idempotency_key character varying(120) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_meal_attendance_channel CHECK (((capture_channel)::text = ANY ((ARRAY['ONLINE'::character varying, 'OFFLINE_SYNC'::character varying, 'MANUAL_OVERRIDE'::character varying])::text[]))),
    CONSTRAINT ck_meal_attendance_denial CHECK (((((outcome)::text = 'ADMITTED'::text) AND (denial_reason_code IS NULL) AND (denial_reason IS NULL) AND (student_dining_assignment_id IS NOT NULL)) OR (((outcome)::text = 'DENIED'::text) AND (denial_reason_code IS NOT NULL) AND (length(TRIM(BOTH FROM denial_reason)) > 0)))),
    CONSTRAINT ck_meal_attendance_outcome CHECK (((outcome)::text = ANY ((ARRAY['ADMITTED'::character varying, 'DENIED'::character varying])::text[])))
);


--
-- Name: meal_attendance_events_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.meal_attendance_events_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    event_number character varying(60),
    meal_service_session_id uuid,
    student_dining_assignment_id uuid,
    student_id uuid,
    student_number character varying(40),
    student_name character varying(200),
    outcome character varying(20),
    denial_reason_code character varying(50),
    denial_reason character varying(1000),
    captured_by_user_id uuid,
    captured_at timestamp with time zone,
    capture_channel character varying(20),
    device_id character varying(100),
    idempotency_key character varying(120),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: meal_attendance_reversals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.meal_attendance_reversals (
    id uuid NOT NULL,
    meal_attendance_event_id uuid NOT NULL,
    reason_code character varying(50) NOT NULL,
    reason character varying(1000) NOT NULL,
    reversed_by_user_id uuid NOT NULL,
    reversed_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_meal_reversal_reason CHECK ((length(TRIM(BOTH FROM reason)) > 0))
);


--
-- Name: meal_attendance_reversals_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.meal_attendance_reversals_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    meal_attendance_event_id uuid,
    reason_code character varying(50),
    reason character varying(1000),
    reversed_by_user_id uuid,
    reversed_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: meal_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.meal_options (
    id uuid NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(120) NOT NULL,
    description character varying(500),
    meal_category character varying(20) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_meal_option_category CHECK (((meal_category)::text = ANY ((ARRAY['BREAKFAST'::character varying, 'LUNCH'::character varying, 'DINNER'::character varying, 'OTHER'::character varying])::text[])))
);


--
-- Name: meal_options_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.meal_options_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code character varying(40),
    name character varying(120),
    description character varying(500),
    meal_category character varying(20),
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
-- Name: meal_service_session_number_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.meal_service_session_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: meal_service_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.meal_service_sessions (
    id uuid NOT NULL,
    session_number character varying(60) NOT NULL,
    dining_hall_id uuid NOT NULL,
    meal_option_id uuid NOT NULL,
    service_date date NOT NULL,
    scheduled_opens_at timestamp with time zone NOT NULL,
    scheduled_closes_at timestamp with time zone NOT NULL,
    status character varying(20) DEFAULT 'PLANNED'::character varying NOT NULL,
    prepared_by_user_id uuid NOT NULL,
    opened_by_user_id uuid,
    opened_at timestamp with time zone,
    closed_by_user_id uuid,
    closed_at timestamp with time zone,
    reconciled_by_user_id uuid,
    reconciled_at timestamp with time zone,
    reconciliation_reason character varying(1000),
    expected_servings integer,
    counted_servings integer,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_meal_session_close CHECK (((((status)::text <> ALL ((ARRAY['CLOSED'::character varying, 'RECONCILED'::character varying])::text[])) AND (closed_by_user_id IS NULL) AND (closed_at IS NULL)) OR (((status)::text = ANY ((ARRAY['CLOSED'::character varying, 'RECONCILED'::character varying])::text[])) AND (closed_by_user_id IS NOT NULL) AND (closed_at IS NOT NULL)))),
    CONSTRAINT ck_meal_session_counts CHECK ((((expected_servings IS NULL) OR (expected_servings >= 0)) AND ((counted_servings IS NULL) OR (counted_servings >= 0)))),
    CONSTRAINT ck_meal_session_open CHECK (((((status)::text = ANY ((ARRAY['PLANNED'::character varying, 'CANCELLED'::character varying])::text[])) AND (opened_by_user_id IS NULL) AND (opened_at IS NULL)) OR (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'CLOSED'::character varying, 'RECONCILED'::character varying])::text[])) AND (opened_by_user_id IS NOT NULL) AND (opened_at IS NOT NULL) AND (opened_by_user_id <> prepared_by_user_id)))),
    CONSTRAINT ck_meal_session_reconcile CHECK (((((status)::text <> 'RECONCILED'::text) AND (reconciled_by_user_id IS NULL) AND (reconciled_at IS NULL) AND (reconciliation_reason IS NULL)) OR (((status)::text = 'RECONCILED'::text) AND (reconciled_by_user_id IS NOT NULL) AND (reconciled_at IS NOT NULL) AND (length(TRIM(BOTH FROM reconciliation_reason)) > 0) AND (counted_servings IS NOT NULL) AND (reconciled_by_user_id <> opened_by_user_id)))),
    CONSTRAINT ck_meal_session_status CHECK (((status)::text = ANY ((ARRAY['PLANNED'::character varying, 'OPEN'::character varying, 'CLOSED'::character varying, 'RECONCILED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT ck_meal_session_window CHECK ((scheduled_closes_at > scheduled_opens_at))
);


--
-- Name: meal_service_sessions_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.meal_service_sessions_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    session_number character varying(60),
    dining_hall_id uuid,
    meal_option_id uuid,
    service_date date,
    scheduled_opens_at timestamp with time zone,
    scheduled_closes_at timestamp with time zone,
    status character varying(20),
    prepared_by_user_id uuid,
    opened_by_user_id uuid,
    opened_at timestamp with time zone,
    closed_by_user_id uuid,
    closed_at timestamp with time zone,
    reconciled_by_user_id uuid,
    reconciled_at timestamp with time zone,
    reconciliation_reason character varying(1000),
    expected_servings integer,
    counted_servings integer,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: meal_service_times; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.meal_service_times (
    id uuid NOT NULL,
    dining_hall_id uuid NOT NULL,
    meal_option_id uuid NOT NULL,
    day_of_week smallint NOT NULL,
    service_opens_at time without time zone NOT NULL,
    service_closes_at time without time zone NOT NULL,
    grace_closes_at time without time zone NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_meal_service_day CHECK (((day_of_week >= 1) AND (day_of_week <= 7))),
    CONSTRAINT ck_meal_service_window CHECK (((service_closes_at > service_opens_at) AND (grace_closes_at >= service_closes_at)))
);


--
-- Name: meal_service_times_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.meal_service_times_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    dining_hall_id uuid,
    meal_option_id uuid,
    day_of_week smallint,
    service_opens_at time without time zone,
    service_closes_at time without time zone,
    grace_closes_at time without time zone,
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
-- Name: revinfo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.revinfo (
    rev integer NOT NULL,
    revtstmp bigint NOT NULL,
    actor_user_id uuid,
    service_name character varying(100) DEFAULT 'dining-service'::character varying NOT NULL,
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
-- Name: student_dietary_requirements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_dietary_requirements (
    id uuid NOT NULL,
    student_id uuid NOT NULL,
    student_number character varying(40) NOT NULL,
    requirement_code character varying(50) NOT NULL,
    description character varying(1000) NOT NULL,
    severity character varying(20) NOT NULL,
    clinical_document_id uuid,
    effective_from date NOT NULL,
    effective_until date,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    recorded_by_user_id uuid NOT NULL,
    resolved_by_user_id uuid,
    resolved_at timestamp with time zone,
    resolution_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_dietary_requirement_resolution CHECK (((((status)::text = 'ACTIVE'::text) AND (resolved_by_user_id IS NULL) AND (resolved_at IS NULL) AND (resolution_reason IS NULL)) OR (((status)::text <> 'ACTIVE'::text) AND (resolved_by_user_id IS NOT NULL) AND (resolved_at IS NOT NULL) AND (length(TRIM(BOTH FROM resolution_reason)) > 0) AND (resolved_by_user_id <> recorded_by_user_id)))),
    CONSTRAINT ck_dietary_requirement_severity CHECK (((severity)::text = ANY ((ARRAY['INFORMATION'::character varying, 'IMPORTANT'::character varying, 'CRITICAL'::character varying])::text[]))),
    CONSTRAINT ck_dietary_requirement_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'RESOLVED'::character varying, 'EXPIRED'::character varying])::text[]))),
    CONSTRAINT ck_dietary_requirement_window CHECK (((effective_until IS NULL) OR (effective_until >= effective_from)))
);


--
-- Name: student_dietary_requirements_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_dietary_requirements_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    student_id uuid,
    student_number character varying(40),
    requirement_code character varying(50),
    description character varying(1000),
    severity character varying(20),
    clinical_document_id uuid,
    effective_from date,
    effective_until date,
    status character varying(20),
    recorded_by_user_id uuid,
    resolved_by_user_id uuid,
    resolved_at timestamp with time zone,
    resolution_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: student_dining_assignments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_dining_assignments (
    id uuid NOT NULL,
    assignment_number character varying(60) NOT NULL,
    student_id uuid NOT NULL,
    student_number character varying(40) NOT NULL,
    student_name character varying(200) NOT NULL,
    academic_period_id uuid NOT NULL,
    academic_period_code character varying(50) NOT NULL,
    dining_hall_id uuid NOT NULL,
    dining_plan_id uuid NOT NULL,
    accommodation_allocation_id uuid,
    effective_from date NOT NULL,
    effective_until date NOT NULL,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    prepared_by_user_id uuid NOT NULL,
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    approval_reason character varying(1000),
    ended_by_user_id uuid,
    ended_at timestamp with time zone,
    end_reason character varying(1000),
    billing_event_id uuid,
    billing_status character varying(20) DEFAULT 'NOT_REQUESTED'::character varying NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    programme_code character varying(50),
    student_group_code character varying(80),
    CONSTRAINT ck_student_dining_assignment_approval CHECK (((((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'CANCELLED'::character varying])::text[])) AND (approved_by_user_id IS NULL) AND (approved_at IS NULL) AND (approval_reason IS NULL)) OR (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'SUSPENDED'::character varying, 'ENDED'::character varying])::text[])) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (length(TRIM(BOTH FROM approval_reason)) > 0) AND (approved_by_user_id <> prepared_by_user_id)))),
    CONSTRAINT ck_student_dining_assignment_end CHECK (((((status)::text <> ALL ((ARRAY['ENDED'::character varying, 'CANCELLED'::character varying])::text[])) AND (ended_by_user_id IS NULL) AND (ended_at IS NULL) AND (end_reason IS NULL)) OR (((status)::text = ANY ((ARRAY['ENDED'::character varying, 'CANCELLED'::character varying])::text[])) AND (ended_by_user_id IS NOT NULL) AND (ended_at IS NOT NULL) AND (length(TRIM(BOTH FROM end_reason)) > 0)))),
    CONSTRAINT ck_student_dining_assignment_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'SUSPENDED'::character varying, 'ENDED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT ck_student_dining_assignment_window CHECK ((effective_until >= effective_from)),
    CONSTRAINT ck_student_dining_billing CHECK (((billing_status)::text = ANY ((ARRAY['NOT_REQUESTED'::character varying, 'PENDING'::character varying, 'ACCEPTED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: student_dining_assignments_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_dining_assignments_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    assignment_number character varying(60),
    student_id uuid,
    student_number character varying(40),
    student_name character varying(200),
    academic_period_id uuid,
    academic_period_code character varying(50),
    dining_hall_id uuid,
    dining_plan_id uuid,
    accommodation_allocation_id uuid,
    effective_from date,
    effective_until date,
    status character varying(20),
    prepared_by_user_id uuid,
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    approval_reason character varying(1000),
    ended_by_user_id uuid,
    ended_at timestamp with time zone,
    end_reason character varying(1000),
    billing_event_id uuid,
    billing_status character varying(20),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    programme_code character varying(50),
    student_group_code character varying(80)
);


--
-- Data for Name: dining_attendant_assignments; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: dining_attendant_assignments_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: dining_hall_assignment_rules; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: dining_hall_assignment_rules_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: dining_halls; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: dining_halls_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: dining_plan_meals; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: dining_plan_meals_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: dining_plans; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: dining_plans_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: dining_workflow_events; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: dining_workflow_events_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: integration_outbox; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: meal_attendance_events; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: meal_attendance_events_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: meal_attendance_reversals; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: meal_attendance_reversals_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: meal_options; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: meal_options_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: meal_service_sessions; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: meal_service_sessions_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: meal_service_times; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: meal_service_times_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: revinfo; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_dietary_requirements; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_dietary_requirements_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_dining_assignments; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_dining_assignments_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Name: dining_assignment_number_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.dining_assignment_number_seq', 1, false);


--
-- Name: meal_attendance_event_number_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.meal_attendance_event_number_seq', 1, false);


--
-- Name: meal_service_session_number_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.meal_service_session_number_seq', 1, false);


--
-- Name: revinfo_rev_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.revinfo_rev_seq', 1, false);


--
-- Name: dining_attendant_assignments_aud dining_attendant_assignments_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_attendant_assignments_aud
    ADD CONSTRAINT dining_attendant_assignments_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: dining_attendant_assignments dining_attendant_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_attendant_assignments
    ADD CONSTRAINT dining_attendant_assignments_pkey PRIMARY KEY (id);


--
-- Name: dining_hall_assignment_rules_aud dining_hall_assignment_rules_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_hall_assignment_rules_aud
    ADD CONSTRAINT dining_hall_assignment_rules_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: dining_hall_assignment_rules dining_hall_assignment_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_hall_assignment_rules
    ADD CONSTRAINT dining_hall_assignment_rules_pkey PRIMARY KEY (id);


--
-- Name: dining_halls_aud dining_halls_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_halls_aud
    ADD CONSTRAINT dining_halls_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: dining_halls dining_halls_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_halls
    ADD CONSTRAINT dining_halls_pkey PRIMARY KEY (id);


--
-- Name: dining_plan_meals_aud dining_plan_meals_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_plan_meals_aud
    ADD CONSTRAINT dining_plan_meals_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: dining_plan_meals dining_plan_meals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_plan_meals
    ADD CONSTRAINT dining_plan_meals_pkey PRIMARY KEY (id);


--
-- Name: dining_plans_aud dining_plans_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_plans_aud
    ADD CONSTRAINT dining_plans_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: dining_plans dining_plans_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_plans
    ADD CONSTRAINT dining_plans_pkey PRIMARY KEY (id);


--
-- Name: dining_workflow_events_aud dining_workflow_events_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_workflow_events_aud
    ADD CONSTRAINT dining_workflow_events_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: dining_workflow_events dining_workflow_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_workflow_events
    ADD CONSTRAINT dining_workflow_events_pkey PRIMARY KEY (id);


--
-- Name: integration_outbox integration_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_outbox
    ADD CONSTRAINT integration_outbox_pkey PRIMARY KEY (id);


--
-- Name: meal_attendance_events_aud meal_attendance_events_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_attendance_events_aud
    ADD CONSTRAINT meal_attendance_events_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: meal_attendance_events meal_attendance_events_event_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_attendance_events
    ADD CONSTRAINT meal_attendance_events_event_number_key UNIQUE (event_number);


--
-- Name: meal_attendance_events meal_attendance_events_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_attendance_events
    ADD CONSTRAINT meal_attendance_events_idempotency_key_key UNIQUE (idempotency_key);


--
-- Name: meal_attendance_events meal_attendance_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_attendance_events
    ADD CONSTRAINT meal_attendance_events_pkey PRIMARY KEY (id);


--
-- Name: meal_attendance_reversals_aud meal_attendance_reversals_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_attendance_reversals_aud
    ADD CONSTRAINT meal_attendance_reversals_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: meal_attendance_reversals meal_attendance_reversals_meal_attendance_event_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_attendance_reversals
    ADD CONSTRAINT meal_attendance_reversals_meal_attendance_event_id_key UNIQUE (meal_attendance_event_id);


--
-- Name: meal_attendance_reversals meal_attendance_reversals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_attendance_reversals
    ADD CONSTRAINT meal_attendance_reversals_pkey PRIMARY KEY (id);


--
-- Name: meal_options_aud meal_options_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_options_aud
    ADD CONSTRAINT meal_options_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: meal_options meal_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_options
    ADD CONSTRAINT meal_options_pkey PRIMARY KEY (id);


--
-- Name: meal_service_sessions_aud meal_service_sessions_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_service_sessions_aud
    ADD CONSTRAINT meal_service_sessions_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: meal_service_sessions meal_service_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_service_sessions
    ADD CONSTRAINT meal_service_sessions_pkey PRIMARY KEY (id);


--
-- Name: meal_service_sessions meal_service_sessions_session_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_service_sessions
    ADD CONSTRAINT meal_service_sessions_session_number_key UNIQUE (session_number);


--
-- Name: meal_service_times_aud meal_service_times_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_service_times_aud
    ADD CONSTRAINT meal_service_times_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: meal_service_times meal_service_times_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_service_times
    ADD CONSTRAINT meal_service_times_pkey PRIMARY KEY (id);


--
-- Name: revinfo revinfo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revinfo
    ADD CONSTRAINT revinfo_pkey PRIMARY KEY (rev);


--
-- Name: student_dietary_requirements_aud student_dietary_requirements_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_dietary_requirements_aud
    ADD CONSTRAINT student_dietary_requirements_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: student_dietary_requirements student_dietary_requirements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_dietary_requirements
    ADD CONSTRAINT student_dietary_requirements_pkey PRIMARY KEY (id);


--
-- Name: student_dining_assignments student_dining_assignments_assignment_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_dining_assignments
    ADD CONSTRAINT student_dining_assignments_assignment_number_key UNIQUE (assignment_number);


--
-- Name: student_dining_assignments_aud student_dining_assignments_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_dining_assignments_aud
    ADD CONSTRAINT student_dining_assignments_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: student_dining_assignments student_dining_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_dining_assignments
    ADD CONSTRAINT student_dining_assignments_pkey PRIMARY KEY (id);


--
-- Name: dining_plans uk_dining_plan_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_plans
    ADD CONSTRAINT uk_dining_plan_version UNIQUE (code, plan_version);


--
-- Name: idx_dining_outbox_dispatch; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dining_outbox_dispatch ON public.integration_outbox USING btree (next_attempt_at, occurred_at, id) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: idx_dining_workflow_aggregate; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dining_workflow_aggregate ON public.dining_workflow_events USING btree (aggregate_type, aggregate_id, occurred_at, id);


--
-- Name: ix_dining_assignment_programme; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_dining_assignment_programme ON public.student_dining_assignments USING btree (programme_code, academic_period_code) WHERE (deleted_at IS NULL);


--
-- Name: ix_dining_assignment_student_group; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_dining_assignment_student_group ON public.student_dining_assignments USING btree (student_group_code, academic_period_code) WHERE ((deleted_at IS NULL) AND (student_group_code IS NOT NULL));


--
-- Name: uk_active_dietary_requirement; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_active_dietary_requirement ON public.student_dietary_requirements USING btree (student_id, lower((requirement_code)::text)) WHERE (((status)::text = 'ACTIVE'::text) AND (deleted_at IS NULL));


--
-- Name: uk_active_dining_attendant; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_active_dining_attendant ON public.dining_attendant_assignments USING btree (dining_hall_id, staff_id) WHERE (active AND (deleted_at IS NULL));


--
-- Name: uk_active_dining_plan; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_active_dining_plan ON public.dining_plans USING btree (lower((code)::text)) WHERE (((status)::text = 'ACTIVE'::text) AND (deleted_at IS NULL));


--
-- Name: uk_active_student_dining_assignment; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_active_student_dining_assignment ON public.student_dining_assignments USING btree (student_id, academic_period_id) WHERE (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'SUSPENDED'::character varying])::text[])) AND (deleted_at IS NULL));


--
-- Name: uk_admitted_meal_per_session; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_admitted_meal_per_session ON public.meal_attendance_events USING btree (meal_service_session_id, student_id) WHERE (((outcome)::text = 'ADMITTED'::text) AND (deleted_at IS NULL));


--
-- Name: uk_dining_hall_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_dining_hall_code ON public.dining_halls USING btree (lower((code)::text)) WHERE (deleted_at IS NULL);


--
-- Name: uk_dining_plan_meal; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_dining_plan_meal ON public.dining_plan_meals USING btree (dining_plan_id, meal_option_id) WHERE (deleted_at IS NULL);


--
-- Name: uk_meal_option_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_meal_option_code ON public.meal_options USING btree (lower((code)::text)) WHERE (deleted_at IS NULL);


--
-- Name: uk_meal_service_session; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_meal_service_session ON public.meal_service_sessions USING btree (dining_hall_id, meal_option_id, service_date) WHERE (((status)::text <> 'CANCELLED'::text) AND (deleted_at IS NULL));


--
-- Name: uk_meal_service_time; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_meal_service_time ON public.meal_service_times USING btree (dining_hall_id, meal_option_id, day_of_week) WHERE (deleted_at IS NULL);


--
-- Name: dining_workflow_events trg_protect_dining_workflow_event; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_dining_workflow_event BEFORE DELETE OR UPDATE ON public.dining_workflow_events FOR EACH ROW EXECUTE FUNCTION public.protect_dining_workflow_evidence();


--
-- Name: meal_attendance_events trg_protect_meal_attendance; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_meal_attendance BEFORE DELETE OR UPDATE ON public.meal_attendance_events FOR EACH ROW EXECUTE FUNCTION public.protect_dining_evidence();


--
-- Name: meal_attendance_reversals trg_protect_meal_reversal; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_meal_reversal BEFORE DELETE OR UPDATE ON public.meal_attendance_reversals FOR EACH ROW EXECUTE FUNCTION public.protect_dining_evidence();


--
-- Name: meal_attendance_events trg_validate_meal_attendance; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_meal_attendance BEFORE INSERT ON public.meal_attendance_events FOR EACH ROW EXECUTE FUNCTION public.validate_meal_attendance();


--
-- Name: dining_attendant_assignments_aud dining_attendant_assignments_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_attendant_assignments_aud
    ADD CONSTRAINT dining_attendant_assignments_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: dining_attendant_assignments dining_attendant_assignments_dining_hall_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_attendant_assignments
    ADD CONSTRAINT dining_attendant_assignments_dining_hall_id_fkey FOREIGN KEY (dining_hall_id) REFERENCES public.dining_halls(id);


--
-- Name: dining_hall_assignment_rules_aud dining_hall_assignment_rules_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_hall_assignment_rules_aud
    ADD CONSTRAINT dining_hall_assignment_rules_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: dining_hall_assignment_rules dining_hall_assignment_rules_dining_hall_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_hall_assignment_rules
    ADD CONSTRAINT dining_hall_assignment_rules_dining_hall_id_fkey FOREIGN KEY (dining_hall_id) REFERENCES public.dining_halls(id);


--
-- Name: dining_halls_aud dining_halls_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_halls_aud
    ADD CONSTRAINT dining_halls_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: dining_plan_meals_aud dining_plan_meals_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_plan_meals_aud
    ADD CONSTRAINT dining_plan_meals_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: dining_plan_meals dining_plan_meals_dining_plan_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_plan_meals
    ADD CONSTRAINT dining_plan_meals_dining_plan_id_fkey FOREIGN KEY (dining_plan_id) REFERENCES public.dining_plans(id);


--
-- Name: dining_plan_meals dining_plan_meals_meal_option_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_plan_meals
    ADD CONSTRAINT dining_plan_meals_meal_option_id_fkey FOREIGN KEY (meal_option_id) REFERENCES public.meal_options(id);


--
-- Name: dining_plans_aud dining_plans_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_plans_aud
    ADD CONSTRAINT dining_plans_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: dining_workflow_events_aud dining_workflow_events_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_workflow_events_aud
    ADD CONSTRAINT dining_workflow_events_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: meal_attendance_events_aud meal_attendance_events_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_attendance_events_aud
    ADD CONSTRAINT meal_attendance_events_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: meal_attendance_events meal_attendance_events_meal_service_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_attendance_events
    ADD CONSTRAINT meal_attendance_events_meal_service_session_id_fkey FOREIGN KEY (meal_service_session_id) REFERENCES public.meal_service_sessions(id);


--
-- Name: meal_attendance_events meal_attendance_events_student_dining_assignment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_attendance_events
    ADD CONSTRAINT meal_attendance_events_student_dining_assignment_id_fkey FOREIGN KEY (student_dining_assignment_id) REFERENCES public.student_dining_assignments(id);


--
-- Name: meal_attendance_reversals_aud meal_attendance_reversals_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_attendance_reversals_aud
    ADD CONSTRAINT meal_attendance_reversals_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: meal_attendance_reversals meal_attendance_reversals_meal_attendance_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_attendance_reversals
    ADD CONSTRAINT meal_attendance_reversals_meal_attendance_event_id_fkey FOREIGN KEY (meal_attendance_event_id) REFERENCES public.meal_attendance_events(id);


--
-- Name: meal_options_aud meal_options_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_options_aud
    ADD CONSTRAINT meal_options_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: meal_service_sessions_aud meal_service_sessions_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_service_sessions_aud
    ADD CONSTRAINT meal_service_sessions_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: meal_service_sessions meal_service_sessions_dining_hall_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_service_sessions
    ADD CONSTRAINT meal_service_sessions_dining_hall_id_fkey FOREIGN KEY (dining_hall_id) REFERENCES public.dining_halls(id);


--
-- Name: meal_service_sessions meal_service_sessions_meal_option_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_service_sessions
    ADD CONSTRAINT meal_service_sessions_meal_option_id_fkey FOREIGN KEY (meal_option_id) REFERENCES public.meal_options(id);


--
-- Name: meal_service_times_aud meal_service_times_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_service_times_aud
    ADD CONSTRAINT meal_service_times_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: meal_service_times meal_service_times_dining_hall_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_service_times
    ADD CONSTRAINT meal_service_times_dining_hall_id_fkey FOREIGN KEY (dining_hall_id) REFERENCES public.dining_halls(id);


--
-- Name: meal_service_times meal_service_times_meal_option_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_service_times
    ADD CONSTRAINT meal_service_times_meal_option_id_fkey FOREIGN KEY (meal_option_id) REFERENCES public.meal_options(id);


--
-- Name: student_dietary_requirements_aud student_dietary_requirements_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_dietary_requirements_aud
    ADD CONSTRAINT student_dietary_requirements_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: student_dining_assignments_aud student_dining_assignments_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_dining_assignments_aud
    ADD CONSTRAINT student_dining_assignments_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: student_dining_assignments student_dining_assignments_dining_hall_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_dining_assignments
    ADD CONSTRAINT student_dining_assignments_dining_hall_id_fkey FOREIGN KEY (dining_hall_id) REFERENCES public.dining_halls(id);


--
-- Name: student_dining_assignments student_dining_assignments_dining_plan_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_dining_assignments
    ADD CONSTRAINT student_dining_assignments_dining_plan_id_fkey FOREIGN KEY (dining_plan_id) REFERENCES public.dining_plans(id);


--
-- PostgreSQL database dump complete
--


