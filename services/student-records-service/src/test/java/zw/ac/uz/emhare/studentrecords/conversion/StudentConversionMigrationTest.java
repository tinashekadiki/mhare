package zw.ac.uz.emhare.studentrecords.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** @author Tinashe K */
@Testcontainers
class StudentConversionMigrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("emhare_student_records")
            .withUsername("emhare_service")
            .withPassword("emhare_test_password");

    private Connection connection;

    @BeforeAll
    static void migrateDatabase() {
        Flyway.configure()
                .dataSource(
                        POSTGRESQL.getJdbcUrl(),
                        POSTGRESQL.getUsername(),
                        POSTGRESQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @BeforeEach
    void openTransaction() throws SQLException {
        connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        connection.setAutoCommit(false);
    }

    @AfterEach
    void rollBackAndCloseConnection() throws SQLException {
        if (connection != null) {
            connection.rollback();
            connection.close();
        }
    }

    @Test
    void rejectsConversionWhenStudentAndEnrolmentDoNotShareTheSourceOffer() throws SQLException {
        ConversionFixture fixture = createConversionFixture();

        SQLException exception = assertThrows(
                SQLException.class,
                () -> insertConversionRequest(fixture, UUID.randomUUID()));

        assertEquals("P0001", exception.getSQLState());
    }

    @Test
    void keepsAcceptedOfferProgrammeSnapshotImmutable() throws SQLException {
        ConversionFixture fixture = createConversionFixture();

        SQLException exception = assertThrows(
                SQLException.class,
                () -> execute(
                        "UPDATE student_programme_enrolments SET programme_id = ? WHERE id = ?",
                        UUID.randomUUID(),
                        fixture.enrolmentId()));

        assertEquals("P0001", exception.getSQLState());
    }

    @Test
    void requiresBothProvisioningResultsBeforeConversionCanComplete() throws SQLException {
        ConversionFixture fixture = createConversionFixture();
        insertConversionRequest(fixture, fixture.offerId());

        SQLException exception = assertThrows(
                SQLException.class,
                () -> execute(
                        "UPDATE student_conversion_requests "
                                + "SET status = 'COMPLETED', completed_at = now() WHERE source_offer_id = ?",
                        fixture.offerId()));

        assertEquals("23514", exception.getSQLState());
    }

    @Test
    void requiresActorTimestampAndReasonForEveryRecordedRetry() throws SQLException {
        ConversionFixture fixture = createConversionFixture();
        insertConversionRequest(fixture, fixture.offerId());

        SQLException exception = assertThrows(
                SQLException.class,
                () -> execute(
                        "UPDATE student_conversion_requests SET retry_count = 1 "
                                + "WHERE source_offer_id = ?",
                        fixture.offerId()));

        assertEquals("23514", exception.getSQLState());
    }

    @Test
    void preservesAcceptedEntryOptionPreferencesAsAuditedSnapshots() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('student_entry_option_preferences', 'student_entry_option_preferences_aud')
                """)) {
            try (var results = statement.executeQuery()) {
                results.next();
                assertEquals(2, results.getInt(1));
            }
        }
    }

    @Test
    void rejectsRegistrationWhenStudentAndProgrammeEnrolmentDoNotMatch() throws SQLException {
        ConversionFixture first = createConversionFixture();
        ConversionFixture second = createConversionFixture();

        SQLException exception = assertThrows(
                SQLException.class,
                () -> insertRegistration(first.studentId(), second, UUID.randomUUID()));

        assertEquals("P0001", exception.getSQLState());
    }

    @Test
    void keepsRegistrationSourceIdentityImmutable() throws SQLException {
        ConversionFixture fixture = createConversionFixture();
        UUID registrationId = UUID.randomUUID();
        insertRegistration(fixture.studentId(), fixture, registrationId);

        SQLException exception = assertThrows(
                SQLException.class,
                () -> execute(
                        "UPDATE registration_sessions SET academic_period_id = ? WHERE id = ?",
                        UUID.randomUUID(), registrationId));

        assertEquals("P0001", exception.getSQLState());
    }

    @Test
    void requiresApprovalEvidenceBeforeRegistrationCanBeConfirmed() throws SQLException {
        ConversionFixture fixture = createConversionFixture();
        UUID registrationId = UUID.randomUUID();
        insertRegistration(fixture.studentId(), fixture, registrationId);

        SQLException exception = assertThrows(
                SQLException.class,
                () -> execute(
                        "UPDATE registration_sessions SET status = 'CONFIRMED' WHERE id = ?",
                        registrationId));

        assertEquals("23514", exception.getSQLState());
    }

    private ConversionFixture createConversionFixture() throws SQLException {
        UUID studentId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID enrolmentId = UUID.randomUUID();
        UUID programmeVersionId = UUID.randomUUID();
        execute(
                """
                INSERT INTO students (
                    id, student_number, user_id, source_applicant_id, source_applicant_number,
                    source_application_id, source_offer_id, applicant_category_code, first_name,
                    last_name, primary_email, status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'LOCAL', 'Tariro', 'Moyo', ?, 'PROVISIONING',
                    now(), now(), 0)
                """,
                studentId,
                "STU-2027-" + studentId.toString().substring(0, 8),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "APL-" + studentId,
                UUID.randomUUID(),
                offerId,
                studentId + "@example.test");
        execute(
                """
                INSERT INTO student_programme_enrolments (
                    id, student_id, source_offer_id, source_programme_choice_id, programme_id,
                    programme_version_id, programme_code, programme_name, intake_id,
                    commencement_date, status, status_reason, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, 'BACC', 'Bachelor of Accountancy', ?, DATE '2027-08-16',
                    'PROVISIONING', 'Accepted offer', now(), now(), 0)
                """,
                enrolmentId,
                studentId,
                offerId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                programmeVersionId,
                UUID.randomUUID());
        return new ConversionFixture(studentId, offerId, enrolmentId, programmeVersionId);
    }

    private void insertRegistration(
            UUID studentId, ConversionFixture enrolmentFixture, UUID registrationId) throws SQLException {
        execute(
                """
                INSERT INTO registration_sessions (
                    id, registration_number, student_id, programme_enrolment_id, academic_period_id,
                    academic_period_code, academic_period_name, academic_period_starts_on,
                    academic_period_ends_on, programme_version_id, programme_period_number,
                    owning_academic_unit_id, owning_academic_unit_code, owning_academic_unit_name,
                    programme_level_id, programme_level_code, programme_level_name,
                    registration_type, status, status_reason, initiated_at,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, '2027-S1', 'Semester 1', DATE '2027-08-16', DATE '2027-12-15',
                    ?, 1, ?, 'BUS', 'Business School', ?, 'UG', 'Undergraduate',
                    'NORMAL', 'DRAFT', 'Registration initiated', now(), now(), now(), 0)
                """,
                registrationId,
                "REG-" + registrationId.toString().substring(0, 8).toUpperCase(),
                studentId,
                enrolmentFixture.enrolmentId(),
                UUID.randomUUID(),
                enrolmentFixture.programmeVersionId(),
                UUID.randomUUID(),
                UUID.randomUUID());
    }

    private void insertConversionRequest(ConversionFixture fixture, UUID sourceOfferId)
            throws SQLException {
        execute(
                """
                INSERT INTO student_conversion_requests (
                    id, source_event_id, source_application_id, source_offer_id, student_id,
                    programme_enrolment_id, status, finance_provisioning_status,
                    portal_provisioning_status, requested_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, 'PROVISIONING', 'PENDING', 'PENDING', now(), now(), now(), 0)
                """,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                sourceOfferId,
                fixture.studentId(),
                fixture.enrolmentId());
    }

    private void execute(String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            statement.executeUpdate();
        }
    }

    private record ConversionFixture(
            UUID studentId, UUID offerId, UUID enrolmentId, UUID programmeVersionId) {
    }
}
