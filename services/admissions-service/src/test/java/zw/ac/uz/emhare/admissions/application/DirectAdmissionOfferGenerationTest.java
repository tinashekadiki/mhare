package zw.ac.uz.emhare.admissions.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionOffer;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.Applicant;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeChoice;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationStatus;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationStatusEvent;
import zw.ac.uz.emhare.admissions.domain.model.OfferDocumentVersion;
import zw.ac.uz.emhare.admissions.domain.model.OfferDocumentVersionStatus;
import zw.ac.uz.emhare.admissions.domain.model.OfferEmailDeliveryStatus;
import zw.ac.uz.emhare.admissions.domain.model.OfferPublication;
import zw.ac.uz.emhare.admissions.domain.model.OfferStatus;
import zw.ac.uz.emhare.admissions.domain.model.OfferType;
import zw.ac.uz.emhare.admissions.domain.model.ProgrammeChoiceStatus;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionOfferRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationStatusEventRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferConditionRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferDispatchRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferDocumentVersionRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferPublicationRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferResponseRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferStatusEventRepository;
import zw.ac.uz.emhare.admissions.integration.AdmissionsIntegrationOutboxService;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.AcademicAdmissionsIntake;
import zw.ac.uz.emhare.common.messaging.OfferLetterContentSnapshot.FeeScheduleSnapshot;
import zw.ac.uz.emhare.admissions.application.OfferLetterFeeScheduleResolver.ResolvedOfferLetterCatalogue;

/** @author Tinashe K */
class DirectAdmissionOfferGenerationTest {
    @Test
    void updateUsesTheIntakeOwnedDates() {
        Fixture fixture = fixture();
        AcademicAdmissionsIntake intake = fixture.academicClient().getAdmissionsIntake(fixture.offer().getIntakeId());

        fixture.service().update(fixture.offerId(), "FIRM", null, "Bearer token");

        verify(fixture.offer()).updateTerms(OfferType.FIRM, null, intake.offerAcceptanceDeadline(),
                intake.registrationDate(), intake.orientationDate(), intake.commencementDate(), fixture.now());
    }

    @Test
    void updateExplainsWhereMissingOfferDatesMustBeConfigured() {
        Fixture fixture = fixture();
        UUID intakeId = fixture.offer().getIntakeId();
        when(fixture.academicClient().getAdmissionsIntake(intakeId)).thenReturn(new AcademicAdmissionsIntake(
                intakeId, UUID.randomUUID(), "2028", "MAR-2028", "March 2028", LocalDate.parse("2027-09-01"),
                LocalDate.parse("2028-01-31"), null, null, null, null, "OPEN", 3, java.util.List.of()));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> fixture.service().update(fixture.offerId(), "FIRM", null, "Bearer token"));

