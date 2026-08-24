package zw.ac.uz.emhare.communications.content.api.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import zw.ac.uz.emhare.communications.content.application.CommunicationViews.CategoryView;
import zw.ac.uz.emhare.communications.content.application.CommunicationViews.EditorialDetailView;
import zw.ac.uz.emhare.communications.content.application.CommunicationViews.EditorialItemView;
import zw.ac.uz.emhare.communications.content.application.CommunicationViews.EventView;
import zw.ac.uz.emhare.communications.content.application.CommunicationViews.MediaView;
import zw.ac.uz.emhare.communications.content.application.CommunicationViews.PageView;
import zw.ac.uz.emhare.communications.content.application.CommunicationViews.PublicHomeView;
import zw.ac.uz.emhare.communications.content.application.CommunicationViews.PublicItemView;
import zw.ac.uz.emhare.communications.content.application.CommunicationViews.ReadReceiptView;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.AttendanceMode;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.ContentKind;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.PublicationStatus;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.WorkflowStatus;

/** Requests and responses for the Communications HTTP boundary. @author Tinashe K */
public final class CommunicationApiModels {

  private CommunicationApiModels() {}

  public record EventDetailsRequest(
      @NotNull Instant startsAt,
      @NotNull Instant endsAt,
      @NotBlank String timezone,
      @NotNull AttendanceMode attendanceMode,
      @Size(max = 240) String venueName,
      @Size(max = 500) String address,
      @Size(max = 1000) String onlineUrl) {}

  public record CreateDraftRequest(
      @NotNull ContentKind kind,
      @Size(max = 180) String slug,
      UUID categoryId,
      @NotBlank @Size(max = 240) String title,
      @NotBlank @Size(max = 600) String summary,
      @NotNull JsonNode structuredContent,
      UUID heroMediaAssetId,
      @Size(max = 1000) String externalUrl,
      @Valid EventDetailsRequest event) {}

  public record EditVersionRequest(
      @NotBlank @Size(max = 240) String title,
      @NotBlank @Size(max = 600) String summary,
      @NotNull JsonNode structuredContent,
      UUID heroMediaAssetId,
      @Size(max = 1000) String externalUrl,
      @Valid EventDetailsRequest event,
      @Min(0) long expectedVersion) {}

  public record ExpectedVersionRequest(@Min(0) long expectedVersion) {}

  public record DecisionRequest(
      @Min(0) long expectedVersion, @NotBlank @Size(max = 1000) String reason) {}

  public record SchedulePublicationRequest(
      @NotNull Instant publishFrom,
      Instant publishUntil,
      boolean pinned,
      boolean featured,
      int displayOrder) {}

  public record CategoryRequest(
      @NotBlank @Size(max = 80) String code,
      @NotBlank @Size(max = 160) String name,
      @Size(max = 500) String description,
      int displayOrder,
      boolean active,
      @Min(0) long expectedVersion) {}

  public record EventResponse(
      Instant startsAt,
      Instant endsAt,
      String timezone,
      AttendanceMode attendanceMode,
      String venueName,
      String address,
      String onlineUrl) {
    public static EventResponse from(EventView view) {
      return view == null
          ? null
          : new EventResponse(
              view.startsAt(),
              view.endsAt(),
              view.timezone(),
              view.attendanceMode(),
              view.venueName(),
              view.address(),
              view.onlineUrl());
    }
  }

  public record PublicItemResponse(
      UUID publicationId,
      UUID itemId,
      UUID versionId,
      ContentKind kind,
      String slug,
      String title,
      String summary,
      int schemaVersion,
      JsonNode structuredContent,
      UUID heroMediaAssetId,
      String mediaUrl,
      String externalUrl,
      Instant publishFrom,
      Instant publishUntil,
      boolean pinned,
      boolean featured,
      EventResponse event) {
    public static PublicItemResponse from(PublicItemView view) {
      if (view == null) {
        return null;
      }
      return new PublicItemResponse(
          view.publicationId(),
          view.itemId(),
          view.versionId(),
          view.kind(),
          view.slug(),
          view.title(),
          view.summary(),
          view.schemaVersion(),
          view.structuredContent(),
          view.heroMediaAssetId(),
          view.heroMediaAssetId() == null
              ? null
              : "/api/communications/public/media/" + view.heroMediaAssetId(),
          view.externalUrl(),
          view.publishFrom(),
          view.publishUntil(),
          view.pinned(),
          view.featured(),
          EventResponse.from(view.event()));
    }
  }

