-- Author: Tinashe K
-- Canonical clean-slate baseline for accommodation-service.

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
-- Name: protect_accommodation_evidence(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_accommodation_evidence() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Accommodation workflow evidence is append-only'; END IF;
 IF ROW(NEW.room_allocation_id,NEW.previous_status,NEW.new_status,NEW.event_type,NEW.from_room_id,NEW.to_room_id,
      NEW.reason,NEW.actor_user_id,NEW.occurred_at)
    IS DISTINCT FROM ROW(OLD.room_allocation_id,OLD.previous_status,OLD.new_status,OLD.event_type,OLD.from_room_id,OLD.to_room_id,
      OLD.reason,OLD.actor_user_id,OLD.occurred_at) THEN RAISE EXCEPTION 'Accommodation workflow evidence is immutable'; END IF;
 RETURN NEW;
END $$;


--
-- Name: protect_approved_accommodation_configuration(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_approved_accommodation_configuration() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
 IF OLD.status<>'DRAFT' AND ROW(NEW.academic_period_id,NEW.academic_period_code,NEW.code,NEW.name,
      NEW.applications_open_at,NEW.applications_close_at,NEW.occupancy_starts_on,NEW.occupancy_ends_on,NEW.allocation_cutoff_at)
    IS DISTINCT FROM ROW(OLD.academic_period_id,OLD.academic_period_code,OLD.code,OLD.name,
      OLD.applications_open_at,OLD.applications_close_at,OLD.occupancy_starts_on,OLD.occupancy_ends_on,OLD.allocation_cutoff_at) THEN
   RAISE EXCEPTION 'Approved accommodation period configuration is immutable'; END IF;
 RETURN NEW;
END $$;


--
-- Name: validate_accommodation_application_period(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_accommodation_application_period() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE period_status varchar(30); opens_at timestamptz; closes_at timestamptz;
BEGIN
 SELECT status,applications_open_at,applications_close_at INTO period_status,opens_at,closes_at
 FROM accommodation_application_periods WHERE id=NEW.application_period_id AND deleted_at IS NULL;
 IF period_status <> 'APPLICATION_OPEN' OR NEW.submitted_at < opens_at OR NEW.submitted_at > closes_at THEN
   RAISE EXCEPTION 'Accommodation application period is not open at the submitted time'; END IF;
 IF EXISTS (SELECT 1 FROM accommodation_blacklist_entries b WHERE b.student_id=NEW.student_id AND b.status='ACTIVE'
      AND b.deleted_at IS NULL AND NEW.submitted_at::date >= b.effective_from
      AND (b.effective_until IS NULL OR NEW.submitted_at::date <= b.effective_until)) THEN
   RAISE EXCEPTION 'Blacklisted student cannot submit an accommodation application'; END IF;
 RETURN NEW;
END $$;


--
-- Name: validate_accommodation_waitlist_period(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_accommodation_waitlist_period() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE actual_period_id uuid;
BEGIN
 SELECT application_period_id INTO actual_period_id FROM accommodation_applications
 WHERE id=NEW.accommodation_application_id AND deleted_at IS NULL;
 IF actual_period_id IS NULL OR actual_period_id<>NEW.application_period_id THEN
   RAISE EXCEPTION 'Wait-list entry period must match its accommodation application'; END IF;
 RETURN NEW;
END $$;


--
-- Name: validate_room_allocation_capacity(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_room_allocation_capacity() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE room_capacity integer; room_condition varchar(20); room_active boolean; hall_active boolean;
 premise_active boolean; hall_gender varchar(20); reserved_group_id uuid; app_gender varchar(20);
 app_status varchar(30); app_period uuid; selected_group_id uuid; rate_period uuid; rate_room_type uuid;
 actual_room_type uuid; period_status varchar(30); period_occupancy_start date; period_occupancy_end date;
 allocation_cutoff timestamptz; rate_effective_from timestamptz; rate_effective_until timestamptz;
BEGIN
 SELECT r.capacity,r.condition_status,r.active,h.active,p.active,h.resident_gender_policy,r.room_type_id,r.reserved_for_group_id
 INTO room_capacity,room_condition,room_active,hall_active,premise_active,hall_gender,actual_room_type,reserved_group_id
 FROM accommodation_rooms r JOIN residence_halls h ON h.id=r.residence_hall_id
 JOIN accommodation_premises p ON p.id=h.premise_id
 WHERE r.id=NEW.room_id AND r.deleted_at IS NULL AND h.deleted_at IS NULL AND p.deleted_at IS NULL
 FOR UPDATE OF r;
 SELECT a.gender_code,a.status,a.application_period_id,a.selected_group_id
 INTO app_gender,app_status,app_period,selected_group_id
 FROM accommodation_applications a WHERE a.id=NEW.accommodation_application_id AND a.deleted_at IS NULL;
 SELECT application_period_id,room_type_id,effective_from,effective_until
 INTO rate_period,rate_room_type,rate_effective_from,rate_effective_until FROM accommodation_rates
 WHERE id=NEW.accommodation_rate_id AND status='ACTIVE' AND rating_status='RATED' AND deleted_at IS NULL;
 SELECT status,occupancy_starts_on,occupancy_ends_on,allocation_cutoff_at
 INTO period_status,period_occupancy_start,period_occupancy_end,allocation_cutoff
 FROM accommodation_application_periods WHERE id=app_period AND deleted_at IS NULL;
 IF room_capacity IS NULL OR NOT room_active OR NOT hall_active OR NOT premise_active OR room_condition<>'AVAILABLE' THEN
   RAISE EXCEPTION 'Room is not available for allocation'; END IF;
 IF app_status NOT IN ('ELIGIBLE','WAITLISTED') AND NOT (TG_OP='UPDATE' AND app_status='ALLOCATED') THEN
   RAISE EXCEPTION 'Only an eligible, waitlisted, or already allocated application can continue allocation'; END IF;
 IF period_status NOT IN ('APPLICATION_CLOSED','ALLOCATION_ACTIVE') OR NEW.allocated_at>allocation_cutoff THEN
   RAISE EXCEPTION 'Accommodation allocation is outside the approved allocation window'; END IF;
 IF NEW.occupancy_starts_on<period_occupancy_start OR NEW.occupancy_ends_on>period_occupancy_end THEN
   RAISE EXCEPTION 'Occupancy dates must remain within the accommodation application period'; END IF;
 IF app_period<>rate_period OR rate_room_type<>actual_room_type THEN RAISE EXCEPTION 'Accommodation rate does not match the application period and room type'; END IF;
 IF NEW.allocated_at<rate_effective_from OR (rate_effective_until IS NOT NULL AND NEW.allocated_at>=rate_effective_until) THEN
   RAISE EXCEPTION 'Accommodation rate is not effective at the allocation time'; END IF;
 IF hall_gender<>'ANY' AND hall_gender<>app_gender THEN RAISE EXCEPTION 'Student gender does not satisfy the residence hall policy'; END IF;
 IF reserved_group_id IS NOT NULL AND reserved_group_id IS DISTINCT FROM selected_group_id THEN
   RAISE EXCEPTION 'Room is reserved for a different accommodation group'; END IF;
 IF EXISTS (SELECT 1 FROM accommodation_blacklist_entries b JOIN accommodation_applications a ON a.student_id=b.student_id
      WHERE a.id=NEW.accommodation_application_id AND b.status='ACTIVE' AND b.deleted_at IS NULL
      AND NEW.occupancy_starts_on >= b.effective_from AND (b.effective_until IS NULL OR NEW.occupancy_starts_on <= b.effective_until)) THEN
   RAISE EXCEPTION 'Blacklisted student cannot be allocated'; END IF;
 IF (SELECT count(*) FROM room_allocations other_allocation WHERE other_allocation.room_id=NEW.room_id
      AND other_allocation.id<>NEW.id AND other_allocation.deleted_at IS NULL
      AND other_allocation.status IN ('PROPOSED','ALLOCATED','CHECKED_IN')
      AND daterange(other_allocation.occupancy_starts_on,other_allocation.occupancy_ends_on,'[]') &&
          daterange(NEW.occupancy_starts_on,NEW.occupancy_ends_on,'[]')) >= room_capacity THEN
   RAISE EXCEPTION 'Room capacity is exhausted for the requested occupancy period'; END IF;
 RETURN NEW;
END $$;


--
-- Name: validate_room_swap(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_room_swap() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE first_status varchar(30); second_status varchar(30); first_room uuid; second_room uuid;
BEGIN
 SELECT status,room_id INTO first_status,first_room FROM room_allocations
 WHERE id=NEW.first_allocation_id AND deleted_at IS NULL FOR UPDATE;
 SELECT status,room_id INTO second_status,second_room FROM room_allocations
 WHERE id=NEW.second_allocation_id AND deleted_at IS NULL FOR UPDATE;
 IF TG_OP='INSERT' THEN
   IF first_status<>'CHECKED_IN' OR second_status<>'CHECKED_IN' THEN
     RAISE EXCEPTION 'Only checked-in room allocations can be swapped'; END IF;
   IF first_room<>NEW.first_original_room_id OR second_room<>NEW.second_original_room_id THEN
     RAISE EXCEPTION 'Room swap must preserve the original room evidence'; END IF;
 END IF;
 IF NEW.status='COMPLETED' AND (first_room<>NEW.second_original_room_id OR second_room<>NEW.first_original_room_id) THEN
   RAISE EXCEPTION 'Both room allocations must be exchanged before completing the swap'; END IF;
 RETURN NEW;
END $$;


--
-- Name: accommodation_allocation_number_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.accommodation_allocation_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: accommodation_application_number_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.accommodation_application_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: accommodation_application_periods; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_application_periods (
    id uuid NOT NULL,
    academic_period_id uuid NOT NULL,
    academic_period_code character varying(50) NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(160) NOT NULL,
    applications_open_at timestamp with time zone NOT NULL,
    applications_close_at timestamp with time zone CONSTRAINT accommodation_application_period_applications_close_at_not_null NOT NULL,
    occupancy_starts_on date NOT NULL,
    occupancy_ends_on date NOT NULL,
    allocation_cutoff_at timestamp with time zone NOT NULL,
    status character varying(30) DEFAULT 'DRAFT'::character varying NOT NULL,
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
    CONSTRAINT ck_accommodation_period_approval CHECK (((((status)::text = 'DRAFT'::text) AND (approved_by_user_id IS NULL) AND (approved_at IS NULL) AND (approval_reason IS NULL)) OR (((status)::text <> 'DRAFT'::text) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (length(TRIM(BOTH FROM approval_reason)) > 0) AND (approved_by_user_id <> prepared_by_user_id)))),
    CONSTRAINT ck_accommodation_period_dates CHECK (((applications_close_at > applications_open_at) AND (occupancy_ends_on >= occupancy_starts_on) AND (allocation_cutoff_at >= applications_close_at))),
    CONSTRAINT ck_accommodation_period_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'APPLICATION_OPEN'::character varying, 'APPLICATION_CLOSED'::character varying, 'ALLOCATION_ACTIVE'::character varying, 'CLOSED'::character varying])::text[])))
);


--
-- Name: accommodation_application_periods_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_application_periods_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    academic_period_id uuid,
    academic_period_code character varying(50),
    code character varying(40),
    name character varying(160),
    applications_open_at timestamp with time zone,
    applications_close_at timestamp with time zone,
    occupancy_starts_on date,
    occupancy_ends_on date,
    allocation_cutoff_at timestamp with time zone,
    status character varying(30),
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
-- Name: accommodation_applications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_applications (
    id uuid NOT NULL,
    application_number character varying(60) NOT NULL,
    application_period_id uuid NOT NULL,
    student_id uuid NOT NULL,
    student_number character varying(40) NOT NULL,
    student_name character varying(200) NOT NULL,
    primary_email character varying(254) NOT NULL,
    gender_code character varying(20) NOT NULL,
    disability_code character varying(80),
    country_code character(3) NOT NULL,
    location_code character varying(80),
    programme_id uuid NOT NULL,
    programme_code character varying(50) NOT NULL,
    programme_name character varying(200) NOT NULL,
    programme_level integer NOT NULL,
    sponsor_code character varying(80),
    payment_state character varying(30) NOT NULL,
    preferred_room_type_id uuid,
    special_requirements character varying(1000),
    priority_score integer DEFAULT 0 NOT NULL,
    status character varying(30) DEFAULT 'SUBMITTED'::character varying NOT NULL,
    submitted_at timestamp with time zone NOT NULL,
    evaluated_by_user_id uuid,
    evaluated_at timestamp with time zone,
    evaluation_reason character varying(1000),
    selected_group_id uuid,
    withdrawn_by_user_id uuid,
    withdrawn_at timestamp with time zone,
    withdrawal_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_accommodation_application_evaluation CHECK (((((status)::text = 'SUBMITTED'::text) AND (evaluated_by_user_id IS NULL) AND (evaluated_at IS NULL) AND (evaluation_reason IS NULL)) OR (((status)::text = ANY ((ARRAY['ELIGIBLE'::character varying, 'WAITLISTED'::character varying, 'ALLOCATED'::character varying, 'REJECTED'::character varying])::text[])) AND (evaluated_by_user_id IS NOT NULL) AND (evaluated_at IS NOT NULL) AND (length(TRIM(BOTH FROM evaluation_reason)) > 0)) OR ((status)::text = 'WITHDRAWN'::text))),
    CONSTRAINT ck_accommodation_application_level CHECK ((programme_level > 0)),
    CONSTRAINT ck_accommodation_application_payment CHECK (((payment_state)::text = ANY ((ARRAY['PAID'::character varying, 'WAIVED'::character varying, 'PART_PAID'::character varying, 'UNPAID'::character varying, 'UNKNOWN'::character varying])::text[]))),
    CONSTRAINT ck_accommodation_application_status CHECK (((status)::text = ANY ((ARRAY['SUBMITTED'::character varying, 'ELIGIBLE'::character varying, 'WAITLISTED'::character varying, 'ALLOCATED'::character varying, 'REJECTED'::character varying, 'WITHDRAWN'::character varying])::text[]))),
    CONSTRAINT ck_accommodation_application_withdrawal CHECK (((((status)::text <> 'WITHDRAWN'::text) AND (withdrawn_by_user_id IS NULL) AND (withdrawn_at IS NULL) AND (withdrawal_reason IS NULL)) OR (((status)::text = 'WITHDRAWN'::text) AND (withdrawn_by_user_id IS NOT NULL) AND (withdrawn_at IS NOT NULL) AND (length(TRIM(BOTH FROM withdrawal_reason)) > 0))))
);


--
-- Name: accommodation_applications_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_applications_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_number character varying(60),
    application_period_id uuid,
    student_id uuid,
    student_number character varying(40),
    student_name character varying(200),
    primary_email character varying(254),
    gender_code character varying(20),
    disability_code character varying(80),
    country_code character(3),
    location_code character varying(80),
    programme_id uuid,
    programme_code character varying(50),
    programme_name character varying(200),
    programme_level integer,
    sponsor_code character varying(80),
    payment_state character varying(30),
    preferred_room_type_id uuid,
    special_requirements character varying(1000),
    priority_score integer,
    status character varying(30),
    submitted_at timestamp with time zone,
    evaluated_by_user_id uuid,
    evaluated_at timestamp with time zone,
    evaluation_reason character varying(1000),
    selected_group_id uuid,
    withdrawn_by_user_id uuid,
    withdrawn_at timestamp with time zone,
    withdrawal_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: accommodation_blacklist_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_blacklist_entries (
    id uuid NOT NULL,
    student_id uuid NOT NULL,
    student_number character varying(40) NOT NULL,
    reason_code character varying(50) NOT NULL,
    reason character varying(1000) NOT NULL,
    effective_from date NOT NULL,
    effective_until date,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    imposed_by_user_id uuid NOT NULL,
    imposed_at timestamp with time zone NOT NULL,
    lifted_by_user_id uuid,
    lifted_at timestamp with time zone,
    lift_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_accommodation_blacklist_lift CHECK (((((status)::text = 'ACTIVE'::text) AND (lifted_by_user_id IS NULL) AND (lifted_at IS NULL) AND (lift_reason IS NULL)) OR (((status)::text <> 'ACTIVE'::text) AND (lifted_by_user_id IS NOT NULL) AND (lifted_at IS NOT NULL) AND (length(TRIM(BOTH FROM lift_reason)) > 0) AND (lifted_by_user_id <> imposed_by_user_id)))),
    CONSTRAINT ck_accommodation_blacklist_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'LIFTED'::character varying, 'EXPIRED'::character varying])::text[]))),
    CONSTRAINT ck_accommodation_blacklist_window CHECK (((effective_until IS NULL) OR (effective_until >= effective_from)))
);


