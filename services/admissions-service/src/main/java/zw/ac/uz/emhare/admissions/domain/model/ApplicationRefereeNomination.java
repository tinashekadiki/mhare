package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Locale;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Application-specific nomination of a reusable applicant referee contact. @author Tinashe K */
@Audited
@Entity
@Table(name = "application_referee_nominations")
@SQLRestriction("deleted_at IS NULL")
public class ApplicationRefereeNomination extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referee_id", nullable = false)
    private ApplicantReferee referee;

    @Column(nullable = false, length = 200) private String organisation;
    @Column(name = "position_title", nullable = false, length = 150) private String positionTitle;
    @Column(nullable = false, length = 500) private String expertise;
    @Column(name = "relationship_to_applicant", nullable = false, length = 200) private String relationshipToApplicant;
    @Column(name = "normalized_email", nullable = false, length = 200) private String normalizedEmail;
    @Column(name = "normalized_phone_number", length = 50) private String normalizedPhoneNumber;
    @Column(name = "is_current", nullable = false) private boolean current;

    protected ApplicationRefereeNomination() { }

    public ApplicationRefereeNomination(
            Application application,
            ApplicantReferee referee,
            String organisation,
            String positionTitle,
            String expertise,
            String relationshipToApplicant) {
        this.application = application;
        this.referee = referee;
        update(organisation, positionTitle, expertise, relationshipToApplicant);
        current = true;
    }

    public void update(String organisation, String positionTitle, String expertise, String relationshipToApplicant) {
        this.organisation = required(organisation, "Referee organisation");
        this.positionTitle = required(positionTitle, "Referee position");
        this.expertise = required(expertise, "Referee expertise");
        this.relationshipToApplicant = required(relationshipToApplicant, "Relationship to applicant");
        this.normalizedEmail = normalizeEmail(referee.getEmail());
        this.normalizedPhoneNumber = normalizePhone(referee.getPhoneNumber());
        this.current = true;
    }

    public void withdraw(java.util.UUID actorUserId) {
        current = false;
        markDeleted(actorUserId);
    }

    public Application getApplication() { return application; }
    public ApplicantReferee getReferee() { return referee; }
    public String getOrganisation() { return organisation; }
    public String getPositionTitle() { return positionTitle; }
    public String getExpertise() { return expertise; }
    public String getRelationshipToApplicant() { return relationshipToApplicant; }
    public String getNormalizedEmail() { return normalizedEmail; }
    public String getNormalizedPhoneNumber() { return normalizedPhoneNumber; }
    public boolean isCurrent() { return current; }

    public static String normalizeEmail(String value) {
        return required(value, "Referee email").toLowerCase(Locale.ROOT);
    }

    public static String normalizePhone(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.replaceAll("[^0-9+]", "");
        return normalized.isBlank() ? null : normalized;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }
}
