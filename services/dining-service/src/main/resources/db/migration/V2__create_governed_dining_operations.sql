-- Author: Tinashe K

CREATE SEQUENCE dining_assignment_number_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE meal_service_session_number_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE meal_attendance_event_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE dining_halls (
    id uuid PRIMARY KEY, code varchar(40) NOT NULL, name varchar(160) NOT NULL,
    location_description varchar(300) NOT NULL, service_capacity integer NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_dining_hall_capacity CHECK (service_capacity > 0)
);
CREATE UNIQUE INDEX uk_dining_hall_code ON dining_halls(lower(code)) WHERE deleted_at IS NULL;

CREATE TABLE meal_options (
    id uuid PRIMARY KEY, code varchar(40) NOT NULL, name varchar(120) NOT NULL,
    description varchar(500), meal_category varchar(20) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_meal_option_category CHECK (meal_category IN ('BREAKFAST','LUNCH','DINNER','OTHER'))
);
CREATE UNIQUE INDEX uk_meal_option_code ON meal_options(lower(code)) WHERE deleted_at IS NULL;

CREATE TABLE meal_service_times (
    id uuid PRIMARY KEY, dining_hall_id uuid NOT NULL REFERENCES dining_halls(id),
    meal_option_id uuid NOT NULL REFERENCES meal_options(id), day_of_week smallint NOT NULL,
    service_opens_at time NOT NULL, service_closes_at time NOT NULL,
    grace_closes_at time NOT NULL, active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_meal_service_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_meal_service_window CHECK (service_closes_at > service_opens_at AND grace_closes_at >= service_closes_at)
);
CREATE UNIQUE INDEX uk_meal_service_time ON meal_service_times(dining_hall_id,meal_option_id,day_of_week)
    WHERE deleted_at IS NULL;

CREATE TABLE dining_plans (
    id uuid PRIMARY KEY, code varchar(40) NOT NULL, plan_version integer NOT NULL,
    name varchar(160) NOT NULL, description varchar(500), finance_fee_catalogue_id uuid,
    valid_from date NOT NULL, valid_until date, status varchar(20) NOT NULL DEFAULT 'DRAFT',
    prepared_by_user_id uuid NOT NULL, approved_by_user_id uuid, approved_at timestamptz,
    approval_reason varchar(1000),
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT uk_dining_plan_version UNIQUE(code,plan_version),
    CONSTRAINT ck_dining_plan_window CHECK (plan_version > 0 AND (valid_until IS NULL OR valid_until >= valid_from)),
    CONSTRAINT ck_dining_plan_status CHECK (status IN ('DRAFT','ACTIVE','RETIRED')),
    CONSTRAINT ck_dining_plan_approval CHECK (
        (status='DRAFT' AND approved_by_user_id IS NULL AND approved_at IS NULL AND approval_reason IS NULL)
        OR (status<>'DRAFT' AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL
            AND length(trim(approval_reason)) > 0 AND approved_by_user_id <> prepared_by_user_id))
);
CREATE UNIQUE INDEX uk_active_dining_plan ON dining_plans(lower(code)) WHERE status='ACTIVE' AND deleted_at IS NULL;

CREATE TABLE dining_plan_meals (
    id uuid PRIMARY KEY, dining_plan_id uuid NOT NULL REFERENCES dining_plans(id),
    meal_option_id uuid NOT NULL REFERENCES meal_options(id), servings_per_service integer NOT NULL DEFAULT 1,
    monday boolean NOT NULL DEFAULT true, tuesday boolean NOT NULL DEFAULT true,
    wednesday boolean NOT NULL DEFAULT true, thursday boolean NOT NULL DEFAULT true,
    friday boolean NOT NULL DEFAULT true, saturday boolean NOT NULL DEFAULT true,
    sunday boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_dining_plan_servings CHECK (servings_per_service > 0)
);
CREATE UNIQUE INDEX uk_dining_plan_meal ON dining_plan_meals(dining_plan_id,meal_option_id) WHERE deleted_at IS NULL;

CREATE TABLE dining_hall_assignment_rules (
    id uuid PRIMARY KEY, dining_hall_id uuid NOT NULL REFERENCES dining_halls(id),
    rule_dimension varchar(30) NOT NULL, comparison_operator varchar(20) NOT NULL,
    comparison_value varchar(200) NOT NULL, priority_rank integer NOT NULL DEFAULT 100,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_dining_assignment_rule_dimension CHECK (rule_dimension IN ('SURNAME_PREFIX','RESIDENCE_HALL','PROGRAMME','STUDENT_GROUP')),
    CONSTRAINT ck_dining_assignment_rule_operator CHECK (comparison_operator IN ('EQUALS','STARTS_WITH','IN')),
    CONSTRAINT ck_dining_assignment_rule_priority CHECK (priority_rank > 0)
);

