-- Author: Tinashe K

CREATE TABLE finance_student_discount_rule_programme_periods (
    id uuid PRIMARY KEY,
    discount_rule_programme_id uuid NOT NULL REFERENCES finance_student_discount_rule_programmes(id),
    programme_period_number integer NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_finance_student_discount_programme_period_number CHECK (programme_period_number > 0)
);

CREATE UNIQUE INDEX uk_finance_student_discount_programme_period
    ON finance_student_discount_rule_programme_periods(discount_rule_programme_id, programme_period_number)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_finance_student_discount_programme_period_resolution
    ON finance_student_discount_rule_programme_periods(discount_rule_programme_id, programme_period_number)
    WHERE deleted_at IS NULL;

CREATE TABLE finance_student_discount_rule_programme_periods_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo(rev),
    revtype smallint,
    discount_rule_programme_id uuid,
    programme_period_number integer,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY(id, rev)
);

-- Preserve the former rule-wide programme period as a programme-specific selection.
INSERT INTO finance_student_discount_rule_programme_periods (
    id, discount_rule_programme_id, programme_period_number, created_at, updated_at,
    created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version)
SELECT gen_random_uuid(), programme.id, discount_rule.programme_period_number,
       programme.created_at, programme.updated_at, programme.created_by_user_id,
       programme.modified_by_user_id, NULL, NULL, 0
  FROM finance_student_discount_rule_programmes programme
  JOIN finance_student_discount_rules discount_rule ON discount_rule.id = programme.discount_rule_id
 WHERE programme.deleted_at IS NULL
   AND discount_rule.deleted_at IS NULL
   AND discount_rule.programme_period_number IS NOT NULL;

CREATE OR REPLACE FUNCTION enforce_finance_student_discount_programme_period_governance()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE rule_status varchar(20);
BEGIN
    SELECT discount_rule.status INTO rule_status
      FROM finance_student_discount_rule_programmes programme
      JOIN finance_student_discount_rules discount_rule ON discount_rule.id = programme.discount_rule_id
     WHERE programme.id = COALESCE(NEW.discount_rule_programme_id, OLD.discount_rule_programme_id);
    IF rule_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Discount programme-period applicability is immutable after activation';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_finance_student_discount_programme_period_governance
    BEFORE INSERT OR UPDATE OR DELETE ON finance_student_discount_rule_programme_periods
    FOR EACH ROW EXECUTE FUNCTION enforce_finance_student_discount_programme_period_governance();
