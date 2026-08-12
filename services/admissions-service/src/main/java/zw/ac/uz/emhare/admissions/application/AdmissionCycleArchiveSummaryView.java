package zw.ac.uz.emhare.admissions.application;

import zw.ac.uz.emhare.admissions.domain.model.AdmissionCycleArchiveSummary;

import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record AdmissionCycleArchiveSummaryView(
        UUID admissionCycleId,
        int totalApplications,
        int submittedApplications,
        int eligibleApplications,
        int selectedApplications,
        int offeredApplications,
        int acceptedApplications,
        int convertedApplications,
        UUID archivedByUserId,
        Instant archivedAt) {

    static AdmissionCycleArchiveSummaryView from(AdmissionCycleArchiveSummary summary) {
        return new AdmissionCycleArchiveSummaryView(
                summary.getAdmissionCycle().getId(), summary.getTotalApplications(), summary.getSubmittedApplications(),
                summary.getEligibleApplications(), summary.getSelectedApplications(), summary.getOfferedApplications(),
                summary.getAcceptedApplications(), summary.getConvertedApplications(),
                summary.getArchivedByUserId(), summary.getArchivedAt());
    }
}