--
-- Name: accommodation_blacklist_entries_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_blacklist_entries_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    student_id uuid,
    student_number character varying(40),
    reason_code character varying(50),
    reason character varying(1000),
    effective_from date,
    effective_until date,
    status character varying(20),
    imposed_by_user_id uuid,
    imposed_at timestamp with time zone,
    lifted_by_user_id uuid,
    lifted_at timestamp with time zone,
    lift_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: accommodation_damage_number_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.accommodation_damage_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: accommodation_damage_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_damage_records (
    id uuid NOT NULL,
    damage_number character varying(60) NOT NULL,
    room_allocation_id uuid NOT NULL,
    room_id uuid NOT NULL,
    description character varying(1000) NOT NULL,
    evidence_document_id uuid,
    status character varying(20) DEFAULT 'REPORTED'::character varying NOT NULL,
    estimated_transaction_amount numeric(19,4),
    transaction_currency_code character(3),
    estimated_base_amount numeric(19,4),
    base_currency_code character(3),
    exchange_rate_id uuid,
    reported_by_user_id uuid NOT NULL,
    reported_at timestamp with time zone NOT NULL,
    assessed_by_user_id uuid,
    assessed_at timestamp with time zone,
    assessment_reason character varying(1000),
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
    CONSTRAINT ck_accommodation_damage_amount CHECK (((estimated_transaction_amount IS NULL) OR (estimated_transaction_amount > (0)::numeric))),
    CONSTRAINT ck_accommodation_damage_assessment CHECK (((((status)::text = 'REPORTED'::text) AND (assessed_by_user_id IS NULL) AND (assessed_at IS NULL) AND (assessment_reason IS NULL)) OR (((status)::text <> 'REPORTED'::text) AND (assessed_by_user_id IS NOT NULL) AND (assessed_at IS NOT NULL) AND (length(TRIM(BOTH FROM assessment_reason)) > 0) AND (assessed_by_user_id <> reported_by_user_id)))),
    CONSTRAINT ck_accommodation_damage_charge_rating CHECK ((((status)::text <> ALL ((ARRAY['CHARGE_PENDING'::character varying, 'CHARGED'::character varying])::text[])) OR (estimated_base_amount IS NOT NULL))),
    CONSTRAINT ck_accommodation_damage_currency CHECK (((base_currency_code IS NULL) OR (base_currency_code = 'USD'::bpchar))),
    CONSTRAINT ck_accommodation_damage_rating CHECK ((((estimated_transaction_amount IS NULL) AND (transaction_currency_code IS NULL) AND (estimated_base_amount IS NULL) AND (base_currency_code IS NULL) AND (exchange_rate_id IS NULL)) OR ((transaction_currency_code = 'USD'::bpchar) AND (exchange_rate_id IS NULL) AND (estimated_base_amount = estimated_transaction_amount)) OR ((transaction_currency_code <> 'USD'::bpchar) AND (exchange_rate_id IS NOT NULL) AND (estimated_base_amount IS NOT NULL)) OR ((transaction_currency_code <> 'USD'::bpchar) AND (exchange_rate_id IS NULL) AND (estimated_base_amount IS NULL)))),
    CONSTRAINT ck_accommodation_damage_resolution CHECK (((((status)::text <> ALL ((ARRAY['WAIVED'::character varying, 'RESOLVED'::character varying])::text[])) AND (resolved_by_user_id IS NULL) AND (resolved_at IS NULL) AND (resolution_reason IS NULL)) OR (((status)::text = ANY ((ARRAY['WAIVED'::character varying, 'RESOLVED'::character varying])::text[])) AND (resolved_by_user_id IS NOT NULL) AND (resolved_at IS NOT NULL) AND (length(TRIM(BOTH FROM resolution_reason)) > 0) AND (resolved_by_user_id <> reported_by_user_id) AND (resolved_by_user_id <> assessed_by_user_id)))),
    CONSTRAINT ck_accommodation_damage_status CHECK (((status)::text = ANY ((ARRAY['REPORTED'::character varying, 'ASSESSED'::character varying, 'CHARGE_PENDING'::character varying, 'CHARGED'::character varying, 'WAIVED'::character varying, 'RESOLVED'::character varying])::text[])))
);


