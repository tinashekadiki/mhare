-- Author: Tinashe K
-- Canonical clean-slate baseline for documents-reporting-service.

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
-- Name: prevent_progression_result_projection_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_progression_result_projection_change() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'Official progression result evidence is immutable';
END;
$$;


--
-- Name: prevent_reporting_source_snapshot_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_reporting_source_snapshot_change() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Official reporting source projections cannot be deleted';
    END IF;
    IF to_jsonb(NEW) - ARRAY['current_version', 'updated_at', 'modified_by_user_id', 'version']
       IS DISTINCT FROM
       to_jsonb(OLD) - ARRAY['current_version', 'updated_at', 'modified_by_user_id', 'version'] THEN
        RAISE EXCEPTION 'Official reporting source evidence is immutable';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: prevent_uploaded_document_content_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_uploaded_document_content_change() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Uploaded document evidence cannot be physically deleted';
    END IF;
    IF to_jsonb(NEW) - ARRAY[
        'verification_status', 'verified_by_user_id', 'verified_at',
        'verification_comment', 'rejection_reason', 'updated_at',
        'modified_by_user_id', 'deleted_at', 'deleted_by_user_id', 'version'
    ] IS DISTINCT FROM to_jsonb(OLD) - ARRAY[
        'verification_status', 'verified_by_user_id', 'verified_at',
        'verification_comment', 'rejection_reason', 'updated_at',
        'modified_by_user_id', 'deleted_at', 'deleted_by_user_id', 'version'
    ] THEN
        RAISE EXCEPTION 'Uploaded document content and ownership evidence is immutable';
    END IF;
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: generated_documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.generated_documents (
    id uuid NOT NULL,
    document_number character varying(100) NOT NULL,
    document_type character varying(40) NOT NULL,
    student_id uuid,
    student_number character varying(40),
    programme_id uuid NOT NULL,
    programme_version_id uuid,
    academic_period_id uuid,
    academic_period_code character varying(50),
    source_progression_decision_id uuid,
    source_progression_decision_version integer CONSTRAINT generated_documents_source_progression_decision_versio_not_null NOT NULL,
    progression_decision_projection_id uuid,
    template_code character varying(80) NOT NULL,
    template_version integer NOT NULL,
    status character varying(20) NOT NULL,
    storage_bucket character varying(100),
    storage_key character varying(500),
    storage_object_version character varying(200),
    content_type character varying(100),
    checksum_sha256 character varying(64),
    size_bytes bigint,
    page_count integer,
    requested_at timestamp with time zone NOT NULL,
    generation_started_at timestamp with time zone,
    generated_at timestamp with time zone,
    generation_attempt_count integer NOT NULL,
    next_generation_attempt_at timestamp with time zone NOT NULL,
    last_failure_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    offer_letter_projection_id uuid,
    CONSTRAINT ck_generated_document_source_kind CHECK (((((document_type)::text = 'RESULT_SLIP'::text) AND (progression_decision_projection_id IS NOT NULL) AND (source_progression_decision_id IS NOT NULL) AND (offer_letter_projection_id IS NULL)) OR (((document_type)::text = 'OFFER_LETTER'::text) AND (offer_letter_projection_id IS NOT NULL) AND (progression_decision_projection_id IS NULL) AND (source_progression_decision_id IS NULL)))),
    CONSTRAINT ck_generated_document_status CHECK (((status)::text = ANY ((ARRAY['REQUESTED'::character varying, 'GENERATING'::character varying, 'STORED'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT ck_generated_document_storage CHECK (((((status)::text = ANY ((ARRAY['REQUESTED'::character varying, 'GENERATING'::character varying, 'FAILED'::character varying])::text[])) AND (generated_at IS NULL)) OR (((status)::text = 'STORED'::text) AND (storage_bucket IS NOT NULL) AND (storage_key IS NOT NULL) AND ((content_type)::text = 'application/pdf'::text) AND (checksum_sha256 IS NOT NULL) AND (length((checksum_sha256)::text) = 64) AND (size_bytes > 0) AND (page_count > 0) AND (generated_at IS NOT NULL)))),
    CONSTRAINT ck_generated_document_type CHECK (((document_type)::text = ANY ((ARRAY['RESULT_SLIP'::character varying, 'OFFER_LETTER'::character varying])::text[]))),
    CONSTRAINT ck_generated_document_versions CHECK (((source_progression_decision_version > 0) AND (template_version > 0) AND (generation_attempt_count >= 0)))
);


--
-- Name: generated_documents_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.generated_documents_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    document_number character varying(100),
    document_type character varying(40),
    student_id uuid,
    student_number character varying(40),
    programme_id uuid,
    programme_version_id uuid,
    academic_period_id uuid,
    academic_period_code character varying(50),
    source_progression_decision_id uuid,
    source_progression_decision_version integer,
    progression_decision_projection_id uuid,
    template_code character varying(80),
    template_version integer,
    status character varying(20),
    storage_bucket character varying(100),
    storage_key character varying(500),
    storage_object_version character varying(200),
    content_type character varying(100),
    checksum_sha256 character varying(64),
    size_bytes bigint,
    page_count integer,
    requested_at timestamp with time zone,
    generation_started_at timestamp with time zone,
    generated_at timestamp with time zone,
    generation_attempt_count integer,
    next_generation_attempt_at timestamp with time zone,
    last_failure_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    offer_letter_projection_id uuid
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
    CONSTRAINT ck_documents_outbox_attempt_count CHECK ((attempt_count >= 0)),
    CONSTRAINT ck_documents_outbox_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PUBLISHED'::character varying, 'DEAD'::character varying])::text[])))
);


--
-- Name: offer_letter_export_audits; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offer_letter_export_audits (
    id uuid NOT NULL,
    requested_by_user_id uuid NOT NULL,
    intake_id uuid NOT NULL,
    programme_id uuid NOT NULL,
    export_format character varying(30) NOT NULL,
    included_document_count integer NOT NULL,
    requested_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    checksum_sha256 character varying(64),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_offer_letter_export_count CHECK ((included_document_count >= 0)),
    CONSTRAINT ck_offer_letter_export_format CHECK (((export_format)::text = ANY ((ARRAY['MERGED_PDF'::character varying, 'ZIP'::character varying])::text[])))
);


--
-- Name: offer_letter_export_audits_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offer_letter_export_audits_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    requested_by_user_id uuid,
    intake_id uuid,
    programme_id uuid,
    export_format character varying(30),
    included_document_count integer,
    requested_at timestamp with time zone,
    completed_at timestamp with time zone,
    checksum_sha256 character varying(64),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: offer_letter_projections; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offer_letter_projections (
    id uuid NOT NULL,
    source_event_id uuid NOT NULL,
    offer_id uuid NOT NULL,
    offer_version bigint NOT NULL,
    offer_number character varying(60) NOT NULL,
    application_id uuid NOT NULL,
    application_number character varying(60) NOT NULL,
    applicant_number character varying(60) NOT NULL,
    applicant_name character varying(240) NOT NULL,
    applicant_email character varying(250) NOT NULL,
    programme_id uuid NOT NULL,
    programme_code character varying(50) NOT NULL,
    programme_name character varying(200) NOT NULL,
    offer_type character varying(30) NOT NULL,
    conditions_text character varying(4000),
    acceptance_deadline timestamp with time zone NOT NULL,
    registration_date date,
    orientation_date date,
    commencement_date date NOT NULL,
    requested_by_user_id uuid NOT NULL,
    requested_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    document_version integer NOT NULL,
    intake_id uuid,
    applicant_user_id uuid,
    CONSTRAINT ck_offer_letter_document_version CHECK ((document_version > 0)),
    CONSTRAINT ck_offer_letter_projection_type CHECK (((offer_type)::text = ANY ((ARRAY['FIRM'::character varying, 'CONDITIONAL'::character varying])::text[])))
);


--
-- Name: offer_letter_projections_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offer_letter_projections_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    source_event_id uuid,
    offer_id uuid,
    offer_version bigint,
    offer_number character varying(60),
    application_id uuid,
    application_number character varying(60),
    applicant_number character varying(60),
    applicant_name character varying(240),
    applicant_email character varying(250),
    programme_id uuid,
    programme_code character varying(50),
    programme_name character varying(200),
    offer_type character varying(30),
    conditions_text character varying(4000),
    acceptance_deadline timestamp with time zone,
    registration_date date,
    orientation_date date,
    commencement_date date,
    requested_by_user_id uuid,
    requested_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    document_version integer,
    intake_id uuid,
    applicant_user_id uuid
);


--
-- Name: progression_decision_projections; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.progression_decision_projections (
    id uuid NOT NULL,
    source_event_id uuid NOT NULL,
    source_progression_decision_id uuid CONSTRAINT progression_decision_projec_source_progression_decisio_not_null NOT NULL,
    decision_number character varying(80) NOT NULL,
    decision_version integer NOT NULL,
    supersedes_decision_id uuid,
    source_progression_rule_set_id uuid CONSTRAINT progression_decision_projec_source_progression_rule_se_not_null NOT NULL,
    progression_rule_code character varying(40) NOT NULL,
    progression_rule_version integer CONSTRAINT progression_decision_projecti_progression_rule_version_not_null NOT NULL,
    source_registration_roster_import_id uuid CONSTRAINT progression_decision_projec_source_registration_roster_not_null NOT NULL,
    student_id uuid NOT NULL,
    student_number character varying(40) NOT NULL,
    programme_enrolment_id uuid CONSTRAINT progression_decision_projection_programme_enrolment_id_not_null NOT NULL,
    programme_id uuid NOT NULL,
    programme_version_id uuid NOT NULL,
    academic_period_id uuid NOT NULL,
    academic_period_code character varying(50) NOT NULL,
    programme_period_number integer CONSTRAINT progression_decision_projectio_programme_period_number_not_null NOT NULL,
    decision_code character varying(30) NOT NULL,
    decision_label character varying(150) NOT NULL,
    next_programme_period_number integer,
    attempted_credits numeric(8,2) NOT NULL,
    passed_credits numeric(8,2) NOT NULL,
    failed_credits numeric(8,2) NOT NULL,
    failed_modules integer NOT NULL,
    failed_compulsory_modules integer CONSTRAINT progression_decision_project_failed_compulsory_modules_not_null NOT NULL,
    weighted_average numeric(6,2) NOT NULL,
    published_by_user_id uuid NOT NULL,
    published_at timestamp with time zone NOT NULL,
    current_version boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_progression_projection_decision CHECK (((decision_code)::text = ANY ((ARRAY['PROCEED'::character varying, 'PROCEED_WITH_CARRY'::character varying, 'REPEAT'::character varying, 'EXCLUDE'::character varying])::text[]))),
    CONSTRAINT ck_progression_projection_lineage CHECK ((((decision_version = 1) AND (supersedes_decision_id IS NULL)) OR ((decision_version > 1) AND (supersedes_decision_id IS NOT NULL)))),
    CONSTRAINT ck_progression_projection_metrics CHECK (((attempted_credits > (0)::numeric) AND (passed_credits >= (0)::numeric) AND (failed_credits >= (0)::numeric) AND ((passed_credits + failed_credits) = attempted_credits) AND (failed_modules >= 0) AND (failed_compulsory_modules >= 0) AND (failed_compulsory_modules <= failed_modules) AND ((weighted_average >= (0)::numeric) AND (weighted_average <= (100)::numeric)))),
    CONSTRAINT ck_progression_projection_version CHECK (((decision_version > 0) AND (progression_rule_version > 0) AND (programme_period_number > 0)))
);


--
-- Name: progression_decision_projections_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.progression_decision_projections_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    source_event_id uuid,
    source_progression_decision_id uuid,
    decision_number character varying(80),
    decision_version integer,
    supersedes_decision_id uuid,
    source_progression_rule_set_id uuid,
    progression_rule_code character varying(40),
    progression_rule_version integer,
    source_registration_roster_import_id uuid,
    student_id uuid,
    student_number character varying(40),
    programme_enrolment_id uuid,
    programme_id uuid,
    programme_version_id uuid,
    academic_period_id uuid,
    academic_period_code character varying(50),
    programme_period_number integer,
    decision_code character varying(30),
    decision_label character varying(150),
    next_programme_period_number integer,
    attempted_credits numeric(8,2),
    passed_credits numeric(8,2),
    failed_credits numeric(8,2),
    failed_modules integer,
    failed_compulsory_modules integer,
    weighted_average numeric(6,2),
    published_by_user_id uuid,
    published_at timestamp with time zone,
    current_version boolean,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: progression_decision_result_projections; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.progression_decision_result_projections (
    id uuid NOT NULL,
    progression_decision_projection_id uuid CONSTRAINT progression_decision_result_progression_decision_proje_not_null NOT NULL,
    published_result_projection_id uuid CONSTRAINT progression_decision_result_published_result_projectio_not_null NOT NULL,
    source_published_result_id uuid CONSTRAINT progression_decision_result_source_published_result_id_not_null NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: progression_decision_result_projections_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.progression_decision_result_projections_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    progression_decision_projection_id uuid,
    published_result_projection_id uuid,
    source_published_result_id uuid,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: published_offer_letter_projections; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.published_offer_letter_projections (
    id uuid NOT NULL,
    source_event_id uuid NOT NULL,
    offer_id uuid NOT NULL,
    offer_status character varying(30) NOT NULL,
    generated_document_id uuid CONSTRAINT published_offer_letter_projectio_generated_document_id_not_null NOT NULL,
    document_version integer NOT NULL,
    offer_number character varying(60) NOT NULL,
    application_id uuid NOT NULL,
    application_number character varying(60) NOT NULL,
    applicant_user_id uuid NOT NULL,
    applicant_name character varying(240) NOT NULL,
    intake_id uuid NOT NULL,
    programme_id uuid NOT NULL,
    programme_code character varying(50) NOT NULL,
    programme_name character varying(200) NOT NULL,
    published_at timestamp with time zone NOT NULL,
    current_publication boolean NOT NULL,
    superseded_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_published_offer_current CHECK (((current_publication AND (superseded_at IS NULL)) OR ((NOT current_publication) AND (superseded_at IS NOT NULL)))),
    CONSTRAINT ck_published_offer_status CHECK (((offer_status)::text = ANY ((ARRAY['SENT'::character varying, 'ACCEPTED'::character varying, 'DECLINED'::character varying, 'EXPIRED'::character varying, 'CONVERTED'::character varying, 'WITHDRAWN'::character varying])::text[])))
);


--
-- Name: published_offer_letter_projections_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.published_offer_letter_projections_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    source_event_id uuid,
    offer_id uuid,
    offer_status character varying(30),
    generated_document_id uuid,
    document_version integer,
    offer_number character varying(60),
    application_id uuid,
    application_number character varying(60),
    applicant_user_id uuid,
    applicant_name character varying(240),
    intake_id uuid,
    programme_id uuid,
    programme_code character varying(50),
    programme_name character varying(200),
    published_at timestamp with time zone,
    current_publication boolean,
    superseded_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: published_result_projections; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.published_result_projections (
    id uuid NOT NULL,
    source_event_id uuid NOT NULL,
    source_published_result_id uuid CONSTRAINT published_result_projection_source_published_result_id_not_null NOT NULL,
    source_result_batch_id uuid NOT NULL,
    source_module_result_id uuid NOT NULL,
    student_id uuid NOT NULL,
    student_number character varying(40) NOT NULL,
    programme_enrolment_id uuid NOT NULL,
    programme_id uuid NOT NULL,
    programme_version_id uuid NOT NULL,
    academic_period_id uuid NOT NULL,
    academic_period_code character varying(50) NOT NULL,
    module_id uuid NOT NULL,
    module_code character varying(50) NOT NULL,
    module_name character varying(200) NOT NULL,
    curriculum_module_type character varying(20) NOT NULL,
    credit_value numeric(6,2) NOT NULL,
    final_mark numeric(6,2) NOT NULL,
    grade character varying(10) NOT NULL,
    remark character varying(100) NOT NULL,
    passing boolean NOT NULL,
    publication_version integer NOT NULL,
    supersedes_published_result_id uuid,
    result_amendment_id uuid,
    published_by_user_id uuid NOT NULL,
    published_at timestamp with time zone NOT NULL,
    current_version boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_result_projection_lineage CHECK ((((publication_version = 1) AND (supersedes_published_result_id IS NULL)) OR ((publication_version > 1) AND (supersedes_published_result_id IS NOT NULL)))),
    CONSTRAINT ck_result_projection_type CHECK (((curriculum_module_type)::text = ANY ((ARRAY['COMPULSORY'::character varying, 'ELECTIVE'::character varying, 'OPTIONAL'::character varying])::text[]))),
    CONSTRAINT ck_result_projection_values CHECK (((credit_value > (0)::numeric) AND ((final_mark >= (0)::numeric) AND (final_mark <= (100)::numeric)) AND (publication_version > 0)))
);


--
-- Name: published_result_projections_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.published_result_projections_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    source_event_id uuid,
    source_published_result_id uuid,
    source_result_batch_id uuid,
    source_module_result_id uuid,
    student_id uuid,
    student_number character varying(40),
    programme_enrolment_id uuid,
    programme_id uuid,
    programme_version_id uuid,
    academic_period_id uuid,
    academic_period_code character varying(50),
    module_id uuid,
    module_code character varying(50),
    module_name character varying(200),
    curriculum_module_type character varying(20),
    credit_value numeric(6,2),
    final_mark numeric(6,2),
    grade character varying(10),
    remark character varying(100),
    passing boolean,
    publication_version integer,
    supersedes_published_result_id uuid,
    result_amendment_id uuid,
    published_by_user_id uuid,
    published_at timestamp with time zone,
    current_version boolean,
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
    service_name character varying(100) DEFAULT 'documents-reporting-service'::character varying NOT NULL,
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
-- Name: uploaded_documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.uploaded_documents (
    id uuid NOT NULL,
    owner_type character varying(40) NOT NULL,
    owner_id uuid NOT NULL,
    document_type_code character varying(80) NOT NULL,
    original_file_name character varying(255) NOT NULL,
    storage_bucket character varying(100) NOT NULL,
    storage_key character varying(500) NOT NULL,
    storage_object_version character varying(200),
    mime_type character varying(100) NOT NULL,
    file_size_bytes bigint NOT NULL,
    checksum_sha256 character varying(64) NOT NULL,
    uploaded_by_user_id uuid NOT NULL,
    uploaded_at timestamp with time zone NOT NULL,
    verification_status character varying(20) NOT NULL,
    verified_by_user_id uuid,
    verified_at timestamp with time zone,
    verification_comment character varying(1000),
    rejection_reason character varying(1000),
    replaces_document_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_uploaded_documents_file CHECK (((length(TRIM(BOTH FROM original_file_name)) > 0) AND (length(TRIM(BOTH FROM document_type_code)) > 0) AND ((mime_type)::text = ANY ((ARRAY['application/pdf'::character varying, 'image/jpeg'::character varying, 'image/png'::character varying])::text[])) AND (file_size_bytes > 0) AND (length((checksum_sha256)::text) = 64))),
    CONSTRAINT ck_uploaded_documents_owner_type CHECK (((owner_type)::text = ANY ((ARRAY['APPLICANT'::character varying, 'APPLICATION'::character varying, 'STUDENT'::character varying, 'STAFF'::character varying, 'FINANCE_RECORD'::character varying, 'ACADEMIC_WORKFLOW'::character varying, 'INSTITUTION'::character varying])::text[]))),
    CONSTRAINT ck_uploaded_documents_replacement CHECK (((replaces_document_id IS NULL) OR (replaces_document_id <> id))),
    CONSTRAINT ck_uploaded_documents_soft_delete CHECK ((((deleted_at IS NULL) AND (deleted_by_user_id IS NULL)) OR ((deleted_at IS NOT NULL) AND (deleted_by_user_id IS NOT NULL)))),
    CONSTRAINT ck_uploaded_documents_verification_evidence CHECK (((((verification_status)::text = 'PENDING'::text) AND (verified_by_user_id IS NULL) AND (verified_at IS NULL) AND (verification_comment IS NULL) AND (rejection_reason IS NULL)) OR (((verification_status)::text = 'VERIFIED'::text) AND (verified_by_user_id IS NOT NULL) AND (verified_at IS NOT NULL) AND (rejection_reason IS NULL)) OR (((verification_status)::text = 'REJECTED'::text) AND (verified_by_user_id IS NOT NULL) AND (verified_at IS NOT NULL) AND (rejection_reason IS NOT NULL) AND (length(TRIM(BOTH FROM rejection_reason)) >= 10)))),
    CONSTRAINT ck_uploaded_documents_verification_status CHECK (((verification_status)::text = ANY ((ARRAY['PENDING'::character varying, 'VERIFIED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: uploaded_documents_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.uploaded_documents_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    owner_type character varying(40),
    owner_id uuid,
    document_type_code character varying(80),
    original_file_name character varying(255),
    storage_bucket character varying(100),
    storage_key character varying(500),
    storage_object_version character varying(200),
    mime_type character varying(100),
    file_size_bytes bigint,
    checksum_sha256 character varying(64),
    uploaded_by_user_id uuid,
    uploaded_at timestamp with time zone,
    verification_status character varying(20),
    verified_by_user_id uuid,
    verified_at timestamp with time zone,
    verification_comment character varying(1000),
    rejection_reason character varying(1000),
    replaces_document_id uuid,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Data for Name: generated_documents; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: generated_documents_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: integration_inbox; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: integration_outbox; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: offer_letter_export_audits; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: offer_letter_export_audits_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: offer_letter_projections; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: offer_letter_projections_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: progression_decision_projections; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: progression_decision_projections_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: progression_decision_result_projections; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: progression_decision_result_projections_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: published_offer_letter_projections; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: published_offer_letter_projections_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: published_result_projections; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: published_result_projections_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: revinfo; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: uploaded_documents; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: uploaded_documents_aud; Type: TABLE DATA; Schema: public; Owner: -
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
-- Name: generated_documents_aud generated_documents_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.generated_documents_aud
    ADD CONSTRAINT generated_documents_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: generated_documents generated_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.generated_documents
    ADD CONSTRAINT generated_documents_pkey PRIMARY KEY (id);


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
-- Name: offer_letter_export_audits_aud offer_letter_export_audits_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_letter_export_audits_aud
    ADD CONSTRAINT offer_letter_export_audits_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: offer_letter_export_audits offer_letter_export_audits_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_letter_export_audits
    ADD CONSTRAINT offer_letter_export_audits_pkey PRIMARY KEY (id);


--
-- Name: offer_letter_projections_aud offer_letter_projections_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_letter_projections_aud
    ADD CONSTRAINT offer_letter_projections_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: offer_letter_projections offer_letter_projections_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_letter_projections
    ADD CONSTRAINT offer_letter_projections_pkey PRIMARY KEY (id);


--
-- Name: offer_letter_projections offer_letter_projections_source_event_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_letter_projections
    ADD CONSTRAINT offer_letter_projections_source_event_id_key UNIQUE (source_event_id);


--
-- Name: progression_decision_projections_aud progression_decision_projections_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.progression_decision_projections_aud
    ADD CONSTRAINT progression_decision_projections_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: progression_decision_projections progression_decision_projections_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.progression_decision_projections
    ADD CONSTRAINT progression_decision_projections_pkey PRIMARY KEY (id);


--
-- Name: progression_decision_result_projections_aud progression_decision_result_projections_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.progression_decision_result_projections_aud
    ADD CONSTRAINT progression_decision_result_projections_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: progression_decision_result_projections progression_decision_result_projections_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.progression_decision_result_projections
    ADD CONSTRAINT progression_decision_result_projections_pkey PRIMARY KEY (id);


--
-- Name: published_offer_letter_projections_aud published_offer_letter_projections_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_offer_letter_projections_aud
    ADD CONSTRAINT published_offer_letter_projections_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: published_offer_letter_projections published_offer_letter_projections_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_offer_letter_projections
    ADD CONSTRAINT published_offer_letter_projections_pkey PRIMARY KEY (id);


--
-- Name: published_offer_letter_projections published_offer_letter_projections_source_event_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_offer_letter_projections
    ADD CONSTRAINT published_offer_letter_projections_source_event_id_key UNIQUE (source_event_id);


--
-- Name: published_result_projections_aud published_result_projections_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_result_projections_aud
    ADD CONSTRAINT published_result_projections_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: published_result_projections published_result_projections_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_result_projections
    ADD CONSTRAINT published_result_projections_pkey PRIMARY KEY (id);


--
-- Name: revinfo revinfo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revinfo
    ADD CONSTRAINT revinfo_pkey PRIMARY KEY (rev);


--
-- Name: generated_documents uk_generated_document_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.generated_documents
    ADD CONSTRAINT uk_generated_document_number UNIQUE (document_number);


--
-- Name: generated_documents uk_generated_document_source; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.generated_documents
    ADD CONSTRAINT uk_generated_document_source UNIQUE (document_type, source_progression_decision_id, source_progression_decision_version);


--
-- Name: generated_documents uk_generated_document_storage_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.generated_documents
    ADD CONSTRAINT uk_generated_document_storage_key UNIQUE (storage_bucket, storage_key);


--
-- Name: offer_letter_projections uk_offer_letter_projection_document_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_letter_projections
    ADD CONSTRAINT uk_offer_letter_projection_document_version UNIQUE (offer_id, document_version);


--
-- Name: progression_decision_projections uk_progression_projection_event; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.progression_decision_projections
    ADD CONSTRAINT uk_progression_projection_event UNIQUE (source_event_id);


--
-- Name: progression_decision_projections uk_progression_projection_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.progression_decision_projections
    ADD CONSTRAINT uk_progression_projection_number UNIQUE (decision_number);


--
-- Name: progression_decision_projections uk_progression_projection_source; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.progression_decision_projections
    ADD CONSTRAINT uk_progression_projection_source UNIQUE (source_progression_decision_id);


--
-- Name: progression_decision_result_projections uk_progression_result_projection; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.progression_decision_result_projections
    ADD CONSTRAINT uk_progression_result_projection UNIQUE (progression_decision_projection_id, source_published_result_id);


--
-- Name: published_offer_letter_projections uk_published_offer_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_offer_letter_projections
    ADD CONSTRAINT uk_published_offer_version UNIQUE (offer_id, document_version);


--
-- Name: published_result_projections uk_result_projection_source_event; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_result_projections
    ADD CONSTRAINT uk_result_projection_source_event UNIQUE (source_event_id);


--
-- Name: published_result_projections uk_result_projection_source_result; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_result_projections
    ADD CONSTRAINT uk_result_projection_source_result UNIQUE (source_published_result_id);


--
-- Name: uploaded_documents uk_uploaded_documents_storage; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.uploaded_documents
    ADD CONSTRAINT uk_uploaded_documents_storage UNIQUE (storage_bucket, storage_key);


--
-- Name: uploaded_documents_aud uploaded_documents_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.uploaded_documents_aud
    ADD CONSTRAINT uploaded_documents_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: uploaded_documents uploaded_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.uploaded_documents
    ADD CONSTRAINT uploaded_documents_pkey PRIMARY KEY (id);


--
-- Name: idx_documents_outbox_dispatch; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_documents_outbox_dispatch ON public.integration_outbox USING btree (next_attempt_at, occurred_at, id) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: idx_documents_reporting_inbox_processed_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_documents_reporting_inbox_processed_at ON public.integration_inbox USING btree (processed_at);


--
-- Name: idx_generated_document_student; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_generated_document_student ON public.generated_documents USING btree (student_number, academic_period_code, generated_at DESC);


--
-- Name: idx_generated_document_work_queue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_generated_document_work_queue ON public.generated_documents USING btree (next_generation_attempt_at, requested_at, id) WHERE (((status)::text = ANY ((ARRAY['REQUESTED'::character varying, 'FAILED'::character varying])::text[])) AND (deleted_at IS NULL));


--
-- Name: idx_progression_projection_student_period; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_progression_projection_student_period ON public.progression_decision_projections USING btree (student_number, academic_period_code, decision_version DESC);


--
-- Name: idx_published_offer_export; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_published_offer_export ON public.published_offer_letter_projections USING btree (intake_id, programme_id, applicant_name, application_number) WHERE (current_publication AND (deleted_at IS NULL) AND ((offer_status)::text <> 'WITHDRAWN'::text));


--
-- Name: idx_result_projection_student_period; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_result_projection_student_period ON public.published_result_projections USING btree (student_number, academic_period_code, module_code) WHERE (current_version AND (deleted_at IS NULL));


--
-- Name: idx_uploaded_documents_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_uploaded_documents_owner ON public.uploaded_documents USING btree (owner_type, owner_id, uploaded_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_uploaded_documents_replacement; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_uploaded_documents_replacement ON public.uploaded_documents USING btree (replaces_document_id) WHERE (replaces_document_id IS NOT NULL);


--
-- Name: idx_uploaded_documents_uploader; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_uploaded_documents_uploader ON public.uploaded_documents USING btree (uploaded_by_user_id, uploaded_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_uploaded_documents_verification_queue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_uploaded_documents_verification_queue ON public.uploaded_documents USING btree (uploaded_at, id) WHERE (((verification_status)::text = 'PENDING'::text) AND (deleted_at IS NULL));


--
-- Name: uk_generated_offer_letter; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_generated_offer_letter ON public.generated_documents USING btree (offer_letter_projection_id) WHERE ((offer_letter_projection_id IS NOT NULL) AND (deleted_at IS NULL));


--
-- Name: uk_progression_projection_current_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_progression_projection_current_scope ON public.progression_decision_projections USING btree (student_id, academic_period_id) WHERE (current_version AND (deleted_at IS NULL));


--
-- Name: uk_published_offer_current; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_published_offer_current ON public.published_offer_letter_projections USING btree (offer_id) WHERE (current_publication AND (deleted_at IS NULL));


--
-- Name: uk_result_projection_current_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_result_projection_current_scope ON public.published_result_projections USING btree (student_id, academic_period_id, module_id) WHERE (current_version AND (deleted_at IS NULL));


--
-- Name: progression_decision_projections trg_progression_decision_projection_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_progression_decision_projection_immutable BEFORE DELETE OR UPDATE ON public.progression_decision_projections FOR EACH ROW EXECUTE FUNCTION public.prevent_reporting_source_snapshot_change();


--
-- Name: progression_decision_result_projections trg_progression_result_projection_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_progression_result_projection_immutable BEFORE DELETE OR UPDATE ON public.progression_decision_result_projections FOR EACH ROW EXECUTE FUNCTION public.prevent_progression_result_projection_change();


--
-- Name: published_result_projections trg_published_result_projection_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_published_result_projection_immutable BEFORE DELETE OR UPDATE ON public.published_result_projections FOR EACH ROW EXECUTE FUNCTION public.prevent_reporting_source_snapshot_change();


--
-- Name: uploaded_documents trg_uploaded_document_content_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_uploaded_document_content_immutable BEFORE DELETE OR UPDATE ON public.uploaded_documents FOR EACH ROW EXECUTE FUNCTION public.prevent_uploaded_document_content_change();


--
-- Name: generated_documents_aud generated_documents_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.generated_documents_aud
    ADD CONSTRAINT generated_documents_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: generated_documents generated_documents_offer_letter_projection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.generated_documents
    ADD CONSTRAINT generated_documents_offer_letter_projection_id_fkey FOREIGN KEY (offer_letter_projection_id) REFERENCES public.offer_letter_projections(id);


--
-- Name: generated_documents generated_documents_progression_decision_projection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.generated_documents
    ADD CONSTRAINT generated_documents_progression_decision_projection_id_fkey FOREIGN KEY (progression_decision_projection_id) REFERENCES public.progression_decision_projections(id);


--
-- Name: offer_letter_export_audits_aud offer_letter_export_audits_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_letter_export_audits_aud
    ADD CONSTRAINT offer_letter_export_audits_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: offer_letter_projections_aud offer_letter_projections_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_letter_projections_aud
    ADD CONSTRAINT offer_letter_projections_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: progression_decision_projections_aud progression_decision_projections_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.progression_decision_projections_aud
    ADD CONSTRAINT progression_decision_projections_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: progression_decision_result_projections progression_decision_result_p_progression_decision_project_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.progression_decision_result_projections
    ADD CONSTRAINT progression_decision_result_p_progression_decision_project_fkey FOREIGN KEY (progression_decision_projection_id) REFERENCES public.progression_decision_projections(id);


--
-- Name: progression_decision_result_projections progression_decision_result_p_published_result_projection__fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.progression_decision_result_projections
    ADD CONSTRAINT progression_decision_result_p_published_result_projection__fkey FOREIGN KEY (published_result_projection_id) REFERENCES public.published_result_projections(id);


--
-- Name: progression_decision_result_projections_aud progression_decision_result_projections_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.progression_decision_result_projections_aud
    ADD CONSTRAINT progression_decision_result_projections_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: published_offer_letter_projections_aud published_offer_letter_projections_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_offer_letter_projections_aud
    ADD CONSTRAINT published_offer_letter_projections_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: published_offer_letter_projections published_offer_letter_projections_generated_document_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_offer_letter_projections
    ADD CONSTRAINT published_offer_letter_projections_generated_document_id_fkey FOREIGN KEY (generated_document_id) REFERENCES public.generated_documents(id);


--
-- Name: published_result_projections_aud published_result_projections_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_result_projections_aud
    ADD CONSTRAINT published_result_projections_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: uploaded_documents_aud uploaded_documents_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.uploaded_documents_aud
    ADD CONSTRAINT uploaded_documents_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: uploaded_documents uploaded_documents_replaces_document_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.uploaded_documents
    ADD CONSTRAINT uploaded_documents_replaces_document_id_fkey FOREIGN KEY (replaces_document_id) REFERENCES public.uploaded_documents(id);


--
-- PostgreSQL database dump complete
--


