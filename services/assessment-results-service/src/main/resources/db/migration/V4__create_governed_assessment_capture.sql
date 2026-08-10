-- Author: Tinashe K

CREATE TABLE assessment_module_offerings (
    id uuid PRIMARY KEY,
    module_id uuid NOT NULL,
    module_code varchar(50) NOT NULL,
    module_name varchar(200) NOT NULL,
    academic_period_id uuid NOT NULL,
    academic_period_code varchar(50) NOT NULL,
    academic_period_name varchar(150) NOT NULL,
    assigned_instructor_user_id uuid NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT uk_assessment_offering_module_period UNIQUE (module_id, academic_period_id),
    CONSTRAINT ck_assessment_offering_status CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED'))
);

CREATE TABLE assessment_schemes (
    id uuid PRIMARY KEY,
    module_offering_id uuid NOT NULL REFERENCES assessment_module_offerings (id),
    scheme_version integer NOT NULL,
    name varchar(150) NOT NULL,
    status varchar(20) NOT NULL,
    approval_reason varchar(1000),
    approved_by_user_id uuid,
    approved_at timestamptz,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT uk_assessment_scheme_offering_version UNIQUE (module_offering_id, scheme_version),
    CONSTRAINT ck_assessment_scheme_version CHECK (scheme_version > 0),
    CONSTRAINT ck_assessment_scheme_status CHECK (status IN ('DRAFT', 'APPROVED', 'SUPERSEDED')),
    CONSTRAINT ck_assessment_scheme_approval CHECK (
        (status = 'DRAFT' AND approved_by_user_id IS NULL AND approved_at IS NULL AND approval_reason IS NULL)
        OR (status IN ('APPROVED', 'SUPERSEDED') AND approved_by_user_id IS NOT NULL
            AND approved_at IS NOT NULL AND length(trim(approval_reason)) > 0)
    )
);

CREATE UNIQUE INDEX uk_assessment_scheme_one_approved
    ON assessment_schemes (module_offering_id) WHERE status = 'APPROVED' AND deleted_at IS NULL;

CREATE TABLE assessment_components (
    id uuid PRIMARY KEY,
    assessment_scheme_id uuid NOT NULL REFERENCES assessment_schemes (id),
    code varchar(30) NOT NULL,
    name varchar(150) NOT NULL,
    component_type varchar(30) NOT NULL,
    weight_percent numeric(5,2) NOT NULL,
    maximum_mark numeric(8,2) NOT NULL,
    capture_opens_at timestamptz NOT NULL,
    capture_closes_at timestamptz NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT uk_assessment_component_code UNIQUE (assessment_scheme_id, code),
    CONSTRAINT ck_assessment_component_type CHECK (
        component_type IN ('COURSEWORK', 'PRACTICAL', 'IN_CLASS_TEST', 'FINAL_EXAM', 'OTHER')),
    CONSTRAINT ck_assessment_component_weight CHECK (weight_percent > 0 AND weight_percent <= 100),
    CONSTRAINT ck_assessment_component_maximum CHECK (maximum_mark > 0),
    CONSTRAINT ck_assessment_component_window CHECK (capture_closes_at > capture_opens_at),
    CONSTRAINT ck_assessment_component_sort CHECK (sort_order > 0)
);

