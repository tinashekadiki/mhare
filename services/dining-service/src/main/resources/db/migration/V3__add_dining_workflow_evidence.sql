-- Author: Tinashe K

CREATE TABLE dining_workflow_events (
    id uuid PRIMARY KEY, aggregate_type varchar(40) NOT NULL, aggregate_id uuid NOT NULL,
    previous_state varchar(30), new_state varchar(30) NOT NULL, event_type varchar(40) NOT NULL,
    reason varchar(1000) NOT NULL, actor_user_id uuid NOT NULL, occurred_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    created_by_user_id uuid, modified_by_user_id uuid, deleted_at timestamptz,
    deleted_by_user_id uuid, version bigint NOT NULL,
    CONSTRAINT ck_dining_workflow_aggregate CHECK (aggregate_type IN ('DINING_ASSIGNMENT','DIETARY_REQUIREMENT','MEAL_SESSION')),
    CONSTRAINT ck_dining_workflow_reason CHECK (length(trim(reason)) > 0)
);
CREATE INDEX idx_dining_workflow_aggregate ON dining_workflow_events(aggregate_type,aggregate_id,occurred_at,id);

CREATE TABLE dining_workflow_events_aud (
    id uuid NOT NULL, rev integer NOT NULL REFERENCES revinfo(rev), revtype smallint,
    aggregate_type varchar(40), aggregate_id uuid, previous_state varchar(30), new_state varchar(30),
    event_type varchar(40), reason varchar(1000), actor_user_id uuid, occurred_at timestamptz,
    created_at timestamptz, updated_at timestamptz, created_by_user_id uuid, modified_by_user_id uuid,
    deleted_at timestamptz, deleted_by_user_id uuid, version bigint, PRIMARY KEY(id,rev)
);

CREATE OR REPLACE FUNCTION protect_dining_workflow_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Dining workflow evidence is append-only and immutable'; END $$;
CREATE TRIGGER trg_protect_dining_workflow_event BEFORE UPDATE OR DELETE ON dining_workflow_events
 FOR EACH ROW EXECUTE FUNCTION protect_dining_workflow_evidence();

GRANT SELECT,INSERT,UPDATE,DELETE ON dining_workflow_events,dining_workflow_events_aud TO emhare_service;
