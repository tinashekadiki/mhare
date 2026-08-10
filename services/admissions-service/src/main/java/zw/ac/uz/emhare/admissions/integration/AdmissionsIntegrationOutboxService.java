package zw.ac.uz.emhare.admissions.integration;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.ApplicationFeeRequiredEvent;
import zw.ac.uz.emhare.common.messaging.AcceptedOfferReadyForConversionEvent;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.NotificationRequestedEvent;
import zw.ac.uz.emhare.common.messaging.MissingApplicationDocumentWorkflowRequestedEvent;
import zw.ac.uz.emhare.admissions.application.AdmissionOffer;
import zw.ac.uz.emhare.admissions.application.Application;
import zw.ac.uz.emhare.admissions.application.ApplicantReferee;
import zw.ac.uz.emhare.admissions.application.AcademicReviewAssignment;
import zw.ac.uz.emhare.admissions.application.AcademicUnitRecommendation;
import zw.ac.uz.emhare.common.messaging.AcademicReviewReleasedEvent;
import zw.ac.uz.emhare.common.messaging.AcademicRecommendationRecordedEvent;
import zw.ac.uz.emhare.common.messaging.OfferLetterRequestedEvent;

/** @author Tinashe K */
@Service
public class AdmissionsIntegrationOutboxService {

    private final AdmissionsOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AdmissionsIntegrationOutboxService(
            AdmissionsOutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void enqueueApplicationFeeRequired(
            UUID applicationId,
            UUID applicantUserId,
            UUID applicantKeycloakUserId,
            BigDecimal amountDue,
            String currencyCode) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = clock.instant();
        ApplicationFeeRequiredEvent event = new ApplicationFeeRequiredEvent(
                eventId,
                ApplicationFeeRequiredEvent.CURRENT_SCHEMA_VERSION,
                occurredAt,
                applicationId,
                applicantUserId,
                applicantKeycloakUserId,
                amountDue,
                currencyCode,
                true);
        outboxEventRepository.save(new AdmissionsOutboxEvent(
                eventId,
                EmhareMessagingTopology.APPLICATION_FEE_REQUIRED_EVENT,
                EmhareMessagingTopology.APPLICATION_FEE_REQUIRED_EVENT,
                serialize(event),
                occurredAt));
    }

    public void enqueueAcademicReviewReleased(AcademicReviewAssignment assignment) {
        UUID eventId = UUID.nameUUIDFromBytes(("academic-review-release:" + assignment.getId() + ":"
                + assignment.getReleaseAttempt() + ":" + assignment.getStatusCode()).getBytes(StandardCharsets.UTF_8));
        if (outboxEventRepository.existsById(eventId)) return;
        Instant occurredAt = clock.instant();
        AcademicReviewReleasedEvent event = new AcademicReviewReleasedEvent(
                eventId, AcademicReviewReleasedEvent.CURRENT_SCHEMA_VERSION, occurredAt,
                assignment.getId(), assignment.getApplication().getId(),
                assignment.getApplication().getApplicationNumber(), assignment.getProgrammeChoice().getId(),
                assignment.getProgrammeChoice().getProgrammeCode(), assignment.getProgrammeChoice().getProgrammeName(),
                assignment.getRecommendationAcademicUnitId(), assignment.getRecommendationAcademicUnitCode(),
                assignment.getRecommendationAcademicUnitName(), assignment.getReleasedByUserId(), assignment.getDueAt());
        outboxEventRepository.save(new AdmissionsOutboxEvent(eventId,
                EmhareMessagingTopology.ACADEMIC_REVIEW_RELEASED_EVENT,
                EmhareMessagingTopology.ACADEMIC_REVIEW_RELEASED_EVENT, serialize(event), occurredAt));
    }

