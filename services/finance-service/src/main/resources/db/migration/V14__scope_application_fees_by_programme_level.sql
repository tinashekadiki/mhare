-- Author: Tinashe K

ALTER TABLE finance_fee_structures DROP CONSTRAINT ck_finance_fee_structure_scope_type;
ALTER TABLE finance_fee_structures ADD CONSTRAINT ck_finance_fee_structure_scope_type CHECK (scope_type IN (
    'INSTITUTION','ACADEMIC_UNIT','PROGRAMME','PROGRAMME_LEVEL','PROGRAMME_TYPE','GLOBAL'));

ALTER TABLE finance_fee_structures DROP CONSTRAINT ck_finance_fee_structure_context_scope;
ALTER TABLE finance_fee_structures ADD CONSTRAINT ck_finance_fee_structure_context_scope CHECK (
    (fee_context='ACADEMIC' AND scope_type IN ('INSTITUTION','ACADEMIC_UNIT','PROGRAMME')
        AND academic_period_id IS NULL AND academic_period_code IS NULL AND academic_period_name IS NULL
        AND programme_period_number IS NULL AND applicant_category_code IS NULL)
    OR (fee_context='APPLICATION' AND scope_type IN ('PROGRAMME_LEVEL','PROGRAMME_TYPE')
        AND academic_period_id IS NULL AND academic_period_code IS NULL AND academic_period_name IS NULL
        AND programme_period_number IS NULL)
    OR (fee_context='ACCOMMODATION' AND scope_type='GLOBAL'
        AND academic_period_id IS NULL AND academic_period_code IS NULL AND academic_period_name IS NULL
        AND programme_period_number IS NULL AND applicant_category_code IS NULL)
);

ALTER TABLE finance_fee_structures ADD CONSTRAINT ck_finance_fee_structure_applicant_category CHECK (
    applicant_category_code IS NULL
    OR applicant_category_code IN ('LOCAL','SADC','INTERNATIONAL','CLE'));

ALTER TABLE finance_fee_rule_scopes DROP CONSTRAINT ck_finance_fee_rule_scope_dimension;
ALTER TABLE finance_fee_rule_scopes ADD CONSTRAINT ck_finance_fee_rule_scope_dimension CHECK (scope_dimension IN (
    'GLOBAL','INSTITUTION','ACADEMIC_UNIT','ACADEMIC_PERIOD','PROGRAMME_PERIOD','APPLICATION_TYPE',
    'PROGRAMME_LEVEL','PROGRAMME_TYPE','APPLICANT_CATEGORY','PROGRAMME','MODULE','ACCOMMODATION_TYPE',
    'DINING_PLAN','GRADUATION'));

ALTER TABLE finance_billing_event_scopes DROP CONSTRAINT ck_finance_billing_event_scope_dimension;
ALTER TABLE finance_billing_event_scopes ADD CONSTRAINT ck_finance_billing_event_scope_dimension CHECK (scope_dimension IN (
    'GLOBAL','INSTITUTION','ACADEMIC_UNIT','ACADEMIC_PERIOD','PROGRAMME_PERIOD','APPLICATION_TYPE',
    'PROGRAMME_LEVEL','PROGRAMME_TYPE','APPLICANT_CATEGORY','PROGRAMME','MODULE','ACCOMMODATION_TYPE',
    'DINING_PLAN','GRADUATION'));
