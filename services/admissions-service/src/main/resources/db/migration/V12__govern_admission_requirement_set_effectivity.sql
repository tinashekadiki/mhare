-- Author: Tinashe K

ALTER TABLE admission_requirement_sets
    ADD CONSTRAINT ck_requirement_sets_effective_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    ADD CONSTRAINT ck_requirement_sets_status
        CHECK (status IN ('DRAFT', 'APPROVED', 'RETIRED')),
    ADD CONSTRAINT ck_requirement_sets_approval
        CHECK (
            (status IN ('APPROVED', 'RETIRED') AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL)
            OR status = 'DRAFT'
        ),
    ADD CONSTRAINT ck_requirement_sets_advanced_rule_version
        CHECK ((advanced_rules_json IS NULL) = (advanced_rules_version IS NULL));

ALTER TABLE admission_requirement_sets
    ADD CONSTRAINT ex_requirement_sets_non_overlapping_approved_effectivity
    EXCLUDE USING gist (
        programme_id WITH =,
        application_type_id WITH =,
        (coalesce(admission_cycle_id, '00000000-0000-0000-0000-000000000000'::uuid)) WITH =,
        daterange(effective_from, coalesce(effective_to, 'infinity'::date), '[]') WITH &&
    )
    WHERE (status = 'APPROVED' AND deleted_at IS NULL);
