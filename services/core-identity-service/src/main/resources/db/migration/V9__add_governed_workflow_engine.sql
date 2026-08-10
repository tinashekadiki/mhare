-- Author: Tinashe K

CREATE TABLE workflow_instances (
    id uuid PRIMARY KEY,
    workflow_code varchar(80) NOT NULL,
    subject_type varchar(80) NOT NULL,
    subject_id uuid NOT NULL,
    subject_reference varchar(160) NOT NULL,
    title varchar(240) NOT NULL,
    status varchar(30) NOT NULL,
    initiated_by_user_id uuid NOT NULL REFERENCES users (id),
    initiated_at timestamptz NOT NULL,
    completed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_workflow_instances_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_workflow_instances_subject
    ON workflow_instances (subject_type, subject_id)
    WHERE deleted_at IS NULL;

CREATE TABLE workflow_tasks (
    id uuid PRIMARY KEY,
    workflow_instance_id uuid NOT NULL REFERENCES workflow_instances (id),
    task_reference varchar(50) NOT NULL,
    title varchar(240) NOT NULL,
    description varchar(2000) NOT NULL,
    assignee_type varchar(20) NOT NULL,
    assigned_user_id uuid REFERENCES users (id),
    assigned_role_id uuid REFERENCES roles (id),
    scope_type varchar(30) NOT NULL,
    academic_unit_id uuid,
    status varchar(30) NOT NULL,
    due_at timestamptz,
    claimed_by_user_id uuid REFERENCES users (id),
    claimed_at timestamptz,
    completed_by_user_id uuid REFERENCES users (id),
    completed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_workflow_tasks_reference UNIQUE (task_reference),
    CONSTRAINT ck_workflow_tasks_assignee_type CHECK (assignee_type IN ('USER', 'ROLE')),
    CONSTRAINT ck_workflow_tasks_scope_type CHECK (scope_type IN ('INSTITUTION', 'ACADEMIC_UNIT')),
    CONSTRAINT ck_workflow_tasks_status CHECK (status IN ('OPEN', 'CLAIMED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_workflow_tasks_assignee CHECK (
        (assignee_type = 'USER' AND assigned_user_id IS NOT NULL AND assigned_role_id IS NULL)
        OR (assignee_type = 'ROLE' AND assigned_role_id IS NOT NULL AND assigned_user_id IS NULL)
    ),
    CONSTRAINT ck_workflow_tasks_scope CHECK (
        (scope_type = 'INSTITUTION' AND academic_unit_id IS NULL)
        OR (scope_type = 'ACADEMIC_UNIT' AND academic_unit_id IS NOT NULL)
    )
);

CREATE INDEX idx_workflow_tasks_open_queue
    ON workflow_tasks (status, due_at, created_at)
    WHERE deleted_at IS NULL AND status IN ('OPEN', 'CLAIMED');

CREATE INDEX idx_workflow_tasks_assigned_user
    ON workflow_tasks (assigned_user_id, status)
    WHERE deleted_at IS NULL AND assigned_user_id IS NOT NULL;

CREATE INDEX idx_workflow_tasks_assigned_role_scope
    ON workflow_tasks (assigned_role_id, academic_unit_id, status)
    WHERE deleted_at IS NULL AND assigned_role_id IS NOT NULL;

CREATE TABLE workflow_decisions (
    id uuid PRIMARY KEY,
    workflow_task_id uuid NOT NULL REFERENCES workflow_tasks (id),
    decision_code varchar(50) NOT NULL,
    decision_comment varchar(2000) NOT NULL,
    actor_user_id uuid NOT NULL REFERENCES users (id),
    decided_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);

CREATE INDEX idx_workflow_decisions_task
    ON workflow_decisions (workflow_task_id, decided_at);

CREATE TABLE workflow_instances_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    workflow_code varchar(80),
    subject_type varchar(80),
    subject_id uuid,
    subject_reference varchar(160),
    title varchar(240),
    status varchar(30),
    initiated_by_user_id uuid,
    initiated_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE workflow_tasks_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    workflow_instance_id uuid,
    task_reference varchar(50),
    title varchar(240),
    description varchar(2000),
    assignee_type varchar(20),
    assigned_user_id uuid,
    assigned_role_id uuid,
    scope_type varchar(30),
    academic_unit_id uuid,
    status varchar(30),
    due_at timestamptz,
    claimed_by_user_id uuid,
    claimed_at timestamptz,
    completed_by_user_id uuid,
    completed_at timestamptz,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE workflow_decisions_aud (
    id uuid NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    workflow_task_id uuid,
    decision_code varchar(50),
    decision_comment varchar(2000),
    actor_user_id uuid,
    decided_at timestamptz,
    created_at timestamptz,
    updated_at timestamptz,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint,
    PRIMARY KEY (id, rev)
);

INSERT INTO permissions (
    id, code, name, category, description,
    created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version
) VALUES
    ('20000000-0000-4000-8000-000000000012', 'CORE_WORKFLOW_MANAGE', 'Manage workflow instances', 'CORE', 'Create governed workflow instances and assign tasks by user, role, institution, or academic unit.', now(), now(), null, null, null, null, 0),
    ('20000000-0000-4000-8000-000000000013', 'CORE_WORKFLOW_TASK', 'Work assigned workflow tasks', 'CORE', 'View, claim, and decide workflow tasks assigned through the authorised role and scope.', now(), now(), null, null, null, null, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (
    id, role_id, permission_id,
    created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version
)
SELECT gen_random_uuid(), role.id, permission.id, now(), now(), null, null, null, null, 0
FROM roles role
CROSS JOIN permissions permission
WHERE role.code = 'SYSTEM_ADMIN'
  AND permission.code IN ('CORE_WORKFLOW_MANAGE', 'CORE_WORKFLOW_TASK')
ON CONFLICT (role_id, permission_id) DO NOTHING;
