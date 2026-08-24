package zw.ac.uz.emhare.communications.content.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.communications.content.application.CommunicationViews.CategoryView;
import zw.ac.uz.emhare.communications.content.application.CommunicationViews.EditorialDetailView;
import zw.ac.uz.emhare.communications.content.application.CommunicationViews.EditorialItemView;
import zw.ac.uz.emhare.communications.content.application.CommunicationViews.EventView;
import zw.ac.uz.emhare.communications.content.application.CommunicationViews.MediaContent;
import zw.ac.uz.emhare.communications.content.application.CommunicationViews.MediaView;
import zw.ac.uz.emhare.communications.content.application.CommunicationViews.PageView;
import zw.ac.uz.emhare.communications.content.application.CommunicationViews.PublicHomeView;
import zw.ac.uz.emhare.communications.content.application.CommunicationViews.PublicItemView;
import zw.ac.uz.emhare.communications.content.application.CommunicationViews.ReadReceiptView;
import zw.ac.uz.emhare.communications.content.application.command.CommunicationCommands.CategoryCommand;
import zw.ac.uz.emhare.communications.content.application.command.CommunicationCommands.CreateDraftCommand;
import zw.ac.uz.emhare.communications.content.application.command.CommunicationCommands.EditVersionCommand;
import zw.ac.uz.emhare.communications.content.application.command.CommunicationCommands.EventDetailsCommand;
import zw.ac.uz.emhare.communications.content.application.command.CommunicationCommands.SchedulePublicationCommand;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationCategory;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationItem;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationItemVersion;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationMediaAsset;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationPublication;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationReadReceipt;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.ContentKind;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.PublicationStatus;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.WorkflowStatus;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationWorkflowEvent;
import zw.ac.uz.emhare.communications.content.domain.model.EventOccurrence;
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
 * Owns editorial workflow, publication, public projections, media, and read receipts. @author
 * Tinashe K
 */
@Service
public class CommunicationApplicationService {

  private final CommunicationCategoryRepository categoryRepository;
  private final CommunicationItemRepository itemRepository;
  private final CommunicationItemVersionRepository versionRepository;
  private final CommunicationPublicationRepository publicationRepository;
  private final CommunicationWorkflowEventRepository workflowEventRepository;
  private final CommunicationMediaAssetRepository mediaAssetRepository;
  private final EventOccurrenceRepository eventOccurrenceRepository;
  private final CommunicationReadReceiptRepository readReceiptRepository;
  private final StructuredContentValidator contentValidator;
  private final EventCalendarService eventCalendarService;
  private final CommunicationMediaStorage mediaStorage;
  private final Clock clock;

