-- Author: Tinashe K

INSERT INTO application_types (
    id, code, name, requires_employment_history, requires_referees, is_active,
    created_at, updated_at, version
)
SELECT gen_random_uuid(), route.code, route.name, route.requires_employment, route.requires_referees,
       false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM (VALUES
    ('UNDERGRAD', 'Undergraduate and Diploma', false, false),
    ('POSTGRAD', 'Postgraduate', true, true),
    ('MBA', 'Master of Business Administration', true, true),
    ('EDUCATION', 'Education', true, true)
) AS route(code, name, requires_employment, requires_referees)
WHERE NOT EXISTS (SELECT 1 FROM application_types existing WHERE existing.code = route.code);

CREATE TABLE application_type_programme_mappings (
    id uuid PRIMARY KEY,
    application_type_id uuid NOT NULL REFERENCES application_types (id),
    programme_id uuid NOT NULL,
    programme_code varchar(50) NOT NULL,
    programme_name varchar(200) NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_application_type_programme_mapping UNIQUE (application_type_id, programme_id)
);

CREATE INDEX ix_application_type_programme_mappings_route
    ON application_type_programme_mappings (application_type_id, programme_id)
    WHERE deleted_at IS NULL AND is_active;

CREATE TABLE application_programme_option_snapshots (
    id uuid PRIMARY KEY,
    application_id uuid NOT NULL REFERENCES applications (id),
    programme_id uuid NOT NULL,
    programme_version_id uuid NOT NULL,
    programme_code varchar(50) NOT NULL,
    programme_name varchar(200) NOT NULL,
    award_name varchar(200) NOT NULL,
    owning_academic_unit_id uuid NOT NULL,
    owning_academic_unit_name varchar(180) NOT NULL,
    programme_version_code varchar(40) NOT NULL,
    programme_type_id uuid,
    programme_type_code varchar(40),
    programme_type_name varchar(120),
    programme_level_id uuid,
    programme_level_code varchar(40),
    programme_level_name varchar(120),
    minimum_entry_option_selections integer NOT NULL DEFAULT 0,
    maximum_entry_option_selections integer NOT NULL DEFAULT 0,
    entry_options_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_application_programme_option_snapshot UNIQUE (application_id, programme_id),
    CONSTRAINT ck_application_programme_option_snapshot_limits CHECK (
        minimum_entry_option_selections >= 0
        AND maximum_entry_option_selections >= minimum_entry_option_selections
    )
);

CREATE TABLE application_document_requirement_snapshots (
    id uuid PRIMARY KEY,
    application_id uuid NOT NULL REFERENCES applications (id),
    requirement_code varchar(60) NOT NULL,
    requirement_name varchar(160) NOT NULL,
    is_required boolean NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_application_document_requirement_snapshot UNIQUE (application_id, requirement_code)
);

CREATE TABLE application_prior_uz_declarations (
    id uuid PRIMARY KEY,
    application_id uuid NOT NULL UNIQUE REFERENCES applications (id),
    previously_studied_at_uz boolean NOT NULL,
    registration_number varchar(80),
    enrolment_started_on date,
    enrolment_ended_on date,
    previously_accepted_offer boolean,
    previously_took_up_place boolean,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_prior_uz_affirmative_details CHECK (
        (NOT previously_studied_at_uz AND registration_number IS NULL
            AND enrolment_started_on IS NULL AND enrolment_ended_on IS NULL
            AND previously_accepted_offer IS NULL AND previously_took_up_place IS NULL)
        OR (previously_studied_at_uz AND registration_number IS NOT NULL
            AND enrolment_started_on IS NOT NULL
            AND previously_accepted_offer IS NOT NULL AND previously_took_up_place IS NOT NULL)
    ),
    CONSTRAINT ck_prior_uz_enrolment_dates CHECK (
        enrolment_ended_on IS NULL OR enrolment_ended_on >= enrolment_started_on
    )
);

