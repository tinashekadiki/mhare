package zw.ac.uz.emhare.admissions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.*;
import zw.ac.uz.emhare.admissions.integration.AdmissionsIntegrationOutboxService;

/** Auditable stored-letter, applicant-response and conversion contracts. @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class AdmissionsOfferLifecycleTest {
  private static final Instant NOW = Instant.parse("2026-08-12T08:00:00Z");
  private static final String REASON = "Verified evidence supports this decision.";
  @Mock private ApplicationRepository applications;
  @Mock private AdmissionOfferRepository offers;
  @Mock private OfferConditionRepository conditions;
  @Mock private OfferResponseRepository responses;
  @Mock private OfferDispatchRepository dispatches;
  @Mock private ApplicationStatusEventRepository applicationEvents;
  @Mock private OfferStatusEventRepository offerEvents;
  @Mock private AdmissionsIntegrationOutboxService outbox;
  @Mock private Clock clock;
  @InjectMocks private AdmissionsSelectionOfferService service;
  private final UUID actor = UUID.randomUUID();
  private final UUID applicantUser = UUID.randomUUID();
  private Application application;
  private ApplicationProgrammeChoice choice;
  private AdmissionOffer offer;

  @BeforeEach
  void setUp() {
    ApplicationType type =
        identified(new ApplicationType("UNDERGRAD", "Undergraduate", false, false));
    Applicant applicant =
        identified(
            new Applicant(
                applicantUser, "A000001", "LOCAL", "Tariro", "Moyo", "applicant@example.test"));
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
                type,
                "APP-1",
                false));
    application.submit(REASON);
    application.moveToUnderReview(actor, REASON);
    application.applyEvaluationOutcome(true, true, REASON);
    application.enterAcademicReview(REASON);
    application.recordChoiceDecision(DecisionOutcome.ADMIT, REASON);
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
    choice.recordEvaluation(EvaluationStatus.ELIGIBLE, REASON);
    choice.enterAcademicReview();
    choice.recordDecision(DecisionOutcome.ADMIT, REASON);
    ProgrammeChoiceDecision decision =
        new ProgrammeChoiceDecision(
            application, choice, DecisionOutcome.ADMIT, REASON, null, actor, NOW);
    offer = identified(new AdmissionOffer(application, choice, decision, "OFFER-1"));
    offer.updateTerms(
        OfferType.FIRM, null, NOW.plusSeconds(3600), null, null, LocalDate.of(2026, 9, 1), NOW);
    lenient().when(offers.findById(offer.getId())).thenReturn(Optional.of(offer));
    lenient().when(applications.findById(application.getId())).thenReturn(Optional.of(application));
    lenient()
        .when(
            offers.findByIdAndApplicationApplicantUserIdAndDeletedAtIsNull(
                offer.getId(), applicantUser))
        .thenReturn(Optional.of(offer));
    lenient().when(clock.instant()).thenReturn(NOW);
    lenient()
        .when(responses.saveAndFlush(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void storedDocumentLinkIsIdempotentForTheSameDocumentAndRejectsReplacement() {
    UUID documentId = UUID.randomUUID();
    service.linkStoredOfferLetter(offer.getId(), 0, documentId);
    service.linkStoredOfferLetter(offer.getId(), 0, documentId);
    assertThat(offer.getGeneratedDocumentId()).isEqualTo(documentId);
    verify(offers, times(2)).saveAndFlush(offer);
    assertThatThrownBy(() -> service.linkStoredOfferLetter(offer.getId(), 0, UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("different generated document");
  }

  @Test
  void storedDocumentFromFutureOfferVersionFailsBeforeLinking() {
    assertThatThrownBy(() -> service.linkStoredOfferLetter(offer.getId(), 1, UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("future offer version");
    assertThat(offer.getGeneratedDocumentId()).isNull();
    verify(offers, never()).saveAndFlush(any());
  }

  @Test
  void missingStoredDocumentCannotBeLinkedOrApproved() {
    assertThatThrownBy(() -> service.linkStoredOfferLetter(offer.getId(), 0, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.approveOffer(offer.getId(), actor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("stored generated offer document");
    verifyNoInteractions(applicationEvents, offerEvents);
  }

  @Test
  void approvalSynchronizesApplicationAndChoiceOnce() {
    offer.linkGeneratedDocument(UUID.randomUUID());
    assertThat(service.approveOffer(offer.getId(), actor).status()).isEqualTo("APPROVED");
    assertThat(service.approveOffer(offer.getId(), actor).status()).isEqualTo("APPROVED");
    assertThat(application.getStatus()).isEqualTo(ApplicationStatus.OFFERED);
    assertThat(choice.getChoiceStatus()).isEqualTo(ProgrammeChoiceStatus.OFFERED);
    verify(applicationEvents).save(any());
    verify(offerEvents).save(any());
  }

  @Test
  void dispatchRecordsDestinationProviderEvidenceAndNotification() {
    approveDirectly();
    var result =
        service.dispatchOffer(
            offer.getId(), " email ", " applicant@example.test ", " message-1 ", actor);
    assertThat(result.status()).isEqualTo("SENT");
    ArgumentCaptor<OfferDispatch> capture = ArgumentCaptor.forClass(OfferDispatch.class);
    verify(dispatches).save(capture.capture());
    assertThat(capture.getValue().getDeliveryMethodCode()).isEqualTo("EMAIL");
    assertThat(capture.getValue().getSentTo()).isEqualTo("applicant@example.test");
    assertThat(capture.getValue().getProviderMessageId()).isEqualTo("message-1");
    assertThat(capture.getValue().getSentAt()).isEqualTo(NOW);
    verify(outbox).enqueueOfferDispatchedNotification(offer);
  }

  @Test
  void unapprovedOfferCannotBeDispatched() {
    assertThatThrownBy(
            () ->
                service.dispatchOffer(
                    offer.getId(), "EMAIL", "applicant@example.test", null, actor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("approved offer");
    verifyNoInteractions(dispatches, outbox);
  }

  @ParameterizedTest
  @CsvSource({"SATISFIED,SENT", "WAIVED,SENT", "SATISFIED,ACCEPTED", "WAIVED,ACCEPTED"})
  void conditionResolutionRecordsOperatorEvidenceAndEnqueuesReadyAcceptedOffer(
      OfferConditionStatus status, OfferStatus offerStatus) {
    publish();
    if (offerStatus == OfferStatus.ACCEPTED) acceptDirectly(false);
    OfferCondition condition =
        identified(new OfferCondition(offer, "TRANSCRIPT", "Provide final transcript", true));
    when(conditions.findByIdAndOfferIdAndDeletedAtIsNull(condition.getId(), offer.getId()))
        .thenReturn(Optional.of(condition));
    when(conditions.findAllByOfferIdAndDeletedAtIsNullOrderByConditionCodeAsc(offer.getId()))
        .thenReturn(List.of(condition));
    var result =
        service.resolveOfferCondition(
            offer.getId(), condition.getId(), status.name(), " " + REASON + " ", actor);
    assertThat(result.conditions().get(0).status()).isEqualTo(status.name());
    assertThat(condition.getSatisfiedByUserId()).isEqualTo(actor);
    assertThat(condition.getSatisfiedAt()).isEqualTo(NOW);
    assertThat(condition.getResolutionNotes()).isEqualTo(REASON);
    if (offerStatus == OfferStatus.ACCEPTED) {
      assertThat(offer.getConversionEventId()).isNotNull();
      verify(outbox).enqueueAcceptedOfferReadyForConversion(offer.getConversionEventId(), offer);
    } else verifyNoInteractions(outbox);
  }

  @Test
  void acceptedConditionResolutionDoesNotDuplicateAlreadyRequestedConversion() {
    publish();
    acceptDirectly(true);
    OfferCondition condition =
        identified(new OfferCondition(offer, "OPTIONAL", "Optional detail", false));
    when(conditions.findByIdAndOfferIdAndDeletedAtIsNull(condition.getId(), offer.getId()))
        .thenReturn(Optional.of(condition));
    service.resolveOfferCondition(offer.getId(), condition.getId(), "SATISFIED", null, actor);
    verifyNoInteractions(outbox);
  }

  @Test
  void acceptedOfferWaitsForEveryRequiredConditionBeforeConversion() {
    publish();
    acceptDirectly(false);
    OfferCondition condition =
        identified(new OfferCondition(offer, "TRANSCRIPT", "Provide final transcript", true));
    when(conditions.findByIdAndOfferIdAndDeletedAtIsNull(condition.getId(), offer.getId()))
        .thenReturn(Optional.of(condition));
    when(conditions.countByOfferIdAndRequiredTrueAndStatusAndDeletedAtIsNull(
            offer.getId(), OfferConditionStatus.PENDING))
        .thenReturn(1L);
    service.resolveOfferCondition(offer.getId(), condition.getId(), "SATISFIED", REASON, actor);
    assertThat(offer.getConversionEventId()).isNull();
    verifyNoInteractions(outbox);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PENDING", "unknown"})
  void invalidConditionResolutionCannotMutateEvidence(String status) {
    publish();
    OfferCondition condition =
        identified(new OfferCondition(offer, "TRANSCRIPT", "Provide final transcript", true));
    when(conditions.findByIdAndOfferIdAndDeletedAtIsNull(condition.getId(), offer.getId()))
        .thenReturn(Optional.of(condition));
    assertThatThrownBy(
            () ->
                service.resolveOfferCondition(
                    offer.getId(), condition.getId(), status, REASON, actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(condition.getStatus()).isEqualTo(OfferConditionStatus.PENDING);
  }

  @Test
  void conditionLookupRejectsWrongOfferOrUnpublishedOffer() {
    assertThatThrownBy(
            () ->
                service.resolveOfferCondition(
                    offer.getId(), UUID.randomUUID(), "SATISFIED", REASON, actor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sent or accepted");
    publish();
    assertThatThrownBy(
            () ->
                service.resolveOfferCondition(
                    offer.getId(), UUID.randomUUID(), "SATISFIED", REASON, actor))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Offer condition not found.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"DRAFT", "APPROVED", "SENT"})
  void withdrawingOfferRestoresAdmittedApplicationOnlyIfPreviouslyOffered(String state) {
    if (state.equals("APPROVED")) approveDirectly();
    else if (state.equals("SENT")) publish();
    var result = service.withdrawOffer(offer.getId(), REASON, actor);
    assertThat(result.status()).isEqualTo("WITHDRAWN");
    assertThat(application.getStatus()).isEqualTo(ApplicationStatus.ADMITTED);
    assertThat(choice.getChoiceStatus()).isEqualTo(ProgrammeChoiceStatus.ADMITTED);
    verify(offerEvents).save(any());
    verify(applicationEvents, times(state.equals("DRAFT") ? 0 : 1)).save(any());
    verify(outbox).enqueueCurrentOfferPublicationStatus(offer);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void withdrawingRequiresAuditableReason(String reason) {
    assertThatThrownBy(() -> service.withdrawOffer(offer.getId(), reason, actor))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reason");
    assertThat(offer.getStatus()).isEqualTo(OfferStatus.DRAFT);
    verifyNoInteractions(outbox);
  }

  @Test
  void acceptedOfferCannotBeWithdrawn() {
    publish();
    acceptDirectly(false);
    assertThatThrownBy(() -> service.withdrawOffer(offer.getId(), REASON, actor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no longer");
  }

  @Test
  void expiredPublishedOfferRestoresAdmissionAndPublishesStatusEvidence() {
    publish();
    when(clock.instant()).thenReturn(NOW.plusSeconds(7200));
    var result = service.expireOffer(offer.getId(), actor);
    assertThat(result.status()).isEqualTo("EXPIRED");
    assertThat(result.expiredAt()).isEqualTo(NOW.plusSeconds(7200));
    assertThat(application.getStatus()).isEqualTo(ApplicationStatus.ADMITTED);
    assertThat(choice.getChoiceStatus()).isEqualTo(ProgrammeChoiceStatus.ADMITTED);
    verify(outbox).enqueueCurrentOfferPublicationStatus(offer);
    verify(applicationEvents).save(any());
  }

  @Test
  void expiryRequiresElapsedDeadlineAndPublishedStatus() {
    assertThatThrownBy(() -> service.expireOffer(offer.getId(), actor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sent offer");
    publish();
    assertThatThrownBy(() -> service.expireOffer(offer.getId(), actor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("has not passed");
    assertThat(offer.getStatus()).isEqualTo(OfferStatus.SENT);
    verifyNoInteractions(outbox);
  }

  @ParameterizedTest
  @EnumSource(OfferResponseType.class)
  void applicantResponseSynchronizesAllThreeAggregatesAndImmutableEvidence(
      OfferResponseType response) {
    publish();
    var result =
        service.respondToOffer(
            offer.getId(),
            applicantUser,
            " " + response.name().toLowerCase() + " ",
            " Applicant reply ");
    assertThat(result.status()).isEqualTo(response.name());
    assertThat(result.response().notes()).isEqualTo("Applicant reply");
    assertThat(application.getStatus().name()).isEqualTo(response.name());
    assertThat(choice.getChoiceStatus())
        .isEqualTo(
            response == OfferResponseType.ACCEPTED
                ? ProgrammeChoiceStatus.OFFERED
                : ProgrammeChoiceStatus.REJECTED);
    ArgumentCaptor<OfferResponse> capture = ArgumentCaptor.forClass(OfferResponse.class);
    verify(responses).saveAndFlush(capture.capture());
    assertThat(capture.getValue().getOfferPublication()).isSameAs(offer.getCurrentPublication());
    assertThat(capture.getValue().getRespondedByUserId()).isEqualTo(applicantUser);
    verify(outbox).enqueueOfferResponseNotification(offer);
    verify(outbox).enqueueCurrentOfferPublicationStatus(offer);
    if (response == OfferResponseType.ACCEPTED)
      verify(outbox).enqueueAcceptedOfferReadyForConversion(offer.getConversionEventId(), offer);
    else verify(outbox, never()).enqueueAcceptedOfferReadyForConversion(any(), any());
  }

  @Test
  void acceptingWithPendingRequiredConditionDefersConversion() {
    publish();
    when(conditions.countByOfferIdAndRequiredTrueAndStatusAndDeletedAtIsNull(
            offer.getId(), OfferConditionStatus.PENDING))
        .thenReturn(2L);
    service.respondToOffer(offer.getId(), applicantUser, "ACCEPTED", null);
    assertThat(offer.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
    assertThat(offer.getConversionEventId()).isNull();
    verify(outbox, never()).enqueueAcceptedOfferReadyForConversion(any(), any());
  }

  @ParameterizedTest
  @EnumSource(OfferResponseType.class)
  void identicalResponseRetryReturnsRecordedOutcomeWithoutRepeatingWrites(
      OfferResponseType response) {
    publish();
    offer.respond(response);
    when(responses.findByOfferId(offer.getId()))
        .thenReturn(
            Optional.of(
                new OfferResponse(
                    offer,
                    offer.getCurrentPublication(),
                    response,
                    NOW,
                    applicantUser,
                    "Original reply")));
    assertThat(
            service
                .respondToOffer(offer.getId(), applicantUser, response.name(), "Retry reply")
                .response()
                .notes())
        .isEqualTo("Original reply");
    verify(responses, never()).saveAndFlush(any());
    verifyNoInteractions(outbox, offerEvents, applicationEvents);
  }

  @Test
  void conflictingResponseRetryCannotChangeImmutableAcceptance() {
    publish();
    offer.respond(OfferResponseType.ACCEPTED);
    when(responses.findByOfferId(offer.getId()))
        .thenReturn(
            Optional.of(
                new OfferResponse(
                    offer,
                    offer.getCurrentPublication(),
                    OfferResponseType.ACCEPTED,
                    NOW,
                    applicantUser,
                    null)));
    assertThatThrownBy(() -> service.respondToOffer(offer.getId(), applicantUser, "DECLINED", null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("immutable");
    assertThat(offer.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"unknown"})
  void unsupportedApplicantResponseFailsBeforeWriting(String response) {
    assertThatThrownBy(() -> service.respondToOffer(offer.getId(), applicantUser, response, null))
        .isInstanceOf(IllegalArgumentException.class);
    verify(responses, never()).saveAndFlush(any());
  }

  @Test
  void responseAfterDeadlineFailsBeforeWriting() {
    publish();
    when(clock.instant()).thenReturn(NOW.plusSeconds(3601));
    assertThatThrownBy(() -> service.respondToOffer(offer.getId(), applicantUser, "ACCEPTED", null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("deadline has passed");
    verify(responses, never()).saveAndFlush(any());
  }

  @Test
  void responseAtExactDeadlineIsAccepted() {
    publish();
    when(clock.instant()).thenReturn(offer.getAcceptanceDeadline());
    assertThat(service.respondToOffer(offer.getId(), applicantUser, "DECLINED", null).status())
        .isEqualTo("DECLINED");
  }

  @Test
  void anotherApplicantCannotRespondToOffer() {
    assertThatThrownBy(
            () -> service.respondToOffer(offer.getId(), UUID.randomUUID(), "ACCEPTED", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Offer not found.");
    verifyNoInteractions(responses, outbox);
  }

  @ParameterizedTest
  @ValueSource(strings = {"unpublished", "amended"})
  void responseRequiresCurrentUnamendedPortalPublication(String state) {
    if (state.equals("amended")) {
      publish();
      offer.updateTerms(
          OfferType.FIRM, null, NOW.plusSeconds(7200), null, null, LocalDate.of(2026, 9, 1), NOW);
    } else {
      approveDirectly();
      offer.markSent(NOW);
    }
    assertThatThrownBy(() -> service.respondToOffer(offer.getId(), applicantUser, "ACCEPTED", null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("published");
    verifyNoInteractions(outbox);
  }

  @Test
  void completedConversionSynchronizesStudentIdentityAndIsIdempotent() {
    publish();
    acceptDirectly(true);
    UUID request = UUID.randomUUID(), student = UUID.randomUUID();
    service.completeStudentConversion(
        request, application.getId(), offer.getId(), student, " R260001A ", actor);
    service.completeStudentConversion(
        request, application.getId(), offer.getId(), student, "R260001A", actor);
    assertThat(offer.getStatus()).isEqualTo(OfferStatus.CONVERTED);
    assertThat(offer.getConvertedStudentId()).isEqualTo(student);
    assertThat(offer.getConvertedStudentNumber()).isEqualTo("R260001A");
    assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CONVERTED);
    assertThat(choice.getChoiceStatus()).isEqualTo(ProgrammeChoiceStatus.CONVERTED);
    verify(outbox).enqueueStudentConversionNotification(offer);
    verify(outbox).enqueueCurrentOfferPublicationStatus(offer);
    verify(applicationEvents).save(any());
    verify(offerEvents).save(any());
    assertThatThrownBy(
            () ->
                service.completeStudentConversion(
                    request,
                    application.getId(),
                    offer.getId(),
                    UUID.randomUUID(),
                    "R260002A",
                    actor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("another student");
  }

  @Test
  void conversionRejectsDifferentApplication() {
    UUID otherId = UUID.randomUUID();
    Application other =
        identified(
            new Application(
                UUID.randomUUID(),
                "OTHER",
                "Other intake",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                3,
                application.getApplicant(),
                application.getApplicationType(),
                "APP-2",
                false));
    when(applications.findById(otherId)).thenReturn(Optional.of(other));
    assertThatThrownBy(
            () ->
                service.completeStudentConversion(
                    UUID.randomUUID(),
                    otherId,
                    offer.getId(),
                    UUID.randomUUID(),
                    "R260001A",
                    actor))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not belong");
  }

  @Test
  void conversionCompletionStillEnforcesRequiredConditionClearance() {
    publish();
    acceptDirectly(true);
    when(conditions.countByOfferIdAndRequiredTrueAndStatusAndDeletedAtIsNull(
            offer.getId(), OfferConditionStatus.PENDING))
        .thenReturn(1L);
    assertThatThrownBy(
            () ->
                service.completeStudentConversion(
                    UUID.randomUUID(),
                    application.getId(),
                    offer.getId(),
                    UUID.randomUUID(),
                    "R260001A",
                    actor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Required offer conditions");
    assertThat(offer.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
    verifyNoInteractions(outbox);
  }

  @Test
  void conversionRequiresPriorHandoffAndAcceptedOffer() {
    assertThatThrownBy(
            () ->
                service.completeStudentConversion(
                    UUID.randomUUID(),
                    application.getId(),
                    offer.getId(),
                    UUID.randomUUID(),
                    "R260001A",
                    actor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("accepted offer");
    publish();
    acceptDirectly(false);
    assertThatThrownBy(
            () ->
                service.completeStudentConversion(
                    UUID.randomUUID(),
                    application.getId(),
                    offer.getId(),
                    UUID.randomUUID(),
                    "R260001A",
                    actor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not requested");
  }

  @ParameterizedTest
  @ValueSource(strings = {"request", "student", "number", "blankNumber"})
  void conversionRequiresCompleteStudentIdentifiers(String absent) {
    publish();
    acceptDirectly(true);
    assertThatThrownBy(
            () ->
                service.completeStudentConversion(
                    absent.equals("request") ? null : UUID.randomUUID(),
                    application.getId(),
                    offer.getId(),
                    absent.equals("student") ? null : UUID.randomUUID(),
                    absent.equals("number")
                        ? null
                        : absent.equals("blankNumber") ? " " : "R260001A",
                    actor))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("identifiers");
    assertThat(offer.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"DRAFT", "APPROVED", "SENT", "ACCEPTED", "DECLINED"})
  void applicantOfferListHidesUnpublishedDraftAndApprovalStates(String state) {
    if (state.equals("APPROVED")) approveDirectly();
    else if (!state.equals("DRAFT")) {
      publish();
      if (state.equals("ACCEPTED") || state.equals("DECLINED"))
        offer.respond(OfferResponseType.valueOf(state));
    }
    when(offers.findAllByApplicationApplicantUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            applicantUser))
        .thenReturn(List.of(offer));
    when(offers.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(offer));
    assertThat(service.listOffers()).hasSize(1);
    assertThat(service.listApplicantOffers(applicantUser))
        .hasSize(state.equals("DRAFT") || state.equals("APPROVED") ? 0 : 1);
  }

  @Test
  void offerCommandsFailClearlyWhenOfferIsMissing() {
    assertThatThrownBy(() -> service.hasUnresolvedRequiredConditions(UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Offer not found.");
  }

  private void approveDirectly() {
    offer.linkGeneratedDocument(UUID.randomUUID());
    offer.approve(actor, NOW);
    application.markOffered(REASON);
    choice.markOffered(REASON);
  }

  private void publish() {
    OfferDocumentVersion document = identified(new OfferDocumentVersion(offer, 1, actor, NOW));
    document.store(UUID.randomUUID(), "DOC-1", "documents", "offer.pdf", "checksum", NOW);
    offer.linkCurrentDocumentVersion(document);
    OfferPublication publication =
        identified(new OfferPublication(offer, document, 1, actor, UUID.randomUUID(), NOW));
    offer.publish(publication, actor, NOW);
    application.markOffered(REASON);
    choice.markOffered(REASON);
  }

  private void acceptDirectly(boolean requestConversion) {
    offer.respond(OfferResponseType.ACCEPTED);
    application.recordOfferResponse(OfferResponseType.ACCEPTED, REASON);
    choice.recordOfferResponse(OfferResponseType.ACCEPTED, REASON);
    if (requestConversion) offer.requestConversion(NOW);
  }

  private <T> T identified(T entity) {
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    return entity;
  }
}
