-- Author: Tinashe K

CREATE SEQUENCE offer_number_sequence START WITH 1 INCREMENT BY 1;

CREATE TABLE selection_rounds (
    id uuid PRIMARY KEY,
    admission_cycle_id uuid NOT NULL REFERENCES admission_cycles (id),
    code varchar(50) NOT NULL,
    name varchar(180) NOT NULL,
    status varchar(30) NOT NULL,
    opened_at timestamptz,
    approved_at timestamptz,
    approved_by_user_id uuid,
    closed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_selection_rounds_cycle_code UNIQUE (admission_cycle_id, code),
    CONSTRAINT ck_selection_rounds_status CHECK (status IN ('DRAFT', 'OPEN', 'APPROVED', 'CLOSED')),
    CONSTRAINT ck_selection_rounds_approval CHECK (
        (status IN ('APPROVED', 'CLOSED') AND approved_at IS NOT NULL AND approved_by_user_id IS NOT NULL)
        OR status IN ('DRAFT', 'OPEN')
    )
);

CREATE TABLE selection_decisions (
    id uuid PRIMARY KEY,
    selection_round_id uuid NOT NULL REFERENCES selection_rounds (id),
    programme_choice_id uuid NOT NULL REFERENCES application_programme_choices (id),
    decision varchar(30) NOT NULL,
    rank_position integer,
    quota_type_code varchar(50),
    reason varchar(1000) NOT NULL,
    decided_by_user_id uuid NOT NULL,
    decided_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_selection_decisions_round_choice UNIQUE (selection_round_id, programme_choice_id),
    CONSTRAINT ck_selection_decisions_decision CHECK (decision IN ('SHORTLIST', 'SELECT', 'REJECT', 'WAITLIST')),
    CONSTRAINT ck_selection_decisions_rank CHECK (rank_position IS NULL OR rank_position > 0),
    CONSTRAINT ck_selection_decisions_reason CHECK (length(trim(reason)) > 0)
);

CREATE UNIQUE INDEX uk_selection_decisions_selected_choice
    ON selection_decisions (programme_choice_id)
    WHERE decision = 'SELECT' AND deleted_at IS NULL;

CREATE TABLE offer_batches (
    id uuid PRIMARY KEY,
    admission_cycle_id uuid NOT NULL REFERENCES admission_cycles (id),
    selection_round_id uuid NOT NULL REFERENCES selection_rounds (id),
    code varchar(50) NOT NULL,
    name varchar(180) NOT NULL,
    scope_type varchar(30) NOT NULL,
    scope_id uuid,
    status varchar(30) NOT NULL,
    approved_by_user_id uuid,
    approved_at timestamptz,
    dispatched_at timestamptz,
    closed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_offer_batches_cycle_code UNIQUE (admission_cycle_id, code),
    CONSTRAINT ck_offer_batches_scope CHECK (
        (scope_type = 'INSTITUTION' AND scope_id IS NULL)
        OR (scope_type IN ('ACADEMIC_UNIT', 'PROGRAMME') AND scope_id IS NOT NULL)
    ),
    CONSTRAINT ck_offer_batches_status CHECK (status IN ('DRAFT', 'APPROVED', 'DISPATCHED', 'CLOSED')),
    CONSTRAINT ck_offer_batches_approval CHECK (
        (status IN ('APPROVED', 'DISPATCHED', 'CLOSED') AND approved_at IS NOT NULL AND approved_by_user_id IS NOT NULL)
        OR status = 'DRAFT'
    )
);

