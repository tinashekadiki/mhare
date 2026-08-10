-- Author: Tinashe K

CREATE SEQUENCE IF NOT EXISTS revinfo_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE published_result_projections (
    id uuid PRIMARY KEY,
    source_event_id uuid NOT NULL,
    source_published_result_id uuid NOT NULL,
    source_result_batch_id uuid NOT NULL,
    source_module_result_id uuid NOT NULL,
    student_id uuid NOT NULL,
    student_number varchar(40) NOT NULL,
    programme_enrolment_id uuid NOT NULL,
    programme_id uuid NOT NULL,
    programme_version_id uuid NOT NULL,
    academic_period_id uuid NOT NULL,
    academic_period_code varchar(50) NOT NULL,
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
    supersedes_published_result_id uuid,
    result_amendment_id uuid,
    published_by_user_id uuid NOT NULL,
    published_at timestamptz NOT NULL,
    current_version boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_result_projection_source_event UNIQUE (source_event_id),
    CONSTRAINT uk_result_projection_source_result UNIQUE (source_published_result_id),
    CONSTRAINT ck_result_projection_type CHECK (
        curriculum_module_type IN ('COMPULSORY', 'ELECTIVE', 'OPTIONAL')
    ),
    CONSTRAINT ck_result_projection_values CHECK (
        credit_value > 0 AND final_mark BETWEEN 0 AND 100 AND publication_version > 0
    ),
    CONSTRAINT ck_result_projection_lineage CHECK (
        (publication_version = 1 AND supersedes_published_result_id IS NULL)
        OR (publication_version > 1 AND supersedes_published_result_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_result_projection_current_scope
    ON published_result_projections (student_id, academic_period_id, module_id)
    WHERE current_version AND deleted_at IS NULL;
CREATE INDEX idx_result_projection_student_period
    ON published_result_projections (student_number, academic_period_code, module_code)
    WHERE current_version AND deleted_at IS NULL;

CREATE TABLE progression_decision_projections (
    id uuid PRIMARY KEY,
    source_event_id uuid NOT NULL,
    source_progression_decision_id uuid NOT NULL,
    decision_number varchar(80) NOT NULL,
    decision_version integer NOT NULL,
    supersedes_decision_id uuid,
    source_progression_rule_set_id uuid NOT NULL,
    progression_rule_code varchar(40) NOT NULL,
    progression_rule_version integer NOT NULL,
    source_registration_roster_import_id uuid NOT NULL,
    student_id uuid NOT NULL,
    student_number varchar(40) NOT NULL,
    programme_enrolment_id uuid NOT NULL,
    programme_id uuid NOT NULL,
    programme_version_id uuid NOT NULL,
    academic_period_id uuid NOT NULL,
    academic_period_code varchar(50) NOT NULL,
    programme_period_number integer NOT NULL,
    decision_code varchar(30) NOT NULL,
    decision_label varchar(150) NOT NULL,
    next_programme_period_number integer,
    attempted_credits numeric(8,2) NOT NULL,
    passed_credits numeric(8,2) NOT NULL,
    failed_credits numeric(8,2) NOT NULL,
    failed_modules integer NOT NULL,
    failed_compulsory_modules integer NOT NULL,
    weighted_average numeric(6,2) NOT NULL,
    published_by_user_id uuid NOT NULL,
    published_at timestamptz NOT NULL,
    current_version boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_progression_projection_event UNIQUE (source_event_id),
    CONSTRAINT uk_progression_projection_source UNIQUE (source_progression_decision_id),
    CONSTRAINT uk_progression_projection_number UNIQUE (decision_number),
    CONSTRAINT ck_progression_projection_version CHECK (
        decision_version > 0 AND progression_rule_version > 0 AND programme_period_number > 0
    ),
    CONSTRAINT ck_progression_projection_decision CHECK (
        decision_code IN ('PROCEED', 'PROCEED_WITH_CARRY', 'REPEAT', 'EXCLUDE')
    ),
    CONSTRAINT ck_progression_projection_metrics CHECK (
        attempted_credits > 0 AND passed_credits >= 0 AND failed_credits >= 0
        AND passed_credits + failed_credits = attempted_credits
        AND failed_modules >= 0 AND failed_compulsory_modules >= 0
        AND failed_compulsory_modules <= failed_modules
        AND weighted_average BETWEEN 0 AND 100
    ),
    CONSTRAINT ck_progression_projection_lineage CHECK (
        (decision_version = 1 AND supersedes_decision_id IS NULL)
        OR (decision_version > 1 AND supersedes_decision_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_progression_projection_current_scope
    ON progression_decision_projections (student_id, academic_period_id)
    WHERE current_version AND deleted_at IS NULL;
CREATE INDEX idx_progression_projection_student_period
    ON progression_decision_projections (student_number, academic_period_code, decision_version DESC);

CREATE TABLE progression_decision_result_projections (
    id uuid PRIMARY KEY,
    progression_decision_projection_id uuid NOT NULL
        REFERENCES progression_decision_projections (id),
    published_result_projection_id uuid NOT NULL
        REFERENCES published_result_projections (id),
    source_published_result_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_progression_result_projection UNIQUE (
        progression_decision_projection_id, source_published_result_id
    )
);

CREATE TABLE generated_documents (
    id uuid PRIMARY KEY,
    document_number varchar(100) NOT NULL,
    document_type varchar(40) NOT NULL,
    student_id uuid NOT NULL,
    student_number varchar(40) NOT NULL,
    programme_id uuid NOT NULL,
    programme_version_id uuid NOT NULL,
    academic_period_id uuid NOT NULL,
    academic_period_code varchar(50) NOT NULL,
    source_progression_decision_id uuid NOT NULL,
    source_progression_decision_version integer NOT NULL,
    progression_decision_projection_id uuid NOT NULL
        REFERENCES progression_decision_projections (id),
    template_code varchar(80) NOT NULL,
    template_version integer NOT NULL,
    status varchar(20) NOT NULL,
    storage_bucket varchar(100),
    storage_key varchar(500),
    storage_object_version varchar(200),
    content_type varchar(100),
    checksum_sha256 varchar(64),
    size_bytes bigint,
    page_count integer,
    requested_at timestamptz NOT NULL,
    generation_started_at timestamptz,
    generated_at timestamptz,
    generation_attempt_count integer NOT NULL,
    next_generation_attempt_at timestamptz NOT NULL,
    last_failure_reason varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_generated_document_number UNIQUE (document_number),
    CONSTRAINT uk_generated_document_source UNIQUE (
        document_type, source_progression_decision_id, source_progression_decision_version
    ),
    CONSTRAINT uk_generated_document_storage_key UNIQUE (storage_bucket, storage_key),
    CONSTRAINT ck_generated_document_type CHECK (document_type IN ('RESULT_SLIP')),
    CONSTRAINT ck_generated_document_status CHECK (
        status IN ('REQUESTED', 'GENERATING', 'STORED', 'FAILED')
    ),
    CONSTRAINT ck_generated_document_versions CHECK (
        source_progression_decision_version > 0 AND template_version > 0
        AND generation_attempt_count >= 0
    ),
    CONSTRAINT ck_generated_document_storage CHECK (
        (status IN ('REQUESTED', 'GENERATING', 'FAILED') AND generated_at IS NULL)
        OR (status = 'STORED' AND storage_bucket IS NOT NULL AND storage_key IS NOT NULL
            AND content_type = 'application/pdf' AND checksum_sha256 IS NOT NULL
            AND length(checksum_sha256) = 64 AND size_bytes > 0 AND page_count > 0
            AND generated_at IS NOT NULL)
    )
);

CREATE INDEX idx_generated_document_work_queue
    ON generated_documents (next_generation_attempt_at, requested_at, id)
    WHERE status IN ('REQUESTED', 'FAILED') AND deleted_at IS NULL;
CREATE INDEX idx_generated_document_student
    ON generated_documents (student_number, academic_period_code, generated_at DESC);

CREATE TABLE integration_inbox (
    event_id uuid PRIMARY KEY,
    event_type varchar(160) NOT NULL,
    source_service varchar(100) NOT NULL,
    payload jsonb NOT NULL,
    received_at timestamptz NOT NULL,
    processed_at timestamptz
);

CREATE INDEX idx_documents_reporting_inbox_processed_at ON integration_inbox (processed_at);

CREATE TABLE published_result_projections_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    source_event_id uuid, source_published_result_id uuid, source_result_batch_id uuid,
    source_module_result_id uuid, student_id uuid, student_number varchar(40),
    programme_enrolment_id uuid, programme_id uuid, programme_version_id uuid,
    academic_period_id uuid, academic_period_code varchar(50), module_id uuid,
    module_code varchar(50), module_name varchar(200), curriculum_module_type varchar(20),
    credit_value numeric(6,2), final_mark numeric(6,2), grade varchar(10), remark varchar(100),
    passing boolean, publication_version integer, supersedes_published_result_id uuid,
    result_amendment_id uuid, published_by_user_id uuid, published_at timestamptz,
    current_version boolean, created_at timestamptz, updated_at timestamptz,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);

CREATE TABLE progression_decision_projections_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    source_event_id uuid, source_progression_decision_id uuid, decision_number varchar(80),
    decision_version integer, supersedes_decision_id uuid, source_progression_rule_set_id uuid,
    progression_rule_code varchar(40), progression_rule_version integer,
    source_registration_roster_import_id uuid, student_id uuid, student_number varchar(40),
    programme_enrolment_id uuid, programme_id uuid, programme_version_id uuid,
    academic_period_id uuid, academic_period_code varchar(50), programme_period_number integer,
    decision_code varchar(30), decision_label varchar(150), next_programme_period_number integer,
    attempted_credits numeric(8,2), passed_credits numeric(8,2), failed_credits numeric(8,2),
    failed_modules integer, failed_compulsory_modules integer, weighted_average numeric(6,2),
    published_by_user_id uuid, published_at timestamptz, current_version boolean,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid,
    modified_by_user_id uuid, deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE progression_decision_result_projections_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    progression_decision_projection_id uuid, published_result_projection_id uuid,
    source_published_result_id uuid, created_at timestamptz, updated_at timestamptz,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);

CREATE TABLE generated_documents_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    document_number varchar(100), document_type varchar(40), student_id uuid,
    student_number varchar(40), programme_id uuid, programme_version_id uuid,
    academic_period_id uuid, academic_period_code varchar(50), source_progression_decision_id uuid,
    source_progression_decision_version integer, progression_decision_projection_id uuid,
    template_code varchar(80), template_version integer, status varchar(20),
    storage_bucket varchar(100), storage_key varchar(500), storage_object_version varchar(200),
    content_type varchar(100), checksum_sha256 varchar(64), size_bytes bigint, page_count integer,
    requested_at timestamptz, generation_started_at timestamptz, generated_at timestamptz,
    generation_attempt_count integer, next_generation_attempt_at timestamptz,
    last_failure_reason varchar(1000), created_at timestamptz, updated_at timestamptz,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);

CREATE OR REPLACE FUNCTION prevent_reporting_source_snapshot_change()
RETURNS trigger LANGUAGE plpgsql AS $$
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

CREATE TRIGGER trg_published_result_projection_immutable
    BEFORE UPDATE OR DELETE ON published_result_projections
    FOR EACH ROW EXECUTE FUNCTION prevent_reporting_source_snapshot_change();
CREATE TRIGGER trg_progression_decision_projection_immutable
    BEFORE UPDATE OR DELETE ON progression_decision_projections
    FOR EACH ROW EXECUTE FUNCTION prevent_reporting_source_snapshot_change();

CREATE OR REPLACE FUNCTION prevent_progression_result_projection_change()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Official progression result evidence is immutable';
END;
$$;

CREATE TRIGGER trg_progression_result_projection_immutable
    BEFORE UPDATE OR DELETE ON progression_decision_result_projections
    FOR EACH ROW EXECUTE FUNCTION prevent_progression_result_projection_change();

GRANT SELECT, INSERT ON TABLE revinfo TO emhare_service;
GRANT USAGE, SELECT ON SEQUENCE revinfo_seq, revinfo_rev_seq TO emhare_service;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    published_result_projections,
    progression_decision_projections,
    progression_decision_result_projections,
    generated_documents,
    integration_inbox,
    published_result_projections_aud,
    progression_decision_projections_aud,
    progression_decision_result_projections_aud,
    generated_documents_aud
TO emhare_service;
