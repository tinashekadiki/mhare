-- Author: Tinashe K

CREATE SEQUENCE student_payment_number_sequence START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE student_payment_receipt_number_sequence START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE student_payment_allocation_number_sequence START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE finance_credit_note_number_sequence START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE finance_reversal_number_sequence START WITH 1 INCREMENT BY 1;

ALTER TABLE exchange_rates ADD COLUMN prepared_by_user_id uuid;
ALTER TABLE exchange_rates ADD COLUMN approval_reason varchar(1000);
ALTER TABLE exchange_rates ADD COLUMN retired_by_user_id uuid;
ALTER TABLE exchange_rates ADD COLUMN retired_at timestamptz;
ALTER TABLE exchange_rates ADD COLUMN retirement_reason varchar(1000);
UPDATE exchange_rates SET prepared_by_user_id=coalesce(created_by_user_id,'00000000-0000-0000-0000-000000000010'::uuid);
UPDATE exchange_rates SET prepared_by_user_id='00000000-0000-0000-0000-000000000010'::uuid
 WHERE approved_by_user_id IS NOT NULL AND prepared_by_user_id=approved_by_user_id;
UPDATE exchange_rates SET approval_reason='Legacy approved exchange rate migrated into maker-checker governance.'
 WHERE status='ACTIVE' AND approval_reason IS NULL;
ALTER TABLE exchange_rates ALTER COLUMN prepared_by_user_id SET NOT NULL;
ALTER TABLE exchange_rates ADD CONSTRAINT ck_exchange_rate_governed_workflow CHECK (
    (status='DRAFT' AND approved_by_user_id IS NULL AND approved_at IS NULL AND approval_reason IS NULL
        AND retired_by_user_id IS NULL AND retired_at IS NULL AND retirement_reason IS NULL)
    OR (status='ACTIVE' AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL
        AND length(trim(approval_reason))>0 AND retired_by_user_id IS NULL AND retired_at IS NULL AND retirement_reason IS NULL)
    OR (status='RETIRED' AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL
        AND length(trim(approval_reason))>0 AND retired_by_user_id IS NOT NULL AND retired_at IS NOT NULL
        AND length(trim(retirement_reason))>0));
ALTER TABLE exchange_rates ADD CONSTRAINT ck_exchange_rate_actor_separation CHECK (
    approved_by_user_id IS NULL OR approved_by_user_id<>prepared_by_user_id);
ALTER TABLE exchange_rates_aud ADD COLUMN prepared_by_user_id uuid;
ALTER TABLE exchange_rates_aud ADD COLUMN approval_reason varchar(1000);
ALTER TABLE exchange_rates_aud ADD COLUMN retired_by_user_id uuid;
ALTER TABLE exchange_rates_aud ADD COLUMN retired_at timestamptz;
ALTER TABLE exchange_rates_aud ADD COLUMN retirement_reason varchar(1000);

