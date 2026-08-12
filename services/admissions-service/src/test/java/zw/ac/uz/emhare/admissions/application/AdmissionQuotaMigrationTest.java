package zw.ac.uz.emhare.admissions.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** @author Tinashe K */
@Testcontainers
class AdmissionQuotaMigrationTest {
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
    void createsCurrentAndAuditedQuotaTablesWithPlanningConstraints() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT
                       (SELECT count(*) FROM information_schema.tables
                        WHERE table_schema='public' AND table_name IN ('admission_quotas', 'admission_quotas_aud')),
                       (SELECT count(*) FROM pg_indexes
                        WHERE schemaname='public' AND indexname IN (
                          'uk_admission_quotas_current_scope', 'ix_admission_quotas_intake_programme'))
                     """);
             ResultSet results = statement.executeQuery()) {
            results.next();
            assertEquals(2, results.getInt(1));
            assertEquals(2, results.getInt(2));
        }
    }
}
