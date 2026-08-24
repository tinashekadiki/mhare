-- Author: Tinashe K
-- Governed Communications author and approver access.

INSERT INTO permissions (id, code, name, category, description, created_at, updated_at, version)
VALUES
  ('20000000-0000-4000-8000-000000000101', 'COMMUNICATIONS_READ', 'Read Communications workspace', 'COMMUNICATIONS', 'Read editorial queues, versions, previews, and publication evidence.', now(), now(), 0),
  ('20000000-0000-4000-8000-000000000102', 'COMMUNICATIONS_AUTHOR', 'Author public content', 'COMMUNICATIONS', 'Create drafts, edit versions, and submit content for independent review.', now(), now(), 0),
  ('20000000-0000-4000-8000-000000000103', 'COMMUNICATIONS_APPROVE', 'Approve public content', 'COMMUNICATIONS', 'Approve or reject submitted content under four-eye control.', now(), now(), 0),
  ('20000000-0000-4000-8000-000000000104', 'COMMUNICATIONS_PUBLISH', 'Schedule public content', 'COMMUNICATIONS', 'Schedule approved content for the public gateway.', now(), now(), 0),
  ('20000000-0000-4000-8000-000000000105', 'COMMUNICATIONS_WITHDRAW', 'Withdraw public content', 'COMMUNICATIONS', 'Withdraw a scheduled or live public publication with a reason.', now(), now(), 0),
  ('20000000-0000-4000-8000-000000000106', 'COMMUNICATIONS_CATEGORY_MANAGE', 'Manage Communications categories', 'COMMUNICATIONS', 'Maintain the governed public-content category catalogue.', now(), now(), 0),
  ('20000000-0000-4000-8000-000000000107', 'COMMUNICATIONS_MEDIA_MANAGE', 'Manage Communications media', 'COMMUNICATIONS', 'Upload and govern accessible public-content media.', now(), now(), 0);

INSERT INTO roles (id, code, name, scope, is_system_managed, created_at, updated_at, version)
VALUES
  ('10000000-0000-4000-8000-000000000101', 'COMMUNICATIONS_AUTHOR', 'Communications Author', 'SYSTEM', true, now(), now(), 0),
  ('10000000-0000-4000-8000-000000000102', 'COMMUNICATIONS_APPROVER', 'Communications Approver', 'SYSTEM', true, now(), now(), 0);

INSERT INTO role_permissions (id, role_id, permission_id, created_at, updated_at, version)
SELECT gen_random_uuid(), role_id, permission_id, now(), now(), 0
FROM (
  VALUES
    ('10000000-0000-4000-8000-000000000001'::uuid, '20000000-0000-4000-8000-000000000101'::uuid),
    ('10000000-0000-4000-8000-000000000001'::uuid, '20000000-0000-4000-8000-000000000102'::uuid),
    ('10000000-0000-4000-8000-000000000001'::uuid, '20000000-0000-4000-8000-000000000103'::uuid),
    ('10000000-0000-4000-8000-000000000001'::uuid, '20000000-0000-4000-8000-000000000104'::uuid),
    ('10000000-0000-4000-8000-000000000001'::uuid, '20000000-0000-4000-8000-000000000105'::uuid),
    ('10000000-0000-4000-8000-000000000001'::uuid, '20000000-0000-4000-8000-000000000106'::uuid),
    ('10000000-0000-4000-8000-000000000001'::uuid, '20000000-0000-4000-8000-000000000107'::uuid),
    ('10000000-0000-4000-8000-000000000101'::uuid, '20000000-0000-4000-8000-000000000101'::uuid),
    ('10000000-0000-4000-8000-000000000101'::uuid, '20000000-0000-4000-8000-000000000102'::uuid),
    ('10000000-0000-4000-8000-000000000101'::uuid, '20000000-0000-4000-8000-000000000107'::uuid),
    ('10000000-0000-4000-8000-000000000102'::uuid, '20000000-0000-4000-8000-000000000101'::uuid),
    ('10000000-0000-4000-8000-000000000102'::uuid, '20000000-0000-4000-8000-000000000103'::uuid),
    ('10000000-0000-4000-8000-000000000102'::uuid, '20000000-0000-4000-8000-000000000104'::uuid),
    ('10000000-0000-4000-8000-000000000102'::uuid, '20000000-0000-4000-8000-000000000105'::uuid),
    ('10000000-0000-4000-8000-000000000102'::uuid, '20000000-0000-4000-8000-000000000106'::uuid),
    ('10000000-0000-4000-8000-000000000102'::uuid, '20000000-0000-4000-8000-000000000107'::uuid)
) AS grants(role_id, permission_id);
