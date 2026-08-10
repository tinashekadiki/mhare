package zw.ac.uz.emhare.finance.payment.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** @author Tinashe K */
@Testcontainers
class ApplicationPaymentProviderMigrationTest {

    @Container
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("emhare_finance")
            .withUsername("emhare_service")
            .withPassword("emhare_test_password");

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void createsAuditedProviderAttemptRegisterWithRequiredControls() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
                Statement statement = connection.createStatement()) {
            assertCount(statement, 2, """
                    SELECT count(*) FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name IN (
                          'application_payment_provider_attempts',
                          'application_payment_provider_attempts_aud')
                    """);
            assertCount(statement, 4, """
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'application_payment_provider_attempts'
                      AND column_name IN ('merchant_trace', 'return_nonce_hash', 'expires_at', 'version')
                    """);
        }
    }

    private void assertCount(Statement statement, int expected, String sql) throws SQLException {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            assertEquals(expected, result.getInt(1));
        }
    }
}
