-- Author: Tinashe K

CREATE TABLE finance_student_discount_rules (
    id uuid PRIMARY KEY,
    code varchar(50) NOT NULL,
    name varchar(160) NOT NULL,
    scope_type varchar(30) NOT NULL,
    scope_reference_id uuid,
    scope_reference_code varchar(80),
    scope_reference_name varchar(200),
    scope_depth integer NOT NULL DEFAULT 0,
    target_type varchar(30) NOT NULL,
    fee_catalogue_id uuid REFERENCES finance_fee_catalogues(id),
    academic_period_id uuid,
    academic_period_code varchar(80),
    academic_period_name varchar(200),
    programme_period_number integer,
    discount_percentage numeric(7,4) NOT NULL,
    authority_reference varchar(500) NOT NULL,
    effective_from timestamptz NOT NULL,
    effective_until timestamptz,
    status varchar(20) NOT NULL,
    prepared_by_user_id uuid NOT NULL,
    activated_by_user_id uuid,
    activated_at timestamptz,
    activation_reason varchar(1000),
    retired_by_user_id uuid,
    retired_at timestamptz,
    retirement_reason varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_finance_student_discount_scope CHECK (scope_type IN ('INSTITUTION','ACADEMIC_UNIT','PROGRAMME')),
    CONSTRAINT ck_finance_student_discount_scope_reference CHECK (
        (scope_type='INSTITUTION' AND scope_reference_id IS NULL AND scope_reference_code IS NULL
            AND scope_reference_name IS NULL AND scope_depth=0)
        OR (scope_type IN ('ACADEMIC_UNIT','PROGRAMME') AND scope_reference_id IS NOT NULL
            AND length(trim(scope_reference_code))>0 AND length(trim(scope_reference_name))>0 AND scope_depth>0)),
    CONSTRAINT ck_finance_student_discount_target CHECK (
        (target_type='ALL_FEES' AND fee_catalogue_id IS NULL)
        OR (target_type='FEE_LINE' AND fee_catalogue_id IS NOT NULL)),
    CONSTRAINT ck_finance_student_discount_period CHECK (
        (academic_period_id IS NULL AND academic_period_code IS NULL AND academic_period_name IS NULL)
        OR (academic_period_id IS NOT NULL AND length(trim(academic_period_code))>0
            AND length(trim(academic_period_name))>0)),
    CONSTRAINT ck_finance_student_discount_programme_period CHECK (
        programme_period_number IS NULL OR programme_period_number>0),
    CONSTRAINT ck_finance_student_discount_percentage CHECK (
        discount_percentage>0 AND discount_percentage<100),
    CONSTRAINT ck_finance_student_discount_authority CHECK (length(trim(authority_reference))>0),
    CONSTRAINT ck_finance_student_discount_effectivity CHECK (
        effective_until IS NULL OR effective_until>effective_from),
    CONSTRAINT ck_finance_student_discount_status CHECK (status IN ('DRAFT','ACTIVE','RETIRED')),
    CONSTRAINT ck_finance_student_discount_workflow CHECK (
        (status='DRAFT' AND activated_by_user_id IS NULL AND activated_at IS NULL AND activation_reason IS NULL
            AND retired_by_user_id IS NULL AND retired_at IS NULL AND retirement_reason IS NULL)
        OR (status='ACTIVE' AND activated_by_user_id IS NOT NULL AND activated_at IS NOT NULL
            AND length(trim(activation_reason))>0 AND retired_by_user_id IS NULL AND retired_at IS NULL
            AND retirement_reason IS NULL)
        OR (status='RETIRED' AND activated_by_user_id IS NOT NULL AND activated_at IS NOT NULL
            AND length(trim(activation_reason))>0 AND retired_by_user_id IS NOT NULL AND retired_at IS NOT NULL
            AND length(trim(retirement_reason))>0)),
    CONSTRAINT ck_finance_student_discount_actor_separation CHECK (
        activated_by_user_id IS NULL OR activated_by_user_id<>prepared_by_user_id)
);

