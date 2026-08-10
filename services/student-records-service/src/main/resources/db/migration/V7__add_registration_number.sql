-- Author: Tinashe K

CREATE SEQUENCE registration_number_sequence
    START WITH 1
    INCREMENT BY 1
    CACHE 20;

ALTER TABLE registration_sessions
    ADD COLUMN registration_number varchar(50);

UPDATE registration_sessions
    SET registration_number = 'REG-' || lpad(nextval('registration_number_sequence')::text, 8, '0')
    WHERE registration_number IS NULL;

ALTER TABLE registration_sessions
    ALTER COLUMN registration_number SET NOT NULL,
    ADD CONSTRAINT uk_registration_sessions_number_programme
        UNIQUE (registration_number, programme_version_id);

ALTER TABLE registration_sessions_aud
    ADD COLUMN registration_number varchar(50);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    registration_sessions,
    registration_sessions_aud
TO emhare_service;
