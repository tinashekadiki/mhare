-- Author: Tinashe K
-- Repair direct offers published before publication synchronized the linked workflow state.

CREATE TEMPORARY TABLE published_offer_state_reconciliation ON COMMIT DROP AS
SELECT DISTINCT ON (application.id)
       application.id AS application_id,
       programme_choice.id AS programme_choice_id,
       offer.offer_number,
       publication.published_by_user_id,
       publication.portal_published_at
FROM offers offer
JOIN applications application
  ON application.id = offer.application_id
 AND application.deleted_at IS NULL
JOIN application_programme_choices programme_choice
  ON programme_choice.id = offer.programme_choice_id
 AND programme_choice.deleted_at IS NULL
JOIN offer_publications publication
  ON publication.id = offer.current_publication_id
 AND publication.current_publication
 AND publication.deleted_at IS NULL
WHERE offer.deleted_at IS NULL
  AND offer.status = 'SENT'
  AND application.status = 'ADMITTED'
  AND programme_choice.choice_status = 'ADMITTED'
ORDER BY application.id, publication.portal_published_at DESC, offer.id;

UPDATE application_programme_choices programme_choice
SET choice_status = 'OFFERED',
    decision_reason = 'Published offer ' || reconciliation.offer_number,
    updated_at = GREATEST(programme_choice.updated_at, reconciliation.portal_published_at),
    modified_by_user_id = reconciliation.published_by_user_id,
    version = programme_choice.version + 1
FROM published_offer_state_reconciliation reconciliation
WHERE programme_choice.id = reconciliation.programme_choice_id
  AND programme_choice.choice_status = 'ADMITTED';

UPDATE applications application
SET status = 'OFFERED',
    status_reason = 'Published offer ' || reconciliation.offer_number,
    updated_at = GREATEST(application.updated_at, reconciliation.portal_published_at),
    modified_by_user_id = reconciliation.published_by_user_id,
    version = application.version + 1
FROM published_offer_state_reconciliation reconciliation
WHERE application.id = reconciliation.application_id
  AND application.status = 'ADMITTED';

INSERT INTO application_status_events (
    id, application_id, from_status, to_status, reason,
    changed_by_user_id, changed_at, created_at, updated_at,
    created_by_user_id, modified_by_user_id, version)
SELECT gen_random_uuid(),
       reconciliation.application_id,
       'ADMITTED',
       'OFFERED',
       'Published offer ' || reconciliation.offer_number,
       reconciliation.published_by_user_id,
       reconciliation.portal_published_at,
       now(),
       now(),
       reconciliation.published_by_user_id,
       reconciliation.published_by_user_id,
       0
FROM published_offer_state_reconciliation reconciliation
WHERE NOT EXISTS (
    SELECT 1
    FROM application_status_events status_event
    WHERE status_event.application_id = reconciliation.application_id
      AND status_event.from_status = 'ADMITTED'
      AND status_event.to_status = 'OFFERED'
      AND status_event.changed_at = reconciliation.portal_published_at
      AND status_event.deleted_at IS NULL
);
