package zw.ac.uz.emhare.admissions.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Application ownership, state transitions and intake snapshot invariants. @author Tinashe K */
class ApplicationWorkflowInvariantTest {
  private static final Instant NOW = Instant.parse("2026-08-12T08:00:00Z");
  private static final LocalDate START = LocalDate.of(2026, 1, 1);
  private static final LocalDate END = LocalDate.of(2026, 12, 31);
  private final UUID owner = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final Applicant applicant =
      new Applicant(owner, "A000001", "LOCAL", "Tariro", "Moyo", "applicant@example.test");
  private final ApplicationType type =
      new ApplicationType("UNDERGRAD", "Undergraduate", false, false);

  @ParameterizedTest
  @ValueSource(
      strings = {
        "id",
        "code",
        "blankCode",
        "name",
        "blankName",
        "start",
        "end",
        "inverted",
        "choices"
      })
  void bothIntakeConstructorsRejectIncompleteOrInvalidAcademicSetupSnapshots(String invalid) {
    UUID id = invalid.equals("id") ? null : UUID.randomUUID();
    String code = invalid.equals("code") ? null : invalid.equals("blankCode") ? " " : "AUG26";
    String name =
        invalid.equals("name") ? null : invalid.equals("blankName") ? " " : "August intake";
    LocalDate start = invalid.equals("start") ? null : START;
    LocalDate end =
        invalid.equals("end") ? null : invalid.equals("inverted") ? START.minusDays(1) : END;
    int choices = invalid.equals("choices") ? 0 : 3;
    assertThatThrownBy(
            () ->
                new Application(
                    id, code, name, start, end, choices, applicant, type, "APP-1", false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("intake snapshot");
    assertThatThrownBy(
            () ->
                new Application(
                    id,
                    code,
                    name,
                    start,
                    end,
                    choices,
                    applicant,
                    type,
                    "APP-1",
                    ApplicationFeePolicySnapshot.feeFree("No application charge", actor, NOW)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("intake snapshot");
  }

  @Test
  void explicitFeePolicyCannotBeOmittedWhenUsingSnapshotConstructor() {
    assertThatThrownBy(
            () ->
                new Application(
                    UUID.randomUUID(),
                    "AUG26",
                    "August intake",
                    START,
                    END,
                    3,
                    applicant,
                    type,
                    "APP-1",
                    null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("fee-policy snapshot");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "review",
        "evaluate",
        "academicReview",
        "decide",
        "continue",
        "reject",
        "offer",
        "respond",
        "reopen",
        "convert",
        "return"
      })
  void draftCannotSkipAnyControlledWorkflowStage(String operation) {
    Application application = application(false);
    assertThatThrownBy(
            () -> {
              switch (operation) {
                case "review" -> application.moveToUnderReview(actor, "Ready");
                case "evaluate" -> application.applyEvaluationOutcome(true, true, "Eligible");
                case "academicReview" -> application.enterAcademicReview("Review");
                case "decide" -> application.recordChoiceDecision(DecisionOutcome.ADMIT, "Admit");
                case "continue" -> application.continueAfterChoiceRejection("Next choice");
                case "reject" -> application.rejectAfterAllChoices("No eligible choices");
                case "offer" -> application.markOffered("Offer");
                case "respond" ->
                    application.recordOfferResponse(OfferResponseType.ACCEPTED, "Accept");
                case "reopen" -> application.reopenAfterOfferClosed("Expired offer");
                case "convert" -> application.markConverted("Student created");
                default -> application.returnToDraft("Correct identity evidence");
              }
            })
        .isInstanceOf(IllegalStateException.class);
    assertThat(application.getStatus()).isEqualTo(ApplicationStatus.DRAFT);
  }

  @Test
  void submittedApplicationCannotSubmitAgainOrChangeItsDeclarationAndCompleteness() {
    Application application = application(false);
    application.acceptDeclaration(owner, "V1", NOW);
    application.recordSectionCompleteness(true);
    application.submit("Submitted");
    assertThatThrownBy(() -> application.submit("Duplicate"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> application.acceptDeclaration(owner, "V2", NOW))
        .isInstanceOf(IllegalStateException.class);
    application.recordSectionCompleteness(false);
    application.invalidateDeclaration();
    assertThat(application.isSectionsComplete()).isTrue();
    assertThat(application.isDeclarationAccepted()).isTrue();
    assertThat(application.getDeclarationVersion()).isEqualTo("V1");
    assertThat(application.canSubmit()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"owner", "version", "blankVersion", "time"})
  void declarationRequiresTheApplicationOwnerVersionAndAcceptanceTime(String invalid) {
    Application application = application(false);
    assertThatThrownBy(
            () ->
                application.acceptDeclaration(
                    invalid.equals("owner") ? actor : owner,
                    invalid.equals("version") ? null : invalid.equals("blankVersion") ? " " : "V1",
                    invalid.equals("time") ? null : NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(application.isDeclarationAccepted()).isFalse();
    assertThat(application.canSubmit()).isFalse();
  }

  @Test
  void changingProfessionalEvidenceInvalidatesDraftDeclarationAndSubmissionReadiness() {
    Application application = application(false);
    application.acceptDeclaration(owner, " V1 ", NOW);
    application.recordSectionCompleteness(true);
    assertThat(application.canSubmit()).isTrue();
    application.recordProfessionalAchievementsDeclaredNone(true);
    assertThat(application.isProfessionalAchievementsDeclaredNone()).isTrue();
    assertThat(application.isDeclarationAccepted()).isFalse();
    assertThat(application.getDeclarationVersion()).isNull();
    assertThat(application.canSubmit()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void returnForCorrectionClearsReviewPointsAndDeclarationButRetainsPaymentEvidence(
      boolean underReview) {
    Application application = application(true);
    application.confirmPayment(NOW);
    application.acceptDeclaration(owner, "V1", NOW);
    application.recordSectionCompleteness(true);
    application.submit("Submitted");
    if (underReview) application.moveToUnderReview(actor, "Verified");
    application.recordCalculatedPoints(new BigDecimal("12"), NOW);
    application.returnToDraft(" Correct identity evidence ");
    assertThat(application.getStatus()).isEqualTo(ApplicationStatus.DRAFT);
    assertThat(application.getStatusReason()).isEqualTo("Correct identity evidence");
    assertThat(application.getCalculatedTotalPoints()).isNull();
    assertThat(application.getPointsCalculatedAt()).isNull();
    assertThat(application.isDeclarationAccepted()).isFalse();
    assertThat(application.canSubmit()).isFalse();
    assertThat(application.getPaymentConfirmedAt()).isEqualTo(NOW);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "Too short"})
  void returningForCorrectionRequiresMeaningfulReason(String reason) {
    Application application = application(false);
    application.submit("Submitted");
    assertThatThrownBy(() -> application.returnToDraft(reason))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("10 characters");
    assertThat(application.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"points", "time"})
  void calculatedPointsRequireValueAndEvidenceTime(String missing) {
    Application application = application(false);
    assertThatThrownBy(
            () ->
                application.recordCalculatedPoints(
                    missing.equals("points") ? null : BigDecimal.TEN,
                    missing.equals("time") ? null : NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(application.getCalculatedTotalPoints()).isNull();
  }

  @Test
  void feeFreeApplicationRejectsSpuriousPaymentAndWaiverCommands() {
    Application application = application(false);
    assertThatThrownBy(() -> application.confirmPayment(NOW))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not require");
    assertThatThrownBy(() -> application.overridePayment(actor, "Exemption"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not require");
    assertThat(application.canEnterReview()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void requiredFeeBlocksReviewUntilConfirmedOrWaived(boolean waive) {
    Application application = application(true);
    application.submit("Submitted");
    assertThatThrownBy(() -> application.moveToUnderReview(actor, "Premature review"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("confirmed or waived");
    assertThat(application.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
    if (waive) {
      application.overridePayment(actor, " Authorised exemption ");
      assertThatThrownBy(() -> application.overridePayment(actor, "Duplicate"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("already been waived");
      assertThat(application.getPaymentOverrideReason()).isEqualTo("Authorised exemption");
    } else {
      assertThat(application.confirmPayment(NOW)).isTrue();
      assertThat(application.confirmPayment(NOW.plusSeconds(1))).isFalse();
      assertThat(application.getPaymentConfirmedAt()).isEqualTo(NOW);
    }
    application.moveToUnderReview(actor, "Financially cleared");
    assertThat(application.getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
    assertThat(application.getStatusReason()).isEqualTo("Financially cleared");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void paymentWaiverRequiresRecordedReason(String reason) {
    Application application = application(true);
    assertThatThrownBy(() -> application.overridePayment(actor, reason))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(application.canEnterReview()).isFalse();
    assertThat(application.getPaymentOverrideByUserId()).isNull();
  }

  @ParameterizedTest
  @CsvSource({"true,true,ELIGIBLE", "false,true,NOT_ELIGIBLE", "false,false,UNDER_REVIEW"})
  void reevaluationCanCorrectEligibleAndIneligibleOutcomes(
      boolean eligible, boolean complete, ApplicationStatus expected) {
    Application application = application(false);
    application.submit("Submitted");
    application.moveToUnderReview(actor, "Verified");
    application.applyEvaluationOutcome(true, true, "Initial eligibility");
    application.applyEvaluationOutcome(false, true, "Evidence corrected");
    application.applyEvaluationOutcome(eligible, complete, "Latest evaluation");
    assertThat(application.getStatus()).isEqualTo(expected);
    assertThat(application.getStatusReason()).isEqualTo("Latest evaluation");
  }

  @ParameterizedTest
  @ValueSource(strings = {"UNDER_REVIEW", "NOT_ELIGIBLE", "UNDER_ACADEMIC_REVIEW"})
  void finalRejectionIsAvailableOnlyAtReviewStagesAfterChoicesAreExhausted(String stage) {
    Application application = application(false);
    application.submit("Submitted");
    application.moveToUnderReview(actor, "Verified");
    if (stage.equals("NOT_ELIGIBLE"))
      application.applyEvaluationOutcome(false, true, "No eligible choice");
    if (stage.equals("UNDER_ACADEMIC_REVIEW")) {
      application.applyEvaluationOutcome(true, true, "Eligible");
      application.enterAcademicReview("Review");
      application.continueAfterChoiceRejection("Next choice rejected");
    }
    application.rejectAfterAllChoices("All choices exhausted");
    assertThat(application.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
    assertThat(application.getStatusReason()).isEqualTo("All choices exhausted");
  }

  @ParameterizedTest
  @ValueSource(strings = {"first", "blankFirst", "last", "blankLast"})
  void officialNameCorrectionsRequireBothLegalNames(String missing) {
    Application application = application(false);
    assertThatThrownBy(
            () ->
                application.synchronizeOfficialName(
                    missing.equals("first") ? null : missing.equals("blankFirst") ? " " : "Tariro",
                    null,
                    missing.equals("last") ? null : missing.equals("blankLast") ? " " : "Moyo"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("required");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", " Nyasha "})
  void officialNameCorrectionNormalizesOptionalMiddleNames(String middle) {
    Application application = application(false);
    application.synchronizeOfficialName(" Tariro ", middle, " Moyo ");
    assertThat(application.getOfficialDisplayName())
        .isEqualTo(middle != null && !middle.isBlank() ? "Tariro Nyasha Moyo" : "Tariro Moyo");
    assertThat(application.getApplicant().getDisplayName()).isEqualTo("Tariro Moyo");
  }

  private Application application(boolean paymentRequired) {
    return new Application(
        UUID.randomUUID(),
        "AUG26",
        "August intake",
        START,
        END,
        3,
        applicant,
        type,
        "APP-1",
        paymentRequired);
  }
}
