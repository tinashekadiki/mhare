package zw.ac.uz.emhare.notifications;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name="notification_requests") @SQLRestriction("deleted_at IS NULL")
public class NotificationRequest extends AuditableEntity {
    public enum Priority { LOW, NORMAL, HIGH, URGENT }
    public enum Status { QUEUED, PROCESSING, SENT, RETRY_SCHEDULED, FAILED, SUPPRESSED, CANCELLED }
    public enum ProviderDeliveryStatus { ACCEPTED, DELIVERED, BOUNCED, FAILED }
    @Column(name="request_number",nullable=false,length=60) private String requestNumber;
    @Column(name="idempotency_key",nullable=false,length=160) private String idempotencyKey;
    @Column(name="source_service",nullable=false,length=80) private String sourceService;
    @Column(name="source_event_id") private UUID sourceEventId;
    @Column(name="event_type",nullable=false,length=120) private String eventType;
    @Column(name="template_id",nullable=false) private UUID templateId;
    @Column(name="template_code",nullable=false,length=80) private String templateCode;
    @Column(name="template_version",nullable=false) private int templateVersion;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private NotificationTemplate.Channel channel;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private NotificationTemplate.Category category;
    @Column(name="recipient_user_id") private UUID recipientUserId;
    @Column(name="recipient_key",nullable=false,length=160) private String recipientKey;
    @Column(name="recipient_address",nullable=false,length=320) private String recipientAddress;
    @Column(length=500) private String subject;
    @Column(nullable=false,columnDefinition="text") private String body;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Priority priority;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
    @Column(name="consent_decision",nullable=false,length=30) private String consentDecision;
    @Column(name="scheduled_at",nullable=false) private Instant scheduledAt;
    @Column(name="next_attempt_at") private Instant nextAttemptAt;
    @Column(name="attempt_count",nullable=false) private int attemptCount;
    @Column(name="max_attempts",nullable=false) private int maxAttempts;
    @Column(name="provider_code",length=80) private String providerCode;
    @Column(name="provider_message_id",length=240) private String providerMessageId;
    @Column(name="sent_at") private Instant sentAt;
    @Column(name="failed_at") private Instant failedAt;
    @Column(name="last_error_code",length=100) private String lastErrorCode;
    @Column(name="last_error_message",length=1000) private String lastErrorMessage;
    @Enumerated(EnumType.STRING) @Column(name="provider_delivery_status",length=30) private ProviderDeliveryStatus providerDeliveryStatus;
    @Column(name="provider_status_at") private Instant providerStatusAt;
    @Column(name="provider_status_detail",length=1000) private String providerStatusDetail;
    @Column(name="manual_retry_by_user_id") private UUID manualRetryByUserId;
    @Column(name="manual_retry_at") private Instant manualRetryAt;
    @Column(name="manual_retry_reason",length=1000) private String manualRetryReason;
    @Column(name="cancelled_by_user_id") private UUID cancelledByUserId;
    @Column(name="cancelled_at") private Instant cancelledAt;
    @Column(name="cancellation_reason",length=1000) private String cancellationReason;
    protected NotificationRequest() {}
    public NotificationRequest(String number,String idempotency,String source,UUID sourceEvent,String eventType,NotificationTemplate template,UUID recipientUserId,String recipientKey,String address,String subject,String body,Priority priority,String consentDecision,Instant scheduledAt,int maxAttempts,boolean suppressed){
        this.requestNumber=number;this.idempotencyKey=NotificationValues.required(idempotency,"Idempotency key");this.sourceService=NotificationValues.code(source,"Source service");this.sourceEventId=sourceEvent;this.eventType=NotificationValues.code(eventType,"Event type");this.templateId=template.getId();this.templateCode=template.getCode();this.templateVersion=template.getTemplateVersion();this.channel=template.getChannel();this.category=template.getCategory();this.recipientUserId=recipientUserId;this.recipientKey=NotificationValues.required(recipientKey,"Recipient key").toLowerCase();this.recipientAddress=NotificationValues.required(address,"Recipient address");this.subject=NotificationValues.optional(subject);this.body=NotificationValues.required(body,"Notification body");this.priority=priority==null?Priority.NORMAL:priority;this.consentDecision=consentDecision;this.scheduledAt=scheduledAt;this.nextAttemptAt=suppressed?null:scheduledAt;this.maxAttempts=maxAttempts;this.status=suppressed?Status.SUPPRESSED:Status.QUEUED;
    }
    public void startAttempt(){if(status!=Status.QUEUED&&status!=Status.RETRY_SCHEDULED)throw new IllegalStateException("Notification is not ready for dispatch.");status=Status.PROCESSING;attemptCount++;}
    public void sent(String provider,String messageId,Instant now){status=Status.SENT;providerCode=NotificationValues.code(provider,"Provider code");providerMessageId=NotificationValues.required(messageId,"Provider message ID");sentAt=now;providerDeliveryStatus=ProviderDeliveryStatus.ACCEPTED;providerStatusAt=now;providerStatusDetail=null;nextAttemptAt=null;lastErrorCode=null;lastErrorMessage=null;}
    public void deliveredInApp(String messageId,Instant now){sent("IN_APP",messageId,now);providerDeliveryStatus=ProviderDeliveryStatus.DELIVERED;}
    public void applyProviderStatus(ProviderDeliveryStatus deliveryStatus,Instant occurredAt,String detail){
        if(status!=Status.SENT)throw new IllegalStateException("Provider delivery evidence requires a sent notification request.");
        java.util.Objects.requireNonNull(deliveryStatus,"Provider delivery status is required.");java.util.Objects.requireNonNull(occurredAt,"Provider status time is required.");
        if(providerStatusAt!=null&&occurredAt.isBefore(providerStatusAt))return;
        providerDeliveryStatus=deliveryStatus;providerStatusAt=occurredAt;providerStatusDetail=NotificationValues.optional(detail);
    }
    public void deliveryFailed(String provider,String code,String message,boolean retryable,Instant now,Instant next){providerCode=provider;lastErrorCode=NotificationValues.optional(code);lastErrorMessage=NotificationValues.required(message,"Delivery error");if(retryable&&attemptCount<maxAttempts){status=Status.RETRY_SCHEDULED;nextAttemptAt=next;}else{status=Status.FAILED;failedAt=now;nextAttemptAt=null;}}
    public void retryNow(UUID actor,String reason,Instant now,long expected){NotificationValues.version(getVersion(),expected,"Notification request");if(status!=Status.FAILED)throw new IllegalStateException("Only failed notifications can be retried manually.");manualRetryByUserId=java.util.Objects.requireNonNull(actor,"Retry operator is required.");manualRetryAt=java.util.Objects.requireNonNull(now,"Retry time is required.");manualRetryReason=NotificationValues.reason(reason,"Manual retry reason");status=Status.RETRY_SCHEDULED;failedAt=null;nextAttemptAt=now;lastErrorCode=null;lastErrorMessage=null;maxAttempts=Math.addExact(attemptCount,5);}
    public void cancel(UUID actor,String reason,Instant now,long expected){NotificationValues.version(getVersion(),expected,"Notification request");if(status==Status.SENT||status==Status.CANCELLED)throw new IllegalStateException("A sent or already cancelled notification cannot be cancelled.");status=Status.CANCELLED;cancelledByUserId=actor;cancelledAt=now;cancellationReason=NotificationValues.required(reason,"Cancellation reason");nextAttemptAt=null;}
    public String getRequestNumber(){return requestNumber;} public String getIdempotencyKey(){return idempotencyKey;} public String getSourceService(){return sourceService;} public UUID getSourceEventId(){return sourceEventId;} public String getEventType(){return eventType;} public UUID getTemplateId(){return templateId;} public String getTemplateCode(){return templateCode;} public int getTemplateVersion(){return templateVersion;} public NotificationTemplate.Channel getChannel(){return channel;} public NotificationTemplate.Category getCategory(){return category;} public UUID getRecipientUserId(){return recipientUserId;} public String getRecipientKey(){return recipientKey;} public String getRecipientAddress(){return recipientAddress;} public String getSubject(){return subject;} public String getBody(){return body;} public Priority getPriority(){return priority;} public Status getStatus(){return status;} public String getConsentDecision(){return consentDecision;} public Instant getScheduledAt(){return scheduledAt;} public Instant getNextAttemptAt(){return nextAttemptAt;} public int getAttemptCount(){return attemptCount;} public int getMaxAttempts(){return maxAttempts;} public String getProviderCode(){return providerCode;} public String getProviderMessageId(){return providerMessageId;} public Instant getSentAt(){return sentAt;} public Instant getFailedAt(){return failedAt;} public String getLastErrorCode(){return lastErrorCode;} public String getLastErrorMessage(){return lastErrorMessage;} public ProviderDeliveryStatus getProviderDeliveryStatus(){return providerDeliveryStatus;} public Instant getProviderStatusAt(){return providerStatusAt;} public String getProviderStatusDetail(){return providerStatusDetail;} public UUID getManualRetryByUserId(){return manualRetryByUserId;} public Instant getManualRetryAt(){return manualRetryAt;} public String getManualRetryReason(){return manualRetryReason;} public UUID getCancelledByUserId(){return cancelledByUserId;} public Instant getCancelledAt(){return cancelledAt;} public String getCancellationReason(){return cancellationReason;}
}
