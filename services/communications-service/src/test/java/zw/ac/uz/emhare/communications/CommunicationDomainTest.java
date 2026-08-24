package zw.ac.uz.emhare.communications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.communications.content.application.EventCalendarService;
import zw.ac.uz.emhare.communications.content.application.StructuredContentValidator;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationCategory;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationItem;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationItemVersion;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationMediaAsset;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationPublication;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.AttendanceMode;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.ContentKind;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.ItemLifecycleStatus;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.MediaStatus;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.PublicationStatus;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.WorkflowStatus;
import zw.ac.uz.emhare.communications.content.domain.model.EventOccurrence;

/** Focused workflow, publication, structured content, event, and media tests. @author Tinashe K */
class CommunicationDomainTest {

  private static final UUID ITEM_ID = UUID.randomUUID();
  private static final UUID AUTHOR_ID = UUID.randomUUID();
  private static final UUID APPROVER_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-08-17T10:00:00Z");

  @Test
  void requiresIndependentApprovalAndMakesApprovedVersionUneditable() {
    CommunicationItemVersion version = draft();
    version.submit(AUTHOR_ID, NOW, 0);

    IllegalArgumentException selfApproval =
        assertThrows(IllegalArgumentException.class, () -> version.approve(AUTHOR_ID, NOW, 0));
    assertEquals("Authors cannot approve their own content version.", selfApproval.getMessage());

    version.approve(APPROVER_ID, NOW, 0);
    assertEquals(WorkflowStatus.APPROVED, version.getWorkflowStatus());
    assertThrows(
        IllegalStateException.class, () -> version.edit("Changed", "Changed", "[]", null, null, 0));
  }

