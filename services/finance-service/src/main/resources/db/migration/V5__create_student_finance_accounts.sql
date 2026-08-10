-- Author: Tinashe K

CREATE SEQUENCE student_finance_account_number_sequence START WITH 1 INCREMENT BY 1;

CREATE TABLE student_finance_accounts (
    id uuid PRIMARY KEY,
    account_number varchar(50) NOT NULL,
    student_id uuid NOT NULL,
    student_number varchar(40) NOT NULL,
    user_id uuid NOT NULL,
    source_offer_id uuid NOT NULL,
    primary_email varchar(200) NOT NULL,
    base_currency_code varchar(3) NOT NULL,
    status varchar(30) NOT NULL,
    opened_at timestamptz NOT NULL,
    closed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_student_finance_accounts_number UNIQUE (account_number),
    CONSTRAINT uk_student_finance_accounts_student UNIQUE (student_id),
    CONSTRAINT uk_student_finance_accounts_offer UNIQUE (source_offer_id),
    CONSTRAINT ck_student_finance_accounts_currency CHECK (base_currency_code = 'USD'),
    CONSTRAINT ck_student_finance_accounts_status CHECK (status IN ('ACTIVE', 'ON_HOLD', 'CLOSED')),
    CONSTRAINT ck_student_finance_accounts_closure CHECK (
        (status = 'CLOSED' AND closed_at IS NOT NULL) OR (status <> 'CLOSED' AND closed_at IS NULL)
    )
);

CREATE TABLE student_finance_accounts_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    account_number varchar(50), student_id uuid, student_number varchar(40), user_id uuid,
    source_offer_id uuid, primary_email varchar(200), base_currency_code varchar(3), status varchar(30),
    opened_at timestamptz, closed_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);
