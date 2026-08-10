-- Author: Tinashe K

ALTER TABLE finance_fee_structures DROP CONSTRAINT ck_finance_fee_structure_context_scope;

UPDATE finance_fee_structures
   SET academic_period_id = NULL,
       academic_period_code = NULL,
       academic_period_name = NULL,
       programme_period_number = NULL,
       updated_at = now()
 WHERE fee_context = 'ACADEMIC'
   AND (academic_period_id IS NOT NULL
        OR academic_period_code IS NOT NULL
        OR academic_period_name IS NOT NULL
        OR programme_period_number IS NOT NULL);

ALTER TABLE finance_fee_structures ADD CONSTRAINT ck_finance_fee_structure_context_scope CHECK (
    (fee_context='ACADEMIC' AND scope_type IN ('INSTITUTION','ACADEMIC_UNIT','PROGRAMME')
        AND academic_period_id IS NULL AND academic_period_code IS NULL AND academic_period_name IS NULL
        AND programme_period_number IS NULL AND applicant_category_code IS NULL)
    OR (fee_context='APPLICATION' AND scope_type='PROGRAMME_TYPE'
        AND academic_period_id IS NULL AND academic_period_code IS NULL AND academic_period_name IS NULL
        AND programme_period_number IS NULL)
    OR (fee_context='ACCOMMODATION' AND scope_type='GLOBAL'
        AND academic_period_id IS NULL AND academic_period_code IS NULL AND academic_period_name IS NULL
        AND programme_period_number IS NULL AND applicant_category_code IS NULL)
);

DROP INDEX IF EXISTS idx_finance_fee_structure_resolution;
CREATE INDEX idx_finance_fee_structure_resolution
    ON finance_fee_structures(fee_context,scope_type,effective_from,effective_until)
    WHERE status='ACTIVE' AND deleted_at IS NULL;

CREATE TABLE finance_fee_structure_attachments (
    id uuid PRIMARY KEY,
    fee_structure_id uuid NOT NULL REFERENCES finance_fee_structures(id),
    programme_id uuid NOT NULL,
    programme_code varchar(80) NOT NULL,
    programme_name varchar(200) NOT NULL,
    academic_period_id uuid NOT NULL,
    academic_period_code varchar(80) NOT NULL,
    academic_period_name varchar(200) NOT NULL,
    programme_period_number integer NOT NULL,
    discount_type varchar(20),
    discount_value numeric(19,4),
    discount_reason varchar(500),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_finance_fee_structure_attachment_programme_period CHECK (programme_period_number>0),
    CONSTRAINT ck_finance_fee_structure_attachment_programme_code CHECK (
        programme_code=upper(programme_code) AND length(trim(programme_code))>0),
    CONSTRAINT ck_finance_fee_structure_attachment_period_code CHECK (
        academic_period_code=upper(academic_period_code) AND length(trim(academic_period_code))>0),
    CONSTRAINT ck_finance_fee_structure_attachment_discount CHECK (
        (discount_type IS NULL AND discount_value IS NULL AND discount_reason IS NULL)
        OR (discount_type='PERCENTAGE' AND discount_value>0 AND discount_value<=100
            AND length(trim(discount_reason))>0)
        OR (discount_type='AMOUNT' AND discount_value>0 AND length(trim(discount_reason))>0)),
    CONSTRAINT ck_finance_fee_structure_attachment_discount_type CHECK (
        discount_type IS NULL OR discount_type IN ('PERCENTAGE','AMOUNT'))
);

CREATE UNIQUE INDEX uk_finance_fee_structure_attachment_period
    ON finance_fee_structure_attachments(fee_structure_id,programme_id,academic_period_id,programme_period_number)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_finance_fee_structure_attachment_resolution
    ON finance_fee_structure_attachments(programme_id,academic_period_id,programme_period_number)
    WHERE deleted_at IS NULL;

CREATE TABLE finance_fee_structure_attachments_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo(rev),
    revtype smallint,
    fee_structure_id uuid,
    programme_id uuid,
    programme_code varchar(80),
    programme_name varchar(200),
    academic_period_id uuid,
    academic_period_code varchar(80),
    academic_period_name varchar(200),
    programme_period_number integer,
    discount_type varchar(20),
    discount_value numeric(19,4),
    discount_reason varchar(500),
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY(id,rev)
);

CREATE OR REPLACE FUNCTION enforce_finance_fee_structure_attachment_governance()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_context varchar(30);
DECLARE parent_status varchar(20);
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Finance fee structure attachment evidence cannot be deleted';
    END IF;

    SELECT fee_context,status INTO parent_context,parent_status
      FROM finance_fee_structures
     WHERE id=NEW.fee_structure_id AND deleted_at IS NULL;

    IF parent_context IS NULL THEN
        RAISE EXCEPTION 'Fee structure attachment requires an existing fee structure';
    END IF;
    IF parent_context<>'ACADEMIC' THEN
        RAISE EXCEPTION 'Only academic fee structures can have programme-period attachments';
    END IF;
    IF TG_OP='INSERT' AND parent_status<>'DRAFT' THEN
        RAISE EXCEPTION 'Programme-period attachments can only be added while the fee structure is draft';
    END IF;
    IF TG_OP='UPDATE' AND parent_status<>'DRAFT' THEN
        RAISE EXCEPTION 'Programme-period attachments are immutable after fee structure activation';
    END IF;

    RETURN NEW;
END $$;

CREATE TRIGGER trg_finance_fee_structure_attachment_governance
    BEFORE INSERT OR UPDATE OR DELETE ON finance_fee_structure_attachments
    FOR EACH ROW EXECUTE FUNCTION enforce_finance_fee_structure_attachment_governance();

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
           AND upper(coalesce(applicant_category_code,''))=upper(coalesce(NEW.applicant_category_code,''))
           AND tstzrange(effective_from,coalesce(effective_until,'infinity'::timestamptz),'[)')
               && tstzrange(NEW.effective_from,coalesce(NEW.effective_until,'infinity'::timestamptz),'[)') LIMIT 1;
        IF conflicting_structure IS NOT NULL THEN
            RAISE EXCEPTION 'An active fee structure already covers this scope and effective window';
        END IF;
    END IF;
    RETURN NEW;
END $$;

GRANT SELECT,INSERT,UPDATE ON finance_fee_structure_attachments TO emhare_service;
