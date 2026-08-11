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

/** @author Tinashe K */
@Testcontainers
class SelectionOfferMigrationTest {

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
    void rejectsSelectingTwoProgrammeChoicesForOneApplication() throws SQLException {
        WorkflowFixture fixture = createWorkflowFixture();
        insertSelectionDecision(fixture.selectionRoundId(), fixture.firstChoiceId());

        SQLException exception = assertThrows(
                SQLException.class,
                () -> insertSelectionDecision(fixture.selectionRoundId(), fixture.secondChoiceId()));

        assertEquals("P0001", exception.getSQLState());
    }

    @Test
    void rejectsOfferUntilBatchAndSelectionRoundAreApproved() throws SQLException {
        WorkflowFixture fixture = createWorkflowFixture();
        insertSelectionDecision(fixture.selectionRoundId(), fixture.firstChoiceId());
        markApplicationAndChoiceAdmitted(fixture.applicationId(), fixture.firstChoiceId());
        UUID batchId = insertOfferBatch(fixture, "DRAFT");

        SQLException exception = assertThrows(
                SQLException.class,
                () -> insertOffer(fixture, batchId));

        assertEquals("P0001", exception.getSQLState());
    }

    @Test
    void rejectsOfferOutsideProgrammeScopedBatch() throws SQLException {
        WorkflowFixture fixture = createWorkflowFixture();
        insertSelectionDecision(fixture.selectionRoundId(), fixture.firstChoiceId());
        markApplicationAndChoiceAdmitted(fixture.applicationId(), fixture.firstChoiceId());
        approveSelectionRound(fixture.selectionRoundId());
        UUID batchId = insertOfferBatch(fixture, "APPROVED", "PROGRAMME", UUID.randomUUID());

        SQLException exception = assertThrows(
                SQLException.class,
                () -> insertOffer(fixture, batchId));

        assertEquals("P0001", exception.getSQLState());
    }

    @Test
    void acceptsResponseOnlyForSentOfferAndMakesItImmutable() throws SQLException {
        WorkflowFixture fixture = createWorkflowFixture();
        insertSelectionDecision(fixture.selectionRoundId(), fixture.firstChoiceId());
        markApplicationAndChoiceAdmitted(fixture.applicationId(), fixture.firstChoiceId());
        approveSelectionRound(fixture.selectionRoundId());
        UUID batchId = insertOfferBatch(fixture, "APPROVED");
        UUID prematureOfferId = insertOffer(fixture, batchId);

        SQLException prematureResponse = assertThrows(SQLException.class, () -> insertOfferResponse(prematureOfferId));
        assertEquals("P0001", prematureResponse.getSQLState());
        connection.rollback();

        fixture = createWorkflowFixture();
        insertSelectionDecision(fixture.selectionRoundId(), fixture.firstChoiceId());
        markApplicationAndChoiceAdmitted(fixture.applicationId(), fixture.firstChoiceId());
        approveSelectionRound(fixture.selectionRoundId());
        batchId = insertOfferBatch(fixture, "APPROVED");
        UUID offerId = insertOffer(fixture, batchId);
        approveAndSendOffer(offerId);
        UUID responseId = insertOfferResponse(offerId);

        UUID immutableResponseId = responseId;
        SQLException mutation = assertThrows(SQLException.class, () -> updateOfferResponse(immutableResponseId));
        assertEquals("P0001", mutation.getSQLState());
    }

    @Test
    void requiresOfferBatchDispatchBeforeClosure() throws SQLException {
        WorkflowFixture fixture = createWorkflowFixture();
        insertSelectionDecision(fixture.selectionRoundId(), fixture.firstChoiceId());
        approveSelectionRound(fixture.selectionRoundId());
        UUID batchId = insertOfferBatch(fixture, "APPROVED");

        SQLException exception = assertThrows(
                SQLException.class,
                () -> execute("UPDATE offer_batches SET status = 'CLOSED', closed_at = now() WHERE id = ?", batchId));

        assertEquals("P0001", exception.getSQLState());
    }

