CREATE TABLE application_payment_provider_attempts (
    id uuid PRIMARY KEY,
    payment_reference_id uuid NOT NULL REFERENCES application_payment_references (id),
    source_application_id uuid NOT NULL,
    provider_code varchar(50) NOT NULL,
    merchant_trace varchar(64) NOT NULL,
    merchant_reference varchar(80) NOT NULL,
    return_nonce_hash varchar(64) NOT NULL,
    transaction_currency_code varchar(3) NOT NULL,
    transaction_amount numeric(12, 2) NOT NULL,
    gateway_url varchar(500) NOT NULL,
    status varchar(30) NOT NULL,
    provider_transaction_reference varchar(160),
    provider_status_code varchar(30),
    provider_result_description varchar(500),
    expires_at timestamptz NOT NULL,
    completed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_application_payment_provider_attempts_trace UNIQUE (provider_code, merchant_trace),
    CONSTRAINT ck_application_payment_provider_attempts_amount CHECK (transaction_amount > 0),
    CONSTRAINT ck_application_payment_provider_attempts_currency CHECK (
        transaction_currency_code = upper(transaction_currency_code)
    ),
    CONSTRAINT ck_application_payment_provider_attempts_status CHECK (
        status IN ('INITIATED', 'PENDING_CONFIRMATION', 'CONFIRMED', 'FAILED', 'CANCELLED', 'EXPIRED')
    )
);

CREATE INDEX idx_application_payment_provider_attempts_reference
    ON application_payment_provider_attempts (payment_reference_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE application_payment_provider_attempts_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    payment_reference_id uuid,
    source_application_id uuid,
    provider_code varchar(50),
    merchant_trace varchar(64),
    merchant_reference varchar(80),
    return_nonce_hash varchar(64),
    transaction_currency_code varchar(3),
    transaction_amount numeric(12, 2),
    gateway_url varchar(500),
    status varchar(30),
    provider_transaction_reference varchar(160),
    provider_status_code varchar(30),
    provider_result_description varchar(500),
    expires_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);
