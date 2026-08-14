package zw.ac.uz.emhare.admissions.reporting.api.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsPipelineFilterOptions;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsPipelineReport;

/** HTTP representation for ADM-RPT-001. @author Tinashe K */
public record AdmissionsPipelineReportResponse(
        Instant generatedAt,
        long totalApplications,
        long totalApplicants,
        List<DimensionCountResponse> statusCounts,
        List<DimensionCountResponse> paymentCounts,
        List<DimensionCountResponse> categoryCounts,
        List<DimensionCountResponse> genderCounts,
        List<RankedChoiceCountResponse> rankedChoiceCounts,
        List<IntakeStatisticResponse> intakeStatistics,
        List<ProgrammeStatisticResponse> programmeStatistics,
        FilterOptionsResponse filterOptions) {

    public static AdmissionsPipelineReportResponse from(AdmissionsPipelineReport report) {
        return new AdmissionsPipelineReportResponse(
                report.generatedAt(),
                report.totalApplications(),
                report.totalApplicants(),
                dimensions(report.statusCounts()),
                dimensions(report.paymentCounts()),
                dimensions(report.categoryCounts()),
                dimensions(report.genderCounts()),
                ranks(report.rankedChoiceCounts()),
                report.intakeStatistics().stream().map(IntakeStatisticResponse::from).toList(),
                report.programmeStatistics().stream().map(ProgrammeStatisticResponse::from).toList(),
                FilterOptionsResponse.from(report.filterOptions()));
    }

    private static List<DimensionCountResponse> dimensions(List<AdmissionsPipelineReport.DimensionCount> counts) {
        return counts.stream().map(count -> new DimensionCountResponse(count.code(), count.count())).toList();
    }

    private static List<RankedChoiceCountResponse> ranks(List<AdmissionsPipelineReport.RankedChoiceCount> counts) {
        return counts.stream()
                .map(count -> new RankedChoiceCountResponse(count.rank(), count.choices(), count.applications()))
                .toList();
    }

    public record DimensionCountResponse(String code, long count) {}

    public record RankedChoiceCountResponse(int rank, long choices, long applications) {}

    public record IntakeStatisticResponse(
            UUID intakeId,
            String intakeCode,
            String intakeName,
            long applications,
            long applicants,
            List<DimensionCountResponse> statusCounts,
            List<DimensionCountResponse> categoryCounts,
            List<DimensionCountResponse> genderCounts,
            List<RankedChoiceCountResponse> rankedChoiceCounts) {

        private static IntakeStatisticResponse from(AdmissionsPipelineReport.IntakeStatistic statistic) {
            return new IntakeStatisticResponse(
                    statistic.intakeId(),
                    statistic.intakeCode(),
                    statistic.intakeName(),
                    statistic.applications(),
                    statistic.applicants(),
                    dimensions(statistic.statusCounts()),
                    dimensions(statistic.categoryCounts()),
                    dimensions(statistic.genderCounts()),
                    ranks(statistic.rankedChoiceCounts()));
        }
    }

    public record ProgrammeStatisticResponse(
            UUID programmeId,
            String programmeCode,
            String programmeName,
            String owningAcademicUnitName,
            long applications,
            long applicants,
            long choices,
            List<DimensionCountResponse> statusCounts,
            List<DimensionCountResponse> categoryCounts,
            List<DimensionCountResponse> genderCounts,
            List<RankedChoiceCountResponse> rankedChoiceCounts) {

        private static ProgrammeStatisticResponse from(AdmissionsPipelineReport.ProgrammeStatistic statistic) {
            return new ProgrammeStatisticResponse(
                    statistic.programmeId(),
                    statistic.programmeCode(),
                    statistic.programmeName(),
                    statistic.owningAcademicUnitName(),
                    statistic.applications(),
                    statistic.applicants(),
                    statistic.choices(),
                    dimensions(statistic.statusCounts()),
                    dimensions(statistic.categoryCounts()),
                    dimensions(statistic.genderCounts()),
                    ranks(statistic.rankedChoiceCounts()));
        }
    }

    public record FilterOptionResponse(String value, String code, String label) {
        private static FilterOptionResponse from(AdmissionsPipelineReport.FilterOption option) {
            return new FilterOptionResponse(option.value(), option.code(), option.label());
        }
    }

    public record FilterOptionsResponse(
            List<FilterOptionResponse> intakes,
            List<FilterOptionResponse> applicationTypes,
            List<FilterOptionResponse> programmes,
            List<FilterOptionResponse> categories,
            List<FilterOptionResponse> genders) {

        private static FilterOptionsResponse from(AdmissionsPipelineFilterOptions options) {
            return new FilterOptionsResponse(
                    options.intakes().stream().map(FilterOptionResponse::from).toList(),
                    options.applicationTypes().stream().map(FilterOptionResponse::from).toList(),
                    options.programmes().stream().map(FilterOptionResponse::from).toList(),
                    options.categories().stream().map(FilterOptionResponse::from).toList(),
                    options.genders().stream().map(FilterOptionResponse::from).toList());
        }
    }
}
