-- Author: Tinashe K
-- Clean admissions baseline generated from the verified rolling-admissions schema.

CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;

--
--



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
COMMENT ON SCHEMA public IS 'standard public schema';


--
-- Name: enforce_application_programme_choice_governance(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_application_programme_choice_governance() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    application_status varchar(30);
    maximum_choices integer;
    capture_snapshot_changed boolean;
BEGIN
    SELECT application.status, application.maximum_programme_choices
      INTO application_status, maximum_choices
      FROM applications application
     WHERE application.id = NEW.application_id
       AND application.deleted_at IS NULL;

    IF application_status IS NULL THEN
        RAISE EXCEPTION 'Application is unavailable for programme choice governance.';
    END IF;

    capture_snapshot_changed := TG_OP = 'INSERT' OR (
        OLD.application_id IS DISTINCT FROM NEW.application_id
        OR OLD.programme_id IS DISTINCT FROM NEW.programme_id
        OR OLD.programme_version_id IS DISTINCT FROM NEW.programme_version_id
        OR OLD.programme_code IS DISTINCT FROM NEW.programme_code
        OR OLD.programme_name IS DISTINCT FROM NEW.programme_name
        OR OLD.award_name IS DISTINCT FROM NEW.award_name
        OR OLD.owning_academic_unit_id IS DISTINCT FROM NEW.owning_academic_unit_id
        OR OLD.owning_academic_unit_name IS DISTINCT FROM NEW.owning_academic_unit_name
        OR OLD.programme_version_code IS DISTINCT FROM NEW.programme_version_code
        OR OLD.catalogue_snapshot_status IS DISTINCT FROM NEW.catalogue_snapshot_status
        OR OLD.choice_rank IS DISTINCT FROM NEW.choice_rank
    );

    IF capture_snapshot_changed AND application_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Programme choice capture snapshots can only change while the application is in DRAFT status.';
    END IF;
    IF NEW.choice_rank < 1 OR NEW.choice_rank > maximum_choices THEN
        RAISE EXCEPTION 'Programme choice rank % exceeds the configured intake maximum of %.', NEW.choice_rank, maximum_choices;
    END IF;

    IF TG_OP = 'UPDATE'
       AND OLD.choice_status IS DISTINCT FROM NEW.choice_status
       AND NOT (
           (OLD.choice_status = 'PENDING' AND NEW.choice_status IN ('ELIGIBLE', 'CONDITIONALLY_ELIGIBLE', 'INELIGIBLE', 'REQUIRES_REVIEW', 'REJECTED'))
           OR (OLD.choice_status = 'REQUIRES_REVIEW' AND NEW.choice_status IN ('ELIGIBLE', 'CONDITIONALLY_ELIGIBLE', 'INELIGIBLE', 'REJECTED'))
           OR (OLD.choice_status = 'INELIGIBLE' AND NEW.choice_status IN ('ELIGIBLE', 'CONDITIONALLY_ELIGIBLE', 'REQUIRES_REVIEW'))
           OR (OLD.choice_status IN ('ELIGIBLE', 'CONDITIONALLY_ELIGIBLE') AND NEW.choice_status IN ('INELIGIBLE', 'REQUIRES_REVIEW', 'UNDER_ACADEMIC_REVIEW', 'REJECTED'))
           OR (OLD.choice_status = 'UNDER_ACADEMIC_REVIEW' AND NEW.choice_status IN ('ADMITTED', 'REJECTED'))
           OR (OLD.choice_status = 'ADMITTED' AND NEW.choice_status = 'OFFERED')
           OR (OLD.choice_status = 'OFFERED' AND NEW.choice_status IN ('ADMITTED', 'CONVERTED', 'REJECTED'))
       ) THEN
        RAISE EXCEPTION 'Invalid programme choice transition from % to %.', OLD.choice_status, NEW.choice_status;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: prohibit_offer_response_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prohibit_offer_response_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'Offer responses are immutable';
END;
$$;


--
-- Name: protect_legacy_admissions_history(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_legacy_admissions_history() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION '% is read-only historical admissions data after ADR-0014', TG_TABLE_NAME;
END;
$$;


--
-- Name: protect_stored_offer_document_version(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_stored_offer_document_version() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.status = 'STORED' THEN
        RAISE EXCEPTION 'Stored offer document versions are immutable';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: validate_offer_batch_source(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_offer_batch_source() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    round_cycle_id uuid;
BEGIN
    SELECT admission_cycle_id INTO round_cycle_id
    FROM selection_rounds
    WHERE id = NEW.selection_round_id AND deleted_at IS NULL;
    IF round_cycle_id IS NULL OR round_cycle_id <> NEW.admission_cycle_id THEN
        RAISE EXCEPTION 'Offer batch and selection round must belong to the same admission cycle';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: validate_offer_batch_transition(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_offer_batch_transition() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.status = NEW.status THEN
        RETURN NEW;
    END IF;
    IF NOT (
        (OLD.status = 'DRAFT' AND NEW.status = 'APPROVED')
        OR (OLD.status = 'APPROVED' AND NEW.status = 'DISPATCHED')
        OR (OLD.status = 'DISPATCHED' AND NEW.status = 'CLOSED')
    ) THEN
        RAISE EXCEPTION 'Invalid offer batch transition from % to %', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: validate_offer_condition_transition(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_offer_condition_transition() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.status = NEW.status THEN
        RETURN NEW;
    END IF;
    IF OLD.status <> 'PENDING' OR NEW.status NOT IN ('SATISFIED', 'WAIVED') THEN
        RAISE EXCEPTION 'Invalid offer condition transition from % to %', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: validate_offer_response(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_offer_response() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    current_offer_status varchar(30);
    current_deadline timestamptz;
BEGIN
    SELECT status, acceptance_deadline INTO current_offer_status, current_deadline
    FROM offers WHERE id = NEW.offer_id AND deleted_at IS NULL FOR UPDATE;
    IF current_offer_status IS DISTINCT FROM 'SENT' THEN
        RAISE EXCEPTION 'Only a sent offer can receive an applicant response';
    END IF;
    IF NEW.responded_at > current_deadline THEN
        RAISE EXCEPTION 'The offer acceptance deadline has passed';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: validate_offer_source(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_offer_source() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    source_application_id uuid;
    source_programme_id uuid;
    source_programme_version_id uuid;
    source_intake_id uuid;
    source_is_approved boolean;
    response_exists boolean;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF OLD.application_id IS DISTINCT FROM NEW.application_id
           OR OLD.programme_choice_id IS DISTINCT FROM NEW.programme_choice_id
           OR OLD.offer_batch_id IS DISTINCT FROM NEW.offer_batch_id
           OR OLD.programme_choice_decision_id IS DISTINCT FROM NEW.programme_choice_decision_id
           OR OLD.programme_id IS DISTINCT FROM NEW.programme_id
           OR OLD.programme_version_id IS DISTINCT FROM NEW.programme_version_id
           OR OLD.programme_code IS DISTINCT FROM NEW.programme_code
           OR OLD.programme_name IS DISTINCT FROM NEW.programme_name
           OR OLD.intake_id IS DISTINCT FROM NEW.intake_id
           OR OLD.offer_number IS DISTINCT FROM NEW.offer_number THEN
            RAISE EXCEPTION 'An offer source snapshot is immutable after creation';
        END IF;

        SELECT EXISTS (
            SELECT 1 FROM offer_responses response
            WHERE response.offer_id = OLD.id AND response.deleted_at IS NULL
        ) INTO response_exists;
        IF response_exists AND (
            OLD.offer_type IS DISTINCT FROM NEW.offer_type
            OR OLD.conditions_text IS DISTINCT FROM NEW.conditions_text
            OR OLD.acceptance_deadline IS DISTINCT FROM NEW.acceptance_deadline
            OR OLD.registration_date IS DISTINCT FROM NEW.registration_date
            OR OLD.orientation_date IS DISTINCT FROM NEW.orientation_date
            OR OLD.commencement_date IS DISTINCT FROM NEW.commencement_date
            OR OLD.generated_document_id IS DISTINCT FROM NEW.generated_document_id
            OR OLD.current_document_version_id IS DISTINCT FROM NEW.current_document_version_id
            OR OLD.current_publication_id IS DISTINCT FROM NEW.current_publication_id
        ) THEN
            RAISE EXCEPTION 'An answered offer and its published document are immutable';
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.offer_batch_id IS NOT NULL OR NEW.programme_choice_decision_id IS NULL THEN
        RAISE EXCEPTION 'New offers require a direct admitted programme-choice decision and cannot use an offer batch';
    END IF;

    SELECT choice.application_id, choice.programme_id, choice.programme_version_id, application_record.intake_id
      INTO source_application_id, source_programme_id, source_programme_version_id, source_intake_id
      FROM application_programme_choices choice
      JOIN applications application_record ON application_record.id = choice.application_id
     WHERE choice.id = NEW.programme_choice_id
       AND choice.deleted_at IS NULL
       AND application_record.deleted_at IS NULL;

    IF source_application_id IS NULL
       OR source_application_id <> NEW.application_id
       OR source_programme_id <> NEW.programme_id
       OR source_programme_version_id <> NEW.programme_version_id
       OR source_intake_id <> NEW.intake_id THEN
        RAISE EXCEPTION 'Offer source does not match the application and programme snapshot';
    END IF;

    SELECT EXISTS (
        SELECT 1 FROM programme_choice_decisions decision
        WHERE decision.id = NEW.programme_choice_decision_id
          AND decision.application_id = NEW.application_id
          AND decision.programme_choice_id = NEW.programme_choice_id
          AND decision.decision = 'ADMIT'
          AND decision.deleted_at IS NULL
    ) INTO source_is_approved;
    IF NOT source_is_approved THEN
        RAISE EXCEPTION 'Direct offers require an admitted programme-choice decision';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: validate_offer_transition(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_offer_transition() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.status = NEW.status THEN
        RETURN NEW;
    END IF;
    IF NOT (
        (OLD.status = 'DRAFT' AND NEW.status IN ('APPROVED', 'WITHDRAWN'))
        OR (OLD.status = 'APPROVED' AND NEW.status IN ('SENT', 'WITHDRAWN'))
        OR (OLD.status = 'SENT' AND NEW.status IN ('ACCEPTED', 'DECLINED', 'EXPIRED', 'WITHDRAWN'))
        OR (OLD.status = 'ACCEPTED' AND NEW.status = 'CONVERTED')
    ) THEN
        RAISE EXCEPTION 'Invalid offer transition from % to %', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: validate_selection_decision(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_selection_decision() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    round_status varchar(30);
    round_cycle_id uuid;
    current_choice_status varchar(30);
    current_application_id uuid;
    current_application_cycle_id uuid;
    existing_application_selection_count bigint;
BEGIN
    SELECT status, admission_cycle_id INTO round_status, round_cycle_id
    FROM selection_rounds WHERE id = NEW.selection_round_id AND deleted_at IS NULL;
    IF round_status IS DISTINCT FROM 'OPEN' THEN
        RAISE EXCEPTION 'Selection decisions can only be recorded in an open selection round';
    END IF;

    SELECT choice.choice_status, choice.application_id, application_record.admission_cycle_id
    INTO current_choice_status, current_application_id, current_application_cycle_id
    FROM application_programme_choices choice
    JOIN applications application_record ON application_record.id = choice.application_id
    WHERE choice.id = NEW.programme_choice_id AND choice.deleted_at IS NULL;
    IF current_application_cycle_id IS NULL OR current_application_cycle_id <> round_cycle_id THEN
        RAISE EXCEPTION 'Programme choice is outside the selection round admission cycle';
    END IF;
    IF NEW.decision IN ('SHORTLIST', 'SELECT', 'WAITLIST')
       AND current_choice_status NOT IN ('ELIGIBLE', 'SHORTLISTED', 'WAITLISTED') THEN
        RAISE EXCEPTION 'Only eligible programme choices can be shortlisted, selected, or waitlisted';
    END IF;

    IF NEW.decision = 'SELECT' THEN
        SELECT count(*) INTO existing_application_selection_count
        FROM selection_decisions existing_decision
        JOIN application_programme_choices existing_choice
          ON existing_choice.id = existing_decision.programme_choice_id
        WHERE existing_choice.application_id = current_application_id
          AND existing_decision.decision = 'SELECT'
          AND existing_decision.deleted_at IS NULL
          AND existing_decision.id <> NEW.id;
        IF existing_application_selection_count > 0 THEN
            RAISE EXCEPTION 'An application can only have one selected programme choice';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: validate_selection_round_transition(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_selection_round_transition() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.status = NEW.status THEN
        RETURN NEW;
    END IF;
    IF NOT (
        (OLD.status = 'DRAFT' AND NEW.status = 'OPEN')
        OR (OLD.status = 'OPEN' AND NEW.status IN ('APPROVED', 'CLOSED'))
        OR (OLD.status = 'APPROVED' AND NEW.status = 'CLOSED')
    ) THEN
        RAISE EXCEPTION 'Invalid selection round transition from % to %', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: academic_recommendations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_recommendations (
    id uuid NOT NULL,
    academic_review_id uuid NOT NULL,
    recommendation_sequence integer NOT NULL,
    recommendation character varying(30) NOT NULL,
    reason character varying(1000) NOT NULL,
    recommended_by_user_id uuid NOT NULL,
    recommended_at timestamp with time zone NOT NULL,
    review_status character varying(30) NOT NULL,
    reviewed_by_user_id uuid,
    reviewed_at timestamp with time zone,
    review_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_academic_recommendation CHECK (((recommendation)::text = ANY ((ARRAY['RECOMMEND_ADMIT'::character varying, 'RECOMMEND_REJECT'::character varying])::text[]))),
    CONSTRAINT ck_academic_recommendation_review CHECK (((((review_status)::text = 'PENDING'::text) AND (reviewed_by_user_id IS NULL) AND (reviewed_at IS NULL)) OR (((review_status)::text <> 'PENDING'::text) AND (reviewed_by_user_id IS NOT NULL) AND (reviewed_at IS NOT NULL) AND (length(TRIM(BOTH FROM COALESCE(review_reason, ''::character varying))) > 0)))),
    CONSTRAINT ck_academic_recommendation_review_status CHECK (((review_status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'RETURNED'::character varying, 'OVERRIDDEN'::character varying])::text[]))),
    CONSTRAINT ck_academic_recommendation_values CHECK (((recommendation_sequence > 0) AND (length(TRIM(BOTH FROM reason)) > 0)))
);


--
-- Name: academic_recommendations_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_recommendations_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    academic_review_id uuid,
    recommendation_sequence integer,
    recommendation character varying(30),
    reason character varying(1000),
    recommended_by_user_id uuid,
    recommended_at timestamp with time zone,
    review_status character varying(30),
    reviewed_by_user_id uuid,
    reviewed_at timestamp with time zone,
    review_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: academic_review_assignments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_review_assignments (
    id uuid NOT NULL,
    selection_round_id uuid NOT NULL,
    application_id uuid NOT NULL,
    programme_choice_id uuid NOT NULL,
    owning_academic_unit_id uuid NOT NULL,
    owning_academic_unit_code character varying(50) NOT NULL,
    owning_academic_unit_name character varying(180) NOT NULL,
    recommendation_academic_unit_id uuid CONSTRAINT academic_review_assignments_recommendation_academic_un_not_null NOT NULL,
    recommendation_academic_unit_code character varying(50) CONSTRAINT academic_review_assignment_recommendation_academic_un_not_null1 NOT NULL,
    recommendation_academic_unit_name character varying(180) CONSTRAINT academic_review_assignment_recommendation_academic_un_not_null2 NOT NULL,
    hierarchy_path_json jsonb NOT NULL,
    choice_rank integer NOT NULL,
    status character varying(30) NOT NULL,
    release_attempt integer NOT NULL,
    released_by_user_id uuid NOT NULL,
    released_at timestamp with time zone NOT NULL,
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
    CONSTRAINT ck_academic_review_assignment_claim CHECK (((((status)::text = 'OPEN'::text) AND (claimed_by_user_id IS NULL) AND (claimed_at IS NULL)) OR ((status)::text = ANY ((ARRAY['CLAIMED'::character varying, 'RECOMMENDED'::character varying, 'RETURNED'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[])))),
    CONSTRAINT ck_academic_review_assignment_status CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'CLAIMED'::character varying, 'RECOMMENDED'::character varying, 'RETURNED'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT ck_academic_review_assignment_values CHECK (((choice_rank > 0) AND (release_attempt > 0)))
);


--
-- Name: TABLE academic_review_assignments; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.academic_review_assignments IS 'Historical — read-only from ADR-0014 onward. No new rows. Superseded by academic_reviews.';


--
-- Name: academic_review_assignments_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_review_assignments_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    selection_round_id uuid,
    application_id uuid,
    programme_choice_id uuid,
    owning_academic_unit_id uuid,
    owning_academic_unit_code character varying(50),
    owning_academic_unit_name character varying(180),
    recommendation_academic_unit_id uuid,
    recommendation_academic_unit_code character varying(50),
    recommendation_academic_unit_name character varying(180),
    hierarchy_path_json jsonb,
    choice_rank integer,
    status character varying(30),
    release_attempt integer,
    released_by_user_id uuid,
    released_at timestamp with time zone,
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
-- Name: academic_reviews; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_reviews (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    programme_choice_id uuid NOT NULL,
    owning_academic_unit_id uuid NOT NULL,
    owning_academic_unit_code character varying(50) NOT NULL,
    owning_academic_unit_name character varying(180) NOT NULL,
    recommendation_academic_unit_id uuid NOT NULL,
    recommendation_academic_unit_code character varying(50) NOT NULL,
    recommendation_academic_unit_name character varying(180) NOT NULL,
    hierarchy_path_json jsonb NOT NULL,
    choice_rank integer NOT NULL,
    status character varying(30) NOT NULL,
    claimed_by_user_id uuid,
    claimed_at timestamp with time zone,
    completed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_academic_review_choice_rank CHECK ((choice_rank > 0)),
    CONSTRAINT ck_academic_review_claim CHECK (((((status)::text = 'OPEN'::text) AND (claimed_by_user_id IS NULL) AND (claimed_at IS NULL)) OR ((status)::text = ANY ((ARRAY['CLAIMED'::character varying, 'RECOMMENDED'::character varying, 'RETURNED'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[])))),
    CONSTRAINT ck_academic_review_status CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'CLAIMED'::character varying, 'RECOMMENDED'::character varying, 'RETURNED'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: academic_reviews_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_reviews_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_id uuid,
    programme_choice_id uuid,
    owning_academic_unit_id uuid,
    owning_academic_unit_code character varying(50),
    owning_academic_unit_name character varying(180),
    recommendation_academic_unit_id uuid,
    recommendation_academic_unit_code character varying(50),
    recommendation_academic_unit_name character varying(180),
    hierarchy_path_json jsonb,
    choice_rank integer,
    status character varying(30),
    claimed_by_user_id uuid,
    claimed_at timestamp with time zone,
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
-- Name: academic_unit_recommendations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_unit_recommendations (
    id uuid NOT NULL,
    academic_review_assignment_id uuid CONSTRAINT academic_unit_recommendatio_academic_review_assignment_not_null NOT NULL,
    recommendation_sequence integer NOT NULL,
    recommendation character varying(30) NOT NULL,
    rank_position integer,
    quota_type_code character varying(50),
    reason character varying(1000) NOT NULL,
    recommended_by_user_id uuid NOT NULL,
    recommended_at timestamp with time zone NOT NULL,
    review_status character varying(30) NOT NULL,
    reviewed_by_user_id uuid,
    reviewed_at timestamp with time zone,
    review_reason character varying(1000),
    final_decision character varying(30),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_academic_unit_recommendation CHECK (((recommendation)::text = ANY ((ARRAY['SHORTLIST'::character varying, 'SELECT'::character varying, 'REJECT'::character varying, 'WAITLIST'::character varying])::text[]))),
    CONSTRAINT ck_academic_unit_recommendation_final_decision CHECK (((final_decision IS NULL) OR ((final_decision)::text = ANY ((ARRAY['SHORTLIST'::character varying, 'SELECT'::character varying, 'REJECT'::character varying, 'WAITLIST'::character varying])::text[])))),
    CONSTRAINT ck_academic_unit_recommendation_review CHECK (((((review_status)::text = 'PENDING'::text) AND (reviewed_by_user_id IS NULL) AND (reviewed_at IS NULL) AND (final_decision IS NULL)) OR (((review_status)::text <> 'PENDING'::text) AND (reviewed_by_user_id IS NOT NULL) AND (reviewed_at IS NOT NULL) AND (length(TRIM(BOTH FROM COALESCE(review_reason, ''::character varying))) > 0)))),
    CONSTRAINT ck_academic_unit_recommendation_review_status CHECK (((review_status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'RETURNED'::character varying, 'OVERRIDDEN'::character varying])::text[]))),
    CONSTRAINT ck_academic_unit_recommendation_values CHECK (((recommendation_sequence > 0) AND ((rank_position IS NULL) OR (rank_position > 0)) AND (length(TRIM(BOTH FROM reason)) > 0)))
);


--
-- Name: TABLE academic_unit_recommendations; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.academic_unit_recommendations IS 'Historical — read-only from ADR-0014 onward. No new rows. Superseded by academic_recommendations.';


--
-- Name: academic_unit_recommendations_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_unit_recommendations_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    academic_review_assignment_id uuid,
    recommendation_sequence integer,
    recommendation character varying(30),
    rank_position integer,
    quota_type_code character varying(50),
    reason character varying(1000),
    recommended_by_user_id uuid,
    recommended_at timestamp with time zone,
    review_status character varying(30),
    reviewed_by_user_id uuid,
    reviewed_at timestamp with time zone,
    review_reason character varying(1000),
    final_decision character varying(30),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: admission_cycle_archive_summaries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admission_cycle_archive_summaries (
    id uuid NOT NULL,
    admission_cycle_id uuid NOT NULL,
    total_applications integer NOT NULL,
    submitted_applications integer CONSTRAINT admission_cycle_archive_summari_submitted_applications_not_null NOT NULL,
    eligible_applications integer CONSTRAINT admission_cycle_archive_summarie_eligible_applications_not_null NOT NULL,
    selected_applications integer CONSTRAINT admission_cycle_archive_summarie_selected_applications_not_null NOT NULL,
    offered_applications integer NOT NULL,
    accepted_applications integer CONSTRAINT admission_cycle_archive_summarie_accepted_applications_not_null NOT NULL,
    converted_applications integer CONSTRAINT admission_cycle_archive_summari_converted_applications_not_null NOT NULL,
    archived_by_user_id uuid NOT NULL,
    archived_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: admission_cycle_archive_summaries_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admission_cycle_archive_summaries_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    admission_cycle_id uuid,
    total_applications integer,
    submitted_applications integer,
    eligible_applications integer,
    selected_applications integer,
    offered_applications integer,
    accepted_applications integer,
    converted_applications integer,
    archived_by_user_id uuid,
    archived_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: admission_cycles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admission_cycles (
    id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    intake_id uuid NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(200) NOT NULL,
    opens_at timestamp with time zone NOT NULL,
    closes_at timestamp with time zone NOT NULL,
    status character varying(30) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    maximum_programme_choices integer DEFAULT 3 NOT NULL,
    application_type_id uuid,
    change_reason character varying(1000) DEFAULT 'Initial record creation.'::character varying NOT NULL,
    CONSTRAINT ck_admission_cycles_maximum_programme_choices CHECK (((maximum_programme_choices >= 1) AND (maximum_programme_choices <= 20)))
);


--
-- Name: TABLE admission_cycles; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.admission_cycles IS 'Internal one-to-one compatibility projection of Academic Setup intakes. Not an administrator-managed admissions window.';


--
-- Name: admission_cycles_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admission_cycles_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    academic_year_id uuid,
    intake_id uuid,
    code character varying(50),
    name character varying(200),
    opens_at timestamp with time zone,
    closes_at timestamp with time zone,
    status character varying(30),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    maximum_programme_choices integer,
    application_type_id uuid,
    change_reason character varying(1000)
);


--
-- Name: admission_qualification_requirement_groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admission_qualification_requirement_groups (
    id uuid NOT NULL,
    requirement_set_id uuid CONSTRAINT admission_qualification_requirement_requirement_set_id_not_null NOT NULL,
    group_code character varying(50) NOT NULL,
    name character varying(160) NOT NULL,
    minimum_satisfied_items integer CONSTRAINT admission_qualification_requir_minimum_satisfied_items_not_null NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_qualification_requirement_group_minimum CHECK ((minimum_satisfied_items > 0))
);


--
-- Name: admission_qualification_requirement_groups_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admission_qualification_requirement_groups_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    requirement_set_id uuid,
    group_code character varying(50),
    name character varying(160),
    minimum_satisfied_items integer,
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
-- Name: admission_qualification_requirement_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admission_qualification_requirement_items (
    id uuid NOT NULL,
    requirement_group_id uuid CONSTRAINT admission_qualification_requireme_requirement_group_id_not_null NOT NULL,
    qualification_level character varying(30) CONSTRAINT admission_qualification_requiremen_qualification_level_not_null NOT NULL,
    minimum_count integer CONSTRAINT admission_qualification_requirement_item_minimum_count_not_null NOT NULL,
    minimum_total_points numeric(8,2),
    minimum_duration_months integer,
    sort_order integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_qualification_requirement_item_duration CHECK (((minimum_duration_months IS NULL) OR (minimum_duration_months >= 0))),
    CONSTRAINT ck_qualification_requirement_item_level CHECK (((qualification_level)::text = ANY ((ARRAY['O_LEVEL'::character varying, 'A_LEVEL'::character varying, 'DIPLOMA'::character varying, 'DEGREE'::character varying, 'PROFESSIONAL'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT ck_qualification_requirement_item_minimum_count CHECK ((minimum_count > 0))
);


--
-- Name: admission_qualification_requirement_items_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admission_qualification_requirement_items_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    requirement_group_id uuid,
    qualification_level character varying(30),
    minimum_count integer,
    minimum_total_points numeric(8,2),
    minimum_duration_months integer,
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
-- Name: admission_quotas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admission_quotas (
    id uuid NOT NULL,
    intake_id uuid NOT NULL,
    programme_id uuid NOT NULL,
    programme_code character varying(50) NOT NULL,
    programme_name character varying(200) NOT NULL,
    quota_type_code character varying(50) NOT NULL,
    capacity integer NOT NULL,
    reserved_capacity integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_admission_quota_capacity CHECK ((capacity > 0)),
    CONSTRAINT ck_admission_quota_reserved_capacity CHECK (((reserved_capacity >= 0) AND (reserved_capacity <= capacity)))
);


--
-- Name: admission_quotas_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admission_quotas_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    intake_id uuid,
    programme_id uuid,
    programme_code character varying(50),
    programme_name character varying(200),
    quota_type_code character varying(50),
    capacity integer,
    reserved_capacity integer,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: admission_requirement_sets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admission_requirement_sets (
    id uuid NOT NULL,
    programme_id uuid NOT NULL,
    application_type_id uuid NOT NULL,
    admission_cycle_id uuid,
    version_code character varying(50) NOT NULL,
    effective_from date NOT NULL,
    effective_to date,
    status character varying(30) NOT NULL,
    minimum_total_points numeric(8,2),
    male_cutoff_points numeric(8,2),
    female_cutoff_points numeric(8,2),
    requires_english boolean NOT NULL,
    advanced_rules_json jsonb,
    advanced_rules_version character varying(30),
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    requires_mathematics_or_science boolean DEFAULT false CONSTRAINT admission_requirement_sets_requires_mathematics_or_sci_not_null NOT NULL,
    intake_id uuid,
    CONSTRAINT ck_requirement_sets_advanced_rule_version CHECK (((advanced_rules_json IS NULL) = (advanced_rules_version IS NULL))),
    CONSTRAINT ck_requirement_sets_approval CHECK (((((status)::text = ANY ((ARRAY['APPROVED'::character varying, 'RETIRED'::character varying])::text[])) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL)) OR ((status)::text = 'DRAFT'::text))),
    CONSTRAINT ck_requirement_sets_effective_period CHECK (((effective_to IS NULL) OR (effective_to >= effective_from))),
    CONSTRAINT ck_requirement_sets_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'APPROVED'::character varying, 'RETIRED'::character varying])::text[])))
);


--
-- Name: COLUMN admission_requirement_sets.admission_cycle_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.admission_requirement_sets.admission_cycle_id IS 'Nullable historical compatibility link only. New requirement sets use intake_id.';


--
-- Name: admission_requirement_sets_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admission_requirement_sets_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    programme_id uuid,
    application_type_id uuid,
    admission_cycle_id uuid,
    version_code character varying(50),
    effective_from date,
    effective_to date,
    status character varying(30),
    minimum_total_points numeric(8,2),
    male_cutoff_points numeric(8,2),
    female_cutoff_points numeric(8,2),
    requires_english boolean,
    advanced_rules_json jsonb,
    advanced_rules_version character varying(30),
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    requires_mathematics_or_science boolean,
    intake_id uuid
);


--
-- Name: admission_subject_requirements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admission_subject_requirements (
    id uuid NOT NULL,
    requirement_set_id uuid NOT NULL,
    level character varying(30) NOT NULL,
    subject_id uuid,
    subject_group_code character varying(50),
    requirement_type character varying(30) NOT NULL,
    minimum_grade character varying(20),
    minimum_points numeric(8,2),
    minimum_count integer,
    weight numeric(8,2),
    sort_order integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: admission_subject_requirements_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admission_subject_requirements_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    requirement_set_id uuid,
    level character varying(30),
    subject_id uuid,
    subject_group_code character varying(50),
    requirement_type character varying(30),
    minimum_grade character varying(20),
    minimum_points numeric(8,2),
    minimum_count integer,
    weight numeric(8,2),
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
-- Name: admission_subjects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admission_subjects (
    id uuid NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(150) NOT NULL,
    level character varying(30) NOT NULL,
    subject_group_code character varying(50),
    is_active boolean NOT NULL,
    legacy_olevel_subject_code character varying(50),
    legacy_subject_code character varying(50),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    is_science_subject boolean DEFAULT false NOT NULL
);


--
-- Name: admission_subjects_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admission_subjects_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code character varying(50),
    name character varying(150),
    level character varying(30),
    subject_group_code character varying(50),
    is_active boolean,
    legacy_olevel_subject_code character varying(50),
    legacy_subject_code character varying(50),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    is_science_subject boolean
);


--
-- Name: applicant_employment_histories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.applicant_employment_histories (
    id uuid NOT NULL,
    applicant_id uuid NOT NULL,
    employer_name character varying(200) NOT NULL,
    position_title character varying(150) NOT NULL,
    started_on date NOT NULL,
    ended_on date,
    is_current boolean NOT NULL,
    responsibilities character varying(2000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_applicant_current_employment_end CHECK (((NOT is_current) OR (ended_on IS NULL))),
    CONSTRAINT ck_applicant_employment_dates CHECK (((ended_on IS NULL) OR (ended_on >= started_on)))
);


--
-- Name: applicant_employment_histories_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.applicant_employment_histories_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    applicant_id uuid,
    employer_name character varying(200),
    position_title character varying(150),
    started_on date,
    ended_on date,
    is_current boolean,
    responsibilities character varying(2000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: applicant_next_of_kin; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.applicant_next_of_kin (
    id uuid NOT NULL,
    applicant_id uuid NOT NULL,
    full_name character varying(200) NOT NULL,
    relationship_code character varying(50) NOT NULL,
    phone_number character varying(50) NOT NULL,
    email character varying(200),
    address character varying(500),
    is_primary boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: applicant_next_of_kin_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.applicant_next_of_kin_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    applicant_id uuid,
    full_name character varying(200),
    relationship_code character varying(50),
    phone_number character varying(50),
    email character varying(200),
    address character varying(500),
    is_primary boolean,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: applicant_number_sequence; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.applicant_number_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: applicant_qualification_results; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.applicant_qualification_results (
    id uuid NOT NULL,
    qualification_sitting_id uuid CONSTRAINT applicant_qualification_resul_qualification_sitting_id_not_null NOT NULL,
    subject_id uuid,
    subject_name_snapshot character varying(150) NOT NULL,
    grade character varying(20) NOT NULL,
    mark numeric(8,2),
    points numeric(8,2),
    is_principal_subject boolean,
    result_status character varying(30) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: applicant_qualification_results_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.applicant_qualification_results_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    qualification_sitting_id uuid,
    subject_id uuid,
    subject_name_snapshot character varying(150),
    grade character varying(20),
    mark numeric(8,2),
    points numeric(8,2),
    is_principal_subject boolean,
    result_status character varying(30),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: applicant_qualification_sittings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.applicant_qualification_sittings (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    level character varying(30) NOT NULL,
    exam_body_id uuid,
    institution_name character varying(200),
    centre_number character varying(50),
    candidate_number character varying(50),
    year_written integer,
    country_id uuid,
    document_id uuid,
    legacy_source_table character varying(100),
    legacy_source_id bigint,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    verification_status character varying(30) DEFAULT 'CAPTURED'::character varying NOT NULL,
    verified_by_user_id uuid,
    verified_at timestamp with time zone,
    rejection_reason character varying(1000),
    CONSTRAINT ck_qualification_sitting_verification_status CHECK (((verification_status)::text = ANY ((ARRAY['CAPTURED'::character varying, 'VERIFIED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: applicant_qualification_sittings_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.applicant_qualification_sittings_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_id uuid,
    level character varying(30),
    exam_body_id uuid,
    institution_name character varying(200),
    centre_number character varying(50),
    candidate_number character varying(50),
    year_written integer,
    country_id uuid,
    document_id uuid,
    legacy_source_table character varying(100),
    legacy_source_id bigint,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    verification_status character varying(30),
    verified_by_user_id uuid,
    verified_at timestamp with time zone,
    rejection_reason character varying(1000)
);


--
-- Name: applicant_referee_invitations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.applicant_referee_invitations (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    referee_id uuid NOT NULL,
    token_hash character varying(64) NOT NULL,
    token_hint character varying(12) NOT NULL,
    status character varying(20) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    sent_at timestamp with time zone NOT NULL,
    opened_at timestamp with time zone,
    submitted_at timestamp with time zone,
    send_count integer DEFAULT 1 NOT NULL,
    relationship_to_applicant character varying(200),
    years_known integer,
    recommendation character varying(40),
    comments character varying(5000),
    declaration_accepted boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    nomination_id uuid,
    CONSTRAINT ck_referee_invitation_recommendation CHECK (((recommendation IS NULL) OR ((recommendation)::text = ANY ((ARRAY['STRONGLY_RECOMMEND'::character varying, 'RECOMMEND'::character varying, 'RECOMMEND_WITH_RESERVATIONS'::character varying, 'DO_NOT_RECOMMEND'::character varying])::text[])))),
    CONSTRAINT ck_referee_invitation_response CHECK (((((status)::text = 'SUBMITTED'::text) AND (submitted_at IS NOT NULL) AND (relationship_to_applicant IS NOT NULL) AND (years_known IS NOT NULL) AND (recommendation IS NOT NULL) AND (comments IS NOT NULL) AND declaration_accepted) OR ((status)::text <> 'SUBMITTED'::text))),
    CONSTRAINT ck_referee_invitation_send_count CHECK ((send_count > 0)),
    CONSTRAINT ck_referee_invitation_status CHECK (((status)::text = ANY ((ARRAY['SENT'::character varying, 'OPENED'::character varying, 'SUBMITTED'::character varying, 'REVOKED'::character varying, 'EXPIRED'::character varying])::text[]))),
    CONSTRAINT ck_referee_invitation_years_known CHECK (((years_known IS NULL) OR ((years_known >= 0) AND (years_known <= 100))))
);


--
-- Name: applicant_referee_invitations_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.applicant_referee_invitations_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_id uuid,
    referee_id uuid,
    token_hash character varying(64),
    token_hint character varying(12),
    status character varying(20),
    expires_at timestamp with time zone,
    sent_at timestamp with time zone,
    opened_at timestamp with time zone,
    submitted_at timestamp with time zone,
    send_count integer,
    relationship_to_applicant character varying(200),
    years_known integer,
    recommendation character varying(40),
    comments character varying(5000),
    declaration_accepted boolean,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    nomination_id uuid
);


--
-- Name: applicant_referees; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.applicant_referees (
    id uuid NOT NULL,
    applicant_id uuid NOT NULL,
    full_name character varying(200) NOT NULL,
    title character varying(100),
    organisation character varying(200) NOT NULL,
    position_title character varying(150),
    email character varying(200) NOT NULL,
    phone_number character varying(50),
    verification_status character varying(30) NOT NULL,
    reference_document_id uuid,
    verified_by_user_id uuid,
    verified_at timestamp with time zone,
    rejection_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_applicant_referee_status CHECK (((verification_status)::text = ANY ((ARRAY['PENDING'::character varying, 'VERIFIED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: applicant_referees_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.applicant_referees_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    applicant_id uuid,
    full_name character varying(200),
    title character varying(100),
    organisation character varying(200),
    position_title character varying(150),
    email character varying(200),
    phone_number character varying(50),
    verification_status character varying(30),
    reference_document_id uuid,
    verified_by_user_id uuid,
    verified_at timestamp with time zone,
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
-- Name: applicants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.applicants (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    applicant_number character varying(40) NOT NULL,
    applicant_category_code character varying(30) NOT NULL,
    title_code character varying(30),
    first_name character varying(100) NOT NULL,
    middle_names character varying(150),
    last_name character varying(100) NOT NULL,
    date_of_birth date,
    gender_code character varying(30),
    marital_status_code character varying(30),
    national_id_number character varying(50),
    passport_number character varying(50),
    country_id uuid,
    nationality_country_id uuid,
    place_of_birth character varying(150),
    disability_status_code character varying(30),
    special_needs character varying(1000),
    sponsor_type_code character varying(30),
    primary_email character varying(200) NOT NULL,
    primary_phone character varying(50),
    postal_address character varying(500),
    residential_address character varying(500),
    legacy_applicants_detail_id bigint,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: applicants_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.applicants_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    user_id uuid,
    applicant_number character varying(40),
    applicant_category_code character varying(30),
    title_code character varying(30),
    first_name character varying(100),
    middle_names character varying(150),
    last_name character varying(100),
    date_of_birth date,
    gender_code character varying(30),
    marital_status_code character varying(30),
    national_id_number character varying(50),
    passport_number character varying(50),
    country_id uuid,
    nationality_country_id uuid,
    place_of_birth character varying(150),
    disability_status_code character varying(30),
    special_needs character varying(1000),
    sponsor_type_code character varying(30),
    primary_email character varying(200),
    primary_phone character varying(50),
    postal_address character varying(500),
    residential_address character varying(500),
    legacy_applicants_detail_id bigint,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: application_accommodation_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_accommodation_requests (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    status character varying(30) NOT NULL,
    preferred_campus_code character varying(50),
    special_requirements character varying(1000),
    notes character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: application_accommodation_requests_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_accommodation_requests_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_id uuid,
    status character varying(30),
    preferred_campus_code character varying(50),
    special_requirements character varying(1000),
    notes character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: application_clearances; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_clearances (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    outcome character varying(30) NOT NULL,
    payment_cleared boolean NOT NULL,
    sections_complete boolean NOT NULL,
    required_documents_verified boolean NOT NULL,
    qualifications_verified boolean NOT NULL,
    confirmed_by_user_id uuid NOT NULL,
    confirmed_at timestamp with time zone NOT NULL,
    reason character varying(1000) NOT NULL,
    invalidated_by_user_id uuid,
    invalidated_at timestamp with time zone,
    invalidation_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    duplicate_checks_passed boolean NOT NULL,
    duplicate_check_summary character varying(1000) NOT NULL,
    CONSTRAINT ck_application_clearance_evidence CHECK ((((outcome)::text <> 'CONFIRMED'::text) OR (payment_cleared AND sections_complete AND required_documents_verified AND qualifications_verified AND duplicate_checks_passed AND (length(TRIM(BOTH FROM duplicate_check_summary)) > 0)))),
    CONSTRAINT ck_application_clearance_invalidation CHECK (((((outcome)::text = 'CONFIRMED'::text) AND (invalidated_at IS NULL) AND (invalidated_by_user_id IS NULL)) OR (((outcome)::text = 'INVALIDATED'::text) AND (invalidated_at IS NOT NULL) AND (invalidated_by_user_id IS NOT NULL) AND (length(TRIM(BOTH FROM COALESCE(invalidation_reason, ''::character varying))) > 0)))),
    CONSTRAINT ck_application_clearance_outcome CHECK (((outcome)::text = ANY ((ARRAY['CONFIRMED'::character varying, 'INVALIDATED'::character varying])::text[]))),
    CONSTRAINT ck_application_clearance_reason CHECK ((length(TRIM(BOTH FROM reason)) > 0))
);


--
-- Name: application_clearances_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_clearances_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_id uuid,
    outcome character varying(30),
    payment_cleared boolean,
    sections_complete boolean,
    required_documents_verified boolean,
    qualifications_verified boolean,
    confirmed_by_user_id uuid,
    confirmed_at timestamp with time zone,
    reason character varying(1000),
    invalidated_by_user_id uuid,
    invalidated_at timestamp with time zone,
    invalidation_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    duplicate_checks_passed boolean,
    duplicate_check_summary character varying(1000)
);


--
-- Name: application_document_requirement_snapshots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_document_requirement_snapshots (
    id uuid NOT NULL,
    application_id uuid CONSTRAINT application_document_requirement_snapsh_application_id_not_null NOT NULL,
    requirement_code character varying(60) CONSTRAINT application_document_requirement_snap_requirement_code_not_null NOT NULL,
    requirement_name character varying(160) CONSTRAINT application_document_requirement_snap_requirement_name_not_null NOT NULL,
    is_required boolean NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: application_document_requirement_snapshots_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_document_requirement_snapshots_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_id uuid,
    requirement_code character varying(60),
    requirement_name character varying(160),
    is_required boolean,
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
-- Name: application_documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_documents (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    document_id uuid NOT NULL,
    requirement_code character varying(80) NOT NULL,
    is_required boolean NOT NULL,
    status character varying(30) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    document_file_name character varying(255),
    document_mime_type character varying(100),
    document_checksum_sha256 character varying(64),
    linked_at timestamp with time zone DEFAULT now() NOT NULL,
    is_current boolean DEFAULT true NOT NULL,
    supersedes_application_document_id uuid,
    verified_by_user_id uuid,
    verified_at timestamp with time zone,
    rejection_reason character varying(1000),
    last_verification_event_id uuid,
    last_document_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_application_documents_metadata CHECK ((((document_file_name IS NULL) AND (document_mime_type IS NULL) AND (document_checksum_sha256 IS NULL)) OR ((document_file_name IS NOT NULL) AND (document_mime_type IS NOT NULL) AND (document_checksum_sha256 IS NOT NULL) AND (length((document_checksum_sha256)::text) = 64)))),
    CONSTRAINT ck_application_documents_projection_version CHECK ((last_document_version >= 0)),
    CONSTRAINT ck_application_documents_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'VERIFIED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT ck_application_documents_supersession CHECK (((supersedes_application_document_id IS NULL) OR (supersedes_application_document_id <> id))),
    CONSTRAINT ck_application_documents_verification_evidence CHECK (((((status)::text = 'PENDING'::text) AND (verified_by_user_id IS NULL) AND (verified_at IS NULL) AND (rejection_reason IS NULL)) OR (((status)::text = 'VERIFIED'::text) AND (verified_by_user_id IS NOT NULL) AND (verified_at IS NOT NULL) AND (rejection_reason IS NULL)) OR (((status)::text = 'REJECTED'::text) AND (verified_by_user_id IS NOT NULL) AND (verified_at IS NOT NULL) AND (rejection_reason IS NOT NULL) AND (length(TRIM(BOTH FROM rejection_reason)) >= 10))))
);


--
-- Name: application_documents_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_documents_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_id uuid,
    document_id uuid,
    requirement_code character varying(80),
    is_required boolean,
    status character varying(30),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    document_file_name character varying(255),
    document_mime_type character varying(100),
    document_checksum_sha256 character varying(64),
    linked_at timestamp with time zone,
    is_current boolean,
    supersedes_application_document_id uuid,
    verified_by_user_id uuid,
    verified_at timestamp with time zone,
    rejection_reason character varying(1000),
    last_verification_event_id uuid,
    last_document_version bigint
);


--
-- Name: application_evaluations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_evaluations (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    programme_choice_id uuid NOT NULL,
    requirement_set_id uuid NOT NULL,
    status character varying(40) NOT NULL,
    total_points numeric(8,2),
    rank_score numeric(10,4),
    missing_requirements_json jsonb,
    rule_results_json jsonb,
    evaluated_at timestamp with time zone NOT NULL,
    evaluated_by_user_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: application_evaluations_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_evaluations_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_id uuid,
    programme_choice_id uuid,
    requirement_set_id uuid,
    status character varying(40),
    total_points numeric(8,2),
    rank_score numeric(10,4),
    missing_requirements_json jsonb,
    rule_results_json jsonb,
    evaluated_at timestamp with time zone,
    evaluated_by_user_id uuid,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: application_exam_arrangements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_exam_arrangements (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    programme_choice_id uuid,
    exam_type_code character varying(50) NOT NULL,
    status character varying(30) NOT NULL,
    scheduled_at timestamp with time zone,
    exam_session_id uuid,
    score numeric(8,2),
    outcome_code character varying(50),
    notes character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: application_exam_arrangements_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_exam_arrangements_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_id uuid,
    programme_choice_id uuid,
    exam_type_code character varying(50),
    status character varying(30),
    scheduled_at timestamp with time zone,
    exam_session_id uuid,
    score numeric(8,2),
    outcome_code character varying(50),
    notes character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: application_fees; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_fees (
    id uuid NOT NULL,
    application_type_id uuid NOT NULL,
    applicant_category_code character varying(30) NOT NULL,
    currency_code character varying(3) NOT NULL,
    amount numeric(12,2) NOT NULL,
    effective_from date NOT NULL,
    effective_to date,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_application_fees_amount_non_negative CHECK ((amount >= (0)::numeric)),
    CONSTRAINT ck_application_fees_applicant_category CHECK (((applicant_category_code)::text = ANY ((ARRAY['LOCAL'::character varying, 'SADC'::character varying, 'INTERNATIONAL'::character varying, 'CLE'::character varying])::text[]))),
    CONSTRAINT ck_application_fees_currency_code CHECK (((currency_code)::text ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT ck_application_fees_effective_period CHECK (((effective_to IS NULL) OR (effective_to >= effective_from)))
);


--
-- Name: application_fees_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_fees_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_type_id uuid,
    applicant_category_code character varying(30),
    currency_code character varying(3),
    amount numeric(12,2),
    effective_from date,
    effective_to date,
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
-- Name: application_number_sequence; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.application_number_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: application_payment_references; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_payment_references (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    reference character varying(80) NOT NULL,
    amount_due numeric(12,2) NOT NULL,
    currency_code character varying(3) NOT NULL,
    base_currency_code character varying(3) DEFAULT 'USD'::character varying NOT NULL,
    exchange_rate_id uuid,
    base_amount_due numeric(12,2),
    status character varying(30) NOT NULL,
    required_for_submission boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    finance_payment_reference_id uuid,
    rating_status character varying(20) NOT NULL,
    paid_at timestamp with time zone,
    finance_state_sequence bigint DEFAULT 0 NOT NULL,
    last_finance_event_at timestamp with time zone,
    CONSTRAINT ck_admissions_payment_projection_rating_status CHECK (((rating_status)::text = ANY ((ARRAY['RATED'::character varying, 'UNRATED'::character varying])::text[])))
);


--
-- Name: application_payment_references_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_payment_references_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_id uuid,
    reference character varying(80),
    amount_due numeric(12,2),
    currency_code character varying(3),
    base_currency_code character varying(3),
    exchange_rate_id uuid,
    base_amount_due numeric(12,2),
    status character varying(30),
    required_for_submission boolean,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    finance_payment_reference_id uuid,
    rating_status character varying(20),
    paid_at timestamp with time zone,
    finance_state_sequence bigint,
    last_finance_event_at timestamp with time zone
);


--
-- Name: application_prior_uz_declarations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_prior_uz_declarations (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    previously_studied_at_uz boolean CONSTRAINT application_prior_uz_declarat_previously_studied_at_uz_not_null NOT NULL,
    registration_number character varying(80),
    enrolment_started_on date,
    enrolment_ended_on date,
    previously_accepted_offer boolean,
    previously_took_up_place boolean,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_prior_uz_affirmative_details CHECK ((((NOT previously_studied_at_uz) AND (registration_number IS NULL) AND (enrolment_started_on IS NULL) AND (enrolment_ended_on IS NULL) AND (previously_accepted_offer IS NULL) AND (previously_took_up_place IS NULL)) OR (previously_studied_at_uz AND (registration_number IS NOT NULL) AND (enrolment_started_on IS NOT NULL) AND (previously_accepted_offer IS NOT NULL) AND (previously_took_up_place IS NOT NULL)))),
    CONSTRAINT ck_prior_uz_enrolment_dates CHECK (((enrolment_ended_on IS NULL) OR (enrolment_ended_on >= enrolment_started_on)))
);


--
-- Name: application_prior_uz_declarations_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_prior_uz_declarations_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_id uuid,
    previously_studied_at_uz boolean,
    registration_number character varying(80),
    enrolment_started_on date,
    enrolment_ended_on date,
    previously_accepted_offer boolean,
    previously_took_up_place boolean,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: application_professional_achievements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_professional_achievements (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    achievement_type character varying(30) NOT NULL,
    title character varying(250) NOT NULL,
    organisation character varying(200),
    achieved_on date,
    description character varying(2000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_professional_achievement_type CHECK (((achievement_type)::text = ANY ((ARRAY['AWARD'::character varying, 'PROFESSIONAL_MEMBERSHIP'::character varying, 'PUBLICATION'::character varying, 'PRESENTATION'::character varying, 'OTHER'::character varying])::text[])))
);


--
-- Name: application_professional_achievements_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_professional_achievements_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_id uuid,
    achievement_type character varying(30),
    title character varying(250),
    organisation character varying(200),
    achieved_on date,
    description character varying(2000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: application_programme_choices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_programme_choices (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    programme_id uuid NOT NULL,
    choice_rank integer NOT NULL,
    choice_status character varying(30) NOT NULL,
    evaluation_summary character varying(1000),
    decision_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    programme_version_id uuid,
    programme_code character varying(50),
    programme_name character varying(200),
    award_name character varying(200),
    owning_academic_unit_id uuid,
    owning_academic_unit_name character varying(180),
    programme_version_code character varying(40),
    catalogue_snapshot_status character varying(30) DEFAULT 'LEGACY_UNRESOLVED'::character varying CONSTRAINT application_programme_choice_catalogue_snapshot_status_not_null NOT NULL,
    CONSTRAINT ck_application_choice_catalogue_snapshot_status CHECK (((catalogue_snapshot_status)::text = ANY ((ARRAY['VALIDATED'::character varying, 'LEGACY_UNRESOLVED'::character varying])::text[]))),
    CONSTRAINT ck_application_choice_validated_snapshot CHECK ((((catalogue_snapshot_status)::text = 'LEGACY_UNRESOLVED'::text) OR ((programme_version_id IS NOT NULL) AND (programme_code IS NOT NULL) AND (programme_name IS NOT NULL) AND (award_name IS NOT NULL) AND (owning_academic_unit_id IS NOT NULL) AND (owning_academic_unit_name IS NOT NULL) AND (programme_version_code IS NOT NULL)))),
    CONSTRAINT ck_application_programme_choice_status CHECK (((choice_status)::text = ANY ((ARRAY['PENDING'::character varying, 'ELIGIBLE'::character varying, 'CONDITIONALLY_ELIGIBLE'::character varying, 'INELIGIBLE'::character varying, 'REQUIRES_REVIEW'::character varying, 'UNDER_ACADEMIC_REVIEW'::character varying, 'ADMITTED'::character varying, 'REJECTED'::character varying, 'OFFERED'::character varying, 'CONVERTED'::character varying])::text[])))
);


--
-- Name: application_programme_choices_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_programme_choices_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_id uuid,
    programme_id uuid,
    choice_rank integer,
    choice_status character varying(30),
    evaluation_summary character varying(1000),
    decision_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    programme_version_id uuid,
    programme_code character varying(50),
    programme_name character varying(200),
    award_name character varying(200),
    owning_academic_unit_id uuid,
    owning_academic_unit_name character varying(180),
    programme_version_code character varying(40),
    catalogue_snapshot_status character varying(30)
);


--
-- Name: application_programme_entry_option_selections; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_programme_entry_option_selections (
    id uuid NOT NULL,
    programme_choice_id uuid CONSTRAINT application_programme_entry_option_programme_choice_id_not_null NOT NULL,
    entry_option_id uuid CONSTRAINT application_programme_entry_option_sel_entry_option_id_not_null NOT NULL,
    entry_option_code character varying(50) CONSTRAINT application_programme_entry_option_s_entry_option_code_not_null NOT NULL,
    entry_option_name character varying(200) CONSTRAINT application_programme_entry_option_s_entry_option_name_not_null NOT NULL,
    preference_rank integer CONSTRAINT application_programme_entry_option_sel_preference_rank_not_null NOT NULL,
    created_at timestamp with time zone CONSTRAINT application_programme_entry_option_selectio_created_at_not_null NOT NULL,
    updated_at timestamp with time zone CONSTRAINT application_programme_entry_option_selectio_updated_at_not_null NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_programme_entry_option_selection_rank CHECK ((preference_rank > 0))
);


--
-- Name: application_programme_entry_option_selections_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_programme_entry_option_selections_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    programme_choice_id uuid,
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
-- Name: application_programme_option_snapshots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_programme_option_snapshots (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    programme_id uuid NOT NULL,
    programme_version_id uuid CONSTRAINT application_programme_option_snap_programme_version_id_not_null NOT NULL,
    programme_code character varying(50) NOT NULL,
    programme_name character varying(200) NOT NULL,
    award_name character varying(200) NOT NULL,
    owning_academic_unit_id uuid CONSTRAINT application_programme_option_s_owning_academic_unit_id_not_null NOT NULL,
    owning_academic_unit_name character varying(180) CONSTRAINT application_programme_option_owning_academic_unit_name_not_null NOT NULL,
    programme_version_code character varying(40) CONSTRAINT application_programme_option_sn_programme_version_code_not_null NOT NULL,
    programme_type_id uuid,
    programme_type_code character varying(40),
    programme_type_name character varying(120),
    programme_level_id uuid,
    programme_level_code character varying(40),
    programme_level_name character varying(120),
    minimum_entry_option_selections integer DEFAULT 0 CONSTRAINT application_programme_optio_minimum_entry_option_selec_not_null NOT NULL,
    maximum_entry_option_selections integer DEFAULT 0 CONSTRAINT application_programme_optio_maximum_entry_option_selec_not_null NOT NULL,
    entry_options_json jsonb DEFAULT '[]'::jsonb CONSTRAINT application_programme_option_snapsh_entry_options_json_not_null NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_application_programme_option_snapshot_limits CHECK (((minimum_entry_option_selections >= 0) AND (maximum_entry_option_selections >= minimum_entry_option_selections)))
);


--
-- Name: application_programme_option_snapshots_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_programme_option_snapshots_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_id uuid,
    programme_id uuid,
    programme_version_id uuid,
    programme_code character varying(50),
    programme_name character varying(200),
    award_name character varying(200),
    owning_academic_unit_id uuid,
    owning_academic_unit_name character varying(180),
    programme_version_code character varying(40),
    programme_type_id uuid,
    programme_type_code character varying(40),
    programme_type_name character varying(120),
    programme_level_id uuid,
    programme_level_code character varying(40),
    programme_level_name character varying(120),
    minimum_entry_option_selections integer,
    maximum_entry_option_selections integer,
    entry_options_json jsonb,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: application_referee_nominations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_referee_nominations (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    referee_id uuid NOT NULL,
    organisation character varying(200) NOT NULL,
    position_title character varying(150) NOT NULL,
    expertise character varying(500) NOT NULL,
    relationship_to_applicant character varying(200) CONSTRAINT application_referee_nominati_relationship_to_applicant_not_null NOT NULL,
    normalized_email character varying(200) NOT NULL,
    normalized_phone_number character varying(50),
    is_current boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: application_referee_nominations_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_referee_nominations_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_id uuid,
    referee_id uuid,
    organisation character varying(200),
    position_title character varying(150),
    expertise character varying(500),
    relationship_to_applicant character varying(200),
    normalized_email character varying(200),
    normalized_phone_number character varying(50),
    is_current boolean,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: application_sections; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_sections (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    section_code character varying(60) NOT NULL,
    section_name character varying(150) NOT NULL,
    is_required boolean NOT NULL,
    is_repeatable boolean NOT NULL,
    minimum_records integer DEFAULT 0 NOT NULL,
    sort_order integer NOT NULL,
    status character varying(30) NOT NULL,
    completed_at timestamp with time zone,
    completion_summary character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_application_sections_minimum_records CHECK ((minimum_records >= 0)),
    CONSTRAINT ck_application_sections_sort_order CHECK ((sort_order > 0)),
    CONSTRAINT ck_application_sections_status CHECK (((status)::text = ANY ((ARRAY['NOT_STARTED'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETE'::character varying, 'VERIFIED'::character varying, 'REJECTED'::character varying, 'CORRECTION_REQUIRED'::character varying])::text[])))
);


--
-- Name: application_sections_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_sections_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_id uuid,
    section_code character varying(60),
    section_name character varying(150),
    is_required boolean,
    is_repeatable boolean,
    minimum_records integer,
    sort_order integer,
    status character varying(30),
    completed_at timestamp with time zone,
    completion_summary character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: application_status_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_status_events (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    from_status character varying(30),
    to_status character varying(30) NOT NULL,
    reason character varying(1000),
    changed_by_user_id uuid NOT NULL,
    changed_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: application_status_events_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_status_events_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_id uuid,
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
-- Name: application_type_document_requirements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_type_document_requirements (
    id uuid NOT NULL,
    application_type_id uuid CONSTRAINT application_type_document_requirem_application_type_id_not_null NOT NULL,
    requirement_code character varying(80) CONSTRAINT application_type_document_requirement_requirement_code_not_null NOT NULL,
    requirement_name character varying(150) CONSTRAINT application_type_document_requirement_requirement_name_not_null NOT NULL,
    is_required boolean NOT NULL,
    sort_order integer NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_application_type_document_requirement_soft_delete CHECK ((((deleted_at IS NULL) AND (deleted_by_user_id IS NULL)) OR ((deleted_at IS NOT NULL) AND (deleted_by_user_id IS NOT NULL)))),
    CONSTRAINT ck_application_type_document_requirement_values CHECK (((length(TRIM(BOTH FROM requirement_code)) > 0) AND (length(TRIM(BOTH FROM requirement_name)) > 0) AND (sort_order > 0)))
);


--
-- Name: application_type_document_requirements_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_type_document_requirements_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_type_id uuid,
    requirement_code character varying(80),
    requirement_name character varying(150),
    is_required boolean,
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
-- Name: application_type_programme_mappings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_type_programme_mappings (
    id uuid NOT NULL,
    application_type_id uuid CONSTRAINT application_type_programme_mapping_application_type_id_not_null NOT NULL,
    programme_id uuid NOT NULL,
    programme_code character varying(50) NOT NULL,
    programme_name character varying(200) NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: application_type_programme_mappings_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_type_programme_mappings_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_type_id uuid,
    programme_id uuid,
    programme_code character varying(50),
    programme_name character varying(200),
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
-- Name: application_type_sections; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_type_sections (
    id uuid NOT NULL,
    application_type_id uuid NOT NULL,
    section_code character varying(60) NOT NULL,
    section_name character varying(150) NOT NULL,
    is_required boolean NOT NULL,
    is_repeatable boolean NOT NULL,
    minimum_records integer DEFAULT 0 NOT NULL,
    sort_order integer NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_application_type_sections_minimum_records CHECK ((minimum_records >= 0)),
    CONSTRAINT ck_application_type_sections_sort_order CHECK ((sort_order > 0))
);


--
-- Name: application_type_sections_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_type_sections_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_type_id uuid,
    section_code character varying(60),
    section_name character varying(150),
    is_required boolean,
    is_repeatable boolean,
    minimum_records integer,
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
-- Name: application_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_types (
    id uuid NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(150) NOT NULL,
    requires_employment_history boolean NOT NULL,
    requires_referees boolean NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    finance_fee_structure_id uuid,
    finance_fee_structure_code character varying(50),
    finance_fee_structure_name character varying(160),
    fee_policy_status character varying(30) DEFAULT 'UNCONFIGURED'::character varying NOT NULL,
    fee_free_reason character varying(1000),
    fee_policy_decided_by_user_id uuid,
    fee_policy_decided_at timestamp with time zone,
    CONSTRAINT ck_application_type_fee_free_reason CHECK ((((fee_policy_status)::text <> 'FEE_FREE'::text) OR (length(TRIM(BOTH FROM fee_free_reason)) >= 10))),
    CONSTRAINT ck_application_type_fee_policy_status CHECK (((fee_policy_status)::text = ANY ((ARRAY['UNCONFIGURED'::character varying, 'FEE_STRUCTURE'::character varying, 'FEE_FREE'::character varying, 'LEGACY_CONFIGURED'::character varying])::text[]))),
    CONSTRAINT ck_application_type_fee_structure_snapshot CHECK ((((finance_fee_structure_id IS NULL) AND (finance_fee_structure_code IS NULL) AND (finance_fee_structure_name IS NULL)) OR ((finance_fee_structure_id IS NOT NULL) AND (finance_fee_structure_code IS NOT NULL) AND (finance_fee_structure_name IS NOT NULL))))
);


--
-- Name: application_types_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_types_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code character varying(50),
    name character varying(150),
    requires_employment_history boolean,
    requires_referees boolean,
    is_active boolean,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    finance_fee_structure_id uuid,
    finance_fee_structure_code character varying(50),
    finance_fee_structure_name character varying(160),
    fee_policy_status character varying(30),
    fee_free_reason character varying(1000),
    fee_policy_decided_by_user_id uuid,
    fee_policy_decided_at timestamp with time zone
);


--
-- Name: applications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.applications (
    id uuid NOT NULL,
    admission_cycle_id uuid,
    applicant_id uuid NOT NULL,
    application_type_id uuid NOT NULL,
    application_number character varying(50) NOT NULL,
    submitted_at timestamp with time zone,
    payment_required boolean NOT NULL,
    payment_confirmed_at timestamp with time zone,
    payment_override_by_user_id uuid,
    payment_override_reason character varying(500),
    status character varying(30) NOT NULL,
    status_reason character varying(1000),
    verified_by_user_id uuid,
    verified_at timestamp with time zone,
    legacy_statu_id bigint,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    sections_complete boolean DEFAULT false NOT NULL,
    declaration_accepted_at timestamp with time zone,
    declaration_accepted_by_user_id uuid,
    declaration_version character varying(50),
    calculated_total_points numeric(8,2),
    points_calculated_at timestamp with time zone,
    professional_achievements_declared_none boolean DEFAULT false NOT NULL,
    intake_id uuid NOT NULL,
    intake_code character varying(50) NOT NULL,
    intake_name character varying(180) NOT NULL,
    intake_starts_on date NOT NULL,
    intake_ends_on date NOT NULL,
    maximum_programme_choices integer NOT NULL,
    CONSTRAINT ck_applications_intake_dates CHECK ((intake_starts_on <= intake_ends_on)),
    CONSTRAINT ck_applications_maximum_programme_choices CHECK ((maximum_programme_choices > 0)),
    CONSTRAINT ck_applications_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'SUBMITTED'::character varying, 'PAYMENT_PENDING'::character varying, 'UNDER_REVIEW'::character varying, 'INCOMPLETE'::character varying, 'ELIGIBLE'::character varying, 'NOT_ELIGIBLE'::character varying, 'UNDER_ACADEMIC_REVIEW'::character varying, 'ADMITTED'::character varying, 'REJECTED'::character varying, 'OFFERED'::character varying, 'ACCEPTED'::character varying, 'DECLINED'::character varying, 'WITHDRAWN'::character varying, 'CONVERTED'::character varying])::text[])))
);


--
-- Name: COLUMN applications.admission_cycle_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.applications.admission_cycle_id IS 'Nullable historical compatibility link only. New applications use intake_id and leave this column null.';


--
-- Name: applications_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.applications_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    admission_cycle_id uuid,
    applicant_id uuid,
    application_type_id uuid,
    application_number character varying(50),
    submitted_at timestamp with time zone,
    payment_required boolean,
    payment_confirmed_at timestamp with time zone,
    payment_override_by_user_id uuid,
    payment_override_reason character varying(500),
    status character varying(30),
    status_reason character varying(1000),
    verified_by_user_id uuid,
    verified_at timestamp with time zone,
    legacy_statu_id bigint,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    sections_complete boolean,
    declaration_accepted_at timestamp with time zone,
    declaration_accepted_by_user_id uuid,
    declaration_version character varying(50),
    calculated_total_points numeric(8,2),
    points_calculated_at timestamp with time zone,
    professional_achievements_declared_none boolean,
    intake_id uuid,
    intake_code character varying(50),
    intake_name character varying(180),
    intake_starts_on date,
    intake_ends_on date,
    maximum_programme_choices integer
);


--
-- Name: exam_bodies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_bodies (
    id uuid NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(150) NOT NULL,
    country_id uuid,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: exam_bodies_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exam_bodies_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code character varying(50),
    name character varying(150),
    country_id uuid,
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
-- Name: grading_scale_values; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grading_scale_values (
    id uuid NOT NULL,
    grading_scale_id uuid NOT NULL,
    grade character varying(20) NOT NULL,
    points numeric(8,2),
    is_pass boolean NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_grading_scale_values_points_non_negative CHECK (((points IS NULL) OR (points >= (0)::numeric)))
);


--
-- Name: grading_scale_values_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grading_scale_values_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    grading_scale_id uuid,
    grade character varying(20),
    points numeric(8,2),
    is_pass boolean,
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
-- Name: grading_scales; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grading_scales (
    id uuid NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(150) NOT NULL,
    level character varying(30) NOT NULL,
    effective_from date NOT NULL,
    effective_to date,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_grading_scales_effective_period CHECK (((effective_to IS NULL) OR (effective_to >= effective_from)))
);


--
-- Name: grading_scales_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grading_scales_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code character varying(50),
    name character varying(150),
    level character varying(30),
    effective_from date,
    effective_to date,
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
    CONSTRAINT ck_admissions_outbox_attempt_count CHECK ((attempt_count >= 0)),
    CONSTRAINT ck_admissions_outbox_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PUBLISHED'::character varying, 'DEAD'::character varying])::text[])))
);


--
-- Name: offer_batches; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offer_batches (
    id uuid NOT NULL,
    admission_cycle_id uuid NOT NULL,
    selection_round_id uuid NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(180) NOT NULL,
    scope_type character varying(30) NOT NULL,
    scope_id uuid,
    status character varying(30) NOT NULL,
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    dispatched_at timestamp with time zone,
    closed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_offer_batches_approval CHECK (((((status)::text = ANY ((ARRAY['APPROVED'::character varying, 'DISPATCHED'::character varying, 'CLOSED'::character varying])::text[])) AND (approved_at IS NOT NULL) AND (approved_by_user_id IS NOT NULL)) OR ((status)::text = 'DRAFT'::text))),
    CONSTRAINT ck_offer_batches_scope CHECK (((((scope_type)::text = 'INSTITUTION'::text) AND (scope_id IS NULL)) OR (((scope_type)::text = ANY ((ARRAY['ACADEMIC_UNIT'::character varying, 'PROGRAMME'::character varying])::text[])) AND (scope_id IS NOT NULL)))),
    CONSTRAINT ck_offer_batches_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'APPROVED'::character varying, 'DISPATCHED'::character varying, 'CLOSED'::character varying])::text[])))
);


--
-- Name: TABLE offer_batches; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.offer_batches IS 'Historical — read-only from ADR-0014 onward. No new rows.';


--
-- Name: offer_batches_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offer_batches_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    admission_cycle_id uuid,
    selection_round_id uuid,
    code character varying(50),
    name character varying(180),
    scope_type character varying(30),
    scope_id uuid,
    status character varying(30),
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    dispatched_at timestamp with time zone,
    closed_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: offer_conditions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offer_conditions (
    id uuid NOT NULL,
    offer_id uuid NOT NULL,
    condition_code character varying(60) NOT NULL,
    description character varying(1000) NOT NULL,
    required boolean NOT NULL,
    status character varying(30) NOT NULL,
    satisfied_by_user_id uuid,
    satisfied_at timestamp with time zone,
    resolution_notes character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_offer_conditions_resolution CHECK (((((status)::text = 'PENDING'::text) AND (satisfied_at IS NULL) AND (satisfied_by_user_id IS NULL)) OR (((status)::text = ANY ((ARRAY['SATISFIED'::character varying, 'WAIVED'::character varying])::text[])) AND (satisfied_at IS NOT NULL) AND (satisfied_by_user_id IS NOT NULL)))),
    CONSTRAINT ck_offer_conditions_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'SATISFIED'::character varying, 'WAIVED'::character varying])::text[])))
);


--
-- Name: offer_conditions_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offer_conditions_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    offer_id uuid,
    condition_code character varying(60),
    description character varying(1000),
    required boolean,
    status character varying(30),
    satisfied_by_user_id uuid,
    satisfied_at timestamp with time zone,
    resolution_notes character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: offer_dispatches; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offer_dispatches (
    id uuid NOT NULL,
    offer_id uuid NOT NULL,
    delivery_method_code character varying(40) NOT NULL,
    sent_to character varying(250) NOT NULL,
    sent_at timestamp with time zone,
    status character varying(30) NOT NULL,
    provider_message_id character varying(200),
    failure_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    offer_publication_id uuid,
    attempt_number integer DEFAULT 1 NOT NULL,
    notification_event_id uuid,
    CONSTRAINT ck_offer_dispatches_attempt CHECK ((attempt_number > 0)),
    CONSTRAINT ck_offer_dispatches_failure CHECK ((((status)::text <> ALL ((ARRAY['FAILED'::character varying, 'BOUNCED'::character varying])::text[])) OR (length(TRIM(BOTH FROM COALESCE(failure_reason, ''::character varying))) > 0))),
    CONSTRAINT ck_offer_dispatches_status CHECK (((status)::text = ANY ((ARRAY['QUEUED'::character varying, 'SENT'::character varying, 'DELIVERED'::character varying, 'FAILED'::character varying, 'BOUNCED'::character varying])::text[])))
);


--
-- Name: offer_dispatches_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offer_dispatches_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    offer_id uuid,
    delivery_method_code character varying(40),
    sent_to character varying(250),
    sent_at timestamp with time zone,
    status character varying(30),
    provider_message_id character varying(200),
    failure_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    offer_publication_id uuid,
    attempt_number integer,
    notification_event_id uuid
);


--
-- Name: offer_document_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offer_document_versions (
    id uuid NOT NULL,
    offer_id uuid NOT NULL,
    document_version integer NOT NULL,
    status character varying(30) NOT NULL,
    generated_document_id uuid,
    document_number character varying(80),
    storage_bucket character varying(120),
    storage_key character varying(500),
    checksum_sha256 character varying(64),
    failure_reason character varying(1000),
    requested_by_user_id uuid NOT NULL,
    requested_at timestamp with time zone NOT NULL,
    stored_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_offer_document_version_evidence CHECK (((((status)::text = 'REQUESTED'::text) AND (generated_document_id IS NULL) AND (stored_at IS NULL) AND (failure_reason IS NULL)) OR (((status)::text = 'STORED'::text) AND (generated_document_id IS NOT NULL) AND (document_number IS NOT NULL) AND (storage_bucket IS NOT NULL) AND (storage_key IS NOT NULL) AND (checksum_sha256 IS NOT NULL) AND (stored_at IS NOT NULL) AND (failure_reason IS NULL)) OR (((status)::text = 'FAILED'::text) AND (length(TRIM(BOTH FROM COALESCE(failure_reason, ''::character varying))) > 0)))),
    CONSTRAINT ck_offer_document_version_number CHECK ((document_version > 0)),
    CONSTRAINT ck_offer_document_version_status CHECK (((status)::text = ANY ((ARRAY['REQUESTED'::character varying, 'STORED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: offer_document_versions_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offer_document_versions_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    offer_id uuid,
    document_version integer,
    status character varying(30),
    generated_document_id uuid,
    document_number character varying(80),
    storage_bucket character varying(120),
    storage_key character varying(500),
    checksum_sha256 character varying(64),
    failure_reason character varying(1000),
    requested_by_user_id uuid,
    requested_at timestamp with time zone,
    stored_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: offer_number_sequence; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.offer_number_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: offer_publications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offer_publications (
    id uuid NOT NULL,
    offer_id uuid NOT NULL,
    offer_document_version_id uuid NOT NULL,
    publication_sequence integer NOT NULL,
    portal_published_at timestamp with time zone NOT NULL,
    published_by_user_id uuid NOT NULL,
    notification_event_id uuid NOT NULL,
    email_delivery_status character varying(30) NOT NULL,
    provider_message_id character varying(240),
    email_status_at timestamp with time zone NOT NULL,
    email_failure_reason character varying(1000),
    current_publication boolean NOT NULL,
    superseded_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_offer_publication_current CHECK (((current_publication AND (superseded_at IS NULL)) OR ((NOT current_publication) AND (superseded_at IS NOT NULL)))),
    CONSTRAINT ck_offer_publication_email_status CHECK (((email_delivery_status)::text = ANY ((ARRAY['QUEUED'::character varying, 'SENT'::character varying, 'FAILED'::character varying, 'BOUNCED'::character varying])::text[]))),
    CONSTRAINT ck_offer_publication_failure CHECK ((((email_delivery_status)::text <> ALL ((ARRAY['FAILED'::character varying, 'BOUNCED'::character varying])::text[])) OR (length(TRIM(BOTH FROM COALESCE(email_failure_reason, ''::character varying))) > 0))),
    CONSTRAINT ck_offer_publication_sequence CHECK ((publication_sequence > 0))
);


--
-- Name: offer_publications_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offer_publications_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    offer_id uuid,
    offer_document_version_id uuid,
    publication_sequence integer,
    portal_published_at timestamp with time zone,
    published_by_user_id uuid,
    notification_event_id uuid,
    email_delivery_status character varying(30),
    provider_message_id character varying(240),
    email_status_at timestamp with time zone,
    email_failure_reason character varying(1000),
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
-- Name: offer_responses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offer_responses (
    id uuid NOT NULL,
    offer_id uuid NOT NULL,
    response character varying(30) NOT NULL,
    responded_at timestamp with time zone NOT NULL,
    responded_by_user_id uuid NOT NULL,
    notes character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    offer_publication_id uuid,
    CONSTRAINT ck_offer_responses_response CHECK (((response)::text = ANY ((ARRAY['ACCEPTED'::character varying, 'DECLINED'::character varying])::text[])))
);


--
-- Name: offer_responses_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offer_responses_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    offer_id uuid,
    response character varying(30),
    responded_at timestamp with time zone,
    responded_by_user_id uuid,
    notes character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    offer_publication_id uuid
);


--
-- Name: offer_status_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offer_status_events (
    id uuid NOT NULL,
    offer_id uuid NOT NULL,
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
    version bigint NOT NULL
);


--
-- Name: offer_status_events_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offer_status_events_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    offer_id uuid,
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
-- Name: offers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offers (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    programme_choice_id uuid NOT NULL,
    offer_batch_id uuid,
    programme_id uuid NOT NULL,
    programme_version_id uuid NOT NULL,
    programme_code character varying(50) NOT NULL,
    programme_name character varying(200) NOT NULL,
    intake_id uuid NOT NULL,
    offer_number character varying(60) NOT NULL,
    offer_type character varying(30),
    status character varying(30) NOT NULL,
    conditions_text character varying(4000),
    acceptance_deadline timestamp with time zone,
    registration_date date,
    orientation_date date,
    commencement_date date,
    generated_document_id uuid,
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    sent_at timestamp with time zone,
    withdrawn_by_user_id uuid,
    withdrawal_reason character varying(1000),
    converted_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    expired_at timestamp with time zone,
    expiry_reason character varying(1000),
    conversion_event_id uuid,
    conversion_requested_at timestamp with time zone,
    conversion_request_id uuid,
    converted_student_id uuid,
    converted_student_number character varying(40),
    programme_choice_decision_id uuid,
    current_document_version_id uuid,
    current_publication_id uuid,
    amendment_pending boolean DEFAULT false NOT NULL,
    CONSTRAINT ck_offers_approval CHECK (((((status)::text = ANY ((ARRAY['APPROVED'::character varying, 'SENT'::character varying, 'ACCEPTED'::character varying, 'DECLINED'::character varying, 'EXPIRED'::character varying, 'CONVERTED'::character varying])::text[])) AND (approved_at IS NOT NULL) AND (approved_by_user_id IS NOT NULL)) OR ((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'WITHDRAWN'::character varying])::text[])))),
    CONSTRAINT ck_offers_complete_outside_draft CHECK ((((status)::text = 'DRAFT'::text) OR ((offer_type IS NOT NULL) AND (acceptance_deadline IS NOT NULL) AND (commencement_date IS NOT NULL)))),
    CONSTRAINT ck_offers_conditional_text CHECK ((((offer_type)::text <> 'CONDITIONAL'::text) OR (length(TRIM(BOTH FROM COALESCE(conditions_text, ''::character varying))) > 0))),
    CONSTRAINT ck_offers_conversion_handoff CHECK ((((conversion_event_id IS NULL) AND (conversion_requested_at IS NULL)) OR ((conversion_event_id IS NOT NULL) AND (conversion_requested_at IS NOT NULL) AND ((status)::text = ANY ((ARRAY['ACCEPTED'::character varying, 'CONVERTED'::character varying])::text[]))))),
    CONSTRAINT ck_offers_conversion_result CHECK (((((status)::text = 'CONVERTED'::text) AND (conversion_request_id IS NOT NULL) AND (converted_student_id IS NOT NULL) AND (length(TRIM(BOTH FROM COALESCE(converted_student_number, ''::character varying))) > 0) AND (converted_at IS NOT NULL)) OR (((status)::text <> 'CONVERTED'::text) AND (conversion_request_id IS NULL) AND (converted_student_id IS NULL) AND (converted_student_number IS NULL) AND (converted_at IS NULL)))),
    CONSTRAINT ck_offers_dates CHECK (((commencement_date IS NULL) OR (((orientation_date IS NULL) OR (orientation_date <= commencement_date)) AND ((registration_date IS NULL) OR (registration_date <= commencement_date))))),
    CONSTRAINT ck_offers_expiry CHECK (((((status)::text = 'EXPIRED'::text) AND (expired_at IS NOT NULL) AND (length(TRIM(BOTH FROM COALESCE(expiry_reason, ''::character varying))) > 0)) OR (((status)::text <> 'EXPIRED'::text) AND (expired_at IS NULL) AND (expiry_reason IS NULL)))),
    CONSTRAINT ck_offers_source_kind CHECK ((((offer_batch_id IS NOT NULL) AND (programme_choice_decision_id IS NULL)) OR ((offer_batch_id IS NULL) AND (programme_choice_decision_id IS NOT NULL)))),
    CONSTRAINT ck_offers_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'APPROVED'::character varying, 'SENT'::character varying, 'ACCEPTED'::character varying, 'DECLINED'::character varying, 'EXPIRED'::character varying, 'WITHDRAWN'::character varying, 'CONVERTED'::character varying])::text[]))),
    CONSTRAINT ck_offers_type CHECK (((offer_type IS NULL) OR ((offer_type)::text = ANY ((ARRAY['FIRM'::character varying, 'CONDITIONAL'::character varying])::text[]))))
);


--
-- Name: offers_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offers_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_id uuid,
    programme_choice_id uuid,
    offer_batch_id uuid,
    programme_id uuid,
    programme_version_id uuid,
    programme_code character varying(50),
    programme_name character varying(200),
    intake_id uuid,
    offer_number character varying(60),
    offer_type character varying(30),
    status character varying(30),
    conditions_text character varying(4000),
    acceptance_deadline timestamp with time zone,
    registration_date date,
    orientation_date date,
    commencement_date date,
    generated_document_id uuid,
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    sent_at timestamp with time zone,
    withdrawn_by_user_id uuid,
    withdrawal_reason character varying(1000),
    converted_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    expired_at timestamp with time zone,
    expiry_reason character varying(1000),
    conversion_event_id uuid,
    conversion_requested_at timestamp with time zone,
    conversion_request_id uuid,
    converted_student_id uuid,
    converted_student_number character varying(40),
    programme_choice_decision_id uuid,
    current_document_version_id uuid,
    current_publication_id uuid,
    amendment_pending boolean
);


--
-- Name: programme_choice_decisions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.programme_choice_decisions (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    programme_choice_id uuid NOT NULL,
    decision character varying(30) NOT NULL,
    reason character varying(1000) NOT NULL,
    source_recommendation_id uuid,
    decided_by_user_id uuid NOT NULL,
    decided_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_programme_choice_decision CHECK (((decision)::text = ANY ((ARRAY['ADMIT'::character varying, 'REJECT'::character varying])::text[]))),
    CONSTRAINT ck_programme_choice_decision_reason CHECK ((length(TRIM(BOTH FROM reason)) > 0))
);


--
-- Name: programme_choice_decisions_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.programme_choice_decisions_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_id uuid,
    programme_choice_id uuid,
    decision character varying(30),
    reason character varying(1000),
    source_recommendation_id uuid,
    decided_by_user_id uuid,
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
-- Name: revinfo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.revinfo (
    rev integer NOT NULL,
    revtstmp bigint NOT NULL,
    actor_user_id uuid,
    service_name character varying(100) DEFAULT 'admissions-service'::character varying NOT NULL,
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
-- Name: selection_decisions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.selection_decisions (
    id uuid NOT NULL,
    selection_round_id uuid NOT NULL,
    programme_choice_id uuid NOT NULL,
    decision character varying(30) NOT NULL,
    rank_position integer,
    quota_type_code character varying(50),
    reason character varying(1000) NOT NULL,
    decided_by_user_id uuid NOT NULL,
    decided_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_selection_decisions_decision CHECK (((decision)::text = ANY ((ARRAY['SHORTLIST'::character varying, 'SELECT'::character varying, 'REJECT'::character varying, 'WAITLIST'::character varying])::text[]))),
    CONSTRAINT ck_selection_decisions_rank CHECK (((rank_position IS NULL) OR (rank_position > 0))),
    CONSTRAINT ck_selection_decisions_reason CHECK ((length(TRIM(BOTH FROM reason)) > 0))
);


--
-- Name: TABLE selection_decisions; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.selection_decisions IS 'Historical — read-only from ADR-0014 onward. No new rows.';


--
-- Name: selection_decisions_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.selection_decisions_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    selection_round_id uuid,
    programme_choice_id uuid,
    decision character varying(30),
    rank_position integer,
    quota_type_code character varying(50),
    reason character varying(1000),
    decided_by_user_id uuid,
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
-- Name: selection_rounds; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.selection_rounds (
    id uuid NOT NULL,
    admission_cycle_id uuid NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(180) NOT NULL,
    status character varying(30) NOT NULL,
    opened_at timestamp with time zone,
    approved_at timestamp with time zone,
    approved_by_user_id uuid,
    closed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_selection_rounds_approval CHECK (((((status)::text = ANY ((ARRAY['APPROVED'::character varying, 'CLOSED'::character varying])::text[])) AND (approved_at IS NOT NULL) AND (approved_by_user_id IS NOT NULL)) OR ((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'OPEN'::character varying])::text[])))),
    CONSTRAINT ck_selection_rounds_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'OPEN'::character varying, 'APPROVED'::character varying, 'CLOSED'::character varying])::text[])))
);


--
-- Name: TABLE selection_rounds; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.selection_rounds IS 'Historical — read-only from ADR-0014 onward. No new rows.';


--
-- Name: selection_rounds_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.selection_rounds_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    admission_cycle_id uuid,
    code character varying(50),
    name character varying(180),
    status character varying(30),
    opened_at timestamp with time zone,
    approved_at timestamp with time zone,
    approved_by_user_id uuid,
    closed_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Data for Name: academic_recommendations; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: academic_recommendations_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: academic_review_assignments; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: academic_review_assignments_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: academic_reviews; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: academic_reviews_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: academic_unit_recommendations; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: academic_unit_recommendations_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: admission_cycle_archive_summaries; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: admission_cycle_archive_summaries_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: admission_cycles; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: admission_cycles_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: admission_qualification_requirement_groups; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: admission_qualification_requirement_groups_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: admission_qualification_requirement_items; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: admission_qualification_requirement_items_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: admission_quotas; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: admission_quotas_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: admission_requirement_sets; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: admission_requirement_sets_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: admission_subject_requirements; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: admission_subject_requirements_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: admission_subjects; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004001', '4001', 'Agriculture', 'O_LEVEL', 'SCIENCE', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, true);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004002', '4002', 'Physical Education, Sport and Mass Displays', 'O_LEVEL', 'TECHNICAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('9f4170b0-43e9-4ef8-bce0-54c83f4b0006', '4003', 'Combined Science', 'O_LEVEL', 'SCIENCE', true, NULL, NULL, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 1, true);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('9f4170b0-43e9-4ef8-bce0-54c83f4b0002', '4004', 'Mathematics', 'O_LEVEL', 'MATHEMATICS', true, NULL, NULL, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 1, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('9f4170b0-43e9-4ef8-bce0-54c83f4b0001', '4005', 'English Language', 'O_LEVEL', 'ENGLISH', true, NULL, NULL, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 1, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004006', '4006', 'Heritage Studies', 'O_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004007', '4007', 'Shona Language', 'O_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004009', '4009', 'Tonga Language', 'O_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004010', '4010', 'Nambya Language', 'O_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004011', '4011', 'Tshivenda Language', 'O_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004012', '4012', 'Xichangana Language', 'O_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004013', '4013', 'Kalanga Language', 'O_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004014', '4014', 'Sesotho Language', 'O_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('9f4170b0-43e9-4ef8-bce0-54c83f4b0007', '4021', 'Computer Science', 'O_LEVEL', 'SCIENCE', true, NULL, NULL, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 1, true);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('9f4170b0-43e9-4ef8-bce0-54c83f4b0008', '4022', 'Geography', 'O_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 1, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('9f4170b0-43e9-4ef8-bce0-54c83f4b0005', '4023', 'Physics', 'O_LEVEL', 'SCIENCE', true, NULL, NULL, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 1, true);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('9f4170b0-43e9-4ef8-bce0-54c83f4b0004', '4024', 'Chemistry', 'O_LEVEL', 'SCIENCE', true, NULL, NULL, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 1, true);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('9f4170b0-43e9-4ef8-bce0-54c83f4b0003', '4025', 'Biology', 'O_LEVEL', 'SCIENCE', true, NULL, NULL, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 1, true);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004026', '4026', 'Additional Mathematics', 'O_LEVEL', 'MATHEMATICS', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004027', '4027', 'Pure Mathematics', 'O_LEVEL', 'MATHEMATICS', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004029', '4029', 'Literature in English', 'O_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('9f4170b0-43e9-4ef8-bce0-54c83f4b0009', '4044', 'History', 'O_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 1, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004045', '4045', 'Sociology', 'O_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004046', '4046', 'Economic History', 'O_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004047', '4047', 'Family and Religious Studies', 'O_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004048', '4048', 'Business and Enterprise Skills', 'O_LEVEL', 'COMMERCIAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004049', '4049', 'Commerce', 'O_LEVEL', 'COMMERCIAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004050', '4050', 'Economics', 'O_LEVEL', 'COMMERCIAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('9f4170b0-43e9-4ef8-bce0-54c83f4b0010', '4051', 'Principles of Accounting', 'O_LEVEL', 'COMMERCIAL', true, NULL, NULL, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 1, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004052', '4052', 'Building Technology and Design', 'O_LEVEL', 'TECHNICAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004053', '4053', 'Design and Technology', 'O_LEVEL', 'TECHNICAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004054', '4054', 'Food Technology and Design', 'O_LEVEL', 'TECHNICAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004055', '4055', 'Metal Technology and Design', 'O_LEVEL', 'TECHNICAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004058', '4058', 'Textile Technology and Design', 'O_LEVEL', 'TECHNICAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004059', '4059', 'Wood Technology and Design', 'O_LEVEL', 'TECHNICAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004060', '4060', 'Art', 'O_LEVEL', 'ARTS', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004061', '4061', 'Dance', 'O_LEVEL', 'ARTS', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004062', '4062', 'Musical Arts', 'O_LEVEL', 'ARTS', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004063', '4063', 'Theatre Arts', 'O_LEVEL', 'ARTS', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004064', '4064', 'French', 'O_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004065', '4065', 'Commercial Studies', 'O_LEVEL', 'COMMERCIAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004068', '4068', 'Ndebele Language', 'O_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004073', '4073', 'Statistics', 'O_LEVEL', 'MATHEMATICS', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004074', '4074', 'English for Communication', 'O_LEVEL', 'ENGLISH', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004075', '4075', 'Mathematics Syllabus A', 'O_LEVEL', 'MATHEMATICS', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004076', '4076', 'Hospitality Management and Design', 'O_LEVEL', 'TECHNICAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-8000-000000004077', '4077', 'Guidance and Counselling', 'O_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000005033', '5033', 'Communication Skills', 'A_LEVEL', 'ENGLISH', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('9f4170b0-43e9-4ef8-bce0-54c83f4b0108', '6001', 'Accounting', 'A_LEVEL', 'COMMERCIAL', true, NULL, NULL, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 1, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006002', '6002', 'Additional Mathematics', 'A_LEVEL', 'MATHEMATICS', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006003', '6003', 'Building Technology and Design', 'A_LEVEL', 'TECHNICAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006004', '6004', 'Business Enterprise Skills', 'A_LEVEL', 'COMMERCIAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006005', '6005', 'Design and Technology', 'A_LEVEL', 'TECHNICAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('9f4170b0-43e9-4ef8-bce0-54c83f4b0107', '6006', 'History', 'A_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 1, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006021', '6021', 'Mechanical Mathematics', 'A_LEVEL', 'MATHEMATICS', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006022', '6022', 'Sports Management', 'A_LEVEL', 'TECHNICAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('9f4170b0-43e9-4ef8-bce0-54c83f4b0105', '6023', 'Computer Science', 'A_LEVEL', 'SCIENCE', true, NULL, NULL, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 1, true);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006024', '6024', 'Horticulture', 'A_LEVEL', 'SCIENCE', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, true);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('9f4170b0-43e9-4ef8-bce0-54c83f4b0110', '6025', 'Business Studies', 'A_LEVEL', 'COMMERCIAL', true, NULL, NULL, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 1, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006026', '6026', 'Theatre Arts', 'A_LEVEL', 'ARTS', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006027', '6027', 'Wood Technology and Design', 'A_LEVEL', 'TECHNICAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006028', '6028', 'Animal Science', 'A_LEVEL', 'SCIENCE', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, true);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006029', '6029', 'Art', 'A_LEVEL', 'ARTS', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('9f4170b0-43e9-4ef8-bce0-54c83f4b0102', '6030', 'Biology', 'A_LEVEL', 'SCIENCE', true, NULL, NULL, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 1, true);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('9f4170b0-43e9-4ef8-bce0-54c83f4b0103', '6031', 'Chemistry', 'A_LEVEL', 'SCIENCE', true, NULL, NULL, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 1, true);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('9f4170b0-43e9-4ef8-bce0-54c83f4b0104', '6032', 'Physics', 'A_LEVEL', 'SCIENCE', true, NULL, NULL, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 1, true);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006034', '6034', 'Economic History', 'A_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006036', '6036', 'Food Technology and Design', 'A_LEVEL', 'TECHNICAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('9f4170b0-43e9-4ef8-bce0-54c83f4b0106', '6037', 'Geography', 'A_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 1, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006039', '6039', 'Literature in English', 'A_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006040', '6040', 'Metal Technology and Design', 'A_LEVEL', 'TECHNICAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006042', '6042', 'Pure Mathematics', 'A_LEVEL', 'MATHEMATICS', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006043', '6043', 'Sociology', 'A_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006044', '6044', 'Software Engineering', 'A_LEVEL', 'SCIENCE', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, true);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006046', '6046', 'Statistics', 'A_LEVEL', 'MATHEMATICS', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006047', '6047', 'Technical Graphics and Design', 'A_LEVEL', 'TECHNICAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006048', '6048', 'Agriculture Engineering', 'A_LEVEL', 'SCIENCE', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, true);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006049', '6049', 'Crop Science', 'A_LEVEL', 'SCIENCE', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, true);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006050', '6050', 'Dance', 'A_LEVEL', 'ARTS', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006053', '6053', 'Musical Arts', 'A_LEVEL', 'ARTS', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006054', '6054', 'Shona Language', 'A_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006055', '6055', 'Ndebele Language', 'A_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006056', '6056', 'Tonga Language', 'A_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006057', '6057', 'Nambya Language', 'A_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006058', '6058', 'Tshivenda Language', 'A_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006059', '6059', 'Xichangana Language', 'A_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006060', '6060', 'Kalanga Language', 'A_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006061', '6061', 'Sesotho Language', 'A_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006068', '6068', 'French', 'A_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006069', '6069', 'Textile Technology and Design', 'A_LEVEL', 'TECHNICAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006070', '6070', 'Physical Education, Sport and Mass Displays', 'A_LEVEL', 'TECHNICAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('9f4170b0-43e9-4ef8-bce0-54c83f4b0109', '6073', 'Economics', 'A_LEVEL', 'COMMERCIAL', true, NULL, NULL, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 1, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006074', '6074', 'Family and Religious Studies', 'A_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006080', '6080', 'Sports Science and Technology', 'A_LEVEL', 'SCIENCE', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, true);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006081', '6081', 'Heritage Studies', 'A_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('9f4170b0-43e9-4ef8-bce0-54c83f4b0101', '6082', 'Mathematics', 'A_LEVEL', 'MATHEMATICS', true, NULL, NULL, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 1, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006083', '6083', 'Hospitality Management and Design', 'A_LEVEL', 'TECHNICAL', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);
INSERT INTO public.admission_subjects (id, code, name, level, subject_group_code, is_active, legacy_olevel_subject_code, legacy_subject_code, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, is_science_subject) VALUES ('8a000000-0000-4000-9000-000000006085', '6085', 'Guidance and Counselling and Life Skills Education', 'A_LEVEL', 'HUMANITIES', true, NULL, NULL, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0, false);


--
-- Data for Name: admission_subjects_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: applicant_employment_histories; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: applicant_employment_histories_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: applicant_next_of_kin; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: applicant_next_of_kin_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: applicant_qualification_results; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: applicant_qualification_results_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: applicant_qualification_sittings; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: applicant_qualification_sittings_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: applicant_referee_invitations; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: applicant_referee_invitations_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: applicant_referees; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: applicant_referees_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: applicants; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: applicants_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_accommodation_requests; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_accommodation_requests_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_clearances; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_clearances_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_document_requirement_snapshots; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_document_requirement_snapshots_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_documents; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_documents_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_evaluations; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_evaluations_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_exam_arrangements; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_exam_arrangements_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_fees; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_fees_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_payment_references; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_payment_references_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_prior_uz_declarations; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_prior_uz_declarations_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_professional_achievements; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_professional_achievements_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_programme_choices; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_programme_choices_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_programme_entry_option_selections; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_programme_entry_option_selections_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_programme_option_snapshots; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_programme_option_snapshots_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_referee_nominations; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_referee_nominations_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_sections; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_sections_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_status_events; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_status_events_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_type_document_requirements; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_type_document_requirements_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_type_programme_mappings; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_type_programme_mappings_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_type_sections; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('46fba233-1943-40d2-9eb0-6d511897ef9a', 'c51f1b3c-3ea6-4106-98df-3089893715d6', 'NEXT_OF_KIN', 'Next of kin', true, true, 1, 20, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('2b464640-a692-44aa-9421-c29890309e36', 'c51f1b3c-3ea6-4106-98df-3089893715d6', 'QUALIFICATIONS', 'Qualifications', true, true, 1, 30, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('b345116c-a628-4e6f-af7a-a78858af5c6c', 'c51f1b3c-3ea6-4106-98df-3089893715d6', 'PROGRAMME_CHOICES', 'Programme choices', true, true, 1, 60, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('84918fae-0481-49fa-9bf0-33c5d74a7898', 'c51f1b3c-3ea6-4106-98df-3089893715d6', 'DOCUMENTS', 'Supporting documents', true, true, 0, 70, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('87d55661-9928-442e-86b6-8b197e67e6fa', 'c51f1b3c-3ea6-4106-98df-3089893715d6', 'PAYMENT', 'Application fee', true, false, 0, 80, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('1294ee9d-8240-4897-809d-2eacb1ed76bc', 'c51f1b3c-3ea6-4106-98df-3089893715d6', 'REVIEW_DECLARATION', 'Review and declaration', true, false, 0, 90, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('f284557f-dd30-48c9-abee-1b71d4faa016', 'd98e35ac-4121-479c-aa45-e2d479d76c6b', 'NEXT_OF_KIN', 'Next of kin', true, true, 1, 20, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('9a1a19bd-ea94-49a3-b905-5ec78296dc85', 'd98e35ac-4121-479c-aa45-e2d479d76c6b', 'QUALIFICATIONS', 'Qualifications', true, true, 1, 30, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('5526500a-b43c-4abc-9e43-9e96d60281d1', 'd98e35ac-4121-479c-aa45-e2d479d76c6b', 'EMPLOYMENT_HISTORY', 'Employment history', true, true, 1, 40, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('fd25612c-ffd9-4a4d-8198-78609de13834', 'd98e35ac-4121-479c-aa45-e2d479d76c6b', 'REFEREES', 'Confidential references', true, true, 2, 50, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('346654c4-ce2e-497e-979b-20ba85561753', 'd98e35ac-4121-479c-aa45-e2d479d76c6b', 'PROGRAMME_CHOICES', 'Programme choices', true, true, 1, 60, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('117516ad-2551-4ef5-98c5-5b430318f950', 'd98e35ac-4121-479c-aa45-e2d479d76c6b', 'DOCUMENTS', 'Supporting documents', true, true, 0, 70, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('011b1b7e-2fe6-4016-b855-fdedd7698894', 'd98e35ac-4121-479c-aa45-e2d479d76c6b', 'PAYMENT', 'Application fee', true, false, 0, 80, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('f01c7439-eb21-4cb5-ba75-97e3eda300df', 'd98e35ac-4121-479c-aa45-e2d479d76c6b', 'REVIEW_DECLARATION', 'Review and declaration', true, false, 0, 90, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('398fd57f-ee32-45f4-99a0-30b8ed75b66b', '0224e06d-252f-4a9d-9f41-c62e3c7233b1', 'NEXT_OF_KIN', 'Next of kin', true, true, 1, 20, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('c2b80e7b-a685-46a9-8fdb-3ef329ec20b3', '0224e06d-252f-4a9d-9f41-c62e3c7233b1', 'QUALIFICATIONS', 'Qualifications', true, true, 1, 30, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('1b55ff6e-716b-47c2-9046-f50a8290867c', '0224e06d-252f-4a9d-9f41-c62e3c7233b1', 'PRIOR_UZ_STUDY', 'Prior UZ study', true, false, 1, 35, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('54a4bc7c-6f14-4995-9a40-e3b20eab87f7', '0224e06d-252f-4a9d-9f41-c62e3c7233b1', 'PROFESSIONAL_ACHIEVEMENTS', 'Professional achievements', true, true, 1, 38, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('2cf36b48-ad43-40a3-b0cc-a2451c24feb2', '0224e06d-252f-4a9d-9f41-c62e3c7233b1', 'EMPLOYMENT_HISTORY', 'Employment history', true, true, 1, 40, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('b69cc0ad-6f3f-49e3-bd61-cb463ce9e0d0', '0224e06d-252f-4a9d-9f41-c62e3c7233b1', 'REFEREES', 'Confidential references', true, true, 3, 50, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('44e46665-8be5-466d-8631-47f3d3a2844c', '0224e06d-252f-4a9d-9f41-c62e3c7233b1', 'PROGRAMME_CHOICES', 'Programme choices', true, true, 1, 60, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('df7e16fa-e0dc-4680-84c0-009c35841429', '0224e06d-252f-4a9d-9f41-c62e3c7233b1', 'DOCUMENTS', 'Supporting documents', true, true, 0, 70, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('551f21d9-ce70-43fc-a43c-8d26aaae0039', '0224e06d-252f-4a9d-9f41-c62e3c7233b1', 'PAYMENT', 'Application fee', true, false, 0, 80, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('aaa85dec-82f0-4659-b298-05d2e66fbcf0', '0224e06d-252f-4a9d-9f41-c62e3c7233b1', 'REVIEW_DECLARATION', 'Review and declaration', true, false, 0, 90, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('4dd668ec-9794-4859-bf94-18cc1e615c5f', '7980214d-84f0-4c0a-a2aa-74b6451636a2', 'NEXT_OF_KIN', 'Next of kin', true, true, 1, 20, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('713d29e1-273a-4b88-acce-cb4720086e80', '7980214d-84f0-4c0a-a2aa-74b6451636a2', 'QUALIFICATIONS', 'Qualifications', true, true, 1, 30, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('a2ebe74a-fcde-4ee7-95d4-75d295658270', '7980214d-84f0-4c0a-a2aa-74b6451636a2', 'EMPLOYMENT_HISTORY', 'Employment history', true, true, 1, 40, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('86a4bfd2-654f-4b13-9556-12499055a6a4', '7980214d-84f0-4c0a-a2aa-74b6451636a2', 'REFEREES', 'Confidential references', true, true, 3, 50, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('48880660-9012-4d86-9404-2f31c5a72498', '7980214d-84f0-4c0a-a2aa-74b6451636a2', 'PROGRAMME_CHOICES', 'Programme choices', true, true, 1, 60, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('3e6149fd-7298-4535-9a05-b7f8d88cec21', '7980214d-84f0-4c0a-a2aa-74b6451636a2', 'DOCUMENTS', 'Supporting documents', true, true, 0, 70, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('0691c7ef-ea11-401d-b8ee-0c2cb34fa1e7', '7980214d-84f0-4c0a-a2aa-74b6451636a2', 'PAYMENT', 'Application fee', true, false, 0, 80, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('b595772f-b617-4593-bfc5-4c46849c94cc', '7980214d-84f0-4c0a-a2aa-74b6451636a2', 'REVIEW_DECLARATION', 'Review and declaration', true, false, 0, 90, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.20533+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('b849c3fd-bff2-4ab3-ab89-f03065f8607c', 'c51f1b3c-3ea6-4106-98df-3089893715d6', 'PERSONAL_DETAILS', 'Applicant details', true, false, 0, 10, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.223422+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('c608f453-a74a-42bd-8a5d-03dd36d9bc2f', 'd98e35ac-4121-479c-aa45-e2d479d76c6b', 'PERSONAL_DETAILS', 'Applicant details', true, false, 0, 10, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.223422+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('0e74be4b-c359-47ca-be65-af718bbcb725', '0224e06d-252f-4a9d-9f41-c62e3c7233b1', 'PERSONAL_DETAILS', 'Applicant details', true, false, 0, 10, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.223422+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.application_type_sections (id, application_type_id, section_code, section_name, is_required, is_repeatable, minimum_records, sort_order, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('2c9fb55e-e761-4477-8497-6e7802d911ba', '7980214d-84f0-4c0a-a2aa-74b6451636a2', 'PERSONAL_DETAILS', 'Applicant details', true, false, 0, 10, true, '2026-08-12 18:58:27.20533+00', '2026-08-12 18:58:27.223422+00', NULL, NULL, NULL, NULL, 0);


--
-- Data for Name: application_type_sections_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_types; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.application_types (id, code, name, requires_employment_history, requires_referees, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, finance_fee_structure_id, finance_fee_structure_code, finance_fee_structure_name, fee_policy_status, fee_free_reason, fee_policy_decided_by_user_id, fee_policy_decided_at) VALUES ('0224e06d-252f-4a9d-9f41-c62e3c7233b1', 'MBA', 'Master of Business Administration', true, true, false, '2026-08-12 18:58:27.140552+00', '2026-08-12 18:58:27.140552+00', NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, 'UNCONFIGURED', NULL, NULL, NULL);
INSERT INTO public.application_types (id, code, name, requires_employment_history, requires_referees, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, finance_fee_structure_id, finance_fee_structure_code, finance_fee_structure_name, fee_policy_status, fee_free_reason, fee_policy_decided_by_user_id, fee_policy_decided_at) VALUES ('c51f1b3c-3ea6-4106-98df-3089893715d6', 'UNDERGRAD', 'Undergraduate and Diploma', false, false, false, '2026-08-12 18:58:27.140552+00', '2026-08-12 18:58:27.140552+00', NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, 'UNCONFIGURED', NULL, NULL, NULL);
INSERT INTO public.application_types (id, code, name, requires_employment_history, requires_referees, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, finance_fee_structure_id, finance_fee_structure_code, finance_fee_structure_name, fee_policy_status, fee_free_reason, fee_policy_decided_by_user_id, fee_policy_decided_at) VALUES ('7980214d-84f0-4c0a-a2aa-74b6451636a2', 'EDUCATION', 'Education', true, true, false, '2026-08-12 18:58:27.140552+00', '2026-08-12 18:58:27.140552+00', NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, 'UNCONFIGURED', NULL, NULL, NULL);
INSERT INTO public.application_types (id, code, name, requires_employment_history, requires_referees, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version, finance_fee_structure_id, finance_fee_structure_code, finance_fee_structure_name, fee_policy_status, fee_free_reason, fee_policy_decided_by_user_id, fee_policy_decided_at) VALUES ('d98e35ac-4121-479c-aa45-e2d479d76c6b', 'POSTGRAD', 'Postgraduate', true, true, false, '2026-08-12 18:58:27.140552+00', '2026-08-12 18:58:27.140552+00', NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, 'UNCONFIGURED', NULL, NULL, NULL);


--
-- Data for Name: application_types_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: applications; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: applications_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exam_bodies; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.exam_bodies (id, code, name, country_id, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('4d874a40-4f3c-4bc7-9ee1-290837c7e708', 'ZIMSEC', 'Zimbabwe School Examinations Council', NULL, true, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.90875+00', NULL, NULL, NULL, NULL, 0);


--
-- Data for Name: exam_bodies_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: grading_scale_values; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.grading_scale_values (id, grading_scale_id, grade, points, is_pass, sort_order, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('f18075bf-8657-4abc-95e0-1f48e65d5a01', 'b67b4ba0-0c04-41ad-a168-83d1b2f5c5a1', 'A', 5.00, true, 1, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.90875+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.grading_scale_values (id, grading_scale_id, grade, points, is_pass, sort_order, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('f18075bf-8657-4abc-95e0-1f48e65d5a02', 'b67b4ba0-0c04-41ad-a168-83d1b2f5c5a1', 'B', 4.00, true, 2, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.90875+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.grading_scale_values (id, grading_scale_id, grade, points, is_pass, sort_order, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('f18075bf-8657-4abc-95e0-1f48e65d5a03', 'b67b4ba0-0c04-41ad-a168-83d1b2f5c5a1', 'C', 3.00, true, 3, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.90875+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.grading_scale_values (id, grading_scale_id, grade, points, is_pass, sort_order, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('f18075bf-8657-4abc-95e0-1f48e65d5a04', 'b67b4ba0-0c04-41ad-a168-83d1b2f5c5a1', 'D', 2.00, true, 4, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.90875+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.grading_scale_values (id, grading_scale_id, grade, points, is_pass, sort_order, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('f18075bf-8657-4abc-95e0-1f48e65d5a05', 'b67b4ba0-0c04-41ad-a168-83d1b2f5c5a1', 'E', 1.00, true, 5, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.90875+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.grading_scale_values (id, grading_scale_id, grade, points, is_pass, sort_order, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('f18075bf-8657-4abc-95e0-1f48e65d5b01', 'b67b4ba0-0c04-41ad-a168-83d1b2f5c5b2', 'A', NULL, true, 1, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.90875+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.grading_scale_values (id, grading_scale_id, grade, points, is_pass, sort_order, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('f18075bf-8657-4abc-95e0-1f48e65d5b02', 'b67b4ba0-0c04-41ad-a168-83d1b2f5c5b2', 'B', NULL, true, 2, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.90875+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.grading_scale_values (id, grading_scale_id, grade, points, is_pass, sort_order, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('f18075bf-8657-4abc-95e0-1f48e65d5b03', 'b67b4ba0-0c04-41ad-a168-83d1b2f5c5b2', 'C', NULL, true, 3, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.90875+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.grading_scale_values (id, grading_scale_id, grade, points, is_pass, sort_order, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('f18075bf-8657-4abc-95e0-1f48e65d5a06', 'b67b4ba0-0c04-41ad-a168-83d1b2f5c5a1', 'O', NULL, false, 6, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.grading_scale_values (id, grading_scale_id, grade, points, is_pass, sort_order, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('f18075bf-8657-4abc-95e0-1f48e65d5a07', 'b67b4ba0-0c04-41ad-a168-83d1b2f5c5a1', 'F', NULL, false, 7, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.grading_scale_values (id, grading_scale_id, grade, points, is_pass, sort_order, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('f18075bf-8657-4abc-95e0-1f48e65d5b04', 'b67b4ba0-0c04-41ad-a168-83d1b2f5c5b2', 'D', NULL, false, 4, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.grading_scale_values (id, grading_scale_id, grade, points, is_pass, sort_order, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('f18075bf-8657-4abc-95e0-1f48e65d5b05', 'b67b4ba0-0c04-41ad-a168-83d1b2f5c5b2', 'E', NULL, false, 5, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.grading_scale_values (id, grading_scale_id, grade, points, is_pass, sort_order, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('f18075bf-8657-4abc-95e0-1f48e65d5b06', 'b67b4ba0-0c04-41ad-a168-83d1b2f5c5b2', 'U', NULL, false, 6, '2026-08-12 18:58:26.941427+00', '2026-08-12 18:58:26.941427+00', NULL, NULL, NULL, NULL, 0);


--
-- Data for Name: grading_scale_values_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: grading_scales; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.grading_scales (id, code, name, level, effective_from, effective_to, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('b67b4ba0-0c04-41ad-a168-83d1b2f5c5a1', 'ZIMSEC-A', 'ZIMSEC A Level', 'A_LEVEL', '1980-01-01', NULL, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.90875+00', NULL, NULL, NULL, NULL, 0);
INSERT INTO public.grading_scales (id, code, name, level, effective_from, effective_to, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version) VALUES ('b67b4ba0-0c04-41ad-a168-83d1b2f5c5b2', 'ZIMSEC-O', 'ZIMSEC O Level', 'O_LEVEL', '1980-01-01', NULL, '2026-08-12 18:58:26.90875+00', '2026-08-12 18:58:26.90875+00', NULL, NULL, NULL, NULL, 0);


--
-- Data for Name: grading_scales_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: integration_inbox; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: integration_outbox; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: offer_batches; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: offer_batches_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: offer_conditions; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: offer_conditions_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: offer_dispatches; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: offer_dispatches_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: offer_document_versions; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: offer_document_versions_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: offer_publications; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: offer_publications_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: offer_responses; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: offer_responses_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: offer_status_events; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: offer_status_events_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: offers; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: offers_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: programme_choice_decisions; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: programme_choice_decisions_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: revinfo; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: selection_decisions; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: selection_decisions_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: selection_rounds; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: selection_rounds_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Name: applicant_number_sequence; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.applicant_number_sequence', 1, false);


--
-- Name: application_number_sequence; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.application_number_sequence', 1, false);


--
-- Name: offer_number_sequence; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.offer_number_sequence', 1, false);


--
-- Name: revinfo_rev_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.revinfo_rev_seq', 1, false);


--
-- Name: revinfo_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.revinfo_seq', 1, false);


--
-- Name: academic_recommendations_aud academic_recommendations_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_recommendations_aud
    ADD CONSTRAINT academic_recommendations_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: academic_recommendations academic_recommendations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_recommendations
    ADD CONSTRAINT academic_recommendations_pkey PRIMARY KEY (id);


--
-- Name: academic_review_assignments_aud academic_review_assignments_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_review_assignments_aud
    ADD CONSTRAINT academic_review_assignments_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: academic_review_assignments academic_review_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_review_assignments
    ADD CONSTRAINT academic_review_assignments_pkey PRIMARY KEY (id);


--
-- Name: academic_reviews_aud academic_reviews_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_reviews_aud
    ADD CONSTRAINT academic_reviews_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: academic_reviews academic_reviews_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_reviews
    ADD CONSTRAINT academic_reviews_pkey PRIMARY KEY (id);


--
-- Name: academic_unit_recommendations_aud academic_unit_recommendations_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_unit_recommendations_aud
    ADD CONSTRAINT academic_unit_recommendations_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: academic_unit_recommendations academic_unit_recommendations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_unit_recommendations
    ADD CONSTRAINT academic_unit_recommendations_pkey PRIMARY KEY (id);


--
-- Name: admission_cycle_archive_summaries_aud admission_cycle_archive_summaries_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_cycle_archive_summaries_aud
    ADD CONSTRAINT admission_cycle_archive_summaries_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: admission_cycle_archive_summaries admission_cycle_archive_summaries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_cycle_archive_summaries
    ADD CONSTRAINT admission_cycle_archive_summaries_pkey PRIMARY KEY (id);


--
-- Name: admission_cycles_aud admission_cycles_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_cycles_aud
    ADD CONSTRAINT admission_cycles_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: admission_cycles admission_cycles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_cycles
    ADD CONSTRAINT admission_cycles_pkey PRIMARY KEY (id);


--
-- Name: admission_qualification_requirement_groups_aud admission_qualification_requirement_groups_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_qualification_requirement_groups_aud
    ADD CONSTRAINT admission_qualification_requirement_groups_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: admission_qualification_requirement_groups admission_qualification_requirement_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_qualification_requirement_groups
    ADD CONSTRAINT admission_qualification_requirement_groups_pkey PRIMARY KEY (id);


--
-- Name: admission_qualification_requirement_items_aud admission_qualification_requirement_items_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_qualification_requirement_items_aud
    ADD CONSTRAINT admission_qualification_requirement_items_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: admission_qualification_requirement_items admission_qualification_requirement_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_qualification_requirement_items
    ADD CONSTRAINT admission_qualification_requirement_items_pkey PRIMARY KEY (id);


--
-- Name: admission_quotas_aud admission_quotas_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_quotas_aud
    ADD CONSTRAINT admission_quotas_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: admission_quotas admission_quotas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_quotas
    ADD CONSTRAINT admission_quotas_pkey PRIMARY KEY (id);


--
-- Name: admission_requirement_sets_aud admission_requirement_sets_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_requirement_sets_aud
    ADD CONSTRAINT admission_requirement_sets_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: admission_requirement_sets admission_requirement_sets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_requirement_sets
    ADD CONSTRAINT admission_requirement_sets_pkey PRIMARY KEY (id);


--
-- Name: admission_subject_requirements_aud admission_subject_requirements_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_subject_requirements_aud
    ADD CONSTRAINT admission_subject_requirements_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: admission_subject_requirements admission_subject_requirements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_subject_requirements
    ADD CONSTRAINT admission_subject_requirements_pkey PRIMARY KEY (id);


--
-- Name: admission_subjects_aud admission_subjects_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_subjects_aud
    ADD CONSTRAINT admission_subjects_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: admission_subjects admission_subjects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_subjects
    ADD CONSTRAINT admission_subjects_pkey PRIMARY KEY (id);


--
-- Name: applicant_employment_histories_aud applicant_employment_histories_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_employment_histories_aud
    ADD CONSTRAINT applicant_employment_histories_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: applicant_employment_histories applicant_employment_histories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_employment_histories
    ADD CONSTRAINT applicant_employment_histories_pkey PRIMARY KEY (id);


--
-- Name: applicant_next_of_kin_aud applicant_next_of_kin_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_next_of_kin_aud
    ADD CONSTRAINT applicant_next_of_kin_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: applicant_next_of_kin applicant_next_of_kin_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_next_of_kin
    ADD CONSTRAINT applicant_next_of_kin_pkey PRIMARY KEY (id);


--
-- Name: applicant_qualification_results_aud applicant_qualification_results_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_qualification_results_aud
    ADD CONSTRAINT applicant_qualification_results_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: applicant_qualification_results applicant_qualification_results_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_qualification_results
    ADD CONSTRAINT applicant_qualification_results_pkey PRIMARY KEY (id);


--
-- Name: applicant_qualification_sittings_aud applicant_qualification_sittings_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_qualification_sittings_aud
    ADD CONSTRAINT applicant_qualification_sittings_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: applicant_qualification_sittings applicant_qualification_sittings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_qualification_sittings
    ADD CONSTRAINT applicant_qualification_sittings_pkey PRIMARY KEY (id);


--
-- Name: applicant_referee_invitations_aud applicant_referee_invitations_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_referee_invitations_aud
    ADD CONSTRAINT applicant_referee_invitations_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: applicant_referee_invitations applicant_referee_invitations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_referee_invitations
    ADD CONSTRAINT applicant_referee_invitations_pkey PRIMARY KEY (id);


--
-- Name: applicant_referees_aud applicant_referees_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_referees_aud
    ADD CONSTRAINT applicant_referees_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: applicant_referees applicant_referees_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_referees
    ADD CONSTRAINT applicant_referees_pkey PRIMARY KEY (id);


--
-- Name: applicants_aud applicants_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicants_aud
    ADD CONSTRAINT applicants_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: applicants applicants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicants
    ADD CONSTRAINT applicants_pkey PRIMARY KEY (id);


--
-- Name: application_accommodation_requests_aud application_accommodation_requests_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_accommodation_requests_aud
    ADD CONSTRAINT application_accommodation_requests_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_accommodation_requests application_accommodation_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_accommodation_requests
    ADD CONSTRAINT application_accommodation_requests_pkey PRIMARY KEY (id);


--
-- Name: application_clearances_aud application_clearances_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_clearances_aud
    ADD CONSTRAINT application_clearances_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_clearances application_clearances_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_clearances
    ADD CONSTRAINT application_clearances_pkey PRIMARY KEY (id);


--
-- Name: application_document_requirement_snapshots_aud application_document_requirement_snapshots_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_document_requirement_snapshots_aud
    ADD CONSTRAINT application_document_requirement_snapshots_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_document_requirement_snapshots application_document_requirement_snapshots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_document_requirement_snapshots
    ADD CONSTRAINT application_document_requirement_snapshots_pkey PRIMARY KEY (id);


--
-- Name: application_documents_aud application_documents_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_documents_aud
    ADD CONSTRAINT application_documents_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_documents application_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_documents
    ADD CONSTRAINT application_documents_pkey PRIMARY KEY (id);


--
-- Name: application_evaluations_aud application_evaluations_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_evaluations_aud
    ADD CONSTRAINT application_evaluations_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_evaluations application_evaluations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_evaluations
    ADD CONSTRAINT application_evaluations_pkey PRIMARY KEY (id);


--
-- Name: application_exam_arrangements_aud application_exam_arrangements_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_exam_arrangements_aud
    ADD CONSTRAINT application_exam_arrangements_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_exam_arrangements application_exam_arrangements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_exam_arrangements
    ADD CONSTRAINT application_exam_arrangements_pkey PRIMARY KEY (id);


--
-- Name: application_fees_aud application_fees_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_fees_aud
    ADD CONSTRAINT application_fees_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_fees application_fees_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_fees
    ADD CONSTRAINT application_fees_pkey PRIMARY KEY (id);


--
-- Name: application_payment_references_aud application_payment_references_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_payment_references_aud
    ADD CONSTRAINT application_payment_references_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_payment_references application_payment_references_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_payment_references
    ADD CONSTRAINT application_payment_references_pkey PRIMARY KEY (id);


--
-- Name: application_prior_uz_declarations application_prior_uz_declarations_application_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_prior_uz_declarations
    ADD CONSTRAINT application_prior_uz_declarations_application_id_key UNIQUE (application_id);


--
-- Name: application_prior_uz_declarations_aud application_prior_uz_declarations_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_prior_uz_declarations_aud
    ADD CONSTRAINT application_prior_uz_declarations_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_prior_uz_declarations application_prior_uz_declarations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_prior_uz_declarations
    ADD CONSTRAINT application_prior_uz_declarations_pkey PRIMARY KEY (id);


--
-- Name: application_professional_achievements_aud application_professional_achievements_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_professional_achievements_aud
    ADD CONSTRAINT application_professional_achievements_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_professional_achievements application_professional_achievements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_professional_achievements
    ADD CONSTRAINT application_professional_achievements_pkey PRIMARY KEY (id);


--
-- Name: application_programme_choices_aud application_programme_choices_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_programme_choices_aud
    ADD CONSTRAINT application_programme_choices_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_programme_choices application_programme_choices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_programme_choices
    ADD CONSTRAINT application_programme_choices_pkey PRIMARY KEY (id);


--
-- Name: application_programme_entry_option_selections_aud application_programme_entry_option_selections_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_programme_entry_option_selections_aud
    ADD CONSTRAINT application_programme_entry_option_selections_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_programme_entry_option_selections application_programme_entry_option_selections_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_programme_entry_option_selections
    ADD CONSTRAINT application_programme_entry_option_selections_pkey PRIMARY KEY (id);


--
-- Name: application_programme_option_snapshots_aud application_programme_option_snapshots_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_programme_option_snapshots_aud
    ADD CONSTRAINT application_programme_option_snapshots_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_programme_option_snapshots application_programme_option_snapshots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_programme_option_snapshots
    ADD CONSTRAINT application_programme_option_snapshots_pkey PRIMARY KEY (id);


--
-- Name: application_referee_nominations_aud application_referee_nominations_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_referee_nominations_aud
    ADD CONSTRAINT application_referee_nominations_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_referee_nominations application_referee_nominations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_referee_nominations
    ADD CONSTRAINT application_referee_nominations_pkey PRIMARY KEY (id);


--
-- Name: application_sections_aud application_sections_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_sections_aud
    ADD CONSTRAINT application_sections_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_sections application_sections_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_sections
    ADD CONSTRAINT application_sections_pkey PRIMARY KEY (id);


--
-- Name: application_status_events_aud application_status_events_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_status_events_aud
    ADD CONSTRAINT application_status_events_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_status_events application_status_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_status_events
    ADD CONSTRAINT application_status_events_pkey PRIMARY KEY (id);


--
-- Name: application_type_document_requirements_aud application_type_document_requirements_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_type_document_requirements_aud
    ADD CONSTRAINT application_type_document_requirements_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_type_document_requirements application_type_document_requirements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_type_document_requirements
    ADD CONSTRAINT application_type_document_requirements_pkey PRIMARY KEY (id);


--
-- Name: application_type_programme_mappings_aud application_type_programme_mappings_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_type_programme_mappings_aud
    ADD CONSTRAINT application_type_programme_mappings_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_type_programme_mappings application_type_programme_mappings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_type_programme_mappings
    ADD CONSTRAINT application_type_programme_mappings_pkey PRIMARY KEY (id);


--
-- Name: application_type_sections_aud application_type_sections_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_type_sections_aud
    ADD CONSTRAINT application_type_sections_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_type_sections application_type_sections_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_type_sections
    ADD CONSTRAINT application_type_sections_pkey PRIMARY KEY (id);


--
-- Name: application_types_aud application_types_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_types_aud
    ADD CONSTRAINT application_types_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_types application_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_types
    ADD CONSTRAINT application_types_pkey PRIMARY KEY (id);


--
-- Name: applications_aud applications_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applications_aud
    ADD CONSTRAINT applications_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: applications applications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applications
    ADD CONSTRAINT applications_pkey PRIMARY KEY (id);


--
-- Name: application_fees ex_application_fees_non_overlapping_effectivity; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_fees
    ADD CONSTRAINT ex_application_fees_non_overlapping_effectivity EXCLUDE USING gist (application_type_id WITH =, applicant_category_code WITH =, daterange(effective_from, COALESCE(effective_to, 'infinity'::date), '[]'::text) WITH &&) WHERE ((is_active AND (deleted_at IS NULL)));


--
-- Name: grading_scales ex_grading_scales_non_overlapping_effectivity; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grading_scales
    ADD CONSTRAINT ex_grading_scales_non_overlapping_effectivity EXCLUDE USING gist (level WITH =, daterange(effective_from, COALESCE(effective_to, 'infinity'::date), '[]'::text) WITH &&) WHERE ((deleted_at IS NULL));


--
-- Name: admission_requirement_sets ex_requirement_sets_non_overlapping_approved_effectivity; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_requirement_sets
    ADD CONSTRAINT ex_requirement_sets_non_overlapping_approved_effectivity EXCLUDE USING gist (programme_id WITH =, application_type_id WITH =, COALESCE(intake_id, '00000000-0000-0000-0000-000000000000'::uuid) WITH =, daterange(effective_from, COALESCE(effective_to, 'infinity'::date), '[]'::text) WITH &&) WHERE ((((status)::text = 'APPROVED'::text) AND (deleted_at IS NULL)));


--
-- Name: exam_bodies_aud exam_bodies_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_bodies_aud
    ADD CONSTRAINT exam_bodies_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: exam_bodies exam_bodies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_bodies
    ADD CONSTRAINT exam_bodies_pkey PRIMARY KEY (id);


--
-- Name: grading_scale_values_aud grading_scale_values_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grading_scale_values_aud
    ADD CONSTRAINT grading_scale_values_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: grading_scale_values grading_scale_values_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grading_scale_values
    ADD CONSTRAINT grading_scale_values_pkey PRIMARY KEY (id);


--
-- Name: grading_scales_aud grading_scales_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grading_scales_aud
    ADD CONSTRAINT grading_scales_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: grading_scales grading_scales_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grading_scales
    ADD CONSTRAINT grading_scales_pkey PRIMARY KEY (id);


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
-- Name: offer_batches_aud offer_batches_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_batches_aud
    ADD CONSTRAINT offer_batches_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: offer_batches offer_batches_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_batches
    ADD CONSTRAINT offer_batches_pkey PRIMARY KEY (id);


--
-- Name: offer_conditions_aud offer_conditions_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_conditions_aud
    ADD CONSTRAINT offer_conditions_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: offer_conditions offer_conditions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_conditions
    ADD CONSTRAINT offer_conditions_pkey PRIMARY KEY (id);


--
-- Name: offer_dispatches_aud offer_dispatches_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_dispatches_aud
    ADD CONSTRAINT offer_dispatches_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: offer_dispatches offer_dispatches_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_dispatches
    ADD CONSTRAINT offer_dispatches_pkey PRIMARY KEY (id);


--
-- Name: offer_document_versions_aud offer_document_versions_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_document_versions_aud
    ADD CONSTRAINT offer_document_versions_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: offer_document_versions offer_document_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_document_versions
    ADD CONSTRAINT offer_document_versions_pkey PRIMARY KEY (id);


--
-- Name: offer_publications_aud offer_publications_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_publications_aud
    ADD CONSTRAINT offer_publications_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: offer_publications offer_publications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_publications
    ADD CONSTRAINT offer_publications_pkey PRIMARY KEY (id);


--
-- Name: offer_responses_aud offer_responses_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_responses_aud
    ADD CONSTRAINT offer_responses_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: offer_responses offer_responses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_responses
    ADD CONSTRAINT offer_responses_pkey PRIMARY KEY (id);


--
-- Name: offer_status_events_aud offer_status_events_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_status_events_aud
    ADD CONSTRAINT offer_status_events_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: offer_status_events offer_status_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_status_events
    ADD CONSTRAINT offer_status_events_pkey PRIMARY KEY (id);


--
-- Name: offers_aud offers_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers_aud
    ADD CONSTRAINT offers_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: offers offers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT offers_pkey PRIMARY KEY (id);


--
-- Name: programme_choice_decisions_aud programme_choice_decisions_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_choice_decisions_aud
    ADD CONSTRAINT programme_choice_decisions_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: programme_choice_decisions programme_choice_decisions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_choice_decisions
    ADD CONSTRAINT programme_choice_decisions_pkey PRIMARY KEY (id);


--
-- Name: revinfo revinfo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revinfo
    ADD CONSTRAINT revinfo_pkey PRIMARY KEY (rev);


--
-- Name: selection_decisions_aud selection_decisions_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.selection_decisions_aud
    ADD CONSTRAINT selection_decisions_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: selection_decisions selection_decisions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.selection_decisions
    ADD CONSTRAINT selection_decisions_pkey PRIMARY KEY (id);


--
-- Name: selection_rounds_aud selection_rounds_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.selection_rounds_aud
    ADD CONSTRAINT selection_rounds_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: selection_rounds selection_rounds_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.selection_rounds
    ADD CONSTRAINT selection_rounds_pkey PRIMARY KEY (id);


--
-- Name: academic_recommendations uk_academic_recommendation_sequence; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_recommendations
    ADD CONSTRAINT uk_academic_recommendation_sequence UNIQUE (academic_review_id, recommendation_sequence);


--
-- Name: academic_reviews uk_academic_review_application_choice; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_reviews
    ADD CONSTRAINT uk_academic_review_application_choice UNIQUE (application_id, programme_choice_id);


--
-- Name: academic_review_assignments uk_academic_review_round_choice_attempt; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_review_assignments
    ADD CONSTRAINT uk_academic_review_round_choice_attempt UNIQUE (selection_round_id, programme_choice_id, release_attempt);


--
-- Name: academic_unit_recommendations uk_academic_unit_recommendation_sequence; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_unit_recommendations
    ADD CONSTRAINT uk_academic_unit_recommendation_sequence UNIQUE (academic_review_assignment_id, recommendation_sequence);


--
-- Name: admission_cycle_archive_summaries uk_admission_cycle_archive_summaries_cycle; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_cycle_archive_summaries
    ADD CONSTRAINT uk_admission_cycle_archive_summaries_cycle UNIQUE (admission_cycle_id);


--
-- Name: admission_cycles uk_admission_cycles_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_cycles
    ADD CONSTRAINT uk_admission_cycles_code UNIQUE (code);


--
-- Name: admission_subjects uk_admission_subjects_level_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_subjects
    ADD CONSTRAINT uk_admission_subjects_level_code UNIQUE (level, code);


--
-- Name: applicants uk_applicants_applicant_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicants
    ADD CONSTRAINT uk_applicants_applicant_number UNIQUE (applicant_number);


--
-- Name: applicants uk_applicants_user_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicants
    ADD CONSTRAINT uk_applicants_user_id UNIQUE (user_id);


--
-- Name: application_accommodation_requests uk_application_accommodation_requests_application; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_accommodation_requests
    ADD CONSTRAINT uk_application_accommodation_requests_application UNIQUE (application_id);


--
-- Name: application_document_requirement_snapshots uk_application_document_requirement_snapshot; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_document_requirement_snapshots
    ADD CONSTRAINT uk_application_document_requirement_snapshot UNIQUE (application_id, requirement_code);


--
-- Name: application_documents uk_application_documents_requirement; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_documents
    ADD CONSTRAINT uk_application_documents_requirement UNIQUE (application_id, requirement_code, document_id);


--
-- Name: application_evaluations uk_application_evaluations_choice_requirement; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_evaluations
    ADD CONSTRAINT uk_application_evaluations_choice_requirement UNIQUE (programme_choice_id, requirement_set_id);


--
-- Name: application_payment_references uk_application_payment_references_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_payment_references
    ADD CONSTRAINT uk_application_payment_references_reference UNIQUE (reference);


--
-- Name: application_programme_option_snapshots uk_application_programme_option_snapshot; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_programme_option_snapshots
    ADD CONSTRAINT uk_application_programme_option_snapshot UNIQUE (application_id, programme_id);


--
-- Name: application_referee_nominations uk_application_referee_nomination_referee; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_referee_nominations
    ADD CONSTRAINT uk_application_referee_nomination_referee UNIQUE (application_id, referee_id);


--
-- Name: application_sections uk_application_sections_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_sections
    ADD CONSTRAINT uk_application_sections_code UNIQUE (application_id, section_code);


--
-- Name: application_type_document_requirements uk_application_type_document_requirement; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_type_document_requirements
    ADD CONSTRAINT uk_application_type_document_requirement UNIQUE (application_type_id, requirement_code);


--
-- Name: application_type_programme_mappings uk_application_type_programme_mapping; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_type_programme_mappings
    ADD CONSTRAINT uk_application_type_programme_mapping UNIQUE (application_type_id, programme_id);


--
-- Name: application_type_sections uk_application_type_sections_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_type_sections
    ADD CONSTRAINT uk_application_type_sections_code UNIQUE (application_type_id, section_code);


--
-- Name: application_types uk_application_types_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_types
    ADD CONSTRAINT uk_application_types_code UNIQUE (code);


--
-- Name: applications uk_applications_application_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applications
    ADD CONSTRAINT uk_applications_application_number UNIQUE (application_number);


--
-- Name: exam_bodies uk_exam_bodies_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_bodies
    ADD CONSTRAINT uk_exam_bodies_code UNIQUE (code);


--
-- Name: grading_scale_values uk_grading_scale_values_scale_grade; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grading_scale_values
    ADD CONSTRAINT uk_grading_scale_values_scale_grade UNIQUE (grading_scale_id, grade);


--
-- Name: grading_scales uk_grading_scales_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grading_scales
    ADD CONSTRAINT uk_grading_scales_code UNIQUE (code);


--
-- Name: offer_batches uk_offer_batches_cycle_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_batches
    ADD CONSTRAINT uk_offer_batches_cycle_code UNIQUE (admission_cycle_id, code);


--
-- Name: offer_conditions uk_offer_conditions_offer_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_conditions
    ADD CONSTRAINT uk_offer_conditions_offer_code UNIQUE (offer_id, condition_code);


--
-- Name: offer_document_versions uk_offer_document_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_document_versions
    ADD CONSTRAINT uk_offer_document_version UNIQUE (offer_id, document_version);


--
-- Name: offer_publications uk_offer_publication_document; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_publications
    ADD CONSTRAINT uk_offer_publication_document UNIQUE (offer_document_version_id);


--
-- Name: offer_publications uk_offer_publication_event; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_publications
    ADD CONSTRAINT uk_offer_publication_event UNIQUE (notification_event_id);


--
-- Name: offer_publications uk_offer_publication_sequence; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_publications
    ADD CONSTRAINT uk_offer_publication_sequence UNIQUE (offer_id, publication_sequence);


--
-- Name: offer_responses uk_offer_responses_offer; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_responses
    ADD CONSTRAINT uk_offer_responses_offer UNIQUE (offer_id);


--
-- Name: offers uk_offers_conversion_event; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT uk_offers_conversion_event UNIQUE (conversion_event_id);


--
-- Name: offers uk_offers_conversion_request; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT uk_offers_conversion_request UNIQUE (conversion_request_id);


--
-- Name: offers uk_offers_offer_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT uk_offers_offer_number UNIQUE (offer_number);


--
-- Name: programme_choice_decisions uk_programme_choice_decision_choice; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_choice_decisions
    ADD CONSTRAINT uk_programme_choice_decision_choice UNIQUE (programme_choice_id);


--
-- Name: application_programme_entry_option_selections uk_programme_entry_option_selection; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_programme_entry_option_selections
    ADD CONSTRAINT uk_programme_entry_option_selection UNIQUE (programme_choice_id, entry_option_id);


--
-- Name: application_programme_entry_option_selections uk_programme_entry_option_selection_rank; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_programme_entry_option_selections
    ADD CONSTRAINT uk_programme_entry_option_selection_rank UNIQUE (programme_choice_id, preference_rank);


--
-- Name: admission_qualification_requirement_groups uk_qualification_requirement_group; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_qualification_requirement_groups
    ADD CONSTRAINT uk_qualification_requirement_group UNIQUE (requirement_set_id, group_code);


--
-- Name: applicant_referee_invitations uk_referee_invitation_token_hash; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_referee_invitations
    ADD CONSTRAINT uk_referee_invitation_token_hash UNIQUE (token_hash);


--
-- Name: admission_requirement_sets uk_requirement_set_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_requirement_sets
    ADD CONSTRAINT uk_requirement_set_version UNIQUE NULLS NOT DISTINCT (programme_id, application_type_id, intake_id, version_code);


--
-- Name: selection_decisions uk_selection_decisions_round_choice; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.selection_decisions
    ADD CONSTRAINT uk_selection_decisions_round_choice UNIQUE (selection_round_id, programme_choice_id);


--
-- Name: selection_rounds uk_selection_rounds_cycle_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.selection_rounds
    ADD CONSTRAINT uk_selection_rounds_cycle_code UNIQUE (admission_cycle_id, code);


--
-- Name: idx_academic_review_root_queue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_academic_review_root_queue ON public.academic_review_assignments USING btree (recommendation_academic_unit_id, status, due_at, released_at) WHERE (deleted_at IS NULL);


--
-- Name: idx_academic_reviews_root_queue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_academic_reviews_root_queue ON public.academic_reviews USING btree (recommendation_academic_unit_id, status) WHERE (deleted_at IS NULL);


--
-- Name: idx_admissions_inbox_processed_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_admissions_inbox_processed_at ON public.integration_inbox USING btree (processed_at);


--
-- Name: idx_admissions_outbox_dispatch; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_admissions_outbox_dispatch ON public.integration_outbox USING btree (next_attempt_at, occurred_at) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: idx_application_documents_verification_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_application_documents_verification_status ON public.application_documents USING btree (application_id, status, requirement_code) WHERE (is_current AND (deleted_at IS NULL));


--
-- Name: idx_application_sections_application_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_application_sections_application_status ON public.application_sections USING btree (application_id, status) WHERE (deleted_at IS NULL);


--
-- Name: idx_application_type_document_requirements_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_application_type_document_requirements_active ON public.application_type_document_requirements USING btree (application_type_id, sort_order, requirement_code) WHERE (is_active AND (deleted_at IS NULL));


--
-- Name: idx_applications_work_items; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_applications_work_items ON public.applications USING btree (status, intake_id, application_type_id, updated_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_offer_document_versions_offer_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_offer_document_versions_offer_status ON public.offer_document_versions USING btree (offer_id, status, document_version DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_programme_choices_work_items; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_programme_choices_work_items ON public.application_programme_choices USING btree (programme_id, choice_status, application_id, choice_rank) WHERE (deleted_at IS NULL);


--
-- Name: idx_referee_invitation_application; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_referee_invitation_application ON public.applicant_referee_invitations USING btree (application_id, referee_id, created_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: ix_admission_quotas_intake_programme; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_admission_quotas_intake_programme ON public.admission_quotas USING btree (intake_id, programme_id) WHERE (deleted_at IS NULL);


--
-- Name: ix_application_type_programme_mappings_route; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_application_type_programme_mappings_route ON public.application_type_programme_mappings USING btree (application_type_id, programme_id) WHERE ((deleted_at IS NULL) AND is_active);


--
-- Name: uk_academic_recommendation_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_academic_recommendation_pending ON public.academic_recommendations USING btree (academic_review_id) WHERE (((review_status)::text = 'PENDING'::text) AND (deleted_at IS NULL));


--
-- Name: uk_academic_review_assignment_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_academic_review_assignment_active ON public.academic_review_assignments USING btree (selection_round_id, programme_choice_id) WHERE (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'CLAIMED'::character varying, 'RECOMMENDED'::character varying, 'RETURNED'::character varying])::text[])) AND (deleted_at IS NULL));


--
-- Name: uk_academic_unit_recommendation_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_academic_unit_recommendation_pending ON public.academic_unit_recommendations USING btree (academic_review_assignment_id) WHERE (((review_status)::text = 'PENDING'::text) AND (deleted_at IS NULL));


--
-- Name: uk_active_referee_invitation; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_active_referee_invitation ON public.applicant_referee_invitations USING btree (application_id, referee_id) WHERE (((status)::text = ANY ((ARRAY['SENT'::character varying, 'OPENED'::character varying])::text[])) AND (deleted_at IS NULL));


--
-- Name: uk_admission_cycles_active_intake_projection; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_admission_cycles_active_intake_projection ON public.admission_cycles USING btree (intake_id) WHERE (deleted_at IS NULL);


--
-- Name: uk_admission_quotas_current_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_admission_quotas_current_scope ON public.admission_quotas USING btree (intake_id, programme_id, quota_type_code) WHERE (deleted_at IS NULL);


--
-- Name: uk_applicant_next_of_kin_primary; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_applicant_next_of_kin_primary ON public.applicant_next_of_kin USING btree (applicant_id) WHERE (is_primary AND (deleted_at IS NULL));


--
-- Name: uk_application_choice_programme; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_application_choice_programme ON public.application_programme_choices USING btree (application_id, programme_id) WHERE (deleted_at IS NULL);


--
-- Name: uk_application_choice_rank; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_application_choice_rank ON public.application_programme_choices USING btree (application_id, choice_rank) WHERE (deleted_at IS NULL);


--
-- Name: uk_application_clearance_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_application_clearance_active ON public.application_clearances USING btree (application_id) WHERE (((outcome)::text = 'CONFIRMED'::text) AND (deleted_at IS NULL));


--
-- Name: uk_application_documents_current_requirement; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_application_documents_current_requirement ON public.application_documents USING btree (application_id, requirement_code) WHERE (is_current AND (deleted_at IS NULL));


--
-- Name: uk_application_documents_last_verification_event; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_application_documents_last_verification_event ON public.application_documents USING btree (last_verification_event_id) WHERE (last_verification_event_id IS NOT NULL);


--
-- Name: uk_application_payment_references_active_application; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_application_payment_references_active_application ON public.application_payment_references USING btree (application_id) WHERE (deleted_at IS NULL);


--
-- Name: uk_application_payment_references_finance_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_application_payment_references_finance_id ON public.application_payment_references USING btree (finance_payment_reference_id) WHERE ((finance_payment_reference_id IS NOT NULL) AND (deleted_at IS NULL));


--
-- Name: uk_application_referee_nomination_email; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_application_referee_nomination_email ON public.application_referee_nominations USING btree (application_id, normalized_email) WHERE ((deleted_at IS NULL) AND is_current);


--
-- Name: uk_application_referee_nomination_phone; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_application_referee_nomination_phone ON public.application_referee_nominations USING btree (application_id, normalized_phone_number) WHERE ((deleted_at IS NULL) AND is_current AND (normalized_phone_number IS NOT NULL));


--
-- Name: uk_applications_active_intake_applicant_type; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_applications_active_intake_applicant_type ON public.applications USING btree (intake_id, applicant_id, application_type_id) WHERE ((deleted_at IS NULL) AND ((status)::text <> ALL ((ARRAY['WITHDRAWN'::character varying, 'DECLINED'::character varying, 'CONVERTED'::character varying])::text[])));


--
-- Name: uk_offer_current_publication; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_offer_current_publication ON public.offer_publications USING btree (offer_id) WHERE (current_publication AND (deleted_at IS NULL));


--
-- Name: uk_offer_dispatch_notification_event; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_offer_dispatch_notification_event ON public.offer_dispatches USING btree (notification_event_id) WHERE ((notification_event_id IS NOT NULL) AND (deleted_at IS NULL));


--
-- Name: uk_offer_dispatch_publication_attempt; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_offer_dispatch_publication_attempt ON public.offer_dispatches USING btree (offer_publication_id, attempt_number) WHERE ((offer_publication_id IS NOT NULL) AND (deleted_at IS NULL));


--
-- Name: uk_offer_dispatches_provider_message; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_offer_dispatches_provider_message ON public.offer_dispatches USING btree (delivery_method_code, provider_message_id) WHERE ((provider_message_id IS NOT NULL) AND (deleted_at IS NULL));


--
-- Name: uk_offers_active_application_programme; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_offers_active_application_programme ON public.offers USING btree (application_id, programme_id) WHERE ((deleted_at IS NULL) AND ((status)::text <> ALL ((ARRAY['DECLINED'::character varying, 'EXPIRED'::character varying, 'WITHDRAWN'::character varying])::text[])));


--
-- Name: uk_qualification_results_sitting_subject; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_qualification_results_sitting_subject ON public.applicant_qualification_results USING btree (qualification_sitting_id, subject_id) WHERE ((subject_id IS NOT NULL) AND (deleted_at IS NULL));


--
-- Name: uk_selection_decisions_selected_choice; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_selection_decisions_selected_choice ON public.selection_decisions USING btree (programme_choice_id) WHERE (((decision)::text = 'SELECT'::text) AND (deleted_at IS NULL));


--
-- Name: application_programme_choices trg_application_programme_choice_governance; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_application_programme_choice_governance BEFORE INSERT OR UPDATE ON public.application_programme_choices FOR EACH ROW EXECUTE FUNCTION public.enforce_application_programme_choice_governance();


--
-- Name: offer_batches trg_offer_batch_source_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_offer_batch_source_guard BEFORE INSERT OR UPDATE OF admission_cycle_id, selection_round_id ON public.offer_batches FOR EACH ROW EXECUTE FUNCTION public.validate_offer_batch_source();


--
-- Name: offer_batches trg_offer_batch_transition; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_offer_batch_transition BEFORE UPDATE OF status ON public.offer_batches FOR EACH ROW EXECUTE FUNCTION public.validate_offer_batch_transition();


--
-- Name: offer_conditions trg_offer_condition_transition; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_offer_condition_transition BEFORE UPDATE OF status ON public.offer_conditions FOR EACH ROW EXECUTE FUNCTION public.validate_offer_condition_transition();


--
-- Name: offer_responses trg_offer_response_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_offer_response_guard BEFORE INSERT ON public.offer_responses FOR EACH ROW EXECUTE FUNCTION public.validate_offer_response();


--
-- Name: offer_responses trg_offer_response_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_offer_response_immutable BEFORE DELETE OR UPDATE ON public.offer_responses FOR EACH ROW EXECUTE FUNCTION public.prohibit_offer_response_mutation();


--
-- Name: offers trg_offer_source_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_offer_source_guard BEFORE INSERT OR UPDATE ON public.offers FOR EACH ROW EXECUTE FUNCTION public.validate_offer_source();


--
-- Name: offers trg_offer_transition; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_offer_transition BEFORE UPDATE OF status ON public.offers FOR EACH ROW EXECUTE FUNCTION public.validate_offer_transition();


--
-- Name: academic_review_assignments trg_protect_legacy_admissions_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_legacy_admissions_history BEFORE INSERT OR DELETE OR UPDATE ON public.academic_review_assignments FOR EACH ROW EXECUTE FUNCTION public.protect_legacy_admissions_history();


--
-- Name: academic_unit_recommendations trg_protect_legacy_admissions_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_legacy_admissions_history BEFORE INSERT OR DELETE OR UPDATE ON public.academic_unit_recommendations FOR EACH ROW EXECUTE FUNCTION public.protect_legacy_admissions_history();


--
-- Name: offer_batches trg_protect_legacy_admissions_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_legacy_admissions_history BEFORE INSERT OR DELETE OR UPDATE ON public.offer_batches FOR EACH ROW EXECUTE FUNCTION public.protect_legacy_admissions_history();


--
-- Name: selection_decisions trg_protect_legacy_admissions_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_legacy_admissions_history BEFORE INSERT OR DELETE OR UPDATE ON public.selection_decisions FOR EACH ROW EXECUTE FUNCTION public.protect_legacy_admissions_history();


--
-- Name: selection_rounds trg_protect_legacy_admissions_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_legacy_admissions_history BEFORE INSERT OR DELETE OR UPDATE ON public.selection_rounds FOR EACH ROW EXECUTE FUNCTION public.protect_legacy_admissions_history();


--
-- Name: offer_document_versions trg_protect_stored_offer_document_version; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_stored_offer_document_version BEFORE DELETE OR UPDATE ON public.offer_document_versions FOR EACH ROW EXECUTE FUNCTION public.protect_stored_offer_document_version();


--
-- Name: selection_decisions trg_selection_decision_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_selection_decision_guard BEFORE INSERT OR UPDATE ON public.selection_decisions FOR EACH ROW EXECUTE FUNCTION public.validate_selection_decision();


--
-- Name: selection_rounds trg_selection_round_transition; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_selection_round_transition BEFORE UPDATE OF status ON public.selection_rounds FOR EACH ROW EXECUTE FUNCTION public.validate_selection_round_transition();


--
-- Name: academic_recommendations academic_recommendations_academic_review_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_recommendations
    ADD CONSTRAINT academic_recommendations_academic_review_id_fkey FOREIGN KEY (academic_review_id) REFERENCES public.academic_reviews(id);


--
-- Name: academic_recommendations_aud academic_recommendations_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_recommendations_aud
    ADD CONSTRAINT academic_recommendations_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: academic_review_assignments academic_review_assignments_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_review_assignments
    ADD CONSTRAINT academic_review_assignments_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.applications(id);


--
-- Name: academic_review_assignments_aud academic_review_assignments_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_review_assignments_aud
    ADD CONSTRAINT academic_review_assignments_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: academic_review_assignments academic_review_assignments_programme_choice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_review_assignments
    ADD CONSTRAINT academic_review_assignments_programme_choice_id_fkey FOREIGN KEY (programme_choice_id) REFERENCES public.application_programme_choices(id);


--
-- Name: academic_review_assignments academic_review_assignments_selection_round_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_review_assignments
    ADD CONSTRAINT academic_review_assignments_selection_round_id_fkey FOREIGN KEY (selection_round_id) REFERENCES public.selection_rounds(id);


--
-- Name: academic_reviews academic_reviews_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_reviews
    ADD CONSTRAINT academic_reviews_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.applications(id);


--
-- Name: academic_reviews_aud academic_reviews_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_reviews_aud
    ADD CONSTRAINT academic_reviews_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: academic_reviews academic_reviews_programme_choice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_reviews
    ADD CONSTRAINT academic_reviews_programme_choice_id_fkey FOREIGN KEY (programme_choice_id) REFERENCES public.application_programme_choices(id);


--
-- Name: academic_unit_recommendations academic_unit_recommendations_academic_review_assignment_i_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_unit_recommendations
    ADD CONSTRAINT academic_unit_recommendations_academic_review_assignment_i_fkey FOREIGN KEY (academic_review_assignment_id) REFERENCES public.academic_review_assignments(id);


--
-- Name: academic_unit_recommendations_aud academic_unit_recommendations_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_unit_recommendations_aud
    ADD CONSTRAINT academic_unit_recommendations_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: admission_cycle_archive_summaries admission_cycle_archive_summaries_admission_cycle_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_cycle_archive_summaries
    ADD CONSTRAINT admission_cycle_archive_summaries_admission_cycle_id_fkey FOREIGN KEY (admission_cycle_id) REFERENCES public.admission_cycles(id);


--
-- Name: admission_cycle_archive_summaries_aud admission_cycle_archive_summaries_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_cycle_archive_summaries_aud
    ADD CONSTRAINT admission_cycle_archive_summaries_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: admission_cycles admission_cycles_application_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_cycles
    ADD CONSTRAINT admission_cycles_application_type_id_fkey FOREIGN KEY (application_type_id) REFERENCES public.application_types(id);


--
-- Name: admission_cycles_aud admission_cycles_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_cycles_aud
    ADD CONSTRAINT admission_cycles_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: admission_qualification_requirement_groups admission_qualification_requirement_gro_requirement_set_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_qualification_requirement_groups
    ADD CONSTRAINT admission_qualification_requirement_gro_requirement_set_id_fkey FOREIGN KEY (requirement_set_id) REFERENCES public.admission_requirement_sets(id);


--
-- Name: admission_qualification_requirement_groups_aud admission_qualification_requirement_groups_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_qualification_requirement_groups_aud
    ADD CONSTRAINT admission_qualification_requirement_groups_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: admission_qualification_requirement_items admission_qualification_requirement_i_requirement_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_qualification_requirement_items
    ADD CONSTRAINT admission_qualification_requirement_i_requirement_group_id_fkey FOREIGN KEY (requirement_group_id) REFERENCES public.admission_qualification_requirement_groups(id);


--
-- Name: admission_qualification_requirement_items_aud admission_qualification_requirement_items_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_qualification_requirement_items_aud
    ADD CONSTRAINT admission_qualification_requirement_items_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: admission_quotas_aud admission_quotas_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_quotas_aud
    ADD CONSTRAINT admission_quotas_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: admission_requirement_sets admission_requirement_sets_admission_cycle_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_requirement_sets
    ADD CONSTRAINT admission_requirement_sets_admission_cycle_id_fkey FOREIGN KEY (admission_cycle_id) REFERENCES public.admission_cycles(id);


--
-- Name: admission_requirement_sets admission_requirement_sets_application_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_requirement_sets
    ADD CONSTRAINT admission_requirement_sets_application_type_id_fkey FOREIGN KEY (application_type_id) REFERENCES public.application_types(id);


--
-- Name: admission_requirement_sets_aud admission_requirement_sets_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_requirement_sets_aud
    ADD CONSTRAINT admission_requirement_sets_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: admission_subject_requirements_aud admission_subject_requirements_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_subject_requirements_aud
    ADD CONSTRAINT admission_subject_requirements_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: admission_subject_requirements admission_subject_requirements_requirement_set_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_subject_requirements
    ADD CONSTRAINT admission_subject_requirements_requirement_set_id_fkey FOREIGN KEY (requirement_set_id) REFERENCES public.admission_requirement_sets(id);


--
-- Name: admission_subject_requirements admission_subject_requirements_subject_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_subject_requirements
    ADD CONSTRAINT admission_subject_requirements_subject_id_fkey FOREIGN KEY (subject_id) REFERENCES public.admission_subjects(id);


--
-- Name: admission_subjects_aud admission_subjects_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admission_subjects_aud
    ADD CONSTRAINT admission_subjects_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: applicant_employment_histories applicant_employment_histories_applicant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_employment_histories
    ADD CONSTRAINT applicant_employment_histories_applicant_id_fkey FOREIGN KEY (applicant_id) REFERENCES public.applicants(id);


--
-- Name: applicant_employment_histories_aud applicant_employment_histories_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_employment_histories_aud
    ADD CONSTRAINT applicant_employment_histories_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: applicant_next_of_kin applicant_next_of_kin_applicant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_next_of_kin
    ADD CONSTRAINT applicant_next_of_kin_applicant_id_fkey FOREIGN KEY (applicant_id) REFERENCES public.applicants(id);


--
-- Name: applicant_next_of_kin_aud applicant_next_of_kin_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_next_of_kin_aud
    ADD CONSTRAINT applicant_next_of_kin_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: applicant_qualification_results_aud applicant_qualification_results_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_qualification_results_aud
    ADD CONSTRAINT applicant_qualification_results_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: applicant_qualification_results applicant_qualification_results_qualification_sitting_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_qualification_results
    ADD CONSTRAINT applicant_qualification_results_qualification_sitting_id_fkey FOREIGN KEY (qualification_sitting_id) REFERENCES public.applicant_qualification_sittings(id);


--
-- Name: applicant_qualification_results applicant_qualification_results_subject_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_qualification_results
    ADD CONSTRAINT applicant_qualification_results_subject_id_fkey FOREIGN KEY (subject_id) REFERENCES public.admission_subjects(id);


--
-- Name: applicant_qualification_sittings applicant_qualification_sittings_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_qualification_sittings
    ADD CONSTRAINT applicant_qualification_sittings_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.applications(id);


--
-- Name: applicant_qualification_sittings_aud applicant_qualification_sittings_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_qualification_sittings_aud
    ADD CONSTRAINT applicant_qualification_sittings_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: applicant_qualification_sittings applicant_qualification_sittings_exam_body_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_qualification_sittings
    ADD CONSTRAINT applicant_qualification_sittings_exam_body_id_fkey FOREIGN KEY (exam_body_id) REFERENCES public.exam_bodies(id);


--
-- Name: applicant_referee_invitations applicant_referee_invitations_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_referee_invitations
    ADD CONSTRAINT applicant_referee_invitations_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.applications(id) ON DELETE CASCADE;


--
-- Name: applicant_referee_invitations_aud applicant_referee_invitations_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_referee_invitations_aud
    ADD CONSTRAINT applicant_referee_invitations_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: applicant_referee_invitations applicant_referee_invitations_nomination_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_referee_invitations
    ADD CONSTRAINT applicant_referee_invitations_nomination_id_fkey FOREIGN KEY (nomination_id) REFERENCES public.application_referee_nominations(id);


--
-- Name: applicant_referee_invitations applicant_referee_invitations_referee_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_referee_invitations
    ADD CONSTRAINT applicant_referee_invitations_referee_id_fkey FOREIGN KEY (referee_id) REFERENCES public.applicant_referees(id) ON DELETE CASCADE;


--
-- Name: applicant_referees applicant_referees_applicant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_referees
    ADD CONSTRAINT applicant_referees_applicant_id_fkey FOREIGN KEY (applicant_id) REFERENCES public.applicants(id);


--
-- Name: applicant_referees_aud applicant_referees_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicant_referees_aud
    ADD CONSTRAINT applicant_referees_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: applicants_aud applicants_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applicants_aud
    ADD CONSTRAINT applicants_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_accommodation_requests application_accommodation_requests_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_accommodation_requests
    ADD CONSTRAINT application_accommodation_requests_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.applications(id);


--
-- Name: application_accommodation_requests_aud application_accommodation_requests_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_accommodation_requests_aud
    ADD CONSTRAINT application_accommodation_requests_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_clearances application_clearances_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_clearances
    ADD CONSTRAINT application_clearances_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.applications(id);


--
-- Name: application_clearances_aud application_clearances_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_clearances_aud
    ADD CONSTRAINT application_clearances_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_document_requirement_snapshots application_document_requirement_snapshots_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_document_requirement_snapshots
    ADD CONSTRAINT application_document_requirement_snapshots_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.applications(id);


--
-- Name: application_document_requirement_snapshots_aud application_document_requirement_snapshots_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_document_requirement_snapshots_aud
    ADD CONSTRAINT application_document_requirement_snapshots_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_documents application_documents_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_documents
    ADD CONSTRAINT application_documents_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.applications(id);


--
-- Name: application_documents_aud application_documents_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_documents_aud
    ADD CONSTRAINT application_documents_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_documents application_documents_supersedes_application_document_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_documents
    ADD CONSTRAINT application_documents_supersedes_application_document_id_fkey FOREIGN KEY (supersedes_application_document_id) REFERENCES public.application_documents(id);


--
-- Name: application_evaluations application_evaluations_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_evaluations
    ADD CONSTRAINT application_evaluations_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.applications(id);


--
-- Name: application_evaluations_aud application_evaluations_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_evaluations_aud
    ADD CONSTRAINT application_evaluations_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_evaluations application_evaluations_programme_choice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_evaluations
    ADD CONSTRAINT application_evaluations_programme_choice_id_fkey FOREIGN KEY (programme_choice_id) REFERENCES public.application_programme_choices(id);


--
-- Name: application_evaluations application_evaluations_requirement_set_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_evaluations
    ADD CONSTRAINT application_evaluations_requirement_set_id_fkey FOREIGN KEY (requirement_set_id) REFERENCES public.admission_requirement_sets(id);


--
-- Name: application_exam_arrangements application_exam_arrangements_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_exam_arrangements
    ADD CONSTRAINT application_exam_arrangements_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.applications(id);


--
-- Name: application_exam_arrangements_aud application_exam_arrangements_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_exam_arrangements_aud
    ADD CONSTRAINT application_exam_arrangements_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_fees application_fees_application_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_fees
    ADD CONSTRAINT application_fees_application_type_id_fkey FOREIGN KEY (application_type_id) REFERENCES public.application_types(id);


--
-- Name: application_fees_aud application_fees_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_fees_aud
    ADD CONSTRAINT application_fees_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_payment_references application_payment_references_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_payment_references
    ADD CONSTRAINT application_payment_references_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.applications(id);


--
-- Name: application_payment_references_aud application_payment_references_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_payment_references_aud
    ADD CONSTRAINT application_payment_references_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_prior_uz_declarations application_prior_uz_declarations_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_prior_uz_declarations
    ADD CONSTRAINT application_prior_uz_declarations_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.applications(id);


--
-- Name: application_prior_uz_declarations_aud application_prior_uz_declarations_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_prior_uz_declarations_aud
    ADD CONSTRAINT application_prior_uz_declarations_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_professional_achievements application_professional_achievements_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_professional_achievements
    ADD CONSTRAINT application_professional_achievements_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.applications(id);


--
-- Name: application_professional_achievements_aud application_professional_achievements_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_professional_achievements_aud
    ADD CONSTRAINT application_professional_achievements_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_programme_choices application_programme_choices_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_programme_choices
    ADD CONSTRAINT application_programme_choices_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.applications(id);


--
-- Name: application_programme_choices_aud application_programme_choices_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_programme_choices_aud
    ADD CONSTRAINT application_programme_choices_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_programme_entry_option_selections application_programme_entry_option_sel_programme_choice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_programme_entry_option_selections
    ADD CONSTRAINT application_programme_entry_option_sel_programme_choice_id_fkey FOREIGN KEY (programme_choice_id) REFERENCES public.application_programme_choices(id);


--
-- Name: application_programme_entry_option_selections_aud application_programme_entry_option_selections_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_programme_entry_option_selections_aud
    ADD CONSTRAINT application_programme_entry_option_selections_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_programme_option_snapshots application_programme_option_snapshots_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_programme_option_snapshots
    ADD CONSTRAINT application_programme_option_snapshots_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.applications(id);


--
-- Name: application_programme_option_snapshots_aud application_programme_option_snapshots_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_programme_option_snapshots_aud
    ADD CONSTRAINT application_programme_option_snapshots_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_referee_nominations application_referee_nominations_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_referee_nominations
    ADD CONSTRAINT application_referee_nominations_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.applications(id);


--
-- Name: application_referee_nominations_aud application_referee_nominations_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_referee_nominations_aud
    ADD CONSTRAINT application_referee_nominations_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_referee_nominations application_referee_nominations_referee_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_referee_nominations
    ADD CONSTRAINT application_referee_nominations_referee_id_fkey FOREIGN KEY (referee_id) REFERENCES public.applicant_referees(id);


--
-- Name: application_sections application_sections_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_sections
    ADD CONSTRAINT application_sections_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.applications(id);


--
-- Name: application_sections_aud application_sections_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_sections_aud
    ADD CONSTRAINT application_sections_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_status_events application_status_events_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_status_events
    ADD CONSTRAINT application_status_events_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.applications(id);


--
-- Name: application_status_events_aud application_status_events_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_status_events_aud
    ADD CONSTRAINT application_status_events_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_type_document_requirements application_type_document_requirements_application_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_type_document_requirements
    ADD CONSTRAINT application_type_document_requirements_application_type_id_fkey FOREIGN KEY (application_type_id) REFERENCES public.application_types(id);


--
-- Name: application_type_document_requirements_aud application_type_document_requirements_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_type_document_requirements_aud
    ADD CONSTRAINT application_type_document_requirements_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_type_programme_mappings application_type_programme_mappings_application_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_type_programme_mappings
    ADD CONSTRAINT application_type_programme_mappings_application_type_id_fkey FOREIGN KEY (application_type_id) REFERENCES public.application_types(id);


--
-- Name: application_type_programme_mappings_aud application_type_programme_mappings_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_type_programme_mappings_aud
    ADD CONSTRAINT application_type_programme_mappings_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_type_sections application_type_sections_application_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_type_sections
    ADD CONSTRAINT application_type_sections_application_type_id_fkey FOREIGN KEY (application_type_id) REFERENCES public.application_types(id);


--
-- Name: application_type_sections_aud application_type_sections_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_type_sections_aud
    ADD CONSTRAINT application_type_sections_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_types_aud application_types_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_types_aud
    ADD CONSTRAINT application_types_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: applications applications_admission_cycle_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applications
    ADD CONSTRAINT applications_admission_cycle_id_fkey FOREIGN KEY (admission_cycle_id) REFERENCES public.admission_cycles(id);


--
-- Name: applications applications_applicant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applications
    ADD CONSTRAINT applications_applicant_id_fkey FOREIGN KEY (applicant_id) REFERENCES public.applicants(id);


--
-- Name: applications applications_application_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applications
    ADD CONSTRAINT applications_application_type_id_fkey FOREIGN KEY (application_type_id) REFERENCES public.application_types(id);


--
-- Name: applications_aud applications_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applications_aud
    ADD CONSTRAINT applications_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: exam_bodies_aud exam_bodies_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exam_bodies_aud
    ADD CONSTRAINT exam_bodies_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: offers fk_offers_current_document_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT fk_offers_current_document_version FOREIGN KEY (current_document_version_id) REFERENCES public.offer_document_versions(id);


--
-- Name: offers fk_offers_current_publication; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT fk_offers_current_publication FOREIGN KEY (current_publication_id) REFERENCES public.offer_publications(id);


--
-- Name: grading_scale_values_aud grading_scale_values_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grading_scale_values_aud
    ADD CONSTRAINT grading_scale_values_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: grading_scale_values grading_scale_values_grading_scale_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grading_scale_values
    ADD CONSTRAINT grading_scale_values_grading_scale_id_fkey FOREIGN KEY (grading_scale_id) REFERENCES public.grading_scales(id);


--
-- Name: grading_scales_aud grading_scales_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grading_scales_aud
    ADD CONSTRAINT grading_scales_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: offer_batches offer_batches_admission_cycle_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_batches
    ADD CONSTRAINT offer_batches_admission_cycle_id_fkey FOREIGN KEY (admission_cycle_id) REFERENCES public.admission_cycles(id);


--
-- Name: offer_batches_aud offer_batches_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_batches_aud
    ADD CONSTRAINT offer_batches_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: offer_batches offer_batches_selection_round_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_batches
    ADD CONSTRAINT offer_batches_selection_round_id_fkey FOREIGN KEY (selection_round_id) REFERENCES public.selection_rounds(id);


--
-- Name: offer_conditions_aud offer_conditions_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_conditions_aud
    ADD CONSTRAINT offer_conditions_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: offer_conditions offer_conditions_offer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_conditions
    ADD CONSTRAINT offer_conditions_offer_id_fkey FOREIGN KEY (offer_id) REFERENCES public.offers(id);


--
-- Name: offer_dispatches_aud offer_dispatches_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_dispatches_aud
    ADD CONSTRAINT offer_dispatches_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: offer_dispatches offer_dispatches_offer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_dispatches
    ADD CONSTRAINT offer_dispatches_offer_id_fkey FOREIGN KEY (offer_id) REFERENCES public.offers(id);


--
-- Name: offer_dispatches offer_dispatches_offer_publication_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_dispatches
    ADD CONSTRAINT offer_dispatches_offer_publication_id_fkey FOREIGN KEY (offer_publication_id) REFERENCES public.offer_publications(id);


--
-- Name: offer_document_versions_aud offer_document_versions_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_document_versions_aud
    ADD CONSTRAINT offer_document_versions_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: offer_document_versions offer_document_versions_offer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_document_versions
    ADD CONSTRAINT offer_document_versions_offer_id_fkey FOREIGN KEY (offer_id) REFERENCES public.offers(id);


--
-- Name: offer_publications_aud offer_publications_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_publications_aud
    ADD CONSTRAINT offer_publications_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: offer_publications offer_publications_offer_document_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_publications
    ADD CONSTRAINT offer_publications_offer_document_version_id_fkey FOREIGN KEY (offer_document_version_id) REFERENCES public.offer_document_versions(id);


--
-- Name: offer_publications offer_publications_offer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_publications
    ADD CONSTRAINT offer_publications_offer_id_fkey FOREIGN KEY (offer_id) REFERENCES public.offers(id);


--
-- Name: offer_responses_aud offer_responses_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_responses_aud
    ADD CONSTRAINT offer_responses_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: offer_responses offer_responses_offer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_responses
    ADD CONSTRAINT offer_responses_offer_id_fkey FOREIGN KEY (offer_id) REFERENCES public.offers(id);


--
-- Name: offer_responses offer_responses_offer_publication_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_responses
    ADD CONSTRAINT offer_responses_offer_publication_id_fkey FOREIGN KEY (offer_publication_id) REFERENCES public.offer_publications(id);


--
-- Name: offer_status_events_aud offer_status_events_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_status_events_aud
    ADD CONSTRAINT offer_status_events_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: offer_status_events offer_status_events_offer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_status_events
    ADD CONSTRAINT offer_status_events_offer_id_fkey FOREIGN KEY (offer_id) REFERENCES public.offers(id);


--
-- Name: offers offers_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT offers_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.applications(id);


--
-- Name: offers_aud offers_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers_aud
    ADD CONSTRAINT offers_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: offers offers_offer_batch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT offers_offer_batch_id_fkey FOREIGN KEY (offer_batch_id) REFERENCES public.offer_batches(id);


--
-- Name: offers offers_programme_choice_decision_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT offers_programme_choice_decision_id_fkey FOREIGN KEY (programme_choice_decision_id) REFERENCES public.programme_choice_decisions(id);


--
-- Name: offers offers_programme_choice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT offers_programme_choice_id_fkey FOREIGN KEY (programme_choice_id) REFERENCES public.application_programme_choices(id);


--
-- Name: programme_choice_decisions programme_choice_decisions_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_choice_decisions
    ADD CONSTRAINT programme_choice_decisions_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.applications(id);


--
-- Name: programme_choice_decisions_aud programme_choice_decisions_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_choice_decisions_aud
    ADD CONSTRAINT programme_choice_decisions_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: programme_choice_decisions programme_choice_decisions_programme_choice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_choice_decisions
    ADD CONSTRAINT programme_choice_decisions_programme_choice_id_fkey FOREIGN KEY (programme_choice_id) REFERENCES public.application_programme_choices(id);


--
-- Name: programme_choice_decisions programme_choice_decisions_source_recommendation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_choice_decisions
    ADD CONSTRAINT programme_choice_decisions_source_recommendation_id_fkey FOREIGN KEY (source_recommendation_id) REFERENCES public.academic_recommendations(id);


--
-- Name: selection_decisions_aud selection_decisions_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.selection_decisions_aud
    ADD CONSTRAINT selection_decisions_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: selection_decisions selection_decisions_programme_choice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.selection_decisions
    ADD CONSTRAINT selection_decisions_programme_choice_id_fkey FOREIGN KEY (programme_choice_id) REFERENCES public.application_programme_choices(id);


--
-- Name: selection_decisions selection_decisions_selection_round_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.selection_decisions
    ADD CONSTRAINT selection_decisions_selection_round_id_fkey FOREIGN KEY (selection_round_id) REFERENCES public.selection_rounds(id);


--
-- Name: selection_rounds selection_rounds_admission_cycle_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.selection_rounds
    ADD CONSTRAINT selection_rounds_admission_cycle_id_fkey FOREIGN KEY (admission_cycle_id) REFERENCES public.admission_cycles(id);


--
-- Name: selection_rounds_aud selection_rounds_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.selection_rounds_aud
    ADD CONSTRAINT selection_rounds_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
--


