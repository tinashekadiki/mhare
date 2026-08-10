-- Author: Tinashe K

CREATE TABLE finance_fee_structures (
    id uuid PRIMARY KEY,
    code varchar(50) NOT NULL,
    name varchar(160) NOT NULL,
    description varchar(1000),
    fee_context varchar(30) NOT NULL,
    scope_type varchar(30) NOT NULL,
    scope_reference_id uuid,
    scope_reference_code varchar(80),
    scope_reference_name varchar(200),
    academic_period_id uuid,
    academic_period_code varchar(80),
    academic_period_name varchar(200),
    programme_period_number integer,
    applicant_category_code varchar(80),
    transaction_currency_code varchar(3) NOT NULL,
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
    CONSTRAINT uk_finance_fee_structure_code UNIQUE(code),
    CONSTRAINT ck_finance_fee_structure_context CHECK (fee_context IN ('ACADEMIC','APPLICATION','ACCOMMODATION')),
    CONSTRAINT ck_finance_fee_structure_scope_type CHECK (scope_type IN ('INSTITUTION','ACADEMIC_UNIT','PROGRAMME','PROGRAMME_TYPE','GLOBAL')),
    CONSTRAINT ck_finance_fee_structure_currency CHECK (
        transaction_currency_code=upper(transaction_currency_code)
        AND transaction_currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_finance_fee_structure_effectivity CHECK (effective_until IS NULL OR effective_until>effective_from),
    CONSTRAINT ck_finance_fee_structure_programme_period CHECK (programme_period_number IS NULL OR programme_period_number>0),
    CONSTRAINT ck_finance_fee_structure_scope_reference CHECK (
        (scope_type IN ('INSTITUTION','GLOBAL') AND scope_reference_id IS NULL
            AND scope_reference_code IS NULL AND scope_reference_name IS NULL)
        OR (scope_type NOT IN ('INSTITUTION','GLOBAL')
            AND (scope_reference_id IS NOT NULL OR length(trim(scope_reference_code))>0)
            AND length(trim(scope_reference_name))>0)),
    CONSTRAINT ck_finance_fee_structure_context_scope CHECK (
        (fee_context='ACADEMIC' AND scope_type IN ('INSTITUTION','ACADEMIC_UNIT','PROGRAMME')
            AND academic_period_id IS NOT NULL AND length(trim(academic_period_name))>0
            AND applicant_category_code IS NULL)
        OR (fee_context='APPLICATION' AND scope_type='PROGRAMME_TYPE'
            AND academic_period_id IS NULL AND academic_period_code IS NULL AND academic_period_name IS NULL
            AND programme_period_number IS NULL)
        OR (fee_context='ACCOMMODATION' AND scope_type='GLOBAL'
            AND academic_period_id IS NULL AND academic_period_code IS NULL AND academic_period_name IS NULL
            AND programme_period_number IS NULL AND applicant_category_code IS NULL)),
    CONSTRAINT ck_finance_fee_structure_status CHECK (status IN ('DRAFT','ACTIVE','RETIRED')),
    CONSTRAINT ck_finance_fee_structure_workflow CHECK (
        (status='DRAFT' AND activated_by_user_id IS NULL AND activated_at IS NULL AND activation_reason IS NULL
            AND retired_by_user_id IS NULL AND retired_at IS NULL AND retirement_reason IS NULL)
        OR (status='ACTIVE' AND activated_by_user_id IS NOT NULL AND activated_at IS NOT NULL
            AND length(trim(activation_reason))>0 AND retired_by_user_id IS NULL AND retired_at IS NULL
            AND retirement_reason IS NULL)
        OR (status='RETIRED' AND activated_by_user_id IS NOT NULL AND activated_at IS NOT NULL
            AND length(trim(activation_reason))>0 AND retired_by_user_id IS NOT NULL AND retired_at IS NOT NULL
            AND length(trim(retirement_reason))>0)),
    CONSTRAINT ck_finance_fee_structure_actor_separation CHECK (
        activated_by_user_id IS NULL OR activated_by_user_id<>prepared_by_user_id)
);

CREATE INDEX idx_finance_fee_structure_resolution
    ON finance_fee_structures(fee_context,scope_type,academic_period_id,effective_from,effective_until)
    WHERE status='ACTIVE' AND deleted_at IS NULL;

ALTER TABLE finance_fee_rules
    ADD COLUMN fee_structure_id uuid REFERENCES finance_fee_structures(id),
    ADD COLUMN structure_line_number integer,
    ADD COLUMN structure_line_description varchar(500),
    ADD CONSTRAINT ck_finance_fee_rule_structure_line CHECK (
        (fee_structure_id IS NULL AND structure_line_number IS NULL AND structure_line_description IS NULL)
        OR (fee_structure_id IS NOT NULL AND structure_line_number>0
            AND length(trim(structure_line_description))>0));

CREATE UNIQUE INDEX uk_finance_fee_structure_line_number
    ON finance_fee_rules(fee_structure_id,structure_line_number)
    WHERE fee_structure_id IS NOT NULL AND deleted_at IS NULL;

ALTER TABLE finance_fee_rule_scopes DROP CONSTRAINT ck_finance_fee_rule_scope_dimension;
ALTER TABLE finance_fee_rule_scopes ADD CONSTRAINT ck_finance_fee_rule_scope_dimension CHECK (scope_dimension IN (
    'GLOBAL','INSTITUTION','ACADEMIC_UNIT','ACADEMIC_PERIOD','PROGRAMME_PERIOD','APPLICATION_TYPE',
    'PROGRAMME_TYPE','APPLICANT_CATEGORY','PROGRAMME','MODULE','ACCOMMODATION_TYPE','DINING_PLAN','GRADUATION'));

ALTER TABLE finance_fee_rules_aud
    ADD COLUMN fee_structure_id uuid,
    ADD COLUMN structure_line_number integer,
    ADD COLUMN structure_line_description varchar(500);

CREATE TABLE finance_fee_structures_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo(rev),
    revtype smallint,
    code varchar(50), name varchar(160), description varchar(1000), fee_context varchar(30), scope_type varchar(30),
    scope_reference_id uuid, scope_reference_code varchar(80), scope_reference_name varchar(200),
    academic_period_id uuid, academic_period_code varchar(80), academic_period_name varchar(200),
    programme_period_number integer, applicant_category_code varchar(80), transaction_currency_code varchar(3),
    effective_from timestamptz, effective_until timestamptz, status varchar(20), prepared_by_user_id uuid,
    activated_by_user_id uuid, activated_at timestamptz, activation_reason varchar(1000), retired_by_user_id uuid,
    retired_at timestamptz, retirement_reason varchar(1000), created_at timestamptz, updated_at timestamptz,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY(id,rev)
);

