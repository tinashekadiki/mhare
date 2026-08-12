package zw.ac.uz.emhare.dining.operations.domain.model;

import zw.ac.uz.emhare.dining.operations.*;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.*;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Immutable @Entity @Table(name="meal_attendance_events") @SQLRestriction("deleted_at IS NULL")
public class MealAttendanceEvent extends AuditableEntity {
    public enum Outcome { ADMITTED, DENIED } public enum CaptureChannel { ONLINE, OFFLINE_SYNC, MANUAL_OVERRIDE }
    @Column(name="event_number",nullable=false,length=60) private String eventNumber;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="meal_service_session_id") private MealServiceSession session;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="student_dining_assignment_id") private StudentDiningAssignment assignment;
    @Column(name="student_id",nullable=false) private UUID studentId; @Column(name="student_number",nullable=false,length=40) private String studentNumber; @Column(name="student_name",nullable=false,length=200) private String studentName;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Outcome outcome; @Column(name="denial_reason_code",length=50) private String denialReasonCode; @Column(name="denial_reason",length=1000) private String denialReason;
    @Column(name="captured_by_user_id",nullable=false) private UUID capturedByUserId; @Column(name="captured_at",nullable=false) private Instant capturedAt;
    @Enumerated(EnumType.STRING) @Column(name="capture_channel",nullable=false,length=20) private CaptureChannel captureChannel; @Column(name="device_id",length=100) private String deviceId; @Column(name="idempotency_key",nullable=false,length=120) private String idempotencyKey;
    protected MealAttendanceEvent() {}
    public MealAttendanceEvent(String number,MealServiceSession session,StudentDiningAssignment assignment,UUID studentId,String studentNumber,String studentName,Outcome outcome,String denialCode,String denialReason,UUID actor,Instant at,CaptureChannel channel,String deviceId,String idempotencyKey){
        if(session==null||session.getStatus()!=MealServiceSession.Status.OPEN||studentId==null||outcome==null||actor==null||at==null||channel==null)throw new IllegalArgumentException("Open session, student, outcome, operator, time, and capture channel are required.");
        if(outcome==Outcome.ADMITTED&&assignment==null)throw new IllegalArgumentException("An active dining assignment is required for admission.");
        if(outcome==Outcome.DENIED&&(denialCode==null||denialCode.isBlank()||denialReason==null||denialReason.isBlank()))throw new IllegalArgumentException("Denied attendance requires a reason code and explanation.");
        eventNumber=DiningOperationValues.code(number,"Attendance event number");this.session=session;this.assignment=assignment;this.studentId=studentId;this.studentNumber=DiningOperationValues.code(studentNumber,"Student number");this.studentName=DiningOperationValues.required(studentName,"Student name");this.outcome=outcome;denialReasonCode=outcome==Outcome.DENIED?DiningOperationValues.code(denialCode,"Denial code"):null;this.denialReason=outcome==Outcome.DENIED?DiningOperationValues.required(denialReason,"Denial reason"):null;capturedByUserId=actor;capturedAt=at;captureChannel=channel;this.deviceId=DiningOperationValues.optional(deviceId);this.idempotencyKey=DiningOperationValues.required(idempotencyKey,"Idempotency key");
    }
    public String getEventNumber(){return eventNumber;} public MealServiceSession getSession(){return session;} public StudentDiningAssignment getAssignment(){return assignment;} public UUID getStudentId(){return studentId;} public String getStudentNumber(){return studentNumber;} public String getStudentName(){return studentName;} public Outcome getOutcome(){return outcome;} public String getDenialReasonCode(){return denialReasonCode;} public String getDenialReason(){return denialReason;} public UUID getCapturedByUserId(){return capturedByUserId;} public Instant getCapturedAt(){return capturedAt;} public CaptureChannel getCaptureChannel(){return captureChannel;} public String getDeviceId(){return deviceId;} public String getIdempotencyKey(){return idempotencyKey;}
}
