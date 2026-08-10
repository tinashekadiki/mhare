package zw.ac.uz.emhare.admissions.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@Table(
        name = "application_accommodation_requests",
        uniqueConstraints = @UniqueConstraint(name = "uk_application_accommodation_requests_application", columnNames = "application_id"))
public class ApplicationAccommodationRequest extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccommodationRequestStatus status;

    @Column(name = "preferred_campus_code", length = 50)
    private String preferredCampusCode;

    @Column(name = "special_requirements", length = 1000)
    private String specialRequirements;

    @Column(length = 1000)
    private String notes;

    protected ApplicationAccommodationRequest() {
    }

    public ApplicationAccommodationRequest(Application application, AccommodationRequestStatus status) {
        this.application = application;
        this.status = status;
    }
}
