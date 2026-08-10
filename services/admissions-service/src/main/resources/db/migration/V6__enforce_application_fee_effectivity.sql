-- Author: Tinashe K

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE application_fees
    ADD CONSTRAINT ck_application_fees_amount_non_negative
        CHECK (amount >= 0),
    ADD CONSTRAINT ck_application_fees_effective_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    ADD CONSTRAINT ck_application_fees_currency_code
        CHECK (currency_code ~ '^[A-Z]{3}$'),
    ADD CONSTRAINT ck_application_fees_applicant_category
        CHECK (applicant_category_code IN ('LOCAL', 'SADC', 'INTERNATIONAL', 'CLE'));

ALTER TABLE application_fees
    ADD CONSTRAINT ex_application_fees_non_overlapping_effectivity
    EXCLUDE USING gist (
        application_type_id WITH =,
        applicant_category_code WITH =,
        daterange(effective_from, COALESCE(effective_to, 'infinity'::date), '[]') WITH &&
    )
    WHERE (is_active AND deleted_at IS NULL);

