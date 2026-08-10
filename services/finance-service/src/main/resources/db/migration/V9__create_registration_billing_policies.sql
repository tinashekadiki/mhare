-- Author: Tinashe K

CREATE TABLE finance_billing_policies (
    id uuid PRIMARY KEY,
    code varchar(50) NOT NULL,
    policy_version integer NOT NULL,
    name varchar(160) NOT NULL,
    source_event_type varchar(160) NOT NULL,
    fee_catalogue_id uuid NOT NULL REFERENCES finance_fee_catalogues(id),
    line_basis varchar(40) NOT NULL,
    quantity_basis varchar(40) NOT NULL,
    fixed_quantity numeric(12,4),
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
    CONSTRAINT uk_finance_billing_policy_version UNIQUE(code,policy_version),
    CONSTRAINT ck_finance_billing_policy_version CHECK (policy_version>0),
    CONSTRAINT ck_finance_billing_policy_source CHECK (length(trim(source_event_type))>0),
    CONSTRAINT ck_finance_billing_policy_line_basis CHECK (line_basis IN ('REGISTRATION','REGISTERED_MODULE')),
    CONSTRAINT ck_finance_billing_policy_quantity_basis CHECK (quantity_basis IN ('FIXED','MODULE_CREDIT_VALUE')),
    CONSTRAINT ck_finance_billing_policy_quantity CHECK (
        (quantity_basis='FIXED' AND fixed_quantity IS NOT NULL AND fixed_quantity>0)
        OR (quantity_basis='MODULE_CREDIT_VALUE' AND fixed_quantity IS NULL AND line_basis='REGISTERED_MODULE')),
    CONSTRAINT ck_finance_billing_policy_effectivity CHECK (effective_until IS NULL OR effective_until>effective_from),
    CONSTRAINT ck_finance_billing_policy_status CHECK (status IN ('DRAFT','ACTIVE','RETIRED')),
    CONSTRAINT ck_finance_billing_policy_workflow CHECK (
        (status='DRAFT' AND activated_by_user_id IS NULL AND activated_at IS NULL AND activation_reason IS NULL
            AND retired_by_user_id IS NULL AND retired_at IS NULL AND retirement_reason IS NULL)
        OR (status='ACTIVE' AND activated_by_user_id IS NOT NULL AND activated_at IS NOT NULL
            AND length(trim(activation_reason))>0 AND retired_by_user_id IS NULL AND retired_at IS NULL AND retirement_reason IS NULL)
        OR (status='RETIRED' AND activated_by_user_id IS NOT NULL AND activated_at IS NOT NULL
            AND length(trim(activation_reason))>0 AND retired_by_user_id IS NOT NULL AND retired_at IS NOT NULL
            AND length(trim(retirement_reason))>0)),
    CONSTRAINT ck_finance_billing_policy_actor_separation CHECK (
        activated_by_user_id IS NULL OR activated_by_user_id<>prepared_by_user_id)
);
CREATE INDEX idx_finance_billing_policy_source_effective
    ON finance_billing_policies(source_event_type,effective_from,effective_until)
    WHERE status='ACTIVE' AND deleted_at IS NULL;

CREATE TABLE finance_billing_policies_aud (
    id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,code varchar(50),
    policy_version integer,name varchar(160),source_event_type varchar(160),fee_catalogue_id uuid,
    line_basis varchar(40),quantity_basis varchar(40),fixed_quantity numeric(12,4),effective_from timestamptz,
    effective_until timestamptz,status varchar(20),prepared_by_user_id uuid,activated_by_user_id uuid,
    activated_at timestamptz,activation_reason varchar(1000),retired_by_user_id uuid,retired_at timestamptz,
    retirement_reason varchar(1000),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,
    modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev)
);

CREATE OR REPLACE FUNCTION enforce_finance_billing_policy_governance() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE catalogue_status varchar(20); conflicting_policy uuid;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Billing-policy evidence cannot be deleted'; END IF;
    IF TG_OP='UPDATE' THEN
        IF OLD.status='RETIRED' THEN RAISE EXCEPTION 'Retired billing-policy evidence is immutable'; END IF;
        IF OLD.status='ACTIVE' AND ROW(NEW.code,NEW.policy_version,NEW.name,NEW.source_event_type,NEW.fee_catalogue_id,
                NEW.line_basis,NEW.quantity_basis,NEW.fixed_quantity,NEW.effective_from,NEW.effective_until,NEW.prepared_by_user_id)
            IS DISTINCT FROM ROW(OLD.code,OLD.policy_version,OLD.name,OLD.source_event_type,OLD.fee_catalogue_id,
                OLD.line_basis,OLD.quantity_basis,OLD.fixed_quantity,OLD.effective_from,OLD.effective_until,OLD.prepared_by_user_id) THEN
            RAISE EXCEPTION 'Active billing-policy definition is immutable';
        END IF;
        IF NOT ((OLD.status='DRAFT' AND NEW.status IN ('DRAFT','ACTIVE'))
            OR (OLD.status='ACTIVE' AND NEW.status IN ('ACTIVE','RETIRED'))) THEN
            RAISE EXCEPTION 'Invalid billing-policy status transition';
        END IF;
    END IF;
    IF NEW.status='ACTIVE' AND (TG_OP='INSERT' OR OLD.status IS DISTINCT FROM 'ACTIVE') THEN
        SELECT status INTO catalogue_status FROM finance_fee_catalogues WHERE id=NEW.fee_catalogue_id AND deleted_at IS NULL;
        IF catalogue_status IS DISTINCT FROM 'ACTIVE' THEN RAISE EXCEPTION 'Billing policy requires an active fee definition'; END IF;
        SELECT id INTO conflicting_policy FROM finance_billing_policies
         WHERE id<>NEW.id AND source_event_type=NEW.source_event_type AND fee_catalogue_id=NEW.fee_catalogue_id
           AND line_basis=NEW.line_basis AND status='ACTIVE' AND deleted_at IS NULL
           AND tstzrange(effective_from,coalesce(effective_until,'infinity'::timestamptz),'[)')
               && tstzrange(NEW.effective_from,coalesce(NEW.effective_until,'infinity'::timestamptz),'[)') LIMIT 1;
        IF conflicting_policy IS NOT NULL THEN RAISE EXCEPTION 'Active billing policy overlaps another policy for the same source, fee, and line basis'; END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_finance_billing_policy_governance BEFORE INSERT OR UPDATE OR DELETE ON finance_billing_policies
    FOR EACH ROW EXECUTE FUNCTION enforce_finance_billing_policy_governance();

GRANT SELECT,INSERT,UPDATE ON finance_billing_policies TO emhare_service;
