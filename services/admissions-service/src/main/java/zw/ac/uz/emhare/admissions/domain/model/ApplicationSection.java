package zw.ac.uz.emhare.admissions.domain.model;

import zw.ac.uz.emhare.admissions.application.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Immutable route definition snapshot with current completion state. @author Tinashe K */
@Audited
@Entity
@Table(
        name = "application_sections",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_application_sections_code",
                columnNames = {"application_id", "section_code"}))
public class ApplicationSection extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "section_code", nullable = false, length = 60)
    private String sectionCode;

    @Column(name = "section_name", nullable = false, length = 150)
    private String sectionName;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "is_repeatable", nullable = false)
    private boolean repeatable;

    @Column(name = "minimum_records", nullable = false)
    private int minimumRecords;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApplicationSectionStatus status;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completion_summary", length = 1000)
    private String completionSummary;

    protected ApplicationSection() {
    }

    public ApplicationSection(Application application, ApplicationTypeSection definition) {
        this(application, definition, definition.isRequired());
    }

    public ApplicationSection(Application application, ApplicationTypeSection definition, boolean required) {
        this.application = application;
        this.sectionCode = definition.getSectionCode();
        this.sectionName = definition.getSectionName();
        this.required = required;
        this.repeatable = definition.isRepeatable();
        this.minimumRecords = definition.getMinimumRecords();
        this.sortOrder = definition.getSortOrder();
        this.status = ApplicationSectionStatus.NOT_STARTED;
    }

    public void recordStatus(ApplicationSectionStatus newStatus, String summary, Instant now) {
        status = newStatus;
        completionSummary = summary == null || summary.isBlank() ? null : summary.trim();
        completedAt = newStatus == ApplicationSectionStatus.COMPLETE || newStatus == ApplicationSectionStatus.VERIFIED
                ? now
                : null;
    }

    public Application getApplication() { return application; }
    public String getSectionCode() { return sectionCode; }
    public String getSectionName() { return sectionName; }
    public boolean isRequired() { return required; }
    public boolean isRepeatable() { return repeatable; }
    public int getMinimumRecords() { return minimumRecords; }
    public int getSortOrder() { return sortOrder; }
    public ApplicationSectionStatus getStatus() { return status; }
    public Instant getCompletedAt() { return completedAt; }
    public String getCompletionSummary() { return completionSummary; }
    public boolean isComplete() {
        return status == ApplicationSectionStatus.COMPLETE || status == ApplicationSectionStatus.VERIFIED;
    }
}
