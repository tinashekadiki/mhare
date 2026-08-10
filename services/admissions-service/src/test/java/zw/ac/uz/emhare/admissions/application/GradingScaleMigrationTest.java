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
class GradingScaleMigrationTest {

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
    void openConnection() throws SQLException {
        connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        connection.setAutoCommit(false);
        try (PreparedStatement deleteValues = connection.prepareStatement(
                "DELETE FROM grading_scale_values WHERE grading_scale_id IN "
                        + "(SELECT id FROM grading_scales WHERE code IN ('ZIMSEC-A', 'ZIMSEC-O'))");
             PreparedStatement deleteScales = connection.prepareStatement(
                     "DELETE FROM grading_scales WHERE code IN ('ZIMSEC-A', 'ZIMSEC-O')")) {
            deleteValues.executeUpdate();
            deleteScales.executeUpdate();
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
    void rejectsOverlappingActiveScalesForTheSameLevel() throws SQLException {
        insertGradingScale("ZIMSEC-A", "A_LEVEL", LocalDate.of(2020, 1, 1), null);

        SQLException exception = assertThrows(SQLException.class, () -> insertGradingScale(
                "ZIMSEC-A-2", "A_LEVEL", LocalDate.of(2024, 1, 1), null));

        assertEquals("23P01", exception.getSQLState());
    }

    @Test
    void permitsOverlappingScalesForDifferentLevels() throws SQLException {
        insertGradingScale("ZIMSEC-A", "A_LEVEL", LocalDate.of(2020, 1, 1), null);
        insertGradingScale("ZIMSEC-O", "O_LEVEL", LocalDate.of(2020, 1, 1), null);
    }

    @Test
    void rejectsBackwardsEffectivePeriods() {
        SQLException exception = assertThrows(SQLException.class, () -> insertGradingScale(
                "ZIMSEC-A", "A_LEVEL", LocalDate.of(2024, 1, 1), LocalDate.of(2023, 1, 1)));

        assertEquals("23514", exception.getSQLState());
    }

    @Test
    void rejectsDuplicateGradeWithinTheSameScale() throws SQLException {
        UUID scaleId = insertGradingScale("ZIMSEC-A", "A_LEVEL", LocalDate.of(2020, 1, 1), null);
        insertGradingScaleValue(scaleId, "A", new BigDecimal("5.00"), true, 1);

        SQLException exception = assertThrows(SQLException.class, () -> insertGradingScaleValue(
                scaleId, "A", new BigDecimal("5.00"), true, 2));

        assertEquals("23505", exception.getSQLState());
    }

    @Test
    void rejectsNegativePoints() throws SQLException {
        UUID scaleId = insertGradingScale("ZIMSEC-A", "A_LEVEL", LocalDate.of(2020, 1, 1), null);

        SQLException exception = assertThrows(SQLException.class, () -> insertGradingScaleValue(
                scaleId, "F", new BigDecimal("-1.00"), false, 6));

        assertEquals("23514", exception.getSQLState());
    }

    private UUID insertGradingScale(String code, String level, LocalDate effectiveFrom, LocalDate effectiveTo) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO grading_scales (
                    id, code, name, level, effective_from, effective_to, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, now(), now(), 0)
                """)) {
            statement.setObject(1, id);
            statement.setString(2, code);
            statement.setString(3, "Grading scale " + code);
            statement.setString(4, level);
            statement.setDate(5, Date.valueOf(effectiveFrom));
            statement.setDate(6, effectiveTo == null ? null : Date.valueOf(effectiveTo));
            statement.executeUpdate();
        }
        return id;
    }

    private void insertGradingScaleValue(UUID scaleId, String grade, BigDecimal points, boolean pass, int sortOrder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO grading_scale_values (
                    id, grading_scale_id, grade, points, is_pass, sort_order, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, now(), now(), 0)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, scaleId);
            statement.setString(3, grade);
            statement.setBigDecimal(4, points);
            statement.setBoolean(5, pass);
            statement.setInt(6, sortOrder);
            statement.executeUpdate();
        }
    }
}
