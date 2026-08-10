ALTER TABLE intakes
    ADD COLUMN maximum_programme_choices integer NOT NULL DEFAULT 3,
    ADD CONSTRAINT ck_intakes_maximum_programme_choices
        CHECK (maximum_programme_choices BETWEEN 1 AND 20);

ALTER TABLE intakes_aud
    ADD COLUMN maximum_programme_choices integer;
