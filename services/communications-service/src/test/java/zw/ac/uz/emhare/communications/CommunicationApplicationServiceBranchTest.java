package zw.ac.uz.emhare.communications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.communications.content.application.CommunicationApplicationService;
import zw.ac.uz.emhare.communications.content.application.EventCalendarService;
import zw.ac.uz.emhare.communications.content.application.StructuredContentValidator;
import zw.ac.uz.emhare.communications.content.application.command.CommunicationCommands.CreateDraftCommand;
import zw.ac.uz.emhare.communications.content.application.command.CommunicationCommands.EventDetailsCommand;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationMediaAsset;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationPublication;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationReadReceipt;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.AttendanceMode;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.ContentKind;
import zw.ac.uz.emhare.communications.content.infrastructure.persistence.CommunicationCategoryRepository;
import zw.ac.uz.emhare.communications.content.infrastructure.persistence.CommunicationItemRepository;
import zw.ac.uz.emhare.communications.content.infrastructure.persistence.CommunicationItemVersionRepository;
import zw.ac.uz.emhare.communications.content.infrastructure.persistence.CommunicationMediaAssetRepository;
import zw.ac.uz.emhare.communications.content.infrastructure.persistence.CommunicationPublicationRepository;
import zw.ac.uz.emhare.communications.content.infrastructure.persistence.CommunicationReadReceiptRepository;
import zw.ac.uz.emhare.communications.content.infrastructure.persistence.CommunicationWorkflowEventRepository;
import zw.ac.uz.emhare.communications.content.infrastructure.persistence.EventOccurrenceRepository;
import zw.ac.uz.emhare.communications.media.infrastructure.CommunicationMediaStorage;

/**
 * @author Tinashe K
 */
@ExtendWith(MockitoExtension.class)
class CommunicationApplicationServiceBranchTest {

  private static final Instant NOW = Instant.parse("2026-08-23T12:00:00Z");

  @Mock private CommunicationCategoryRepository categoryRepository;
  @Mock private CommunicationItemRepository itemRepository;
  @Mock private CommunicationItemVersionRepository versionRepository;
  @Mock private CommunicationPublicationRepository publicationRepository;
  @Mock private CommunicationWorkflowEventRepository workflowEventRepository;
  @Mock private CommunicationMediaAssetRepository mediaAssetRepository;
  @Mock private EventOccurrenceRepository eventOccurrenceRepository;
  @Mock private CommunicationReadReceiptRepository readReceiptRepository;
  @Mock private StructuredContentValidator contentValidator;
  @Mock private EventCalendarService eventCalendarService;
  @Mock private CommunicationMediaStorage mediaStorage;

  private CommunicationApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new CommunicationApplicationService(
            categoryRepository,
            itemRepository,
            versionRepository,
            publicationRepository,
            workflowEventRepository,
            mediaAssetRepository,
            eventOccurrenceRepository,
            readReceiptRepository,
            contentValidator,
            eventCalendarService,
            mediaStorage,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void shouldRejectEveryInvalidKindSpecificDraftCombination() {
    UUID actorUserId = UUID.randomUUID();
    EventDetailsCommand event =
        new EventDetailsCommand(
            NOW.plusSeconds(3600),
            NOW.plusSeconds(7200),
            "Africa/Harare",
            AttendanceMode.HYBRID,
            "Great Hall",
            null,
            "https://meet.example.test");

    assertThrows(
        IllegalArgumentException.class,
        () -> service.createDraft(draft(null, null, null), actorUserId));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.createDraft(draft(ContentKind.LINK, null, null), actorUserId));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.createDraft(draft(ContentKind.LINK, "  ", null), actorUserId));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.createDraft(draft(ContentKind.EVENT, null, null), actorUserId));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.createDraft(draft(ContentKind.NEWS, null, event), actorUserId));
  }

  @Test
  void shouldKeepReadReceiptsIdempotentAcrossExistingNewAndRacingWrites() {
    UUID publicationId = UUID.randomUUID();
    UUID readerUserId = UUID.randomUUID();
    CommunicationPublication publication =
        new CommunicationPublication(
            UUID.randomUUID(), UUID.randomUUID(), NOW.minusSeconds(60), null, false, false, 0, NOW);
    CommunicationReadReceipt existing =
        new CommunicationReadReceipt(publicationId, readerUserId, NOW.minusSeconds(30));
    CommunicationReadReceipt created =
        new CommunicationReadReceipt(publicationId, readerUserId, NOW);
    when(publicationRepository.findById(publicationId)).thenReturn(Optional.of(publication));
    when(readReceiptRepository.findByPublicationIdAndReaderUserId(publicationId, readerUserId))
        .thenReturn(
            Optional.of(existing), Optional.empty(), Optional.empty(), Optional.of(created));
    when(readReceiptRepository.saveAndFlush(any(CommunicationReadReceipt.class)))
        .thenReturn(created)
        .thenThrow(new DataIntegrityViolationException("duplicate"));

    assertEquals(
        existing.getReadAt(), service.recordRead(publicationId, readerUserId).firstReadAt());
    assertEquals(NOW, service.recordRead(publicationId, readerUserId).firstReadAt());
    assertEquals(NOW, service.recordRead(publicationId, readerUserId).firstReadAt());
  }

  @Test
  void shouldValidateMediaSizeAndPreserveOnlySafeFileExtensions() {
    UUID actorUserId = UUID.randomUUID();
    byte[] bytes = {1, 2, 3};
    when(mediaStorage.maximumBytes()).thenReturn(2L, 100L);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.uploadMedia("large.webp", "image/webp", bytes, "Campus", actorUserId));

    when(mediaAssetRepository.save(any(CommunicationMediaAsset.class)))
        .thenAnswer(
            invocation -> {
              CommunicationMediaAsset asset = invocation.getArgument(0);
              ReflectionTestUtils.setField(asset, "id", UUID.randomUUID());
              return asset;
            });
    for (String fileName : new String[] {null, "README", "photo.WEBP", "unsafe.reallylongext"}) {
      assertEquals(
          fileName,
          service.uploadMedia(fileName, "image/webp", bytes, "Campus", actorUserId).fileName());
    }
  }

  @Test
  void shouldReturnAnEmptyPublicHomeWhenNothingIsPublished() {
    when(publicationRepository.findAllPublicAt(
            NOW,
            zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues
                .PublicationStatus.WITHDRAWN))
        .thenReturn(List.of());
    when(itemRepository.findAllById(List.of())).thenReturn(List.of());
    when(versionRepository.findAllById(List.of())).thenReturn(List.of());
    when(eventOccurrenceRepository.findAllByCommunicationVersionIdIn(java.util.Set.of()))
        .thenReturn(List.of());

    var home = service.publicHome();

    assertEquals(List.of(), home.urgentNotices());
    assertEquals(List.of(), home.importantLinks());
    assertEquals(null, home.featuredCampaign());
    assertEquals(List.of(), home.upcomingEvents());
    assertEquals(List.of(), home.latestNews());
  }

  private CreateDraftCommand draft(
      ContentKind kind, String externalUrl, EventDetailsCommand eventDetails) {
    return new CreateDraftCommand(
        kind, null, null, "University update", "Summary", "[]", null, externalUrl, eventDetails);
  }
}
