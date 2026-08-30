package zw.ac.uz.emhare.documentsreporting.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.ProgressionDecisionPublishedEvent;
import zw.ac.uz.emhare.common.messaging.PublishedResultVersionCreatedEvent;
import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.GeneratedDocumentRepository;
import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.model.GeneratedDocument;
import zw.ac.uz.emhare.documentsreporting.infrastructure.messaging.model.DocumentsReportingIntegrationInbox;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.messaging.DocumentsReportingIntegrationInboxRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.ProgressionDecisionProjectionRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.ProgressionDecisionResultProjectionRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.PublishedResultProjectionRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.ProgressionDecisionProjection;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.ProgressionDecisionResultProjection;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.PublishedResultProjection;

/**
 * Exact result lineage and reconciled progression evidence before official document
 * generation. @author Tinashe K
 */
@ExtendWith(MockitoExtension.class)
class OfficialResultsEvidenceContractTest {
  private static final Instant NOW = Instant.parse("2026-08-12T08:00:00Z");
  private static final String SOURCE = "assessment-results-service";
  @Mock private DocumentsReportingIntegrationInboxRepository inboxRepository;
  @Mock private PublishedResultProjectionRepository results;
  @Mock private ProgressionDecisionProjectionRepository decisions;
  @Mock private ProgressionDecisionResultProjectionRepository evidenceRepository;
  @Mock private GeneratedDocumentRepository documents;
  @Spy private ObjectMapper mapper = new ObjectMapper();
  @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  @InjectMocks private OfficialResultsProjectionListener listener;
  private final UUID student = UUID.randomUUID();
  private final UUID programme = UUID.randomUUID();
  private final UUID programmeVersion = UUID.randomUUID();
  private final UUID period = UUID.randomUUID();
  private final UUID module = UUID.randomUUID();
  private final UUID sourceResult = UUID.randomUUID();
  private final Map<UUID, DocumentsReportingIntegrationInbox> inboxRows = new HashMap<>();
  private final Map<UUID, PublishedResultProjection> resultRows = new HashMap<>();
  private final Map<UUID, ProgressionDecisionProjection> decisionRows = new HashMap<>();
  private final List<GeneratedDocument> generated = new ArrayList<>();
  private final List<ProgressionDecisionResultProjection> linkedEvidence = new ArrayList<>();
  private PublishedResultProjection currentResult;
  private ProgressionDecisionProjection currentDecision;
  private boolean documentExists;

