package zw.ac.uz.emhare.notifications.api.controller;

import zw.ac.uz.emhare.notifications.*;
import zw.ac.uz.emhare.notifications.api.model.*;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.notifications.api.model.NotificationApiModels.*;

/** @author Tinashe K */
@RestController @RequestMapping("/api/notifications")
@PreAuthorize("hasAnyAuthority('ROLE_system-admin','ROLE_notifications-officer')")
public class NotificationController {
    private final NotificationService service; private final EmhareCurrentUserResolver users;
    public NotificationController(NotificationService service,EmhareCurrentUserResolver users){this.service=service;this.users=users;}
    @GetMapping public Register register(){return service.register();}
    @PostMapping("/templates") public TemplateSummary createTemplate(Authentication authentication,@Valid @RequestBody CreateTemplate request){return service.createTemplate(request,actor(authentication));}
    @PutMapping("/templates/{id}") public TemplateSummary updateTemplate(@PathVariable UUID id,@Valid @RequestBody UpdateTemplate request){return service.updateTemplate(id,request);}
    @PostMapping("/templates/{id}/transition") public TemplateSummary transitionTemplate(Authentication authentication,@PathVariable UUID id,@Valid @RequestBody TemplateTransition request){return service.transitionTemplate(id,request,actor(authentication));}
    @PostMapping("/consents") public ConsentSummary recordConsent(@Valid @RequestBody RecordConsent request){return service.recordConsent(request);}
    @PostMapping("/requests") public RequestSummary queue(@Valid @RequestBody QueueNotification request){return service.queue(request);}
    @PostMapping("/requests/{id}/retry") public RequestSummary retry(Authentication authentication,@PathVariable UUID id,@Valid @RequestBody ManualAction request){return service.retry(id,request,actor(authentication));}
    @PostMapping("/requests/{id}/cancel") public RequestSummary cancel(Authentication authentication,@PathVariable UUID id,@Valid @RequestBody ManualAction request){return service.cancel(id,request,actor(authentication));}
    @PostMapping("/event-inbox/{id}/retry") public InboxSummary retryEvent(Authentication authentication,@PathVariable UUID id,@Valid @RequestBody ManualAction request){return service.retryEvent(id,request,actor(authentication));}
    private UUID actor(Authentication authentication){return users.fromAuthentication(authentication).orElseThrow(()->new IllegalStateException("Authenticated user is required.")).auditUserId();}
}
