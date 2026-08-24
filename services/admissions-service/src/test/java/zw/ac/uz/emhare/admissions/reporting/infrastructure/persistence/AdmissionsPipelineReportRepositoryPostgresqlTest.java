package zw.ac.uz.emhare.admissions.reporting.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import zw.ac.uz.emhare.admissions.reporting.application.AdmissionsPipelineReportQuery;

/** PostgreSQL contract coverage for ADM-RPT-001 projections. @author Tinashe K */
@Testcontainers
class AdmissionsPipelineReportRepositoryPostgresqlTest {

  @Container
  private static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
          .withDatabaseName("emhare_admissions_reports")
          .withUsername("emhare_service")
          .withPassword("emhare_test_password");

  private JdbcTemplate jdbcTemplate;
  private AdmissionsPipelineReportRepository repository;
  private AdmissionsOperationalReportRepository operationalRepository;

  @BeforeAll
  static void migrateDatabase() {
    Flyway.configure()
        .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();
  }

  @BeforeEach
  void createRepositoryAndClearFixtures() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.update("DELETE FROM applications_aud");
    jdbcTemplate.update("DELETE FROM revinfo");
    jdbcTemplate.update("DELETE FROM application_programme_choices");
    jdbcTemplate.update("DELETE FROM applications");
    repository = new AdmissionsPipelineReportRepository(jdbcTemplate);
    operationalRepository = new AdmissionsOperationalReportRepository(jdbcTemplate);
  }

  @Test
  void projections_shouldReadCurrentApplicationsAndApplyProgrammeAndDemographicFilters() {
    UUID intakeId = UUID.randomUUID();
    UUID applicationTypeId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM application_types WHERE code = 'UNDERGRAD'", UUID.class);
    UUID localApplicantId = insertApplicant("LOCAL", "FEMALE", "Local");
    UUID internationalApplicantId = insertApplicant("INTERNATIONAL", "MALE", "International");
    UUID localApplicationId =
        insertApplication(localApplicantId, applicationTypeId, intakeId, "APP-LOCAL");
    UUID internationalApplicationId =
        insertApplication(
            internationalApplicantId, applicationTypeId, intakeId, "APP-INTERNATIONAL");
    UUID accountingProgrammeId = UUID.randomUUID();
    UUID computerScienceProgrammeId = UUID.randomUUID();
    insertChoice(localApplicationId, accountingProgrammeId, 1, "HACC", "Bachelor of Accountancy");
    insertChoice(
        localApplicationId, computerScienceProgrammeId, 2, "HCS", "Bachelor of Computer Science");
    insertChoice(
        internationalApplicationId,
        computerScienceProgrammeId,
        1,
        "HCS",
        "Bachelor of Computer Science");
    jdbcTemplate.update("UPDATE applications SET status = 'UNDER_REVIEW'");

    List<AdmissionsPipelineReportRow> allRows =
        repository.findReportRows(AdmissionsPipelineReportQuery.empty());
    List<AdmissionsPipelineReportRow> localComputerScience =
        repository.findReportRows(
            AdmissionsPipelineReportQuery.of(
                intakeId, computerScienceProgrammeId, applicationTypeId, "local", "female"));

    assertEquals(3, allRows.size());
    assertEquals(1, localComputerScience.size());
    assertEquals(localApplicationId, localComputerScience.getFirst().applicationId());
    assertEquals(2, localComputerScience.getFirst().choiceRank());
    assertEquals(1, repository.findFilterOptions().intakes().size());
    assertEquals(2, repository.findFilterOptions().programmes().size());
    assertEquals(
        List.of("INTERNATIONAL", "LOCAL"),
        repository.findFilterOptions().categories().stream().map(option -> option.code()).toList());
  }

  @Test
  void
      detailedExport_shouldReturnOneApplicationRowAndKeepAllRankedChoicesWhenFilteringByProgramme() {
    UUID intakeId = UUID.randomUUID();
    UUID applicationTypeId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM application_types WHERE code = 'UNDERGRAD'", UUID.class);
    UUID applicantId = insertApplicant("LOCAL", "FEMALE", "Jane");
    UUID applicationId = insertApplication(applicantId, applicationTypeId, intakeId, "APP-DETAIL");
    UUID draftApplicantId = insertApplicant("LOCAL", "FEMALE", "Draft");
    UUID draftApplicationId =
        insertApplication(draftApplicantId, applicationTypeId, intakeId, "APP-DRAFT");
    UUID accountingProgrammeId = UUID.randomUUID();
    UUID computerScienceProgrammeId = UUID.randomUUID();
    insertChoice(applicationId, accountingProgrammeId, 1, "HACC", "Bachelor of Accountancy");
    insertChoice(
        applicationId, computerScienceProgrammeId, 2, "HCS", "Bachelor of Computer Science");
    jdbcTemplate.update(
        """
                UPDATE applications
                   SET status = 'UNDER_REVIEW', submitted_at = TIMESTAMPTZ '2026-08-13 10:15:00Z',
                       payment_confirmed_at = TIMESTAMPTZ '2026-08-13 09:00:00Z', calculated_total_points = 14
                 WHERE id = ?
                """,
        applicationId);

    List<AdmissionsDetailedExportRow> rows =
        repository.findDetailedExportRows(
            AdmissionsPipelineReportQuery.of(
                intakeId, computerScienceProgrammeId, applicationTypeId, "local", "female"));

    assertEquals(1, rows.size());
    AdmissionsDetailedExportRow row = rows.getFirst();
    assertEquals(applicationId, row.applicationId());
    assertEquals("UNDER_REVIEW", row.applicationStatus());
    assertEquals("PAID", row.paymentStatus());
    assertEquals("Jane Applicant", row.applicantName());
    assertEquals(
        "1. HACC - Bachelor of Accountancy | 2. HCS - Bachelor of Computer Science",
        row.programmeChoices());

    AdmissionsDetailedExportRow draftRow =
        repository.findDetailedExportRows(AdmissionsPipelineReportQuery.empty()).stream()
            .filter(item -> draftApplicationId.equals(item.applicationId()))
            .findFirst()
            .orElseThrow();
    assertEquals(null, draftRow.submittedAt());
    assertEquals("", draftRow.programmeChoices());
  }

  @Test
  void operationalProjection_shouldExposeCanonicalApplicantChoiceAndWorkflowEvidence() {
    UUID intakeId = UUID.randomUUID();
    UUID applicationTypeId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM application_types WHERE code = 'UNDERGRAD'", UUID.class);
    UUID applicantId = insertApplicant("LOCAL", "FEMALE", "Report");
    jdbcTemplate.update(
        "UPDATE applicants SET disability_status_code = 'VISUAL_IMPAIRMENT', sponsor_type_code = 'STAFF_DEPENDANT' WHERE id = ?",
        applicantId);
    UUID applicationId =
        insertApplication(applicantId, applicationTypeId, intakeId, "APP-OPERATIONAL");
    UUID programmeId = UUID.randomUUID();
    insertChoice(applicationId, programmeId, 1, "HCS", "Bachelor of Computer Science");

    List<AdmissionsOperationalReportRow> rows =
        operationalRepository.findRows(
            AdmissionsPipelineReportQuery.of(
                intakeId, programmeId, applicationTypeId, "local", "female"));

    assertEquals(1, rows.size());
    AdmissionsOperationalReportRow row = rows.getFirst();
    assertEquals(applicationId, row.applicationId());
    assertEquals("VISUAL_IMPAIRMENT", row.disabilityStatusCode());
    assertEquals("STAFF_DEPENDANT", row.sponsorTypeCode());
    assertEquals("HCS", row.programmeCode());
    assertEquals(1, row.choiceRank());
  }

  @Test
  void intakeMovementProjection_shouldReconstructBeforeAndAfterIntakesAndApplyEveryFilter() {
    UUID previousIntakeId = UUID.randomUUID();
    UUID newIntakeId = UUID.randomUUID();
    UUID applicationTypeId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM application_types WHERE code = 'UNDERGRAD'", UUID.class);
    UUID applicantId = insertApplicant("LOCAL", "FEMALE", "Moved");
    UUID applicationId =
        insertApplication(applicantId, applicationTypeId, previousIntakeId, "APP-MOVED");
    UUID programmeId = UUID.randomUUID();
    insertChoice(applicationId, programmeId, 1, "HCS", "Bachelor of Computer Science");
    jdbcTemplate.update(
        """
                UPDATE applications
                   SET intake_id = ?, intake_code = 'AUG-2026', intake_name = 'August 2026', status = 'ACCEPTED'
                 WHERE id = ?
                """,
        newIntakeId,
        applicationId);
    UUID actor = UUID.randomUUID();
    Integer firstRevision =
        jdbcTemplate.queryForObject(
            """
                INSERT INTO revinfo (revtstmp, actor_user_id, reason)
                VALUES (1786694400000, ?, 'Initial intake') RETURNING rev
                """,
            Integer.class,
            actor);
    Integer secondRevision =
        jdbcTemplate.queryForObject(
            """
                INSERT INTO revinfo (revtstmp, actor_user_id, reason)
                VALUES (1786780800000, ?, 'Approved intake movement') RETURNING rev
                """,
            Integer.class,
            actor);
    jdbcTemplate.update(
        """
                INSERT INTO applications_aud (id, rev, revtype, intake_id, intake_code, intake_name)
                VALUES (?, ?, 0, ?, 'JAN-2026', 'January 2026'),
                       (?, ?, 1, ?, 'AUG-2026', 'August 2026')
                """,
        applicationId,
        firstRevision,
        previousIntakeId,
        applicationId,
        secondRevision,
        newIntakeId);

    List<AdmissionsIntakeMovementRow> movements =
        operationalRepository.findIntakeMovements(
            AdmissionsPipelineReportQuery.of(
                newIntakeId, programmeId, applicationTypeId, "local", "female"));

    assertEquals(1, movements.size());
    AdmissionsIntakeMovementRow movement = movements.getFirst();
    assertEquals("JAN-2026", movement.previousIntakeCode());
    assertEquals("AUG-2026", movement.newIntakeCode());
    assertEquals("ACCEPTED", movement.applicationStatus());
    assertEquals(actor, movement.changedByUserId());
    assertEquals("Approved intake movement", movement.reason());
  }

  private UUID insertApplicant(String categoryCode, String genderCode, String namePrefix) {
    UUID applicantId = UUID.randomUUID();
    jdbcTemplate.update(
        """
                INSERT INTO applicants (
                    id, user_id, applicant_number, applicant_category_code, first_name, last_name,
                    gender_code, primary_email, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, 'Applicant', ?, ?, now(), now(), 0)
                """,
        applicantId,
        UUID.randomUUID(),
        "A" + applicantId.toString().substring(0, 6),
        categoryCode,
        namePrefix,
        genderCode,
        applicantId + "@example.test");
    return applicantId;
  }

  private UUID insertApplication(
      UUID applicantId, UUID applicationTypeId, UUID intakeId, String numberPrefix) {
    UUID applicationId = UUID.randomUUID();
    jdbcTemplate.update(
        """
                INSERT INTO applications (
                    id, applicant_id, application_type_id, application_number, payment_required,
                    application_fee_policy_status, status, created_at, updated_at, version,
                    intake_id, intake_code, intake_name,
                    intake_starts_on, intake_ends_on, maximum_programme_choices,
                    official_first_name, official_last_name
                ) VALUES (?, ?, ?, ?, true, 'LEGACY_UNSNAPSHOTTED', 'DRAFT', now(), now(), 0,
                          ?, 'AUG-2026', 'August 2026',
                          DATE '2026-08-01', DATE '2026-08-31', 3,
                          'Test', 'Applicant')
                """,
        applicationId,
        applicantId,
        applicationTypeId,
        numberPrefix + '-' + applicationId.toString().substring(0, 6),
        intakeId);
    return applicationId;
  }

  private void insertChoice(
      UUID applicationId, UUID programmeId, int rank, String code, String name) {
    jdbcTemplate.update(
        """
                INSERT INTO application_programme_choices (
                    id, application_id, programme_id, choice_rank, choice_status,
                    created_at, updated_at, version, programme_code, programme_name,
                    owning_academic_unit_name, catalogue_snapshot_status
                ) VALUES (?, ?, ?, ?, 'PENDING', now(), now(), 0, ?, ?, 'Faculty of Business', 'LEGACY_UNRESOLVED')
                """,
        UUID.randomUUID(),
        applicationId,
        programmeId,
        rank,
        code,
        name);
  }
}
