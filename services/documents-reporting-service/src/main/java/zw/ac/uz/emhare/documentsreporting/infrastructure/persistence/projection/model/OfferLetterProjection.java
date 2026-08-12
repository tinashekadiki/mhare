package zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model;


import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.messaging.OfferLetterRequestedEvent;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Immutable offer snapshot owned by Documents and Reporting. @author Tinashe K */
@Audited @Entity @Table(name = "offer_letter_projections") @SQLRestriction("deleted_at IS NULL")
public class OfferLetterProjection extends AuditableEntity {
    @Column(name="source_event_id", nullable=false) private UUID sourceEventId;
    @Column(name="offer_id", nullable=false) private UUID offerId;
    @Column(name="offer_version", nullable=false) private long offerVersion;
    @Column(name="document_version", nullable=false) private int documentVersion;
    @Column(name="offer_number", nullable=false, length=60) private String offerNumber;
    @Column(name="application_id", nullable=false) private UUID applicationId;
    @Column(name="application_number", nullable=false, length=60) private String applicationNumber;
    @Column(name="applicant_number", nullable=false, length=60) private String applicantNumber;
    @Column(name="applicant_name", nullable=false, length=240) private String applicantName;
    @Column(name="applicant_email", nullable=false, length=250) private String applicantEmail;
    @Column(name="applicant_user_id") private UUID applicantUserId;
    @Column(name="programme_id", nullable=false) private UUID programmeId;
    @Column(name="programme_code", nullable=false, length=50) private String programmeCode;
    @Column(name="programme_name", nullable=false, length=200) private String programmeName;
    @Column(name="intake_id") private UUID intakeId;
    @Column(name="offer_type", nullable=false, length=30) private String offerType;
    @Column(name="conditions_text", length=4000) private String conditionsText;
    @Column(name="acceptance_deadline", nullable=false) private Instant acceptanceDeadline;
    @Column(name="registration_date") private LocalDate registrationDate;
    @Column(name="orientation_date") private LocalDate orientationDate;
    @Column(name="commencement_date", nullable=false) private LocalDate commencementDate;
    @Column(name="requested_by_user_id", nullable=false) private UUID requestedByUserId;
    @Column(name="requested_at", nullable=false) private Instant requestedAt;
    protected OfferLetterProjection() { }
    public OfferLetterProjection(OfferLetterRequestedEvent event) {
        sourceEventId=event.eventId(); offerId=event.offerId(); offerVersion=event.offerVersion(); documentVersion=event.documentVersion();
        offerNumber=event.offerNumber(); applicationId=event.applicationId(); applicationNumber=event.applicationNumber();
        applicantNumber=event.applicantNumber(); applicantName=event.applicantName(); applicantEmail=event.applicantEmail(); applicantUserId=event.applicantUserId();
        programmeId=event.programmeId(); programmeCode=event.programmeCode(); programmeName=event.programmeName(); intakeId=event.intakeId();
        offerType=event.offerType(); conditionsText=event.conditionsText(); acceptanceDeadline=event.acceptanceDeadline();
        registrationDate=event.registrationDate(); orientationDate=event.orientationDate(); commencementDate=event.commencementDate();
        requestedByUserId=event.requestedByUserId(); requestedAt=event.occurredAt();
    }
    public UUID getOfferId(){return offerId;} public long getOfferVersion(){return offerVersion;}
    public int getDocumentVersion(){return documentVersion;} public UUID getIntakeId(){return intakeId;}
    public UUID getApplicantUserId(){return applicantUserId;}
    public String getOfferNumber(){return offerNumber;} public String getApplicationNumber(){return applicationNumber;}
    public String getApplicantNumber(){return applicantNumber;} public String getApplicantName(){return applicantName;}
    public String getApplicantEmail(){return applicantEmail;} public UUID getProgrammeId(){return programmeId;}
    public String getProgrammeCode(){return programmeCode;} public String getProgrammeName(){return programmeName;}
    public String getOfferType(){return offerType;} public String getConditionsText(){return conditionsText;}
    public Instant getAcceptanceDeadline(){return acceptanceDeadline;} public LocalDate getRegistrationDate(){return registrationDate;}
    public LocalDate getOrientationDate(){return orientationDate;} public LocalDate getCommencementDate(){return commencementDate;}
}
