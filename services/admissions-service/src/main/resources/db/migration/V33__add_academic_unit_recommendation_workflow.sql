-- Author: Tinashe K

CREATE TABLE application_clearances (
    id uuid PRIMARY KEY,
    application_id uuid NOT NULL REFERENCES applications (id),
    outcome varchar(30) NOT NULL,
    payment_cleared boolean NOT NULL,
    sections_complete boolean NOT NULL,
    required_documents_verified boolean NOT NULL,
    qualifications_verified boolean NOT NULL,
    confirmed_by_user_id uuid NOT NULL,
    confirmed_at timestamptz NOT NULL,
    reason varchar(1000) NOT NULL,
    invalidated_by_user_id uuid,
    invalidated_at timestamptz,
    invalidation_reason varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_application_clearance_outcome CHECK (outcome IN ('CONFIRMED', 'INVALIDATED')),
    CONSTRAINT ck_application_clearance_evidence CHECK (
        outcome <> 'CONFIRMED' OR
        (payment_cleared AND sections_complete AND required_documents_verified AND qualifications_verified)
    ),
    CONSTRAINT ck_application_clearance_reason CHECK (length(trim(reason)) > 0),
    CONSTRAINT ck_application_clearance_invalidation CHECK (
        (outcome = 'CONFIRMED' AND invalidated_at IS NULL AND invalidated_by_user_id IS NULL)
        OR (outcome = 'INVALIDATED' AND invalidated_at IS NOT NULL AND invalidated_by_user_id IS NOT NULL
            AND length(trim(coalesce(invalidation_reason, ''))) > 0)
    )
);

CREATE UNIQUE INDEX uk_application_clearance_active
    ON application_clearances (application_id)
    WHERE outcome = 'CONFIRMED' AND deleted_at IS NULL;