CREATE TABLE dining_attendant_assignments (
    id uuid PRIMARY KEY, dining_hall_id uuid NOT NULL REFERENCES dining_halls(id),
    staff_id uuid NOT NULL, staff_number varchar(40) NOT NULL, staff_name varchar(200) NOT NULL,
    effective_from date NOT NULL, effective_until date, role_code varchar(30) NOT NULL DEFAULT 'ATTENDANT',
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_dining_attendant_window CHECK (effective_until IS NULL OR effective_until >= effective_from),
    CONSTRAINT ck_dining_attendant_role CHECK (role_code IN ('ATTENDANT','SUPERVISOR','MANAGER'))
);
CREATE UNIQUE INDEX uk_active_dining_attendant ON dining_attendant_assignments(dining_hall_id,staff_id)
    WHERE active AND deleted_at IS NULL;

CREATE TABLE student_dining_assignments (
    id uuid PRIMARY KEY, assignment_number varchar(60) NOT NULL UNIQUE,
    student_id uuid NOT NULL, student_number varchar(40) NOT NULL, student_name varchar(200) NOT NULL,
    academic_period_id uuid NOT NULL, academic_period_code varchar(50) NOT NULL,
    dining_hall_id uuid NOT NULL REFERENCES dining_halls(id), dining_plan_id uuid NOT NULL REFERENCES dining_plans(id),
    accommodation_allocation_id uuid, effective_from date NOT NULL, effective_until date NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'DRAFT', prepared_by_user_id uuid NOT NULL,
    approved_by_user_id uuid, approved_at timestamptz, approval_reason varchar(1000),
    ended_by_user_id uuid, ended_at timestamptz, end_reason varchar(1000),
    billing_event_id uuid, billing_status varchar(20) NOT NULL DEFAULT 'NOT_REQUESTED',
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_student_dining_assignment_window CHECK (effective_until >= effective_from),
    CONSTRAINT ck_student_dining_assignment_status CHECK (status IN ('DRAFT','ACTIVE','SUSPENDED','ENDED','CANCELLED')),
    CONSTRAINT ck_student_dining_assignment_approval CHECK (
        (status IN ('DRAFT','CANCELLED') AND approved_by_user_id IS NULL AND approved_at IS NULL AND approval_reason IS NULL)
        OR (status IN ('ACTIVE','SUSPENDED','ENDED') AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL
            AND length(trim(approval_reason)) > 0 AND approved_by_user_id <> prepared_by_user_id)),
    CONSTRAINT ck_student_dining_assignment_end CHECK (
        (status NOT IN ('ENDED','CANCELLED') AND ended_by_user_id IS NULL AND ended_at IS NULL AND end_reason IS NULL)
        OR (status IN ('ENDED','CANCELLED') AND ended_by_user_id IS NOT NULL AND ended_at IS NOT NULL AND length(trim(end_reason)) > 0)),
    CONSTRAINT ck_student_dining_billing CHECK (billing_status IN ('NOT_REQUESTED','PENDING','ACCEPTED','FAILED'))
);
CREATE UNIQUE INDEX uk_active_student_dining_assignment ON student_dining_assignments(student_id,academic_period_id)
    WHERE status IN ('DRAFT','ACTIVE','SUSPENDED') AND deleted_at IS NULL;

