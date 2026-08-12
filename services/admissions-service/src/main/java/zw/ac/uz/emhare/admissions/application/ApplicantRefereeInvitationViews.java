package zw.ac.uz.emhare.admissions.application;

import java.time.Instant;

/** Public referee-response contracts expose only the minimum application context. @author Tinashe K */
public final class ApplicantRefereeInvitationViews {
    private ApplicantRefereeInvitationViews() {
    }

    public record PublicReferenceRequest(
            String applicantName,
            String applicationNumber,
            String applicationTypeName,
            String refereeName,
            String refereeOrganisation,
            String status,
            Instant expiresAt,
            Instant submittedAt) {
    }
}
