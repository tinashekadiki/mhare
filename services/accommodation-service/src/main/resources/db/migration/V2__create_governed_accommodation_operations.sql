-- Author: Tinashe K

CREATE SEQUENCE accommodation_application_number_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE accommodation_allocation_number_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE accommodation_damage_number_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE accommodation_swap_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE accommodation_premises (
    id uuid PRIMARY KEY, code varchar(40) NOT NULL, name varchar(160) NOT NULL,
    address_line varchar(300) NOT NULL, suburb varchar(120), landlord_name varchar(160),
    contact_details varchar(500), active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL
);
CREATE UNIQUE INDEX uk_accommodation_premise_code ON accommodation_premises(lower(code)) WHERE deleted_at IS NULL;

CREATE TABLE accommodation_room_types (
    id uuid PRIMARY KEY, code varchar(40) NOT NULL, name varchar(120) NOT NULL,
    description varchar(500), default_capacity integer NOT NULL, active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_accommodation_room_type_capacity CHECK (default_capacity > 0)
);
CREATE UNIQUE INDEX uk_accommodation_room_type_code ON accommodation_room_types(lower(code)) WHERE deleted_at IS NULL;

CREATE TABLE residence_halls (
    id uuid PRIMARY KEY, premise_id uuid NOT NULL REFERENCES accommodation_premises(id),
    code varchar(40) NOT NULL, name varchar(160) NOT NULL,
    resident_gender_policy varchar(20) NOT NULL DEFAULT 'ANY',
    warden_name varchar(160), warden_contact varchar(160), active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_residence_hall_gender CHECK (resident_gender_policy IN ('ANY','FEMALE','MALE'))
);
CREATE UNIQUE INDEX uk_residence_hall_code ON residence_halls(premise_id,lower(code)) WHERE deleted_at IS NULL;

CREATE TABLE accommodation_rooms (
    id uuid PRIMARY KEY, residence_hall_id uuid NOT NULL REFERENCES residence_halls(id),
    room_type_id uuid NOT NULL REFERENCES accommodation_room_types(id), code varchar(40) NOT NULL,
    floor_label varchar(40), capacity integer NOT NULL, accessibility_ready boolean NOT NULL DEFAULT false,
    condition_status varchar(20) NOT NULL DEFAULT 'AVAILABLE', condition_notes varchar(500),
    reserved_for_group_id uuid, active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_accommodation_room_capacity CHECK (capacity > 0),
    CONSTRAINT ck_accommodation_room_condition CHECK (condition_status IN ('AVAILABLE','MAINTENANCE','OUT_OF_SERVICE'))
);
CREATE UNIQUE INDEX uk_accommodation_room_code ON accommodation_rooms(residence_hall_id,lower(code)) WHERE deleted_at IS NULL;

CREATE TABLE accommodation_room_facilities (
    id uuid PRIMARY KEY, code varchar(40) NOT NULL, name varchar(120) NOT NULL,
    description varchar(500), active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL
);
CREATE UNIQUE INDEX uk_accommodation_facility_code ON accommodation_room_facilities(lower(code)) WHERE deleted_at IS NULL;

CREATE TABLE accommodation_room_facility_assignments (
    id uuid PRIMARY KEY, room_id uuid NOT NULL REFERENCES accommodation_rooms(id),
    facility_id uuid NOT NULL REFERENCES accommodation_room_facilities(id), quantity integer NOT NULL DEFAULT 1,
    condition_notes varchar(500),
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_accommodation_facility_quantity CHECK (quantity > 0)
);
CREATE UNIQUE INDEX uk_accommodation_room_facility ON accommodation_room_facility_assignments(room_id,facility_id) WHERE deleted_at IS NULL;

CREATE TABLE accommodation_application_periods (
    id uuid PRIMARY KEY, academic_period_id uuid NOT NULL, academic_period_code varchar(50) NOT NULL,
    code varchar(40) NOT NULL, name varchar(160) NOT NULL,
    applications_open_at timestamptz NOT NULL, applications_close_at timestamptz NOT NULL,
    occupancy_starts_on date NOT NULL, occupancy_ends_on date NOT NULL,
    allocation_cutoff_at timestamptz NOT NULL, status varchar(30) NOT NULL DEFAULT 'DRAFT',
    prepared_by_user_id uuid NOT NULL, approved_by_user_id uuid, approved_at timestamptz,
    approval_reason varchar(1000),
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_accommodation_period_dates CHECK (
        applications_close_at > applications_open_at AND occupancy_ends_on >= occupancy_starts_on
        AND allocation_cutoff_at >= applications_close_at),
    CONSTRAINT ck_accommodation_period_status CHECK (status IN ('DRAFT','APPLICATION_OPEN','APPLICATION_CLOSED','ALLOCATION_ACTIVE','CLOSED')),
    CONSTRAINT ck_accommodation_period_approval CHECK (
        (status='DRAFT' AND approved_by_user_id IS NULL AND approved_at IS NULL AND approval_reason IS NULL)
        OR (status<>'DRAFT' AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL
            AND length(trim(approval_reason)) > 0 AND approved_by_user_id <> prepared_by_user_id))
);
CREATE UNIQUE INDEX uk_accommodation_period_code ON accommodation_application_periods(academic_period_id,lower(code)) WHERE deleted_at IS NULL;

