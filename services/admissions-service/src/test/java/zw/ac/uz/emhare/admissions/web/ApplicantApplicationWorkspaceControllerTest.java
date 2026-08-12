package zw.ac.uz.emhare.admissions.web;

import zw.ac.uz.emhare.admissions.domain.model.Applicant;

import zw.ac.uz.emhare.admissions.application.command.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceService;
import zw.ac.uz.emhare.admissions.application.command.CreateQualificationResultCommand;
import zw.ac.uz.emhare.admissions.application.command.UpdateApplicantProfileCommand;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreCurrentUserProfile;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreUserSummary;
import zw.ac.uz.emhare.admissions.security.ApplicantRegistrationIdentityResolver;
import zw.ac.uz.emhare.admissions.api.model.ApplicantWorkspaceRequests.SaveOwnProfileRequest;
import zw.ac.uz.emhare.admissions.api.model.ApplicantWorkspaceRequests.AddQualificationResultItemRequest;
import zw.ac.uz.emhare.admissions.api.model.ApplicantWorkspaceRequests.AddQualificationResultsRequest;
import zw.ac.uz.emhare.admissions.api.controller.ApplicantApplicationWorkspaceController;

/** Verifies applicant-owned profile fields that are governed by registration identity. @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class ApplicantApplicationWorkspaceControllerTest {

    @Mock
    private ApplicantApplicationWorkspaceService workspaceService;

    @Mock
    private CoreIdentityClient coreIdentityClient;

    private ApplicantApplicationWorkspaceController controller;

    @BeforeEach
    void setUp() {
        controller = new ApplicantApplicationWorkspaceController(
                workspaceService,
                coreIdentityClient,
                new ApplicantRegistrationIdentityResolver());
    }

    @Test
    void saveProfile_shouldUseRegisteredNamesInsteadOfApplicantRequestFields() {
        UUID applicationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt());
        when(coreIdentityClient.syncCurrentUser(authentication)).thenReturn(new CoreCurrentUserProfile(
                new CoreUserSummary(
                        userId,
                        UUID.randomUUID(),
                        "registered.applicant",
                        "registered@example.test",
                        "Registered Applicant",
                        "ACTIVE"),
                List.of()));
        when(workspaceService.saveOwnProfile(eq(applicationId), eq(userId), any(UpdateApplicantProfileCommand.class)))
                .thenReturn(null);

        controller.saveProfile(authentication, applicationId, new SaveOwnProfileRequest(
                "LOCAL",
                "Ms",
                "Middle",
                LocalDate.of(2000, 1, 1),
                "FEMALE",
                "SINGLE",
                "63-123456A78",
                null,
                null,
                null,
                "Harare",
                "NONE",
                null,
                null,
                "registered@example.test",
                "+263771234567",
                null,
                "Harare",
                4));

        ArgumentCaptor<UpdateApplicantProfileCommand> commandCaptor =
                ArgumentCaptor.forClass(UpdateApplicantProfileCommand.class);
        verify(workspaceService).saveOwnProfile(eq(applicationId), eq(userId), commandCaptor.capture());
        assertEquals("Registered", commandCaptor.getValue().firstName());
        assertEquals("Applicant", commandCaptor.getValue().lastName());
    }

    @Test
    void addQualificationResults_shouldMapEveryBatchRowToOneTransactionalServiceCall() {
        UUID applicationId = UUID.randomUUID();
        UUID sittingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID englishSubjectId = UUID.randomUUID();
        UUID mathematicsSubjectId = UUID.randomUUID();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt());
        when(coreIdentityClient.syncCurrentUser(authentication)).thenReturn(new CoreCurrentUserProfile(
                new CoreUserSummary(
                        userId,
                        UUID.randomUUID(),
                        "registered.applicant",
                        "registered@example.test",
                        "Registered Applicant",
                        "ACTIVE"),
                List.of()));
        when(workspaceService.addQualificationResults(eq(applicationId), eq(userId), eq(sittingId), any()))
                .thenReturn(null);

        controller.addQualificationResults(authentication, applicationId, sittingId, new AddQualificationResultsRequest(List.of(
                new AddQualificationResultItemRequest(englishSubjectId, "A", false),
                new AddQualificationResultItemRequest(mathematicsSubjectId, "B", true))));

        verify(workspaceService).addQualificationResults(
                applicationId,
                userId,
                sittingId,
                List.of(
                        new CreateQualificationResultCommand(englishSubjectId, "A", false),
                        new CreateQualificationResultCommand(mathematicsSubjectId, "B", true)));
    }

    private Jwt jwt() {
        Instant now = Instant.now();
        return new Jwt(
                "token",
                now,
                now.plusSeconds(300),
                Map.of("alg", "none"),
                Map.of(
                        "sub", UUID.randomUUID().toString(),
                        "preferred_username", "registered.applicant",
                        "given_name", "Registered",
                        "family_name", "Applicant"));
    }
}
