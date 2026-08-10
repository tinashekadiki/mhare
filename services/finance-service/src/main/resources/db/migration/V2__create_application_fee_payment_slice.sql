CREATE SEQUENCE IF NOT EXISTS revinfo_seq
    START WITH 1
    INCREMENT BY 50;

CREATE SEQUENCE application_payment_reference_sequence
    START WITH 1
    INCREMENT BY 1
    CACHE 20;

CREATE TABLE exchange_rates (
    id uuid PRIMARY KEY,
    source_currency_code varchar(3) NOT NULL,
    base_currency_code varchar(3) NOT NULL DEFAULT 'USD',
    rate_to_base numeric(20, 8) NOT NULL,
    effective_from timestamptz NOT NULL,
    effective_to timestamptz,
    source_name varchar(120) NOT NULL,
    source_reference varchar(160),
    status varchar(20) NOT NULL,
    approved_by_user_id uuid,
    approved_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_exchange_rates_currency_codes CHECK (
        source_currency_code = upper(source_currency_code)
        AND base_currency_code = 'USD'
        AND source_currency_code <> base_currency_code
    ),
    CONSTRAINT ck_exchange_rates_positive_rate CHECK (rate_to_base > 0),
    CONSTRAINT ck_exchange_rates_effective_range CHECK (
        effective_to IS NULL OR effective_to > effective_from
    ),
    CONSTRAINT ck_exchange_rates_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_exchange_rates_approval CHECK (
        (status = 'ACTIVE' AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL)
        OR status <> 'ACTIVE'
    )
);

CREATE INDEX idx_exchange_rates_effective_lookup
    ON exchange_rates (source_currency_code, base_currency_code, effective_from DESC)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

CREATE TABLE application_payment_references (
    id uuid PRIMARY KEY,
    source_application_id uuid NOT NULL,
    applicant_user_id uuid NOT NULL,
    applicant_keycloak_user_id uuid NOT NULL,
    reference varchar(80) NOT NULL,
    amount_due numeric(12, 2) NOT NULL,
    currency_code varchar(3) NOT NULL,
    base_currency_code varchar(3) NOT NULL DEFAULT 'USD',
    exchange_rate_id uuid REFERENCES exchange_rates (id),
    base_amount_due numeric(12, 2),
    rating_status varchar(20) NOT NULL,
    status varchar(30) NOT NULL,
    required_for_submission boolean NOT NULL,
    expires_at timestamptz,
    paid_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_application_payment_references_application UNIQUE (source_application_id),
    CONSTRAINT uk_application_payment_references_reference UNIQUE (reference),
    CONSTRAINT ck_application_payment_references_amount CHECK (amount_due > 0),
    CONSTRAINT ck_application_payment_references_currency CHECK (
        currency_code = upper(currency_code) AND base_currency_code = 'USD'
    ),
    CONSTRAINT ck_application_payment_references_rating_status CHECK (
        rating_status IN ('RATED', 'UNRATED')
    ),
    CONSTRAINT ck_application_payment_references_status CHECK (
        status IN ('PENDING', 'PAID', 'EXPIRED', 'CANCELLED')
    ),
    CONSTRAINT ck_application_payment_references_base_amount CHECK (
        (currency_code = 'USD'
            AND exchange_rate_id IS NULL
            AND base_amount_due = amount_due
            AND rating_status = 'RATED')
        OR
        (currency_code <> 'USD'
            AND exchange_rate_id IS NULL
            AND base_amount_due IS NULL
            AND rating_status = 'UNRATED')
        OR
        (currency_code <> 'USD'
            AND exchange_rate_id IS NOT NULL
            AND base_amount_due IS NOT NULL
            AND rating_status = 'RATED')
    ),
    CONSTRAINT ck_application_payment_references_paid_at CHECK (
        (status = 'PAID' AND paid_at IS NOT NULL) OR status <> 'PAID'
    )
);