CREATE TABLE student_account_payments (
    id uuid PRIMARY KEY,payment_number varchar(40) NOT NULL,student_finance_account_id uuid REFERENCES student_finance_accounts(id),
    payer_name varchar(200) NOT NULL,provider_code varchar(50) NOT NULL,provider_transaction_reference varchar(160) NOT NULL,
    payment_channel varchar(40) NOT NULL,transaction_currency_code varchar(3) NOT NULL,transaction_amount numeric(16,2) NOT NULL,
    base_currency_code varchar(3) NOT NULL DEFAULT 'USD',exchange_rate_id uuid REFERENCES exchange_rates(id),base_amount numeric(16,2),
    rating_status varchar(20) NOT NULL,rating_applied_by_user_id uuid,rating_applied_at timestamptz,
    paid_at timestamptz NOT NULL,provider_event_fingerprint varchar(128) NOT NULL,
    reconciliation_status varchar(30) NOT NULL,captured_by_user_id uuid NOT NULL,captured_at timestamptz NOT NULL,
    reconciled_by_user_id uuid,reconciled_at timestamptz,reconciliation_reason varchar(1000),
    rejected_by_user_id uuid,rejected_at timestamptz,rejection_reason varchar(1000),
    created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,created_by_user_id uuid,modified_by_user_id uuid,
    deleted_at timestamptz,deleted_by_user_id uuid,version bigint NOT NULL,
    CONSTRAINT uk_student_account_payment_number UNIQUE(payment_number),
    CONSTRAINT uk_student_account_payment_provider UNIQUE(provider_code,provider_transaction_reference),
    CONSTRAINT uk_student_account_payment_fingerprint UNIQUE(provider_event_fingerprint),
    CONSTRAINT ck_student_account_payment_payer CHECK (length(trim(payer_name))>0),
    CONSTRAINT ck_student_account_payment_channel CHECK (payment_channel IN ('CASH','BANK_TRANSFER','CARD','MOBILE_MONEY','ONLINE','OTHER')),
    CONSTRAINT ck_student_account_payment_currency CHECK (transaction_currency_code=upper(transaction_currency_code) AND transaction_currency_code ~ '^[A-Z]{3}$' AND base_currency_code='USD'),
    CONSTRAINT ck_student_account_payment_amount CHECK (transaction_amount>0),
    CONSTRAINT ck_student_account_payment_rating_status CHECK (rating_status IN ('RATED','UNRATED')),
    CONSTRAINT ck_student_account_payment_rating CHECK (
        (transaction_currency_code='USD' AND exchange_rate_id IS NULL AND base_amount=transaction_amount AND rating_status='RATED' AND rating_applied_by_user_id IS NOT NULL AND rating_applied_at IS NOT NULL)
        OR (transaction_currency_code<>'USD' AND exchange_rate_id IS NULL AND base_amount IS NULL AND rating_status='UNRATED' AND rating_applied_by_user_id IS NULL AND rating_applied_at IS NULL)
        OR (transaction_currency_code<>'USD' AND exchange_rate_id IS NOT NULL AND base_amount IS NOT NULL AND rating_status='RATED' AND rating_applied_by_user_id IS NOT NULL AND rating_applied_at IS NOT NULL)),
    CONSTRAINT ck_student_account_payment_reconciliation_status CHECK (reconciliation_status IN ('PENDING','RECONCILED','REJECTED')),
    CONSTRAINT ck_student_account_payment_reconciliation CHECK (
        (reconciliation_status='PENDING' AND reconciled_by_user_id IS NULL AND reconciled_at IS NULL AND reconciliation_reason IS NULL
            AND rejected_by_user_id IS NULL AND rejected_at IS NULL AND rejection_reason IS NULL)
        OR (reconciliation_status='RECONCILED' AND rating_status='RATED' AND reconciled_by_user_id IS NOT NULL
            AND reconciled_at IS NOT NULL AND length(trim(reconciliation_reason))>0 AND rejected_by_user_id IS NULL
            AND rejected_at IS NULL AND rejection_reason IS NULL)
        OR (reconciliation_status='REJECTED' AND reconciled_by_user_id IS NULL AND reconciled_at IS NULL
            AND reconciliation_reason IS NULL AND rejected_by_user_id IS NOT NULL AND rejected_at IS NOT NULL
            AND length(trim(rejection_reason))>0)),
    CONSTRAINT ck_student_account_payment_actor_separation CHECK (
        (reconciled_by_user_id IS NULL OR reconciled_by_user_id<>captured_by_user_id)
        AND (rejected_by_user_id IS NULL OR rejected_by_user_id<>captured_by_user_id))
);
CREATE INDEX idx_student_account_payment_queue ON student_account_payments(reconciliation_status,rating_status,paid_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_student_account_payment_account ON student_account_payments(student_finance_account_id,paid_at) WHERE deleted_at IS NULL;

CREATE TABLE student_payment_suspense_resolutions (
    id uuid PRIMARY KEY,payment_id uuid NOT NULL REFERENCES student_account_payments(id),student_finance_account_id uuid NOT NULL REFERENCES student_finance_accounts(id),
    resolved_by_user_id uuid NOT NULL,resolved_at timestamptz NOT NULL,resolution_reason varchar(1000) NOT NULL,
    created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,created_by_user_id uuid,modified_by_user_id uuid,
    deleted_at timestamptz,deleted_by_user_id uuid,version bigint NOT NULL,
    CONSTRAINT uk_student_payment_suspense_resolution UNIQUE(payment_id),
    CONSTRAINT ck_student_payment_suspense_reason CHECK (length(trim(resolution_reason))>0)
);

CREATE TABLE student_payment_receipts (
    id uuid PRIMARY KEY,payment_id uuid NOT NULL REFERENCES student_account_payments(id),receipt_number varchar(40) NOT NULL,
    student_finance_account_id uuid REFERENCES student_finance_accounts(id),payer_name varchar(200) NOT NULL,
    transaction_currency_code varchar(3) NOT NULL,transaction_amount numeric(16,2) NOT NULL,base_currency_code varchar(3) NOT NULL,
    base_amount numeric(16,2) NOT NULL,issued_by_user_id uuid NOT NULL,issued_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,created_by_user_id uuid,modified_by_user_id uuid,
    deleted_at timestamptz,deleted_by_user_id uuid,version bigint NOT NULL,
    CONSTRAINT uk_student_payment_receipt_payment UNIQUE(payment_id),CONSTRAINT uk_student_payment_receipt_number UNIQUE(receipt_number),
    CONSTRAINT ck_student_payment_receipt_amount CHECK (transaction_amount>0 AND base_amount>0 AND base_currency_code='USD')
);

CREATE TABLE student_payment_allocations (
    id uuid PRIMARY KEY,allocation_number varchar(40) NOT NULL,payment_id uuid NOT NULL REFERENCES student_account_payments(id),
    invoice_id uuid NOT NULL REFERENCES finance_invoices(id),transaction_currency_code varchar(3) NOT NULL,
    transaction_amount numeric(16,2) NOT NULL,base_currency_code varchar(3) NOT NULL,payment_base_amount numeric(16,2) NOT NULL,
    invoice_base_amount numeric(16,2) NOT NULL,realised_exchange_difference numeric(16,2) NOT NULL,
    allocated_by_user_id uuid NOT NULL,allocated_at timestamptz NOT NULL,allocation_reason varchar(1000) NOT NULL,
    created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,created_by_user_id uuid,modified_by_user_id uuid,
    deleted_at timestamptz,deleted_by_user_id uuid,version bigint NOT NULL,
    CONSTRAINT uk_student_payment_allocation_number UNIQUE(allocation_number),
    CONSTRAINT ck_student_payment_allocation_amount CHECK (transaction_amount>0 AND payment_base_amount>0 AND invoice_base_amount>0 AND base_currency_code='USD'),
    CONSTRAINT ck_student_payment_allocation_exchange_difference CHECK (realised_exchange_difference=payment_base_amount-invoice_base_amount),
    CONSTRAINT ck_student_payment_allocation_reason CHECK (length(trim(allocation_reason))>0)
);
CREATE INDEX idx_student_payment_allocation_payment ON student_payment_allocations(payment_id);
CREATE INDEX idx_student_payment_allocation_invoice ON student_payment_allocations(invoice_id);

CREATE TABLE student_payment_allocation_reversals (
    id uuid PRIMARY KEY,reversal_number varchar(40) NOT NULL,allocation_id uuid NOT NULL REFERENCES student_payment_allocations(id),
    reversed_by_user_id uuid NOT NULL,reversed_at timestamptz NOT NULL,reversal_reason varchar(1000) NOT NULL,
    created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,created_by_user_id uuid,modified_by_user_id uuid,
    deleted_at timestamptz,deleted_by_user_id uuid,version bigint NOT NULL,
    CONSTRAINT uk_student_payment_allocation_reversal UNIQUE(allocation_id),
    CONSTRAINT uk_student_payment_allocation_reversal_number UNIQUE(reversal_number),
    CONSTRAINT ck_student_payment_allocation_reversal_reason CHECK (length(trim(reversal_reason))>0)
);

CREATE TABLE student_payment_reversals (
    id uuid PRIMARY KEY,reversal_number varchar(40) NOT NULL,payment_id uuid NOT NULL REFERENCES student_account_payments(id),
    reversed_by_user_id uuid NOT NULL,reversed_at timestamptz NOT NULL,reversal_reason varchar(1000) NOT NULL,
    created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,created_by_user_id uuid,modified_by_user_id uuid,
    deleted_at timestamptz,deleted_by_user_id uuid,version bigint NOT NULL,
    CONSTRAINT uk_student_payment_reversal UNIQUE(payment_id),CONSTRAINT uk_student_payment_reversal_number UNIQUE(reversal_number),
    CONSTRAINT ck_student_payment_reversal_reason CHECK (length(trim(reversal_reason))>0)
);

CREATE TABLE finance_credit_notes (
    id uuid PRIMARY KEY,credit_note_number varchar(40) NOT NULL,invoice_id uuid NOT NULL REFERENCES finance_invoices(id),
    transaction_currency_code varchar(3) NOT NULL,transaction_amount numeric(16,2) NOT NULL,base_currency_code varchar(3) NOT NULL,
    base_amount numeric(16,2) NOT NULL,credit_note_date date NOT NULL,status varchar(20) NOT NULL,
    prepared_by_user_id uuid NOT NULL,prepared_at timestamptz NOT NULL,preparation_reason varchar(1000) NOT NULL,
    posted_by_user_id uuid,posted_at timestamptz,posting_reason varchar(1000),
    created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,created_by_user_id uuid,modified_by_user_id uuid,
    deleted_at timestamptz,deleted_by_user_id uuid,version bigint NOT NULL,
    CONSTRAINT uk_finance_credit_note_number UNIQUE(credit_note_number),
    CONSTRAINT ck_finance_credit_note_amount CHECK (transaction_amount>0 AND base_amount>0 AND base_currency_code='USD'),
    CONSTRAINT ck_finance_credit_note_status CHECK (
        (status='DRAFT' AND posted_by_user_id IS NULL AND posted_at IS NULL AND posting_reason IS NULL)
        OR (status='POSTED' AND posted_by_user_id IS NOT NULL AND posted_at IS NOT NULL
            AND length(trim(posting_reason))>0 AND posted_by_user_id<>prepared_by_user_id)),
    CONSTRAINT ck_finance_credit_note_reason CHECK (length(trim(preparation_reason))>0)
);
CREATE INDEX idx_finance_credit_note_invoice ON finance_credit_notes(invoice_id,credit_note_date);

CREATE TABLE finance_credit_note_lines (
    id uuid PRIMARY KEY,credit_note_id uuid NOT NULL REFERENCES finance_credit_notes(id),line_number integer NOT NULL,
    invoice_line_id uuid NOT NULL REFERENCES finance_invoice_lines(id),transaction_amount numeric(16,2) NOT NULL,
    base_amount numeric(16,2) NOT NULL,reason varchar(500) NOT NULL,
    created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,created_by_user_id uuid,modified_by_user_id uuid,
    deleted_at timestamptz,deleted_by_user_id uuid,version bigint NOT NULL,
    CONSTRAINT uk_finance_credit_note_line_number UNIQUE(credit_note_id,line_number),
    CONSTRAINT ck_finance_credit_note_line_amount CHECK (line_number>0 AND transaction_amount>0 AND base_amount>0),
    CONSTRAINT ck_finance_credit_note_line_reason CHECK (length(trim(reason))>0)
);
CREATE INDEX idx_finance_credit_note_line_invoice_line ON finance_credit_note_lines(invoice_line_id);

CREATE TABLE student_account_payments_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,payment_number varchar(40),student_finance_account_id uuid,payer_name varchar(200),provider_code varchar(50),provider_transaction_reference varchar(160),payment_channel varchar(40),transaction_currency_code varchar(3),transaction_amount numeric(16,2),base_currency_code varchar(3),exchange_rate_id uuid,base_amount numeric(16,2),rating_status varchar(20),rating_applied_by_user_id uuid,rating_applied_at timestamptz,paid_at timestamptz,provider_event_fingerprint varchar(128),reconciliation_status varchar(30),captured_by_user_id uuid,captured_at timestamptz,reconciled_by_user_id uuid,reconciled_at timestamptz,reconciliation_reason varchar(1000),rejected_by_user_id uuid,rejected_at timestamptz,rejection_reason varchar(1000),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE student_payment_suspense_resolutions_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,payment_id uuid,student_finance_account_id uuid,resolved_by_user_id uuid,resolved_at timestamptz,resolution_reason varchar(1000),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE student_payment_receipts_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,payment_id uuid,receipt_number varchar(40),student_finance_account_id uuid,payer_name varchar(200),transaction_currency_code varchar(3),transaction_amount numeric(16,2),base_currency_code varchar(3),base_amount numeric(16,2),issued_by_user_id uuid,issued_at timestamptz,created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE student_payment_allocations_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,allocation_number varchar(40),payment_id uuid,invoice_id uuid,transaction_currency_code varchar(3),transaction_amount numeric(16,2),base_currency_code varchar(3),payment_base_amount numeric(16,2),invoice_base_amount numeric(16,2),realised_exchange_difference numeric(16,2),allocated_by_user_id uuid,allocated_at timestamptz,allocation_reason varchar(1000),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE student_payment_allocation_reversals_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,reversal_number varchar(40),allocation_id uuid,reversed_by_user_id uuid,reversed_at timestamptz,reversal_reason varchar(1000),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE student_payment_reversals_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,reversal_number varchar(40),payment_id uuid,reversed_by_user_id uuid,reversed_at timestamptz,reversal_reason varchar(1000),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE finance_credit_notes_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,credit_note_number varchar(40),invoice_id uuid,transaction_currency_code varchar(3),transaction_amount numeric(16,2),base_currency_code varchar(3),base_amount numeric(16,2),credit_note_date date,status varchar(20),prepared_by_user_id uuid,prepared_at timestamptz,preparation_reason varchar(1000),posted_by_user_id uuid,posted_at timestamptz,posting_reason varchar(1000),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE finance_credit_note_lines_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,credit_note_id uuid,line_number integer,invoice_line_id uuid,transaction_amount numeric(16,2),base_amount numeric(16,2),reason varchar(500),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));

