package zw.ac.uz.emhare.dining.operations;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.*;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Immutable @Entity @Table(name="dining_workflow_events") @SQLRestriction("deleted_at IS NULL")
public class DiningWorkflowEvent extends AuditableEntity {
    public enum AggregateType { DINING_ASSIGNMENT, DIETARY_REQUIREMENT, MEAL_SESSION }
    @Enumerated(EnumType.STRING) @Column(name="aggregate_type",nullable=false,length=40) private AggregateType aggregateType;
    @Column(name="aggregate_id",nullable=false) private UUID aggregateId; @Column(name="previous_state",length=30) private String previousState;
    @Column(name="new_state",nullable=false,length=30) private String newState; @Column(name="event_type",nullable=false,length=40) private String eventType;
    @Column(nullable=false,length=1000) private String reason; @Column(name="actor_user_id",nullable=false) private UUID actorUserId; @Column(name="occurred_at",nullable=false) private Instant occurredAt;
    protected DiningWorkflowEvent() {}
    public DiningWorkflowEvent(AggregateType type,UUID aggregateId,String previous,String next,String eventType,String reason,UUID actor,Instant at){
        if(type==null||aggregateId==null||next==null||actor==null||at==null)throw new IllegalArgumentException("Workflow aggregate, state, actor, and time are required.");
        aggregateType=type;this.aggregateId=aggregateId;previousState=DiningOperationValues.optional(previous);newState=DiningOperationValues.code(next,"New state");this.eventType=DiningOperationValues.code(eventType,"Event type");this.reason=DiningOperationValues.required(reason,"Workflow reason");actorUserId=actor;occurredAt=at;
    }
    public AggregateType getAggregateType(){return aggregateType;} public UUID getAggregateId(){return aggregateId;} public String getPreviousState(){return previousState;} public String getNewState(){return newState;} public String getEventType(){return eventType;} public String getReason(){return reason;} public UUID getActorUserId(){return actorUserId;} public Instant getOccurredAt(){return occurredAt;}
}