CREATE OR REPLACE FUNCTION enforce_finance_fee_structure_governance() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE conflicting_structure uuid;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Finance fee structure evidence cannot be deleted'; END IF;
    IF TG_OP='UPDATE' THEN
        IF OLD.status='RETIRED' THEN RAISE EXCEPTION 'Retired finance fee structure evidence is immutable'; END IF;
        IF OLD.status='ACTIVE' AND ROW(NEW.code,NEW.name,NEW.description,NEW.fee_context,NEW.scope_type,
                NEW.scope_reference_id,NEW.scope_reference_code,NEW.scope_reference_name,NEW.academic_period_id,
                NEW.academic_period_code,NEW.academic_period_name,NEW.programme_period_number,
                NEW.applicant_category_code,NEW.transaction_currency_code,NEW.effective_from,NEW.effective_until,
                NEW.prepared_by_user_id)
            IS DISTINCT FROM ROW(OLD.code,OLD.name,OLD.description,OLD.fee_context,OLD.scope_type,
                OLD.scope_reference_id,OLD.scope_reference_code,OLD.scope_reference_name,OLD.academic_period_id,
                OLD.academic_period_code,OLD.academic_period_name,OLD.programme_period_number,
                OLD.applicant_category_code,OLD.transaction_currency_code,OLD.effective_from,OLD.effective_until,
                OLD.prepared_by_user_id) THEN
            RAISE EXCEPTION 'Active finance fee structure definition is immutable';
        END IF;
        IF NOT ((OLD.status='DRAFT' AND NEW.status IN ('DRAFT','ACTIVE'))
            OR (OLD.status='ACTIVE' AND NEW.status IN ('ACTIVE','RETIRED'))) THEN
            RAISE EXCEPTION 'Invalid finance fee structure status transition';
        END IF;
    END IF;
    IF NEW.status='ACTIVE' AND (TG_OP='INSERT' OR OLD.status IS DISTINCT FROM 'ACTIVE') THEN
        SELECT id INTO conflicting_structure FROM finance_fee_structures
         WHERE id<>NEW.id AND status='ACTIVE' AND deleted_at IS NULL
           AND fee_context=NEW.fee_context AND scope_type=NEW.scope_type
           AND scope_reference_id IS NOT DISTINCT FROM NEW.scope_reference_id
           AND upper(coalesce(scope_reference_code,''))=upper(coalesce(NEW.scope_reference_code,''))
           AND academic_period_id IS NOT DISTINCT FROM NEW.academic_period_id
           AND programme_period_number IS NOT DISTINCT FROM NEW.programme_period_number
           AND upper(coalesce(applicant_category_code,''))=upper(coalesce(NEW.applicant_category_code,''))
           AND tstzrange(effective_from,coalesce(effective_until,'infinity'::timestamptz),'[)')
               && tstzrange(NEW.effective_from,coalesce(NEW.effective_until,'infinity'::timestamptz),'[)') LIMIT 1;
        IF conflicting_structure IS NOT NULL THEN
            RAISE EXCEPTION 'An active fee structure already covers this scope and effective window';
        END IF;
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_finance_fee_structure_governance
    BEFORE UPDATE OR DELETE ON finance_fee_structures
    FOR EACH ROW EXECUTE FUNCTION enforce_finance_fee_structure_governance();

GRANT SELECT,INSERT,UPDATE ON finance_fee_structures TO emhare_service;