CREATE OR REPLACE FUNCTION enforce_exchange_rate_governance() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE conflicting_rate uuid;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Exchange-rate evidence cannot be deleted'; END IF;
    IF TG_OP='UPDATE' THEN
        IF OLD.status='RETIRED' THEN RAISE EXCEPTION 'Retired exchange-rate evidence is immutable'; END IF;
        IF OLD.status='ACTIVE' AND ROW(NEW.source_currency_code,NEW.base_currency_code,NEW.rate_to_base,NEW.effective_from,NEW.effective_to,NEW.source_name,NEW.source_reference,NEW.prepared_by_user_id) IS DISTINCT FROM ROW(OLD.source_currency_code,OLD.base_currency_code,OLD.rate_to_base,OLD.effective_from,OLD.effective_to,OLD.source_name,OLD.source_reference,OLD.prepared_by_user_id) THEN RAISE EXCEPTION 'Active exchange-rate evidence is immutable'; END IF;
        IF NOT ((OLD.status='DRAFT' AND NEW.status IN ('DRAFT','ACTIVE')) OR (OLD.status='ACTIVE' AND NEW.status IN ('ACTIVE','RETIRED'))) THEN RAISE EXCEPTION 'Invalid exchange-rate status transition'; END IF;
    END IF;
    IF NEW.status='ACTIVE' AND (TG_OP='INSERT' OR OLD.status IS DISTINCT FROM 'ACTIVE') THEN
        SELECT id INTO conflicting_rate FROM exchange_rates WHERE id<>NEW.id AND source_currency_code=NEW.source_currency_code AND base_currency_code='USD' AND status='ACTIVE' AND deleted_at IS NULL AND tstzrange(effective_from,coalesce(effective_to,'infinity'::timestamptz),'[)') && tstzrange(NEW.effective_from,coalesce(NEW.effective_to,'infinity'::timestamptz),'[)') LIMIT 1;
        IF conflicting_rate IS NOT NULL THEN RAISE EXCEPTION 'Active exchange rate overlaps another rate for this currency'; END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_exchange_rate_governance BEFORE INSERT OR UPDATE OR DELETE ON exchange_rates FOR EACH ROW EXECUTE FUNCTION enforce_exchange_rate_governance();

