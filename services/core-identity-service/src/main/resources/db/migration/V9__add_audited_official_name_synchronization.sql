ALTER TABLE users
    ADD COLUMN first_name varchar(100),
    ADD COLUMN middle_names varchar(150),
    ADD COLUMN last_name varchar(100);

UPDATE users
SET first_name = NULLIF(split_part(trim(display_name), ' ', 1), ''),
    last_name = NULLIF(trim(substr(trim(display_name), length(split_part(trim(display_name), ' ', 1)) + 1)), '')
WHERE display_name IS NOT NULL;

ALTER TABLE users_aud
    ADD COLUMN first_name varchar(100),
    ADD COLUMN middle_names varchar(150),
    ADD COLUMN last_name varchar(100);

CREATE TABLE official_name_synchronizations (
    id uuid NOT NULL,
    source_request_id uuid NOT NULL,
    source_application_id uuid NOT NULL,
    source_document_id uuid NOT NULL,
    user_id uuid NOT NULL,
    previous_first_name varchar(100),
    previous_middle_names varchar(150),
    previous_last_name varchar(100),
    approved_first_name varchar(100) NOT NULL,
    approved_middle_names varchar(150),
    approved_last_name varchar(100) NOT NULL,
    approval_reason varchar(1000) NOT NULL,
    synchronized_at timestamp with time zone NOT NULL,
    synchronized_by_user_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT official_name_synchronizations_pkey PRIMARY KEY (id),
    CONSTRAINT uk_official_name_synchronizations_source_request UNIQUE (source_request_id),
    CONSTRAINT fk_official_name_synchronizations_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_official_name_synchronizations_user
    ON official_name_synchronizations(user_id, synchronized_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE official_name_synchronizations_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    source_request_id uuid,
    source_application_id uuid,
    source_document_id uuid,
    user_id uuid,
    previous_first_name varchar(100),
    previous_middle_names varchar(150),
    previous_last_name varchar(100),
    approved_first_name varchar(100),
    approved_middle_names varchar(150),
    approved_last_name varchar(100),
    approval_reason varchar(1000),
    synchronized_at timestamp with time zone,
    synchronized_by_user_id uuid,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    CONSTRAINT official_name_synchronizations_aud_pkey PRIMARY KEY (id, rev),
    CONSTRAINT fk_official_name_synchronizations_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);