    @Test
    void makesResolvedOfferConditionsFinal() throws SQLException {
        WorkflowFixture fixture = createWorkflowFixture();
        insertSelectionDecision(fixture.selectionRoundId(), fixture.firstChoiceId());
        markApplicationAndChoiceAdmitted(fixture.applicationId(), fixture.firstChoiceId());
        approveSelectionRound(fixture.selectionRoundId());
        UUID batchId = insertOfferBatch(fixture, "APPROVED");
        UUID offerId = insertOffer(fixture, batchId);
        UUID conditionId = UUID.randomUUID();
        execute("""
                INSERT INTO offer_conditions (id, offer_id, condition_code, description, required, status,
                    created_at, updated_at, version)
                VALUES (?, ?, 'CERTIFICATE', 'Submit the original certificate', true, 'PENDING', now(), now(), 0)
                """, conditionId, offerId);
        execute("""
                UPDATE offer_conditions SET status = 'SATISFIED', satisfied_by_user_id = ?, satisfied_at = now()
                WHERE id = ?
                """, UUID.randomUUID(), conditionId);

        SQLException exception = assertThrows(
                SQLException.class,
                () -> execute("UPDATE offer_conditions SET status = 'WAIVED' WHERE id = ?", conditionId));

        assertEquals("P0001", exception.getSQLState());
    }

    @Test
    void requiresExpiryEvidenceForExpiredOffers() throws SQLException {
        WorkflowFixture fixture = createWorkflowFixture();
        insertSelectionDecision(fixture.selectionRoundId(), fixture.firstChoiceId());
        markApplicationAndChoiceAdmitted(fixture.applicationId(), fixture.firstChoiceId());
        approveSelectionRound(fixture.selectionRoundId());
        UUID batchId = insertOfferBatch(fixture, "APPROVED");
        UUID offerId = insertOffer(fixture, batchId);
        approveAndSendOffer(offerId);

        SQLException exception = assertThrows(
                SQLException.class,
                () -> execute("UPDATE offers SET status = 'EXPIRED' WHERE id = ?", offerId));

        assertEquals("23514", exception.getSQLState());
    }

    @Test
    void admissionCycleMaintenanceReasonExistsInCurrentAndAuditSchemas() throws SQLException {
        UUID admissionCycleId = UUID.randomUUID();
        execute("""
                INSERT INTO admission_cycles (
                    id, academic_year_id, intake_id, code, name, opens_at, closes_at,
                    status, maximum_programme_choices, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'Maintenance audit cycle', now(), now() + interval '30 days',
                    'DRAFT', 3, now(), now(), 0)
                """, admissionCycleId, UUID.randomUUID(), UUID.randomUUID(), "AUDIT-" + admissionCycleId);

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT change_reason FROM admission_cycles WHERE id = ?")) {
            statement.setObject(1, admissionCycleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                assertEquals("Initial record creation.", resultSet.getString("change_reason"));
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT change_reason FROM admission_cycles_aud WHERE false")) {
            statement.executeQuery().close();
        }
    }

    @Test
    void permitsGovernedChoiceLifecycleButKeepsSubmittedCatalogueSnapshotImmutable() throws SQLException {
        WorkflowFixture fixture = createWorkflowFixture();
        execute("UPDATE application_programme_choices SET choice_status = 'UNDER_ACADEMIC_REVIEW' WHERE id = ?", fixture.firstChoiceId());

        SQLException exception = assertThrows(
                SQLException.class,
                () -> execute("UPDATE application_programme_choices SET programme_id = ? WHERE id = ?",
                        UUID.randomUUID(), fixture.firstChoiceId()));

        assertEquals("P0001", exception.getSQLState());
    }

    @Test
    void permitsOfferDispatchAfterApplicationAdvancesButKeepsOfferSourceImmutable() throws SQLException {
        WorkflowFixture fixture = createWorkflowFixture();
        insertSelectionDecision(fixture.selectionRoundId(), fixture.firstChoiceId());
        markApplicationAndChoiceAdmitted(fixture.applicationId(), fixture.firstChoiceId());
        approveSelectionRound(fixture.selectionRoundId());
        UUID batchId = insertOfferBatch(fixture, "APPROVED");
        UUID offerId = insertOffer(fixture, batchId);
        execute("UPDATE offers SET status = 'APPROVED', approved_at = now(), approved_by_user_id = ? WHERE id = ?",
                UUID.randomUUID(), offerId);
        execute("UPDATE applications SET status = 'OFFERED' WHERE id = ?", fixture.applicationId());
        execute("UPDATE application_programme_choices SET choice_status = 'OFFERED' WHERE id = ?", fixture.firstChoiceId());
        execute("UPDATE offers SET status = 'SENT', sent_at = now() WHERE id = ?", offerId);

        SQLException exception = assertThrows(
                SQLException.class,
                () -> execute("UPDATE offers SET programme_id = ? WHERE id = ?", UUID.randomUUID(), offerId));

        assertEquals("P0001", exception.getSQLState());
    }