CREATE TABLE accommodation_rates (
    id uuid PRIMARY KEY, application_period_id uuid NOT NULL REFERENCES accommodation_application_periods(id),
    room_type_id uuid NOT NULL REFERENCES accommodation_room_types(id), rate_version integer NOT NULL,
    finance_fee_catalogue_id uuid NOT NULL, transaction_currency_code char(3) NOT NULL,
    indicative_transaction_amount numeric(19,4) NOT NULL, base_currency_code char(3) NOT NULL DEFAULT 'USD',
    exchange_rate_id uuid, indicative_base_amount numeric(19,4), rating_status varchar(20) NOT NULL,
    effective_from timestamptz NOT NULL, effective_until timestamptz,
    status varchar(20) NOT NULL DEFAULT 'DRAFT', prepared_by_user_id uuid NOT NULL,
    approved_by_user_id uuid, approved_at timestamptz, approval_reason varchar(1000),
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT uk_accommodation_rate_version UNIQUE(application_period_id,room_type_id,rate_version),
    CONSTRAINT ck_accommodation_rate_amount CHECK (indicative_transaction_amount > 0 AND (indicative_base_amount IS NULL OR indicative_base_amount > 0)),
    CONSTRAINT ck_accommodation_rate_window CHECK (effective_until IS NULL OR effective_until > effective_from),
    CONSTRAINT ck_accommodation_rate_currency CHECK (base_currency_code='USD'),
    CONSTRAINT ck_accommodation_rate_rating CHECK (
        (transaction_currency_code='USD' AND rating_status='RATED' AND exchange_rate_id IS NULL
            AND indicative_base_amount=indicative_transaction_amount)
        OR (transaction_currency_code<>'USD' AND rating_status='RATED' AND exchange_rate_id IS NOT NULL AND indicative_base_amount IS NOT NULL)
        OR (transaction_currency_code<>'USD' AND rating_status='UNRATED' AND exchange_rate_id IS NULL AND indicative_base_amount IS NULL)),
    CONSTRAINT ck_accommodation_rate_status CHECK (status IN ('DRAFT','ACTIVE','RETIRED')),
    CONSTRAINT ck_accommodation_rate_activation CHECK (status<>'ACTIVE' OR rating_status='RATED'),
    CONSTRAINT ck_accommodation_rate_approval CHECK (
        (status='DRAFT' AND approved_by_user_id IS NULL AND approved_at IS NULL AND approval_reason IS NULL)
        OR (status IN ('ACTIVE','RETIRED') AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL
            AND length(trim(approval_reason)) > 0 AND approved_by_user_id <> prepared_by_user_id))
);
CREATE UNIQUE INDEX uk_accommodation_active_rate ON accommodation_rates(application_period_id,room_type_id)
    WHERE status='ACTIVE' AND deleted_at IS NULL;

CREATE TABLE accommodation_groups (
    id uuid PRIMARY KEY, application_period_id uuid NOT NULL REFERENCES accommodation_application_periods(id),
    code varchar(40) NOT NULL, name varchar(160) NOT NULL, description varchar(500),
    priority_rank integer NOT NULL, reserved_bed_count integer NOT NULL DEFAULT 0, active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_accommodation_group_priority CHECK (priority_rank > 0 AND reserved_bed_count >= 0)
);
CREATE UNIQUE INDEX uk_accommodation_group_code ON accommodation_groups(application_period_id,lower(code)) WHERE deleted_at IS NULL;
ALTER TABLE accommodation_rooms ADD CONSTRAINT fk_accommodation_room_reserved_group
    FOREIGN KEY (reserved_for_group_id) REFERENCES accommodation_groups(id);

CREATE TABLE accommodation_group_rules (
    id uuid PRIMARY KEY, accommodation_group_id uuid NOT NULL REFERENCES accommodation_groups(id),
    rule_dimension varchar(30) NOT NULL, comparison_operator varchar(20) NOT NULL,
    comparison_value varchar(200) NOT NULL, mandatory boolean NOT NULL DEFAULT true,
    priority_points integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_accommodation_rule_dimension CHECK (rule_dimension IN ('DISABILITY','GENDER','PROGRAMME','SPONSOR','LEVEL','LOCATION','PAYMENT_STATE','COUNTRY','PRIORITY')),
    CONSTRAINT ck_accommodation_rule_operator CHECK (comparison_operator IN ('EQUALS','NOT_EQUALS','IN','NOT_IN','PRESENT')),
    CONSTRAINT ck_accommodation_rule_points CHECK (priority_points BETWEEN -10000 AND 10000)
);

CREATE TABLE accommodation_blacklist_entries (
    id uuid PRIMARY KEY, student_id uuid NOT NULL, student_number varchar(40) NOT NULL,
    reason_code varchar(50) NOT NULL, reason varchar(1000) NOT NULL,
    effective_from date NOT NULL, effective_until date, status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    imposed_by_user_id uuid NOT NULL, imposed_at timestamptz NOT NULL,
    lifted_by_user_id uuid, lifted_at timestamptz, lift_reason varchar(1000),
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_accommodation_blacklist_window CHECK (effective_until IS NULL OR effective_until >= effective_from),
    CONSTRAINT ck_accommodation_blacklist_status CHECK (status IN ('ACTIVE','LIFTED','EXPIRED')),
    CONSTRAINT ck_accommodation_blacklist_lift CHECK (
        (status='ACTIVE' AND lifted_by_user_id IS NULL AND lifted_at IS NULL AND lift_reason IS NULL)
        OR (status<>'ACTIVE' AND lifted_by_user_id IS NOT NULL AND lifted_at IS NOT NULL
            AND length(trim(lift_reason)) > 0 AND lifted_by_user_id <> imposed_by_user_id))
);
CREATE UNIQUE INDEX uk_accommodation_active_blacklist ON accommodation_blacklist_entries(student_id)
    WHERE status='ACTIVE' AND deleted_at IS NULL;

