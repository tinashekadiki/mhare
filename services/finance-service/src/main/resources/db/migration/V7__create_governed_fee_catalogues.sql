-- Author: Tinashe K

CREATE TABLE finance_fee_catalogues (
    id uuid PRIMARY KEY,
    code varchar(50) NOT NULL,
    name varchar(160) NOT NULL,
    description varchar(1000),
    charge_type varchar(30) NOT NULL,
    receivable_account_code varchar(50) NOT NULL,
    revenue_account_code varchar(50) NOT NULL,
    tax_code varchar(30),
    base_currency_code varchar(3) NOT NULL DEFAULT 'USD',
    status varchar(20) NOT NULL,
    prepared_by_user_id uuid NOT NULL,
    activated_by_user_id uuid,
    activated_at timestamptz,
    activation_reason varchar(1000),
    retired_by_user_id uuid,
    retired_at timestamptz,
    retirement_reason varchar(1000),
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_finance_fee_catalogue_charge_type CHECK (charge_type IN (
        'APPLICATION','PROGRAMME','MODULE','ACCOMMODATION','DINING','GRADUATION','OTHER')),
    CONSTRAINT ck_finance_fee_catalogue_currency CHECK (base_currency_code='USD'),
    CONSTRAINT ck_finance_fee_catalogue_status CHECK (status IN ('DRAFT','ACTIVE','RETIRED')),
    CONSTRAINT ck_finance_fee_catalogue_activation CHECK (
        (status='DRAFT' AND activated_by_user_id IS NULL AND activated_at IS NULL AND activation_reason IS NULL
            AND retired_by_user_id IS NULL AND retired_at IS NULL AND retirement_reason IS NULL)
        OR (status='ACTIVE' AND activated_by_user_id IS NOT NULL AND activated_at IS NOT NULL
            AND length(trim(activation_reason))>0 AND retired_by_user_id IS NULL AND retired_at IS NULL AND retirement_reason IS NULL)
        OR (status='RETIRED' AND activated_by_user_id IS NOT NULL AND activated_at IS NOT NULL
            AND length(trim(activation_reason))>0 AND retired_by_user_id IS NOT NULL AND retired_at IS NOT NULL
            AND length(trim(retirement_reason))>0)
    ),
    CONSTRAINT ck_finance_fee_catalogue_actor_separation CHECK (
        activated_by_user_id IS NULL OR activated_by_user_id<>prepared_by_user_id)
);
CREATE UNIQUE INDEX uk_finance_fee_catalogue_code ON finance_fee_catalogues(lower(code)) WHERE deleted_at IS NULL;
CREATE INDEX idx_finance_fee_catalogue_status ON finance_fee_catalogues(status,charge_type) WHERE deleted_at IS NULL;

