-- Author: Tinashe K

CREATE TABLE progression_rule_sets (
    id uuid PRIMARY KEY,
    rule_code varchar(40) NOT NULL,
    rule_name varchar(180) NOT NULL,
    programme_id uuid NOT NULL,
    programme_version_id uuid NOT NULL,
    programme_period_number integer NOT NULL,
    rule_version integer NOT NULL,
    status varchar(20) NOT NULL,
    approved_by_user_id uuid,
    approved_at timestamptz,
    approval_reason varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_progression_rule_code_version UNIQUE (rule_code, rule_version),
    CONSTRAINT ck_progression_rule_period CHECK (programme_period_number > 0),
    CONSTRAINT ck_progression_rule_version CHECK (rule_version > 0),
    CONSTRAINT ck_progression_rule_status CHECK (status IN ('DRAFT', 'APPROVED', 'SUPERSEDED')),
    CONSTRAINT ck_progression_rule_approval CHECK (
        (status = 'DRAFT' AND approved_by_user_id IS NULL AND approved_at IS NULL AND approval_reason IS NULL)
        OR (status IN ('APPROVED', 'SUPERSEDED') AND approved_by_user_id IS NOT NULL
            AND approved_at IS NOT NULL AND length(trim(approval_reason)) > 0)
    )
);

CREATE UNIQUE INDEX uk_progression_rule_approved_scope
    ON progression_rule_sets (programme_version_id, programme_period_number)
    WHERE status = 'APPROVED' AND deleted_at IS NULL;

CREATE TABLE progression_rule_outcomes (
    id uuid PRIMARY KEY,
    progression_rule_set_id uuid NOT NULL REFERENCES progression_rule_sets (id),
    priority integer NOT NULL,
    decision_code varchar(30) NOT NULL,
    decision_label varchar(150) NOT NULL,
    minimum_weighted_average numeric(6,2),
    minimum_passed_credits numeric(8,2),
    maximum_failed_credits numeric(8,2),
    maximum_failed_modules integer,
    require_all_compulsory_passed boolean NOT NULL,
    next_programme_period_number integer,
    fallback_outcome boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_progression_rule_outcome_priority UNIQUE (progression_rule_set_id, priority),
    CONSTRAINT ck_progression_rule_outcome_priority CHECK (priority > 0),
    CONSTRAINT ck_progression_rule_outcome_decision CHECK (
        decision_code IN ('PROCEED', 'PROCEED_WITH_CARRY', 'REPEAT', 'EXCLUDE')
    ),
    CONSTRAINT ck_progression_rule_outcome_average CHECK (
        minimum_weighted_average IS NULL OR minimum_weighted_average BETWEEN 0 AND 100
    ),
    CONSTRAINT ck_progression_rule_outcome_credits CHECK (
        (minimum_passed_credits IS NULL OR minimum_passed_credits >= 0)
        AND (maximum_failed_credits IS NULL OR maximum_failed_credits >= 0)
    ),
    CONSTRAINT ck_progression_rule_outcome_failed_modules CHECK (
        maximum_failed_modules IS NULL OR maximum_failed_modules >= 0
    ),
    CONSTRAINT ck_progression_rule_outcome_next_period CHECK (
        next_programme_period_number IS NULL OR next_programme_period_number > 0
    ),
    CONSTRAINT ck_progression_rule_outcome_fallback CHECK (
        NOT fallback_outcome OR (
            minimum_weighted_average IS NULL
            AND minimum_passed_credits IS NULL
            AND maximum_failed_credits IS NULL
            AND maximum_failed_modules IS NULL
            AND NOT require_all_compulsory_passed
        )
    )
);