CREATE TABLE accommodation_applications (
    id uuid PRIMARY KEY, application_number varchar(60) NOT NULL UNIQUE,
    application_period_id uuid NOT NULL REFERENCES accommodation_application_periods(id),
    student_id uuid NOT NULL, student_number varchar(40) NOT NULL, student_name varchar(200) NOT NULL,
    primary_email varchar(254) NOT NULL, gender_code varchar(20) NOT NULL, disability_code varchar(80),
    country_code char(3) NOT NULL, location_code varchar(80), programme_id uuid NOT NULL,
    programme_code varchar(50) NOT NULL, programme_name varchar(200) NOT NULL,
    programme_level integer NOT NULL, sponsor_code varchar(80), payment_state varchar(30) NOT NULL,
    preferred_room_type_id uuid REFERENCES accommodation_room_types(id), special_requirements varchar(1000),
    priority_score integer NOT NULL DEFAULT 0, status varchar(30) NOT NULL DEFAULT 'SUBMITTED',
    submitted_at timestamptz NOT NULL, evaluated_by_user_id uuid, evaluated_at timestamptz,
    evaluation_reason varchar(1000), selected_group_id uuid REFERENCES accommodation_groups(id),
    withdrawn_by_user_id uuid, withdrawn_at timestamptz, withdrawal_reason varchar(1000),
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT uk_accommodation_application_student UNIQUE(application_period_id,student_id),
    CONSTRAINT ck_accommodation_application_level CHECK (programme_level > 0),
    CONSTRAINT ck_accommodation_application_payment CHECK (payment_state IN ('PAID','WAIVED','PART_PAID','UNPAID','UNKNOWN')),
    CONSTRAINT ck_accommodation_application_status CHECK (status IN ('SUBMITTED','ELIGIBLE','WAITLISTED','ALLOCATED','REJECTED','WITHDRAWN')),
    CONSTRAINT ck_accommodation_application_evaluation CHECK (
        (status='SUBMITTED' AND evaluated_by_user_id IS NULL AND evaluated_at IS NULL AND evaluation_reason IS NULL)
        OR (status IN ('ELIGIBLE','WAITLISTED','ALLOCATED','REJECTED') AND evaluated_by_user_id IS NOT NULL
            AND evaluated_at IS NOT NULL AND length(trim(evaluation_reason)) > 0)
        OR status='WITHDRAWN'),
    CONSTRAINT ck_accommodation_application_withdrawal CHECK (
        (status<>'WITHDRAWN' AND withdrawn_by_user_id IS NULL AND withdrawn_at IS NULL AND withdrawal_reason IS NULL)
        OR (status='WITHDRAWN' AND withdrawn_by_user_id IS NOT NULL AND withdrawn_at IS NOT NULL AND length(trim(withdrawal_reason)) > 0))
);

CREATE TABLE accommodation_waitlist_entries (
    id uuid PRIMARY KEY, accommodation_application_id uuid NOT NULL REFERENCES accommodation_applications(id),
    application_period_id uuid NOT NULL REFERENCES accommodation_application_periods(id),
    waitlist_position integer NOT NULL, priority_score integer NOT NULL, status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    entered_by_user_id uuid NOT NULL, entered_at timestamptz NOT NULL,
    removed_by_user_id uuid, removed_at timestamptz, removal_reason varchar(1000),
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_accommodation_waitlist_position CHECK (waitlist_position > 0),
    CONSTRAINT ck_accommodation_waitlist_status CHECK (status IN ('ACTIVE','ALLOCATED','WITHDRAWN','REMOVED')),
    CONSTRAINT ck_accommodation_waitlist_removal CHECK (
        (status='ACTIVE' AND removed_by_user_id IS NULL AND removed_at IS NULL AND removal_reason IS NULL)
        OR (status<>'ACTIVE' AND removed_by_user_id IS NOT NULL AND removed_at IS NOT NULL AND length(trim(removal_reason)) > 0))
);
CREATE UNIQUE INDEX uk_accommodation_active_waitlist_application ON accommodation_waitlist_entries(accommodation_application_id)
    WHERE status='ACTIVE' AND deleted_at IS NULL;
CREATE UNIQUE INDEX uk_accommodation_active_waitlist_position ON accommodation_waitlist_entries(application_period_id,
    waitlist_position) WHERE status='ACTIVE' AND deleted_at IS NULL;

