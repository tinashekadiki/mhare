-- Author: Tinashe K

ALTER TABLE published_results
    DROP CONSTRAINT uk_published_result_student_module_period,
    ADD COLUMN publication_version integer NOT NULL DEFAULT 1,
    ADD COLUMN supersedes_published_result_id uuid REFERENCES published_results(id),
    ADD COLUMN result_amendment_id uuid;

ALTER TABLE published_results
    ADD CONSTRAINT uk_published_result_student_module_period_version
        UNIQUE (student_id, module_id, academic_period_id, publication_version),
    ADD CONSTRAINT uk_published_result_supersedes UNIQUE (supersedes_published_result_id),
    ADD CONSTRAINT ck_published_result_version_lineage CHECK (
        publication_version > 0
        AND ((publication_version = 1 AND supersedes_published_result_id IS NULL AND result_amendment_id IS NULL)
          OR (publication_version > 1 AND supersedes_published_result_id IS NOT NULL AND result_amendment_id IS NOT NULL))
    );

ALTER TABLE published_results_aud
    ADD COLUMN publication_version integer,
    ADD COLUMN supersedes_published_result_id uuid,
    ADD COLUMN result_amendment_id uuid;

CREATE TABLE published_result_amendments (
    id uuid PRIMARY KEY,
    amendment_number varchar(60) NOT NULL,
    original_published_result_id uuid NOT NULL REFERENCES published_results(id),
    replacement_result_batch_id uuid NOT NULL REFERENCES result_batches(id),
    replacement_module_result_id uuid NOT NULL REFERENCES module_results(id),
    proposed_final_mark numeric(6,2) NOT NULL,
    proposed_grade varchar(10) NOT NULL,
    proposed_remark varchar(100) NOT NULL,
    request_reason varchar(1000) NOT NULL,
    status varchar(20) NOT NULL,
    requested_by_user_id uuid NOT NULL,
    requested_at timestamptz NOT NULL,
    reviewed_by_user_id uuid,
    reviewed_at timestamptz,
    review_reason varchar(1000),
    approved_by_user_id uuid,
    approved_at timestamptz,
    approval_reason varchar(1000),
    applied_by_user_id uuid,
    applied_at timestamptz,
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
    CONSTRAINT uk_published_result_amendment_number UNIQUE (amendment_number),
    CONSTRAINT ck_published_result_amendment_mark CHECK (proposed_final_mark BETWEEN 0 AND 100),
    CONSTRAINT ck_published_result_amendment_text CHECK (
        length(trim(request_reason)) > 0
        AND length(trim(proposed_grade)) > 0
        AND length(trim(proposed_remark)) > 0
    ),
    CONSTRAINT ck_published_result_amendment_status CHECK (
        status IN ('REQUESTED', 'REVIEWED', 'APPROVED', 'APPLIED', 'REJECTED')
    ),
    CONSTRAINT ck_published_result_amendment_stage_evidence CHECK (
        (status = 'REQUESTED'
          AND reviewed_at IS NULL AND approved_at IS NULL AND applied_at IS NULL AND rejected_at IS NULL)
        OR (status = 'REVIEWED'
          AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL AND length(trim(review_reason)) > 0
          AND approved_at IS NULL AND applied_at IS NULL AND rejected_at IS NULL)
        OR (status = 'APPROVED'
          AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL AND length(trim(review_reason)) > 0
          AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL AND length(trim(approval_reason)) > 0
          AND applied_at IS NULL AND rejected_at IS NULL)
        OR (status = 'APPLIED'
          AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL AND length(trim(review_reason)) > 0
          AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL AND length(trim(approval_reason)) > 0
          AND applied_by_user_id IS NOT NULL AND applied_at IS NOT NULL AND rejected_at IS NULL)
        OR (status = 'REJECTED'
          AND rejected_by_user_id IS NOT NULL AND rejected_at IS NOT NULL AND length(trim(rejection_reason)) > 0
          AND approved_at IS NULL AND applied_at IS NULL)
    ),
    CONSTRAINT ck_published_result_amendment_separation CHECK (
        (reviewed_by_user_id IS NULL OR reviewed_by_user_id <> requested_by_user_id)
        AND (approved_by_user_id IS NULL OR (
            approved_by_user_id <> requested_by_user_id
            AND approved_by_user_id <> reviewed_by_user_id))
        AND (applied_by_user_id IS NULL OR applied_by_user_id <> approved_by_user_id)
        AND (rejected_by_user_id IS NULL OR rejected_by_user_id <> requested_by_user_id)
    )
);

