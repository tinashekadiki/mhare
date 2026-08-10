package zw.ac.uz.emhare.admissions.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** @author Tinashe K */
@Testcontainers
class ZimsecReferenceDataMigrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("emhare_admissions")
            .withUsername("emhare_service")
            .withPassword("emhare_test_password");

    @BeforeAll
    static void migrateDatabase() {
        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void seedsCompleteZimsecSubjectAndGradeReferenceData() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())) {
            Map<String, BigDecimal> pointsByGrade = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT value.grade, value.points
                    FROM grading_scale_values value
                    JOIN grading_scales scale ON scale.id = value.grading_scale_id
                    WHERE scale.code = 'ZIMSEC-A'
                      AND value.points IS NOT NULL
                    ORDER BY value.sort_order
                    """); ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    pointsByGrade.put(resultSet.getString("grade"), resultSet.getBigDecimal("points"));
                }
            }
            assertEquals(Map.of(
                    "A", new BigDecimal("5.00"), "B", new BigDecimal("4.00"),
                    "C", new BigDecimal("3.00"), "D", new BigDecimal("2.00"),
                    "E", new BigDecimal("1.00")), pointsByGrade);

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT level,
                           count(*) AS subject_count,
                           count(*) FILTER (WHERE is_science_subject) AS science_count
                    FROM admission_subjects
                    WHERE level IN ('O_LEVEL', 'A_LEVEL')
                    GROUP BY level
                    ORDER BY level
                    """); ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("A_LEVEL", resultSet.getString("level"));
                assertEquals(51, resultSet.getInt("subject_count"));
                assertTrue(resultSet.getInt("science_count") >= 10);
                assertTrue(resultSet.next());
                assertEquals("O_LEVEL", resultSet.getString("level"));
                assertEquals(47, resultSet.getInt("subject_count"));
                assertTrue(resultSet.getInt("science_count") >= 6);
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT scale.code,
                           count(*) AS grade_count,
                           count(*) FILTER (WHERE value.is_pass) AS pass_count
                    FROM grading_scale_values value
                    JOIN grading_scales scale ON scale.id = value.grading_scale_id
                    WHERE scale.code IN ('ZIMSEC-A', 'ZIMSEC-O')
                    GROUP BY scale.code
                    ORDER BY scale.code
                    """); ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("ZIMSEC-A", resultSet.getString("code"));
                assertEquals(7, resultSet.getInt("grade_count"));
                assertEquals(5, resultSet.getInt("pass_count"));
                assertTrue(resultSet.next());
                assertEquals("ZIMSEC-O", resultSet.getString("code"));
                assertEquals(6, resultSet.getInt("grade_count"));
                assertEquals(3, resultSet.getInt("pass_count"));
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT name
                    FROM admission_subjects
                    WHERE level = 'O_LEVEL' AND code = '4068'
                    """); ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("Ndebele Language", resultSet.getString("name"));
            }
        }
    }
}