CREATE TABLE finance_fee_rules (
    id uuid PRIMARY KEY,
    fee_catalogue_id uuid NOT NULL REFERENCES finance_fee_catalogues(id),
    rule_version integer NOT NULL,
    transaction_currency_code varchar(3) NOT NULL,
    transaction_amount numeric(14,2) NOT NULL,
    base_currency_code varchar(3) NOT NULL DEFAULT 'USD',
    exchange_rate_id uuid REFERENCES exchange_rates(id),
    base_amount numeric(14,2),
    rating_status varchar(20) NOT NULL,
    effective_from timestamptz NOT NULL,
    effective_until timestamptz,
    scope_signature text,
    status varchar(20) NOT NULL,
    prepared_by_user_id uuid NOT NULL,
    approved_by_user_id uuid,
    approved_at timestamptz,
    approval_reason varchar(1000),
    retired_by_user_id uuid,
    retired_at timestamptz,
    retirement_reason varchar(1000),
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT uk_finance_fee_rule_version UNIQUE(fee_catalogue_id,rule_version),
    CONSTRAINT ck_finance_fee_rule_version CHECK (rule_version>0),
    CONSTRAINT ck_finance_fee_rule_amount CHECK (transaction_amount>0),
    CONSTRAINT ck_finance_fee_rule_currency CHECK (
        transaction_currency_code=upper(transaction_currency_code)
        AND transaction_currency_code ~ '^[A-Z]{3}$' AND base_currency_code='USD'),
    CONSTRAINT ck_finance_fee_rule_effectivity CHECK (effective_until IS NULL OR effective_until>effective_from),
    CONSTRAINT ck_finance_fee_rule_rating_status CHECK (rating_status IN ('RATED','UNRATED')),
    CONSTRAINT ck_finance_fee_rule_rating CHECK (
        (transaction_currency_code='USD' AND exchange_rate_id IS NULL AND base_amount=transaction_amount AND rating_status='RATED')
        OR (transaction_currency_code<>'USD' AND exchange_rate_id IS NULL AND base_amount IS NULL AND rating_status='UNRATED')
        OR (transaction_currency_code<>'USD' AND exchange_rate_id IS NOT NULL AND base_amount IS NOT NULL AND rating_status='RATED')),
    CONSTRAINT ck_finance_fee_rule_status CHECK (status IN ('DRAFT','PENDING_RATE','APPROVED','RETIRED')),
    CONSTRAINT ck_finance_fee_rule_status_rating CHECK (
        (status='PENDING_RATE' AND rating_status='UNRATED') OR status<>'PENDING_RATE'),
    CONSTRAINT ck_finance_fee_rule_approval CHECK (
        (status IN ('DRAFT','PENDING_RATE') AND approved_by_user_id IS NULL AND approved_at IS NULL
            AND approval_reason IS NULL AND retired_by_user_id IS NULL AND retired_at IS NULL AND retirement_reason IS NULL)
        OR (status='APPROVED' AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL
            AND length(trim(approval_reason))>0 AND scope_signature IS NOT NULL
            AND retired_by_user_id IS NULL AND retired_at IS NULL AND retirement_reason IS NULL)
        OR (status='RETIRED' AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL
            AND length(trim(approval_reason))>0 AND scope_signature IS NOT NULL
            AND retired_by_user_id IS NOT NULL AND retired_at IS NOT NULL AND length(trim(retirement_reason))>0)
    ),
    CONSTRAINT ck_finance_fee_rule_actor_separation CHECK (
        approved_by_user_id IS NULL OR approved_by_user_id<>prepared_by_user_id)
);
CREATE INDEX idx_finance_fee_rule_effective_lookup
    ON finance_fee_rules(fee_catalogue_id,effective_from,effective_until) WHERE status='APPROVED' AND deleted_at IS NULL;
CREATE INDEX idx_finance_fee_rule_rating_queue
    ON finance_fee_rules(transaction_currency_code,effective_from) WHERE status='PENDING_RATE' AND deleted_at IS NULL;

CREATE TABLE finance_fee_rule_scopes (
    id uuid PRIMARY KEY,
    fee_rule_id uuid NOT NULL REFERENCES finance_fee_rules(id),
    scope_dimension varchar(40) NOT NULL,
    reference_id uuid,
    reference_code varchar(80),
    reference_name varchar(200),
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT uk_finance_fee_rule_scope_dimension UNIQUE(fee_rule_id,scope_dimension),
    CONSTRAINT ck_finance_fee_rule_scope_dimension CHECK (scope_dimension IN (
        'GLOBAL','ACADEMIC_PERIOD','APPLICATION_TYPE','APPLICANT_CATEGORY','PROGRAMME','MODULE',
        'ACCOMMODATION_TYPE','DINING_PLAN','GRADUATION')),
    CONSTRAINT ck_finance_fee_rule_scope_reference CHECK (
        (scope_dimension='GLOBAL' AND reference_id IS NULL AND reference_code IS NULL AND reference_name IS NULL)
        OR (scope_dimension<>'GLOBAL' AND (reference_id IS NOT NULL OR length(trim(reference_code))>0)
            AND length(trim(reference_name))>0))
);
CREATE INDEX idx_finance_fee_rule_scope_lookup
    ON finance_fee_rule_scopes(scope_dimension,reference_id,reference_code) WHERE deleted_at IS NULL;