CREATE TABLE room_allocations (
    id uuid PRIMARY KEY, allocation_number varchar(60) NOT NULL UNIQUE,
    accommodation_application_id uuid NOT NULL REFERENCES accommodation_applications(id),
    room_id uuid NOT NULL REFERENCES accommodation_rooms(id), accommodation_rate_id uuid NOT NULL REFERENCES accommodation_rates(id),
    occupancy_starts_on date NOT NULL, occupancy_ends_on date NOT NULL, status varchar(30) NOT NULL DEFAULT 'PROPOSED',
    allocated_by_user_id uuid NOT NULL, allocated_at timestamptz NOT NULL,
    approved_by_user_id uuid, approved_at timestamptz, approval_reason varchar(1000),
    checked_in_by_user_id uuid, checked_in_at timestamptz, check_in_notes varchar(1000),
    checked_out_by_user_id uuid, checked_out_at timestamptz, check_out_notes varchar(1000),
    ended_by_user_id uuid, ended_at timestamptz, end_reason varchar(1000),
    billing_event_id uuid, billing_status varchar(20) NOT NULL DEFAULT 'NOT_REQUESTED',
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_room_allocation_dates CHECK (occupancy_ends_on >= occupancy_starts_on),
    CONSTRAINT ck_room_allocation_status CHECK (status IN ('PROPOSED','ALLOCATED','CHECKED_IN','CHECKED_OUT','WITHDRAWN','CANCELLED')),
    CONSTRAINT ck_room_allocation_approval CHECK (
        (status='PROPOSED' AND approved_by_user_id IS NULL AND approved_at IS NULL AND approval_reason IS NULL)
        OR (status='CANCELLED' AND approved_by_user_id IS NULL AND approved_at IS NULL AND approval_reason IS NULL)
        OR (status<>'PROPOSED' AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL
            AND length(trim(approval_reason)) > 0 AND approved_by_user_id <> allocated_by_user_id)),
    CONSTRAINT ck_room_allocation_checkin CHECK (
        (status IN ('PROPOSED','ALLOCATED','CANCELLED') AND checked_in_by_user_id IS NULL
            AND checked_in_at IS NULL AND check_in_notes IS NULL)
        OR (status IN ('CHECKED_IN','CHECKED_OUT','WITHDRAWN') AND checked_in_by_user_id IS NOT NULL
            AND checked_in_at IS NOT NULL AND length(trim(check_in_notes)) > 0)),
    CONSTRAINT ck_room_allocation_checkout CHECK (
        (status<>'CHECKED_OUT' AND checked_out_by_user_id IS NULL AND checked_out_at IS NULL AND check_out_notes IS NULL)
        OR (status='CHECKED_OUT' AND checked_out_by_user_id IS NOT NULL AND checked_out_at IS NOT NULL
            AND length(trim(check_out_notes)) > 0 AND checked_out_by_user_id <> checked_in_by_user_id)),
    CONSTRAINT ck_room_allocation_ending CHECK (
        (status NOT IN ('WITHDRAWN','CANCELLED') AND ended_by_user_id IS NULL AND ended_at IS NULL AND end_reason IS NULL)
        OR (status IN ('WITHDRAWN','CANCELLED') AND ended_by_user_id IS NOT NULL AND ended_at IS NOT NULL
            AND length(trim(end_reason)) > 0)),
    CONSTRAINT ck_room_allocation_billing CHECK (billing_status IN ('NOT_REQUESTED','PENDING','ACCEPTED','FAILED'))
);
CREATE UNIQUE INDEX uk_room_allocation_active_student ON room_allocations(accommodation_application_id)
    WHERE status IN ('PROPOSED','ALLOCATED','CHECKED_IN') AND deleted_at IS NULL;
CREATE INDEX idx_room_allocation_occupancy ON room_allocations(room_id,occupancy_starts_on,occupancy_ends_on)
    WHERE status IN ('PROPOSED','ALLOCATED','CHECKED_IN') AND deleted_at IS NULL;

CREATE TABLE room_allocation_events (
    id uuid PRIMARY KEY, room_allocation_id uuid NOT NULL REFERENCES room_allocations(id),
    previous_status varchar(30), new_status varchar(30) NOT NULL, event_type varchar(30) NOT NULL,
    from_room_id uuid REFERENCES accommodation_rooms(id), to_room_id uuid REFERENCES accommodation_rooms(id),
    reason varchar(1000) NOT NULL, actor_user_id uuid NOT NULL, occurred_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_room_allocation_event_type CHECK (event_type IN ('PROPOSED','APPROVED','CHECKED_IN','CHECKED_OUT','MOVED','WITHDRAWN','CANCELLED','BILLING_REQUESTED','BILLING_ACCEPTED','BILLING_FAILED'))
);

