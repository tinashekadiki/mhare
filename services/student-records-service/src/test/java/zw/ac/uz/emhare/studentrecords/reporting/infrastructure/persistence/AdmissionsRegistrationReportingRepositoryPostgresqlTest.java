package zw.ac.uz.emhare.studentrecords.reporting.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

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
import zw.ac.uz.emhare.studentrecords.reporting.AdmissionsRegistrationOutcome;

/** PostgreSQL contract for authoritative Admissions registration outcomes. @author Tinashe K */
@Testcontainers
class AdmissionsRegistrationReportingRepositoryPostgresqlTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("emhare_registration_reporting")
            .withUsername("emhare_service")
            .withPassword("emhare_test_password");

    private JdbcTemplate jdbcTemplate;
    private AdmissionsRegistrationReportingRepository repository;

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
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM registration_sessions");
        jdbcTemplate.update("DELETE FROM student_programme_enrolments");
        jdbcTemplate.update("DELETE FROM students");
        repository = new AdmissionsRegistrationReportingRepository(jdbcTemplate);
    }

    @Test
    void reportsTheLatestRealRegistrationSessionForAnAdmissionsApplication() {
        UUID applicationId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID enrolmentId = UUID.randomUUID();
        UUID programmeId = UUID.randomUUID();
        UUID programmeVersionId = UUID.randomUUID();
        UUID intakeId = UUID.randomUUID();
        insertStudent(studentId, applicationId, offerId);
        insertEnrolment(enrolmentId, studentId, offerId, programmeId, programmeVersionId, intakeId);
        insertRegistration(UUID.randomUUID(), studentId, enrolmentId, programmeVersionId,
                "DRAFT", "2026-08-13 08:00:00+00", null);
        insertRegistration(UUID.randomUUID(), studentId, enrolmentId, programmeVersionId,
                "CONFIRMED", "2026-08-14 08:00:00+00", "2026-08-14 10:00:00+00");

        List<AdmissionsRegistrationOutcome> outcomes = repository.findOutcomes();

        assertThat(outcomes).singleElement().satisfies(outcome -> {
            assertThat(outcome.sourceApplicationId()).isEqualTo(applicationId);
            assertThat(outcome.sourceOfferId()).isEqualTo(offerId);
            assertThat(outcome.programmeId()).isEqualTo(programmeId);
            assertThat(outcome.intakeId()).isEqualTo(intakeId);
            assertThat(outcome.registrationStatus()).isEqualTo("CONFIRMED");
            assertThat(outcome.registrationConfirmedAt()).isNotNull();
        });
    }

    private void insertStudent(UUID studentId, UUID applicationId, UUID offerId) {
        jdbcTemplate.update("""
                INSERT INTO students (
                    id, student_number, user_id, source_applicant_id, source_applicant_number,
                    source_application_id, source_offer_id, applicant_category_code, first_name, last_name,
                    primary_email, status, activated_at, created_at, updated_at, version
                ) VALUES (?, 'R260001A', ?, ?, 'A000001', ?, ?, 'LOCAL', 'Tariro', 'Moyo',
                          'tariro@example.test', 'ACTIVE', now(), now(), now(), 0)
                """, studentId, UUID.randomUUID(), UUID.randomUUID(), applicationId, offerId);
    }

    private void insertEnrolment(
            UUID enrolmentId,
            UUID studentId,
            UUID offerId,
            UUID programmeId,
            UUID programmeVersionId,
            UUID intakeId) {
        jdbcTemplate.update("""
                INSERT INTO student_programme_enrolments (
                    id, student_id, source_offer_id, source_programme_choice_id, programme_id,
                    programme_version_id, programme_code, programme_name, intake_id, commencement_date,
                    status, status_reason, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, 'HCS', 'Computer Science', ?, DATE '2026-08-01',
                          'ACTIVE', 'Offer accepted', now(), now(), 0)
                """, enrolmentId, studentId, offerId, UUID.randomUUID(), programmeId, programmeVersionId, intakeId);
    }

    private void insertRegistration(
            UUID registrationId,
            UUID studentId,
            UUID enrolmentId,
            UUID programmeVersionId,
            String status,
            String initiatedAt,
            String confirmedAt) {
        UUID academicActor = "CONFIRMED".equals(status) ? UUID.randomUUID() : null;
        UUID confirmationActor = "CONFIRMED".equals(status) ? UUID.randomUUID() : null;
        jdbcTemplate.update("""
                INSERT INTO registration_sessions (
                    id, student_id, programme_enrolment_id, academic_period_id, academic_period_code,
                    academic_period_name, academic_period_starts_on, academic_period_ends_on,
                    programme_version_id, programme_period_number, registration_type, status, status_reason,
                    initiated_at, submitted_at, academic_approved_by_user_id, academic_approved_at,
                    confirmed_by_user_id, confirmed_at, created_at, updated_at, version,
                    registration_number, owning_academic_unit_id, owning_academic_unit_code,
                    owning_academic_unit_name, programme_level_id, programme_level_code, programme_level_name
                ) VALUES (?, ?, ?, ?, ?, 'August to December 2026', DATE '2026-08-01', DATE '2026-12-20',
                          ?, 1, 'NORMAL', ?, 'Reporting contract fixture', ?::timestamptz,
                          CASE WHEN ? = 'CONFIRMED' THEN ?::timestamptz ELSE NULL END,
                          ?, CASE WHEN ? = 'CONFIRMED' THEN ?::timestamptz ELSE NULL END,
                          ?, ?::timestamptz, now(), now(), 0, 'R260001A', ?, 'SCI', 'Faculty of Science',
                          ?, 'UG', 'Undergraduate')
                """,
                registrationId, studentId, enrolmentId, UUID.randomUUID(),
                "PERIOD-" + registrationId.toString().substring(0, 6), programmeVersionId,
                status, initiatedAt, status, initiatedAt, academicActor, status, initiatedAt,
                confirmationActor, confirmedAt, UUID.randomUUID(), UUID.randomUUID());
    }
}