--
-- Name: accommodation_damage_records_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_damage_records_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    damage_number character varying(60),
    room_allocation_id uuid,
    room_id uuid,
    description character varying(1000),
    evidence_document_id uuid,
    status character varying(20),
    estimated_transaction_amount numeric(19,4),
    transaction_currency_code character(3),
    estimated_base_amount numeric(19,4),
    base_currency_code character(3),
    exchange_rate_id uuid,
    reported_by_user_id uuid,
    reported_at timestamp with time zone,
    assessed_by_user_id uuid,
    assessed_at timestamp with time zone,
    assessment_reason character varying(1000),
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
-- Name: accommodation_group_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_group_rules (
    id uuid NOT NULL,
    accommodation_group_id uuid NOT NULL,
    rule_dimension character varying(30) NOT NULL,
    comparison_operator character varying(20) NOT NULL,
    comparison_value character varying(200) NOT NULL,
    mandatory boolean DEFAULT true NOT NULL,
    priority_points integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_accommodation_rule_dimension CHECK (((rule_dimension)::text = ANY ((ARRAY['DISABILITY'::character varying, 'GENDER'::character varying, 'PROGRAMME'::character varying, 'SPONSOR'::character varying, 'LEVEL'::character varying, 'LOCATION'::character varying, 'PAYMENT_STATE'::character varying, 'COUNTRY'::character varying, 'PRIORITY'::character varying])::text[]))),
    CONSTRAINT ck_accommodation_rule_operator CHECK (((comparison_operator)::text = ANY ((ARRAY['EQUALS'::character varying, 'NOT_EQUALS'::character varying, 'IN'::character varying, 'NOT_IN'::character varying, 'PRESENT'::character varying])::text[]))),
    CONSTRAINT ck_accommodation_rule_points CHECK (((priority_points >= '-10000'::integer) AND (priority_points <= 10000)))
);


--
-- Name: accommodation_group_rules_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_group_rules_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    accommodation_group_id uuid,
    rule_dimension character varying(30),
    comparison_operator character varying(20),
    comparison_value character varying(200),
    mandatory boolean,
    priority_points integer,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: accommodation_groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_groups (
    id uuid NOT NULL,
    application_period_id uuid NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(160) NOT NULL,
    description character varying(500),
    priority_rank integer NOT NULL,
    reserved_bed_count integer DEFAULT 0 NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_accommodation_group_priority CHECK (((priority_rank > 0) AND (reserved_bed_count >= 0)))
);


