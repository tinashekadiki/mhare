package zw.ac.uz.emhare.admissions.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
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

@Testcontainers
class ApplicationFeeMigrationTest {

    private static final UUID APPLICATION_TYPE_ID = UUID.fromString("7f98bc45-60e1-4c5d-b957-ac8b06e20933");

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("emhare_admissions")
            .withUsername("emhare_service")
            .withPassword("emhare_test_password");

    private Connection connection;

    @BeforeAll
    static void migrateDatabase() {
        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @BeforeEach
    void openTransactionAndCreateApplicationType() throws SQLException {
        connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        connection.setAutoCommit(false);

        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO application_types (
                    id, code, name, requires_employment_history, requires_referees, is_active,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, false, false, true, now(), now(), 0)
                """)) {
            statement.setObject(1, APPLICATION_TYPE_ID);
            statement.setString(2, "FEE-" + UUID.randomUUID());
            statement.setString(3, "Application fee constraint verification");
            statement.executeUpdate();
        }
    }

    @AfterEach
    void rollBackAndCloseConnection() throws SQLException {
        if (connection != null) {
            connection.rollback();
            connection.close();
        }
    }

    @Test
    void rejectsOverlappingActiveFeePeriodsForTheSameTypeAndCategory() throws SQLException {
        insertFee("LOCAL", "USD", new BigDecimal("25.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), true);

        SQLException exception = assertThrows(SQLException.class, () -> insertFee(
                "LOCAL", "USD", new BigDecimal("30.00"),
                LocalDate.of(2026, 6, 1), null, true));

        assertEquals("23P01", exception.getSQLState());
    }

    @Test
    void permitsOverlappingInactiveFeeHistory() throws SQLException {
        insertFee("LOCAL", "USD", new BigDecimal("25.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), true);
        insertFee("LOCAL", "USD", new BigDecimal("30.00"),
                LocalDate.of(2026, 6, 1), null, false);
    }

    @Test
    void rejectsNegativeFeeAmounts() {
        SQLException exception = assertThrows(SQLException.class, () -> insertFee(
                "SADC", "USD", new BigDecimal("-0.01"),
                LocalDate.of(2026, 1, 1), null, true));

        assertEquals("23514", exception.getSQLState());
    }

    @Test
    void rejectsBackwardsEffectivePeriods() {
        SQLException exception = assertThrows(SQLException.class, () -> insertFee(
                "INTERNATIONAL", "USD", new BigDecimal("25.00"),
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 31), true));

        assertEquals("23514", exception.getSQLState());
    }

    @Test
    void rejectsMalformedCurrencyCodes() {
        SQLException malformedCurrencyException = assertThrows(SQLException.class, () -> insertFee(
                "CLE", "usd", new BigDecimal("25.00"),
                LocalDate.of(2026, 1, 1), null, true));
        assertEquals("23514", malformedCurrencyException.getSQLState());
    }

    @Test
    void rejectsUnknownApplicantCategoryCodes() {
        SQLException unknownCategoryException = assertThrows(SQLException.class, () -> insertFee(
                "UNCONTROLLED", "USD", new BigDecimal("25.00"),
                LocalDate.of(2026, 1, 1), null, true));
        assertEquals("23514", unknownCategoryException.getSQLState());
    }

    @Test
    void rejectsProgrammeChoiceRankAboveCycleMaximum() throws SQLException {
        ProgrammeChoiceFixture fixture = createDraftApplication(2);

        SQLException exception = assertThrows(SQLException.class, () -> insertValidatedProgrammeChoice(fixture.applicationId(), 3, true));

        assertEquals("P0001", exception.getSQLState());
    }

    @Test
    void rejectsProgrammeChoiceCaptureAfterApplicationLeavesDraft() throws SQLException {
        ProgrammeChoiceFixture fixture = createDraftApplication(2);
        try (PreparedStatement statement = connection.prepareStatement("UPDATE applications SET status = 'SUBMITTED' WHERE id = ?")) {
            statement.setObject(1, fixture.applicationId());
            statement.executeUpdate();
        }

        SQLException exception = assertThrows(SQLException.class, () -> insertValidatedProgrammeChoice(fixture.applicationId(), 1, true));

        assertEquals("P0001", exception.getSQLState());
    }

    @Test
    void rejectsValidatedProgrammeChoiceWithoutCatalogueSnapshot() throws SQLException {
        ProgrammeChoiceFixture fixture = createDraftApplication(2);

        SQLException exception = assertThrows(SQLException.class, () -> insertValidatedProgrammeChoice(fixture.applicationId(), 1, false));

        assertEquals("23514", exception.getSQLState());
    }

    private void insertFee(
            String applicantCategoryCode,
            String currencyCode,
            BigDecimal amount,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO application_fees (
                    id, application_type_id, applicant_category_code, currency_code, amount,
                    effective_from, effective_to, is_active, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), now(), 0)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, APPLICATION_TYPE_ID);
            statement.setString(3, applicantCategoryCode);
            statement.setString(4, currencyCode);
            statement.setBigDecimal(5, amount);
            statement.setDate(6, Date.valueOf(effectiveFrom));
            statement.setDate(7, effectiveTo == null ? null : Date.valueOf(effectiveTo));
            statement.setBoolean(8, active);
            statement.executeUpdate();
        }
    }

    private ProgrammeChoiceFixture createDraftApplication(int maximumProgrammeChoices) throws SQLException {
        UUID cycleId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        try (PreparedStatement cycle = connection.prepareStatement("""
                INSERT INTO admission_cycles (
                    id, academic_year_id, intake_id, code, name, opens_at, closes_at, status,
                    maximum_programme_choices, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, 'Migration test cycle', now() - interval '1 day',
                    now() + interval '1 day', 'OPEN', ?, now(), now(), 0)
                """);
             PreparedStatement applicant = connection.prepareStatement("""
                INSERT INTO applicants (
                    id, user_id, applicant_number, applicant_category_code, first_name, last_name,
                    primary_email, created_at, updated_at, version
                ) VALUES (?, ?, ?, 'LOCAL', 'Test', 'Applicant', ?, now(), now(), 0)
                """);
             PreparedStatement application = connection.prepareStatement("""
                INSERT INTO applications (
                    id, admission_cycle_id, applicant_id, application_type_id, application_number,
                    payment_required, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, false, 'DRAFT', now(), now(), 0)
                """)) {
            cycle.setObject(1, cycleId);
            cycle.setObject(2, UUID.randomUUID());
            cycle.setObject(3, UUID.randomUUID());
            cycle.setString(4, "CYCLE-" + cycleId);
            cycle.setInt(5, maximumProgrammeChoices);
            cycle.executeUpdate();

            applicant.setObject(1, applicantId);
            applicant.setObject(2, UUID.randomUUID());
            applicant.setString(3, "APP-" + applicantId);
            applicant.setString(4, applicantId + "@example.test");
            applicant.executeUpdate();

            application.setObject(1, applicationId);
            application.setObject(2, cycleId);
            application.setObject(3, applicantId);
            application.setObject(4, APPLICATION_TYPE_ID);
            application.setString(5, "EMH-" + applicationId);
            application.executeUpdate();
        }
        return new ProgrammeChoiceFixture(applicationId);
    }

    private void insertValidatedProgrammeChoice(UUID applicationId, int rank, boolean completeSnapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO application_programme_choices (
                    id, application_id, programme_id, programme_version_id, programme_code, programme_name,
                    award_name, owning_academic_unit_id, owning_academic_unit_name, programme_version_code,
                    catalogue_snapshot_status, choice_rank, choice_status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'VALIDATED', ?, 'PENDING', now(), now(), 0)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, applicationId);
            statement.setObject(3, UUID.randomUUID());
            statement.setObject(4, completeSnapshot ? UUID.randomUUID() : null);
            statement.setString(5, completeSnapshot ? "BSCIT" : null);
            statement.setString(6, completeSnapshot ? "Bachelor of Science in IT" : null);
            statement.setString(7, completeSnapshot ? "Bachelor of Science Honours Degree" : null);
            statement.setObject(8, completeSnapshot ? UUID.randomUUID() : null);
            statement.setString(9, completeSnapshot ? "Department of Computing" : null);
            statement.setString(10, completeSnapshot ? "2027.1" : null);
            statement.setInt(11, rank);
            statement.executeUpdate();
        }
    }

    private record ProgrammeChoiceFixture(UUID applicationId) {
    }
}
