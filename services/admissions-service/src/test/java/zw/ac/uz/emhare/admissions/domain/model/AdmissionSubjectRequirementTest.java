package zw.ac.uz.emhare.admissions.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Admission subject-rule invariant coverage. @author Tinashe K */
class AdmissionSubjectRequirementTest {

  @Test
  void capturesAndNormalisesAnIndividualSubjectRequirement() {
    AdmissionSubject mathematics =
        new AdmissionSubject("MATH", "Mathematics", SubjectLevel.A_LEVEL, "MATHEMATICS");

    AdmissionSubjectRequirement requirement =
        new AdmissionSubjectRequirement(
            null,
            SubjectLevel.A_LEVEL,
            mathematics,
            "  science  ",
            SubjectRequirementType.COMPULSORY,
            "  b  ",
            new BigDecimal("4"),
            1,
            BigDecimal.ONE,
            2);

    assertThat(requirement.getSubject()).isSameAs(mathematics);
    assertThat(requirement.getSubjectGroupCode()).isEqualTo("SCIENCE");
    assertThat(requirement.getMinimumGrade()).isEqualTo("B");
    assertThat(requirement.getMinimumPoints()).isEqualByComparingTo("4");
    assertThat(requirement.getMinimumCount()).isOne();
    assertThat(requirement.getWeight()).isEqualByComparingTo("1");
    assertThat(requirement.getSortOrder()).isEqualTo(2);
  }

  @Test
  void acceptsAGroupRequirementAndNormalisesBlankOptionalValues() {
    AdmissionSubjectRequirement requirement =
        new AdmissionSubjectRequirement(
            null,
            SubjectLevel.O_LEVEL,
            null,
            "languages",
            SubjectRequirementType.ANY_OF,
            " ",
            null,
            2,
            null,
            1);

    assertThat(requirement.getSubject()).isNull();
    assertThat(requirement.getSubjectGroupCode()).isEqualTo("LANGUAGES");
    assertThat(requirement.getMinimumGrade()).isNull();
  }

  @Test
  void rejectsMissingOrMismatchedSubjectEvidence() {
    assertThatThrownBy(
            () ->
                new AdmissionSubjectRequirement(
                    null,
                    SubjectLevel.A_LEVEL,
                    null,
                    " ",
                    SubjectRequirementType.COMPULSORY,
                    null,
                    null,
                    null,
                    null,
                    1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("subject or subject group");

    AdmissionSubject oLevelEnglish =
        new AdmissionSubject("ENG", "English", SubjectLevel.O_LEVEL, "ENGLISH");
    assertThatThrownBy(
            () ->
                new AdmissionSubjectRequirement(
                    null,
                    SubjectLevel.A_LEVEL,
                    oLevelEnglish,
                    null,
                    SubjectRequirementType.COMPULSORY,
                    "C",
                    null,
                    null,
                    null,
                    1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("subject level");
  }
}
