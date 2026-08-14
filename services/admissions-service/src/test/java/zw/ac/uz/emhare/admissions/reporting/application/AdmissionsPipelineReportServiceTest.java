package zw.ac.uz.emhare.admissions.reporting.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.ac.uz.emhare.admissions.reporting.infrastructure.persistence.AdmissionsPipelineReportRepository;
import zw.ac.uz.emhare.admissions.reporting.infrastructure.persistence.AdmissionsPipelineReportRow;

/** @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class AdmissionsPipelineReportServiceTest {

    private static final Instant GENERATED_AT = Instant.parse("2026-08-13T16:00:00Z");

    @Mock
    private AdmissionsPipelineReportRepository reportRepository;

    @Test
    void generate_shouldKeepPipelineTotalsDistinctWhileCountingEveryRankedChoice() {
        UUID intakeId = UUID.randomUUID();
        UUID undergraduateTypeId = UUID.randomUUID();
        UUID firstApplicationId = UUID.randomUUID();
        UUID secondApplicationId = UUID.randomUUID();
        UUID firstApplicantId = UUID.randomUUID();
        UUID secondApplicantId = UUID.randomUUID();
        UUID accountingProgrammeId = UUID.randomUUID();
        UUID computerScienceProgrammeId = UUID.randomUUID();
        AdmissionsPipelineReportQuery query = AdmissionsPipelineReportQuery.empty();
        when(reportRepository.findReportRows(query)).thenReturn(List.of(
                row(firstApplicationId, firstApplicantId, "UNDER_REVIEW", true, GENERATED_AT, null,
                        intakeId, undergraduateTypeId, "LOCAL", "FEMALE", 1, accountingProgrammeId,
                        "HACC", "Bachelor of Accountancy"),
                row(firstApplicationId, firstApplicantId, "UNDER_REVIEW", true, GENERATED_AT, null,
                        intakeId, undergraduateTypeId, "LOCAL", "FEMALE", 2, computerScienceProgrammeId,
                        "HCS", "Bachelor of Computer Science"),
                row(secondApplicationId, secondApplicantId, "OFFERED", false, null, null,
                        intakeId, undergraduateTypeId, "INTERNATIONAL", "MALE", 1, computerScienceProgrammeId,
                        "HCS", "Bachelor of Computer Science")));
        when(reportRepository.findFilterOptions()).thenReturn(filterOptions(
                intakeId, undergraduateTypeId, accountingProgrammeId, computerScienceProgrammeId));
        AdmissionsPipelineReportService service = new AdmissionsPipelineReportService(
                reportRepository, Clock.fixed(GENERATED_AT, ZoneOffset.UTC));

        AdmissionsPipelineReport report = service.generate(query);

        assertEquals(2, report.totalApplications());
        assertEquals(2, report.totalApplicants());
        assertCount(report.statusCounts(), "UNDER_REVIEW", 1);
        assertCount(report.statusCounts(), "OFFERED", 1);
        assertCount(report.paymentCounts(), "PAID", 1);
        assertCount(report.paymentCounts(), "NOT_REQUIRED", 1);
        assertCount(report.categoryCounts(), "LOCAL", 1);
        assertCount(report.genderCounts(), "FEMALE", 1);
        assertRank(report.rankedChoiceCounts(), 1, 2, 2);
        assertRank(report.rankedChoiceCounts(), 2, 1, 1);
        assertEquals(1, report.intakeStatistics().size());
        assertEquals(2, report.intakeStatistics().getFirst().applications());
        assertEquals(2, report.programmeStatistics().size());
        AdmissionsPipelineReport.ProgrammeStatistic computerScience = report.programmeStatistics().stream()
                .filter(statistic -> statistic.programmeId().equals(computerScienceProgrammeId))
                .findFirst().orElseThrow();
        assertEquals(2, computerScience.applications());
        assertEquals(2, computerScience.choices());
        assertRank(computerScience.rankedChoiceCounts(), 1, 1, 1);
        assertRank(computerScience.rankedChoiceCounts(), 2, 1, 1);
        assertEquals(2, report.filterOptions().programmes().size());
    }

    @Test
    void query_shouldNormalizeControlledDemographicCodes() {
        AdmissionsPipelineReportQuery query = AdmissionsPipelineReportQuery.of(
                null, null, null, " local ", " female ");

        assertEquals("LOCAL", query.categoryCode());
        assertEquals("FEMALE", query.genderCode());
    }

    @Test
    void generate_shouldRetainUnrecordedDemographicsAndPaymentClearanceEdgesWithoutChoices() {
        UUID intakeId = UUID.randomUUID();
        UUID applicationTypeId = UUID.randomUUID();
        UUID pendingApplicationId = UUID.randomUUID();
        UUID waivedApplicationId = UUID.randomUUID();
        AdmissionsPipelineReportQuery query = AdmissionsPipelineReportQuery.empty();
        AdmissionsPipelineReportRow pending = new AdmissionsPipelineReportRow(
                pendingApplicationId, UUID.randomUUID(), "PAYMENT_PENDING", true, null, null,
                intakeId, "AUG-2026", "August 2026", applicationTypeId, "UNDERGRAD", "Undergraduate",
                "", null, null, null, null, null, null, null);
        AdmissionsPipelineReportRow waived = new AdmissionsPipelineReportRow(
                waivedApplicationId, UUID.randomUUID(), "UNDER_REVIEW", true, null, UUID.randomUUID(),
                intakeId, "AUG-2026", "August 2026", applicationTypeId, "UNDERGRAD", "Undergraduate",
                "LOCAL", "FEMALE", null, null, null, null, null, null);
        when(reportRepository.findReportRows(query)).thenReturn(List.of(pending, waived));
        when(reportRepository.findFilterOptions()).thenReturn(
                new AdmissionsPipelineFilterOptions(List.of(), List.of(), List.of(), List.of(), List.of()));
        AdmissionsPipelineReportService service = new AdmissionsPipelineReportService(
                reportRepository, Clock.fixed(GENERATED_AT, ZoneOffset.UTC));

        AdmissionsPipelineReport report = service.generate(query);

        assertCount(report.paymentCounts(), "PENDING", 1);
        assertCount(report.paymentCounts(), "WAIVED", 1);
        assertCount(report.categoryCounts(), "NOT_RECORDED", 1);
        assertCount(report.genderCounts(), "NOT_RECORDED", 1);
        assertEquals(0, report.programmeStatistics().size());
        assertEquals(0, report.rankedChoiceCounts().size());
        assertEquals("AUG-2026", report.intakeStatistics().getFirst().intakeCode());
    }

    @Test
    void query_shouldTreatBlankControlledCodesAsUnfiltered() {
        AdmissionsPipelineReportQuery query = AdmissionsPipelineReportQuery.of(
                null, null, null, "  ", null);

        assertEquals(null, query.categoryCode());
        assertEquals(null, query.genderCode());
    }

    @Test
    void generate_shouldCountAnApplicantsDemographicOnceAcrossMultipleApplications() {
        UUID applicantId = UUID.randomUUID();
        UUID intakeId = UUID.randomUUID();
        UUID applicationTypeId = UUID.randomUUID();
        AdmissionsPipelineReportQuery query = AdmissionsPipelineReportQuery.empty();
        when(reportRepository.findReportRows(query)).thenReturn(List.of(
                new AdmissionsPipelineReportRow(
                        UUID.randomUUID(), applicantId, "UNDER_REVIEW", false, null, null,
                        intakeId, "AUG-2026", "August 2026", applicationTypeId, "UNDERGRAD", "Undergraduate",
                        "LOCAL", "FEMALE", null, null, null, null, null, null),
                new AdmissionsPipelineReportRow(
                        UUID.randomUUID(), applicantId, "UNDER_REVIEW", false, null, null,
                        intakeId, "AUG-2026", "August 2026", applicationTypeId, "POSTGRAD", "Postgraduate",
                        "LOCAL", "FEMALE", null, null, null, null, null, null)));
        when(reportRepository.findFilterOptions()).thenReturn(
                new AdmissionsPipelineFilterOptions(List.of(), List.of(), List.of(), List.of(), List.of()));
        AdmissionsPipelineReportService service = new AdmissionsPipelineReportService(
                reportRepository, Clock.fixed(GENERATED_AT, ZoneOffset.UTC));

        AdmissionsPipelineReport report = service.generate(query);

        assertEquals(2, report.totalApplications());
        assertEquals(1, report.totalApplicants());
        assertCount(report.categoryCounts(), "LOCAL", 1);
        assertCount(report.genderCounts(), "FEMALE", 1);
    }

    private static AdmissionsPipelineReportRow row(
            UUID applicationId,
            UUID applicantId,
            String status,
            boolean paymentRequired,
            Instant paymentConfirmedAt,
            UUID paymentOverrideByUserId,
            UUID intakeId,
            UUID applicationTypeId,
            String category,
            String gender,
            int choiceRank,
            UUID programmeId,
            String programmeCode,
            String programmeName) {
        return new AdmissionsPipelineReportRow(
                applicationId, applicantId, status, paymentRequired, paymentConfirmedAt, paymentOverrideByUserId,
                intakeId, "AUG-2026", "August 2026", applicationTypeId, "UNDERGRAD", "Undergraduate",
                category, gender, UUID.randomUUID(), choiceRank, programmeId, programmeCode, programmeName,
                "Faculty of Business");
    }

    private static AdmissionsPipelineFilterOptions filterOptions(
            UUID intakeId,
            UUID applicationTypeId,
            UUID firstProgrammeId,
            UUID secondProgrammeId) {
        return new AdmissionsPipelineFilterOptions(
                List.of(new AdmissionsPipelineReport.FilterOption(intakeId.toString(), "AUG-2026", "August 2026")),
                List.of(new AdmissionsPipelineReport.FilterOption(applicationTypeId.toString(), "UNDERGRAD", "Undergraduate")),
                List.of(
                        new AdmissionsPipelineReport.FilterOption(firstProgrammeId.toString(), "HACC", "Bachelor of Accountancy"),
                        new AdmissionsPipelineReport.FilterOption(secondProgrammeId.toString(), "HCS", "Bachelor of Computer Science")),
                List.of(
                        new AdmissionsPipelineReport.FilterOption("INTERNATIONAL", "INTERNATIONAL", "International"),
                        new AdmissionsPipelineReport.FilterOption("LOCAL", "LOCAL", "Local")),
                List.of(
                        new AdmissionsPipelineReport.FilterOption("FEMALE", "FEMALE", "Female"),
                        new AdmissionsPipelineReport.FilterOption("MALE", "MALE", "Male")));
    }

    private static void assertCount(List<AdmissionsPipelineReport.DimensionCount> counts, String code, long expected) {
        assertEquals(expected, counts.stream().filter(count -> code.equals(count.code()))
                .findFirst().orElseThrow().count());
    }

    private static void assertRank(
            List<AdmissionsPipelineReport.RankedChoiceCount> counts,
            int rank,
            long choices,
            long applications) {
        AdmissionsPipelineReport.RankedChoiceCount count = counts.stream()
                .filter(item -> item.rank() == rank).findFirst().orElseThrow();
        assertEquals(choices, count.choices());
        assertEquals(applications, count.applications());
    }
}
