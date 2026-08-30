package zw.ac.uz.emhare.admissions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.*;
import zw.ac.uz.emhare.admissions.integration.AdmissionsIntegrationOutboxService;
import zw.ac.uz.emhare.admissions.integration.DocumentsReportingClient;
import zw.ac.uz.emhare.admissions.integration.DocumentsReportingClient.UploadedDocumentSnapshot;
import zw.ac.uz.emhare.common.messaging.DocumentVerificationChangedEvent;

/**
 * Applicant-owned evidence, immutable requirement snapshots and correction guards. @author Tinashe
 * K
 */
@ExtendWith(MockitoExtension.class)
class AdmissionsDocumentGovernanceTest {
  private static final Instant NOW = Instant.parse("2026-08-12T08:00:00Z");
  @Mock private ApplicationRepository applications;
  @Mock private ApplicationTypeRepository types;
  @Mock private ApplicationTypeDocumentRequirementRepository requirements;
  @Mock private ApplicationDocumentRequirementSnapshotRepository snapshots;
  @Mock private ApplicationTypeDocumentRequirementCategoryRepository categories;
  @Mock private ApplicationDocumentRepository documents;
  @Mock private ApplicantIdentityNameCorrectionRepository corrections;
  @Mock private ApplicationProgrammeChoiceRepository choices;
  @Mock private DocumentsReportingClient client;
  @Mock private AdmissionsIntegrationOutboxService outbox;
  @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  @InjectMocks private AdmissionsDocumentService service;
  private Application application;
  private ApplicationType type;
  private ApplicationTypeDocumentRequirement identity;
  private final UUID owner = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final List<ApplicationDocument> evidence = new ArrayList<>();
  private final List<ApplicationDocumentRequirementSnapshot> snapshotRows = new ArrayList<>();

