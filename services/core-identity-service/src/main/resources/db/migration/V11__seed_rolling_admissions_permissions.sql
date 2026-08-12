-- Author: Tinashe K

INSERT INTO permissions (
    id, code, name, category, description,
    created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version
) VALUES
    ('20000000-0000-4000-8000-000000000021', 'ADMISSIONS_ELIGIBILITY_REVIEW',
     'Resolve admissions eligibility', 'ADMISSIONS',
     'Recalculate eligibility and resolve cases requiring an evidenced manual review.',
     now(), now(), null, null, null, null, 0),
    ('20000000-0000-4000-8000-000000000022', 'ADMISSIONS_DECISION_MAKE',
     'Make admission decisions', 'ADMISSIONS',
     'Record the final direct admission or rejection decision for a programme choice.',
     now(), now(), null, null, null, null, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (
    id, role_id, permission_id,
    created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version
)
SELECT gen_random_uuid(), role.id, permission.id, now(), now(), null, null, null, null, 0
FROM roles role
JOIN permissions permission ON permission.code IN (
    'ADMISSIONS_ELIGIBILITY_REVIEW', 'ADMISSIONS_DECISION_MAKE'
)
WHERE role.code IN ('SYSTEM_ADMIN', 'ADMISSIONS_OFFICER')
ON CONFLICT (role_id, permission_id) DO NOTHING;
