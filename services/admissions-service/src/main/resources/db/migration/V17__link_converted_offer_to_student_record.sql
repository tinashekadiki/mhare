-- Author: Tinashe K

ALTER TABLE offers
    ADD COLUMN conversion_request_id uuid,
    ADD COLUMN converted_student_id uuid,
    ADD COLUMN converted_student_number varchar(40),
    ADD CONSTRAINT uk_offers_conversion_request UNIQUE (conversion_request_id),
    ADD CONSTRAINT ck_offers_conversion_result CHECK (
        (status = 'CONVERTED' AND conversion_request_id IS NOT NULL
            AND converted_student_id IS NOT NULL
            AND length(trim(coalesce(converted_student_number, ''))) > 0
            AND converted_at IS NOT NULL)
        OR (status <> 'CONVERTED' AND conversion_request_id IS NULL
            AND converted_student_id IS NULL
            AND converted_student_number IS NULL
            AND converted_at IS NULL)
    );

ALTER TABLE offers_aud
    ADD COLUMN conversion_request_id uuid,
    ADD COLUMN converted_student_id uuid,
    ADD COLUMN converted_student_number varchar(40);
