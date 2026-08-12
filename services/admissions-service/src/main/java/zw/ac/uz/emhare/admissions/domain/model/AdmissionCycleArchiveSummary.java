package zw.ac.uz.emhare.admissions.domain.model;

import zw.ac.uz.emhare.admissions.application.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(
        name = "admission_cycle_archive_summaries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_admission_cycle_archive_summaries_cycle", columnNames = "admission_cycle_id"))
public class AdmissionCycleArchiveSummary extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admission_cycle_id", nullable = false)
    private AdmissionCycle admissionCycle;

    @Column(name = "total_applications", nullable = false)
    private int totalApplications;

    @Column(name = "submitted_applications", nullable = false)
    private int submittedApplications;

    @Column(name = "eligible_applications", nullable = false)
    private int eligibleApplications;

    @Column(name = "selected_applications", nullable = false)
    private int selectedApplications;

    @Column(name = "offered_applications", nullable = false)
    private int offeredApplications;

    @Column(name = "accepted_applications", nullable = false)
    private int acceptedApplications;

    @Column(name = "converted_applications", nullable = false)
    private int convertedApplications;

    @Column(name = "archived_by_user_id", nullable = false)
    private UUID archivedByUserId;

    @Column(name = "archived_at", nullable = false)
    private Instant archivedAt;

    protected AdmissionCycleArchiveSummary() {
    }

    public AdmissionCycleArchiveSummary(
            AdmissionCycle admissionCycle,
            int totalApplications,
            int submittedApplications,
            int eligibleApplications,
            int selectedApplications,
            int offeredApplications,
            int acceptedApplications,
            int convertedApplications,
            UUID archivedByUserId,
            Instant archivedAt) {
        this.admissionCycle = admissionCycle;
        this.totalApplications = totalApplications;
        this.submittedApplications = submittedApplications;
        this.eligibleApplications = eligibleApplications;
        this.selectedApplications = selectedApplications;
        this.offeredApplications = offeredApplications;
        this.acceptedApplications = acceptedApplications;
        this.convertedApplications = convertedApplications;
        this.archivedByUserId = archivedByUserId;
        this.archivedAt = archivedAt;
    }

    public AdmissionCycle getAdmissionCycle() {
        return admissionCycle;
    }

    public int getTotalApplications() {
        return totalApplications;
    }

    public int getSubmittedApplications() {
        return submittedApplications;
    }

    public int getEligibleApplications() {
        return eligibleApplications;
    }

    public int getSelectedApplications() {
        return selectedApplications;
    }

    public int getOfferedApplications() {
        return offeredApplications;
    }

    public int getAcceptedApplications() {
        return acceptedApplications;
    }

    public int getConvertedApplications() {
        return convertedApplications;
    }

    public UUID getArchivedByUserId() {
        return archivedByUserId;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
