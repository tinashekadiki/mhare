package zw.ac.uz.emhare.admissions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionSubject;
import zw.ac.uz.emhare.admissions.domain.model.Applicant;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationType;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionSubjectRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantIdentityNameCorrectionRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationRepository;
import zw.ac.uz.emhare.admissions.integration.DocumentsReportingClient;
import zw.ac.uz.emhare.admissions.integration.DocumentsReportingClient.DocumentOcrExtractionSnapshot;
import zw.ac.uz.emhare.admissions.integration.DocumentsReportingClient.UploadedDocumentSnapshot;

/** Applicant-owned OCR proposal mapping coverage. @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class ApplicantDocumentPrefillServiceTest {

  @Mock private ApplicationRepository applicationRepository;
  @Mock private AdmissionSubjectRepository subjectRepository;
  @Mock private DocumentsReportingClient documentsReportingClient;
  @Mock private ApplicantIdentityNameCorrectionRepository identityNameCorrectionRepository;

  private ApplicantDocumentPrefillService service;
  private UUID applicationId;
  private UUID applicantUserId;
  private UUID documentId;
  private Application application;

  @BeforeEach
  void setUp() {
    service =
        new ApplicantDocumentPrefillService(
            applicationRepository,
            subjectRepository,
            documentsReportingClient,
            identityNameCorrectionRepository,
            new ObjectMapper());
    applicationId = UUID.randomUUID();
    applicantUserId = UUID.randomUUID();
    documentId = UUID.randomUUID();
    Applicant applicant =
        new Applicant(applicantUserId, "A000001", "LOCAL", "Tariro", "Moyo", "tariro@example.test");
    application =
        new Application(
            UUID.randomUUID(),
            "AUG-2026",
            "August 2026",
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
            3,
            applicant,
            new ApplicationType("UNDERGRAD", "Undergraduate", false, false),
            "EMH-AUG-2026-000001",
            false);
    lenient()
        .when(applicationRepository.findById(applicationId))
        .thenReturn(Optional.of(application));
    lenient()
        .when(documentsReportingClient.getUploadedDocument(documentId))
        .thenReturn(uploadedDocument("APPLICATION", applicationId));
    lenient()
        .when(
            identityNameCorrectionRepository.findByApplicationIdAndDocumentIdAndDeletedAtIsNull(
                applicationId, documentId))
        .thenReturn(Optional.empty());
    lenient()
        .when(identityNameCorrectionRepository.saveAndFlush(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void rejectsMissingApplicationAndEvidenceOwnedByAnotherApplication() {
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.prefill(applicationId, applicantUserId, documentId, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not found");

    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
    when(documentsReportingClient.getUploadedDocument(documentId))
        .thenReturn(uploadedDocument("APPLICATION", UUID.randomUUID()));
    assertThatThrownBy(() -> service.prefill(applicationId, applicantUserId, documentId, null))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("not owned");
  }

  @Test
  void keepsPendingExtractionLockedAndAllowsManualEntryAfterFailure() {
    when(documentsReportingClient.getOcrExtraction(documentId))
        .thenReturn(extraction("PROCESSING", null, null));
    var pending = service.prefill(applicationId, applicantUserId, documentId, null);
    assertThat(pending.manualEntryAllowed()).isFalse();
    assertThat(pending.personalFields()).isEmpty();
    assertThat(pending.warnings()).containsExactly("OCR is still processing this document.");

    when(documentsReportingClient.getOcrExtraction(documentId))
        .thenReturn(extraction("FAILED", null, null));
    var failed = service.prefill(applicationId, applicantUserId, documentId, null);
    assertThat(failed.manualEntryAllowed()).isTrue();
    assertThat(failed.warnings().getFirst()).contains("Enter the details manually");
  }

  @Test
  void returnsEditableIdentityFactsAndPersistsRegisteredNameMismatchWarnings() {
    when(documentsReportingClient.getOcrExtraction(documentId))
        .thenReturn(
            extraction(
                "COMPLETED",
                "{\"firstName\":\"Different\",\"lastName\":\"Moyo\",\"middleNames\":\"Rudo\"}",
                "[\"Review low confidence date\"]"));

    var prefill = service.prefill(applicationId, applicantUserId, documentId, null);

    assertThat(prefill.personalFields()).containsEntry("middleNames", "Rudo");
    assertThat(prefill.warnings())
        .contains("Review low confidence date")
        .anyMatch(message -> message.contains("firstName") && message.contains("registered"));
    assertThat(prefill.identityNameMismatch()).isNotNull();
    assertThat(prefill.identityNameMismatch().registeredName().firstName()).isEqualTo("Tariro");
    assertThat(prefill.identityNameMismatch().registeredName().lastName()).isEqualTo("Moyo");
    assertThat(prefill.identityNameMismatch().documentName().firstName()).isEqualTo("Different");
    assertThat(prefill.identityNameMismatch().documentName().middleNames()).isEqualTo("Rudo");
    assertThat(prefill.identityNameMismatch().documentId()).isEqualTo(documentId);
    assertThat(prefill.identityNameMismatch().status()).isEqualTo("OCR_REVIEWED");
    assertThat(prefill.manualEntryAllowed()).isTrue();
    verify(identityNameCorrectionRepository).saveAndFlush(any());
  }

  @Test
  void doesNotCreateStructuredMismatchWhenRegisteredAndDocumentNamesAgree() {
    when(documentsReportingClient.getOcrExtraction(documentId))
        .thenReturn(
            extraction("COMPLETED", "{\"firstName\":\" tariro \" ,\"lastName\":\"MOYO\"}", "[]"));

    var prefill = service.prefill(applicationId, applicantUserId, documentId, null);

    assertThat(prefill.identityNameMismatch()).isNull();
  }

  @Test
  void resolvesExactManagedSubjectsAndFlagsAmbiguousMatches() {
    AdmissionSubject mathematics = subject(UUID.randomUUID(), "4008", "Mathematics");
    AdmissionSubject duplicateMathematics = subject(UUID.randomUUID(), "MATH", "Mathematics");
    AdmissionSubject english = subject(UUID.randomUUID(), "4005", "English Language");
    when(subjectRepository.findAllByLevelAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(
            zw.ac.uz.emhare.admissions.domain.model.SubjectLevel.A_LEVEL))
        .thenReturn(List.of(mathematics, duplicateMathematics, english));
    when(documentsReportingClient.getOcrExtraction(documentId))
        .thenReturn(
            extraction(
                "COMPLETED",
                "{\"lines\":[\"Mathematics A\",\"English Language B\",\"Unknown C\"]}",
                "[]"));

    var prefill = service.prefill(applicationId, applicantUserId, documentId, "A_LEVEL");

    assertThat(prefill.qualificationResults()).hasSize(2);
    assertThat(prefill.qualificationResults().getFirst().confirmationRequired()).isTrue();
    assertThat(prefill.qualificationResults().getFirst().candidateSubjects())
        .containsExactly("Mathematics", "Mathematics");
    assertThat(prefill.qualificationResults().get(1).subjectId()).isEqualTo(english.getId());
    assertThat(prefill.qualificationResults().get(1).grade()).isEqualTo("B");
  }

  @Test
  void ignoresUnsupportedLevelsAndMalformedOptionalWarningsButRejectsMalformedFacts() {
    when(documentsReportingClient.getOcrExtraction(documentId))
        .thenReturn(extraction("COMPLETED", "{}", "not-json"));
    var unsupported = service.prefill(applicationId, applicantUserId, documentId, "DIPLOMA");
    assertThat(unsupported.qualificationResults()).isEmpty();
    assertThat(unsupported.warnings().getFirst()).contains("could not be read");

    when(documentsReportingClient.getOcrExtraction(documentId))
        .thenReturn(extraction("COMPLETED", "not-json", "[]"));
    assertThatThrownBy(() -> service.prefill(applicationId, applicantUserId, documentId, "INVALID"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("facts could not be read");
  }

  private UploadedDocumentSnapshot uploadedDocument(String ownerType, UUID ownerId) {
    return new UploadedDocumentSnapshot(
        documentId,
        ownerType,
        ownerId,
        "NATIONAL_ID",
        "national-id.pdf",
        "application/pdf",
        1024,
        "checksum",
        applicantUserId,
        Instant.parse("2026-08-23T10:00:00Z"),
        "PENDING",
        null,
        null,
        null,
        null,
        null,
        "COMPLETED",
        0);
  }

  private DocumentOcrExtractionSnapshot extraction(
      String status, String proposedFactsJson, String warningsJson) {
    return new DocumentOcrExtractionSnapshot(
        documentId,
        status,
        "DOCLING_RAPIDOCR",
        "docling-serve-v1.29.0",
        "{}",
        proposedFactsJson,
        "{}",
        warningsJson,
        1,
        Instant.parse("2026-08-23T10:00:00Z"),
        null,
        null,
        null,
        null,
        0);
  }

  private AdmissionSubject subject(UUID id, String code, String name) {
    AdmissionSubject subject = mock(AdmissionSubject.class);
    lenient().when(subject.getId()).thenReturn(id);
    lenient().when(subject.getCode()).thenReturn(code);
    when(subject.getName()).thenReturn(name);
    return subject;
  }
}