CREATE TABLE offers (
    id uuid PRIMARY KEY,
    application_id uuid NOT NULL REFERENCES applications (id),
    programme_choice_id uuid NOT NULL REFERENCES application_programme_choices (id),
    offer_batch_id uuid NOT NULL REFERENCES offer_batches (id),
    programme_id uuid NOT NULL,
    programme_version_id uuid NOT NULL,
    programme_code varchar(50) NOT NULL,
    programme_name varchar(200) NOT NULL,
    intake_id uuid NOT NULL,
    offer_number varchar(60) NOT NULL,
    offer_type varchar(30) NOT NULL,
    status varchar(30) NOT NULL,
    conditions_text varchar(4000),
    acceptance_deadline timestamptz NOT NULL,
    registration_date date,
    orientation_date date,
    commencement_date date NOT NULL,
    generated_document_id uuid,
    approved_by_user_id uuid,
    approved_at timestamptz,
    sent_at timestamptz,
    withdrawn_by_user_id uuid,
    withdrawal_reason varchar(1000),
    converted_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_offers_offer_number UNIQUE (offer_number),
    CONSTRAINT ck_offers_type CHECK (offer_type IN ('FIRM', 'CONDITIONAL')),
    CONSTRAINT ck_offers_status CHECK (status IN ('DRAFT', 'APPROVED', 'SENT', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'WITHDRAWN', 'CONVERTED')),
    CONSTRAINT ck_offers_dates CHECK (
        (orientation_date IS NULL OR orientation_date <= commencement_date)
        AND (registration_date IS NULL OR registration_date <= commencement_date)
    ),
    CONSTRAINT ck_offers_approval CHECK (
        (status IN ('APPROVED', 'SENT', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'CONVERTED')
            AND approved_at IS NOT NULL AND approved_by_user_id IS NOT NULL)
        OR status IN ('DRAFT', 'WITHDRAWN')
    ),
    CONSTRAINT ck_offers_conditional_text CHECK (
        offer_type <> 'CONDITIONAL' OR length(trim(coalesce(conditions_text, ''))) > 0
    )
);

CREATE UNIQUE INDEX uk_offers_active_application_programme
    ON offers (application_id, programme_id)
    WHERE deleted_at IS NULL AND status NOT IN ('DECLINED', 'EXPIRED', 'WITHDRAWN');

CREATE TABLE offer_conditions (
    id uuid PRIMARY KEY,
    offer_id uuid NOT NULL REFERENCES offers (id),
    condition_code varchar(60) NOT NULL,
    description varchar(1000) NOT NULL,
    required boolean NOT NULL,
    status varchar(30) NOT NULL,
    satisfied_by_user_id uuid,
    satisfied_at timestamptz,
    resolution_notes varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_offer_conditions_offer_code UNIQUE (offer_id, condition_code),
    CONSTRAINT ck_offer_conditions_status CHECK (status IN ('PENDING', 'SATISFIED', 'WAIVED')),
    CONSTRAINT ck_offer_conditions_resolution CHECK (
        (status = 'PENDING' AND satisfied_at IS NULL AND satisfied_by_user_id IS NULL)
        OR (status IN ('SATISFIED', 'WAIVED') AND satisfied_at IS NOT NULL AND satisfied_by_user_id IS NOT NULL)
    )
);

CREATE TABLE offer_dispatches (
    id uuid PRIMARY KEY,
    offer_id uuid NOT NULL REFERENCES offers (id),
    delivery_method_code varchar(40) NOT NULL,
    sent_to varchar(250) NOT NULL,
    sent_at timestamptz NOT NULL,
    status varchar(30) NOT NULL,
    provider_message_id varchar(200),
    failure_reason varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_offer_dispatches_status CHECK (status IN ('SENT', 'DELIVERED', 'FAILED', 'BOUNCED')),
    CONSTRAINT ck_offer_dispatches_failure CHECK (status NOT IN ('FAILED', 'BOUNCED') OR length(trim(coalesce(failure_reason, ''))) > 0)
);

CREATE UNIQUE INDEX uk_offer_dispatches_provider_message
    ON offer_dispatches (delivery_method_code, provider_message_id)
    WHERE provider_message_id IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE offer_responses (
    id uuid PRIMARY KEY,
    offer_id uuid NOT NULL REFERENCES offers (id),
    response varchar(30) NOT NULL,
    responded_at timestamptz NOT NULL,
    responded_by_user_id uuid NOT NULL,
    notes varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_offer_responses_offer UNIQUE (offer_id),
    CONSTRAINT ck_offer_responses_response CHECK (response IN ('ACCEPTED', 'DECLINED'))
);

CREATE TABLE offer_status_events (
    id uuid PRIMARY KEY,
    offer_id uuid NOT NULL REFERENCES offers (id),
    from_status varchar(30),
    to_status varchar(30) NOT NULL,
    reason varchar(1000) NOT NULL,
    changed_by_user_id uuid NOT NULL,
    changed_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);

