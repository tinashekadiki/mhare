-- Author: Tinashe K

ALTER TABLE offers
    ALTER COLUMN offer_batch_id DROP NOT NULL,
    ALTER COLUMN offer_type DROP NOT NULL,
    ALTER COLUMN acceptance_deadline DROP NOT NULL,
    ALTER COLUMN commencement_date DROP NOT NULL,
    ADD COLUMN programme_choice_decision_id uuid REFERENCES programme_choice_decisions (id),
    ADD COLUMN current_document_version_id uuid,
    ADD COLUMN current_publication_id uuid,
    ADD COLUMN amendment_pending boolean NOT NULL DEFAULT false;

ALTER TABLE offers DROP CONSTRAINT ck_offers_type;
ALTER TABLE offers ADD CONSTRAINT ck_offers_type CHECK (
    offer_type IS NULL OR offer_type IN ('FIRM', 'CONDITIONAL')
);
ALTER TABLE offers DROP CONSTRAINT ck_offers_dates;
ALTER TABLE offers ADD CONSTRAINT ck_offers_dates CHECK (
    commencement_date IS NULL OR (
        (orientation_date IS NULL OR orientation_date <= commencement_date)
        AND (registration_date IS NULL OR registration_date <= commencement_date)
    )
);
ALTER TABLE offers DROP CONSTRAINT ck_offers_conditional_text;
ALTER TABLE offers ADD CONSTRAINT ck_offers_conditional_text CHECK (
    offer_type <> 'CONDITIONAL' OR length(trim(coalesce(conditions_text, ''))) > 0
);
ALTER TABLE offers ADD CONSTRAINT ck_offers_complete_outside_draft CHECK (
    status = 'DRAFT' OR (
        offer_type IS NOT NULL AND acceptance_deadline IS NOT NULL AND commencement_date IS NOT NULL
    )
);
ALTER TABLE offers ADD CONSTRAINT ck_offers_source_kind CHECK (
    (offer_batch_id IS NOT NULL AND programme_choice_decision_id IS NULL)
    OR (offer_batch_id IS NULL AND programme_choice_decision_id IS NOT NULL)
);

CREATE TABLE offer_document_versions (
    id uuid PRIMARY KEY,
    offer_id uuid NOT NULL REFERENCES offers (id),
    document_version integer NOT NULL,
    status varchar(30) NOT NULL,
    generated_document_id uuid,
    document_number varchar(80),
    storage_bucket varchar(120),
    storage_key varchar(500),
    checksum_sha256 varchar(64),
    failure_reason varchar(1000),
    requested_by_user_id uuid NOT NULL,
    requested_at timestamptz NOT NULL,
    stored_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_offer_document_version UNIQUE (offer_id, document_version),
    CONSTRAINT ck_offer_document_version_number CHECK (document_version > 0),
    CONSTRAINT ck_offer_document_version_status CHECK (status IN ('REQUESTED', 'STORED', 'FAILED')),
    CONSTRAINT ck_offer_document_version_evidence CHECK (
        (status = 'REQUESTED' AND generated_document_id IS NULL AND stored_at IS NULL AND failure_reason IS NULL)
        OR (status = 'STORED' AND generated_document_id IS NOT NULL AND document_number IS NOT NULL
            AND storage_bucket IS NOT NULL AND storage_key IS NOT NULL AND checksum_sha256 IS NOT NULL
            AND stored_at IS NOT NULL AND failure_reason IS NULL)
        OR (status = 'FAILED' AND length(trim(coalesce(failure_reason, ''))) > 0)
    )
);

