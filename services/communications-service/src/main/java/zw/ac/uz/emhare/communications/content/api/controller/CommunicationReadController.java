package zw.ac.uz.emhare.communications.content.api.controller;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.communications.content.api.model.CommunicationApiModels.ReadReceiptResponse;
import zw.ac.uz.emhare.communications.content.application.CommunicationApplicationService;

/** Records idempotent reads only for authenticated users. @author Tinashe K */
@RestController
@RequestMapping("/api/communications/publications")
@PreAuthorize("isAuthenticated()")
public class CommunicationReadController {

  private final CommunicationApplicationService service;
  private final EmhareCurrentUserResolver currentUserResolver;

  public CommunicationReadController(
      CommunicationApplicationService service, EmhareCurrentUserResolver currentUserResolver) {
    this.service = service;
    this.currentUserResolver = currentUserResolver;
  }

  @PutMapping("/{publicationId}/read")
  public ReadReceiptResponse read(
      @PathVariable("publicationId") UUID publicationId, Authentication authentication) {
    UUID actor =
        currentUserResolver
            .fromAuthentication(authentication)
            .orElseThrow(() -> new IllegalStateException("Authenticated user is required."))
            .auditUserId();
    return ReadReceiptResponse.from(service.recordRead(publicationId, actor));
  }
}