  public record PublicHomeResponse(
      List<PublicItemResponse> urgentNotices,
      List<PublicItemResponse> importantLinks,
      PublicItemResponse featuredCampaign,
      List<PublicItemResponse> upcomingEvents,
      List<PublicItemResponse> latestNews) {
    public static PublicHomeResponse from(PublicHomeView view) {
      return new PublicHomeResponse(
          view.urgentNotices().stream().map(PublicItemResponse::from).toList(),
          view.importantLinks().stream().map(PublicItemResponse::from).toList(),
          PublicItemResponse.from(view.featuredCampaign()),
          view.upcomingEvents().stream().map(PublicItemResponse::from).toList(),
          view.latestNews().stream().map(PublicItemResponse::from).toList());
    }
  }

  public record EditorialItemResponse(
      UUID itemId,
      UUID versionId,
      ContentKind kind,
      String slug,
      String title,
      String summary,
      WorkflowStatus workflowStatus,
      int versionNumber,
      long expectedVersion,
      UUID authoredByUserId,
      Instant updatedAt,
      PublicationStatus publicationStatus,
      UUID publicationId,
      Long publicationExpectedVersion) {
    public static EditorialItemResponse from(EditorialItemView view) {
      return new EditorialItemResponse(
          view.itemId(),
          view.versionId(),
          view.kind(),
          view.slug(),
          view.title(),
          view.summary(),
          view.workflowStatus(),
          view.versionNumber(),
          view.expectedVersion(),
          view.authoredByUserId(),
          view.updatedAt(),
          view.publicationStatus(),
          view.publicationId(),
          view.publicationExpectedVersion());
    }
  }

  public record EditorialPageResponse(
      List<EditorialItemResponse> items, int page, int size, long totalItems, int totalPages) {
    public static EditorialPageResponse from(PageView<EditorialItemView> view) {
      return new EditorialPageResponse(
          view.items().stream().map(EditorialItemResponse::from).toList(),
          view.page(),
          view.size(),
          view.totalItems(),
          view.totalPages());
    }
  }

  public record EditorialDetailResponse(
      EditorialItemResponse item,
      UUID categoryId,
      JsonNode structuredContent,
      UUID heroMediaAssetId,
      String externalUrl,
      EventResponse event) {
    public static EditorialDetailResponse from(EditorialDetailView view) {
      return new EditorialDetailResponse(
          EditorialItemResponse.from(view.item()),
          view.categoryId(),
          view.structuredContent(),
          view.heroMediaAssetId(),
          view.externalUrl(),
          EventResponse.from(view.event()));
    }
  }

  public record CategoryResponse(
      UUID id,
      String code,
      String name,
      String description,
      int displayOrder,
      boolean active,
      long expectedVersion) {
    public static CategoryResponse from(CategoryView view) {
      return new CategoryResponse(
          view.id(),
          view.code(),
          view.name(),
          view.description(),
          view.displayOrder(),
          view.active(),
          view.expectedVersion());
    }
  }

  public record MediaResponse(
      UUID id,
      String fileName,
      String contentType,
      long sizeBytes,
      String alternativeText,
      String publicUrl) {
    public static MediaResponse from(MediaView view) {
      return new MediaResponse(
          view.id(),
          view.fileName(),
          view.contentType(),
          view.sizeBytes(),
          view.alternativeText(),
          view.publicUrl());
    }
  }

  public record ReadReceiptResponse(UUID publicationId, Instant firstReadAt) {
    public static ReadReceiptResponse from(ReadReceiptView view) {
      return new ReadReceiptResponse(view.publicationId(), view.firstReadAt());
    }
  }
}
