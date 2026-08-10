package zw.ac.uz.emhare.assessmentresults.assessment;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.assessmentresults.assessment.AssessmentEnums.SchemeStatus;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name="assessment_schemes") @SQLRestriction("deleted_at IS NULL")
public class AssessmentScheme extends AuditableEntity {
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="module_offering_id")
    private AssessmentModuleOffering moduleOffering;
    @Column(name="scheme_version", nullable=false) private int schemeVersion;
    @Column(nullable=false, length=150) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private SchemeStatus status;
    @Column(name="approval_reason", length=1000) private String approvalReason;
    @Column(name="approved_by_user_id") private UUID approvedByUserId;
    @Column(name="approved_at") private Instant approvedAt;

    protected AssessmentScheme() {}
    public AssessmentScheme(AssessmentModuleOffering moduleOffering, int schemeVersion, String name) {
        this.moduleOffering=moduleOffering; this.schemeVersion=schemeVersion;
        this.name=requireText(name, "Scheme name"); this.status=SchemeStatus.DRAFT;
    }
    public void approve(UUID actor, String reason, Instant now, long expectedVersion) {
        requireVersion(expectedVersion);
        if(status!=SchemeStatus.DRAFT) throw new IllegalStateException("Only a draft assessment scheme can be approved.");
        status=SchemeStatus.APPROVED; approvalReason=requireText(reason,"Approval reason"); approvedByUserId=actor; approvedAt=now;
        moduleOffering.activate();
    }
    public void supersede() { if(status!=SchemeStatus.APPROVED)throw new IllegalStateException("Only an approved scheme can be superseded."); status=SchemeStatus.SUPERSEDED; }
    private void requireVersion(long expected) { if(getVersion()!=expected) throw new IllegalStateException("Assessment scheme was changed by another user. Refresh before retrying."); }
    static String requireText(String value, String label) { if(value==null||value.isBlank()) throw new IllegalArgumentException(label+" is required."); return value.trim(); }
    public AssessmentModuleOffering getModuleOffering(){return moduleOffering;}
    public int getSchemeVersion(){return schemeVersion;} public String getName(){return name;}
    public SchemeStatus getStatus(){return status;} public String getApprovalReason(){return approvalReason;}
    public UUID getApprovedByUserId(){return approvedByUserId;} public Instant getApprovedAt(){return approvedAt;}
}