CREATE UNIQUE INDEX uk_finance_student_discount_code
    ON finance_student_discount_rules(lower(code)) WHERE deleted_at IS NULL;
CREATE INDEX idx_finance_student_discount_resolution
    ON finance_student_discount_rules(status,effective_from,effective_until,scope_type,target_type)
    WHERE deleted_at IS NULL;

CREATE TABLE finance_student_discount_rule_programmes (
    id uuid PRIMARY KEY,
    discount_rule_id uuid NOT NULL REFERENCES finance_student_discount_rules(id),
    programme_id uuid NOT NULL,
    programme_code varchar(80) NOT NULL,
    programme_name varchar(200) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_finance_student_discount_programme_code CHECK (
        programme_code=upper(programme_code) AND length(trim(programme_code))>0),
    CONSTRAINT ck_finance_student_discount_programme_name CHECK (length(trim(programme_name))>0)
);
CREATE UNIQUE INDEX uk_finance_student_discount_rule_programme
    ON finance_student_discount_rule_programmes(discount_rule_id,programme_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_finance_student_discount_programme_resolution
    ON finance_student_discount_rule_programmes(programme_id,discount_rule_id) WHERE deleted_at IS NULL;

CREATE TABLE finance_student_discount_rules_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint,
    code varchar(50), name varchar(160), scope_type varchar(30), scope_reference_id uuid,
    scope_reference_code varchar(80), scope_reference_name varchar(200), scope_depth integer,
    target_type varchar(30), fee_catalogue_id uuid, academic_period_id uuid, academic_period_code varchar(80),
    academic_period_name varchar(200), programme_period_number integer, discount_percentage numeric(7,4),
    authority_reference varchar(500), effective_from timestamptz, effective_until timestamptz, status varchar(20),
    prepared_by_user_id uuid, activated_by_user_id uuid, activated_at timestamptz, activation_reason varchar(1000),
    retired_by_user_id uuid, retired_at timestamptz, retirement_reason varchar(1000), created_at timestamptz,
    updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev)
);
CREATE TABLE finance_student_discount_rule_programmes_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint,
    discount_rule_id uuid, programme_id uuid, programme_code varchar(80), programme_name varchar(200),
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev)
);

-- Preserve existing attachment discounts as standalone programme rules.
INSERT INTO finance_student_discount_rules (
    id,code,name,scope_type,scope_reference_id,scope_reference_code,scope_reference_name,scope_depth,
    target_type,fee_catalogue_id,academic_period_id,academic_period_code,academic_period_name,
    programme_period_number,discount_percentage,authority_reference,effective_from,effective_until,status,
    prepared_by_user_id,activated_by_user_id,activated_at,activation_reason,retired_by_user_id,retired_at,
    retirement_reason,created_at,updated_at,created_by_user_id,modified_by_user_id,deleted_at,deleted_by_user_id,version)
SELECT attachment.id,
       'MIG-'||left(structure.code,15)||'-'||left(attachment.programme_code,10)||'-'||left(replace(attachment.id::text,'-',''),12),
       left(attachment.programme_name||' attachment discount',160),
       'PROGRAMME',attachment.programme_id,attachment.programme_code,attachment.programme_name,1000,
       'ALL_FEES',NULL,attachment.academic_period_id,attachment.academic_period_code,attachment.academic_period_name,
       attachment.programme_period_number,
       CASE WHEN attachment.discount_type='PERCENTAGE' THEN attachment.discount_value
            ELSE least(99.9999,round((attachment.discount_value / NULLIF((SELECT sum(rule.transaction_amount)
                 FROM finance_fee_rules rule WHERE rule.fee_structure_id=structure.id AND rule.deleted_at IS NULL),0))*100,4))
       END,
       attachment.discount_reason,structure.effective_from,structure.effective_until,
       CASE WHEN structure.status='ACTIVE' THEN 'ACTIVE' ELSE 'DRAFT' END,
       structure.prepared_by_user_id,
       CASE WHEN structure.status='ACTIVE' THEN structure.activated_by_user_id END,
       CASE WHEN structure.status='ACTIVE' THEN structure.activated_at END,
       CASE WHEN structure.status='ACTIVE' THEN 'Migrated from active fee-structure attachment.' END,
       NULL,NULL,NULL,attachment.created_at,attachment.updated_at,attachment.created_by_user_id,
       attachment.modified_by_user_id,NULL,NULL,attachment.version
  FROM finance_fee_structure_attachments attachment
  JOIN finance_fee_structures structure ON structure.id=attachment.fee_structure_id
 WHERE attachment.deleted_at IS NULL AND attachment.discount_type IS NOT NULL;

