ALTER TABLE application_programme_choices
    DROP CONSTRAINT uk_application_choice_rank,
    DROP CONSTRAINT uk_application_choice_programme;

CREATE UNIQUE INDEX uk_application_choice_rank
    ON application_programme_choices (application_id, choice_rank)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_application_choice_programme
    ON application_programme_choices (application_id, programme_id)
    WHERE deleted_at IS NULL;
