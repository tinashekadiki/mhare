package zw.ac.uz.emhare.studentrecords.conversion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import zw.ac.uz.emhare.common.messaging.AcceptedOfferReadyForConversionEvent;
import zw.ac.uz.emhare.studentrecords.conversion.domain.model.*;
import zw.ac.uz.emhare.studentrecords.conversion.infrastructure.persistence.*;
import zw.ac.uz.emhare.studentrecords.integration.StudentRecordsIntegrationOutboxService;

/**
 * @author Tinashe K
 */
class StudentConversionContractTest {
  private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
  private StudentProfileRepository students;
  private StudentProgrammeEnrolmentRepository enrolments;
  private StudentConversionRequestRepository conversions;
  private StudentStatusEventRepository events;
  private StudentEntryOptionPreferenceRepository preferences;
  private StudentRecordsIntegrationOutboxService outbox;
  private StudentNumberGenerator numbers;
  private StudentConversionService service;
  private StudentConversionRequest conversion;

  @BeforeEach
  void setUp() {
    students = mock(StudentProfileRepository.class);
    enrolments = mock(StudentProgrammeEnrolmentRepository.class);
    conversions = mock(StudentConversionRequestRepository.class);
    events = mock(StudentStatusEventRepository.class);
    preferences = mock(StudentEntryOptionPreferenceRepository.class);
    outbox = mock(StudentRecordsIntegrationOutboxService.class);
    numbers = mock(StudentNumberGenerator.class);
    service =
        new StudentConversionService(
            students,
            enrolments,
            conversions,
            events,
            outbox,
            numbers,
            Clock.fixed(NOW, ZoneOffset.UTC),
            preferences);
    when(numbers.nextStudentNumber("LOCAL", 2026)).thenReturn("R260001");
    when(students.saveAndFlush(any()))
        .thenAnswer(invocation -> persisted(invocation.getArgument(0)));
    when(enrolments.saveAndFlush(any()))
        .thenAnswer(invocation -> persisted(invocation.getArgument(0)));
    when(conversions.saveAndFlush(any()))
        .thenAnswer(invocation -> persisted(invocation.getArgument(0)));
    var offer = offer();
    var student = persisted(new StudentProfile("R260009", offer));
    var enrolment = persisted(new StudentProgrammeEnrolment(student, offer));
    conversion =
        persisted(
            new StudentConversionRequest(
                offer.eventId(), offer.applicationId(), offer.offerId(), student, enrolment, NOW));
    when(conversions.findById(conversion.getId())).thenReturn(Optional.of(conversion));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "eventId",
        "applicationId",
        "offerId",
        "applicantId",
        "applicantUserId",
        "programmeChoiceId",
        "programmeId",
        "programmeVersionId",
        "intakeId",
        "commencementDate"
      })
  void missingCrossServiceContractIdentityIsRejectedBeforePersistence(String field) {
    var tree = mapper.valueToTree(offer()).deepCopy();
    ((tools.jackson.databind.node.ObjectNode) tree).putNull(field);
    var invalid = mapper.treeToValue(tree, AcceptedOfferReadyForConversionEvent.class);
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> service.startConversion(invalid));
    assertEquals(
        "Accepted-offer conversion event contract is invalid or unsupported.", error.getMessage());
    verifyNoInteractions(students, enrolments, events, preferences, numbers, outbox);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 3})
  void unsupportedSchemaCannotStartConversion(int version) {
    var tree = (tools.jackson.databind.node.ObjectNode) mapper.valueToTree(offer());
    tree.put("schemaVersion", version);
    var invalid = mapper.treeToValue(tree, AcceptedOfferReadyForConversionEvent.class);
    assertThrows(IllegalArgumentException.class, () -> service.startConversion(invalid));
    verifyNoInteractions(students, outbox);
  }

  @ParameterizedTest
  @ValueSource(strings = {"empty", "null", "ranked"})
  void acceptsSupportedOfferContractAndPersistsOnlySuppliedEntryPreferences(String preferenceKind) {
    var tree = (tools.jackson.databind.node.ObjectNode) mapper.valueToTree(offer());
    if (preferenceKind.equals("null")) tree.putNull("entryOptionPreferences");
    if (preferenceKind.equals("ranked"))
      tree.set(
          "entryOptionPreferences",
          mapper.valueToTree(
              List.of(
                  new AcceptedOfferReadyForConversionEvent.EntryOptionPreference(
                      UUID.randomUUID(), " BIO ", " Biological Sciences ", 1),
                  new AcceptedOfferReadyForConversionEvent.EntryOptionPreference(
                      UUID.randomUUID(), " CHEM ", " Chemistry ", 2))));
    var event = mapper.treeToValue(tree, AcceptedOfferReadyForConversionEvent.class);
    StudentConversionSummary result = service.startConversion(event);
    assertEquals("PROVISIONING", result.status());
    assertEquals("PROVISIONING", result.studentStatus());
    assertEquals("PROVISIONING", result.programmeEnrolmentStatus());
    assertEquals(event.applicationId(), result.sourceApplicationId());
    assertEquals(event.programmeVersionId(), result.programmeVersionId());
    assertEquals("R260001", result.studentNumber());
    if (preferenceKind.equals("ranked")) {
      ArgumentCaptor<Iterable<StudentEntryOptionPreference>> savedPreferences =
          ArgumentCaptor.captor();
      verify(preferences).saveAll(savedPreferences.capture());
      List<StudentEntryOptionPreference> saved = new ArrayList<>();
      savedPreferences.getValue().forEach(saved::add);
      assertEquals(2, saved.size());
      for (int index = 0; index < saved.size(); index++) {
        var supplied = event.entryOptionPreferences().get(index);
        var persistedPreference = saved.get(index);
        assertEquals(
            supplied.entryOptionId(),
            ReflectionTestUtils.getField(persistedPreference, "entryOptionId"));
        assertEquals(
            supplied.preferenceRank(),
            ReflectionTestUtils.getField(persistedPreference, "preferenceRank"));
        assertEquals(
            supplied.entryOptionCode().trim(),
            ReflectionTestUtils.getField(persistedPreference, "entryOptionCode"));
        assertEquals(
            supplied.entryOptionName().trim(),
            ReflectionTestUtils.getField(persistedPreference, "entryOptionName"));
        var owner =
            (StudentProgrammeEnrolment)
                ReflectionTestUtils.getField(persistedPreference, "programmeEnrolment");
        assertNotNull(owner);
        assertEquals(result.programmeEnrolmentId(), owner.getId());
      }
    } else verifyNoInteractions(preferences);
    verify(events).save(any(StudentStatusEvent.class));
    verify(outbox).enqueueProvisioningRequests(any(StudentConversionRequest.class));
    verify(outbox, never()).enqueueConversionCompleted(any());
  }

  @Test
  void repeatOfferDeliveryReturnsExistingConversionWithoutCreatingStudentOrProvisioningTwice() {
    var event = offer();
    when(conversions.findBySourceOfferIdAndDeletedAtIsNull(event.offerId()))
        .thenReturn(Optional.of(conversion));
    assertEquals(conversion.getId(), service.startConversion(event).id());
    verifyNoInteractions(students, enrolments, events, preferences, numbers, outbox);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void eitherProvisioningOrderActivatesOnlyAfterBothAndDuplicateAcknowledgementsAreIdempotent(
      boolean financeFirst) {
    if (financeFirst) service.recordFinanceProvisioning(conversion.getId(), true, null);
    else service.recordPortalProvisioning(conversion.getId(), true, null);
    assertEquals(StudentStatus.PROVISIONING, conversion.getStudent().getStatus());
    verifyNoInteractions(events, outbox);
    if (financeFirst) service.recordPortalProvisioning(conversion.getId(), true, null);
    else service.recordFinanceProvisioning(conversion.getId(), true, null);
    assertEquals(StudentConversionStatus.COMPLETED, conversion.getStatus());
    assertEquals(StudentStatus.ACTIVE, conversion.getStudent().getStatus());
    assertEquals(ProgrammeEnrolmentStatus.ACTIVE, conversion.getProgrammeEnrolment().getStatus());
    service.recordFinanceProvisioning(conversion.getId(), true, null);
    service.recordPortalProvisioning(conversion.getId(), true, null);
    verify(outbox, times(1)).enqueueConversionCompleted(conversion);
    verify(events, times(1)).save(any(StudentStatusEvent.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"finance", "portal", "both"})
  void failedProvisioningCanRetryOnlyPendingOwnersWithAuditableReason(String owner) {
    service.recordFinanceProvisioning(
        conversion.getId(), owner.equals("portal"), "Finance unavailable");
    service.recordPortalProvisioning(
        conversion.getId(), owner.equals("finance"), "Identity unavailable");
    assertEquals(StudentConversionStatus.FAILED, conversion.getStatus());
    assertFalse(conversion.canComplete());
    UUID actor = UUID.randomUUID();
    StudentConversionSummary result =
        service.retryProvisioning(conversion.getId(), "  Restored provider  ", actor);
    assertEquals("PROVISIONING", result.status());
    assertEquals(1, result.retryCount());
    assertEquals("Restored provider", result.lastRetryReason());
    assertEquals(actor, result.lastRetryByUserId());
    assertNull(result.failureReason());
    assertEquals(!owner.equals("portal"), conversion.needsFinanceProvisioning());
    assertEquals(!owner.equals("finance"), conversion.needsPortalProvisioning());
    verify(outbox).enqueueProvisioningRequests(conversion);
    verify(outbox, never()).enqueueConversionCompleted(any());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void retriesRequireNonBlankEvidence(String reason) {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.retryProvisioning(conversion.getId(), reason, UUID.randomUUID()));
    assertEquals(0, conversion.getRetryCount());
    verifyNoInteractions(outbox);
  }

  @Test
  void retriesRequireActorAndTimestampAndCannotRestartCompletedConversion() {
    assertThrows(
        IllegalArgumentException.class, () -> conversion.retryProvisioning("Evidence", null, NOW));
    assertThrows(
        IllegalArgumentException.class,
        () -> conversion.retryProvisioning("Evidence", UUID.randomUUID(), null));
    conversion.recordFinanceProvisioning(true, null);
    conversion.recordPortalProvisioning(true, null);
    assertThrows(
        IllegalStateException.class,
        () -> conversion.retryProvisioning("Evidence", UUID.randomUUID(), NOW));
    conversion.complete(NOW);
    assertThrows(
        IllegalStateException.class,
        () -> service.retryProvisioning(conversion.getId(), "Evidence", UUID.randomUUID()));
    verifyNoInteractions(outbox);
  }

  @Test
  void missingProvisioningTargetFailsClosedAndRegisterPreservesConversionEvidence() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.recordFinanceProvisioning(UUID.randomUUID(), true, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.recordPortalProvisioning(UUID.randomUUID(), false, "Unknown"));
    when(conversions.findAllByDeletedAtIsNullOrderByRequestedAtDesc())
        .thenReturn(List.of(conversion));
    assertEquals(conversion.getId(), service.listConversions().getFirst().id());
    verifyNoInteractions(outbox);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "missing-id",
        "null-code",
        "blank-code",
        "null-name",
        "blank-name",
        "zero-rank",
        "negative-rank"
      })
  void malformedAdmissionPreferencesCannotBecomeStudentEvidence(String failure) {
    UUID optionId = failure.equals("missing-id") ? null : UUID.randomUUID();
    String code = failure.equals("null-code") ? null : failure.equals("blank-code") ? " " : "BIO";
    String name =
        failure.equals("null-name") ? null : failure.equals("blank-name") ? " " : "Biology";
    int rank = failure.equals("zero-rank") ? 0 : failure.equals("negative-rank") ? -1 : 1;
    var preference =
        new AcceptedOfferReadyForConversionEvent.EntryOptionPreference(optionId, code, name, rank);
    if (failure.equals("missing-id"))
      assertThrows(
          NullPointerException.class,
          () -> new StudentEntryOptionPreference(conversion.getProgrammeEnrolment(), preference));
    else
      assertThrows(
          IllegalArgumentException.class,
          () -> new StudentEntryOptionPreference(conversion.getProgrammeEnrolment(), preference));
  }

  private static <T> T persisted(T entity) {
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    return entity;
  }

  private static AcceptedOfferReadyForConversionEvent offer() {
    return new AcceptedOfferReadyForConversionEvent(
        UUID.randomUUID(),
        2,
        NOW,
        UUID.randomUUID(),
        "APP-26",
        UUID.randomUUID(),
        "OFR-26",
        UUID.randomUUID(),
        UUID.randomUUID(),
        "APL-26",
        "LOCAL",
        "Tariro",
        "Moyo",
        "tariro@example.test",
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "BSC",
        "Computing",
        UUID.randomUUID(),
        LocalDate.of(2026, 8, 1));
  }
}
