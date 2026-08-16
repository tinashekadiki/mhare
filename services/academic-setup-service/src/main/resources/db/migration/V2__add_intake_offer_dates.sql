ALTER TABLE intakes
    ADD COLUMN offer_acceptance_deadline timestamp with time zone,
    ADD COLUMN registration_date date,
    ADD COLUMN orientation_date date,
    ADD COLUMN commencement_date date;

ALTER TABLE intakes_aud
    ADD COLUMN offer_acceptance_deadline timestamp with time zone,
    ADD COLUMN registration_date date,
    ADD COLUMN orientation_date date,
    ADD COLUMN commencement_date date;

ALTER TABLE intakes
    ADD CONSTRAINT ck_intakes_registration_before_commencement
        CHECK (registration_date IS NULL OR commencement_date IS NULL OR registration_date <= commencement_date),
    ADD CONSTRAINT ck_intakes_orientation_before_commencement
        CHECK (orientation_date IS NULL OR commencement_date IS NULL OR orientation_date <= commencement_date);
