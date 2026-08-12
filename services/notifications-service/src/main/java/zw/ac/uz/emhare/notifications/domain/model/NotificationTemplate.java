package zw.ac.uz.emhare.notifications.domain.model;

import zw.ac.uz.emhare.notifications.*;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name="notification_templates") @SQLRestriction("deleted_at IS NULL")
public class NotificationTemplate extends AuditableEntity {
    public enum Channel { EMAIL, SMS, IN_APP }
    public enum Category { TRANSACTIONAL, WORKFLOW, SECURITY, MARKETING }
    public enum Status { DRAFT, ACTIVE, RETIRED }
    @Column(nullable=false,length=80) private String code;
    @Column(name="template_version",nullable=false) private int templateVersion;
    @Column(nullable=false,length=180) private String name;
    @Column(name="event_type",nullable=false,length=120) private String eventType;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Channel channel;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private Category category;
    @Column(nullable=false,length=20) private String locale;
    @Column(name="subject_template",length=500) private String subjectTemplate;
    @Column(name="body_template",nullable=false,columnDefinition="text") private String bodyTemplate;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
    @Column(name="prepared_by_user_id",nullable=false) private UUID preparedByUserId;
    @Column(name="approved_by_user_id") private UUID approvedByUserId;
    @Column(name="approved_at") private Instant approvedAt;
    @Column(name="approval_reason",length=1000) private String approvalReason;
    protected NotificationTemplate() {}
    public NotificationTemplate(String code,int templateVersion,String name,String eventType,Channel channel,Category category,String locale,String subjectTemplate,String bodyTemplate,UUID actor){
        if(templateVersion<1||channel==null||category==null||actor==null)throw new IllegalArgumentException("Template version, channel, category, and preparer are required.");
        this.code=NotificationValues.code(code,"Template code");this.templateVersion=templateVersion;this.name=NotificationValues.required(name,"Template name");this.eventType=NotificationValues.code(eventType,"Event type");this.channel=channel;this.category=category;this.locale=NotificationValues.required(locale,"Locale");this.subjectTemplate=NotificationValues.optional(subjectTemplate);this.bodyTemplate=NotificationValues.required(bodyTemplate,"Body template");this.status=Status.DRAFT;this.preparedByUserId=actor;
    }
    public void updateDraft(String name,String eventType,Category category,String subject,String body,long expected){
        NotificationValues.version(getVersion(),expected,"Notification template");if(status!=Status.DRAFT)throw new IllegalStateException("Only draft notification templates can be updated.");
        this.name=NotificationValues.required(name,"Template name");this.eventType=NotificationValues.code(eventType,"Event type");this.category=category;this.subjectTemplate=NotificationValues.optional(subject);this.bodyTemplate=NotificationValues.required(body,"Body template");
    }
    public void activate(UUID actor,String reason,Instant now,long expected){
        NotificationValues.version(getVersion(),expected,"Notification template");if(status!=Status.DRAFT)throw new IllegalStateException("Only draft notification templates can be activated.");if(preparedByUserId.equals(actor))throw new IllegalStateException("The template preparer cannot approve the same template.");
        status=Status.ACTIVE;approvedByUserId=actor;approvedAt=now;approvalReason=NotificationValues.required(reason,"Approval reason");
    }
    public void retire(UUID actor,String reason,Instant now,long expected){
        NotificationValues.version(getVersion(),expected,"Notification template");if(status!=Status.ACTIVE)throw new IllegalStateException("Only active templates can be retired.");status=Status.RETIRED;approvedByUserId=actor;approvedAt=now;approvalReason=NotificationValues.required(reason,"Retirement reason");
    }
    public String getCode(){return code;} public int getTemplateVersion(){return templateVersion;} public String getName(){return name;} public String getEventType(){return eventType;} public Channel getChannel(){return channel;} public Category getCategory(){return category;} public String getLocale(){return locale;} public String getSubjectTemplate(){return subjectTemplate;} public String getBodyTemplate(){return bodyTemplate;} public Status getStatus(){return status;} public UUID getPreparedByUserId(){return preparedByUserId;} public UUID getApprovedByUserId(){return approvedByUserId;} public Instant getApprovedAt(){return approvedAt;} public String getApprovalReason(){return approvalReason;}
}
