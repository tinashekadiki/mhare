package zw.ac.uz.emhare.admissions.reporting.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.admissions.integration.StudentRecordsReportingClient;
import zw.ac.uz.emhare.admissions.integration.StudentRecordsReportingClient.RegistrationOutcomeResult;
import zw.ac.uz.emhare.admissions.integration.http.StudentRecordsReportingHttpService.RegistrationOutcome;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsOperationalReport.ChartPoint;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsOperationalReport.Column;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsOperationalReport.Metric;
import zw.ac.uz.emhare.admissions.reporting.infrastructure.persistence.AdmissionsIntakeMovementRow;
import zw.ac.uz.emhare.admissions.reporting.infrastructure.persistence.AdmissionsOperationalReportRepository;
import zw.ac.uz.emhare.admissions.reporting.infrastructure.persistence.AdmissionsOperationalReportRow;

/** Builds every approved Admissions family from canonical workflow evidence. @author Tinashe K */
@Service
public class AdmissionsOperationalReportService {

    private static final Set<String> ACCEPTED_STATUSES = Set.of("ACCEPTED", "CONVERTED");
    private static final Set<String> SELECTED_STATUSES = Set.of("ADMITTED", "OFFERED", "ACCEPTED", "CONVERTED");
    private static final Set<String> REJECTED_STATUSES = Set.of("NOT_ELIGIBLE", "REJECTED");
    private static final Set<String> FINAL_NOT_SELECTED_STATUSES = Set.of("NOT_ELIGIBLE", "REJECTED", "DECLINED", "WITHDRAWN");

    private final AdmissionsOperationalReportRepository repository;
    private final AdmissionsReportCatalogueService catalogueService;
    private final StudentRecordsReportingClient studentRecordsClient;
    private final Clock clock;

