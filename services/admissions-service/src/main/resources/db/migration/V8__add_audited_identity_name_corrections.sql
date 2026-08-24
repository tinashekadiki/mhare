ALTER TABLE applications
    ADD COLUMN official_first_name varchar(100),
    ADD COLUMN official_middle_names varchar(150),
    ADD COLUMN official_last_name varchar(100);

UPDATE applications application_record
SET official_first_name = applicant.first_name,
    official_middle_names = applicant.middle_names,
    official_last_name = applicant.last_name
FROM applicants applicant
WHERE applicant.id = application_record.applicant_id;

ALTER TABLE applications
    ALTER COLUMN official_first_name SET NOT NULL,
    ALTER COLUMN official_last_name SET NOT NULL;

ALTER TABLE applications_aud
    ADD COLUMN official_first_name varchar(100),
    ADD COLUMN official_middle_names varchar(150),
    ADD COLUMN official_last_name varchar(100);

CREATE TABLE applicant_identity_name_corrections (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    applicant_id uuid NOT NULL,
    document_id uuid NOT NULL,
    registered_first_name varchar(100) NOT NULL,
    registered_middle_names varchar(150),
    registered_last_name varchar(100) NOT NULL,
    document_first_name varchar(100) NOT NULL,
    document_middle_names varchar(150),
    document_last_name varchar(100) NOT NULL,
    request_reason varchar(1000),
    status varchar(30) NOT NULL,
    requested_at timestamp with time zone,
    requested_by_user_id uuid,
    decided_at timestamp with time zone,
    decided_by_user_id uuid,
    decision_reason varchar(1000),
    core_synchronized_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT applicant_identity_name_corrections_pkey PRIMARY KEY (id),
    CONSTRAINT ck_applicant_identity_name_corrections_status CHECK (
        status IN ('OCR_REVIEWED', 'REQUESTED', 'APPROVED', 'REJECTED', 'SUPERSEDED')
    ),
    CONSTRAINT fk_identity_name_corrections_application FOREIGN KEY (application_id) REFERENCES applications(id),
    CONSTRAINT fk_identity_name_corrections_applicant FOREIGN KEY (applicant_id) REFERENCES applicants(id),
    CONSTRAINT uk_identity_name_corrections_application_document UNIQUE (application_id, document_id)
);

CREATE INDEX idx_identity_name_corrections_status
    ON applicant_identity_name_corrections(status, requested_at)
    WHERE deleted_at IS NULL;

CREATE TABLE applicant_identity_name_corrections_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_id uuid,
    applicant_id uuid,
    document_id uuid,
    registered_first_name varchar(100),
    registered_middle_names varchar(150),
    registered_last_name varchar(100),
    document_first_name varchar(100),
    document_middle_names varchar(150),
    document_last_name varchar(100),
    request_reason varchar(1000),
    status varchar(30),
    requested_at timestamp with time zone,
    requested_by_user_id uuid,
    decided_at timestamp with time zone,
    decided_by_user_id uuid,
    decision_reason varchar(1000),
    core_synchronized_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    CONSTRAINT applicant_identity_name_corrections_aud_pkey PRIMARY KEY (id, rev),
    CONSTRAINT fk_identity_name_corrections_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);