CREATE TABLE application_professional_achievements (
    id uuid PRIMARY KEY,
    application_id uuid NOT NULL REFERENCES applications (id),
    achievement_type varchar(30) NOT NULL,
    title varchar(250) NOT NULL,
    organisation varchar(200),
    achieved_on date,
    description varchar(2000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_professional_achievement_type CHECK (
        achievement_type IN ('AWARD', 'PROFESSIONAL_MEMBERSHIP', 'PUBLICATION', 'PRESENTATION', 'OTHER')
    )
);

ALTER TABLE applications
    ADD COLUMN professional_achievements_declared_none boolean NOT NULL DEFAULT false;

ALTER TABLE applications_aud
    ADD COLUMN professional_achievements_declared_none boolean;

CREATE TABLE application_referee_nominations (
    id uuid PRIMARY KEY,
    application_id uuid NOT NULL REFERENCES applications (id),
    referee_id uuid NOT NULL REFERENCES applicant_referees (id),
    organisation varchar(200) NOT NULL,
    position_title varchar(150) NOT NULL,
    expertise varchar(500) NOT NULL,
    relationship_to_applicant varchar(200) NOT NULL,
    normalized_email varchar(200) NOT NULL,
    normalized_phone_number varchar(50),
    is_current boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_application_referee_nomination_referee UNIQUE (application_id, referee_id)
);

CREATE UNIQUE INDEX uk_application_referee_nomination_email
    ON application_referee_nominations (application_id, normalized_email)
    WHERE deleted_at IS NULL AND is_current;

CREATE UNIQUE INDEX uk_application_referee_nomination_phone
    ON application_referee_nominations (application_id, normalized_phone_number)
    WHERE deleted_at IS NULL AND is_current AND normalized_phone_number IS NOT NULL;

INSERT INTO application_referee_nominations (
    id, application_id, referee_id, organisation, position_title, expertise,
    relationship_to_applicant, normalized_email, normalized_phone_number, is_current,
    created_at, updated_at, created_by_user_id, modified_by_user_id, version
)
SELECT gen_random_uuid(), invitation.application_id, invitation.referee_id,
       referee.organisation, COALESCE(referee.position_title, 'Not supplied'), 'Not supplied',
       COALESCE(invitation.relationship_to_applicant, 'Not supplied'), lower(trim(referee.email)),
       NULLIF(regexp_replace(COALESCE(referee.phone_number, ''), '[^0-9+]', '', 'g'), ''),
       invitation.status <> 'REVOKED', invitation.created_at, CURRENT_TIMESTAMP,
       invitation.created_by_user_id, invitation.modified_by_user_id, 0
FROM applicant_referee_invitations invitation
JOIN applicant_referees referee ON referee.id = invitation.referee_id
WHERE invitation.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM application_referee_nominations existing
      WHERE existing.application_id = invitation.application_id
        AND existing.referee_id = invitation.referee_id
  );

ALTER TABLE applicant_referee_invitations
    ADD COLUMN nomination_id uuid REFERENCES application_referee_nominations (id);

UPDATE applicant_referee_invitations invitation
SET nomination_id = nomination.id
FROM application_referee_nominations nomination
WHERE nomination.application_id = invitation.application_id
  AND nomination.referee_id = invitation.referee_id
  AND invitation.nomination_id IS NULL;

ALTER TABLE applicant_referee_invitations_aud ADD COLUMN nomination_id uuid;

CREATE TABLE admission_qualification_requirement_groups (
    id uuid PRIMARY KEY,
    requirement_set_id uuid NOT NULL REFERENCES admission_requirement_sets (id),
    group_code varchar(50) NOT NULL,
    name varchar(160) NOT NULL,
    minimum_satisfied_items integer NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_qualification_requirement_group UNIQUE (requirement_set_id, group_code),
    CONSTRAINT ck_qualification_requirement_group_minimum CHECK (minimum_satisfied_items > 0)
);

CREATE TABLE admission_qualification_requirement_items (
    id uuid PRIMARY KEY,
    requirement_group_id uuid NOT NULL REFERENCES admission_qualification_requirement_groups (id),
    qualification_level varchar(30) NOT NULL,
    minimum_count integer NOT NULL,
    minimum_total_points numeric(8, 2),
    minimum_duration_months integer,
    sort_order integer NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_qualification_requirement_item_level CHECK (
        qualification_level IN ('O_LEVEL', 'A_LEVEL', 'DIPLOMA', 'DEGREE', 'PROFESSIONAL', 'OTHER')
    ),
    CONSTRAINT ck_qualification_requirement_item_minimum_count CHECK (minimum_count > 0),
    CONSTRAINT ck_qualification_requirement_item_duration CHECK (
        minimum_duration_months IS NULL OR minimum_duration_months >= 0
    )
);

