-- Author: Tinashe K

ALTER TABLE application_types
    ADD COLUMN finance_fee_structure_id uuid,
    ADD COLUMN finance_fee_structure_code varchar(50),
    ADD COLUMN finance_fee_structure_name varchar(160);

ALTER TABLE application_types
    ADD CONSTRAINT ck_application_type_fee_structure_snapshot CHECK (
        (finance_fee_structure_id IS NULL AND finance_fee_structure_code IS NULL AND finance_fee_structure_name IS NULL)
        OR (finance_fee_structure_id IS NOT NULL AND finance_fee_structure_code IS NOT NULL AND finance_fee_structure_name IS NOT NULL)
    );

ALTER TABLE application_types_aud
    ADD COLUMN finance_fee_structure_id uuid,
    ADD COLUMN finance_fee_structure_code varchar(50),
    ADD COLUMN finance_fee_structure_name varchar(160);