INSERT INTO finance_student_discount_rule_programmes (
    id,discount_rule_id,programme_id,programme_code,programme_name,created_at,updated_at,
    created_by_user_id,modified_by_user_id,deleted_at,deleted_by_user_id,version)
SELECT gen_random_uuid(),attachment.id,attachment.programme_id,attachment.programme_code,attachment.programme_name,
       attachment.created_at,attachment.updated_at,attachment.created_by_user_id,attachment.modified_by_user_id,
       NULL,NULL,0
  FROM finance_fee_structure_attachments attachment
 WHERE attachment.deleted_at IS NULL AND attachment.discount_type IS NOT NULL;

CREATE OR REPLACE FUNCTION enforce_finance_student_discount_governance() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Student discount evidence cannot be deleted'; END IF;
    IF TG_OP='UPDATE' THEN
        IF OLD.status='RETIRED' THEN RAISE EXCEPTION 'Retired student discount evidence is immutable'; END IF;
        IF OLD.status='ACTIVE' AND ROW(NEW.code,NEW.name,NEW.scope_type,NEW.scope_reference_id,
                NEW.scope_reference_code,NEW.scope_reference_name,NEW.scope_depth,NEW.target_type,
                NEW.fee_catalogue_id,NEW.academic_period_id,NEW.academic_period_code,NEW.academic_period_name,
                NEW.programme_period_number,NEW.discount_percentage,NEW.authority_reference,
                NEW.effective_from,NEW.effective_until,NEW.prepared_by_user_id)
            IS DISTINCT FROM ROW(OLD.code,OLD.name,OLD.scope_type,OLD.scope_reference_id,
                OLD.scope_reference_code,OLD.scope_reference_name,OLD.scope_depth,OLD.target_type,
                OLD.fee_catalogue_id,OLD.academic_period_id,OLD.academic_period_code,OLD.academic_period_name,
                OLD.programme_period_number,OLD.discount_percentage,OLD.authority_reference,
                OLD.effective_from,OLD.effective_until,OLD.prepared_by_user_id) THEN
            RAISE EXCEPTION 'Active student discount definition is immutable';
        END IF;
        IF NOT ((OLD.status='DRAFT' AND NEW.status IN ('DRAFT','ACTIVE'))
            OR (OLD.status='ACTIVE' AND NEW.status IN ('ACTIVE','RETIRED'))) THEN
            RAISE EXCEPTION 'Invalid student discount status transition';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_finance_student_discount_governance
    BEFORE UPDATE OR DELETE ON finance_student_discount_rules
    FOR EACH ROW EXECUTE FUNCTION enforce_finance_student_discount_governance();

CREATE OR REPLACE FUNCTION enforce_finance_student_discount_programme_governance() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE rule_status varchar(20);
BEGIN
    SELECT status INTO rule_status FROM finance_student_discount_rules
     WHERE id=COALESCE(NEW.discount_rule_id,OLD.discount_rule_id);
    IF rule_status<>'DRAFT' THEN
        RAISE EXCEPTION 'Discount programme applicability is immutable after activation';
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_finance_student_discount_programme_governance
    BEFORE INSERT OR UPDATE OR DELETE ON finance_student_discount_rule_programmes
    FOR EACH ROW EXECUTE FUNCTION enforce_finance_student_discount_programme_governance();