CREATE TABLE application_programme_entry_option_selections (
    id uuid PRIMARY KEY,
    programme_choice_id uuid NOT NULL REFERENCES application_programme_choices (id),
    entry_option_id uuid NOT NULL,
    entry_option_code varchar(50) NOT NULL,
    entry_option_name varchar(200) NOT NULL,
    preference_rank integer NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_programme_entry_option_selection UNIQUE (programme_choice_id, entry_option_id),
    CONSTRAINT uk_programme_entry_option_selection_rank UNIQUE (programme_choice_id, preference_rank),
    CONSTRAINT ck_programme_entry_option_selection_rank CHECK (preference_rank > 0)
);

CREATE TABLE application_type_programme_mappings_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    application_type_id uuid, programme_id uuid, programme_code varchar(50), programme_name varchar(200), is_active boolean,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);
CREATE TABLE application_programme_option_snapshots_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    application_id uuid, programme_id uuid, programme_version_id uuid, programme_code varchar(50), programme_name varchar(200),
    award_name varchar(200), owning_academic_unit_id uuid, owning_academic_unit_name varchar(180), programme_version_code varchar(40),
    programme_type_id uuid, programme_type_code varchar(40), programme_type_name varchar(120),
    programme_level_id uuid, programme_level_code varchar(40), programme_level_name varchar(120),
    minimum_entry_option_selections integer, maximum_entry_option_selections integer, entry_options_json jsonb,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);
CREATE TABLE application_document_requirement_snapshots_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    application_id uuid, requirement_code varchar(60), requirement_name varchar(160), is_required boolean, sort_order integer,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);
CREATE TABLE application_prior_uz_declarations_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    application_id uuid, previously_studied_at_uz boolean, registration_number varchar(80),
    enrolment_started_on date, enrolment_ended_on date, previously_accepted_offer boolean, previously_took_up_place boolean,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);
CREATE TABLE application_professional_achievements_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    application_id uuid, achievement_type varchar(30), title varchar(250), organisation varchar(200), achieved_on date, description varchar(2000),
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);
CREATE TABLE application_referee_nominations_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    application_id uuid, referee_id uuid, organisation varchar(200), position_title varchar(150), expertise varchar(500),
    relationship_to_applicant varchar(200), normalized_email varchar(200), normalized_phone_number varchar(50), is_current boolean,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);
CREATE TABLE admission_qualification_requirement_groups_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    requirement_set_id uuid, group_code varchar(50), name varchar(160), minimum_satisfied_items integer, sort_order integer,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);
CREATE TABLE admission_qualification_requirement_items_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    requirement_group_id uuid, qualification_level varchar(30), minimum_count integer, minimum_total_points numeric(8, 2),
    minimum_duration_months integer, sort_order integer,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);
CREATE TABLE application_programme_entry_option_selections_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    programme_choice_id uuid, entry_option_id uuid, entry_option_code varchar(50), entry_option_name varchar(200), preference_rank integer,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY (id, rev)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON
    application_type_programme_mappings,
    application_programme_option_snapshots,
    application_document_requirement_snapshots,
    application_prior_uz_declarations,
    application_professional_achievements,
    application_referee_nominations,
    admission_qualification_requirement_groups,
    admission_qualification_requirement_items,
    application_programme_entry_option_selections
TO emhare_service;

GRANT SELECT, INSERT, UPDATE, DELETE ON
    application_type_programme_mappings_aud,
    application_programme_option_snapshots_aud,
    application_document_requirement_snapshots_aud,
    application_prior_uz_declarations_aud,
    application_professional_achievements_aud,
    application_referee_nominations_aud,
    admission_qualification_requirement_groups_aud,
    admission_qualification_requirement_items_aud,
    application_programme_entry_option_selections_aud
TO emhare_service;