    public void enqueueAcademicRecommendationRecorded(
            AcademicReviewAssignment assignment, AcademicUnitRecommendation recommendation) {
        UUID eventId = UUID.nameUUIDFromBytes(("academic-recommendation:" + recommendation.getId())
                .getBytes(StandardCharsets.UTF_8));
        if (outboxEventRepository.existsById(eventId)) return;
        Instant occurredAt = clock.instant();
        AcademicRecommendationRecordedEvent event = new AcademicRecommendationRecordedEvent(
                eventId, AcademicRecommendationRecordedEvent.CURRENT_SCHEMA_VERSION, occurredAt,
                assignment.getId(), recommendation.getId(), recommendation.getRecommendationCode(),
                recommendation.getRecommendedByUserId(), recommendation.getRecommendedAt());
        outboxEventRepository.save(new AdmissionsOutboxEvent(eventId,
                EmhareMessagingTopology.ACADEMIC_RECOMMENDATION_RECORDED_EVENT,
                EmhareMessagingTopology.ACADEMIC_RECOMMENDATION_RECORDED_EVENT, serialize(event), occurredAt));
    }

    public void enqueueOfferLetterRequested(AdmissionOffer offer, UUID requestedByUserId) {
        UUID eventId = UUID.nameUUIDFromBytes(("offer-letter:" + offer.getId() + ":" + offer.getVersion())
                .getBytes(StandardCharsets.UTF_8));
        if (outboxEventRepository.existsById(eventId)) return;
        Instant occurredAt = clock.instant();
        var application = offer.getApplication();
        var applicant = application.getApplicant();
        OfferLetterRequestedEvent event = new OfferLetterRequestedEvent(
                eventId, OfferLetterRequestedEvent.CURRENT_SCHEMA_VERSION, occurredAt,
                offer.getId(), offer.getVersion(), offer.getOfferNumber(), application.getId(),
                application.getApplicationNumber(), applicant.getApplicantNumber(), applicant.getDisplayName(),
                applicant.getPrimaryEmail(), offer.getProgrammeId(), offer.getProgrammeCode(), offer.getProgrammeName(),
                offer.getOfferTypeCode(), offer.getConditionsText(), offer.getAcceptanceDeadline(),
                offer.getRegistrationDate(), offer.getOrientationDate(), offer.getCommencementDate(), requestedByUserId);
        outboxEventRepository.save(new AdmissionsOutboxEvent(eventId,
                EmhareMessagingTopology.OFFER_LETTER_REQUESTED_EVENT,
                EmhareMessagingTopology.OFFER_LETTER_REQUESTED_EVENT, serialize(event), occurredAt));
    }

    public void enqueueAcceptedOfferReadyForConversion(UUID eventId, AdmissionOffer offer) {
        Instant occurredAt = clock.instant();
        var application = offer.getApplication();
        var applicant = application.getApplicant();
        AcceptedOfferReadyForConversionEvent event = new AcceptedOfferReadyForConversionEvent(
                eventId,
                AcceptedOfferReadyForConversionEvent.CURRENT_SCHEMA_VERSION,
                occurredAt,
                application.getId(),
                application.getApplicationNumber(),
                offer.getId(),
                offer.getOfferNumber(),
                applicant.getId(),
                applicant.getUserId(),
                applicant.getApplicantNumber(),
                applicant.getApplicantCategoryCode(),
                applicant.getFirstName(),
                applicant.getLastName(),
                applicant.getPrimaryEmail(),
                offer.getProgrammeChoice().getId(),
                offer.getProgrammeId(),
                offer.getProgrammeVersionId(),
                offer.getProgrammeCode(),
                offer.getProgrammeName(),
                offer.getIntakeId(),
                offer.getCommencementDate());
        outboxEventRepository.save(new AdmissionsOutboxEvent(
                eventId,
                EmhareMessagingTopology.ACCEPTED_OFFER_READY_FOR_CONVERSION_EVENT,
                EmhareMessagingTopology.ACCEPTED_OFFER_READY_FOR_CONVERSION_EVENT,
                serialize(event),
                occurredAt));
    }

