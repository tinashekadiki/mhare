-- Author: Tinashe K

-- Programme level codes are the Finance-owned cross-service snapshot. Use the
-- code for overlap governance so a legacy structure without an Academic Setup
-- UUID still conflicts with a new structure for the same UG or PG level.
CREATE OR REPLACE FUNCTION enforce_finance_fee_structure_governance() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE conflicting_structure uuid;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Finance fee structure evidence cannot be deleted'; END IF;
    IF TG_OP='UPDATE' THEN
        IF OLD.status='RETIRED' THEN RAISE EXCEPTION 'Retired finance fee structure evidence is immutable'; END IF;
        IF OLD.status='ACTIVE' AND ROW(NEW.code,NEW.name,NEW.description,NEW.fee_context,NEW.scope_type,
                NEW.scope_reference_id,NEW.scope_reference_code,NEW.scope_reference_name,
                NEW.programme_level_id,NEW.programme_level_code,NEW.programme_level_name,
                NEW.academic_period_id,NEW.academic_period_code,NEW.academic_period_name,
                NEW.programme_period_number,NEW.applicant_category_code,NEW.transaction_currency_code,
                NEW.effective_from,NEW.effective_until,NEW.prepared_by_user_id)
            IS DISTINCT FROM ROW(OLD.code,OLD.name,OLD.description,OLD.fee_context,OLD.scope_type,
                OLD.scope_reference_id,OLD.scope_reference_code,OLD.scope_reference_name,
                OLD.programme_level_id,OLD.programme_level_code,OLD.programme_level_name,
                OLD.academic_period_id,OLD.academic_period_code,OLD.academic_period_name,
                OLD.programme_period_number,OLD.applicant_category_code,OLD.transaction_currency_code,
                OLD.effective_from,OLD.effective_until,OLD.prepared_by_user_id) THEN
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
           AND upper(programme_level_code)=upper(NEW.programme_level_code)
           AND scope_reference_id IS NOT DISTINCT FROM NEW.scope_reference_id
           AND upper(coalesce(scope_reference_code,''))=upper(coalesce(NEW.scope_reference_code,''))
           AND upper(coalesce(applicant_category_code,''))=upper(coalesce(NEW.applicant_category_code,''))
           AND tstzrange(effective_from,coalesce(effective_until,'infinity'::timestamptz),'[)')
               && tstzrange(NEW.effective_from,coalesce(NEW.effective_until,'infinity'::timestamptz),'[)') LIMIT 1;
        IF conflicting_structure IS NOT NULL THEN
            RAISE EXCEPTION 'An active fee structure already covers this programme level, scope, and effective window';
        END IF;
    END IF;
    RETURN NEW;
END $$;
