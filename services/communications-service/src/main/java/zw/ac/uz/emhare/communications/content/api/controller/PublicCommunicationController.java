package zw.ac.uz.emhare.communications.content.api.controller;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import zw.ac.uz.emhare.communications.content.api.model.CommunicationApiModels.PublicHomeResponse;
import zw.ac.uz.emhare.communications.content.api.model.CommunicationApiModels.PublicItemResponse;
import zw.ac.uz.emhare.communications.content.application.CommunicationApplicationService;
import zw.ac.uz.emhare.communications.content.application.CommunicationViews.MediaContent;

/** Anonymous canonical public gateway content. @author Tinashe K */
@RestController
@RequestMapping("/api/communications/public")
public class PublicCommunicationController {

  private final CommunicationApplicationService service;
  private final String publicGatewayBaseUrl;

  public PublicCommunicationController(
      CommunicationApplicationService service,
      @Value("${emhare.communications.public-gateway-base-url:http://localhost:3002}")
          String publicGatewayBaseUrl) {
    this.service = service;
    this.publicGatewayBaseUrl = publicGatewayBaseUrl;
  }

  @GetMapping("/home")
  public PublicHomeResponse home() {
    return PublicHomeResponse.from(service.publicHome());
  }

  @GetMapping("/items/{slug}")
  public PublicItemResponse item(@PathVariable("slug") String slug) {
    return PublicItemResponse.from(service.publicItem(slug));
  }

  @GetMapping(value = "/events/{slug}/calendar.ics", produces = "text/calendar")
  public ResponseEntity<String> calendar(@PathVariable("slug") String slug) {
    String canonicalUrl =
        UriComponentsBuilder.fromUriString(publicGatewayBaseUrl)
            .pathSegment("events", slug)
            .build()
            .toUriString();
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(slug + ".ics", StandardCharsets.UTF_8)
                .build()
                .toString())
        .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
        .body(service.eventCalendar(slug, canonicalUrl));
  }

  @GetMapping("/media/{assetId}")
  public ResponseEntity<byte[]> media(@PathVariable("assetId") UUID assetId) {
    MediaContent media = service.publicMedia(assetId);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline()
                .filename(media.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .contentType(MediaType.parseMediaType(media.contentType()))
        .body(media.bytes());
  }
}
