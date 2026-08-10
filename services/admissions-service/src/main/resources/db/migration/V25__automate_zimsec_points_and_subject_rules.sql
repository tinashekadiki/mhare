-- Author: Tinashe K

ALTER TABLE admission_subjects
    ADD COLUMN is_science_subject boolean NOT NULL DEFAULT false;

ALTER TABLE admission_subjects_aud
    ADD COLUMN is_science_subject boolean;

ALTER TABLE admission_requirement_sets
    ADD COLUMN requires_mathematics_or_science boolean NOT NULL DEFAULT false;

ALTER TABLE admission_requirement_sets_aud
    ADD COLUMN requires_mathematics_or_science boolean;

ALTER TABLE applications
    ADD COLUMN calculated_total_points numeric(8, 2),
    ADD COLUMN points_calculated_at timestamptz;

ALTER TABLE applications_aud
    ADD COLUMN calculated_total_points numeric(8, 2),
    ADD COLUMN points_calculated_at timestamptz;

INSERT INTO exam_bodies (
    id, code, name, is_active, created_at, updated_at, version
)
VALUES (
    '4d874a40-4f3c-4bc7-9ee1-290837c7e708', 'ZIMSEC',
    'Zimbabwe School Examinations Council', true, now(), now(), 0
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO admission_subjects (
    id, code, name, level, subject_group_code, is_science_subject, is_active,
    created_at, updated_at, version
)
VALUES
    ('9f4170b0-43e9-4ef8-bce0-54c83f4b0001', 'ENG', 'English Language', 'O_LEVEL', 'ENGLISH', false, true, now(), now(), 0),
    ('9f4170b0-43e9-4ef8-bce0-54c83f4b0002', 'MATH', 'Mathematics', 'O_LEVEL', 'MATHEMATICS', false, true, now(), now(), 0),
    ('9f4170b0-43e9-4ef8-bce0-54c83f4b0003', 'BIO', 'Biology', 'O_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('9f4170b0-43e9-4ef8-bce0-54c83f4b0004', 'CHEM', 'Chemistry', 'O_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('9f4170b0-43e9-4ef8-bce0-54c83f4b0005', 'PHYS', 'Physics', 'O_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('9f4170b0-43e9-4ef8-bce0-54c83f4b0006', 'GENSCI', 'General Science', 'O_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('9f4170b0-43e9-4ef8-bce0-54c83f4b0007', 'COMSCI', 'Computer Science', 'O_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('9f4170b0-43e9-4ef8-bce0-54c83f4b0008', 'GEOG', 'Geography', 'O_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('9f4170b0-43e9-4ef8-bce0-54c83f4b0009', 'HIST', 'History', 'O_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('9f4170b0-43e9-4ef8-bce0-54c83f4b0010', 'ACCT', 'Principles of Accounts', 'O_LEVEL', 'COMMERCIAL', false, true, now(), now(), 0),
    ('9f4170b0-43e9-4ef8-bce0-54c83f4b0101', 'MATH', 'Mathematics', 'A_LEVEL', 'MATHEMATICS', false, true, now(), now(), 0),
    ('9f4170b0-43e9-4ef8-bce0-54c83f4b0102', 'BIO', 'Biology', 'A_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('9f4170b0-43e9-4ef8-bce0-54c83f4b0103', 'CHEM', 'Chemistry', 'A_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('9f4170b0-43e9-4ef8-bce0-54c83f4b0104', 'PHYS', 'Physics', 'A_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('9f4170b0-43e9-4ef8-bce0-54c83f4b0105', 'COMSCI', 'Computer Science', 'A_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('9f4170b0-43e9-4ef8-bce0-54c83f4b0106', 'GEOG', 'Geography', 'A_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('9f4170b0-43e9-4ef8-bce0-54c83f4b0107', 'HIST', 'History', 'A_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('9f4170b0-43e9-4ef8-bce0-54c83f4b0108', 'ACCT', 'Accounting', 'A_LEVEL', 'COMMERCIAL', false, true, now(), now(), 0),
    ('9f4170b0-43e9-4ef8-bce0-54c83f4b0109', 'ECON', 'Economics', 'A_LEVEL', 'COMMERCIAL', false, true, now(), now(), 0),
    ('9f4170b0-43e9-4ef8-bce0-54c83f4b0110', 'BUS', 'Business Studies', 'A_LEVEL', 'COMMERCIAL', false, true, now(), now(), 0)
ON CONFLICT (level, code) DO UPDATE
SET is_science_subject = EXCLUDED.is_science_subject,
    subject_group_code = EXCLUDED.subject_group_code,
    updated_at = now();

INSERT INTO grading_scales (
    id, code, name, level, effective_from, effective_to,
    created_at, updated_at, version
)
SELECT 'b67b4ba0-0c04-41ad-a168-83d1b2f5c5a1', 'ZIMSEC-A', 'ZIMSEC A Level',
       'A_LEVEL', DATE '1980-01-01', NULL, now(), now(), 0
WHERE NOT EXISTS (
    SELECT 1 FROM grading_scales
    WHERE level = 'A_LEVEL' AND deleted_at IS NULL
);

INSERT INTO grading_scale_values (
    id, grading_scale_id, grade, points, is_pass, sort_order,
    created_at, updated_at, version
)
SELECT value.id, scale.id, value.grade, value.points, true, value.sort_order,
       now(), now(), 0
FROM grading_scales scale
CROSS JOIN (VALUES
    ('f18075bf-8657-4abc-95e0-1f48e65d5a01'::uuid, 'A', 5.00::numeric, 1),
    ('f18075bf-8657-4abc-95e0-1f48e65d5a02'::uuid, 'B', 4.00::numeric, 2),
    ('f18075bf-8657-4abc-95e0-1f48e65d5a03'::uuid, 'C', 3.00::numeric, 3),
    ('f18075bf-8657-4abc-95e0-1f48e65d5a04'::uuid, 'D', 2.00::numeric, 4),
    ('f18075bf-8657-4abc-95e0-1f48e65d5a05'::uuid, 'E', 1.00::numeric, 5)
) AS value(id, grade, points, sort_order)
WHERE scale.code = 'ZIMSEC-A'
ON CONFLICT (grading_scale_id, grade) DO UPDATE
SET points = EXCLUDED.points,
    is_pass = EXCLUDED.is_pass,
    sort_order = EXCLUDED.sort_order,
    updated_at = now();

INSERT INTO grading_scales (
    id, code, name, level, effective_from, effective_to,
    created_at, updated_at, version
)
SELECT 'b67b4ba0-0c04-41ad-a168-83d1b2f5c5b2', 'ZIMSEC-O', 'ZIMSEC O Level',
       'O_LEVEL', DATE '1980-01-01', NULL, now(), now(), 0
WHERE NOT EXISTS (
    SELECT 1 FROM grading_scales
    WHERE level = 'O_LEVEL' AND deleted_at IS NULL
);

INSERT INTO grading_scale_values (
    id, grading_scale_id, grade, points, is_pass, sort_order,
    created_at, updated_at, version
)
SELECT value.id, scale.id, value.grade, NULL, true, value.sort_order,
       now(), now(), 0
FROM grading_scales scale
CROSS JOIN (VALUES
    ('f18075bf-8657-4abc-95e0-1f48e65d5b01'::uuid, 'A', 1),
    ('f18075bf-8657-4abc-95e0-1f48e65d5b02'::uuid, 'B', 2),
    ('f18075bf-8657-4abc-95e0-1f48e65d5b03'::uuid, 'C', 3)
) AS value(id, grade, sort_order)
WHERE scale.code = 'ZIMSEC-O'
ON CONFLICT (grading_scale_id, grade) DO UPDATE
SET points = NULL,
    is_pass = EXCLUDED.is_pass,
    sort_order = EXCLUDED.sort_order,
    updated_at = now();
