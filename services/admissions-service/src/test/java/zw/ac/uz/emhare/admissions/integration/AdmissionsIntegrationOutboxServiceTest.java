package zw.ac.uz.emhare.admissions.integration;

import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.AdmissionsOutboxEvent;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.messaging.AdmissionsOutboxEventRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProgrammeEntryOptionSelectionRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationDocumentRequirementSnapshotRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProgrammeOptionSnapshotRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionCycle;
import zw.ac.uz.emhare.admissions.domain.model.Applicant;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationType;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantReferee;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionOffer;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeChoice;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeEntryOptionSelection;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationDocumentRequirementSnapshot;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeOptionSnapshot;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.NotificationRequestedEvent;
import zw.ac.uz.emhare.common.messaging.MissingApplicationDocumentWorkflowRequestedEvent;
import zw.ac.uz.emhare.common.messaging.OfferLetterRequestedEvent;
import zw.ac.uz.emhare.common.messaging.OfferLetterContentSnapshot.BankAccountSnapshot;

/** @author Tinashe K */
class AdmissionsIntegrationOutboxServiceTest {

    @Test
    void offerLetterRequestCarriesImmutableApplicantProgrammeAndEvidenceContent() throws Exception {
        AdmissionsOutboxEventRepository repository = mock(AdmissionsOutboxEventRepository.class);
        ApplicationProgrammeEntryOptionSelectionRepository entryOptions =
                mock(ApplicationProgrammeEntryOptionSelectionRepository.class);
        ApplicationDocumentRequirementSnapshotRepository documentRequirements =
                mock(ApplicationDocumentRequirementSnapshotRepository.class);
        ApplicationProgrammeOptionSnapshotRepository programmeSnapshots =
                mock(ApplicationProgrammeOptionSnapshotRepository.class);
        when(repository.existsById(any(UUID.class))).thenReturn(false);
        when(repository.save(any(AdmissionsOutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ObjectMapper objectMapper = new ObjectMapper();
        AdmissionsIntegrationOutboxService service = new AdmissionsIntegrationOutboxService(repository, objectMapper,
                Clock.fixed(Instant.parse("2028-01-08T10:00:00Z"), ZoneOffset.UTC), entryOptions,
                documentRequirements, programmeSnapshots);
        AdmissionOffer offer = mock(AdmissionOffer.class);
        Application application = mock(Application.class);
        Applicant applicant = mock(Applicant.class);
        ApplicationType applicationType = mock(ApplicationType.class);
        ApplicationProgrammeChoice programmeChoice = mock(ApplicationProgrammeChoice.class);
        ApplicationProgrammeEntryOptionSelection studyOption = mock(ApplicationProgrammeEntryOptionSelection.class);
        ApplicationDocumentRequirementSnapshot requiredDocument = mock(ApplicationDocumentRequirementSnapshot.class);
        ApplicationDocumentRequirementSnapshot optionalDocument = mock(ApplicationDocumentRequirementSnapshot.class);
        ApplicationProgrammeOptionSnapshot programmeSnapshot = mock(ApplicationProgrammeOptionSnapshot.class);
        UUID offerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID choiceId = UUID.randomUUID();
        UUID programmeId = UUID.randomUUID();
        when(offer.getId()).thenReturn(offerId); when(offer.getVersion()).thenReturn(6L);
        when(offer.getOfferNumber()).thenReturn("OF-2028-000001"); when(offer.getApplication()).thenReturn(application);
        when(offer.getProgrammeChoice()).thenReturn(programmeChoice); when(offer.getProgrammeId()).thenReturn(programmeId);
        when(offer.getProgrammeCode()).thenReturn("BSC-CS"); when(offer.getProgrammeName()).thenReturn("Computer Science");
        when(offer.getIntakeId()).thenReturn(UUID.randomUUID()); when(offer.getOfferTypeCode()).thenReturn("FIRM");
        when(offer.getAcceptanceDeadline()).thenReturn(Instant.parse("2028-02-28T21:59:59Z"));
        when(offer.getCommencementDate()).thenReturn(java.time.LocalDate.parse("2028-03-04"));
        when(application.getId()).thenReturn(applicationId); when(application.getApplicationNumber()).thenReturn("EMH-2028-000001");
        when(application.getApplicant()).thenReturn(applicant); when(application.getApplicationType()).thenReturn(applicationType);
        when(application.getIntakeName()).thenReturn("March 2028 Intake");
        when(applicant.getApplicantNumber()).thenReturn("A000001"); when(applicant.getDisplayName()).thenReturn("Nyasha Moyo");
        when(applicant.getPrimaryEmail()).thenReturn("nyasha@example.test"); when(applicant.getUserId()).thenReturn(UUID.randomUUID());
        when(applicant.getPostalAddress()).thenReturn("14 Samora Machel Avenue, Harare");
        when(applicant.getApplicantCategoryCode()).thenReturn("LOCAL");
        when(applicationType.getCode()).thenReturn("UNDERGRAD"); when(applicationType.getName()).thenReturn("Undergraduate");
        when(programmeChoice.getId()).thenReturn(choiceId); when(programmeChoice.getOwningAcademicUnitName()).thenReturn("Faculty of Science");
        when(programmeChoice.getAwardName()).thenReturn("Bachelor of Science Honours");
        when(programmeChoice.getProgrammeVersionCode()).thenReturn("2028.1");
        when(studyOption.getEntryOptionName()).thenReturn("Computer Science");
        when(requiredDocument.isRequired()).thenReturn(true); when(requiredDocument.getRequirementName()).thenReturn("Identity document");
        when(optionalDocument.isRequired()).thenReturn(false);
        when(programmeSnapshot.getProgrammeLevelName()).thenReturn("Undergraduate");
        when(entryOptions.findAllByProgrammeChoice_IdAndDeletedAtIsNullOrderByPreferenceRankAsc(choiceId))
                .thenReturn(List.of(studyOption));
        when(documentRequirements.findAllByApplicationIdAndDeletedAtIsNullOrderBySortOrderAscRequirementCodeAsc(applicationId))
                .thenReturn(List.of(requiredDocument, optionalDocument));
        when(programmeSnapshots.findByApplicationIdAndProgrammeIdAndDeletedAtIsNull(applicationId, programmeId))
                .thenReturn(java.util.Optional.of(programmeSnapshot));

        CoreIdentityClient.CoreInstitutionProfile institutionProfile = new CoreIdentityClient.CoreInstitutionProfile(
                UUID.randomUUID(), "UZ", "University of Zimbabwe", "University of Zimbabwe", "Dr Jane Dube", "USD", "ZW",
                "Africa/Harare", "{\"email\":\"admissions@uz.ac.zw\",\"phone\":\"+263 24 2303211\"}",
                "{\"offerLetterSignatoryName\":\"Legacy Signatory\",\"offerLetterSignatoryTitle\":\"Registrar\","
                        + "\"registrarSignatureDocumentId\":\"42b272b2-6f22-4fad-baf2-cfc3d6438e76\"}",
                "{\"accounts\":["
                        + "{\"currencyCode\":\"USD\",\"bankName\":\"CBZ BANK\",\"accountNumber\":\"01120770100249\",\"swiftCode\":\"COBZZWHAXXX\"},"
                        + "{\"currencyCode\":\"ZWG\",\"bankName\":\"CBZ BANK\",\"accountNumber\":\"01120770100052\",\"swiftCode\":\"COBZZWHAXXX\"}]}", "UZ");
        service.enqueueOfferLetterRequested(offer, 2, UUID.randomUUID(), "University of Zimbabwe", null,
                institutionProfile);

        ArgumentCaptor<AdmissionsOutboxEvent> eventCaptor = ArgumentCaptor.forClass(AdmissionsOutboxEvent.class);
        verify(repository).save(eventCaptor.capture());
        OfferLetterRequestedEvent event = objectMapper.readValue(eventCaptor.getValue().getPayload(), OfferLetterRequestedEvent.class);
        assertEquals(OfferLetterRequestedEvent.CURRENT_SCHEMA_VERSION, event.schemaVersion());
        assertNotNull(event.contentSnapshot());
        assertEquals("14 Samora Machel Avenue, Harare", event.contentSnapshot().applicantPostalAddress());
        assertEquals("University of Zimbabwe", event.contentSnapshot().academicUnitName());
        assertEquals("Undergraduate", event.contentSnapshot().programmeLevelName());
        assertEquals(List.of("Computer Science"), event.contentSnapshot().studyOptions());
        assertEquals(List.of("Identity document"), event.contentSnapshot().requiredVerificationDocuments());
        assertEquals("admissions@uz.ac.zw", event.contentSnapshot().institutionEmail());
        assertEquals("Dr Jane Dube", event.contentSnapshot().signatoryName());
        assertEquals("42b272b2-6f22-4fad-baf2-cfc3d6438e76",
                event.contentSnapshot().signatorySignatureDocumentId());
        assertEquals(List.of("USD", "ZWG"), event.contentSnapshot().bankAccounts().stream()
                .map(BankAccountSnapshot::currencyCode).toList());
        assertEquals(List.of("01120770100249", "01120770100052"), event.contentSnapshot().bankAccounts().stream()
                .map(BankAccountSnapshot::accountNumber).toList());
        assertNull(event.contentSnapshot().feeSchedule());
    }

    @Test
    void offerLetterCompatibilityOverloadsUseSafeDefaultsAndRejectInvalidCoreJson() {
        AdmissionsOutboxEventRepository repository = mock(AdmissionsOutboxEventRepository.class);
        when(repository.existsById(any(UUID.class))).thenReturn(false);
        when(repository.save(any(AdmissionsOutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ObjectMapper objectMapper = new ObjectMapper();
        AdmissionsIntegrationOutboxService service = new AdmissionsIntegrationOutboxService(repository, objectMapper,
                Clock.fixed(Instant.parse("2028-01-08T10:00:00Z"), ZoneOffset.UTC));
        AdmissionOffer firstOffer = minimalOffer(UUID.randomUUID());
        AdmissionOffer secondOffer = minimalOffer(UUID.randomUUID());
        AdmissionOffer thirdOffer = minimalOffer(UUID.randomUUID());

        service.enqueueOfferLetterRequested(firstOffer, UUID.randomUUID());
        service.enqueueOfferLetterRequested(secondOffer, 2, UUID.randomUUID());
        service.enqueueOfferLetterRequested(thirdOffer, 3, UUID.randomUUID(), null);

        verify(repository, times(3)).save(any(AdmissionsOutboxEvent.class));
        CoreIdentityClient.CoreInstitutionProfile invalid = new CoreIdentityClient.CoreInstitutionProfile(
                UUID.randomUUID(), "UZ", "University of Zimbabwe", "University of Zimbabwe", "USD", "ZW",
                "Africa/Harare", "{invalid", "{}", "UZ");
        assertThrows(IllegalStateException.class,
                () -> service.enqueueOfferLetterRequested(minimalOffer(UUID.randomUUID()), 4,
                        UUID.randomUUID(), null, invalid));
    }

    @Test
    void applicationSubmissionCreatesIdempotentEmailAndInAppOutboxEvents() throws Exception {
        AdmissionsOutboxEventRepository repository = org.mockito.Mockito.mock(AdmissionsOutboxEventRepository.class);
        when(repository.existsById(any(UUID.class))).thenReturn(false);
        when(repository.save(any(AdmissionsOutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ObjectMapper objectMapper = new ObjectMapper();
        Instant occurredAt = Instant.parse("2027-01-15T10:00:00Z");
        AdmissionsIntegrationOutboxService service = new AdmissionsIntegrationOutboxService(
                repository,
                objectMapper,
                Clock.fixed(occurredAt, ZoneOffset.UTC));
        UUID applicationId = UUID.randomUUID();
        UUID applicantUserId = UUID.randomUUID();
        Application application = application(applicationId, applicantUserId);

        service.enqueueApplicationSubmittedNotification(application);

        ArgumentCaptor<AdmissionsOutboxEvent> eventCaptor = ArgumentCaptor.forClass(AdmissionsOutboxEvent.class);
        verify(repository, times(2)).save(eventCaptor.capture());
        List<AdmissionsOutboxEvent> outboxEvents = eventCaptor.getAllValues();
        assertEquals(
                List.of(
                        EmhareMessagingTopology.NOTIFICATION_REQUESTED_EVENT,
                        EmhareMessagingTopology.NOTIFICATION_REQUESTED_EVENT),
                outboxEvents.stream().map(AdmissionsOutboxEvent::getRoutingKey).toList());
        List<NotificationRequestedEvent> notifications = outboxEvents.stream()
                .map(event -> deserialize(objectMapper, event.getPayload()))
                .toList();
        assertEquals(List.of("EMAIL", "IN_APP"), notifications.stream().map(NotificationRequestedEvent::channel).toList());
        assertEquals(
                List.of("APPLICATION_SUBMITTED_EMAIL", "APPLICATION_SUBMITTED_IN_APP"),
                notifications.stream().map(NotificationRequestedEvent::templateCode).toList());
        assertEquals(List.of(applicantUserId, applicantUserId), notifications.stream().map(NotificationRequestedEvent::recipientUserId).toList());
        assertEquals(List.of(occurredAt, occurredAt), notifications.stream().map(NotificationRequestedEvent::occurredAt).toList());
        assertNotEquals(notifications.get(0).eventId(), notifications.get(1).eventId());
        assertEquals("EMH-2027-0001", notifications.get(0).variables().get("applicationNumber"));
    }

    @Test
    void rejectedDocumentCreatesHighPriorityEmailAndInAppCorrectionNotifications() throws Exception {
        AdmissionsOutboxEventRepository repository = org.mockito.Mockito.mock(AdmissionsOutboxEventRepository.class);
        when(repository.existsById(any(UUID.class))).thenReturn(false);
        when(repository.save(any(AdmissionsOutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ObjectMapper objectMapper = new ObjectMapper();
        Instant occurredAt = Instant.parse("2027-01-15T10:00:00Z");
        AdmissionsIntegrationOutboxService service = new AdmissionsIntegrationOutboxService(
                repository,
                objectMapper,
                Clock.fixed(occurredAt, ZoneOffset.UTC));
        UUID documentId = UUID.randomUUID();
        UUID verifierUserId = UUID.randomUUID();
        Application application = application(UUID.randomUUID(), UUID.randomUUID());

        service.enqueueMissingDocumentsNotification(
                application,
                "NATIONAL_ID",
                "The identity number is unreadable.",
                documentId,
                4,
                verifierUserId);

        ArgumentCaptor<AdmissionsOutboxEvent> eventCaptor = ArgumentCaptor.forClass(AdmissionsOutboxEvent.class);
        verify(repository, times(3)).save(eventCaptor.capture());
        List<AdmissionsOutboxEvent> outboxEvents = eventCaptor.getAllValues();
        List<NotificationRequestedEvent> notifications = outboxEvents.stream()
                .filter(event -> event.getEventType().equals(EmhareMessagingTopology.NOTIFICATION_REQUESTED_EVENT))
                .map(event -> deserialize(objectMapper, event.getPayload()))
                .toList();
        assertEquals(List.of("EMAIL", "IN_APP"), notifications.stream().map(NotificationRequestedEvent::channel).toList());
        assertEquals(
                List.of("MISSING_DOCUMENTS_EMAIL", "MISSING_DOCUMENTS_IN_APP"),
                notifications.stream().map(NotificationRequestedEvent::templateCode).toList());
        assertEquals(List.of("HIGH", "HIGH"), notifications.stream().map(NotificationRequestedEvent::priority).toList());
        assertEquals(
                "NATIONAL_ID — The identity number is unreadable.",
                notifications.get(0).variables().get("documentList"));
        assertEquals(
                "admissions:missing-document:" + documentId + ":4:email",
                notifications.get(0).idempotencyKey());
        AdmissionsOutboxEvent workflowOutboxEvent = outboxEvents.stream()
                .filter(event -> event.getEventType().equals(
                        EmhareMessagingTopology.MISSING_APPLICATION_DOCUMENT_WORKFLOW_REQUESTED_EVENT))
                .findFirst()
                .orElseThrow();
        MissingApplicationDocumentWorkflowRequestedEvent workflowEvent = objectMapper.readValue(
                workflowOutboxEvent.getPayload(),
                MissingApplicationDocumentWorkflowRequestedEvent.class);
        assertEquals(application.getId(), workflowEvent.applicationId());
        assertEquals(application.getApplicant().getUserId(), workflowEvent.applicantUserId());
        assertEquals(documentId, workflowEvent.documentId());
        assertEquals(4, workflowEvent.documentVersion());
        assertEquals(verifierUserId, workflowEvent.initiatedByUserId());
        assertEquals(
                application.getIntakeEndsOn().plusDays(1).atStartOfDay(ZoneOffset.UTC).minusNanos(1).toInstant(),
                workflowEvent.dueAt());
    }

    @Test
    void refereeRequestTargetsExternalEmailWithoutRequiringAnEmhareUser() {
        AdmissionsOutboxEventRepository repository = org.mockito.Mockito.mock(AdmissionsOutboxEventRepository.class);
        when(repository.existsById(any(UUID.class))).thenReturn(false);
        when(repository.save(any(AdmissionsOutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ObjectMapper objectMapper = new ObjectMapper();
        Instant occurredAt = Instant.parse("2027-01-15T10:00:00Z");
        AdmissionsIntegrationOutboxService service = new AdmissionsIntegrationOutboxService(
                repository, objectMapper, Clock.fixed(occurredAt, ZoneOffset.UTC));
        Application application = application(UUID.randomUUID(), UUID.randomUUID());
        ApplicantReferee referee = new ApplicantReferee(
                application.getApplicant(), "Dr Tariro Dube", "Dr", "UZ", "Dean",
                "referee@example.test", null);

        service.enqueueRefereeReferenceRequest(
                application, referee, UUID.randomUUID(), "http://localhost:3001/references/token", occurredAt.plusSeconds(86400));

        ArgumentCaptor<AdmissionsOutboxEvent> eventCaptor = ArgumentCaptor.forClass(AdmissionsOutboxEvent.class);
        verify(repository).save(eventCaptor.capture());
        NotificationRequestedEvent notification = deserialize(objectMapper, eventCaptor.getValue().getPayload());
        assertEquals("REFEREE_REFERENCE_REQUEST_EMAIL", notification.templateCode());
        assertEquals("referee@example.test", notification.recipientKey());
        assertEquals("referee@example.test", notification.recipientAddress());
        assertNull(notification.recipientUserId());
        assertEquals("http://localhost:3001/references/token", notification.variables().get("responseUrl"));
    }

    private Application application(UUID applicationId, UUID applicantUserId) {
        AdmissionCycle admissionCycle = new AdmissionCycle(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "2027-AUG",
                "2027 August Intake",
                Instant.parse("2027-01-01T00:00:00Z"),
                Instant.parse("2027-02-01T00:00:00Z"));
        ApplicationType applicationType = new ApplicationType("UNDERGRAD", "Undergraduate", false, false);
        Applicant applicant = new Applicant(
                applicantUserId,
                "APP-0001",
                "LOCAL",
                "Nyasha",
                "Moyo",
                "nyasha@example.test");
        Application application = new Application(
                admissionCycle,
                applicant,
                applicationType,
                "EMH-2027-0001",
                false);
        ReflectionTestUtils.setField(application, "id", applicationId);
        return application;
    }

    private AdmissionOffer minimalOffer(UUID offerId) {
        AdmissionOffer offer = mock(AdmissionOffer.class);
        Application application = mock(Application.class);
        Applicant applicant = mock(Applicant.class);
        ApplicationType applicationType = mock(ApplicationType.class);
        ApplicationProgrammeChoice choice = mock(ApplicationProgrammeChoice.class);
        when(offer.getId()).thenReturn(offerId);
        when(offer.getVersion()).thenReturn(1L);
        when(offer.getOfferNumber()).thenReturn("OF-" + offerId);
        when(offer.getApplication()).thenReturn(application);
        when(offer.getProgrammeChoice()).thenReturn(choice);
        when(offer.getProgrammeId()).thenReturn(UUID.randomUUID());
        when(offer.getOfferTypeCode()).thenReturn("FIRM");
        when(application.getId()).thenReturn(UUID.randomUUID());
        when(application.getApplicationNumber()).thenReturn("APP-1");
        when(application.getApplicant()).thenReturn(applicant);
        when(application.getApplicationType()).thenReturn(applicationType);
        when(applicationType.getCode()).thenReturn("UG");
        when(applicationType.getName()).thenReturn("Undergraduate");
        when(applicant.getApplicantNumber()).thenReturn("A000001");
        when(applicant.getDisplayName()).thenReturn("Applicant");
        when(applicant.getPrimaryEmail()).thenReturn("applicant@example.test");
        when(applicant.getUserId()).thenReturn(UUID.randomUUID());
        return offer;
    }

    private NotificationRequestedEvent deserialize(ObjectMapper objectMapper, String payload) {
        try {
            return objectMapper.readValue(payload, NotificationRequestedEvent.class);
        } catch (Exception exception) {
            throw new AssertionError("Notification outbox payload could not be read.", exception);
        }
    }
}