CREATE TABLE finance_fee_catalogues_aud (
    id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,
    code varchar(50),name varchar(160),description varchar(1000),charge_type varchar(30),
    receivable_account_code varchar(50),revenue_account_code varchar(50),tax_code varchar(30),base_currency_code varchar(3),
    status varchar(20),prepared_by_user_id uuid,activated_by_user_id uuid,activated_at timestamptz,
    activation_reason varchar(1000),retired_by_user_id uuid,retired_at timestamptz,retirement_reason varchar(1000),
    created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,
    deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev)
);
CREATE TABLE finance_fee_rules_aud (
    id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,
    fee_catalogue_id uuid,rule_version integer,transaction_currency_code varchar(3),transaction_amount numeric(14,2),
    base_currency_code varchar(3),exchange_rate_id uuid,base_amount numeric(14,2),rating_status varchar(20),
    effective_from timestamptz,effective_until timestamptz,scope_signature text,status varchar(20),
    prepared_by_user_id uuid,approved_by_user_id uuid,approved_at timestamptz,approval_reason varchar(1000),
    retired_by_user_id uuid,retired_at timestamptz,retirement_reason varchar(1000),created_at timestamptz,
    updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,
    deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev)
);
CREATE TABLE finance_fee_rule_scopes_aud (
    id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,fee_rule_id uuid,
    scope_dimension varchar(40),reference_id uuid,reference_code varchar(80),reference_name varchar(200),
    created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,
    deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev)
);

CREATE OR REPLACE FUNCTION enforce_finance_fee_catalogue_governance() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Finance fee catalogue evidence cannot be deleted'; END IF;
    IF TG_OP='UPDATE' THEN
        IF OLD.status='RETIRED' THEN RAISE EXCEPTION 'Retired finance fee catalogue evidence is immutable'; END IF;
        IF OLD.status='ACTIVE' AND ROW(NEW.code,NEW.name,NEW.description,NEW.charge_type,NEW.receivable_account_code,
                NEW.revenue_account_code,NEW.tax_code,NEW.base_currency_code,NEW.prepared_by_user_id)
            IS DISTINCT FROM ROW(OLD.code,OLD.name,OLD.description,OLD.charge_type,OLD.receivable_account_code,
                OLD.revenue_account_code,OLD.tax_code,OLD.base_currency_code,OLD.prepared_by_user_id) THEN
            RAISE EXCEPTION 'Active finance fee catalogue definition is immutable';
        END IF;
        IF NOT ((OLD.status='DRAFT' AND NEW.status IN ('DRAFT','ACTIVE'))
            OR (OLD.status='ACTIVE' AND NEW.status IN ('ACTIVE','RETIRED'))) THEN
            RAISE EXCEPTION 'Invalid finance fee catalogue status transition';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_finance_fee_catalogue_governance BEFORE UPDATE OR DELETE ON finance_fee_catalogues
    FOR EACH ROW EXECUTE FUNCTION enforce_finance_fee_catalogue_governance();

CREATE OR REPLACE FUNCTION enforce_finance_fee_rule_scope_governance() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE rule_status varchar(20);
BEGIN
    SELECT status INTO rule_status FROM finance_fee_rules WHERE id=COALESCE(NEW.fee_rule_id,OLD.fee_rule_id);
    IF rule_status NOT IN ('DRAFT','PENDING_RATE') THEN
        RAISE EXCEPTION 'Approved finance fee rule scope evidence is immutable';
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END $$;
CREATE TRIGGER trg_finance_fee_rule_scope_governance BEFORE INSERT OR UPDATE OR DELETE ON finance_fee_rule_scopes
    FOR EACH ROW EXECUTE FUNCTION enforce_finance_fee_rule_scope_governance();

