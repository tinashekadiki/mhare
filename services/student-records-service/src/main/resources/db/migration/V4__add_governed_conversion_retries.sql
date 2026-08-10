-- Author: Tinashe K

ALTER TABLE student_conversion_requests
    ADD COLUMN retry_count integer NOT NULL DEFAULT 0,
    ADD COLUMN last_retry_at timestamptz,
    ADD COLUMN last_retry_by_user_id uuid,
    ADD COLUMN last_retry_reason varchar(1000),
    ADD CONSTRAINT ck_student_conversion_requests_retry_count CHECK (retry_count >= 0),
    ADD CONSTRAINT ck_student_conversion_requests_failure_reason CHECK (
        (status = 'FAILED' AND failure_reason IS NOT NULL)
        OR (status <> 'FAILED' AND failure_reason IS NULL)
    ),
    ADD CONSTRAINT ck_student_conversion_requests_retry_evidence CHECK (
        (retry_count = 0 AND last_retry_at IS NULL
            AND last_retry_by_user_id IS NULL AND last_retry_reason IS NULL)
        OR (retry_count > 0 AND last_retry_at IS NOT NULL
            AND last_retry_by_user_id IS NOT NULL AND last_retry_reason IS NOT NULL)
    );

ALTER TABLE student_conversion_requests_aud
    ADD COLUMN retry_count integer,
    ADD COLUMN last_retry_at timestamptz,
    ADD COLUMN last_retry_by_user_id uuid,
    ADD COLUMN last_retry_reason varchar(1000);