CREATE OR REPLACE FUNCTION enforce_student_payment_governance() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE account_status varchar(30);rate_currency varchar(3);rate_status varchar(20);rate_from timestamptz;rate_until timestamptz;rate_to_base_value numeric(20,10);
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Student payment evidence cannot be deleted'; END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.student_finance_account_id IS NOT NULL THEN SELECT status INTO account_status FROM student_finance_accounts WHERE id=NEW.student_finance_account_id AND deleted_at IS NULL;IF account_status IS DISTINCT FROM 'ACTIVE' THEN RAISE EXCEPTION 'Payment account must be active';END IF;END IF;
    ELSE
        IF OLD.reconciliation_status<>'PENDING' THEN RAISE EXCEPTION 'Reconciled or rejected payment evidence is immutable'; END IF;
        IF ROW(NEW.payment_number,NEW.student_finance_account_id,NEW.payer_name,NEW.provider_code,NEW.provider_transaction_reference,NEW.payment_channel,NEW.transaction_currency_code,NEW.transaction_amount,NEW.base_currency_code,NEW.paid_at,NEW.provider_event_fingerprint,NEW.captured_by_user_id,NEW.captured_at) IS DISTINCT FROM ROW(OLD.payment_number,OLD.student_finance_account_id,OLD.payer_name,OLD.provider_code,OLD.provider_transaction_reference,OLD.payment_channel,OLD.transaction_currency_code,OLD.transaction_amount,OLD.base_currency_code,OLD.paid_at,OLD.provider_event_fingerprint,OLD.captured_by_user_id,OLD.captured_at) THEN RAISE EXCEPTION 'Captured payment provider and transaction evidence is immutable'; END IF;
        IF ROW(NEW.exchange_rate_id,NEW.base_amount,NEW.rating_status,NEW.rating_applied_by_user_id,NEW.rating_applied_at) IS DISTINCT FROM ROW(OLD.exchange_rate_id,OLD.base_amount,OLD.rating_status,OLD.rating_applied_by_user_id,OLD.rating_applied_at)
            AND NOT (OLD.rating_status='UNRATED' AND OLD.exchange_rate_id IS NULL AND OLD.base_amount IS NULL
                AND OLD.rating_applied_by_user_id IS NULL AND OLD.rating_applied_at IS NULL AND NEW.rating_status='RATED'
                AND NEW.exchange_rate_id IS NOT NULL AND NEW.base_amount IS NOT NULL
                AND NEW.rating_applied_by_user_id IS NOT NULL AND NEW.rating_applied_at IS NOT NULL)
        THEN RAISE EXCEPTION 'Payment rating may only transition once from unrated to rated'; END IF;
        IF NEW.reconciliation_status NOT IN ('PENDING','RECONCILED','REJECTED') THEN RAISE EXCEPTION 'Invalid payment reconciliation transition'; END IF;
    END IF;
    IF NEW.exchange_rate_id IS NOT NULL THEN
        SELECT source_currency_code,status,effective_from,effective_to,rate_to_base INTO rate_currency,rate_status,rate_from,rate_until,rate_to_base_value FROM exchange_rates WHERE id=NEW.exchange_rate_id;
        IF rate_currency IS DISTINCT FROM NEW.transaction_currency_code OR rate_status IS DISTINCT FROM 'ACTIVE' OR rate_from>NEW.paid_at OR (rate_until IS NOT NULL AND rate_until<=NEW.paid_at) THEN RAISE EXCEPTION 'Payment exchange rate must be active and effective when paid';END IF;
        IF NEW.base_amount IS DISTINCT FROM round(NEW.transaction_amount*rate_to_base_value,2) THEN RAISE EXCEPTION 'Payment base amount must equal the transaction amount converted using its effective rate';END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_student_payment_governance BEFORE INSERT OR UPDATE OR DELETE ON student_account_payments FOR EACH ROW EXECUTE FUNCTION enforce_student_payment_governance();