    private WorkflowFixture createWorkflowFixture() throws SQLException {
        UUID applicationTypeId = UUID.randomUUID();
        UUID admissionCycleId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID firstChoiceId = UUID.randomUUID();
        UUID secondChoiceId = UUID.randomUUID();
        UUID firstProgrammeId = UUID.randomUUID();
        UUID firstProgrammeVersionId = UUID.randomUUID();
        UUID owningAcademicUnitId = UUID.randomUUID();
        UUID selectionRoundId = UUID.randomUUID();

        execute("""
                INSERT INTO application_types (id, code, name, requires_employment_history, requires_referees,
                    is_active, created_at, updated_at, version)
                VALUES (?, ?, 'Selection test type', false, false, true, now(), now(), 0)
                """, applicationTypeId, "SEL-TYPE-" + applicationTypeId);
        execute("""
                INSERT INTO admission_cycles (id, academic_year_id, intake_id, code, name, opens_at, closes_at,
                    status, maximum_programme_choices, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'Selection test cycle', now() - interval '30 days', now() - interval '1 day',
                    'SELECTION', 3, now(), now(), 0)
                """, admissionCycleId, UUID.randomUUID(), UUID.randomUUID(), "SEL-CYCLE-" + admissionCycleId);
        execute("""
                INSERT INTO applicants (id, user_id, applicant_number, applicant_category_code, first_name, last_name,
                    primary_email, created_at, updated_at, version)
                VALUES (?, ?, ?, 'LOCAL', 'Selection', 'Applicant', ?, now(), now(), 0)
                """, applicantId, UUID.randomUUID(), "APP-" + applicantId, applicantId + "@example.test");
        execute("""
                INSERT INTO applications (id, admission_cycle_id, applicant_id, application_type_id,
                    application_number, payment_required, status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, false, 'DRAFT', now(), now(), 0)
                """, applicationId, admissionCycleId, applicantId, applicationTypeId, "EMH-" + applicationId);
        insertChoice(firstChoiceId, applicationId, firstProgrammeId, firstProgrammeVersionId, owningAcademicUnitId, 1);
        insertChoice(secondChoiceId, applicationId, UUID.randomUUID(), UUID.randomUUID(), owningAcademicUnitId, 2);
        execute("UPDATE applications SET status = 'ELIGIBLE' WHERE id = ?", applicationId);
        execute("UPDATE application_programme_choices SET choice_status = 'ELIGIBLE' WHERE application_id = ?", applicationId);
        execute("""
                INSERT INTO selection_rounds (id, admission_cycle_id, code, name, status, opened_at,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, 'Selection test round', 'OPEN', now(), now(), now(), 0)
                """, selectionRoundId, admissionCycleId, "ROUND-" + selectionRoundId);
        return new WorkflowFixture(
                admissionCycleId, applicationId, firstChoiceId, secondChoiceId, firstProgrammeId,
                firstProgrammeVersionId, owningAcademicUnitId, selectionRoundId);
    }

    private void insertChoice(
            UUID choiceId,
            UUID applicationId,
            UUID programmeId,
            UUID programmeVersionId,
            UUID owningAcademicUnitId,
            int rank) throws SQLException {
        execute("""
                INSERT INTO application_programme_choices (
                    id, application_id, programme_id, programme_version_id, programme_code, programme_name,
                    award_name, owning_academic_unit_id, owning_academic_unit_name, programme_version_code,
                    catalogue_snapshot_status, choice_rank, choice_status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 'Selection Test Programme', 'Bachelor Degree', ?, 'Test Academic Unit',
                    '2027.1', 'VALIDATED', ?, 'PENDING', now(), now(), 0)
                """, choiceId, applicationId, programmeId, programmeVersionId, "PRG-" + rank, owningAcademicUnitId, rank);
    }

    private void insertSelectionDecision(UUID selectionRoundId, UUID choiceId) throws SQLException {
        execute("""
                INSERT INTO selection_decisions (id, selection_round_id, programme_choice_id, decision,
                    rank_position, quota_type_code, reason, decided_by_user_id, decided_at,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, 'SELECT', 1, 'MERIT', 'Selected by migration test', ?, now(), now(), now(), 0)
                """, UUID.randomUUID(), selectionRoundId, choiceId, UUID.randomUUID());
    }

