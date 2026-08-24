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

/** Verifies evidence placement, category, and award migrations. @author Tinashe K */
@Testcontainers
class EvidenceCaptureMigrationTest {

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
        .load()
        .migrate();
  }

  @Test
  void createsCaptureMetadataAndQualificationAwardColumnsWithAuditParity() throws Exception {
    assertEquals(1, countColumn("application_type_document_requirements", "capture_section_code"));
    assertEquals(
        1, countColumn("application_type_document_requirements_aud", "capture_section_code"));
    assertEquals(
        1, countColumn("application_document_requirement_snapshots", "applicant_category_codes"));
    assertEquals(
        1,
        countColumn("application_document_requirement_snapshots_aud", "applicant_category_codes"));
    assertEquals(1, countColumn("applicant_qualification_sittings", "award_type_code"));
    assertEquals(1, countColumn("applicant_qualification_sittings_aud", "award_type_code"));
  }

  @Test
  void createsCategoryApplicabilityBusinessAndAuditTables() throws Exception {
    assertEquals(1, countTable("application_type_document_requirement_categories"));
    assertEquals(1, countTable("application_type_document_requirement_categories_aud"));
  }

  private int countColumn(String tableName, String columnName) throws Exception {
    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """)) {
      statement.setString(1, tableName);
      statement.setString(2, columnName);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        return result.getInt(1);
      }
    }
  }

  private int countTable(String tableName) throws Exception {
    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = ?
                """)) {
      statement.setString(1, tableName);
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
