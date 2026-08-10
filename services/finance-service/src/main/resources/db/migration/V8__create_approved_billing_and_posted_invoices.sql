-- Author: Tinashe K

CREATE SEQUENCE finance_billing_event_number_sequence START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE finance_invoice_number_sequence START WITH 1 INCREMENT BY 1;

CREATE TABLE finance_billing_events (
    id uuid PRIMARY KEY,
    event_number varchar(40) NOT NULL,
    source_service varchar(80) NOT NULL,
    source_event_type varchar(160) NOT NULL,
    source_event_id uuid NOT NULL,
    source_aggregate_type varchar(80) NOT NULL,
    source_aggregate_id uuid NOT NULL,
    source_line_reference varchar(160) NOT NULL,
    student_finance_account_id uuid NOT NULL REFERENCES student_finance_accounts(id),
    student_id uuid NOT NULL,
    student_number varchar(40) NOT NULL,
    fee_catalogue_id uuid NOT NULL REFERENCES finance_fee_catalogues(id),
    fee_rule_id uuid NOT NULL REFERENCES finance_fee_rules(id),
    description varchar(500) NOT NULL,
    quantity numeric(12,4) NOT NULL,
    transaction_currency_code varchar(3) NOT NULL,
    transaction_unit_amount numeric(16,2) NOT NULL,
    transaction_amount numeric(16,2) NOT NULL,
    base_currency_code varchar(3) NOT NULL DEFAULT 'USD',
    exchange_rate_id uuid REFERENCES exchange_rates(id),
    base_unit_amount numeric(16,2) NOT NULL,
    base_amount numeric(16,2) NOT NULL,
    effective_at timestamptz NOT NULL,
    status varchar(30) NOT NULL,
    prepared_by_user_id uuid NOT NULL,
    submitted_at timestamptz NOT NULL,
    approved_by_user_id uuid,
    approved_at timestamptz,
    approval_reason varchar(1000),
    rejected_by_user_id uuid,
    rejected_at timestamptz,
    rejection_reason varchar(1000),
    invoiced_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_finance_billing_event_number UNIQUE(event_number),
    CONSTRAINT uk_finance_billing_event_source_line UNIQUE(source_service,source_event_id,source_line_reference),
    CONSTRAINT ck_finance_billing_event_source CHECK (
        length(trim(source_service))>0 AND length(trim(source_event_type))>0
        AND length(trim(source_aggregate_type))>0 AND length(trim(source_line_reference))>0),
    CONSTRAINT ck_finance_billing_event_description CHECK (length(trim(description))>0),
    CONSTRAINT ck_finance_billing_event_quantity CHECK (quantity>0),
    CONSTRAINT ck_finance_billing_event_amounts CHECK (
        transaction_unit_amount>0 AND transaction_amount=round(transaction_unit_amount*quantity,2)
        AND base_unit_amount>0 AND base_amount=round(base_unit_amount*quantity,2)),
    CONSTRAINT ck_finance_billing_event_currency CHECK (
        transaction_currency_code=upper(transaction_currency_code)
        AND transaction_currency_code ~ '^[A-Z]{3}$' AND base_currency_code='USD'),
    CONSTRAINT ck_finance_billing_event_rating CHECK (
        (transaction_currency_code='USD' AND exchange_rate_id IS NULL
            AND base_unit_amount=transaction_unit_amount AND base_amount=transaction_amount)
        OR (transaction_currency_code<>'USD' AND exchange_rate_id IS NOT NULL
            AND base_unit_amount>0 AND base_amount>0)),
    CONSTRAINT ck_finance_billing_event_status CHECK (
        status IN ('PENDING_APPROVAL','APPROVED','REJECTED','INVOICED')),
    CONSTRAINT ck_finance_billing_event_workflow CHECK (
        (status='PENDING_APPROVAL' AND approved_by_user_id IS NULL AND approved_at IS NULL AND approval_reason IS NULL
            AND rejected_by_user_id IS NULL AND rejected_at IS NULL AND rejection_reason IS NULL AND invoiced_at IS NULL)
        OR (status='APPROVED' AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL
            AND length(trim(approval_reason))>0 AND rejected_by_user_id IS NULL AND rejected_at IS NULL
            AND rejection_reason IS NULL AND invoiced_at IS NULL)
        OR (status='REJECTED' AND approved_by_user_id IS NULL AND approved_at IS NULL AND approval_reason IS NULL
            AND rejected_by_user_id IS NOT NULL AND rejected_at IS NOT NULL
            AND length(trim(rejection_reason))>0 AND invoiced_at IS NULL)
        OR (status='INVOICED' AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL
            AND length(trim(approval_reason))>0 AND rejected_by_user_id IS NULL AND rejected_at IS NULL
            AND rejection_reason IS NULL AND invoiced_at IS NOT NULL)),
    CONSTRAINT ck_finance_billing_event_actor_separation CHECK (
        (approved_by_user_id IS NULL OR approved_by_user_id<>prepared_by_user_id)
        AND (rejected_by_user_id IS NULL OR rejected_by_user_id<>prepared_by_user_id))
);
CREATE INDEX idx_finance_billing_event_approval_queue
    ON finance_billing_events(status,submitted_at,event_number) WHERE deleted_at IS NULL;
