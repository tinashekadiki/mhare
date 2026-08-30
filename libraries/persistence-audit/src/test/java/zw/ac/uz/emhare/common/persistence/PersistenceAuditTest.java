package zw.ac.uz.emhare.common.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.MDC;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * @author Tinashe K
 */
class PersistenceAuditTest {
  private static final UUID LOCAL_USER = new UUID(0, 1), SUBJECT_USER = new UUID(0, 2);
  private final String previousServiceName = EmhareRevisionContext.getServiceName();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
    EmhareRevisionContext.clearRequestMetadata();
    EmhareRevisionContext.setServiceName(previousServiceName);
    MDC.clear();
  }

  @ParameterizedTest
  @CsvSource({
    "true,valid,valid,local",
    "false,valid,valid,local",
    "true,invalid,valid,subject",
    "false,missing,valid,subject",
    "true,missing,invalid,none",
    "false,invalid,missing,none"
  })
  void resolvesLocalIdentityBeforeExternalSubjectForBothAuthenticationShapes(
      boolean token, String local, String subject, String expected) {
    var builder =
        Jwt.withTokenValue("test-token").header("alg", "RS256").claim("iss", "test-issuer");
    if (!local.equals("missing"))
      builder.claim("emhare_user_id", local.equals("valid") ? LOCAL_USER.toString() : "not-a-uuid");
    if (!subject.equals("missing"))
      builder.subject(subject.equals("valid") ? SUBJECT_USER.toString() : "external-user");
    Jwt jwt = builder.build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            token
                ? new JwtAuthenticationToken(jwt)
                : new UsernamePasswordAuthenticationToken(jwt, "unused", List.of()));
    UUID expectedUser =
        expected.equals("local") ? LOCAL_USER : expected.equals("subject") ? SUBJECT_USER : null;
    assertEquals(expectedUser, new CurrentUserAuditorAware().getCurrentAuditor().orElse(null));
    EmhareRevisionEntity revision = new EmhareRevisionEntity();
    new EmhareRevisionListener().newRevision(revision);
    assertEquals(expectedUser, revision.getActorUserId());
  }

  @Test
  void anonymousAndNonJwtWorkDoNotInventAnAuditActor() {
    assertTrue(new CurrentUserAuditorAware().getCurrentAuditor().isEmpty());
    EmhareRevisionEntity revision = new EmhareRevisionEntity();
    new EmhareRevisionListener().newRevision(revision);
    assertNull(revision.getActorUserId());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("system", "unused"));
    assertTrue(new CurrentUserAuditorAware().getCurrentAuditor().isEmpty());
    new EmhareRevisionListener().newRevision(revision);
    assertNull(revision.getActorUserId());
  }

  @Test
  void propagatesRequestMetadataAndClearsItBetweenRequests() throws Exception {
    new RevisionMetadataInitializer(
            new MockEnvironment().withProperty("spring.application.name", "finance-service"))
        .run(null);
    EmhareRevisionContext.setServiceName(null);
    EmhareRevisionContext.setServiceName(" ");
    assertEquals("finance-service", EmhareRevisionContext.getServiceName());
    MDC.put("correlationId", "logging-context");
    assertEquals("logging-context", EmhareRevisionContext.getCorrelationId().orElseThrow());
    EmhareRevisionContext.setRequestMetadata("request-context", "Approved payment waiver");
    EmhareRevisionEntity revision = new EmhareRevisionEntity();
    new EmhareRevisionListener().newRevision(revision);
    assertEquals("finance-service", revision.getServiceName());
    assertEquals("request-context", revision.getCorrelationId());
    assertEquals("Approved payment waiver", revision.getReason());
    Thread isolatedRequest =
        new Thread(
            () -> {
              assertTrue(EmhareRevisionContext.getReason().isEmpty());
              assertTrue(EmhareRevisionContext.getCorrelationId().isEmpty());
            });
    java.util.concurrent.atomic.AtomicReference<Throwable> failure =
        new java.util.concurrent.atomic.AtomicReference<>();
    isolatedRequest.setUncaughtExceptionHandler((thread, error) -> failure.set(error));
    isolatedRequest.start();
    isolatedRequest.join();
    assertNull(failure.get());
    EmhareRevisionContext.clearRequestMetadata();
    MDC.clear();
    new EmhareRevisionListener().newRevision(revision);
    assertNull(revision.getReason());
    assertNull(revision.getCorrelationId());
  }

  @Test
  void softDeletionRetainsCreationMetadataAndRecordsItsActorAndTime() {
    AuditableEntity entity = new AuditableEntity() {};
    Instant created = Instant.parse("2026-08-01T00:00:00Z");
    ReflectionTestUtils.setField(entity, "id", new UUID(0, 10));
    ReflectionTestUtils.setField(entity, "createdAt", created);
    ReflectionTestUtils.setField(entity, "updatedAt", created);
    ReflectionTestUtils.setField(entity, "createdByUserId", LOCAL_USER);
    ReflectionTestUtils.setField(entity, "modifiedByUserId", LOCAL_USER);
    ReflectionTestUtils.setField(entity, "version", 3L);
    assertFalse(entity.isDeleted());
    assertNull(entity.getDeletedAt());
    assertNull(entity.getDeletedByUserId());
    Instant before = Instant.now();
    entity.markDeleted(SUBJECT_USER);
    assertTrue(entity.isDeleted());
    assertFalse(entity.getDeletedAt().isBefore(before));
    assertFalse(entity.getDeletedAt().isAfter(Instant.now()));
    assertEquals(SUBJECT_USER, entity.getDeletedByUserId());
    assertEquals(new UUID(0, 10), entity.getId());
    assertEquals(created, entity.getCreatedAt());
    assertEquals(created, entity.getUpdatedAt());
    assertEquals(LOCAL_USER, entity.getCreatedByUserId());
    assertEquals(LOCAL_USER, entity.getModifiedByUserId());
    assertEquals(3, entity.getVersion());
  }
}