-- Retain legacy attachment evidence but direct all new discount configuration to the standalone register.
CREATE OR REPLACE FUNCTION enforce_finance_fee_structure_attachment_governance() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Fee-structure attachments are legacy evidence; configure student discounts in the standalone discount register';
END $$;

ALTER TABLE finance_billing_event_scopes DROP CONSTRAINT ck_finance_billing_event_scope_dimension;
ALTER TABLE finance_billing_event_scopes ADD CONSTRAINT ck_finance_billing_event_scope_dimension CHECK (scope_dimension IN (
    'GLOBAL','INSTITUTION','ACADEMIC_UNIT','ACADEMIC_PERIOD','PROGRAMME_PERIOD','APPLICATION_TYPE',
    'PROGRAMME_TYPE','APPLICANT_CATEGORY','PROGRAMME','MODULE','ACCOMMODATION_TYPE','DINING_PLAN','GRADUATION'));

ALTER TABLE finance_billing_events
    ADD COLUMN discount_rule_id uuid REFERENCES finance_student_discount_rules(id),
    ADD COLUMN discount_rule_code varchar(50),
    ADD COLUMN discount_percentage numeric(7,4),
    ADD COLUMN gross_transaction_amount numeric(16,2),
    ADD COLUMN transaction_discount_amount numeric(16,2) NOT NULL DEFAULT 0,
    ADD COLUMN gross_base_amount numeric(16,2),
    ADD COLUMN base_discount_amount numeric(16,2) NOT NULL DEFAULT 0;
UPDATE finance_billing_events SET gross_transaction_amount=transaction_amount,gross_base_amount=base_amount;
ALTER TABLE finance_billing_events ALTER COLUMN gross_transaction_amount SET NOT NULL;
ALTER TABLE finance_billing_events ALTER COLUMN gross_base_amount SET NOT NULL;
ALTER TABLE finance_billing_events DROP CONSTRAINT ck_finance_billing_event_amounts;
ALTER TABLE finance_billing_events ADD CONSTRAINT ck_finance_billing_event_amounts CHECK (
    quantity>0 AND transaction_unit_amount>0 AND gross_transaction_amount=round(transaction_unit_amount*quantity,2)
    AND transaction_discount_amount>=0 AND transaction_amount=gross_transaction_amount-transaction_discount_amount
    AND base_unit_amount>0 AND gross_base_amount=round(base_unit_amount*quantity,2)
    AND base_discount_amount>=0 AND base_amount=gross_base_amount-base_discount_amount
    AND transaction_amount>0 AND base_amount>0);
ALTER TABLE finance_billing_events ADD CONSTRAINT ck_finance_billing_event_discount CHECK (
    (discount_rule_id IS NULL AND discount_rule_code IS NULL AND discount_percentage IS NULL
        AND transaction_discount_amount=0 AND base_discount_amount=0)
    OR (discount_rule_id IS NOT NULL AND length(trim(discount_rule_code))>0
        AND discount_percentage>0 AND discount_percentage<100
        AND transaction_discount_amount=round(gross_transaction_amount*discount_percentage/100,2)
        AND base_discount_amount=round(gross_base_amount*discount_percentage/100,2)));

ALTER TABLE finance_billing_events_aud
    ADD COLUMN discount_rule_id uuid, ADD COLUMN discount_rule_code varchar(50),
    ADD COLUMN discount_percentage numeric(7,4), ADD COLUMN gross_transaction_amount numeric(16,2),
    ADD COLUMN transaction_discount_amount numeric(16,2), ADD COLUMN gross_base_amount numeric(16,2),
    ADD COLUMN base_discount_amount numeric(16,2);