CREATE INDEX idx_finance_billing_event_student
    ON finance_billing_events(student_finance_account_id,effective_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_finance_billing_event_source
    ON finance_billing_events(source_aggregate_type,source_aggregate_id) WHERE deleted_at IS NULL;

CREATE TABLE finance_billing_event_scopes (
    id uuid PRIMARY KEY,
    billing_event_id uuid NOT NULL REFERENCES finance_billing_events(id),
    scope_dimension varchar(40) NOT NULL,
    reference_id uuid,
    reference_code varchar(80),
    reference_name varchar(200),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_finance_billing_event_scope_dimension UNIQUE(billing_event_id,scope_dimension),
    CONSTRAINT ck_finance_billing_event_scope_dimension CHECK (scope_dimension IN (
        'GLOBAL','ACADEMIC_PERIOD','APPLICATION_TYPE','APPLICANT_CATEGORY','PROGRAMME','MODULE',
        'ACCOMMODATION_TYPE','DINING_PLAN','GRADUATION')),
    CONSTRAINT ck_finance_billing_event_scope_reference CHECK (
        (scope_dimension='GLOBAL' AND reference_id IS NULL AND reference_code IS NULL AND reference_name IS NULL)
        OR (scope_dimension<>'GLOBAL' AND (reference_id IS NOT NULL OR length(trim(reference_code))>0)
            AND length(trim(reference_name))>0))
);
CREATE INDEX idx_finance_billing_event_scope_lookup
    ON finance_billing_event_scopes(scope_dimension,reference_id,reference_code) WHERE deleted_at IS NULL;

CREATE TABLE finance_invoices (
    id uuid PRIMARY KEY,
    invoice_number varchar(40) NOT NULL,
    student_finance_account_id uuid NOT NULL REFERENCES student_finance_accounts(id),
    student_id uuid NOT NULL,
    student_number varchar(40) NOT NULL,
    transaction_currency_code varchar(3) NOT NULL,
    base_currency_code varchar(3) NOT NULL DEFAULT 'USD',
    gross_transaction_amount numeric(16,2) NOT NULL,
    gross_base_amount numeric(16,2) NOT NULL,
    invoice_date date NOT NULL,
    due_date date NOT NULL,
    status varchar(20) NOT NULL,
    posted_by_user_id uuid NOT NULL,
    posted_at timestamptz NOT NULL,
    posting_reason varchar(1000) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_finance_invoice_number UNIQUE(invoice_number),
    CONSTRAINT ck_finance_invoice_currency CHECK (
        transaction_currency_code=upper(transaction_currency_code)
        AND transaction_currency_code ~ '^[A-Z]{3}$' AND base_currency_code='USD'),
    CONSTRAINT ck_finance_invoice_amounts CHECK (gross_transaction_amount>0 AND gross_base_amount>0),
    CONSTRAINT ck_finance_invoice_dates CHECK (due_date>=invoice_date),
    CONSTRAINT ck_finance_invoice_status CHECK (status='POSTED'),
    CONSTRAINT ck_finance_invoice_reason CHECK (length(trim(posting_reason))>0)
);
CREATE INDEX idx_finance_invoice_student_date
    ON finance_invoices(student_finance_account_id,invoice_date,invoice_number) WHERE deleted_at IS NULL;

CREATE TABLE finance_invoice_lines (
    id uuid PRIMARY KEY,
    invoice_id uuid NOT NULL REFERENCES finance_invoices(id),
    line_number integer NOT NULL,
    billing_event_id uuid NOT NULL REFERENCES finance_billing_events(id),
    fee_catalogue_id uuid NOT NULL REFERENCES finance_fee_catalogues(id),
    fee_rule_id uuid NOT NULL REFERENCES finance_fee_rules(id),
    fee_code varchar(50) NOT NULL,
    description varchar(500) NOT NULL,
    quantity numeric(12,4) NOT NULL,
    transaction_currency_code varchar(3) NOT NULL,
    transaction_unit_amount numeric(16,2) NOT NULL,
    transaction_amount numeric(16,2) NOT NULL,
    base_currency_code varchar(3) NOT NULL,
    exchange_rate_id uuid REFERENCES exchange_rates(id),
    base_unit_amount numeric(16,2) NOT NULL,
    base_amount numeric(16,2) NOT NULL,
    receivable_account_code varchar(50) NOT NULL,
    revenue_account_code varchar(50) NOT NULL,
    tax_code varchar(30),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_finance_invoice_line_number UNIQUE(invoice_id,line_number),
    CONSTRAINT uk_finance_invoice_line_billing_event UNIQUE(billing_event_id),
    CONSTRAINT ck_finance_invoice_line_number CHECK (line_number>0),
    CONSTRAINT ck_finance_invoice_line_description CHECK (length(trim(description))>0),
    CONSTRAINT ck_finance_invoice_line_amounts CHECK (
        quantity>0 AND transaction_unit_amount>0 AND transaction_amount=round(transaction_unit_amount*quantity,2)
        AND base_unit_amount>0 AND base_amount=round(base_unit_amount*quantity,2)),
    CONSTRAINT ck_finance_invoice_line_currency CHECK (
        transaction_currency_code=upper(transaction_currency_code)
        AND transaction_currency_code ~ '^[A-Z]{3}$' AND base_currency_code='USD')
);
CREATE INDEX idx_finance_invoice_line_posting_accounts
    ON finance_invoice_lines(receivable_account_code,revenue_account_code);

CREATE TABLE finance_billing_events_aud (
    id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,
    event_number varchar(40),source_service varchar(80),source_event_type varchar(160),source_event_id uuid,
    source_aggregate_type varchar(80),source_aggregate_id uuid,source_line_reference varchar(160),
    student_finance_account_id uuid,student_id uuid,student_number varchar(40),fee_catalogue_id uuid,fee_rule_id uuid,
    description varchar(500),quantity numeric(12,4),transaction_currency_code varchar(3),
    transaction_unit_amount numeric(16,2),transaction_amount numeric(16,2),base_currency_code varchar(3),
    exchange_rate_id uuid,base_unit_amount numeric(16,2),base_amount numeric(16,2),effective_at timestamptz,
    status varchar(30),prepared_by_user_id uuid,submitted_at timestamptz,approved_by_user_id uuid,
    approved_at timestamptz,approval_reason varchar(1000),rejected_by_user_id uuid,rejected_at timestamptz,
    rejection_reason varchar(1000),invoiced_at timestamptz,created_at timestamptz,updated_at timestamptz,
    created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,
    PRIMARY KEY(id,rev)
);
CREATE TABLE finance_billing_event_scopes_aud (
    id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,billing_event_id uuid,
    scope_dimension varchar(40),reference_id uuid,reference_code varchar(80),reference_name varchar(200),
    created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,
    deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev)
);
CREATE TABLE finance_invoices_aud (
    id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,invoice_number varchar(40),
    student_finance_account_id uuid,student_id uuid,student_number varchar(40),transaction_currency_code varchar(3),
    base_currency_code varchar(3),gross_transaction_amount numeric(16,2),gross_base_amount numeric(16,2),
    invoice_date date,due_date date,status varchar(20),posted_by_user_id uuid,posted_at timestamptz,
    posting_reason varchar(1000),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,
    modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev)
);
CREATE TABLE finance_invoice_lines_aud (
    id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,invoice_id uuid,line_number integer,
    billing_event_id uuid,fee_catalogue_id uuid,fee_rule_id uuid,fee_code varchar(50),description varchar(500),
    quantity numeric(12,4),transaction_currency_code varchar(3),transaction_unit_amount numeric(16,2),
    transaction_amount numeric(16,2),base_currency_code varchar(3),exchange_rate_id uuid,
    base_unit_amount numeric(16,2),base_amount numeric(16,2),receivable_account_code varchar(50),
    revenue_account_code varchar(50),tax_code varchar(30),created_at timestamptz,updated_at timestamptz,
    created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,
    PRIMARY KEY(id,rev)
);

