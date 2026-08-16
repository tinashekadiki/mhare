package zw.ac.uz.emhare.admissions.application;

import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.*;
import zw.ac.uz.emhare.admissions.integration.AdmissionsIntegrationOutboxService;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.AcademicAdmissionsIntake;
import zw.ac.uz.emhare.admissions.application.OfferLetterFeeScheduleResolver.ResolvedOfferLetterCatalogue;

/** Governed, versioned direct-offer commands for the rolling workflow. @author Tinashe K */
@Service
public class DirectAdmissionOfferService {
    private final AdmissionOfferRepository offerRepository;
    private final OfferResponseRepository responseRepository;
    private final OfferConditionRepository conditionRepository;
    private final OfferDocumentVersionRepository documentRepository;
    private final OfferPublicationRepository publicationRepository;
    private final OfferDispatchRepository dispatchRepository;
    private final OfferStatusEventRepository statusEventRepository;
    private final ApplicationStatusEventRepository applicationStatusEventRepository;
    private final AdmissionsIntegrationOutboxService outboxService;
    private final OfferLetterFeeScheduleResolver feeScheduleResolver;
    private final CoreIdentityClient coreIdentityClient;
    private final AcademicSetupCatalogueClient academicSetupCatalogueClient;
    private final Clock clock;

    public DirectAdmissionOfferService(AdmissionOfferRepository offerRepository,
            OfferResponseRepository responseRepository, OfferConditionRepository conditionRepository,
            OfferDocumentVersionRepository documentRepository, OfferPublicationRepository publicationRepository,
            OfferDispatchRepository dispatchRepository, OfferStatusEventRepository statusEventRepository,
            ApplicationStatusEventRepository applicationStatusEventRepository,
            AdmissionsIntegrationOutboxService outboxService, OfferLetterFeeScheduleResolver feeScheduleResolver,
            CoreIdentityClient coreIdentityClient, AcademicSetupCatalogueClient academicSetupCatalogueClient, Clock clock) {
        this.offerRepository = offerRepository;
        this.responseRepository = responseRepository;
        this.conditionRepository = conditionRepository;
        this.documentRepository = documentRepository;
        this.publicationRepository = publicationRepository;
        this.dispatchRepository = dispatchRepository;
        this.statusEventRepository = statusEventRepository;
        this.applicationStatusEventRepository = applicationStatusEventRepository;
        this.outboxService = outboxService;
        this.feeScheduleResolver = feeScheduleResolver;
        this.coreIdentityClient = coreIdentityClient;
        this.academicSetupCatalogueClient = academicSetupCatalogueClient;
        this.clock = clock;
    }

    @Transactional
    public AdmissionOfferSummary update(UUID offerId, String offerTypeCode, String conditionsText,
            String authorization) {
        AdmissionOffer offer = offer(offerId);
        requireUnanswered(offer);
        OfferType offerType = parse(OfferType.class, offerTypeCode, "offer type");
        applyIntakeOfferDates(offer, offerType, conditionsText, authorization);
        return summary(offerRepository.saveAndFlush(offer));
    }

    @Transactional
    public DocumentGenerationResult generate(UUID offerId, UUID actorUserId) {
        return generate(offerId, actorUserId, null);
    }

    @Transactional
    public DocumentGenerationResult generate(UUID offerId, UUID actorUserId, String authorization) {
        AdmissionOffer offer = offer(offerId);
        requireUnanswered(offer);
        if (authorization != null && !authorization.isBlank() && offer.getOfferType() != null) {
            applyIntakeOfferDates(offer, offer.getOfferType(), offer.getConditionsText(), authorization);
            offerRepository.saveAndFlush(offer);
        }
        if (offer.getOfferType() == null || offer.getAcceptanceDeadline() == null || offer.getCommencementDate() == null) {
            throw new IllegalStateException("Complete offer terms are required before document generation.");
        }
        ResolvedOfferLetterCatalogue catalogue = feeScheduleResolver.resolve(offer, authorization, clock.instant());
        CoreIdentityClient.CoreInstitutionProfile institutionProfile = institutionProfile(authorization);
        OfferDocumentVersion latestRequested = documentRepository
                .findFirstByOfferIdAndStatusAndDeletedAtIsNullOrderByDocumentVersionDesc(
                        offerId, OfferDocumentVersionStatus.REQUESTED).orElse(null);
        if (latestRequested != null) {
            outboxService.enqueueOfferLetterRequested(
                    offer, latestRequested.getDocumentVersion(), actorUserId,
                    catalogue.highestAcademicUnitName(), catalogue.feeSchedule(), institutionProfile);
            return result(latestRequested);
        }
        int version = documentRepository.countByOfferIdAndDeletedAtIsNull(offerId) + 1;
        OfferDocumentVersion document = documentRepository.saveAndFlush(
                new OfferDocumentVersion(offer, version, actorUserId, clock.instant()));
        outboxService.enqueueOfferLetterRequested(offer, version, actorUserId,
                catalogue.highestAcademicUnitName(), catalogue.feeSchedule(), institutionProfile);
        return result(document);
    }

