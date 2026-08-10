package zw.ac.uz.emhare.admissions.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
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
import zw.ac.uz.emhare.admissions.application.AdmissionsApplicationService;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationConfigurationService;
import zw.ac.uz.emhare.admissions.application.ApplicationSummary;
import zw.ac.uz.emhare.admissions.application.CreateApplicationCommand;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreCurrentUserProfile;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreUserSummary;
import zw.ac.uz.emhare.admissions.security.ApplicantRegistrationIdentityResolver;

@ExtendWith(MockitoExtension.class)
class AdmissionsApplicationControllerTest {

    @Mock
    private AdmissionsApplicationService admissionsApplicationService;

    @Mock
    private CoreIdentityClient coreIdentityClient;

    @Mock
    private ApplicantApplicationConfigurationService applicantApplicationConfigurationService;

    private AdmissionsApplicationController controller;

    @BeforeEach
    void setUp() {
        controller = new AdmissionsApplicationController(
                admissionsApplicationService,
                applicantApplicationConfigurationService,
                coreIdentityClient,
                new ApplicantRegistrationIdentityResolver());
    }

    @Test
    void startApplication_shouldDeriveApplicantIdentityFromAuthenticatedRegistrationClaims() {
        UUID coreUserId = UUID.randomUUID();
        UUID keycloakUserId = UUID.randomUUID();
        UUID admissionCycleId = UUID.randomUUID();
        UUID applicationTypeId = UUID.randomUUID();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt());
        CoreCurrentUserProfile profile = new CoreCurrentUserProfile(
                new CoreUserSummary(
                        coreUserId,
                        keycloakUserId,
                        "applicant",
                        "trusted@example.test",
                "Applicant User",
                "ACTIVE"),
                List.of());
        ApplicationSummary expectedSummary = new ApplicationSummary(
                UUID.randomUUID(),
                "EMH-2027-0001",
                "APP-0001",
                "Applicant User",
                UUID.randomUUID(),
                "2027-AUG",
                UUID.randomUUID(),
                "Undergraduate",
                "DRAFT",
                true,
                "PENDING",
                null,
                false,
                false,
                null,
                null,
                "NOT_CONFIRMED",
                null,
                null,
                null,
                null,
                List.of());

        when(coreIdentityClient.syncCurrentUser(authentication)).thenReturn(profile);
        when(admissionsApplicationService.startApplication(org.mockito.ArgumentMatchers.any(CreateApplicationCommand.class)))
                .thenReturn(expectedSummary);

        ApplicationSummary actualSummary = controller.startApplication(authentication, new CreateApplicationRequest(
                "LOCAL",
                admissionCycleId,
                applicationTypeId,
                List.of()));

        ArgumentCaptor<CreateApplicationCommand> commandCaptor = ArgumentCaptor.forClass(CreateApplicationCommand.class);
        verify(admissionsApplicationService).startApplication(commandCaptor.capture());
        CreateApplicationCommand command = commandCaptor.getValue();
        assertEquals(expectedSummary, actualSummary);
        assertEquals(coreUserId, command.applicantUserId());
        assertEquals(keycloakUserId, command.applicantKeycloakUserId());
        assertEquals("trusted@example.test", command.primaryEmail());
        assertEquals("Registered", command.firstName());
        assertEquals("Applicant", command.lastName());
        assertTrue(command.programmeIds().isEmpty());
    }

    @Test
    void createApplicationRequest_shouldAllowProgrammeChoicesToBeCapturedInTheWorkspace() {
        CreateApplicationRequest request = new CreateApplicationRequest(
                "LOCAL",
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of());

        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(validatorFactory.getValidator().validate(request).isEmpty());
        }
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
                        "preferred_username", "applicant",
                        "given_name", "Registered",
                        "family_name", "Applicant"));
    }
}
