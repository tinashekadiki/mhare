package zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.messaging.OfferPublicationEvent;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.model.GeneratedDocument;

/** Current and superseded portal-published offer-letter projection. @author Tinashe K */
@Audited @Entity @Table(name = "published_offer_letter_projections") @SQLRestriction("deleted_at IS NULL")
public class PublishedOfferLetterProjection extends AuditableEntity {
    @Column(name="source_event_id",nullable=false,unique=true) private UUID sourceEventId;
    @Column(name="offer_id",nullable=false) private UUID offerId;
    @Column(name="offer_status",nullable=false,length=30) private String offerStatus;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="generated_document_id",nullable=false)
    private GeneratedDocument generatedDocument;
    @Column(name="document_version",nullable=false) private int documentVersion;
    @Column(name="offer_number",nullable=false,length=60) private String offerNumber;
    @Column(name="application_id",nullable=false) private UUID applicationId;
    @Column(name="application_number",nullable=false,length=60) private String applicationNumber;
    @Column(name="applicant_user_id",nullable=false) private UUID applicantUserId;
    @Column(name="applicant_name",nullable=false,length=240) private String applicantName;
    @Column(name="intake_id",nullable=false) private UUID intakeId;
    @Column(name="programme_id",nullable=false) private UUID programmeId;
    @Column(name="programme_code",nullable=false,length=50) private String programmeCode;
    @Column(name="programme_name",nullable=false,length=200) private String programmeName;
    @Column(name="published_at",nullable=false) private Instant publishedAt;
    @Column(name="current_publication",nullable=false) private boolean currentPublication;
    @Column(name="superseded_at") private Instant supersededAt;
    protected PublishedOfferLetterProjection() { }
    public PublishedOfferLetterProjection(OfferPublicationEvent event, GeneratedDocument document) {
        sourceEventId=event.eventId();offerId=event.offerId();offerStatus=event.offerStatus();generatedDocument=document;
        documentVersion=event.documentVersion();offerNumber=event.offerNumber();applicationId=event.applicationId();
        applicationNumber=event.applicationNumber();applicantUserId=event.applicantUserId();applicantName=event.applicantName();
        intakeId=event.intakeId();programmeId=event.programmeId();programmeCode=event.programmeCode();programmeName=event.programmeName();
        publishedAt=event.publishedAt();currentPublication=event.currentPublication();supersededAt=event.supersededAt();
    }
    public void supersede(Instant now){if(currentPublication){currentPublication=false;supersededAt=now;}}
    public void synchronizeOfferStatus(String synchronizedOfferStatus){
        if(synchronizedOfferStatus==null||synchronizedOfferStatus.isBlank())throw new IllegalArgumentException("Offer status is required.");
        offerStatus=synchronizedOfferStatus;
    }
    public UUID getOfferId(){return offerId;} public String getOfferStatus(){return offerStatus;}
    public GeneratedDocument getGeneratedDocument(){return generatedDocument;} public int getDocumentVersion(){return documentVersion;}
    public String getOfferNumber(){return offerNumber;} public String getApplicationNumber(){return applicationNumber;}
    public UUID getApplicantUserId(){return applicantUserId;} public String getApplicantName(){return applicantName;}
    public UUID getIntakeId(){return intakeId;}
    public UUID getProgrammeId(){return programmeId;} public boolean isCurrentPublication(){return currentPublication;}
}
