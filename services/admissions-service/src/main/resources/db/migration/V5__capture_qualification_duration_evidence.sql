-- Author: Tinashe K
ALTER TABLE applicant_qualification_sittings
    ADD COLUMN duration_months integer;

ALTER TABLE applicant_qualification_sittings
    ADD CONSTRAINT ck_applicant_qualification_sitting_duration
        CHECK (duration_months IS NULL OR duration_months > 0);

ALTER TABLE applicant_qualification_sittings_aud
    ADD COLUMN duration_months integer;