CREATE OR REPLACE FUNCTION enforce_finance_fee_rule_governance() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE catalogue_status varchar(20); canonical_scope text; conflicting_rule uuid;
DECLARE rate_currency varchar(3); rate_base varchar(3); rate_status varchar(20); rate_from timestamptz; rate_until timestamptz;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Finance fee rule evidence cannot be deleted'; END IF;
    IF TG_OP='UPDATE' THEN
        IF OLD.status='RETIRED' THEN RAISE EXCEPTION 'Retired finance fee rule evidence is immutable'; END IF;
        IF OLD.status='APPROVED' AND ROW(NEW.fee_catalogue_id,NEW.rule_version,NEW.transaction_currency_code,
                NEW.transaction_amount,NEW.base_currency_code,NEW.exchange_rate_id,NEW.base_amount,NEW.rating_status,
                NEW.effective_from,NEW.effective_until,NEW.scope_signature,NEW.prepared_by_user_id)
            IS DISTINCT FROM ROW(OLD.fee_catalogue_id,OLD.rule_version,OLD.transaction_currency_code,
                OLD.transaction_amount,OLD.base_currency_code,OLD.exchange_rate_id,OLD.base_amount,OLD.rating_status,
                OLD.effective_from,OLD.effective_until,OLD.scope_signature,OLD.prepared_by_user_id) THEN
            RAISE EXCEPTION 'Approved finance fee rule pricing evidence is immutable';
        END IF;
        IF NOT ((OLD.status IN ('DRAFT','PENDING_RATE') AND NEW.status IN ('DRAFT','PENDING_RATE','APPROVED'))
            OR (OLD.status='APPROVED' AND NEW.status IN ('APPROVED','RETIRED'))) THEN
            RAISE EXCEPTION 'Invalid finance fee rule status transition';
        END IF;
    END IF;
    IF NEW.exchange_rate_id IS NOT NULL THEN
        SELECT source_currency_code,base_currency_code,status,effective_from,effective_to
          INTO rate_currency,rate_base,rate_status,rate_from,rate_until FROM exchange_rates WHERE id=NEW.exchange_rate_id;
        IF rate_currency IS DISTINCT FROM NEW.transaction_currency_code OR rate_base IS DISTINCT FROM 'USD'
            OR rate_status IS DISTINCT FROM 'ACTIVE' OR rate_from>NEW.effective_from
            OR (rate_until IS NOT NULL AND rate_until<=NEW.effective_from) THEN
            RAISE EXCEPTION 'Fee rule exchange rate must be active and effective for its transaction currency and start time';
        END IF;
    END IF;
    IF NEW.status='APPROVED' AND (TG_OP='INSERT' OR OLD.status IS DISTINCT FROM 'APPROVED') THEN
        IF NEW.rating_status<>'RATED' THEN RAISE EXCEPTION 'Unrated fee rules cannot be approved for billing'; END IF;
        SELECT status INTO catalogue_status FROM finance_fee_catalogues WHERE id=NEW.fee_catalogue_id AND deleted_at IS NULL;
        IF catalogue_status IS DISTINCT FROM 'ACTIVE' THEN RAISE EXCEPTION 'Fee rule catalogue must be active before rule approval'; END IF;
        SELECT string_agg(scope_dimension||':'||coalesce(reference_id::text,upper(reference_code),'*'),'|' ORDER BY scope_dimension)
          INTO canonical_scope FROM finance_fee_rule_scopes WHERE fee_rule_id=NEW.id AND deleted_at IS NULL;
        IF canonical_scope IS NULL THEN RAISE EXCEPTION 'Fee rule requires at least one explicit scope before approval'; END IF;
        NEW.scope_signature=canonical_scope;
        SELECT id INTO conflicting_rule FROM finance_fee_rules
         WHERE fee_catalogue_id=NEW.fee_catalogue_id AND status='APPROVED' AND deleted_at IS NULL AND id<>NEW.id
           AND scope_signature=canonical_scope
           AND tstzrange(effective_from,coalesce(effective_until,'infinity'::timestamptz),'[)')
               && tstzrange(NEW.effective_from,coalesce(NEW.effective_until,'infinity'::timestamptz),'[)') LIMIT 1;
        IF conflicting_rule IS NOT NULL THEN RAISE EXCEPTION 'Approved fee rule overlaps another rule for the same catalogue and scope'; END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_finance_fee_rule_governance BEFORE INSERT OR UPDATE OR DELETE ON finance_fee_rules
    FOR EACH ROW EXECUTE FUNCTION enforce_finance_fee_rule_governance();

GRANT SELECT,INSERT,UPDATE ON finance_fee_catalogues,finance_fee_rules,finance_fee_rule_scopes TO emhare_service;
