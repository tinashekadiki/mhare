package zw.ac.uz.emhare.dining.operations.domain.model;

import zw.ac.uz.emhare.dining.operations.*;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name="student_dietary_requirements") @SQLRestriction("deleted_at IS NULL")
public class StudentDietaryRequirement extends AuditableEntity {
    public enum Severity { INFORMATION, IMPORTANT, CRITICAL } public enum Status { ACTIVE, RESOLVED, EXPIRED }
    @Column(name="student_id",nullable=false) private UUID studentId; @Column(name="student_number",nullable=false,length=40) private String studentNumber;
    @Column(name="requirement_code",nullable=false,length=50) private String requirementCode; @Column(nullable=false,length=1000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Severity severity; @Column(name="clinical_document_id") private UUID clinicalDocumentId;
    @Column(name="effective_from",nullable=false) private LocalDate effectiveFrom; @Column(name="effective_until") private LocalDate effectiveUntil;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status; @Column(name="recorded_by_user_id",nullable=false) private UUID recordedByUserId;
    @Column(name="resolved_by_user_id") private UUID resolvedByUserId; @Column(name="resolved_at") private Instant resolvedAt; @Column(name="resolution_reason",length=1000) private String resolutionReason;
    protected StudentDietaryRequirement() {}
    public StudentDietaryRequirement(UUID studentId,String studentNumber,String code,String description,Severity severity,UUID documentId,LocalDate from,LocalDate until,UUID actor){
        if(studentId==null||severity==null||from==null||actor==null)throw new IllegalArgumentException("Student, severity, effective date, and recording operator are required.");if(until!=null&&until.isBefore(from))throw new IllegalArgumentException("Dietary requirement end cannot precede its start.");
        this.studentId=studentId;this.studentNumber=DiningOperationValues.code(studentNumber,"Student number");requirementCode=DiningOperationValues.code(code,"Requirement code");this.description=DiningOperationValues.required(description,"Dietary requirement description");this.severity=severity;clinicalDocumentId=documentId;effectiveFrom=from;effectiveUntil=until;recordedByUserId=actor;status=Status.ACTIVE;
    }
    public void resolve(Status target,UUID actor,String reason,Instant at,long expected){DiningOperationValues.version(getVersion(),expected,"Dietary requirement");if(status!=Status.ACTIVE||target==Status.ACTIVE)throw new IllegalStateException("Only an active dietary requirement can be resolved or expired.");if(actor==null||actor.equals(recordedByUserId))throw new IllegalArgumentException("A different authorised operator must resolve the dietary requirement.");resolvedByUserId=actor;resolvedAt=at;resolutionReason=DiningOperationValues.required(reason,"Resolution reason");status=target;}
    public UUID getStudentId(){return studentId;} public String getStudentNumber(){return studentNumber;} public String getRequirementCode(){return requirementCode;} public String getDescription(){return description;} public Severity getSeverity(){return severity;} public UUID getClinicalDocumentId(){return clinicalDocumentId;} public LocalDate getEffectiveFrom(){return effectiveFrom;} public LocalDate getEffectiveUntil(){return effectiveUntil;} public Status getStatus(){return status;} public UUID getRecordedByUserId(){return recordedByUserId;} public UUID getResolvedByUserId(){return resolvedByUserId;} public Instant getResolvedAt(){return resolvedAt;} public String getResolutionReason(){return resolutionReason;}
}
