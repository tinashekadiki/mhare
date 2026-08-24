package zw.ac.uz.emhare.communications.content.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.AttendanceMode;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.ContentKind;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.PublicationStatus;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.WorkflowStatus;

/** Application-facing Communications projections. @author Tinashe K */
public final class CommunicationViews {

  private CommunicationViews() {}

  public record EventView(
      Instant startsAt,
      Instant endsAt,
      String timezone,
      AttendanceMode attendanceMode,
      String venueName,
      String address,
      String onlineUrl) {}

  public record PublicItemView(
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
      String externalUrl,
      Instant publishFrom,
      Instant publishUntil,
      boolean pinned,
      boolean featured,
      EventView event) {}

  public record PublicHomeView(
      List<PublicItemView> urgentNotices,
      List<PublicItemView> importantLinks,
      PublicItemView featuredCampaign,
      List<PublicItemView> upcomingEvents,
      List<PublicItemView> latestNews) {}

  public record EditorialItemView(
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
      Long publicationExpectedVersion) {}

  public record EditorialDetailView(
      EditorialItemView item,
      UUID categoryId,
      JsonNode structuredContent,
      UUID heroMediaAssetId,
      String externalUrl,
      EventView event) {}

  public record PageView<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}

  public record CategoryView(
      UUID id,
      String code,
      String name,
      String description,
      int displayOrder,
      boolean active,
      long expectedVersion) {}

  public record MediaView(
      UUID id,
      String fileName,
      String contentType,
      long sizeBytes,
      String alternativeText,
      String publicUrl) {}

  public record MediaContent(String contentType, String fileName, byte[] bytes) {}

  public record ReadReceiptView(UUID publicationId, Instant firstReadAt) {}
}
