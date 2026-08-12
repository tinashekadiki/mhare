package zw.ac.uz.emhare.admissions.domain.model;

import zw.ac.uz.emhare.admissions.application.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@Table(name = "applications", uniqueConstraints = @UniqueConstraint(name = "uk_applications_application_number", columnNames = "application_number"))
public class Application extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admission_cycle_id", nullable = false)
    private AdmissionCycle admissionCycle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_type_id", nullable = false)
    private ApplicationType applicationType;

    @Column(name = "application_number", nullable = false, length = 50)
    private String applicationNumber;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "payment_required", nullable = false)
    private boolean paymentRequired;

    @Column(name = "payment_confirmed_at")
    private Instant paymentConfirmedAt;

    @Column(name = "payment_override_by_user_id")
    private UUID paymentOverrideByUserId;

    @Column(name = "payment_override_reason", length = 500)
    private String paymentOverrideReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApplicationStatus status;

    @Column(name = "status_reason", length = 1000)
    private String statusReason;

    @Column(name = "sections_complete", nullable = false)
    private boolean sectionsComplete;

    @Column(name = "declaration_accepted_at")
    private Instant declarationAcceptedAt;

    @Column(name = "declaration_accepted_by_user_id")
    private UUID declarationAcceptedByUserId;

    @Column(name = "declaration_version", length = 50)
    private String declarationVersion;

    @Column(name = "professional_achievements_declared_none", nullable = false)
    private boolean professionalAchievementsDeclaredNone;

    @Column(name = "verified_by_user_id")
    private UUID verifiedByUserId;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "calculated_total_points", precision = 8, scale = 2)
    private BigDecimal calculatedTotalPoints;

    @Column(name = "points_calculated_at")
    private Instant pointsCalculatedAt;

    @Column(name = "legacy_statu_id")
    private Long legacyStatuId;

    protected Application() {
    }

    public Application(
            AdmissionCycle admissionCycle,
            Applicant applicant,
            ApplicationType applicationType,
            String applicationNumber,
            boolean paymentRequired) {
        this.admissionCycle = admissionCycle;
        this.applicant = applicant;
        this.applicationType = applicationType;
        this.applicationNumber = applicationNumber;
        this.paymentRequired = paymentRequired;
        this.status = ApplicationStatus.DRAFT;
        this.sectionsComplete = false;
    }

    public void submit(String reason) {
        if (status != ApplicationStatus.DRAFT) {
            throw new IllegalStateException("Only a draft application can be submitted.");
        }
        submittedAt = Instant.now();
        statusReason = reason;
        status = ApplicationStatus.SUBMITTED;
    }

    public void recordCalculatedPoints(BigDecimal totalPoints, Instant calculatedAt) {
        if (totalPoints == null || calculatedAt == null) {
            throw new IllegalArgumentException("Calculated points and calculation time are required.");
        }
        calculatedTotalPoints = totalPoints;
        pointsCalculatedAt = calculatedAt;
    }

    public void returnToDraft(String reason) {
        if (status != ApplicationStatus.SUBMITTED && status != ApplicationStatus.UNDER_REVIEW) {
            throw new IllegalStateException("Only a submitted application or an application under review can return to draft.");
        }
        if (reason == null || reason.trim().length() < 10) {
            throw new IllegalArgumentException("A correction reason of at least 10 characters is required.");
        }
        status = ApplicationStatus.DRAFT;
        statusReason = reason.trim();
        submittedAt = null;
        verifiedByUserId = null;
        verifiedAt = null;
        calculatedTotalPoints = null;
        pointsCalculatedAt = null;
        invalidateDeclaration();
    }

    public boolean confirmPayment(Instant confirmedAt) {
        if (!paymentRequired) {
            throw new IllegalStateException("This application does not require an application fee.");
        }
        if (paymentConfirmedAt != null) {
            return false;
        }
        paymentConfirmedAt = confirmedAt;
        return true;
    }

    public void overridePayment(UUID actorUserId, String reason) {
        if (!paymentRequired) {
            throw new IllegalStateException("This application does not require an application fee.");
        }
        if (paymentOverrideByUserId != null) {
            throw new IllegalStateException("Application fee has already been waived.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A payment waiver reason is required.");
        }
        paymentOverrideByUserId = actorUserId;
        paymentOverrideReason = reason.trim();
    }

    public void moveToUnderReview(UUID verifiedByUserId, String reason) {
        if (status != ApplicationStatus.SUBMITTED) {
            throw new IllegalStateException("Only a submitted application can enter review.");
        }
        if (!canEnterReview()) {
            throw new IllegalStateException("Application fee must be confirmed or waived before review.");
        }
        this.verifiedByUserId = verifiedByUserId;
        this.verifiedAt = Instant.now();
        this.statusReason = reason;
        this.status = ApplicationStatus.UNDER_REVIEW;
    }

    public void applyEvaluationOutcome(boolean anyEligible, boolean allEvaluated, String reason) {
        if (status != ApplicationStatus.UNDER_REVIEW
                && status != ApplicationStatus.ELIGIBLE
                && status != ApplicationStatus.NOT_ELIGIBLE) {
            throw new IllegalStateException("Only an application under review can receive evaluation outcomes.");
        }
        status = anyEligible
                ? ApplicationStatus.ELIGIBLE
                : allEvaluated ? ApplicationStatus.NOT_ELIGIBLE : ApplicationStatus.UNDER_REVIEW;
        statusReason = reason;
    }

    public void enterAcademicReview(String reason) {
        if (status != ApplicationStatus.ELIGIBLE) {
            throw new IllegalStateException("Only an eligible application can enter academic review.");
        }
        status = ApplicationStatus.UNDER_ACADEMIC_REVIEW;
        statusReason = reason;
    }

    public void recordChoiceDecision(DecisionOutcome decision, String reason) {
        if (status != ApplicationStatus.UNDER_ACADEMIC_REVIEW) {
            throw new IllegalStateException("Only an application under academic review can receive an admission decision.");
        }
        status = decision == DecisionOutcome.ADMIT ? ApplicationStatus.ADMITTED : ApplicationStatus.REJECTED;
        statusReason = reason;
    }

    public void continueAfterChoiceRejection(String reason) {
        if (status != ApplicationStatus.UNDER_ACADEMIC_REVIEW) {
            throw new IllegalStateException("Only an application under academic review can continue to another choice.");
        }
        statusReason = reason;
    }

    public void rejectAfterAllChoices(String reason) {
        if (status != ApplicationStatus.UNDER_ACADEMIC_REVIEW
                && status != ApplicationStatus.UNDER_REVIEW
                && status != ApplicationStatus.NOT_ELIGIBLE) {
            throw new IllegalStateException("The application is not awaiting a final rejection.");
        }
        status = ApplicationStatus.REJECTED;
        statusReason = reason;
    }

    public void markOffered(String reason) {
        if (status != ApplicationStatus.ADMITTED) {
            throw new IllegalStateException("Only an admitted application can receive an offer.");
        }
        status = ApplicationStatus.OFFERED;
        statusReason = reason;
    }

    public void recordOfferResponse(OfferResponseType response, String reason) {
        if (status != ApplicationStatus.OFFERED) {
            throw new IllegalStateException("Only an offered application can record an offer response.");
        }
        status = response == OfferResponseType.ACCEPTED ? ApplicationStatus.ACCEPTED : ApplicationStatus.DECLINED;
        statusReason = reason;
    }

    public void reopenAfterOfferClosed(String reason) {
        if (status != ApplicationStatus.OFFERED) {
            throw new IllegalStateException("Only an offered application can return to admitted status.");
        }
        status = ApplicationStatus.ADMITTED;
        statusReason = reason;
    }

    public void markConverted(String reason) {
        if (status != ApplicationStatus.ACCEPTED) {
            throw new IllegalStateException("Only an accepted application can be converted to a student.");
        }
        status = ApplicationStatus.CONVERTED;
        statusReason = reason;
    }

    public boolean canEnterReview() {
        return !paymentRequired || paymentConfirmedAt != null || paymentOverrideByUserId != null;
    }

    public boolean canSubmit() {
        return status == ApplicationStatus.DRAFT && sectionsComplete;
    }

    public void recordSectionCompleteness(boolean complete) {
        if (status != ApplicationStatus.DRAFT) {
            return;
        }
        sectionsComplete = complete;
    }

    public void recordProfessionalAchievementsDeclaredNone(boolean declaredNone) {
        professionalAchievementsDeclaredNone = declaredNone;
        invalidateDeclaration();
    }

    public boolean isProfessionalAchievementsDeclaredNone() { return professionalAchievementsDeclaredNone; }

    public void acceptDeclaration(UUID applicantUserId, String version, Instant acceptedAt) {
        if (status != ApplicationStatus.DRAFT) {
            throw new IllegalStateException("Only a draft application can accept the applicant declaration.");
        }
        if (!getApplicant().getUserId().equals(applicantUserId)) {
            throw new IllegalArgumentException("Application not found.");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Declaration version is required.");
        }
        if (acceptedAt == null) {
            throw new IllegalArgumentException("Declaration acceptance time is required.");
        }
        declarationAcceptedAt = acceptedAt;
        declarationAcceptedByUserId = applicantUserId;
        declarationVersion = version.trim();
    }

    public void invalidateDeclaration() {
        if (status != ApplicationStatus.DRAFT) {
            return;
        }
        declarationAcceptedAt = null;
        declarationAcceptedByUserId = null;
        declarationVersion = null;
        sectionsComplete = false;
    }

    public boolean isSectionsComplete() {
        return sectionsComplete;
    }

    public boolean isDeclarationAccepted() {
        return declarationAcceptedAt != null && declarationAcceptedByUserId != null;
    }

    public Instant getDeclarationAcceptedAt() {
        return declarationAcceptedAt;
    }

    public String getDeclarationVersion() {
        return declarationVersion;
    }

    public BigDecimal getCalculatedTotalPoints() { return calculatedTotalPoints; }
    public Instant getPointsCalculatedAt() { return pointsCalculatedAt; }

    public AdmissionCycle getAdmissionCycle() {
        return admissionCycle;
    }

    public Applicant getApplicant() {
        return applicant;
    }

    public ApplicationType getApplicationType() {
        return applicationType;
    }

    public String getApplicationNumber() {
        return applicationNumber;
    }

    public boolean isPaymentRequired() {
        return paymentRequired;
    }

    public Instant getPaymentConfirmedAt() {
        return paymentConfirmedAt;
    }

    public UUID getPaymentOverrideByUserId() {
        return paymentOverrideByUserId;
    }

    public String getPaymentOverrideReason() {
        return paymentOverrideReason;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public String getStatusCode() {
        return status.name();
    }

    public String getStatusReason() {
        return statusReason;
    }
}
