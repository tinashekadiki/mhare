package zw.ac.uz.emhare.admissions.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Qualification-duration evidence invariant coverage. @author Tinashe K */
class ApplicantQualificationSittingDurationTest {

  @Test
  void capturesPositiveDurationAndAllowsMissingDuration() {
    ApplicantQualificationSitting sitting =
        new ApplicantQualificationSitting(null, QualificationLevel.DEGREE, null, null, null, 2025);

    sitting.update(null, "University of Zimbabwe", null, null, 2025, null, null, 48);
    assertThat(sitting.getDurationMonths()).isEqualTo(48);

    sitting.update(null, "University of Zimbabwe", null, null, 2025, null, null, null);
    assertThat(sitting.getDurationMonths()).isNull();
  }

  @Test
  void rejectsZeroOrNegativeDuration() {
    ApplicantQualificationSitting sitting =
        new ApplicantQualificationSitting(null, QualificationLevel.DIPLOMA, null, null, null, 2025);

    assertThatThrownBy(() -> sitting.update(null, "Polytechnic", null, null, 2025, null, null, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one month");
    assertThatThrownBy(() -> sitting.update(null, "Polytechnic", null, null, 2025, null, null, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void requiresSubjectResultsOnlyForSchoolQualificationLevels() {
    assertThat(sittingAt(QualificationLevel.O_LEVEL).requiresSubjectResultsForVerification())
        .isTrue();
    assertThat(sittingAt(QualificationLevel.A_LEVEL).requiresSubjectResultsForVerification())
        .isTrue();
    assertThat(sittingAt(QualificationLevel.DIPLOMA).requiresSubjectResultsForVerification())
        .isFalse();
    assertThat(sittingAt(QualificationLevel.DEGREE).requiresSubjectResultsForVerification())
        .isFalse();
    assertThat(sittingAt(QualificationLevel.PROFESSIONAL).requiresSubjectResultsForVerification())
        .isFalse();
    assertThat(sittingAt(QualificationLevel.OTHER).requiresSubjectResultsForVerification())
        .isFalse();
  }

  @Test
  void completesSchoolEvidenceWithResultsAndHigherEvidenceWithInstitutionAndDuration() {
    ApplicantQualificationSitting school = sittingAt(QualificationLevel.A_LEVEL);
    assertThat(school.hasCompleteEvidence(0)).isFalse();
    school.update(null, null, null, null, 2025, null, UUID.randomUUID(), null);
    assertThat(school.hasCompleteEvidence(1)).isTrue();

    ApplicantQualificationSitting degree = sittingAt(QualificationLevel.DEGREE);
    assertThat(degree.hasCompleteEvidence(3)).isFalse();
    degree.update(null, "University of Zimbabwe", null, null, 2025, null, UUID.randomUUID(), 48);
    degree.updateAwardDetails(QualificationAwardType.DEGREE, "Bachelor of Science");
    assertThat(degree.hasCompleteEvidence(0)).isTrue();
  }

  private ApplicantQualificationSitting sittingAt(QualificationLevel level) {
    return new ApplicantQualificationSitting(null, level, null, null, null, 2025);
  }
}
