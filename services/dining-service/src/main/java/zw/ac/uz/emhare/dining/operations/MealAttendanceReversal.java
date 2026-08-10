package zw.ac.uz.emhare.dining.operations;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.*;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Immutable @Entity @Table(name="meal_attendance_reversals") @SQLRestriction("deleted_at IS NULL")
public class MealAttendanceReversal extends AuditableEntity {
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="meal_attendance_event_id") private MealAttendanceEvent event;
    @Column(name="reason_code",nullable=false,length=50) private String reasonCode; @Column(nullable=false,length=1000) private String reason;
    @Column(name="reversed_by_user_id",nullable=false) private UUID reversedByUserId; @Column(name="reversed_at",nullable=false) private Instant reversedAt;
    protected MealAttendanceReversal() {}
    public MealAttendanceReversal(MealAttendanceEvent event,String code,String reason,UUID actor,Instant at){if(event==null||actor==null||at==null)throw new IllegalArgumentException("Attendance event, reversing operator, and time are required.");this.event=event;reasonCode=DiningOperationValues.code(code,"Reversal reason code");this.reason=DiningOperationValues.required(reason,"Reversal reason");reversedByUserId=actor;reversedAt=at;}
    public MealAttendanceEvent getEvent(){return event;} public String getReasonCode(){return reasonCode;} public String getReason(){return reason;} public UUID getReversedByUserId(){return reversedByUserId;} public Instant getReversedAt(){return reversedAt;}
}