CREATE TABLE student_dietary_requirements (
    id uuid PRIMARY KEY, student_id uuid NOT NULL, student_number varchar(40) NOT NULL,
    requirement_code varchar(50) NOT NULL, description varchar(1000) NOT NULL,
    severity varchar(20) NOT NULL, clinical_document_id uuid, effective_from date NOT NULL,
    effective_until date, status varchar(20) NOT NULL DEFAULT 'ACTIVE', recorded_by_user_id uuid NOT NULL,
    resolved_by_user_id uuid, resolved_at timestamptz, resolution_reason varchar(1000),
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_dietary_requirement_window CHECK (effective_until IS NULL OR effective_until >= effective_from),
    CONSTRAINT ck_dietary_requirement_severity CHECK (severity IN ('INFORMATION','IMPORTANT','CRITICAL')),
    CONSTRAINT ck_dietary_requirement_status CHECK (status IN ('ACTIVE','RESOLVED','EXPIRED')),
    CONSTRAINT ck_dietary_requirement_resolution CHECK (
        (status='ACTIVE' AND resolved_by_user_id IS NULL AND resolved_at IS NULL AND resolution_reason IS NULL)
        OR (status<>'ACTIVE' AND resolved_by_user_id IS NOT NULL AND resolved_at IS NOT NULL
            AND length(trim(resolution_reason)) > 0 AND resolved_by_user_id <> recorded_by_user_id))
);
CREATE UNIQUE INDEX uk_active_dietary_requirement ON student_dietary_requirements(student_id,lower(requirement_code))
    WHERE status='ACTIVE' AND deleted_at IS NULL;

CREATE TABLE meal_service_sessions (
    id uuid PRIMARY KEY, session_number varchar(60) NOT NULL UNIQUE,
    dining_hall_id uuid NOT NULL REFERENCES dining_halls(id), meal_option_id uuid NOT NULL REFERENCES meal_options(id),
    service_date date NOT NULL, scheduled_opens_at timestamptz NOT NULL, scheduled_closes_at timestamptz NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'PLANNED', prepared_by_user_id uuid NOT NULL,
    opened_by_user_id uuid, opened_at timestamptz, closed_by_user_id uuid, closed_at timestamptz,
    reconciled_by_user_id uuid, reconciled_at timestamptz, reconciliation_reason varchar(1000),
    expected_servings integer, counted_servings integer,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_meal_session_window CHECK (scheduled_closes_at > scheduled_opens_at),
    CONSTRAINT ck_meal_session_status CHECK (status IN ('PLANNED','OPEN','CLOSED','RECONCILED','CANCELLED')),
    CONSTRAINT ck_meal_session_open CHECK ((status IN ('PLANNED','CANCELLED') AND opened_by_user_id IS NULL AND opened_at IS NULL)
        OR (status IN ('OPEN','CLOSED','RECONCILED') AND opened_by_user_id IS NOT NULL AND opened_at IS NOT NULL AND opened_by_user_id <> prepared_by_user_id)),
    CONSTRAINT ck_meal_session_close CHECK ((status NOT IN ('CLOSED','RECONCILED') AND closed_by_user_id IS NULL AND closed_at IS NULL)
        OR (status IN ('CLOSED','RECONCILED') AND closed_by_user_id IS NOT NULL AND closed_at IS NOT NULL)),
    CONSTRAINT ck_meal_session_reconcile CHECK ((status<>'RECONCILED' AND reconciled_by_user_id IS NULL AND reconciled_at IS NULL AND reconciliation_reason IS NULL)
        OR (status='RECONCILED' AND reconciled_by_user_id IS NOT NULL AND reconciled_at IS NOT NULL
            AND length(trim(reconciliation_reason)) > 0 AND counted_servings IS NOT NULL
            AND reconciled_by_user_id <> opened_by_user_id)),
    CONSTRAINT ck_meal_session_counts CHECK ((expected_servings IS NULL OR expected_servings >= 0) AND (counted_servings IS NULL OR counted_servings >= 0))
);
CREATE UNIQUE INDEX uk_meal_service_session ON meal_service_sessions(dining_hall_id,meal_option_id,service_date)
    WHERE status<>'CANCELLED' AND deleted_at IS NULL;

CREATE TABLE meal_attendance_events (
    id uuid PRIMARY KEY, event_number varchar(60) NOT NULL UNIQUE,
    meal_service_session_id uuid NOT NULL REFERENCES meal_service_sessions(id),
    student_dining_assignment_id uuid REFERENCES student_dining_assignments(id),
    student_id uuid NOT NULL, student_number varchar(40) NOT NULL, student_name varchar(200) NOT NULL,
    outcome varchar(20) NOT NULL, denial_reason_code varchar(50), denial_reason varchar(1000),
    captured_by_user_id uuid NOT NULL, captured_at timestamptz NOT NULL,
    capture_channel varchar(20) NOT NULL, device_id varchar(100), idempotency_key varchar(120) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_meal_attendance_outcome CHECK (outcome IN ('ADMITTED','DENIED')),
    CONSTRAINT ck_meal_attendance_denial CHECK ((outcome='ADMITTED' AND denial_reason_code IS NULL AND denial_reason IS NULL AND student_dining_assignment_id IS NOT NULL)
        OR (outcome='DENIED' AND denial_reason_code IS NOT NULL AND length(trim(denial_reason)) > 0)),
    CONSTRAINT ck_meal_attendance_channel CHECK (capture_channel IN ('ONLINE','OFFLINE_SYNC','MANUAL_OVERRIDE'))
);
CREATE UNIQUE INDEX uk_admitted_meal_per_session ON meal_attendance_events(meal_service_session_id,student_id)
    WHERE outcome='ADMITTED' AND deleted_at IS NULL;

