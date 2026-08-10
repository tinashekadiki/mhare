package zw.ac.uz.emhare.notifications;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

/** @author Tinashe K */
public final class NotificationContracts {
    private NotificationContracts() {}
    public record CreateTemplate(@NotBlank String code,@Min(1) int templateVersion,@NotBlank String name,@NotBlank String eventType,@NotNull NotificationTemplate.Channel channel,@NotNull NotificationTemplate.Category category,@NotBlank String locale,String subjectTemplate,@NotBlank String bodyTemplate) {}
    public record UpdateTemplate(@NotBlank String name,@NotBlank String eventType,@NotNull NotificationTemplate.Category category,String subjectTemplate,@NotBlank String bodyTemplate,@Min(0) long expectedVersion) {}
    public record TemplateTransition(@NotNull NotificationTemplate.Status targetStatus,@NotBlank @Size(max=1000) String reason,@Min(0) long expectedVersion) {}
    public record RecordConsent(UUID recipientUserId,@NotBlank String recipientKey,@NotNull NotificationTemplate.Channel channel,@NotNull NotificationTemplate.Category category,@NotNull NotificationConsent.Status status,@NotBlank String source,String evidenceReference,@Min(0) Long expectedVersion) {}
    public record QueueNotification(@NotBlank String idempotencyKey,@NotBlank String sourceService,UUID sourceEventId,@NotBlank String eventType,@NotBlank String templateCode,@NotNull NotificationTemplate.Channel channel,@NotBlank String locale,UUID recipientUserId,@NotBlank String recipientKey,@NotBlank String recipientAddress,@NotNull NotificationRequest.Priority priority,Instant scheduledAt,@Min(1) @Max(20) Integer maxAttempts,Map<String,String> variables) {}
    public record ManualAction(@NotBlank @Size(max=1000) String reason,@Min(0) long expectedVersion) {}
    public record MarkInAppRead(@Min(0) long expectedVersion) {}
    public record TemplateSummary(UUID id,String code,int templateVersion,String name,String eventType,NotificationTemplate.Channel channel,NotificationTemplate.Category category,String locale,String subjectTemplate,String bodyTemplate,NotificationTemplate.Status status,UUID preparedByUserId,UUID approvedByUserId,Instant approvedAt,String approvalReason,long version) {}
    public record ConsentSummary(UUID id,UUID recipientUserId,String recipientKey,NotificationTemplate.Channel channel,NotificationTemplate.Category category,NotificationConsent.Status status,String source,String evidenceReference,Instant effectiveFrom,long version) {}
    public record RequestSummary(UUID id,String requestNumber,String idempotencyKey,String sourceService,UUID sourceEventId,String eventType,String templateCode,int templateVersion,NotificationTemplate.Channel channel,NotificationTemplate.Category category,UUID recipientUserId,String recipientKey,String recipientAddress,String subject,String body,NotificationRequest.Priority priority,NotificationRequest.Status status,String consentDecision,Instant scheduledAt,Instant nextAttemptAt,int attemptCount,int maxAttempts,String providerCode,String providerMessageId,NotificationRequest.ProviderDeliveryStatus providerDeliveryStatus,Instant providerStatusAt,String providerStatusDetail,Instant sentAt,Instant failedAt,String lastErrorCode,String lastErrorMessage,UUID manualRetryByUserId,Instant manualRetryAt,String manualRetryReason,long version) {}
    public record AttemptSummary(UUID id,UUID notificationRequestId,int attemptNumber,String providerCode,Instant startedAt,Instant completedAt,NotificationDeliveryAttempt.Outcome outcome,String providerMessageId,String errorCode,String errorMessage,Map<String,Object> responseMetadata) {}
    public record InboxSummary(UUID id,String sourceService,UUID sourceEventId,String eventType,Instant receivedAt,Instant processedAt,NotificationEventInbox.Status status,String processingError,int attemptCount,int maxAttempts,Instant nextAttemptAt,Instant lastAttemptAt,UUID manualRetryByUserId,Instant manualRetryAt,String manualRetryReason,long version) {}
    public record CallbackSummary(UUID id,String providerCode,String providerEventId,String providerMessageId,NotificationProviderCallback.DeliveryStatus deliveryStatus,Instant occurredAt,Instant receivedAt,UUID notificationRequestId,String errorCode,String errorMessage) {}
    public record ProviderCallbackPayload(@NotBlank String providerEventId,@NotBlank String providerMessageId,@NotNull NotificationProviderCallback.DeliveryStatus deliveryStatus,@NotNull Instant occurredAt,String errorCode,String errorMessage) {}
    public record InAppSummary(UUID id,UUID notificationRequestId,UUID recipientUserId,String recipientKey,String title,String body,Instant deliveredAt,Instant readAt,UUID readByUserId,long version) {}
    public record Register(List<TemplateSummary> templates,List<ConsentSummary> consents,List<RequestSummary> requests,List<AttemptSummary> deliveryAttempts,List<InboxSummary> eventInbox,List<CallbackSummary> providerCallbacks,List<InAppSummary> inAppNotifications) {}
}
