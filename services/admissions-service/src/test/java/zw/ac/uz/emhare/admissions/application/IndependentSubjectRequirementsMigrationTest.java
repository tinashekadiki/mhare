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

/** Verifies independent subject classifications and baseline admission rules. @author Tinashe K */
@Testcontainers
class IndependentSubjectRequirementsMigrationTest {

  @Container
  private static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
          .withDatabaseName("emhare_admissions")
          .withUsername("emhare_service")
          .withPassword("emhare_test_password");

  @BeforeAll
  static void migrateDatabase() {
    Flyway.configure()
        .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
        .locations("classpath:db/migration")
        .target("6")
        .load()
        .migrate();
  }

  @Test
  void createsIndependentFlagsOnBusinessAndAuditTables() throws Exception {
    assertEquals(
        2, countColumns("admission_subjects", "is_mathematics_subject", "is_english_subject"));
    assertEquals(
        2, countColumns("admission_subjects_aud", "is_mathematics_subject", "is_english_subject"));
    assertEquals(
        2, countColumns("admission_requirement_sets", "requires_mathematics", "requires_science"));
    assertEquals(
        2,
        countColumns("admission_requirement_sets_aud", "requires_mathematics", "requires_science"));
  }

  @Test
  void backfillsExistingMathematicsAndEnglishClassifications() throws Exception {
    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                SELECT count(*)
                FROM admission_subjects
                WHERE (code = '4004' AND is_mathematics_subject)
                   OR (code = '4005' AND is_english_subject)
                """)) {
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        assertEquals(2, result.getInt(1));
      }
    }
  }

  private int countColumns(String tableName, String firstColumn, String secondColumn)
      throws Exception {
    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name IN (?, ?)
                """)) {
      statement.setString(1, tableName);
      statement.setString(2, firstColumn);
      statement.setString(3, secondColumn);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        return result.getInt(1);
      }
    }
  }

  private Connection connection() throws Exception {
    return DriverManager.getConnection(
        POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
  }
}