CREATE UNIQUE INDEX uk_active_published_result_amendment_original
    ON published_result_amendments(original_published_result_id)
    WHERE status <> 'REJECTED' AND deleted_at IS NULL;
CREATE UNIQUE INDEX uk_active_published_result_amendment_replacement
    ON published_result_amendments(replacement_module_result_id)
    WHERE status <> 'REJECTED' AND deleted_at IS NULL;
CREATE INDEX idx_published_result_amendments_queue
    ON published_result_amendments(status, requested_at);

ALTER TABLE published_results
    ADD CONSTRAINT fk_published_result_amendment
        FOREIGN KEY (result_amendment_id) REFERENCES published_result_amendments(id),
    ADD CONSTRAINT uk_published_result_amendment UNIQUE (result_amendment_id);

CREATE TABLE published_result_amendment_events (
    id uuid PRIMARY KEY,
    published_result_amendment_id uuid NOT NULL REFERENCES published_result_amendments(id),
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
    CONSTRAINT ck_published_result_amendment_event_status CHECK (
        (from_status IS NULL OR from_status IN ('REQUESTED', 'REVIEWED', 'APPROVED', 'APPLIED', 'REJECTED'))
        AND to_status IN ('REQUESTED', 'REVIEWED', 'APPROVED', 'APPLIED', 'REJECTED')
    ),
    CONSTRAINT ck_published_result_amendment_event_reason CHECK (length(trim(reason)) > 0)
);

CREATE TABLE published_result_amendments_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo(rev),
    revtype smallint,
    amendment_number varchar(60),
    original_published_result_id uuid,
    replacement_result_batch_id uuid,
    replacement_module_result_id uuid,
    proposed_final_mark numeric(6,2),
    proposed_grade varchar(10),
    proposed_remark varchar(100),
    request_reason varchar(1000),
    status varchar(20),
    requested_by_user_id uuid,
    requested_at timestamptz,
    reviewed_by_user_id uuid,
    reviewed_at timestamptz,
    review_reason varchar(1000),
    approved_by_user_id uuid,
    approved_at timestamptz,
    approval_reason varchar(1000),
    applied_by_user_id uuid,
    applied_at timestamptz,
    rejected_by_user_id uuid,
    rejected_at timestamptz,
    rejection_reason varchar(1000),
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE published_result_amendment_events_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo(rev),
    revtype smallint,
    published_result_amendment_id uuid,
    from_status varchar(20),
    to_status varchar(20),
    reason varchar(1000),
    actor_user_id uuid,
    occurred_at timestamptz,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

CREATE OR REPLACE FUNCTION validate_published_result_amendment_evidence()
RETURNS trigger LANGUAGE plpgsql AS $$
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

CREATE TRIGGER trg_validate_published_result_amendment_evidence
    BEFORE INSERT ON published_result_amendments
    FOR EACH ROW EXECUTE FUNCTION validate_published_result_amendment_evidence();

CREATE OR REPLACE FUNCTION prevent_published_result_amendment_evidence_change()
RETURNS trigger LANGUAGE plpgsql AS $$
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

CREATE TRIGGER trg_published_result_amendment_evidence_immutable
    BEFORE UPDATE ON published_result_amendments
    FOR EACH ROW EXECUTE FUNCTION prevent_published_result_amendment_evidence_change();

CREATE OR REPLACE FUNCTION validate_published_result_lineage()
RETURNS trigger LANGUAGE plpgsql AS $$
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

CREATE TRIGGER trg_validate_published_result_lineage
    BEFORE INSERT ON published_results
    FOR EACH ROW EXECUTE FUNCTION validate_published_result_lineage();

CREATE OR REPLACE FUNCTION prevent_published_result_amendment_event_change()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Published result amendment events are append-only';
END;
$$;

CREATE TRIGGER trg_published_result_amendment_event_immutable
    BEFORE UPDATE OR DELETE ON published_result_amendment_events
    FOR EACH ROW EXECUTE FUNCTION prevent_published_result_amendment_event_change();
