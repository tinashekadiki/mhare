package zw.ac.uz.emhare.admissions.application;

import zw.ac.uz.emhare.admissions.domain.model.AdmissionCycle;
import zw.ac.uz.emhare.admissions.domain.model.Applicant;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantReferee;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantRefereeInvitation;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationType;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantRefereeInvitationRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantRefereeInvitation.Recommendation;
import zw.ac.uz.emhare.admissions.application.command.SubmitReferenceCommand;
import zw.ac.uz.emhare.admissions.integration.AdmissionsIntegrationOutboxService;

/** @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class ApplicantRefereeInvitationServiceTest {

    @Mock
    private ApplicantRefereeInvitationRepository invitationRepository;

    @Mock
    private AdmissionsIntegrationOutboxService integrationOutboxService;

    private ApplicantRefereeInvitationService service;
    private Application application;
    private ApplicantReferee referee;
    private final Instant now = Instant.parse("2027-01-15T10:00:00Z");

    @BeforeEach
    void setUp() {
        service = new ApplicantRefereeInvitationService(
                invitationRepository,
                integrationOutboxService,
                Clock.fixed(now, ZoneOffset.UTC),
                "http://localhost:3001/");
        application = application();
        referee = new ApplicantReferee(
                application.getApplicant(), "Dr Tariro Dube", "Dr", "UZ", "Dean",
                "tariro.dube@example.test", "+263771000000");
        ReflectionTestUtils.setField(referee, "id", UUID.randomUUID());
    }

    @Test
    void issueInvitationStoresOnlyTokenHashAndQueuesSecureReferenceRequest() {
        when(invitationRepository.findAllByApplicationIdAndRefereeIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                application.getId(), referee.getId())).thenReturn(List.of());
        when(invitationRepository.saveAndFlush(any(ApplicantRefereeInvitation.class))).thenAnswer(invocation -> {
            ApplicantRefereeInvitation invitation = invocation.getArgument(0);
            ReflectionTestUtils.setField(invitation, "id", UUID.randomUUID());
            return invitation;
        });

        ApplicantRefereeInvitation invitation = service.issueInvitation(application, referee);

        ArgumentCaptor<String> responseUrl = ArgumentCaptor.forClass(String.class);
        verify(integrationOutboxService).enqueueRefereeReferenceRequest(
                eq(application), eq(referee), eq(invitation.getId()), responseUrl.capture(), eq(invitation.getExpiresAt()));
        String rawToken = responseUrl.getValue().substring(responseUrl.getValue().lastIndexOf('/') + 1);
        assertEquals(ApplicantRefereeInvitation.Status.SENT, invitation.getStatus());
        assertEquals(now.plusSeconds(30L * 24 * 60 * 60), invitation.getExpiresAt());
        assertEquals(64, invitation.getTokenHash().length());
        assertFalse(invitation.getTokenHash().contains(rawToken));
        assertNotEquals(rawToken, invitation.getTokenHash());
    }

    @Test
    void publicTokenCanBeOpenedAndSubmittedOnlyOnce() {
        ApplicantRefereeInvitation invitation = new ApplicantRefereeInvitation(
                application, referee, "stored-hash", "token-hint", now, now.plusSeconds(86400), 1);
        String rawToken = "public-reference-token";
        when(invitationRepository.findByTokenHashAndDeletedAtIsNull(any(String.class)))
                .thenReturn(Optional.of(invitation));

        var opened = service.openReferenceRequest(rawToken);
        var submitted = service.submitReference(rawToken, new SubmitReferenceCommand(
                "Line manager", 4, Recommendation.STRONGLY_RECOMMEND,
                "The applicant demonstrates strong judgement, leadership and readiness for postgraduate study.", true));

        assertEquals("OPENED", opened.status());
        assertEquals("SUBMITTED", submitted.status());
        assertEquals(now, submitted.submittedAt());
        assertEquals(Recommendation.STRONGLY_RECOMMEND, invitation.getRecommendation());
        assertThrows(IllegalStateException.class, () -> service.submitReference(rawToken, new SubmitReferenceCommand(
                "Line manager", 4, Recommendation.RECOMMEND, "A duplicate response must not be accepted.", true)));
    }

    @Test
    void countsOnlyTheLatestSubmittedResponseForEachRefereeInTheCurrentApplication() {
        ApplicantReferee secondReferee = new ApplicantReferee(
                application.getApplicant(), "Prof Rudo Ncube", "Prof", "UZ", "Director",
                "rudo.ncube@example.test", "+263772000000");
        ReflectionTestUtils.setField(secondReferee, "id", UUID.randomUUID());
        ApplicantRefereeInvitation previouslySubmitted = new ApplicantRefereeInvitation(
                application, referee, "old-hash", "old", now.minusSeconds(100), now.plusSeconds(86400), 1);
        previouslySubmitted.submit(
                "Line manager", 3, Recommendation.RECOMMEND,
                "This older response was replaced by a new current invitation.", true, now.minusSeconds(50));
        ApplicantRefereeInvitation replacement = new ApplicantRefereeInvitation(
                application, referee, "new-hash", "new", now, now.plusSeconds(86400), 2);
        ApplicantRefereeInvitation submittedCurrent = new ApplicantRefereeInvitation(
                application, secondReferee, "second-hash", "second", now, now.plusSeconds(86400), 1);
        submittedCurrent.submit(
                "Academic supervisor", 5, Recommendation.STRONGLY_RECOMMEND,
                "This is the current completed confidential response.", true, now);
        when(invitationRepository.findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(application.getId()))
                .thenReturn(List.of(replacement, previouslySubmitted, submittedCurrent));

        long submittedReferences = service.countCurrentSubmittedReferences(application.getId());

        assertEquals(1, submittedReferences);
    }

    private Application application() {
        AdmissionCycle cycle = new AdmissionCycle(
                UUID.randomUUID(), UUID.randomUUID(), "MBA-2027", "MBA 2027 Intake",
                now.minusSeconds(3600), now.plusSeconds(86400));
        ApplicationType type = new ApplicationType("POSTGRAD", "Masters and MBA", true, true);
        Applicant applicant = new Applicant(
                UUID.randomUUID(), "APP-0001", "LOCAL", "Nyasha", "Moyo", "nyasha@example.test");
        Application value = new Application(cycle, applicant, type, "EMH-MBA-0001", false);
        ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
        return value;
    }
}