    public void enqueueApplicationSubmittedNotification(Application application) {
        enqueueApplicantNotifications(
                "APPLICATION_SUBMITTED",
                "APPLICATION_SUBMITTED",
                "application-submitted:" + application.getId(),
                application,
                Map.of(
                        "firstName", application.getApplicant().getFirstName(),
                        "applicationNumber", application.getApplicationNumber()));
    }

    public void enqueueRefereeReferenceRequest(
            Application application,
            ApplicantReferee referee,
            UUID invitationId,
            String responseUrl,
            Instant expiresAt) {
        enqueueNotification(
                "REFEREE_REFERENCE_REQUEST",
                "REFEREE_REFERENCE_REQUEST_EMAIL",
                "referee-reference-request:" + invitationId,
                "EMAIL",
                null,
                referee.getEmail(),
                referee.getEmail(),
                Map.of(
                        "refereeName", referee.getFullName(),
                        "applicantName", application.getApplicant().getDisplayName(),
                        "applicationNumber", application.getApplicationNumber(),
                        "applicationTypeName", application.getApplicationType().getName(),
                        "responseUrl", responseUrl,
                        "expiresAt", expiresAt.toString()));
    }

    public void enqueuePaymentConfirmedNotification(Application application, String paymentReference) {
        enqueueApplicantNotifications(
                "PAYMENT_CONFIRMED",
                "PAYMENT_CONFIRMED",
                "payment-confirmed:" + application.getId(),
                application,
                Map.of(
                        "firstName", application.getApplicant().getFirstName(),
                        "applicationNumber", application.getApplicationNumber(),
                        "paymentReference", paymentReference));
    }

    public void enqueueMissingDocumentsNotification(
            Application application,
            String requirementCode,
            String rejectionReason,
            UUID documentId,
            long documentVersion,
            UUID verifierUserId) {
        enqueueApplicantNotifications(
                "MISSING_DOCUMENTS",
                "MISSING_DOCUMENTS",
                "missing-document:" + documentId + ":" + documentVersion,
                application,
                Map.of(
                        "firstName", application.getApplicant().getFirstName(),
                        "applicationNumber", application.getApplicationNumber(),
                        "documentList", requirementCode + " — " + rejectionReason));
        enqueueMissingDocumentWorkflow(
                application,
                requirementCode,
                rejectionReason,
                documentId,
                documentVersion,
                verifierUserId);
    }

