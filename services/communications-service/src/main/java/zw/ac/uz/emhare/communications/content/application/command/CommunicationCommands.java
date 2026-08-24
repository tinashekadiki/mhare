package zw.ac.uz.emhare.communications.content.application.command;

import java.time.Instant;
import java.util.UUID;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.AttendanceMode;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.ContentKind;

/** Internal Communications use-case inputs. @author Tinashe K */
public final class CommunicationCommands {

  private CommunicationCommands() {}

  public record EventDetailsCommand(
      Instant startsAt,
      Instant endsAt,
      String timezone,
      AttendanceMode attendanceMode,
      String venueName,
      String address,
      String onlineUrl) {}

  public record CreateDraftCommand(
      ContentKind kind,
      String slug,
      UUID categoryId,
      String title,
      String summary,
      String structuredContent,
      UUID heroMediaAssetId,
      String externalUrl,
      EventDetailsCommand event) {}

  public record EditVersionCommand(
      String title,
      String summary,
      String structuredContent,
      UUID heroMediaAssetId,
      String externalUrl,
      EventDetailsCommand event,
      long expectedVersion) {}

  public record SchedulePublicationCommand(
      Instant publishFrom,
      Instant publishUntil,
      boolean pinned,
      boolean featured,
      int displayOrder) {}

  public record CategoryCommand(
      String code, String name, String description, int displayOrder, boolean active) {}
}
