package zw.ac.uz.emhare.assessmentresults.result;
import jakarta.persistence.*;import java.time.Instant;import java.util.UUID;import org.hibernate.annotations.SQLRestriction;import org.hibernate.envers.Audited;import zw.ac.uz.emhare.common.persistence.AuditableEntity;
/** @author Tinashe K */
@Audited @Entity @Table(name="result_batch_status_events") @SQLRestriction("deleted_at IS NULL")
public class ResultBatchStatusEvent extends AuditableEntity{
 @ManyToOne(fetch=FetchType.LAZY,optional=false)@JoinColumn(name="result_batch_id")private ResultBatch resultBatch;@Enumerated(EnumType.STRING)@Column(name="from_status",length=20)private ResultBatch.Status fromStatus;@Enumerated(EnumType.STRING)@Column(name="to_status",nullable=false,length=20)private ResultBatch.Status toStatus;@Column(nullable=false,length=1000)private String reason;@Column(name="actor_user_id",nullable=false)private UUID actorUserId;@Column(name="occurred_at",nullable=false)private Instant occurredAt;
 protected ResultBatchStatusEvent(){}public ResultBatchStatusEvent(ResultBatch batch,ResultBatch.Status from,String reason,UUID actor,Instant now){resultBatch=batch;fromStatus=from;toStatus=batch.getStatus();this.reason=GradingScheme.text(reason);actorUserId=actor;occurredAt=now;}
}
