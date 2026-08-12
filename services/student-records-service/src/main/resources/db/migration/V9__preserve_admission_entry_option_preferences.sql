-- Author: Tinashe K

CREATE TABLE student_entry_option_preferences (
    id uuid PRIMARY KEY,
    programme_enrolment_id uuid NOT NULL REFERENCES student_programme_enrolments (id),
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
    CONSTRAINT uk_student_entry_option_preference UNIQUE (programme_enrolment_id, entry_option_id),
    CONSTRAINT ck_student_entry_option_preference_rank CHECK (preference_rank > 0)
);

CREATE TABLE student_entry_option_preferences_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    programme_enrolment_id uuid,
    entry_option_id uuid,
    entry_option_code varchar(50),
    entry_option_name varchar(200),
    preference_rank integer,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    student_entry_option_preferences,
    student_entry_option_preferences_aud
TO emhare_service;
