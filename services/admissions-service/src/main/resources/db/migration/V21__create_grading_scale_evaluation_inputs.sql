-- Author: Tinashe K

CREATE TABLE grading_scales (
    id uuid PRIMARY KEY,
    code varchar(50) NOT NULL,
    name varchar(150) NOT NULL,
    level varchar(30) NOT NULL,
    effective_from date NOT NULL,
    effective_to date,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_grading_scales_code UNIQUE (code),
    CONSTRAINT ck_grading_scales_effective_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE TABLE grading_scales_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    code varchar(50),
    name varchar(150),
    level varchar(30),
    effective_from date,
    effective_to date,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

-- Resolving the applicable grading scale for a result relies on there being at most one
-- active scale per level covering any given date (see QualificationPointsCalculator).
ALTER TABLE grading_scales
    ADD CONSTRAINT ex_grading_scales_non_overlapping_effectivity
    EXCLUDE USING gist (
        level WITH =,
        daterange(effective_from, COALESCE(effective_to, 'infinity'::date), '[]') WITH &&
    )
    WHERE (deleted_at IS NULL);

CREATE TABLE grading_scale_values (
    id uuid PRIMARY KEY,
    grading_scale_id uuid NOT NULL REFERENCES grading_scales (id),
    grade varchar(20) NOT NULL,
    points numeric(8, 2),
    is_pass boolean NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_grading_scale_values_scale_grade UNIQUE (grading_scale_id, grade),
    CONSTRAINT ck_grading_scale_values_points_non_negative CHECK (points IS NULL OR points >= 0)
);

CREATE TABLE grading_scale_values_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    grading_scale_id uuid,
    grade varchar(20),
    points numeric(8, 2),
    is_pass boolean,
    sort_order integer,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);
