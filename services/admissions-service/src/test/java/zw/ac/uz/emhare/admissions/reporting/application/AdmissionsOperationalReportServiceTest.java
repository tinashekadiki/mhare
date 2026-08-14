package zw.ac.uz.emhare.admissions.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.admissions.integration.StudentRecordsReportingClient;
import zw.ac.uz.emhare.admissions.integration.StudentRecordsReportingClient.RegistrationOutcomeResult;
import zw.ac.uz.emhare.admissions.integration.http.StudentRecordsReportingHttpService.RegistrationOutcome;
import zw.ac.uz.emhare.admissions.reporting.infrastructure.persistence.AdmissionsOperationalReportRepository;
import zw.ac.uz.emhare.admissions.reporting.infrastructure.persistence.AdmissionsIntakeMovementRow;
import zw.ac.uz.emhare.admissions.reporting.infrastructure.persistence.AdmissionsOperationalReportRow;

/** @author Tinashe K */
class AdmissionsOperationalReportServiceTest {

    private final AdmissionsOperationalReportRepository repository = mock(AdmissionsOperationalReportRepository.class);
    private final StudentRecordsReportingClient studentRecordsClient = mock(StudentRecordsReportingClient.class);
    private AdmissionsOperationalReportService service;

    @BeforeEach
    void setUp() {
        service = new AdmissionsOperationalReportService(repository, new AdmissionsReportCatalogueService(),
                studentRecordsClient, Clock.fixed(Instant.parse("2026-08-14T09:00:00Z"), ZoneOffset.UTC));
        when(studentRecordsClient.outcomes()).thenReturn(new RegistrationOutcomeResult(true, List.of(), null));
    }

    @Test
    void demandCountsApplicationsOnceButEveryRankedChoice() {
        UUID applicationId = UUID.randomUUID();
        AdmissionsOperationalReportRow first = row(applicationId, UUID.randomUUID(), 1, "FEMALE", "OFFERED", "ADMIT", "SENT");
        AdmissionsOperationalReportRow second = row(applicationId, UUID.randomUUID(), 2, "FEMALE", "OFFERED", null, null);
        when(repository.findRows(AdmissionsPipelineReportQuery.empty())).thenReturn(List.of(first, second));

        AdmissionsOperationalReport report = service.generate(
                AdmissionsReportCode.APPLICATION_DEMAND, AdmissionsPipelineReportQuery.empty());

        assertThat(report.rows()).hasSize(1);
        assertThat(report.rows().getFirst()).containsSequence("1", "1", "2", "1", "1", "0", "1");
        assertThat(report.chart().getFirst().value()).isEqualTo(2);
    }

    @Test
    void executiveUsesConfirmedStudentRecordsRegistrationInsteadOfConvertedAsAProxy() {
        UUID applicationId = UUID.randomUUID();
        AdmissionsOperationalReportRow accepted = row(
                applicationId, UUID.randomUUID(), 1, "MALE", "ACCEPTED", "ADMIT", "ACCEPTED");
        when(repository.findRows(AdmissionsPipelineReportQuery.empty())).thenReturn(List.of(accepted));
        when(studentRecordsClient.outcomes()).thenReturn(new RegistrationOutcomeResult(true, List.of(
                new RegistrationOutcome(applicationId, UUID.randomUUID(), UUID.randomUUID(), "R260001A",
                        accepted.programmeId(), "HCS", "Computer Science", accepted.intakeId(),
                        "CONFIRMED", Instant.parse("2026-08-14T08:00:00Z"))), null));

        AdmissionsOperationalReport report = service.generate(
                AdmissionsReportCode.EXECUTIVE_STATISTICS, AdmissionsPipelineReportQuery.empty());

        assertThat(report.rows()).anySatisfy(values -> {
            assertThat(values.get(0)).isEqualTo("Programme");
            assertThat(values.get(5)).isEqualTo("1");
            assertThat(values.get(7)).isEqualTo("1");
        });
    }