CREATE TABLE student_assessment_marks (
    id uuid PRIMARY KEY,
    assessment_component_id uuid NOT NULL REFERENCES assessment_components (id),
    assessment_roster_entry_id uuid NOT NULL REFERENCES assessment_roster_entries (id),
    revision_number integer NOT NULL,
    supersedes_mark_id uuid REFERENCES student_assessment_marks (id),
    score numeric(8,2) NOT NULL,
    status varchar(20) NOT NULL,
    capture_method varchar(20) NOT NULL,
    captured_by_user_id uuid NOT NULL,
    captured_at timestamptz NOT NULL,
    submitted_by_user_id uuid,
    submitted_at timestamptz,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT uk_student_assessment_mark_revision UNIQUE (
        assessment_component_id, assessment_roster_entry_id, revision_number),
    CONSTRAINT ck_student_assessment_mark_revision CHECK (revision_number > 0),
    CONSTRAINT ck_student_assessment_mark_score CHECK (score >= 0),
    CONSTRAINT ck_student_assessment_mark_status CHECK (status IN ('CAPTURED', 'SUBMITTED', 'SUPERSEDED')),
    CONSTRAINT ck_student_assessment_mark_method CHECK (capture_method IN ('MANUAL', 'UPLOAD', 'AMENDMENT')),
    CONSTRAINT ck_student_assessment_mark_submission CHECK (
        (status = 'CAPTURED' AND submitted_by_user_id IS NULL AND submitted_at IS NULL)
        OR (status IN ('SUBMITTED', 'SUPERSEDED') AND submitted_by_user_id IS NOT NULL AND submitted_at IS NOT NULL)
    ),
    CONSTRAINT ck_student_assessment_mark_supersession CHECK (
        (revision_number = 1 AND supersedes_mark_id IS NULL)
        OR (revision_number > 1 AND supersedes_mark_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_student_assessment_mark_current
    ON student_assessment_marks (assessment_component_id, assessment_roster_entry_id)
    WHERE status IN ('CAPTURED', 'SUBMITTED') AND deleted_at IS NULL;

CREATE TABLE mark_amendment_requests (
    id uuid PRIMARY KEY,
    original_mark_id uuid NOT NULL REFERENCES student_assessment_marks (id),
    proposed_score numeric(8,2) NOT NULL,
    reason varchar(1000) NOT NULL,
    status varchar(20) NOT NULL,
    requested_by_user_id uuid NOT NULL,
    requested_at timestamptz NOT NULL,
    decided_by_user_id uuid,
    decided_at timestamptz,
    decision_reason varchar(1000),
    replacement_mark_id uuid REFERENCES student_assessment_marks (id),
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_mark_amendment_score CHECK (proposed_score >= 0),
    CONSTRAINT ck_mark_amendment_reason CHECK (length(trim(reason)) > 0),
    CONSTRAINT ck_mark_amendment_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_mark_amendment_decision CHECK (
        (status = 'REQUESTED' AND decided_by_user_id IS NULL AND decided_at IS NULL
            AND decision_reason IS NULL AND replacement_mark_id IS NULL)
        OR (status = 'APPROVED' AND decided_by_user_id IS NOT NULL AND decided_at IS NOT NULL
            AND length(trim(decision_reason)) > 0 AND replacement_mark_id IS NOT NULL)
        OR (status = 'REJECTED' AND decided_by_user_id IS NOT NULL AND decided_at IS NOT NULL
            AND length(trim(decision_reason)) > 0 AND replacement_mark_id IS NULL)
    )
);

CREATE UNIQUE INDEX uk_mark_amendment_one_open
    ON mark_amendment_requests (original_mark_id) WHERE status = 'REQUESTED' AND deleted_at IS NULL;

CREATE TABLE assessment_calculation_runs (
    id uuid PRIMARY KEY,
    module_offering_id uuid NOT NULL REFERENCES assessment_module_offerings (id),
    assessment_scheme_id uuid NOT NULL REFERENCES assessment_schemes (id),
    rule_snapshot jsonb NOT NULL,
    roster_count integer NOT NULL,
    complete_result_count integer NOT NULL,
    incomplete_result_count integer NOT NULL,
    status varchar(20) NOT NULL,
    initiated_by_user_id uuid NOT NULL,
    initiated_at timestamptz NOT NULL,
    completed_at timestamptz,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_assessment_calculation_counts CHECK (
        roster_count >= 0 AND complete_result_count >= 0 AND incomplete_result_count >= 0
        AND roster_count = complete_result_count + incomplete_result_count),
    CONSTRAINT ck_assessment_calculation_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_assessment_calculation_completion CHECK (
        (status = 'RUNNING' AND completed_at IS NULL)
        OR (status IN ('COMPLETED', 'FAILED') AND completed_at IS NOT NULL)
    )
);

CREATE TABLE assessment_calculation_outcomes (
    id uuid PRIMARY KEY,
    calculation_run_id uuid NOT NULL REFERENCES assessment_calculation_runs (id),
    assessment_roster_entry_id uuid NOT NULL REFERENCES assessment_roster_entries (id),
    weighted_total numeric(6,2),
    is_complete boolean NOT NULL,
    missing_component_codes varchar(1000),
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT uk_assessment_calculation_outcome UNIQUE (calculation_run_id, assessment_roster_entry_id),
    CONSTRAINT ck_assessment_calculation_total CHECK (weighted_total IS NULL OR weighted_total BETWEEN 0 AND 100),
    CONSTRAINT ck_assessment_calculation_completeness CHECK (
        (is_complete AND weighted_total IS NOT NULL AND missing_component_codes IS NULL)
        OR (NOT is_complete AND weighted_total IS NULL AND length(trim(missing_component_codes)) > 0)
    )
);

CREATE INDEX idx_assessment_offerings_operations ON assessment_module_offerings (academic_period_id, status);
CREATE INDEX idx_assessment_marks_capture ON student_assessment_marks (assessment_component_id, status);
CREATE INDEX idx_mark_amendments_queue ON mark_amendment_requests (status, requested_at);
CREATE INDEX idx_assessment_calculation_history ON assessment_calculation_runs (module_offering_id, initiated_at DESC);

CREATE TABLE assessment_module_offerings_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    module_id uuid, module_code varchar(50), module_name varchar(200), academic_period_id uuid,
    academic_period_code varchar(50), academic_period_name varchar(150), assigned_instructor_user_id uuid,
    status varchar(20), created_at timestamptz, updated_at timestamptz, created_by_user_id uuid,
    modified_by_user_id uuid, deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);
CREATE TABLE assessment_schemes_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    module_offering_id uuid, scheme_version integer, name varchar(150), status varchar(20),
    approval_reason varchar(1000), approved_by_user_id uuid, approved_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid,
    modified_by_user_id uuid, deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);
CREATE TABLE assessment_components_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    assessment_scheme_id uuid, code varchar(30), name varchar(150), component_type varchar(30),
    weight_percent numeric(5,2), maximum_mark numeric(8,2), capture_opens_at timestamptz,
    capture_closes_at timestamptz, sort_order integer, created_at timestamptz, updated_at timestamptz,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);
