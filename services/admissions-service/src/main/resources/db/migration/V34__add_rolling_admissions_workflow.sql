-- Author: Tinashe K

-- Hard cutover: application_programme_choices.choice_status and applications.status move to the
-- rolling-workflow value set per ADR-0014. Any existing row already carrying a retired value is
-- backfilled to its nearest equivalent before the CHECK constraint is tightened, so this migration
-- is safe to run against a database that already has draft/dev data using the old values.

UPDATE application_programme_choices SET choice_status = 'ELIGIBLE' WHERE choice_status IN ('SHORTLISTED', 'WAITLISTED');
UPDATE application_programme_choices SET choice_status = 'ADMITTED' WHERE choice_status = 'SELECTED';

UPDATE applications SET status = 'ELIGIBLE' WHERE status = 'SHORTLISTED';
UPDATE applications SET status = 'ADMITTED' WHERE status = 'SELECTED';

ALTER TABLE application_programme_choices DROP CONSTRAINT IF EXISTS ck_application_programme_choice_status;
ALTER TABLE application_programme_choices ADD CONSTRAINT ck_application_programme_choice_status CHECK (
    choice_status IN ('PENDING', 'ELIGIBLE', 'CONDITIONALLY_ELIGIBLE', 'INELIGIBLE', 'REQUIRES_REVIEW',
        'UNDER_ACADEMIC_REVIEW', 'ADMITTED', 'REJECTED', 'OFFERED', 'CONVERTED')
);

ALTER TABLE applications DROP CONSTRAINT IF EXISTS ck_applications_status;
ALTER TABLE applications ADD CONSTRAINT ck_applications_status CHECK (
    status IN ('DRAFT', 'SUBMITTED', 'PAYMENT_PENDING', 'UNDER_REVIEW', 'INCOMPLETE', 'ELIGIBLE', 'NOT_ELIGIBLE',
        'UNDER_ACADEMIC_REVIEW', 'ADMITTED', 'REJECTED', 'OFFERED', 'ACCEPTED', 'DECLINED', 'WITHDRAWN', 'CONVERTED')
);

CREATE TABLE academic_reviews (
    id uuid PRIMARY KEY,
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
    claimed_by_user_id uuid,
    claimed_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_academic_review_application_choice UNIQUE (application_id, programme_choice_id),
    CONSTRAINT ck_academic_review_status CHECK
        (status IN ('OPEN', 'CLAIMED', 'RECOMMENDED', 'RETURNED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_academic_review_choice_rank CHECK (choice_rank > 0),
    CONSTRAINT ck_academic_review_claim CHECK (
        (status = 'OPEN' AND claimed_by_user_id IS NULL AND claimed_at IS NULL)
        OR (status IN ('CLAIMED', 'RECOMMENDED', 'RETURNED', 'COMPLETED', 'CANCELLED'))
    )
);

CREATE INDEX idx_academic_reviews_root_queue
    ON academic_reviews (recommendation_academic_unit_id, status)
    WHERE deleted_at IS NULL;

CREATE TABLE academic_recommendations (
    id uuid PRIMARY KEY,
    academic_review_id uuid NOT NULL REFERENCES academic_reviews (id),
    recommendation_sequence integer NOT NULL,
    recommendation varchar(30) NOT NULL,
    reason varchar(1000) NOT NULL,
    recommended_by_user_id uuid NOT NULL,
    recommended_at timestamptz NOT NULL,
    review_status varchar(30) NOT NULL,
    reviewed_by_user_id uuid,
    reviewed_at timestamptz,
    review_reason varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_academic_recommendation_sequence UNIQUE (academic_review_id, recommendation_sequence),
    CONSTRAINT ck_academic_recommendation CHECK (recommendation IN ('RECOMMEND_ADMIT', 'RECOMMEND_REJECT')),
    CONSTRAINT ck_academic_recommendation_review_status CHECK
        (review_status IN ('PENDING', 'APPROVED', 'RETURNED', 'OVERRIDDEN')),
    CONSTRAINT ck_academic_recommendation_values CHECK
        (recommendation_sequence > 0 AND length(trim(reason)) > 0),
    CONSTRAINT ck_academic_recommendation_review CHECK (
        (review_status = 'PENDING' AND reviewed_by_user_id IS NULL AND reviewed_at IS NULL)
        OR (review_status <> 'PENDING' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL
            AND length(trim(coalesce(review_reason, ''))) > 0)
    )
);

CREATE UNIQUE INDEX uk_academic_recommendation_pending
    ON academic_recommendations (academic_review_id)
    WHERE review_status = 'PENDING' AND deleted_at IS NULL;

CREATE TABLE programme_choice_decisions (
    id uuid PRIMARY KEY,
    application_id uuid NOT NULL REFERENCES applications (id),
    programme_choice_id uuid NOT NULL REFERENCES application_programme_choices (id),
    decision varchar(30) NOT NULL,
    reason varchar(1000) NOT NULL,
    source_recommendation_id uuid REFERENCES academic_recommendations (id),
    decided_by_user_id uuid NOT NULL,
    decided_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_programme_choice_decision_choice UNIQUE (programme_choice_id),
    CONSTRAINT ck_programme_choice_decision CHECK (decision IN ('ADMIT', 'REJECT')),
    CONSTRAINT ck_programme_choice_decision_reason CHECK (length(trim(reason)) > 0)
);

CREATE TABLE academic_reviews_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    application_id uuid, programme_choice_id uuid,
    owning_academic_unit_id uuid, owning_academic_unit_code varchar(50), owning_academic_unit_name varchar(180),
    recommendation_academic_unit_id uuid, recommendation_academic_unit_code varchar(50),
    recommendation_academic_unit_name varchar(180), hierarchy_path_json jsonb, choice_rank integer,
    status varchar(30), claimed_by_user_id uuid, claimed_at timestamptz, completed_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);

CREATE TABLE academic_recommendations_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    academic_review_id uuid, recommendation_sequence integer, recommendation varchar(30),
    reason varchar(1000), recommended_by_user_id uuid, recommended_at timestamptz, review_status varchar(30),
    reviewed_by_user_id uuid, reviewed_at timestamptz, review_reason varchar(1000),
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);

CREATE TABLE programme_choice_decisions_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    application_id uuid, programme_choice_id uuid, decision varchar(30), reason varchar(1000),
    source_recommendation_id uuid, decided_by_user_id uuid, decided_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);

COMMENT ON TABLE selection_rounds IS 'Historical — read-only from ADR-0014 onward. No new rows.';
COMMENT ON TABLE selection_decisions IS 'Historical — read-only from ADR-0014 onward. No new rows.';
COMMENT ON TABLE academic_review_assignments IS 'Historical — read-only from ADR-0014 onward. No new rows. Superseded by academic_reviews.';
COMMENT ON TABLE academic_unit_recommendations IS 'Historical — read-only from ADR-0014 onward. No new rows. Superseded by academic_recommendations.';
COMMENT ON TABLE offer_batches IS 'Historical — read-only from ADR-0014 onward. No new rows.';

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    academic_reviews, academic_recommendations, programme_choice_decisions,
    academic_reviews_aud, academic_recommendations_aud, programme_choice_decisions_aud
TO emhare_service;
