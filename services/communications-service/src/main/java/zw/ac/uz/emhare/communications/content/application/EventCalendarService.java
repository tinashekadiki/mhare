package zw.ac.uz.emhare.communications.content.application;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationItem;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationItemVersion;
import zw.ac.uz.emhare.communications.content.domain.model.EventOccurrence;

/** Generates RFC 5545-compatible calendar downloads for public events. @author Tinashe K */
@Component
public class EventCalendarService {

  private static final DateTimeFormatter CALENDAR_TIME =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

  public String generate(
      CommunicationItem item,
      CommunicationItemVersion version,
      EventOccurrence occurrence,
      String canonicalUrl) {
    ZoneId zone = ZoneId.of(occurrence.getTimezone());
    String location =
        occurrence.getVenueName() != null
            ? occurrence.getVenueName()
                + (occurrence.getAddress() == null ? "" : ", " + occurrence.getAddress())
            : occurrence.getOnlineUrl();
    return String.join(
            "\r\n",
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//University of Zimbabwe//eMhare Communications//EN",
            "CALSCALE:GREGORIAN",
            "BEGIN:VEVENT",
            "UID:" + version.getId() + "@emhare.uz.ac.zw",
            "DTSTAMP:"
                + DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                    .withZone(ZoneId.of("UTC"))
                    .format(version.getUpdatedAt()),
            "DTSTART;TZID="
                + occurrence.getTimezone()
                + ":"
                + CALENDAR_TIME.format(occurrence.getStartsAt().atZone(zone)),
            "DTEND;TZID="
                + occurrence.getTimezone()
                + ":"
                + CALENDAR_TIME.format(occurrence.getEndsAt().atZone(zone)),
            "SUMMARY:" + escape(version.getTitle()),
            "DESCRIPTION:" + escape(version.getSummary()),
            "LOCATION:" + escape(location == null ? "" : location),
            "URL:" + escape(canonicalUrl),
            "END:VEVENT",
            "END:VCALENDAR")
        + "\r\n";
  }

  private String escape(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace(",", "\\,")
        .replace(";", "\\;");
  }
}
