-- Author: Tinashe K

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM finance_student_discount_rules WHERE deleted_at IS NULL) THEN
        RAISE EXCEPTION 'Existing student discounts must be classified with programme level and study level before V18 can be applied';
    END IF;
END $$;

ALTER TABLE finance_student_discount_rules
    ADD COLUMN academic_unit_id uuid,
    ADD COLUMN academic_unit_code varchar(80),
    ADD COLUMN academic_unit_name varchar(200),
    ADD COLUMN programme_id uuid,
    ADD COLUMN programme_code varchar(80),
    ADD COLUMN programme_name varchar(200),
    ADD COLUMN programme_level_id uuid NOT NULL,
    ADD COLUMN programme_level_code varchar(80) NOT NULL,
    ADD COLUMN programme_level_name varchar(200) NOT NULL,
    ADD COLUMN programme_study_level varchar(20) NOT NULL;

ALTER TABLE finance_student_discount_rules
    ADD CONSTRAINT ck_finance_student_discount_academic_unit_snapshot CHECK (
        (academic_unit_id IS NULL AND academic_unit_code IS NULL AND academic_unit_name IS NULL)
        OR (academic_unit_id IS NOT NULL AND length(trim(academic_unit_code)) > 0
            AND academic_unit_code = upper(academic_unit_code) AND length(trim(academic_unit_name)) > 0)),
    ADD CONSTRAINT ck_finance_student_discount_programme_snapshot CHECK (
        (programme_id IS NULL AND programme_code IS NULL AND programme_name IS NULL)
        OR (programme_id IS NOT NULL AND length(trim(programme_code)) > 0
            AND programme_code = upper(programme_code) AND length(trim(programme_name)) > 0)),
    ADD CONSTRAINT ck_finance_student_discount_level CHECK (
        programme_level_code IN ('UG','PG') AND length(trim(programme_level_name)) > 0),
    ADD CONSTRAINT ck_finance_student_discount_study_level CHECK (
        programme_study_level ~ '^[1-9][0-9]*\.[1-9][0-9]*$'),
    ADD CONSTRAINT ck_finance_student_discount_explicit_scope CHECK (
        (programme_id IS NOT NULL AND scope_type = 'PROGRAMME'
            AND scope_reference_id = programme_id AND scope_reference_code = programme_code
            AND scope_reference_name = programme_name)
        OR (programme_id IS NULL AND academic_unit_id IS NOT NULL AND scope_type = 'ACADEMIC_UNIT'
            AND scope_reference_id = academic_unit_id AND scope_reference_code = academic_unit_code
            AND scope_reference_name = academic_unit_name)
        OR (programme_id IS NULL AND academic_unit_id IS NULL AND scope_type = 'INSTITUTION'
            AND scope_reference_id IS NULL AND scope_reference_code IS NULL AND scope_reference_name IS NULL));

ALTER TABLE finance_student_discount_rules_aud
    ADD COLUMN academic_unit_id uuid,
    ADD COLUMN academic_unit_code varchar(80),
    ADD COLUMN academic_unit_name varchar(200),
    ADD COLUMN programme_id uuid,
    ADD COLUMN programme_code varchar(80),
    ADD COLUMN programme_name varchar(200),
    ADD COLUMN programme_level_id uuid,
    ADD COLUMN programme_level_code varchar(80),
    ADD COLUMN programme_level_name varchar(200),
    ADD COLUMN programme_study_level varchar(20);

DROP INDEX idx_finance_student_discount_resolution;
CREATE INDEX idx_finance_student_discount_resolution
    ON finance_student_discount_rules (
        status, programme_level_id, programme_level_code, programme_study_level,
        programme_id, academic_unit_id, target_type, fee_catalogue_id, effective_from, effective_until)
    WHERE deleted_at IS NULL;