  public CommunicationApplicationService(
      CommunicationCategoryRepository categoryRepository,
      CommunicationItemRepository itemRepository,
      CommunicationItemVersionRepository versionRepository,
      CommunicationPublicationRepository publicationRepository,
      CommunicationWorkflowEventRepository workflowEventRepository,
      CommunicationMediaAssetRepository mediaAssetRepository,
      EventOccurrenceRepository eventOccurrenceRepository,
      CommunicationReadReceiptRepository readReceiptRepository,
      StructuredContentValidator contentValidator,
      EventCalendarService eventCalendarService,
      CommunicationMediaStorage mediaStorage,
      Clock clock) {
    this.categoryRepository = categoryRepository;
    this.itemRepository = itemRepository;
    this.versionRepository = versionRepository;
    this.publicationRepository = publicationRepository;
    this.workflowEventRepository = workflowEventRepository;
    this.mediaAssetRepository = mediaAssetRepository;
    this.eventOccurrenceRepository = eventOccurrenceRepository;
    this.readReceiptRepository = readReceiptRepository;
    this.contentValidator = contentValidator;
    this.eventCalendarService = eventCalendarService;
    this.mediaStorage = mediaStorage;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public PublicHomeView publicHome() {
    List<PublicItemView> items = publicItems();
    List<PublicItemView> urgent =
        items.stream()
            .filter(item -> item.kind() == ContentKind.ALERT || item.kind() == ContentKind.NOTICE)
            .sorted(
                Comparator.comparing(PublicItemView::pinned)
                    .reversed()
                    .thenComparing(PublicItemView::publishFrom)
                    .reversed())
            .limit(6)
            .toList();
    List<PublicItemView> links =
        items.stream().filter(item -> item.kind() == ContentKind.LINK).limit(8).toList();
    PublicItemView campaign =
        items.stream()
            .filter(item -> item.kind() == ContentKind.CAMPAIGN && item.featured())
            .findFirst()
            .orElseGet(
                () ->
                    items.stream()
                        .filter(item -> item.kind() == ContentKind.CAMPAIGN)
                        .findFirst()
                        .orElse(null));
    Instant now = clock.instant();
    List<PublicItemView> events =
        items.stream()
            .filter(
                item ->
                    item.kind() == ContentKind.EVENT
                        && item.event() != null
                        && item.event().endsAt().isAfter(now))
            .sorted(Comparator.comparing(item -> item.event().startsAt()))
            .limit(6)
            .toList();
    List<PublicItemView> news =
        items.stream()
            .filter(item -> item.kind() == ContentKind.NEWS)
            .sorted(Comparator.comparing(PublicItemView::publishFrom).reversed())
            .limit(6)
            .toList();
    return new PublicHomeView(urgent, links, campaign, events, news);
  }

  @Transactional(readOnly = true)
  public PublicItemView publicItem(String slug) {
    CommunicationItem item =
        itemRepository
            .findBySlugIgnoreCase(slug)
            .orElseThrow(() -> new CommunicationNotFoundException("Public item was not found."));
    return publicItems().stream()
        .filter(candidate -> candidate.itemId().equals(item.getId()))
        .findFirst()
        .orElseThrow(() -> new CommunicationNotFoundException("Public item was not found."));
  }

  @Transactional(readOnly = true)
  public String eventCalendar(String slug, String canonicalUrl) {
    PublicItemView publicItem = publicItem(slug);
    if (publicItem.kind() != ContentKind.EVENT) {
      throw new CommunicationNotFoundException("Public event was not found.");
    }
    CommunicationItem item = requiredItem(publicItem.itemId());
    CommunicationItemVersion version = requiredVersion(publicItem.versionId());
    EventOccurrence occurrence =
        eventOccurrenceRepository
            .findByCommunicationVersionId(version.getId())
            .orElseThrow(() -> new CommunicationNotFoundException("Public event was not found."));
    return eventCalendarService.generate(item, version, occurrence, canonicalUrl);
  }

  @Transactional(readOnly = true)
  public PageView<EditorialItemView> editorialQueue(
      String query, ContentKind kind, int page, int size) {
    String normalizedQuery = query == null || query.isBlank() ? "" : query.trim();
    Page<CommunicationItem> result =
        itemRepository.search(
            normalizedQuery,
            kind,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")));
    List<EditorialItemView> items = result.getContent().stream().map(this::editorialView).toList();
    return new PageView<>(
        items,
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public EditorialDetailView editorialDetail(UUID itemId) {
    CommunicationItem item = requiredItem(itemId);
    CommunicationItemVersion version =
        versionRepository
            .findTopByItemIdOrderByVersionNumberDesc(itemId)
            .orElseThrow(
                () -> new CommunicationNotFoundException("Communication version was not found."));
    EventOccurrence occurrence =
        eventOccurrenceRepository.findByCommunicationVersionId(version.getId()).orElse(null);
    return new EditorialDetailView(
        editorialView(item, version),
        item.getCategoryId(),
        contentValidator.read(version.getStructuredContent()),
        version.getHeroMediaAssetId(),
        version.getExternalUrl(),
        occurrence == null ? null : eventView(occurrence));
  }

  @Transactional
  public EditorialItemView createDraft(CreateDraftCommand command, UUID actorUserId) {
    validateKindSpecificFields(command.kind(), command.externalUrl(), command.event());
    String content = contentValidator.validateAndNormalize(command.structuredContent());
    String slug =
        command.slug() == null || command.slug().isBlank()
            ? generateAvailableSlug(command.title())
            : command.slug();
    CommunicationItem item =
        itemRepository.save(new CommunicationItem(command.kind(), slug, command.categoryId()));
    CommunicationItemVersion version =
        versionRepository.save(
            new CommunicationItemVersion(
                item.getId(),
                1,
                command.title(),
                command.summary(),
                content,
                actorUserId,
                command.heroMediaAssetId(),
                command.externalUrl()));
    saveEventOccurrence(command.kind(), command.event(), version.getId());
    workflowEventRepository.save(
        new CommunicationWorkflowEvent(
            item.getId(),
            version.getId(),
            "DRAFT_CREATED",
            null,
            WorkflowStatus.DRAFT,
            actorUserId,
            null,
            clock.instant()));
    return editorialView(item, version);
  }

  private String generateAvailableSlug(String title) {
    String normalizedTitle =
        Normalizer.normalize(title, Normalizer.Form.NFKD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT);
    String baseSlug = normalizedTitle.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    if (baseSlug.isBlank()) {
      baseSlug = "public-item";
    }
    baseSlug = baseSlug.substring(0, Math.min(baseSlug.length(), 180)).replaceAll("-+$", "");
    String availableSlug = baseSlug;
    int suffixNumber = 2;
    while (itemRepository.existsBySlugIgnoreCase(availableSlug)) {
      String suffix = "-" + suffixNumber++;
      int maximumBaseLength = 180 - suffix.length();
      String truncatedBase =
          baseSlug
              .substring(0, Math.min(baseSlug.length(), maximumBaseLength))
              .replaceAll("-+$", "");
      availableSlug = truncatedBase + suffix;
    }
    return availableSlug;
  }

  @Transactional
  public EditorialItemView editVersion(
      UUID versionId, EditVersionCommand command, UUID actorUserId) {
    CommunicationItemVersion version = requiredVersion(versionId);
    CommunicationItem item = requiredItem(version.getItemId());
    validateKindSpecificFields(item.getKind(), command.externalUrl(), command.event());
    String content = contentValidator.validateAndNormalize(command.structuredContent());
    version.edit(
        command.title(),
        command.summary(),
        content,
        command.heroMediaAssetId(),
        command.externalUrl(),
        command.expectedVersion());
    updateEventOccurrence(item.getKind(), command.event(), version.getId());
    versionRepository.save(version);
    workflowEventRepository.save(
        new CommunicationWorkflowEvent(
            item.getId(),
            version.getId(),
            "DRAFT_EDITED",
            WorkflowStatus.DRAFT,
            WorkflowStatus.DRAFT,
            actorUserId,
            null,
            clock.instant()));
    return editorialView(item, version);
  }

  @Transactional
  public EditorialItemView createCorrection(UUID itemId, UUID actorUserId) {
    CommunicationItem item = requiredItem(itemId);
    CommunicationItemVersion source =
        versionRepository
            .findTopByItemIdOrderByVersionNumberDesc(itemId)
            .filter(version -> version.getWorkflowStatus() == WorkflowStatus.APPROVED)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Only approved content can start a correction version."));
    CommunicationItemVersion correction =
        versionRepository.save(
            new CommunicationItemVersion(
                itemId,
                source.getVersionNumber() + 1,
                source.getTitle(),
                source.getSummary(),
                source.getStructuredContent(),
                actorUserId,
                source.getHeroMediaAssetId(),
                source.getExternalUrl()));
    eventOccurrenceRepository
        .findByCommunicationVersionId(source.getId())
        .ifPresent(
            occurrence ->
                eventOccurrenceRepository.save(
                    new EventOccurrence(
                        correction.getId(),
                        occurrence.getStartsAt(),
                        occurrence.getEndsAt(),
                        occurrence.getTimezone(),
                        occurrence.getAttendanceMode(),
                        occurrence.getVenueName(),
                        occurrence.getAddress(),
                        occurrence.getOnlineUrl())));
    workflowEventRepository.save(
        new CommunicationWorkflowEvent(
            itemId,
            correction.getId(),
            "CORRECTION_CREATED",
            null,
            WorkflowStatus.DRAFT,
            actorUserId,
            "Correction of approved version " + source.getVersionNumber(),
            clock.instant()));
    return editorialView(item, correction);
  }

  @Transactional
  public EditorialItemView submit(UUID versionId, long expectedVersion, UUID actorUserId) {
    CommunicationItemVersion version = requiredVersion(versionId);
    WorkflowStatus from = version.getWorkflowStatus();
    version.submit(actorUserId, clock.instant(), expectedVersion);
    versionRepository.save(version);
    recordWorkflow(version, "SUBMITTED", from, WorkflowStatus.IN_REVIEW, actorUserId, null);
    return editorialView(requiredItem(version.getItemId()), version);
  }

  @Transactional
  public EditorialItemView approve(UUID versionId, long expectedVersion, UUID actorUserId) {
    CommunicationItemVersion version = requiredVersion(versionId);
    version.approve(actorUserId, clock.instant(), expectedVersion);
    versionRepository.save(version);
    recordWorkflow(
        version, "APPROVED", WorkflowStatus.IN_REVIEW, WorkflowStatus.APPROVED, actorUserId, null);
    return editorialView(requiredItem(version.getItemId()), version);
  }

  @Transactional
  public EditorialItemView reject(
      UUID versionId, long expectedVersion, String reason, UUID actorUserId) {
    CommunicationItemVersion version = requiredVersion(versionId);
    version.reject(actorUserId, clock.instant(), reason, expectedVersion);
    versionRepository.save(version);
    recordWorkflow(
        version,
        "REJECTED",
        WorkflowStatus.IN_REVIEW,
        WorkflowStatus.REJECTED,
        actorUserId,
        reason);
    return editorialView(requiredItem(version.getItemId()), version);
  }

  @Transactional
  public EditorialItemView schedule(
      UUID versionId, SchedulePublicationCommand command, UUID actorUserId) {
    CommunicationItemVersion version = requiredVersion(versionId);
    if (version.getWorkflowStatus() != WorkflowStatus.APPROVED) {
      throw new IllegalStateException("Only an approved version can be scheduled.");
    }
    if (publicationRepository.findByVersionId(versionId).isPresent()) {
      throw new IllegalStateException("This version already has a publication.");
    }
    publicationRepository.save(
        new CommunicationPublication(
            version.getItemId(),
            versionId,
            command.publishFrom(),
            command.publishUntil(),
            command.pinned(),
            command.featured(),
            command.displayOrder(),
            clock.instant()));
    return editorialView(requiredItem(version.getItemId()), version);
  }

  @Transactional
  public EditorialItemView withdraw(
      UUID publicationId, long expectedVersion, String reason, UUID actorUserId) {
    CommunicationPublication publication =
        publicationRepository
            .findById(publicationId)
            .orElseThrow(() -> new CommunicationNotFoundException("Publication was not found."));
    publication.withdraw(actorUserId, clock.instant(), reason, expectedVersion);
    publicationRepository.save(publication);
    return editorialView(
        requiredItem(publication.getItemId()), requiredVersion(publication.getVersionId()));
  }

  @Transactional
  public ReadReceiptView recordRead(UUID publicationId, UUID readerUserId) {
    publicationRepository
        .findById(publicationId)
        .orElseThrow(() -> new CommunicationNotFoundException("Publication was not found."));
    Optional<CommunicationReadReceipt> existing =
        readReceiptRepository.findByPublicationIdAndReaderUserId(publicationId, readerUserId);
    if (existing.isPresent()) {
      return new ReadReceiptView(publicationId, existing.get().getReadAt());
    }
    try {
      CommunicationReadReceipt saved =
          readReceiptRepository.saveAndFlush(
              new CommunicationReadReceipt(publicationId, readerUserId, clock.instant()));
      return new ReadReceiptView(publicationId, saved.getReadAt());
    } catch (DataIntegrityViolationException race) {
      CommunicationReadReceipt receipt =
          readReceiptRepository
              .findByPublicationIdAndReaderUserId(publicationId, readerUserId)
              .orElseThrow(() -> race);
      return new ReadReceiptView(publicationId, receipt.getReadAt());
    }
  }

  @Transactional(readOnly = true)
  public List<CategoryView> categories() {
    return categoryRepository.findAllByOrderByDisplayOrderAscNameAsc().stream()
        .map(this::categoryView)
        .toList();
  }

  @Transactional
  public CategoryView createCategory(CategoryCommand command) {
    return categoryView(
        categoryRepository.save(
            new CommunicationCategory(
                command.code(),
                command.name(),
                command.description(),
                command.displayOrder(),
                command.active())));
  }

  @Transactional
  public CategoryView updateCategory(
      UUID categoryId, CategoryCommand command, long expectedVersion) {
    CommunicationCategory category =
        categoryRepository
            .findById(categoryId)
            .orElseThrow(() -> new CommunicationNotFoundException("Category was not found."));
    category.update(
        command.name(),
        command.description(),
        command.displayOrder(),
        command.active(),
        expectedVersion);
    return categoryView(categoryRepository.save(category));
  }

  @Transactional
  public MediaView uploadMedia(
      String fileName, String contentType, byte[] bytes, String alternativeText, UUID actorUserId) {
    CommunicationMediaAsset.validate(contentType, bytes.length, alternativeText);
    if (bytes.length > mediaStorage.maximumBytes()) {
      throw new IllegalArgumentException("Media file exceeds the configured maximum size.");
    }
    String checksum = sha256(bytes);
    String storageKey = "public/" + UUID.randomUUID() + safeExtension(fileName);
    mediaStorage.store(storageKey, contentType, bytes);
    CommunicationMediaAsset asset =
        mediaAssetRepository.save(
            new CommunicationMediaAsset(
                storageKey,
                fileName,
                contentType,
                bytes.length,
                checksum,
                alternativeText,
                actorUserId));
    return mediaView(asset);
  }

  @Transactional(readOnly = true)
  public MediaContent publicMedia(UUID assetId) {
    CommunicationMediaAsset asset =
        mediaAssetRepository
            .findById(assetId)
            .orElseThrow(() -> new CommunicationNotFoundException("Media asset was not found."));
    return new MediaContent(
        asset.getContentType(),
        asset.getOriginalFileName(),
        mediaStorage.read(asset.getStorageKey()));
  }

  private List<PublicItemView> publicItems() {
    Instant now = clock.instant();
    List<CommunicationPublication> publications =
        publicationRepository.findAllPublicAt(now, PublicationStatus.WITHDRAWN).stream()
            .filter(publication -> publication.isPublicAt(now))
            .toList();
    Map<UUID, CommunicationItem> items =
        itemRepository
            .findAllById(publications.stream().map(CommunicationPublication::getItemId).toList())
            .stream()
            .collect(Collectors.toMap(CommunicationItem::getId, Function.identity()));
    Map<UUID, CommunicationItemVersion> versions =
        versionRepository
            .findAllById(publications.stream().map(CommunicationPublication::getVersionId).toList())
            .stream()
            .filter(version -> version.getWorkflowStatus() == WorkflowStatus.APPROVED)
            .collect(Collectors.toMap(CommunicationItemVersion::getId, Function.identity()));
    Map<UUID, EventOccurrence> events =
        eventOccurrenceRepository.findAllByCommunicationVersionIdIn(versions.keySet()).stream()
            .collect(
                Collectors.toMap(EventOccurrence::getCommunicationVersionId, Function.identity()));
    return publications.stream()
        .filter(publication -> items.containsKey(publication.getItemId()))
        .filter(publication -> versions.containsKey(publication.getVersionId()))
        .map(
            publication ->
                publicView(
                    publication,
                    items.get(publication.getItemId()),
                    versions.get(publication.getVersionId()),
                    events.get(publication.getVersionId())))
        .toList();
  }

  private PublicItemView publicView(
      CommunicationPublication publication,
      CommunicationItem item,
      CommunicationItemVersion version,
      EventOccurrence occurrence) {
    return new PublicItemView(
        publication.getId(),
        item.getId(),
        version.getId(),
        item.getKind(),
        item.getSlug(),
        version.getTitle(),
        version.getSummary(),
        version.getSchemaVersion(),
        contentValidator.read(version.getStructuredContent()),
        version.getHeroMediaAssetId(),
        version.getExternalUrl(),
        publication.getPublishFrom(),
        publication.getPublishUntil(),
        publication.isPinned(),
        publication.isFeatured(),
        occurrence == null ? null : eventView(occurrence));
  }

  private EditorialItemView editorialView(CommunicationItem item) {
    CommunicationItemVersion version =
        versionRepository
            .findTopByItemIdOrderByVersionNumberDesc(item.getId())
            .orElseThrow(() -> new IllegalStateException("Communication item has no version."));
    return editorialView(item, version);
  }

  private EditorialItemView editorialView(
      CommunicationItem item, CommunicationItemVersion version) {
    Optional<CommunicationPublication> publication =
        publicationRepository.findByVersionId(version.getId());
    return new EditorialItemView(
        item.getId(),
        version.getId(),
        item.getKind(),
        item.getSlug(),
        version.getTitle(),
        version.getSummary(),
        version.getWorkflowStatus(),
        version.getVersionNumber(),
        version.getVersion(),
        version.getAuthoredByUserId(),
        version.getUpdatedAt(),
        publication.map(value -> value.effectiveStatus(clock.instant())).orElse(null),
        publication.map(CommunicationPublication::getId).orElse(null),
        publication.map(CommunicationPublication::getVersion).orElse(null));
  }

  private void recordWorkflow(
      CommunicationItemVersion version,
      String eventType,
      WorkflowStatus from,
      WorkflowStatus to,
      UUID actor,
      String reason) {
    workflowEventRepository.save(
        new CommunicationWorkflowEvent(
            version.getItemId(),
            version.getId(),
            eventType,
            from,
            to,
            actor,
            reason,
            clock.instant()));
  }

  private void validateKindSpecificFields(
      ContentKind kind, String externalUrl, EventDetailsCommand event) {
    if (kind == null) {
      throw new IllegalArgumentException("Content kind is required.");
    }
    if (kind == ContentKind.LINK && (externalUrl == null || externalUrl.isBlank())) {
      throw new IllegalArgumentException("Link content requires a destination URL.");
    }
    if (kind == ContentKind.EVENT && event == null) {
      throw new IllegalArgumentException("Event content requires occurrence details.");
    }
    if (kind != ContentKind.EVENT && event != null) {
      throw new IllegalArgumentException("Occurrence details are allowed only for event content.");
    }
  }

  private void saveEventOccurrence(
      ContentKind kind, EventDetailsCommand event, UUID communicationVersionId) {
    if (kind == ContentKind.EVENT) {
      eventOccurrenceRepository.save(toEventOccurrence(event, communicationVersionId));
    }
  }

  private void updateEventOccurrence(
      ContentKind kind, EventDetailsCommand event, UUID communicationVersionId) {
    if (kind != ContentKind.EVENT) {
      return;
    }
    EventOccurrence occurrence =
        eventOccurrenceRepository
            .findByCommunicationVersionId(communicationVersionId)
            .orElseThrow(() -> new IllegalStateException("Event occurrence was not found."));
    occurrence.update(
        event.startsAt(),
        event.endsAt(),
        event.timezone(),
        event.attendanceMode(),
        event.venueName(),
        event.address(),
        event.onlineUrl());
    eventOccurrenceRepository.save(occurrence);
  }

  private EventOccurrence toEventOccurrence(
      EventDetailsCommand event, UUID communicationVersionId) {
    return new EventOccurrence(
        communicationVersionId,
        event.startsAt(),
        event.endsAt(),
        event.timezone(),
        event.attendanceMode(),
        event.venueName(),
        event.address(),
        event.onlineUrl());
  }

  private EventView eventView(EventOccurrence event) {
    return new EventView(
        event.getStartsAt(),
        event.getEndsAt(),
        event.getTimezone(),
        event.getAttendanceMode(),
        event.getVenueName(),
        event.getAddress(),
        event.getOnlineUrl());
  }

  private CategoryView categoryView(CommunicationCategory category) {
    return new CategoryView(
        category.getId(),
        category.getCode(),
        category.getName(),
        category.getDescription(),
        category.getDisplayOrder(),
        category.isActive(),
        category.getVersion());
  }

  private MediaView mediaView(CommunicationMediaAsset asset) {
    return new MediaView(
        asset.getId(),
        asset.getOriginalFileName(),
        asset.getContentType(),
        asset.getSizeBytes(),
        asset.getAlternativeText(),
        "/api/communications/public/media/" + asset.getId());
  }

  private CommunicationItem requiredItem(UUID itemId) {
    return itemRepository
        .findById(itemId)
        .orElseThrow(() -> new CommunicationNotFoundException("Communication item was not found."));
  }

  private CommunicationItemVersion requiredVersion(UUID versionId) {
    return versionRepository
        .findById(versionId)
        .orElseThrow(
            () -> new CommunicationNotFoundException("Communication version was not found."));
  }

  private String sha256(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable.", exception);
    }
  }

  private String safeExtension(String fileName) {
    if (fileName == null) {
      return "";
    }
    int separator = fileName.lastIndexOf('.');
    if (separator < 0) {
      return "";
    }
    String extension = fileName.substring(separator).toLowerCase();
    return extension.matches("\\.[a-z0-9]{1,8}") ? extension : "";
  }
}
