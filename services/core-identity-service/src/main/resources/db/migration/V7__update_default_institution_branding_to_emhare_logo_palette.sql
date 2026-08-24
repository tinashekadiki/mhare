-- Author: Tinashe K

UPDATE institution_profile
SET branding_json = jsonb_set(
        jsonb_set(branding_json, '{primaryColor}', '"#001f6e"'::jsonb),
        '{secondaryColor}',
        '"#cb920e"'::jsonb
    ),
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE deleted_at IS NULL
  AND LOWER(branding_json ->> 'primaryColor') = '#20743a'
  AND LOWER(branding_json ->> 'secondaryColor') = '#f8b334';
