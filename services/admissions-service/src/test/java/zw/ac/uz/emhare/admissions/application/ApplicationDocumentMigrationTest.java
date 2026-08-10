package zw.ac.uz.emhare.admissions.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** @author Tinashe K */
@Testcontainers
class ApplicationDocumentMigrationTest {
    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("emhare_admissions")
            .withUsername("emhare_service")
            .withPassword("emhare_test_password");

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .locations("classpath:db/migration")
                .load().migrate();
    }

    @Test
    void createsRequirementAuditAndCurrentDocumentProjectionControls() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
             PreparedStatement tables = connection.prepareStatement("""
                     SELECT count(*) FROM information_schema.tables
                     WHERE table_schema='public' AND table_name IN (
                         'application_type_document_requirements',
                         'application_type_document_requirements_aud')
                     """);
             ResultSet tableResults = tables.executeQuery()) {
            tableResults.next();
            assertEquals(2, tableResults.getInt(1));
        }
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
             PreparedStatement indexes = connection.prepareStatement("""
                     SELECT count(*) FROM pg_indexes
                     WHERE schemaname='public' AND indexname IN (
                         'uk_application_documents_current_requirement',
                         'uk_application_documents_last_verification_event',
                         'idx_application_documents_verification_status')
                     """);
             ResultSet indexResults = indexes.executeQuery()) {
            indexResults.next();
            assertEquals(3, indexResults.getInt(1));
        }
    }
}
