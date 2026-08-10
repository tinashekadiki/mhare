-- Author: Tinashe K
-- Preserve programme and local student-group snapshots for governed dining statistics.

ALTER TABLE student_dining_assignments
    ADD COLUMN programme_code varchar(50),
    ADD COLUMN student_group_code varchar(80);

ALTER TABLE student_dining_assignments_aud
    ADD COLUMN programme_code varchar(50),
    ADD COLUMN student_group_code varchar(80);

CREATE INDEX ix_dining_assignment_programme
    ON student_dining_assignments(programme_code, academic_period_code)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_dining_assignment_student_group
    ON student_dining_assignments(student_group_code, academic_period_code)
    WHERE deleted_at IS NULL AND student_group_code IS NOT NULL;