ALTER TABLE finance_invoice_lines
    ADD COLUMN discount_rule_id uuid REFERENCES finance_student_discount_rules(id),
    ADD COLUMN discount_rule_code varchar(50), ADD COLUMN discount_percentage numeric(7,4),
    ADD COLUMN gross_transaction_amount numeric(16,2),
    ADD COLUMN transaction_discount_amount numeric(16,2) NOT NULL DEFAULT 0,
    ADD COLUMN gross_base_amount numeric(16,2), ADD COLUMN base_discount_amount numeric(16,2) NOT NULL DEFAULT 0;
UPDATE finance_invoice_lines SET gross_transaction_amount=transaction_amount,gross_base_amount=base_amount;
ALTER TABLE finance_invoice_lines ALTER COLUMN gross_transaction_amount SET NOT NULL;
ALTER TABLE finance_invoice_lines ALTER COLUMN gross_base_amount SET NOT NULL;
ALTER TABLE finance_invoice_lines DROP CONSTRAINT ck_finance_invoice_line_amounts;
ALTER TABLE finance_invoice_lines ADD CONSTRAINT ck_finance_invoice_line_amounts CHECK (
    quantity>0 AND transaction_unit_amount>0 AND gross_transaction_amount=round(transaction_unit_amount*quantity,2)
    AND transaction_amount=gross_transaction_amount-transaction_discount_amount AND transaction_amount>0
    AND base_unit_amount>0 AND gross_base_amount=round(base_unit_amount*quantity,2)
    AND base_amount=gross_base_amount-base_discount_amount AND base_amount>0);
ALTER TABLE finance_invoice_lines_aud
    ADD COLUMN discount_rule_id uuid, ADD COLUMN discount_rule_code varchar(50),
    ADD COLUMN discount_percentage numeric(7,4), ADD COLUMN gross_transaction_amount numeric(16,2),
    ADD COLUMN transaction_discount_amount numeric(16,2), ADD COLUMN gross_base_amount numeric(16,2),
    ADD COLUMN base_discount_amount numeric(16,2);

ALTER TABLE finance_invoices
    ADD COLUMN transaction_discount_amount numeric(16,2) NOT NULL DEFAULT 0,
    ADD COLUMN net_transaction_amount numeric(16,2),
    ADD COLUMN base_discount_amount numeric(16,2) NOT NULL DEFAULT 0,
    ADD COLUMN net_base_amount numeric(16,2);
UPDATE finance_invoices SET net_transaction_amount=gross_transaction_amount,net_base_amount=gross_base_amount;
ALTER TABLE finance_invoices ALTER COLUMN net_transaction_amount SET NOT NULL;
ALTER TABLE finance_invoices ALTER COLUMN net_base_amount SET NOT NULL;
ALTER TABLE finance_invoices DROP CONSTRAINT ck_finance_invoice_amounts;
ALTER TABLE finance_invoices ADD CONSTRAINT ck_finance_invoice_amounts CHECK (
    gross_transaction_amount>0 AND transaction_discount_amount>=0
    AND net_transaction_amount=gross_transaction_amount-transaction_discount_amount AND net_transaction_amount>0
    AND gross_base_amount>0 AND base_discount_amount>=0
    AND net_base_amount=gross_base_amount-base_discount_amount AND net_base_amount>0);
ALTER TABLE finance_invoices_aud
    ADD COLUMN transaction_discount_amount numeric(16,2), ADD COLUMN net_transaction_amount numeric(16,2),
    ADD COLUMN base_discount_amount numeric(16,2), ADD COLUMN net_base_amount numeric(16,2);

