-- Author: Tinashe K

ALTER TABLE assessment_roster_entries DROP CONSTRAINT ck_assessment_roster_module_type;
ALTER TABLE assessment_roster_entries ADD CONSTRAINT ck_assessment_roster_module_type
    CHECK (curriculum_module_type IN ('COMPULSORY', 'ELECTIVE', 'OPTIONAL'));
