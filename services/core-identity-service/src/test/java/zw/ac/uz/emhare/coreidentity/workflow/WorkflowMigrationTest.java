package zw.ac.uz.emhare.coreidentity.workflow;

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
class WorkflowMigrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("emhare_core_identity")
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
    void createsAuditedWorkflowTablesAndPermissions() throws SQLException {
        assertEquals(3, countTables("workflow_instances", "workflow_tasks", "workflow_decisions"));
        assertEquals(3, countTables("workflow_instances_aud", "workflow_tasks_aud", "workflow_decisions_aud"));
        assertEquals(2, countPermissions("CORE_WORKFLOW_MANAGE", "CORE_WORKFLOW_TASK"));
    }

    @Test
    void seedsCurrencySpecificUsdAndZwgInstitutionBankAccounts() throws SQLException {
        assertEquals(4, institutionBankAccountCount(null));
        assertEquals(2, institutionBankAccountCount("USD"));
        assertEquals(2, institutionBankAccountCount("ZWG"));
        assertEquals(1, institutionBankAccountNumberCount("01120770100249"));
        assertEquals(1, institutionBankAccountNumberCount("01120770100052"));
        assertEquals(1, institutionBankAccountNumberCount("10099186633010"));
        assertEquals(1, institutionBankAccountNumberCount("10099183902014"));
        assertEquals(4, institutionBankAccountPaymentReferenceCount(
                "After accepting this offer, eMhare will generate your registration number. "
                        + "Quote that registration number as the payment reference."));
    }

    @Test
    void seedsTheRegistrarNameUsedByOfficialDocuments() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT registrar_name FROM institution_profile WHERE code = 'UZ'")) {
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                assertEquals("Registrar", result.getString(1));
            }
        }
    }

    @Test
    void rejectsAmbiguousTaskAssigneesAndInvalidScope() throws SQLException {
        UUID firstUserId = createUser();
        UUID firstWorkflowId = createWorkflow(firstUserId);
        UUID systemAdminRoleId = roleId("SYSTEM_ADMIN");

        SQLException ambiguousAssignee = assertThrows(SQLException.class, () -> execute(
                taskInsertSql(),
                UUID.randomUUID(), firstWorkflowId, "WT-AMBIGUOUS", firstUserId, systemAdminRoleId,
                "INSTITUTION", null));
        assertEquals("23514", ambiguousAssignee.getSQLState());

        connection.rollback();
        UUID secondUserId = createUser();
        UUID secondWorkflowId = createWorkflow(secondUserId);

        SQLException invalidScope = assertThrows(SQLException.class, () -> execute(
                taskInsertSql(),
                UUID.randomUUID(), secondWorkflowId, "WT-SCOPE", secondUserId, null,
                "ACADEMIC_UNIT", null));
        assertEquals("23514", invalidScope.getSQLState());
    }

    private int countTables(String... tableNames) throws SQLException {
        int count = 0;
        for (String tableName : tableNames) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?")) {
                statement.setString(1, tableName);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    count += result.getInt(1);
                }
            }
        }
        return count;
    }

    private int countPermissions(String firstCode, String secondCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM permissions WHERE code IN (?, ?)")) {
            statement.setString(1, firstCode);
            statement.setString(2, secondCode);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private int institutionBankAccountCount(String currencyCode) throws SQLException {
        String sql = """
                SELECT count(*)
                FROM institution_profile profile
                CROSS JOIN LATERAL jsonb_array_elements(profile.bank_details_json -> 'accounts') account
                WHERE profile.code = 'UZ'
                  AND (? IS NULL OR account ->> 'currencyCode' = ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, currencyCode);
            statement.setString(2, currencyCode);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private int institutionBankAccountNumberCount(String accountNumber) throws SQLException {
        String sql = """
                SELECT count(*)
                FROM institution_profile profile
                CROSS JOIN LATERAL jsonb_array_elements(profile.bank_details_json -> 'accounts') account
                WHERE profile.code = 'UZ'
                  AND account ->> 'accountNumber' = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accountNumber);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private int institutionBankAccountPaymentReferenceCount(String instruction) throws SQLException {
        String sql = """
                SELECT count(*)
                FROM institution_profile profile
                CROSS JOIN LATERAL jsonb_array_elements(profile.bank_details_json -> 'accounts') account
                WHERE profile.code = 'UZ'
                  AND account ->> 'paymentReferenceInstructions' = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, instruction);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private UUID createUser() throws SQLException {
        UUID userId = UUID.randomUUID();
        execute("""
                INSERT INTO users (
                    id, username, email, display_name, status, created_at, updated_at, version)
                VALUES (?, ?, ?, 'Workflow Tester', 'ACTIVE', now(), now(), 0)
                """, userId, "workflow-" + userId, userId + "@example.test");
        return userId;
    }

    private UUID createWorkflow(UUID actorUserId) throws SQLException {
        UUID workflowId = UUID.randomUUID();
        execute("""
                INSERT INTO workflow_instances (
                    id, workflow_code, subject_type, subject_id, subject_reference, title,
                    status, initiated_by_user_id, initiated_at, created_at, updated_at, version)
                VALUES (?, 'TEST', 'TEST_SUBJECT', ?, 'TEST-1', 'Test workflow',
                    'ACTIVE', ?, now(), now(), now(), 0)
                """, workflowId, UUID.randomUUID(), actorUserId);
        return workflowId;
    }

    private UUID roleId(String code) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM roles WHERE code = ?")) {
            statement.setString(1, code);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getObject(1, UUID.class);
            }
        }
    }

    private String taskInsertSql() {
        return """
                INSERT INTO workflow_tasks (
                    id, workflow_instance_id, task_reference, title, description,
                    assignee_type, assigned_user_id, assigned_role_id, scope_type,
                    academic_unit_id, status, created_at, updated_at, version)
                VALUES (?, ?, ?, 'Review', 'Review evidence',
                    'USER', ?, ?, ?, ?, 'OPEN', now(), now(), 0)
                """;
    }

    private void execute(String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            statement.executeUpdate();
        }
    }
}