    @Test
    void registersAndSpecialCategoriesExposeOverlappingNamedBusinessViews() {
        UUID applicationId = UUID.randomUUID();
        AdmissionsOperationalReportRow acceptedDisabled = row(
                applicationId, UUID.randomUUID(), 1, "FEMALE", "ACCEPTED", "ADMIT", "ACCEPTED");
        when(repository.findRows(AdmissionsPipelineReportQuery.empty())).thenReturn(List.of(acceptedDisabled));

        AdmissionsOperationalReport registers = service.generate(
                AdmissionsReportCode.APPLICANT_REGISTERS, AdmissionsPipelineReportQuery.empty());
        AdmissionsOperationalReport special = service.generate(
                AdmissionsReportCode.SPECIAL_CATEGORY_REGISTERS, AdmissionsPipelineReportQuery.empty());

        assertThat(registers.rows()).extracting(values -> values.getFirst())
                .contains("Applied", "Selected", "Accepted");
        assertThat(special.rows()).extracting(values -> values.getFirst())
                .contains("Applicants with disabilities", "Staff dependants", "Disabled applicants accepted");
    }

    @Test
    void selectionScheduleAttributesOnlyTheAdmittedProgrammeChoice() {
        UUID applicationId = UUID.randomUUID();
        AdmissionsOperationalReportRow admitted = row(
                applicationId, UUID.randomUUID(), 1, "FEMALE", "ACCEPTED", "ADMIT", "ACCEPTED");
        AdmissionsOperationalReportRow alternative = row(
                applicationId, UUID.randomUUID(), 2, "FEMALE", "ACCEPTED", null, null);
        when(repository.findRows(AdmissionsPipelineReportQuery.empty())).thenReturn(List.of(admitted, alternative));

        AdmissionsOperationalReport report = service.generate(
                AdmissionsReportCode.SELECTION_SCHEDULES, AdmissionsPipelineReportQuery.empty());

        assertThat(report.rows()).hasSize(3).allSatisfy(values -> {
            assertThat(values.get(7)).isEqualTo("1");
            assertThat(values.get(9)).isEqualTo("ADMIT");
        });
    }

    @Test
    void generatesEverySelectionScheduleVariantFromDecisionBackedChoices() {
        AdmissionsOperationalReportRow postgraduate = customRow(
                UUID.randomUUID(), 1, "MALE", "OFFERED", "POSTGRAD", "LOCAL", null, null,
                "ADMIT", "ADMITTED", "MBA", Instant.parse("2026-08-01T09:00:00Z"), null);
        AdmissionsOperationalReportRow education = customRow(
                UUID.randomUUID(), 2, null, "OFFERED", "EDUCATION", "LOCAL", null, null,
                null, "OFFERED", null, null, null);
        when(repository.findRows(AdmissionsPipelineReportQuery.empty())).thenReturn(List.of(postgraduate, education));

        AdmissionsOperationalReport report = service.generate(
                AdmissionsReportCode.SELECTION_SCHEDULES, AdmissionsPipelineReportQuery.empty());

        assertThat(report.rows()).extracting(List::getFirst)
                .contains("Postgraduate SAR", "Undergraduate SAR", "Undergraduate rolling admissions",
                        "Trimmed Programme list", "Selected-applicant education report");
        assertThat(report.rows()).allSatisfy(values -> assertThat(values).hasSize(13));
    }

    @Test
    void intakeMovementsRetainAcceptedAndUndecidedAuditEvidence() {
        UUID actor = UUID.randomUUID();
        when(repository.findIntakeMovements(AdmissionsPipelineReportQuery.empty())).thenReturn(List.of(
                new AdmissionsIntakeMovementRow(UUID.randomUUID(), "APP-1", "A000001", "Accepted Applicant",
                        "ACCEPTED", UUID.randomUUID(), "JAN-2026", "January", UUID.randomUUID(),
                        "AUG-2026", "August", actor, Instant.parse("2026-08-14T08:00:00Z"), "Deferral approved"),
                new AdmissionsIntakeMovementRow(UUID.randomUUID(), "APP-2", "A000002", "Undecided Applicant",
                        "UNDER_REVIEW", UUID.randomUUID(), null, null, UUID.randomUUID(),
                        null, null, null, null, null)));

        AdmissionsOperationalReport report = service.generate(
                AdmissionsReportCode.INTAKE_MOVEMENTS, AdmissionsPipelineReportQuery.empty());

        assertThat(report.metrics().getFirst().value()).isEqualTo("2");
        assertThat(report.rows()).extracting(List::getFirst)
                .containsExactly("Accepted applicants moved", "Undecided applicants moved");
        assertThat(report.rows().get(1)).contains("Not recorded");
    }

