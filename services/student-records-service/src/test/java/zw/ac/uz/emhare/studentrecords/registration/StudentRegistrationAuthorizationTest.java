package zw.ac.uz.emhare.studentrecords.registration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.studentrecords.integration.CoreIdentityClient;
import zw.ac.uz.emhare.studentrecords.registration.api.controller.StudentRegistrationController;
import zw.ac.uz.emhare.studentrecords.registration.api.model.RegistrationRequests.*;
import zw.ac.uz.emhare.studentrecords.registration.domain.model.RegistrationType;

/**
 * Actual method-security interception with mocked service boundaries; not an HTTP test. @author
 * Tinashe K
 */
@SpringJUnitConfig(StudentRegistrationAuthorizationTest.SecurityConfiguration.class)
class StudentRegistrationAuthorizationTest {
  @Configuration
  @EnableMethodSecurity
  static class SecurityConfiguration {
    @Bean
    StudentRegistrationService registrationService() {
      return mock(StudentRegistrationService.class);
    }

    @Bean
    CoreIdentityClient identityClient() {
      return mock(CoreIdentityClient.class);
    }

    @Bean
    StudentRegistrationController registrationController(
        StudentRegistrationService service, CoreIdentityClient identity) {
      return new StudentRegistrationController(service, new EmhareCurrentUserResolver(), identity);
    }
  }

  @Autowired private StudentRegistrationController controller;
  @Autowired private StudentRegistrationService service;
  @Autowired private CoreIdentityClient identity;
  private final UUID localUser = UUID.randomUUID(), registrationId = UUID.randomUUID();
  private JwtAuthenticationToken authentication;

  @BeforeEach
  void resetBoundaries() {
    reset(service, identity);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @ValueSource(strings = {"system-admin", "registry-officer", "academic-admin"})
  void staffRegisterAllowsAuthorisedAcademicAndRegistryReaders(String role) {
    authenticate(role);
    when(service.list()).thenReturn(List.of());
    assertTrue(controller.list().isEmpty());
    verify(service).list();
  }

  @ParameterizedTest
  @ValueSource(strings = {"student", "finance-officer", "applicant"})
  void nonStaffCannotReadTheWholeRegistrationRegister(String role) {
    authenticate(role);
    assertThrows(AccessDeniedException.class, controller::list);
    verifyNoInteractions(service, identity);
  }

  @ParameterizedTest
  @CsvSource({
    "system-admin,submit",
    "registry-officer,submit",
    "system-admin,approve",
    "academic-admin,approve",
    "system-admin,confirm",
    "registry-officer,confirm",
    "system-admin,reject",
    "registry-officer,reject",
    "academic-admin,reject"
  })
  void permittedDecisionsResolveAuditIdentityAndForwardVersionAndEvidence(
      String role, String action) {
    authenticate(role);
    decide(action);
    switch (action) {
      case "submit" -> verify(service).submit(registrationId, 7, "Authority checked", localUser);
      case "approve" ->
          verify(service).approveAcademically(registrationId, 7, "Authority checked", localUser);
      case "confirm" -> verify(service).confirm(registrationId, 7, "Authority checked", localUser);
      case "reject" -> verify(service).reject(registrationId, 7, "Authority checked", localUser);
      default -> throw new AssertionError(action);
    }
    verifyNoInteractions(identity);
  }

  @ParameterizedTest
  @CsvSource({
    "academic-admin,submit",
    "registry-officer,approve",
    "academic-admin,confirm",
    "student,confirm",
    "student,reject",
    "finance-officer,reject"
  })
  void unauthorisedDecisionCannotReachTheService(String role, String action) {
    authenticate(role);
    assertThrows(AccessDeniedException.class, () -> decide(action));
    verifyNoInteractions(service, identity);
  }

  @ParameterizedTest
  @ValueSource(strings = {"system-admin", "registry-officer"})
  void staffCreationReturnsLocationAndForwardsGovernedRequest(String role) {
    authenticate(role);
    var request =
        new CreateRegistration(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            RegistrationType.NORMAL,
            Set.of());
    var summary = mock(RegistrationSummary.class);
    when(summary.id()).thenReturn(registrationId);
    when(service.create(request, localUser)).thenReturn(summary);
    var response = controller.create(authentication, request);
    assertEquals(201, response.getStatusCode().value());
    assertEquals(
        "/api/student-records/registrations/" + registrationId,
        response.getHeaders().getLocation().toString());
    assertSame(summary, response.getBody());
  }

  @Test
  void studentEndpointsUseSynchronizedLocalIdentityInsteadOfAcceptingAClientStudentId() {
    authenticate("student");
    UUID studentUser = UUID.randomUUID();
    when(identity.syncCurrentUserId(authentication)).thenReturn(studentUser);
    when(service.listForUser(studentUser)).thenReturn(List.of());
    assertTrue(controller.listMine(authentication).isEmpty());
    var request = new CreateOwnRegistration(UUID.randomUUID(), UUID.randomUUID(), 1, Set.of());
    var summary = mock(RegistrationSummary.class);
    when(summary.id()).thenReturn(registrationId);
    when(service.createForUser(request, studentUser)).thenReturn(summary);
    assertEquals(201, controller.createMine(authentication, request).getStatusCode().value());
    controller.submitMine(authentication, registrationId, new SubmitOwnRegistration(8, true));
    verify(service).submitForUser(registrationId, 8, studentUser);
    verify(identity, times(3)).syncCurrentUserId(authentication);
  }

  @ParameterizedTest
  @ValueSource(strings = {"registry-officer", "academic-admin", "applicant"})
  void nonStudentsCannotUseSelfServiceOwnershipEndpoints(String role) {
    authenticate(role);
    assertThrows(AccessDeniedException.class, () -> controller.listMine(authentication));
    assertThrows(
        AccessDeniedException.class,
        () ->
            controller.createMine(
                authentication,
                new CreateOwnRegistration(UUID.randomUUID(), UUID.randomUUID(), 1, Set.of())));
    assertThrows(
        AccessDeniedException.class,
        () ->
            controller.submitMine(
                authentication, registrationId, new SubmitOwnRegistration(0, true)));
    verifyNoInteractions(service, identity);
  }

  private void decide(String action) {
    var request = new RegistrationDecision(7, "Authority checked");
    switch (action) {
      case "submit" -> controller.submit(authentication, registrationId, request);
      case "approve" -> controller.approveAcademically(authentication, registrationId, request);
      case "confirm" -> controller.confirm(authentication, registrationId, request);
      case "reject" -> controller.reject(authentication, registrationId, request);
      default -> throw new AssertionError(action);
    }
  }

  private void authenticate(String role) {
    Jwt jwt =
        Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject(UUID.randomUUID().toString())
            .claim("emhare_user_id", localUser.toString())
            .build();
    authentication =
        new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
