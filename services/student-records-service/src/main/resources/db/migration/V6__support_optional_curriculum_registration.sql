-- Author: Tinashe K

ALTER TABLE registration_modules DROP CONSTRAINT ck_registration_modules_type;
ALTER TABLE registration_modules ADD CONSTRAINT ck_registration_modules_type
    CHECK (curriculum_module_type IN ('COMPULSORY', 'ELECTIVE', 'OPTIONAL'));
