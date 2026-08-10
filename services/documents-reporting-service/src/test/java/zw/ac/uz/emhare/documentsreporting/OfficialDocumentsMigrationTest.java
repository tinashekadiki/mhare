package zw.ac.uz.emhare.documentsreporting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
class OfficialDocumentsMigrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("emhare_documents_reporting")
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
    void createsOfficialResultProjectionDocumentAndInboxTables() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema='public' AND table_name IN (
                    'published_result_projections', 'progression_decision_projections',
                    'progression_decision_result_projections', 'generated_documents',
                    'integration_inbox', 'uploaded_documents', 'integration_outbox')
                """); ResultSet results = statement.executeQuery()) {
            results.next();
            assertEquals(7, results.getInt(1));
        }
    }

    @Test
    void createsEnversAuditTablesForEveryReportingBusinessTable() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema='public' AND table_name IN (
                    'published_result_projections_aud', 'progression_decision_projections_aud',
                    'progression_decision_result_projections_aud', 'generated_documents_aud',
                    'uploaded_documents_aud')
                """); ResultSet results = statement.executeQuery()) {
            results.next();
            assertEquals(5, results.getInt(1));
        }
    }

    @Test
    void createsUniqueCurrentProjectionAndGenerationQueueIndexes() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*) FROM pg_indexes
                WHERE schemaname='public' AND indexname IN (
                    'uk_result_projection_current_scope',
                    'uk_progression_projection_current_scope',
                    'idx_generated_document_work_queue',
                    'idx_uploaded_documents_verification_queue',
                    'idx_documents_outbox_dispatch')
                """); ResultSet results = statement.executeQuery()) {
            results.next();
            assertEquals(5, results.getInt(1));
        }
    }

    @Test
    void enforcesUploadedDocumentVerificationEvidenceAndImmutableContent() throws SQLException {
        String documentId = "10000000-0000-4000-8000-000000000001";
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO uploaded_documents (
                    id, owner_type, owner_id, document_type_code, original_file_name,
                    storage_bucket, storage_key, mime_type, file_size_bytes, checksum_sha256,
                    uploaded_by_user_id, uploaded_at, verification_status,
                    created_at, updated_at, version
                ) VALUES (
                    ?::uuid, 'APPLICATION', '20000000-0000-4000-8000-000000000001',
                    'NATIONAL_ID', 'identity.pdf', 'documents', 'uploads/identity.pdf',
                    'application/pdf', 100, repeat('a', 64),
                    '30000000-0000-4000-8000-000000000001', now(), 'PENDING', now(), now(), 0
                )
                """)) {
            insert.setString(1, documentId);
            assertEquals(1, insert.executeUpdate());
        }

        try (PreparedStatement verify = connection.prepareStatement("""
                UPDATE uploaded_documents
                SET verification_status='VERIFIED',
                    verified_by_user_id='40000000-0000-4000-8000-000000000001',
                    verified_at=now(), updated_at=now(), version=1
                WHERE id=?::uuid
                """)) {
            verify.setString(1, documentId);
            assertEquals(1, verify.executeUpdate());
        }

        try (PreparedStatement mutateContent = connection.prepareStatement("""
                UPDATE uploaded_documents SET owner_id='50000000-0000-4000-8000-000000000001'
                WHERE id=?::uuid
                """)) {
            mutateContent.setString(1, documentId);
            org.junit.jupiter.api.Assertions.assertThrows(SQLException.class, mutateContent::executeUpdate);
        }
    }

    @Test
    void acceptsInstitutionOwnedBrandAssets() throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO uploaded_documents (
                    id, owner_type, owner_id, document_type_code, original_file_name,
                    storage_bucket, storage_key, mime_type, file_size_bytes, checksum_sha256,
                    uploaded_by_user_id, uploaded_at, verification_status,
                    created_at, updated_at, version
                ) VALUES (
                    '10000000-0000-4000-8000-000000000002', 'INSTITUTION',
                    '20000000-0000-4000-8000-000000000002', 'INSTITUTION_LOGO',
                    'institution-logo.png', 'documents', 'uploads/institution/logo.png',
                    'image/png', 100, repeat('b', 64),
                    '30000000-0000-4000-8000-000000000002', now(), 'PENDING', now(), now(), 0
                )
                """)) {
            assertEquals(1, insert.executeUpdate());
        }
    }
}
