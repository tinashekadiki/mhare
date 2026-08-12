package zw.ac.uz.emhare.assessmentresults.progression;

import zw.ac.uz.emhare.assessmentresults.progression.domain.model.ProgressionRuleOutcome;
import zw.ac.uz.emhare.assessmentresults.progression.domain.model.ProgressionRuleSet;
import zw.ac.uz.emhare.assessmentresults.progression.domain.model.StudentOverallDecision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.assessmentresults.roster.domain.model.RegistrationRosterImport;

/** @author Tinashe K */
class StudentOverallDecisionTest {

    @Test
    void appliesTheFirstMatchingProgressionThreshold() {
        ProgressionRuleSet ruleSet = ruleSet();
        ProgressionMetrics metrics = new ProgressionMetrics(
                new BigDecimal("120.00"),
                new BigDecimal("108.00"),
                new BigDecimal("12.00"),
                1,
                0,
                new BigDecimal("62.50"));
        ProgressionRuleOutcome proceed = new ProgressionRuleOutcome(
                ruleSet,
                1,
                ProgressionRuleOutcome.DecisionCode.PROCEED_WITH_CARRY,
                "Proceed with one carry Module",
                new BigDecimal("50.00"),
                null,
                new BigDecimal("12.00"),
                1,
                true,
                2,
                false);
        ProgressionRuleOutcome fallback = new ProgressionRuleOutcome(
                ruleSet,
                2,
                ProgressionRuleOutcome.DecisionCode.REPEAT,
                "Repeat the programme period",
                null,
                null,
                null,
                null,
                false,
                1,
                true);

        assertEquals(true, proceed.matches(metrics));
        assertEquals(true, fallback.matches(metrics));
    }

    @Test
    void enforcesIndependentReviewApprovalAndPublication() {
        UUID calculator = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        UUID approver = UUID.randomUUID();
        UUID publisher = UUID.randomUUID();
        StudentOverallDecision decision = decision(calculator);

        assertThrows(IllegalStateException.class, () -> decision.review(
                calculator, "Self review", Instant.parse("2027-01-02T00:00:00Z"), 0));
        decision.review(reviewer, "Evidence reviewed", Instant.parse("2027-01-02T00:00:00Z"), 0);
        assertThrows(IllegalStateException.class, () -> decision.approve(
                reviewer, "Self approval", Instant.parse("2027-01-03T00:00:00Z"), 0));
        decision.approve(approver, "Board approval", Instant.parse("2027-01-03T00:00:00Z"), 0);
        assertThrows(IllegalStateException.class, () -> decision.publish(
                approver, "Self publication", Instant.parse("2027-01-04T00:00:00Z"), 0));
        decision.publish(publisher, "Released to the official record", Instant.parse("2027-01-04T00:00:00Z"), 0);

        assertEquals(StudentOverallDecision.Status.PUBLISHED, decision.getStatus());
        assertEquals(publisher, decision.getPublishedByUserId());
    }

    private StudentOverallDecision decision(UUID calculator) {
        ProgressionRuleSet ruleSet = ruleSet();
        ProgressionRuleOutcome outcome = new ProgressionRuleOutcome(
                ruleSet,
                1,
                ProgressionRuleOutcome.DecisionCode.PROCEED,
                "Proceed",
                new BigDecimal("50.00"),
                null,
                BigDecimal.ZERO,
                0,
                true,
                2,
                false);
        RegistrationRosterImport roster = mock(RegistrationRosterImport.class);
        when(roster.getStudentId()).thenReturn(UUID.randomUUID());
        when(roster.getStudentNumber()).thenReturn("STU-2027-0000001");
        when(roster.getProgrammeEnrolmentId()).thenReturn(UUID.randomUUID());
        when(roster.getProgrammeId()).thenReturn(ruleSet.getProgrammeId());
        when(roster.getProgrammeVersionId()).thenReturn(ruleSet.getProgrammeVersionId());
        when(roster.getAcademicPeriodId()).thenReturn(UUID.randomUUID());
        when(roster.getAcademicPeriodCode()).thenReturn("2027-S1");
        when(roster.getProgrammePeriodNumber()).thenReturn(1);
        return new StudentOverallDecision(
                ruleSet,
                roster,
                outcome,
                new ProgressionMetrics(
                        new BigDecimal("120.00"),
                        new BigDecimal("120.00"),
                        BigDecimal.ZERO,
                        0,
                        0,
                        new BigDecimal("68.00")),
                "PRG-2027-S1-STU-2027-0000001-V1",
                1,
                null,
                calculator,
                Instant.parse("2027-01-01T00:00:00Z"));
    }

    private ProgressionRuleSet ruleSet() {
        return new ProgressionRuleSet(
                "BACC-P1",
                "Accounting progression period 1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                1);
    }
}