CREATE TABLE student_assessment_marks_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    assessment_component_id uuid, assessment_roster_entry_id uuid, revision_number integer,
    supersedes_mark_id uuid, score numeric(8,2), status varchar(20), capture_method varchar(20),
    captured_by_user_id uuid, captured_at timestamptz, submitted_by_user_id uuid, submitted_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);
CREATE TABLE mark_amendment_requests_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    original_mark_id uuid, proposed_score numeric(8,2), reason varchar(1000), status varchar(20),
    requested_by_user_id uuid, requested_at timestamptz, decided_by_user_id uuid, decided_at timestamptz,
    decision_reason varchar(1000), replacement_mark_id uuid, created_at timestamptz, updated_at timestamptz,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);
CREATE TABLE assessment_calculation_runs_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    module_offering_id uuid, assessment_scheme_id uuid, rule_snapshot jsonb, roster_count integer,
    complete_result_count integer, incomplete_result_count integer, status varchar(20),
    initiated_by_user_id uuid, initiated_at timestamptz, completed_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid,
    modified_by_user_id uuid, deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);
CREATE TABLE assessment_calculation_outcomes_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    calculation_run_id uuid, assessment_roster_entry_id uuid, weighted_total numeric(6,2),
    is_complete boolean, missing_component_codes varchar(1000), created_at timestamptz,
    updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);

CREATE OR REPLACE FUNCTION enforce_assessment_component_scheme_mutability()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM assessment_schemes WHERE id = COALESCE(NEW.assessment_scheme_id, OLD.assessment_scheme_id)
               AND status <> 'DRAFT') THEN
        RAISE EXCEPTION 'Components of an approved assessment scheme are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_assessment_component_scheme_mutability
    BEFORE INSERT OR UPDATE OR DELETE ON assessment_components
    FOR EACH ROW EXECUTE FUNCTION enforce_assessment_component_scheme_mutability();

CREATE OR REPLACE FUNCTION prevent_submitted_mark_evidence_change()
RETURNS trigger LANGUAGE plpgsql AS $$
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
CREATE TRIGGER trg_submitted_mark_evidence_immutable
    BEFORE UPDATE ON student_assessment_marks
    FOR EACH ROW EXECUTE FUNCTION prevent_submitted_mark_evidence_change();

CREATE OR REPLACE FUNCTION validate_assessment_mark_scope()
RETURNS trigger LANGUAGE plpgsql AS $$
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
CREATE TRIGGER trg_assessment_mark_scope
    BEFORE INSERT OR UPDATE ON student_assessment_marks
    FOR EACH ROW EXECUTE FUNCTION validate_assessment_mark_scope();