CREATE TABLE meal_attendance_reversals (
    id uuid PRIMARY KEY, meal_attendance_event_id uuid NOT NULL UNIQUE REFERENCES meal_attendance_events(id),
    reason_code varchar(50) NOT NULL, reason varchar(1000) NOT NULL,
    reversed_by_user_id uuid NOT NULL, reversed_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_meal_reversal_reason CHECK (length(trim(reason)) > 0)
);

CREATE TABLE integration_outbox (
    id uuid PRIMARY KEY, event_type varchar(160) NOT NULL, routing_key varchar(160) NOT NULL,
    payload jsonb NOT NULL, occurred_at timestamptz NOT NULL, status varchar(20) NOT NULL DEFAULT 'PENDING',
    attempt_count integer NOT NULL DEFAULT 0, next_attempt_at timestamptz NOT NULL, published_at timestamptz,
    last_error varchar(1000), created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_dining_outbox_status CHECK (status IN ('PENDING','PUBLISHED','DEAD')),
    CONSTRAINT ck_dining_outbox_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_dining_outbox_publication CHECK ((status='PUBLISHED' AND published_at IS NOT NULL) OR (status<>'PUBLISHED' AND published_at IS NULL))
);
CREATE INDEX idx_dining_outbox_dispatch ON integration_outbox(next_attempt_at,occurred_at,id) WHERE status='PENDING';

CREATE TABLE dining_halls_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,code varchar(40),name varchar(160),location_description varchar(300),service_capacity integer,active boolean,created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE meal_options_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,code varchar(40),name varchar(120),description varchar(500),meal_category varchar(20),active boolean,created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE meal_service_times_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,dining_hall_id uuid,meal_option_id uuid,day_of_week smallint,service_opens_at time,service_closes_at time,grace_closes_at time,active boolean,created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE dining_plans_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,code varchar(40),plan_version integer,name varchar(160),description varchar(500),finance_fee_catalogue_id uuid,valid_from date,valid_until date,status varchar(20),prepared_by_user_id uuid,approved_by_user_id uuid,approved_at timestamptz,approval_reason varchar(1000),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE dining_plan_meals_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,dining_plan_id uuid,meal_option_id uuid,servings_per_service integer,monday boolean,tuesday boolean,wednesday boolean,thursday boolean,friday boolean,saturday boolean,sunday boolean,created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE dining_hall_assignment_rules_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,dining_hall_id uuid,rule_dimension varchar(30),comparison_operator varchar(20),comparison_value varchar(200),priority_rank integer,active boolean,created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE dining_attendant_assignments_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,dining_hall_id uuid,staff_id uuid,staff_number varchar(40),staff_name varchar(200),effective_from date,effective_until date,role_code varchar(30),active boolean,created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE student_dining_assignments_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,assignment_number varchar(60),student_id uuid,student_number varchar(40),student_name varchar(200),academic_period_id uuid,academic_period_code varchar(50),dining_hall_id uuid,dining_plan_id uuid,accommodation_allocation_id uuid,effective_from date,effective_until date,status varchar(20),prepared_by_user_id uuid,approved_by_user_id uuid,approved_at timestamptz,approval_reason varchar(1000),ended_by_user_id uuid,ended_at timestamptz,end_reason varchar(1000),billing_event_id uuid,billing_status varchar(20),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE student_dietary_requirements_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,student_id uuid,student_number varchar(40),requirement_code varchar(50),description varchar(1000),severity varchar(20),clinical_document_id uuid,effective_from date,effective_until date,status varchar(20),recorded_by_user_id uuid,resolved_by_user_id uuid,resolved_at timestamptz,resolution_reason varchar(1000),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE meal_service_sessions_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,session_number varchar(60),dining_hall_id uuid,meal_option_id uuid,service_date date,scheduled_opens_at timestamptz,scheduled_closes_at timestamptz,status varchar(20),prepared_by_user_id uuid,opened_by_user_id uuid,opened_at timestamptz,closed_by_user_id uuid,closed_at timestamptz,reconciled_by_user_id uuid,reconciled_at timestamptz,reconciliation_reason varchar(1000),expected_servings integer,counted_servings integer,created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE meal_attendance_events_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,event_number varchar(60),meal_service_session_id uuid,student_dining_assignment_id uuid,student_id uuid,student_number varchar(40),student_name varchar(200),outcome varchar(20),denial_reason_code varchar(50),denial_reason varchar(1000),captured_by_user_id uuid,captured_at timestamptz,capture_channel varchar(20),device_id varchar(100),idempotency_key varchar(120),created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));
CREATE TABLE meal_attendance_reversals_aud (id uuid NOT NULL,rev integer NOT NULL REFERENCES revinfo(rev),revtype smallint,meal_attendance_event_id uuid,reason_code varchar(50),reason varchar(1000),reversed_by_user_id uuid,reversed_at timestamptz,created_at timestamptz,updated_at timestamptz,created_by_user_id uuid,modified_by_user_id uuid,deleted_at timestamptz,deleted_by_user_id uuid,version bigint,PRIMARY KEY(id,rev));

