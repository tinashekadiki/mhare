package zw.ac.uz.emhare.admissions.application;

import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zw.ac.uz.emhare.admissions.application.ApplicantRefereeInvitationViews.PublicReferenceRequest;
import zw.ac.uz.emhare.admissions.application.ApplicantRefereeInvitationViews.SubmitReferenceCommand;
import zw.ac.uz.emhare.admissions.integration.AdmissionsIntegrationOutboxService;

/** Creates one-time referee links and records confidential postgraduate references. @author Tinashe K */
@Service
public class ApplicantRefereeInvitationService {

    private static final Duration INVITATION_VALIDITY = Duration.ofDays(30);

    private final ApplicantRefereeInvitationRepository invitationRepository;
    private final AdmissionsIntegrationOutboxService integrationOutboxService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String applicantPortalPublicUrl;

    public ApplicantRefereeInvitationService(
            ApplicantRefereeInvitationRepository invitationRepository,
            AdmissionsIntegrationOutboxService integrationOutboxService,
            Clock clock,
            @Value("${emhare.applicant-portal.public-url:http://localhost:3001}") String applicantPortalPublicUrl) {
        this.invitationRepository = invitationRepository;
        this.integrationOutboxService = integrationOutboxService;
        this.clock = clock;
        this.applicantPortalPublicUrl = stripTrailingSlash(applicantPortalPublicUrl);
    }

    @Transactional
    public ApplicantRefereeInvitation issueInvitation(Application application, ApplicantReferee referee) {
        List<ApplicantRefereeInvitation> previousInvitations = invitationRepository
                .findAllByApplicationIdAndRefereeIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                        application.getId(), referee.getId());
        previousInvitations.forEach(ApplicantRefereeInvitation::revoke);
        int sendCount = previousInvitations.stream()
                .mapToInt(ApplicantRefereeInvitation::getSendCount)
                .max()
                .orElse(0) + 1;

        Instant sentAt = clock.instant();
        Instant expiresAt = sentAt.plus(INVITATION_VALIDITY);
        String token = newToken();
        ApplicantRefereeInvitation invitation = invitationRepository.saveAndFlush(
                new ApplicantRefereeInvitation(
                        application,
                        referee,
                        hash(token),
                        token.substring(0, Math.min(12, token.length())),
                        sentAt,
                        expiresAt,
                        sendCount));
        integrationOutboxService.enqueueRefereeReferenceRequest(
                application,
                referee,
                invitation.getId(),
                applicantPortalPublicUrl + "/references/" + token,
                expiresAt);
        return invitation;
    }

    @Transactional
    public PublicReferenceRequest openReferenceRequest(String token) {
        ApplicantRefereeInvitation invitation = requireInvitation(token);
        Instant now = clock.instant();
        invitation.expire(now);
        if (invitation.getStatus() == ApplicantRefereeInvitation.Status.EXPIRED) {
            throw new IllegalStateException("This reference invitation has expired. Ask the applicant to send a new invitation.");
        }
        if (invitation.getStatus() == ApplicantRefereeInvitation.Status.REVOKED) {
            throw new IllegalStateException("This reference invitation is no longer active. Ask the applicant to send a new invitation.");
        }
        invitation.markOpened(now);
        return publicRequest(invitation);
    }

    @Transactional
    public PublicReferenceRequest submitReference(String token, SubmitReferenceCommand command) {
        ApplicantRefereeInvitation invitation = requireInvitation(token);
        Instant now = clock.instant();
        invitation.expire(now);
        invitation.submit(
                command.relationshipToApplicant(),
                command.yearsKnown(),
                command.recommendation(),
                command.comments(),
                command.declarationAccepted(),
                now);
        return publicRequest(invitation);
    }

    @Transactional
    public void revokeInvitations(UUID applicationId, UUID refereeId) {
        invitationRepository.findAllByApplicationIdAndRefereeIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                        applicationId, refereeId)
                .forEach(ApplicantRefereeInvitation::revoke);
    }

    @Transactional
    public Map<UUID, ApplicantRefereeInvitation> latestInvitations(UUID applicationId) {
        Map<UUID, ApplicantRefereeInvitation> latestByRefereeId = new LinkedHashMap<>();
        invitationRepository.findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(applicationId)
                .forEach(invitation -> latestByRefereeId.putIfAbsent(invitation.getReferee().getId(), invitation));
        return latestByRefereeId;
    }

    private ApplicantRefereeInvitation requireInvitation(String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Reference invitation token is required.");
        return invitationRepository.findByTokenHashAndDeletedAtIsNull(hash(token.trim()))
                .orElseThrow(() -> new IllegalArgumentException("Reference invitation was not found."));
    }

    private PublicReferenceRequest publicRequest(ApplicantRefereeInvitation invitation) {
        Application application = invitation.getApplication();
        Applicant applicant = application.getApplicant();
        ApplicantReferee referee = invitation.getReferee();
        return new PublicReferenceRequest(
                applicant.getDisplayName(),
                application.getApplicationNumber(),
                application.getApplicationType().getName(),
                referee.getFullName(),
                referee.getOrganisation(),
                invitation.getStatus().name(),
                invitation.getExpiresAt(),
                invitation.getSubmittedAt());
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private static String stripTrailingSlash(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized.isBlank() ? "http://localhost:3001" : normalized;
    }
}
