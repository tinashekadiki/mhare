package zw.ac.uz.emhare.dining.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import zw.ac.uz.emhare.dining.operations.domain.model.StudentDietaryRequirement;
import zw.ac.uz.emhare.dining.operations.domain.model.StudentDietaryRequirement.Severity;
import zw.ac.uz.emhare.dining.operations.domain.model.StudentDietaryRequirement.Status;

/**
 * @author Tinashe K
 */
class DiningDietaryRequirementGovernanceTest extends DiningGovernanceFixture {
  @ParameterizedTest
  @ValueSource(strings = {"student", "severity", "from", "actor", "reversed"})
  void dietaryEvidenceRequiresStudentSeverityEffectiveScopeAndRecorder(String invalid) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StudentDietaryRequirement(
                invalid.equals("student") ? null : STUDENT,
                "R260001",
                "ALLERGY",
                "Avoid peanuts",
                invalid.equals("severity") ? null : Severity.CRITICAL,
                UUID.randomUUID(),
                invalid.equals("from") ? null : START,
                invalid.equals("reversed") ? START.minusDays(1) : END,
                invalid.equals("actor") ? null : MAKER));
  }

  @Test
  void onlyIndependentOperatorCanResolveOrExpireActiveDietaryEvidence() {
    UUID clinical = UUID.randomUUID();
    var requirement =
        new StudentDietaryRequirement(
            STUDENT,
            " r260001 ",
            " allergy ",
            " Avoid peanuts ",
            Severity.CRITICAL,
            clinical,
            START,
            END,
            MAKER);
    assertThrows(
        IllegalStateException.class,
        () -> requirement.resolve(Status.ACTIVE, CHECKER, "No transition", NOW, 0));
    assertThrows(
        IllegalStateException.class,
        () -> requirement.resolve(Status.RESOLVED, CHECKER, "Stale transition", NOW, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> requirement.resolve(Status.RESOLVED, null, "No operator", NOW, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> requirement.resolve(Status.RESOLVED, MAKER, "Self resolution", NOW, 0));
    requirement.resolve(Status.EXPIRED, CHECKER, " Clinical evidence expired ", NOW, 0);
    assertEquals(Status.EXPIRED, requirement.getStatus());
    assertEquals(CHECKER, requirement.getResolvedByUserId());
    assertEquals(NOW, requirement.getResolvedAt());
    assertEquals("Clinical evidence expired", requirement.getResolutionReason());
    assertEquals(clinical, requirement.getClinicalDocumentId());
    assertEquals("ALLERGY", requirement.getRequirementCode());
    assertEquals("Avoid peanuts", requirement.getDescription());
    assertEquals(MAKER, requirement.getRecordedByUserId());
    assertThrows(
        IllegalStateException.class,
        () -> requirement.resolve(Status.RESOLVED, CHECKER, "Repeated resolution", NOW, 0));
  }
}