CREATE OR REPLACE FUNCTION enforce_student_payment_suspense_resolution() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE payment_status varchar(30);original_account uuid;account_status varchar(30);capturer uuid;
BEGIN IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Payment suspense-resolution evidence is immutable';END IF;SELECT reconciliation_status,student_finance_account_id,captured_by_user_id INTO payment_status,original_account,capturer FROM student_account_payments WHERE id=NEW.payment_id;SELECT status INTO account_status FROM student_finance_accounts WHERE id=NEW.student_finance_account_id AND deleted_at IS NULL;IF payment_status IS DISTINCT FROM 'RECONCILED' OR original_account IS NOT NULL OR account_status IS DISTINCT FROM 'ACTIVE' OR capturer=NEW.resolved_by_user_id OR EXISTS(SELECT 1 FROM student_payment_reversals WHERE payment_id=NEW.payment_id) THEN RAISE EXCEPTION 'Only an independently resolved, unreversed, reconciled suspense payment can be assigned to an active account';END IF;RETURN NEW;END $$;
CREATE TRIGGER trg_student_payment_suspense_resolution BEFORE INSERT OR UPDATE OR DELETE ON student_payment_suspense_resolutions FOR EACH ROW EXECUTE FUNCTION enforce_student_payment_suspense_resolution();