  @Test
  void validatesVersionTransitionsAndReopensRejectedContentAsDraft() {
    CommunicationItemVersion version = draft();

    assertThrows(
        IllegalArgumentException.class,
        () -> version.edit("Title", "Summary", "[]", null, null, 1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CommunicationItemVersion(ITEM_ID, 1, " ", "Summary", "[]", AUTHOR_ID, null, null));

    version.submit(AUTHOR_ID, NOW, 0);
    assertEquals(AUTHOR_ID, version.getSubmittedByUserId());
    assertEquals(NOW, version.getSubmittedAt());
    assertThrows(IllegalStateException.class, () -> version.submit(AUTHOR_ID, NOW, 0));
    assertThrows(IllegalArgumentException.class, () -> version.reject(APPROVER_ID, NOW, " ", 0));

    version.reject(APPROVER_ID, NOW, " Needs a clearer call to action ", 0);
    assertEquals(WorkflowStatus.REJECTED, version.getWorkflowStatus());
    assertEquals(APPROVER_ID, version.getDecidedByUserId());
    assertEquals("Needs a clearer call to action", version.getDecisionReason());
    assertThrows(IllegalStateException.class, () -> version.approve(APPROVER_ID, NOW, 0));

    UUID heroMediaAssetId = UUID.randomUUID();
    version.edit(
        " Revised title ",
        " Revised summary ",
        "[]",
        heroMediaAssetId,
        " https://example.test/details ",
        0);
    assertEquals(WorkflowStatus.DRAFT, version.getWorkflowStatus());
    assertEquals("Revised title", version.getTitle());
    assertEquals("Revised summary", version.getSummary());
    assertEquals(heroMediaAssetId, version.getHeroMediaAssetId());
    assertEquals("https://example.test/details", version.getExternalUrl());
    assertNull(version.getDecidedByUserId());
    assertNull(version.getDecidedAt());
    assertNull(version.getDecisionReason());
    assertThrows(IllegalStateException.class, () -> version.reject(APPROVER_ID, NOW, "Reason", 0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CommunicationItemVersion(ITEM_ID, 1, null, "Summary", "[]", AUTHOR_ID, null, null));
    version.edit("Title", "Summary", "[]", null, " ", 0);
    assertNull(version.getExternalUrl());
  }

  @Test
  void resolvesScheduledLiveExpiredAndWithdrawnPublicationWindows() {
    CommunicationPublication future = publication(NOW.plusSeconds(60), NOW.plusSeconds(120));
    CommunicationPublication live = publication(NOW.minusSeconds(60), NOW.plusSeconds(60));
    CommunicationPublication expired = publication(NOW.minusSeconds(120), NOW.minusSeconds(60));

    assertEquals(PublicationStatus.SCHEDULED, future.effectiveStatus(NOW));
    assertEquals(PublicationStatus.LIVE, live.effectiveStatus(NOW));
    assertTrue(live.isPublicAt(NOW));
    assertEquals(PublicationStatus.EXPIRED, expired.effectiveStatus(NOW));
    assertFalse(expired.isPublicAt(NOW));

    live.withdraw(APPROVER_ID, NOW, "Incorrect public date", 0);
    assertEquals(PublicationStatus.WITHDRAWN, live.effectiveStatus(NOW));
    assertEquals(NOW, live.getWithdrawnAt());
    assertEquals("Incorrect public date", live.getWithdrawalReason());
    live.withdraw(APPROVER_ID, NOW.plusSeconds(1), "Already withdrawn", 0);
    assertEquals(NOW, live.getWithdrawnAt());
  }

  @Test
  void validatesPublicationWindowAndWithdrawalConcurrency() {
    assertThrows(IllegalArgumentException.class, () -> publication(NOW, NOW));
    CommunicationPublication openEnded = publication(NOW.minusSeconds(1), null);
    assertEquals(PublicationStatus.LIVE, openEnded.effectiveStatus(NOW.plusSeconds(86_400)));
    assertThrows(
        IllegalArgumentException.class, () -> openEnded.withdraw(APPROVER_ID, NOW, "Reason", 1));
    assertThrows(
        IllegalArgumentException.class, () -> openEnded.withdraw(APPROVER_ID, NOW, null, 0));
    assertThrows(
        IllegalArgumentException.class, () -> openEnded.withdraw(APPROVER_ID, NOW, " ", 0));
    assertEquals(ITEM_ID, openEnded.getItemId());
    assertTrue(openEnded.getVersionId() != null);
    assertFalse(openEnded.isPinned());
    assertFalse(openEnded.isFeatured());
    assertEquals(0, openEnded.getDisplayOrder());
  }

  @Test
  void rejectsRawHtmlAndRequiresImageAlternativeText() {
    StructuredContentValidator validator = new StructuredContentValidator(new ObjectMapper());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            validator.validateAndNormalize(
                "[{\"type\":\"PARAGRAPH\",\"text\":\"<script>alert(1)</script>\"}]"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            validator.validateAndNormalize(
                "[{\"type\":\"IMAGE\",\"mediaAssetId\":\"asset-1\",\"alternativeText\":\"\"}]"));
    assertEquals(
        "[{\"type\":\"PARAGRAPH\",\"text\":\"Safe public copy\"}]",
        validator.validateAndNormalize("[{\"type\":\"PARAGRAPH\",\"text\":\"Safe public copy\"}]"));
  }

  @Test
  void validatesEveryStructuredContentBoundary() {
    StructuredContentValidator validator = new StructuredContentValidator(new ObjectMapper());

    assertThrows(IllegalArgumentException.class, () -> validator.validateAndNormalize("{"));
    assertThrows(IllegalArgumentException.class, () -> validator.validateAndNormalize("{}"));
    assertThrows(IllegalArgumentException.class, () -> validator.validateAndNormalize("[1]"));
    assertThrows(
        IllegalArgumentException.class,
        () -> validator.validateAndNormalize("[{\"type\":\"VIDEO\"}]"));
    assertThrows(
        IllegalArgumentException.class,
        () -> validator.validateAndNormalize("[{\"type\":\"PARAGRAPH\",\"rawHtml\":\"safe\"}]"));
    assertThrows(
        IllegalArgumentException.class,
        () -> validator.validateAndNormalize("[{\"type\":\"LINKS\"}]"));
    assertThrows(
        IllegalArgumentException.class,
        () -> validator.validateAndNormalize("[{\"type\":\"LINKS\",\"links\":[]}]"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            validator.validateAndNormalize(
                "[{\"type\":\"LINKS\",\"links\":[{\"label\":\"Mail\",\"url\":\"mailto:test@example.test\"}]}]"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            validator.validateAndNormalize(
                "[{\"type\":\"LINKS\",\"links\":[{\"label\":\"Help\",\"url\":\"help\"}]}]"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            validator.validateAndNormalize(
                "[{\"type\":\"LINKS\",\"links\":[{\"label\":\"Help\",\"url\":\"http://[\"}]}]"));
    assertThrows(
        IllegalArgumentException.class,
        () -> validator.validateAndNormalize("[{\"type\":\"LINKS\",\"links\":{}}]"));
    assertThrows(
        IllegalArgumentException.class,
        () -> validator.validateAndNormalize("[{\"type\":\"PARAGRAPH\"}]"));
    assertThrows(
        IllegalArgumentException.class,
        () -> validator.validateAndNormalize("[{\"type\":\"LIST\"}]"));
    assertThrows(
        IllegalArgumentException.class,
        () -> validator.validateAndNormalize("[{\"type\":\"LIST\",\"items\":{}}]"));
    assertThrows(
        IllegalArgumentException.class,
        () -> validator.validateAndNormalize("[{\"type\":\"LIST\",\"items\":[]}]"));
    assertThrows(
        IllegalArgumentException.class,
        () -> validator.validateAndNormalize("[{\"type\":\"LIST\",\"items\":[1]}]"));
    assertThrows(
        IllegalArgumentException.class,
        () -> validator.validateAndNormalize("[{\"type\":\"LIST\",\"items\":[\" \" ]}]"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            validator.validateAndNormalize(
                "[{\"type\":\"IMAGE\",\"alternativeText\":\"Campus\"}]"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            validator.validateAndNormalize(
                "[{\"type\":\"LINKS\",\"links\":[{\"url\":\"/help\"}]}]"));

    String validBlocks =
        "[{\"type\":\"HEADING\",\"text\":\"Welcome\"},"
            + "{\"type\":\"LIST\",\"items\":[\"Apply\"]},"
            + "{\"type\":\"LINKS\",\"links\":["
            + "{\"label\":\"Internal\",\"url\":\"/student\"},"
            + "{\"label\":\"External\",\"url\":\"https://example.test\"}]}]";
    assertEquals(validBlocks, validator.validateAndNormalize(validBlocks));
    assertTrue(validator.read(validBlocks).isArray());
    assertThrows(IllegalStateException.class, () -> validator.read("{"));
  }

  @Test
  void validatesTimezoneEventWindowAndAttendanceLocation() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new EventOccurrence(
                UUID.randomUUID(),
                NOW,
                NOW.minusSeconds(1),
                "Africa/Harare",
                AttendanceMode.IN_PERSON,
                "Great Hall",
                null,
                null));
    assertThrows(
        java.time.DateTimeException.class,
        () ->
            new EventOccurrence(
                UUID.randomUUID(),
                NOW,
                NOW.plusSeconds(3600),
                "Africa/Nowhere",
                AttendanceMode.ONLINE,
                null,
                null,
                "https://example.test/event"));
  }

  @Test
  void validatesEventAttendanceModesAndUpdatesTimezoneAwareDetails() {
    UUID versionId = UUID.randomUUID();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new EventOccurrence(
                versionId, NOW, NOW.plusSeconds(60), "Africa/Harare", null, null, null, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new EventOccurrence(
                versionId,
                NOW,
                NOW.plusSeconds(60),
                "Africa/Harare",
                AttendanceMode.IN_PERSON,
                " ",
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new EventOccurrence(
                versionId,
                NOW,
                NOW.plusSeconds(60),
                "Africa/Harare",
                AttendanceMode.ONLINE,
                null,
                null,
                " "));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new EventOccurrence(
                versionId,
                NOW,
                NOW.plusSeconds(60),
                "Africa/Harare",
                AttendanceMode.HYBRID,
                "Great Hall",
                null,
                null));

    EventOccurrence occurrence =
        new EventOccurrence(
            versionId,
            NOW,
            NOW.plusSeconds(3600),
            "Africa/Harare",
            AttendanceMode.HYBRID,
            " Great Hall ",
            " Main Campus ",
            " https://example.test/live ");
    occurrence.update(
        NOW.plusSeconds(7200),
        NOW.plusSeconds(10_800),
        "Africa/Johannesburg",
        AttendanceMode.ONLINE,
        null,
        " ",
        " https://example.test/revised ");
    assertEquals(versionId, occurrence.getCommunicationVersionId());
    assertEquals(NOW.plusSeconds(7200), occurrence.getStartsAt());
    assertEquals(NOW.plusSeconds(10_800), occurrence.getEndsAt());
    assertEquals("Africa/Johannesburg", occurrence.getTimezone());
    assertEquals(AttendanceMode.ONLINE, occurrence.getAttendanceMode());
    assertNull(occurrence.getVenueName());
    assertNull(occurrence.getAddress());
    assertEquals("https://example.test/revised", occurrence.getOnlineUrl());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            occurrence.update(
                null,
                NOW,
                "Africa/Harare",
                AttendanceMode.ONLINE,
                null,
                null,
                "https://example.test"));
  }

  @Test
  void validatesPublicMediaTypeSizeAndAlternativeText() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CommunicationMediaAsset.validate("image/svg+xml", 100, "Diagram"));
    assertThrows(
        IllegalArgumentException.class,
        () -> CommunicationMediaAsset.validate("image/png", 100, " "));
    assertThrows(
        IllegalArgumentException.class,
        () -> CommunicationMediaAsset.validate("image/png", 0, "Description"));
    assertThrows(
        IllegalArgumentException.class,
        () -> CommunicationMediaAsset.validate("image/png", 100, null));
    CommunicationMediaAsset asset =
        new CommunicationMediaAsset(
            "communications/image.webp",
            "lecture.webp",
            "image/webp",
            100,
            "checksum",
            " Students in a lecture theatre ",
            AUTHOR_ID);
    assertEquals("communications/image.webp", asset.getStorageKey());
    assertEquals("lecture.webp", asset.getOriginalFileName());
    assertEquals("image/webp", asset.getContentType());
    assertEquals(100, asset.getSizeBytes());
    assertEquals("checksum", asset.getChecksumSha256());
    assertEquals("Students in a lecture theatre", asset.getAlternativeText());
    assertEquals(MediaStatus.READY, asset.getStatus());
  }

  @Test
  void validatesCategoryAndStableItemIdentityBoundaries() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CommunicationCategory(null, "News", null, 1, true));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CommunicationCategory("NEWS", " ", null, 1, true));
    CommunicationCategory category =
        new CommunicationCategory(" news ", " News ", "Updates", 1, true);
    assertEquals("NEWS", category.getCode());
    assertEquals("News", category.getName());
    assertThrows(IllegalArgumentException.class, () -> category.update("News", null, 2, false, 1));
    category.update("Notices", "Public notices", 2, false, 0);
    assertEquals("Notices", category.getName());
    assertEquals("Public notices", category.getDescription());
    assertEquals(2, category.getDisplayOrder());
    assertFalse(category.isActive());