CREATE OR REPLACE FUNCTION validate_meal_attendance() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE session_status varchar(20); hall_id uuid; option_id uuid; service_day date; hall_capacity integer;
 assignment_status varchar(20); assignment_hall uuid; assignment_plan uuid; assignment_start date; assignment_end date;
BEGIN
 SELECT s.status,s.dining_hall_id,s.meal_option_id,s.service_date,h.service_capacity
 INTO session_status,hall_id,option_id,service_day,hall_capacity
 FROM meal_service_sessions s JOIN dining_halls h ON h.id=s.dining_hall_id
 WHERE s.id=NEW.meal_service_session_id AND s.deleted_at IS NULL FOR UPDATE OF s;
 IF session_status<>'OPEN' THEN RAISE EXCEPTION 'Meal attendance can only be captured for an open service session'; END IF;
 IF NEW.outcome='ADMITTED' THEN
   SELECT a.status,a.dining_hall_id,a.dining_plan_id,a.effective_from,a.effective_until
   INTO assignment_status,assignment_hall,assignment_plan,assignment_start,assignment_end
   FROM student_dining_assignments a WHERE a.id=NEW.student_dining_assignment_id AND a.student_id=NEW.student_id AND a.deleted_at IS NULL;
   IF assignment_status<>'ACTIVE' OR assignment_hall<>hall_id OR service_day NOT BETWEEN assignment_start AND assignment_end THEN
     RAISE EXCEPTION 'Student does not have an active assignment for this dining hall and service date'; END IF;
   IF NOT EXISTS (SELECT 1 FROM dining_plan_meals pm WHERE pm.dining_plan_id=assignment_plan AND pm.meal_option_id=option_id
       AND pm.deleted_at IS NULL AND CASE extract(isodow FROM service_day)::integer WHEN 1 THEN pm.monday WHEN 2 THEN pm.tuesday
       WHEN 3 THEN pm.wednesday WHEN 4 THEN pm.thursday WHEN 5 THEN pm.friday WHEN 6 THEN pm.saturday ELSE pm.sunday END) THEN
     RAISE EXCEPTION 'Dining plan does not include this meal option on the service day'; END IF;
   IF (SELECT count(*) FROM meal_attendance_events e WHERE e.meal_service_session_id=NEW.meal_service_session_id
       AND e.outcome='ADMITTED' AND e.deleted_at IS NULL) >= hall_capacity THEN
     RAISE EXCEPTION 'Dining hall service capacity has been reached'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_validate_meal_attendance BEFORE INSERT ON meal_attendance_events
 FOR EACH ROW EXECUTE FUNCTION validate_meal_attendance();

CREATE OR REPLACE FUNCTION protect_dining_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Dining attendance evidence is append-only and immutable'; END $$;
CREATE TRIGGER trg_protect_meal_attendance BEFORE UPDATE OR DELETE ON meal_attendance_events
 FOR EACH ROW EXECUTE FUNCTION protect_dining_evidence();
CREATE TRIGGER trg_protect_meal_reversal BEFORE UPDATE OR DELETE ON meal_attendance_reversals
 FOR EACH ROW EXECUTE FUNCTION protect_dining_evidence();

GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO emhare_service;
GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO emhare_service;
