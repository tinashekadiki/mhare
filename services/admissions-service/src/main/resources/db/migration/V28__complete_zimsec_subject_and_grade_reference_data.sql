-- Author: Tinashe K

-- Preserve the identifiers introduced in V25 while aligning their codes and names
-- with the current ZIMSEC subject catalogue.
UPDATE admission_subjects
SET code = mapped.code,
    name = mapped.name,
    updated_at = now(),
    version = version + 1
FROM (VALUES
    ('O_LEVEL', 'ENG', '4005', 'English Language'),
    ('O_LEVEL', 'MATH', '4004', 'Mathematics'),
    ('O_LEVEL', 'BIO', '4025', 'Biology'),
    ('O_LEVEL', 'CHEM', '4024', 'Chemistry'),
    ('O_LEVEL', 'PHYS', '4023', 'Physics'),
    ('O_LEVEL', 'GENSCI', '4003', 'Combined Science'),
    ('O_LEVEL', 'COMSCI', '4021', 'Computer Science'),
    ('O_LEVEL', 'GEOG', '4022', 'Geography'),
    ('O_LEVEL', 'HIST', '4044', 'History'),
    ('O_LEVEL', 'ACCT', '4051', 'Principles of Accounting'),
    ('A_LEVEL', 'MATH', '6082', 'Mathematics'),
    ('A_LEVEL', 'BIO', '6030', 'Biology'),
    ('A_LEVEL', 'CHEM', '6031', 'Chemistry'),
    ('A_LEVEL', 'PHYS', '6032', 'Physics'),
    ('A_LEVEL', 'COMSCI', '6023', 'Computer Science'),
    ('A_LEVEL', 'GEOG', '6037', 'Geography'),
    ('A_LEVEL', 'HIST', '6006', 'History'),
    ('A_LEVEL', 'ACCT', '6001', 'Accounting'),
    ('A_LEVEL', 'ECON', '6073', 'Economics'),
    ('A_LEVEL', 'BUS', '6025', 'Business Studies')
) AS mapped(level, old_code, code, name)
WHERE admission_subjects.level = mapped.level
  AND admission_subjects.code = mapped.old_code;