CREATE INDEX idx_offer_document_versions_offer_status
    ON offer_document_versions (offer_id, status, document_version DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE offer_publications (
    id uuid PRIMARY KEY,
    offer_id uuid NOT NULL REFERENCES offers (id),
    offer_document_version_id uuid NOT NULL REFERENCES offer_document_versions (id),
    publication_sequence integer NOT NULL,
    portal_published_at timestamptz NOT NULL,
    published_by_user_id uuid NOT NULL,
    notification_event_id uuid NOT NULL,
    email_delivery_status varchar(30) NOT NULL,
    provider_message_id varchar(240),
    email_status_at timestamptz NOT NULL,
    email_failure_reason varchar(1000),
    current_publication boolean NOT NULL,
    superseded_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_offer_publication_sequence UNIQUE (offer_id, publication_sequence),
    CONSTRAINT uk_offer_publication_document UNIQUE (offer_document_version_id),
    CONSTRAINT uk_offer_publication_event UNIQUE (notification_event_id),
    CONSTRAINT ck_offer_publication_sequence CHECK (publication_sequence > 0),
    CONSTRAINT ck_offer_publication_email_status CHECK (email_delivery_status IN ('QUEUED', 'SENT', 'FAILED', 'BOUNCED')),
    CONSTRAINT ck_offer_publication_failure CHECK (
        email_delivery_status NOT IN ('FAILED', 'BOUNCED')
        OR length(trim(coalesce(email_failure_reason, ''))) > 0
    ),
    CONSTRAINT ck_offer_publication_current CHECK (
        (current_publication AND superseded_at IS NULL)
        OR (NOT current_publication AND superseded_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_offer_current_publication
    ON offer_publications (offer_id)
    WHERE current_publication AND deleted_at IS NULL;

ALTER TABLE offers
    ADD CONSTRAINT fk_offers_current_document_version
        FOREIGN KEY (current_document_version_id) REFERENCES offer_document_versions (id),
    ADD CONSTRAINT fk_offers_current_publication
        FOREIGN KEY (current_publication_id) REFERENCES offer_publications (id);

ALTER TABLE offer_responses
    ADD COLUMN offer_publication_id uuid REFERENCES offer_publications (id);

ALTER TABLE offer_dispatches
    ALTER COLUMN sent_at DROP NOT NULL,
    ADD COLUMN offer_publication_id uuid REFERENCES offer_publications (id),
    ADD COLUMN attempt_number integer NOT NULL DEFAULT 1,
    ADD COLUMN notification_event_id uuid;
ALTER TABLE offer_dispatches DROP CONSTRAINT ck_offer_dispatches_status;
ALTER TABLE offer_dispatches ADD CONSTRAINT ck_offer_dispatches_status
    CHECK (status IN ('QUEUED', 'SENT', 'DELIVERED', 'FAILED', 'BOUNCED'));
ALTER TABLE offer_dispatches ADD CONSTRAINT ck_offer_dispatches_attempt CHECK (attempt_number > 0);
CREATE UNIQUE INDEX uk_offer_dispatch_publication_attempt
    ON offer_dispatches (offer_publication_id, attempt_number)
    WHERE offer_publication_id IS NOT NULL AND deleted_at IS NULL;
CREATE UNIQUE INDEX uk_offer_dispatch_notification_event
    ON offer_dispatches (notification_event_id)
    WHERE notification_event_id IS NOT NULL AND deleted_at IS NULL;

ALTER TABLE offers_aud
    ADD COLUMN programme_choice_decision_id uuid,
    ADD COLUMN current_document_version_id uuid,
    ADD COLUMN current_publication_id uuid,
    ADD COLUMN amendment_pending boolean;
ALTER TABLE offer_responses_aud ADD COLUMN offer_publication_id uuid;
ALTER TABLE offer_dispatches_aud
    ADD COLUMN offer_publication_id uuid,
    ADD COLUMN attempt_number integer,
    ADD COLUMN notification_event_id uuid;

CREATE TABLE offer_document_versions_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    offer_id uuid, document_version integer, status varchar(30), generated_document_id uuid,
    document_number varchar(80), storage_bucket varchar(120), storage_key varchar(500),
    checksum_sha256 varchar(64), failure_reason varchar(1000), requested_by_user_id uuid,
    requested_at timestamptz, stored_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE offer_publications_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    offer_id uuid, offer_document_version_id uuid, publication_sequence integer,
    portal_published_at timestamptz, published_by_user_id uuid, notification_event_id uuid,
    email_delivery_status varchar(30), provider_message_id varchar(240), email_status_at timestamptz,
    email_failure_reason varchar(1000), current_publication boolean, superseded_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

DROP TRIGGER trg_offer_source_guard ON offers;

CREATE OR REPLACE FUNCTION validate_offer_source()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    source_application_id uuid;
    source_programme_id uuid;
    source_programme_version_id uuid;
    source_intake_id uuid;
    source_owning_academic_unit_id uuid;
    batch_scope_type varchar(30);
    batch_scope_id uuid;
    source_is_approved boolean;
    response_exists boolean;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF OLD.application_id IS DISTINCT FROM NEW.application_id
           OR OLD.programme_choice_id IS DISTINCT FROM NEW.programme_choice_id
           OR OLD.offer_batch_id IS DISTINCT FROM NEW.offer_batch_id
           OR OLD.programme_choice_decision_id IS DISTINCT FROM NEW.programme_choice_decision_id
           OR OLD.programme_id IS DISTINCT FROM NEW.programme_id
           OR OLD.programme_version_id IS DISTINCT FROM NEW.programme_version_id
           OR OLD.programme_code IS DISTINCT FROM NEW.programme_code
           OR OLD.programme_name IS DISTINCT FROM NEW.programme_name
           OR OLD.intake_id IS DISTINCT FROM NEW.intake_id
           OR OLD.offer_number IS DISTINCT FROM NEW.offer_number THEN
            RAISE EXCEPTION 'An offer source snapshot is immutable after creation';
        END IF;

        SELECT EXISTS (SELECT 1 FROM offer_responses response
                       WHERE response.offer_id = OLD.id AND response.deleted_at IS NULL)
          INTO response_exists;
        IF response_exists AND (
            OLD.offer_type IS DISTINCT FROM NEW.offer_type
            OR OLD.conditions_text IS DISTINCT FROM NEW.conditions_text
            OR OLD.acceptance_deadline IS DISTINCT FROM NEW.acceptance_deadline
            OR OLD.registration_date IS DISTINCT FROM NEW.registration_date
            OR OLD.orientation_date IS DISTINCT FROM NEW.orientation_date
            OR OLD.commencement_date IS DISTINCT FROM NEW.commencement_date
            OR OLD.generated_document_id IS DISTINCT FROM NEW.generated_document_id
            OR OLD.current_document_version_id IS DISTINCT FROM NEW.current_document_version_id
            OR OLD.current_publication_id IS DISTINCT FROM NEW.current_publication_id
        ) THEN
            RAISE EXCEPTION 'An answered offer and its published document are immutable';
        END IF;
        RETURN NEW;
    END IF;

    SELECT choice.application_id, choice.programme_id, choice.programme_version_id,
           choice.owning_academic_unit_id, cycle.intake_id
      INTO source_application_id, source_programme_id, source_programme_version_id,
           source_owning_academic_unit_id, source_intake_id
      FROM application_programme_choices choice
      JOIN applications application_record ON application_record.id = choice.application_id
      JOIN admission_cycles cycle ON cycle.id = application_record.admission_cycle_id
     WHERE choice.id = NEW.programme_choice_id
       AND choice.deleted_at IS NULL
       AND application_record.deleted_at IS NULL;

    IF source_application_id IS NULL
       OR source_application_id <> NEW.application_id
       OR source_programme_id <> NEW.programme_id
       OR source_programme_version_id <> NEW.programme_version_id
       OR source_intake_id <> NEW.intake_id THEN
        RAISE EXCEPTION 'Offer source does not match the application and programme snapshot';
    END IF;

    IF NEW.programme_choice_decision_id IS NOT NULL THEN
        SELECT EXISTS (
            SELECT 1 FROM programme_choice_decisions decision
            WHERE decision.id = NEW.programme_choice_decision_id
              AND decision.application_id = NEW.application_id
              AND decision.programme_choice_id = NEW.programme_choice_id
              AND decision.decision = 'ADMIT'
              AND decision.deleted_at IS NULL
        ) INTO source_is_approved;
        IF NOT source_is_approved THEN
            RAISE EXCEPTION 'Direct offers require an admitted programme-choice decision';
        END IF;
    ELSE
        SELECT EXISTS (
            SELECT 1
            FROM selection_decisions decision
            JOIN selection_rounds round_record ON round_record.id = decision.selection_round_id
            JOIN offer_batches batch ON batch.selection_round_id = round_record.id
            WHERE decision.programme_choice_id = NEW.programme_choice_id
              AND decision.decision = 'SELECT'
              AND decision.deleted_at IS NULL
              AND round_record.status = 'APPROVED'
              AND round_record.deleted_at IS NULL
              AND batch.id = NEW.offer_batch_id
              AND batch.status = 'APPROVED'
              AND batch.deleted_at IS NULL
        ) INTO source_is_approved;
        IF NOT source_is_approved THEN
            RAISE EXCEPTION 'Historical offers require an approved batch and selected decision';
        END IF;
        SELECT scope_type, scope_id INTO batch_scope_type, batch_scope_id
          FROM offer_batches WHERE id = NEW.offer_batch_id;
        IF (batch_scope_type = 'PROGRAMME' AND batch_scope_id <> source_programme_id)
           OR (batch_scope_type = 'ACADEMIC_UNIT' AND batch_scope_id <> source_owning_academic_unit_id) THEN
            RAISE EXCEPTION 'Selected programme choice is outside the offer batch scope';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_offer_source_guard
BEFORE INSERT OR UPDATE ON offers
FOR EACH ROW EXECUTE FUNCTION validate_offer_source();

CREATE OR REPLACE FUNCTION protect_stored_offer_document_version()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status = 'STORED' THEN
        RAISE EXCEPTION 'Stored offer document versions are immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_protect_stored_offer_document_version
BEFORE UPDATE OR DELETE ON offer_document_versions
FOR EACH ROW EXECUTE FUNCTION protect_stored_offer_document_version();

CREATE INDEX idx_applications_work_items
    ON applications (status, admission_cycle_id, application_type_id, updated_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_programme_choices_work_items
    ON application_programme_choices (programme_id, choice_status, application_id, choice_rank)
    WHERE deleted_at IS NULL;

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    offer_document_versions, offer_document_versions_aud,
    offer_publications, offer_publications_aud
TO emhare_service;