CREATE TABLE student_overall_decisions (
    id uuid PRIMARY KEY,
    progression_rule_set_id uuid NOT NULL REFERENCES progression_rule_sets (id),
    registration_roster_import_id uuid NOT NULL REFERENCES registration_roster_imports (id),
    decision_number varchar(80) NOT NULL,
    decision_version integer NOT NULL,
    supersedes_decision_id uuid REFERENCES student_overall_decisions (id),
    student_id uuid NOT NULL,
    student_number varchar(40) NOT NULL,
    programme_enrolment_id uuid NOT NULL,
    programme_id uuid NOT NULL,
    programme_version_id uuid NOT NULL,
    academic_period_id uuid NOT NULL,
    academic_period_code varchar(50) NOT NULL,
    programme_period_number integer NOT NULL,
    matched_outcome_id uuid NOT NULL REFERENCES progression_rule_outcomes (id),
    decision_code varchar(30) NOT NULL,
    decision_label varchar(150) NOT NULL,
    next_programme_period_number integer,
    attempted_credits numeric(8,2) NOT NULL,
    passed_credits numeric(8,2) NOT NULL,
    failed_credits numeric(8,2) NOT NULL,
    failed_modules integer NOT NULL,
    failed_compulsory_modules integer NOT NULL,
    weighted_average numeric(6,2) NOT NULL,
    status varchar(20) NOT NULL,
    status_reason varchar(1000) NOT NULL,
    calculated_by_user_id uuid NOT NULL,
    calculated_at timestamptz NOT NULL,
    reviewed_by_user_id uuid,
    reviewed_at timestamptz,
    review_reason varchar(1000),
    approved_by_user_id uuid,
    approved_at timestamptz,
    approval_reason varchar(1000),
    published_by_user_id uuid,
    published_at timestamptz,
    publication_reason varchar(1000),
    rejected_by_user_id uuid,
    rejected_at timestamptz,
    rejection_reason varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_student_overall_decision_number UNIQUE (decision_number),
    CONSTRAINT uk_student_overall_decision_version UNIQUE (registration_roster_import_id, decision_version),
    CONSTRAINT uk_student_overall_decision_supersedes UNIQUE (supersedes_decision_id),
    CONSTRAINT ck_student_overall_decision_version CHECK (decision_version > 0),
    CONSTRAINT ck_student_overall_decision_period CHECK (programme_period_number > 0),
    CONSTRAINT ck_student_overall_decision_code CHECK (
        decision_code IN ('PROCEED', 'PROCEED_WITH_CARRY', 'REPEAT', 'EXCLUDE')
    ),
    CONSTRAINT ck_student_overall_decision_metrics CHECK (
        attempted_credits > 0 AND passed_credits >= 0 AND failed_credits >= 0
        AND passed_credits + failed_credits = attempted_credits
        AND failed_modules >= 0 AND failed_compulsory_modules >= 0
        AND failed_compulsory_modules <= failed_modules
        AND weighted_average BETWEEN 0 AND 100
    ),
    CONSTRAINT ck_student_overall_decision_status CHECK (
        status IN ('CALCULATED', 'REVIEWED', 'APPROVED', 'PUBLISHED', 'REJECTED')
    ),
    CONSTRAINT ck_student_overall_decision_lineage CHECK (
        (decision_version = 1 AND supersedes_decision_id IS NULL)
        OR (decision_version > 1 AND supersedes_decision_id IS NOT NULL)
    ),
    CONSTRAINT ck_student_overall_decision_workflow CHECK (
        (status = 'CALCULATED' AND reviewed_at IS NULL AND approved_at IS NULL
            AND published_at IS NULL AND rejected_at IS NULL)
        OR (status = 'REVIEWED' AND reviewed_at IS NOT NULL AND approved_at IS NULL
            AND published_at IS NULL AND rejected_at IS NULL)
        OR (status = 'APPROVED' AND reviewed_at IS NOT NULL AND approved_at IS NOT NULL
            AND published_at IS NULL AND rejected_at IS NULL)
        OR (status = 'PUBLISHED' AND reviewed_at IS NOT NULL AND approved_at IS NOT NULL
            AND published_at IS NOT NULL AND rejected_at IS NULL)
        OR (status = 'REJECTED' AND rejected_at IS NOT NULL AND published_at IS NULL)
    ),
    CONSTRAINT ck_student_overall_decision_separation CHECK (
        (reviewed_by_user_id IS NULL OR reviewed_by_user_id <> calculated_by_user_id)
        AND (approved_by_user_id IS NULL OR (
            approved_by_user_id <> calculated_by_user_id
            AND approved_by_user_id <> reviewed_by_user_id
        ))
        AND (published_by_user_id IS NULL OR (
            published_by_user_id <> calculated_by_user_id
            AND published_by_user_id <> reviewed_by_user_id
            AND published_by_user_id <> approved_by_user_id
        ))
    )
);