CREATE OR REPLACE FUNCTION enforce_student_payment_receipt() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE payment_record student_account_payments%ROWTYPE;effective_account uuid;
BEGIN IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Issued receipt evidence is immutable';END IF;SELECT * INTO payment_record FROM student_account_payments WHERE id=NEW.payment_id;SELECT coalesce(payment_record.student_finance_account_id,(SELECT student_finance_account_id FROM student_payment_suspense_resolutions WHERE payment_id=NEW.payment_id)) INTO effective_account;IF payment_record.reconciliation_status<>'RECONCILED' OR payment_record.rating_status<>'RATED' OR EXISTS(SELECT 1 FROM student_payment_reversals WHERE payment_id=NEW.payment_id) OR ROW(NEW.student_finance_account_id,NEW.payer_name,NEW.transaction_currency_code,NEW.transaction_amount,NEW.base_currency_code,NEW.base_amount) IS DISTINCT FROM ROW(effective_account,payment_record.payer_name,payment_record.transaction_currency_code,payment_record.transaction_amount,payment_record.base_currency_code,payment_record.base_amount) THEN RAISE EXCEPTION 'Receipt must preserve the exact reconciled payment evidence';END IF;RETURN NEW;END $$;
CREATE TRIGGER trg_student_payment_receipt BEFORE INSERT OR UPDATE OR DELETE ON student_payment_receipts FOR EACH ROW EXECUTE FUNCTION enforce_student_payment_receipt();