CREATE OR REPLACE FUNCTION enforce_finance_billing_event_discount_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE rule_status varchar(20); rule_code varchar(50); rule_percentage numeric(7,4);
BEGIN
    IF TG_OP='INSERT' THEN
        NEW.gross_transaction_amount=coalesce(NEW.gross_transaction_amount,NEW.transaction_amount);
        NEW.gross_base_amount=coalesce(NEW.gross_base_amount,NEW.base_amount);
        NEW.transaction_discount_amount=coalesce(NEW.transaction_discount_amount,0);
        NEW.base_discount_amount=coalesce(NEW.base_discount_amount,0);
    END IF;
    IF TG_OP='UPDATE' AND ROW(NEW.discount_rule_id,NEW.discount_rule_code,NEW.discount_percentage,
            NEW.gross_transaction_amount,NEW.transaction_discount_amount,NEW.gross_base_amount,NEW.base_discount_amount)
        IS DISTINCT FROM ROW(OLD.discount_rule_id,OLD.discount_rule_code,OLD.discount_percentage,
            OLD.gross_transaction_amount,OLD.transaction_discount_amount,OLD.gross_base_amount,OLD.base_discount_amount) THEN
        RAISE EXCEPTION 'Submitted billing-event discount evidence is immutable';
    END IF;
    IF TG_OP='INSERT' AND NEW.discount_rule_id IS NOT NULL THEN
        SELECT status,code,discount_percentage INTO rule_status,rule_code,rule_percentage
          FROM finance_student_discount_rules WHERE id=NEW.discount_rule_id AND deleted_at IS NULL;
        IF rule_status IS DISTINCT FROM 'ACTIVE' OR rule_code IS DISTINCT FROM NEW.discount_rule_code
            OR rule_percentage IS DISTINCT FROM NEW.discount_percentage THEN
            RAISE EXCEPTION 'Billing discount snapshot must equal one active student discount rule';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_finance_billing_event_discount_evidence
    BEFORE INSERT OR UPDATE ON finance_billing_events
    FOR EACH ROW EXECUTE FUNCTION enforce_finance_billing_event_discount_evidence();

CREATE OR REPLACE FUNCTION normalize_finance_invoice_discount_totals() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.transaction_discount_amount=coalesce(NEW.transaction_discount_amount,0);
    NEW.base_discount_amount=coalesce(NEW.base_discount_amount,0);
    NEW.net_transaction_amount=coalesce(NEW.net_transaction_amount,NEW.gross_transaction_amount-NEW.transaction_discount_amount);
    NEW.net_base_amount=coalesce(NEW.net_base_amount,NEW.gross_base_amount-NEW.base_discount_amount);
    RETURN NEW;
END $$;
CREATE TRIGGER trg_finance_invoice_discount_totals
    BEFORE INSERT ON finance_invoices
    FOR EACH ROW EXECUTE FUNCTION normalize_finance_invoice_discount_totals();

