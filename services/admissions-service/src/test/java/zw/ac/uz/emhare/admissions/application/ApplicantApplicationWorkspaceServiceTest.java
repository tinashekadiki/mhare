package zw.ac.uz.emhare.admissions.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceService.*;
import zw.ac.uz.emhare.admissions.application.command.CreateQualificationResultCommand;
import zw.ac.uz.emhare.admissions.application.command.SaveQualificationAggregateCommand;
import zw.ac.uz.emhare.admissions.application.command.UpdateApplicantProfileCommand;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.*;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.AcademicProgrammeOption;
import zw.ac.uz.emhare.admissions.integration.DocumentsReportingClient;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/**
 * @author Tinashe K
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApplicantApplicationWorkspaceServiceTest {
  private static final UUID APPLICATION = new UUID(0, 1),
      APPLICANT = new UUID(0, 2),
      USER = new UUID(0, 3),
      TYPE = new UUID(0, 4),
      PROGRAMME = new UUID(0, 5),
      ENTRY = new UUID(0, 6),
      OTHER = new UUID(0, 99);
  private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  @Mock private ApplicationRepository applicationRepository;
  @Mock private ApplicantRepository applicantRepository;
  @Mock private ApplicationPaymentReferenceRepository paymentReferenceRepository;
  @Mock private ApplicationProgrammeChoiceRepository programmeChoiceRepository;
  @Mock private ApplicationTypeSectionRepository sectionDefinitionRepository;
  @Mock private ApplicationSectionRepository sectionRepository;
  @Mock private ApplicationTypeDocumentRequirementRepository documentRequirementRepository;
  @Mock private ApplicantNextOfKinRepository nextOfKinRepository;
  @Mock private ApplicantEmploymentHistoryRepository employmentRepository;
  @Mock private ApplicantIdentityNameCorrectionRepository identityNameCorrectionRepository;
  @Mock private ApplicantRefereeRepository refereeRepository;
  @Mock private ApplicationRefereeNominationRepository refereeNominationRepository;
  @Mock private ApplicationPriorUzDeclarationRepository priorUzDeclarationRepository;
  @Mock private ApplicationProfessionalAchievementRepository professionalAchievementRepository;
  @Mock private ApplicantRefereeInvitationService refereeInvitationService;
  @Mock private ApplicantQualificationSittingRepository qualificationSittingRepository;
  @Mock private ApplicantQualificationResultRepository qualificationResultRepository;
  @Mock private ExamBodyRepository examBodyRepository;
  @Mock private AdmissionSubjectRepository subjectRepository;
  @Mock private AdmissionsDocumentService documentService;
  @Mock private ApplicationPaymentSubmissionReadinessService paymentSubmissionReadinessService;
  @Mock private QualificationEligibilityService qualificationEligibilityService;
  @Mock private DocumentsReportingClient documentsReportingClient;
  @Mock private ApplicationProgrammeOptionSnapshotRepository programmeOptionSnapshotRepository;
  @Mock private ApplicationProgrammeEntryOptionSelectionRepository entryOptionSelectionRepository;
  @Mock private AdmissionsApplicationWorkflowProgressService workflowProgressService;
  @Mock private Clock clock;

  @Spy
  private tools.jackson.databind.ObjectMapper objectMapper =
      new tools.jackson.databind.ObjectMapper();

  @InjectMocks private ApplicantApplicationWorkspaceService service;
  private Applicant applicant;
  private ApplicationType type;
  private Application application;
  private final List<ApplicationSection> sections = new ArrayList<>();
  private final List<ApplicantNextOfKin> kin = new ArrayList<>();
  private final List<ApplicantEmploymentHistory> jobs = new ArrayList<>();
  private final List<ApplicationProfessionalAchievement> achievements = new ArrayList<>();
  private final List<ApplicationProgrammeChoice> choices = new ArrayList<>();
  private final List<ApplicationProgrammeEntryOptionSelection> entrySelections = new ArrayList<>();
  private final List<ApplicantQualificationSitting> sittings = new ArrayList<>();
  private final List<ApplicantQualificationResult> results = new ArrayList<>();
  private final List<ApplicantReferee> referees = new ArrayList<>();
  private final List<ApplicationRefereeNomination> nominations = new ArrayList<>();
  private ApplicationPriorUzDeclaration priorDeclaration;

  @BeforeEach
  void setUp() {
    applicant =
        identify(
            new Applicant(USER, "A000001", "LOCAL", "Test", "Applicant", "applicant@example.test"),
            APPLICANT);
    type = identify(new ApplicationType("UNDERGRAD", "Undergraduate", false, false), TYPE);
    application =
        identify(
            new Application(
                OTHER,
                "2026-S1",
                "August intake",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31),
                2,
                applicant,
                type,
                "APP-001",
                false),
            APPLICATION);
    when(clock.instant()).thenReturn(NOW);
    when(applicationRepository.findById(APPLICATION)).thenReturn(Optional.of(application));
    when(sectionRepository.findAllByApplicationIdAndDeletedAtIsNullOrderBySortOrderAsc(APPLICATION))
        .thenReturn(sections);
    when(sectionDefinitionRepository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    when(sectionRepository.saveAllAndFlush(any()))
        .thenAnswer(
            inv -> {
              List<ApplicationSection> added = inv.getArgument(0);
              sections.addAll(added);
              return added;
            });
    when(documentService.staffRegister(APPLICATION))
        .thenReturn(
            new AdmissionsDocumentViews.ApplicationDocumentRegister(
                APPLICATION, "APP-001", true, false, List.of(), List.of(), List.of(), List.of()));
    when(paymentSubmissionReadinessService.evaluate(application))
        .thenReturn(
            new ApplicationPaymentSubmissionReadinessService.PaymentSubmissionReadiness(
                true, true, "No fee required"));
    when(qualificationEligibilityService.recalculateApplicationPoints(APPLICATION))
        .thenReturn(
            new QualificationPointsCalculator.EligibilitySnapshot(
                BigDecimal.TEN, List.of(), List.of()));
    when(refereeInvitationService.latestInvitations(APPLICATION)).thenReturn(Map.of());
    when(nextOfKinRepository.findAllByApplicantIdAndDeletedAtIsNullOrderByPrimaryDescFullNameAsc(
            APPLICANT))
        .thenAnswer(inv -> kin.stream().filter(value -> !value.isDeleted()).toList());
    when(nextOfKinRepository.countByApplicantIdAndDeletedAtIsNull(APPLICANT))
        .thenAnswer(inv -> kin.stream().filter(value -> !value.isDeleted()).count());
    when(nextOfKinRepository.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              ApplicantNextOfKin value = inv.getArgument(0);
              if (value.getId() == null) {
                identify(value, new UUID(0, 100 + kin.size()));
                kin.add(value);
              }
              return value;
            });
    when(nextOfKinRepository.findByIdAndApplicantIdAndDeletedAtIsNull(any(), eq(APPLICANT)))
        .thenAnswer(
            inv ->
                kin.stream()
                    .filter(value -> value.getId().equals(inv.getArgument(0)) && !value.isDeleted())
                    .findFirst());
    when(employmentRepository.findAllByApplicantIdAndDeletedAtIsNullOrderByStartedOnDesc(APPLICANT))
        .thenAnswer(inv -> jobs.stream().filter(value -> !value.isDeleted()).toList());
    when(employmentRepository.countByApplicantIdAndDeletedAtIsNull(APPLICANT))
        .thenAnswer(inv -> jobs.stream().filter(value -> !value.isDeleted()).count());
    when(employmentRepository.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              ApplicantEmploymentHistory value = inv.getArgument(0);
              if (value.getId() == null) {
                identify(value, new UUID(0, 200 + jobs.size()));
                jobs.add(value);
              }
              return value;
            });
    when(employmentRepository.findByIdAndApplicantIdAndDeletedAtIsNull(any(), eq(APPLICANT)))
        .thenAnswer(
            inv ->
                jobs.stream()
                    .filter(value -> value.getId().equals(inv.getArgument(0)) && !value.isDeleted())
                    .findFirst());
    when(priorUzDeclarationRepository.findByApplicationIdAndDeletedAtIsNull(APPLICATION))
        .thenAnswer(inv -> Optional.ofNullable(priorDeclaration));
    when(priorUzDeclarationRepository.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              priorDeclaration = inv.getArgument(0);
              return priorDeclaration;
            });
    when(professionalAchievementRepository
            .findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtAsc(APPLICATION))
        .thenAnswer(inv -> achievements.stream().filter(value -> !value.isDeleted()).toList());
    when(professionalAchievementRepository.saveAllAndFlush(any()))
        .thenAnswer(
            inv -> {
              List<ApplicationProfessionalAchievement> added = inv.getArgument(0);
              achievements.addAll(added);
              return added;
            });
    when(programmeChoiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(APPLICATION))
        .thenReturn(choices);
    when(programmeChoiceRepository.saveAllAndFlush(any()))
        .thenAnswer(
            inv -> {
              List<ApplicationProgrammeChoice> added = inv.getArgument(0);
              for (var value : added)
                if (value.getId() == null) {
                  identify(value, new UUID(0, 300 + choices.size()));
                  choices.add(value);
                }
              return added;
            });
    when(entryOptionSelectionRepository.saveAllAndFlush(any()))
        .thenAnswer(
            inv -> {
              List<ApplicationProgrammeEntryOptionSelection> added = inv.getArgument(0);
              entrySelections.addAll(added);
              return added;
            });
    when(entryOptionSelectionRepository
            .findAllByProgrammeChoice_IdAndDeletedAtIsNullOrderByPreferenceRankAsc(any()))
        .thenAnswer(
            inv ->
                entrySelections.stream()
                    .filter(value -> value.getProgrammeChoiceId().equals(inv.getArgument(0)))
                    .toList());
    when(qualificationSittingRepository
            .findAllByApplicationIdAndDeletedAtIsNullOrderByYearWrittenDesc(APPLICATION))
        .thenAnswer(inv -> sittings.stream().filter(value -> !value.isDeleted()).toList());
    when(qualificationSittingRepository.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              ApplicantQualificationSitting value = inv.getArgument(0);
              if (value.getId() == null) {
                identify(value, new UUID(0, 400 + sittings.size()));
                sittings.add(value);
              }
              return value;
            });
    when(qualificationSittingRepository.findByIdAndApplicationIdAndDeletedAtIsNull(
            any(), eq(APPLICATION)))
        .thenAnswer(
            inv ->
                sittings.stream()
                    .filter(value -> value.getId().equals(inv.getArgument(0)) && !value.isDeleted())
                    .findFirst());
    when(qualificationResultRepository
            .findAllByQualificationSittingIdAndDeletedAtIsNullOrderBySubjectNameSnapshotAsc(any()))
        .thenAnswer(
            inv ->
                results.stream()
                    .filter(
                        value ->
                            value.getQualificationSitting().getId().equals(inv.getArgument(0))
                                && !value.isDeleted())
                    .toList());
    when(qualificationResultRepository.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              ApplicantQualificationResult value = inv.getArgument(0);
              storeResult(value);
              return value;
            });
    when(qualificationResultRepository.saveAllAndFlush(any()))
        .thenAnswer(
            inv -> {
              List<ApplicantQualificationResult> added = inv.getArgument(0);
              added.forEach(this::storeResult);
              return added;
            });
    when(qualificationResultRepository.findByIdAndQualificationSittingIdAndDeletedAtIsNull(
            any(), any()))
        .thenAnswer(
            inv ->
                results.stream()
                    .filter(
                        value ->
                            value.getId().equals(inv.getArgument(0))
                                && value
                                    .getQualificationSitting()
                                    .getId()
                                    .equals(inv.getArgument(1))
                                && !value.isDeleted())
                    .findFirst());
    when(qualificationResultRepository.findActiveSubjectIdsByQualificationSittingId(any()))
        .thenAnswer(
            inv ->
                results.stream()
                    .filter(
                        value ->
                            value.getQualificationSitting().getId().equals(inv.getArgument(0))
                                && !value.isDeleted()
                                && value.getSubject() != null)
                    .map(value -> value.getSubject().getId())
                    .toList());
    when(refereeRepository.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              ApplicantReferee value = inv.getArgument(0);
              if (value.getId() == null) {
                identify(value, new UUID(0, 600 + referees.size()));
                referees.add(value);
              }
              return value;
            });
    when(refereeRepository.findByIdAndApplicantIdAndDeletedAtIsNull(any(), eq(APPLICANT)))
        .thenAnswer(
            inv ->
                referees.stream()
                    .filter(value -> value.getId().equals(inv.getArgument(0)) && !value.isDeleted())
                    .findFirst());
    when(refereeNominationRepository.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              ApplicationRefereeNomination value = inv.getArgument(0);
              if (value.getId() == null) {
                identify(value, new UUID(0, 700 + nominations.size()));
                nominations.add(value);
              }
              return value;
            });
    when(refereeNominationRepository
            .findAllByApplicationIdAndCurrentTrueAndDeletedAtIsNullOrderByCreatedAtAsc(APPLICATION))
        .thenAnswer(
            inv -> nominations.stream().filter(ApplicationRefereeNomination::isCurrent).toList());
    when(refereeNominationRepository
            .findByApplicationIdAndRefereeIdAndCurrentTrueAndDeletedAtIsNull(
                eq(APPLICATION), any()))
        .thenAnswer(
            inv ->
                nominations.stream()
                    .filter(
                        value ->
                            value.isCurrent()
                                && value.getReferee().getId().equals(inv.getArgument(1)))
                    .findFirst());
  }

  @Test
  void refereeInvitationIsReissuedOnlyForNewOrChangedEmailAndWithdrawalRevokesAccess() {
    saveReferee(null, "referee@example.test", "+263 771 234 567", 0);
    var referee = referees.getFirst();
    var nomination = nominations.getFirst();
    verify(refereeInvitationService).issueInvitation(application, referee, nomination);
    saveReferee(referee.getId(), "REFEREE@example.test", "+263771234567", 0);
    verify(refereeInvitationService).issueInvitation(application, referee, nomination);
    saveReferee(referee.getId(), "new@example.test", null, 0);
    verify(refereeInvitationService, times(2)).issueInvitation(application, referee, nomination);
    service.resendRefereeInvitation(APPLICATION, USER, referee.getId(), 0);
    verify(refereeInvitationService, times(3)).issueInvitation(application, referee, nomination);
    assertThrows(
        IllegalStateException.class,
        () -> service.resendRefereeInvitation(APPLICATION, USER, referee.getId(), 1));
    assertThrows(
        IllegalStateException.class,
        () -> service.deleteReferee(APPLICATION, USER, referee.getId(), 1));
    service.deleteReferee(APPLICATION, USER, referee.getId(), 0);
    verify(refereeInvitationService).revokeInvitations(APPLICATION, referee.getId());
    assertFalse(nomination.isCurrent());
    assertFalse(referee.isDeleted());
    assertThrows(
        IllegalArgumentException.class,
        () -> service.resendRefereeInvitation(APPLICATION, USER, referee.getId(), 0));
    service.deleteReferee(APPLICATION, USER, referee.getId(), 0);
  }

  @ParameterizedTest
  @ValueSource(strings = {"same-email", "same-phone", "missing-referee", "stale-update"})
  void refereeContactsAndVersionsProtectNominations(String scenario) {
    saveReferee(null, "referee@example.test", "+263 771 234 567", 0);
    if (scenario.equals("same-email"))
      assertThrows(
          IllegalArgumentException.class,
          () -> saveReferee(null, "REFEREE@example.test", "+263772222222", 0));
    if (scenario.equals("same-phone"))
      assertThrows(
          IllegalArgumentException.class,
          () -> saveReferee(null, "other@example.test", "+263771234567", 0));
    if (scenario.equals("missing-referee")) {
      assertThrows(
          IllegalArgumentException.class, () -> saveReferee(OTHER, "other@example.test", null, 0));
      assertThrows(
          IllegalArgumentException.class,
          () -> service.resendRefereeInvitation(APPLICATION, USER, OTHER, 0));
      assertThrows(
          IllegalArgumentException.class, () -> service.deleteReferee(APPLICATION, USER, OTHER, 0));
    }
    if (scenario.equals("stale-update"))
      assertThrows(
          IllegalStateException.class,
          () -> saveReferee(referees.getFirst().getId(), "other@example.test", null, 1));
    assertEquals(1, nominations.size());
    verify(refereeInvitationService).issueInvitation(any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"O_LEVEL", "A_LEVEL", "DIPLOMA", "DEGREE", "PROFESSIONAL", "OTHER"})
  void qualificationAggregatePersistsOwnedEvidenceAndAppropriateAwardOrSubjectResults(
      String level) {
    qualificationReference(level);
    service.saveQualificationAggregate(APPLICATION, USER, null, qualificationCommand(level));
    var sitting = sittings.getFirst();
    assertEquals(OTHER, sitting.getDocumentId());
    assertEquals(level, sitting.getLevel().name());
    if (level.endsWith("_LEVEL")) {
      assertNull(sitting.getAwardType());
      assertEquals("A", results.getFirst().getGrade());
      assertTrue(results.getFirst().getPrincipalSubject());
      var oldResult = results.getFirst();
      service.saveQualificationAggregate(
          APPLICATION, USER, sitting.getId(), qualificationCommand(level));
      assertTrue(oldResult.isDeleted());
      assertEquals(USER, oldResult.getDeletedByUserId());
    } else {
      assertEquals(level, sitting.getAwardType().name());
      assertEquals("Professional qualification", sitting.getQualificationName());
      assertTrue(results.isEmpty());
    }
    service.recordQualificationDecision(APPLICATION, sitting.getId(), OTHER, "VERIFIED", null, 0);
    assertEquals(QualificationResultStatus.VERIFIED, sitting.getVerificationStatus());
    service.reopenQualificationsForApplicantCorrection(APPLICATION);
    assertEquals(QualificationResultStatus.CAPTURED, sitting.getVerificationStatus());
    assertNull(sitting.getVerifiedByUserId());
    service.recordQualificationDecision(
        APPLICATION, sitting.getId(), OTHER, "REJECTED", " Evidence is illegible ", 0);
    assertEquals("Evidence is illegible", sitting.getRejectionReason());
    service.deleteQualificationSitting(APPLICATION, USER, sitting.getId(), 0);
    assertTrue(sitting.isDeleted());
    assertTrue(results.stream().allMatch(AuditableEntity::isDeleted));
  }

  @ParameterizedTest
  @ValueSource(strings = {"wrong-owner-type", "wrong-owner-id", "rejected", "wrong-document-type"})
  void qualificationEvidenceCannotCrossApplicationOwnershipOrUseRejectedDocuments(String scenario) {
    qualificationReference("O_LEVEL");
    when(documentsReportingClient.getUploadedDocument(OTHER))
        .thenReturn(
            uploadedEvidence(
                scenario.equals("wrong-owner-type") ? "APPLICANT" : "APPLICATION",
                scenario.equals("wrong-owner-id") ? APPLICANT : APPLICATION,
                scenario.equals("wrong-document-type") ? "PASSPORT" : "O_LEVEL",
                scenario.equals("rejected") ? "REJECTED" : "PENDING"));
    if (scenario.startsWith("wrong-owner"))
      assertThrows(
          org.springframework.security.access.AccessDeniedException.class,
          () ->
              service.saveQualificationAggregate(
                  APPLICATION, USER, null, qualificationCommand("O_LEVEL")));
    else if (scenario.equals("rejected"))
      assertThrows(
          IllegalStateException.class,
          () ->
              service.saveQualificationAggregate(
                  APPLICATION, USER, null, qualificationCommand("O_LEVEL")));
    else
      assertThrows(
          IllegalArgumentException.class,
          () ->
              service.saveQualificationAggregate(
                  APPLICATION, USER, null, qualificationCommand("O_LEVEL")));
    verify(qualificationSittingRepository, never()).saveAndFlush(any());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "no-results",
        "duplicate-results",
        "unknown-subject",
        "inactive-subject",
        "deleted-subject",
        "wrong-subject-level",
        "missing-evidence",
        "missing-exam-body",
        "deleted-exam-body",
        "unknown-exam-body",
        "non-school-results",
        "missing-title",
        "missing-institution",
        "bad-award",
        "bad-level"
      })
  void qualificationAggregateRejectsInvalidCapture(String scenario) {
    qualificationReference("O_LEVEL");
    var command = qualificationCommand("O_LEVEL");
    String level = "O_LEVEL", award = null, title = "Qualification", institution = "School";
    UUID evidence = OTHER, exam = ENTRY;
    List<CreateQualificationResultCommand> captured = command.results();
    if (scenario.equals("no-results")) captured = null;
    if (scenario.equals("duplicate-results"))
      captured = List.of(captured.getFirst(), captured.getFirst());
    if (scenario.equals("unknown-subject"))
      when(subjectRepository.findAllById(any())).thenReturn(List.of());
    if (scenario.equals("inactive-subject")
        || scenario.equals("deleted-subject")
        || scenario.equals("wrong-subject-level")) {
      var subject =
          identify(
              new AdmissionSubject(
                  "MAT",
                  "Mathematics",
                  scenario.equals("wrong-subject-level")
                      ? SubjectLevel.A_LEVEL
                      : SubjectLevel.O_LEVEL,
                  "SCIENCE",
                  true),
              PROGRAMME);
      if (scenario.equals("inactive-subject"))
        subject.updateReference("MAT", "Mathematics", "SCIENCE", true, true, false, false);
      if (scenario.equals("deleted-subject")) subject.markDeleted(USER);
      when(subjectRepository.findAllById(any())).thenReturn(List.of(subject));
    }
    if (scenario.equals("missing-evidence")) evidence = null;
    if (scenario.equals("missing-exam-body")) exam = null;
    if (scenario.equals("unknown-exam-body"))
      when(examBodyRepository.findById(ENTRY)).thenReturn(Optional.empty());
    if (scenario.equals("deleted-exam-body")) {
      var body = identify(new ExamBody("ZIMSEC", "ZIMSEC", OTHER), ENTRY);
      body.markDeleted(USER);
      when(examBodyRepository.findById(ENTRY)).thenReturn(Optional.of(body));
    }
    if (List.of("non-school-results", "missing-title", "missing-institution", "bad-award")
        .contains(scenario)) {
      level = "DIPLOMA";
      if (!scenario.equals("non-school-results")) captured = List.of();
    }
    if (scenario.equals("missing-title")) title = " ";
    if (scenario.equals("missing-institution")) institution = null;
    if (scenario.equals("bad-award")) award = "UNKNOWN";
    if (scenario.equals("bad-level")) level = "UNKNOWN";
    var invalid =
        new SaveQualificationAggregateCommand(
            level,
            award,
            title,
            exam,
            institution,
            "100",
            "200",
            2025,
            24,
            OTHER,
            evidence,
            captured,
            0);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.saveQualificationAggregate(APPLICATION, USER, null, invalid));
  }

  @Test
  void qualificationResultCaptureProtectsSubjectIdentityAndDeletesOnlyOwnedCurrentRecords() {
    qualificationReference("O_LEVEL");
    saveLegacySitting(null, "O_LEVEL", ENTRY, 0);
    var sitting = sittings.getFirst();
    assertThrows(
        IllegalStateException.class,
        () ->
            service.recordQualificationDecision(
                APPLICATION, sitting.getId(), OTHER, "VERIFIED", null, 0));
    service.saveQualificationResult(
        APPLICATION, USER, sitting.getId(), null, PROGRAMME, " a ", true, 0);
    var result = results.getFirst();
    assertEquals("A", result.getGrade());
    assertThrows(
        IllegalStateException.class,
        () ->
            service.saveQualificationResult(
                APPLICATION, USER, sitting.getId(), result.getId(), PROGRAMME, "B", false, 1));
    service.saveQualificationResult(
        APPLICATION, USER, sitting.getId(), result.getId(), PROGRAMME, "B", false, 0);
    assertEquals("B", result.getGrade());
    assertFalse(result.getPrincipalSubject());
    var changedSubject =
        identify(new AdmissionSubject("ENG", "English", SubjectLevel.O_LEVEL, "LANGUAGE"), OTHER);
    when(subjectRepository.findById(OTHER)).thenReturn(Optional.of(changedSubject));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.saveQualificationResult(
                APPLICATION, USER, sitting.getId(), result.getId(), OTHER, "B", false, 0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.saveQualificationResult(
                APPLICATION, USER, sitting.getId(), OTHER, PROGRAMME, "B", false, 0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.recordQualificationDecision(
                APPLICATION, sitting.getId(), OTHER, "UNKNOWN", null, 0));
    assertThrows(
        IllegalStateException.class,
        () ->
            service.recordQualificationDecision(
                APPLICATION, sitting.getId(), OTHER, "VERIFIED", null, 1));
    assertThrows(
        IllegalStateException.class,
        () ->
            service.deleteQualificationResult(
                APPLICATION, USER, sitting.getId(), result.getId(), 1));
    service.deleteQualificationResult(APPLICATION, USER, sitting.getId(), result.getId(), 0);
    assertTrue(result.isDeleted());
    assertEquals(USER, result.getDeletedByUserId());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.deleteQualificationResult(
                APPLICATION, USER, sitting.getId(), result.getId(), 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.deleteQualificationResult(APPLICATION, USER, OTHER, result.getId(), 0));
  }

  @Test
  void bulkResultCaptureRejectsDuplicatesAndAllowsNewSubjectsOnlyOnce() {
    qualificationReference("A_LEVEL");
    saveLegacySitting(null, "A_LEVEL", ENTRY, 0);
    var sitting = sittings.getFirst();
    var commands = qualificationCommand("A_LEVEL").results();
    assertThrows(
        IllegalArgumentException.class,
        () -> service.addQualificationResults(APPLICATION, USER, sitting.getId(), null));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.addQualificationResults(APPLICATION, USER, sitting.getId(), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.addQualificationResults(
                APPLICATION,
                USER,
                sitting.getId(),
                List.of(commands.getFirst(), commands.getFirst())));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.addQualificationResults(APPLICATION, USER, OTHER, commands));
    service.addQualificationResults(APPLICATION, USER, sitting.getId(), commands);
    assertEquals(1, results.size());
    assertEquals("A", results.getFirst().getGrade());
    assertThrows(
        IllegalArgumentException.class,
        () -> service.addQualificationResults(APPLICATION, USER, sitting.getId(), commands));
  }

  @Test
  void sittingEditsEnforceOwnershipVersionAndImmutableLevel() {
    qualificationReference("O_LEVEL");
    saveLegacySitting(null, "O_LEVEL", ENTRY, 0);
    var sitting = sittings.getFirst();
    saveLegacySitting(sitting.getId(), "O_LEVEL", ENTRY, 0);
    assertEquals(24, sitting.getDurationMonths());
    assertThrows(
        IllegalStateException.class, () -> saveLegacySitting(sitting.getId(), "O_LEVEL", ENTRY, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> saveLegacySitting(sitting.getId(), "A_LEVEL", ENTRY, 0));
    assertThrows(IllegalArgumentException.class, () -> saveLegacySitting(null, "O_LEVEL", null, 0));
    assertThrows(
        IllegalArgumentException.class, () -> saveLegacySitting(OTHER, "O_LEVEL", ENTRY, 0));
    assertThrows(
        IllegalArgumentException.class, () -> saveLegacySitting(null, "O_LEVEL", OTHER, 0));
    assertThrows(
        IllegalStateException.class,
        () -> service.deleteQualificationSitting(APPLICATION, USER, sitting.getId(), 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.deleteQualificationSitting(APPLICATION, USER, OTHER, 0));
    saveLegacySitting(null, "DEGREE", null, 0);
    assertNull(sittings.getLast().getExamBody());
  }

  @Test
  void verificationQueueExcludesDraftsAndVerifiedQualificationsButIncludesPendingDocuments() {
    qualificationReference("DIPLOMA");
    service.saveQualificationAggregate(APPLICATION, USER, null, qualificationCommand("DIPLOMA"));
    when(applicationRepository.findAll()).thenReturn(List.of(application));
    assertTrue(service.verificationQueue().qualifications().isEmpty());
    application.submit("Submitted by applicant");
    when(documentService.staffRegister(APPLICATION))
        .thenReturn(
            new AdmissionsDocumentViews.ApplicationDocumentRegister(
                APPLICATION,
                "APP-001",
                true,
                false,
                List.of(),
                List.of("DIPLOMA"),
                List.of(),
                List.of()));
    var queue = service.verificationQueue();
    assertEquals(1, queue.qualifications().size());
    assertEquals(1, queue.documents().size());
    assertFalse(queue.applicationSections().isEmpty());
    service.recordQualificationDecision(
        APPLICATION, sittings.getFirst().getId(), OTHER, "VERIFIED", null, 0);
    assertTrue(service.verificationQueue().qualifications().isEmpty());
    application.markDeleted(USER);
    assertTrue(service.verificationQueue().applicationSections().isEmpty());
  }

  @Test
  void verifiedQualificationMustBeReopenedByStaffBeforeApplicantEdits() {
    qualificationReference("DEGREE");
    service.saveQualificationAggregate(APPLICATION, USER, null, qualificationCommand("DEGREE"));
    var sitting = sittings.getFirst();
    service.recordQualificationDecision(APPLICATION, sitting.getId(), OTHER, "VERIFIED", null, 0);
    clearInvocations(qualificationSittingRepository);
    assertThrows(
        IllegalStateException.class,
        () ->
            service.saveQualificationAggregate(
                APPLICATION, USER, sitting.getId(), qualificationCommand("DEGREE")));
    verify(qualificationSittingRepository, never()).saveAndFlush(any());
    assertEquals(QualificationResultStatus.VERIFIED, sitting.getVerificationStatus());
  }

  private void storeResult(ApplicantQualificationResult value) {
    if (value.getId() == null) {
      identify(value, new UUID(0, 500 + results.size()));
      results.add(value);
    }
  }

  private void saveReferee(UUID id, String email, String phone, long version) {
    service.saveReferee(
        APPLICATION,
        USER,
        id,
        "Referee Name",
        "Dr",
        "University",
        "Lecturer",
        email,
        phone,
        "Computer science",
        "Academic supervisor",
        version);
  }

  private void saveLegacySitting(UUID id, String level, UUID exam, long version) {
    service.saveQualificationSitting(
        APPLICATION,
        USER,
        id,
        level,
        exam,
        "School",
        "100",
        "200",
        2025,
        24,
        OTHER,
        OTHER,
        version);
  }

  private void qualificationReference(String level) {
    var body = identify(new ExamBody("ZIMSEC", "ZIMSEC", OTHER), ENTRY);
    when(examBodyRepository.findById(ENTRY)).thenReturn(Optional.of(body));
    var subject =
        identify(
            new AdmissionSubject(
                "MAT",
                "Mathematics",
                level.equals("A_LEVEL") ? SubjectLevel.A_LEVEL : SubjectLevel.O_LEVEL,
                "SCIENCE",
                true),
            PROGRAMME);
    when(subjectRepository.findById(PROGRAMME)).thenReturn(Optional.of(subject));
    when(subjectRepository.findAllById(any())).thenReturn(List.of(subject));
    when(documentsReportingClient.getUploadedDocument(OTHER))
        .thenReturn(
            uploadedEvidence(
                "APPLICATION", APPLICATION, "ACADEMIC_QUALIFICATION_EVIDENCE", "PENDING"));
  }

  private SaveQualificationAggregateCommand qualificationCommand(String level) {
    return new SaveQualificationAggregateCommand(
        level,
        null,
        level.endsWith("_LEVEL") ? null : "Professional qualification",
        level.endsWith("_LEVEL") ? ENTRY : null,
        "School",
        "100",
        "200",
        2025,
        24,
        OTHER,
        OTHER,
        level.endsWith("_LEVEL")
            ? List.of(new CreateQualificationResultCommand(PROGRAMME, " a ", true))
            : List.of(),
        0);
  }

  private DocumentsReportingClient.UploadedDocumentSnapshot uploadedEvidence(
      String ownerType, UUID ownerId, String code, String status) {
    return new DocumentsReportingClient.UploadedDocumentSnapshot(
        OTHER,
        ownerType,
        ownerId,
        code,
        "qualification.pdf",
        "application/pdf",
        100,
        "checksum",
        USER,
        NOW,
        status,
        null,
        null,
        null,
        null,
        null,
        "QUEUED",
        0);
  }

  @Test
  void initializesOnlyOnceWithRouteSpecificRequirementsAndCurrentPoints() {
    var workspace = service.applicantWorkspace(APPLICATION, USER);
    assertEquals("APP-001", workspace.application().applicationNumber());
    assertEquals(BigDecimal.TEN, workspace.application().calculatedTotalPoints());
    assertFalse(workspace.readyForSubmission());
    assertTrue(
        workspace.missingRequirements().stream().anyMatch(value -> value.contains("Next of kin")));
    assertFalse(
        workspace.sections().stream()
            .filter(section -> section.code().equals("PAYMENT"))
            .findFirst()
            .orElseThrow()
            .required());
    assertFalse(
        workspace.sections().stream()
            .filter(section -> section.code().equals("DOCUMENTS"))
            .findFirst()
            .orElseThrow()
            .required());
    service.staffWorkspace(APPLICATION);
    verify(sectionRepository).saveAllAndFlush(any());
    verify(qualificationEligibilityService, times(2)).recalculateApplicationPoints(APPLICATION);
  }

  @Test
  void preservesConfiguredSectionDefinitionsAndRefreshesIncompleteRequiredEvidence() {
    var definitions =
        List.of(
            new ApplicationTypeSection(type, "DOCUMENTS", "Documents", true, false, 0, 1),
            new ApplicationTypeSection(type, "PAYMENT", "Payment", true, false, 0, 2),
            new ApplicationTypeSection(
                type, "EMPLOYMENT_HISTORY", "Employment", false, true, 0, 3));
    when(sectionDefinitionRepository
            .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAsc(TYPE))
        .thenReturn(definitions);
    ApplicationTypeDocumentRequirement requirement = mock(ApplicationTypeDocumentRequirement.class);
    when(requirement.isRequired()).thenReturn(true);
    when(documentRequirementRepository
            .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscRequirementCodeAsc(
                TYPE))
        .thenReturn(List.of(requirement));
    when(documentService.staffRegister(APPLICATION))
        .thenReturn(
            new AdmissionsDocumentViews.ApplicationDocumentRegister(
                APPLICATION,
                "APP-001",
                false,
                false,
                List.of("ID"),
                List.of(),
                List.of(),
                List.of()));
    var workspace = service.applicantWorkspace(APPLICATION, USER);
    assertEquals(3, workspace.sections().size());
    assertEquals("IN_PROGRESS", workspace.sections().getFirst().status());
    assertTrue(workspace.sections().getLast().completionSummary().contains("Not required"));
    assertThrows(IllegalStateException.class, () -> service.assertReadyForSubmission(application));
  }

  @Test
  void applicationOwnershipDeletionAndSubmittedStateAreEnforcedBeforeCapture() {
    assertThrows(
        IllegalArgumentException.class, () -> service.applicantWorkspace(APPLICATION, OTHER));
    assertThrows(IllegalArgumentException.class, () -> service.applicantWorkspace(OTHER, USER));
    application.submit("Applicant submitted");
    assertThrows(IllegalStateException.class, () -> saveKin(null, "Parent", true, 0));
    application.markDeleted(USER);
    assertThrows(IllegalArgumentException.class, () -> service.staffWorkspace(APPLICATION));
    verify(nextOfKinRepository, never()).saveAndFlush(any());
  }

  @Test
  void nextOfKinChangesKeepOnePrimaryContactInvalidateDeclarationAndSoftDelete() {
    application.acceptDeclaration(USER, "2026.1", NOW);
    saveKin(null, "First parent", true, 0);
    assertFalse(application.isDeclarationAccepted());
    var second = saveKin(null, "Second parent", true, 0);
    assertFalse(second.nextOfKin().getFirst().primary());
    assertTrue(second.nextOfKin().getLast().primary());
    UUID id = kin.getLast().getId();
    assertThrows(IllegalStateException.class, () -> saveKin(id, "Stale", true, 1));
    assertThrows(IllegalArgumentException.class, () -> saveKin(OTHER, "Missing", true, 0));
    assertEquals(
        "Updated parent", saveKin(id, "Updated parent", true, 0).nextOfKin().getLast().fullName());
    assertThrows(
        IllegalStateException.class, () -> service.deleteNextOfKin(APPLICATION, USER, id, 1));
    service.deleteNextOfKin(APPLICATION, USER, id, 0);
    assertTrue(kin.getLast().isDeleted());
    assertEquals(USER, kin.getLast().getDeletedByUserId());
    assertThrows(
        IllegalArgumentException.class, () -> service.deleteNextOfKin(APPLICATION, USER, id, 0));
  }

  @Test
  void employmentCaptureSupportsUpdateAndAuditedRemovalWithOptimisticVersions() {
    service.saveEmployment(
        APPLICATION,
        USER,
        null,
        "UZ",
        "Research assistant",
        LocalDate.of(2024, 1, 1),
        null,
        true,
        "Research",
        0);
    UUID id = jobs.getFirst().getId();
    assertThrows(
        IllegalStateException.class,
        () ->
            service.saveEmployment(
                APPLICATION,
                USER,
                id,
                "UZ",
                "Stale",
                LocalDate.of(2024, 1, 1),
                null,
                true,
                "Research",
                1));
    var updated =
        service.saveEmployment(
            APPLICATION,
            USER,
            id,
            "UZ",
            "Researcher",
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2025, 1, 1),
            false,
            "Completed",
            0);
    assertEquals("Researcher", updated.employmentHistory().getFirst().positionTitle());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.saveEmployment(
                APPLICATION,
                USER,
                OTHER,
                "UZ",
                "Missing",
                LocalDate.of(2024, 1, 1),
                null,
                true,
                "Research",
                0));
    assertThrows(
        IllegalStateException.class, () -> service.deleteEmployment(APPLICATION, USER, id, 1));
    service.deleteEmployment(APPLICATION, USER, id, 0);
    assertTrue(jobs.getFirst().isDeleted());
    assertThrows(
        IllegalArgumentException.class, () -> service.deleteEmployment(APPLICATION, USER, id, 0));
  }

  @Test
  void previousUzStudyDeclarationIsUpdatedWithoutCreatingADuplicate() {
    assertFalse(
        service
            .savePriorUzDeclaration(APPLICATION, USER, false, null, null, null, null, null)
            .priorUzDeclaration()
            .previouslyStudiedAtUz());
    var previous = priorDeclaration;
    var updated =
        service.savePriorUzDeclaration(
            APPLICATION,
            USER,
            true,
            "R200001",
            LocalDate.of(2020, 1, 1),
            LocalDate.of(2024, 1, 1),
            true,
            true);
    assertSame(previous, priorDeclaration);
    assertEquals("R200001", updated.priorUzDeclaration().registrationNumber());
    assertTrue(updated.priorUzDeclaration().previouslyTookUpPlace());
  }

  @Test
  void achievementsAreReplacedByAuditedEvidenceOrAnExplicitNoneDeclaration() {
    var input =
        new ProfessionalAchievementInput(
            " award ", " Academic award ", " UZ ", LocalDate.of(2025, 1, 1), " Research prize ");
    var captured =
        service.replaceProfessionalAchievements(APPLICATION, USER, false, List.of(input));
    assertEquals("AWARD", captured.professionalAchievements().getFirst().type());
    assertEquals("Academic award", captured.professionalAchievements().getFirst().title());
    assertThrows(
        IllegalArgumentException.class,
        () -> service.replaceProfessionalAchievements(APPLICATION, USER, true, List.of(input)));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.replaceProfessionalAchievements(APPLICATION, USER, false, List.of()));
    var cleared = service.replaceProfessionalAchievements(APPLICATION, USER, true, null);
    assertTrue(cleared.professionalAchievementsDeclaredNone());
    assertTrue(cleared.professionalAchievements().isEmpty());
    assertTrue(achievements.getFirst().isDeleted());
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "unsupported"})
  void rejectsUnknownAchievementTypes(String invalid) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.replaceProfessionalAchievements(
                APPLICATION,
                USER,
                false,
                List.of(new ProfessionalAchievementInput(invalid, "Title", null, null, null))));
  }

  @Test
  void programmeReplacementUsesOnlyDraftSnapshotsAndPreservesPreferenceOrder() {
    programmeOption("[{\"id\":\"" + ENTRY + "\",\"code\":\"DAY\",\"name\":\"Day study\"}]", 1, 1);
    var selected =
        service.replaceStructuredProgrammeChoices(
            APPLICATION,
            USER,
            List.of(new ProgrammeChoiceSelection(PROGRAMME, List.of(ENTRY))),
            false,
            null);
    assertEquals("BSC", selected.application().programmeChoices().getFirst().programmeCode());
    assertEquals(ENTRY, selected.programmeEntryPreferences().getFirst().entryOptionId());
    assertEquals(1, selected.programmeEntryPreferences().getFirst().preferenceRank());
    ApplicationProgrammeChoice original = choices.getFirst();
    service.replaceStructuredProgrammeChoices(
        APPLICATION,
        OTHER,
        List.of(new ProgrammeChoiceSelection(PROGRAMME, List.of(ENTRY))),
        true,
        "Correct applicant choice with authority");
    assertTrue(original.isDeleted());
    assertEquals(OTHER, original.getDeletedByUserId());
    assertFalse(choices.getLast().isDeleted());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "null",
        "empty",
        "too-many",
        "duplicate",
        "outside",
        "entry-duplicate",
        "entry-minimum",
        "entry-maximum",
        "entry-outside",
        "reason-null",
        "reason-short",
        "invalid-json"
      })
  void rejectsProgrammeRequestsThatViolateTheSnapshottedContract(String invalid) {
    programmeOption(
        invalid.equals("invalid-json") ? "not-json" : "[]",
        invalid.equals("entry-minimum") ? 1 : 0,
        invalid.equals("entry-maximum") ? 0 : 2);
    List<ProgrammeChoiceSelection> request =
        List.of(new ProgrammeChoiceSelection(PROGRAMME, List.of()));
    if (invalid.equals("null")) request = null;
    if (invalid.equals("empty")) request = List.of();
    if (invalid.equals("too-many"))
      request = List.of(request.getFirst(), request.getFirst(), request.getFirst());
    if (invalid.equals("duplicate")) request = List.of(request.getFirst(), request.getFirst());
    if (invalid.equals("outside"))
      request = List.of(new ProgrammeChoiceSelection(OTHER, List.of()));
    if (invalid.equals("entry-duplicate"))
      request = List.of(new ProgrammeChoiceSelection(PROGRAMME, List.of(ENTRY, ENTRY)));
    if (invalid.equals("entry-outside") || invalid.equals("entry-maximum"))
      request = List.of(new ProgrammeChoiceSelection(PROGRAMME, List.of(ENTRY)));
    List<ProgrammeChoiceSelection> invalidRequest = request;
    Class<? extends RuntimeException> failure =
        invalid.equals("invalid-json")
            ? IllegalStateException.class
            : IllegalArgumentException.class;
    assertThrows(
        failure,
        () ->
            service.replaceStructuredProgrammeChoices(
                APPLICATION,
                USER,
                invalidRequest,
                invalid.startsWith("reason"),
                invalid.equals("reason-short") ? "short" : null));
    verify(programmeChoiceRepository, never()).saveAllAndFlush(any());
  }

  @Test
  void supportsSimpleChoicesAndEmptyEntrySnapshotsWithoutSynthesizingOptions() {
    programmeOption("{}", 0, 0);
    assertTrue(
        service
            .replaceProgrammeChoices(APPLICATION, USER, List.of(PROGRAMME), false, null)
            .programmeEntryPreferences()
            .isEmpty());
    programmeOption("[]", 0, 0);
    assertTrue(
        service
            .replaceStructuredProgrammeChoices(
                APPLICATION,
                USER,
                List.of(new ProgrammeChoiceSelection(PROGRAMME, null)),
                false,
                null)
            .programmeEntryPreferences()
            .isEmpty());
  }

  @Test
  void declarationsRequireCompleteSectionsAndTheCurrentVersionAndCanBeWithdrawn() {
    assertThrows(
        IllegalStateException.class,
        () -> service.acceptDeclaration(APPLICATION, USER, true, "2026.1"));
    sections.clear();
    sections.add(
        new ApplicationSection(
            application,
            new ApplicationTypeSection(
                type, "REVIEW_DECLARATION", "Declaration", true, false, 0, 1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.acceptDeclaration(APPLICATION, USER, true, "2025.1"));
    var accepted = service.acceptDeclaration(APPLICATION, USER, true, "2026.1");
    assertEquals(NOW, accepted.declarationAcceptedAt());
    assertTrue(accepted.readyForSubmission());
    assertDoesNotThrow(() -> service.assertReadyForSubmission(application));
    assertNull(
        service.acceptDeclaration(APPLICATION, USER, false, "2026.1").declarationAcceptedAt());
  }

  @Test
  void profileCorrectionsCannotChangeCategoryOrTakeAnotherApplicantsIdentity() {
    assertThrows(
        IllegalStateException.class,
        () ->
            service.saveOwnProfile(
                APPLICATION, USER, profile("INTERNATIONAL", "63-100001A01", null)));
    Applicant another =
        identify(
            new Applicant(OTHER, "A000002", "LOCAL", "Other", "Applicant", "other@example.test"),
            OTHER);
    when(applicantRepository.findByNationalIdNumberIgnoreCaseAndDeletedAtIsNull("63-100001A01"))
        .thenReturn(Optional.of(another));
    assertThrows(
        IllegalStateException.class,
        () -> service.saveOwnProfile(APPLICATION, USER, profile("LOCAL", "63-100001A01", null)));
    when(applicantRepository.findByNationalIdNumberIgnoreCaseAndDeletedAtIsNull(any()))
        .thenReturn(Optional.of(applicant));
    when(applicantRepository.findByPassportNumberIgnoreCaseAndDeletedAtIsNull("P1234"))
        .thenReturn(Optional.of(another));
    assertThrows(
        IllegalStateException.class,
        () -> service.saveOwnProfile(APPLICATION, USER, profile("LOCAL", "63-100001A01", "P1234")));
    when(applicantRepository.findByPassportNumberIgnoreCaseAndDeletedAtIsNull(any()))
        .thenReturn(Optional.of(applicant));
    var corrected =
        service.saveOwnProfile(APPLICATION, USER, profile("LOCAL", "63-100001A01", "P1234"));
    assertEquals("Updated", corrected.profile().firstName());
    verify(applicantRepository).saveAndFlush(applicant);
  }

  private ApplicantApplicationWorkspaceViews.ApplicationWorkspace saveKin(
      UUID id, String name, boolean primary, long version) {
    return service.saveNextOfKin(
        APPLICATION,
        USER,
        id,
        name,
        "PARENT",
        "0772000000",
        "parent@example.test",
        "Harare",
        primary,
        version);
  }

  private void programmeOption(String json, int minimum, int maximum) {
    var option =
        new AcademicProgrammeOption(
            PROGRAMME, "BSC", "Science", "BSc", OTHER, "V1", OTHER, "Science", 6, 8, null, null,
            null, null, null, null, minimum, maximum, List.of());
    when(programmeOptionSnapshotRepository
            .findAllByApplicationIdAndDeletedAtIsNullOrderByProgrammeCodeAsc(APPLICATION))
        .thenReturn(List.of(new ApplicationProgrammeOptionSnapshot(application, option, json)));
  }

  private static UpdateApplicantProfileCommand profile(
      String category, String nationalId, String passport) {
    return new UpdateApplicantProfileCommand(
        category,
        "MR",
        "Updated",
        null,
        "Applicant",
        LocalDate.of(2000, 1, 1),
        "MALE",
        "SINGLE",
        nationalId,
        passport,
        OTHER,
        OTHER,
        "Harare",
        "NONE",
        null,
        "SELF",
        "applicant@example.test",
        "0772000000",
        "Harare",
        "Harare",
        "Applicant corrected own profile",
        0);
  }

  private static <T extends AuditableEntity> T identify(T entity, UUID id) {
    ReflectionTestUtils.setField(entity, "id", id);
    return entity;
  }
}
