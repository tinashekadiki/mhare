-- Author: Tinashe K
-- Canonical clean-slate baseline for core-identity-service.

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
-- Name: prevent_student_portal_provisioning_identity_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_student_portal_provisioning_identity_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF ROW(
            NEW.conversion_request_id,
            NEW.student_id,
            NEW.student_number,
            NEW.user_id,
            NEW.role_assignment_id,
            NEW.provisioned_at
        ) IS DISTINCT FROM ROW(
            OLD.conversion_request_id,
            OLD.student_id,
            OLD.student_number,
            OLD.user_id,
            OLD.role_assignment_id,
            OLD.provisioned_at
        ) THEN
        RAISE EXCEPTION 'Student portal provisioning identity is immutable';
    END IF;
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: audit_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_events (
    id uuid NOT NULL,
    actor_user_id uuid,
    event_type character varying(100) NOT NULL,
    subject_type character varying(100) NOT NULL,
    subject_id uuid,
    summary character varying(1000) NOT NULL,
    before_json jsonb,
    after_json jsonb,
    occurred_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_audit_events_event_type CHECK ((length(TRIM(BOTH FROM event_type)) > 0)),
    CONSTRAINT ck_audit_events_subject_type CHECK ((length(TRIM(BOTH FROM subject_type)) > 0)),
    CONSTRAINT ck_audit_events_summary CHECK ((length(TRIM(BOTH FROM summary)) > 0))
);


--
-- Name: audit_events_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_events_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    actor_user_id uuid,
    event_type character varying(100),
    subject_type character varying(100),
    subject_id uuid,
    summary character varying(1000),
    before_json jsonb,
    after_json jsonb,
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
-- Name: countries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.countries (
    id uuid NOT NULL,
    iso2_code character varying(2) NOT NULL,
    iso3_code character varying(3) NOT NULL,
    name character varying(150) NOT NULL,
    nationality_name character varying(150) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: countries_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.countries_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    iso2_code character varying(2),
    iso3_code character varying(3),
    name character varying(150),
    nationality_name character varying(150),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: institution_profile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.institution_profile (
    id uuid NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(200) NOT NULL,
    legal_name character varying(250) NOT NULL,
    default_currency_code character varying(3) DEFAULT 'USD'::character varying NOT NULL,
    country_code character varying(2) NOT NULL,
    timezone character varying(80) NOT NULL,
    contact_details_json jsonb,
    branding_json jsonb,
    legacy_code character varying(50),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: institution_profile_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.institution_profile_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code character varying(50),
    name character varying(200),
    legal_name character varying(250),
    default_currency_code character varying(3),
    country_code character varying(2),
    timezone character varying(80),
    contact_details_json jsonb,
    branding_json jsonb,
    legacy_code character varying(50),
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
    CONSTRAINT ck_core_identity_outbox_attempt_count CHECK ((attempt_count >= 0)),
    CONSTRAINT ck_core_identity_outbox_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PUBLISHED'::character varying, 'DEAD'::character varying])::text[])))
);


--
-- Name: login_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.login_events (
    id uuid NOT NULL,
    user_id uuid,
    keycloak_user_id uuid,
    username character varying(150),
    email character varying(200),
    occurred_at timestamp with time zone NOT NULL,
    ip_address character varying(80),
    user_agent character varying(500),
    outcome character varying(30) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    identity_session_id character varying(150)
);


--
-- Name: login_events_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.login_events_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    user_id uuid,
    keycloak_user_id uuid,
    username character varying(150),
    email character varying(200),
    occurred_at timestamp with time zone,
    ip_address character varying(80),
    user_agent character varying(500),
    outcome character varying(30),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    identity_session_id character varying(150)
);