    private CoreIdentityClient.CoreInstitutionProfile institutionProfile(String authorization) {
        return authorization == null || authorization.isBlank() ? null : coreIdentityClient.institutionProfile(authorization);
    }

    private void applyIntakeOfferDates(AdmissionOffer offer, OfferType offerType, String conditionsText,
            String authorization) {
        AcademicAdmissionsIntake intake = academicSetupCatalogueClient.getAdmissionsIntake(offer.getIntakeId());
        if (intake.offerAcceptanceDeadline() == null || intake.commencementDate() == null) {
            throw new IllegalStateException("Configure the offer acceptance deadline and commencement date on intake "
                    + intake.code() + " before generating offers.");
        }
        offer.updateTerms(offerType, conditionsText, intake.offerAcceptanceDeadline(), intake.registrationDate(),
                intake.orientationDate(), intake.commencementDate(), clock.instant());
    }

    @Transactional
    public void linkStoredDocument(UUID offerId, long offerVersion, int documentVersion,
            UUID generatedDocumentId, String documentNumber, String storageBucket, String storageKey,
            String checksumSha256, Instant storedAt) {
        AdmissionOffer offer = offer(offerId);
        if (offer.getVersion() < offerVersion) {
            throw new IllegalStateException("Offer document event is newer than the local offer state.");
        }
        OfferDocumentVersion document = documentRepository
                .findByOfferIdAndDocumentVersionAndDeletedAtIsNull(offerId, documentVersion)
                .orElseThrow(() -> new IllegalArgumentException("Requested offer document version was not found."));
        document.store(generatedDocumentId, documentNumber, storageBucket, storageKey, checksumSha256, storedAt);
        offer.linkCurrentDocumentVersion(document);
        documentRepository.save(document);
        offerRepository.saveAndFlush(offer);
    }

