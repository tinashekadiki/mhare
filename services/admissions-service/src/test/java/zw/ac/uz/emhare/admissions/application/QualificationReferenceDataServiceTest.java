package zw.ac.uz.emhare.admissions.application;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.GradeReferenceOption;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.SubjectReferenceOption;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionSubject;
import zw.ac.uz.emhare.admissions.domain.model.GradingScale;
import zw.ac.uz.emhare.admissions.domain.model.GradingScaleValue;
import zw.ac.uz.emhare.admissions.domain.model.QualificationLevel;
import zw.ac.uz.emhare.admissions.domain.model.SubjectLevel;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionSubjectRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ExamBodyRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.GradingScaleRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.GradingScaleValueRepository;

/**
 * @author Tinashe K
 */
@ExtendWith(MockitoExtension.class)
class QualificationReferenceDataServiceTest {

  @Mock private ExamBodyRepository examBodyRepository;

  @Mock private AdmissionSubjectRepository subjectRepository;

  @Mock private GradingScaleRepository gradingScaleRepository;

  @Mock private GradingScaleValueRepository gradingScaleValueRepository;

  private final Clock clock = Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);
  private QualificationReferenceDataService service;

  @BeforeEach
  void setUp() {
    service =
        new QualificationReferenceDataService(
            examBodyRepository,
            subjectRepository,
            gradingScaleRepository,
            gradingScaleValueRepository,
            clock);
  }

  @Test
  void createSubject_shouldNormalizeAndPersistReferenceFields() {
    UUID subjectId = UUID.randomUUID();
    when(subjectRepository.existsByLevelAndCodeIgnoreCaseAndDeletedAtIsNull(
            SubjectLevel.O_LEVEL, "4099"))
        .thenReturn(false);
    when(subjectRepository.saveAndFlush(any(AdmissionSubject.class)))
        .thenAnswer(
            invocation -> {
              AdmissionSubject subject = invocation.getArgument(0);
              ReflectionTestUtils.setField(subject, "id", subjectId);
              return subject;
            });

    SubjectReferenceOption saved =
        service.createSubject(
            "o_level", " 4099 ", "  Applied Technology ", " technical ", true, false, true, true);

    assertAll(
        () -> assertEquals(subjectId, saved.id()),
        () -> assertEquals("4099", saved.code()),
        () -> assertEquals("Applied Technology", saved.name()),
        () -> assertEquals("TECHNICAL", saved.subjectGroupCode()),
        () -> assertTrue(saved.scienceSubject()),
        () -> assertFalse(saved.mathematicsSubject()),
        () -> assertTrue(saved.englishSubject()),
        () -> assertTrue(saved.active()));
  }

  @Test
  void updateSubject_shouldRejectDuplicateCodeWithinTheSameLevel() {
    AdmissionSubject subject = subject("4001", "Agriculture", SubjectLevel.O_LEVEL, 3L);
    when(subjectRepository.findByIdAndDeletedAtIsNull(subject.getId()))
        .thenReturn(Optional.of(subject));
    when(subjectRepository.existsByLevelAndCodeIgnoreCaseAndDeletedAtIsNull(
            SubjectLevel.O_LEVEL, "4002"))
        .thenReturn(true);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.updateSubject(
                    subject.getId(),
                    "4002",
                    "Agriculture",
                    "SCIENCE",
                    true,
                    false,
                    false,
                    true,
                    3L));

    assertTrue(exception.getMessage().contains("already exists"));
  }

  @Test
  void deleteSubject_shouldSoftDeleteWithActorAndEnforceExpectedVersion() {
    UUID actorUserId = UUID.randomUUID();
    AdmissionSubject subject = subject("4001", "Agriculture", SubjectLevel.O_LEVEL, 4L);
    when(subjectRepository.findByIdAndDeletedAtIsNull(subject.getId()))
        .thenReturn(Optional.of(subject));

    service.deleteSubject(subject.getId(), actorUserId, 4L);

    assertAll(
        () -> assertTrue(subject.isDeleted()),
        () -> assertEquals(actorUserId, subject.getDeletedByUserId()));
    verify(subjectRepository).saveAndFlush(subject);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> service.deleteSubject(subject.getId(), actorUserId, 3L));
    assertTrue(exception.getMessage().contains("changed by another user"));
  }

  @Test
  void createGrade_shouldUseEffectiveScaleAndPersistPoints() {
    GradingScale scale = gradingScale(QualificationLevel.A_LEVEL);
    UUID gradeId = UUID.randomUUID();
    when(gradingScaleRepository.findApplicableScale(
            QualificationLevel.A_LEVEL, LocalDate.now(clock)))
        .thenReturn(Optional.of(scale));
    when(gradingScaleValueRepository.findByGradingScaleIdAndGradeIgnoreCaseAndDeletedAtIsNull(
            scale.getId(), "A*"))
        .thenReturn(Optional.empty());
    when(gradingScaleValueRepository.saveAndFlush(any(GradingScaleValue.class)))
        .thenAnswer(
            invocation -> {
              GradingScaleValue value = invocation.getArgument(0);
              ReflectionTestUtils.setField(value, "id", gradeId);
              return value;
            });

    GradeReferenceOption saved =
        service.createGrade("a_level", " a* ", new BigDecimal("6.00"), true, 0);

    assertAll(
        () -> assertEquals(gradeId, saved.id()),
        () -> assertEquals("A*", saved.grade()),
        () -> assertEquals(new BigDecimal("6.00"), saved.points()),
        () -> assertTrue(saved.pass()),
        () -> assertEquals(0, saved.sortOrder()));
  }

  @Test
  void updateGrade_shouldPersistNonPassOutcomeAndDeleteShouldPreserveSoftDeleteAudit() {
    GradingScale scale = gradingScale(QualificationLevel.O_LEVEL);
    GradingScaleValue value = new GradingScaleValue(scale, "U", null, false, 6);
    UUID gradeId = UUID.randomUUID();
    UUID actorUserId = UUID.randomUUID();
    ReflectionTestUtils.setField(value, "id", gradeId);
    ReflectionTestUtils.setField(value, "version", 2L);
    when(gradingScaleValueRepository.findByIdAndDeletedAtIsNull(gradeId))
        .thenReturn(Optional.of(value));
    when(gradingScaleValueRepository.saveAndFlush(value)).thenReturn(value);

    GradeReferenceOption updated = service.updateGrade(gradeId, "u", null, false, 7, 2L);
    service.deleteGrade(gradeId, actorUserId, 2L);

    assertAll(
        () -> assertEquals("U", updated.grade()),
        () -> assertFalse(updated.pass()),
        () -> assertEquals(7, updated.sortOrder()),
        () -> assertTrue(value.isDeleted()),
        () -> assertEquals(actorUserId, value.getDeletedByUserId()));
  }

  private AdmissionSubject subject(String code, String name, SubjectLevel level, long version) {
    AdmissionSubject subject = new AdmissionSubject(code, name, level, "SCIENCE", true);
    ReflectionTestUtils.setField(subject, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(subject, "version", version);
    return subject;
  }

  private GradingScale gradingScale(QualificationLevel level) {
    GradingScale scale =
        new GradingScale(
            "TEST-" + level.name(),
            "Test " + level.name(),
            level,
            LocalDate.parse("1980-01-01"),
            null);
    ReflectionTestUtils.setField(scale, "id", UUID.randomUUID());
    return scale;
  }
}