CREATE TABLE selection_rounds_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    admission_cycle_id uuid, code varchar(50), name varchar(180), status varchar(30),
    opened_at timestamptz, approved_at timestamptz, approved_by_user_id uuid, closed_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE selection_decisions_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    selection_round_id uuid, programme_choice_id uuid, decision varchar(30), rank_position integer,
    quota_type_code varchar(50), reason varchar(1000), decided_by_user_id uuid, decided_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE offer_batches_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    admission_cycle_id uuid, selection_round_id uuid, code varchar(50), name varchar(180), scope_type varchar(30),
    scope_id uuid, status varchar(30), approved_by_user_id uuid, approved_at timestamptz,
    dispatched_at timestamptz, closed_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE offers_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    application_id uuid, programme_choice_id uuid, offer_batch_id uuid, programme_id uuid, programme_version_id uuid,
    programme_code varchar(50), programme_name varchar(200), intake_id uuid, offer_number varchar(60),
    offer_type varchar(30), status varchar(30), conditions_text varchar(4000), acceptance_deadline timestamptz,
    registration_date date, orientation_date date, commencement_date date, generated_document_id uuid,
    approved_by_user_id uuid, approved_at timestamptz, sent_at timestamptz,
    withdrawn_by_user_id uuid, withdrawal_reason varchar(1000), converted_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE offer_conditions_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    offer_id uuid, condition_code varchar(60), description varchar(1000), required boolean, status varchar(30),
    satisfied_by_user_id uuid, satisfied_at timestamptz, resolution_notes varchar(1000),
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE offer_dispatches_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    offer_id uuid, delivery_method_code varchar(40), sent_to varchar(250), sent_at timestamptz,
    status varchar(30), provider_message_id varchar(200), failure_reason varchar(1000),
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE offer_responses_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    offer_id uuid, response varchar(30), responded_at timestamptz, responded_by_user_id uuid, notes varchar(1000),
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

CREATE TABLE offer_status_events_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo (rev), revtype smallint,
    offer_id uuid, from_status varchar(30), to_status varchar(30), reason varchar(1000),
    changed_by_user_id uuid, changed_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint,
    PRIMARY KEY (id, rev)
);

CREATE OR REPLACE FUNCTION validate_selection_round_transition()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status = NEW.status THEN
        RETURN NEW;
    END IF;
    IF NOT (
        (OLD.status = 'DRAFT' AND NEW.status = 'OPEN')
        OR (OLD.status = 'OPEN' AND NEW.status IN ('APPROVED', 'CLOSED'))
        OR (OLD.status = 'APPROVED' AND NEW.status = 'CLOSED')
    ) THEN
        RAISE EXCEPTION 'Invalid selection round transition from % to %', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_selection_round_transition
BEFORE UPDATE OF status ON selection_rounds
FOR EACH ROW EXECUTE FUNCTION validate_selection_round_transition();

CREATE OR REPLACE FUNCTION validate_selection_decision()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    round_status varchar(30);
    current_choice_status varchar(30);
BEGIN
    SELECT status INTO round_status FROM selection_rounds WHERE id = NEW.selection_round_id AND deleted_at IS NULL;
    IF round_status IS DISTINCT FROM 'OPEN' THEN
        RAISE EXCEPTION 'Selection decisions can only be recorded in an open selection round';
    END IF;
    SELECT choice_status INTO current_choice_status
    FROM application_programme_choices WHERE id = NEW.programme_choice_id AND deleted_at IS NULL;
    IF NEW.decision IN ('SHORTLIST', 'SELECT', 'WAITLIST')
       AND current_choice_status NOT IN ('ELIGIBLE', 'SHORTLISTED', 'WAITLISTED') THEN
        RAISE EXCEPTION 'Only eligible programme choices can be shortlisted, selected, or waitlisted';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_selection_decision_guard
BEFORE INSERT OR UPDATE ON selection_decisions
FOR EACH ROW EXECUTE FUNCTION validate_selection_decision();

