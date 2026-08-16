package zw.ac.uz.emhare.admissions.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

/** Verifies the database boundary for ADR-0014 rolling admissions. @author Tinashe K */
@Testcontainers
class RollingAdmissionsMigrationTest {

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
    void createsDirectOfferWithoutAdmissionCycleSelectionRoundOrOfferBatch() throws SQLException {
        RollingFixture fixture = createRollingFixture();
        UUID offerId = insertDirectOffer(fixture);

        assertEquals(offerId, queryUuid("""
                SELECT id FROM offers
                WHERE id = ?
                  AND offer_batch_id IS NULL
                  AND programme_choice_decision_id = ?
                  AND intake_id = ?
                """, offerId, fixture.decisionId(), fixture.intakeId()));
    }

    @Test
    void rejectsNewRoundBatchAndReleaseWorkflowRows() throws SQLException {
        UUID admissionCycleId = insertHistoricalAdmissionCycle();

        SQLException exception = assertThrows(SQLException.class, () -> execute("""
                INSERT INTO selection_rounds (
                    id, admission_cycle_id, code, name, status, created_at, updated_at, version)
                VALUES (?, ?, ?, 'Obsolete round', 'DRAFT', now(), now(), 0)
                """, UUID.randomUUID(), admissionCycleId, "ROUND-" + UUID.randomUUID()));

        assertEquals("P0001", exception.getSQLState());
    }

    @Test
    void keepsDirectOfferSourceSnapshotImmutable() throws SQLException {
        RollingFixture fixture = createRollingFixture();
        UUID offerId = insertDirectOffer(fixture);

        SQLException exception = assertThrows(
                SQLException.class,
                () -> execute("UPDATE offers SET programme_id = ? WHERE id = ?", UUID.randomUUID(), offerId));

        assertEquals("P0001", exception.getSQLState());
    }

    @Test
    void allowsPublishAndSendToApproveAndDispatchDraftOfferAtomically() throws SQLException {
        RollingFixture fixture = createRollingFixture();
        UUID offerId = insertDirectOffer(fixture);

        execute("""
                UPDATE offers
                SET offer_type = 'FIRM', acceptance_deadline = now() + interval '7 days',
                    commencement_date = current_date + 30, status = 'SENT',
                    approved_by_user_id = ?, approved_at = now(), sent_at = now()
                WHERE id = ?
                """, UUID.randomUUID(), offerId);

        assertEquals("SENT", queryString("SELECT status FROM offers WHERE id = ?", offerId));
    }

    private RollingFixture createRollingFixture() throws SQLException {
        UUID applicationTypeId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID choiceId = UUID.randomUUID();
        UUID programmeId = UUID.randomUUID();
        UUID programmeVersionId = UUID.randomUUID();
        UUID intakeId = UUID.randomUUID();
        UUID decisionId = UUID.randomUUID();

        execute("""
                INSERT INTO application_types (
                    id, code, name, requires_employment_history, requires_referees,
                    is_active, created_at, updated_at, version)
                VALUES (?, ?, 'Rolling test type', false, false, true, now(), now(), 0)
                """, applicationTypeId, "ROLLING-" + applicationTypeId);
        execute("""
                INSERT INTO applicants (
                    id, user_id, applicant_number, applicant_category_code, first_name, last_name,
                    primary_email, created_at, updated_at, version)
                VALUES (?, ?, ?, 'LOCAL', 'Rolling', 'Applicant', ?, now(), now(), 0)
                """, applicantId, UUID.randomUUID(), "APP-" + applicantId, applicantId + "@example.test");
        execute("""
                INSERT INTO applications (
                    id, intake_id, intake_code, intake_name, intake_starts_on, intake_ends_on,
                    maximum_programme_choices, applicant_id, application_type_id, application_number,
                    payment_required, status, sections_complete,
                    professional_achievements_declared_none, created_at, updated_at, version)
                VALUES (?, ?, 'AUG27', 'August 2027', current_date - 30, current_date + 30,
                    3, ?, ?, ?, false, 'DRAFT', false, false, now(), now(), 0)
                """, applicationId, intakeId, applicantId, applicationTypeId, "EMH-" + applicationId);
        execute("""
                INSERT INTO application_programme_choices (
                    id, application_id, programme_id, programme_version_id, programme_code, programme_name,
                    award_name, owning_academic_unit_id, owning_academic_unit_name, programme_version_code,
                    catalogue_snapshot_status, choice_rank, choice_status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'PRG-1', 'Rolling Test Programme', 'Bachelor Degree', ?,
                    'Test Academic Unit', '2027.1', 'VALIDATED', 1, 'PENDING', now(), now(), 0)
                """, choiceId, applicationId, programmeId, programmeVersionId, UUID.randomUUID());
        execute("UPDATE application_programme_choices SET choice_status = 'ELIGIBLE' WHERE id = ?", choiceId);
        execute("UPDATE application_programme_choices SET choice_status = 'UNDER_ACADEMIC_REVIEW' WHERE id = ?", choiceId);
        execute("UPDATE application_programme_choices SET choice_status = 'ADMITTED' WHERE id = ?", choiceId);
        execute("UPDATE applications SET status = 'ADMITTED' WHERE id = ?", applicationId);
        execute("""
                INSERT INTO programme_choice_decisions (
                    id, application_id, programme_choice_id, decision, reason,
                    decided_by_user_id, decided_at, created_at, updated_at, version)
                VALUES (?, ?, ?, 'ADMIT', 'Rolling admission approved', ?, now(), now(), now(), 0)
                """, decisionId, applicationId, choiceId, UUID.randomUUID());
        return new RollingFixture(
                applicationId, choiceId, programmeId, programmeVersionId, intakeId, decisionId);
    }

    private UUID insertDirectOffer(RollingFixture fixture) throws SQLException {
        UUID offerId = UUID.randomUUID();
        execute("""
                INSERT INTO offers (
                    id, application_id, programme_choice_id, programme_choice_decision_id,
                    programme_id, programme_version_id, programme_code, programme_name, intake_id,
                    offer_number, status, amendment_pending, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, 'PRG-1', 'Rolling Test Programme', ?, ?,
                    'DRAFT', false, now(), now(), 0)
                """, offerId, fixture.applicationId(), fixture.choiceId(), fixture.decisionId(),
                fixture.programmeId(), fixture.programmeVersionId(), fixture.intakeId(), "OFR-" + offerId);
        return offerId;
    }

    private UUID insertHistoricalAdmissionCycle() throws SQLException {
        UUID admissionCycleId = UUID.randomUUID();
        execute("""
                INSERT INTO admission_cycles (
                    id, academic_year_id, intake_id, code, name, opens_at, closes_at,
                    status, maximum_programme_choices, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'Historical cycle', now() - interval '30 days', now() - interval '1 day',
                    'COMPLETED', 3, now(), now(), 0)
                """, admissionCycleId, UUID.randomUUID(), UUID.randomUUID(), "HISTORY-" + admissionCycleId);
        return admissionCycleId;
    }

    private UUID queryUuid(String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getObject(1, UUID.class);
            }
        }
    }

    private String queryString(String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
        }
    }

    private void execute(String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            statement.executeUpdate();
        }
    }

    private void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }

    private record RollingFixture(
            UUID applicationId,
            UUID choiceId,
            UUID programmeId,
            UUID programmeVersionId,
            UUID intakeId,
            UUID decisionId) {
    }
}