    @Test
    void statusRegistersCoverDraftUnconfirmedReviewAndFinalOutcomes() {
        List<AdmissionsOperationalReportRow> evidence = List.of(
                customRow(UUID.randomUUID(), null, null, "DRAFT", "UNDERGRAD", "LOCAL", "NONE", null,
                        null, "PENDING", "HCS", null, null),
                customRow(UUID.randomUUID(), 1, "FEMALE", "SUBMITTED", "UNDERGRAD", "LOCAL", "NO", null,
                        null, "ELIGIBLE", "HCS", Instant.parse("2026-08-01T09:00:00Z"), null),
                customRow(UUID.randomUUID(), 1, "MALE", "REJECTED", "UNDERGRAD", "LOCAL", null, null,
                        "REJECT", "NOT_ELIGIBLE", "HACC", null, null),
                customRow(UUID.randomUUID(), 1, "MALE", "WITHDRAWN", "UNDERGRAD", "LOCAL", "NO_DISABILITY", null,
                        null, "PENDING", "HENG", null, null));
        when(repository.findRows(AdmissionsPipelineReportQuery.empty())).thenReturn(evidence);

        AdmissionsOperationalReport report = service.generate(
                AdmissionsReportCode.APPLICANT_REGISTERS, AdmissionsPipelineReportQuery.empty());

        assertThat(report.rows()).extracting(List::getFirst)
                .contains("Applying", "Applied", "Unconfirmed", "Not yet selected", "Rejected", "Not selected");
    }

    @Test
    void analysisGroupsNullAndRankedDimensionsWithoutInflatingDistinctCounts() {
        UUID applicationId = UUID.randomUUID();
        AdmissionsOperationalReportRow noChoice = customRow(
                applicationId, null, null, "UNDER_REVIEW", "UNDERGRAD", "LOCAL", null, null,
                null, "PENDING", null, null, null);
        AdmissionsOperationalReportRow ranked = customRow(
                applicationId, 11, "OTHER", "ACCEPTED", "UNDERGRAD", "LOCAL", null, null,
                "ADMIT", "ADMITTED", "HCS", Instant.parse("2026-08-01T09:00:00Z"), null);
        AdmissionsOperationalReportRow first = customRow(
                UUID.randomUUID(), 1, "", "UNDER_REVIEW", null, "LOCAL", null, null,
                null, "PENDING", "", null, null);
        AdmissionsOperationalReportRow second = customRow(
                UUID.randomUUID(), 2, "FEMALE", "UNDER_REVIEW", "UNDERGRAD", "LOCAL", null, null,
                null, "PENDING", "HENG", null, null);
        AdmissionsOperationalReportRow third = customRow(
                UUID.randomUUID(), 3, "MALE", "UNDER_REVIEW", "UNDERGRAD", "LOCAL", null, null,
                null, "PENDING", "HACC", null, null);
        AdmissionsOperationalReportRow fourth = customRow(
                UUID.randomUUID(), 4, "OTHER", "UNDER_REVIEW", "UNDERGRAD", "LOCAL", null, null,
                null, "PENDING", "HLAW", null, null);
        when(repository.findRows(AdmissionsPipelineReportQuery.empty()))
                .thenReturn(List.of(noChoice, ranked, first, second, third, fourth));

        AdmissionsOperationalReport report = service.generate(
                AdmissionsReportCode.ADMISSIONS_ANALYSIS, AdmissionsPipelineReportQuery.empty());

        assertThat(report.rows()).anySatisfy(values -> assertThat(values).contains("Choice rank", "No choice"));
        assertThat(report.rows()).anySatisfy(values -> assertThat(values).contains("Choice rank", "11th"));
        assertThat(report.rows()).anySatisfy(values -> assertThat(values).contains("Choice rank", "1st"));
        assertThat(report.rows()).anySatisfy(values -> assertThat(values).contains("Choice rank", "2nd"));
        assertThat(report.rows()).anySatisfy(values -> assertThat(values).contains("Choice rank", "3rd"));
        assertThat(report.rows()).anySatisfy(values -> assertThat(values).contains("Choice rank", "4th"));
        assertThat(report.chart()).isNotEmpty();
    }