CREATE TABLE room_swaps (
    id uuid PRIMARY KEY, swap_number varchar(60) NOT NULL UNIQUE,
    first_allocation_id uuid NOT NULL REFERENCES room_allocations(id), second_allocation_id uuid NOT NULL REFERENCES room_allocations(id),
    first_original_room_id uuid NOT NULL REFERENCES accommodation_rooms(id),
    second_original_room_id uuid NOT NULL REFERENCES accommodation_rooms(id),
    status varchar(20) NOT NULL DEFAULT 'REQUESTED', reason varchar(1000) NOT NULL,
    requested_by_user_id uuid NOT NULL, requested_at timestamptz NOT NULL,
    approved_by_user_id uuid, approved_at timestamptz, approval_reason varchar(1000),
    completed_by_user_id uuid, completed_at timestamptz,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_room_swap_distinct CHECK (first_allocation_id <> second_allocation_id),
    CONSTRAINT ck_room_swap_distinct_rooms CHECK (first_original_room_id <> second_original_room_id),
    CONSTRAINT ck_room_swap_status CHECK (status IN ('REQUESTED','APPROVED','COMPLETED','REJECTED','CANCELLED')),
    CONSTRAINT ck_room_swap_approval CHECK ((status='REQUESTED' AND approved_by_user_id IS NULL)
        OR status IN ('REJECTED','CANCELLED')
        OR (status IN ('APPROVED','COMPLETED') AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL
            AND length(trim(approval_reason)) > 0 AND approved_by_user_id <> requested_by_user_id)),
    CONSTRAINT ck_room_swap_completion CHECK (
        (status<>'COMPLETED' AND completed_by_user_id IS NULL AND completed_at IS NULL)
        OR (status='COMPLETED' AND completed_by_user_id IS NOT NULL AND completed_at IS NOT NULL
            AND completed_by_user_id <> requested_by_user_id AND completed_by_user_id <> approved_by_user_id))
);

CREATE TABLE accommodation_damage_records (
    id uuid PRIMARY KEY, damage_number varchar(60) NOT NULL UNIQUE,
    room_allocation_id uuid NOT NULL REFERENCES room_allocations(id), room_id uuid NOT NULL REFERENCES accommodation_rooms(id),
    description varchar(1000) NOT NULL, evidence_document_id uuid, status varchar(20) NOT NULL DEFAULT 'REPORTED',
    estimated_transaction_amount numeric(19,4), transaction_currency_code char(3),
    estimated_base_amount numeric(19,4), base_currency_code char(3), exchange_rate_id uuid,
    reported_by_user_id uuid NOT NULL, reported_at timestamptz NOT NULL,
    assessed_by_user_id uuid, assessed_at timestamptz, assessment_reason varchar(1000),
    resolved_by_user_id uuid, resolved_at timestamptz, resolution_reason varchar(1000),
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_accommodation_damage_status CHECK (status IN ('REPORTED','ASSESSED','CHARGE_PENDING','CHARGED','WAIVED','RESOLVED')),
    CONSTRAINT ck_accommodation_damage_currency CHECK (base_currency_code IS NULL OR base_currency_code='USD'),
    CONSTRAINT ck_accommodation_damage_amount CHECK (estimated_transaction_amount IS NULL OR estimated_transaction_amount > 0),
    CONSTRAINT ck_accommodation_damage_rating CHECK (
        (estimated_transaction_amount IS NULL AND transaction_currency_code IS NULL AND estimated_base_amount IS NULL
            AND base_currency_code IS NULL AND exchange_rate_id IS NULL)
        OR (transaction_currency_code='USD' AND exchange_rate_id IS NULL AND estimated_base_amount=estimated_transaction_amount)
        OR (transaction_currency_code<>'USD' AND exchange_rate_id IS NOT NULL AND estimated_base_amount IS NOT NULL)
        OR (transaction_currency_code<>'USD' AND exchange_rate_id IS NULL AND estimated_base_amount IS NULL)),
    CONSTRAINT ck_accommodation_damage_assessment CHECK (
        (status='REPORTED' AND assessed_by_user_id IS NULL AND assessed_at IS NULL AND assessment_reason IS NULL)
        OR (status<>'REPORTED' AND assessed_by_user_id IS NOT NULL AND assessed_at IS NOT NULL
            AND length(trim(assessment_reason)) > 0 AND assessed_by_user_id <> reported_by_user_id)),
    CONSTRAINT ck_accommodation_damage_resolution CHECK (
        (status NOT IN ('WAIVED','RESOLVED') AND resolved_by_user_id IS NULL AND resolved_at IS NULL AND resolution_reason IS NULL)
        OR (status IN ('WAIVED','RESOLVED') AND resolved_by_user_id IS NOT NULL AND resolved_at IS NOT NULL
            AND length(trim(resolution_reason)) > 0 AND resolved_by_user_id <> reported_by_user_id
            AND resolved_by_user_id <> assessed_by_user_id)),
    CONSTRAINT ck_accommodation_damage_charge_rating CHECK (
        status NOT IN ('CHARGE_PENDING','CHARGED') OR estimated_base_amount IS NOT NULL)
);

CREATE TABLE integration_outbox (
    id uuid PRIMARY KEY, event_type varchar(160) NOT NULL, routing_key varchar(160) NOT NULL,
    payload jsonb NOT NULL, occurred_at timestamptz NOT NULL, status varchar(20) NOT NULL DEFAULT 'PENDING',
    attempt_count integer NOT NULL DEFAULT 0, next_attempt_at timestamptz NOT NULL, published_at timestamptz,
    last_error varchar(1000), created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_accommodation_outbox_status CHECK (status IN ('PENDING','PUBLISHED','DEAD')),
    CONSTRAINT ck_accommodation_outbox_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_accommodation_outbox_publication CHECK ((status='PUBLISHED' AND published_at IS NOT NULL)
        OR (status<>'PUBLISHED' AND published_at IS NULL))
);
CREATE INDEX idx_accommodation_outbox_dispatch ON integration_outbox(next_attempt_at,occurred_at,id) WHERE status='PENDING';

