-- Author: Tinashe K
-- Canonical clean-slate baseline for notifications-service.

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
-- Name: protect_notification_attempt_evidence(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_notification_attempt_evidence() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN RAISE EXCEPTION 'Notification delivery attempt evidence is append-only and immutable'; END $$;


--
-- Name: protect_notification_callback_evidence(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_notification_callback_evidence() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN RAISE EXCEPTION 'Notification provider callback evidence is append-only and immutable'; END $$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: in_app_notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.in_app_notifications (
    id uuid NOT NULL,
    notification_request_id uuid NOT NULL,
    recipient_user_id uuid,
    recipient_key character varying(160) NOT NULL,
    title character varying(500),
    body text NOT NULL,
    delivered_at timestamp with time zone NOT NULL,
    read_at timestamp with time zone,
    read_by_user_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_in_app_delete_pair CHECK ((((deleted_at IS NULL) AND (deleted_by_user_id IS NULL)) OR ((deleted_at IS NOT NULL) AND (deleted_by_user_id IS NOT NULL)))),
    CONSTRAINT ck_in_app_read_evidence CHECK ((((read_at IS NULL) AND (read_by_user_id IS NULL)) OR ((read_at IS NOT NULL) AND (read_by_user_id IS NOT NULL))))
);


--
-- Name: in_app_notifications_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.in_app_notifications_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    notification_request_id uuid,
    recipient_user_id uuid,
    recipient_key character varying(160),
    title character varying(500),
    body text,
    delivered_at timestamp with time zone,
    read_at timestamp with time zone,
    read_by_user_id uuid,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: notification_consents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_consents (
    id uuid NOT NULL,
    recipient_user_id uuid,
    recipient_key character varying(160) NOT NULL,
    channel character varying(20) NOT NULL,
    category character varying(30) NOT NULL,
    status character varying(20) NOT NULL,
    source character varying(80) NOT NULL,
    evidence_reference character varying(300),
    effective_from timestamp with time zone NOT NULL,
    effective_until timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_notification_consent_category CHECK (((category)::text = ANY ((ARRAY['TRANSACTIONAL'::character varying, 'WORKFLOW'::character varying, 'SECURITY'::character varying, 'MARKETING'::character varying])::text[]))),
    CONSTRAINT ck_notification_consent_channel CHECK (((channel)::text = ANY ((ARRAY['EMAIL'::character varying, 'SMS'::character varying, 'IN_APP'::character varying])::text[]))),
    CONSTRAINT ck_notification_consent_status CHECK (((status)::text = ANY ((ARRAY['OPTED_IN'::character varying, 'OPTED_OUT'::character varying, 'NOT_REQUIRED'::character varying])::text[]))),
    CONSTRAINT ck_notification_consent_window CHECK (((effective_until IS NULL) OR (effective_until > effective_from)))
);


--
-- Name: notification_consents_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_consents_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    recipient_user_id uuid,
    recipient_key character varying(160),
    channel character varying(20),
    category character varying(30),
    status character varying(20),
    source character varying(80),
    evidence_reference character varying(300),
    effective_from timestamp with time zone,
    effective_until timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: notification_delivery_attempts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_delivery_attempts (
    id uuid NOT NULL,
    notification_request_id uuid NOT NULL,
    attempt_number integer NOT NULL,
    provider_code character varying(80) NOT NULL,
    started_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone NOT NULL,
    outcome character varying(20) NOT NULL,
    provider_message_id character varying(240),
    error_code character varying(100),
    error_message character varying(1000),
    response_metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_notification_attempt_number CHECK ((attempt_number > 0)),
    CONSTRAINT ck_notification_attempt_outcome CHECK (((outcome)::text = ANY ((ARRAY['SENT'::character varying, 'RETRYABLE_FAILURE'::character varying, 'PERMANENT_FAILURE'::character varying])::text[]))),
    CONSTRAINT ck_notification_attempt_result CHECK (((((outcome)::text = 'SENT'::text) AND (provider_message_id IS NOT NULL) AND (error_message IS NULL)) OR (((outcome)::text <> 'SENT'::text) AND (error_message IS NOT NULL))))
);


--
-- Name: notification_delivery_attempts_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_delivery_attempts_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    notification_request_id uuid,
    attempt_number integer,
    provider_code character varying(80),
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    outcome character varying(20),
    provider_message_id character varying(240),
    error_code character varying(100),
    error_message character varying(1000),
    response_metadata jsonb,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: notification_delivery_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_delivery_outbox (
    id uuid NOT NULL,
    event_type character varying(160) NOT NULL,
    routing_key character varying(160) NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    status character varying(20) NOT NULL,
    attempt_count integer NOT NULL,
    next_attempt_at timestamp with time zone NOT NULL,
    published_at timestamp with time zone,
    last_error character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: notification_event_inbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_event_inbox (
    id uuid NOT NULL,
    source_service character varying(80) NOT NULL,
    source_event_id uuid NOT NULL,
    event_type character varying(120) NOT NULL,
    payload jsonb,
    received_at timestamp with time zone NOT NULL,
    processed_at timestamp with time zone,
    status character varying(20) DEFAULT 'RECEIVED'::character varying NOT NULL,
    processing_error character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    raw_payload text NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    max_attempts integer DEFAULT 10 NOT NULL,
    next_attempt_at timestamp with time zone,
    last_attempt_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    manual_retry_by_user_id uuid,
    manual_retry_at timestamp with time zone,
    manual_retry_reason character varying(1000),
    CONSTRAINT ck_notification_inbox_attempts CHECK (((attempt_count >= 0) AND (max_attempts > 0) AND (attempt_count <= max_attempts))),
    CONSTRAINT ck_notification_inbox_delete_pair CHECK ((((deleted_at IS NULL) AND (deleted_by_user_id IS NULL)) OR ((deleted_at IS NOT NULL) AND (deleted_by_user_id IS NOT NULL)))),
    CONSTRAINT ck_notification_inbox_retry_evidence CHECK ((((manual_retry_by_user_id IS NULL) AND (manual_retry_at IS NULL) AND (manual_retry_reason IS NULL)) OR ((manual_retry_by_user_id IS NOT NULL) AND (manual_retry_at IS NOT NULL) AND (length(TRIM(BOTH FROM manual_retry_reason)) >= 10)))),
    CONSTRAINT ck_notification_inbox_status CHECK (((status)::text = ANY ((ARRAY['RECEIVED'::character varying, 'PROCESSING'::character varying, 'PROCESSED'::character varying, 'RETRY_SCHEDULED'::character varying, 'DEAD'::character varying])::text[])))
);


--
-- Name: notification_event_inbox_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_event_inbox_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    source_service character varying(80),
    source_event_id uuid,
    event_type character varying(120),
    raw_payload text,
    received_at timestamp with time zone,
    processed_at timestamp with time zone,
    status character varying(20),
    processing_error character varying(1000),
    attempt_count integer,
    max_attempts integer,
    next_attempt_at timestamp with time zone,
    last_attempt_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    manual_retry_by_user_id uuid,
    manual_retry_at timestamp with time zone,
    manual_retry_reason character varying(1000)
);


--
-- Name: notification_provider_callbacks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_provider_callbacks (
    id uuid NOT NULL,
    provider_code character varying(80) NOT NULL,
    provider_event_id character varying(240) NOT NULL,
    provider_message_id character varying(240) NOT NULL,
    delivery_status character varying(30) NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    received_at timestamp with time zone NOT NULL,
    notification_request_id uuid,
    error_code character varying(100),
    error_message character varying(1000),
    callback_payload jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_notification_callback_delete_pair CHECK ((((deleted_at IS NULL) AND (deleted_by_user_id IS NULL)) OR ((deleted_at IS NOT NULL) AND (deleted_by_user_id IS NOT NULL)))),
    CONSTRAINT ck_notification_callback_payload_object CHECK ((jsonb_typeof(callback_payload) = 'object'::text)),
    CONSTRAINT ck_notification_callback_status CHECK (((delivery_status)::text = ANY ((ARRAY['DELIVERED'::character varying, 'BOUNCED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: notification_provider_callbacks_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_provider_callbacks_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    provider_code character varying(80),
    provider_event_id character varying(240),
    provider_message_id character varying(240),
    delivery_status character varying(30),
    occurred_at timestamp with time zone,
    received_at timestamp with time zone,
    notification_request_id uuid,
    error_code character varying(100),
    error_message character varying(1000),
    callback_payload jsonb,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: notification_request_attachments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_request_attachments (
    id uuid NOT NULL,
    notification_request_id uuid CONSTRAINT notification_request_attachmen_notification_request_id_not_null NOT NULL,
    attachment_sequence integer NOT NULL,
    source_document_id uuid NOT NULL,
    file_name character varying(240) NOT NULL,
    content_type character varying(160) NOT NULL,
    checksum_sha256 character varying(64) NOT NULL,
    download_url character varying(2000) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_notification_attachment_sequence CHECK ((attachment_sequence > 0))
);


--
-- Name: notification_request_attachments_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_request_attachments_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    notification_request_id uuid,
    attachment_sequence integer,
    source_document_id uuid,
    file_name character varying(240),
    content_type character varying(160),
    checksum_sha256 character varying(64),
    download_url character varying(2000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: notification_request_number_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notification_request_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_requests (
    id uuid NOT NULL,
    request_number character varying(60) NOT NULL,
    idempotency_key character varying(160) NOT NULL,
    source_service character varying(80) NOT NULL,
    source_event_id uuid,
    event_type character varying(120) NOT NULL,
    template_id uuid NOT NULL,
    template_code character varying(80) NOT NULL,
    template_version integer NOT NULL,
    channel character varying(20) NOT NULL,
    category character varying(30) NOT NULL,
    recipient_user_id uuid,
    recipient_key character varying(160) NOT NULL,
    recipient_address character varying(320) NOT NULL,
    subject character varying(500),
    body text NOT NULL,
    priority character varying(20) DEFAULT 'NORMAL'::character varying NOT NULL,
    status character varying(20) DEFAULT 'QUEUED'::character varying NOT NULL,
    consent_decision character varying(30) NOT NULL,
    scheduled_at timestamp with time zone NOT NULL,
    next_attempt_at timestamp with time zone,
    attempt_count integer DEFAULT 0 NOT NULL,
    max_attempts integer DEFAULT 5 NOT NULL,
    provider_code character varying(80),
    provider_message_id character varying(240),
    sent_at timestamp with time zone,
    failed_at timestamp with time zone,
    last_error_code character varying(100),
    last_error_message character varying(1000),
    cancelled_by_user_id uuid,
    cancelled_at timestamp with time zone,
    cancellation_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    provider_delivery_status character varying(30),
    provider_status_at timestamp with time zone,
    provider_status_detail character varying(1000),
    manual_retry_by_user_id uuid,
    manual_retry_at timestamp with time zone,
    manual_retry_reason character varying(1000),
    CONSTRAINT ck_notification_provider_delivery_status CHECK (((provider_delivery_status IS NULL) OR ((provider_delivery_status)::text = ANY ((ARRAY['ACCEPTED'::character varying, 'DELIVERED'::character varying, 'BOUNCED'::character varying, 'FAILED'::character varying])::text[])))),
    CONSTRAINT ck_notification_provider_status_evidence CHECK ((((provider_delivery_status IS NULL) AND (provider_status_at IS NULL)) OR ((provider_delivery_status IS NOT NULL) AND (provider_status_at IS NOT NULL)))),
    CONSTRAINT ck_notification_request_attempts CHECK (((attempt_count >= 0) AND (max_attempts > 0) AND (attempt_count <= max_attempts))),
    CONSTRAINT ck_notification_request_cancel CHECK (((((status)::text = 'CANCELLED'::text) AND (cancelled_at IS NOT NULL) AND (cancelled_by_user_id IS NOT NULL) AND (length(TRIM(BOTH FROM cancellation_reason)) > 0)) OR ((status)::text <> 'CANCELLED'::text))),
    CONSTRAINT ck_notification_request_category CHECK (((category)::text = ANY ((ARRAY['TRANSACTIONAL'::character varying, 'WORKFLOW'::character varying, 'SECURITY'::character varying, 'MARKETING'::character varying])::text[]))),
    CONSTRAINT ck_notification_request_channel CHECK (((channel)::text = ANY ((ARRAY['EMAIL'::character varying, 'SMS'::character varying, 'IN_APP'::character varying])::text[]))),
    CONSTRAINT ck_notification_request_failure CHECK (((((status)::text = 'FAILED'::text) AND (failed_at IS NOT NULL) AND (last_error_message IS NOT NULL)) OR ((status)::text <> 'FAILED'::text))),
    CONSTRAINT ck_notification_request_priority CHECK (((priority)::text = ANY ((ARRAY['LOW'::character varying, 'NORMAL'::character varying, 'HIGH'::character varying, 'URGENT'::character varying])::text[]))),
    CONSTRAINT ck_notification_request_retry_evidence CHECK ((((manual_retry_by_user_id IS NULL) AND (manual_retry_at IS NULL) AND (manual_retry_reason IS NULL)) OR ((manual_retry_by_user_id IS NOT NULL) AND (manual_retry_at IS NOT NULL) AND (length(TRIM(BOTH FROM manual_retry_reason)) >= 10)))),
    CONSTRAINT ck_notification_request_sent CHECK (((((status)::text = 'SENT'::text) AND (sent_at IS NOT NULL) AND (provider_message_id IS NOT NULL)) OR ((status)::text <> 'SENT'::text))),
    CONSTRAINT ck_notification_request_status CHECK (((status)::text = ANY ((ARRAY['QUEUED'::character varying, 'PROCESSING'::character varying, 'SENT'::character varying, 'RETRY_SCHEDULED'::character varying, 'FAILED'::character varying, 'SUPPRESSED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: notification_requests_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_requests_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    request_number character varying(60),
    idempotency_key character varying(160),
    source_service character varying(80),
    source_event_id uuid,
    event_type character varying(120),
    template_id uuid,
    template_code character varying(80),
    template_version integer,
    channel character varying(20),
    category character varying(30),
    recipient_user_id uuid,
    recipient_key character varying(160),
    recipient_address character varying(320),
    subject character varying(500),
    body text,
    priority character varying(20),
    status character varying(20),
    consent_decision character varying(30),
    scheduled_at timestamp with time zone,
    next_attempt_at timestamp with time zone,
    attempt_count integer,
    max_attempts integer,
    provider_code character varying(80),
    provider_message_id character varying(240),
    sent_at timestamp with time zone,
    failed_at timestamp with time zone,
    last_error_code character varying(100),
    last_error_message character varying(1000),
    cancelled_by_user_id uuid,
    cancelled_at timestamp with time zone,
    cancellation_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    provider_delivery_status character varying(30),
    provider_status_at timestamp with time zone,
    provider_status_detail character varying(1000),
    manual_retry_by_user_id uuid,
    manual_retry_at timestamp with time zone,
    manual_retry_reason character varying(1000)
);


--
-- Name: notification_templates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_templates (
    id uuid NOT NULL,
    code character varying(80) NOT NULL,
    template_version integer NOT NULL,
    name character varying(180) NOT NULL,
    event_type character varying(120) NOT NULL,
    channel character varying(20) NOT NULL,
    category character varying(30) NOT NULL,
    locale character varying(20) DEFAULT 'en-ZW'::character varying NOT NULL,
    subject_template character varying(500),
    body_template text NOT NULL,
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
    CONSTRAINT ck_notification_template_approval CHECK (((((status)::text = 'DRAFT'::text) AND (approved_by_user_id IS NULL) AND (approved_at IS NULL) AND (approval_reason IS NULL)) OR (((status)::text <> 'DRAFT'::text) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (length(TRIM(BOTH FROM approval_reason)) > 0) AND (approved_by_user_id <> prepared_by_user_id)))),
    CONSTRAINT ck_notification_template_category CHECK (((category)::text = ANY ((ARRAY['TRANSACTIONAL'::character varying, 'WORKFLOW'::character varying, 'SECURITY'::character varying, 'MARKETING'::character varying])::text[]))),
    CONSTRAINT ck_notification_template_channel CHECK (((channel)::text = ANY ((ARRAY['EMAIL'::character varying, 'SMS'::character varying, 'IN_APP'::character varying])::text[]))),
    CONSTRAINT ck_notification_template_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'RETIRED'::character varying])::text[]))),
    CONSTRAINT ck_notification_template_version CHECK ((template_version > 0))
);


--
-- Name: notification_templates_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_templates_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code character varying(80),
    template_version integer,
    name character varying(180),
    event_type character varying(120),
    channel character varying(20),
    category character varying(30),
    locale character varying(20),
    subject_template character varying(500),
    body_template text,
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
-- Name: revinfo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.revinfo (
    rev integer NOT NULL,
    revtstmp bigint NOT NULL,
    actor_user_id uuid,
    service_name character varying(100) DEFAULT 'notifications-service'::character varying NOT NULL,
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
-- Data for Name: in_app_notifications; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: in_app_notifications_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notification_consents; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notification_consents_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notification_delivery_attempts; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notification_delivery_attempts_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notification_delivery_outbox; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notification_event_inbox; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notification_event_inbox_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notification_provider_callbacks; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notification_provider_callbacks_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notification_request_attachments; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notification_request_attachments_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notification_requests; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notification_requests_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notification_templates; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.notification_templates (id, code, template_version, name, event_type, channel, category, locale, subject_template, body_template, status, prepared_by_user_id, approved_by_user_id, approved_at, approval_reason, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('a1cc32f0-f60e-7396-a588-0288150b985c', 'APPLICATION_SUBMITTED_EMAIL', 1, 'Application submitted email', 'APPLICATION_SUBMITTED', 'EMAIL', 'TRANSACTIONAL', 'en-ZW', 'Application {{applicationNumber}} submitted', 'Dear {{firstName}}, your application {{applicationNumber}} has been submitted successfully.', 'ACTIVE', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '2026-08-13 13:08:52.655482+00', 'Approved baseline transactional template for required ERP workflow notifications.', '2026-08-13 13:08:52.655482+00', '2026-08-13 13:08:52.655482+00', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', NULL, NULL, 0);
INSERT INTO public.notification_templates (id, code, template_version, name, event_type, channel, category, locale, subject_template, body_template, status, prepared_by_user_id, approved_by_user_id, approved_at, approval_reason, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('11d82fb8-1c7c-a845-85c3-98800451e06e', 'APPLICATION_SUBMITTED_IN_APP', 1, 'Application submitted in-app', 'APPLICATION_SUBMITTED', 'IN_APP', 'TRANSACTIONAL', 'en-ZW', 'Application {{applicationNumber}} submitted', 'Dear {{firstName}}, your application {{applicationNumber}} has been submitted successfully.', 'ACTIVE', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '2026-08-13 13:08:52.655482+00', 'Approved baseline transactional template for required ERP workflow notifications.', '2026-08-13 13:08:52.655482+00', '2026-08-13 13:08:52.655482+00', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', NULL, NULL, 0);
INSERT INTO public.notification_templates (id, code, template_version, name, event_type, channel, category, locale, subject_template, body_template, status, prepared_by_user_id, approved_by_user_id, approved_at, approval_reason, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('b9c80bd3-5969-0888-4424-21870d668ac5', 'MISSING_DOCUMENTS_EMAIL', 1, 'Missing application documents email', 'MISSING_DOCUMENTS', 'EMAIL', 'TRANSACTIONAL', 'en-ZW', 'Documents required for application {{applicationNumber}}', 'Dear {{firstName}}, application {{applicationNumber}} requires the following documents: {{documentList}}.', 'ACTIVE', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '2026-08-13 13:08:52.655482+00', 'Approved baseline transactional template for required ERP workflow notifications.', '2026-08-13 13:08:52.655482+00', '2026-08-13 13:08:52.655482+00', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', NULL, NULL, 0);
INSERT INTO public.notification_templates (id, code, template_version, name, event_type, channel, category, locale, subject_template, body_template, status, prepared_by_user_id, approved_by_user_id, approved_at, approval_reason, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('bd682722-38fe-814e-6015-80baca59538f', 'MISSING_DOCUMENTS_IN_APP', 1, 'Missing application documents in-app', 'MISSING_DOCUMENTS', 'IN_APP', 'TRANSACTIONAL', 'en-ZW', 'Documents required for application {{applicationNumber}}', 'Dear {{firstName}}, application {{applicationNumber}} requires the following documents: {{documentList}}.', 'ACTIVE', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '2026-08-13 13:08:52.655482+00', 'Approved baseline transactional template for required ERP workflow notifications.', '2026-08-13 13:08:52.655482+00', '2026-08-13 13:08:52.655482+00', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', NULL, NULL, 0);
INSERT INTO public.notification_templates (id, code, template_version, name, event_type, channel, category, locale, subject_template, body_template, status, prepared_by_user_id, approved_by_user_id, approved_at, approval_reason, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('4df9890a-6f35-2302-6432-a249f981a2c4', 'PAYMENT_CONFIRMED_EMAIL', 1, 'Application payment confirmed email', 'PAYMENT_CONFIRMED', 'EMAIL', 'TRANSACTIONAL', 'en-ZW', 'Payment confirmed for application {{applicationNumber}}', 'Dear {{firstName}}, payment reference {{paymentReference}} for application {{applicationNumber}} has been confirmed.', 'ACTIVE', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '2026-08-13 13:08:52.655482+00', 'Approved baseline transactional template for required ERP workflow notifications.', '2026-08-13 13:08:52.655482+00', '2026-08-13 13:08:52.655482+00', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', NULL, NULL, 0);
INSERT INTO public.notification_templates (id, code, template_version, name, event_type, channel, category, locale, subject_template, body_template, status, prepared_by_user_id, approved_by_user_id, approved_at, approval_reason, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('55956b14-94f9-7484-7f0e-a8f663bf3530', 'PAYMENT_CONFIRMED_IN_APP', 1, 'Application payment confirmed in-app', 'PAYMENT_CONFIRMED', 'IN_APP', 'TRANSACTIONAL', 'en-ZW', 'Payment confirmed for application {{applicationNumber}}', 'Dear {{firstName}}, payment reference {{paymentReference}} for application {{applicationNumber}} has been confirmed.', 'ACTIVE', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '2026-08-13 13:08:52.655482+00', 'Approved baseline transactional template for required ERP workflow notifications.', '2026-08-13 13:08:52.655482+00', '2026-08-13 13:08:52.655482+00', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', NULL, NULL, 0);
INSERT INTO public.notification_templates (id, code, template_version, name, event_type, channel, category, locale, subject_template, body_template, status, prepared_by_user_id, approved_by_user_id, approved_at, approval_reason, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('9be30f3e-fdaf-dfbe-d0a4-874f38d50509', 'VERIFICATION_DECISION_EMAIL', 1, 'Application verification decision email', 'VERIFICATION_DECISION', 'EMAIL', 'TRANSACTIONAL', 'en-ZW', 'Application {{applicationNumber}} verification update', 'Dear {{firstName}}, application {{applicationNumber}} now has verification status {{decision}}.', 'ACTIVE', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '2026-08-13 13:08:52.655482+00', 'Approved baseline transactional template for required ERP workflow notifications.', '2026-08-13 13:08:52.655482+00', '2026-08-13 13:08:52.655482+00', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', NULL, NULL, 0);
INSERT INTO public.notification_templates (id, code, template_version, name, event_type, channel, category, locale, subject_template, body_template, status, prepared_by_user_id, approved_by_user_id, approved_at, approval_reason, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('e96ae2f6-6614-74d8-34f6-3930d5415d75', 'VERIFICATION_DECISION_IN_APP', 1, 'Application verification decision in-app', 'VERIFICATION_DECISION', 'IN_APP', 'TRANSACTIONAL', 'en-ZW', 'Application {{applicationNumber}} verification update', 'Dear {{firstName}}, application {{applicationNumber}} now has verification status {{decision}}.', 'ACTIVE', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '2026-08-13 13:08:52.655482+00', 'Approved baseline transactional template for required ERP workflow notifications.', '2026-08-13 13:08:52.655482+00', '2026-08-13 13:08:52.655482+00', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', NULL, NULL, 0);
INSERT INTO public.notification_templates (id, code, template_version, name, event_type, channel, category, locale, subject_template, body_template, status, prepared_by_user_id, approved_by_user_id, approved_at, approval_reason, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('72d3365f-b3d1-4588-fb95-e1e34a10e9a9', 'OFFER_DISPATCHED_EMAIL', 1, 'Admission offer dispatched email', 'OFFER_DISPATCHED', 'EMAIL', 'TRANSACTIONAL', 'en-ZW', 'Admission offer {{offerNumber}}', 'Dear {{firstName}}, offer {{offerNumber}} for {{programmeName}} is available. Respond by {{acceptanceDeadline}}.', 'ACTIVE', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '2026-08-13 13:08:52.655482+00', 'Approved baseline transactional template for required ERP workflow notifications.', '2026-08-13 13:08:52.655482+00', '2026-08-13 13:08:52.655482+00', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', NULL, NULL, 0);
INSERT INTO public.notification_templates (id, code, template_version, name, event_type, channel, category, locale, subject_template, body_template, status, prepared_by_user_id, approved_by_user_id, approved_at, approval_reason, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('57eb3874-d5a7-b50e-e6e3-75646398f127', 'OFFER_DISPATCHED_IN_APP', 1, 'Admission offer dispatched in-app', 'OFFER_DISPATCHED', 'IN_APP', 'TRANSACTIONAL', 'en-ZW', 'Admission offer {{offerNumber}}', 'Dear {{firstName}}, offer {{offerNumber}} for {{programmeName}} is available. Respond by {{acceptanceDeadline}}.', 'ACTIVE', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '2026-08-13 13:08:52.655482+00', 'Approved baseline transactional template for required ERP workflow notifications.', '2026-08-13 13:08:52.655482+00', '2026-08-13 13:08:52.655482+00', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', NULL, NULL, 0);
INSERT INTO public.notification_templates (id, code, template_version, name, event_type, channel, category, locale, subject_template, body_template, status, prepared_by_user_id, approved_by_user_id, approved_at, approval_reason, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('9b4d417f-19b1-c312-e9d0-b3e754e58c50', 'OFFER_RESPONSE_EMAIL', 1, 'Admission offer response email', 'OFFER_RESPONSE', 'EMAIL', 'TRANSACTIONAL', 'en-ZW', 'Offer {{offerNumber}} response recorded', 'Dear {{firstName}}, your {{response}} response to offer {{offerNumber}} has been recorded.', 'ACTIVE', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '2026-08-13 13:08:52.655482+00', 'Approved baseline transactional template for required ERP workflow notifications.', '2026-08-13 13:08:52.655482+00', '2026-08-13 13:08:52.655482+00', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', NULL, NULL, 0);
INSERT INTO public.notification_templates (id, code, template_version, name, event_type, channel, category, locale, subject_template, body_template, status, prepared_by_user_id, approved_by_user_id, approved_at, approval_reason, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('f262793b-66ef-f8c3-9da5-ab3a4a29e616', 'OFFER_RESPONSE_IN_APP', 1, 'Admission offer response in-app', 'OFFER_RESPONSE', 'IN_APP', 'TRANSACTIONAL', 'en-ZW', 'Offer {{offerNumber}} response recorded', 'Dear {{firstName}}, your {{response}} response to offer {{offerNumber}} has been recorded.', 'ACTIVE', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '2026-08-13 13:08:52.655482+00', 'Approved baseline transactional template for required ERP workflow notifications.', '2026-08-13 13:08:52.655482+00', '2026-08-13 13:08:52.655482+00', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', NULL, NULL, 0);
INSERT INTO public.notification_templates (id, code, template_version, name, event_type, channel, category, locale, subject_template, body_template, status, prepared_by_user_id, approved_by_user_id, approved_at, approval_reason, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('5a837529-5f1b-10ef-f27c-1f76e2143c63', 'STUDENT_CONVERSION_EMAIL', 1, 'Student conversion completed email', 'STUDENT_CONVERSION', 'EMAIL', 'TRANSACTIONAL', 'en-ZW', 'Student record {{studentNumber}} created', 'Dear {{firstName}}, your student record {{studentNumber}} for {{programmeName}} has been created.', 'ACTIVE', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '2026-08-13 13:08:52.655482+00', 'Approved baseline transactional template for required ERP workflow notifications.', '2026-08-13 13:08:52.655482+00', '2026-08-13 13:08:52.655482+00', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', NULL, NULL, 0);
INSERT INTO public.notification_templates (id, code, template_version, name, event_type, channel, category, locale, subject_template, body_template, status, prepared_by_user_id, approved_by_user_id, approved_at, approval_reason, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('d1c9c101-a0a9-91cc-1eab-42b9763c5881', 'STUDENT_CONVERSION_IN_APP', 1, 'Student conversion completed in-app', 'STUDENT_CONVERSION', 'IN_APP', 'TRANSACTIONAL', 'en-ZW', 'Student record {{studentNumber}} created', 'Dear {{firstName}}, your student record {{studentNumber}} for {{programmeName}} has been created.', 'ACTIVE', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '2026-08-13 13:08:52.655482+00', 'Approved baseline transactional template for required ERP workflow notifications.', '2026-08-13 13:08:52.655482+00', '2026-08-13 13:08:52.655482+00', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', NULL, NULL, 0);
INSERT INTO public.notification_templates (id, code, template_version, name, event_type, channel, category, locale, subject_template, body_template, status, prepared_by_user_id, approved_by_user_id, approved_at, approval_reason, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('829f95a3-bf14-32aa-7e1f-c51e98f2f9b3', 'REGISTRATION_ACTION_EMAIL', 1, 'Student registration action email', 'REGISTRATION_ACTION', 'EMAIL', 'TRANSACTIONAL', 'en-ZW', 'Registration action for {{studentNumber}}', 'Dear {{firstName}}, registration for {{studentNumber}} requires this action: {{requiredAction}}.', 'ACTIVE', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '2026-08-13 13:08:52.655482+00', 'Approved baseline transactional template for required ERP workflow notifications.', '2026-08-13 13:08:52.655482+00', '2026-08-13 13:08:52.655482+00', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', NULL, NULL, 0);
INSERT INTO public.notification_templates (id, code, template_version, name, event_type, channel, category, locale, subject_template, body_template, status, prepared_by_user_id, approved_by_user_id, approved_at, approval_reason, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('61efdaa7-6921-418f-24d9-51c6ea22a8ae', 'REGISTRATION_ACTION_IN_APP', 1, 'Student registration action in-app', 'REGISTRATION_ACTION', 'IN_APP', 'TRANSACTIONAL', 'en-ZW', 'Registration action for {{studentNumber}}', 'Dear {{firstName}}, registration for {{studentNumber}} requires this action: {{requiredAction}}.', 'ACTIVE', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '2026-08-13 13:08:52.655482+00', 'Approved baseline transactional template for required ERP workflow notifications.', '2026-08-13 13:08:52.655482+00', '2026-08-13 13:08:52.655482+00', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', NULL, NULL, 0);
INSERT INTO public.notification_templates (id, code, template_version, name, event_type, channel, category, locale, subject_template, body_template, status, prepared_by_user_id, approved_by_user_id, approved_at, approval_reason, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('386f3283-e775-42e2-0db6-6ca13d9474ad', 'WORKFLOW_TASK_EMAIL', 1, 'Operational workflow task email', 'WORKFLOW_TASK', 'EMAIL', 'TRANSACTIONAL', 'en-ZW', 'Workflow task {{taskReference}}', '{{taskTitle}} requires action by {{dueAt}}. Reference: {{taskReference}}.', 'ACTIVE', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '2026-08-13 13:08:52.655482+00', 'Approved baseline transactional template for required ERP workflow notifications.', '2026-08-13 13:08:52.655482+00', '2026-08-13 13:08:52.655482+00', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', NULL, NULL, 0);
INSERT INTO public.notification_templates (id, code, template_version, name, event_type, channel, category, locale, subject_template, body_template, status, prepared_by_user_id, approved_by_user_id, approved_at, approval_reason, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('838d2331-e0f1-03e8-9f5e-f96500f9b2b4', 'WORKFLOW_TASK_IN_APP', 1, 'Operational workflow task in-app', 'WORKFLOW_TASK', 'IN_APP', 'TRANSACTIONAL', 'en-ZW', 'Workflow task {{taskReference}}', '{{taskTitle}} requires action by {{dueAt}}. Reference: {{taskReference}}.', 'ACTIVE', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '2026-08-13 13:08:52.655482+00', 'Approved baseline transactional template for required ERP workflow notifications.', '2026-08-13 13:08:52.655482+00', '2026-08-13 13:08:52.655482+00', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', NULL, NULL, 0);
INSERT INTO public.notification_templates (id, code, template_version, name, event_type, channel, category, locale, subject_template, body_template, status, prepared_by_user_id, approved_by_user_id, approved_at, approval_reason, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('aa787f54-2f02-e353-13c9-eec547044088', 'REFEREE_REFERENCE_REQUEST_EMAIL', 1, 'Postgraduate referee reference request email', 'REFEREE_REFERENCE_REQUEST', 'EMAIL', 'TRANSACTIONAL', 'en-ZW', 'Reference request for {{applicantName}} ({{applicationNumber}})', 'Dear {{refereeName}}, {{applicantName}} has nominated you as a referee for {{applicationTypeName}} application {{applicationNumber}}. Please provide your confidential reference by {{expiresAt}} using this secure link: {{responseUrl}}', 'ACTIVE', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '2026-08-13 13:08:52.666796+00', 'Approved transactional template for applicant-nominated postgraduate referee requests.', '2026-08-13 13:08:52.666796+00', '2026-08-13 13:08:52.666796+00', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', NULL, NULL, 0);
INSERT INTO public.notification_templates (id, code, template_version, name, event_type, channel, category, locale, subject_template, body_template, status, prepared_by_user_id, approved_by_user_id, approved_at, approval_reason, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('9a415a2d-d623-0567-e9e4-7d8b78ebb3ff', 'ADMISSION_OFFER_PUBLISHED_EMAIL', 1, 'Published admission offer email', 'ADMISSION_OFFER_PUBLISHED', 'EMAIL', 'TRANSACTIONAL', 'en-ZW', 'Your University of Zimbabwe admission offer {{offerNumber}}', 'Dear {{applicantName}}, your admission offer {{offerNumber}} for {{programmeName}} is now available in the applicant portal. The official offer letter is attached. Sign in to review and respond before {{acceptanceDeadline}}.', 'ACTIVE', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '2026-08-13 13:08:52.6728+00', 'Approved transactional template for individually published admission offers.', '2026-08-13 13:08:52.6728+00', '2026-08-13 13:08:52.6728+00', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', NULL, NULL, 0);


--
-- Data for Name: notification_templates_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: revinfo; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Name: notification_request_number_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.notification_request_number_seq', 1, false);


--
-- Name: revinfo_rev_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.revinfo_rev_seq', 1, false);


--
-- Name: in_app_notifications_aud in_app_notifications_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.in_app_notifications_aud
    ADD CONSTRAINT in_app_notifications_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: in_app_notifications in_app_notifications_notification_request_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.in_app_notifications
    ADD CONSTRAINT in_app_notifications_notification_request_id_key UNIQUE (notification_request_id);


--
-- Name: in_app_notifications in_app_notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.in_app_notifications
    ADD CONSTRAINT in_app_notifications_pkey PRIMARY KEY (id);


--
-- Name: notification_consents_aud notification_consents_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_consents_aud
    ADD CONSTRAINT notification_consents_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: notification_consents notification_consents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_consents
    ADD CONSTRAINT notification_consents_pkey PRIMARY KEY (id);


--
-- Name: notification_delivery_attempts_aud notification_delivery_attempts_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_delivery_attempts_aud
    ADD CONSTRAINT notification_delivery_attempts_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: notification_delivery_attempts notification_delivery_attempts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_delivery_attempts
    ADD CONSTRAINT notification_delivery_attempts_pkey PRIMARY KEY (id);


--
-- Name: notification_delivery_outbox notification_delivery_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_delivery_outbox
    ADD CONSTRAINT notification_delivery_outbox_pkey PRIMARY KEY (id);


--
-- Name: notification_event_inbox_aud notification_event_inbox_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_event_inbox_aud
    ADD CONSTRAINT notification_event_inbox_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: notification_event_inbox notification_event_inbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_event_inbox
    ADD CONSTRAINT notification_event_inbox_pkey PRIMARY KEY (id);


--
-- Name: notification_provider_callbacks_aud notification_provider_callbacks_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_provider_callbacks_aud
    ADD CONSTRAINT notification_provider_callbacks_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: notification_provider_callbacks notification_provider_callbacks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_provider_callbacks
    ADD CONSTRAINT notification_provider_callbacks_pkey PRIMARY KEY (id);


--
-- Name: notification_request_attachments_aud notification_request_attachments_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_request_attachments_aud
    ADD CONSTRAINT notification_request_attachments_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: notification_request_attachments notification_request_attachments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_request_attachments
    ADD CONSTRAINT notification_request_attachments_pkey PRIMARY KEY (id);


--
-- Name: notification_requests_aud notification_requests_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_requests_aud
    ADD CONSTRAINT notification_requests_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: notification_requests notification_requests_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_requests
    ADD CONSTRAINT notification_requests_idempotency_key_key UNIQUE (idempotency_key);


--
-- Name: notification_requests notification_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_requests
    ADD CONSTRAINT notification_requests_pkey PRIMARY KEY (id);


--
-- Name: notification_requests notification_requests_request_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_requests
    ADD CONSTRAINT notification_requests_request_number_key UNIQUE (request_number);


--
-- Name: notification_templates_aud notification_templates_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_templates_aud
    ADD CONSTRAINT notification_templates_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: notification_templates notification_templates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_templates
    ADD CONSTRAINT notification_templates_pkey PRIMARY KEY (id);


--
-- Name: revinfo revinfo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revinfo
    ADD CONSTRAINT revinfo_pkey PRIMARY KEY (rev);


--
-- Name: notification_request_attachments uk_notification_attachment_sequence; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_request_attachments
    ADD CONSTRAINT uk_notification_attachment_sequence UNIQUE (notification_request_id, attachment_sequence);


--
-- Name: notification_delivery_attempts uk_notification_attempt; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_delivery_attempts
    ADD CONSTRAINT uk_notification_attempt UNIQUE (notification_request_id, attempt_number);


--
-- Name: notification_event_inbox uk_notification_inbox_event; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_event_inbox
    ADD CONSTRAINT uk_notification_inbox_event UNIQUE (source_service, source_event_id);


--
-- Name: notification_provider_callbacks uk_notification_provider_callback; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_provider_callbacks
    ADD CONSTRAINT uk_notification_provider_callback UNIQUE (provider_code, provider_event_id);


--
-- Name: notification_templates uk_notification_template_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_templates
    ADD CONSTRAINT uk_notification_template_version UNIQUE (code, template_version);


--
-- Name: idx_in_app_notification_recipient; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_in_app_notification_recipient ON public.in_app_notifications USING btree (recipient_user_id, delivered_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_notification_callback_message; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_callback_message ON public.notification_provider_callbacks USING btree (provider_code, provider_message_id, received_at DESC);


--
-- Name: idx_notification_delivery_outbox_dispatch; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_delivery_outbox_dispatch ON public.notification_delivery_outbox USING btree (status, next_attempt_at, occurred_at);


--
-- Name: idx_notification_dispatch_queue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_dispatch_queue ON public.notification_requests USING btree (priority, next_attempt_at, scheduled_at, id) WHERE (((status)::text = ANY ((ARRAY['QUEUED'::character varying, 'RETRY_SCHEDULED'::character varying])::text[])) AND (deleted_at IS NULL));


--
-- Name: idx_notification_inbox_processing; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_inbox_processing ON public.notification_event_inbox USING btree (next_attempt_at, received_at, id) WHERE (((status)::text = ANY ((ARRAY['RECEIVED'::character varying, 'RETRY_SCHEDULED'::character varying])::text[])) AND (deleted_at IS NULL));


--
-- Name: idx_notification_recipient_history; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_recipient_history ON public.notification_requests USING btree (lower((recipient_key)::text), created_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_notification_requests_provider_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_requests_provider_status ON public.notification_requests USING btree (provider_delivery_status, provider_status_at DESC) WHERE ((provider_delivery_status IS NOT NULL) AND (deleted_at IS NULL));


--
-- Name: idx_notification_template_event; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_template_event ON public.notification_templates USING btree (event_type, channel, status) WHERE (deleted_at IS NULL);


--
-- Name: uk_active_notification_template; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_active_notification_template ON public.notification_templates USING btree (lower((code)::text), channel, locale) WHERE (((status)::text = 'ACTIVE'::text) AND (deleted_at IS NULL));


--
-- Name: uk_current_notification_consent; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_current_notification_consent ON public.notification_consents USING btree (lower((recipient_key)::text), channel, category) WHERE ((effective_until IS NULL) AND (deleted_at IS NULL));


--
-- Name: uk_notification_provider_message; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_notification_provider_message ON public.notification_requests USING btree (provider_code, provider_message_id) WHERE ((provider_code IS NOT NULL) AND (provider_message_id IS NOT NULL) AND (deleted_at IS NULL));


--
-- Name: notification_delivery_attempts trg_protect_notification_attempt; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_notification_attempt BEFORE DELETE OR UPDATE ON public.notification_delivery_attempts FOR EACH ROW EXECUTE FUNCTION public.protect_notification_attempt_evidence();


--
-- Name: notification_provider_callbacks trg_protect_notification_callback; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_notification_callback BEFORE DELETE OR UPDATE ON public.notification_provider_callbacks FOR EACH ROW EXECUTE FUNCTION public.protect_notification_callback_evidence();


--
-- Name: in_app_notifications_aud in_app_notifications_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.in_app_notifications_aud
    ADD CONSTRAINT in_app_notifications_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: in_app_notifications in_app_notifications_notification_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.in_app_notifications
    ADD CONSTRAINT in_app_notifications_notification_request_id_fkey FOREIGN KEY (notification_request_id) REFERENCES public.notification_requests(id);


--
-- Name: notification_consents_aud notification_consents_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_consents_aud
    ADD CONSTRAINT notification_consents_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: notification_delivery_attempts_aud notification_delivery_attempts_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_delivery_attempts_aud
    ADD CONSTRAINT notification_delivery_attempts_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: notification_delivery_attempts notification_delivery_attempts_notification_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_delivery_attempts
    ADD CONSTRAINT notification_delivery_attempts_notification_request_id_fkey FOREIGN KEY (notification_request_id) REFERENCES public.notification_requests(id);


--
-- Name: notification_event_inbox_aud notification_event_inbox_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_event_inbox_aud
    ADD CONSTRAINT notification_event_inbox_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: notification_provider_callbacks_aud notification_provider_callbacks_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_provider_callbacks_aud
    ADD CONSTRAINT notification_provider_callbacks_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: notification_provider_callbacks notification_provider_callbacks_notification_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_provider_callbacks
    ADD CONSTRAINT notification_provider_callbacks_notification_request_id_fkey FOREIGN KEY (notification_request_id) REFERENCES public.notification_requests(id);


--
-- Name: notification_request_attachments_aud notification_request_attachments_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_request_attachments_aud
    ADD CONSTRAINT notification_request_attachments_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: notification_request_attachments notification_request_attachments_notification_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_request_attachments
    ADD CONSTRAINT notification_request_attachments_notification_request_id_fkey FOREIGN KEY (notification_request_id) REFERENCES public.notification_requests(id);


--
-- Name: notification_requests_aud notification_requests_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_requests_aud
    ADD CONSTRAINT notification_requests_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: notification_requests notification_requests_template_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_requests
    ADD CONSTRAINT notification_requests_template_id_fkey FOREIGN KEY (template_id) REFERENCES public.notification_templates(id);


--
-- Name: notification_templates_aud notification_templates_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_templates_aud
    ADD CONSTRAINT notification_templates_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- PostgreSQL database dump complete
--