CREATE TABLE academic_review_assignments (
    id uuid PRIMARY KEY,
    selection_round_id uuid NOT NULL REFERENCES selection_rounds (id),
    application_id uuid NOT NULL REFERENCES applications (id),
    programme_choice_id uuid NOT NULL REFERENCES application_programme_choices (id),
    owning_academic_unit_id uuid NOT NULL,
    owning_academic_unit_code varchar(50) NOT NULL,
    owning_academic_unit_name varchar(180) NOT NULL,
    recommendation_academic_unit_id uuid NOT NULL,
    recommendation_academic_unit_code varchar(50) NOT NULL,
    recommendation_academic_unit_name varchar(180) NOT NULL,
    hierarchy_path_json jsonb NOT NULL,
    choice_rank integer NOT NULL,
    status varchar(30) NOT NULL,
    release_attempt integer NOT NULL,
    released_by_user_id uuid NOT NULL,
    released_at timestamptz NOT NULL,
    due_at timestamptz,
    claimed_by_user_id uuid,
    claimed_at timestamptz,
    completed_by_user_id uuid,
    completed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_academic_review_round_choice_attempt UNIQUE
        (selection_round_id, programme_choice_id, release_attempt),
    CONSTRAINT ck_academic_review_assignment_status CHECK
        (status IN ('OPEN', 'CLAIMED', 'RECOMMENDED', 'RETURNED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_academic_review_assignment_values CHECK (choice_rank > 0 AND release_attempt > 0),
    CONSTRAINT ck_academic_review_assignment_claim CHECK (
        (status = 'OPEN' AND claimed_by_user_id IS NULL AND claimed_at IS NULL)
        OR (status IN ('CLAIMED', 'RECOMMENDED', 'RETURNED', 'COMPLETED', 'CANCELLED'))
    )
);

CREATE UNIQUE INDEX uk_academic_review_assignment_active
    ON academic_review_assignments (selection_round_id, programme_choice_id)
    WHERE status IN ('OPEN', 'CLAIMED', 'RECOMMENDED', 'RETURNED') AND deleted_at IS NULL;
CREATE INDEX idx_academic_review_root_queue
    ON academic_review_assignments (recommendation_academic_unit_id, status, due_at, released_at)
    WHERE deleted_at IS NULL;

CREATE TABLE academic_unit_recommendations (
    id uuid PRIMARY KEY,
    academic_review_assignment_id uuid NOT NULL REFERENCES academic_review_assignments (id),
    recommendation_sequence integer NOT NULL,
    recommendation varchar(30) NOT NULL,
    rank_position integer,
    quota_type_code varchar(50),
    reason varchar(1000) NOT NULL,
    recommended_by_user_id uuid NOT NULL,
    recommended_at timestamptz NOT NULL,
    review_status varchar(30) NOT NULL,
    reviewed_by_user_id uuid,
    reviewed_at timestamptz,
    review_reason varchar(1000),
    final_decision varchar(30),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_academic_unit_recommendation_sequence UNIQUE
        (academic_review_assignment_id, recommendation_sequence),
    CONSTRAINT ck_academic_unit_recommendation CHECK
        (recommendation IN ('SHORTLIST', 'SELECT', 'REJECT', 'WAITLIST')),
    CONSTRAINT ck_academic_unit_recommendation_review_status CHECK
        (review_status IN ('PENDING', 'APPROVED', 'RETURNED', 'OVERRIDDEN')),
    CONSTRAINT ck_academic_unit_recommendation_final_decision CHECK
        (final_decision IS NULL OR final_decision IN ('SHORTLIST', 'SELECT', 'REJECT', 'WAITLIST')),
    CONSTRAINT ck_academic_unit_recommendation_values CHECK
        (recommendation_sequence > 0 AND (rank_position IS NULL OR rank_position > 0)
            AND length(trim(reason)) > 0),
    CONSTRAINT ck_academic_unit_recommendation_review CHECK (
        (review_status = 'PENDING' AND reviewed_by_user_id IS NULL AND reviewed_at IS NULL AND final_decision IS NULL)
        OR (review_status <> 'PENDING' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL
            AND length(trim(coalesce(review_reason, ''))) > 0)
    )
);

CREATE UNIQUE INDEX uk_academic_unit_recommendation_pending
    ON academic_unit_recommendations (academic_review_assignment_id)
    WHERE review_status = 'PENDING' AND deleted_at IS NULL;

CREATE TABLE application_clearances_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    application_id uuid, outcome varchar(30), payment_cleared boolean, sections_complete boolean,
    required_documents_verified boolean, qualifications_verified boolean,
    confirmed_by_user_id uuid, confirmed_at timestamptz, reason varchar(1000),
    invalidated_by_user_id uuid, invalidated_at timestamptz, invalidation_reason varchar(1000),
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);

CREATE TABLE academic_review_assignments_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    selection_round_id uuid, application_id uuid, programme_choice_id uuid,
    owning_academic_unit_id uuid, owning_academic_unit_code varchar(50), owning_academic_unit_name varchar(180),
    recommendation_academic_unit_id uuid, recommendation_academic_unit_code varchar(50),
    recommendation_academic_unit_name varchar(180), hierarchy_path_json jsonb, choice_rank integer,
    status varchar(30), release_attempt integer, released_by_user_id uuid, released_at timestamptz,
    due_at timestamptz, claimed_by_user_id uuid, claimed_at timestamptz,
    completed_by_user_id uuid, completed_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);

CREATE TABLE academic_unit_recommendations_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    academic_review_assignment_id uuid, recommendation_sequence integer, recommendation varchar(30),
    rank_position integer, quota_type_code varchar(50), reason varchar(1000),
    recommended_by_user_id uuid, recommended_at timestamptz, review_status varchar(30),
    reviewed_by_user_id uuid, reviewed_at timestamptz, review_reason varchar(1000), final_decision varchar(30),
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    application_clearances, academic_review_assignments, academic_unit_recommendations,
    application_clearances_aud, academic_review_assignments_aud, academic_unit_recommendations_aud
TO emhare_service;
