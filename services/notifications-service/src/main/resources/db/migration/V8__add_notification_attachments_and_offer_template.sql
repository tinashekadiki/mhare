-- Author: Tinashe K

CREATE TABLE notification_request_attachments (
    id uuid PRIMARY KEY,
    notification_request_id uuid NOT NULL REFERENCES notification_requests (id),
    attachment_sequence integer NOT NULL,
    source_document_id uuid NOT NULL,
    file_name varchar(240) NOT NULL,
    content_type varchar(160) NOT NULL,
    checksum_sha256 varchar(64) NOT NULL,
    download_url varchar(2000) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_notification_attachment_sequence UNIQUE (notification_request_id, attachment_sequence),
    CONSTRAINT ck_notification_attachment_sequence CHECK (attachment_sequence > 0)
);

CREATE TABLE notification_request_attachments_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    notification_request_id uuid, attachment_sequence integer, source_document_id uuid,
    file_name varchar(240), content_type varchar(160), checksum_sha256 varchar(64), download_url varchar(2000),
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

INSERT INTO notification_templates (
    id, code, template_version, name, event_type, channel, category, locale,
    subject_template, body_template, status,
    prepared_by_user_id, approved_by_user_id, approved_at, approval_reason,
    created_at, updated_at, created_by_user_id, modified_by_user_id, version
) VALUES (
    md5('ADMISSION_OFFER_PUBLISHED_EMAIL:1')::uuid,
    'ADMISSION_OFFER_PUBLISHED_EMAIL', 1, 'Published admission offer email',
    'ADMISSION_OFFER_PUBLISHED', 'EMAIL', 'TRANSACTIONAL', 'en-ZW',
    'Your University of Zimbabwe admission offer {{offerNumber}}',
    'Dear {{applicantName}}, your admission offer {{offerNumber}} for {{programmeName}} is now available in the applicant portal. The official offer letter is attached. Sign in to review and respond before {{acceptanceDeadline}}.',
    'ACTIVE',
    '00000000-0000-0000-0000-000000000001'::uuid,
    '00000000-0000-0000-0000-000000000002'::uuid,
    CURRENT_TIMESTAMP,
    'Approved transactional template for individually published admission offers.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
    '00000000-0000-0000-0000-000000000001'::uuid,
    '00000000-0000-0000-0000-000000000002'::uuid,
    0
) ON CONFLICT (code, template_version) DO NOTHING;

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    notification_request_attachments, notification_request_attachments_aud
TO emhare_service;
