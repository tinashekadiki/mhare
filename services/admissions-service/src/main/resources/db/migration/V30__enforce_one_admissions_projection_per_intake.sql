CREATE UNIQUE INDEX uk_admission_cycles_active_intake_projection
    ON admission_cycles (intake_id)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE admission_cycles IS
    'Internal one-to-one compatibility projection of Academic Setup intakes. Not an administrator-managed admissions window.';
