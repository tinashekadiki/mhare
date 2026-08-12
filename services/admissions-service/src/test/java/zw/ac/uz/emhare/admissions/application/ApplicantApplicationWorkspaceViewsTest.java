package zw.ac.uz.emhare.admissions.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionCycle;
import zw.ac.uz.emhare.admissions.domain.model.Applicant;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantReferee;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantRefereeInvitation;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationRefereeNomination;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationType;

/** @author Tinashe K */
class ApplicantApplicationWorkspaceViewsTest {

    private static final Instant SUBMITTED_AT = Instant.parse("2027-01-15T10:00:00Z");

    @Test
    void exposesSubmittedReferenceDetailsOnlyInTheStaffWorkspaceView() {
        Applicant applicant = new Applicant(
                UUID.randomUUID(), "APP-0001", "LOCAL", "Nyasha", "Moyo", "nyasha@example.test");
        AdmissionCycle intake = new AdmissionCycle(
                UUID.randomUUID(), UUID.randomUUID(), "MBA-2027", "MBA 2027 Intake",
                SUBMITTED_AT.minusSeconds(3600), SUBMITTED_AT.plusSeconds(86400));
        Application application = new Application(
                intake, applicant, new ApplicationType("MBA", "MBA", true, true), "EMH-MBA-0001", false);
        ApplicantReferee referee = new ApplicantReferee(
                applicant, "Dr Tariro Dube", "Dr", "UZ", "Dean",
                "tariro.dube@example.test", "+263771000000");
        ReflectionTestUtils.setField(referee, "id", UUID.randomUUID());
        ApplicationRefereeNomination nomination = new ApplicationRefereeNomination(
                application, referee, "UZ", "Dean", "Academic leadership", "Line manager");
        ApplicantRefereeInvitation invitation = new ApplicantRefereeInvitation(
                application, referee, nomination, "stored-hash", "token-hint",
                SUBMITTED_AT.minusSeconds(7200), SUBMITTED_AT.plusSeconds(86400), 1);
        invitation.submit(
                "Line manager", 4, ApplicantRefereeInvitation.Recommendation.STRONGLY_RECOMMEND,
                "The applicant demonstrates sound judgement and readiness for postgraduate study.",
                true, SUBMITTED_AT);

        var staffSummary = ApplicantApplicationWorkspaceViews.RefereeSummary.from(
                nomination, invitation, true);
        var applicantSummary = ApplicantApplicationWorkspaceViews.RefereeSummary.from(
                nomination, invitation, false);

        assertEquals("SUBMITTED", staffSummary.invitationStatus());
        assertEquals("Line manager", staffSummary.referenceRelationshipToApplicant());
        assertEquals(4, staffSummary.yearsKnown());
        assertEquals("STRONGLY_RECOMMEND", staffSummary.recommendation());
        assertEquals(
                "The applicant demonstrates sound judgement and readiness for postgraduate study.",
                staffSummary.referenceComments());
        assertNull(applicantSummary.referenceRelationshipToApplicant());
        assertNull(applicantSummary.yearsKnown());
        assertNull(applicantSummary.recommendation());
        assertNull(applicantSummary.referenceComments());
    }

    @Test
    void keepsConfidentialResponseDetailsEmptyUntilAReferenceIsSubmitted() {
        Applicant applicant = new Applicant(
                UUID.randomUUID(), "APP-0002", "LOCAL", "Tendai", "Dube", "tendai@example.test");
        AdmissionCycle intake = new AdmissionCycle(
                UUID.randomUUID(), UUID.randomUUID(), "MBA-2027", "MBA 2027 Intake",
                SUBMITTED_AT.minusSeconds(3600), SUBMITTED_AT.plusSeconds(86400));
        Application application = new Application(
                intake, applicant, new ApplicationType("MBA", "MBA", true, true), "EMH-MBA-0002", false);
        ApplicantReferee referee = new ApplicantReferee(
                applicant, "Prof Rudo Ncube", "Prof", "UZ", "Director",
                "rudo.ncube@example.test", "+263772000000");
        ReflectionTestUtils.setField(referee, "id", UUID.randomUUID());
        ApplicationRefereeNomination nomination = new ApplicationRefereeNomination(
                application, referee, "UZ", "Director", "Data science", "Academic supervisor");
        ApplicantRefereeInvitation sentInvitation = new ApplicantRefereeInvitation(
                application, referee, nomination, "stored-hash", "token-hint",
                SUBMITTED_AT.minusSeconds(7200), SUBMITTED_AT.plusSeconds(86400), 1);

        var sentSummary = ApplicantApplicationWorkspaceViews.RefereeSummary.from(
                nomination, sentInvitation, true);
        var notSentSummary = ApplicantApplicationWorkspaceViews.RefereeSummary.from(
                nomination, null, true);

        assertEquals("SENT", sentSummary.invitationStatus());
        assertNull(sentSummary.referenceRelationshipToApplicant());
        assertNull(sentSummary.yearsKnown());
        assertNull(sentSummary.recommendation());
        assertNull(sentSummary.referenceComments());
        assertNull(sentSummary.referenceSubmittedAt());
        assertEquals("NOT_SENT", notSentSummary.invitationStatus());
        assertNull(notSentSummary.invitedAt());
        assertNull(notSentSummary.referenceSubmittedAt());
    }
}
