package zw.ac.uz.emhare.admissions.api.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import zw.ac.uz.emhare.admissions.application.AdmissionsRollingWorkflowService;
import zw.ac.uz.emhare.admissions.application.AdmissionsWorkItemService;
import zw.ac.uz.emhare.admissions.application.DirectAdmissionOfferService;
import zw.ac.uz.emhare.admissions.application.DirectAdmissionOfferService.DocumentGenerationResult;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreCurrentUserProfile;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreUserSummary;

/** @author Tinashe K */
class AdmissionsRollingWorkflowControllerOfferTest {
    @Test
    void forwardsTheAuthenticatedBearerTokenToGovernedGeneration() {
        CoreIdentityClient core = mock(CoreIdentityClient.class);
        DirectAdmissionOfferService offers = mock(DirectAdmissionOfferService.class);
        UUID offerId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        JwtAuthenticationToken authentication = jwtAuthentication("signed-token");
        CoreCurrentUserProfile profile = profile(actorId);
        DocumentGenerationResult expected = mock(DocumentGenerationResult.class);
        when(core.syncCurrentUser(authentication)).thenReturn(profile);
        when(offers.generate(offerId, actorId, "Bearer signed-token")).thenReturn(expected);
        AdmissionsRollingWorkflowController controller = controller(core, offers);

        assertSame(expected, controller.generateDocument(authentication, offerId));
        verify(offers).generate(offerId, actorId, "Bearer signed-token");
    }

    @Test
    void rejectsNonJwtGenerationContexts() {
        CoreIdentityClient core = mock(CoreIdentityClient.class);
        DirectAdmissionOfferService offers = mock(DirectAdmissionOfferService.class);
        Authentication authentication = mock(Authentication.class);
        when(core.syncCurrentUser(authentication)).thenReturn(profile(UUID.randomUUID()));

        assertThrows(IllegalStateException.class,
                () -> controller(core, offers).generateDocument(authentication, UUID.randomUUID()));
    }

    private AdmissionsRollingWorkflowController controller(CoreIdentityClient core,
            DirectAdmissionOfferService offers) {
        return new AdmissionsRollingWorkflowController(mock(AdmissionsWorkItemService.class),
                mock(AdmissionsRollingWorkflowService.class), core, offers);
    }

    private JwtAuthenticationToken jwtAuthentication(String tokenValue) {
        Jwt jwt = Jwt.withTokenValue(tokenValue).header("alg", "none").subject("user")
                .issuedAt(Instant.parse("2028-01-10T08:00:00Z"))
                .expiresAt(Instant.parse("2028-01-10T09:00:00Z")).build();
        return new JwtAuthenticationToken(jwt);
    }

    private CoreCurrentUserProfile profile(UUID actorId) {
        return new CoreCurrentUserProfile(new CoreUserSummary(actorId, UUID.randomUUID(), "user",
                "user@example.test", "User", "ACTIVE"), List.of(), List.of(), List.of(), true);
    }
}