--
-- Name: lookup_sets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lookup_sets (
    id uuid NOT NULL,
    code character varying(80) NOT NULL,
    name character varying(150) NOT NULL,
    description character varying(500),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: lookup_sets_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lookup_sets_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code character varying(80),
    name character varying(150),
    description character varying(500),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: lookup_values; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lookup_values (
    id uuid NOT NULL,
    lookup_set_id uuid NOT NULL,
    code character varying(80) NOT NULL,
    name character varying(150) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: lookup_values_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lookup_values_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    lookup_set_id uuid,
    code character varying(80),
    name character varying(150),
    sort_order integer,
    is_active boolean,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permissions (
    id uuid NOT NULL,
    code character varying(120) NOT NULL,
    name character varying(180) NOT NULL,
    category character varying(40) NOT NULL,
    description character varying(500),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: permissions_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permissions_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code character varying(120),
    name character varying(180),
    category character varying(40),
    description character varying(500),
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
    service_name character varying(100) DEFAULT 'core-identity-service'::character varying NOT NULL,
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
-- Name: revinfo_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.revinfo_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: role_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_permissions (
    id uuid NOT NULL,
    role_id uuid NOT NULL,
    permission_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: role_permissions_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_permissions_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    role_id uuid,
    permission_id uuid,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    id uuid NOT NULL,
    code character varying(80) NOT NULL,
    name character varying(150) NOT NULL,
    scope character varying(30) NOT NULL,
    is_system_managed boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: roles_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code character varying(80),
    name character varying(150),
    scope character varying(30),
    is_system_managed boolean,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: student_portal_access_provisioning; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_portal_access_provisioning (
    id uuid NOT NULL,
    conversion_request_id uuid CONSTRAINT student_portal_access_provisioni_conversion_request_id_not_null NOT NULL,
    student_id uuid NOT NULL,
    student_number character varying(40) NOT NULL,
    user_id uuid NOT NULL,
    role_assignment_id uuid NOT NULL,
    status character varying(30) NOT NULL,
    provisioned_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_student_portal_access_status CHECK (((status)::text = ANY ((ARRAY['PROVISIONED'::character varying, 'REVOKED'::character varying])::text[])))
);


--
-- Name: student_portal_access_provisioning_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_portal_access_provisioning_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    conversion_request_id uuid,
    student_id uuid,
    student_number character varying(40),
    user_id uuid,
    role_assignment_id uuid,
    status character varying(30),
    provisioned_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: user_role_assignments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_role_assignments (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    role_id uuid NOT NULL,
    academic_unit_id uuid,
    starts_at timestamp with time zone NOT NULL,
    ends_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: user_role_assignments_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_role_assignments_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    user_id uuid,
    role_id uuid,
    academic_unit_id uuid,
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
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid NOT NULL,
    keycloak_user_id uuid,
    username character varying(150) NOT NULL,
    email character varying(200) NOT NULL,
    phone_number character varying(50),
    display_name character varying(200) NOT NULL,
    status character varying(30) NOT NULL,
    last_login_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: users_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    keycloak_user_id uuid,
    username character varying(150),
    email character varying(200),
    phone_number character varying(50),
    display_name character varying(200),
    status character varying(30),
    last_login_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: workflow_decisions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workflow_decisions (
    id uuid NOT NULL,
    workflow_task_id uuid NOT NULL,
    decision_code character varying(50) NOT NULL,
    decision_comment character varying(2000) NOT NULL,
    actor_user_id uuid NOT NULL,
    decided_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: workflow_decisions_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workflow_decisions_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    workflow_task_id uuid,
    decision_code character varying(50),
    decision_comment character varying(2000),
    actor_user_id uuid,
    decided_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: workflow_instances; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workflow_instances (
    id uuid NOT NULL,
    workflow_code character varying(80) NOT NULL,
    subject_type character varying(80) NOT NULL,
    subject_id uuid NOT NULL,
    subject_reference character varying(160) NOT NULL,
    title character varying(240) NOT NULL,
    status character varying(30) NOT NULL,
    initiated_by_user_id uuid NOT NULL,
    initiated_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_workflow_instances_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: workflow_instances_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workflow_instances_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    workflow_code character varying(80),
    subject_type character varying(80),
    subject_id uuid,
    subject_reference character varying(160),
    title character varying(240),
    status character varying(30),
    initiated_by_user_id uuid,
    initiated_at timestamp with time zone,
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
-- Name: workflow_tasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workflow_tasks (
    id uuid NOT NULL,
    workflow_instance_id uuid NOT NULL,
    task_reference character varying(50) NOT NULL,
    title character varying(240) NOT NULL,
    description character varying(2000) NOT NULL,
    assignee_type character varying(20) NOT NULL,
    assigned_user_id uuid,
    assigned_role_id uuid,
    scope_type character varying(30) NOT NULL,
    academic_unit_id uuid,
    status character varying(30) NOT NULL,
    due_at timestamp with time zone,
    claimed_by_user_id uuid,
    claimed_at timestamp with time zone,
    completed_by_user_id uuid,
    completed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_workflow_tasks_assignee CHECK (((((assignee_type)::text = 'USER'::text) AND (assigned_user_id IS NOT NULL) AND (assigned_role_id IS NULL)) OR (((assignee_type)::text = 'ROLE'::text) AND (assigned_role_id IS NOT NULL) AND (assigned_user_id IS NULL)))),
    CONSTRAINT ck_workflow_tasks_assignee_type CHECK (((assignee_type)::text = ANY ((ARRAY['USER'::character varying, 'ROLE'::character varying])::text[]))),
    CONSTRAINT ck_workflow_tasks_scope CHECK (((((scope_type)::text = 'INSTITUTION'::text) AND (academic_unit_id IS NULL)) OR (((scope_type)::text = 'ACADEMIC_UNIT'::text) AND (academic_unit_id IS NOT NULL)))),
    CONSTRAINT ck_workflow_tasks_scope_type CHECK (((scope_type)::text = ANY ((ARRAY['INSTITUTION'::character varying, 'ACADEMIC_UNIT'::character varying])::text[]))),
    CONSTRAINT ck_workflow_tasks_status CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'CLAIMED'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: workflow_tasks_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workflow_tasks_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    workflow_instance_id uuid,
    task_reference character varying(50),
    title character varying(240),
    description character varying(2000),
    assignee_type character varying(20),
    assigned_user_id uuid,
    assigned_role_id uuid,
    scope_type character varying(30),
    academic_unit_id uuid,
    status character varying(30),
    due_at timestamp with time zone,
    claimed_by_user_id uuid,
    claimed_at timestamp with time zone,
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
-- Data for Name: audit_events; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: audit_events_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: countries; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.countries (id, iso2_code, iso3_code, name, nationality_name, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('31000000-0000-4000-8000-000000000001', 'ZW', 'ZWE', 'Zimbabwe', 'Zimbabwean', '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.countries (id, iso2_code, iso3_code, name, nationality_name, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('31000000-0000-4000-8000-000000000002', 'ZA', 'ZAF', 'South Africa', 'South African', '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.countries (id, iso2_code, iso3_code, name, nationality_name, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('31000000-0000-4000-8000-000000000003', 'ZM', 'ZMB', 'Zambia', 'Zambian', '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.countries (id, iso2_code, iso3_code, name, nationality_name, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('31000000-0000-4000-8000-000000000004', 'BW', 'BWA', 'Botswana', 'Motswana', '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.countries (id, iso2_code, iso3_code, name, nationality_name, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('31000000-0000-4000-8000-000000000005', 'MW', 'MWI', 'Malawi', 'Malawian', '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);


--
-- Data for Name: countries_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: institution_profile; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.institution_profile (id, code, name, legal_name, default_currency_code, country_code, timezone, contact_details_json, branding_json, legacy_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('30000000-0000-4000-8000-000000000001', 'UZ', 'University of Zimbabwe', 'University of Zimbabwe', 'USD', 'ZW', 'Africa/Harare', '{"email": "info@uz.ac.zw", "phone": "", "website": "https://www.uz.ac.zw"}', '{"primaryColor": "#20743a", "documentHeader": "University of Zimbabwe", "secondaryColor": "#f8b334"}', 'UZ', '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);


--
-- Data for Name: institution_profile_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: integration_inbox; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: integration_outbox; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: login_events; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: login_events_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: lookup_sets; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.lookup_sets (id, code, name, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('32000000-0000-4000-8000-000000000001', 'TITLES', 'Titles', 'Personal titles used by applicants, students, staff, and guardians.', '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.lookup_sets (id, code, name, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('32000000-0000-4000-8000-000000000002', 'GENDERS', 'Genders', 'Gender options used where required by institutional processes.', '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.lookup_sets (id, code, name, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('32000000-0000-4000-8000-000000000003', 'MARITAL_STATUSES', 'Marital statuses', 'Marital status options for person records.', '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.lookup_sets (id, code, name, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('32000000-0000-4000-8000-000000000004', 'APPLICANT_CATEGORIES', 'Applicant categories', 'Applicant categories for admissions rules and reporting.', '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.lookup_sets (id, code, name, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('32000000-0000-4000-8000-000000000005', 'DOCUMENT_TYPES', 'Document types', 'Reusable document classifications.', '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);


--
-- Data for Name: lookup_sets_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: lookup_values; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.lookup_values (id, lookup_set_id, code, name, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('9261e6a0-b2df-41a1-aaf4-c63cb7537df4', '32000000-0000-4000-8000-000000000001', 'DR', 'Dr', 40, true, '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.lookup_values (id, lookup_set_id, code, name, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('363cc9cf-ca5e-444a-ba4b-1eea75cbd892', '32000000-0000-4000-8000-000000000001', 'MS', 'Ms', 30, true, '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.lookup_values (id, lookup_set_id, code, name, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('01417ab3-b8c8-48d5-bbf5-e54955422140', '32000000-0000-4000-8000-000000000001', 'MRS', 'Mrs', 20, true, '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.lookup_values (id, lookup_set_id, code, name, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('0c6d2ad1-e75e-4e03-9646-81621ce3677f', '32000000-0000-4000-8000-000000000001', 'MR', 'Mr', 10, true, '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.lookup_values (id, lookup_set_id, code, name, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('c0afdc68-826e-4f7e-aed5-ad818e0bc403', '32000000-0000-4000-8000-000000000002', 'OTHER', 'Other', 30, true, '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.lookup_values (id, lookup_set_id, code, name, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('6c8d66af-061f-41f5-8eef-f83829e78108', '32000000-0000-4000-8000-000000000002', 'MALE', 'Male', 20, true, '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.lookup_values (id, lookup_set_id, code, name, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('8d0b9966-9827-4016-a18f-46d0bf12f554', '32000000-0000-4000-8000-000000000002', 'FEMALE', 'Female', 10, true, '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.lookup_values (id, lookup_set_id, code, name, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('669b177e-f274-41b4-86db-4d4998e199f0', '32000000-0000-4000-8000-000000000003', 'MARRIED', 'Married', 20, true, '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.lookup_values (id, lookup_set_id, code, name, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('7fbb4a67-8872-46e5-8093-4b6094c8672c', '32000000-0000-4000-8000-000000000003', 'SINGLE', 'Single', 10, true, '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.lookup_values (id, lookup_set_id, code, name, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('0d54726f-c265-453c-b315-04a685456598', '32000000-0000-4000-8000-000000000004', 'MATURE_ENTRY', 'Mature Entry', 30, true, '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.lookup_values (id, lookup_set_id, code, name, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('bb496592-cde8-418e-ba92-3113b97e881f', '32000000-0000-4000-8000-000000000004', 'INTERNATIONAL', 'International', 20, true, '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.lookup_values (id, lookup_set_id, code, name, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('cb5ecc30-8745-4dbd-8e52-500a31a046c0', '32000000-0000-4000-8000-000000000004', 'LOCAL', 'Local', 10, true, '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.lookup_values (id, lookup_set_id, code, name, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('713f30bf-8169-46d9-ae70-1c2b9597b35a', '32000000-0000-4000-8000-000000000005', 'QUALIFICATION_CERTIFICATE', 'Qualification Certificate', 30, true, '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.lookup_values (id, lookup_set_id, code, name, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('67922513-0c56-41da-aa62-cdaeaf4f7c8f', '32000000-0000-4000-8000-000000000005', 'PASSPORT', 'Passport', 20, true, '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.lookup_values (id, lookup_set_id, code, name, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('4868a185-9848-44a7-ad66-3e8e88c0c27d', '32000000-0000-4000-8000-000000000005', 'NATIONAL_ID', 'National ID', 10, true, '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);


--
-- Data for Name: lookup_values_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: permissions; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.permissions (id, code, name, category, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('20000000-0000-4000-8000-000000000001', 'CORE_USER_MANAGE', 'Manage users', 'CORE', 'Create, update, disable, and inspect platform users.', '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.permissions (id, code, name, category, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('20000000-0000-4000-8000-000000000002', 'CORE_ROLE_MANAGE', 'Manage roles', 'CORE', 'Create roles and grant permissions to roles.', '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.permissions (id, code, name, category, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('20000000-0000-4000-8000-000000000003', 'CORE_PERMISSION_MANAGE', 'Manage permissions', 'CORE', 'Inspect and maintain permission catalogue entries.', '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.permissions (id, code, name, category, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('20000000-0000-4000-8000-000000000004', 'CORE_ROLE_ASSIGN', 'Assign roles', 'CORE', 'Assign and expire role assignments for users.', '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.permissions (id, code, name, category, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('20000000-0000-4000-8000-000000000005', 'ADMISSIONS_APPLICATION_APPLY', 'Apply for admission', 'ADMISSIONS', 'Create and maintain an owned applicant application.', '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.permissions (id, code, name, category, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('20000000-0000-4000-8000-000000000006', 'ADMISSIONS_APPLICATION_REVIEW', 'Review applications', 'ADMISSIONS', 'Review, verify, and progress submitted applications.', '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.permissions (id, code, name, category, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('20000000-0000-4000-8000-000000000007', 'ADMISSIONS_SETUP_MANAGE', 'Manage admissions setup', 'ADMISSIONS', 'Maintain cycles, application types, fees, subjects, and requirements.', '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.permissions (id, code, name, category, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('20000000-0000-4000-8000-000000000008', 'ADMISSIONS_PAYMENT_OVERRIDE', 'Override application fee gate', 'ADMISSIONS', 'Record authorised application fee waivers or overrides.', '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.permissions (id, code, name, category, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('20000000-0000-4000-8000-000000000009', 'CORE_INSTITUTION_MANAGE', 'Manage institution profile', 'CORE', 'Maintain institution identity, contact, branding, and operational defaults.', '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.permissions (id, code, name, category, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('20000000-0000-4000-8000-000000000010', 'CORE_REFERENCE_MANAGE', 'Manage reference data', 'CORE', 'Maintain countries, lookup sets, and lookup values.', '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.permissions (id, code, name, category, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('20000000-0000-4000-8000-000000000011', 'CORE_AUDIT_READ', 'Read Core audit records', 'CORE', 'Inspect login history and Core security audit information.', '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.permissions (id, code, name, category, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('20000000-0000-4000-8000-000000000012', 'CORE_WORKFLOW_MANAGE', 'Manage workflow instances', 'CORE', 'Create governed workflow instances and assign tasks by user, role, institution, or academic unit.', '2026-08-13 13:07:20.028723+00', '2026-08-13 13:07:20.028723+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.permissions (id, code, name, category, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('20000000-0000-4000-8000-000000000013', 'CORE_WORKFLOW_TASK', 'Work assigned workflow tasks', 'CORE', 'View, claim, and decide workflow tasks assigned through the authorised role and scope.', '2026-08-13 13:07:20.028723+00', '2026-08-13 13:07:20.028723+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.permissions (id, code, name, category, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('20000000-0000-4000-8000-000000000014', 'ADMISSIONS_APPLICATION_CONFIRM', 'Confirm applications', 'ADMISSIONS', 'Confirm payment, application sections, documents, and qualification evidence.', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.permissions (id, code, name, category, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('20000000-0000-4000-8000-000000000015', 'ADMISSIONS_ACADEMIC_REVIEW_RELEASE', 'Release academic reviews', 'ADMISSIONS', 'Release eligible programme choices to the resolved highest academic unit.', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.permissions (id, code, name, category, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('20000000-0000-4000-8000-000000000016', 'ADMISSIONS_ACADEMIC_UNIT_RECOMMEND', 'Record academic unit recommendations', 'ADMISSIONS', 'Claim and recommend applications assigned to the exact highest academic unit.', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.permissions (id, code, name, category, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('20000000-0000-4000-8000-000000000017', 'ADMISSIONS_SELECTION_APPROVE', 'Approve admissions selections', 'ADMISSIONS', 'Approve, return, or override academic unit recommendations and lock selection rounds.', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.permissions (id, code, name, category, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('20000000-0000-4000-8000-000000000018', 'ADMISSIONS_OFFER_MANAGE', 'Manage admission offers', 'ADMISSIONS', 'Create and inspect governed admission offers and offer batches.', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.permissions (id, code, name, category, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('20000000-0000-4000-8000-000000000019', 'ADMISSIONS_OFFER_APPROVE', 'Approve admission offers', 'ADMISSIONS', 'Approve admission offers after their official letter is stored.', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.permissions (id, code, name, category, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('20000000-0000-4000-8000-000000000020', 'ADMISSIONS_OFFER_DISPATCH', 'Dispatch admission offers', 'ADMISSIONS', 'Dispatch approved offers and preserve dispatch evidence.', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.permissions (id, code, name, category, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('20000000-0000-4000-8000-000000000021', 'ADMISSIONS_ELIGIBILITY_REVIEW', 'Resolve admissions eligibility', 'ADMISSIONS', 'Recalculate eligibility and resolve cases requiring an evidenced manual review.', '2026-08-13 13:07:20.074576+00', '2026-08-13 13:07:20.074576+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.permissions (id, code, name, category, description, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('20000000-0000-4000-8000-000000000022', 'ADMISSIONS_DECISION_MAKE', 'Make admission decisions', 'ADMISSIONS', 'Record the final direct admission or rejection decision for a programme choice.', '2026-08-13 13:07:20.074576+00', '2026-08-13 13:07:20.074576+00', NULL, NULL, NULL, NULL, 0);


--
-- Data for Name: permissions_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: revinfo; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: role_permissions; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('6723fde5-f3b5-42ef-ab1a-e9b9cae3cf62', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001', '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('86828826-823c-4f04-8cb6-668f0b31b963', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000002', '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('2c686432-0695-4e90-8304-bd52d8958196', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000003', '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('ea9ff97f-5ba0-43c5-89b0-383819563b83', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000004', '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('cff59217-cf60-44af-a4a7-a60856e8a5f3', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000005', '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('ed827df4-e045-4667-8699-c7efbd696aa9', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000006', '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('4d2196f1-dd9e-479c-aa69-9be623e573be', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000007', '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('2aef5364-f01b-4693-9e9e-97e5fe2a8d71', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000008', '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('267fba7a-4a06-4ba5-9891-2946844dd54a', '10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000006', '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('14e22419-d8e5-4db0-ba23-d3d717df4742', '10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000007', '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('60fdb342-3046-4e63-ad0e-384a157b04dc', '10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000008', '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('b813e17d-7c0f-4ddd-85b8-e1f0a79e7c32', '10000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000008', '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('eb0e1105-ce16-4d3b-b543-50fea9bf9b58', '10000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000005', '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('5cbb74ee-9dc9-4102-91e6-dfdc07a821cd', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000009', '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('cd969701-84c4-4b70-8056-06165e06ae72', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000010', '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('4a8ed661-e935-45c0-9ce4-8bb38fd8a898', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000011', '2026-08-13 13:07:19.978873+00', '2026-08-13 13:07:19.978873+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('836c625d-fbf2-4baf-aeb0-e3f82f5fa8c5', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000012', '2026-08-13 13:07:20.028723+00', '2026-08-13 13:07:20.028723+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('bd971420-e643-4625-bfbe-34fcf3d7b16a', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000013', '2026-08-13 13:07:20.028723+00', '2026-08-13 13:07:20.028723+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('091aa265-99fe-4c7e-8e1a-bdd812b9298c', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000014', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('13c24aa2-1ce5-456e-93a5-367a44cf887e', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000015', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('617a91a4-4e23-462c-ba2b-cfc28b3e30c3', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000016', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('df8664b5-3502-4654-ac74-fd346007c615', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000017', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('857ab320-a831-4f4c-b90a-ba528aaad9e1', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000018', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('e0b302a2-78ce-4988-981b-8616d4707369', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000019', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('3280e144-caa7-4f0a-bff8-3cf47dda0620', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000020', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('734fea66-f4a7-4f4a-b068-ebfdb816681d', '10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000014', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('9a2ef704-b71c-4d00-bb80-bec39688045f', '10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000015', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('5675e8bb-a618-4203-8550-f76c0c57023e', '10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000017', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('26e0f63a-650c-4fa3-b240-d01e7c3d608f', '10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000018', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('981d71d5-b110-418e-b1b8-7b50fb5ad752', '10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000019', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('ac0947b8-9b5b-4f5f-b4ea-a3bbf2b872f7', '10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000020', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('d7a37c6d-5bd9-4e77-8091-4b20667474b4', '10000000-0000-4000-8000-000000000006', '20000000-0000-4000-8000-000000000006', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('5e099ba9-1ae6-42d2-a79f-61568b9b50c3', '10000000-0000-4000-8000-000000000006', '20000000-0000-4000-8000-000000000013', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('e77496da-a0c6-4faa-822e-ed1195b59024', '10000000-0000-4000-8000-000000000006', '20000000-0000-4000-8000-000000000016', '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('d1800b8f-5881-4675-9dd4-15fbf8ef3c9d', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000021', '2026-08-13 13:07:20.074576+00', '2026-08-13 13:07:20.074576+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('1aafbfdb-6003-479a-b79e-b6870a3e4ce8', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000022', '2026-08-13 13:07:20.074576+00', '2026-08-13 13:07:20.074576+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('492571ca-5730-4702-8ead-06f135da8a45', '10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000021', '2026-08-13 13:07:20.074576+00', '2026-08-13 13:07:20.074576+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.role_permissions (id, role_id, permission_id, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('383f0aa4-2f1c-446d-b1e2-69d043bc7940', '10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000022', '2026-08-13 13:07:20.074576+00', '2026-08-13 13:07:20.074576+00', NULL, NULL, NULL, NULL, 0);


--
-- Data for Name: role_permissions_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: roles; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.roles (id, code, name, scope, is_system_managed, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('10000000-0000-4000-8000-000000000001', 'SYSTEM_ADMIN', 'System Admin', 'SYSTEM', true, '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.roles (id, code, name, scope, is_system_managed, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('10000000-0000-4000-8000-000000000002', 'ADMISSIONS_OFFICER', 'Admissions Officer', 'SYSTEM', true, '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.roles (id, code, name, scope, is_system_managed, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('10000000-0000-4000-8000-000000000003', 'FINANCE_OFFICER', 'Finance Officer', 'SYSTEM', true, '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.roles (id, code, name, scope, is_system_managed, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('10000000-0000-4000-8000-000000000004', 'APPLICANT', 'Applicant', 'SYSTEM', true, '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.roles (id, code, name, scope, is_system_managed, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('10000000-0000-4000-8000-000000000005', 'STUDENT', 'Student', 'SYSTEM', true, '2026-08-13 13:07:19.932802+00', '2026-08-13 13:07:19.932802+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.roles (id, code, name, scope, is_system_managed, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('10000000-0000-4000-8000-000000000006', 'ACADEMIC_UNIT_STAFF', 'Academic Unit Staff', 'ACADEMIC_UNIT', true, '2026-08-13 13:07:20.066214+00', '2026-08-13 13:07:20.066214+00', NULL, NULL, NULL, NULL, 0);


--
-- Data for Name: roles_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_portal_access_provisioning; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_portal_access_provisioning_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: user_role_assignments; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: user_role_assignments_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: users_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: workflow_decisions; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: workflow_decisions_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: workflow_instances; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: workflow_instances_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: workflow_tasks; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: workflow_tasks_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Name: revinfo_rev_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.revinfo_rev_seq', 1, false);


--
-- Name: revinfo_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.revinfo_seq', 1, false);


--
-- Name: audit_events_aud audit_events_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_events_aud
    ADD CONSTRAINT audit_events_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: audit_events audit_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_events
    ADD CONSTRAINT audit_events_pkey PRIMARY KEY (id);


--
-- Name: countries_aud countries_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.countries_aud
    ADD CONSTRAINT countries_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: countries countries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.countries
    ADD CONSTRAINT countries_pkey PRIMARY KEY (id);


--
-- Name: institution_profile_aud institution_profile_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.institution_profile_aud
    ADD CONSTRAINT institution_profile_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: institution_profile institution_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.institution_profile
    ADD CONSTRAINT institution_profile_pkey PRIMARY KEY (id);


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
-- Name: login_events_aud login_events_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_events_aud
    ADD CONSTRAINT login_events_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: login_events login_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_events
    ADD CONSTRAINT login_events_pkey PRIMARY KEY (id);


--
-- Name: lookup_sets_aud lookup_sets_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lookup_sets_aud
    ADD CONSTRAINT lookup_sets_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: lookup_sets lookup_sets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lookup_sets
    ADD CONSTRAINT lookup_sets_pkey PRIMARY KEY (id);


--
-- Name: lookup_values_aud lookup_values_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lookup_values_aud
    ADD CONSTRAINT lookup_values_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: lookup_values lookup_values_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lookup_values
    ADD CONSTRAINT lookup_values_pkey PRIMARY KEY (id);


--
-- Name: permissions_aud permissions_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions_aud
    ADD CONSTRAINT permissions_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);


--
-- Name: revinfo revinfo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revinfo
    ADD CONSTRAINT revinfo_pkey PRIMARY KEY (rev);


--
-- Name: role_permissions_aud role_permissions_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions_aud
    ADD CONSTRAINT role_permissions_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: role_permissions role_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (id);


--
-- Name: roles_aud roles_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles_aud
    ADD CONSTRAINT roles_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: student_portal_access_provisioning_aud student_portal_access_provisioning_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_portal_access_provisioning_aud
    ADD CONSTRAINT student_portal_access_provisioning_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: student_portal_access_provisioning student_portal_access_provisioning_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_portal_access_provisioning
    ADD CONSTRAINT student_portal_access_provisioning_pkey PRIMARY KEY (id);


--
-- Name: countries uk_countries_iso2_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.countries
    ADD CONSTRAINT uk_countries_iso2_code UNIQUE (iso2_code);


--
-- Name: countries uk_countries_iso3_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.countries
    ADD CONSTRAINT uk_countries_iso3_code UNIQUE (iso3_code);


--
-- Name: institution_profile uk_institution_profile_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.institution_profile
    ADD CONSTRAINT uk_institution_profile_code UNIQUE (code);


--
-- Name: lookup_sets uk_lookup_sets_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lookup_sets
    ADD CONSTRAINT uk_lookup_sets_code UNIQUE (code);


--
-- Name: lookup_values uk_lookup_values_set_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lookup_values
    ADD CONSTRAINT uk_lookup_values_set_code UNIQUE (lookup_set_id, code);


--
-- Name: permissions uk_permissions_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT uk_permissions_code UNIQUE (code);


--
-- Name: role_permissions uk_role_permissions_role_permission; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT uk_role_permissions_role_permission UNIQUE (role_id, permission_id);


--
-- Name: roles uk_roles_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT uk_roles_code UNIQUE (code);


--
-- Name: student_portal_access_provisioning uk_student_portal_access_conversion; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_portal_access_provisioning
    ADD CONSTRAINT uk_student_portal_access_conversion UNIQUE (conversion_request_id);


--
-- Name: student_portal_access_provisioning uk_student_portal_access_student; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_portal_access_provisioning
    ADD CONSTRAINT uk_student_portal_access_student UNIQUE (student_id);


--
-- Name: users uk_users_email; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk_users_email UNIQUE (email);


--
-- Name: users uk_users_keycloak_user_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk_users_keycloak_user_id UNIQUE (keycloak_user_id);


--
-- Name: users uk_users_username; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk_users_username UNIQUE (username);


--
-- Name: workflow_tasks uk_workflow_tasks_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_tasks
    ADD CONSTRAINT uk_workflow_tasks_reference UNIQUE (task_reference);


--
-- Name: user_role_assignments_aud user_role_assignments_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role_assignments_aud
    ADD CONSTRAINT user_role_assignments_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: user_role_assignments user_role_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role_assignments
    ADD CONSTRAINT user_role_assignments_pkey PRIMARY KEY (id);


--
-- Name: users_aud users_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users_aud
    ADD CONSTRAINT users_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: workflow_decisions_aud workflow_decisions_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_decisions_aud
    ADD CONSTRAINT workflow_decisions_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: workflow_decisions workflow_decisions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_decisions
    ADD CONSTRAINT workflow_decisions_pkey PRIMARY KEY (id);


--
-- Name: workflow_instances_aud workflow_instances_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_instances_aud
    ADD CONSTRAINT workflow_instances_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: workflow_instances workflow_instances_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_instances
    ADD CONSTRAINT workflow_instances_pkey PRIMARY KEY (id);


--
-- Name: workflow_tasks_aud workflow_tasks_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_tasks_aud
    ADD CONSTRAINT workflow_tasks_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: workflow_tasks workflow_tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_tasks
    ADD CONSTRAINT workflow_tasks_pkey PRIMARY KEY (id);


--
-- Name: idx_core_identity_inbox_processed_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_core_identity_inbox_processed_at ON public.integration_inbox USING btree (processed_at);


--
-- Name: idx_core_identity_outbox_dispatch; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_core_identity_outbox_dispatch ON public.integration_outbox USING btree (next_attempt_at, occurred_at) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: idx_workflow_decisions_task; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_workflow_decisions_task ON public.workflow_decisions USING btree (workflow_task_id, decided_at);


--
-- Name: idx_workflow_instances_subject; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_workflow_instances_subject ON public.workflow_instances USING btree (subject_type, subject_id) WHERE (deleted_at IS NULL);


--
-- Name: idx_workflow_tasks_assigned_role_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_workflow_tasks_assigned_role_scope ON public.workflow_tasks USING btree (assigned_role_id, academic_unit_id, status) WHERE ((deleted_at IS NULL) AND (assigned_role_id IS NOT NULL));


--
-- Name: idx_workflow_tasks_assigned_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_workflow_tasks_assigned_user ON public.workflow_tasks USING btree (assigned_user_id, status) WHERE ((deleted_at IS NULL) AND (assigned_user_id IS NOT NULL));


--
-- Name: idx_workflow_tasks_open_queue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_workflow_tasks_open_queue ON public.workflow_tasks USING btree (status, due_at, created_at) WHERE ((deleted_at IS NULL) AND ((status)::text = ANY ((ARRAY['OPEN'::character varying, 'CLAIMED'::character varying])::text[])));


--
-- Name: ix_audit_events_actor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_audit_events_actor ON public.audit_events USING btree (actor_user_id, occurred_at DESC);


--
-- Name: ix_audit_events_occurred_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_audit_events_occurred_at ON public.audit_events USING btree (occurred_at DESC);


--
-- Name: ix_audit_events_subject; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_audit_events_subject ON public.audit_events USING btree (subject_type, subject_id, occurred_at DESC);


--
-- Name: ix_login_events_keycloak_user_id_occurred_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_login_events_keycloak_user_id_occurred_at ON public.login_events USING btree (keycloak_user_id, occurred_at DESC);


--
-- Name: ix_login_events_user_id_occurred_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_login_events_user_id_occurred_at ON public.login_events USING btree (user_id, occurred_at DESC);


--
-- Name: ix_lookup_values_lookup_set_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_lookup_values_lookup_set_id ON public.lookup_values USING btree (lookup_set_id);


--
-- Name: uk_login_events_identity_session; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_login_events_identity_session ON public.login_events USING btree (keycloak_user_id, identity_session_id) WHERE ((identity_session_id IS NOT NULL) AND (deleted_at IS NULL));


--
-- Name: uk_user_role_assignments_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_user_role_assignments_active ON public.user_role_assignments USING btree (user_id, role_id, academic_unit_id) WHERE ((ends_at IS NULL) AND (deleted_at IS NULL));


--
-- Name: uk_user_role_assignments_active_system; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_user_role_assignments_active_system ON public.user_role_assignments USING btree (user_id, role_id) WHERE ((academic_unit_id IS NULL) AND (ends_at IS NULL) AND (deleted_at IS NULL));


--
-- Name: student_portal_access_provisioning trg_student_portal_provisioning_identity_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_student_portal_provisioning_identity_immutable BEFORE UPDATE ON public.student_portal_access_provisioning FOR EACH ROW EXECUTE FUNCTION public.prevent_student_portal_provisioning_identity_mutation();


--
-- Name: audit_events_aud audit_events_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_events_aud
    ADD CONSTRAINT audit_events_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: countries_aud countries_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.countries_aud
    ADD CONSTRAINT countries_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: institution_profile_aud institution_profile_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.institution_profile_aud
    ADD CONSTRAINT institution_profile_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: login_events_aud login_events_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_events_aud
    ADD CONSTRAINT login_events_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: login_events login_events_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_events
    ADD CONSTRAINT login_events_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: lookup_sets_aud lookup_sets_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lookup_sets_aud
    ADD CONSTRAINT lookup_sets_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: lookup_values_aud lookup_values_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lookup_values_aud
    ADD CONSTRAINT lookup_values_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: lookup_values lookup_values_lookup_set_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lookup_values
    ADD CONSTRAINT lookup_values_lookup_set_id_fkey FOREIGN KEY (lookup_set_id) REFERENCES public.lookup_sets(id);


--
-- Name: permissions_aud permissions_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions_aud
    ADD CONSTRAINT permissions_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: role_permissions_aud role_permissions_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions_aud
    ADD CONSTRAINT role_permissions_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: role_permissions role_permissions_permission_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES public.permissions(id);


--
-- Name: role_permissions role_permissions_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id);


--
-- Name: roles_aud roles_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles_aud
    ADD CONSTRAINT roles_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: student_portal_access_provisioning_aud student_portal_access_provisioning_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_portal_access_provisioning_aud
    ADD CONSTRAINT student_portal_access_provisioning_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: student_portal_access_provisioning student_portal_access_provisioning_role_assignment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_portal_access_provisioning
    ADD CONSTRAINT student_portal_access_provisioning_role_assignment_id_fkey FOREIGN KEY (role_assignment_id) REFERENCES public.user_role_assignments(id);


--
-- Name: student_portal_access_provisioning student_portal_access_provisioning_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_portal_access_provisioning
    ADD CONSTRAINT student_portal_access_provisioning_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: user_role_assignments_aud user_role_assignments_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role_assignments_aud
    ADD CONSTRAINT user_role_assignments_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: user_role_assignments user_role_assignments_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role_assignments
    ADD CONSTRAINT user_role_assignments_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id);


--
-- Name: user_role_assignments user_role_assignments_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role_assignments
    ADD CONSTRAINT user_role_assignments_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: users_aud users_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users_aud
    ADD CONSTRAINT users_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: workflow_decisions workflow_decisions_actor_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_decisions
    ADD CONSTRAINT workflow_decisions_actor_user_id_fkey FOREIGN KEY (actor_user_id) REFERENCES public.users(id);


--
-- Name: workflow_decisions_aud workflow_decisions_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_decisions_aud
    ADD CONSTRAINT workflow_decisions_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: workflow_decisions workflow_decisions_workflow_task_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_decisions
    ADD CONSTRAINT workflow_decisions_workflow_task_id_fkey FOREIGN KEY (workflow_task_id) REFERENCES public.workflow_tasks(id);


--
-- Name: workflow_instances_aud workflow_instances_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_instances_aud
    ADD CONSTRAINT workflow_instances_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: workflow_instances workflow_instances_initiated_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_instances
    ADD CONSTRAINT workflow_instances_initiated_by_user_id_fkey FOREIGN KEY (initiated_by_user_id) REFERENCES public.users(id);


--
-- Name: workflow_tasks workflow_tasks_assigned_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_tasks
    ADD CONSTRAINT workflow_tasks_assigned_role_id_fkey FOREIGN KEY (assigned_role_id) REFERENCES public.roles(id);


--
-- Name: workflow_tasks workflow_tasks_assigned_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_tasks
    ADD CONSTRAINT workflow_tasks_assigned_user_id_fkey FOREIGN KEY (assigned_user_id) REFERENCES public.users(id);


--
-- Name: workflow_tasks_aud workflow_tasks_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_tasks_aud
    ADD CONSTRAINT workflow_tasks_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: workflow_tasks workflow_tasks_claimed_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_tasks
    ADD CONSTRAINT workflow_tasks_claimed_by_user_id_fkey FOREIGN KEY (claimed_by_user_id) REFERENCES public.users(id);


--
-- Name: workflow_tasks workflow_tasks_completed_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_tasks
    ADD CONSTRAINT workflow_tasks_completed_by_user_id_fkey FOREIGN KEY (completed_by_user_id) REFERENCES public.users(id);


--
-- Name: workflow_tasks workflow_tasks_workflow_instance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_tasks
    ADD CONSTRAINT workflow_tasks_workflow_instance_id_fkey FOREIGN KEY (workflow_instance_id) REFERENCES public.workflow_instances(id);


--
-- PostgreSQL database dump complete
--