CREATE OR REPLACE FUNCTION enforce_finance_billing_event_governance() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE account_student_id uuid; account_student_number varchar(40); account_status varchar(30);
DECLARE catalogue_status varchar(20); rule_status varchar(20); rule_catalogue_id uuid;
DECLARE rule_currency varchar(3); rule_transaction_amount numeric(16,2); rule_base_currency varchar(3);
DECLARE rule_exchange_rate_id uuid; rule_base_amount numeric(16,2); rule_from timestamptz; rule_until timestamptz;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Billing-event evidence cannot be deleted'; END IF;
    IF TG_OP='INSERT' THEN
        SELECT student_id,student_number,status INTO account_student_id,account_student_number,account_status
          FROM student_finance_accounts WHERE id=NEW.student_finance_account_id AND deleted_at IS NULL;
        IF account_status IS DISTINCT FROM 'ACTIVE' OR account_student_id IS DISTINCT FROM NEW.student_id
            OR account_student_number IS DISTINCT FROM NEW.student_number THEN
            RAISE EXCEPTION 'Billing event requires the matching active student finance account';
        END IF;
        SELECT status INTO catalogue_status FROM finance_fee_catalogues
          WHERE id=NEW.fee_catalogue_id AND deleted_at IS NULL;
        SELECT status,fee_catalogue_id,transaction_currency_code,transaction_amount,base_currency_code,
               exchange_rate_id,base_amount,effective_from,effective_until
          INTO rule_status,rule_catalogue_id,rule_currency,rule_transaction_amount,rule_base_currency,
               rule_exchange_rate_id,rule_base_amount,rule_from,rule_until
          FROM finance_fee_rules WHERE id=NEW.fee_rule_id AND deleted_at IS NULL;
        IF catalogue_status IS DISTINCT FROM 'ACTIVE' OR rule_status IS DISTINCT FROM 'APPROVED'
            OR rule_catalogue_id IS DISTINCT FROM NEW.fee_catalogue_id
            OR NEW.effective_at<rule_from OR (rule_until IS NOT NULL AND NEW.effective_at>=rule_until) THEN
            RAISE EXCEPTION 'Billing event requires one approved effective price from an active fee definition';
        END IF;
        IF NEW.transaction_currency_code IS DISTINCT FROM rule_currency
            OR NEW.transaction_unit_amount IS DISTINCT FROM rule_transaction_amount
            OR NEW.base_currency_code IS DISTINCT FROM rule_base_currency
            OR NEW.exchange_rate_id IS DISTINCT FROM rule_exchange_rate_id
            OR NEW.base_unit_amount IS DISTINCT FROM rule_base_amount THEN
            RAISE EXCEPTION 'Billing event pricing snapshot must equal the approved fee rule';
        END IF;
    ELSE
        IF OLD.status IN ('REJECTED','INVOICED') THEN RAISE EXCEPTION 'Final billing-event evidence is immutable'; END IF;
        IF ROW(NEW.event_number,NEW.source_service,NEW.source_event_type,NEW.source_event_id,
                NEW.source_aggregate_type,NEW.source_aggregate_id,NEW.source_line_reference,
                NEW.student_finance_account_id,NEW.student_id,NEW.student_number,NEW.fee_catalogue_id,
                NEW.fee_rule_id,NEW.description,NEW.quantity,NEW.transaction_currency_code,
                NEW.transaction_unit_amount,NEW.transaction_amount,NEW.base_currency_code,NEW.exchange_rate_id,
                NEW.base_unit_amount,NEW.base_amount,NEW.effective_at,NEW.prepared_by_user_id,NEW.submitted_at)
            IS DISTINCT FROM ROW(OLD.event_number,OLD.source_service,OLD.source_event_type,OLD.source_event_id,
                OLD.source_aggregate_type,OLD.source_aggregate_id,OLD.source_line_reference,
                OLD.student_finance_account_id,OLD.student_id,OLD.student_number,OLD.fee_catalogue_id,
                OLD.fee_rule_id,OLD.description,OLD.quantity,OLD.transaction_currency_code,
                OLD.transaction_unit_amount,OLD.transaction_amount,OLD.base_currency_code,OLD.exchange_rate_id,
                OLD.base_unit_amount,OLD.base_amount,OLD.effective_at,OLD.prepared_by_user_id,OLD.submitted_at) THEN
            RAISE EXCEPTION 'Submitted billing-event source and pricing evidence is immutable';
        END IF;
        IF NOT ((OLD.status='PENDING_APPROVAL' AND NEW.status IN ('PENDING_APPROVAL','APPROVED','REJECTED'))
            OR (OLD.status='APPROVED' AND NEW.status IN ('APPROVED','INVOICED'))) THEN
            RAISE EXCEPTION 'Invalid billing-event status transition';
        END IF;
        IF NEW.status='INVOICED' AND NOT EXISTS (
            SELECT 1 FROM finance_invoice_lines WHERE billing_event_id=NEW.id) THEN
            RAISE EXCEPTION 'Billing event cannot be marked invoiced without an immutable invoice line';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_finance_billing_event_governance BEFORE INSERT OR UPDATE OR DELETE ON finance_billing_events
    FOR EACH ROW EXECUTE FUNCTION enforce_finance_billing_event_governance();