CREATE OR REPLACE FUNCTION enforce_finance_student_discount_governance() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE conflicting_discount_code varchar(50);
BEGIN
    IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'Student discount evidence cannot be deleted'; END IF;
    IF TG_OP = 'UPDATE' THEN
        IF OLD.status = 'RETIRED' THEN RAISE EXCEPTION 'Retired student discount evidence is immutable'; END IF;
        IF OLD.status = 'ACTIVE' AND ROW(
                NEW.code, NEW.name, NEW.scope_type, NEW.scope_reference_id, NEW.scope_reference_code,
                NEW.scope_reference_name, NEW.scope_depth, NEW.academic_unit_id, NEW.academic_unit_code,
                NEW.academic_unit_name, NEW.programme_id, NEW.programme_code, NEW.programme_name,
                NEW.programme_level_id, NEW.programme_level_code, NEW.programme_level_name,
                NEW.programme_study_level, NEW.target_type, NEW.fee_catalogue_id,
                NEW.discount_percentage, NEW.authority_reference, NEW.effective_from, NEW.effective_until,
                NEW.prepared_by_user_id)
            IS DISTINCT FROM ROW(
                OLD.code, OLD.name, OLD.scope_type, OLD.scope_reference_id, OLD.scope_reference_code,
                OLD.scope_reference_name, OLD.scope_depth, OLD.academic_unit_id, OLD.academic_unit_code,
                OLD.academic_unit_name, OLD.programme_id, OLD.programme_code, OLD.programme_name,
                OLD.programme_level_id, OLD.programme_level_code, OLD.programme_level_name,
                OLD.programme_study_level, OLD.target_type, OLD.fee_catalogue_id,
                OLD.discount_percentage, OLD.authority_reference, OLD.effective_from, OLD.effective_until,
                OLD.prepared_by_user_id) THEN
            RAISE EXCEPTION 'Active student discount definition is immutable';
        END IF;
        IF NOT ((OLD.status = 'DRAFT' AND NEW.status IN ('DRAFT','ACTIVE'))
            OR (OLD.status = 'ACTIVE' AND NEW.status IN ('ACTIVE','RETIRED'))) THEN
            RAISE EXCEPTION 'Invalid student discount status transition';
        END IF;
    END IF;

    IF NEW.status = 'ACTIVE' AND (TG_OP = 'INSERT' OR OLD.status <> 'ACTIVE') THEN
        SELECT existing.code INTO conflicting_discount_code
          FROM finance_student_discount_rules existing
         WHERE existing.id <> NEW.id
           AND existing.deleted_at IS NULL
           AND existing.status = 'ACTIVE'
           AND existing.programme_level_id = NEW.programme_level_id
           AND existing.programme_level_code = NEW.programme_level_code
           AND existing.programme_study_level = NEW.programme_study_level
           AND existing.target_type = NEW.target_type
           AND existing.fee_catalogue_id IS NOT DISTINCT FROM NEW.fee_catalogue_id
           AND existing.effective_from < COALESCE(NEW.effective_until, 'infinity'::timestamptz)
           AND NEW.effective_from < COALESCE(existing.effective_until, 'infinity'::timestamptz)
           AND (CASE WHEN existing.programme_id IS NOT NULL THEN 3
                     WHEN existing.academic_unit_id IS NOT NULL THEN 2 ELSE 1 END)
               = (CASE WHEN NEW.programme_id IS NOT NULL THEN 3
                       WHEN NEW.academic_unit_id IS NOT NULL THEN 2 ELSE 1 END)
           AND (
               (NEW.programme_id IS NOT NULL AND existing.programme_id = NEW.programme_id
                    AND (existing.academic_unit_id IS NULL OR NEW.academic_unit_id IS NULL
                         OR existing.academic_unit_id = NEW.academic_unit_id))
               OR (NEW.programme_id IS NULL AND NEW.academic_unit_id IS NOT NULL
                    AND existing.programme_id IS NULL AND existing.academic_unit_id = NEW.academic_unit_id)
               OR (NEW.programme_id IS NULL AND NEW.academic_unit_id IS NULL
                    AND existing.programme_id IS NULL AND existing.academic_unit_id IS NULL))
         LIMIT 1;
        IF conflicting_discount_code IS NOT NULL THEN
            RAISE EXCEPTION 'Student discount conflicts with active discount % at equal priority', conflicting_discount_code;
        END IF;
    END IF;
    RETURN NEW;
END $$;
