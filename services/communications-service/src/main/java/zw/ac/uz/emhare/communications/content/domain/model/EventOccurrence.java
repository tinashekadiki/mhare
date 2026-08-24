package zw.ac.uz.emhare.communications.content.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.AttendanceMode;

/** Timezone-aware occurrence for an EVENT content version. @author Tinashe K */
@Audited
@Entity
@Table(name = "event_occurrences")
@SQLRestriction("deleted_at IS NULL")
public class EventOccurrence extends AuditableEntity {

  @Column(name = "communication_version_id", nullable = false, unique = true)
  private UUID communicationVersionId;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @Column(nullable = false, length = 80)
  private String timezone;

  @Enumerated(EnumType.STRING)
  @Column(name = "attendance_mode", nullable = false, length = 20)
  private AttendanceMode attendanceMode;

  @Column(name = "venue_name", length = 240)
  private String venueName;

  @Column(length = 500)
  private String address;

  @Column(name = "online_url", length = 1000)
  private String onlineUrl;

  protected EventOccurrence() {}

  public EventOccurrence(
      UUID communicationVersionId,
      Instant startsAt,
      Instant endsAt,
      String timezone,
      AttendanceMode attendanceMode,
      String venueName,
      String address,
      String onlineUrl) {
    if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
      throw new IllegalArgumentException("Event end must be after its start.");
    }
    ZoneId.of(timezone);
    validateLocation(attendanceMode, venueName, onlineUrl);
    this.communicationVersionId = communicationVersionId;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.timezone = timezone;
    this.attendanceMode = attendanceMode;
    this.venueName = blankToNull(venueName);
    this.address = blankToNull(address);
    this.onlineUrl = blankToNull(onlineUrl);
  }

  public void update(
      Instant startsAt,
      Instant endsAt,
      String timezone,
      AttendanceMode attendanceMode,
      String venueName,
      String address,
      String onlineUrl) {
    if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
      throw new IllegalArgumentException("Event end must be after its start.");
    }
    ZoneId.of(timezone);
    validateLocation(attendanceMode, venueName, onlineUrl);
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.timezone = timezone;
    this.attendanceMode = attendanceMode;
    this.venueName = blankToNull(venueName);
    this.address = blankToNull(address);
    this.onlineUrl = blankToNull(onlineUrl);
  }

  private static void validateLocation(
      AttendanceMode attendanceMode, String venueName, String onlineUrl) {
    if (attendanceMode == null) {
      throw new IllegalArgumentException("Attendance mode is required.");
    }
    if ((attendanceMode == AttendanceMode.IN_PERSON || attendanceMode == AttendanceMode.HYBRID)
        && (venueName == null || venueName.isBlank())) {
      throw new IllegalArgumentException("Venue is required for an in-person event.");
    }
    if ((attendanceMode == AttendanceMode.ONLINE || attendanceMode == AttendanceMode.HYBRID)
        && (onlineUrl == null || onlineUrl.isBlank())) {
      throw new IllegalArgumentException("Online URL is required for an online event.");
    }
  }

  public UUID getCommunicationVersionId() {
    return communicationVersionId;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public String getTimezone() {
    return timezone;
  }

  public AttendanceMode getAttendanceMode() {
    return attendanceMode;
  }

  public String getVenueName() {
    return venueName;
  }

  public String getAddress() {
    return address;
  }

  public String getOnlineUrl() {
    return onlineUrl;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
