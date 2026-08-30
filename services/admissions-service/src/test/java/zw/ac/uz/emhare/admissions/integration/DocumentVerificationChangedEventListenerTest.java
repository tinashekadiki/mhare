package zw.ac.uz.emhare.admissions.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.application.AdmissionsDocumentService;
import zw.ac.uz.emhare.admissions.application.AdmissionsRollingWorkflowService;
import zw.ac.uz.emhare.common.messaging.DocumentVerificationChangedEvent;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;

/** Cross-service verification event schema, idempotence and orchestration. @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class DocumentVerificationChangedEventListenerTest {
  private static final Instant NOW = Instant.parse("2026-08-12T08:00:00Z");
  @Mock private AdmissionsIntegrationInbox inbox;
  @Mock private AdmissionsDocumentService documents;
  @Mock private AdmissionsRollingWorkflowService workflow;
  private final ObjectMapper mapper = new ObjectMapper();
  private final UUID eventId = UUID.randomUUID();
  private final UUID ownerId = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private DocumentVerificationChangedEventListener listener;

  @BeforeEach
  void setUp() {
    listener =
        new DocumentVerificationChangedEventListener(
            inbox, documents, workflow, mapper, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @ParameterizedTest
  @CsvSource({
    "APPLICATION,VERIFIED",
    "APPLICATION,REJECTED",
    "APPLICANT,VERIFIED",
    "STUDENT,VERIFIED"
  })
  void claimedDocumentEvidenceAdvancesOnlyApplicationOwners(String ownerType, String status) {
    Map<String, Object> payload = payload();
    payload.put("ownerType", ownerType);
    payload.put("verificationStatus", status);
    if (status.equals("REJECTED")) payload.put("rejectionReason", "Identity details do not match.");
    String json = mapper.writeValueAsString(payload);
    when(inbox.claim(
            eventId,
            EmhareMessagingTopology.DOCUMENT_VERIFICATION_CHANGED_EVENT,
            "documents-reporting-service",
            json,
            NOW))
        .thenReturn(true);
    listener.receive(message(json));
    var order = inOrder(inbox, documents, workflow);
    order
        .verify(inbox)
        .claim(
            eventId,
            EmhareMessagingTopology.DOCUMENT_VERIFICATION_CHANGED_EVENT,
            "documents-reporting-service",
            json,
            NOW);
    order
        .verify(documents)
        .applyVerification(mapper.readValue(json, DocumentVerificationChangedEvent.class));
    if (ownerType.equals("APPLICATION")) order.verify(workflow).advance(ownerId, actor);
    else verifyNoInteractions(workflow);
    order.verify(inbox).markProcessed(eventId, NOW);
  }

  @Test
  void duplicateDeliveryDoesNotRepeatVerificationOrWorkflow() {
    listener.receive(message(mapper.writeValueAsString(payload())));
    verifyNoInteractions(documents, workflow);
    verify(inbox, never()).markProcessed(any(), any());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "eventId",
        "documentId",
        "ownerId",
        "ownerType",
        "documentTypeCode",
        "verificationStatus",
        "verifiedByUserId",
        "verifiedAt"
      })
  void requiredEventFieldsMustExistBeforeInboxClaim(String missingField) {
    Map<String, Object> payload = payload();
    payload.remove(missingField);
    assertThatThrownBy(() -> listener.receive(message(mapper.writeValueAsString(payload))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("contract");
    verifyNoInteractions(inbox, documents, workflow);
  }

  @ParameterizedTest
  @CsvSource({"schemaVersion,0", "schemaVersion,2", "documentVersion,0", "documentVersion,-1"})
  void unsupportedSchemaOrNonpositiveDocumentVersionIsRejected(String field, long invalidValue) {
    Map<String, Object> payload = payload();
    payload.put(field, invalidValue);
    assertThatThrownBy(() -> listener.receive(message(mapper.writeValueAsString(payload))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("contract");
    verifyNoInteractions(inbox);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PENDING", "UNKNOWN", "verified"})
  void eventStatusMustBeCanonicalFinalVerificationDecision(String status) {
    Map<String, Object> payload = payload();
    payload.put("verificationStatus", status);
    assertThatThrownBy(() -> listener.receive(message(mapper.writeValueAsString(payload))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("contract");
    verifyNoInteractions(inbox);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void rejectedDocumentMustIncludeReasonEvidence(String reason) {
    Map<String, Object> payload = payload();
    payload.put("verificationStatus", "REJECTED");
    payload.put("rejectionReason", reason);
    assertThatThrownBy(() -> listener.receive(message(mapper.writeValueAsString(payload))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rejection evidence");
    verifyNoInteractions(inbox);
  }

  @ParameterizedTest
  @ValueSource(strings = {"{broken-json", "[]", "{\"eventId\":\"not-a-uuid\"}"})
  void malformedJsonFailsBeforeClaim(String json) {
    assertThatThrownBy(() -> listener.receive(message(json)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("payload is invalid");
    verifyNoInteractions(inbox);
  }

  @ParameterizedTest
  @ValueSource(strings = {"verification", "workflow"})
  void downstreamFailureLeavesClaimUnprocessedForTransactionalRetry(String failure) {
    when(inbox.claim(eq(eventId), anyString(), anyString(), anyString(), eq(NOW))).thenReturn(true);
    if (failure.equals("verification"))
      doThrow(new IllegalStateException("Dependency failed"))
          .when(documents)
          .applyVerification(any());
    else
      doThrow(new IllegalStateException("Dependency failed"))
          .when(workflow)
          .advance(ownerId, actor);
    assertThatThrownBy(() -> listener.receive(message(mapper.writeValueAsString(payload()))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Dependency failed");
    verify(inbox, never()).markProcessed(any(), any());
  }

  private Map<String, Object> payload() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventId", eventId);
    payload.put("schemaVersion", 1);
    payload.put("occurredAt", NOW);
    payload.put("documentId", UUID.randomUUID());
    payload.put("ownerType", "APPLICATION");
    payload.put("ownerId", ownerId);
    payload.put("documentTypeCode", "NATIONAL_ID");
    payload.put("verificationStatus", "VERIFIED");
    payload.put("verifiedByUserId", actor);
    payload.put("verifiedAt", NOW);
    payload.put("verificationComment", "Original evidence checked");
    payload.put("documentVersion", 2);
    return payload;
  }

  private Message message(String json) {
    return new Message(json.getBytes(StandardCharsets.UTF_8));
  }
}
