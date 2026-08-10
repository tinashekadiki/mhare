ALTER TABLE application_payment_references
    ADD COLUMN finance_payment_reference_id uuid,
    ADD COLUMN rating_status varchar(20),
    ADD COLUMN paid_at timestamptz;

UPDATE application_payment_references
SET rating_status = CASE
    WHEN base_amount_due IS NULL THEN 'UNRATED'
    ELSE 'RATED'
END
WHERE rating_status IS NULL;

ALTER TABLE application_payment_references
    ALTER COLUMN rating_status SET NOT NULL,
    ADD CONSTRAINT ck_admissions_payment_projection_rating_status
        CHECK (rating_status IN ('RATED', 'UNRATED'));

CREATE UNIQUE INDEX uk_application_payment_references_active_application
    ON application_payment_references (application_id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_application_payment_references_finance_id
    ON application_payment_references (finance_payment_reference_id)
    WHERE finance_payment_reference_id IS NOT NULL AND deleted_at IS NULL;

ALTER TABLE application_payment_references_aud
    ADD COLUMN finance_payment_reference_id uuid,
    ADD COLUMN rating_status varchar(20),
    ADD COLUMN paid_at timestamptz;