--
-- Name: accommodation_groups_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_groups_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_period_id uuid,
    code character varying(40),
    name character varying(160),
    description character varying(500),
    priority_rank integer,
    reserved_bed_count integer,
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
-- Name: accommodation_premises; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_premises (
    id uuid NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(160) NOT NULL,
    address_line character varying(300) NOT NULL,
    suburb character varying(120),
    landlord_name character varying(160),
    contact_details character varying(500),
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: accommodation_premises_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_premises_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code character varying(40),
    name character varying(160),
    address_line character varying(300),
    suburb character varying(120),
    landlord_name character varying(160),
    contact_details character varying(500),
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
-- Name: accommodation_rates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_rates (
    id uuid NOT NULL,
    application_period_id uuid NOT NULL,
    room_type_id uuid NOT NULL,
    rate_version integer NOT NULL,
    finance_fee_catalogue_id uuid NOT NULL,
    transaction_currency_code character(3) NOT NULL,
    indicative_transaction_amount numeric(19,4) NOT NULL,
    base_currency_code character(3) DEFAULT 'USD'::bpchar NOT NULL,
    exchange_rate_id uuid,
    indicative_base_amount numeric(19,4),
    rating_status character varying(20) NOT NULL,
    effective_from timestamp with time zone NOT NULL,
    effective_until timestamp with time zone,
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
    CONSTRAINT ck_accommodation_rate_activation CHECK ((((status)::text <> 'ACTIVE'::text) OR ((rating_status)::text = 'RATED'::text))),
    CONSTRAINT ck_accommodation_rate_amount CHECK (((indicative_transaction_amount > (0)::numeric) AND ((indicative_base_amount IS NULL) OR (indicative_base_amount > (0)::numeric)))),
    CONSTRAINT ck_accommodation_rate_approval CHECK (((((status)::text = 'DRAFT'::text) AND (approved_by_user_id IS NULL) AND (approved_at IS NULL) AND (approval_reason IS NULL)) OR (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'RETIRED'::character varying])::text[])) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (length(TRIM(BOTH FROM approval_reason)) > 0) AND (approved_by_user_id <> prepared_by_user_id)))),
    CONSTRAINT ck_accommodation_rate_currency CHECK ((base_currency_code = 'USD'::bpchar)),
    CONSTRAINT ck_accommodation_rate_rating CHECK ((((transaction_currency_code = 'USD'::bpchar) AND ((rating_status)::text = 'RATED'::text) AND (exchange_rate_id IS NULL) AND (indicative_base_amount = indicative_transaction_amount)) OR ((transaction_currency_code <> 'USD'::bpchar) AND ((rating_status)::text = 'RATED'::text) AND (exchange_rate_id IS NOT NULL) AND (indicative_base_amount IS NOT NULL)) OR ((transaction_currency_code <> 'USD'::bpchar) AND ((rating_status)::text = 'UNRATED'::text) AND (exchange_rate_id IS NULL) AND (indicative_base_amount IS NULL)))),
    CONSTRAINT ck_accommodation_rate_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'RETIRED'::character varying])::text[]))),
    CONSTRAINT ck_accommodation_rate_window CHECK (((effective_until IS NULL) OR (effective_until > effective_from)))
);


--
-- Name: accommodation_rates_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_rates_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_period_id uuid,
    room_type_id uuid,
    rate_version integer,
    finance_fee_catalogue_id uuid,
    transaction_currency_code character(3),
    indicative_transaction_amount numeric(19,4),
    base_currency_code character(3),
    exchange_rate_id uuid,
    indicative_base_amount numeric(19,4),
    rating_status character varying(20),
    effective_from timestamp with time zone,
    effective_until timestamp with time zone,
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
-- Name: accommodation_room_facilities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_room_facilities (
    id uuid NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(120) NOT NULL,
    description character varying(500),
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: accommodation_room_facilities_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_room_facilities_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code character varying(40),
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
-- Name: accommodation_room_facility_assignments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_room_facility_assignments (
    id uuid NOT NULL,
    room_id uuid NOT NULL,
    facility_id uuid NOT NULL,
    quantity integer DEFAULT 1 NOT NULL,
    condition_notes character varying(500),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_accommodation_facility_quantity CHECK ((quantity > 0))
);


--
-- Name: accommodation_room_facility_assignments_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_room_facility_assignments_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    room_id uuid,
    facility_id uuid,
    quantity integer,
    condition_notes character varying(500),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: accommodation_room_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_room_types (
    id uuid NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(120) NOT NULL,
    description character varying(500),
    default_capacity integer NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_accommodation_room_type_capacity CHECK ((default_capacity > 0))
);


--
-- Name: accommodation_room_types_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_room_types_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code character varying(40),
    name character varying(120),
    description character varying(500),
    default_capacity integer,
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
-- Name: accommodation_rooms; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_rooms (
    id uuid NOT NULL,
    residence_hall_id uuid NOT NULL,
    room_type_id uuid NOT NULL,
    code character varying(40) NOT NULL,
    floor_label character varying(40),
    capacity integer NOT NULL,
    accessibility_ready boolean DEFAULT false NOT NULL,
    condition_status character varying(20) DEFAULT 'AVAILABLE'::character varying NOT NULL,
    condition_notes character varying(500),
    reserved_for_group_id uuid,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_accommodation_room_capacity CHECK ((capacity > 0)),
    CONSTRAINT ck_accommodation_room_condition CHECK (((condition_status)::text = ANY ((ARRAY['AVAILABLE'::character varying, 'MAINTENANCE'::character varying, 'OUT_OF_SERVICE'::character varying])::text[])))
);


--
-- Name: accommodation_rooms_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_rooms_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    residence_hall_id uuid,
    room_type_id uuid,
    code character varying(40),
    floor_label character varying(40),
    capacity integer,
    accessibility_ready boolean,
    condition_status character varying(20),
    condition_notes character varying(500),
    reserved_for_group_id uuid,
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
-- Name: accommodation_swap_number_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.accommodation_swap_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: accommodation_waitlist_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_waitlist_entries (
    id uuid NOT NULL,
    accommodation_application_id uuid CONSTRAINT accommodation_waitlist_entr_accommodation_application__not_null NOT NULL,
    application_period_id uuid NOT NULL,
    waitlist_position integer NOT NULL,
    priority_score integer NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    entered_by_user_id uuid NOT NULL,
    entered_at timestamp with time zone NOT NULL,
    removed_by_user_id uuid,
    removed_at timestamp with time zone,
    removal_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_accommodation_waitlist_position CHECK ((waitlist_position > 0)),
    CONSTRAINT ck_accommodation_waitlist_removal CHECK (((((status)::text = 'ACTIVE'::text) AND (removed_by_user_id IS NULL) AND (removed_at IS NULL) AND (removal_reason IS NULL)) OR (((status)::text <> 'ACTIVE'::text) AND (removed_by_user_id IS NOT NULL) AND (removed_at IS NOT NULL) AND (length(TRIM(BOTH FROM removal_reason)) > 0)))),
    CONSTRAINT ck_accommodation_waitlist_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'ALLOCATED'::character varying, 'WITHDRAWN'::character varying, 'REMOVED'::character varying])::text[])))
);


