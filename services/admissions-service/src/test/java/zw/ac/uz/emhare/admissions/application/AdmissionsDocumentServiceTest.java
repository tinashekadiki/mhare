package zw.ac.uz.emhare.admissions.application;

import zw.ac.uz.emhare.admissions.domain.model.AdmissionCycle;
import zw.ac.uz.emhare.admissions.domain.model.Applicant;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationDocument;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeChoice;
import zw.ac.uz.emhare.admissions.domain.model.ProgrammeSelectionSnapshot;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationType;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationTypeDocumentRequirement;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationDocumentRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProgrammeChoiceRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeDocumentRequirementRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationDocumentRequirementSnapshotRepository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.admissions.integration.AdmissionsIntegrationOutboxService;
import zw.ac.uz.emhare.admissions.integration.DocumentsReportingClient;
import zw.ac.uz.emhare.common.messaging.DocumentVerificationChangedEvent;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationRepository;

/** @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class AdmissionsDocumentServiceTest {
    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApplicationTypeRepository applicationTypeRepository;
    @Mock private ApplicationTypeDocumentRequirementRepository requirementRepository;
    @Mock private ApplicationDocumentRequirementSnapshotRepository requirementSnapshotRepository;
    @Mock private ApplicationDocumentRepository documentRepository;
    @Mock private ApplicationProgrammeChoiceRepository programmeChoiceRepository;
    @Mock private DocumentsReportingClient documentsReportingClient;
    @Mock private AdmissionsIntegrationOutboxService integrationOutboxService;

    private AdmissionsDocumentService service;
    private Application application;
    private ApplicationType applicationType;
    private ApplicationTypeDocumentRequirement requirement;
    private final Instant now = Instant.parse("2027-01-15T10:00:00Z");

    @BeforeEach
    void setUp() {
        service = new AdmissionsDocumentService(
                applicationRepository, applicationTypeRepository, requirementRepository, requirementSnapshotRepository, documentRepository,
                programmeChoiceRepository, documentsReportingClient, integrationOutboxService,
                Clock.fixed(now, ZoneOffset.UTC));
        applicationType = new ApplicationType("UNDERGRAD", "Undergraduate", false, false);
        ReflectionTestUtils.setField(applicationType, "id", UUID.randomUUID());
        Applicant applicant = new Applicant(
                UUID.randomUUID(), "APP-0001", "LOCAL", "Nyasha", "Moyo", "nyasha@example.test");
        AdmissionCycle cycle = new AdmissionCycle(
                UUID.randomUUID(), UUID.randomUUID(), "2027-AUG", "August 2027",
                now.minusSeconds(3600), now.plusSeconds(86400));
        application = new Application(cycle, applicant, applicationType, "EMH-2027-0001", false);
        ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
        requirement = new ApplicationTypeDocumentRequirement(
                applicationType, "NATIONAL_ID", "National identity document", true, 1);
        ReflectionTestUtils.setField(requirement, "id", UUID.randomUUID());
    }

    @Test
    void academicUnitRegisterAggregatesDocumentStateAcrossApplicationsOwnedByTheUnit() {
        UUID owningAcademicUnitId = UUID.randomUUID();
        ProgrammeSelectionSnapshot snapshot = new ProgrammeSelectionSnapshot(
                UUID.randomUUID(), UUID.randomUUID(), "BSCIT", "Bachelor of Science in IT",
                "Bachelor of Science Honours Degree", owningAcademicUnitId, "Department of Computing", "2027.1");
        ApplicationProgrammeChoice choice = new ApplicationProgrammeChoice(application, snapshot, 1);
        when(programmeChoiceRepository.findAllByOwningAcademicUnitId(owningAcademicUnitId))
                .thenReturn(List.of(choice));
        when(applicationRepository.findAllById(List.of(application.getId()))).thenReturn(List.of(application));
        when(requirementRepository
                .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscRequirementCodeAsc(
                        applicationType.getId()))
                .thenReturn(List.of(requirement));
        when(documentRepository.findAllByApplicationIdAndCurrentTrueAndDeletedAtIsNullOrderByRequirementCodeAsc(
                application.getId())).thenReturn(List.of());

        List<AdmissionsDocumentViews.AcademicUnitApplicationDocumentEntry> register =
                service.academicUnitRegister(owningAcademicUnitId);

        org.junit.jupiter.api.Assertions.assertEquals(1, register.size());
        AdmissionsDocumentViews.AcademicUnitApplicationDocumentEntry entry = register.get(0);
        org.junit.jupiter.api.Assertions.assertEquals("EMH-2027-0001", entry.applicationNumber());
        org.junit.jupiter.api.Assertions.assertEquals("Nyasha Moyo", entry.applicantName());
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of("NATIONAL_ID"), entry.documents().missingRequirementCodes());
    }

    @Test
    void submissionRequiresUploadedEvidenceAndReviewRequiresVerification() {
        when(requirementRepository
                .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscRequirementCodeAsc(
                        applicationType.getId()))
                .thenReturn(List.of(requirement));
        when(documentRepository.findAllByApplicationIdAndCurrentTrueAndDeletedAtIsNullOrderByRequirementCodeAsc(
                application.getId())).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> service.assertReadyForSubmission(application));

        ApplicationDocument pending = pendingDocument();
        when(documentRepository.findAllByApplicationIdAndCurrentTrueAndDeletedAtIsNullOrderByRequirementCodeAsc(
                application.getId())).thenReturn(List.of(pending));
        assertDoesNotThrow(() -> service.assertReadyForSubmission(application));
        assertThrows(IllegalStateException.class, () -> service.assertReadyForReview(application));
        assertFalse(service.isReadyForReview(application));

        DocumentVerificationChangedEvent verifiedEvent = verificationEvent(pending, "VERIFIED", null, 1);
        pending.applyVerification(verifiedEvent);
        assertDoesNotThrow(() -> service.assertReadyForReview(application));
        assertTrue(service.isReadyForReview(application));
    }

    @Test
    void emptyDocumentRequirementConfigurationDoesNotPassSubmissionGate() {
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        when(requirementRepository
                .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscRequirementCodeAsc(
                        applicationType.getId()))
                .thenReturn(List.of());

        AdmissionsDocumentViews.ApplicationDocumentRegister register =
                service.applicantRegister(application.getId(), application.getApplicant().getUserId());

        assertFalse(register.requiredDocumentsUploaded());
        assertFalse(register.requiredDocumentsVerified());
        assertThrows(IllegalStateException.class, () -> service.assertReadyForSubmission(application));
    }

    @Test
    void rejectedVerificationUpdatesProjectionAndQueuesMissingDocumentNotification() {
        ApplicationDocument pending = pendingDocument();
        DocumentVerificationChangedEvent rejectedEvent = verificationEvent(
                pending, "REJECTED", "The identity number is unreadable.", 1);
        when(documentRepository.findByDocumentIdAndCurrentTrueAndDeletedAtIsNull(pending.getDocumentId()))
                .thenReturn(Optional.of(pending));
        when(documentRepository.saveAndFlush(any(ApplicationDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.applyVerification(rejectedEvent);

        verify(integrationOutboxService).enqueueMissingDocumentsNotification(
                application,
                "NATIONAL_ID",
                "The identity number is unreadable.",
                pending.getDocumentId(),
                1,
                rejectedEvent.verifiedByUserId());
    }

    @Test
    void ignoresDuplicateOrOlderDocumentVerificationEvents() {
        ApplicationDocument pending = pendingDocument();
        DocumentVerificationChangedEvent verifiedEvent = verificationEvent(pending, "VERIFIED", null, 2);
        pending.applyVerification(verifiedEvent);
        when(documentRepository.findByDocumentIdAndCurrentTrueAndDeletedAtIsNull(pending.getDocumentId()))
                .thenReturn(Optional.of(pending));

        service.applyVerification(verificationEvent(pending, "REJECTED", "Stale rejection evidence.", 1));

        org.mockito.Mockito.verify(documentRepository, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    void documentReplacementCannotBypassTheAdmissionCycleDeadline() {
        UUID applicantUserId = UUID.randomUUID();
        Applicant applicant = new Applicant(
                applicantUserId, "APP-0002", "LOCAL", "Tariro", "Dube", "tariro@example.test");
        AdmissionCycle closedCycle = new AdmissionCycle(
                UUID.randomUUID(), UUID.randomUUID(), "2026-AUG", "August 2026",
                now.minusSeconds(86400), now.minusSeconds(1));
        Application closedApplication = new Application(
                closedCycle, applicant, applicationType, "EMH-2026-0099", false);
        ReflectionTestUtils.setField(closedApplication, "id", UUID.randomUUID());
        when(applicationRepository.findById(closedApplication.getId())).thenReturn(Optional.of(closedApplication));

        assertThrows(IllegalStateException.class, () -> service.linkApplicantDocument(
                closedApplication.getId(), applicantUserId, UUID.randomUUID(), "NATIONAL_ID"));

        org.mockito.Mockito.verify(documentsReportingClient, org.mockito.Mockito.never()).getUploadedDocument(any());
    }

    private ApplicationDocument pendingDocument() {
        ApplicationDocument document = new ApplicationDocument(
                application, UUID.randomUUID(), "NATIONAL_ID", true, "identity.pdf", "application/pdf",
                "a".repeat(64), now, null);
        ReflectionTestUtils.setField(document, "id", UUID.randomUUID());
        return document;
    }

    private DocumentVerificationChangedEvent verificationEvent(
            ApplicationDocument document, String status, String rejectionReason, long version) {
        return new DocumentVerificationChangedEvent(
                UUID.randomUUID(), DocumentVerificationChangedEvent.CURRENT_SCHEMA_VERSION, now,
                document.getDocumentId(), "APPLICATION", application.getId(), "NATIONAL_ID", status,
                UUID.randomUUID(), now, status.equals("VERIFIED") ? "Identity confirmed." : null,
                rejectionReason, version);
    }
}