    public AdmissionsOperationalReportService(
            AdmissionsOperationalReportRepository repository,
            AdmissionsReportCatalogueService catalogueService,
            StudentRecordsReportingClient studentRecordsClient,
            Clock clock) {
        this.repository = repository;
        this.catalogueService = catalogueService;
        this.studentRecordsClient = studentRecordsClient;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdmissionsOperationalReport generate(AdmissionsReportCode code, AdmissionsPipelineReportQuery query) {
        AdmissionsReportDefinition definition = catalogueService.require(code);
        Instant generatedAt = clock.instant();
        if (code == AdmissionsReportCode.INTAKE_MOVEMENTS) {
            return intakeMovements(definition, generatedAt, repository.findIntakeMovements(query));
        }
        List<AdmissionsOperationalReportRow> rows = repository.findRows(query);
        return switch (code) {
            case APPLICATION_DEMAND -> demand(definition, generatedAt, rows);
            case EXECUTIVE_STATISTICS -> executive(definition, generatedAt, rows);
            case APPLICANT_REGISTERS -> registers(definition, generatedAt, rows);
            case SPECIAL_CATEGORY_REGISTERS -> specialCategories(definition, generatedAt, rows);
            case SELECTION_SCHEDULES -> selectionSchedules(definition, generatedAt, rows);
            case ADMISSIONS_ANALYSIS -> analysis(definition, generatedAt, rows);
            case OFFER_LETTERS -> offerLetters(definition, generatedAt, rows);
            case INTAKE_MOVEMENTS -> throw new IllegalStateException("Intake movements are handled separately.");
        };
    }

    private AdmissionsOperationalReport demand(
            AdmissionsReportDefinition definition,
            Instant generatedAt,
            List<AdmissionsOperationalReportRow> evidence) {
        Map<String, DemandAggregate> programmes = new LinkedHashMap<>();
        for (AdmissionsOperationalReportRow row : evidence) {
            if (row.choiceId() == null) continue;
            String key = text(row.programmeCode(), row.programmeId());
            programmes.computeIfAbsent(key, ignored -> new DemandAggregate(row)).add(row);
        }
        List<List<String>> rows = programmes.values().stream()
                .sorted(Comparator.comparing(value -> value.programmeCode, String.CASE_INSENSITIVE_ORDER))
                .map(DemandAggregate::row).toList();
        List<ChartPoint> chart = programmes.values().stream()
                .sorted(Comparator.comparingLong(DemandAggregate::choiceCount).reversed())
                .limit(20)
                .map(value -> new ChartPoint(value.programmeCode, value.choiceCount(), "Programme choices"))
                .toList();
        return report(definition, generatedAt, evidence, rows, chart,
                columns("programme", "Programme", "academicUnit", "Academic unit", "applications", "Applications",
                        "applicants", "Applicants", "choices", "Choices", "first", "1st choice", "second", "2nd choice",
                        "third", "3rd choice", "female", "Female", "male", "Male", "otherGender", "Other/not recorded",
                        "offered", "Offered", "accepted", "Accepted", "pending", "Pending approval"),
                List.of("Applications and applicants are distinct. Choice counts include every ranked Programme choice."));
    }

    private AdmissionsOperationalReport executive(
            AdmissionsReportDefinition definition,
            Instant generatedAt,
            List<AdmissionsOperationalReportRow> evidence) {
        RegistrationOutcomeResult registrationResult = studentRecordsClient.outcomes();
        Set<UUID> confirmedApplications = registrationResult.outcomes().stream()
                .filter(outcome -> "CONFIRMED".equals(outcome.registrationStatus()))
                .map(RegistrationOutcome::sourceApplicationId).collect(java.util.stream.Collectors.toSet());
        List<List<String>> rows = new ArrayList<>();
        appendExecutive(rows, "Intake", evidence, row -> text(row.intakeCode(), row.intakeId()), confirmedApplications);
        appendExecutive(rows, "Academic unit", evidence, row -> display(row.owningAcademicUnitName()), confirmedApplications);
        appendExecutive(rows, "Programme", evidence, row -> text(row.programmeCode(), row.programmeId()), confirmedApplications);
        appendExecutive(rows, "Submitted year", evidence, row -> row.submittedAt() == null
                ? "Not submitted" : String.valueOf(row.submittedAt().atZone(ZoneId.of("Africa/Harare")).getYear()), confirmedApplications);
        List<String> notes = new ArrayList<>();
        notes.add("Accepted totals come from current accepted or converted applications; registered totals require a confirmed Student Records registration session.");
        if (!registrationResult.available()) notes.add(registrationResult.unavailableReason());
        return report(definition, generatedAt, evidence, rows,
                rows.stream().filter(row -> "Programme".equals(row.get(0))).map(row ->
                        new ChartPoint(row.get(1), parse(row.get(5)), "Accepted")).toList(),
                columns("scope", "Scope", "value", "Value", "applications", "Applications", "applicants", "Applicants",
                        "offered", "Offered", "accepted", "Accepted", "converted", "Converted", "registered", "Registered"), notes);
    }

    private static void appendExecutive(
            List<List<String>> destination,
            String scope,
            List<AdmissionsOperationalReportRow> evidence,
            Function<AdmissionsOperationalReportRow, String> dimension,
            Set<UUID> confirmedApplications) {
        Map<String, ExecutiveAggregate> groups = new LinkedHashMap<>();
        evidence.forEach(row -> groups.computeIfAbsent(dimension.apply(row), ignored -> new ExecutiveAggregate()).add(row));
        groups.entrySet().stream().sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)).forEach(entry -> {
            ExecutiveAggregate value = entry.getValue();
            long registered = value.applicationIds.stream().filter(confirmedApplications::contains).count();
            destination.add(List.of(scope, entry.getKey(), number(value.applicationIds.size()),
                    number(value.applicantIds.size()), number(value.offered.size()), number(value.accepted.size()),
                    number(value.converted.size()), number(registered)));
        });
    }

    private AdmissionsOperationalReport registers(
            AdmissionsReportDefinition definition,
            Instant generatedAt,
            List<AdmissionsOperationalReportRow> evidence) {
        List<List<String>> rows = new ArrayList<>();
        applications(evidence).values().forEach(applicationRows -> {
            AdmissionsOperationalReportRow application = applicationRows.getFirst();
            Set<String> registers = registerMembership(application, applicationRows);
            String programmes = programmeSummary(applicationRows);
            for (String register : registers) {
                rows.add(List.of(register, application.applicationNumber(), application.applicantNumber(),
                        application.applicantName(), display(application.intakeCode()),
                        display(application.applicationTypeCode()), application.applicationStatus(), programmes,
                        instant(application.submittedAt())));
            }
        });
        rows.sort(Comparator.comparing((List<String> row) -> row.get(0)).thenComparing(row -> row.get(3)));
        return report(definition, generatedAt, evidence, rows, List.of(),
                columns("register", "Register", "applicationNumber", "Application", "applicantNumber", "Applicant number",
                        "applicant", "Applicant", "intake", "Intake", "route", "Route", "status", "Current status",
                        "programmes", "Programme choices", "submittedAt", "Submitted at"),
                List.of("A record may appear in more than one operational register where the named business views overlap.",
                        "Selected is decision-backed (admitted or later); accepted requires an accepted offer response or converted state."));
    }

    private AdmissionsOperationalReport specialCategories(
            AdmissionsReportDefinition definition,
            Instant generatedAt,
            List<AdmissionsOperationalReportRow> evidence) {
        List<List<String>> rows = new ArrayList<>();
        applications(evidence).values().forEach(applicationRows -> {
            AdmissionsOperationalReportRow application = applicationRows.getFirst();
            boolean disabled = hasDisability(application.disabilityStatusCode());
            boolean staffDependant = contains(application.sponsorTypeCode(), "STAFF");
            boolean accepted = ACCEPTED_STATUSES.contains(application.applicationStatus());
            List<String> categories = new ArrayList<>();
            if (disabled) categories.add("Applicants with disabilities");
            if (staffDependant) categories.add("Staff dependants");
            if (disabled) categories.add(accepted ? "Disabled applicants accepted" : "Disabled applicants not accepted");
            categories.forEach(category -> rows.add(List.of(category, application.applicationNumber(),
                    application.applicantNumber(), application.applicantName(), display(application.disabilityStatusCode()),
                    display(application.sponsorTypeCode()), application.applicationStatus(), programmeSummary(applicationRows))));
        });
        return report(definition, generatedAt, evidence, rows, List.of(),
                columns("category", "Register", "applicationNumber", "Application", "applicantNumber", "Applicant number",
                        "applicant", "Applicant", "disability", "Disability", "sponsor", "Sponsor type",
                        "status", "Current status", "programmes", "Programme choices"),
                List.of("Special categories use the applicant profile snapshot; blank and explicit no-disability values are excluded."));
    }

    private AdmissionsOperationalReport selectionSchedules(
            AdmissionsReportDefinition definition,
            Instant generatedAt,
            List<AdmissionsOperationalReportRow> evidence) {
        List<List<String>> rows = new ArrayList<>();
        evidence.stream().filter(AdmissionsOperationalReportService::selectedChoice).forEach(row -> {
            List<String> schedules = new ArrayList<>();
            if (contains(row.applicationTypeCode(), "POST") || contains(row.applicationTypeCode(), "MBA")) {
                schedules.add("Postgraduate SAR");
            } else {
                schedules.add("Undergraduate SAR");
                schedules.add("Undergraduate rolling admissions");
            }
            schedules.add("Trimmed Programme list");
            if (contains(row.applicationTypeCode(), "EDUCATION")) schedules.add("Selected-applicant education report");
            schedules.forEach(schedule -> rows.add(List.of(schedule, row.applicationNumber(), row.applicantNumber(),
                    row.applicantName(), display(row.intakeCode()), text(row.programmeCode(), row.programmeId()),
                    display(row.owningAcademicUnitName()), number(row.choiceRank()), decimal(row.totalPoints()),
                    display(row.decision()), display(row.offerStatus()), display(row.qualificationSummary()),
                    display(row.schoolsAttended()))));
        });
        return report(definition, generatedAt, evidence, rows, List.of(),
                columns("schedule", "Schedule", "applicationNumber", "Application", "applicantNumber", "Applicant number",
                        "applicant", "Applicant", "intake", "Intake", "programme", "Programme", "academicUnit", "Academic unit",
                        "rank", "Choice rank", "points", "Points", "decision", "Decision", "offerStatus", "Offer status",
                        "qualifications", "Qualification evidence", "schools", "Schools/institutions"),
                List.of("New eMhare uses rolling per-applicant decisions; the undergraduate rolling schedule replaces the legacy supplementary batch lifecycle."));
    }

    private AdmissionsOperationalReport intakeMovements(
            AdmissionsReportDefinition definition,
            Instant generatedAt,
            List<AdmissionsIntakeMovementRow> evidence) {
        List<List<String>> rows = evidence.stream().map(row -> List.of(
                ACCEPTED_STATUSES.contains(row.applicationStatus()) ? "Accepted applicants moved" : "Undecided applicants moved",
                row.applicationNumber(), row.applicantNumber(), row.applicantName(), row.applicationStatus(),
                display(row.previousIntakeCode()), display(row.newIntakeCode()), instant(row.changedAt()),
                display(row.changedByUserId()), display(row.reason()))).toList();
        return new AdmissionsOperationalReport(definition, generatedAt,
                List.of(new Metric("Movements", number(rows.size()))),
                columns("movement", "Movement", "applicationNumber", "Application", "applicantNumber", "Applicant number",
                        "applicant", "Applicant", "status", "Current status", "fromIntake", "From intake",
                        "toIntake", "To intake", "changedAt", "Changed at", "changedBy", "Changed by", "reason", "Audit reason"),
                rows, List.of(), List.of("Movement rows are reconstructed from Envers before-and-after intake snapshots."));
    }

    private AdmissionsOperationalReport analysis(
            AdmissionsReportDefinition definition,
            Instant generatedAt,
            List<AdmissionsOperationalReportRow> evidence) {
        List<List<String>> rows = new ArrayList<>();
        appendAnalysis(rows, "Academic unit", evidence, row -> display(row.owningAcademicUnitName()));
        appendAnalysis(rows, "Programme", evidence, row -> text(row.programmeCode(), row.programmeId()));
        appendAnalysis(rows, "Intake", evidence, row -> text(row.intakeCode(), row.intakeId()));
        appendAnalysis(rows, "Gender", evidence, row -> display(row.genderCode()));
        appendAnalysis(rows, "Choice rank", evidence, row -> row.choiceRank() == null ? "No choice" : ordinal(row.choiceRank()));
        appendAnalysis(rows, "School/institution", evidence, row -> display(row.schoolsAttended()));
        List<ChartPoint> chart = rows.stream().filter(row -> "Programme".equals(row.get(0)))
                .sorted(Comparator.comparingLong((List<String> row) -> parse(row.get(4))).reversed()).limit(20)
                .map(row -> new ChartPoint(row.get(1), parse(row.get(4)), "Choices")).toList();
        return report(definition, generatedAt, evidence, rows, chart,
                columns("dimension", "Dimension", "value", "Value", "applications", "Applications",
                        "applicants", "Applicants", "choices", "Choices", "accepted", "Accepted"),
                List.of("Each row states its dimension. Applicant and application totals remain distinct; choice totals count ranked choice records."));
    }

    private static void appendAnalysis(
            List<List<String>> destination,
            String dimensionName,
            List<AdmissionsOperationalReportRow> evidence,
            Function<AdmissionsOperationalReportRow, String> dimension) {
        Map<String, ExecutiveAggregate> groups = new LinkedHashMap<>();
        evidence.forEach(row -> groups.computeIfAbsent(dimension.apply(row), ignored -> new ExecutiveAggregate()).add(row));
        groups.entrySet().stream().sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)).forEach(entry -> {
            ExecutiveAggregate value = entry.getValue();
            destination.add(List.of(dimensionName, entry.getKey(), number(value.applicationIds.size()),
                    number(value.applicantIds.size()), number(value.choiceIds.size()), number(value.accepted.size())));
        });
    }

    private AdmissionsOperationalReport offerLetters(
            AdmissionsReportDefinition definition,
            Instant generatedAt,
            List<AdmissionsOperationalReportRow> evidence) {
        List<List<String>> rows = evidence.stream().filter(row -> row.offerId() != null).map(row -> List.of(
                offerCategory(row), row.offerNumber(), row.applicationNumber(), row.applicantNumber(), row.applicantName(),
                text(row.programmeCode(), row.programmeId()), display(row.offerType()), display(row.offerStatus()),
                row.publishedAt() == null ? "Not published" : "Published", display(row.emailDeliveryStatus()),
                display(row.offerResponse()))).toList();
        return report(definition, generatedAt, evidence, rows, List.of(),
                columns("category", "Letter category", "offerNumber", "Offer", "applicationNumber", "Application",
                        "applicantNumber", "Applicant number", "applicant", "Applicant", "programme", "Programme",
                        "offerType", "Offer type", "offerStatus", "Offer status", "publication", "Publication",
                        "emailStatus", "Email status", "response", "Response"),
                List.of("This register is read-only. PDF generation, current publication, bulk print and email remain governed actions in the offer-letter workspace."));
    }

    private AdmissionsOperationalReport report(
            AdmissionsReportDefinition definition,
            Instant generatedAt,
            List<AdmissionsOperationalReportRow> evidence,
            List<List<String>> rows,
            List<ChartPoint> chart,
            List<Column> columns,
            List<String> notes) {
        Set<UUID> applications = new HashSet<>();
        Set<UUID> applicants = new HashSet<>();
        evidence.forEach(row -> { applications.add(row.applicationId()); applicants.add(row.applicantId()); });
        return new AdmissionsOperationalReport(definition, generatedAt,
                List.of(new Metric("Applications", number(applications.size())),
                        new Metric("Applicants", number(applicants.size())),
                        new Metric("Report rows", number(rows.size()))),
                columns, rows, chart, notes);
    }

    private static Map<UUID, List<AdmissionsOperationalReportRow>> applications(List<AdmissionsOperationalReportRow> rows) {
        Map<UUID, List<AdmissionsOperationalReportRow>> applications = new LinkedHashMap<>();
        rows.forEach(row -> applications.computeIfAbsent(row.applicationId(), ignored -> new ArrayList<>()).add(row));
        return applications;
    }

    private static Set<String> registerMembership(
            AdmissionsOperationalReportRow application,
            List<AdmissionsOperationalReportRow> rows) {
        Set<String> registers = new LinkedHashSet<>();
        String status = application.applicationStatus();
        if (!"DRAFT".equals(status)) registers.add("Applied");
        if ("DRAFT".equals(status)) registers.add("Applying");
        if (Set.of("SUBMITTED", "PAYMENT_PENDING", "INCOMPLETE").contains(status)) registers.add("Unconfirmed");
        boolean decided = rows.stream().anyMatch(row -> row.decision() != null);
        if (!decided && !SELECTED_STATUSES.contains(status) && !FINAL_NOT_SELECTED_STATUSES.contains(status)) {
            registers.add("Not yet selected");
        }
        if (SELECTED_STATUSES.contains(status)) registers.add("Selected");
        if (ACCEPTED_STATUSES.contains(status)) registers.add("Accepted");
        if (REJECTED_STATUSES.contains(status)) registers.add("Rejected");
        if (FINAL_NOT_SELECTED_STATUSES.contains(status)) registers.add("Not selected");
        return registers;
    }

    private static String programmeSummary(List<AdmissionsOperationalReportRow> rows) {
        return rows.stream().filter(row -> row.choiceId() != null)
                .sorted(Comparator.comparing(row -> row.choiceRank() == null ? Integer.MAX_VALUE : row.choiceRank()))
                .map(row -> number(row.choiceRank()) + ". " + text(row.programmeCode(), row.programmeId()))
                .distinct().collect(java.util.stream.Collectors.joining(" | "));
    }

    private static boolean selectedChoice(AdmissionsOperationalReportRow row) {
        return "ADMIT".equals(row.decision())
                || Set.of("ADMITTED", "OFFERED", "CONVERTED").contains(row.choiceStatus());
    }

    private static String offerCategory(AdmissionsOperationalReportRow row) {
        String domicile = "INTERNATIONAL".equals(row.applicantCategoryCode()) ? "International" : "Local";
        if (contains(row.applicationTypeCode(), "TRANSFER")) return domicile + " transfer";
        if (contains(row.applicationTypeCode(), "POST") || contains(row.applicationTypeCode(), "MBA")) {
            return domicile + " postgraduate";
        }
        return domicile + " undergraduate";
    }

    private static boolean hasDisability(String value) {
        if (value == null || value.isBlank()) return false;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return !Set.of("NO", "NONE", "NOT_DISABLED", "NO_DISABILITY").contains(normalized);
    }

    private static boolean contains(String value, String fragment) {
        return value != null && value.toUpperCase(Locale.ROOT).contains(fragment);
    }

    private static List<Column> columns(String... keyLabels) {
        List<Column> columns = new ArrayList<>();
        for (int index = 0; index < keyLabels.length; index += 2) {
            columns.add(new Column(keyLabels[index], keyLabels[index + 1]));
        }
        return List.copyOf(columns);
    }

    private static String text(String value, UUID fallback) {
        return value == null || value.isBlank() ? display(fallback) : value;
    }

    private static String display(Object value) {
        return value == null || value.toString().isBlank() ? "Not recorded" : value.toString();
    }

    private static String instant(Instant value) {
        return value == null ? "Not recorded" : value.toString();
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "Not recorded" : value.stripTrailingZeros().toPlainString();
    }

    private static String number(Number value) {
        return value == null ? "0" : String.valueOf(value);
    }

    private static long parse(String value) {
        return Long.parseLong(value);
    }

    private static String ordinal(int value) {
        if (value % 100 >= 11 && value % 100 <= 13) return value + "th";
        return value + switch (value % 10) { case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th"; };
    }

    private static final class ExecutiveAggregate {
        private final Set<UUID> applicationIds = new HashSet<>();
        private final Set<UUID> applicantIds = new HashSet<>();
        private final Set<UUID> choiceIds = new HashSet<>();
        private final Set<UUID> offered = new HashSet<>();
        private final Set<UUID> accepted = new HashSet<>();
        private final Set<UUID> converted = new HashSet<>();

        private void add(AdmissionsOperationalReportRow row) {
            applicationIds.add(row.applicationId());
            applicantIds.add(row.applicantId());
            if (row.choiceId() != null) choiceIds.add(row.choiceId());
            if (row.offerId() != null && !"DRAFT".equals(row.offerStatus()) && !"WITHDRAWN".equals(row.offerStatus())) offered.add(row.applicationId());
            if (ACCEPTED_STATUSES.contains(row.applicationStatus())) accepted.add(row.applicationId());
            if ("CONVERTED".equals(row.applicationStatus())) converted.add(row.applicationId());
        }
    }

    private static final class DemandAggregate {
        private final String programmeCode;
        private final String programmeName;
        private final String academicUnit;
        private final Set<UUID> applicationIds = new HashSet<>();
        private final Set<UUID> applicantIds = new HashSet<>();
        private final Set<UUID> choiceIds = new HashSet<>();
        private final Map<Integer, Set<UUID>> ranks = new HashMap<>();
        private final Map<String, Set<UUID>> genders = new HashMap<>();
        private final Set<UUID> offered = new HashSet<>();
        private final Set<UUID> accepted = new HashSet<>();
        private final Set<UUID> pending = new HashSet<>();

        private DemandAggregate(AdmissionsOperationalReportRow row) {
            programmeCode = text(row.programmeCode(), row.programmeId());
            programmeName = display(row.programmeName());
            academicUnit = display(row.owningAcademicUnitName());
        }

        private void add(AdmissionsOperationalReportRow row) {
            applicationIds.add(row.applicationId());
            applicantIds.add(row.applicantId());
            choiceIds.add(row.choiceId());
            if (row.choiceRank() != null) ranks.computeIfAbsent(row.choiceRank(), ignored -> new HashSet<>()).add(row.choiceId());
            genders.computeIfAbsent(display(row.genderCode()), ignored -> new HashSet<>()).add(row.applicantId());
            if (row.offerId() != null && !Set.of("DRAFT", "WITHDRAWN").contains(row.offerStatus())) offered.add(row.applicationId());
            if (ACCEPTED_STATUSES.contains(row.applicationStatus())) accepted.add(row.applicationId());
            if (row.decision() == null && Set.of("ELIGIBLE", "CONDITIONALLY_ELIGIBLE", "REQUIRES_REVIEW", "UNDER_ACADEMIC_REVIEW").contains(row.choiceStatus())) pending.add(row.applicationId());
        }

        private long choiceCount() { return choiceIds.size(); }

        private List<String> row() {
            long female = gender("FEMALE");
            long male = gender("MALE");
            long other = Math.max(0, applicantIds.size() - female - male);
            return List.of(programmeCode + " · " + programmeName, academicUnit, number(applicationIds.size()),
                    number(applicantIds.size()), number(choiceIds.size()), number(rank(1)), number(rank(2)), number(rank(3)),
                    number(female), number(male), number(other), number(offered.size()), number(accepted.size()), number(pending.size()));
        }

        private long rank(int rank) { return ranks.getOrDefault(rank, Set.of()).size(); }
        private long gender(String gender) {
            return genders.entrySet().stream().filter(entry -> gender.equalsIgnoreCase(entry.getKey()))
                    .mapToLong(entry -> entry.getValue().size()).sum();
        }
    }
}