INSERT INTO admission_subjects (
    id, code, name, level, subject_group_code, is_science_subject, is_active,
    created_at, updated_at, version
)
VALUES
    ('8a000000-0000-4000-8000-000000004001', '4001', 'Agriculture', 'O_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004002', '4002', 'Physical Education, Sport and Mass Displays', 'O_LEVEL', 'TECHNICAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004003', '4003', 'Combined Science', 'O_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004004', '4004', 'Mathematics', 'O_LEVEL', 'MATHEMATICS', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004005', '4005', 'English Language', 'O_LEVEL', 'ENGLISH', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004006', '4006', 'Heritage Studies', 'O_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004007', '4007', 'Shona Language', 'O_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004009', '4009', 'Tonga Language', 'O_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004010', '4010', 'Nambya Language', 'O_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004011', '4011', 'Tshivenda Language', 'O_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004012', '4012', 'Xichangana Language', 'O_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004013', '4013', 'Kalanga Language', 'O_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004014', '4014', 'Sesotho Language', 'O_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004021', '4021', 'Computer Science', 'O_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004022', '4022', 'Geography', 'O_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004023', '4023', 'Physics', 'O_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004024', '4024', 'Chemistry', 'O_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004025', '4025', 'Biology', 'O_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004026', '4026', 'Additional Mathematics', 'O_LEVEL', 'MATHEMATICS', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004027', '4027', 'Pure Mathematics', 'O_LEVEL', 'MATHEMATICS', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004029', '4029', 'Literature in English', 'O_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004044', '4044', 'History', 'O_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004045', '4045', 'Sociology', 'O_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004046', '4046', 'Economic History', 'O_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004047', '4047', 'Family and Religious Studies', 'O_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004048', '4048', 'Business and Enterprise Skills', 'O_LEVEL', 'COMMERCIAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004049', '4049', 'Commerce', 'O_LEVEL', 'COMMERCIAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004050', '4050', 'Economics', 'O_LEVEL', 'COMMERCIAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004051', '4051', 'Principles of Accounting', 'O_LEVEL', 'COMMERCIAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004052', '4052', 'Building Technology and Design', 'O_LEVEL', 'TECHNICAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004053', '4053', 'Design and Technology', 'O_LEVEL', 'TECHNICAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004054', '4054', 'Food Technology and Design', 'O_LEVEL', 'TECHNICAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004055', '4055', 'Metal Technology and Design', 'O_LEVEL', 'TECHNICAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004058', '4058', 'Textile Technology and Design', 'O_LEVEL', 'TECHNICAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004059', '4059', 'Wood Technology and Design', 'O_LEVEL', 'TECHNICAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004060', '4060', 'Art', 'O_LEVEL', 'ARTS', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004061', '4061', 'Dance', 'O_LEVEL', 'ARTS', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004062', '4062', 'Musical Arts', 'O_LEVEL', 'ARTS', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004063', '4063', 'Theatre Arts', 'O_LEVEL', 'ARTS', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004064', '4064', 'French', 'O_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004065', '4065', 'Commercial Studies', 'O_LEVEL', 'COMMERCIAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004068', '4068', 'Ndebele Language', 'O_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004073', '4073', 'Statistics', 'O_LEVEL', 'MATHEMATICS', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004074', '4074', 'English for Communication', 'O_LEVEL', 'ENGLISH', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004075', '4075', 'Mathematics Syllabus A', 'O_LEVEL', 'MATHEMATICS', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004076', '4076', 'Hospitality Management and Design', 'O_LEVEL', 'TECHNICAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-8000-000000004077', '4077', 'Guidance and Counselling', 'O_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),

    ('8a000000-0000-4000-9000-000000005033', '5033', 'Communication Skills', 'A_LEVEL', 'ENGLISH', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006001', '6001', 'Accounting', 'A_LEVEL', 'COMMERCIAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006002', '6002', 'Additional Mathematics', 'A_LEVEL', 'MATHEMATICS', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006003', '6003', 'Building Technology and Design', 'A_LEVEL', 'TECHNICAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006004', '6004', 'Business Enterprise Skills', 'A_LEVEL', 'COMMERCIAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006005', '6005', 'Design and Technology', 'A_LEVEL', 'TECHNICAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006006', '6006', 'History', 'A_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006021', '6021', 'Mechanical Mathematics', 'A_LEVEL', 'MATHEMATICS', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006022', '6022', 'Sports Management', 'A_LEVEL', 'TECHNICAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006023', '6023', 'Computer Science', 'A_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006024', '6024', 'Horticulture', 'A_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006025', '6025', 'Business Studies', 'A_LEVEL', 'COMMERCIAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006026', '6026', 'Theatre Arts', 'A_LEVEL', 'ARTS', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006027', '6027', 'Wood Technology and Design', 'A_LEVEL', 'TECHNICAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006028', '6028', 'Animal Science', 'A_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006029', '6029', 'Art', 'A_LEVEL', 'ARTS', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006030', '6030', 'Biology', 'A_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006031', '6031', 'Chemistry', 'A_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006032', '6032', 'Physics', 'A_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006034', '6034', 'Economic History', 'A_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006036', '6036', 'Food Technology and Design', 'A_LEVEL', 'TECHNICAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006037', '6037', 'Geography', 'A_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006039', '6039', 'Literature in English', 'A_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006040', '6040', 'Metal Technology and Design', 'A_LEVEL', 'TECHNICAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006042', '6042', 'Pure Mathematics', 'A_LEVEL', 'MATHEMATICS', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006043', '6043', 'Sociology', 'A_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006044', '6044', 'Software Engineering', 'A_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006046', '6046', 'Statistics', 'A_LEVEL', 'MATHEMATICS', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006047', '6047', 'Technical Graphics and Design', 'A_LEVEL', 'TECHNICAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006048', '6048', 'Agriculture Engineering', 'A_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006049', '6049', 'Crop Science', 'A_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006050', '6050', 'Dance', 'A_LEVEL', 'ARTS', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006053', '6053', 'Musical Arts', 'A_LEVEL', 'ARTS', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006054', '6054', 'Shona Language', 'A_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006055', '6055', 'Ndebele Language', 'A_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006056', '6056', 'Tonga Language', 'A_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006057', '6057', 'Nambya Language', 'A_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006058', '6058', 'Tshivenda Language', 'A_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006059', '6059', 'Xichangana Language', 'A_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006060', '6060', 'Kalanga Language', 'A_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006061', '6061', 'Sesotho Language', 'A_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006068', '6068', 'French', 'A_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006069', '6069', 'Textile Technology and Design', 'A_LEVEL', 'TECHNICAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006070', '6070', 'Physical Education, Sport and Mass Displays', 'A_LEVEL', 'TECHNICAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006073', '6073', 'Economics', 'A_LEVEL', 'COMMERCIAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006074', '6074', 'Family and Religious Studies', 'A_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006080', '6080', 'Sports Science and Technology', 'A_LEVEL', 'SCIENCE', true, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006081', '6081', 'Heritage Studies', 'A_LEVEL', 'HUMANITIES', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006082', '6082', 'Mathematics', 'A_LEVEL', 'MATHEMATICS', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006083', '6083', 'Hospitality Management and Design', 'A_LEVEL', 'TECHNICAL', false, true, now(), now(), 0),
    ('8a000000-0000-4000-9000-000000006085', '6085', 'Guidance and Counselling and Life Skills Education', 'A_LEVEL', 'HUMANITIES', false, true, now(), now(), 0)
