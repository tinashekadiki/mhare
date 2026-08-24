ALTER TABLE admission_subjects
    ADD COLUMN is_mathematics_subject boolean DEFAULT false NOT NULL,
    ADD COLUMN is_english_subject boolean DEFAULT false NOT NULL;

ALTER TABLE admission_subjects_aud
    ADD COLUMN is_mathematics_subject boolean,
    ADD COLUMN is_english_subject boolean;

UPDATE admission_subjects
SET is_mathematics_subject = upper(subject_group_code) = 'MATHEMATICS',
    is_english_subject = upper(subject_group_code) = 'ENGLISH';

ALTER TABLE admission_requirement_sets
    ADD COLUMN requires_mathematics boolean DEFAULT false NOT NULL,
    ADD COLUMN requires_science boolean DEFAULT false NOT NULL;

ALTER TABLE admission_requirement_sets_aud
    ADD COLUMN requires_mathematics boolean,
    ADD COLUMN requires_science boolean;

COMMENT ON COLUMN admission_requirement_sets.requires_mathematics_or_science IS
    'Historical combined rule retained for approved requirement-set compatibility. New rules use requires_mathematics and requires_science independently.';