    private void enqueueMissingDocumentWorkflow(
            Application application,
            String requirementCode,
            String rejectionReason,
            UUID documentId,
            long documentVersion,
            UUID verifierUserId) {
        String idempotencyKey = "missing-document-workflow:" + documentId + ":" + documentVersion;
        UUID eventId = UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8));
        if (outboxEventRepository.existsById(eventId)) return;
        Instant occurredAt = clock.instant();
        MissingApplicationDocumentWorkflowRequestedEvent event =
                new MissingApplicationDocumentWorkflowRequestedEvent(
                        eventId,
                        MissingApplicationDocumentWorkflowRequestedEvent.CURRENT_SCHEMA_VERSION,
                        occurredAt,
                        application.getId(),
                        application.getApplicationNumber(),
                        application.getApplicant().getUserId(),
                        documentId,
                        documentVersion,
                        requirementCode,
                        rejectionReason,
                        verifierUserId,
                        application.getAdmissionCycle().getClosesAt());
        outboxEventRepository.save(new AdmissionsOutboxEvent(
                eventId,
                EmhareMessagingTopology.MISSING_APPLICATION_DOCUMENT_WORKFLOW_REQUESTED_EVENT,
                EmhareMessagingTopology.MISSING_APPLICATION_DOCUMENT_WORKFLOW_REQUESTED_EVENT,
                serialize(event),
                occurredAt));
    }

    public void enqueueVerificationDecisionNotification(Application application) {
        enqueueApplicantNotifications(
                "VERIFICATION_DECISION",
                "VERIFICATION_DECISION",
                "verification-decision:" + application.getId() + ":" + application.getStatusCode(),
                application,
                Map.of(
                        "firstName", application.getApplicant().getFirstName(),
                        "applicationNumber", application.getApplicationNumber(),
                        "decision", application.getStatusCode()));
    }

    public void enqueueOfferDispatchedNotification(AdmissionOffer offer) {
        enqueueApplicantNotifications(
                "OFFER_DISPATCHED",
                "OFFER_DISPATCHED",
                "offer-dispatched:" + offer.getId(),
                offer.getApplication(),
                Map.of(
                        "firstName", offer.getApplication().getApplicant().getFirstName(),
                        "offerNumber", offer.getOfferNumber(),
                        "programmeName", offer.getProgrammeName(),
                        "acceptanceDeadline", offer.getAcceptanceDeadline().toString()));
    }

    public void enqueueOfferResponseNotification(AdmissionOffer offer) {
        enqueueApplicantNotifications(
                "OFFER_RESPONSE",
                "OFFER_RESPONSE",
                "offer-response:" + offer.getId(),
                offer.getApplication(),
                Map.of(
                        "firstName", offer.getApplication().getApplicant().getFirstName(),
                        "offerNumber", offer.getOfferNumber(),
                        "response", offer.getStatusCode()));
    }

    public void enqueueStudentConversionNotification(AdmissionOffer offer) {
        enqueueApplicantNotifications(
                "STUDENT_CONVERSION",
                "STUDENT_CONVERSION",
                "student-conversion:" + offer.getConversionRequestId(),
                offer.getApplication(),
                Map.of(
                        "firstName", offer.getApplication().getApplicant().getFirstName(),
                        "studentNumber", offer.getConvertedStudentNumber(),
                        "programmeName", offer.getProgrammeName()));
    }

    private void enqueueApplicantNotifications(
            String eventType,
            String templateCodePrefix,
            String businessIdempotencyKey,
            Application application,
            Map<String, String> variables) {
        enqueueNotification(
                eventType,
                templateCodePrefix + "_EMAIL",
                businessIdempotencyKey + ":email",
                "EMAIL",
                application.getApplicant().getUserId(),
                application.getApplicant().getUserId().toString(),
                application.getApplicant().getPrimaryEmail(),
                variables);
        enqueueNotification(
                eventType,
                templateCodePrefix + "_IN_APP",
                businessIdempotencyKey + ":in-app",
                "IN_APP",
                application.getApplicant().getUserId(),
                application.getApplicant().getUserId().toString(),
                application.getApplicant().getUserId().toString(),
                variables);
    }

    private void enqueueNotification(
            String eventType,
            String templateCode,
            String businessIdempotencyKey,
            String channel,
            UUID recipientUserId,
            String recipientKey,
            String recipientAddress,
            Map<String, String> variables) {
        String idempotencyKey = "admissions:" + businessIdempotencyKey;
        UUID eventId = UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8));
        if (outboxEventRepository.existsById(eventId)) {
            return;
        }
        Instant occurredAt = clock.instant();
        NotificationRequestedEvent notification = new NotificationRequestedEvent(
                eventId,
                NotificationRequestedEvent.CURRENT_SCHEMA_VERSION,
                occurredAt,
                "admissions-service",
                eventId,
                idempotencyKey,
                eventType,
                templateCode,
                channel,
                "en-ZW",
                recipientUserId,
                recipientKey,
                recipientAddress,
                "HIGH",
                null,
                8,
                variables);
        outboxEventRepository.save(new AdmissionsOutboxEvent(
                eventId,
                EmhareMessagingTopology.NOTIFICATION_REQUESTED_EVENT,
                EmhareMessagingTopology.NOTIFICATION_REQUESTED_EVENT,
                serialize(notification),
                occurredAt));
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Admissions integration event could not be serialized.", exception);
        }
    }
}
