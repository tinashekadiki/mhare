-- Author: Tinashe K

CREATE TABLE assessment_calculation_component_evidence (
    id uuid PRIMARY KEY,
    calculation_run_id uuid NOT NULL REFERENCES assessment_calculation_runs(id),
    calculation_outcome_id uuid NOT NULL REFERENCES assessment_calculation_outcomes(id),
    assessment_component_id uuid NOT NULL REFERENCES assessment_components(id),
    submitted_mark_id uuid NOT NULL REFERENCES student_assessment_marks(id),
    component_code varchar(30) NOT NULL,
    component_type varchar(30) NOT NULL,
    score numeric(8,2) NOT NULL,
    maximum_mark numeric(8,2) NOT NULL,
    weight_percent numeric(5,2) NOT NULL,
    weighted_contribution numeric(6,2) NOT NULL,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT uk_calculation_component_evidence UNIQUE(calculation_outcome_id,assessment_component_id),
    CONSTRAINT ck_calculation_component_evidence_values CHECK(score>=0 AND maximum_mark>0 AND score<=maximum_mark AND weight_percent>0 AND weight_percent<=100 AND weighted_contribution>=0 AND weighted_contribution<=100)
);

CREATE TABLE grading_schemes (
    id uuid PRIMARY KEY, code varchar(30) NOT NULL, name varchar(150) NOT NULL,
    scheme_version integer NOT NULL, status varchar(20) NOT NULL,
    approved_by_user_id uuid, approved_at timestamptz, approval_reason varchar(1000),
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT uk_grading_scheme_code_version UNIQUE(code,scheme_version),
    CONSTRAINT ck_grading_scheme_status CHECK(status IN ('DRAFT','APPROVED','SUPERSEDED')),
    CONSTRAINT ck_grading_scheme_approval CHECK((status='DRAFT' AND approved_at IS NULL) OR (status IN ('APPROVED','SUPERSEDED') AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL AND length(trim(approval_reason))>0))
);
CREATE UNIQUE INDEX uk_grading_scheme_approved_code ON grading_schemes(code) WHERE status='APPROVED' AND deleted_at IS NULL;
CREATE TABLE grading_bands (
    id uuid PRIMARY KEY, grading_scheme_id uuid NOT NULL REFERENCES grading_schemes(id),
    minimum_mark numeric(6,2) NOT NULL, maximum_mark numeric(6,2) NOT NULL,
    grade varchar(10) NOT NULL, remark varchar(100) NOT NULL, passing boolean NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT uk_grading_band_grade UNIQUE(grading_scheme_id,grade),
    CONSTRAINT ck_grading_band_range CHECK(minimum_mark>=0 AND maximum_mark<=100 AND maximum_mark>=minimum_mark),
    CONSTRAINT ck_grading_band_sort CHECK(sort_order>0)
);

CREATE TABLE result_batches (
    id uuid PRIMARY KEY,
    module_offering_id uuid NOT NULL REFERENCES assessment_module_offerings(id),
    calculation_run_id uuid NOT NULL REFERENCES assessment_calculation_runs(id),
    grading_scheme_id uuid NOT NULL REFERENCES grading_schemes(id),
    batch_number varchar(50) NOT NULL,
    status varchar(20) NOT NULL,
    status_reason varchar(1000) NOT NULL,
    submitted_by_user_id uuid, submitted_at timestamptz,
    moderated_by_user_id uuid, moderated_at timestamptz,
    approved_by_user_id uuid, approved_at timestamptz,
    published_by_user_id uuid, published_at timestamptz,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT uk_result_batch_calculation UNIQUE (calculation_run_id),
    CONSTRAINT uk_result_batch_number UNIQUE (batch_number),
    CONSTRAINT ck_result_batch_status CHECK (status IN ('DRAFT','SUBMITTED','MODERATED','APPROVED','PUBLISHED','REJECTED')),
    CONSTRAINT ck_result_batch_stage_evidence CHECK (
      (status='DRAFT' AND submitted_at IS NULL AND moderated_at IS NULL AND approved_at IS NULL AND published_at IS NULL)
      OR (status IN ('SUBMITTED','REJECTED') AND submitted_at IS NOT NULL AND moderated_at IS NULL AND approved_at IS NULL AND published_at IS NULL)
      OR (status='MODERATED' AND submitted_at IS NOT NULL AND moderated_at IS NOT NULL AND approved_at IS NULL AND published_at IS NULL)
      OR (status='APPROVED' AND submitted_at IS NOT NULL AND moderated_at IS NOT NULL AND approved_at IS NOT NULL AND published_at IS NULL)
      OR (status='PUBLISHED' AND submitted_at IS NOT NULL AND moderated_at IS NOT NULL AND approved_at IS NOT NULL AND published_at IS NOT NULL)
    ),
    CONSTRAINT ck_result_batch_separation CHECK (
      moderated_by_user_id IS NULL OR (moderated_by_user_id <> submitted_by_user_id
        AND (approved_by_user_id IS NULL OR approved_by_user_id <> moderated_by_user_id))
    )
);

