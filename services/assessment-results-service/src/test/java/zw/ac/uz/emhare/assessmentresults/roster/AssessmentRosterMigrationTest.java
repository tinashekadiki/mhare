package zw.ac.uz.emhare.assessmentresults.roster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
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
class AssessmentRosterMigrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("emhare_assessment_results")
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
    void createsAuditTablesForBothRosterBusinessTables() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('registration_roster_imports_aud', 'assessment_roster_entries_aud')
                """); ResultSet results = statement.executeQuery()) {
            results.next();
            assertEquals(2, results.getInt(1));
        }
    }

    @Test
    void createsAuditTablesForEveryAssessmentWorkflowBusinessTable() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name IN (
                    'assessment_module_offerings_aud', 'assessment_schemes_aud',
                    'assessment_components_aud', 'student_assessment_marks_aud',
                    'mark_amendment_requests_aud', 'assessment_calculation_runs_aud',
                    'assessment_calculation_outcomes_aud')
                """); ResultSet results = statement.executeQuery()) {
            results.next();
            assertEquals(7, results.getInt(1));
        }
    }

    @Test
    void createsAuditTablesForCalculationEvidenceAndResultGovernance() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN (
                  'assessment_calculation_component_evidence_aud','grading_schemes_aud','grading_bands_aud',
                  'result_batches_aud','module_results_aud','result_batch_status_events_aud','published_results_aud')
                """); ResultSet results = statement.executeQuery()) {
            results.next();
            assertEquals(7, results.getInt(1));
        }
    }

    @Test
    void createsAppendOnlyPublishedResultAmendmentSchemaAndAuditTables() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema='public' AND table_name IN (
                  'published_result_amendments', 'published_result_amendments_aud',
                  'published_result_amendment_events', 'published_result_amendment_events_aud')
                """); ResultSet results = statement.executeQuery()) {
            results.next();
            assertEquals(4, results.getInt(1));
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema='public' AND table_name='published_results'
                  AND column_name IN (
                    'publication_version', 'supersedes_published_result_id', 'result_amendment_id')
                """); ResultSet results = statement.executeQuery()) {
            results.next();
            assertEquals(3, results.getInt(1));
        }
    }

    @Test
    void createsGovernedProgrammeProgressionSchemaAndAuditTables() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema='public' AND table_name IN (
                  'progression_rule_sets', 'progression_rule_sets_aud',
                  'progression_rule_outcomes', 'progression_rule_outcomes_aud',
                  'student_overall_decisions', 'student_overall_decisions_aud',
                  'student_overall_decision_results', 'student_overall_decision_results_aud',
                  'student_overall_decision_events', 'student_overall_decision_events_aud')
                """); ResultSet results = statement.executeQuery()) {
            results.next();
            assertEquals(10, results.getInt(1));
        }
    }

    @Test
    void rejectsProgressionRuleApprovalWithoutOneFinalFallback() throws SQLException {
        UUID ruleSetId = UUID.randomUUID();
        execute("""
                INSERT INTO progression_rule_sets (
                    id, rule_code, rule_name, programme_id, programme_version_id,
                    programme_period_number, rule_version, status, created_at, updated_at, version)
                VALUES (?, 'BACC-P1', 'Accounting progression', ?, ?, 1, 1, 'DRAFT', now(), now(), 0)
                """, ruleSetId, UUID.randomUUID(), UUID.randomUUID());
        execute("""
                INSERT INTO progression_rule_outcomes (
                    id, progression_rule_set_id, priority, decision_code, decision_label,
                    minimum_weighted_average, require_all_compulsory_passed, fallback_outcome,
                    created_at, updated_at, version)
                VALUES (?, ?, 1, 'PROCEED', 'Proceed', 50, true, false, now(), now(), 0)
                """, UUID.randomUUID(), ruleSetId);

        SQLException exception = assertThrows(SQLException.class, () -> execute("""
                UPDATE progression_rule_sets SET status='APPROVED', approved_by_user_id=?,
                    approved_at=now(), approval_reason='Academic board approval', updated_at=now()
                WHERE id=?
                """, UUID.randomUUID(), ruleSetId));

        assertEquals("P0001", exception.getSQLState());
    }

    @Test
    void rejectsApprovalWhenGradingBandsContainAGap() throws SQLException {
        UUID gradingSchemeId = UUID.randomUUID();
        execute("""
                INSERT INTO grading_schemes(id,code,name,scheme_version,status,created_at,updated_at,version)
                VALUES (?,'GAP','Invalid gap scheme',1,'DRAFT',now(),now(),0)
                """, gradingSchemeId);
        execute("""
                INSERT INTO grading_bands(id,grading_scheme_id,minimum_mark,maximum_mark,grade,remark,passing,sort_order,created_at,updated_at,version)
                VALUES (?, ?, 0, 49.99, 'F', 'Fail', false, 1, now(), now(), 0),
                       (?, ?, 50.01, 100, 'P', 'Pass', true, 2, now(), now(), 0)
                """, UUID.randomUUID(), gradingSchemeId, UUID.randomUUID(), gradingSchemeId);

        SQLException exception = assertThrows(SQLException.class, () -> execute("""
                UPDATE grading_schemes SET status='APPROVED', approved_by_user_id=?, approved_at=now(),
                  approval_reason='Attempted approval', updated_at=now() WHERE id=?
                """, UUID.randomUUID(), gradingSchemeId));

        assertEquals("P0001", exception.getSQLState());
    }

    @Test
    void preventsChangesToComponentsAfterSchemeApproval() throws SQLException {
        AssessmentFixture fixture = insertAssessmentFixture();
        execute("UPDATE assessment_schemes SET status='APPROVED', approval_reason='Academic approval', approved_by_user_id=?, approved_at=now() WHERE id=?",
                UUID.randomUUID(), fixture.schemeId());

        SQLException exception = assertThrows(SQLException.class, () -> execute(
                "UPDATE assessment_components SET maximum_mark=80 WHERE id=?", fixture.componentId()));

        assertEquals("P0001", exception.getSQLState());
    }

    @Test
    void rejectsScoresAboveTheComponentMaximumAndKeepsSubmittedEvidenceImmutable() throws SQLException {
        AssessmentFixture fixture = insertAssessmentFixture();
        UUID actor = UUID.randomUUID();
        Savepoint beforeExcessiveScore = connection.setSavepoint();
        SQLException excessiveScore = assertThrows(SQLException.class, () -> execute("""
                INSERT INTO student_assessment_marks (
                    id, assessment_component_id, assessment_roster_entry_id, revision_number, score,
                    status, capture_method, captured_by_user_id, captured_at,
                    submitted_by_user_id, submitted_at, created_at, updated_at, version)
                VALUES (?, ?, ?, 1, 101, 'SUBMITTED', 'MANUAL', ?, now(), ?, now(), now(), now(), 0)
                """, UUID.randomUUID(), fixture.componentId(), fixture.rosterEntryId(), actor, actor));
        assertEquals("P0001", excessiveScore.getSQLState());
        connection.rollback(beforeExcessiveScore);

        UUID markId = UUID.randomUUID();
        execute("""
                INSERT INTO student_assessment_marks (
                    id, assessment_component_id, assessment_roster_entry_id, revision_number, score,
                    status, capture_method, captured_by_user_id, captured_at,
                    submitted_by_user_id, submitted_at, created_at, updated_at, version)
                VALUES (?, ?, ?, 1, 68, 'SUBMITTED', 'MANUAL', ?, now(), ?, now(), now(), now(), 0)
                """, markId, fixture.componentId(), fixture.rosterEntryId(), actor, actor);

        SQLException evidenceChange = assertThrows(SQLException.class, () -> execute(
                "UPDATE student_assessment_marks SET score=72 WHERE id=?", markId));
        assertEquals("P0001", evidenceChange.getSQLState());
    }

    @Test
    void keepsImportedRegistrationIdentityImmutable() throws SQLException {
        UUID rosterImportId = insertRosterImport();

        SQLException exception = assertThrows(SQLException.class, () -> execute(
                "UPDATE registration_roster_imports SET student_id = ? WHERE id = ?",
                UUID.randomUUID(), rosterImportId));

        assertEquals("P0001", exception.getSQLState());
    }

    @Test
    void keepsRegisteredModuleSnapshotImmutable() throws SQLException {
        UUID rosterImportId = insertRosterImport();
        UUID rosterEntryId = UUID.randomUUID();
        execute("""
                INSERT INTO assessment_roster_entries (
                    id, roster_import_id, registration_module_id, curriculum_module_id, module_id,
                    module_code, module_name, curriculum_module_type, credit_value,
                    eligibility_status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 'ACC101', 'Financial Accounting I', 'COMPULSORY',
                    12.00, 'ELIGIBLE', now(), now(), 0)
                """, rosterEntryId, rosterImportId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        SQLException exception = assertThrows(SQLException.class, () -> execute(
                "UPDATE assessment_roster_entries SET module_code = 'CHANGED' WHERE id = ?",
                rosterEntryId));

        assertEquals("P0001", exception.getSQLState());
    }

    private UUID insertRosterImport() throws SQLException {
        UUID id = UUID.randomUUID();
        execute("""
                INSERT INTO registration_roster_imports (
                    id, source_event_id, registration_session_id, student_id, student_number,
                    programme_enrolment_id, programme_id, programme_version_id, academic_period_id,
                    academic_period_code, academic_period_name, academic_period_starts_on,
                    academic_period_ends_on, programme_period_number, imported_at,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'STU-2027-0000001', ?, ?, ?, ?, '2027-S1', 'Semester 1',
                    DATE '2027-08-16', DATE '2027-12-15', 1, now(), now(), now(), 0)
                """, id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        return id;
    }

    private AssessmentFixture insertAssessmentFixture() throws SQLException {
        UUID rosterImportId = insertRosterImport();
        UUID moduleId = UUID.randomUUID();
        UUID rosterEntryId = UUID.randomUUID();
        execute("""
                INSERT INTO assessment_roster_entries (
                    id, roster_import_id, registration_module_id, curriculum_module_id, module_id,
                    module_code, module_name, curriculum_module_type, credit_value,
                    eligibility_status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 'ACC101', 'Financial Accounting I', 'COMPULSORY',
                    12.00, 'ELIGIBLE', now(), now(), 0)
                """, rosterEntryId, rosterImportId, UUID.randomUUID(), UUID.randomUUID(), moduleId);
        UUID offeringId = UUID.randomUUID();
        execute("""
                INSERT INTO assessment_module_offerings (
                    id, module_id, module_code, module_name, academic_period_id,
                    academic_period_code, academic_period_name, assigned_instructor_user_id,
                    status, created_at, updated_at, version)
                SELECT ?, ?, 'ACC101', 'Financial Accounting I', academic_period_id,
                    academic_period_code, academic_period_name, ?, 'DRAFT', now(), now(), 0
                FROM registration_roster_imports WHERE id=?
                """, offeringId, moduleId, UUID.randomUUID(), rosterImportId);
        UUID schemeId = UUID.randomUUID();
        execute("""
                INSERT INTO assessment_schemes (
                    id, module_offering_id, scheme_version, name, status,
                    created_at, updated_at, version)
                VALUES (?, ?, 1, 'Standard scheme', 'DRAFT', now(), now(), 0)
                """, schemeId, offeringId);
        UUID componentId = UUID.randomUUID();
        execute("""
                INSERT INTO assessment_components (
                    id, assessment_scheme_id, code, name, component_type, weight_percent,
                    maximum_mark, capture_opens_at, capture_closes_at, sort_order,
                    created_at, updated_at, version)
                VALUES (?, ?, 'CWK', 'Coursework', 'COURSEWORK', 100, 100,
                    now() - interval '1 day', now() + interval '1 day', 1, now(), now(), 0)
                """, componentId, schemeId);
        return new AssessmentFixture(rosterEntryId, schemeId, componentId);
    }

    private record AssessmentFixture(UUID rosterEntryId, UUID schemeId, UUID componentId) {}

    private void execute(String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            statement.executeUpdate();
        }
    }
}