ON CONFLICT (level, code) DO UPDATE
SET name = EXCLUDED.name,
    subject_group_code = EXCLUDED.subject_group_code,
    is_science_subject = EXCLUDED.is_science_subject,
    is_active = true,
    deleted_at = NULL,
    deleted_by_user_id = NULL,
    updated_at = now();

INSERT INTO grading_scale_values (
    id, grading_scale_id, grade, points, is_pass, sort_order,
    created_at, updated_at, version
)
SELECT value.id, scale.id, value.grade, value.points, value.is_pass, value.sort_order,
       now(), now(), 0
FROM grading_scales scale
CROSS JOIN (VALUES
    ('f18075bf-8657-4abc-95e0-1f48e65d5a06'::uuid, 'O', NULL::numeric, false, 6),
    ('f18075bf-8657-4abc-95e0-1f48e65d5a07'::uuid, 'F', NULL::numeric, false, 7)
) AS value(id, grade, points, is_pass, sort_order)
WHERE scale.code = 'ZIMSEC-A'
ON CONFLICT (grading_scale_id, grade) DO UPDATE
SET points = EXCLUDED.points,
    is_pass = EXCLUDED.is_pass,
    sort_order = EXCLUDED.sort_order,
    deleted_at = NULL,
    deleted_by_user_id = NULL,
    updated_at = now();

INSERT INTO grading_scale_values (
    id, grading_scale_id, grade, points, is_pass, sort_order,
    created_at, updated_at, version
)
SELECT value.id, scale.id, value.grade, NULL, value.is_pass, value.sort_order,
       now(), now(), 0
FROM grading_scales scale
CROSS JOIN (VALUES
    ('f18075bf-8657-4abc-95e0-1f48e65d5b04'::uuid, 'D', false, 4),
    ('f18075bf-8657-4abc-95e0-1f48e65d5b05'::uuid, 'E', false, 5),
    ('f18075bf-8657-4abc-95e0-1f48e65d5b06'::uuid, 'U', false, 6)
) AS value(id, grade, is_pass, sort_order)
WHERE scale.code = 'ZIMSEC-O'
ON CONFLICT (grading_scale_id, grade) DO UPDATE
SET points = NULL,
    is_pass = EXCLUDED.is_pass,
    sort_order = EXCLUDED.sort_order,
    deleted_at = NULL,
    deleted_by_user_id = NULL,
    updated_at = now();
