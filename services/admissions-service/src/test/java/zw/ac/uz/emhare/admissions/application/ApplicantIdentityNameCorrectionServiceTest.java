package zw.ac.uz.emhare.admissions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import zw.ac.uz.emhare.admissions.domain.model.Applicant;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantIdentityNameCorrection;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationDocument;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationType;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantIdentityNameCorrectionRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationDocumentRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationRepository;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Applicant request and staff approval regression coverage. @author Tinashe K */
class ApplicantIdentityNameCorrectionServiceTest {

  private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
  private final ApplicantRepository applicantRepository = mock(ApplicantRepository.class);
  private final ApplicationDocumentRepository documentRepository =
      mock(ApplicationDocumentRepository.class);
  private final ApplicantIdentityNameCorrectionRepository correctionRepository =
      mock(ApplicantIdentityNameCorrectionRepository.class);
  private final Instant now = Instant.parse("2026-08-24T10:00:00Z");
  private final UUID applicantUserId = UUID.randomUUID();
  private final UUID applicationId = UUID.randomUUID();
  private final UUID documentId = UUID.randomUUID();
  private Applicant applicant;
  private Application application;
  private ApplicantIdentityNameCorrectionService service;
  private AtomicReference<ApplicantIdentityNameCorrection> storedCorrection;

  @BeforeEach
  void setUp() throws Exception {
    service =
        new ApplicantIdentityNameCorrectionService(
            applicationRepository,
            applicantRepository,
            documentRepository,
            correctionRepository,
            Clock.fixed(now, ZoneOffset.UTC));
    applicant =
        new Applicant(
            applicantUserId, "A000001", "LOCAL", "Registered", "Name", "applicant@example.test");
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
    setId(application, applicationId);
    ApplicationDocument applicationDocument =
        new ApplicationDocument(
            application,
            documentId,
            "NATIONAL_ID",
            true,
            "identity.jpg",
            "image/jpeg",
            "checksum",
            now,
            null);
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
    when(documentRepository.findByDocumentIdAndCurrentTrueAndDeletedAtIsNull(documentId))
        .thenReturn(Optional.of(applicationDocument));
    when(correctionRepository.findByApplicationIdAndDocumentIdAndDeletedAtIsNull(
            applicationId, documentId))
        .thenReturn(Optional.empty());
    storedCorrection = new AtomicReference<>();
    when(correctionRepository.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              ApplicantIdentityNameCorrection value = invocation.getArgument(0);
              if (value.getId() == null) setId(value, UUID.randomUUID());
              storedCorrection.set(value);
              return value;
            });
    when(correctionRepository.findById(any()))
        .thenAnswer(invocation -> Optional.ofNullable(storedCorrection.get()));
  }

  @Test
  void requestDoesNotOverwriteRegisteredNameAndDoesNotBlockDraft() {
    var summary =
        service.requestOfficialNameCorrection(
            applicationId,
            applicantUserId,
            documentId,
            "Document",
            "Middle",
            "Person",
            "The registered account omitted my official document names.");

    assertThat(summary.status()).isEqualTo("REQUESTED");
    assertThat(summary.requestedAt()).isEqualTo(now);
    assertThat(applicant.getFirstName()).isEqualTo("Registered");
    assertThat(applicant.getLastName()).isEqualTo("Name");
    assertThat(application.getStatus().name()).isEqualTo("DRAFT");
  }

  @Test
  void approvalUpdatesApplicantAndApplicationOfficialSnapshot() {
    var requested =
        service.requestOfficialNameCorrection(
            applicationId,
            applicantUserId,
            documentId,
            "Document",
            "Middle",
            "Person",
            "The registered account omitted my official document names.");

    var approved =
        service.completeApproval(
            requested.id(),
            UUID.randomUUID(),
            "Compared the original National ID with the applicant account.");

    assertThat(approved.status()).isEqualTo("APPROVED");
    assertThat(applicant.getDisplayName()).isEqualTo("Document Middle Person");
    assertThat(application.getOfficialFirstName()).isEqualTo("Document");
    assertThat(application.getOfficialMiddleNames()).isEqualTo("Middle");
    assertThat(application.getOfficialLastName()).isEqualTo("Person");
    assertThat(application.getOfficialDisplayName()).isEqualTo("Document Middle Person");
  }

  @Test
  void rejectsAnotherApplicantsApplication() {
    assertThatThrownBy(
            () ->
                service.reviewOcrReading(
                    applicationId, UUID.randomUUID(), documentId, "Document", null, "Person"))
        .isInstanceOf(AccessDeniedException.class);
  }

  private static void setId(AuditableEntity entity, UUID id) throws Exception {
    Field idField = AuditableEntity.class.getDeclaredField("id");
    idField.setAccessible(true);
    idField.set(entity, id);
  }
}
