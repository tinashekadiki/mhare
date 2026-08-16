-- Author: Tinashe K

ALTER TABLE institution_profile
    ADD COLUMN registrar_name varchar(200);

ALTER TABLE institution_profile_aud
    ADD COLUMN registrar_name varchar(200);

UPDATE institution_profile
SET registrar_name = COALESCE(
        NULLIF(BTRIM(branding_json ->> 'offerLetterSignatoryName'), ''),
        'Registrar'
    )
WHERE registrar_name IS NULL;

ALTER TABLE institution_profile
    ALTER COLUMN registrar_name SET NOT NULL;