CREATE TABLE module_results (
    id uuid PRIMARY KEY,
    result_batch_id uuid NOT NULL REFERENCES result_batches(id),
    calculation_outcome_id uuid NOT NULL REFERENCES assessment_calculation_outcomes(id),
    assessment_roster_entry_id uuid NOT NULL REFERENCES assessment_roster_entries(id),
    coursework_mark numeric(6,2) NOT NULL,
    examination_mark numeric(6,2) NOT NULL,
    final_mark numeric(6,2) NOT NULL,
    grade varchar(10) NOT NULL,
    remark varchar(100) NOT NULL,
    result_status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT uk_module_result_batch_roster UNIQUE (result_batch_id, assessment_roster_entry_id),
    CONSTRAINT uk_module_result_outcome UNIQUE (calculation_outcome_id),
    CONSTRAINT ck_module_result_marks CHECK (
      coursework_mark BETWEEN 0 AND 100 AND examination_mark BETWEEN 0 AND 100 AND final_mark BETWEEN 0 AND 100),
    CONSTRAINT ck_module_result_status CHECK (result_status IN ('PASS','FAIL'))
);

CREATE TABLE result_batch_status_events (
    id uuid PRIMARY KEY,
    result_batch_id uuid NOT NULL REFERENCES result_batches(id),
    from_status varchar(20), to_status varchar(20) NOT NULL,
    reason varchar(1000) NOT NULL, actor_user_id uuid NOT NULL, occurred_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_result_event_statuses CHECK (
      (from_status IS NULL OR from_status IN ('DRAFT','SUBMITTED','MODERATED','APPROVED','PUBLISHED','REJECTED'))
      AND to_status IN ('DRAFT','SUBMITTED','MODERATED','APPROVED','PUBLISHED','REJECTED')),
    CONSTRAINT ck_result_event_reason CHECK(length(trim(reason))>0)
);

CREATE TABLE published_results (
    id uuid PRIMARY KEY,
    result_batch_id uuid NOT NULL REFERENCES result_batches(id),
    module_result_id uuid NOT NULL REFERENCES module_results(id),
    student_id uuid NOT NULL,
    student_number varchar(40) NOT NULL,
    module_id uuid NOT NULL,
    module_code varchar(50) NOT NULL,
    module_name varchar(200) NOT NULL,
    academic_period_id uuid NOT NULL,
    academic_period_code varchar(50) NOT NULL,
    final_mark numeric(6,2) NOT NULL,
    grade varchar(10) NOT NULL,
    remark varchar(100) NOT NULL,
    published_by_user_id uuid NOT NULL, published_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT uk_published_result_module_result UNIQUE(module_result_id),
    CONSTRAINT uk_published_result_student_module_period UNIQUE(student_id,module_id,academic_period_id),
    CONSTRAINT ck_published_result_mark CHECK(final_mark BETWEEN 0 AND 100)
);

CREATE INDEX idx_result_batches_queue ON result_batches(status, created_at);
CREATE INDEX idx_published_results_student ON published_results(student_id, academic_period_id);

CREATE TABLE result_batches_aud (
 id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint,
 module_offering_id uuid, calculation_run_id uuid, grading_scheme_id uuid, batch_number varchar(50), status varchar(20), status_reason varchar(1000),
 submitted_by_user_id uuid, submitted_at timestamptz, moderated_by_user_id uuid, moderated_at timestamptz,
 approved_by_user_id uuid, approved_at timestamptz, published_by_user_id uuid, published_at timestamptz,
 created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
 deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev));
CREATE TABLE assessment_calculation_component_evidence_aud (
 id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint,
 calculation_run_id uuid, calculation_outcome_id uuid, assessment_component_id uuid, submitted_mark_id uuid,
 component_code varchar(30), component_type varchar(30), score numeric(8,2), maximum_mark numeric(8,2),
 weight_percent numeric(5,2), weighted_contribution numeric(6,2), created_at timestamptz, updated_at timestamptz,
 created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev));
