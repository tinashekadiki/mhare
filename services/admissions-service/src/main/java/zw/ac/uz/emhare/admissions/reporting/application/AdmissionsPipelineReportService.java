package zw.ac.uz.emhare.admissions.reporting.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.admissions.reporting.infrastructure.persistence.AdmissionsPipelineReportRepository;
import zw.ac.uz.emhare.admissions.reporting.infrastructure.persistence.AdmissionsPipelineReportRow;

/** Builds ADM-RPT-001 without multiplying applications by programme choices. @author Tinashe K */
@Service
public class AdmissionsPipelineReportService {

    private static final String NOT_RECORDED = "NOT_RECORDED";

    private final AdmissionsPipelineReportRepository reportRepository;
    private final Clock clock;

    public AdmissionsPipelineReportService(AdmissionsPipelineReportRepository reportRepository, Clock clock) {
        this.reportRepository = reportRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdmissionsPipelineReport generate(AdmissionsPipelineReportQuery query) {
        ReportAccumulator total = new ReportAccumulator();
        Map<UUID, IntakeAccumulator> intakes = new LinkedHashMap<>();
        Map<UUID, ProgrammeAccumulator> programmes = new LinkedHashMap<>();

        for (AdmissionsPipelineReportRow row : reportRepository.findReportRows(query)) {
            total.add(row);
            intakes.computeIfAbsent(row.intakeId(), ignored -> new IntakeAccumulator(row)).add(row);
            if (row.programmeId() != null && row.choiceId() != null) {
                programmes.computeIfAbsent(row.programmeId(), ignored -> new ProgrammeAccumulator(row)).add(row);
            }
        }

        List<AdmissionsPipelineReport.IntakeStatistic> intakeStatistics = intakes.values().stream()
                .map(IntakeAccumulator::toStatistic)
                .sorted(Comparator.comparing(AdmissionsPipelineReport.IntakeStatistic::intakeCode,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        List<AdmissionsPipelineReport.ProgrammeStatistic> programmeStatistics = programmes.values().stream()
                .map(ProgrammeAccumulator::toStatistic)
                .sorted(Comparator.comparing(AdmissionsPipelineReport.ProgrammeStatistic::programmeCode,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        return new AdmissionsPipelineReport(
                Instant.now(clock),
                total.applicationIds.size(),
                total.applicantIds.size(),
                total.counts(total.statusApplications),
                total.counts(total.paymentApplications),
                total.counts(total.categoryApplicants),
                total.counts(total.genderApplicants),
                total.rankCounts(),
                intakeStatistics,
                programmeStatistics,
                reportRepository.findFilterOptions());
    }

    private static class ReportAccumulator {
        protected final Set<UUID> applicationIds = new HashSet<>();
        protected final Set<UUID> applicantIds = new HashSet<>();
        protected final Map<String, Set<UUID>> statusApplications = new HashMap<>();
        protected final Map<String, Set<UUID>> paymentApplications = new HashMap<>();
        protected final Map<String, Set<UUID>> categoryApplicants = new HashMap<>();
        protected final Map<String, Set<UUID>> genderApplicants = new HashMap<>();
        private final Map<Integer, Set<UUID>> rankChoiceIds = new HashMap<>();
        private final Map<Integer, Set<UUID>> rankApplicationIds = new HashMap<>();

        void add(AdmissionsPipelineReportRow row) {
            applicationIds.add(row.applicationId());
            applicantIds.add(row.applicantId());
            addDimension(statusApplications, row.applicationStatus(), row.applicationId());
            addDimension(paymentApplications, paymentStatus(row), row.applicationId());
            addDimension(categoryApplicants, codeOrNotRecorded(row.applicantCategoryCode()), row.applicantId());
            addDimension(genderApplicants, codeOrNotRecorded(row.genderCode()), row.applicantId());
            if (row.choiceId() != null && row.choiceRank() != null) {
                rankChoiceIds.computeIfAbsent(row.choiceRank(), ignored -> new HashSet<>()).add(row.choiceId());
                rankApplicationIds.computeIfAbsent(row.choiceRank(), ignored -> new HashSet<>()).add(row.applicationId());
            }
        }

        List<AdmissionsPipelineReport.DimensionCount> counts(Map<String, Set<UUID>> values) {
            return values.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> new AdmissionsPipelineReport.DimensionCount(entry.getKey(), entry.getValue().size()))
                    .toList();
        }

        List<AdmissionsPipelineReport.RankedChoiceCount> rankCounts() {
            List<AdmissionsPipelineReport.RankedChoiceCount> result = new ArrayList<>();
            rankChoiceIds.keySet().stream().sorted().forEach(rank -> result.add(
                    new AdmissionsPipelineReport.RankedChoiceCount(
                            rank,
                            rankChoiceIds.get(rank).size(),
                            rankApplicationIds.getOrDefault(rank, Set.of()).size())));
            return List.copyOf(result);
        }

        private static void addDimension(Map<String, Set<UUID>> dimensions, String code, UUID applicationId) {
            dimensions.computeIfAbsent(code, ignored -> new HashSet<>()).add(applicationId);
        }
    }

    private static final class IntakeAccumulator extends ReportAccumulator {
        private final UUID intakeId;
        private final String intakeCode;
        private final String intakeName;

        private IntakeAccumulator(AdmissionsPipelineReportRow row) {
            intakeId = row.intakeId();
            intakeCode = row.intakeCode();
            intakeName = row.intakeName();
        }

        private AdmissionsPipelineReport.IntakeStatistic toStatistic() {
            return new AdmissionsPipelineReport.IntakeStatistic(
                    intakeId,
                    intakeCode,
                    intakeName,
                    applicationIds.size(),
                    applicantIds.size(),
                    counts(statusApplications),
                    counts(categoryApplicants),
                    counts(genderApplicants),
                    rankCounts());
        }
    }

    private static final class ProgrammeAccumulator extends ReportAccumulator {
        private final UUID programmeId;
        private final String programmeCode;
        private final String programmeName;
        private final String owningAcademicUnitName;
        private final Set<UUID> choiceIds = new HashSet<>();

        private ProgrammeAccumulator(AdmissionsPipelineReportRow row) {
            programmeId = row.programmeId();
            programmeCode = row.programmeCode();
            programmeName = row.programmeName();
            owningAcademicUnitName = row.owningAcademicUnitName();
        }

        @Override
        void add(AdmissionsPipelineReportRow row) {
            super.add(row);
            choiceIds.add(row.choiceId());
        }

        private AdmissionsPipelineReport.ProgrammeStatistic toStatistic() {
            return new AdmissionsPipelineReport.ProgrammeStatistic(
                    programmeId,
                    programmeCode,
                    programmeName,
                    owningAcademicUnitName,
                    applicationIds.size(),
                    applicantIds.size(),
                    choiceIds.size(),
                    counts(statusApplications),
                    counts(categoryApplicants),
                    counts(genderApplicants),
                    rankCounts());
        }
    }

    private static String codeOrNotRecorded(String value) {
        return value == null || value.isBlank() ? NOT_RECORDED : value;
    }

    private static String paymentStatus(AdmissionsPipelineReportRow row) {
        if (!row.paymentRequired()) {
            return "NOT_REQUIRED";
        }
        if (row.paymentConfirmedAt() != null) {
            return "PAID";
        }
        if (row.paymentOverrideByUserId() != null) {
            return "WAIVED";
        }
        return "PENDING";
    }
}