    @Test
    void specialCategoriesExcludeExplicitNoDisabilityAndSeparateNotAcceptedApplicants() {
        AdmissionsOperationalReportRow notAcceptedDisabled = customRow(
                UUID.randomUUID(), 1, "FEMALE", "UNDER_REVIEW", "UNDERGRAD", "LOCAL", "HEARING", null,
                null, "ELIGIBLE", "HCS", null, null);
        AdmissionsOperationalReportRow explicitNo = customRow(
                UUID.randomUUID(), 1, "MALE", "UNDER_REVIEW", "UNDERGRAD", "LOCAL", " not_disabled ", "PRIVATE",
                null, "ELIGIBLE", "HACC", null, null);
        AdmissionsOperationalReportRow blank = customRow(
                UUID.randomUUID(), 1, "MALE", "UNDER_REVIEW", "UNDERGRAD", "LOCAL", " ", "STAFF",
                null, "ELIGIBLE", "HENG", null, null);
        when(repository.findRows(AdmissionsPipelineReportQuery.empty()))
                .thenReturn(List.of(notAcceptedDisabled, explicitNo, blank));

        AdmissionsOperationalReport report = service.generate(
                AdmissionsReportCode.SPECIAL_CATEGORY_REGISTERS, AdmissionsPipelineReportQuery.empty());

        assertThat(report.rows()).extracting(List::getFirst)
                .containsExactlyInAnyOrder("Applicants with disabilities", "Disabled applicants not accepted",
                        "Staff dependants");
    }

    @Test
    void offerRegisterKeepsDomicileStudyLevelPublicationAndDeliveryDimensions() {
        AdmissionsOperationalReportRow internationalTransfer = customRow(
                UUID.randomUUID(), 1, "FEMALE", "OFFERED", "TRANSFER", "INTERNATIONAL", null, null,
                "ADMIT", "ADMITTED", "HCS", null, null);
        AdmissionsOperationalReportRow localPostgraduate = customRow(
                UUID.randomUUID(), 1, "MALE", "ACCEPTED", "MBA", "LOCAL", null, null,
                "ADMIT", "ADMITTED", "MBA", null, Instant.parse("2026-08-14T09:00:00Z"));
        AdmissionsOperationalReportRow internationalUndergraduate = customRow(
                UUID.randomUUID(), 1, "OTHER", "OFFERED", "UNDERGRAD", "INTERNATIONAL", null, null,
                "ADMIT", "ADMITTED", "HENG", null, null);
        when(repository.findRows(AdmissionsPipelineReportQuery.empty()))
                .thenReturn(List.of(internationalTransfer, localPostgraduate, internationalUndergraduate));

        AdmissionsOperationalReport report = service.generate(
                AdmissionsReportCode.OFFER_LETTERS, AdmissionsPipelineReportQuery.empty());

        assertThat(report.rows()).extracting(List::getFirst)
                .containsExactly("International transfer", "Local postgraduate", "International undergraduate");
        assertThat(report.rows()).extracting(values -> values.get(8)).contains("Published", "Not published");
    }

    @Test
    void executiveWithholdsRegistrationTotalsAndExplainsStudentRecordsFailure() {
        AdmissionsOperationalReportRow row = customRow(
                UUID.randomUUID(), 3, "MALE", "CONVERTED", "UNDERGRAD", "LOCAL", null, null,
                "ADMIT", "CONVERTED", "HCS", null, null);
        when(repository.findRows(AdmissionsPipelineReportQuery.empty())).thenReturn(List.of(row));
        when(studentRecordsClient.outcomes()).thenReturn(new RegistrationOutcomeResult(
                false, List.of(), "Student Records unavailable"));

        AdmissionsOperationalReport report = service.generate(
                AdmissionsReportCode.EXECUTIVE_STATISTICS, AdmissionsPipelineReportQuery.empty());

        assertThat(report.notes()).contains("Student Records unavailable");
        assertThat(report.rows()).allSatisfy(values -> assertThat(values.get(7)).isEqualTo("0"));
    }

