-- Author: Tinashe K

UPDATE admission_requirement_sets
SET requires_mathematics_or_science = true,
    updated_at = now()
WHERE status = 'DRAFT'
  AND deleted_at IS NULL
  AND requires_mathematics_or_science = false;
