package zw.ac.uz.emhare.dining.operations.domain.model;

import zw.ac.uz.emhare.dining.setup.domain.model.DiningHall;
import zw.ac.uz.emhare.dining.setup.domain.model.DiningPlan;

import zw.ac.uz.emhare.dining.operations.*;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.dining.setup.*;

/** @author Tinashe K */
@Audited @Entity @Table(name="student_dining_assignments") @SQLRestriction("deleted_at IS NULL")
public class StudentDiningAssignment extends AuditableEntity {
    public enum Status { DRAFT, ACTIVE, SUSPENDED, ENDED, CANCELLED }
    public enum BillingStatus { NOT_REQUESTED, PENDING, ACCEPTED, FAILED }
    @Column(name="assignment_number",nullable=false,length=60) private String assignmentNumber;
    @Column(name="student_id",nullable=false) private UUID studentId;
    @Column(name="student_number",nullable=false,length=40) private String studentNumber;
    @Column(name="student_name",nullable=false,length=200) private String studentName;
    @Column(name="academic_period_id",nullable=false) private UUID academicPeriodId;
    @Column(name="academic_period_code",nullable=false,length=50) private String academicPeriodCode;
    @Column(name="programme_code",length=50) private String programmeCode;
    @Column(name="student_group_code",length=80) private String studentGroupCode;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="dining_hall_id") private DiningHall diningHall;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="dining_plan_id") private DiningPlan diningPlan;
    @Column(name="accommodation_allocation_id") private UUID accommodationAllocationId;
    @Column(name="effective_from",nullable=false) private LocalDate effectiveFrom;
    @Column(name="effective_until",nullable=false) private LocalDate effectiveUntil;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
    @Column(name="prepared_by_user_id",nullable=false) private UUID preparedByUserId;
    @Column(name="approved_by_user_id") private UUID approvedByUserId;
    @Column(name="approved_at") private Instant approvedAt;
    @Column(name="approval_reason",length=1000) private String approvalReason;
    @Column(name="ended_by_user_id") private UUID endedByUserId;
    @Column(name="ended_at") private Instant endedAt;
    @Column(name="end_reason",length=1000) private String endReason;
    @Column(name="billing_event_id") private UUID billingEventId;
    @Enumerated(EnumType.STRING) @Column(name="billing_status",nullable=false,length=20) private BillingStatus billingStatus;
    protected StudentDiningAssignment() {}
    public StudentDiningAssignment(String number,UUID studentId,String studentNumber,String studentName,
            UUID academicPeriodId,String academicPeriodCode,String programmeCode,String studentGroupCode,DiningHall hall,DiningPlan plan,
            UUID accommodationAllocationId,LocalDate from,LocalDate until,UUID preparer){
        if(studentId==null||academicPeriodId==null||hall==null||!hall.isActive()||plan==null||plan.getStatus()!=DiningPlan.Status.ACTIVE||from==null||until==null||preparer==null)
            throw new IllegalArgumentException("Student, academic period, active hall, active plan, dates, and preparing operator are required.");
        if(until.isBefore(from)||from.isBefore(plan.getValidFrom())||(plan.getValidUntil()!=null&&until.isAfter(plan.getValidUntil())))
            throw new IllegalArgumentException("Assignment dates must remain within the active dining plan window.");
        assignmentNumber=DiningOperationValues.code(number,"Assignment number");this.studentId=studentId;
        this.studentNumber=DiningOperationValues.code(studentNumber,"Student number");this.studentName=DiningOperationValues.required(studentName,"Student name");
        this.academicPeriodId=academicPeriodId;this.academicPeriodCode=DiningOperationValues.code(academicPeriodCode,"Academic period code");
        this.programmeCode=DiningOperationValues.code(programmeCode,"Programme code");
        this.studentGroupCode=studentGroupCode==null||studentGroupCode.isBlank()?null:DiningOperationValues.code(studentGroupCode,"Student group code");
        diningHall=hall;diningPlan=plan;this.accommodationAllocationId=accommodationAllocationId;effectiveFrom=from;effectiveUntil=until;
        preparedByUserId=preparer;status=Status.DRAFT;billingStatus=BillingStatus.NOT_REQUESTED;
    }
    public void activate(UUID actor,String reason,Instant at,long expected){DiningOperationValues.version(getVersion(),expected,"Dining assignment");if(status!=Status.DRAFT)throw new IllegalStateException("Only a draft dining assignment can be activated.");if(actor==null||actor.equals(preparedByUserId))throw new IllegalArgumentException("A different authorised operator must approve the dining assignment.");approvedByUserId=actor;approvedAt=at;approvalReason=DiningOperationValues.required(reason,"Approval reason");status=Status.ACTIVE;}
    public void suspend(String reason,long expected){DiningOperationValues.version(getVersion(),expected,"Dining assignment");if(status!=Status.ACTIVE)throw new IllegalStateException("Only an active dining assignment can be suspended.");approvalReason=DiningOperationValues.required(reason,"Suspension reason");status=Status.SUSPENDED;}
    public void resume(String reason,long expected){DiningOperationValues.version(getVersion(),expected,"Dining assignment");if(status!=Status.SUSPENDED)throw new IllegalStateException("Only a suspended dining assignment can resume.");approvalReason=DiningOperationValues.required(reason,"Resumption reason");status=Status.ACTIVE;}
    public void end(Status target,UUID actor,String reason,Instant at,long expected){DiningOperationValues.version(getVersion(),expected,"Dining assignment");if(target==Status.CANCELLED&&status!=Status.DRAFT)throw new IllegalStateException("Only a draft dining assignment can be cancelled.");if(target==Status.ENDED&&status!=Status.ACTIVE&&status!=Status.SUSPENDED)throw new IllegalStateException("Only an active or suspended dining assignment can end.");endedByUserId=actor;endedAt=at;endReason=DiningOperationValues.required(reason,"Ending reason");status=target;}
    public String getAssignmentNumber(){return assignmentNumber;} public UUID getStudentId(){return studentId;} public String getStudentNumber(){return studentNumber;} public String getStudentName(){return studentName;}
    public UUID getAcademicPeriodId(){return academicPeriodId;} public String getAcademicPeriodCode(){return academicPeriodCode;} public DiningHall getDiningHall(){return diningHall;} public DiningPlan getDiningPlan(){return diningPlan;}
    public String getProgrammeCode(){return programmeCode;} public String getStudentGroupCode(){return studentGroupCode;}
    public UUID getAccommodationAllocationId(){return accommodationAllocationId;} public LocalDate getEffectiveFrom(){return effectiveFrom;} public LocalDate getEffectiveUntil(){return effectiveUntil;} public Status getStatus(){return status;}
    public UUID getPreparedByUserId(){return preparedByUserId;} public UUID getApprovedByUserId(){return approvedByUserId;} public Instant getApprovedAt(){return approvedAt;} public String getApprovalReason(){return approvalReason;}
    public UUID getEndedByUserId(){return endedByUserId;} public Instant getEndedAt(){return endedAt;} public String getEndReason(){return endReason;} public UUID getBillingEventId(){return billingEventId;} public BillingStatus getBillingStatus(){return billingStatus;}
}