CREATE TABLE accommodation_premises_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,code varchar(40),name varchar(160),address_line varchar(300),suburb varchar(120),landlord_name varchar(160),contact_details varchar(500),active boolean,created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE accommodation_room_types_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,code varchar(40),name varchar(120),description varchar(500),default_capacity integer,active boolean,created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE residence_halls_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,premise_id uuid,code varchar(40),name varchar(160),resident_gender_policy varchar(20),warden_name varchar(160),warden_contact varchar(160),active boolean,created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE accommodation_rooms_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,residence_hall_id uuid,room_type_id uuid,code varchar(40),floor_label varchar(40),capacity integer,accessibility_ready boolean,condition_status varchar(20),condition_notes varchar(500),reserved_for_group_id uuid,active boolean,created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE accommodation_room_facilities_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,code varchar(40),name varchar(120),description varchar(500),active boolean,created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE accommodation_room_facility_assignments_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,room_id uuid,facility_id uuid,quantity integer,condition_notes varchar(500),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE accommodation_application_periods_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,academic_period_id uuid,academic_period_code varchar(50),code varchar(40),name varchar(160),applications_open_at timestamptz,applications_close_at timestamptz,occupancy_starts_on date,occupancy_ends_on date,allocation_cutoff_at timestamptz,status varchar(30),prepared_by_user_id uuid,approved_by_user_id uuid,approved_at timestamptz,approval_reason varchar(1000),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE accommodation_rates_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,application_period_id uuid,room_type_id uuid,rate_version integer,finance_fee_catalogue_id uuid,transaction_currency_code char(3),indicative_transaction_amount numeric(19,4),base_currency_code char(3),exchange_rate_id uuid,indicative_base_amount numeric(19,4),rating_status varchar(20),effective_from timestamptz,effective_until timestamptz,status varchar(20),prepared_by_user_id uuid,approved_by_user_id uuid,approved_at timestamptz,approval_reason varchar(1000),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE accommodation_groups_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,application_period_id uuid,code varchar(40),name varchar(160),description varchar(500),priority_rank integer,reserved_bed_count integer,active boolean,created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE accommodation_group_rules_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,accommodation_group_id uuid,rule_dimension varchar(30),comparison_operator varchar(20),comparison_value varchar(200),mandatory boolean,priority_points integer,created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE accommodation_blacklist_entries_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,student_id uuid,student_number varchar(40),reason_code varchar(50),reason varchar(1000),effective_from date,effective_until date,status varchar(20),imposed_by_user_id uuid,imposed_at timestamptz,lifted_by_user_id uuid,lifted_at timestamptz,lift_reason varchar(1000),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE accommodation_applications_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,application_number varchar(60),application_period_id uuid,student_id uuid,student_number varchar(40),student_name varchar(200),primary_email varchar(254),gender_code varchar(20),disability_code varchar(80),country_code char(3),location_code varchar(80),programme_id uuid,programme_code varchar(50),programme_name varchar(200),programme_level integer,sponsor_code varchar(80),payment_state varchar(30),preferred_room_type_id uuid,special_requirements varchar(1000),priority_score integer,status varchar(30),submitted_at timestamptz,evaluated_by_user_id uuid,evaluated_at timestamptz,evaluation_reason varchar(1000),selected_group_id uuid,withdrawn_by_user_id uuid,withdrawn_at timestamptz,withdrawal_reason varchar(1000),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE accommodation_waitlist_entries_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,accommodation_application_id uuid,application_period_id uuid,waitlist_position integer,priority_score integer,status varchar(20),entered_by_user_id uuid,entered_at timestamptz,removed_by_user_id uuid,removed_at timestamptz,removal_reason varchar(1000),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE room_allocations_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,allocation_number varchar(60),accommodation_application_id uuid,room_id uuid,accommodation_rate_id uuid,occupancy_starts_on date,occupancy_ends_on date,status varchar(30),allocated_by_user_id uuid,allocated_at timestamptz,approved_by_user_id uuid,approved_at timestamptz,approval_reason varchar(1000),checked_in_by_user_id uuid,checked_in_at timestamptz,check_in_notes varchar(1000),checked_out_by_user_id uuid,checked_out_at timestamptz,check_out_notes varchar(1000),ended_by_user_id uuid,ended_at timestamptz,end_reason varchar(1000),billing_event_id uuid,billing_status varchar(20),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE room_allocation_events_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,room_allocation_id uuid,previous_status varchar(30),new_status varchar(30),event_type varchar(30),from_room_id uuid,to_room_id uuid,reason varchar(1000),actor_user_id uuid,occurred_at timestamptz,created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE room_swaps_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,swap_number varchar(60),first_allocation_id uuid,second_allocation_id uuid,first_original_room_id uuid,second_original_room_id uuid,status varchar(20),reason varchar(1000),requested_by_user_id uuid,requested_at timestamptz,approved_by_user_id uuid,approved_at timestamptz,approval_reason varchar(1000),completed_by_user_id uuid,completed_at timestamptz,created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE accommodation_damage_records_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,damage_number varchar(60),room_allocation_id uuid,room_id uuid,description varchar(1000),evidence_document_id uuid,status varchar(20),estimated_transaction_amount numeric(19,4),transaction_currency_code char(3),estimated_base_amount numeric(19,4),base_currency_code char(3),exchange_rate_id uuid,reported_by_user_id uuid,reported_at timestamptz,assessed_by_user_id uuid,assessed_at timestamptz,assessment_reason varchar(1000),resolved_by_user_id uuid,resolved_at timestamptz,resolution_reason varchar(1000),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));

