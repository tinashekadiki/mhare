package zw.ac.uz.emhare.admissions.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.AdmissionsOutboxEvent;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProgrammeEntryOptionSelectionRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.messaging.AdmissionsOutboxEventRepository;
import zw.ac.uz.emhare.common.messaging.*;

/** Real aggregate snapshots serialized across Admissions service boundaries. @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class AdmissionsOutboxContractTest {
  private static final Instant NOW = Instant.parse("2026-08-12T08:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Africa/Harare"));
  @Mock private AdmissionsOutboxEventRepository repository;
  @Mock private ApplicationProgrammeEntryOptionSelectionRepository entryOptions;
  private final ObjectMapper mapper = new ObjectMapper();
  private final Map<UUID, AdmissionsOutboxEvent> saved = new LinkedHashMap<>();
  private final UUID actor = UUID.randomUUID();
  private AdmissionsIntegrationOutboxService service;
  private Application application;
  private ApplicationProgrammeChoice choice;
  private AdmissionOffer offer;

  @BeforeEach
  void setUp() {
    lenient()
        .when(repository.existsById(any()))
        .thenAnswer(invocation -> saved.containsKey(invocation.getArgument(0)));
    lenient()
        .when(repository.save(any()))
        .thenAnswer(
            invocation -> {
              AdmissionsOutboxEvent event = invocation.getArgument(0);
              saved.put(event.getId(), event);
              return event;
            });
    service = new AdmissionsIntegrationOutboxService(repository, mapper, CLOCK);
    Applicant applicant =
        identified(
            new Applicant(
                UUID.randomUUID(), "A000001", "LOCAL", "Tariro", "Moyo", "applicant@example.test"));
    application =
        identified(
            new Application(
                UUID.randomUUID(),
                "AUG26",
                "August intake",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                3,
                applicant,
                new ApplicationType("UNDERGRAD", "Undergraduate", false, false),
                "APP-1",
                false));
    choice =
        identified(
            new ApplicationProgrammeChoice(
                application,
                new ProgrammeSelectionSnapshot(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "BSC",
                    "Science",
                    "BSc",
                    UUID.randomUUID(),
                    "Computing",
                    "2026"),
                1));
    offer =
        identified(
            new AdmissionOffer(
                application,
                choice,
                new ProgrammeChoiceDecision(
                    application,
                    choice,
                    DecisionOutcome.ADMIT,
                    "Approved admission",
                    null,
                    actor,
                    NOW),
                "OFFER-1"));
    offer.updateTerms(
        OfferType.FIRM, null, NOW.plusSeconds(3600), null, null, LocalDate.of(2026, 9, 1), NOW);
  }

  @Test
  void feeRequestPreservesFinanceOwnershipAndApplicantIdentifiers() {
    UUID keycloak = UUID.randomUUID();
    service.enqueueApplicationFeeRequired(
        application.getId(),
        application.getApplicant().getUserId(),
        keycloak,
        new BigDecimal("20.50"),
        "USD");
    ApplicationFeeRequiredEvent event = single(ApplicationFeeRequiredEvent.class);
    assertThat(event.applicationId()).isEqualTo(application.getId());
    assertThat(event.applicantUserId()).isEqualTo(application.getApplicant().getUserId());
    assertThat(event.applicantKeycloakUserId()).isEqualTo(keycloak);
    assertThat(event.amountDue()).isEqualByComparingTo("20.50");
    assertThat(event.currencyCode()).isEqualTo("USD");
    assertThat(event.schemaVersion()).isEqualTo(ApplicationFeeRequiredEvent.CURRENT_SCHEMA_VERSION);
    assertThat(saved.values().iterator().next().getRoutingKey())
        .isEqualTo(EmhareMessagingTopology.APPLICATION_FEE_REQUIRED_EVENT);
  }

  @Test
  void academicReleaseAndRecommendationAreIdempotentAndKeepImmutableReviewScope() {
    AcademicReviewAssignment assignment =
        identified(
            new AcademicReviewAssignment(
                null,
                choice,
                UUID.randomUUID(),
                "SCI",
                "Science faculty",
                UUID.randomUUID(),
                "COMP",
                "Computing school",
                "[]",
                2,
                actor,
                NOW,
                NOW.plusSeconds(3600)));
    AcademicUnitRecommendation recommendation =
        identified(
            new AcademicUnitRecommendation(
                assignment,
                1,
                SelectionDecisionType.SELECT,
                2,
                "GENERAL",
                "Meets programme requirements",
                actor,
                NOW));
    service.enqueueAcademicReviewReleased(assignment);
    service.enqueueAcademicReviewReleased(assignment);
    service.enqueueAcademicRecommendationRecorded(assignment, recommendation);
    service.enqueueAcademicRecommendationRecorded(assignment, recommendation);
    assertThat(saved).hasSize(2);
    AcademicReviewReleasedEvent released =
        read(saved.values().stream().toList().get(0), AcademicReviewReleasedEvent.class);
    AcademicRecommendationRecordedEvent recommended =
        read(saved.values().stream().toList().get(1), AcademicRecommendationRecordedEvent.class);
    assertThat(released.applicationId()).isEqualTo(application.getId());
    assertThat(released.programmeChoiceId()).isEqualTo(choice.getId());
    assertThat(released.recommendationAcademicUnitId())
        .isEqualTo(assignment.getRecommendationAcademicUnitId());
    assertThat(released.dueAt()).isEqualTo(NOW.plusSeconds(3600));
    assertThat(recommended.recommendationId()).isEqualTo(recommendation.getId());
    assertThat(recommended.recommendation()).isEqualTo("SELECT");
    assertThat(recommended.recommendedByUserId()).isEqualTo(actor);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "APPLICATION_SUBMITTED",
        "PAYMENT_CONFIRMED",
        "VERIFICATION_DECISION",
        "OFFER_DISPATCHED",
        "OFFER_RESPONSE",
        "STUDENT_CONVERSION"
      })
  void applicantMilestonesGenerateExactlyOneEmailAndOneInAppMessage(String milestone) {
    publish();
    offer.respond(OfferResponseType.ACCEPTED);
    if (milestone.equals("STUDENT_CONVERSION")) {
      offer.requestConversion(NOW);
      offer.markConverted(UUID.randomUUID(), UUID.randomUUID(), "R260001", NOW);
    }
    Runnable enqueue =
        switch (milestone) {
          case "APPLICATION_SUBMITTED" ->
              () -> service.enqueueApplicationSubmittedNotification(application);
          case "PAYMENT_CONFIRMED" ->
              () -> service.enqueuePaymentConfirmedNotification(application, "PAY-123");
          case "VERIFICATION_DECISION" ->
              () -> service.enqueueVerificationDecisionNotification(application);
          case "OFFER_DISPATCHED" -> () -> service.enqueueOfferDispatchedNotification(offer);
          case "OFFER_RESPONSE" -> () -> service.enqueueOfferResponseNotification(offer);
          default -> () -> service.enqueueStudentConversionNotification(offer);
        };
    enqueue.run();
    enqueue.run();
    List<NotificationRequestedEvent> messages =
        saved.values().stream().map(row -> read(row, NotificationRequestedEvent.class)).toList();
    assertThat(messages)
        .hasSize(2)
        .extracting(NotificationRequestedEvent::channel)
        .containsExactly("EMAIL", "IN_APP");
    assertThat(messages)
        .allSatisfy(
            message -> {
              assertThat(message.eventType()).isEqualTo(milestone);
              assertThat(message.occurredAt()).isEqualTo(NOW);
              assertThat(message.recipientUserId())
                  .isEqualTo(application.getApplicant().getUserId());
              assertThat(message.templateCode()).isEqualTo(milestone + "_" + message.channel());
            });
    assertThat(messages.get(0).recipientAddress()).isEqualTo("applicant@example.test");
    assertThat(messages.get(1).recipientAddress())
        .isEqualTo(application.getApplicant().getUserId().toString());
    String evidenceKey =
        switch (milestone) {
          case "PAYMENT_CONFIRMED" -> "paymentReference";
          case "STUDENT_CONVERSION" -> "studentNumber";
          case "OFFER_RESPONSE" -> "response";
          case "OFFER_DISPATCHED" -> "acceptanceDeadline";
          default -> "applicationNumber";
        };
    String evidence =
        switch (milestone) {
          case "PAYMENT_CONFIRMED" -> "PAY-123";
          case "STUDENT_CONVERSION" -> "R260001";
          case "OFFER_RESPONSE" -> "ACCEPTED";
          case "OFFER_DISPATCHED" -> NOW.plusSeconds(3600).toString();
          default -> "APP-1";
        };
    assertThat(messages.get(0).variables()).containsEntry(evidenceKey, evidence);
  }

  @Test
  void refereeInvitationDeliversOnlyTheConfiguredPortalUrlToTheExternalReferee() {
    ApplicantReferee referee =
        new ApplicantReferee(
            application.getApplicant(),
            "Dr Chipo Dube",
            "Dr",
            "School",
            "Teacher",
            "referee@example.test",
            null);
    UUID invitation = UUID.randomUUID();
    String url = "https://apply.example.test/references/token-123";
    service.enqueueRefereeReferenceRequest(
        application, referee, invitation, url, NOW.plusSeconds(600));
    service.enqueueRefereeReferenceRequest(
        application, referee, invitation, url, NOW.plusSeconds(600));
    NotificationRequestedEvent event = single(NotificationRequestedEvent.class);
    assertThat(event.recipientUserId()).isNull();
    assertThat(event.channel()).isEqualTo("EMAIL");
    assertThat(event.recipientAddress()).isEqualTo("referee@example.test");
    assertThat(event.variables())
        .containsEntry("responseUrl", url)
        .containsEntry("expiresAt", NOW.plusSeconds(600).toString())
        .containsEntry("applicantName", "Tariro Moyo");
  }

  @Test
  void missingDocumentWorkflowUsesIntakeClosingDayInInstitutionTimezoneAndDeduplicates() {
    UUID document = UUID.randomUUID();
    service.enqueueMissingDocumentsNotification(
        application, "IDENTITY", "Unreadable", document, 3, actor);
    service.enqueueMissingDocumentsNotification(
        application, "IDENTITY", "Unreadable", document, 3, actor);
    assertThat(saved).hasSize(3);
    AdmissionsOutboxEvent workflow =
        saved.values().stream()
            .filter(
                row ->
                    row.getEventType()
                        .equals(
                            EmhareMessagingTopology
                                .MISSING_APPLICATION_DOCUMENT_WORKFLOW_REQUESTED_EVENT))
            .findFirst()
            .orElseThrow();
    MissingApplicationDocumentWorkflowRequestedEvent event =
        read(workflow, MissingApplicationDocumentWorkflowRequestedEvent.class);
    assertThat(event.documentId()).isEqualTo(document);
    assertThat(event.documentVersion()).isEqualTo(3);
    assertThat(event.dueAt()).isEqualTo(Instant.parse("2026-12-31T21:59:59.999999999Z"));
    assertThat(event.rejectionReason()).isEqualTo("Unreadable");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void conversionSnapshotPreservesProgrammeVersionAndOrderedEntryPreferences(boolean configured) {
    UUID option = UUID.randomUUID();
    if (configured) {
      service = new AdmissionsIntegrationOutboxService(repository, mapper, CLOCK, entryOptions);
      when(entryOptions.findAllByProgrammeChoice_IdAndDeletedAtIsNullOrderByPreferenceRankAsc(
              choice.getId()))
          .thenReturn(
              List.of(
                  new ApplicationProgrammeEntryOptionSelection(
                      choice, option, "COMPUTING", "Computing major", 1)));
    }
    publish();
    offer.respond(OfferResponseType.ACCEPTED);
    UUID eventId = offer.requestConversion(NOW);
    service.enqueueAcceptedOfferReadyForConversion(eventId, offer);
    AcceptedOfferReadyForConversionEvent event = single(AcceptedOfferReadyForConversionEvent.class);
    assertThat(event.eventId()).isEqualTo(eventId);
    assertThat(event.programmeVersionId()).isEqualTo(choice.getProgrammeVersionId());
    assertThat(event.applicantId()).isEqualTo(application.getApplicant().getId());
    assertThat(event.applicantUserId()).isEqualTo(application.getApplicant().getUserId());
    assertThat(event.commencementDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    if (configured)
      assertThat(event.entryOptionPreferences())
          .containsExactly(
              new AcceptedOfferReadyForConversionEvent.EntryOptionPreference(
                  option, "COMPUTING", "Computing major", 1));
    else assertThat(event.entryOptionPreferences()).isEmpty();
  }

  @Test
  void publicationAndEachEmailAttemptHaveIndependentIdempotencyWithStoredPdfAttachment() {
    OfferPublication publication = publish();
    OfferDispatch first =
        new OfferDispatch(offer, publication, 1, UUID.randomUUID(), "applicant@example.test", NOW);
    service.enqueueOfferPublication(publication, first);
    service.enqueueOfferPublication(publication, first);
    OfferDispatch retry =
        new OfferDispatch(offer, publication, 2, UUID.randomUUID(), "applicant@example.test", NOW);
    service.enqueueOfferEmail(publication, retry);
    service.enqueueOfferEmail(publication, retry);
    assertThat(saved).hasSize(3);
    OfferPublicationEvent event =
        read(saved.values().iterator().next(), OfferPublicationEvent.class);
    assertThat(event.currentPublication()).isTrue();
    assertThat(event.offerStatus()).isEqualTo("SENT");
    assertThat(event.generatedDocumentId())
        .isEqualTo(publication.getDocumentVersion().getGeneratedDocumentId());
    List<NotificationRequestedEvent> emails =
        saved.values().stream()
            .filter(
                row ->
                    row.getEventType().equals(EmhareMessagingTopology.NOTIFICATION_REQUESTED_EVENT))
            .map(row -> read(row, NotificationRequestedEvent.class))
            .toList();
    assertThat(emails).hasSize(2);
    assertThat(emails.get(0).idempotencyKey()).endsWith("email-attempt:1");
    assertThat(emails.get(1).idempotencyKey()).endsWith("email-attempt:2");
    assertThat(emails)
        .allSatisfy(
            email -> {
              assertThat(email.attachments())
                  .containsExactly(
                      new NotificationAttachmentReference(
                          publication.getDocumentVersion().getGeneratedDocumentId(),
                          "DOC-1",
                          "documents",
                          "offer.pdf",
                          "sha256",
                          "APP-1-OFFER-1.pdf",
                          "application/pdf"));
              assertThat(email.templateCode()).isEqualTo("ADMISSION_OFFER_PUBLISHED_EMAIL");
            });
  }

  @Test
  void statusProjectionIsAbsentBeforePublicationAndChangesWithApplicantResponse() {
    service.enqueueCurrentOfferPublicationStatus(offer);
    assertThat(saved).isEmpty();
    publish();
    service.enqueueCurrentOfferPublicationStatus(offer);
    service.enqueueCurrentOfferPublicationStatus(offer);
    offer.respond(OfferResponseType.ACCEPTED);
    service.enqueueCurrentOfferPublicationStatus(offer);
    service.enqueueCurrentOfferPublicationStatus(offer);
    assertThat(saved.values().stream().map(row -> read(row, OfferPublicationEvent.class)))
        .extracting(OfferPublicationEvent::offerStatus)
        .containsExactly("SENT", "ACCEPTED");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "{}"})
  void absentProfileJsonProducesSafeInstitutionDefaults(String empty) {
    service.enqueueOfferLetterRequested(
        offer, 1, actor, " ", null, profile(null, empty, empty, empty));
    OfferLetterRequestedEvent event = single(OfferLetterRequestedEvent.class);
    assertThat(event.contentSnapshot().bankAccounts()).isEmpty();
    assertThat(event.contentSnapshot().bankDetails()).isNull();
    assertThat(event.contentSnapshot().academicUnitName()).isEqualTo("Computing");
    assertThat(event.contentSnapshot().signatoryName()).isEqualTo("Registrar");
  }

  @Test
  void legacyBankConfigurationAndBrandingAreTrimmedAndPreservedInOfficialSnapshot() {
    service.enqueueOfferLetterRequested(
        offer,
        2,
        actor,
        " University ",
        null,
        profile(
            " ",
            "{\"postalAddress\":\" \",\"address\":\"Harare\",\"telephone\":\"242\"}",
            "{\"offerLetterSignatoryName\":\" Dr Dube \",\"offerLetterSignatoryTitle\":\" Registrar \",\"registrarSignatureDocumentId\":\"signature-id\"}",
            "{\"bankName\":\" CBZ \",\"accountNumber\":\" 001 \",\"accountName\":\" University \",\"branchName\":\" \"}"));
    OfferLetterContentSnapshot content = single(OfferLetterRequestedEvent.class).contentSnapshot();
    assertThat(content.bankDetails().bankName()).isEqualTo("CBZ");
    assertThat(content.bankDetails().accountNumber()).isEqualTo("001");
    assertThat(content.bankDetails().branchName()).isNull();
    assertThat(content.bankAccounts()).isEmpty();
    assertThat(content.signatoryName()).isEqualTo("Dr Dube");
    assertThat(content.academicUnitName()).isEqualTo("University");
    assertThat(content.institutionPostalAddress()).isEqualTo("Harare");
    assertThat(content.institutionTelephone()).isEqualTo("242");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"bankName\":\"CBZ\"}",
        "{\"accountNumber\":\"001\"}",
        "{\"bankName\":17,\"accountNumber\":\"001\"}",
        "{\"bankName\":\"CBZ\",\"accountNumber\":\" \"}"
      })
  void incompleteLegacyBankDetailsAreNotPublishedAsPaymentInstructions(String bankJson) {
    service.enqueueOfferLetterRequested(offer, 1, actor, null, profile(null, null, null, bankJson));
    assertThat(single(OfferLetterRequestedEvent.class).contentSnapshot().bankDetails()).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"accounts\":[42]}",
        "{\"accounts\":[{\"bankName\":\"CBZ\",\"accountNumber\":\"001\"}]}",
        "{\"accounts\":[{\"currencyCode\":\"USD\",\"accountNumber\":\"001\"}]}",
        "{\"accounts\":[{\"currencyCode\":\"USD\",\"bankName\":\"CBZ\"}]}"
      })
  void malformedManagedAccountsFailBeforeAnyOutboxRecordIsWritten(String bankJson) {
    assertThatThrownBy(
            () ->
                service.enqueueOfferLetterRequested(
                    offer, 1, actor, null, profile(null, null, null, bankJson)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("institution bank account");
    assertThat(saved).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "contacts,Institution document profile",
    "branding,Institution document profile",
    "bank,Institution bank configuration"
  })
  void malformedInstitutionJsonFailsClosed(String field, String message) {
    assertThatThrownBy(
            () ->
                service.enqueueOfferLetterRequested(
                    offer,
                    1,
                    actor,
                    null,
                    profile(
                        null,
                        field.equals("contacts") ? "{bad" : "{}",
                        field.equals("branding") ? "{bad" : "{}",
                        field.equals("bank") ? "{bad" : "{}")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(message);
    assertThat(saved).isEmpty();
  }

  @Test
  void repeatedLetterRequestIsIdempotentButNewDocumentVersionCreatesDistinctSnapshot() {
    service.enqueueOfferLetterRequested(offer, actor);
    service.enqueueOfferLetterRequested(offer, actor);
    service.enqueueOfferLetterRequested(offer, 2, actor);
    service.enqueueOfferLetterRequested(offer, 2, actor);
    assertThat(saved.values().stream().map(row -> read(row, OfferLetterRequestedEvent.class)))
        .extracting(OfferLetterRequestedEvent::documentVersion)
        .containsExactly(1, 2);
  }

  private CoreIdentityClient.CoreInstitutionProfile profile(
      String registrar, String contacts, String branding, String banks) {
    return new CoreIdentityClient.CoreInstitutionProfile(
        UUID.randomUUID(),
        "UZ",
        "University of Zimbabwe",
        "University of Zimbabwe",
        registrar,
        "USD",
        "ZW",
        "Africa/Harare",
        contacts,
        branding,
        banks,
        "UZ");
  }

  private OfferPublication publish() {
    OfferDocumentVersion document = identified(new OfferDocumentVersion(offer, 1, actor, NOW));
    document.store(UUID.randomUUID(), "DOC-1", "documents", "offer.pdf", "sha256", NOW);
    offer.linkCurrentDocumentVersion(document);
    OfferPublication publication =
        identified(new OfferPublication(offer, document, 1, actor, UUID.randomUUID(), NOW));
    offer.publish(publication, actor, NOW);
    return publication;
  }

  private <T> T single(Class<T> type) {
    assertThat(saved).hasSize(1);
    return read(saved.values().iterator().next(), type);
  }

  private <T> T read(AdmissionsOutboxEvent row, Class<T> type) {
    assertThat(row.getRoutingKey()).isEqualTo(row.getEventType());
    return mapper.readValue(row.getPayload(), type);
  }

  private <T> T identified(T entity) {
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    return entity;
  }
}