  @BeforeEach
  void setUp() {
    type = identified(new ApplicationType("UNDERGRAD", "Undergraduate", false, false));
    application =
        identified(
            new Application(
                UUID.randomUUID(),
                "AUG26",
                "August intake",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 8, 12),
                3,
                new Applicant(
                    owner, "A000001", "LOCAL", "Tariro", "Moyo", "applicant@example.test"),
                type,
                "APP-1",
                false));
    identity = requirement("IDENTITY", true);
    lenient().when(applications.findById(application.getId())).thenReturn(Optional.of(application));
    lenient().when(types.findById(type.getId())).thenReturn(Optional.of(type));
    lenient()
        .when(
            requirements
                .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscRequirementCodeAsc(
                    type.getId()))
        .thenReturn(List.of(identity));
    lenient()
        .when(
            snapshots.findAllByApplicationIdAndDeletedAtIsNullOrderBySortOrderAscRequirementCodeAsc(
                application.getId()))
        .thenReturn(snapshotRows);
    lenient()
        .when(
            documents
                .findAllByApplicationIdAndCurrentTrueAndDeletedAtIsNullOrderByRequirementCodeAsc(
                    application.getId()))
        .thenAnswer(
            invocation -> evidence.stream().filter(ApplicationDocument::isCurrent).toList());
    lenient()
        .when(documents.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              ApplicationDocument row = invocation.getArgument(0);
              if (!evidence.contains(row)) {
                identified(row);
                evidence.add(row);
              }
              return row;
            });
  }

  @Test
  void configuredRequirementCreationNormalizesCodeAndRetainsApplicabilityInSummary() {
    when(requirements.saveAndFlush(any()))
        .thenAnswer(invocation -> identified(invocation.getArgument(0)));
    AdmissionsDocumentViews.DocumentRequirementSummary created =
        service.createRequirement(type.getId(), " passport ", "Passport", false, 4);
    assertThat(created.requirementCode()).isEqualTo("PASSPORT");
    assertThat(created.requirementName()).isEqualTo("Passport");
    assertThat(created.required()).isFalse();
    assertThat(created.sortOrder()).isEqualTo(4);
    assertThat(created.applicationTypeId()).isEqualTo(type.getId());
    when(categories.findAllByDocumentRequirementIdAndDeletedAtIsNull(identity.getId()))
        .thenReturn(List.of(new ApplicationTypeDocumentRequirementCategory(identity, "LOCAL")));
    assertThat(service.requirements(type.getId()).get(0).applicantCategoryCodes())
        .containsExactly("LOCAL");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void blankRequirementCodeCannotCreateAnUnmatchableRequirement(String code) {
    assertThatThrownBy(() -> service.createRequirement(type.getId(), code, "Identity", true, 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("code is required");
    verify(requirements, never()).saveAndFlush(any());
  }

  @Test
  void requirementsCannotBeCreatedForMissingRouteOrDuplicatedNormalizedCode() {
    assertThatThrownBy(
            () -> service.createRequirement(UUID.randomUUID(), "IDENTITY", "Identity", true, 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("type was not found");
    when(requirements.findByApplicationTypeIdAndRequirementCodeAndActiveTrueAndDeletedAtIsNull(
            type.getId(), "IDENTITY"))
        .thenReturn(Optional.of(identity));
    assertThatThrownBy(
            () -> service.createRequirement(type.getId(), " identity ", "Identity", true, 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already has");
    verify(requirements, never()).saveAndFlush(any());
  }

  @Test
  void firstSnapshotFreezesOnlyUniversalAndMatchingApplicantCategoryRequirements() {
    ApplicationTypeDocumentRequirement passport = requirement("PASSPORT", true);
    ApplicationTypeDocumentRequirement local = requirement("LOCAL_PROOF", true);
    when(requirements
            .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscRequirementCodeAsc(
                type.getId()))
        .thenReturn(List.of(identity, passport, local));
    when(categories.findAllByDocumentRequirementIdAndDeletedAtIsNull(identity.getId()))
        .thenReturn(List.of());
    when(categories.findAllByDocumentRequirementIdAndDeletedAtIsNull(passport.getId()))
        .thenReturn(
            List.of(new ApplicationTypeDocumentRequirementCategory(passport, "INTERNATIONAL")));
    when(categories.findAllByDocumentRequirementIdAndDeletedAtIsNull(local.getId()))
        .thenReturn(List.of(new ApplicationTypeDocumentRequirementCategory(local, "LOCAL")));
    service.snapshotRequirements(application);
    ArgumentCaptor<Iterable<ApplicationDocumentRequirementSnapshot>> capture =
        ArgumentCaptor.forClass(Iterable.class);
    verify(snapshots).saveAllAndFlush(capture.capture());
    assertThat(capture.getValue())
        .extracting(ApplicationDocumentRequirementSnapshot::getRequirementCode)
        .containsExactly("IDENTITY", "LOCAL_PROOF");
  }

  @Test
  void existingSnapshotIsNotRebuiltWhenCurrentRoutePolicyChanges() {
    snapshotRows.add(
        new ApplicationDocumentRequirementSnapshot(application, identity, List.of("LOCAL")));
    service.snapshotRequirements(application);
    verifyNoInteractions(requirements, categories);
    verify(snapshots, never()).saveAllAndFlush(any());
    assertThat(
            service
                .applicantRegister(application.getId(), owner)
                .requirements()
                .get(0)
                .applicantCategoryCodes())
        .containsExactly("LOCAL");
  }

  @Test
  void noApplicableRequirementsDoesNotWriteAnEmptySnapshotBatch() {
    when(categories.findAllByDocumentRequirementIdAndDeletedAtIsNull(identity.getId()))
        .thenReturn(
            List.of(new ApplicationTypeDocumentRequirementCategory(identity, "INTERNATIONAL")));
    service.snapshotRequirements(application);
    verify(snapshots, never()).saveAllAndFlush(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "foreignOwner"})
  void applicantCannotReadOrLinkEvidenceForAnUnknownOrForeignApplication(String invalid) {
    UUID id = invalid.equals("missing") ? UUID.randomUUID() : application.getId();
    UUID caller = invalid.equals("foreignOwner") ? actor : owner;
    assertThatThrownBy(() -> service.applicantRegister(id, caller))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not found");
    assertThatThrownBy(
            () -> service.linkApplicantDocument(id, caller, UUID.randomUUID(), "IDENTITY"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not found");
    verifyNoInteractions(client);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ownerType", "ownerId", "documentType", "verification"})
  void uploadedDocumentMustMatchOwnerRequirementAndPendingStatus(String invalid) {
    UUID documentId = UUID.randomUUID();
    when(client.getUploadedDocument(documentId)).thenReturn(upload(documentId, invalid, null));
    assertThatThrownBy(
            () ->
                service.linkApplicantDocument(application.getId(), owner, documentId, " identity "))
        .isInstanceOfAny(IllegalArgumentException.class, IllegalStateException.class);
    verify(documents, never()).saveAndFlush(any());
  }

  @Test
  void documentMustBelongToTheApplicationsRequirementSnapshot() {
    snapshotRows.add(new ApplicationDocumentRequirementSnapshot(application, identity, List.of()));
    assertThatThrownBy(
            () ->
                service.linkApplicantDocument(
                    application.getId(), owner, UUID.randomUUID(), "PASSPORT"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("snapshot");
    verifyNoInteractions(client);
  }

  @ParameterizedTest
  @ValueSource(strings = {"review", "deadline"})
  void linkingStopsAfterReviewBeginsOrAfterTheFinalIntakeDay(String closed) {
    if (closed.equals("review")) {
      application.submit("Submitted");
      application.moveToUnderReview(actor, "Verified");
    } else when(clock.instant()).thenReturn(Instant.parse("2026-08-13T00:00:00Z"));
    assertThatThrownBy(
            () ->
                service.linkApplicantDocument(
                    application.getId(), owner, UUID.randomUUID(), "IDENTITY"))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(client);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void newPendingEvidenceCanBeLinkedOnFinalIntakeDayWhileDraftOrSubmitted(boolean submitted) {
    if (submitted) application.submit("Submitted");
    UUID documentId = UUID.randomUUID();
    when(client.getUploadedDocument(documentId)).thenReturn(upload(documentId, "valid", null));
    AdmissionsDocumentViews.ApplicationDocumentRegister register =
        service.linkApplicantDocument(application.getId(), owner, documentId, "IDENTITY");
    assertThat(register.requiredDocumentsUploaded()).isTrue();
    assertThat(register.requiredDocumentsVerified()).isFalse();
    assertThat(register.pendingRequirementCodes()).containsExactly("IDENTITY");
    assertThat(register.requirements().get(0).documentId()).isEqualTo(documentId);
    assertThat(evidence.get(0).getLinkedAt()).isEqualTo(NOW);
  }

  @Test
  void replacementWithoutCurrentEvidenceOrWithWrongPredecessorIsRejected() {
    UUID documentId = UUID.randomUUID();
    when(client.getUploadedDocument(documentId))
        .thenReturn(upload(documentId, "valid", UUID.randomUUID()));
    assertThatThrownBy(
            () -> service.linkApplicantDocument(application.getId(), owner, documentId, "IDENTITY"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no current");
    ApplicationDocument current = pending();
    when(documents.findByApplicationIdAndRequirementCodeAndCurrentTrueAndDeletedAtIsNull(
            application.getId(), "IDENTITY"))
        .thenReturn(Optional.of(current));
    assertThatThrownBy(
            () -> service.linkApplicantDocument(application.getId(), owner, documentId, "IDENTITY"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("identify the current");
    assertThat(current.isCurrent()).isTrue();
    verify(documents, never()).saveAndFlush(any());
  }

  @ParameterizedTest
  @CsvSource({"false,PENDING", "true,REJECTED"})
  void correctionReplacesPendingDraftOrRejectedSubmittedEvidenceWithoutLosingLineage(
      boolean submitted, String status) {
    ApplicationDocument old = pending();
    evidence.add(old);
    if (submitted) {
      application.submit("Submitted");
      old.applyVerification(event(old, status, 1));
    }
    UUID next = UUID.randomUUID();
    when(client.getUploadedDocument(next)).thenReturn(upload(next, "valid", old.getDocumentId()));
    when(documents.findByApplicationIdAndRequirementCodeAndCurrentTrueAndDeletedAtIsNull(
            application.getId(), "IDENTITY"))
        .thenReturn(Optional.of(old));
    service.linkApplicantDocument(application.getId(), owner, next, "IDENTITY");
    assertThat(old.isCurrent()).isFalse();
    assertThat(evidence.get(1).getSupersedesApplicationDocumentId()).isEqualTo(old.getId());
    assertThat(evidence.get(1).getDocumentId()).isEqualTo(next);
    var persistenceOrder = inOrder(documents);
    persistenceOrder.verify(documents).saveAndFlush(old);
    persistenceOrder.verify(documents).saveAndFlush(evidence.get(1));
    verify(corrections)
        .findByApplicationIdAndDocumentIdAndDeletedAtIsNull(
            application.getId(), old.getDocumentId());
  }

  @ParameterizedTest
  @ValueSource(strings = {"PENDING", "VERIFIED", "REJECTED"})
  void retryingTheCurrentAttachmentReturnsItsRegisterWithoutReplacingIt(String status) {
    ApplicationDocument current = pending();
    if (!status.equals("PENDING")) current.applyVerification(event(current, status, 1));
    evidence.add(current);
    when(documents.findByApplicationIdAndRequirementCodeAndCurrentTrueAndDeletedAtIsNull(
            application.getId(), "IDENTITY"))
        .thenReturn(Optional.of(current));

    var register =
        service.linkApplicantDocument(
            application.getId(), owner, current.getDocumentId(), "IDENTITY");

    assertThat(register.requirements().get(0).documentId()).isEqualTo(current.getDocumentId());
    assertThat(register.requirements().get(0).state()).isEqualTo(status);
    assertThat(current.isCurrent()).isTrue();
    verify(documents, never()).saveAndFlush(any());
    verifyNoInteractions(client, corrections);
  }

  @Test
  void submittedPendingEvidenceCannotBeReplacedUntilVerifierRejectsIt() {
    application.submit("Submitted");
    ApplicationDocument old = pending();
    UUID next = UUID.randomUUID();
    when(client.getUploadedDocument(next)).thenReturn(upload(next, "valid", old.getDocumentId()));
    when(documents.findByApplicationIdAndRequirementCodeAndCurrentTrueAndDeletedAtIsNull(
            application.getId(), "IDENTITY"))
        .thenReturn(Optional.of(old));
    assertThatThrownBy(
            () -> service.linkApplicantDocument(application.getId(), owner, next, "IDENTITY"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Only pending evidence on a draft or rejected");
    assertThat(old.isCurrent()).isTrue();
    verifyNoInteractions(corrections);
  }

  @ParameterizedTest
  @CsvSource({"PENDING,true,false", "REJECTED,false,false", "VERIFIED,true,true"})
  void requiredDocumentGateReflectsCurrentVerificationEvidence(
      String status, boolean uploaded, boolean verified) {
    ApplicationDocument current = pending();
    if (!status.equals("PENDING")) current.applyVerification(event(current, status, 1));
    evidence.add(current);
    AdmissionsDocumentViews.ApplicationDocumentRegister register =
        service.staffRegister(application.getId());
    assertThat(register.requiredDocumentsUploaded()).isEqualTo(uploaded);
    assertThat(register.requiredDocumentsVerified()).isEqualTo(verified);
    assertThat(register.requirements().get(0).state()).isEqualTo(status);
    if (status.equals("REJECTED"))
      assertThat(register.rejectedRequirementCodes()).containsExactly("IDENTITY");
  }

  @Test
  void missingOptionalDocumentDoesNotBlockRequiredVerifiedEvidence() {
    ApplicationTypeDocumentRequirement optional = requirement("SUPPORTING", false);
    when(requirements
            .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscRequirementCodeAsc(
                type.getId()))
        .thenReturn(List.of(identity, optional));
    ApplicationDocument current = pending();
    current.applyVerification(event(current, "VERIFIED", 1));
    evidence.add(current);
    AdmissionsDocumentViews.ApplicationDocumentRegister register =
        service.staffRegister(application.getId());
    assertThat(register.requiredDocumentsVerified()).isTrue();
    assertThat(register.missingRequirementCodes()).isEmpty();
    assertThat(register.requirements().get(1).state()).isEqualTo("MISSING");
  }

  @Test
  void verificationIgnoresUnknownAndStaleEventsButRejectedEvidenceTriggersCorrectionOnce() {
    ApplicationDocument current = pending();
    DocumentVerificationChangedEvent rejected = event(current, "REJECTED", 2);
    service.applyVerification(rejected);
    verify(documents, never()).saveAndFlush(any());
    when(documents.findByDocumentIdAndCurrentTrueAndDeletedAtIsNull(current.getDocumentId()))
        .thenReturn(Optional.of(current));
    service.applyVerification(rejected);
    service.applyVerification(rejected);
    service.applyVerification(event(current, "VERIFIED", 1));
    assertThat(current.getStatus()).isEqualTo(ApplicationDocument.VerificationStatus.REJECTED);
    verify(outbox)
        .enqueueMissingDocumentsNotification(
            application, "IDENTITY", "Unreadable", current.getDocumentId(), 2, actor);
    verify(documents).saveAndFlush(current);
  }

  private UploadedDocumentSnapshot upload(UUID id, String invalid, UUID replaces) {
    return new UploadedDocumentSnapshot(
        id,
        invalid.equals("ownerType") ? "APPLICANT" : "APPLICATION",
        invalid.equals("ownerId") ? UUID.randomUUID() : application.getId(),
        invalid.equals("documentType") ? "PASSPORT" : "IDENTITY",
        "identity.pdf",
        "application/pdf",
        100,
        "sha256",
        owner,
        NOW,
        invalid.equals("verification") ? "VERIFIED" : "PENDING",
        null,
        null,
        null,
        null,
        replaces,
        "PENDING",
        0);
  }

  private ApplicationDocument pending() {
    return identified(
        new ApplicationDocument(
            application,
            UUID.randomUUID(),
            "IDENTITY",
            true,
            "identity.pdf",
            "application/pdf",
            "sha256",
            NOW,
            null));
  }

  private ApplicationTypeDocumentRequirement requirement(String code, boolean required) {
    return identified(
        new ApplicationTypeDocumentRequirement(type, code, code + " evidence", required, 1));
  }

  private DocumentVerificationChangedEvent event(
      ApplicationDocument document, String status, long version) {
    return new DocumentVerificationChangedEvent(
        UUID.randomUUID(),
        1,
        NOW,
        document.getDocumentId(),
        "APPLICATION",
        application.getId(),
        "IDENTITY",
        status,
        actor,
        NOW,
        null,
        status.equals("REJECTED") ? "Unreadable" : null,
        version);
  }

  private <T> T identified(T entity) {
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    return entity;
  }
}
