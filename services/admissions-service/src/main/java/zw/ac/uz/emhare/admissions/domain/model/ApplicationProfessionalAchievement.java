package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Typed professional evidence supplied for one application. @author Tinashe K */
@Audited
@Entity
@Table(name = "application_professional_achievements")
@SQLRestriction("deleted_at IS NULL")
public class ApplicationProfessionalAchievement extends AuditableEntity {
    public enum Type { AWARD, PROFESSIONAL_MEMBERSHIP, PUBLICATION, PRESENTATION, OTHER }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;
    @Enumerated(EnumType.STRING)
    @Column(name = "achievement_type", nullable = false, length = 30) private Type type;
    @Column(nullable = false, length = 250) private String title;
    @Column(length = 200) private String organisation;
    @Column(name = "achieved_on") private LocalDate achievedOn;
    @Column(length = 2000) private String description;

    protected ApplicationProfessionalAchievement() { }

    public ApplicationProfessionalAchievement(
            Application application, Type type, String title, String organisation,
            LocalDate achievedOn, String description) {
        this.application = application;
        this.type = java.util.Objects.requireNonNull(type, "Achievement type is required.");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Achievement title is required.");
        this.title = title.trim();
        this.organisation = trimToNull(organisation);
        this.achievedOn = achievedOn;
        this.description = trimToNull(description);
    }

    public Type getType() { return type; }
    public String getTitle() { return title; }
    public String getOrganisation() { return organisation; }
    public LocalDate getAchievedOn() { return achievedOn; }
    public String getDescription() { return description; }

    private static String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