    private void markApplicationAndChoiceAdmitted(UUID applicationId, UUID choiceId) throws SQLException {
        // ADR-0014 retired the SELECTED status in favour of ADMITTED (2026-08-11 admissions
        // backend rolling-workflow plan, Task 3/4); the governance trigger installed by V34 only
        // permits reaching ADMITTED via UNDER_ACADEMIC_REVIEW, so this is a two-step transition.
        execute("UPDATE application_programme_choices SET choice_status = 'UNDER_ACADEMIC_REVIEW' WHERE id = ?", choiceId);
        execute("UPDATE application_programme_choices SET choice_status = 'ADMITTED' WHERE id = ?", choiceId);
        execute("UPDATE applications SET status = 'ADMITTED' WHERE id = ?", applicationId);
    }

    private void approveSelectionRound(UUID selectionRoundId) throws SQLException {
        execute("""
                UPDATE selection_rounds SET status = 'APPROVED', approved_at = now(), approved_by_user_id = ?
                WHERE id = ?
                """, UUID.randomUUID(), selectionRoundId);
    }

    private UUID insertOfferBatch(WorkflowFixture fixture, String status) throws SQLException {
        return insertOfferBatch(fixture, status, "INSTITUTION", null);
    }

    private UUID insertOfferBatch(WorkflowFixture fixture, String status, String scopeType, UUID scopeId) throws SQLException {
        UUID batchId = UUID.randomUUID();
        execute("""
                INSERT INTO offer_batches (id, admission_cycle_id, selection_round_id, code, name, scope_type,
                    scope_id, status, approved_by_user_id, approved_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'Selection test batch', ?, ?, ?, ?, ?, now(), now(), 0)
                """,
                batchId, fixture.admissionCycleId(), fixture.selectionRoundId(), "BATCH-" + batchId,
                scopeType, scopeId, status,
                "APPROVED".equals(status) ? UUID.randomUUID() : null,
                "APPROVED".equals(status) ? new java.sql.Timestamp(System.currentTimeMillis()) : null);
        return batchId;
    }

    private UUID insertOffer(WorkflowFixture fixture, UUID batchId) throws SQLException {
        UUID offerId = UUID.randomUUID();
        UUID intakeId = queryUuid("SELECT intake_id FROM admission_cycles WHERE id = ?", fixture.admissionCycleId());
        execute("""
                INSERT INTO offers (id, application_id, programme_choice_id, offer_batch_id, programme_id,
                    programme_version_id, programme_code, programme_name, intake_id, offer_number, offer_type,
                    status, acceptance_deadline, commencement_date, generated_document_id,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, 'PRG-1', 'Selection Test Programme', ?, ?, 'FIRM', 'DRAFT',
                    now() + interval '30 days', current_date + 60, ?, now(), now(), 0)
                """, offerId, fixture.applicationId(), fixture.firstChoiceId(), batchId, fixture.firstProgrammeId(),
                fixture.firstProgrammeVersionId(), intakeId, "OFR-" + offerId, UUID.randomUUID());
        return offerId;
    }

    private void approveAndSendOffer(UUID offerId) throws SQLException {
        execute("UPDATE offers SET status = 'APPROVED', approved_at = now(), approved_by_user_id = ? WHERE id = ?",
                UUID.randomUUID(), offerId);
        execute("UPDATE offers SET status = 'SENT', sent_at = now() WHERE id = ?", offerId);
    }

    private UUID insertOfferResponse(UUID offerId) throws SQLException {
        UUID responseId = UUID.randomUUID();
        execute("""
                INSERT INTO offer_responses (id, offer_id, response, responded_at, responded_by_user_id,
                    created_at, updated_at, version)
                VALUES (?, ?, 'ACCEPTED', now(), ?, now(), now(), 0)
                """, responseId, offerId, UUID.randomUUID());
        return responseId;
    }

    private void updateOfferResponse(UUID responseId) throws SQLException {
        execute("UPDATE offer_responses SET notes = 'Attempted mutation' WHERE id = ?", responseId);
    }

    private UUID queryUuid(String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getObject(1, UUID.class);
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

    private record WorkflowFixture(
            UUID admissionCycleId,
            UUID applicationId,
            UUID firstChoiceId,
            UUID secondChoiceId,
            UUID firstProgrammeId,
            UUID firstProgrammeVersionId,
            UUID owningAcademicUnitId,
            UUID selectionRoundId) {
    }
}