CREATE OR REPLACE FUNCTION enforce_finance_invoice_line_governance() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE event_status varchar(30); event_account_id uuid; invoice_account_id uuid; invoice_currency varchar(3);
DECLARE event_record finance_billing_events%ROWTYPE; catalogue_record finance_fee_catalogues%ROWTYPE;
BEGIN
    IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Posted invoice-line evidence is immutable; use a credit note or reversal'; END IF;
    SELECT * INTO event_record FROM finance_billing_events WHERE id=NEW.billing_event_id;
    SELECT student_finance_account_id,transaction_currency_code INTO invoice_account_id,invoice_currency
      FROM finance_invoices WHERE id=NEW.invoice_id;
    SELECT * INTO catalogue_record FROM finance_fee_catalogues WHERE id=NEW.fee_catalogue_id;
    NEW.gross_transaction_amount=coalesce(NEW.gross_transaction_amount,event_record.gross_transaction_amount);
    NEW.transaction_discount_amount=coalesce(NEW.transaction_discount_amount,event_record.transaction_discount_amount);
    NEW.gross_base_amount=coalesce(NEW.gross_base_amount,event_record.gross_base_amount);
    NEW.base_discount_amount=coalesce(NEW.base_discount_amount,event_record.base_discount_amount);
    NEW.discount_rule_id=coalesce(NEW.discount_rule_id,event_record.discount_rule_id);
    NEW.discount_rule_code=coalesce(NEW.discount_rule_code,event_record.discount_rule_code);
    NEW.discount_percentage=coalesce(NEW.discount_percentage,event_record.discount_percentage);
    IF event_record.status IS DISTINCT FROM 'APPROVED'
        OR event_record.student_finance_account_id IS DISTINCT FROM invoice_account_id
        OR event_record.transaction_currency_code IS DISTINCT FROM invoice_currency THEN
        RAISE EXCEPTION 'Invoice line requires an approved billing event for the same account and currency';
    END IF;
    IF ROW(NEW.fee_catalogue_id,NEW.fee_rule_id,NEW.description,NEW.quantity,NEW.transaction_currency_code,
            NEW.transaction_unit_amount,NEW.gross_transaction_amount,NEW.transaction_discount_amount,
            NEW.transaction_amount,NEW.base_currency_code,NEW.exchange_rate_id,NEW.base_unit_amount,
            NEW.gross_base_amount,NEW.base_discount_amount,NEW.base_amount,NEW.discount_rule_id,
            NEW.discount_rule_code,NEW.discount_percentage)
        IS DISTINCT FROM ROW(event_record.fee_catalogue_id,event_record.fee_rule_id,event_record.description,
            event_record.quantity,event_record.transaction_currency_code,event_record.transaction_unit_amount,
            event_record.gross_transaction_amount,event_record.transaction_discount_amount,event_record.transaction_amount,
            event_record.base_currency_code,event_record.exchange_rate_id,event_record.base_unit_amount,
            event_record.gross_base_amount,event_record.base_discount_amount,event_record.base_amount,
            event_record.discount_rule_id,event_record.discount_rule_code,event_record.discount_percentage) THEN
        RAISE EXCEPTION 'Invoice line must preserve the exact approved billing-event price and discount snapshot';
    END IF;
    IF ROW(NEW.fee_code,NEW.receivable_account_code,NEW.revenue_account_code,NEW.tax_code)
        IS DISTINCT FROM ROW(catalogue_record.code,catalogue_record.receivable_account_code,
            catalogue_record.revenue_account_code,catalogue_record.tax_code) THEN
        RAISE EXCEPTION 'Invoice line must preserve the fee-definition posting accounts';
    END IF;
    RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION validate_finance_posted_invoice() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE line_count integer; gross_transaction_total numeric(16,2); transaction_discount_total numeric(16,2);
DECLARE net_transaction_total numeric(16,2); gross_base_total numeric(16,2); base_discount_total numeric(16,2);
DECLARE net_base_total numeric(16,2); uninvoiced_count integer;
BEGIN
    SELECT count(*),coalesce(sum(line.gross_transaction_amount),0),coalesce(sum(line.transaction_discount_amount),0),
           coalesce(sum(line.transaction_amount),0),coalesce(sum(line.gross_base_amount),0),
           coalesce(sum(line.base_discount_amount),0),coalesce(sum(line.base_amount),0),
           count(*) FILTER (WHERE event.status<>'INVOICED')
      INTO line_count,gross_transaction_total,transaction_discount_total,net_transaction_total,
           gross_base_total,base_discount_total,net_base_total,uninvoiced_count
      FROM finance_invoice_lines line JOIN finance_billing_events event ON event.id=line.billing_event_id
     WHERE line.invoice_id=NEW.id;
    IF line_count=0 OR gross_transaction_total IS DISTINCT FROM NEW.gross_transaction_amount
        OR transaction_discount_total IS DISTINCT FROM NEW.transaction_discount_amount
        OR net_transaction_total IS DISTINCT FROM NEW.net_transaction_amount
        OR gross_base_total IS DISTINCT FROM NEW.gross_base_amount
        OR base_discount_total IS DISTINCT FROM NEW.base_discount_amount
        OR net_base_total IS DISTINCT FROM NEW.net_base_amount OR uninvoiced_count<>0 THEN
        RAISE EXCEPTION 'Posted invoice totals and billing-event discount evidence must reconcile exactly';
    END IF;
    RETURN NULL;
END $$;

GRANT SELECT,INSERT,UPDATE ON finance_student_discount_rules,finance_student_discount_rule_programmes TO emhare_service;