    @Test
    void demandHandlesUnrankedMaleAndOtherGenderChoicesAndSkipsApplicationsWithoutAChoice() {
        UUID applicationId = UUID.randomUUID();
        AdmissionsOperationalReportRow male = customRow(
                applicationId, 3, "MALE", "UNDER_REVIEW", "UNDERGRAD", "LOCAL", null, null,
                null, "REQUIRES_REVIEW", "HCS", null, null);
        AdmissionsOperationalReportRow other = customRow(
                UUID.randomUUID(), null, null, "UNDER_REVIEW", "UNDERGRAD", "LOCAL", null, null,
                "WAITLIST", "PENDING", "HENG", null, null);
        AdmissionsOperationalReportRow noChoice = customRow(
                UUID.randomUUID(), null, "FEMALE", "DRAFT", "UNDERGRAD", "LOCAL", null, null,
                null, "PENDING", null, null, null);
        when(repository.findRows(AdmissionsPipelineReportQuery.empty())).thenReturn(List.of(male, other, noChoice));

        AdmissionsOperationalReport report = service.generate(
                AdmissionsReportCode.APPLICATION_DEMAND, AdmissionsPipelineReportQuery.empty());

        assertThat(report.rows()).hasSize(2);
        assertThat(report.metrics().getFirst().value()).isEqualTo("3");
    }

    private static AdmissionsOperationalReportRow row(
            UUID applicationId,
            UUID choiceId,
            int rank,
            String gender,
            String applicationStatus,
            String decision,
            String offerStatus) {
        UUID programmeId = UUID.nameUUIDFromBytes("programme".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID offerId = offerStatus == null ? null : UUID.randomUUID();
        return new AdmissionsOperationalReportRow(
                applicationId, UUID.nameUUIDFromBytes(("applicant-" + applicationId).getBytes()),
                "APP-001", "A000001", "Tariro Moyo", Instant.parse("2026-08-01T09:00:00Z"),
                applicationStatus, new BigDecimal("14"), UUID.nameUUIDFromBytes("intake".getBytes()),
                "AUG-2026", "August 2026", UUID.nameUUIDFromBytes("type".getBytes()),
                "UNDERGRAD", "Undergraduate", "LOCAL", gender, "VISUAL_IMPAIRMENT", "STAFF_DEPENDANT",
                choiceId, rank, decision == null ? "ELIGIBLE" : "ADMITTED", programmeId, "HCS",
                "Computer Science", "Faculty of Science", "A_LEVEL 2025 Mathematics A", "UZ High School",
                decision, decision == null ? null : Instant.parse("2026-08-10T09:00:00Z"), offerId,
                offerId == null ? null : "OFF-001", offerStatus, offerId == null ? null : "FIRM",
                offerId == null ? null : Instant.parse("2026-08-11T09:00:00Z"),
                offerId == null ? null : "SENT", "ACCEPTED".equals(offerStatus) ? "ACCEPTED" : null);
    }

    private static AdmissionsOperationalReportRow customRow(
            UUID applicationId,
            Integer rank,
            String gender,
            String applicationStatus,
            String applicationTypeCode,
            String category,
            String disability,
            String sponsor,
            String decision,
            String choiceStatus,
            String programmeCode,
            Instant submittedAt,
            Instant publishedAt) {
        UUID choiceId = programmeCode == null && rank == null ? null : UUID.randomUUID();
        UUID programmeId = programmeCode == null ? UUID.randomUUID() : UUID.nameUUIDFromBytes(programmeCode.getBytes());
        UUID offerId = decision == null ? null : UUID.randomUUID();
        return new AdmissionsOperationalReportRow(
                applicationId, UUID.nameUUIDFromBytes(("applicant-" + applicationId).getBytes()),
                "APP-" + applicationId.toString().substring(0, 6), "A" + applicationId.toString().substring(0, 6),
                "Report Applicant", submittedAt, applicationStatus, null, UUID.randomUUID(), null, null,
                UUID.randomUUID(), applicationTypeCode, applicationTypeCode, category, gender, disability, sponsor,
                choiceId, rank, choiceStatus, programmeId, programmeCode, null, null, null, null,
                decision, decision == null ? null : Instant.parse("2026-08-10T09:00:00Z"), offerId,
                offerId == null ? null : "OFF-" + applicationId.toString().substring(0, 6),
                offerId == null ? null : applicationStatus, offerId == null ? null : "FIRM", publishedAt,
                offerId == null ? null : "SENT", "ACCEPTED".equals(applicationStatus) ? "ACCEPTED" : null);
    }
}
