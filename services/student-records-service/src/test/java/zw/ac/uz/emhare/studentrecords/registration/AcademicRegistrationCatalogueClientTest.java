package zw.ac.uz.emhare.studentrecords.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import zw.ac.uz.emhare.studentrecords.integration.http.AcademicSetupRegistrationHttpService;
import zw.ac.uz.emhare.studentrecords.registration.AcademicRegistrationCatalogueClient.RegistrationCatalogue;

/**
 * @author Tinashe K
 */
class AcademicRegistrationCatalogueClientTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void forwardsCurrentBearerTokenAcrossTheAcademicSetupBoundary() {
    AcademicSetupRegistrationHttpService httpService =
        mock(AcademicSetupRegistrationHttpService.class);
    AcademicRegistrationCatalogueClient client =
        new AcademicRegistrationCatalogueClient(httpService);
    UUID academicPeriodId = UUID.randomUUID();
    UUID programmeVersionId = UUID.randomUUID();
    RegistrationCatalogue catalogue = catalogue(academicPeriodId, programmeVersionId);
    when(httpService.getRegistrationCatalogue(
            "Bearer student-access-token", academicPeriodId, programmeVersionId, 4))
        .thenReturn(catalogue);
    Jwt jwt =
        new Jwt(
            "student-access-token",
            Instant.parse("2026-08-17T00:00:00Z"),
            Instant.parse("2026-08-17T01:00:00Z"),
            Map.of("alg", "none"),
            Map.of("sub", UUID.randomUUID().toString()));
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

    RegistrationCatalogue resolved =
        client.getRegistrationCatalogue(academicPeriodId, programmeVersionId, 4);

    assertThat(resolved).isSameAs(catalogue);
    verify(httpService)
        .getRegistrationCatalogue(
            "Bearer student-access-token", academicPeriodId, programmeVersionId, 4);
  }

  private RegistrationCatalogue catalogue(UUID academicPeriodId, UUID programmeVersionId) {
    return new RegistrationCatalogue(
        academicPeriodId,
        "2026-S1",
        "Semester 1",
        LocalDate.parse("2026-01-01"),
        LocalDate.parse("2026-06-30"),
        programmeVersionId,
        UUID.randomUUID(),
        "BSC",
        "Bachelor of Science",
        "2026.1",
        UUID.randomUUID(),
        "SCI",
        "Faculty of Science",
        UUID.randomUUID(),
        "UG",
        "Undergraduate",
        4,
        List.of());
  }
}