CREATE INDEX idx_application_payment_references_applicant
    ON application_payment_references (applicant_keycloak_user_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE application_payments (
    id uuid PRIMARY KEY,
    payment_reference_id uuid NOT NULL REFERENCES application_payment_references (id),
    source_application_id uuid NOT NULL,
    provider_code varchar(50) NOT NULL,
    provider_transaction_reference varchar(160) NOT NULL,
    amount numeric(12, 2) NOT NULL,
    currency_code varchar(3) NOT NULL,
    base_currency_code varchar(3) NOT NULL DEFAULT 'USD',
    exchange_rate_id uuid REFERENCES exchange_rates (id),
    base_amount numeric(12, 2),
    rating_status varchar(20) NOT NULL,
    paid_at timestamptz NOT NULL,
    confirmed_at timestamptz,
    status varchar(30) NOT NULL,
    provider_event_fingerprint varchar(128) NOT NULL,
    failure_reason varchar(500),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_application_payments_provider_transaction UNIQUE (
        provider_code,
        provider_transaction_reference
    ),
    CONSTRAINT ck_application_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_application_payments_currency CHECK (
        currency_code = upper(currency_code) AND base_currency_code = 'USD'
    ),
    CONSTRAINT ck_application_payments_rating_status CHECK (
        rating_status IN ('RATED', 'UNRATED')
    ),
    CONSTRAINT ck_application_payments_status CHECK (
        status IN ('PENDING', 'CONFIRMED', 'FAILED', 'REVERSED')
    ),
    CONSTRAINT ck_application_payments_base_amount CHECK (
        (currency_code = 'USD'
            AND exchange_rate_id IS NULL
            AND base_amount = amount
            AND rating_status = 'RATED')
        OR
        (currency_code <> 'USD'
            AND exchange_rate_id IS NULL
            AND base_amount IS NULL
            AND rating_status = 'UNRATED')
        OR
        (currency_code <> 'USD'
            AND exchange_rate_id IS NOT NULL
            AND base_amount IS NOT NULL
            AND rating_status = 'RATED')
    )
);

CREATE INDEX idx_application_payments_reconciliation
    ON application_payments (status, rating_status, paid_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE finance_receipts (
    id uuid PRIMARY KEY,
    application_payment_id uuid NOT NULL REFERENCES application_payments (id),
    receipt_number varchar(80) NOT NULL,
    document_id uuid,
    status varchar(30) NOT NULL,
    issued_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_finance_receipts_payment UNIQUE (application_payment_id),
    CONSTRAINT uk_finance_receipts_number UNIQUE (receipt_number),
    CONSTRAINT ck_finance_receipts_status CHECK (
        status IN ('PENDING_GENERATION', 'ISSUED', 'VOIDED')
    ),
    CONSTRAINT ck_finance_receipts_issued CHECK (
        (status = 'ISSUED' AND document_id IS NOT NULL AND issued_at IS NOT NULL)
        OR status <> 'ISSUED'
    )
);

CREATE TABLE exchange_rates_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    source_currency_code varchar(3),
    base_currency_code varchar(3),
    rate_to_base numeric(20, 8),
    effective_from timestamptz,
    effective_to timestamptz,
    source_name varchar(120),
    source_reference varchar(160),
    status varchar(20),
    approved_by_user_id uuid,
    approved_at timestamptz,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE application_payment_references_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    source_application_id uuid,
    applicant_user_id uuid,
    applicant_keycloak_user_id uuid,
    reference varchar(80),
    amount_due numeric(12, 2),
    currency_code varchar(3),
    base_currency_code varchar(3),
    exchange_rate_id uuid,
    base_amount_due numeric(12, 2),
    rating_status varchar(20),
    status varchar(30),
    required_for_submission boolean,
    expires_at timestamptz,
    paid_at timestamptz,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE application_payments_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    payment_reference_id uuid,
    source_application_id uuid,
    provider_code varchar(50),
    provider_transaction_reference varchar(160),
    amount numeric(12, 2),
    currency_code varchar(3),
    base_currency_code varchar(3),
    exchange_rate_id uuid,
    base_amount numeric(12, 2),
    rating_status varchar(20),
    paid_at timestamptz,
    confirmed_at timestamptz,
    status varchar(30),
    provider_event_fingerprint varchar(128),
    failure_reason varchar(500),
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE finance_receipts_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    application_payment_id uuid,
    receipt_number varchar(80),
    document_id uuid,
    status varchar(30),
    issued_at timestamptz,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);