CREATE TABLE student_overall_decision_results (
    id uuid PRIMARY KEY,
    student_overall_decision_id uuid NOT NULL REFERENCES student_overall_decisions (id),
    published_result_id uuid NOT NULL REFERENCES published_results (id),
    assessment_roster_entry_id uuid NOT NULL REFERENCES assessment_roster_entries (id),
    module_id uuid NOT NULL,
    module_code varchar(50) NOT NULL,
    module_name varchar(200) NOT NULL,
    curriculum_module_type varchar(20) NOT NULL,
    credit_value numeric(6,2) NOT NULL,
    final_mark numeric(6,2) NOT NULL,
    grade varchar(10) NOT NULL,
    remark varchar(100) NOT NULL,
    passing boolean NOT NULL,
    publication_version integer NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_overall_decision_roster_result UNIQUE (
        student_overall_decision_id, assessment_roster_entry_id
    ),
    CONSTRAINT uk_overall_decision_published_result UNIQUE (
        student_overall_decision_id, published_result_id
    ),
    CONSTRAINT ck_overall_decision_result_type CHECK (
        curriculum_module_type IN ('COMPULSORY', 'ELECTIVE', 'OPTIONAL')
    ),
    CONSTRAINT ck_overall_decision_result_values CHECK (
        credit_value > 0 AND final_mark BETWEEN 0 AND 100 AND publication_version > 0
    )
);

CREATE TABLE student_overall_decision_events (
    id uuid PRIMARY KEY,
    student_overall_decision_id uuid NOT NULL REFERENCES student_overall_decisions (id),
    from_status varchar(20),
    to_status varchar(20) NOT NULL,
    reason varchar(1000) NOT NULL,
    actor_user_id uuid NOT NULL,
    occurred_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_overall_decision_event_status CHECK (
        (from_status IS NULL OR from_status IN ('CALCULATED', 'REVIEWED', 'APPROVED', 'PUBLISHED', 'REJECTED'))
        AND to_status IN ('CALCULATED', 'REVIEWED', 'APPROVED', 'PUBLISHED', 'REJECTED')
    ),
    CONSTRAINT ck_overall_decision_event_reason CHECK (length(trim(reason)) > 0)
);

CREATE INDEX idx_progression_decision_queue ON student_overall_decisions (status, calculated_at DESC);
CREATE INDEX idx_progression_decision_student ON student_overall_decisions (student_id, academic_period_id, decision_version DESC);

CREATE TABLE progression_rule_sets_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    rule_code varchar(40), rule_name varchar(180), programme_id uuid, programme_version_id uuid,
    programme_period_number integer, rule_version integer, status varchar(20),
    approved_by_user_id uuid, approved_at timestamptz, approval_reason varchar(1000),
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid,
    modified_by_user_id uuid, deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE progression_rule_outcomes_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    progression_rule_set_id uuid, priority integer, decision_code varchar(30), decision_label varchar(150),
    minimum_weighted_average numeric(6,2), minimum_passed_credits numeric(8,2),
    maximum_failed_credits numeric(8,2), maximum_failed_modules integer,
    require_all_compulsory_passed boolean, next_programme_period_number integer, fallback_outcome boolean,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid,
    modified_by_user_id uuid, deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE student_overall_decisions_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    progression_rule_set_id uuid, registration_roster_import_id uuid, decision_number varchar(80),
    decision_version integer, supersedes_decision_id uuid, student_id uuid, student_number varchar(40),
    programme_enrolment_id uuid, programme_id uuid, programme_version_id uuid, academic_period_id uuid,
    academic_period_code varchar(50), programme_period_number integer, matched_outcome_id uuid,
    decision_code varchar(30), decision_label varchar(150), next_programme_period_number integer,
    attempted_credits numeric(8,2), passed_credits numeric(8,2), failed_credits numeric(8,2),
    failed_modules integer, failed_compulsory_modules integer, weighted_average numeric(6,2),
    status varchar(20), status_reason varchar(1000), calculated_by_user_id uuid, calculated_at timestamptz,
    reviewed_by_user_id uuid, reviewed_at timestamptz, review_reason varchar(1000),
    approved_by_user_id uuid, approved_at timestamptz, approval_reason varchar(1000),
    published_by_user_id uuid, published_at timestamptz, publication_reason varchar(1000),
    rejected_by_user_id uuid, rejected_at timestamptz, rejection_reason varchar(1000),
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid,
    modified_by_user_id uuid, deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE student_overall_decision_results_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    student_overall_decision_id uuid, published_result_id uuid, assessment_roster_entry_id uuid,
    module_id uuid, module_code varchar(50), module_name varchar(200), curriculum_module_type varchar(20),
    credit_value numeric(6,2), final_mark numeric(6,2), grade varchar(10), remark varchar(100),
    passing boolean, publication_version integer, created_at timestamptz, updated_at timestamptz,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);

