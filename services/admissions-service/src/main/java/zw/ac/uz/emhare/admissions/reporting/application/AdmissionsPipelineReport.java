package zw.ac.uz.emhare.admissions.reporting.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Authoritative admissions pipeline totals and reusable reporting dimensions. @author Tinashe K */
public record AdmissionsPipelineReport(
        Instant generatedAt,
        long totalApplications,
        long totalApplicants,
        List<DimensionCount> statusCounts,
        List<DimensionCount> paymentCounts,
        List<DimensionCount> categoryCounts,
        List<DimensionCount> genderCounts,
        List<RankedChoiceCount> rankedChoiceCounts,
        List<IntakeStatistic> intakeStatistics,
        List<ProgrammeStatistic> programmeStatistics,
        AdmissionsPipelineFilterOptions filterOptions) {

    public record DimensionCount(String code, long count) {}

    public record RankedChoiceCount(int rank, long choices, long applications) {}

    public record IntakeStatistic(
            UUID intakeId,
            String intakeCode,
            String intakeName,
            long applications,
            long applicants,
            List<DimensionCount> statusCounts,
            List<DimensionCount> categoryCounts,
            List<DimensionCount> genderCounts,
            List<RankedChoiceCount> rankedChoiceCounts) {}

    public record ProgrammeStatistic(
            UUID programmeId,
            String programmeCode,
            String programmeName,
            String owningAcademicUnitName,
            long applications,
            long applicants,
            long choices,
            List<DimensionCount> statusCounts,
            List<DimensionCount> categoryCounts,
            List<DimensionCount> genderCounts,
            List<RankedChoiceCount> rankedChoiceCounts) {}

    public record FilterOption(String value, String code, String label) {}
}