--
-- Name: accommodation_waitlist_entries_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accommodation_waitlist_entries_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    accommodation_application_id uuid,
    application_period_id uuid,
    waitlist_position integer,
    priority_score integer,
    status character varying(20),
    entered_by_user_id uuid,
    entered_at timestamp with time zone,
    removed_by_user_id uuid,
    removed_at timestamp with time zone,
    removal_reason character varying(1000),
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
    CONSTRAINT ck_accommodation_outbox_attempts CHECK ((attempt_count >= 0)),
    CONSTRAINT ck_accommodation_outbox_publication CHECK (((((status)::text = 'PUBLISHED'::text) AND (published_at IS NOT NULL)) OR (((status)::text <> 'PUBLISHED'::text) AND (published_at IS NULL)))),
    CONSTRAINT ck_accommodation_outbox_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PUBLISHED'::character varying, 'DEAD'::character varying])::text[])))
);


--
-- Name: residence_halls; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.residence_halls (
    id uuid NOT NULL,
    premise_id uuid NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(160) NOT NULL,
    resident_gender_policy character varying(20) DEFAULT 'ANY'::character varying NOT NULL,
    warden_name character varying(160),
    warden_contact character varying(160),
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_residence_hall_gender CHECK (((resident_gender_policy)::text = ANY ((ARRAY['ANY'::character varying, 'FEMALE'::character varying, 'MALE'::character varying])::text[])))
);


--
-- Name: residence_halls_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.residence_halls_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    premise_id uuid,
    code character varying(40),
    name character varying(160),
    resident_gender_policy character varying(20),
    warden_name character varying(160),
    warden_contact character varying(160),
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
    service_name character varying(100) DEFAULT 'accommodation-service'::character varying NOT NULL,
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
-- Name: room_allocation_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.room_allocation_events (
    id uuid NOT NULL,
    room_allocation_id uuid NOT NULL,
    previous_status character varying(30),
    new_status character varying(30) NOT NULL,
    event_type character varying(30) NOT NULL,
    from_room_id uuid,
    to_room_id uuid,
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
    CONSTRAINT ck_room_allocation_event_type CHECK (((event_type)::text = ANY ((ARRAY['PROPOSED'::character varying, 'APPROVED'::character varying, 'CHECKED_IN'::character varying, 'CHECKED_OUT'::character varying, 'MOVED'::character varying, 'WITHDRAWN'::character varying, 'CANCELLED'::character varying, 'BILLING_REQUESTED'::character varying, 'BILLING_ACCEPTED'::character varying, 'BILLING_FAILED'::character varying])::text[])))
);