CREATE OR REPLACE FUNCTION enforce_finance_billing_event_scope_governance() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE event_status varchar(30);
BEGIN
    IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Billing-event scope evidence is immutable'; END IF;
    SELECT status INTO event_status FROM finance_billing_events WHERE id=NEW.billing_event_id;
    IF event_status IS DISTINCT FROM 'PENDING_APPROVAL' THEN
        RAISE EXCEPTION 'Scopes can only be captured with a pending billing event';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_finance_billing_event_scope_governance BEFORE INSERT OR UPDATE OR DELETE ON finance_billing_event_scopes
    FOR EACH ROW EXECUTE FUNCTION enforce_finance_billing_event_scope_governance();

CREATE OR REPLACE FUNCTION enforce_finance_invoice_governance() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Posted invoice evidence is immutable; use a credit note or reversal'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_finance_invoice_governance BEFORE INSERT OR UPDATE OR DELETE ON finance_invoices
    FOR EACH ROW EXECUTE FUNCTION enforce_finance_invoice_governance();

CREATE OR REPLACE FUNCTION enforce_finance_invoice_line_governance() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE event_status varchar(30); event_account_id uuid; event_catalogue_id uuid; event_rule_id uuid;
DECLARE event_currency varchar(3); event_unit numeric(16,2); event_amount numeric(16,2);
DECLARE event_base_currency varchar(3); event_rate_id uuid; event_base_unit numeric(16,2); event_base_amount numeric(16,2);
DECLARE event_quantity numeric(12,4); event_description varchar(500); invoice_account_id uuid; invoice_currency varchar(3);
DECLARE catalogue_code varchar(50); catalogue_receivable varchar(50); catalogue_revenue varchar(50); catalogue_tax varchar(30);
BEGIN
    IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Posted invoice-line evidence is immutable; use a credit note or reversal'; END IF;
    SELECT status,student_finance_account_id,fee_catalogue_id,fee_rule_id,transaction_currency_code,
           transaction_unit_amount,transaction_amount,base_currency_code,exchange_rate_id,base_unit_amount,
           base_amount,quantity,description
      INTO event_status,event_account_id,event_catalogue_id,event_rule_id,event_currency,event_unit,event_amount,
           event_base_currency,event_rate_id,event_base_unit,event_base_amount,event_quantity,event_description
      FROM finance_billing_events WHERE id=NEW.billing_event_id;
    SELECT student_finance_account_id,transaction_currency_code INTO invoice_account_id,invoice_currency
      FROM finance_invoices WHERE id=NEW.invoice_id;
    SELECT code,receivable_account_code,revenue_account_code,tax_code
      INTO catalogue_code,catalogue_receivable,catalogue_revenue,catalogue_tax
      FROM finance_fee_catalogues WHERE id=NEW.fee_catalogue_id;
    IF event_status IS DISTINCT FROM 'APPROVED' OR event_account_id IS DISTINCT FROM invoice_account_id
        OR event_currency IS DISTINCT FROM invoice_currency THEN
        RAISE EXCEPTION 'Invoice line requires an approved billing event for the same account and currency';
    END IF;
    IF ROW(NEW.fee_catalogue_id,NEW.fee_rule_id,NEW.description,NEW.quantity,NEW.transaction_currency_code,
            NEW.transaction_unit_amount,NEW.transaction_amount,NEW.base_currency_code,NEW.exchange_rate_id,
            NEW.base_unit_amount,NEW.base_amount)
        IS DISTINCT FROM ROW(event_catalogue_id,event_rule_id,event_description,event_quantity,event_currency,
            event_unit,event_amount,event_base_currency,event_rate_id,event_base_unit,event_base_amount) THEN
        RAISE EXCEPTION 'Invoice line must preserve the exact approved billing-event price snapshot';
    END IF;
    IF ROW(NEW.fee_code,NEW.receivable_account_code,NEW.revenue_account_code,NEW.tax_code)
        IS DISTINCT FROM ROW(catalogue_code,catalogue_receivable,catalogue_revenue,catalogue_tax) THEN
        RAISE EXCEPTION 'Invoice line must preserve the fee-definition posting accounts';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_finance_invoice_line_governance BEFORE INSERT OR UPDATE OR DELETE ON finance_invoice_lines
    FOR EACH ROW EXECUTE FUNCTION enforce_finance_invoice_line_governance();