    assertThrows(IllegalArgumentException.class, () -> CommunicationItem.normalizeSlug(null));
    assertThrows(IllegalArgumentException.class, () -> CommunicationItem.normalizeSlug(" "));
    assertThrows(IllegalArgumentException.class, () -> CommunicationItem.normalizeSlug("---"));
    UUID categoryId = UUID.randomUUID();
    CommunicationItem item = new CommunicationItem(ContentKind.NEWS, " Open Day 2026 ", categoryId);
    assertEquals("open-day-2026", item.getSlug());
    assertEquals(ContentKind.NEWS, item.getKind());
    assertEquals(categoryId, item.getCategoryId());
    assertEquals(ItemLifecycleStatus.ACTIVE, item.getLifecycleStatus());
    item.archive();
    assertEquals(ItemLifecycleStatus.ARCHIVED, item.getLifecycleStatus());
  }

  @Test
  void generatesEscapedCalendarLocationsForPhysicalAndOnlineEvents() {
    CommunicationItem item = mock(CommunicationItem.class);
    CommunicationItemVersion version = mock(CommunicationItemVersion.class);
    EventOccurrence occurrence = mock(EventOccurrence.class);
    when(version.getId()).thenReturn(UUID.randomUUID());
    when(version.getUpdatedAt()).thenReturn(NOW);
    when(version.getTitle()).thenReturn("Open Day, 2026");
    when(version.getSummary()).thenReturn("Welcome; students\nBring documents");
    when(occurrence.getTimezone()).thenReturn("Africa/Harare");
    when(occurrence.getStartsAt()).thenReturn(NOW);
    when(occurrence.getEndsAt()).thenReturn(NOW.plusSeconds(3600));
    when(occurrence.getVenueName()).thenReturn("Great Hall");
    when(occurrence.getAddress()).thenReturn("Main Campus");

    EventCalendarService calendarService = new EventCalendarService();
    String physical =
        calendarService.generate(item, version, occurrence, "https://emhare.uz.ac.zw/events/open");
    assertTrue(physical.contains("LOCATION:Great Hall\\, Main Campus"));
    assertTrue(physical.contains("SUMMARY:Open Day\\, 2026"));
    assertTrue(physical.contains("DESCRIPTION:Welcome\\; students\\nBring documents"));

    when(occurrence.getVenueName()).thenReturn(null);
    when(occurrence.getOnlineUrl()).thenReturn(null);
    String online =
        calendarService.generate(item, version, occurrence, "https://emhare.uz.ac.zw/events/open");
    assertTrue(online.contains("LOCATION:"));
  }

  private CommunicationItemVersion draft() {
    return new CommunicationItemVersion(
        ITEM_ID, 1, "Public title", "Public summary", "[]", AUTHOR_ID, null, null);
  }

  private CommunicationPublication publication(Instant from, Instant until) {
    return new CommunicationPublication(
        ITEM_ID, UUID.randomUUID(), from, until, false, false, 0, NOW);
  }
}