CREATE OR REPLACE FUNCTION validate_accommodation_application_period() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE period_status varchar(30); opens_at timestamptz; closes_at timestamptz;
BEGIN
 SELECT status,applications_open_at,applications_close_at INTO period_status,opens_at,closes_at
 FROM accommodation_application_periods WHERE id=NEW.application_period_id AND deleted_at IS NULL;
 IF period_status <> 'APPLICATION_OPEN' OR NEW.submitted_at < opens_at OR NEW.submitted_at > closes_at THEN
   RAISE EXCEPTION 'Accommodation application period is not open at the submitted time'; END IF;
 IF EXISTS (SELECT 1 FROM accommodation_blacklist_entries b WHERE b.student_id=NEW.student_id AND b.status='ACTIVE'
      AND b.deleted_at IS NULL AND NEW.submitted_at::date >= b.effective_from
      AND (b.effective_until IS NULL OR NEW.submitted_at::date <= b.effective_until)) THEN
   RAISE EXCEPTION 'Blacklisted student cannot submit an accommodation application'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_validate_accommodation_application BEFORE INSERT ON accommodation_applications
 FOR EACH ROW EXECUTE FUNCTION validate_accommodation_application_period();

CREATE OR REPLACE FUNCTION validate_accommodation_waitlist_period() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE actual_period_id uuid;
BEGIN
 SELECT application_period_id INTO actual_period_id FROM accommodation_applications
 WHERE id=NEW.accommodation_application_id AND deleted_at IS NULL;
 IF actual_period_id IS NULL OR actual_period_id<>NEW.application_period_id THEN
   RAISE EXCEPTION 'Wait-list entry period must match its accommodation application'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_validate_accommodation_waitlist BEFORE INSERT OR UPDATE OF accommodation_application_id,application_period_id
 ON accommodation_waitlist_entries FOR EACH ROW EXECUTE FUNCTION validate_accommodation_waitlist_period();

CREATE OR REPLACE FUNCTION validate_room_allocation_capacity() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE room_capacity integer; room_condition varchar(20); room_active boolean; hall_active boolean;
 premise_active boolean; hall_gender varchar(20); reserved_group_id uuid; app_gender varchar(20);
 app_status varchar(30); app_period uuid; selected_group_id uuid; rate_period uuid; rate_room_type uuid;
 actual_room_type uuid; period_status varchar(30); period_occupancy_start date; period_occupancy_end date;
 allocation_cutoff timestamptz; rate_effective_from timestamptz; rate_effective_until timestamptz;
BEGIN
 SELECT r.capacity,r.condition_status,r.active,h.active,p.active,h.resident_gender_policy,r.room_type_id,r.reserved_for_group_id
 INTO room_capacity,room_condition,room_active,hall_active,premise_active,hall_gender,actual_room_type,reserved_group_id
 FROM accommodation_rooms r JOIN residence_halls h ON h.id=r.residence_hall_id
 JOIN accommodation_premises p ON p.id=h.premise_id
 WHERE r.id=NEW.room_id AND r.deleted_at IS NULL AND h.deleted_at IS NULL AND p.deleted_at IS NULL
 FOR UPDATE OF r;
 SELECT a.gender_code,a.status,a.application_period_id,a.selected_group_id
 INTO app_gender,app_status,app_period,selected_group_id
 FROM accommodation_applications a WHERE a.id=NEW.accommodation_application_id AND a.deleted_at IS NULL;
 SELECT application_period_id,room_type_id,effective_from,effective_until
 INTO rate_period,rate_room_type,rate_effective_from,rate_effective_until FROM accommodation_rates
 WHERE id=NEW.accommodation_rate_id AND status='ACTIVE' AND rating_status='RATED' AND deleted_at IS NULL;
 SELECT status,occupancy_starts_on,occupancy_ends_on,allocation_cutoff_at
 INTO period_status,period_occupancy_start,period_occupancy_end,allocation_cutoff
 FROM accommodation_application_periods WHERE id=app_period AND deleted_at IS NULL;
 IF room_capacity IS NULL OR NOT room_active OR NOT hall_active OR NOT premise_active OR room_condition<>'AVAILABLE' THEN
   RAISE EXCEPTION 'Room is not available for allocation'; END IF;
 IF app_status NOT IN ('ELIGIBLE','WAITLISTED') AND NOT (TG_OP='UPDATE' AND app_status='ALLOCATED') THEN
   RAISE EXCEPTION 'Only an eligible, waitlisted, or already allocated application can continue allocation'; END IF;
 IF period_status NOT IN ('APPLICATION_CLOSED','ALLOCATION_ACTIVE') OR NEW.allocated_at>allocation_cutoff THEN
   RAISE EXCEPTION 'Accommodation allocation is outside the approved allocation window'; END IF;
 IF NEW.occupancy_starts_on<period_occupancy_start OR NEW.occupancy_ends_on>period_occupancy_end THEN
   RAISE EXCEPTION 'Occupancy dates must remain within the accommodation application period'; END IF;
 IF app_period<>rate_period OR rate_room_type<>actual_room_type THEN RAISE EXCEPTION 'Accommodation rate does not match the application period and room type'; END IF;
 IF NEW.allocated_at<rate_effective_from OR (rate_effective_until IS NOT NULL AND NEW.allocated_at>=rate_effective_until) THEN
   RAISE EXCEPTION 'Accommodation rate is not effective at the allocation time'; END IF;
 IF hall_gender<>'ANY' AND hall_gender<>app_gender THEN RAISE EXCEPTION 'Student gender does not satisfy the residence hall policy'; END IF;
 IF reserved_group_id IS NOT NULL AND reserved_group_id IS DISTINCT FROM selected_group_id THEN
   RAISE EXCEPTION 'Room is reserved for a different accommodation group'; END IF;
 IF EXISTS (SELECT 1 FROM accommodation_blacklist_entries b JOIN accommodation_applications a ON a.student_id=b.student_id
      WHERE a.id=NEW.accommodation_application_id AND b.status='ACTIVE' AND b.deleted_at IS NULL
      AND NEW.occupancy_starts_on >= b.effective_from AND (b.effective_until IS NULL OR NEW.occupancy_starts_on <= b.effective_until)) THEN
   RAISE EXCEPTION 'Blacklisted student cannot be allocated'; END IF;
 IF (SELECT count(*) FROM room_allocations other_allocation WHERE other_allocation.room_id=NEW.room_id
      AND other_allocation.id<>NEW.id AND other_allocation.deleted_at IS NULL
      AND other_allocation.status IN ('PROPOSED','ALLOCATED','CHECKED_IN')
      AND daterange(other_allocation.occupancy_starts_on,other_allocation.occupancy_ends_on,'[]') &&
          daterange(NEW.occupancy_starts_on,NEW.occupancy_ends_on,'[]')) >= room_capacity THEN
   RAISE EXCEPTION 'Room capacity is exhausted for the requested occupancy period'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_validate_room_allocation BEFORE INSERT OR UPDATE OF room_id,accommodation_rate_id,occupancy_starts_on,occupancy_ends_on,status
 ON room_allocations FOR EACH ROW EXECUTE FUNCTION validate_room_allocation_capacity();