CREATE OR REPLACE FUNCTION enforce_student_payment_allocation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE payment_status varchar(30);payment_currency varchar(3);payment_amount numeric(16,2);payment_base numeric(16,2);payment_account uuid;invoice_currency varchar(3);invoice_account uuid;invoice_amount numeric(16,2);invoice_base numeric(16,2);payment_allocated numeric(16,2);payment_base_allocated numeric(16,2);invoice_allocated numeric(16,2);invoice_base_allocated numeric(16,2);invoice_credited numeric(16,2);invoice_base_credited numeric(16,2);expected_payment_base numeric(16,2);expected_invoice_base numeric(16,2);
BEGIN IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Payment-allocation evidence is immutable; use an allocation reversal';END IF;
SELECT reconciliation_status,transaction_currency_code,transaction_amount,base_amount,coalesce(student_finance_account_id,(SELECT student_finance_account_id FROM student_payment_suspense_resolutions WHERE payment_id=NEW.payment_id)) INTO payment_status,payment_currency,payment_amount,payment_base,payment_account FROM student_account_payments WHERE id=NEW.payment_id;
SELECT transaction_currency_code,student_finance_account_id,gross_transaction_amount,gross_base_amount INTO invoice_currency,invoice_account,invoice_amount,invoice_base FROM finance_invoices WHERE id=NEW.invoice_id;
SELECT coalesce(sum(a.transaction_amount),0),coalesce(sum(a.payment_base_amount),0) INTO payment_allocated,payment_base_allocated FROM student_payment_allocations a WHERE a.payment_id=NEW.payment_id AND NOT EXISTS(SELECT 1 FROM student_payment_allocation_reversals r WHERE r.allocation_id=a.id);
SELECT coalesce(sum(a.transaction_amount),0),coalesce(sum(a.invoice_base_amount),0) INTO invoice_allocated,invoice_base_allocated FROM student_payment_allocations a WHERE a.invoice_id=NEW.invoice_id AND NOT EXISTS(SELECT 1 FROM student_payment_allocation_reversals r WHERE r.allocation_id=a.id);
SELECT coalesce(sum(c.transaction_amount),0),coalesce(sum(c.base_amount),0) INTO invoice_credited,invoice_base_credited FROM finance_credit_notes c WHERE c.invoice_id=NEW.invoice_id AND c.status='POSTED';
IF NEW.transaction_amount=payment_amount-payment_allocated THEN expected_payment_base=payment_base-payment_base_allocated;ELSE expected_payment_base=round(NEW.transaction_amount*payment_base/payment_amount,2);END IF;
IF NEW.transaction_amount=invoice_amount-invoice_allocated-invoice_credited THEN expected_invoice_base=invoice_base-invoice_base_allocated-invoice_base_credited;ELSE expected_invoice_base=round(NEW.transaction_amount*invoice_base/invoice_amount,2);END IF;
IF payment_status IS DISTINCT FROM 'RECONCILED' OR payment_account IS NULL OR payment_account IS DISTINCT FROM invoice_account OR payment_currency IS DISTINCT FROM invoice_currency OR NEW.transaction_currency_code IS DISTINCT FROM payment_currency OR NEW.base_currency_code IS DISTINCT FROM 'USD' OR EXISTS(SELECT 1 FROM student_payment_reversals WHERE payment_id=NEW.payment_id) OR NEW.transaction_amount>payment_amount-payment_allocated OR NEW.payment_base_amount>payment_base-payment_base_allocated OR NEW.transaction_amount>invoice_amount-invoice_allocated-invoice_credited OR NEW.invoice_base_amount>invoice_base-invoice_base_allocated-invoice_base_credited OR NEW.payment_base_amount IS DISTINCT FROM expected_payment_base OR NEW.invoice_base_amount IS DISTINCT FROM expected_invoice_base OR NEW.realised_exchange_difference IS DISTINCT FROM NEW.payment_base_amount-NEW.invoice_base_amount THEN RAISE EXCEPTION 'Payment allocation must match available account, currency, payment basis, invoice basis, and realised exchange difference';END IF;RETURN NEW;END $$;
CREATE TRIGGER trg_student_payment_allocation BEFORE INSERT OR UPDATE OR DELETE ON student_payment_allocations FOR EACH ROW EXECUTE FUNCTION enforce_student_payment_allocation();

CREATE OR REPLACE FUNCTION enforce_student_allocation_reversal() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE allocator uuid;BEGIN IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Allocation-reversal evidence is immutable';END IF;SELECT allocated_by_user_id INTO allocator FROM student_payment_allocations WHERE id=NEW.allocation_id;IF allocator IS NULL OR allocator=NEW.reversed_by_user_id THEN RAISE EXCEPTION 'Allocation reversal requires an independent Finance operator';END IF;RETURN NEW;END $$;
CREATE TRIGGER trg_student_allocation_reversal BEFORE INSERT OR UPDATE OR DELETE ON student_payment_allocation_reversals FOR EACH ROW EXECUTE FUNCTION enforce_student_allocation_reversal();

CREATE OR REPLACE FUNCTION enforce_student_payment_reversal() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE capturer uuid;reconciler uuid;unreversed_allocations integer;BEGIN IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Payment-reversal evidence is immutable';END IF;SELECT captured_by_user_id,reconciled_by_user_id INTO capturer,reconciler FROM student_account_payments WHERE id=NEW.payment_id AND reconciliation_status='RECONCILED';SELECT count(*) INTO unreversed_allocations FROM student_payment_allocations a WHERE a.payment_id=NEW.payment_id AND NOT EXISTS(SELECT 1 FROM student_payment_allocation_reversals r WHERE r.allocation_id=a.id);IF reconciler IS NULL OR reconciler=NEW.reversed_by_user_id OR capturer=NEW.reversed_by_user_id OR unreversed_allocations>0 THEN RAISE EXCEPTION 'Payment reversal requires an independent operator and reversal of every active allocation';END IF;RETURN NEW;END $$;
CREATE TRIGGER trg_student_payment_reversal BEFORE INSERT OR UPDATE OR DELETE ON student_payment_reversals FOR EACH ROW EXECUTE FUNCTION enforce_student_payment_reversal();

