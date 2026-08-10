INSERT INTO permissions (
    id, code, name, category, description,
    created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version
) VALUES
    ('20000000-0000-4000-8000-000000000009', 'CORE_INSTITUTION_MANAGE', 'Manage institution profile', 'CORE', 'Maintain institution identity, contact, branding, and operational defaults.', now(), now(), null, null, null, null, 0),
    ('20000000-0000-4000-8000-000000000010', 'CORE_REFERENCE_MANAGE', 'Manage reference data', 'CORE', 'Maintain countries, lookup sets, and lookup values.', now(), now(), null, null, null, null, 0),
    ('20000000-0000-4000-8000-000000000011', 'CORE_AUDIT_READ', 'Read Core audit records', 'CORE', 'Inspect login history and Core security audit information.', now(), now(), null, null, null, null, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (
    id, role_id, permission_id,
    created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version
)
SELECT gen_random_uuid(), role.id, permission.id, now(), now(), null, null, null, null, 0
FROM roles role
CROSS JOIN permissions permission
WHERE role.code = 'SYSTEM_ADMIN'
  AND permission.code IN ('CORE_INSTITUTION_MANAGE', 'CORE_REFERENCE_MANAGE', 'CORE_AUDIT_READ')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO institution_profile (
    id, code, name, legal_name, default_currency_code, country_code, timezone,
    contact_details_json, branding_json, legacy_code,
    created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version
) VALUES (
    '30000000-0000-4000-8000-000000000001',
    'UZ',
    'University of Zimbabwe',
    'University of Zimbabwe',
    'USD',
    'ZW',
    'Africa/Harare',
    '{"email": "info@uz.ac.zw", "phone": "", "website": "https://www.uz.ac.zw"}'::jsonb,
    '{"primaryColor": "#20743a", "secondaryColor": "#f8b334", "documentHeader": "University of Zimbabwe"}'::jsonb,
    'UZ',
    now(), now(), null, null, null, null, 0
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO countries (
    id, iso2_code, iso3_code, name, nationality_name,
    created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version
) VALUES
    ('31000000-0000-4000-8000-000000000001', 'ZW', 'ZWE', 'Zimbabwe', 'Zimbabwean', now(), now(), null, null, null, null, 0),
    ('31000000-0000-4000-8000-000000000002', 'ZA', 'ZAF', 'South Africa', 'South African', now(), now(), null, null, null, null, 0),
    ('31000000-0000-4000-8000-000000000003', 'ZM', 'ZMB', 'Zambia', 'Zambian', now(), now(), null, null, null, null, 0),
    ('31000000-0000-4000-8000-000000000004', 'BW', 'BWA', 'Botswana', 'Motswana', now(), now(), null, null, null, null, 0),
    ('31000000-0000-4000-8000-000000000005', 'MW', 'MWI', 'Malawi', 'Malawian', now(), now(), null, null, null, null, 0)
ON CONFLICT (iso2_code) DO NOTHING;

INSERT INTO lookup_sets (
    id, code, name, description,
    created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version
) VALUES
    ('32000000-0000-4000-8000-000000000001', 'TITLES', 'Titles', 'Personal titles used by applicants, students, staff, and guardians.', now(), now(), null, null, null, null, 0),
    ('32000000-0000-4000-8000-000000000002', 'GENDERS', 'Genders', 'Gender options used where required by institutional processes.', now(), now(), null, null, null, null, 0),
    ('32000000-0000-4000-8000-000000000003', 'MARITAL_STATUSES', 'Marital statuses', 'Marital status options for person records.', now(), now(), null, null, null, null, 0),
    ('32000000-0000-4000-8000-000000000004', 'APPLICANT_CATEGORIES', 'Applicant categories', 'Applicant categories for admissions rules and reporting.', now(), now(), null, null, null, null, 0),
    ('32000000-0000-4000-8000-000000000005', 'DOCUMENT_TYPES', 'Document types', 'Reusable document classifications.', now(), now(), null, null, null, null, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO lookup_values (
    id, lookup_set_id, code, name, sort_order, is_active,
    created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version
)
SELECT gen_random_uuid(), lookup_set.id, value.code, value.name, value.sort_order, true,
       now(), now(), null, null, null, null, 0
FROM lookup_sets lookup_set
JOIN (VALUES
    ('TITLES', 'MR', 'Mr', 10),
    ('TITLES', 'MRS', 'Mrs', 20),
    ('TITLES', 'MS', 'Ms', 30),
    ('TITLES', 'DR', 'Dr', 40),
    ('GENDERS', 'FEMALE', 'Female', 10),
    ('GENDERS', 'MALE', 'Male', 20),
    ('GENDERS', 'OTHER', 'Other', 30),
    ('MARITAL_STATUSES', 'SINGLE', 'Single', 10),
    ('MARITAL_STATUSES', 'MARRIED', 'Married', 20),
    ('APPLICANT_CATEGORIES', 'LOCAL', 'Local', 10),
    ('APPLICANT_CATEGORIES', 'INTERNATIONAL', 'International', 20),
    ('APPLICANT_CATEGORIES', 'MATURE_ENTRY', 'Mature Entry', 30),
    ('DOCUMENT_TYPES', 'NATIONAL_ID', 'National ID', 10),
    ('DOCUMENT_TYPES', 'PASSPORT', 'Passport', 20),
    ('DOCUMENT_TYPES', 'QUALIFICATION_CERTIFICATE', 'Qualification Certificate', 30)
) AS value(set_code, code, name, sort_order) ON value.set_code = lookup_set.code
ON CONFLICT (lookup_set_id, code) DO NOTHING;
