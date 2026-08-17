ALTER TABLE applications
    ADD COLUMN application_fee_policy_status varchar(30),
    ADD COLUMN application_fee_structure_id uuid,
    ADD COLUMN application_fee_structure_code varchar(50),
    ADD COLUMN application_fee_structure_name varchar(160),
    ADD COLUMN application_fee_structure_version bigint,
    ADD COLUMN application_fee_programme_level_id uuid,
    ADD COLUMN application_fee_programme_level_code varchar(80),
    ADD COLUMN application_fee_applicant_category_code varchar(80),
    ADD COLUMN application_fee_amount numeric(19, 2),
    ADD COLUMN application_fee_currency_code varchar(3),
    ADD COLUMN application_fee_effective_at timestamp with time zone,
    ADD COLUMN application_fee_free_reason varchar(1000),
    ADD COLUMN application_fee_policy_decided_by_user_id uuid,
    ADD COLUMN application_fee_policy_decided_at timestamp with time zone;

UPDATE applications
SET application_fee_policy_status = 'LEGACY_UNSNAPSHOTTED';

ALTER TABLE applications
    ALTER COLUMN application_fee_policy_status SET NOT NULL,
    ADD CONSTRAINT ck_applications_fee_policy_status
        CHECK (application_fee_policy_status IN ('FEE_STRUCTURE', 'FEE_FREE', 'LEGACY_UNSNAPSHOTTED')),
    ADD CONSTRAINT ck_applications_fee_currency_code
        CHECK (application_fee_currency_code IS NULL OR application_fee_currency_code ~ '^[A-Z]{3}$'),
    ADD CONSTRAINT ck_applications_fee_amount
        CHECK (application_fee_amount IS NULL OR application_fee_amount > 0),
    ADD CONSTRAINT ck_applications_fee_structure_version
        CHECK (application_fee_structure_version IS NULL OR application_fee_structure_version >= 0),
    ADD CONSTRAINT ck_applications_fee_policy_snapshot
        CHECK (
            (
                application_fee_policy_status = 'LEGACY_UNSNAPSHOTTED'
                AND application_fee_structure_id IS NULL
                AND application_fee_structure_code IS NULL
                AND application_fee_structure_name IS NULL
                AND application_fee_structure_version IS NULL
                AND application_fee_programme_level_id IS NULL
                AND application_fee_programme_level_code IS NULL
                AND application_fee_applicant_category_code IS NULL
                AND application_fee_amount IS NULL
                AND application_fee_currency_code IS NULL
                AND application_fee_effective_at IS NULL
                AND application_fee_free_reason IS NULL
                AND application_fee_policy_decided_by_user_id IS NULL
                AND application_fee_policy_decided_at IS NULL
            ) OR (
                application_fee_policy_status = 'FEE_FREE'
                AND payment_required = false
                AND application_fee_structure_id IS NULL
                AND application_fee_structure_code IS NULL
                AND application_fee_structure_name IS NULL
                AND application_fee_structure_version IS NULL
                AND application_fee_programme_level_id IS NULL
                AND application_fee_programme_level_code IS NULL
                AND application_fee_applicant_category_code IS NULL
                AND application_fee_amount IS NULL
                AND application_fee_currency_code IS NULL
                AND application_fee_effective_at IS NULL
                AND application_fee_free_reason IS NOT NULL
                AND application_fee_policy_decided_by_user_id IS NOT NULL
                AND application_fee_policy_decided_at IS NOT NULL
            ) OR (
                application_fee_policy_status = 'FEE_STRUCTURE'
                AND payment_required = true
                AND application_fee_structure_id IS NOT NULL
                AND application_fee_structure_code IS NOT NULL
                AND application_fee_structure_name IS NOT NULL
                AND application_fee_structure_version IS NOT NULL
                AND application_fee_programme_level_id IS NOT NULL
                AND application_fee_programme_level_code IS NOT NULL
                AND application_fee_applicant_category_code IS NOT NULL
                AND application_fee_amount IS NOT NULL
                AND application_fee_currency_code IS NOT NULL
                AND application_fee_effective_at IS NOT NULL
                AND application_fee_free_reason IS NULL
                AND application_fee_policy_decided_by_user_id IS NULL
                AND application_fee_policy_decided_at IS NULL
            )
        );

ALTER TABLE applications_aud
    ADD COLUMN application_fee_policy_status varchar(30),
    ADD COLUMN application_fee_structure_id uuid,
    ADD COLUMN application_fee_structure_code varchar(50),
    ADD COLUMN application_fee_structure_name varchar(160),
    ADD COLUMN application_fee_structure_version bigint,
    ADD COLUMN application_fee_programme_level_id uuid,
    ADD COLUMN application_fee_programme_level_code varchar(80),
    ADD COLUMN application_fee_applicant_category_code varchar(80),
    ADD COLUMN application_fee_amount numeric(19, 2),
    ADD COLUMN application_fee_currency_code varchar(3),
    ADD COLUMN application_fee_effective_at timestamp with time zone,
    ADD COLUMN application_fee_free_reason varchar(1000),
    ADD COLUMN application_fee_policy_decided_by_user_id uuid,
    ADD COLUMN application_fee_policy_decided_at timestamp with time zone;

COMMENT ON COLUMN applications.application_fee_policy_status IS
    'Immutable fee-policy evidence captured at draft creation. LEGACY_UNSNAPSHOTTED is reserved for records predating V4.';
COMMENT ON COLUMN applications.application_fee_structure_id IS
    'Finance-owned fee structure resolved by programme level, applicant category, and effective time.';