CREATE OR REPLACE FUNCTION enforce_finance_credit_note() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Credit-note evidence is immutable';END IF;
IF TG_OP='INSERT' THEN IF NEW.status<>'DRAFT' THEN RAISE EXCEPTION 'A credit note must enter the independent posting workflow as a draft';END IF;
ELSE
    IF OLD.status<>'DRAFT' OR NEW.status<>'POSTED' THEN RAISE EXCEPTION 'Only a draft credit note can transition once to posted';END IF;
    IF ROW(NEW.credit_note_number,NEW.invoice_id,NEW.transaction_currency_code,NEW.transaction_amount,NEW.base_currency_code,NEW.base_amount,NEW.credit_note_date,NEW.prepared_by_user_id,NEW.prepared_at,NEW.preparation_reason) IS DISTINCT FROM ROW(OLD.credit_note_number,OLD.invoice_id,OLD.transaction_currency_code,OLD.transaction_amount,OLD.base_currency_code,OLD.base_amount,OLD.credit_note_date,OLD.prepared_by_user_id,OLD.prepared_at,OLD.preparation_reason) THEN RAISE EXCEPTION 'Submitted credit-note amount and source evidence is immutable';END IF;
END IF;RETURN NEW;END $$;
CREATE TRIGGER trg_finance_credit_note BEFORE INSERT OR UPDATE OR DELETE ON finance_credit_notes FOR EACH ROW EXECUTE FUNCTION enforce_finance_credit_note();

CREATE OR REPLACE FUNCTION enforce_finance_credit_note_line() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE note_invoice uuid;note_currency varchar(3);note_status varchar(20);line_invoice uuid;line_amount numeric(16,2);line_base numeric(16,2);credited numeric(16,2);credited_base numeric(16,2);
BEGIN IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Submitted credit-note-line evidence is immutable';END IF;SELECT invoice_id,transaction_currency_code,status INTO note_invoice,note_currency,note_status FROM finance_credit_notes WHERE id=NEW.credit_note_id;SELECT invoice_id,transaction_amount,base_amount INTO line_invoice,line_amount,line_base FROM finance_invoice_lines WHERE id=NEW.invoice_line_id;SELECT coalesce(sum(line.transaction_amount),0),coalesce(sum(line.base_amount),0) INTO credited,credited_base FROM finance_credit_note_lines line JOIN finance_credit_notes note ON note.id=line.credit_note_id WHERE line.invoice_line_id=NEW.invoice_line_id AND note.status='POSTED';IF note_status IS DISTINCT FROM 'DRAFT' OR note_invoice IS DISTINCT FROM line_invoice OR NEW.transaction_amount>line_amount-credited OR NEW.base_amount>line_base-credited_base THEN RAISE EXCEPTION 'Draft credit-note line exceeds the remaining posted amount of its invoice line';END IF;RETURN NEW;END $$;
CREATE TRIGGER trg_finance_credit_note_line BEFORE INSERT OR UPDATE OR DELETE ON finance_credit_note_lines FOR EACH ROW EXECUTE FUNCTION enforce_finance_credit_note_line();

CREATE OR REPLACE FUNCTION validate_finance_credit_note() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE line_count integer;transaction_total numeric(16,2);base_total numeric(16,2);invoice_currency varchar(3);BEGIN SELECT count(*),coalesce(sum(transaction_amount),0),coalesce(sum(base_amount),0) INTO line_count,transaction_total,base_total FROM finance_credit_note_lines WHERE credit_note_id=NEW.id;SELECT transaction_currency_code INTO invoice_currency FROM finance_invoices WHERE id=NEW.invoice_id;IF line_count=0 OR transaction_total IS DISTINCT FROM NEW.transaction_amount OR base_total IS DISTINCT FROM NEW.base_amount OR invoice_currency IS DISTINCT FROM NEW.transaction_currency_code THEN RAISE EXCEPTION 'Credit-note lines, totals, and invoice currency must reconcile exactly';END IF;IF NEW.status='POSTED' AND EXISTS(SELECT 1 FROM finance_invoice_lines invoice_line WHERE invoice_line.invoice_id=NEW.invoice_id AND (SELECT coalesce(sum(note_line.transaction_amount),0) FROM finance_credit_note_lines note_line JOIN finance_credit_notes note ON note.id=note_line.credit_note_id WHERE note_line.invoice_line_id=invoice_line.id AND note.status='POSTED')>invoice_line.transaction_amount) THEN RAISE EXCEPTION 'Posted credit notes exceed an invoice line balance';END IF;RETURN NULL;END $$;
CREATE CONSTRAINT TRIGGER trg_validate_finance_credit_note AFTER INSERT OR UPDATE ON finance_credit_notes DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_finance_credit_note();

GRANT USAGE,SELECT ON SEQUENCE student_payment_number_sequence,student_payment_receipt_number_sequence,student_payment_allocation_number_sequence,finance_credit_note_number_sequence,finance_reversal_number_sequence TO emhare_service;
GRANT SELECT,INSERT,UPDATE ON exchange_rates,student_account_payments TO emhare_service;
GRANT SELECT,INSERT ON student_payment_suspense_resolutions,student_payment_receipts,student_payment_allocations,student_payment_allocation_reversals,student_payment_reversals,finance_credit_note_lines TO emhare_service;
GRANT SELECT,INSERT,UPDATE ON finance_credit_notes TO emhare_service;
