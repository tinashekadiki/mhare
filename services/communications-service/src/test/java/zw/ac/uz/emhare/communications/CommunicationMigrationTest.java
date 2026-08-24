package zw.ac.uz.emhare.communications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Verifies the Communications service-owned schema. @author Tinashe K */
@Testcontainers
class CommunicationMigrationTest {

  @Container
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18-alpine")
          .withDatabaseName("emhare_communications_migration")
          .withUsername("emhare_service")
          .withPassword("emhare_test_password");

  private Connection connection;

  @BeforeAll
  static void migrate() {
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();
  }

  @BeforeEach
  void connect() throws SQLException {
    connection =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  @AfterEach
  void close() throws SQLException {
    connection.close();
  }

  @Test
  void createsEveryBusinessAndEnversAuditTableWithTheRequiredColumns() throws SQLException {
    for (String table :
        List.of(
            "communication_categories",
            "communication_items",
            "communication_item_versions",
            "communication_publications",
            "communication_workflow_events",
            "communication_media_assets",
            "event_occurrences",
            "communication_read_receipts")) {
      assertTrue(tableExists(table), table);
      assertTrue(tableExists(table + "_aud"), table + "_aud");
      assertStandardColumns(table);
    }
  }

  @Test
  void preventsDuplicateAuthenticatedReadReceipts() throws SQLException {
    Fixture fixture = insertApprovedPublication();
    UUID reader = UUID.randomUUID();
    execute(
        "INSERT INTO communication_read_receipts(id,publication_id,reader_user_id,read_at,created_at,updated_at,version) VALUES (?,?,?,now(),now(),now(),0)",
        UUID.randomUUID(),
        fixture.publicationId(),
        reader);

    SQLException duplicate =
        assertThrows(
            SQLException.class,
            () ->
                execute(
                    "INSERT INTO communication_read_receipts(id,publication_id,reader_user_id,read_at,created_at,updated_at,version) VALUES (?,?,?,now(),now(),now(),0)",
                    UUID.randomUUID(),
                    fixture.publicationId(),
                    reader));

    assertEquals("23505", duplicate.getSQLState());
  }

  @Test
  void approvedVersionsAndWorkflowEvidenceAreImmutable() throws SQLException {
    Fixture fixture = insertApprovedPublication();
    SQLException versionChange =
        assertThrows(
            SQLException.class,
            () ->
                execute(
                    "UPDATE communication_item_versions SET title='Changed' WHERE id=?",
                    fixture.versionId()));
    assertEquals("P0001", versionChange.getSQLState());

    UUID eventId = UUID.randomUUID();
    execute(
        "INSERT INTO communication_workflow_events(id,item_id,version_id,event_type,from_status,to_status,actor_user_id,occurred_at,created_at,updated_at,version) VALUES (?,?,?,'APPROVED','IN_REVIEW','APPROVED',?,now(),now(),now(),0)",
        eventId,
        fixture.itemId(),
        fixture.versionId(),
        UUID.randomUUID());
    SQLException eventChange =
        assertThrows(
            SQLException.class,
            () -> execute("DELETE FROM communication_workflow_events WHERE id=?", eventId));
    assertEquals("P0001", eventChange.getSQLState());
  }

  private Fixture insertApprovedPublication() throws SQLException {
    UUID itemId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    UUID publicationId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    UUID approverId = UUID.randomUUID();
    execute(
        "INSERT INTO communication_items(id,kind,slug,lifecycle_status,created_at,updated_at,version) VALUES (?,'NEWS',?,'ACTIVE',now(),now(),0)",
        itemId,
        "test-" + itemId);
    execute(
        "INSERT INTO communication_item_versions(id,item_id,version_number,title,summary,schema_version,structured_content,workflow_status,authored_by_user_id,submitted_by_user_id,submitted_at,decided_by_user_id,decided_at,created_at,updated_at,version) VALUES (?,?,1,'Title','Summary',1,'[]'::jsonb,'APPROVED',?,?,now(),?,now(),now(),now(),0)",
        versionId,
        itemId,
        authorId,
        authorId,
        approverId);
    execute(
        "INSERT INTO communication_publications(id,item_id,version_id,status,publish_from,created_at,updated_at,version) VALUES (?,?,?,'LIVE',now(),now(),now(),0)",
        publicationId,
        itemId,
        versionId);
    return new Fixture(itemId, versionId, publicationId);
  }

  private boolean tableExists(String name) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
      statement.setString(1, "public." + name);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        return result.getBoolean(1);
      }
    }
  }

  private void assertStandardColumns(String table) throws SQLException {
    Set<String> actual = new HashSet<>();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name=?")) {
      statement.setString(1, table);
      try (ResultSet result = statement.executeQuery()) {
        while (result.next()) {
          actual.add(result.getString(1));
        }
      }
    }
    assertTrue(
        actual.containsAll(
            Set.of(
                "id",
                "created_at",
                "updated_at",
                "created_by_user_id",
                "modified_by_user_id",
                "deleted_at",
                "deleted_by_user_id",
                "version")));
  }

  private void execute(String sql, Object... values) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < values.length; index++) {
        statement.setObject(index + 1, values[index]);
      }
      statement.executeUpdate();
    }
  }

  private record Fixture(UUID itemId, UUID versionId, UUID publicationId) {}
}