        assertEquals("Configure the offer acceptance deadline and commencement date on intake MAR-2028 before generating offers.",
                exception.getMessage());
    }

    @Test
    void reusesAnOutstandingVersionWithAuthoritativeFinanceAndCoreSnapshots() {
        Fixture fixture = fixture();
        OfferDocumentVersion existing = mock(OfferDocumentVersion.class);
        when(existing.getDocumentVersion()).thenReturn(3);
        when(existing.getStatus()).thenReturn(OfferDocumentVersionStatus.REQUESTED);
        when(existing.getRequestedAt()).thenReturn(fixture.now());
        when(fixture.documentRepository()
                .findFirstByOfferIdAndStatusAndDeletedAtIsNullOrderByDocumentVersionDesc(
                        fixture.offerId(), OfferDocumentVersionStatus.REQUESTED))
                .thenReturn(Optional.of(existing));
        FeeScheduleSnapshot schedule = mock(FeeScheduleSnapshot.class);
        CoreIdentityClient.CoreInstitutionProfile profile = mock(CoreIdentityClient.CoreInstitutionProfile.class);
        when(fixture.feeResolver().resolve(fixture.offer(), "Bearer token", fixture.now()))
                .thenReturn(new ResolvedOfferLetterCatalogue("Faculty of Science", schedule));
        when(fixture.coreClient().institutionProfile("Bearer token")).thenReturn(profile);

        var result = fixture.service().generate(fixture.offerId(), fixture.actorId(), "Bearer token");

        assertEquals(3, result.documentVersion());
        verify(fixture.outbox()).enqueueOfferLetterRequested(
                fixture.offer(), 3, fixture.actorId(), "Faculty of Science", schedule, profile);
    }

    @Test
    void compatibilityGenerationCreatesTheNextVersionWithoutInventingRemoteSnapshots() {
        Fixture fixture = fixture();
        when(fixture.documentRepository()
                .findFirstByOfferIdAndStatusAndDeletedAtIsNullOrderByDocumentVersionDesc(
                        fixture.offerId(), OfferDocumentVersionStatus.REQUESTED))
                .thenReturn(Optional.empty());
        when(fixture.documentRepository().countByOfferIdAndDeletedAtIsNull(fixture.offerId())).thenReturn(1);
        when(fixture.documentRepository().saveAndFlush(any(OfferDocumentVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = fixture.service().generate(fixture.offerId(), fixture.actorId());

        assertEquals(2, result.documentVersion());
        verify(fixture.feeResolver()).resolve(fixture.offer(), null, fixture.now());
        verify(fixture.coreClient(), never()).institutionProfile(any());
        verify(fixture.outbox()).enqueueOfferLetterRequested(
                eq(fixture.offer()), eq(2), eq(fixture.actorId()), eq(null), eq(null), eq(null));
    }

    @Test
    void blankAuthorizationAlsoUsesTheCompatibilitySnapshotPath() {
        Fixture fixture = fixture();
        OfferDocumentVersion existing = mock(OfferDocumentVersion.class);
        when(existing.getDocumentVersion()).thenReturn(1);
        when(existing.getStatus()).thenReturn(OfferDocumentVersionStatus.REQUESTED);
        when(existing.getRequestedAt()).thenReturn(fixture.now());
        when(fixture.documentRepository()
                .findFirstByOfferIdAndStatusAndDeletedAtIsNullOrderByDocumentVersionDesc(
                        fixture.offerId(), OfferDocumentVersionStatus.REQUESTED))
                .thenReturn(Optional.of(existing));

        fixture.service().generate(fixture.offerId(), fixture.actorId(), " ");

        verify(fixture.coreClient(), never()).institutionProfile(any());
    }

    @Test
    void firstPublicationMovesTheApplicationAndProgrammeChoiceToOffered() {
        Fixture fixture = fixture();
        OfferDocumentVersion storedDocument = mock(OfferDocumentVersion.class);
        when(storedDocument.getStatus()).thenReturn(OfferDocumentVersionStatus.STORED);
        when(storedDocument.getDocumentVersion()).thenReturn(1);
        when(fixture.offer().getCurrentDocumentVersion()).thenReturn(storedDocument);
        when(fixture.offer().getOfferNumber()).thenReturn("OFR-MAR-2028-00000001");
        when(fixture.offer().getStatus()).thenReturn(
                OfferStatus.DRAFT, OfferStatus.DRAFT, OfferStatus.DRAFT, OfferStatus.SENT);
        stubAdmittedWorkflowState(fixture);
        when(fixture.applicant().getPrimaryEmail()).thenReturn("applicant@example.test");
        when(fixture.publicationRepository().countByOfferIdAndDeletedAtIsNull(fixture.offerId())).thenReturn(0);
        when(fixture.publicationRepository().saveAndFlush(any(OfferPublication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(fixture.dispatchRepository().saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service().publishAndSend(fixture.offerId(), fixture.actorId());

        verify(fixture.application()).markOffered("Published offer OFR-MAR-2028-00000001");
        verify(fixture.programmeChoice()).markOffered("Published offer OFR-MAR-2028-00000001");
        verify(fixture.applicationStatusEventRepository()).save(any(ApplicationStatusEvent.class));
    }

    @Test
    void repeatedPublicationRepairsAPreviouslyPublishedOfferWithAdmittedLinkedState() {
        Fixture fixture = fixture();
        stubCurrentPublication(fixture);
        stubAdmittedWorkflowState(fixture);

        fixture.service().publishAndSend(fixture.offerId(), fixture.actorId());

        verify(fixture.application()).markOffered("Published offer OFR-MAR-2028-00000001");
        verify(fixture.programmeChoice()).markOffered("Published offer OFR-MAR-2028-00000001");
        verify(fixture.applicationStatusEventRepository()).save(any(ApplicationStatusEvent.class));
    }

    @Test
    void repeatedPublicationLeavesAlreadyOfferedLinkedStateUnchanged() {
        Fixture fixture = fixture();
        stubCurrentPublication(fixture);
        when(fixture.application().getStatus()).thenReturn(ApplicationStatus.OFFERED);
        when(fixture.programmeChoice().getChoiceStatus()).thenReturn(ProgrammeChoiceStatus.OFFERED);

        fixture.service().publishAndSend(fixture.offerId(), fixture.actorId());

        verify(fixture.application(), never()).markOffered(any());
        verify(fixture.programmeChoice(), never()).markOffered(any());
        verify(fixture.applicationStatusEventRepository(), never()).save(any());
    }

    @Test
    void repeatedPublicationRejectsAnApplicationOutsideAdmittedOrOfferedState() {
        Fixture fixture = fixture();
        stubCurrentPublication(fixture);
        when(fixture.application().getStatus()).thenReturn(ApplicationStatus.ACCEPTED);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> fixture.service().publishAndSend(fixture.offerId(), fixture.actorId()));

        assertEquals("A published offer requires an admitted or offered application.", exception.getMessage());
    }

    @Test
    void repeatedPublicationRejectsAProgrammeChoiceOutsideAdmittedOrOfferedState() {
        Fixture fixture = fixture();
        stubCurrentPublication(fixture);
        when(fixture.application().getStatus()).thenReturn(ApplicationStatus.OFFERED);
        when(fixture.programmeChoice().getChoiceStatus()).thenReturn(ProgrammeChoiceStatus.REJECTED);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> fixture.service().publishAndSend(fixture.offerId(), fixture.actorId()));

        assertEquals("A published offer requires an admitted or offered programme choice.", exception.getMessage());
    }

    private void stubCurrentPublication(Fixture fixture) {
        UUID documentId = UUID.randomUUID();
        OfferDocumentVersion storedDocument = mock(OfferDocumentVersion.class);
        OfferPublication currentPublication = mock(OfferPublication.class);
        when(storedDocument.getId()).thenReturn(documentId);
        when(storedDocument.getStatus()).thenReturn(OfferDocumentVersionStatus.STORED);
        when(currentPublication.getDocumentVersion()).thenReturn(storedDocument);
        when(currentPublication.getEmailDeliveryStatus()).thenReturn(OfferEmailDeliveryStatus.QUEUED);
        when(fixture.offer().getCurrentDocumentVersion()).thenReturn(storedDocument);
        when(fixture.offer().getOfferNumber()).thenReturn("OFR-MAR-2028-00000001");
        when(fixture.offer().getStatus()).thenReturn(OfferStatus.SENT);
        when(fixture.publicationRepository()
                .findByOfferIdAndCurrentPublicationTrueAndDeletedAtIsNull(fixture.offerId()))
                .thenReturn(Optional.of(currentPublication));
    }

    private void stubAdmittedWorkflowState(Fixture fixture) {
        AtomicReference<ApplicationStatus> applicationStatus = new AtomicReference<>(ApplicationStatus.ADMITTED);
        AtomicReference<ProgrammeChoiceStatus> choiceStatus = new AtomicReference<>(ProgrammeChoiceStatus.ADMITTED);
        when(fixture.application().getStatus()).thenAnswer(invocation -> applicationStatus.get());
        doAnswer(invocation -> {
            applicationStatus.set(ApplicationStatus.OFFERED);
            return null;
        }).when(fixture.application()).markOffered(any());
        when(fixture.programmeChoice().getChoiceStatus()).thenAnswer(invocation -> choiceStatus.get());
        doAnswer(invocation -> {
            choiceStatus.set(ProgrammeChoiceStatus.OFFERED);
            return null;
        }).when(fixture.programmeChoice()).markOffered(any());
    }

    private Fixture fixture() {
        AdmissionOfferRepository offerRepository = mock(AdmissionOfferRepository.class);
        OfferResponseRepository responseRepository = mock(OfferResponseRepository.class);
        OfferDocumentVersionRepository documentRepository = mock(OfferDocumentVersionRepository.class);
        AdmissionsIntegrationOutboxService outbox = mock(AdmissionsIntegrationOutboxService.class);
        OfferLetterFeeScheduleResolver feeResolver = mock(OfferLetterFeeScheduleResolver.class);
        CoreIdentityClient coreClient = mock(CoreIdentityClient.class);
        AcademicSetupCatalogueClient academicClient = mock(AcademicSetupCatalogueClient.class);
        AdmissionOffer offer = mock(AdmissionOffer.class);
        Application application = mock(Application.class);
        Applicant applicant = mock(Applicant.class);
        ApplicationProgrammeChoice programmeChoice = mock(ApplicationProgrammeChoice.class);
        OfferConditionRepository conditionRepository = mock(OfferConditionRepository.class);
        OfferPublicationRepository publicationRepository = mock(OfferPublicationRepository.class);
        OfferDispatchRepository dispatchRepository = mock(OfferDispatchRepository.class);
        ApplicationStatusEventRepository applicationStatusEventRepository = mock(ApplicationStatusEventRepository.class);
        UUID offerId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.parse("2028-01-10T08:00:00Z");
        when(offerRepository.findById(offerId)).thenReturn(Optional.of(offer));
        when(offerRepository.saveAndFlush(offer)).thenReturn(offer);
        when(offer.getId()).thenReturn(offerId);
        when(offer.getApplication()).thenReturn(application);
        when(application.getId()).thenReturn(UUID.randomUUID());
        when(application.getApplicationNumber()).thenReturn("EMH-2028-000001");
        when(application.getApplicant()).thenReturn(applicant);
        when(applicant.getApplicantNumber()).thenReturn("A000001");
        when(applicant.getDisplayName()).thenReturn("Test Applicant");
        when(offer.getProgrammeChoice()).thenReturn(programmeChoice);
        when(programmeChoice.getId()).thenReturn(UUID.randomUUID());
        UUID intakeId = UUID.randomUUID();
        when(offer.getIntakeId()).thenReturn(intakeId);
        when(offer.getStatus()).thenReturn(OfferStatus.APPROVED);
        when(offer.getOfferType()).thenReturn(OfferType.FIRM);
        when(offer.getAcceptanceDeadline()).thenReturn(now.plusSeconds(86_400));
        when(offer.getCommencementDate()).thenReturn(LocalDate.parse("2028-03-04"));
        when(academicClient.getAdmissionsIntake(intakeId)).thenReturn(new AcademicAdmissionsIntake(
                intakeId, UUID.randomUUID(), "2028", "MAR-2028", "March 2028", LocalDate.parse("2027-09-01"),
                LocalDate.parse("2028-01-31"), now.plusSeconds(86_400), LocalDate.parse("2028-02-26"),
                LocalDate.parse("2028-02-29"), LocalDate.parse("2028-03-04"), "OPEN", 3, java.util.List.of()));
        when(responseRepository.findByOfferId(offerId)).thenReturn(Optional.empty());
        when(conditionRepository.findAllByOfferIdAndDeletedAtIsNullOrderByConditionCodeAsc(offerId))
                .thenReturn(java.util.List.of());
        when(feeResolver.resolve(any(AdmissionOffer.class), nullable(String.class), any(Instant.class)))
                .thenReturn(new ResolvedOfferLetterCatalogue(null, null));
        DirectAdmissionOfferService service = new DirectAdmissionOfferService(offerRepository, responseRepository,
                conditionRepository, documentRepository, publicationRepository,
                dispatchRepository, mock(OfferStatusEventRepository.class), applicationStatusEventRepository,
                outbox, feeResolver,
                coreClient, academicClient, Clock.fixed(now, ZoneOffset.UTC));
        return new Fixture(service, offerId, actorId, now, offer, documentRepository, outbox, feeResolver,
                coreClient, academicClient, application, applicant, programmeChoice, publicationRepository,
                dispatchRepository, applicationStatusEventRepository);
    }

    private record Fixture(DirectAdmissionOfferService service, UUID offerId, UUID actorId, Instant now,
            AdmissionOffer offer, OfferDocumentVersionRepository documentRepository,
            AdmissionsIntegrationOutboxService outbox, OfferLetterFeeScheduleResolver feeResolver,
            CoreIdentityClient coreClient, AcademicSetupCatalogueClient academicClient,
            Application application, Applicant applicant, ApplicationProgrammeChoice programmeChoice,
            OfferPublicationRepository publicationRepository, OfferDispatchRepository dispatchRepository,
            ApplicationStatusEventRepository applicationStatusEventRepository) { }
}