    @Transactional
    public PublicationResult publishAndSend(UUID offerId, UUID actorUserId) {
        AdmissionOffer offer = offer(offerId);
        requireUnanswered(offer);
        OfferDocumentVersion document = offer.getCurrentDocumentVersion();
        if (document == null || document.getStatus() != OfferDocumentVersionStatus.STORED) {
            throw new IllegalStateException("The latest generated PDF must be stored before publication.");
        }
        OfferPublication existing = publicationRepository
                .findByOfferIdAndCurrentPublicationTrueAndDeletedAtIsNull(offerId).orElse(null);
        if (existing != null && existing.getDocumentVersion().getId().equals(document.getId())
                && !offer.isAmendmentPending()) {
            synchronizePublishedWorkflowState(offer, actorUserId);
            offerRepository.saveAndFlush(offer);
            OfferDispatch dispatch = dispatchRepository
                    .findAllByOfferPublicationIdAndDeletedAtIsNullOrderByAttemptNumberDesc(existing.getId())
                    .stream().findFirst().orElse(null);
            return result(existing, dispatch);
        }
        Instant now = clock.instant();
        if (existing != null) existing.supersede(now);
        int sequence = publicationRepository.countByOfferIdAndDeletedAtIsNull(offerId) + 1;
        UUID notificationEventId = UUID.nameUUIDFromBytes(("offer-publication-email:" + offerId + ":" + document.getDocumentVersion())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        OfferPublication publication = publicationRepository.saveAndFlush(new OfferPublication(
                offer, document, sequence, actorUserId, notificationEventId, now));
        OfferStatus previous = offer.getStatus();
        Application application = offer.getApplication();
        offer.publish(publication, actorUserId, now);
        synchronizePublishedWorkflowState(offer, actorUserId);
        OfferDispatch dispatch = dispatchRepository.saveAndFlush(new OfferDispatch(offer, publication, 1,
                notificationEventId, application.getApplicant().getPrimaryEmail(), now));
        offerRepository.saveAndFlush(offer);
        if (previous != offer.getStatus()) statusEventRepository.save(new OfferStatusEvent(offer, previous,
                offer.getStatus(), "Offer letter published to the applicant portal.", actorUserId, now));
        outboxService.enqueueOfferPublication(publication, dispatch);
        return result(publication, dispatch);
    }

    private void synchronizePublishedWorkflowState(AdmissionOffer offer, UUID actorUserId) {
        Application application = offer.getApplication();
        ApplicationStatus previousApplicationStatus = application.getStatus();
        ApplicationProgrammeChoice programmeChoice = offer.getProgrammeChoice();
        String publicationReason = "Published offer " + offer.getOfferNumber();
        if (application.getStatus() == ApplicationStatus.ADMITTED) {
            application.markOffered(publicationReason);
        } else if (application.getStatus() != ApplicationStatus.OFFERED) {
            throw new IllegalStateException("A published offer requires an admitted or offered application.");
        }
        if (programmeChoice.getChoiceStatus() == ProgrammeChoiceStatus.ADMITTED) {
            programmeChoice.markOffered(publicationReason);
        } else if (programmeChoice.getChoiceStatus() != ProgrammeChoiceStatus.OFFERED) {
            throw new IllegalStateException("A published offer requires an admitted or offered programme choice.");
        }
        if (previousApplicationStatus != application.getStatus()) {
            applicationStatusEventRepository.save(new ApplicationStatusEvent(
                    application, previousApplicationStatus, application.getStatus(), publicationReason, actorUserId));
        }
    }

    @Transactional
    public PublicationResult retryEmail(UUID offerId, String reason, UUID actorUserId) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("An email retry reason is required.");
        AdmissionOffer offer = offer(offerId);
        OfferPublication publication = publicationRepository
                .findByOfferIdAndCurrentPublicationTrueAndDeletedAtIsNull(offerId)
                .orElseThrow(() -> new IllegalStateException("A current portal publication is required."));
        if (publication.getEmailDeliveryStatus() != OfferEmailDeliveryStatus.FAILED
                && publication.getEmailDeliveryStatus() != OfferEmailDeliveryStatus.BOUNCED) {
            throw new IllegalStateException("Only a failed or bounced offer email can be retried.");
        }
        int attempt = dispatchRepository.findAllByOfferPublicationIdAndDeletedAtIsNullOrderByAttemptNumberDesc(publication.getId())
                .stream().findFirst().map(previous -> previous.getAttemptNumber() + 1).orElse(1);
        UUID notificationEventId = UUID.nameUUIDFromBytes(("offer-publication-email:" + publication.getId() + ":attempt:" + attempt)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        OfferDispatch dispatch = dispatchRepository.findByNotificationEventIdAndDeletedAtIsNull(notificationEventId)
                .orElseGet(() -> dispatchRepository.saveAndFlush(new OfferDispatch(offer, publication, attempt,
                        notificationEventId, offer.getApplication().getApplicant().getPrimaryEmail(), clock.instant())));
        publication.recordEmailStatus(OfferEmailDeliveryStatus.QUEUED, null, null, clock.instant());
        publicationRepository.save(publication);
        outboxService.enqueueOfferEmail(publication, dispatch);
        return result(publication, dispatch);
    }

    @Transactional
    public PublishedDocumentAccess applicantDocument(UUID offerId, UUID applicantUserId) {
        AdmissionOffer offer = offerRepository.findByIdAndApplicationApplicantUserIdAndDeletedAtIsNull(offerId, applicantUserId)
                .orElseThrow(() -> new IllegalArgumentException("Published offer was not found."));
        OfferPublication publication = publicationRepository.findByOfferIdAndCurrentPublicationTrueAndDeletedAtIsNull(offerId)
                .orElseThrow(() -> new IllegalStateException("The offer letter is not currently published."));
        return new PublishedDocumentAccess(offer.getId(), publication.getId(), publication.getDocumentVersion().getGeneratedDocumentId(),
                publication.getDocumentVersion().getDocumentNumber(), publication.getDocumentVersion().getChecksumSha256());
    }

    private AdmissionOffer offer(UUID offerId) {
        return offerRepository.findById(offerId).filter(value -> value.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("Offer not found."));
    }

    private void requireUnanswered(AdmissionOffer offer) {
        if (responseRepository.findByOfferId(offer.getId()).isPresent()
                || offer.getStatus() == OfferStatus.ACCEPTED || offer.getStatus() == OfferStatus.DECLINED) {
            throw new IllegalStateException("An accepted or declined offer and its document history are locked.");
        }
    }

    private AdmissionOfferSummary summary(AdmissionOffer offer) {
        return AdmissionOfferSummary.from(offer,
                conditionRepository.findAllByOfferIdAndDeletedAtIsNullOrderByConditionCodeAsc(offer.getId()),
                responseRepository.findByOfferId(offer.getId()).orElse(null));
    }

    private DocumentGenerationResult result(OfferDocumentVersion document) {
        return new DocumentGenerationResult(document.getId(), document.getDocumentVersion(), document.getStatus().name(),
                document.getGeneratedDocumentId(), document.getDocumentNumber(), document.getChecksumSha256(),
                document.getRequestedAt(), document.getStoredAt(), document.getFailureReason());
    }

    private PublicationResult result(OfferPublication publication, OfferDispatch dispatch) {
        return new PublicationResult(publication.getId(), publication.getDocumentVersion().getId(),
                publication.getPublicationSequence(), publication.getPortalPublishedAt(), publication.getEmailDeliveryStatus().name(),
                dispatch == null ? null : dispatch.getAttemptNumber(), dispatch == null ? null : dispatch.getStatus().name());
    }

    private <T extends Enum<T>> T parse(Class<T> type, String value, String label) {
        try { return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Unsupported " + label + ".", exception); }
    }

    public record DocumentGenerationResult(UUID id, int documentVersion, String status,
            UUID generatedDocumentId, String documentNumber, String checksumSha256,
            Instant requestedAt, Instant storedAt, String failureReason) { }
    public record PublicationResult(UUID publicationId, UUID documentVersionId, int sequence,
            Instant portalPublishedAt, String emailStatus, Integer emailAttempt, String emailAttemptStatus) { }
    public record PublishedDocumentAccess(UUID offerId, UUID publicationId, UUID generatedDocumentId,
            String documentNumber, String checksumSha256) { }
}
