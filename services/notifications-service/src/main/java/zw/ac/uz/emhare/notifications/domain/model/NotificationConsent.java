package zw.ac.uz.emhare.notifications.domain.model;

import zw.ac.uz.emhare.notifications.*;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name="notification_consents") @SQLRestriction("deleted_at IS NULL")
public class NotificationConsent extends AuditableEntity {
    public enum Status { OPTED_IN, OPTED_OUT, NOT_REQUIRED }
    @Column(name="recipient_user_id") private UUID recipientUserId;
    @Column(name="recipient_key",nullable=false,length=160) private String recipientKey;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private NotificationTemplate.Channel channel;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private NotificationTemplate.Category category;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
    @Column(nullable=false,length=80) private String source;
    @Column(name="evidence_reference",length=300) private String evidenceReference;
    @Column(name="effective_from",nullable=false) private Instant effectiveFrom;
    @Column(name="effective_until") private Instant effectiveUntil;
    protected NotificationConsent() {}
    public NotificationConsent(UUID userId,String key,NotificationTemplate.Channel channel,NotificationTemplate.Category category,Status status,String source,String evidence,Instant from){
        if(channel==null||category==null||status==null||from==null)throw new IllegalArgumentException("Consent channel, category, status, and effective time are required.");recipientUserId=userId;recipientKey=NotificationValues.required(key,"Recipient key").toLowerCase();this.channel=channel;this.category=category;this.status=status;this.source=NotificationValues.required(source,"Consent source");evidenceReference=NotificationValues.optional(evidence);effectiveFrom=from;
    }
    public void replace(Status status,String source,String evidence,Instant now,long expected){NotificationValues.version(getVersion(),expected,"Notification consent");this.status=status;this.source=NotificationValues.required(source,"Consent source");this.evidenceReference=NotificationValues.optional(evidence);this.effectiveFrom=now;}
    public UUID getRecipientUserId(){return recipientUserId;} public String getRecipientKey(){return recipientKey;} public NotificationTemplate.Channel getChannel(){return channel;} public NotificationTemplate.Category getCategory(){return category;} public Status getStatus(){return status;} public String getSource(){return source;} public String getEvidenceReference(){return evidenceReference;} public Instant getEffectiveFrom(){return effectiveFrom;} public Instant getEffectiveUntil(){return effectiveUntil;}
}
