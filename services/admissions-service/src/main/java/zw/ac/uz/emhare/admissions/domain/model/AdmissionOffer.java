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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "offers")
public class AdmissionOffer extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_choice_id", nullable = false)
    private ApplicationProgrammeChoice programmeChoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_batch_id")
    private OfferBatch offerBatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "programme_choice_decision_id")
    private ProgrammeChoiceDecision programmeChoiceDecision;

    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;

    @Column(name = "programme_version_id", nullable = false)
    private UUID programmeVersionId;

    @Column(name = "programme_code", nullable = false, length = 50)
    private String programmeCode;

    @Column(name = "programme_name", nullable = false, length = 200)
    private String programmeName;

    @Column(name = "intake_id", nullable = false)
    private UUID intakeId;

    @Column(name = "offer_number", nullable = false, length = 60)
    private String offerNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "offer_type", length = 30)
    private OfferType offerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OfferStatus status;

    @Column(name = "conditions_text", length = 4000)
    private String conditionsText;

    @Column(name = "acceptance_deadline")
    private Instant acceptanceDeadline;

    @Column(name = "registration_date")
    private LocalDate registrationDate;

    @Column(name = "orientation_date")
    private LocalDate orientationDate;

    @Column(name = "commencement_date")
    private LocalDate commencementDate;

    @Column(name = "generated_document_id")
    private UUID generatedDocumentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_document_version_id")
    private OfferDocumentVersion currentDocumentVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_publication_id")
    private OfferPublication currentPublication;

    @Column(name = "amendment_pending", nullable = false)
    private boolean amendmentPending;

    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "withdrawn_by_user_id")
    private UUID withdrawnByUserId;

    @Column(name = "withdrawal_reason", length = 1000)
    private String withdrawalReason;

    @Column(name = "converted_at")
    private Instant convertedAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "expiry_reason", length = 1000)
    private String expiryReason;

    @Column(name = "conversion_event_id")
    private UUID conversionEventId;

    @Column(name = "conversion_requested_at")
    private Instant conversionRequestedAt;

    @Column(name = "conversion_request_id")
    private UUID conversionRequestId;

    @Column(name = "converted_student_id")
    private UUID convertedStudentId;

    @Column(name = "converted_student_number", length = 40)
    private String convertedStudentNumber;

    protected AdmissionOffer() {
    }

    public AdmissionOffer(
            Application application,
            ApplicationProgrammeChoice programmeChoice,
            OfferBatch offerBatch,
            String offerNumber,
            OfferType offerType,
            String conditionsText,
            Instant acceptanceDeadline,
            LocalDate registrationDate,
            LocalDate orientationDate,
            LocalDate commencementDate,
            UUID generatedDocumentId,
            Instant now) {
        if (offerBatch.getStatus() != OfferBatchStatus.APPROVED) {
            throw new IllegalStateException("Offers can only be created in an approved offer batch.");
        }
        if (!application.getId().equals(programmeChoice.getApplication().getId())) {
            throw new IllegalArgumentException("Programme choice does not belong to the application.");
        }
        if (!acceptanceDeadline.isAfter(now)) {
            throw new IllegalArgumentException("Offer acceptance deadline must be in the future.");
        }
        if (offerType == OfferType.CONDITIONAL && (conditionsText == null || conditionsText.isBlank())) {
            throw new IllegalArgumentException("Conditional offers require conditions.");
        }
        if (registrationDate != null && registrationDate.isAfter(commencementDate)) {
            throw new IllegalArgumentException("Registration date cannot be after commencement date.");
        }
        if (orientationDate != null && orientationDate.isAfter(commencementDate)) {
            throw new IllegalArgumentException("Orientation date cannot be after commencement date.");
        }
        this.application = application;
        this.programmeChoice = programmeChoice;
        this.offerBatch = offerBatch;
        this.programmeId = programmeChoice.getProgrammeId();
        this.programmeVersionId = programmeChoice.getProgrammeVersionId();
        this.programmeCode = programmeChoice.getProgrammeCode();
        this.programmeName = programmeChoice.getProgrammeName();
        this.intakeId = application.getAdmissionCycle().getIntakeId();
        this.offerNumber = offerNumber;
        this.offerType = offerType;
        this.status = OfferStatus.DRAFT;
        this.conditionsText = conditionsText == null || conditionsText.isBlank() ? null : conditionsText.trim();
        this.acceptanceDeadline = acceptanceDeadline;
        this.registrationDate = registrationDate;
        this.orientationDate = orientationDate;
        this.commencementDate = commencementDate;
        this.generatedDocumentId = generatedDocumentId;
    }

    public AdmissionOffer(
            Application application,
            ApplicationProgrammeChoice programmeChoice,
            ProgrammeChoiceDecision programmeChoiceDecision,
            String offerNumber) {
        if (programmeChoiceDecision.getDecision() != DecisionOutcome.ADMIT
                || !programmeChoiceDecision.getProgrammeChoice().getId().equals(programmeChoice.getId())) {
            throw new IllegalArgumentException("Direct offers require the admitted programme-choice decision.");
        }
        if (!application.getId().equals(programmeChoice.getApplication().getId())) {
            throw new IllegalArgumentException("Programme choice does not belong to the application.");
        }
        this.application = application;
        this.programmeChoice = programmeChoice;
        this.programmeChoiceDecision = programmeChoiceDecision;
        this.programmeId = programmeChoice.getProgrammeId();
        this.programmeVersionId = programmeChoice.getProgrammeVersionId();
        this.programmeCode = programmeChoice.getProgrammeCode();
        this.programmeName = programmeChoice.getProgrammeName();
        this.intakeId = application.getAdmissionCycle().getIntakeId();
        this.offerNumber = offerNumber;
        this.status = OfferStatus.DRAFT;
        this.amendmentPending = false;
    }

    public void updateTerms(OfferType offerType, String conditionsText, Instant acceptanceDeadline,
            LocalDate registrationDate, LocalDate orientationDate, LocalDate commencementDate, Instant now) {
        if (status != OfferStatus.DRAFT && status != OfferStatus.SENT) {
            throw new IllegalStateException("Only a draft or unanswered published offer can be edited.");
        }
        if (acceptanceDeadline == null || !acceptanceDeadline.isAfter(now) || commencementDate == null) {
            throw new IllegalArgumentException("A future acceptance deadline and commencement date are required.");
        }
        if (offerType == OfferType.CONDITIONAL && (conditionsText == null || conditionsText.isBlank())) {
            throw new IllegalArgumentException("Conditional offers require conditions.");
        }
        if (registrationDate != null && registrationDate.isAfter(commencementDate)
                || orientationDate != null && orientationDate.isAfter(commencementDate)) {
            throw new IllegalArgumentException("Registration and orientation cannot be after commencement.");
        }
        this.offerType = offerType;
        this.conditionsText = conditionsText == null || conditionsText.isBlank() ? null : conditionsText.trim();
        this.acceptanceDeadline = acceptanceDeadline;
        this.registrationDate = registrationDate;
        this.orientationDate = orientationDate;
        this.commencementDate = commencementDate;
        if (currentPublication != null) amendmentPending = true;
    }

    public void linkCurrentDocumentVersion(OfferDocumentVersion documentVersion) {
        if (documentVersion.getStatus() != OfferDocumentVersionStatus.STORED
                || documentVersion.getOffer() != this) {
            throw new IllegalArgumentException("Stored document version does not belong to this offer.");
        }
        currentDocumentVersion = documentVersion;
        generatedDocumentId = documentVersion.getGeneratedDocumentId();
    }

    public void publish(OfferPublication publication, UUID actorUserId, Instant now) {
        if (currentDocumentVersion == null || publication.getDocumentVersion() != currentDocumentVersion) {
            throw new IllegalStateException("The latest stored offer document must be published.");
        }
        if (offerType == null || acceptanceDeadline == null || commencementDate == null) {
            throw new IllegalStateException("Complete offer terms are required before publication.");
        }
        if (status == OfferStatus.DRAFT) {
            approvedByUserId = actorUserId;
            approvedAt = now;
        } else if (status != OfferStatus.SENT) {
            throw new IllegalStateException("This offer can no longer be published.");
        }
        currentPublication = publication;
        amendmentPending = false;
        status = OfferStatus.SENT;
        sentAt = now;
    }

    public void approve(UUID actorUserId, Instant now) {
        requireStatus(OfferStatus.DRAFT, "Only a draft offer can be approved.");
        if (generatedDocumentId == null) {
            throw new IllegalStateException("A stored generated offer document is required before approval.");
        }
        status = OfferStatus.APPROVED;
        approvedByUserId = actorUserId;
        approvedAt = now;
    }

    public void linkGeneratedDocument(UUID documentId) {
        if (documentId == null) throw new IllegalArgumentException("Generated offer document is required.");
        if (generatedDocumentId != null && !generatedDocumentId.equals(documentId)) {
            throw new IllegalStateException("Offer is already linked to a different generated document.");
        }
        generatedDocumentId = documentId;
    }

    public void markSent(Instant now) {
        requireStatus(OfferStatus.APPROVED, "Only an approved offer can be dispatched.");
        status = OfferStatus.SENT;
        sentAt = now;
    }

    public void respond(OfferResponseType response) {
        requireStatus(OfferStatus.SENT, "Only a sent offer can receive an applicant response.");
        if (currentPublication == null || amendmentPending) {
            throw new IllegalStateException("The current offer document must be published before responding.");
        }
        status = response == OfferResponseType.ACCEPTED ? OfferStatus.ACCEPTED : OfferStatus.DECLINED;
    }

    public void withdraw(UUID actorUserId, String reason) {
        if (status != OfferStatus.DRAFT && status != OfferStatus.APPROVED && status != OfferStatus.SENT) {
            throw new IllegalStateException("This offer can no longer be withdrawn.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("An offer withdrawal reason is required.");
        }
        status = OfferStatus.WITHDRAWN;
        withdrawnByUserId = actorUserId;
        withdrawalReason = reason.trim();
    }

    public void expire(Instant now, String reason) {
        requireStatus(OfferStatus.SENT, "Only a sent offer can expire.");
        if (!now.isAfter(acceptanceDeadline)) {
            throw new IllegalStateException("The offer acceptance deadline has not passed.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("An offer expiry reason is required.");
        }
        status = OfferStatus.EXPIRED;
        expiredAt = now;
        expiryReason = reason.trim();
    }

    public void markConverted(UUID conversionRequestId, UUID studentId, String studentNumber, Instant now) {
        requireStatus(OfferStatus.ACCEPTED, "Only an accepted offer can be converted.");
        if (this.conversionEventId == null) {
            throw new IllegalStateException("Offer conversion was not requested.");
        }
        if (conversionRequestId == null || studentId == null || studentNumber == null || studentNumber.isBlank()) {
            throw new IllegalArgumentException("Conversion request and student identifiers are required.");
        }
        status = OfferStatus.CONVERTED;
        this.conversionRequestId = conversionRequestId;
        this.convertedStudentId = studentId;
        this.convertedStudentNumber = studentNumber.trim();
        convertedAt = now;
    }

    public UUID requestConversion(Instant now) {
        requireStatus(OfferStatus.ACCEPTED, "Only an accepted offer can be handed off for conversion.");
        if (conversionEventId == null) {
            conversionEventId = UUID.randomUUID();
            conversionRequestedAt = now;
        }
        return conversionEventId;
    }

    private void requireStatus(OfferStatus required, String message) {
        if (status != required) throw new IllegalStateException(message);
    }

    public Application getApplication() { return application; }
    public ApplicationProgrammeChoice getProgrammeChoice() { return programmeChoice; }
    public OfferBatch getOfferBatch() { return offerBatch; }
    public ProgrammeChoiceDecision getProgrammeChoiceDecision() { return programmeChoiceDecision; }
    public UUID getProgrammeId() { return programmeId; }
    public UUID getProgrammeVersionId() { return programmeVersionId; }
    public String getProgrammeCode() { return programmeCode; }
    public String getProgrammeName() { return programmeName; }
    public UUID getIntakeId() { return intakeId; }
    public String getOfferNumber() { return offerNumber; }
    public String getOfferTypeCode() { return offerType == null ? null : offerType.name(); }
    public OfferType getOfferType() { return offerType; }
    public OfferStatus getStatus() { return status; }
    public String getStatusCode() { return status.name(); }
    public String getConditionsText() { return conditionsText; }
    public Instant getAcceptanceDeadline() { return acceptanceDeadline; }
    public LocalDate getRegistrationDate() { return registrationDate; }
    public LocalDate getOrientationDate() { return orientationDate; }
    public LocalDate getCommencementDate() { return commencementDate; }
    public UUID getGeneratedDocumentId() { return generatedDocumentId; }
    public OfferDocumentVersion getCurrentDocumentVersion() { return currentDocumentVersion; }
    public OfferPublication getCurrentPublication() { return currentPublication; }
    public boolean isAmendmentPending() { return amendmentPending; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getSentAt() { return sentAt; }
    public Instant getExpiredAt() { return expiredAt; }
    public String getExpiryReason() { return expiryReason; }
    public UUID getConversionEventId() { return conversionEventId; }
    public Instant getConversionRequestedAt() { return conversionRequestedAt; }
    public UUID getConversionRequestId() { return conversionRequestId; }
    public UUID getConvertedStudentId() { return convertedStudentId; }
    public String getConvertedStudentNumber() { return convertedStudentNumber; }
    public Instant getConvertedAt() { return convertedAt; }
}