CREATE OR REPLACE FUNCTION validate_finance_posted_invoice() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE line_count integer; transaction_total numeric(16,2); base_total numeric(16,2); uninvoiced_count integer;
BEGIN
    SELECT count(*),coalesce(sum(transaction_amount),0),coalesce(sum(base_amount),0),
           count(*) FILTER (WHERE event_status<>'INVOICED')
      INTO line_count,transaction_total,base_total,uninvoiced_count
      FROM (SELECT line.transaction_amount,line.base_amount,event.status AS event_status
              FROM finance_invoice_lines line JOIN finance_billing_events event ON event.id=line.billing_event_id
             WHERE line.invoice_id=NEW.id) evidence;
    IF line_count=0 OR transaction_total IS DISTINCT FROM NEW.gross_transaction_amount
        OR base_total IS DISTINCT FROM NEW.gross_base_amount OR uninvoiced_count<>0 THEN
        RAISE EXCEPTION 'Posted invoice totals and billing-event evidence must reconcile exactly';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER trg_validate_finance_posted_invoice
    AFTER INSERT ON finance_invoices DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_finance_posted_invoice();

GRANT USAGE,SELECT ON SEQUENCE finance_billing_event_number_sequence,finance_invoice_number_sequence TO emhare_service;
GRANT SELECT,INSERT,UPDATE ON finance_billing_events TO emhare_service;
GRANT SELECT,INSERT ON finance_billing_event_scopes,finance_invoices,finance_invoice_lines TO emhare_service;
