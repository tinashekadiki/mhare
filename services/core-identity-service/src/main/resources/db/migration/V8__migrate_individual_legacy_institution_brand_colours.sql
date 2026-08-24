-- Author: Tinashe K

UPDATE institution_profile
SET branding_json = CASE
        WHEN LOWER(branding_json ->> 'primaryColor') = '#20743a'
            THEN jsonb_set(branding_json, '{primaryColor}', '"#001f6e"'::jsonb)
        ELSE branding_json
    END,
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE deleted_at IS NULL
  AND LOWER(branding_json ->> 'primaryColor') = '#20743a';

UPDATE institution_profile
SET branding_json = CASE
        WHEN LOWER(branding_json ->> 'secondaryColor') = '#f8b334'
            THEN jsonb_set(branding_json, '{secondaryColor}', '"#cb920e"'::jsonb)
        ELSE branding_json
    END,
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE deleted_at IS NULL
  AND LOWER(branding_json ->> 'secondaryColor') = '#f8b334';
