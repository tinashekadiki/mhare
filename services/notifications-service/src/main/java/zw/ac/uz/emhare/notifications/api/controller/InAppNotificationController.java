package zw.ac.uz.emhare.notifications.api.controller;

import zw.ac.uz.emhare.notifications.*;
import zw.ac.uz.emhare.notifications.api.model.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.notifications.api.model.NotificationApiModels.InAppSummary;
import zw.ac.uz.emhare.notifications.api.model.NotificationApiModels.MarkInAppRead;

/** Recipient-owned in-application notification inbox. @author Tinashe K */
@RestController
@RequestMapping("/api/notifications/my-inbox")
@PreAuthorize("isAuthenticated()")
public class InAppNotificationController {
    private final NotificationService notificationService;
    private final EmhareCurrentUserResolver currentUserResolver;

    public InAppNotificationController(
            NotificationService notificationService,
            EmhareCurrentUserResolver currentUserResolver) {
        this.notificationService = notificationService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public List<InAppSummary> list(Authentication authentication) {
        return notificationService.myInAppNotifications(actor(authentication));
    }

    @PatchMapping("/{notificationId}/read")
    public InAppSummary markRead(
            Authentication authentication,
            @PathVariable UUID notificationId,
            @Valid @RequestBody MarkInAppRead request) {
        return notificationService.markInAppRead(notificationId, actor(authentication), request.expectedVersion());
    }

    private UUID actor(Authentication authentication) {
        return currentUserResolver.fromAuthentication(authentication)
                .orElseThrow(() -> new IllegalStateException("Authenticated user is required."))
                .auditUserId();
    }
}