  @BeforeEach
  void setUp() {
    lenient()
        .when(inboxRepository.findById(any()))
        .thenAnswer(invocation -> Optional.ofNullable(inboxRows.get(invocation.getArgument(0))));
    lenient()
        .when(inboxRepository.save(any()))
        .thenAnswer(
            invocation -> {
              DocumentsReportingIntegrationInbox row = invocation.getArgument(0);
              inboxRows.put(row.getEventId(), row);
              return row;
            });
    lenient()
        .when(results.findBySourcePublishedResultIdAndDeletedAtIsNull(any()))
        .thenAnswer(invocation -> Optional.ofNullable(resultRows.get(invocation.getArgument(0))));
    lenient()
        .when(
            results
                .findByStudentIdAndAcademicPeriodIdAndModuleIdAndCurrentVersionTrueAndDeletedAtIsNull(
                    any(), any(), any()))
        .thenAnswer(invocation -> Optional.ofNullable(currentResult));
    lenient()
        .when(results.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              PublishedResultProjection row = invocation.getArgument(0);
              if (row.getId() == null) identified(row);
              resultRows.put(row.getSourcePublishedResultId(), row);
              return row;
            });
    lenient()
        .when(decisions.findBySourceProgressionDecisionIdAndDeletedAtIsNull(any()))
        .thenAnswer(invocation -> Optional.ofNullable(decisionRows.get(invocation.getArgument(0))));
    lenient()
        .when(
            decisions.findByStudentIdAndAcademicPeriodIdAndCurrentVersionTrueAndDeletedAtIsNull(
                any(), any()))
        .thenAnswer(invocation -> Optional.ofNullable(currentDecision));
    lenient()
        .when(decisions.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              ProgressionDecisionProjection row = invocation.getArgument(0);
              if (row.getId() == null) identified(row);
              decisionRows.put(row.getSourceProgressionDecisionId(), row);
              return row;
            });
    lenient()
        .when(evidenceRepository.saveAllAndFlush(any()))
        .thenAnswer(
            invocation -> {
              ((Iterable<ProgressionDecisionResultProjection>) invocation.getArgument(0))
                  .forEach(linkedEvidence::add);
              return linkedEvidence;
            });
    lenient()
        .when(
            documents
                .existsByDocumentTypeAndSourceProgressionDecisionIdAndSourceProgressionDecisionVersionAndDeletedAtIsNull(
                    any(), any(), org.mockito.ArgumentMatchers.anyInt()))
        .thenAnswer(invocation -> documentExists);
    lenient()
        .when(documents.save(any()))
        .thenAnswer(
            invocation -> {
              GeneratedDocument row = invocation.getArgument(0);
              generated.add(row);
              return row;
            });
  }

  @Test
  void initialResultPublicationCreatesImmutableProjectionAndProcessesInboxOnce() {
    PublishedResultVersionCreatedEvent event = resultEvent(sourceResult, 1, null);
    listener.onPublishedResult(message(event));
    listener.onPublishedResult(message(event));
    assertThat(resultRows).hasSize(1);
    assertThat(resultRows.get(sourceResult).getFinalMark()).isEqualByComparingTo("70");
    assertThat(inboxRows.get(event.eventId()).getProcessedAt()).isEqualTo(NOW);
    verify(results).saveAndFlush(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"schema", "missingId", "wrongId", "source"})
  void brokerEnvelopeMustIdentifyTheSupportedAssessmentEvent(String invalid) {
    PublishedResultVersionCreatedEvent result = resultEvent(sourceResult, 1, null);
    Message message = message(result);
    if (invalid.equals("schema"))
      message = message(changed(result, "schemaVersion", 2), result.eventId());
    if (invalid.equals("missingId")) message.getMessageProperties().setMessageId(null);
    if (invalid.equals("wrongId"))
      message.getMessageProperties().setMessageId(UUID.randomUUID().toString());
    if (invalid.equals("source"))
      message.getMessageProperties().setHeader("source-service", "untrusted-service");
    Message rejected = message;
    assertThatThrownBy(() -> listener.onPublishedResult(rejected))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(inboxRows).isEmpty();
    verify(results, never()).saveAndFlush(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"{bad", "[]"})
  void malformedResultAndDecisionJsonNeverReachTheInbox(String json) {
    Message malformed = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8)).build();
    assertThatThrownBy(() -> listener.onPublishedResult(malformed))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("deserialized");
    assertThatThrownBy(() -> listener.onProgressionDecision(malformed))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("deserialized");
    assertThat(inboxRows).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"eventType", "source", "content", "invalidJson"})
  void reusedEventIdCannotChangeItsEnvelopeOrPayload(String invalid) {
    PublishedResultVersionCreatedEvent event = resultEvent(sourceResult, 1, null);
    inboxRows.put(
        event.eventId(),
        new DocumentsReportingIntegrationInbox(
            event.eventId(),
            invalid.equals("eventType")
                ? "other-event"
                : EmhareMessagingTopology.PUBLISHED_RESULT_VERSION_CREATED_EVENT,
            invalid.equals("source") ? "other-service" : SOURCE,
            invalid.equals("invalidJson")
                ? "{bad"
                : mapper.writeValueAsString(
                    invalid.equals("content") ? changed(event, "finalMark", 99) : event),
            NOW));
    assertThatThrownBy(() -> listener.onPublishedResult(message(event)))
        .isInstanceOf(IllegalStateException.class);
    verify(results, never()).saveAndFlush(any());
    assertThat(inboxRows.get(event.eventId()).getProcessedAt()).isNull();
  }

  @Test
  void existingUnprocessedInboxResumesWithoutCreatingAnotherInboxRecord() {
    PublishedResultVersionCreatedEvent event = resultEvent(sourceResult, 1, null);
    inboxRows.put(
        event.eventId(),
        new DocumentsReportingIntegrationInbox(
            event.eventId(),
            EmhareMessagingTopology.PUBLISHED_RESULT_VERSION_CREATED_EVENT,
            SOURCE,
            mapper.writeValueAsString(event),
            NOW));
    listener.onPublishedResult(message(event));
    verify(inboxRepository, never()).save(any());
    assertThat(inboxRows.get(event.eventId()).getProcessedAt()).isEqualTo(NOW);
  }

  @Test
  void reusedResultSourceIdOrDuplicateInitialScopeCannotCreateAnotherResult() {
    PublishedResultVersionCreatedEvent event = resultEvent(sourceResult, 1, null);
    resultRows.put(sourceResult, identified(new PublishedResultProjection(event)));
    assertThatThrownBy(() -> listener.onPublishedResult(message(event)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("source ID was reused");
    resultRows.clear();
    currentResult = identified(new PublishedResultProjection(event));
    assertThatThrownBy(() -> listener.onPublishedResult(message(event)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("initial result publication");
  }

  @ParameterizedTest
  @ValueSource(strings = {"studentId", "academicPeriodId", "moduleId", "publicationVersion"})
  void resultCorrectionMustFollowTheExactPredecessorScopeAndSequence(String invalid) {
    resultRows.put(
        sourceResult,
        identified(new PublishedResultProjection(resultEvent(sourceResult, 1, null))));
    PublishedResultVersionCreatedEvent next = resultEvent(UUID.randomUUID(), 2, sourceResult);
    assertThatThrownBy(
            () ->
                listener.onPublishedResult(
                    message(
                        changed(
                            next,
                            invalid,
                            invalid.equals("publicationVersion") ? 3 : UUID.randomUUID()),
                        next.eventId())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("correction lineage");
    verify(results, never()).saveAndFlush(any());
  }

  @Test
  void correctionRequiresDeliveredPredecessorAndCannotReplaceADifferentCurrentResult() {
    PublishedResultVersionCreatedEvent next = resultEvent(UUID.randomUUID(), 2, sourceResult);
    assertThatThrownBy(() -> listener.onPublishedResult(message(next)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("has not reached");
    resultRows.put(
        sourceResult,
        identified(new PublishedResultProjection(resultEvent(sourceResult, 1, null))));
    currentResult =
        identified(new PublishedResultProjection(resultEvent(UUID.randomUUID(), 1, null)));
    assertThatThrownBy(() -> listener.onPublishedResult(message(next)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("current projection");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void validCorrectionRetainsPredecessorAndStoresNextPublishedVersion(boolean currentPresent) {
    PublishedResultProjection predecessor =
        identified(new PublishedResultProjection(resultEvent(sourceResult, 1, null)));
    resultRows.put(sourceResult, predecessor);
    if (currentPresent) currentResult = predecessor;
    PublishedResultVersionCreatedEvent next = resultEvent(UUID.randomUUID(), 2, sourceResult);
    listener.onPublishedResult(message(next));
    verify(results).saveAndFlush(predecessor);
    assertThat(resultRows).hasSize(2);
    assertThat(resultRows.get(next.publishedResultId()).getPublicationVersion()).isEqualTo(2);
    assertThat(inboxRows.get(next.eventId()).getProcessedAt()).isEqualTo(NOW);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void progressionRequiresAnExplicitNonemptyResultEvidenceSet(boolean nullEvidence) {
    ProgressionDecisionPublishedEvent event = decisionEvent(1, null, List.of(sourceResult));
    assertThatThrownBy(
            () ->
                listener.onProgressionDecision(
                    message(
                        changed(event, "sourcePublishedResultIds", nullEvidence ? null : List.of()),
                        event.eventId())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("exact result evidence");
    assertThat(generated).isEmpty();
  }

  @Test
  void progressionRejectsReusedDecisionSourceAndUndeliveredResults() {
    ProgressionDecisionPublishedEvent event = decisionEvent(1, null, List.of(sourceResult));
    decisionRows.put(
        event.progressionDecisionId(), identified(new ProgressionDecisionProjection(event)));
    assertThatThrownBy(() -> listener.onProgressionDecision(message(event)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("source ID was reused");
    decisionRows.clear();
    assertThatThrownBy(() -> listener.onProgressionDecision(message(event)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("evidence has not reached");
  }

  @ParameterizedTest
  @ValueSource(strings = {"studentId", "academicPeriodId", "programmeId", "programmeVersionId"})
  void progressionEvidenceCannotCrossStudentPeriodOrProgrammeBoundaries(String invalid) {
    resultRows.put(
        sourceResult,
        identified(new PublishedResultProjection(resultEvent(sourceResult, 1, null))));
    ProgressionDecisionPublishedEvent event = decisionEvent(1, null, List.of(sourceResult));
    assertThatThrownBy(
            () ->
                listener.onProgressionDecision(
                    message(changed(event, invalid, UUID.randomUUID()), event.eventId())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("outside the published decision scope");
    assertThat(generated).isEmpty();
  }

  @Test
  void progressionCannotCountTwoResultVersionsOfTheSameModule() {
    resultRows.put(
        sourceResult,
        identified(new PublishedResultProjection(resultEvent(sourceResult, 1, null))));
    UUID duplicate = UUID.randomUUID();
    resultRows.put(
        duplicate,
        identified(new PublishedResultProjection(resultEvent(duplicate, 2, sourceResult))));
    assertThatThrownBy(
            () ->
                listener.onProgressionDecision(
                    message(decisionEvent(1, null, List.of(sourceResult, duplicate)))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("duplicate Module");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "attemptedCredits",
        "passedCredits",
        "failedCredits",
        "failedModules",
        "failedCompulsoryModules",
        "weightedAverage"
      })
  void everyPublishedMetricMustReconcileToExactResultEvidence(String invalid) {
    resultRows.put(
        sourceResult,
        identified(new PublishedResultProjection(resultEvent(sourceResult, 1, null))));
    ProgressionDecisionPublishedEvent event = decisionEvent(1, null, List.of(sourceResult));
    assertThatThrownBy(
            () ->
                listener.onProgressionDecision(
                    message(changed(event, invalid, 99), event.eventId())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("metrics do not reconcile");
    assertThat(generated).isEmpty();
    assertThat(linkedEvidence).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void validProgressionStoresExactEvidenceAndQueuesAtMostOneOfficialResultSlip(
      boolean alreadyRequested) {
    documentExists = alreadyRequested;
    resultRows.put(
        sourceResult,
        identified(new PublishedResultProjection(resultEvent(sourceResult, 1, null))));
    ProgressionDecisionPublishedEvent event = decisionEvent(1, null, List.of(sourceResult));
    listener.onProgressionDecision(message(event));
    listener.onProgressionDecision(message(event));
    assertThat(linkedEvidence).hasSize(1);
    assertThat(decisionRows).hasSize(1);
    assertThat(inboxRows.get(event.eventId()).getProcessedAt()).isEqualTo(NOW);
    if (alreadyRequested) assertThat(generated).isEmpty();
    else {
      assertThat(generated).hasSize(1);
      assertThat(generated.get(0).getDocumentType())
          .isEqualTo(GeneratedDocument.DocumentType.RESULT_SLIP);
      assertThat(generated.get(0).getDocumentNumber()).isEqualTo("RSLIP-PRG-1");
    }
  }

  @Test
  void failedCompulsoryAndOptionalModulesReconcileToWeightedRoundedTotals() {
    List<UUID> ids = new ArrayList<>();
    for (int index = 0; index < 3; index++) {
      UUID id = UUID.randomUUID();
      ids.add(id);
      Map<String, Object> result = changed(resultEvent(id, 1, null), "moduleId", UUID.randomUUID());
      result.put("finalMark", index == 0 ? 80 : index == 1 ? 40 : 20);
      result.put("passing", index == 0);
      result.put("grade", index == 0 ? "A" : "F");
      result.put("remark", index == 0 ? "PASS" : "FAIL");
      result.put("curriculumModuleType", index == 2 ? "OPTIONAL" : "COMPULSORY");
      resultRows.put(
          id,
          identified(
              new PublishedResultProjection(
                  mapper.convertValue(result, PublishedResultVersionCreatedEvent.class))));
    }
    ProgressionDecisionPublishedEvent event = decisionEvent(1, null, ids);
    Map<String, Object> decision = changed(event, "attemptedCredits", 30);
    decision.put("passedCredits", 10);
    decision.put("failedCredits", 20);
    decision.put("failedModules", 2);
    decision.put("failedCompulsoryModules", 1);
    decision.put("weightedAverage", new BigDecimal("46.67"));
    listener.onProgressionDecision(message(decision, event.eventId()));
    assertThat(linkedEvidence).hasSize(3);
    assertThat(generated).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"initialExists", "missingPredecessor", "wrongPredecessor", "skippedVersion"})
  void progressionPublicationMustFollowCurrentDecisionLineage(String invalid) {
    resultRows.put(
        sourceResult,
        identified(new PublishedResultProjection(resultEvent(sourceResult, 1, null))));
    ProgressionDecisionPublishedEvent previous = decisionEvent(1, null, List.of(sourceResult));
    if (!invalid.equals("missingPredecessor"))
      currentDecision = identified(new ProgressionDecisionProjection(previous));
    ProgressionDecisionPublishedEvent next =
        decisionEvent(
            invalid.equals("initialExists") ? 1 : invalid.equals("skippedVersion") ? 3 : 2,
            invalid.equals("wrongPredecessor")
                ? UUID.randomUUID()
                : previous.progressionDecisionId(),
            List.of(sourceResult));
    assertThatThrownBy(() -> listener.onProgressionDecision(message(next)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(generated).isEmpty();
  }

  @Test
  void amendedProgressionRetainsPriorDecisionAndQueuesNewVersionedSlip() {
    resultRows.put(
        sourceResult,
        identified(new PublishedResultProjection(resultEvent(sourceResult, 1, null))));
    ProgressionDecisionPublishedEvent previous = decisionEvent(1, null, List.of(sourceResult));
    currentDecision = identified(new ProgressionDecisionProjection(previous));
    decisionRows.put(previous.progressionDecisionId(), currentDecision);
    ProgressionDecisionPublishedEvent next =
        decisionEvent(2, previous.progressionDecisionId(), List.of(sourceResult));
    listener.onProgressionDecision(message(next));
    verify(decisions).saveAndFlush(currentDecision);
    assertThat(decisionRows).hasSize(2);
    assertThat(generated.get(0).getProgressionDecision().getDecisionVersion()).isEqualTo(2);
  }

  private PublishedResultVersionCreatedEvent resultEvent(UUID id, int version, UUID predecessor) {
    return new PublishedResultVersionCreatedEvent(
        UUID.randomUUID(),
        1,
        NOW,
        id,
        UUID.randomUUID(),
        UUID.randomUUID(),
        student,
        "R260001",
        UUID.randomUUID(),
        programme,
        programmeVersion,
        period,
        "2026-S1",
        module,
        "CSC101",
        "Introduction to computing",
        "COMPULSORY",
        BigDecimal.TEN,
        new BigDecimal("70"),
        "B",
        "PASS",
        true,
        version,
        predecessor,
        version > 1 ? UUID.randomUUID() : null,
        UUID.randomUUID(),
        NOW);
  }

  private ProgressionDecisionPublishedEvent decisionEvent(
      int version, UUID predecessor, List<UUID> evidence) {
    return new ProgressionDecisionPublishedEvent(
        UUID.randomUUID(),
        1,
        NOW,
        UUID.randomUUID(),
        "PRG-" + version,
        version,
        predecessor,
        UUID.randomUUID(),
        "BSC-P1",
        1,
        UUID.randomUUID(),
        student,
        "R260001",
        UUID.randomUUID(),
        programme,
        programmeVersion,
        period,
        "2026-S1",
        1,
        "PROCEED",
        "Proceed",
        2,
        BigDecimal.TEN,
        BigDecimal.TEN,
        BigDecimal.ZERO,
        0,
        0,
        new BigDecimal("70"),
        UUID.randomUUID(),
        NOW,
        evidence);
  }

  private Message message(PublishedResultVersionCreatedEvent event) {
    return message(event, event.eventId());
  }

  private Message message(ProgressionDecisionPublishedEvent event) {
    return message(event, event.eventId());
  }

  private Message message(Object event, UUID id) {
    return MessageBuilder.withBody(
            mapper.writeValueAsString(event).getBytes(StandardCharsets.UTF_8))
        .setMessageId(id.toString())
        .setHeader("source-service", SOURCE)
        .build();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> changed(Object event, String field, Object value) {
    Map<String, Object> result = new HashMap<>(mapper.convertValue(event, Map.class));
    result.put(field, value);
    return result;
  }

  private <T> T identified(T entity) {
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    return entity;
  }
}