CREATE OR REPLACE FUNCTION validate_offer_batch_transition()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status = NEW.status THEN
        RETURN NEW;
    END IF;
    IF NOT (
        (OLD.status = 'DRAFT' AND NEW.status = 'APPROVED')
        OR (OLD.status = 'APPROVED' AND NEW.status IN ('DISPATCHED', 'CLOSED'))
        OR (OLD.status = 'DISPATCHED' AND NEW.status = 'CLOSED')
    ) THEN
        RAISE EXCEPTION 'Invalid offer batch transition from % to %', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_offer_batch_transition
BEFORE UPDATE OF status ON offer_batches
FOR EACH ROW EXECUTE FUNCTION validate_offer_batch_transition();

CREATE OR REPLACE FUNCTION validate_offer_source()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    source_is_approved boolean;
    source_application_id uuid;
    source_programme_id uuid;
    source_programme_version_id uuid;
    source_intake_id uuid;
BEGIN
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
          AND batch.deleted_at IS NULL
    ) INTO source_is_approved;
    IF NOT source_is_approved THEN
        RAISE EXCEPTION 'Offers require an approved selected decision in the batch selection round';
    END IF;

    SELECT choice.application_id, choice.programme_id, choice.programme_version_id, cycle.intake_id
    INTO source_application_id, source_programme_id, source_programme_version_id, source_intake_id
    FROM application_programme_choices choice
    JOIN applications application_record ON application_record.id = choice.application_id
    JOIN admission_cycles cycle ON cycle.id = application_record.admission_cycle_id
    WHERE choice.id = NEW.programme_choice_id
      AND choice.choice_status = 'SELECTED'
      AND application_record.status = 'SELECTED';

    IF source_application_id IS NULL
       OR source_application_id <> NEW.application_id
       OR source_programme_id <> NEW.programme_id
       OR source_programme_version_id <> NEW.programme_version_id
       OR source_intake_id <> NEW.intake_id THEN
        RAISE EXCEPTION 'Offer source does not match the selected application and programme snapshot';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_offer_source_guard
BEFORE INSERT OR UPDATE OF application_id, programme_choice_id, offer_batch_id, programme_id, programme_version_id, intake_id
ON offers FOR EACH ROW EXECUTE FUNCTION validate_offer_source();

CREATE OR REPLACE FUNCTION validate_offer_transition()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status = NEW.status THEN
        RETURN NEW;
    END IF;
    IF NOT (
        (OLD.status = 'DRAFT' AND NEW.status IN ('APPROVED', 'WITHDRAWN'))
        OR (OLD.status = 'APPROVED' AND NEW.status IN ('SENT', 'WITHDRAWN'))
        OR (OLD.status = 'SENT' AND NEW.status IN ('ACCEPTED', 'DECLINED', 'EXPIRED', 'WITHDRAWN'))
        OR (OLD.status = 'ACCEPTED' AND NEW.status = 'CONVERTED')
    ) THEN
        RAISE EXCEPTION 'Invalid offer transition from % to %', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_offer_transition
BEFORE UPDATE OF status ON offers
FOR EACH ROW EXECUTE FUNCTION validate_offer_transition();

CREATE OR REPLACE FUNCTION validate_offer_response()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    current_offer_status varchar(30);
    current_deadline timestamptz;
BEGIN
    SELECT status, acceptance_deadline INTO current_offer_status, current_deadline
    FROM offers WHERE id = NEW.offer_id AND deleted_at IS NULL FOR UPDATE;
    IF current_offer_status IS DISTINCT FROM 'SENT' THEN
        RAISE EXCEPTION 'Only a sent offer can receive an applicant response';
    END IF;
    IF NEW.responded_at > current_deadline THEN
        RAISE EXCEPTION 'The offer acceptance deadline has passed';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_offer_response_guard
BEFORE INSERT ON offer_responses
FOR EACH ROW EXECUTE FUNCTION validate_offer_response();

CREATE OR REPLACE FUNCTION prohibit_offer_response_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Offer responses are immutable';
END;
$$;

CREATE TRIGGER trg_offer_response_immutable
BEFORE UPDATE OR DELETE ON offer_responses
FOR EACH ROW EXECUTE FUNCTION prohibit_offer_response_mutation();