--
-- Name: room_allocation_events_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.room_allocation_events_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    room_allocation_id uuid,
    previous_status character varying(30),
    new_status character varying(30),
    event_type character varying(30),
    from_room_id uuid,
    to_room_id uuid,
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
-- Name: room_allocations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.room_allocations (
    id uuid NOT NULL,
    allocation_number character varying(60) NOT NULL,
    accommodation_application_id uuid NOT NULL,
    room_id uuid NOT NULL,
    accommodation_rate_id uuid NOT NULL,
    occupancy_starts_on date NOT NULL,
    occupancy_ends_on date NOT NULL,
    status character varying(30) DEFAULT 'PROPOSED'::character varying NOT NULL,
    allocated_by_user_id uuid NOT NULL,
    allocated_at timestamp with time zone NOT NULL,
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    approval_reason character varying(1000),
    checked_in_by_user_id uuid,
    checked_in_at timestamp with time zone,
    check_in_notes character varying(1000),
    checked_out_by_user_id uuid,
    checked_out_at timestamp with time zone,
    check_out_notes character varying(1000),
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
    CONSTRAINT ck_room_allocation_approval CHECK (((((status)::text = 'PROPOSED'::text) AND (approved_by_user_id IS NULL) AND (approved_at IS NULL) AND (approval_reason IS NULL)) OR (((status)::text = 'CANCELLED'::text) AND (approved_by_user_id IS NULL) AND (approved_at IS NULL) AND (approval_reason IS NULL)) OR (((status)::text <> 'PROPOSED'::text) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (length(TRIM(BOTH FROM approval_reason)) > 0) AND (approved_by_user_id <> allocated_by_user_id)))),
    CONSTRAINT ck_room_allocation_billing CHECK (((billing_status)::text = ANY ((ARRAY['NOT_REQUESTED'::character varying, 'PENDING'::character varying, 'ACCEPTED'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT ck_room_allocation_checkin CHECK (((((status)::text = ANY ((ARRAY['PROPOSED'::character varying, 'ALLOCATED'::character varying, 'CANCELLED'::character varying])::text[])) AND (checked_in_by_user_id IS NULL) AND (checked_in_at IS NULL) AND (check_in_notes IS NULL)) OR (((status)::text = ANY ((ARRAY['CHECKED_IN'::character varying, 'CHECKED_OUT'::character varying, 'WITHDRAWN'::character varying])::text[])) AND (checked_in_by_user_id IS NOT NULL) AND (checked_in_at IS NOT NULL) AND (length(TRIM(BOTH FROM check_in_notes)) > 0)))),
    CONSTRAINT ck_room_allocation_checkout CHECK (((((status)::text <> 'CHECKED_OUT'::text) AND (checked_out_by_user_id IS NULL) AND (checked_out_at IS NULL) AND (check_out_notes IS NULL)) OR (((status)::text = 'CHECKED_OUT'::text) AND (checked_out_by_user_id IS NOT NULL) AND (checked_out_at IS NOT NULL) AND (length(TRIM(BOTH FROM check_out_notes)) > 0) AND (checked_out_by_user_id <> checked_in_by_user_id)))),
    CONSTRAINT ck_room_allocation_dates CHECK ((occupancy_ends_on >= occupancy_starts_on)),
    CONSTRAINT ck_room_allocation_ending CHECK (((((status)::text <> ALL ((ARRAY['WITHDRAWN'::character varying, 'CANCELLED'::character varying])::text[])) AND (ended_by_user_id IS NULL) AND (ended_at IS NULL) AND (end_reason IS NULL)) OR (((status)::text = ANY ((ARRAY['WITHDRAWN'::character varying, 'CANCELLED'::character varying])::text[])) AND (ended_by_user_id IS NOT NULL) AND (ended_at IS NOT NULL) AND (length(TRIM(BOTH FROM end_reason)) > 0)))),
    CONSTRAINT ck_room_allocation_status CHECK (((status)::text = ANY ((ARRAY['PROPOSED'::character varying, 'ALLOCATED'::character varying, 'CHECKED_IN'::character varying, 'CHECKED_OUT'::character varying, 'WITHDRAWN'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: room_allocations_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.room_allocations_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    allocation_number character varying(60),
    accommodation_application_id uuid,
    room_id uuid,
    accommodation_rate_id uuid,
    occupancy_starts_on date,
    occupancy_ends_on date,
    status character varying(30),
    allocated_by_user_id uuid,
    allocated_at timestamp with time zone,
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    approval_reason character varying(1000),
    checked_in_by_user_id uuid,
    checked_in_at timestamp with time zone,
    check_in_notes character varying(1000),
    checked_out_by_user_id uuid,
    checked_out_at timestamp with time zone,
    check_out_notes character varying(1000),
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
    version bigint
);


--
-- Name: room_swaps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.room_swaps (
    id uuid NOT NULL,
    swap_number character varying(60) NOT NULL,
    first_allocation_id uuid NOT NULL,
    second_allocation_id uuid NOT NULL,
    first_original_room_id uuid NOT NULL,
    second_original_room_id uuid NOT NULL,
    status character varying(20) DEFAULT 'REQUESTED'::character varying NOT NULL,
    reason character varying(1000) NOT NULL,
    requested_by_user_id uuid NOT NULL,
    requested_at timestamp with time zone NOT NULL,
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    approval_reason character varying(1000),
    completed_by_user_id uuid,
    completed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_room_swap_approval CHECK (((((status)::text = 'REQUESTED'::text) AND (approved_by_user_id IS NULL)) OR ((status)::text = ANY ((ARRAY['REJECTED'::character varying, 'CANCELLED'::character varying])::text[])) OR (((status)::text = ANY ((ARRAY['APPROVED'::character varying, 'COMPLETED'::character varying])::text[])) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (length(TRIM(BOTH FROM approval_reason)) > 0) AND (approved_by_user_id <> requested_by_user_id)))),
    CONSTRAINT ck_room_swap_completion CHECK (((((status)::text <> 'COMPLETED'::text) AND (completed_by_user_id IS NULL) AND (completed_at IS NULL)) OR (((status)::text = 'COMPLETED'::text) AND (completed_by_user_id IS NOT NULL) AND (completed_at IS NOT NULL) AND (completed_by_user_id <> requested_by_user_id) AND (completed_by_user_id <> approved_by_user_id)))),
    CONSTRAINT ck_room_swap_distinct CHECK ((first_allocation_id <> second_allocation_id)),
    CONSTRAINT ck_room_swap_distinct_rooms CHECK ((first_original_room_id <> second_original_room_id)),
    CONSTRAINT ck_room_swap_status CHECK (((status)::text = ANY ((ARRAY['REQUESTED'::character varying, 'APPROVED'::character varying, 'COMPLETED'::character varying, 'REJECTED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: room_swaps_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.room_swaps_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    swap_number character varying(60),
    first_allocation_id uuid,
    second_allocation_id uuid,
    first_original_room_id uuid,
    second_original_room_id uuid,
    status character varying(20),
    reason character varying(1000),
    requested_by_user_id uuid,
    requested_at timestamp with time zone,
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    approval_reason character varying(1000),
    completed_by_user_id uuid,
    completed_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Data for Name: accommodation_application_periods; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_application_periods_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_applications; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_applications_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_blacklist_entries; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_blacklist_entries_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_damage_records; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_damage_records_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_group_rules; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_group_rules_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_groups; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_groups_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_premises; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_premises_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_rates; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_rates_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_room_facilities; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_room_facilities_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_room_facility_assignments; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_room_facility_assignments_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_room_types; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_room_types_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_rooms; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_rooms_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_waitlist_entries; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: accommodation_waitlist_entries_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: integration_outbox; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: residence_halls; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: residence_halls_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: revinfo; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: room_allocation_events; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: room_allocation_events_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: room_allocations; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: room_allocations_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: room_swaps; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: room_swaps_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Name: accommodation_allocation_number_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.accommodation_allocation_number_seq', 1, false);


--
-- Name: accommodation_application_number_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.accommodation_application_number_seq', 1, false);


--
-- Name: accommodation_damage_number_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.accommodation_damage_number_seq', 1, false);


--
-- Name: accommodation_swap_number_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.accommodation_swap_number_seq', 1, false);


--
-- Name: revinfo_rev_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.revinfo_rev_seq', 1, false);


--
-- Name: accommodation_application_periods_aud accommodation_application_periods_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_application_periods_aud
    ADD CONSTRAINT accommodation_application_periods_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: accommodation_application_periods accommodation_application_periods_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_application_periods
    ADD CONSTRAINT accommodation_application_periods_pkey PRIMARY KEY (id);


--
-- Name: accommodation_applications accommodation_applications_application_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_applications
    ADD CONSTRAINT accommodation_applications_application_number_key UNIQUE (application_number);


--
-- Name: accommodation_applications_aud accommodation_applications_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_applications_aud
    ADD CONSTRAINT accommodation_applications_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: accommodation_applications accommodation_applications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_applications
    ADD CONSTRAINT accommodation_applications_pkey PRIMARY KEY (id);


--
-- Name: accommodation_blacklist_entries_aud accommodation_blacklist_entries_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_blacklist_entries_aud
    ADD CONSTRAINT accommodation_blacklist_entries_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: accommodation_blacklist_entries accommodation_blacklist_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_blacklist_entries
    ADD CONSTRAINT accommodation_blacklist_entries_pkey PRIMARY KEY (id);


--
-- Name: accommodation_damage_records_aud accommodation_damage_records_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_damage_records_aud
    ADD CONSTRAINT accommodation_damage_records_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: accommodation_damage_records accommodation_damage_records_damage_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_damage_records
    ADD CONSTRAINT accommodation_damage_records_damage_number_key UNIQUE (damage_number);


--
-- Name: accommodation_damage_records accommodation_damage_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_damage_records
    ADD CONSTRAINT accommodation_damage_records_pkey PRIMARY KEY (id);


--
-- Name: accommodation_group_rules_aud accommodation_group_rules_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_group_rules_aud
    ADD CONSTRAINT accommodation_group_rules_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: accommodation_group_rules accommodation_group_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_group_rules
    ADD CONSTRAINT accommodation_group_rules_pkey PRIMARY KEY (id);


--
-- Name: accommodation_groups_aud accommodation_groups_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_groups_aud
    ADD CONSTRAINT accommodation_groups_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: accommodation_groups accommodation_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_groups
    ADD CONSTRAINT accommodation_groups_pkey PRIMARY KEY (id);


--
-- Name: accommodation_premises_aud accommodation_premises_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_premises_aud
    ADD CONSTRAINT accommodation_premises_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: accommodation_premises accommodation_premises_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_premises
    ADD CONSTRAINT accommodation_premises_pkey PRIMARY KEY (id);


--
-- Name: accommodation_rates_aud accommodation_rates_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_rates_aud
    ADD CONSTRAINT accommodation_rates_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: accommodation_rates accommodation_rates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_rates
    ADD CONSTRAINT accommodation_rates_pkey PRIMARY KEY (id);


--
-- Name: accommodation_room_facilities_aud accommodation_room_facilities_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_room_facilities_aud
    ADD CONSTRAINT accommodation_room_facilities_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: accommodation_room_facilities accommodation_room_facilities_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_room_facilities
    ADD CONSTRAINT accommodation_room_facilities_pkey PRIMARY KEY (id);


--
-- Name: accommodation_room_facility_assignments_aud accommodation_room_facility_assignments_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_room_facility_assignments_aud
    ADD CONSTRAINT accommodation_room_facility_assignments_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: accommodation_room_facility_assignments accommodation_room_facility_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_room_facility_assignments
    ADD CONSTRAINT accommodation_room_facility_assignments_pkey PRIMARY KEY (id);


--
-- Name: accommodation_room_types_aud accommodation_room_types_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_room_types_aud
    ADD CONSTRAINT accommodation_room_types_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: accommodation_room_types accommodation_room_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_room_types
    ADD CONSTRAINT accommodation_room_types_pkey PRIMARY KEY (id);


--
-- Name: accommodation_rooms_aud accommodation_rooms_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_rooms_aud
    ADD CONSTRAINT accommodation_rooms_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: accommodation_rooms accommodation_rooms_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_rooms
    ADD CONSTRAINT accommodation_rooms_pkey PRIMARY KEY (id);


--
-- Name: accommodation_waitlist_entries_aud accommodation_waitlist_entries_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_waitlist_entries_aud
    ADD CONSTRAINT accommodation_waitlist_entries_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: accommodation_waitlist_entries accommodation_waitlist_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_waitlist_entries
    ADD CONSTRAINT accommodation_waitlist_entries_pkey PRIMARY KEY (id);


--
-- Name: integration_outbox integration_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_outbox
    ADD CONSTRAINT integration_outbox_pkey PRIMARY KEY (id);


--
-- Name: residence_halls_aud residence_halls_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.residence_halls_aud
    ADD CONSTRAINT residence_halls_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: residence_halls residence_halls_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.residence_halls
    ADD CONSTRAINT residence_halls_pkey PRIMARY KEY (id);


--
-- Name: revinfo revinfo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revinfo
    ADD CONSTRAINT revinfo_pkey PRIMARY KEY (rev);


--
-- Name: room_allocation_events_aud room_allocation_events_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_allocation_events_aud
    ADD CONSTRAINT room_allocation_events_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: room_allocation_events room_allocation_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_allocation_events
    ADD CONSTRAINT room_allocation_events_pkey PRIMARY KEY (id);


--
-- Name: room_allocations room_allocations_allocation_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_allocations
    ADD CONSTRAINT room_allocations_allocation_number_key UNIQUE (allocation_number);


--
-- Name: room_allocations_aud room_allocations_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_allocations_aud
    ADD CONSTRAINT room_allocations_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: room_allocations room_allocations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_allocations
    ADD CONSTRAINT room_allocations_pkey PRIMARY KEY (id);


--
-- Name: room_swaps_aud room_swaps_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_swaps_aud
    ADD CONSTRAINT room_swaps_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: room_swaps room_swaps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_swaps
    ADD CONSTRAINT room_swaps_pkey PRIMARY KEY (id);


--
-- Name: room_swaps room_swaps_swap_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_swaps
    ADD CONSTRAINT room_swaps_swap_number_key UNIQUE (swap_number);


--
-- Name: accommodation_applications uk_accommodation_application_student; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_applications
    ADD CONSTRAINT uk_accommodation_application_student UNIQUE (application_period_id, student_id);


--
-- Name: accommodation_rates uk_accommodation_rate_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_rates
    ADD CONSTRAINT uk_accommodation_rate_version UNIQUE (application_period_id, room_type_id, rate_version);


--
-- Name: idx_accommodation_outbox_dispatch; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_accommodation_outbox_dispatch ON public.integration_outbox USING btree (next_attempt_at, occurred_at, id) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: idx_room_allocation_occupancy; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_room_allocation_occupancy ON public.room_allocations USING btree (room_id, occupancy_starts_on, occupancy_ends_on) WHERE (((status)::text = ANY ((ARRAY['PROPOSED'::character varying, 'ALLOCATED'::character varying, 'CHECKED_IN'::character varying])::text[])) AND (deleted_at IS NULL));


--
-- Name: uk_accommodation_active_blacklist; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_accommodation_active_blacklist ON public.accommodation_blacklist_entries USING btree (student_id) WHERE (((status)::text = 'ACTIVE'::text) AND (deleted_at IS NULL));


--
-- Name: uk_accommodation_active_rate; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_accommodation_active_rate ON public.accommodation_rates USING btree (application_period_id, room_type_id) WHERE (((status)::text = 'ACTIVE'::text) AND (deleted_at IS NULL));


--
-- Name: uk_accommodation_active_waitlist_application; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_accommodation_active_waitlist_application ON public.accommodation_waitlist_entries USING btree (accommodation_application_id) WHERE (((status)::text = 'ACTIVE'::text) AND (deleted_at IS NULL));


--
-- Name: uk_accommodation_active_waitlist_position; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_accommodation_active_waitlist_position ON public.accommodation_waitlist_entries USING btree (application_period_id, waitlist_position) WHERE (((status)::text = 'ACTIVE'::text) AND (deleted_at IS NULL));


--
-- Name: uk_accommodation_facility_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_accommodation_facility_code ON public.accommodation_room_facilities USING btree (lower((code)::text)) WHERE (deleted_at IS NULL);


--
-- Name: uk_accommodation_group_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_accommodation_group_code ON public.accommodation_groups USING btree (application_period_id, lower((code)::text)) WHERE (deleted_at IS NULL);


--
-- Name: uk_accommodation_period_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_accommodation_period_code ON public.accommodation_application_periods USING btree (academic_period_id, lower((code)::text)) WHERE (deleted_at IS NULL);


--
-- Name: uk_accommodation_premise_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_accommodation_premise_code ON public.accommodation_premises USING btree (lower((code)::text)) WHERE (deleted_at IS NULL);


--
-- Name: uk_accommodation_room_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_accommodation_room_code ON public.accommodation_rooms USING btree (residence_hall_id, lower((code)::text)) WHERE (deleted_at IS NULL);


--
-- Name: uk_accommodation_room_facility; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_accommodation_room_facility ON public.accommodation_room_facility_assignments USING btree (room_id, facility_id) WHERE (deleted_at IS NULL);


--
-- Name: uk_accommodation_room_type_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_accommodation_room_type_code ON public.accommodation_room_types USING btree (lower((code)::text)) WHERE (deleted_at IS NULL);


--
-- Name: uk_residence_hall_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_residence_hall_code ON public.residence_halls USING btree (premise_id, lower((code)::text)) WHERE (deleted_at IS NULL);


--
-- Name: uk_room_allocation_active_student; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_room_allocation_active_student ON public.room_allocations USING btree (accommodation_application_id) WHERE (((status)::text = ANY ((ARRAY['PROPOSED'::character varying, 'ALLOCATED'::character varying, 'CHECKED_IN'::character varying])::text[])) AND (deleted_at IS NULL));


--
-- Name: accommodation_application_periods trg_protect_accommodation_period; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_accommodation_period BEFORE UPDATE ON public.accommodation_application_periods FOR EACH ROW EXECUTE FUNCTION public.protect_approved_accommodation_configuration();


--
-- Name: room_allocation_events trg_protect_room_allocation_event; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_room_allocation_event BEFORE DELETE OR UPDATE ON public.room_allocation_events FOR EACH ROW EXECUTE FUNCTION public.protect_accommodation_evidence();


--
-- Name: accommodation_applications trg_validate_accommodation_application; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_accommodation_application BEFORE INSERT ON public.accommodation_applications FOR EACH ROW EXECUTE FUNCTION public.validate_accommodation_application_period();


--
-- Name: accommodation_waitlist_entries trg_validate_accommodation_waitlist; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_accommodation_waitlist BEFORE INSERT OR UPDATE OF accommodation_application_id, application_period_id ON public.accommodation_waitlist_entries FOR EACH ROW EXECUTE FUNCTION public.validate_accommodation_waitlist_period();


--
-- Name: room_allocations trg_validate_room_allocation; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_room_allocation BEFORE INSERT OR UPDATE OF room_id, accommodation_rate_id, occupancy_starts_on, occupancy_ends_on, status ON public.room_allocations FOR EACH ROW EXECUTE FUNCTION public.validate_room_allocation_capacity();


--
-- Name: room_swaps trg_validate_room_swap; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_room_swap BEFORE INSERT OR UPDATE OF status ON public.room_swaps FOR EACH ROW EXECUTE FUNCTION public.validate_room_swap();


--
-- Name: accommodation_application_periods_aud accommodation_application_periods_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_application_periods_aud
    ADD CONSTRAINT accommodation_application_periods_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: accommodation_applications accommodation_applications_application_period_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_applications
    ADD CONSTRAINT accommodation_applications_application_period_id_fkey FOREIGN KEY (application_period_id) REFERENCES public.accommodation_application_periods(id);


--
-- Name: accommodation_applications_aud accommodation_applications_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_applications_aud
    ADD CONSTRAINT accommodation_applications_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: accommodation_applications accommodation_applications_preferred_room_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_applications
    ADD CONSTRAINT accommodation_applications_preferred_room_type_id_fkey FOREIGN KEY (preferred_room_type_id) REFERENCES public.accommodation_room_types(id);


--
-- Name: accommodation_applications accommodation_applications_selected_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_applications
    ADD CONSTRAINT accommodation_applications_selected_group_id_fkey FOREIGN KEY (selected_group_id) REFERENCES public.accommodation_groups(id);


--
-- Name: accommodation_blacklist_entries_aud accommodation_blacklist_entries_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_blacklist_entries_aud
    ADD CONSTRAINT accommodation_blacklist_entries_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: accommodation_damage_records_aud accommodation_damage_records_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_damage_records_aud
    ADD CONSTRAINT accommodation_damage_records_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: accommodation_damage_records accommodation_damage_records_room_allocation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_damage_records
    ADD CONSTRAINT accommodation_damage_records_room_allocation_id_fkey FOREIGN KEY (room_allocation_id) REFERENCES public.room_allocations(id);


--
-- Name: accommodation_damage_records accommodation_damage_records_room_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_damage_records
    ADD CONSTRAINT accommodation_damage_records_room_id_fkey FOREIGN KEY (room_id) REFERENCES public.accommodation_rooms(id);


--
-- Name: accommodation_group_rules accommodation_group_rules_accommodation_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_group_rules
    ADD CONSTRAINT accommodation_group_rules_accommodation_group_id_fkey FOREIGN KEY (accommodation_group_id) REFERENCES public.accommodation_groups(id);


--
-- Name: accommodation_group_rules_aud accommodation_group_rules_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_group_rules_aud
    ADD CONSTRAINT accommodation_group_rules_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: accommodation_groups accommodation_groups_application_period_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_groups
    ADD CONSTRAINT accommodation_groups_application_period_id_fkey FOREIGN KEY (application_period_id) REFERENCES public.accommodation_application_periods(id);


--
-- Name: accommodation_groups_aud accommodation_groups_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_groups_aud
    ADD CONSTRAINT accommodation_groups_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: accommodation_premises_aud accommodation_premises_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_premises_aud
    ADD CONSTRAINT accommodation_premises_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: accommodation_rates accommodation_rates_application_period_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_rates
    ADD CONSTRAINT accommodation_rates_application_period_id_fkey FOREIGN KEY (application_period_id) REFERENCES public.accommodation_application_periods(id);


--
-- Name: accommodation_rates_aud accommodation_rates_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_rates_aud
    ADD CONSTRAINT accommodation_rates_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: accommodation_rates accommodation_rates_room_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_rates
    ADD CONSTRAINT accommodation_rates_room_type_id_fkey FOREIGN KEY (room_type_id) REFERENCES public.accommodation_room_types(id);


--
-- Name: accommodation_room_facilities_aud accommodation_room_facilities_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_room_facilities_aud
    ADD CONSTRAINT accommodation_room_facilities_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: accommodation_room_facility_assignments_aud accommodation_room_facility_assignments_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_room_facility_assignments_aud
    ADD CONSTRAINT accommodation_room_facility_assignments_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: accommodation_room_facility_assignments accommodation_room_facility_assignments_facility_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_room_facility_assignments
    ADD CONSTRAINT accommodation_room_facility_assignments_facility_id_fkey FOREIGN KEY (facility_id) REFERENCES public.accommodation_room_facilities(id);


--
-- Name: accommodation_room_facility_assignments accommodation_room_facility_assignments_room_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_room_facility_assignments
    ADD CONSTRAINT accommodation_room_facility_assignments_room_id_fkey FOREIGN KEY (room_id) REFERENCES public.accommodation_rooms(id);


--
-- Name: accommodation_room_types_aud accommodation_room_types_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_room_types_aud
    ADD CONSTRAINT accommodation_room_types_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: accommodation_rooms_aud accommodation_rooms_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_rooms_aud
    ADD CONSTRAINT accommodation_rooms_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: accommodation_rooms accommodation_rooms_residence_hall_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_rooms
    ADD CONSTRAINT accommodation_rooms_residence_hall_id_fkey FOREIGN KEY (residence_hall_id) REFERENCES public.residence_halls(id);


--
-- Name: accommodation_rooms accommodation_rooms_room_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_rooms
    ADD CONSTRAINT accommodation_rooms_room_type_id_fkey FOREIGN KEY (room_type_id) REFERENCES public.accommodation_room_types(id);


--
-- Name: accommodation_waitlist_entries accommodation_waitlist_entrie_accommodation_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_waitlist_entries
    ADD CONSTRAINT accommodation_waitlist_entrie_accommodation_application_id_fkey FOREIGN KEY (accommodation_application_id) REFERENCES public.accommodation_applications(id);


--
-- Name: accommodation_waitlist_entries accommodation_waitlist_entries_application_period_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_waitlist_entries
    ADD CONSTRAINT accommodation_waitlist_entries_application_period_id_fkey FOREIGN KEY (application_period_id) REFERENCES public.accommodation_application_periods(id);


--
-- Name: accommodation_waitlist_entries_aud accommodation_waitlist_entries_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_waitlist_entries_aud
    ADD CONSTRAINT accommodation_waitlist_entries_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: accommodation_rooms fk_accommodation_room_reserved_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accommodation_rooms
    ADD CONSTRAINT fk_accommodation_room_reserved_group FOREIGN KEY (reserved_for_group_id) REFERENCES public.accommodation_groups(id);


--
-- Name: residence_halls_aud residence_halls_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.residence_halls_aud
    ADD CONSTRAINT residence_halls_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: residence_halls residence_halls_premise_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.residence_halls
    ADD CONSTRAINT residence_halls_premise_id_fkey FOREIGN KEY (premise_id) REFERENCES public.accommodation_premises(id);


--
-- Name: room_allocation_events_aud room_allocation_events_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_allocation_events_aud
    ADD CONSTRAINT room_allocation_events_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: room_allocation_events room_allocation_events_from_room_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_allocation_events
    ADD CONSTRAINT room_allocation_events_from_room_id_fkey FOREIGN KEY (from_room_id) REFERENCES public.accommodation_rooms(id);


--
-- Name: room_allocation_events room_allocation_events_room_allocation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_allocation_events
    ADD CONSTRAINT room_allocation_events_room_allocation_id_fkey FOREIGN KEY (room_allocation_id) REFERENCES public.room_allocations(id);


--
-- Name: room_allocation_events room_allocation_events_to_room_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_allocation_events
    ADD CONSTRAINT room_allocation_events_to_room_id_fkey FOREIGN KEY (to_room_id) REFERENCES public.accommodation_rooms(id);


--
-- Name: room_allocations room_allocations_accommodation_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_allocations
    ADD CONSTRAINT room_allocations_accommodation_application_id_fkey FOREIGN KEY (accommodation_application_id) REFERENCES public.accommodation_applications(id);


--
-- Name: room_allocations room_allocations_accommodation_rate_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_allocations
    ADD CONSTRAINT room_allocations_accommodation_rate_id_fkey FOREIGN KEY (accommodation_rate_id) REFERENCES public.accommodation_rates(id);


--
-- Name: room_allocations_aud room_allocations_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_allocations_aud
    ADD CONSTRAINT room_allocations_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: room_allocations room_allocations_room_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_allocations
    ADD CONSTRAINT room_allocations_room_id_fkey FOREIGN KEY (room_id) REFERENCES public.accommodation_rooms(id);


--
-- Name: room_swaps_aud room_swaps_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_swaps_aud
    ADD CONSTRAINT room_swaps_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: room_swaps room_swaps_first_allocation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_swaps
    ADD CONSTRAINT room_swaps_first_allocation_id_fkey FOREIGN KEY (first_allocation_id) REFERENCES public.room_allocations(id);


--
-- Name: room_swaps room_swaps_first_original_room_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_swaps
    ADD CONSTRAINT room_swaps_first_original_room_id_fkey FOREIGN KEY (first_original_room_id) REFERENCES public.accommodation_rooms(id);


--
-- Name: room_swaps room_swaps_second_allocation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_swaps
    ADD CONSTRAINT room_swaps_second_allocation_id_fkey FOREIGN KEY (second_allocation_id) REFERENCES public.room_allocations(id);


--
-- Name: room_swaps room_swaps_second_original_room_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_swaps
    ADD CONSTRAINT room_swaps_second_original_room_id_fkey FOREIGN KEY (second_original_room_id) REFERENCES public.accommodation_rooms(id);


--
-- PostgreSQL database dump complete
--


