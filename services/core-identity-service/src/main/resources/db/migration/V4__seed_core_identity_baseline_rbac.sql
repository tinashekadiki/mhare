INSERT INTO roles (
    id, code, name, scope, is_system_managed,
    created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version
) VALUES
    ('10000000-0000-4000-8000-000000000001', 'SYSTEM_ADMIN', 'System Admin', 'SYSTEM', true, now(), now(), null, null, null, null, 0),
    ('10000000-0000-4000-8000-000000000002', 'ADMISSIONS_OFFICER', 'Admissions Officer', 'SYSTEM', true, now(), now(), null, null, null, null, 0),
    ('10000000-0000-4000-8000-000000000003', 'FINANCE_OFFICER', 'Finance Officer', 'SYSTEM', true, now(), now(), null, null, null, null, 0),
    ('10000000-0000-4000-8000-000000000004', 'APPLICANT', 'Applicant', 'SYSTEM', true, now(), now(), null, null, null, null, 0),
    ('10000000-0000-4000-8000-000000000005', 'STUDENT', 'Student', 'SYSTEM', true, now(), now(), null, null, null, null, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (
    id, code, name, category, description,
    created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version
) VALUES
    ('20000000-0000-4000-8000-000000000001', 'CORE_USER_MANAGE', 'Manage users', 'CORE', 'Create, update, disable, and inspect platform users.', now(), now(), null, null, null, null, 0),
    ('20000000-0000-4000-8000-000000000002', 'CORE_ROLE_MANAGE', 'Manage roles', 'CORE', 'Create roles and grant permissions to roles.', now(), now(), null, null, null, null, 0),
    ('20000000-0000-4000-8000-000000000003', 'CORE_PERMISSION_MANAGE', 'Manage permissions', 'CORE', 'Inspect and maintain permission catalogue entries.', now(), now(), null, null, null, null, 0),
    ('20000000-0000-4000-8000-000000000004', 'CORE_ROLE_ASSIGN', 'Assign roles', 'CORE', 'Assign and expire role assignments for users.', now(), now(), null, null, null, null, 0),
    ('20000000-0000-4000-8000-000000000005', 'ADMISSIONS_APPLICATION_APPLY', 'Apply for admission', 'ADMISSIONS', 'Create and maintain an owned applicant application.', now(), now(), null, null, null, null, 0),
    ('20000000-0000-4000-8000-000000000006', 'ADMISSIONS_APPLICATION_REVIEW', 'Review applications', 'ADMISSIONS', 'Review, verify, and progress submitted applications.', now(), now(), null, null, null, null, 0),
    ('20000000-0000-4000-8000-000000000007', 'ADMISSIONS_SETUP_MANAGE', 'Manage admissions setup', 'ADMISSIONS', 'Maintain cycles, application types, fees, subjects, and requirements.', now(), now(), null, null, null, null, 0),
    ('20000000-0000-4000-8000-000000000008', 'ADMISSIONS_PAYMENT_OVERRIDE', 'Override application fee gate', 'ADMISSIONS', 'Record authorised application fee waivers or overrides.', now(), now(), null, null, null, null, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (
    id, role_id, permission_id,
    created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version
)
SELECT gen_random_uuid(), role.id, permission.id, now(), now(), null, null, null, null, 0
FROM roles role
CROSS JOIN permissions permission
WHERE role.code = 'SYSTEM_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (
    id, role_id, permission_id,
    created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version
)
SELECT gen_random_uuid(), role.id, permission.id, now(), now(), null, null, null, null, 0
FROM roles role
JOIN permissions permission ON permission.code IN ('ADMISSIONS_APPLICATION_REVIEW', 'ADMISSIONS_SETUP_MANAGE', 'ADMISSIONS_PAYMENT_OVERRIDE')
WHERE role.code = 'ADMISSIONS_OFFICER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (
    id, role_id, permission_id,
    created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version
)
SELECT gen_random_uuid(), role.id, permission.id, now(), now(), null, null, null, null, 0
FROM roles role
JOIN permissions permission ON permission.code = 'ADMISSIONS_PAYMENT_OVERRIDE'
WHERE role.code = 'FINANCE_OFFICER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (
    id, role_id, permission_id,
    created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version
)
SELECT gen_random_uuid(), role.id, permission.id, now(), now(), null, null, null, null, 0
FROM roles role
JOIN permissions permission ON permission.code = 'ADMISSIONS_APPLICATION_APPLY'
WHERE role.code = 'APPLICANT'
ON CONFLICT (role_id, permission_id) DO NOTHING;
