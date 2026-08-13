-- Author: Tinashe K
-- Canonical clean-slate baseline for assessment-results-service.

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
-- Name: enforce_assessment_component_scheme_mutability(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_assessment_component_scheme_mutability() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM assessment_schemes WHERE id = COALESCE(NEW.assessment_scheme_id, OLD.assessment_scheme_id)
               AND status <> 'DRAFT') THEN
        RAISE EXCEPTION 'Components of an approved assessment scheme are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;


--
-- Name: enforce_grading_band_scheme_mutability(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_grading_band_scheme_mutability() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
 IF EXISTS(SELECT 1 FROM grading_schemes WHERE id=COALESCE(NEW.grading_scheme_id,OLD.grading_scheme_id) AND status<>'DRAFT')
 THEN RAISE EXCEPTION 'Bands of an approved grading scheme are immutable'; END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END; $$;


--
-- Name: enforce_progression_outcome_mutability(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_progression_outcome_mutability() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE target_rule_set_id uuid;
BEGIN
    target_rule_set_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.progression_rule_set_id ELSE NEW.progression_rule_set_id END;
    IF EXISTS (SELECT 1 FROM progression_rule_sets WHERE id = target_rule_set_id AND status <> 'DRAFT') THEN
        RAISE EXCEPTION 'Outcomes of an approved progression rule set are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;


--
-- Name: prevent_approved_progression_rule_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_approved_progression_rule_change() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.status IN ('APPROVED', 'SUPERSEDED') AND (
        NEW.rule_code IS DISTINCT FROM OLD.rule_code OR NEW.rule_name IS DISTINCT FROM OLD.rule_name
        OR NEW.programme_id IS DISTINCT FROM OLD.programme_id
        OR NEW.programme_version_id IS DISTINCT FROM OLD.programme_version_id
        OR NEW.programme_period_number IS DISTINCT FROM OLD.programme_period_number
        OR NEW.rule_version IS DISTINCT FROM OLD.rule_version
    ) THEN RAISE EXCEPTION 'Approved progression rule identity is immutable'; END IF;
    RETURN NEW;
END;
$$;


--
-- Name: prevent_assessment_roster_snapshot_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_assessment_roster_snapshot_change() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.roster_import_id IS DISTINCT FROM OLD.roster_import_id
       OR NEW.registration_module_id IS DISTINCT FROM OLD.registration_module_id
       OR NEW.curriculum_module_id IS DISTINCT FROM OLD.curriculum_module_id
       OR NEW.module_id IS DISTINCT FROM OLD.module_id
       OR NEW.module_code IS DISTINCT FROM OLD.module_code
       OR NEW.module_name IS DISTINCT FROM OLD.module_name
       OR NEW.curriculum_module_type IS DISTINCT FROM OLD.curriculum_module_type
       OR NEW.credit_value IS DISTINCT FROM OLD.credit_value
       OR NEW.minimum_mark_required IS DISTINCT FROM OLD.minimum_mark_required THEN
        RAISE EXCEPTION 'Assessment roster source snapshot is immutable';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: prevent_calculation_component_evidence_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_calculation_component_evidence_change() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN RAISE EXCEPTION 'Calculation component evidence is immutable'; END; $$;


--
-- Name: prevent_module_result_evidence_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_module_result_evidence_change() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
 IF NEW.result_batch_id IS DISTINCT FROM OLD.result_batch_id OR NEW.calculation_outcome_id IS DISTINCT FROM OLD.calculation_outcome_id
 OR NEW.assessment_roster_entry_id IS DISTINCT FROM OLD.assessment_roster_entry_id OR NEW.coursework_mark IS DISTINCT FROM OLD.coursework_mark
 OR NEW.examination_mark IS DISTINCT FROM OLD.examination_mark OR NEW.final_mark IS DISTINCT FROM OLD.final_mark
 OR NEW.grade IS DISTINCT FROM OLD.grade OR NEW.remark IS DISTINCT FROM OLD.remark OR NEW.result_status IS DISTINCT FROM OLD.result_status
 THEN RAISE EXCEPTION 'Calculated Module result evidence is immutable'; END IF;
 RETURN NEW;
END; $$;


--
-- Name: prevent_overall_decision_evidence_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_overall_decision_evidence_change() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.progression_rule_set_id IS DISTINCT FROM OLD.progression_rule_set_id
       OR NEW.registration_roster_import_id IS DISTINCT FROM OLD.registration_roster_import_id
       OR NEW.decision_number IS DISTINCT FROM OLD.decision_number
       OR NEW.decision_version IS DISTINCT FROM OLD.decision_version
       OR NEW.supersedes_decision_id IS DISTINCT FROM OLD.supersedes_decision_id
       OR NEW.student_id IS DISTINCT FROM OLD.student_id OR NEW.student_number IS DISTINCT FROM OLD.student_number
       OR NEW.programme_enrolment_id IS DISTINCT FROM OLD.programme_enrolment_id
       OR NEW.programme_id IS DISTINCT FROM OLD.programme_id
       OR NEW.programme_version_id IS DISTINCT FROM OLD.programme_version_id
       OR NEW.academic_period_id IS DISTINCT FROM OLD.academic_period_id
       OR NEW.academic_period_code IS DISTINCT FROM OLD.academic_period_code
       OR NEW.programme_period_number IS DISTINCT FROM OLD.programme_period_number
       OR NEW.matched_outcome_id IS DISTINCT FROM OLD.matched_outcome_id
       OR NEW.decision_code IS DISTINCT FROM OLD.decision_code OR NEW.decision_label IS DISTINCT FROM OLD.decision_label
       OR NEW.next_programme_period_number IS DISTINCT FROM OLD.next_programme_period_number
       OR NEW.attempted_credits IS DISTINCT FROM OLD.attempted_credits
       OR NEW.passed_credits IS DISTINCT FROM OLD.passed_credits
       OR NEW.failed_credits IS DISTINCT FROM OLD.failed_credits
       OR NEW.failed_modules IS DISTINCT FROM OLD.failed_modules
       OR NEW.failed_compulsory_modules IS DISTINCT FROM OLD.failed_compulsory_modules
       OR NEW.weighted_average IS DISTINCT FROM OLD.weighted_average
       OR NEW.calculated_by_user_id IS DISTINCT FROM OLD.calculated_by_user_id
       OR NEW.calculated_at IS DISTINCT FROM OLD.calculated_at THEN
        RAISE EXCEPTION 'Calculated progression decision evidence is immutable';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: prevent_overall_decision_result_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_overall_decision_result_change() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'Progression decision source evidence is immutable';
END;
$$;


--
-- Name: prevent_published_result_amendment_event_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_published_result_amendment_event_change() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'Published result amendment events are append-only';
END;
$$;


--
-- Name: prevent_published_result_amendment_evidence_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_published_result_amendment_evidence_change() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.amendment_number IS DISTINCT FROM OLD.amendment_number
       OR NEW.original_published_result_id IS DISTINCT FROM OLD.original_published_result_id
       OR NEW.replacement_result_batch_id IS DISTINCT FROM OLD.replacement_result_batch_id
       OR NEW.replacement_module_result_id IS DISTINCT FROM OLD.replacement_module_result_id
       OR NEW.proposed_final_mark IS DISTINCT FROM OLD.proposed_final_mark
       OR NEW.proposed_grade IS DISTINCT FROM OLD.proposed_grade
       OR NEW.proposed_remark IS DISTINCT FROM OLD.proposed_remark
       OR NEW.request_reason IS DISTINCT FROM OLD.request_reason
       OR NEW.requested_by_user_id IS DISTINCT FROM OLD.requested_by_user_id
       OR NEW.requested_at IS DISTINCT FROM OLD.requested_at THEN
        RAISE EXCEPTION 'Published result amendment source evidence is immutable';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: prevent_published_result_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_published_result_change() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN RAISE EXCEPTION 'Published results are immutable; use a governed correction'; END; $$;


--
-- Name: prevent_roster_import_identity_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_roster_import_identity_change() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.source_event_id IS DISTINCT FROM OLD.source_event_id
       OR NEW.registration_session_id IS DISTINCT FROM OLD.registration_session_id
       OR NEW.student_id IS DISTINCT FROM OLD.student_id
       OR NEW.student_number IS DISTINCT FROM OLD.student_number
       OR NEW.programme_enrolment_id IS DISTINCT FROM OLD.programme_enrolment_id
       OR NEW.programme_id IS DISTINCT FROM OLD.programme_id
       OR NEW.programme_version_id IS DISTINCT FROM OLD.programme_version_id
       OR NEW.academic_period_id IS DISTINCT FROM OLD.academic_period_id
       OR NEW.academic_period_code IS DISTINCT FROM OLD.academic_period_code
       OR NEW.academic_period_name IS DISTINCT FROM OLD.academic_period_name
       OR NEW.academic_period_starts_on IS DISTINCT FROM OLD.academic_period_starts_on
       OR NEW.academic_period_ends_on IS DISTINCT FROM OLD.academic_period_ends_on
       OR NEW.programme_period_number IS DISTINCT FROM OLD.programme_period_number THEN
        RAISE EXCEPTION 'Imported registration roster identity is immutable';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: prevent_submitted_mark_evidence_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_submitted_mark_evidence_change() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.status IN ('SUBMITTED', 'SUPERSEDED') AND (
        NEW.assessment_component_id IS DISTINCT FROM OLD.assessment_component_id
        OR NEW.assessment_roster_entry_id IS DISTINCT FROM OLD.assessment_roster_entry_id
        OR NEW.revision_number IS DISTINCT FROM OLD.revision_number
        OR NEW.supersedes_mark_id IS DISTINCT FROM OLD.supersedes_mark_id
        OR NEW.score IS DISTINCT FROM OLD.score
        OR NEW.capture_method IS DISTINCT FROM OLD.capture_method
        OR NEW.captured_by_user_id IS DISTINCT FROM OLD.captured_by_user_id
        OR NEW.captured_at IS DISTINCT FROM OLD.captured_at
        OR NEW.submitted_by_user_id IS DISTINCT FROM OLD.submitted_by_user_id
        OR NEW.submitted_at IS DISTINCT FROM OLD.submitted_at) THEN
        RAISE EXCEPTION 'Submitted assessment mark evidence is immutable';
    END IF;
    IF OLD.status = 'SUBMITTED' AND NEW.status NOT IN ('SUBMITTED', 'SUPERSEDED') THEN
        RAISE EXCEPTION 'A submitted assessment mark can only be superseded';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: validate_assessment_mark_scope(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_assessment_mark_scope() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE component_max numeric(8,2); component_module uuid; roster_module uuid;
BEGIN
    SELECT c.maximum_mark, o.module_id INTO component_max, component_module
      FROM assessment_components c JOIN assessment_schemes s ON s.id = c.assessment_scheme_id
      JOIN assessment_module_offerings o ON o.id = s.module_offering_id WHERE c.id = NEW.assessment_component_id;
    SELECT module_id INTO roster_module FROM assessment_roster_entries WHERE id = NEW.assessment_roster_entry_id;
    IF NEW.score > component_max THEN RAISE EXCEPTION 'Assessment score exceeds component maximum'; END IF;
    IF component_module IS DISTINCT FROM roster_module THEN
        RAISE EXCEPTION 'Assessment mark roster Module does not match component offering';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: validate_grading_scheme_on_approval(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_grading_scheme_on_approval() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE invalid_count integer; first_min numeric; last_max numeric;
BEGIN
 IF NEW.status='APPROVED' AND OLD.status<>'APPROVED' THEN
   SELECT min(minimum_mark),max(maximum_mark) INTO first_min,last_max FROM grading_bands WHERE grading_scheme_id=NEW.id AND deleted_at IS NULL;
   SELECT count(*) INTO invalid_count FROM (
     SELECT minimum_mark,lag(maximum_mark) OVER(ORDER BY minimum_mark) previous_maximum
     FROM grading_bands WHERE grading_scheme_id=NEW.id AND deleted_at IS NULL
   ) bands WHERE previous_maximum IS NOT NULL AND minimum_mark <> previous_maximum + 0.01;
   IF first_min IS DISTINCT FROM 0 OR last_max IS DISTINCT FROM 100 OR invalid_count>0 THEN
     RAISE EXCEPTION 'Approved grading bands must cover 0.00 through 100.00 without gaps or overlaps';
   END IF;
 END IF;
 RETURN NEW;
END; $$;


--
-- Name: validate_overall_decision_evidence(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_overall_decision_evidence() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    eligible_count integer; evidence_count integer; current_count integer;
    calculated_attempted numeric(8,2); calculated_passed numeric(8,2); calculated_failed numeric(8,2);
    calculated_failed_modules integer; calculated_failed_compulsory integer; calculated_average numeric(6,2);
    expected_outcome_id uuid;
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM progression_rule_sets rule_set
        JOIN registration_roster_imports roster ON roster.id = NEW.registration_roster_import_id
        JOIN progression_rule_outcomes outcome ON outcome.id = NEW.matched_outcome_id
        WHERE rule_set.id = NEW.progression_rule_set_id AND rule_set.status = 'APPROVED'
          AND outcome.progression_rule_set_id = rule_set.id
          AND rule_set.programme_id = roster.programme_id
          AND rule_set.programme_version_id = roster.programme_version_id
          AND rule_set.programme_period_number = roster.programme_period_number
          AND NEW.student_id = roster.student_id AND NEW.student_number = roster.student_number
          AND NEW.programme_enrolment_id = roster.programme_enrolment_id
          AND NEW.programme_id = roster.programme_id
          AND NEW.programme_version_id = roster.programme_version_id
          AND NEW.academic_period_id = roster.academic_period_id
          AND NEW.academic_period_code = roster.academic_period_code
          AND NEW.programme_period_number = roster.programme_period_number
          AND NEW.decision_code = outcome.decision_code
          AND NEW.decision_label = outcome.decision_label
          AND NEW.next_programme_period_number IS NOT DISTINCT FROM outcome.next_programme_period_number
    ) THEN
        RAISE EXCEPTION 'Progression decision scope and outcome must match the approved rule and roster snapshots';
    END IF;

    SELECT count(*) INTO eligible_count FROM assessment_roster_entries
    WHERE roster_import_id = NEW.registration_roster_import_id AND eligibility_status = 'ELIGIBLE' AND deleted_at IS NULL;

    SELECT count(*), coalesce(sum(credit_value), 0),
           coalesce(sum(credit_value) FILTER (WHERE passing), 0),
           coalesce(sum(credit_value) FILTER (WHERE NOT passing), 0),
           count(*) FILTER (WHERE NOT passing),
           count(*) FILTER (WHERE NOT passing AND curriculum_module_type = 'COMPULSORY'),
           round(sum(final_mark * credit_value) / nullif(sum(credit_value), 0), 2)
    INTO evidence_count, calculated_attempted, calculated_passed, calculated_failed,
         calculated_failed_modules, calculated_failed_compulsory, calculated_average
    FROM student_overall_decision_results WHERE student_overall_decision_id = NEW.id;

    SELECT count(*) INTO current_count
    FROM student_overall_decision_results evidence
    WHERE evidence.student_overall_decision_id = NEW.id
      AND NOT EXISTS (
          SELECT 1 FROM published_results newer
          JOIN published_results source ON source.id = evidence.published_result_id
          WHERE newer.student_id = source.student_id AND newer.module_id = source.module_id
            AND newer.academic_period_id = source.academic_period_id
            AND newer.publication_version > source.publication_version
      );

    IF eligible_count = 0 OR evidence_count <> eligible_count OR current_count <> evidence_count THEN
        RAISE EXCEPTION 'Progression requires one current published result for every eligible registered Module';
    END IF;
    IF NEW.attempted_credits IS DISTINCT FROM calculated_attempted
       OR NEW.passed_credits IS DISTINCT FROM calculated_passed
       OR NEW.failed_credits IS DISTINCT FROM calculated_failed
       OR NEW.failed_modules IS DISTINCT FROM calculated_failed_modules
       OR NEW.failed_compulsory_modules IS DISTINCT FROM calculated_failed_compulsory
       OR NEW.weighted_average IS DISTINCT FROM calculated_average THEN
        RAISE EXCEPTION 'Progression aggregate metrics do not match immutable published result evidence';
    END IF;

    SELECT outcome.id INTO expected_outcome_id
    FROM progression_rule_outcomes outcome
    WHERE outcome.progression_rule_set_id = NEW.progression_rule_set_id AND outcome.deleted_at IS NULL
      AND (outcome.minimum_weighted_average IS NULL OR NEW.weighted_average >= outcome.minimum_weighted_average)
      AND (outcome.minimum_passed_credits IS NULL OR NEW.passed_credits >= outcome.minimum_passed_credits)
      AND (outcome.maximum_failed_credits IS NULL OR NEW.failed_credits <= outcome.maximum_failed_credits)
      AND (outcome.maximum_failed_modules IS NULL OR NEW.failed_modules <= outcome.maximum_failed_modules)
      AND (NOT outcome.require_all_compulsory_passed OR NEW.failed_compulsory_modules = 0)
    ORDER BY outcome.priority LIMIT 1;
    IF expected_outcome_id IS DISTINCT FROM NEW.matched_outcome_id THEN
        RAISE EXCEPTION 'Progression decision does not match the first applicable approved rule outcome';
    END IF;
    RETURN NULL;
END;
$$;


--
-- Name: validate_overall_decision_lineage(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_overall_decision_lineage() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE previous_decision student_overall_decisions%ROWTYPE;
BEGIN
    IF NEW.decision_version = 1 THEN RETURN NEW; END IF;
    SELECT * INTO previous_decision FROM student_overall_decisions WHERE id = NEW.supersedes_decision_id;
    IF previous_decision.id IS NULL OR previous_decision.registration_roster_import_id <> NEW.registration_roster_import_id
       OR previous_decision.decision_version + 1 <> NEW.decision_version
       OR previous_decision.status NOT IN ('PUBLISHED', 'REJECTED') THEN
        RAISE EXCEPTION 'Progression decision lineage must supersede the latest terminal decision';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: validate_overall_decision_result_snapshot(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_overall_decision_result_snapshot() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE valid_snapshot boolean;
BEGIN
    SELECT true INTO valid_snapshot
    FROM student_overall_decisions decision
    JOIN published_results published_result ON published_result.id = NEW.published_result_id
    JOIN module_results module_result ON module_result.id = published_result.module_result_id
    JOIN assessment_roster_entries roster_entry ON roster_entry.id = module_result.assessment_roster_entry_id
    WHERE decision.id = NEW.student_overall_decision_id
      AND roster_entry.id = NEW.assessment_roster_entry_id
      AND roster_entry.roster_import_id = decision.registration_roster_import_id
      AND published_result.student_id = decision.student_id
      AND published_result.academic_period_id = decision.academic_period_id
      AND NEW.module_id = published_result.module_id
      AND NEW.module_code = published_result.module_code
      AND NEW.module_name = published_result.module_name
      AND NEW.curriculum_module_type = roster_entry.curriculum_module_type
      AND NEW.credit_value = roster_entry.credit_value
      AND NEW.final_mark = published_result.final_mark
      AND NEW.grade = published_result.grade
      AND NEW.remark = published_result.remark
      AND NEW.passing = (module_result.result_status = 'PASS')
      AND NEW.publication_version = published_result.publication_version;
    IF valid_snapshot IS DISTINCT FROM true THEN
        RAISE EXCEPTION 'Progression evidence must exactly snapshot its linked roster and published result';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: validate_progression_rule_approval(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_progression_rule_approval() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE outcome_count integer; fallback_count integer; fallback_priority integer; maximum_priority integer;
BEGIN
    IF NEW.status = 'APPROVED' AND OLD.status <> 'APPROVED' THEN
        SELECT count(*), count(*) FILTER (WHERE fallback_outcome),
               max(priority) FILTER (WHERE fallback_outcome), max(priority)
        INTO outcome_count, fallback_count, fallback_priority, maximum_priority
        FROM progression_rule_outcomes
        WHERE progression_rule_set_id = NEW.id AND deleted_at IS NULL;
        IF outcome_count < 2 OR fallback_count <> 1 OR fallback_priority <> maximum_priority THEN
            RAISE EXCEPTION 'Approved progression rules require ordered outcomes and exactly one final fallback';
        END IF;
        IF EXISTS (
            SELECT 1 FROM progression_rule_outcomes
            WHERE progression_rule_set_id = NEW.id AND deleted_at IS NULL AND NOT fallback_outcome
              AND minimum_weighted_average IS NULL AND minimum_passed_credits IS NULL
              AND maximum_failed_credits IS NULL AND maximum_failed_modules IS NULL
              AND NOT require_all_compulsory_passed
        ) THEN
            RAISE EXCEPTION 'Every non-fallback progression outcome requires at least one threshold';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: validate_published_result_amendment_evidence(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_published_result_amendment_evidence() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    original_result published_results%ROWTYPE;
    replacement_result module_results%ROWTYPE;
    replacement_batch result_batches%ROWTYPE;
    replacement_offering assessment_module_offerings%ROWTYPE;
    replacement_roster assessment_roster_entries%ROWTYPE;
    replacement_import registration_roster_imports%ROWTYPE;
BEGIN
    SELECT * INTO STRICT original_result FROM published_results WHERE id = NEW.original_published_result_id;
    IF EXISTS (
        SELECT 1 FROM published_results newer
        WHERE newer.student_id = original_result.student_id
          AND newer.module_id = original_result.module_id
          AND newer.academic_period_id = original_result.academic_period_id
          AND newer.publication_version > original_result.publication_version
          AND newer.deleted_at IS NULL
    ) THEN
        RAISE EXCEPTION 'Only the current published result version can be amended';
    END IF;

    SELECT * INTO STRICT replacement_result FROM module_results WHERE id = NEW.replacement_module_result_id;
    SELECT * INTO STRICT replacement_batch FROM result_batches WHERE id = NEW.replacement_result_batch_id;
    SELECT * INTO STRICT replacement_offering FROM assessment_module_offerings WHERE id = replacement_batch.module_offering_id;
    SELECT * INTO STRICT replacement_roster FROM assessment_roster_entries WHERE id = replacement_result.assessment_roster_entry_id;
    SELECT * INTO STRICT replacement_import FROM registration_roster_imports WHERE id = replacement_roster.roster_import_id;

    IF replacement_result.result_batch_id <> replacement_batch.id OR replacement_batch.status <> 'APPROVED' THEN
        RAISE EXCEPTION 'Replacement evidence must belong to an approved result batch';
    END IF;
    IF replacement_import.student_id <> original_result.student_id
       OR replacement_offering.module_id <> original_result.module_id
       OR replacement_offering.academic_period_id <> original_result.academic_period_id THEN
        RAISE EXCEPTION 'Replacement evidence must match the original student, Module, and academic period';
    END IF;
    IF NEW.proposed_final_mark <> replacement_result.final_mark
       OR NEW.proposed_grade <> replacement_result.grade
       OR NEW.proposed_remark <> replacement_result.remark THEN
        RAISE EXCEPTION 'Proposed result values must match the replacement result evidence';
    END IF;
    IF NEW.proposed_final_mark = original_result.final_mark
       AND NEW.proposed_grade = original_result.grade
       AND NEW.proposed_remark = original_result.remark THEN
        RAISE EXCEPTION 'A published result amendment must change the published result';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: validate_published_result_lineage(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_published_result_lineage() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    previous_result published_results%ROWTYPE;
    amendment published_result_amendments%ROWTYPE;
BEGIN
    IF NEW.publication_version = 1 THEN
        IF EXISTS (
            SELECT 1 FROM published_results existing
            WHERE existing.student_id = NEW.student_id
              AND existing.module_id = NEW.module_id
              AND existing.academic_period_id = NEW.academic_period_id
              AND existing.deleted_at IS NULL
        ) THEN
            RAISE EXCEPTION 'A published result already exists; use a governed amendment';
        END IF;
        RETURN NEW;
    END IF;

    SELECT * INTO STRICT previous_result FROM published_results WHERE id = NEW.supersedes_published_result_id;
    SELECT * INTO STRICT amendment FROM published_result_amendments WHERE id = NEW.result_amendment_id;
    IF previous_result.student_id <> NEW.student_id
       OR previous_result.module_id <> NEW.module_id
       OR previous_result.academic_period_id <> NEW.academic_period_id
       OR previous_result.publication_version + 1 <> NEW.publication_version THEN
        RAISE EXCEPTION 'Published result correction lineage is invalid';
    END IF;
    IF amendment.status <> 'APPLIED'
       OR amendment.original_published_result_id <> previous_result.id
       OR amendment.replacement_result_batch_id <> NEW.result_batch_id
       OR amendment.replacement_module_result_id <> NEW.module_result_id
       OR amendment.proposed_final_mark <> NEW.final_mark
       OR amendment.proposed_grade <> NEW.grade
       OR amendment.proposed_remark <> NEW.remark THEN
        RAISE EXCEPTION 'Published result correction does not match the applied amendment evidence';
    END IF;
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: assessment_calculation_component_evidence; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assessment_calculation_component_evidence (
    id uuid NOT NULL,
    calculation_run_id uuid CONSTRAINT assessment_calculation_component_ev_calculation_run_id_not_null NOT NULL,
    calculation_outcome_id uuid CONSTRAINT assessment_calculation_componen_calculation_outcome_id_not_null NOT NULL,
    assessment_component_id uuid CONSTRAINT assessment_calculation_compone_assessment_component_id_not_null NOT NULL,
    submitted_mark_id uuid CONSTRAINT assessment_calculation_component_evi_submitted_mark_id_not_null NOT NULL,
    component_code character varying(30) CONSTRAINT assessment_calculation_component_eviden_component_code_not_null NOT NULL,
    component_type character varying(30) CONSTRAINT assessment_calculation_component_eviden_component_type_not_null NOT NULL,
    score numeric(8,2) NOT NULL,
    maximum_mark numeric(8,2) NOT NULL,
    weight_percent numeric(5,2) CONSTRAINT assessment_calculation_component_eviden_weight_percent_not_null NOT NULL,
    weighted_contribution numeric(6,2) CONSTRAINT assessment_calculation_component_weighted_contribution_not_null NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_calculation_component_evidence_values CHECK (((score >= (0)::numeric) AND (maximum_mark > (0)::numeric) AND (score <= maximum_mark) AND (weight_percent > (0)::numeric) AND (weight_percent <= (100)::numeric) AND (weighted_contribution >= (0)::numeric) AND (weighted_contribution <= (100)::numeric)))
);


--
-- Name: assessment_calculation_component_evidence_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assessment_calculation_component_evidence_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    calculation_run_id uuid,
    calculation_outcome_id uuid,
    assessment_component_id uuid,
    submitted_mark_id uuid,
    component_code character varying(30),
    component_type character varying(30),
    score numeric(8,2),
    maximum_mark numeric(8,2),
    weight_percent numeric(5,2),
    weighted_contribution numeric(6,2),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: assessment_calculation_outcomes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assessment_calculation_outcomes (
    id uuid NOT NULL,
    calculation_run_id uuid NOT NULL,
    assessment_roster_entry_id uuid CONSTRAINT assessment_calculation_outc_assessment_roster_entry_id_not_null NOT NULL,
    weighted_total numeric(6,2),
    is_complete boolean NOT NULL,
    missing_component_codes character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_assessment_calculation_completeness CHECK (((is_complete AND (weighted_total IS NOT NULL) AND (missing_component_codes IS NULL)) OR ((NOT is_complete) AND (weighted_total IS NULL) AND (length(TRIM(BOTH FROM missing_component_codes)) > 0)))),
    CONSTRAINT ck_assessment_calculation_total CHECK (((weighted_total IS NULL) OR ((weighted_total >= (0)::numeric) AND (weighted_total <= (100)::numeric))))
);


--
-- Name: assessment_calculation_outcomes_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assessment_calculation_outcomes_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    calculation_run_id uuid,
    assessment_roster_entry_id uuid,
    weighted_total numeric(6,2),
    is_complete boolean,
    missing_component_codes character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: assessment_calculation_runs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assessment_calculation_runs (
    id uuid NOT NULL,
    module_offering_id uuid NOT NULL,
    assessment_scheme_id uuid NOT NULL,
    rule_snapshot jsonb NOT NULL,
    roster_count integer NOT NULL,
    complete_result_count integer NOT NULL,
    incomplete_result_count integer NOT NULL,
    status character varying(20) NOT NULL,
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
    CONSTRAINT ck_assessment_calculation_completion CHECK (((((status)::text = 'RUNNING'::text) AND (completed_at IS NULL)) OR (((status)::text = ANY ((ARRAY['COMPLETED'::character varying, 'FAILED'::character varying])::text[])) AND (completed_at IS NOT NULL)))),
    CONSTRAINT ck_assessment_calculation_counts CHECK (((roster_count >= 0) AND (complete_result_count >= 0) AND (incomplete_result_count >= 0) AND (roster_count = (complete_result_count + incomplete_result_count)))),
    CONSTRAINT ck_assessment_calculation_status CHECK (((status)::text = ANY ((ARRAY['RUNNING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: assessment_calculation_runs_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assessment_calculation_runs_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    module_offering_id uuid,
    assessment_scheme_id uuid,
    rule_snapshot jsonb,
    roster_count integer,
    complete_result_count integer,
    incomplete_result_count integer,
    status character varying(20),
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
-- Name: assessment_components; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assessment_components (
    id uuid NOT NULL,
    assessment_scheme_id uuid NOT NULL,
    code character varying(30) NOT NULL,
    name character varying(150) NOT NULL,
    component_type character varying(30) NOT NULL,
    weight_percent numeric(5,2) NOT NULL,
    maximum_mark numeric(8,2) NOT NULL,
    capture_opens_at timestamp with time zone NOT NULL,
    capture_closes_at timestamp with time zone NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_assessment_component_maximum CHECK ((maximum_mark > (0)::numeric)),
    CONSTRAINT ck_assessment_component_sort CHECK ((sort_order > 0)),
    CONSTRAINT ck_assessment_component_type CHECK (((component_type)::text = ANY ((ARRAY['COURSEWORK'::character varying, 'PRACTICAL'::character varying, 'IN_CLASS_TEST'::character varying, 'FINAL_EXAM'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT ck_assessment_component_weight CHECK (((weight_percent > (0)::numeric) AND (weight_percent <= (100)::numeric))),
    CONSTRAINT ck_assessment_component_window CHECK ((capture_closes_at > capture_opens_at))
);


--
-- Name: assessment_components_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assessment_components_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    assessment_scheme_id uuid,
    code character varying(30),
    name character varying(150),
    component_type character varying(30),
    weight_percent numeric(5,2),
    maximum_mark numeric(8,2),
    capture_opens_at timestamp with time zone,
    capture_closes_at timestamp with time zone,
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
-- Name: assessment_module_offerings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assessment_module_offerings (
    id uuid NOT NULL,
    module_id uuid NOT NULL,
    module_code character varying(50) NOT NULL,
    module_name character varying(200) NOT NULL,
    academic_period_id uuid NOT NULL,
    academic_period_code character varying(50) NOT NULL,
    academic_period_name character varying(150) NOT NULL,
    assigned_instructor_user_id uuid CONSTRAINT assessment_module_offerings_assigned_instructor_user_i_not_null NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_assessment_offering_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'CLOSED'::character varying])::text[])))
);


--
-- Name: assessment_module_offerings_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assessment_module_offerings_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    module_id uuid,
    module_code character varying(50),
    module_name character varying(200),
    academic_period_id uuid,
    academic_period_code character varying(50),
    academic_period_name character varying(150),
    assigned_instructor_user_id uuid,
    status character varying(20),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: assessment_roster_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assessment_roster_entries (
    id uuid NOT NULL,
    roster_import_id uuid NOT NULL,
    registration_module_id uuid NOT NULL,
    curriculum_module_id uuid NOT NULL,
    module_id uuid NOT NULL,
    module_code character varying(50) NOT NULL,
    module_name character varying(200) NOT NULL,
    curriculum_module_type character varying(20) NOT NULL,
    credit_value numeric(6,2) NOT NULL,
    minimum_mark_required numeric(5,2),
    eligibility_status character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_assessment_roster_credit CHECK ((credit_value > (0)::numeric)),
    CONSTRAINT ck_assessment_roster_eligibility CHECK (((eligibility_status)::text = ANY ((ARRAY['ELIGIBLE'::character varying, 'WITHDRAWN'::character varying])::text[]))),
    CONSTRAINT ck_assessment_roster_minimum_mark CHECK (((minimum_mark_required IS NULL) OR ((minimum_mark_required >= (0)::numeric) AND (minimum_mark_required <= (100)::numeric)))),
    CONSTRAINT ck_assessment_roster_module_type CHECK (((curriculum_module_type)::text = ANY ((ARRAY['COMPULSORY'::character varying, 'ELECTIVE'::character varying, 'OPTIONAL'::character varying])::text[])))
);


--
-- Name: assessment_roster_entries_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assessment_roster_entries_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    roster_import_id uuid,
    registration_module_id uuid,
    curriculum_module_id uuid,
    module_id uuid,
    module_code character varying(50),
    module_name character varying(200),
    curriculum_module_type character varying(20),
    credit_value numeric(6,2),
    minimum_mark_required numeric(5,2),
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
-- Name: assessment_schemes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assessment_schemes (
    id uuid NOT NULL,
    module_offering_id uuid NOT NULL,
    scheme_version integer NOT NULL,
    name character varying(150) NOT NULL,
    status character varying(20) NOT NULL,
    approval_reason character varying(1000),
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_assessment_scheme_approval CHECK (((((status)::text = 'DRAFT'::text) AND (approved_by_user_id IS NULL) AND (approved_at IS NULL) AND (approval_reason IS NULL)) OR (((status)::text = ANY ((ARRAY['APPROVED'::character varying, 'SUPERSEDED'::character varying])::text[])) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (length(TRIM(BOTH FROM approval_reason)) > 0)))),
    CONSTRAINT ck_assessment_scheme_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'APPROVED'::character varying, 'SUPERSEDED'::character varying])::text[]))),
    CONSTRAINT ck_assessment_scheme_version CHECK ((scheme_version > 0))
);


--
-- Name: assessment_schemes_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assessment_schemes_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    module_offering_id uuid,
    scheme_version integer,
    name character varying(150),
    status character varying(20),
    approval_reason character varying(1000),
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: grading_bands; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grading_bands (
    id uuid NOT NULL,
    grading_scheme_id uuid NOT NULL,
    minimum_mark numeric(6,2) NOT NULL,
    maximum_mark numeric(6,2) NOT NULL,
    grade character varying(10) NOT NULL,
    remark character varying(100) NOT NULL,
    passing boolean NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_grading_band_range CHECK (((minimum_mark >= (0)::numeric) AND (maximum_mark <= (100)::numeric) AND (maximum_mark >= minimum_mark))),
    CONSTRAINT ck_grading_band_sort CHECK ((sort_order > 0))
);


--
-- Name: grading_bands_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grading_bands_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    grading_scheme_id uuid,
    minimum_mark numeric(6,2),
    maximum_mark numeric(6,2),
    grade character varying(10),
    remark character varying(100),
    passing boolean,
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
-- Name: grading_schemes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grading_schemes (
    id uuid NOT NULL,
    code character varying(30) NOT NULL,
    name character varying(150) NOT NULL,
    scheme_version integer NOT NULL,
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
    CONSTRAINT ck_grading_scheme_approval CHECK (((((status)::text = 'DRAFT'::text) AND (approved_at IS NULL)) OR (((status)::text = ANY ((ARRAY['APPROVED'::character varying, 'SUPERSEDED'::character varying])::text[])) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (length(TRIM(BOTH FROM approval_reason)) > 0)))),
    CONSTRAINT ck_grading_scheme_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'APPROVED'::character varying, 'SUPERSEDED'::character varying])::text[])))
);


--
-- Name: grading_schemes_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grading_schemes_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code character varying(30),
    name character varying(150),
    scheme_version integer,
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
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone NOT NULL,
    published_at timestamp with time zone,
    last_error character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT integration_outbox_attempt_count_check CHECK ((attempt_count >= 0)),
    CONSTRAINT integration_outbox_publication_check CHECK (((((status)::text = 'PUBLISHED'::text) AND (published_at IS NOT NULL)) OR (((status)::text <> 'PUBLISHED'::text) AND (published_at IS NULL)))),
    CONSTRAINT integration_outbox_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PUBLISHED'::character varying, 'DEAD'::character varying])::text[])))
);


--
-- Name: mark_amendment_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mark_amendment_requests (
    id uuid NOT NULL,
    original_mark_id uuid NOT NULL,
    proposed_score numeric(8,2) NOT NULL,
    reason character varying(1000) NOT NULL,
    status character varying(20) NOT NULL,
    requested_by_user_id uuid NOT NULL,
    requested_at timestamp with time zone NOT NULL,
    decided_by_user_id uuid,
    decided_at timestamp with time zone,
    decision_reason character varying(1000),
    replacement_mark_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_mark_amendment_decision CHECK (((((status)::text = 'REQUESTED'::text) AND (decided_by_user_id IS NULL) AND (decided_at IS NULL) AND (decision_reason IS NULL) AND (replacement_mark_id IS NULL)) OR (((status)::text = 'APPROVED'::text) AND (decided_by_user_id IS NOT NULL) AND (decided_at IS NOT NULL) AND (length(TRIM(BOTH FROM decision_reason)) > 0) AND (replacement_mark_id IS NOT NULL)) OR (((status)::text = 'REJECTED'::text) AND (decided_by_user_id IS NOT NULL) AND (decided_at IS NOT NULL) AND (length(TRIM(BOTH FROM decision_reason)) > 0) AND (replacement_mark_id IS NULL)))),
    CONSTRAINT ck_mark_amendment_reason CHECK ((length(TRIM(BOTH FROM reason)) > 0)),
    CONSTRAINT ck_mark_amendment_score CHECK ((proposed_score >= (0)::numeric)),
    CONSTRAINT ck_mark_amendment_status CHECK (((status)::text = ANY ((ARRAY['REQUESTED'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: mark_amendment_requests_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mark_amendment_requests_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    original_mark_id uuid,
    proposed_score numeric(8,2),
    reason character varying(1000),
    status character varying(20),
    requested_by_user_id uuid,
    requested_at timestamp with time zone,
    decided_by_user_id uuid,
    decided_at timestamp with time zone,
    decision_reason character varying(1000),
    replacement_mark_id uuid,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: module_results; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.module_results (
    id uuid NOT NULL,
    result_batch_id uuid NOT NULL,
    calculation_outcome_id uuid NOT NULL,
    assessment_roster_entry_id uuid NOT NULL,
    coursework_mark numeric(6,2) NOT NULL,
    examination_mark numeric(6,2) NOT NULL,
    final_mark numeric(6,2) NOT NULL,
    grade character varying(10) NOT NULL,
    remark character varying(100) NOT NULL,
    result_status character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_module_result_marks CHECK ((((coursework_mark >= (0)::numeric) AND (coursework_mark <= (100)::numeric)) AND ((examination_mark >= (0)::numeric) AND (examination_mark <= (100)::numeric)) AND ((final_mark >= (0)::numeric) AND (final_mark <= (100)::numeric)))),
    CONSTRAINT ck_module_result_status CHECK (((result_status)::text = ANY ((ARRAY['PASS'::character varying, 'FAIL'::character varying])::text[])))
);


--
-- Name: module_results_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.module_results_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    result_batch_id uuid,
    calculation_outcome_id uuid,
    assessment_roster_entry_id uuid,
    coursework_mark numeric(6,2),
    examination_mark numeric(6,2),
    final_mark numeric(6,2),
    grade character varying(10),
    remark character varying(100),
    result_status character varying(20),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: progression_rule_outcomes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.progression_rule_outcomes (
    id uuid NOT NULL,
    progression_rule_set_id uuid NOT NULL,
    priority integer NOT NULL,
    decision_code character varying(30) NOT NULL,
    decision_label character varying(150) NOT NULL,
    minimum_weighted_average numeric(6,2),
    minimum_passed_credits numeric(8,2),
    maximum_failed_credits numeric(8,2),
    maximum_failed_modules integer,
    require_all_compulsory_passed boolean CONSTRAINT progression_rule_outcomes_require_all_compulsory_passe_not_null NOT NULL,
    next_programme_period_number integer,
    fallback_outcome boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_progression_rule_outcome_average CHECK (((minimum_weighted_average IS NULL) OR ((minimum_weighted_average >= (0)::numeric) AND (minimum_weighted_average <= (100)::numeric)))),
    CONSTRAINT ck_progression_rule_outcome_credits CHECK ((((minimum_passed_credits IS NULL) OR (minimum_passed_credits >= (0)::numeric)) AND ((maximum_failed_credits IS NULL) OR (maximum_failed_credits >= (0)::numeric)))),
    CONSTRAINT ck_progression_rule_outcome_decision CHECK (((decision_code)::text = ANY ((ARRAY['PROCEED'::character varying, 'PROCEED_WITH_CARRY'::character varying, 'REPEAT'::character varying, 'EXCLUDE'::character varying])::text[]))),
    CONSTRAINT ck_progression_rule_outcome_failed_modules CHECK (((maximum_failed_modules IS NULL) OR (maximum_failed_modules >= 0))),
    CONSTRAINT ck_progression_rule_outcome_fallback CHECK (((NOT fallback_outcome) OR ((minimum_weighted_average IS NULL) AND (minimum_passed_credits IS NULL) AND (maximum_failed_credits IS NULL) AND (maximum_failed_modules IS NULL) AND (NOT require_all_compulsory_passed)))),
    CONSTRAINT ck_progression_rule_outcome_next_period CHECK (((next_programme_period_number IS NULL) OR (next_programme_period_number > 0))),
    CONSTRAINT ck_progression_rule_outcome_priority CHECK ((priority > 0))
);


--
-- Name: progression_rule_outcomes_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.progression_rule_outcomes_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    progression_rule_set_id uuid,
    priority integer,
    decision_code character varying(30),
    decision_label character varying(150),
    minimum_weighted_average numeric(6,2),
    minimum_passed_credits numeric(8,2),
    maximum_failed_credits numeric(8,2),
    maximum_failed_modules integer,
    require_all_compulsory_passed boolean,
    next_programme_period_number integer,
    fallback_outcome boolean,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: progression_rule_sets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.progression_rule_sets (
    id uuid NOT NULL,
    rule_code character varying(40) NOT NULL,
    rule_name character varying(180) NOT NULL,
    programme_id uuid NOT NULL,
    programme_version_id uuid NOT NULL,
    programme_period_number integer NOT NULL,
    rule_version integer NOT NULL,
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
    CONSTRAINT ck_progression_rule_approval CHECK (((((status)::text = 'DRAFT'::text) AND (approved_by_user_id IS NULL) AND (approved_at IS NULL) AND (approval_reason IS NULL)) OR (((status)::text = ANY ((ARRAY['APPROVED'::character varying, 'SUPERSEDED'::character varying])::text[])) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (length(TRIM(BOTH FROM approval_reason)) > 0)))),
    CONSTRAINT ck_progression_rule_period CHECK ((programme_period_number > 0)),
    CONSTRAINT ck_progression_rule_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'APPROVED'::character varying, 'SUPERSEDED'::character varying])::text[]))),
    CONSTRAINT ck_progression_rule_version CHECK ((rule_version > 0))
);


--
-- Name: progression_rule_sets_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.progression_rule_sets_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    rule_code character varying(40),
    rule_name character varying(180),
    programme_id uuid,
    programme_version_id uuid,
    programme_period_number integer,
    rule_version integer,
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
-- Name: published_result_amendment_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.published_result_amendment_events (
    id uuid NOT NULL,
    published_result_amendment_id uuid CONSTRAINT published_result_amendment__published_result_amendment_not_null NOT NULL,
    from_status character varying(20),
    to_status character varying(20) NOT NULL,
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
    CONSTRAINT ck_published_result_amendment_event_reason CHECK ((length(TRIM(BOTH FROM reason)) > 0)),
    CONSTRAINT ck_published_result_amendment_event_status CHECK ((((from_status IS NULL) OR ((from_status)::text = ANY ((ARRAY['REQUESTED'::character varying, 'REVIEWED'::character varying, 'APPROVED'::character varying, 'APPLIED'::character varying, 'REJECTED'::character varying])::text[]))) AND ((to_status)::text = ANY ((ARRAY['REQUESTED'::character varying, 'REVIEWED'::character varying, 'APPROVED'::character varying, 'APPLIED'::character varying, 'REJECTED'::character varying])::text[]))))
);


--
-- Name: published_result_amendment_events_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.published_result_amendment_events_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    published_result_amendment_id uuid,
    from_status character varying(20),
    to_status character varying(20),
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
-- Name: published_result_amendments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.published_result_amendments (
    id uuid NOT NULL,
    amendment_number character varying(60) NOT NULL,
    original_published_result_id uuid CONSTRAINT published_result_amendments_original_published_result__not_null NOT NULL,
    replacement_result_batch_id uuid CONSTRAINT published_result_amendments_replacement_result_batch_i_not_null NOT NULL,
    replacement_module_result_id uuid CONSTRAINT published_result_amendments_replacement_module_result__not_null NOT NULL,
    proposed_final_mark numeric(6,2) NOT NULL,
    proposed_grade character varying(10) NOT NULL,
    proposed_remark character varying(100) NOT NULL,
    request_reason character varying(1000) NOT NULL,
    status character varying(20) NOT NULL,
    requested_by_user_id uuid NOT NULL,
    requested_at timestamp with time zone NOT NULL,
    reviewed_by_user_id uuid,
    reviewed_at timestamp with time zone,
    review_reason character varying(1000),
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    approval_reason character varying(1000),
    applied_by_user_id uuid,
    applied_at timestamp with time zone,
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
    CONSTRAINT ck_published_result_amendment_mark CHECK (((proposed_final_mark >= (0)::numeric) AND (proposed_final_mark <= (100)::numeric))),
    CONSTRAINT ck_published_result_amendment_separation CHECK ((((reviewed_by_user_id IS NULL) OR (reviewed_by_user_id <> requested_by_user_id)) AND ((approved_by_user_id IS NULL) OR ((approved_by_user_id <> requested_by_user_id) AND (approved_by_user_id <> reviewed_by_user_id))) AND ((applied_by_user_id IS NULL) OR (applied_by_user_id <> approved_by_user_id)) AND ((rejected_by_user_id IS NULL) OR (rejected_by_user_id <> requested_by_user_id)))),
    CONSTRAINT ck_published_result_amendment_stage_evidence CHECK (((((status)::text = 'REQUESTED'::text) AND (reviewed_at IS NULL) AND (approved_at IS NULL) AND (applied_at IS NULL) AND (rejected_at IS NULL)) OR (((status)::text = 'REVIEWED'::text) AND (reviewed_by_user_id IS NOT NULL) AND (reviewed_at IS NOT NULL) AND (length(TRIM(BOTH FROM review_reason)) > 0) AND (approved_at IS NULL) AND (applied_at IS NULL) AND (rejected_at IS NULL)) OR (((status)::text = 'APPROVED'::text) AND (reviewed_by_user_id IS NOT NULL) AND (reviewed_at IS NOT NULL) AND (length(TRIM(BOTH FROM review_reason)) > 0) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (length(TRIM(BOTH FROM approval_reason)) > 0) AND (applied_at IS NULL) AND (rejected_at IS NULL)) OR (((status)::text = 'APPLIED'::text) AND (reviewed_by_user_id IS NOT NULL) AND (reviewed_at IS NOT NULL) AND (length(TRIM(BOTH FROM review_reason)) > 0) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (length(TRIM(BOTH FROM approval_reason)) > 0) AND (applied_by_user_id IS NOT NULL) AND (applied_at IS NOT NULL) AND (rejected_at IS NULL)) OR (((status)::text = 'REJECTED'::text) AND (rejected_by_user_id IS NOT NULL) AND (rejected_at IS NOT NULL) AND (length(TRIM(BOTH FROM rejection_reason)) > 0) AND (approved_at IS NULL) AND (applied_at IS NULL)))),
    CONSTRAINT ck_published_result_amendment_status CHECK (((status)::text = ANY ((ARRAY['REQUESTED'::character varying, 'REVIEWED'::character varying, 'APPROVED'::character varying, 'APPLIED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT ck_published_result_amendment_text CHECK (((length(TRIM(BOTH FROM request_reason)) > 0) AND (length(TRIM(BOTH FROM proposed_grade)) > 0) AND (length(TRIM(BOTH FROM proposed_remark)) > 0)))
);


--
-- Name: published_result_amendments_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.published_result_amendments_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    amendment_number character varying(60),
    original_published_result_id uuid,
    replacement_result_batch_id uuid,
    replacement_module_result_id uuid,
    proposed_final_mark numeric(6,2),
    proposed_grade character varying(10),
    proposed_remark character varying(100),
    request_reason character varying(1000),
    status character varying(20),
    requested_by_user_id uuid,
    requested_at timestamp with time zone,
    reviewed_by_user_id uuid,
    reviewed_at timestamp with time zone,
    review_reason character varying(1000),
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    approval_reason character varying(1000),
    applied_by_user_id uuid,
    applied_at timestamp with time zone,
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
-- Name: published_results; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.published_results (
    id uuid NOT NULL,
    result_batch_id uuid NOT NULL,
    module_result_id uuid NOT NULL,
    student_id uuid NOT NULL,
    student_number character varying(40) NOT NULL,
    module_id uuid NOT NULL,
    module_code character varying(50) NOT NULL,
    module_name character varying(200) NOT NULL,
    academic_period_id uuid NOT NULL,
    academic_period_code character varying(50) NOT NULL,
    final_mark numeric(6,2) NOT NULL,
    grade character varying(10) NOT NULL,
    remark character varying(100) NOT NULL,
    published_by_user_id uuid NOT NULL,
    published_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    publication_version integer DEFAULT 1 NOT NULL,
    supersedes_published_result_id uuid,
    result_amendment_id uuid,
    CONSTRAINT ck_published_result_mark CHECK (((final_mark >= (0)::numeric) AND (final_mark <= (100)::numeric))),
    CONSTRAINT ck_published_result_version_lineage CHECK (((publication_version > 0) AND (((publication_version = 1) AND (supersedes_published_result_id IS NULL) AND (result_amendment_id IS NULL)) OR ((publication_version > 1) AND (supersedes_published_result_id IS NOT NULL) AND (result_amendment_id IS NOT NULL)))))
);


--
-- Name: published_results_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.published_results_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    result_batch_id uuid,
    module_result_id uuid,
    student_id uuid,
    student_number character varying(40),
    module_id uuid,
    module_code character varying(50),
    module_name character varying(200),
    academic_period_id uuid,
    academic_period_code character varying(50),
    final_mark numeric(6,2),
    grade character varying(10),
    remark character varying(100),
    published_by_user_id uuid,
    published_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    publication_version integer,
    supersedes_published_result_id uuid,
    result_amendment_id uuid
);


--
-- Name: registration_roster_imports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.registration_roster_imports (
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
    programme_period_number integer NOT NULL,
    imported_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_registration_roster_imports_dates CHECK ((academic_period_ends_on >= academic_period_starts_on)),
    CONSTRAINT ck_registration_roster_imports_period CHECK ((programme_period_number > 0))
);


--
-- Name: registration_roster_imports_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.registration_roster_imports_aud (
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
    programme_period_number integer,
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
-- Name: result_batch_status_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.result_batch_status_events (
    id uuid NOT NULL,
    result_batch_id uuid NOT NULL,
    from_status character varying(20),
    to_status character varying(20) NOT NULL,
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
    CONSTRAINT ck_result_event_reason CHECK ((length(TRIM(BOTH FROM reason)) > 0)),
    CONSTRAINT ck_result_event_statuses CHECK ((((from_status IS NULL) OR ((from_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'SUBMITTED'::character varying, 'MODERATED'::character varying, 'APPROVED'::character varying, 'PUBLISHED'::character varying, 'REJECTED'::character varying])::text[]))) AND ((to_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'SUBMITTED'::character varying, 'MODERATED'::character varying, 'APPROVED'::character varying, 'PUBLISHED'::character varying, 'REJECTED'::character varying])::text[]))))
);


--
-- Name: result_batch_status_events_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.result_batch_status_events_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    result_batch_id uuid,
    from_status character varying(20),
    to_status character varying(20),
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
-- Name: result_batches; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.result_batches (
    id uuid NOT NULL,
    module_offering_id uuid NOT NULL,
    calculation_run_id uuid NOT NULL,
    grading_scheme_id uuid NOT NULL,
    batch_number character varying(50) NOT NULL,
    status character varying(20) NOT NULL,
    status_reason character varying(1000) NOT NULL,
    submitted_by_user_id uuid,
    submitted_at timestamp with time zone,
    moderated_by_user_id uuid,
    moderated_at timestamp with time zone,
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    published_by_user_id uuid,
    published_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_result_batch_separation CHECK (((moderated_by_user_id IS NULL) OR ((moderated_by_user_id <> submitted_by_user_id) AND ((approved_by_user_id IS NULL) OR (approved_by_user_id <> moderated_by_user_id))))),
    CONSTRAINT ck_result_batch_stage_evidence CHECK (((((status)::text = 'DRAFT'::text) AND (submitted_at IS NULL) AND (moderated_at IS NULL) AND (approved_at IS NULL) AND (published_at IS NULL)) OR (((status)::text = ANY ((ARRAY['SUBMITTED'::character varying, 'REJECTED'::character varying])::text[])) AND (submitted_at IS NOT NULL) AND (moderated_at IS NULL) AND (approved_at IS NULL) AND (published_at IS NULL)) OR (((status)::text = 'MODERATED'::text) AND (submitted_at IS NOT NULL) AND (moderated_at IS NOT NULL) AND (approved_at IS NULL) AND (published_at IS NULL)) OR (((status)::text = 'APPROVED'::text) AND (submitted_at IS NOT NULL) AND (moderated_at IS NOT NULL) AND (approved_at IS NOT NULL) AND (published_at IS NULL)) OR (((status)::text = 'PUBLISHED'::text) AND (submitted_at IS NOT NULL) AND (moderated_at IS NOT NULL) AND (approved_at IS NOT NULL) AND (published_at IS NOT NULL)))),
    CONSTRAINT ck_result_batch_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'SUBMITTED'::character varying, 'MODERATED'::character varying, 'APPROVED'::character varying, 'PUBLISHED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: result_batches_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.result_batches_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    module_offering_id uuid,
    calculation_run_id uuid,
    grading_scheme_id uuid,
    batch_number character varying(50),
    status character varying(20),
    status_reason character varying(1000),
    submitted_by_user_id uuid,
    submitted_at timestamp with time zone,
    moderated_by_user_id uuid,
    moderated_at timestamp with time zone,
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    published_by_user_id uuid,
    published_at timestamp with time zone,
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
    service_name character varying(100) DEFAULT 'assessment-results-service'::character varying NOT NULL,
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
-- Name: student_assessment_marks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_assessment_marks (
    id uuid NOT NULL,
    assessment_component_id uuid NOT NULL,
    assessment_roster_entry_id uuid NOT NULL,
    revision_number integer NOT NULL,
    supersedes_mark_id uuid,
    score numeric(8,2) NOT NULL,
    status character varying(20) NOT NULL,
    capture_method character varying(20) NOT NULL,
    captured_by_user_id uuid NOT NULL,
    captured_at timestamp with time zone NOT NULL,
    submitted_by_user_id uuid,
    submitted_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_student_assessment_mark_method CHECK (((capture_method)::text = ANY ((ARRAY['MANUAL'::character varying, 'UPLOAD'::character varying, 'AMENDMENT'::character varying])::text[]))),
    CONSTRAINT ck_student_assessment_mark_revision CHECK ((revision_number > 0)),
    CONSTRAINT ck_student_assessment_mark_score CHECK ((score >= (0)::numeric)),
    CONSTRAINT ck_student_assessment_mark_status CHECK (((status)::text = ANY ((ARRAY['CAPTURED'::character varying, 'SUBMITTED'::character varying, 'SUPERSEDED'::character varying])::text[]))),
    CONSTRAINT ck_student_assessment_mark_submission CHECK (((((status)::text = 'CAPTURED'::text) AND (submitted_by_user_id IS NULL) AND (submitted_at IS NULL)) OR (((status)::text = ANY ((ARRAY['SUBMITTED'::character varying, 'SUPERSEDED'::character varying])::text[])) AND (submitted_by_user_id IS NOT NULL) AND (submitted_at IS NOT NULL)))),
    CONSTRAINT ck_student_assessment_mark_supersession CHECK ((((revision_number = 1) AND (supersedes_mark_id IS NULL)) OR ((revision_number > 1) AND (supersedes_mark_id IS NOT NULL))))
);


--
-- Name: student_assessment_marks_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_assessment_marks_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    assessment_component_id uuid,
    assessment_roster_entry_id uuid,
    revision_number integer,
    supersedes_mark_id uuid,
    score numeric(8,2),
    status character varying(20),
    capture_method character varying(20),
    captured_by_user_id uuid,
    captured_at timestamp with time zone,
    submitted_by_user_id uuid,
    submitted_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: student_overall_decision_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_overall_decision_events (
    id uuid NOT NULL,
    student_overall_decision_id uuid CONSTRAINT student_overall_decision_ev_student_overall_decision_i_not_null NOT NULL,
    from_status character varying(20),
    to_status character varying(20) NOT NULL,
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
    CONSTRAINT ck_overall_decision_event_reason CHECK ((length(TRIM(BOTH FROM reason)) > 0)),
    CONSTRAINT ck_overall_decision_event_status CHECK ((((from_status IS NULL) OR ((from_status)::text = ANY ((ARRAY['CALCULATED'::character varying, 'REVIEWED'::character varying, 'APPROVED'::character varying, 'PUBLISHED'::character varying, 'REJECTED'::character varying])::text[]))) AND ((to_status)::text = ANY ((ARRAY['CALCULATED'::character varying, 'REVIEWED'::character varying, 'APPROVED'::character varying, 'PUBLISHED'::character varying, 'REJECTED'::character varying])::text[]))))
);


--
-- Name: student_overall_decision_events_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_overall_decision_events_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    student_overall_decision_id uuid,
    from_status character varying(20),
    to_status character varying(20),
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
-- Name: student_overall_decision_results; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_overall_decision_results (
    id uuid NOT NULL,
    student_overall_decision_id uuid CONSTRAINT student_overall_decision_re_student_overall_decision_i_not_null NOT NULL,
    published_result_id uuid NOT NULL,
    assessment_roster_entry_id uuid CONSTRAINT student_overall_decision_re_assessment_roster_entry_id_not_null NOT NULL,
    module_id uuid NOT NULL,
    module_code character varying(50) NOT NULL,
    module_name character varying(200) NOT NULL,
    curriculum_module_type character varying(20) CONSTRAINT student_overall_decision_result_curriculum_module_type_not_null NOT NULL,
    credit_value numeric(6,2) NOT NULL,
    final_mark numeric(6,2) NOT NULL,
    grade character varying(10) NOT NULL,
    remark character varying(100) NOT NULL,
    passing boolean NOT NULL,
    publication_version integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_overall_decision_result_type CHECK (((curriculum_module_type)::text = ANY ((ARRAY['COMPULSORY'::character varying, 'ELECTIVE'::character varying, 'OPTIONAL'::character varying])::text[]))),
    CONSTRAINT ck_overall_decision_result_values CHECK (((credit_value > (0)::numeric) AND ((final_mark >= (0)::numeric) AND (final_mark <= (100)::numeric)) AND (publication_version > 0)))
);


--
-- Name: student_overall_decision_results_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_overall_decision_results_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    student_overall_decision_id uuid,
    published_result_id uuid,
    assessment_roster_entry_id uuid,
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
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: student_overall_decisions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_overall_decisions (
    id uuid NOT NULL,
    progression_rule_set_id uuid NOT NULL,
    registration_roster_import_id uuid CONSTRAINT student_overall_decisions_registration_roster_import_i_not_null NOT NULL,
    decision_number character varying(80) NOT NULL,
    decision_version integer NOT NULL,
    supersedes_decision_id uuid,
    student_id uuid NOT NULL,
    student_number character varying(40) NOT NULL,
    programme_enrolment_id uuid NOT NULL,
    programme_id uuid NOT NULL,
    programme_version_id uuid NOT NULL,
    academic_period_id uuid NOT NULL,
    academic_period_code character varying(50) NOT NULL,
    programme_period_number integer NOT NULL,
    matched_outcome_id uuid NOT NULL,
    decision_code character varying(30) NOT NULL,
    decision_label character varying(150) NOT NULL,
    next_programme_period_number integer,
    attempted_credits numeric(8,2) NOT NULL,
    passed_credits numeric(8,2) NOT NULL,
    failed_credits numeric(8,2) NOT NULL,
    failed_modules integer NOT NULL,
    failed_compulsory_modules integer NOT NULL,
    weighted_average numeric(6,2) NOT NULL,
    status character varying(20) NOT NULL,
    status_reason character varying(1000) NOT NULL,
    calculated_by_user_id uuid NOT NULL,
    calculated_at timestamp with time zone NOT NULL,
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
    CONSTRAINT ck_student_overall_decision_code CHECK (((decision_code)::text = ANY ((ARRAY['PROCEED'::character varying, 'PROCEED_WITH_CARRY'::character varying, 'REPEAT'::character varying, 'EXCLUDE'::character varying])::text[]))),
    CONSTRAINT ck_student_overall_decision_lineage CHECK ((((decision_version = 1) AND (supersedes_decision_id IS NULL)) OR ((decision_version > 1) AND (supersedes_decision_id IS NOT NULL)))),
    CONSTRAINT ck_student_overall_decision_metrics CHECK (((attempted_credits > (0)::numeric) AND (passed_credits >= (0)::numeric) AND (failed_credits >= (0)::numeric) AND ((passed_credits + failed_credits) = attempted_credits) AND (failed_modules >= 0) AND (failed_compulsory_modules >= 0) AND (failed_compulsory_modules <= failed_modules) AND ((weighted_average >= (0)::numeric) AND (weighted_average <= (100)::numeric)))),
    CONSTRAINT ck_student_overall_decision_period CHECK ((programme_period_number > 0)),
    CONSTRAINT ck_student_overall_decision_separation CHECK ((((reviewed_by_user_id IS NULL) OR (reviewed_by_user_id <> calculated_by_user_id)) AND ((approved_by_user_id IS NULL) OR ((approved_by_user_id <> calculated_by_user_id) AND (approved_by_user_id <> reviewed_by_user_id))) AND ((published_by_user_id IS NULL) OR ((published_by_user_id <> calculated_by_user_id) AND (published_by_user_id <> reviewed_by_user_id) AND (published_by_user_id <> approved_by_user_id))))),
    CONSTRAINT ck_student_overall_decision_status CHECK (((status)::text = ANY ((ARRAY['CALCULATED'::character varying, 'REVIEWED'::character varying, 'APPROVED'::character varying, 'PUBLISHED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT ck_student_overall_decision_version CHECK ((decision_version > 0)),
    CONSTRAINT ck_student_overall_decision_workflow CHECK (((((status)::text = 'CALCULATED'::text) AND (reviewed_at IS NULL) AND (approved_at IS NULL) AND (published_at IS NULL) AND (rejected_at IS NULL)) OR (((status)::text = 'REVIEWED'::text) AND (reviewed_at IS NOT NULL) AND (approved_at IS NULL) AND (published_at IS NULL) AND (rejected_at IS NULL)) OR (((status)::text = 'APPROVED'::text) AND (reviewed_at IS NOT NULL) AND (approved_at IS NOT NULL) AND (published_at IS NULL) AND (rejected_at IS NULL)) OR (((status)::text = 'PUBLISHED'::text) AND (reviewed_at IS NOT NULL) AND (approved_at IS NOT NULL) AND (published_at IS NOT NULL) AND (rejected_at IS NULL)) OR (((status)::text = 'REJECTED'::text) AND (rejected_at IS NOT NULL) AND (published_at IS NULL))))
);


--
-- Name: student_overall_decisions_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_overall_decisions_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    progression_rule_set_id uuid,
    registration_roster_import_id uuid,
    decision_number character varying(80),
    decision_version integer,
    supersedes_decision_id uuid,
    student_id uuid,
    student_number character varying(40),
    programme_enrolment_id uuid,
    programme_id uuid,
    programme_version_id uuid,
    academic_period_id uuid,
    academic_period_code character varying(50),
    programme_period_number integer,
    matched_outcome_id uuid,
    decision_code character varying(30),
    decision_label character varying(150),
    next_programme_period_number integer,
    attempted_credits numeric(8,2),
    passed_credits numeric(8,2),
    failed_credits numeric(8,2),
    failed_modules integer,
    failed_compulsory_modules integer,
    weighted_average numeric(6,2),
    status character varying(20),
    status_reason character varying(1000),
    calculated_by_user_id uuid,
    calculated_at timestamp with time zone,
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
-- Data for Name: assessment_calculation_component_evidence; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: assessment_calculation_component_evidence_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: assessment_calculation_outcomes; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: assessment_calculation_outcomes_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: assessment_calculation_runs; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: assessment_calculation_runs_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: assessment_components; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: assessment_components_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: assessment_module_offerings; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: assessment_module_offerings_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: assessment_roster_entries; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: assessment_roster_entries_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: assessment_schemes; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: assessment_schemes_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: grading_bands; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: grading_bands_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: grading_schemes; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: grading_schemes_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: integration_inbox; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: integration_outbox; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: mark_amendment_requests; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: mark_amendment_requests_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: module_results; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: module_results_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: progression_rule_outcomes; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: progression_rule_outcomes_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: progression_rule_sets; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: progression_rule_sets_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: published_result_amendment_events; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: published_result_amendment_events_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: published_result_amendments; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: published_result_amendments_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: published_results; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: published_results_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: registration_roster_imports; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: registration_roster_imports_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: result_batch_status_events; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: result_batch_status_events_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: result_batches; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: result_batches_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: revinfo; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_assessment_marks; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_assessment_marks_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_overall_decision_events; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_overall_decision_events_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_overall_decision_results; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_overall_decision_results_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_overall_decisions; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_overall_decisions_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Name: revinfo_rev_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.revinfo_rev_seq', 1, false);


--
-- Name: assessment_calculation_component_evidence_aud assessment_calculation_component_evidence_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_calculation_component_evidence_aud
    ADD CONSTRAINT assessment_calculation_component_evidence_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: assessment_calculation_component_evidence assessment_calculation_component_evidence_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_calculation_component_evidence
    ADD CONSTRAINT assessment_calculation_component_evidence_pkey PRIMARY KEY (id);


--
-- Name: assessment_calculation_outcomes_aud assessment_calculation_outcomes_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_calculation_outcomes_aud
    ADD CONSTRAINT assessment_calculation_outcomes_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: assessment_calculation_outcomes assessment_calculation_outcomes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_calculation_outcomes
    ADD CONSTRAINT assessment_calculation_outcomes_pkey PRIMARY KEY (id);


--
-- Name: assessment_calculation_runs_aud assessment_calculation_runs_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_calculation_runs_aud
    ADD CONSTRAINT assessment_calculation_runs_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: assessment_calculation_runs assessment_calculation_runs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_calculation_runs
    ADD CONSTRAINT assessment_calculation_runs_pkey PRIMARY KEY (id);


--
-- Name: assessment_components_aud assessment_components_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_components_aud
    ADD CONSTRAINT assessment_components_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: assessment_components assessment_components_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_components
    ADD CONSTRAINT assessment_components_pkey PRIMARY KEY (id);


--
-- Name: assessment_module_offerings_aud assessment_module_offerings_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_module_offerings_aud
    ADD CONSTRAINT assessment_module_offerings_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: assessment_module_offerings assessment_module_offerings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_module_offerings
    ADD CONSTRAINT assessment_module_offerings_pkey PRIMARY KEY (id);


--
-- Name: assessment_roster_entries_aud assessment_roster_entries_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_roster_entries_aud
    ADD CONSTRAINT assessment_roster_entries_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: assessment_roster_entries assessment_roster_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_roster_entries
    ADD CONSTRAINT assessment_roster_entries_pkey PRIMARY KEY (id);


--
-- Name: assessment_schemes_aud assessment_schemes_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_schemes_aud
    ADD CONSTRAINT assessment_schemes_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: assessment_schemes assessment_schemes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_schemes
    ADD CONSTRAINT assessment_schemes_pkey PRIMARY KEY (id);


--
-- Name: grading_bands_aud grading_bands_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grading_bands_aud
    ADD CONSTRAINT grading_bands_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: grading_bands grading_bands_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grading_bands
    ADD CONSTRAINT grading_bands_pkey PRIMARY KEY (id);


--
-- Name: grading_schemes_aud grading_schemes_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grading_schemes_aud
    ADD CONSTRAINT grading_schemes_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: grading_schemes grading_schemes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grading_schemes
    ADD CONSTRAINT grading_schemes_pkey PRIMARY KEY (id);


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
-- Name: mark_amendment_requests_aud mark_amendment_requests_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mark_amendment_requests_aud
    ADD CONSTRAINT mark_amendment_requests_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: mark_amendment_requests mark_amendment_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mark_amendment_requests
    ADD CONSTRAINT mark_amendment_requests_pkey PRIMARY KEY (id);


--
-- Name: module_results_aud module_results_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.module_results_aud
    ADD CONSTRAINT module_results_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: module_results module_results_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.module_results
    ADD CONSTRAINT module_results_pkey PRIMARY KEY (id);


--
-- Name: progression_rule_outcomes_aud progression_rule_outcomes_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.progression_rule_outcomes_aud
    ADD CONSTRAINT progression_rule_outcomes_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: progression_rule_outcomes progression_rule_outcomes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.progression_rule_outcomes
    ADD CONSTRAINT progression_rule_outcomes_pkey PRIMARY KEY (id);


--
-- Name: progression_rule_sets_aud progression_rule_sets_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.progression_rule_sets_aud
    ADD CONSTRAINT progression_rule_sets_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: progression_rule_sets progression_rule_sets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.progression_rule_sets
    ADD CONSTRAINT progression_rule_sets_pkey PRIMARY KEY (id);


--
-- Name: published_result_amendment_events_aud published_result_amendment_events_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_result_amendment_events_aud
    ADD CONSTRAINT published_result_amendment_events_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: published_result_amendment_events published_result_amendment_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_result_amendment_events
    ADD CONSTRAINT published_result_amendment_events_pkey PRIMARY KEY (id);


--
-- Name: published_result_amendments_aud published_result_amendments_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_result_amendments_aud
    ADD CONSTRAINT published_result_amendments_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: published_result_amendments published_result_amendments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_result_amendments
    ADD CONSTRAINT published_result_amendments_pkey PRIMARY KEY (id);


--
-- Name: published_results_aud published_results_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_results_aud
    ADD CONSTRAINT published_results_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: published_results published_results_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_results
    ADD CONSTRAINT published_results_pkey PRIMARY KEY (id);


--
-- Name: registration_roster_imports_aud registration_roster_imports_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_roster_imports_aud
    ADD CONSTRAINT registration_roster_imports_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: registration_roster_imports registration_roster_imports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_roster_imports
    ADD CONSTRAINT registration_roster_imports_pkey PRIMARY KEY (id);


--
-- Name: result_batch_status_events_aud result_batch_status_events_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.result_batch_status_events_aud
    ADD CONSTRAINT result_batch_status_events_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: result_batch_status_events result_batch_status_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.result_batch_status_events
    ADD CONSTRAINT result_batch_status_events_pkey PRIMARY KEY (id);


--
-- Name: result_batches_aud result_batches_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.result_batches_aud
    ADD CONSTRAINT result_batches_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: result_batches result_batches_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.result_batches
    ADD CONSTRAINT result_batches_pkey PRIMARY KEY (id);


--
-- Name: revinfo revinfo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revinfo
    ADD CONSTRAINT revinfo_pkey PRIMARY KEY (rev);


--
-- Name: student_assessment_marks_aud student_assessment_marks_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_assessment_marks_aud
    ADD CONSTRAINT student_assessment_marks_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: student_assessment_marks student_assessment_marks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_assessment_marks
    ADD CONSTRAINT student_assessment_marks_pkey PRIMARY KEY (id);


--
-- Name: student_overall_decision_events_aud student_overall_decision_events_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_overall_decision_events_aud
    ADD CONSTRAINT student_overall_decision_events_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: student_overall_decision_events student_overall_decision_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_overall_decision_events
    ADD CONSTRAINT student_overall_decision_events_pkey PRIMARY KEY (id);


--
-- Name: student_overall_decision_results_aud student_overall_decision_results_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_overall_decision_results_aud
    ADD CONSTRAINT student_overall_decision_results_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: student_overall_decision_results student_overall_decision_results_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_overall_decision_results
    ADD CONSTRAINT student_overall_decision_results_pkey PRIMARY KEY (id);


--
-- Name: student_overall_decisions_aud student_overall_decisions_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_overall_decisions_aud
    ADD CONSTRAINT student_overall_decisions_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: student_overall_decisions student_overall_decisions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_overall_decisions
    ADD CONSTRAINT student_overall_decisions_pkey PRIMARY KEY (id);


--
-- Name: assessment_calculation_outcomes uk_assessment_calculation_outcome; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_calculation_outcomes
    ADD CONSTRAINT uk_assessment_calculation_outcome UNIQUE (calculation_run_id, assessment_roster_entry_id);


--
-- Name: assessment_components uk_assessment_component_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_components
    ADD CONSTRAINT uk_assessment_component_code UNIQUE (assessment_scheme_id, code);


--
-- Name: assessment_module_offerings uk_assessment_offering_module_period; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_module_offerings
    ADD CONSTRAINT uk_assessment_offering_module_period UNIQUE (module_id, academic_period_id);


--
-- Name: assessment_roster_entries uk_assessment_roster_import_module; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_roster_entries
    ADD CONSTRAINT uk_assessment_roster_import_module UNIQUE (roster_import_id, module_id);


--
-- Name: assessment_roster_entries uk_assessment_roster_registration_module; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_roster_entries
    ADD CONSTRAINT uk_assessment_roster_registration_module UNIQUE (registration_module_id);


--
-- Name: assessment_schemes uk_assessment_scheme_offering_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_schemes
    ADD CONSTRAINT uk_assessment_scheme_offering_version UNIQUE (module_offering_id, scheme_version);


--
-- Name: assessment_calculation_component_evidence uk_calculation_component_evidence; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_calculation_component_evidence
    ADD CONSTRAINT uk_calculation_component_evidence UNIQUE (calculation_outcome_id, assessment_component_id);


--
-- Name: grading_bands uk_grading_band_grade; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grading_bands
    ADD CONSTRAINT uk_grading_band_grade UNIQUE (grading_scheme_id, grade);


--
-- Name: grading_schemes uk_grading_scheme_code_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grading_schemes
    ADD CONSTRAINT uk_grading_scheme_code_version UNIQUE (code, scheme_version);


--
-- Name: module_results uk_module_result_batch_roster; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.module_results
    ADD CONSTRAINT uk_module_result_batch_roster UNIQUE (result_batch_id, assessment_roster_entry_id);


--
-- Name: module_results uk_module_result_outcome; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.module_results
    ADD CONSTRAINT uk_module_result_outcome UNIQUE (calculation_outcome_id);


--
-- Name: student_overall_decision_results uk_overall_decision_published_result; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_overall_decision_results
    ADD CONSTRAINT uk_overall_decision_published_result UNIQUE (student_overall_decision_id, published_result_id);


--
-- Name: student_overall_decision_results uk_overall_decision_roster_result; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_overall_decision_results
    ADD CONSTRAINT uk_overall_decision_roster_result UNIQUE (student_overall_decision_id, assessment_roster_entry_id);


--
-- Name: progression_rule_sets uk_progression_rule_code_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.progression_rule_sets
    ADD CONSTRAINT uk_progression_rule_code_version UNIQUE (rule_code, rule_version);


--
-- Name: progression_rule_outcomes uk_progression_rule_outcome_priority; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.progression_rule_outcomes
    ADD CONSTRAINT uk_progression_rule_outcome_priority UNIQUE (progression_rule_set_id, priority);


--
-- Name: published_results uk_published_result_amendment; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_results
    ADD CONSTRAINT uk_published_result_amendment UNIQUE (result_amendment_id);


--
-- Name: published_result_amendments uk_published_result_amendment_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_result_amendments
    ADD CONSTRAINT uk_published_result_amendment_number UNIQUE (amendment_number);


--
-- Name: published_results uk_published_result_module_result; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_results
    ADD CONSTRAINT uk_published_result_module_result UNIQUE (module_result_id);


--
-- Name: published_results uk_published_result_student_module_period_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_results
    ADD CONSTRAINT uk_published_result_student_module_period_version UNIQUE (student_id, module_id, academic_period_id, publication_version);


--
-- Name: published_results uk_published_result_supersedes; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_results
    ADD CONSTRAINT uk_published_result_supersedes UNIQUE (supersedes_published_result_id);


--
-- Name: registration_roster_imports uk_registration_roster_imports_event; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_roster_imports
    ADD CONSTRAINT uk_registration_roster_imports_event UNIQUE (source_event_id);


--
-- Name: registration_roster_imports uk_registration_roster_imports_session; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_roster_imports
    ADD CONSTRAINT uk_registration_roster_imports_session UNIQUE (registration_session_id);


--
-- Name: result_batches uk_result_batch_calculation; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.result_batches
    ADD CONSTRAINT uk_result_batch_calculation UNIQUE (calculation_run_id);


--
-- Name: result_batches uk_result_batch_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.result_batches
    ADD CONSTRAINT uk_result_batch_number UNIQUE (batch_number);


--
-- Name: student_assessment_marks uk_student_assessment_mark_revision; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_assessment_marks
    ADD CONSTRAINT uk_student_assessment_mark_revision UNIQUE (assessment_component_id, assessment_roster_entry_id, revision_number);


--
-- Name: student_overall_decisions uk_student_overall_decision_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_overall_decisions
    ADD CONSTRAINT uk_student_overall_decision_number UNIQUE (decision_number);


--
-- Name: student_overall_decisions uk_student_overall_decision_supersedes; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_overall_decisions
    ADD CONSTRAINT uk_student_overall_decision_supersedes UNIQUE (supersedes_decision_id);


--
-- Name: student_overall_decisions uk_student_overall_decision_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_overall_decisions
    ADD CONSTRAINT uk_student_overall_decision_version UNIQUE (registration_roster_import_id, decision_version);


--
-- Name: idx_assessment_calculation_history; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_assessment_calculation_history ON public.assessment_calculation_runs USING btree (module_offering_id, initiated_at DESC);


--
-- Name: idx_assessment_marks_capture; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_assessment_marks_capture ON public.student_assessment_marks USING btree (assessment_component_id, status);


--
-- Name: idx_assessment_offerings_operations; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_assessment_offerings_operations ON public.assessment_module_offerings USING btree (academic_period_id, status);


--
-- Name: idx_assessment_results_inbox_processed_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_assessment_results_inbox_processed_at ON public.integration_inbox USING btree (processed_at);


--
-- Name: idx_assessment_roster_module_period; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_assessment_roster_module_period ON public.assessment_roster_entries USING btree (module_id, roster_import_id) WHERE ((deleted_at IS NULL) AND ((eligibility_status)::text = 'ELIGIBLE'::text));


--
-- Name: idx_mark_amendments_queue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mark_amendments_queue ON public.mark_amendment_requests USING btree (status, requested_at);


--
-- Name: idx_progression_decision_queue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_progression_decision_queue ON public.student_overall_decisions USING btree (status, calculated_at DESC);


--
-- Name: idx_progression_decision_student; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_progression_decision_student ON public.student_overall_decisions USING btree (student_id, academic_period_id, decision_version DESC);


--
-- Name: idx_published_result_amendments_queue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_published_result_amendments_queue ON public.published_result_amendments USING btree (status, requested_at);


--
-- Name: idx_published_results_student; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_published_results_student ON public.published_results USING btree (student_id, academic_period_id);


--
-- Name: idx_registration_roster_imports_operations; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_registration_roster_imports_operations ON public.registration_roster_imports USING btree (academic_period_id, student_number) WHERE (deleted_at IS NULL);


--
-- Name: idx_result_batches_queue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_result_batches_queue ON public.result_batches USING btree (status, created_at);


--
-- Name: integration_outbox_dispatch_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX integration_outbox_dispatch_idx ON public.integration_outbox USING btree (next_attempt_at, occurred_at, id) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: integration_outbox_event_type_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX integration_outbox_event_type_idx ON public.integration_outbox USING btree (event_type, occurred_at DESC);


--
-- Name: uk_active_published_result_amendment_original; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_active_published_result_amendment_original ON public.published_result_amendments USING btree (original_published_result_id) WHERE (((status)::text <> 'REJECTED'::text) AND (deleted_at IS NULL));


--
-- Name: uk_active_published_result_amendment_replacement; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_active_published_result_amendment_replacement ON public.published_result_amendments USING btree (replacement_module_result_id) WHERE (((status)::text <> 'REJECTED'::text) AND (deleted_at IS NULL));


--
-- Name: uk_assessment_scheme_one_approved; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_assessment_scheme_one_approved ON public.assessment_schemes USING btree (module_offering_id) WHERE (((status)::text = 'APPROVED'::text) AND (deleted_at IS NULL));


--
-- Name: uk_grading_scheme_approved_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_grading_scheme_approved_code ON public.grading_schemes USING btree (code) WHERE (((status)::text = 'APPROVED'::text) AND (deleted_at IS NULL));


--
-- Name: uk_mark_amendment_one_open; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_mark_amendment_one_open ON public.mark_amendment_requests USING btree (original_mark_id) WHERE (((status)::text = 'REQUESTED'::text) AND (deleted_at IS NULL));


--
-- Name: uk_progression_rule_approved_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_progression_rule_approved_scope ON public.progression_rule_sets USING btree (programme_version_id, programme_period_number) WHERE (((status)::text = 'APPROVED'::text) AND (deleted_at IS NULL));


--
-- Name: uk_student_assessment_mark_current; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_student_assessment_mark_current ON public.student_assessment_marks USING btree (assessment_component_id, assessment_roster_entry_id) WHERE (((status)::text = ANY ((ARRAY['CAPTURED'::character varying, 'SUBMITTED'::character varying])::text[])) AND (deleted_at IS NULL));


--
-- Name: progression_rule_sets trg_approved_progression_rule_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_approved_progression_rule_immutable BEFORE UPDATE ON public.progression_rule_sets FOR EACH ROW EXECUTE FUNCTION public.prevent_approved_progression_rule_change();


--
-- Name: assessment_components trg_assessment_component_scheme_mutability; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_assessment_component_scheme_mutability BEFORE INSERT OR DELETE OR UPDATE ON public.assessment_components FOR EACH ROW EXECUTE FUNCTION public.enforce_assessment_component_scheme_mutability();


--
-- Name: student_assessment_marks trg_assessment_mark_scope; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_assessment_mark_scope BEFORE INSERT OR UPDATE ON public.student_assessment_marks FOR EACH ROW EXECUTE FUNCTION public.validate_assessment_mark_scope();


--
-- Name: assessment_roster_entries trg_assessment_roster_snapshot_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_assessment_roster_snapshot_immutable BEFORE UPDATE ON public.assessment_roster_entries FOR EACH ROW EXECUTE FUNCTION public.prevent_assessment_roster_snapshot_change();


--
-- Name: assessment_calculation_component_evidence trg_calculation_component_evidence_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_calculation_component_evidence_immutable BEFORE DELETE OR UPDATE ON public.assessment_calculation_component_evidence FOR EACH ROW EXECUTE FUNCTION public.prevent_calculation_component_evidence_change();


--
-- Name: grading_bands trg_grading_band_scheme_mutability; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_grading_band_scheme_mutability BEFORE INSERT OR DELETE OR UPDATE ON public.grading_bands FOR EACH ROW EXECUTE FUNCTION public.enforce_grading_band_scheme_mutability();


--
-- Name: module_results trg_module_result_evidence_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_module_result_evidence_immutable BEFORE UPDATE ON public.module_results FOR EACH ROW EXECUTE FUNCTION public.prevent_module_result_evidence_change();


--
-- Name: student_overall_decisions trg_overall_decision_evidence_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_overall_decision_evidence_immutable BEFORE UPDATE ON public.student_overall_decisions FOR EACH ROW EXECUTE FUNCTION public.prevent_overall_decision_evidence_change();


--
-- Name: student_overall_decision_results trg_overall_decision_result_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_overall_decision_result_immutable BEFORE DELETE OR UPDATE ON public.student_overall_decision_results FOR EACH ROW EXECUTE FUNCTION public.prevent_overall_decision_result_change();


--
-- Name: progression_rule_outcomes trg_progression_outcome_mutability; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_progression_outcome_mutability BEFORE INSERT OR DELETE OR UPDATE ON public.progression_rule_outcomes FOR EACH ROW EXECUTE FUNCTION public.enforce_progression_outcome_mutability();


--
-- Name: published_result_amendment_events trg_published_result_amendment_event_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_published_result_amendment_event_immutable BEFORE DELETE OR UPDATE ON public.published_result_amendment_events FOR EACH ROW EXECUTE FUNCTION public.prevent_published_result_amendment_event_change();


--
-- Name: published_result_amendments trg_published_result_amendment_evidence_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_published_result_amendment_evidence_immutable BEFORE UPDATE ON public.published_result_amendments FOR EACH ROW EXECUTE FUNCTION public.prevent_published_result_amendment_evidence_change();


--
-- Name: published_results trg_published_result_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_published_result_immutable BEFORE DELETE OR UPDATE ON public.published_results FOR EACH ROW EXECUTE FUNCTION public.prevent_published_result_change();


--
-- Name: registration_roster_imports trg_registration_roster_import_identity_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_registration_roster_import_identity_immutable BEFORE UPDATE ON public.registration_roster_imports FOR EACH ROW EXECUTE FUNCTION public.prevent_roster_import_identity_change();


--
-- Name: student_assessment_marks trg_submitted_mark_evidence_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_submitted_mark_evidence_immutable BEFORE UPDATE ON public.student_assessment_marks FOR EACH ROW EXECUTE FUNCTION public.prevent_submitted_mark_evidence_change();


--
-- Name: grading_schemes trg_validate_grading_scheme_approval; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_grading_scheme_approval BEFORE UPDATE ON public.grading_schemes FOR EACH ROW EXECUTE FUNCTION public.validate_grading_scheme_on_approval();


--
-- Name: student_overall_decisions trg_validate_overall_decision_evidence; Type: TRIGGER; Schema: public; Owner: -
--

CREATE CONSTRAINT TRIGGER trg_validate_overall_decision_evidence AFTER INSERT ON public.student_overall_decisions DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.validate_overall_decision_evidence();


--
-- Name: student_overall_decisions trg_validate_overall_decision_lineage; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_overall_decision_lineage BEFORE INSERT ON public.student_overall_decisions FOR EACH ROW EXECUTE FUNCTION public.validate_overall_decision_lineage();


--
-- Name: student_overall_decision_results trg_validate_overall_decision_result_snapshot; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_overall_decision_result_snapshot BEFORE INSERT ON public.student_overall_decision_results FOR EACH ROW EXECUTE FUNCTION public.validate_overall_decision_result_snapshot();


--
-- Name: progression_rule_sets trg_validate_progression_rule_approval; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_progression_rule_approval BEFORE UPDATE ON public.progression_rule_sets FOR EACH ROW EXECUTE FUNCTION public.validate_progression_rule_approval();


--
-- Name: published_result_amendments trg_validate_published_result_amendment_evidence; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_published_result_amendment_evidence BEFORE INSERT ON public.published_result_amendments FOR EACH ROW EXECUTE FUNCTION public.validate_published_result_amendment_evidence();


--
-- Name: published_results trg_validate_published_result_lineage; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_published_result_lineage BEFORE INSERT ON public.published_results FOR EACH ROW EXECUTE FUNCTION public.validate_published_result_lineage();


--
-- Name: assessment_calculation_component_evidence assessment_calculation_component_e_assessment_component_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_calculation_component_evidence
    ADD CONSTRAINT assessment_calculation_component_e_assessment_component_id_fkey FOREIGN KEY (assessment_component_id) REFERENCES public.assessment_components(id);


--
-- Name: assessment_calculation_component_evidence assessment_calculation_component_ev_calculation_outcome_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_calculation_component_evidence
    ADD CONSTRAINT assessment_calculation_component_ev_calculation_outcome_id_fkey FOREIGN KEY (calculation_outcome_id) REFERENCES public.assessment_calculation_outcomes(id);


--
-- Name: assessment_calculation_component_evidence assessment_calculation_component_eviden_calculation_run_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_calculation_component_evidence
    ADD CONSTRAINT assessment_calculation_component_eviden_calculation_run_id_fkey FOREIGN KEY (calculation_run_id) REFERENCES public.assessment_calculation_runs(id);


--
-- Name: assessment_calculation_component_evidence assessment_calculation_component_evidenc_submitted_mark_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_calculation_component_evidence
    ADD CONSTRAINT assessment_calculation_component_evidenc_submitted_mark_id_fkey FOREIGN KEY (submitted_mark_id) REFERENCES public.student_assessment_marks(id);


--
-- Name: assessment_calculation_component_evidence_aud assessment_calculation_component_evidence_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_calculation_component_evidence_aud
    ADD CONSTRAINT assessment_calculation_component_evidence_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: assessment_calculation_outcomes assessment_calculation_outcomes_assessment_roster_entry_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_calculation_outcomes
    ADD CONSTRAINT assessment_calculation_outcomes_assessment_roster_entry_id_fkey FOREIGN KEY (assessment_roster_entry_id) REFERENCES public.assessment_roster_entries(id);


--
-- Name: assessment_calculation_outcomes_aud assessment_calculation_outcomes_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_calculation_outcomes_aud
    ADD CONSTRAINT assessment_calculation_outcomes_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: assessment_calculation_outcomes assessment_calculation_outcomes_calculation_run_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_calculation_outcomes
    ADD CONSTRAINT assessment_calculation_outcomes_calculation_run_id_fkey FOREIGN KEY (calculation_run_id) REFERENCES public.assessment_calculation_runs(id);


--
-- Name: assessment_calculation_runs assessment_calculation_runs_assessment_scheme_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_calculation_runs
    ADD CONSTRAINT assessment_calculation_runs_assessment_scheme_id_fkey FOREIGN KEY (assessment_scheme_id) REFERENCES public.assessment_schemes(id);


--
-- Name: assessment_calculation_runs_aud assessment_calculation_runs_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_calculation_runs_aud
    ADD CONSTRAINT assessment_calculation_runs_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: assessment_calculation_runs assessment_calculation_runs_module_offering_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_calculation_runs
    ADD CONSTRAINT assessment_calculation_runs_module_offering_id_fkey FOREIGN KEY (module_offering_id) REFERENCES public.assessment_module_offerings(id);


--
-- Name: assessment_components assessment_components_assessment_scheme_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_components
    ADD CONSTRAINT assessment_components_assessment_scheme_id_fkey FOREIGN KEY (assessment_scheme_id) REFERENCES public.assessment_schemes(id);


--
-- Name: assessment_components_aud assessment_components_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_components_aud
    ADD CONSTRAINT assessment_components_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: assessment_module_offerings_aud assessment_module_offerings_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_module_offerings_aud
    ADD CONSTRAINT assessment_module_offerings_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: assessment_roster_entries_aud assessment_roster_entries_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_roster_entries_aud
    ADD CONSTRAINT assessment_roster_entries_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: assessment_roster_entries assessment_roster_entries_roster_import_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_roster_entries
    ADD CONSTRAINT assessment_roster_entries_roster_import_id_fkey FOREIGN KEY (roster_import_id) REFERENCES public.registration_roster_imports(id);


--
-- Name: assessment_schemes_aud assessment_schemes_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_schemes_aud
    ADD CONSTRAINT assessment_schemes_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: assessment_schemes assessment_schemes_module_offering_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assessment_schemes
    ADD CONSTRAINT assessment_schemes_module_offering_id_fkey FOREIGN KEY (module_offering_id) REFERENCES public.assessment_module_offerings(id);


--
-- Name: published_results fk_published_result_amendment; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_results
    ADD CONSTRAINT fk_published_result_amendment FOREIGN KEY (result_amendment_id) REFERENCES public.published_result_amendments(id);


--
-- Name: grading_bands_aud grading_bands_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grading_bands_aud
    ADD CONSTRAINT grading_bands_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: grading_bands grading_bands_grading_scheme_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grading_bands
    ADD CONSTRAINT grading_bands_grading_scheme_id_fkey FOREIGN KEY (grading_scheme_id) REFERENCES public.grading_schemes(id);


--
-- Name: grading_schemes_aud grading_schemes_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grading_schemes_aud
    ADD CONSTRAINT grading_schemes_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: mark_amendment_requests_aud mark_amendment_requests_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mark_amendment_requests_aud
    ADD CONSTRAINT mark_amendment_requests_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: mark_amendment_requests mark_amendment_requests_original_mark_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mark_amendment_requests
    ADD CONSTRAINT mark_amendment_requests_original_mark_id_fkey FOREIGN KEY (original_mark_id) REFERENCES public.student_assessment_marks(id);


--
-- Name: mark_amendment_requests mark_amendment_requests_replacement_mark_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mark_amendment_requests
    ADD CONSTRAINT mark_amendment_requests_replacement_mark_id_fkey FOREIGN KEY (replacement_mark_id) REFERENCES public.student_assessment_marks(id);


--
-- Name: module_results module_results_assessment_roster_entry_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.module_results
    ADD CONSTRAINT module_results_assessment_roster_entry_id_fkey FOREIGN KEY (assessment_roster_entry_id) REFERENCES public.assessment_roster_entries(id);


--
-- Name: module_results_aud module_results_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.module_results_aud
    ADD CONSTRAINT module_results_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: module_results module_results_calculation_outcome_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.module_results
    ADD CONSTRAINT module_results_calculation_outcome_id_fkey FOREIGN KEY (calculation_outcome_id) REFERENCES public.assessment_calculation_outcomes(id);


--
-- Name: module_results module_results_result_batch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.module_results
    ADD CONSTRAINT module_results_result_batch_id_fkey FOREIGN KEY (result_batch_id) REFERENCES public.result_batches(id);


--
-- Name: progression_rule_outcomes_aud progression_rule_outcomes_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.progression_rule_outcomes_aud
    ADD CONSTRAINT progression_rule_outcomes_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: progression_rule_outcomes progression_rule_outcomes_progression_rule_set_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.progression_rule_outcomes
    ADD CONSTRAINT progression_rule_outcomes_progression_rule_set_id_fkey FOREIGN KEY (progression_rule_set_id) REFERENCES public.progression_rule_sets(id);


--
-- Name: progression_rule_sets_aud progression_rule_sets_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.progression_rule_sets_aud
    ADD CONSTRAINT progression_rule_sets_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: published_result_amendment_events published_result_amendment_ev_published_result_amendment_i_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_result_amendment_events
    ADD CONSTRAINT published_result_amendment_ev_published_result_amendment_i_fkey FOREIGN KEY (published_result_amendment_id) REFERENCES public.published_result_amendments(id);


--
-- Name: published_result_amendment_events_aud published_result_amendment_events_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_result_amendment_events_aud
    ADD CONSTRAINT published_result_amendment_events_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: published_result_amendments_aud published_result_amendments_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_result_amendments_aud
    ADD CONSTRAINT published_result_amendments_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: published_result_amendments published_result_amendments_original_published_result_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_result_amendments
    ADD CONSTRAINT published_result_amendments_original_published_result_id_fkey FOREIGN KEY (original_published_result_id) REFERENCES public.published_results(id);


--
-- Name: published_result_amendments published_result_amendments_replacement_module_result_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_result_amendments
    ADD CONSTRAINT published_result_amendments_replacement_module_result_id_fkey FOREIGN KEY (replacement_module_result_id) REFERENCES public.module_results(id);


--
-- Name: published_result_amendments published_result_amendments_replacement_result_batch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_result_amendments
    ADD CONSTRAINT published_result_amendments_replacement_result_batch_id_fkey FOREIGN KEY (replacement_result_batch_id) REFERENCES public.result_batches(id);


--
-- Name: published_results_aud published_results_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_results_aud
    ADD CONSTRAINT published_results_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: published_results published_results_module_result_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_results
    ADD CONSTRAINT published_results_module_result_id_fkey FOREIGN KEY (module_result_id) REFERENCES public.module_results(id);


--
-- Name: published_results published_results_result_batch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_results
    ADD CONSTRAINT published_results_result_batch_id_fkey FOREIGN KEY (result_batch_id) REFERENCES public.result_batches(id);


--
-- Name: published_results published_results_supersedes_published_result_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.published_results
    ADD CONSTRAINT published_results_supersedes_published_result_id_fkey FOREIGN KEY (supersedes_published_result_id) REFERENCES public.published_results(id);


--
-- Name: registration_roster_imports_aud registration_roster_imports_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_roster_imports_aud
    ADD CONSTRAINT registration_roster_imports_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: result_batch_status_events_aud result_batch_status_events_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.result_batch_status_events_aud
    ADD CONSTRAINT result_batch_status_events_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: result_batch_status_events result_batch_status_events_result_batch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.result_batch_status_events
    ADD CONSTRAINT result_batch_status_events_result_batch_id_fkey FOREIGN KEY (result_batch_id) REFERENCES public.result_batches(id);


--
-- Name: result_batches_aud result_batches_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.result_batches_aud
    ADD CONSTRAINT result_batches_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: result_batches result_batches_calculation_run_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.result_batches
    ADD CONSTRAINT result_batches_calculation_run_id_fkey FOREIGN KEY (calculation_run_id) REFERENCES public.assessment_calculation_runs(id);


--
-- Name: result_batches result_batches_grading_scheme_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.result_batches
    ADD CONSTRAINT result_batches_grading_scheme_id_fkey FOREIGN KEY (grading_scheme_id) REFERENCES public.grading_schemes(id);


--
-- Name: result_batches result_batches_module_offering_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.result_batches
    ADD CONSTRAINT result_batches_module_offering_id_fkey FOREIGN KEY (module_offering_id) REFERENCES public.assessment_module_offerings(id);


--
-- Name: student_assessment_marks student_assessment_marks_assessment_component_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_assessment_marks
    ADD CONSTRAINT student_assessment_marks_assessment_component_id_fkey FOREIGN KEY (assessment_component_id) REFERENCES public.assessment_components(id);


--
-- Name: student_assessment_marks student_assessment_marks_assessment_roster_entry_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_assessment_marks
    ADD CONSTRAINT student_assessment_marks_assessment_roster_entry_id_fkey FOREIGN KEY (assessment_roster_entry_id) REFERENCES public.assessment_roster_entries(id);


--
-- Name: student_assessment_marks_aud student_assessment_marks_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_assessment_marks_aud
    ADD CONSTRAINT student_assessment_marks_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: student_assessment_marks student_assessment_marks_supersedes_mark_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_assessment_marks
    ADD CONSTRAINT student_assessment_marks_supersedes_mark_id_fkey FOREIGN KEY (supersedes_mark_id) REFERENCES public.student_assessment_marks(id);


--
-- Name: student_overall_decision_events student_overall_decision_event_student_overall_decision_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_overall_decision_events
    ADD CONSTRAINT student_overall_decision_event_student_overall_decision_id_fkey FOREIGN KEY (student_overall_decision_id) REFERENCES public.student_overall_decisions(id);


--
-- Name: student_overall_decision_events_aud student_overall_decision_events_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_overall_decision_events_aud
    ADD CONSTRAINT student_overall_decision_events_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: student_overall_decision_results student_overall_decision_resul_student_overall_decision_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_overall_decision_results
    ADD CONSTRAINT student_overall_decision_resul_student_overall_decision_id_fkey FOREIGN KEY (student_overall_decision_id) REFERENCES public.student_overall_decisions(id);


--
-- Name: student_overall_decision_results student_overall_decision_result_assessment_roster_entry_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_overall_decision_results
    ADD CONSTRAINT student_overall_decision_result_assessment_roster_entry_id_fkey FOREIGN KEY (assessment_roster_entry_id) REFERENCES public.assessment_roster_entries(id);


--
-- Name: student_overall_decision_results_aud student_overall_decision_results_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_overall_decision_results_aud
    ADD CONSTRAINT student_overall_decision_results_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: student_overall_decision_results student_overall_decision_results_published_result_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_overall_decision_results
    ADD CONSTRAINT student_overall_decision_results_published_result_id_fkey FOREIGN KEY (published_result_id) REFERENCES public.published_results(id);


--
-- Name: student_overall_decisions_aud student_overall_decisions_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_overall_decisions_aud
    ADD CONSTRAINT student_overall_decisions_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: student_overall_decisions student_overall_decisions_matched_outcome_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_overall_decisions
    ADD CONSTRAINT student_overall_decisions_matched_outcome_id_fkey FOREIGN KEY (matched_outcome_id) REFERENCES public.progression_rule_outcomes(id);


--
-- Name: student_overall_decisions student_overall_decisions_progression_rule_set_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_overall_decisions
    ADD CONSTRAINT student_overall_decisions_progression_rule_set_id_fkey FOREIGN KEY (progression_rule_set_id) REFERENCES public.progression_rule_sets(id);


--
-- Name: student_overall_decisions student_overall_decisions_registration_roster_import_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_overall_decisions
    ADD CONSTRAINT student_overall_decisions_registration_roster_import_id_fkey FOREIGN KEY (registration_roster_import_id) REFERENCES public.registration_roster_imports(id);


--
-- Name: student_overall_decisions student_overall_decisions_supersedes_decision_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_overall_decisions
    ADD CONSTRAINT student_overall_decisions_supersedes_decision_id_fkey FOREIGN KEY (supersedes_decision_id) REFERENCES public.student_overall_decisions(id);


--
-- PostgreSQL database dump complete
--