CREATE TABLE student_overall_decision_events_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    student_overall_decision_id uuid, from_status varchar(20), to_status varchar(20),
    reason varchar(1000), actor_user_id uuid, occurred_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid,
    modified_by_user_id uuid, deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

CREATE OR REPLACE FUNCTION enforce_progression_outcome_mutability() RETURNS trigger LANGUAGE plpgsql AS $$
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
CREATE TRIGGER trg_progression_outcome_mutability
    BEFORE INSERT OR UPDATE OR DELETE ON progression_rule_outcomes
    FOR EACH ROW EXECUTE FUNCTION enforce_progression_outcome_mutability();

CREATE OR REPLACE FUNCTION validate_progression_rule_approval() RETURNS trigger LANGUAGE plpgsql AS $$
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
CREATE TRIGGER trg_validate_progression_rule_approval
    BEFORE UPDATE ON progression_rule_sets
    FOR EACH ROW EXECUTE FUNCTION validate_progression_rule_approval();

CREATE OR REPLACE FUNCTION prevent_approved_progression_rule_change() RETURNS trigger LANGUAGE plpgsql AS $$
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
CREATE TRIGGER trg_approved_progression_rule_immutable
    BEFORE UPDATE ON progression_rule_sets
    FOR EACH ROW EXECUTE FUNCTION prevent_approved_progression_rule_change();

CREATE OR REPLACE FUNCTION prevent_overall_decision_result_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Progression decision source evidence is immutable';
END;
$$;
CREATE TRIGGER trg_overall_decision_result_immutable
    BEFORE UPDATE OR DELETE ON student_overall_decision_results
    FOR EACH ROW EXECUTE FUNCTION prevent_overall_decision_result_change();

CREATE OR REPLACE FUNCTION validate_overall_decision_result_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
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
CREATE TRIGGER trg_validate_overall_decision_result_snapshot
    BEFORE INSERT ON student_overall_decision_results
    FOR EACH ROW EXECUTE FUNCTION validate_overall_decision_result_snapshot();

CREATE OR REPLACE FUNCTION prevent_overall_decision_evidence_change() RETURNS trigger LANGUAGE plpgsql AS $$
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
CREATE TRIGGER trg_overall_decision_evidence_immutable
    BEFORE UPDATE ON student_overall_decisions
    FOR EACH ROW EXECUTE FUNCTION prevent_overall_decision_evidence_change();

CREATE OR REPLACE FUNCTION validate_overall_decision_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
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
CREATE CONSTRAINT TRIGGER trg_validate_overall_decision_evidence
    AFTER INSERT ON student_overall_decisions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_overall_decision_evidence();

CREATE OR REPLACE FUNCTION validate_overall_decision_lineage() RETURNS trigger LANGUAGE plpgsql AS $$
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
CREATE TRIGGER trg_validate_overall_decision_lineage
    BEFORE INSERT ON student_overall_decisions
    FOR EACH ROW EXECUTE FUNCTION validate_overall_decision_lineage();