CREATE TABLE grading_schemes_aud (
 id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint,
 code varchar(30), name varchar(150), scheme_version integer, status varchar(20), approved_by_user_id uuid,
 approved_at timestamptz, approval_reason varchar(1000), created_at timestamptz, updated_at timestamptz,
 created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev));
CREATE TABLE grading_bands_aud (
 id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint,
 grading_scheme_id uuid, minimum_mark numeric(6,2), maximum_mark numeric(6,2), grade varchar(10), remark varchar(100), passing boolean, sort_order integer,
 created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
 deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev));
CREATE TABLE module_results_aud (
 id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint,
 result_batch_id uuid, calculation_outcome_id uuid, assessment_roster_entry_id uuid,
 coursework_mark numeric(6,2), examination_mark numeric(6,2), final_mark numeric(6,2), grade varchar(10), remark varchar(100), result_status varchar(20),
 created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
 deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev));
CREATE TABLE result_batch_status_events_aud (
 id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint,
 result_batch_id uuid, from_status varchar(20), to_status varchar(20), reason varchar(1000), actor_user_id uuid, occurred_at timestamptz,
 created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
 deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev));
CREATE TABLE published_results_aud (
 id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint,
 result_batch_id uuid, module_result_id uuid, student_id uuid, student_number varchar(40), module_id uuid,
 module_code varchar(50), module_name varchar(200), academic_period_id uuid, academic_period_code varchar(50),
 final_mark numeric(6,2), grade varchar(10), remark varchar(100), published_by_user_id uuid, published_at timestamptz,
 created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
 deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev));

CREATE OR REPLACE FUNCTION prevent_module_result_evidence_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.result_batch_id IS DISTINCT FROM OLD.result_batch_id OR NEW.calculation_outcome_id IS DISTINCT FROM OLD.calculation_outcome_id
 OR NEW.assessment_roster_entry_id IS DISTINCT FROM OLD.assessment_roster_entry_id OR NEW.coursework_mark IS DISTINCT FROM OLD.coursework_mark
 OR NEW.examination_mark IS DISTINCT FROM OLD.examination_mark OR NEW.final_mark IS DISTINCT FROM OLD.final_mark
 OR NEW.grade IS DISTINCT FROM OLD.grade OR NEW.remark IS DISTINCT FROM OLD.remark OR NEW.result_status IS DISTINCT FROM OLD.result_status
 THEN RAISE EXCEPTION 'Calculated Module result evidence is immutable'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER trg_module_result_evidence_immutable BEFORE UPDATE ON module_results FOR EACH ROW EXECUTE FUNCTION prevent_module_result_evidence_change();

CREATE OR REPLACE FUNCTION prevent_published_result_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Published results are immutable; use a governed correction'; END; $$;
CREATE TRIGGER trg_published_result_immutable BEFORE UPDATE OR DELETE ON published_results FOR EACH ROW EXECUTE FUNCTION prevent_published_result_change();

CREATE OR REPLACE FUNCTION enforce_grading_band_scheme_mutability() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF EXISTS(SELECT 1 FROM grading_schemes WHERE id=COALESCE(NEW.grading_scheme_id,OLD.grading_scheme_id) AND status<>'DRAFT')
 THEN RAISE EXCEPTION 'Bands of an approved grading scheme are immutable'; END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END; $$;
CREATE TRIGGER trg_grading_band_scheme_mutability BEFORE INSERT OR UPDATE OR DELETE ON grading_bands FOR EACH ROW EXECUTE FUNCTION enforce_grading_band_scheme_mutability();

CREATE OR REPLACE FUNCTION prevent_calculation_component_evidence_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Calculation component evidence is immutable'; END; $$;
CREATE TRIGGER trg_calculation_component_evidence_immutable BEFORE UPDATE OR DELETE ON assessment_calculation_component_evidence FOR EACH ROW EXECUTE FUNCTION prevent_calculation_component_evidence_change();

CREATE OR REPLACE FUNCTION validate_grading_scheme_on_approval() RETURNS trigger LANGUAGE plpgsql AS $$
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
CREATE TRIGGER trg_validate_grading_scheme_approval BEFORE UPDATE ON grading_schemes FOR EACH ROW EXECUTE FUNCTION validate_grading_scheme_on_approval();
