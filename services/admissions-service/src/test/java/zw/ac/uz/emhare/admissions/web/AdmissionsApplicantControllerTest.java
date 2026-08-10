package zw.ac.uz.emhare.admissions.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import zw.ac.uz.emhare.admissions.application.AdmissionsApplicantService;
import zw.ac.uz.emhare.admissions.application.ApplicantViews.ApplicantDetails;
import zw.ac.uz.emhare.admissions.application.ApplicantViews.ApplicantProfile;
import zw.ac.uz.emhare.admissions.application.UpdateApplicantProfileCommand;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreCurrentUserProfile;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreUserSummary;

@ExtendWith(MockitoExtension.class)
class AdmissionsApplicantControllerTest {

    @Mock
    private AdmissionsApplicantService admissionsApplicantService;

    @Mock
    private CoreIdentityClient coreIdentityClient;

    @Mock
    private Authentication authentication;

    @Test
    void correctApplicant_shouldSynchronizeActorAndMapGovernedCorrectionCommand() {
        AdmissionsApplicantController controller = new AdmissionsApplicantController(
                admissionsApplicantService,
                coreIdentityClient);
        UUID applicantId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UpdateApplicantProfileRequest request = new UpdateApplicantProfileRequest(
                "LOCAL", "MS", "Nyasha", null, "Moyo", LocalDate.parse("2003-04-12"),
                "FEMALE", "SINGLE", "63-123456-A-78", null, UUID.randomUUID(), UUID.randomUUID(),
                "Harare", "NONE", null, "SELF", "nyasha@example.test", "+263771234567",
                null, "1 College Road, Harare", "Corrected against verified identity document.", 2);
        ApplicantProfile profile = new ApplicantProfile(
                applicantId, UUID.randomUUID(), "APP-0001", "LOCAL", "MS", "Nyasha", null, "Moyo",
                request.dateOfBirth(), "FEMALE", "SINGLE", request.nationalIdNumber(), null,
                request.countryId(), request.nationalityCountryId(), "Harare", "NONE", null, "SELF",
                request.primaryEmail(), request.primaryPhone(), null, request.residentialAddress(),
                100, List.of(), null, null, 3);
        ApplicantDetails expectedDetails = new ApplicantDetails(profile, List.of());
        when(coreIdentityClient.syncCurrentUser(authentication)).thenReturn(new CoreCurrentUserProfile(
                new CoreUserSummary(actorUserId, UUID.randomUUID(), "reviewer", "reviewer@example.test", "Reviewer", "ACTIVE"),
                List.of()));
        when(admissionsApplicantService.correctApplicant(
                org.mockito.ArgumentMatchers.eq(applicantId),
                org.mockito.ArgumentMatchers.any(UpdateApplicantProfileCommand.class)))
                .thenReturn(expectedDetails);

        ApplicantDetails actualDetails = controller.correctApplicant(authentication, applicantId, request);

        assertEquals(expectedDetails, actualDetails);
        verify(coreIdentityClient).syncCurrentUser(authentication);
        ArgumentCaptor<UpdateApplicantProfileCommand> commandCaptor = ArgumentCaptor.forClass(UpdateApplicantProfileCommand.class);
        verify(admissionsApplicantService).correctApplicant(org.mockito.ArgumentMatchers.eq(applicantId), commandCaptor.capture());
        assertEquals("Corrected against verified identity document.", commandCaptor.getValue().changeReason());
        assertEquals(2, commandCaptor.getValue().expectedVersion());
    }
}
