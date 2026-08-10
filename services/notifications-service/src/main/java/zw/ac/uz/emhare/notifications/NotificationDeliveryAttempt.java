package zw.ac.uz.emhare.notifications;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Immutable @Entity @Table(name="notification_delivery_attempts") @SQLRestriction("deleted_at IS NULL")
public class NotificationDeliveryAttempt extends AuditableEntity {
    public enum Outcome { SENT, RETRYABLE_FAILURE, PERMANENT_FAILURE }
    @Column(name="notification_request_id",nullable=false) private UUID notificationRequestId;
    @Column(name="attempt_number",nullable=false) private int attemptNumber;
    @Column(name="provider_code",nullable=false,length=80) private String providerCode;
    @Column(name="started_at",nullable=false) private Instant startedAt;
    @Column(name="completed_at",nullable=false) private Instant completedAt;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private Outcome outcome;
    @Column(name="provider_message_id",length=240) private String providerMessageId;
    @Column(name="error_code",length=100) private String errorCode;
    @Column(name="error_message",length=1000) private String errorMessage;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="response_metadata",nullable=false,columnDefinition="jsonb") private Map<String,Object> responseMetadata;
    protected NotificationDeliveryAttempt() {}
    public NotificationDeliveryAttempt(UUID requestId,int number,String provider,Instant started,Instant completed,Outcome outcome,String messageId,String errorCode,String errorMessage,Map<String,Object> metadata){notificationRequestId=requestId;attemptNumber=number;providerCode=NotificationValues.required(provider,"Provider code");startedAt=started;completedAt=completed;this.outcome=outcome;providerMessageId=NotificationValues.optional(messageId);this.errorCode=NotificationValues.optional(errorCode);this.errorMessage=NotificationValues.optional(errorMessage);responseMetadata=metadata==null?Map.of():Map.copyOf(metadata);}
    public UUID getNotificationRequestId(){return notificationRequestId;} public int getAttemptNumber(){return attemptNumber;} public String getProviderCode(){return providerCode;} public Instant getStartedAt(){return startedAt;} public Instant getCompletedAt(){return completedAt;} public Outcome getOutcome(){return outcome;} public String getProviderMessageId(){return providerMessageId;} public String getErrorCode(){return errorCode;} public String getErrorMessage(){return errorMessage;} public Map<String,Object> getResponseMetadata(){return responseMetadata;}
}
