package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Explicit prior-University-of-Zimbabwe study declaration for one application. @author Tinashe K */
@Audited
@Entity
@Table(name = "application_prior_uz_declarations")
@SQLRestriction("deleted_at IS NULL")
public class ApplicationPriorUzDeclaration extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "previously_studied_at_uz", nullable = false) private boolean previouslyStudiedAtUz;
    @Column(name = "registration_number", length = 80) private String registrationNumber;
    @Column(name = "enrolment_started_on") private LocalDate enrolmentStartedOn;
    @Column(name = "enrolment_ended_on") private LocalDate enrolmentEndedOn;
    @Column(name = "previously_accepted_offer") private Boolean previouslyAcceptedOffer;
    @Column(name = "previously_took_up_place") private Boolean previouslyTookUpPlace;

    protected ApplicationPriorUzDeclaration() { }

    public ApplicationPriorUzDeclaration(
            Application application,
            boolean previouslyStudiedAtUz,
            String registrationNumber,
            LocalDate enrolmentStartedOn,
            LocalDate enrolmentEndedOn,
            Boolean previouslyAcceptedOffer,
            Boolean previouslyTookUpPlace) {
        this.application = application;
        update(previouslyStudiedAtUz, registrationNumber, enrolmentStartedOn, enrolmentEndedOn,
                previouslyAcceptedOffer, previouslyTookUpPlace);
    }

    public void update(
            boolean previouslyStudiedAtUz,
            String registrationNumber,
            LocalDate enrolmentStartedOn,
            LocalDate enrolmentEndedOn,
            Boolean previouslyAcceptedOffer,
            Boolean previouslyTookUpPlace) {
        this.previouslyStudiedAtUz = previouslyStudiedAtUz;
        if (!previouslyStudiedAtUz) {
            this.registrationNumber = null;
            this.enrolmentStartedOn = null;
            this.enrolmentEndedOn = null;
            this.previouslyAcceptedOffer = null;
            this.previouslyTookUpPlace = null;
            return;
        }
        if (registrationNumber == null || registrationNumber.isBlank() || enrolmentStartedOn == null
                || previouslyAcceptedOffer == null || previouslyTookUpPlace == null) {
            throw new IllegalArgumentException(
                    "Registration number, enrolment start, offer acceptance and place uptake are required for prior UZ study.");
        }
        if (enrolmentEndedOn != null && enrolmentEndedOn.isBefore(enrolmentStartedOn)) {
            throw new IllegalArgumentException("Prior UZ enrolment end cannot precede its start.");
        }
        this.registrationNumber = registrationNumber.trim();
        this.enrolmentStartedOn = enrolmentStartedOn;
        this.enrolmentEndedOn = enrolmentEndedOn;
        this.previouslyAcceptedOffer = previouslyAcceptedOffer;
        this.previouslyTookUpPlace = previouslyTookUpPlace;
    }

    public boolean isPreviouslyStudiedAtUz() { return previouslyStudiedAtUz; }
    public String getRegistrationNumber() { return registrationNumber; }
    public LocalDate getEnrolmentStartedOn() { return enrolmentStartedOn; }
    public LocalDate getEnrolmentEndedOn() { return enrolmentEndedOn; }
    public Boolean getPreviouslyAcceptedOffer() { return previouslyAcceptedOffer; }
    public Boolean getPreviouslyTookUpPlace() { return previouslyTookUpPlace; }
}
