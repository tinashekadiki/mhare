package zw.ac.uz.emhare.assessmentresults.result.domain.model;

import zw.ac.uz.emhare.assessmentresults.result.*;
import jakarta.persistence.*;import java.time.Instant;import java.util.UUID;import org.hibernate.annotations.SQLRestriction;import org.hibernate.envers.Audited;import zw.ac.uz.emhare.common.persistence.AuditableEntity;
/** @author Tinashe K */
@Audited @Entity @Table(name="grading_schemes") @SQLRestriction("deleted_at IS NULL")
public class GradingScheme extends AuditableEntity{
 public enum Status{DRAFT,APPROVED,SUPERSEDED}
 @Column(nullable=false,length=30)private String code;@Column(nullable=false,length=150)private String name;@Column(name="scheme_version",nullable=false)private int schemeVersion;@Enumerated(EnumType.STRING)@Column(nullable=false,length=20)private Status status;@Column(name="approved_by_user_id")private UUID approvedByUserId;@Column(name="approved_at")private Instant approvedAt;@Column(name="approval_reason",length=1000)private String approvalReason;
 protected GradingScheme(){} public GradingScheme(String code,String name,int version){this.code=text(code).toUpperCase();this.name=text(name);schemeVersion=version;status=Status.DRAFT;}
 public void approve(UUID actor,String reason,Instant now,long expected){if(getVersion()!=expected)throw new IllegalStateException("Grading scheme changed. Refresh before retrying.");if(status!=Status.DRAFT)throw new IllegalStateException("Only a draft grading scheme can be approved.");status=Status.APPROVED;approvedByUserId=actor;approvedAt=now;approvalReason=text(reason);}
 static String text(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("A required value is missing.");return value.trim();}
 public String getCode(){return code;}public String getName(){return name;}public int getSchemeVersion(){return schemeVersion;}public Status getStatus(){return status;}public UUID getApprovedByUserId(){return approvedByUserId;}public Instant getApprovedAt(){return approvedAt;}public String getApprovalReason(){return approvalReason;}
}
