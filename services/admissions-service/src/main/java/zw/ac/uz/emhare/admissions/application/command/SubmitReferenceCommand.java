package zw.ac.uz.emhare.admissions.application.command;

import zw.ac.uz.emhare.admissions.domain.model.ApplicantRefereeInvitation;

/** Internal referee-submission use-case input. @author Tinashe K */
public record SubmitReferenceCommand(
        String relationshipToApplicant,
        int yearsKnown,
        ApplicantRefereeInvitation.Recommendation recommendation,
        String comments,
        boolean declarationAccepted) {
}
