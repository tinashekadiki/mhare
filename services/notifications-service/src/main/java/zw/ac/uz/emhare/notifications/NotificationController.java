package zw.ac.uz.emhare.notifications;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.notifications.NotificationContracts.*;

/** @author Tinashe K */
@RestController @RequestMapping("/api/notifications")
@PreAuthorize("hasAnyAuthority('ROLE_system-admin','ROLE_notifications-officer')")
public class NotificationController {
    private final NotificationService service; private final EmhareCurrentUserResolver users;
    public NotificationController(NotificationService service,EmhareCurrentUserResolver users){this.service=service;this.users=users;}
    @GetMapping public Register register(){return service.register();}
    @PostMapping("/templates") public TemplateSummary createTemplate(Authentication authentication,@Valid @RequestBody CreateTemplate command){return service.createTemplate(command,actor(authentication));}
    @PutMapping("/templates/{id}") public TemplateSummary updateTemplate(@PathVariable UUID id,@Valid @RequestBody UpdateTemplate command){return service.updateTemplate(id,command);}
    @PostMapping("/templates/{id}/transition") public TemplateSummary transitionTemplate(Authentication authentication,@PathVariable UUID id,@Valid @RequestBody TemplateTransition command){return service.transitionTemplate(id,command,actor(authentication));}
    @PostMapping("/consents") public ConsentSummary recordConsent(@Valid @RequestBody RecordConsent command){return service.recordConsent(command);}
    @PostMapping("/requests") public RequestSummary queue(@Valid @RequestBody QueueNotification command){return service.queue(command);}
    @PostMapping("/requests/{id}/retry") public RequestSummary retry(Authentication authentication,@PathVariable UUID id,@Valid @RequestBody ManualAction command){return service.retry(id,command,actor(authentication));}
    @PostMapping("/requests/{id}/cancel") public RequestSummary cancel(Authentication authentication,@PathVariable UUID id,@Valid @RequestBody ManualAction command){return service.cancel(id,command,actor(authentication));}
    @PostMapping("/event-inbox/{id}/retry") public InboxSummary retryEvent(Authentication authentication,@PathVariable UUID id,@Valid @RequestBody ManualAction command){return service.retryEvent(id,command,actor(authentication));}
    private UUID actor(Authentication authentication){return users.fromAuthentication(authentication).orElseThrow(()->new IllegalStateException("Authenticated user is required.")).auditUserId();}
}