CREATE OR REPLACE FUNCTION validate_room_swap() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE first_status varchar(30); second_status varchar(30); first_room uuid; second_room uuid;
BEGIN
 SELECT status,room_id INTO first_status,first_room FROM room_allocations
 WHERE id=NEW.first_allocation_id AND deleted_at IS NULL FOR UPDATE;
 SELECT status,room_id INTO second_status,second_room FROM room_allocations
 WHERE id=NEW.second_allocation_id AND deleted_at IS NULL FOR UPDATE;
 IF TG_OP='INSERT' THEN
   IF first_status<>'CHECKED_IN' OR second_status<>'CHECKED_IN' THEN
     RAISE EXCEPTION 'Only checked-in room allocations can be swapped'; END IF;
   IF first_room<>NEW.first_original_room_id OR second_room<>NEW.second_original_room_id THEN
     RAISE EXCEPTION 'Room swap must preserve the original room evidence'; END IF;
 END IF;
 IF NEW.status='COMPLETED' AND (first_room<>NEW.second_original_room_id OR second_room<>NEW.first_original_room_id) THEN
   RAISE EXCEPTION 'Both room allocations must be exchanged before completing the swap'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_validate_room_swap BEFORE INSERT OR UPDATE OF status ON room_swaps
 FOR EACH ROW EXECUTE FUNCTION validate_room_swap();

CREATE OR REPLACE FUNCTION protect_accommodation_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Accommodation workflow evidence is append-only'; END IF;
 IF ROW(NEW.room_allocation_id,NEW.previous_status,NEW.new_status,NEW.event_type,NEW.from_room_id,NEW.to_room_id,
      NEW.reason,NEW.actor_user_id,NEW.occurred_at)
    IS DISTINCT FROM ROW(OLD.room_allocation_id,OLD.previous_status,OLD.new_status,OLD.event_type,OLD.from_room_id,OLD.to_room_id,
      OLD.reason,OLD.actor_user_id,OLD.occurred_at) THEN RAISE EXCEPTION 'Accommodation workflow evidence is immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_protect_room_allocation_event BEFORE UPDATE OR DELETE ON room_allocation_events
 FOR EACH ROW EXECUTE FUNCTION protect_accommodation_evidence();

CREATE OR REPLACE FUNCTION protect_approved_accommodation_configuration() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.status<>'DRAFT' AND ROW(NEW.academic_period_id,NEW.academic_period_code,NEW.code,NEW.name,
      NEW.applications_open_at,NEW.applications_close_at,NEW.occupancy_starts_on,NEW.occupancy_ends_on,NEW.allocation_cutoff_at)
    IS DISTINCT FROM ROW(OLD.academic_period_id,OLD.academic_period_code,OLD.code,OLD.name,
      OLD.applications_open_at,OLD.applications_close_at,OLD.occupancy_starts_on,OLD.occupancy_ends_on,OLD.allocation_cutoff_at) THEN
   RAISE EXCEPTION 'Approved accommodation period configuration is immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_protect_accommodation_period BEFORE UPDATE ON accommodation_application_periods
 FOR EACH ROW EXECUTE FUNCTION protect_approved_accommodation_configuration();

GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO emhare_service;
GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO emhare_service;
