-- Author: Tinashe K

CREATE TABLE applicant_referee_invitations (
    id uuid PRIMARY KEY,
    application_id uuid NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    referee_id uuid NOT NULL REFERENCES applicant_referees (id) ON DELETE CASCADE,
    token_hash char(64) NOT NULL,
    token_hint varchar(12) NOT NULL,
    status varchar(20) NOT NULL,
    expires_at timestamptz NOT NULL,
    sent_at timestamptz NOT NULL,
    opened_at timestamptz,
    submitted_at timestamptz,
    send_count integer NOT NULL DEFAULT 1,
    relationship_to_applicant varchar(200),
    years_known integer,
    recommendation varchar(40),
    comments varchar(5000),
    declaration_accepted boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_referee_invitation_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_referee_invitation_status CHECK (status IN ('SENT', 'OPENED', 'SUBMITTED', 'REVOKED', 'EXPIRED')),
    CONSTRAINT ck_referee_invitation_send_count CHECK (send_count > 0),
    CONSTRAINT ck_referee_invitation_years_known CHECK (years_known IS NULL OR years_known BETWEEN 0 AND 100),
    CONSTRAINT ck_referee_invitation_recommendation CHECK (
        recommendation IS NULL OR recommendation IN (
            'STRONGLY_RECOMMEND', 'RECOMMEND', 'RECOMMEND_WITH_RESERVATIONS', 'DO_NOT_RECOMMEND')),
    CONSTRAINT ck_referee_invitation_response CHECK (
        (status = 'SUBMITTED'
            AND submitted_at IS NOT NULL
            AND relationship_to_applicant IS NOT NULL
            AND years_known IS NOT NULL
            AND recommendation IS NOT NULL
            AND comments IS NOT NULL
            AND declaration_accepted)
        OR status <> 'SUBMITTED')
);

CREATE UNIQUE INDEX uk_active_referee_invitation
    ON applicant_referee_invitations (application_id, referee_id)
    WHERE status IN ('SENT', 'OPENED') AND deleted_at IS NULL;

CREATE INDEX idx_referee_invitation_application
    ON applicant_referee_invitations (application_id, referee_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE applicant_referee_invitations_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    application_id uuid,
    referee_id uuid,
    token_hash char(64),
    token_hint varchar(12),
    status varchar(20),
    expires_at timestamptz,
    sent_at timestamptz,
    opened_at timestamptz,
    submitted_at timestamptz,
    send_count integer,
    relationship_to_applicant varchar(200),
    years_known integer,
    recommendation varchar(40),
    comments varchar(5000),
    declaration_accepted boolean,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON applicant_referee_invitations TO emhare_service;
GRANT SELECT, INSERT, UPDATE, DELETE ON applicant_referee_invitations_aud TO emhare_service;
