-- Author: Tinashe K

INSERT INTO roles (
    id, code, name, scope, is_system_managed,
    created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version
) VALUES
    ('10000000-0000-4000-8000-000000000006', 'ACADEMIC_UNIT_STAFF', 'Academic Unit Staff', 'ACADEMIC_UNIT', true,
     now(), now(), null, null, null, null, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (
    id, code, name, category, description,
    created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version
) VALUES
    ('20000000-0000-4000-8000-000000000014', 'ADMISSIONS_APPLICATION_CONFIRM', 'Confirm applications', 'ADMISSIONS', 'Confirm payment, application sections, documents, and qualification evidence.', now(), now(), null, null, null, null, 0),
    ('20000000-0000-4000-8000-000000000015', 'ADMISSIONS_ACADEMIC_REVIEW_RELEASE', 'Release academic reviews', 'ADMISSIONS', 'Release eligible programme choices to the resolved highest academic unit.', now(), now(), null, null, null, null, 0),
    ('20000000-0000-4000-8000-000000000016', 'ADMISSIONS_ACADEMIC_UNIT_RECOMMEND', 'Record academic unit recommendations', 'ADMISSIONS', 'Claim and recommend applications assigned to the exact highest academic unit.', now(), now(), null, null, null, null, 0),
    ('20000000-0000-4000-8000-000000000017', 'ADMISSIONS_SELECTION_APPROVE', 'Approve admissions selections', 'ADMISSIONS', 'Approve, return, or override academic unit recommendations and lock selection rounds.', now(), now(), null, null, null, null, 0),
    ('20000000-0000-4000-8000-000000000018', 'ADMISSIONS_OFFER_MANAGE', 'Manage admission offers', 'ADMISSIONS', 'Create and inspect governed admission offers and offer batches.', now(), now(), null, null, null, null, 0),
    ('20000000-0000-4000-8000-000000000019', 'ADMISSIONS_OFFER_APPROVE', 'Approve admission offers', 'ADMISSIONS', 'Approve admission offers after their official letter is stored.', now(), now(), null, null, null, null, 0),
    ('20000000-0000-4000-8000-000000000020', 'ADMISSIONS_OFFER_DISPATCH', 'Dispatch admission offers', 'ADMISSIONS', 'Dispatch approved offers and preserve dispatch evidence.', now(), now(), null, null, null, null, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (
    id, role_id, permission_id,
    created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version
)
SELECT gen_random_uuid(), role.id, permission.id, now(), now(), null, null, null, null, 0
FROM roles role CROSS JOIN permissions permission
WHERE role.code = 'SYSTEM_ADMIN'
  AND permission.code LIKE 'ADMISSIONS_%'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (
    id, role_id, permission_id,
    created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version
)
SELECT gen_random_uuid(), role.id, permission.id, now(), now(), null, null, null, null, 0
FROM roles role JOIN permissions permission ON permission.code IN (
    'ADMISSIONS_APPLICATION_CONFIRM', 'ADMISSIONS_ACADEMIC_REVIEW_RELEASE', 'ADMISSIONS_SELECTION_APPROVE',
    'ADMISSIONS_OFFER_MANAGE', 'ADMISSIONS_OFFER_APPROVE', 'ADMISSIONS_OFFER_DISPATCH'
)
WHERE role.code = 'ADMISSIONS_OFFICER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (
    id, role_id, permission_id,
    created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version
)
SELECT gen_random_uuid(), role.id, permission.id, now(), now(), null, null, null, null, 0
FROM roles role JOIN permissions permission ON permission.code IN (
    'ADMISSIONS_ACADEMIC_UNIT_RECOMMEND', 'ADMISSIONS_APPLICATION_REVIEW', 'CORE_WORKFLOW_TASK'
)
WHERE role.code = 'ACADEMIC_UNIT_STAFF'
ON CONFLICT (role_id, permission_id) DO NOTHING;
