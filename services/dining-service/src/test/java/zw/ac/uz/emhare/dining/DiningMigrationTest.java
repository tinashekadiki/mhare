package zw.ac.uz.emhare.dining;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** @author Tinashe K */
@Testcontainers
class DiningMigrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("emhare_dining")
            .withUsername("emhare_service")
            .withPassword("emhare_test_password");

    private Connection connection;

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
    }

    @BeforeEach
    void connect() throws SQLException {
        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        execute("TRUNCATE dining_halls, meal_options, dining_plans, student_dietary_requirements CASCADE");
    }
    @AfterEach
    void close() throws SQLException { connection.close(); }

    @Test
    void createsEveryGovernedBusinessAndAuditTable() throws SQLException {
        List<String> businessTables = List.of("dining_halls", "meal_options", "meal_service_times",
                "dining_plans", "dining_plan_meals", "dining_hall_assignment_rules",
                "dining_attendant_assignments", "student_dining_assignments",
                "student_dietary_requirements", "meal_service_sessions",
                "meal_attendance_events", "meal_attendance_reversals");
        for (String table : businessTables) {
            assertTrue(tableExists(table), table + " must exist");
            assertTrue(tableExists(table + "_aud"), table + " audit table must exist");
            assertStandardColumns(table);
        }
    }

    @Test
    void enforcesIndependentApprovalForPlansAndAssignments() throws SQLException {
        UUID operator = UUID.randomUUID();
        SQLException planError = assertThrows(SQLException.class, () -> execute("""
            INSERT INTO dining_plans(id,code,plan_version,name,valid_from,status,prepared_by_user_id,
              approved_by_user_id,approved_at,approval_reason,created_at,updated_at,version)
            VALUES (?,?,?,?,?,'ACTIVE',?,?,now(),'self approval',now(),now(),0)
            """, UUID.randomUUID(), "FULL", 1, "Full board", LocalDate.now(), operator, operator));
        assertEquals("23514", planError.getSQLState());
    }

    @Test
    void admitsOnlyAnEntitledStudentOnceIntoAnOpenSession() throws SQLException {
        Fixture fixture = createOpenSessionFixture();
        UUID firstEvent = UUID.randomUUID();
        execute("""
            INSERT INTO meal_attendance_events(id,event_number,meal_service_session_id,student_dining_assignment_id,
              student_id,student_number,student_name,outcome,captured_by_user_id,captured_at,capture_channel,
              idempotency_key,created_at,updated_at,version)
            VALUES (?,?,?,?,?,?,?,'ADMITTED',?,now(),'ONLINE',?,now(),now(),0)
            """, firstEvent, "MEAL-00000001", fixture.sessionId, fixture.assignmentId, fixture.studentId,
                "R271234A", "Example Student", UUID.randomUUID(), "scan-0001");

        SQLException duplicate = assertThrows(SQLException.class, () -> execute("""
            INSERT INTO meal_attendance_events(id,event_number,meal_service_session_id,student_dining_assignment_id,
              student_id,student_number,student_name,outcome,captured_by_user_id,captured_at,capture_channel,
              idempotency_key,created_at,updated_at,version)
            VALUES (?,?,?,?,?,?,?,'ADMITTED',?,now(),'ONLINE',?,now(),now(),0)
            """, UUID.randomUUID(), "MEAL-00000002", fixture.sessionId, fixture.assignmentId,
                fixture.studentId, "R271234A", "Example Student", UUID.randomUUID(), "scan-0002"));
        assertEquals("23505", duplicate.getSQLState());
    }

    @Test
    void rejectsAdmissionForAPlanWithoutTheMealOption() throws SQLException {
        Fixture fixture = createOpenSessionFixture();
        execute("DELETE FROM dining_plan_meals");
        SQLException error = assertThrows(SQLException.class, () -> execute("""
            INSERT INTO meal_attendance_events(id,event_number,meal_service_session_id,student_dining_assignment_id,
              student_id,student_number,student_name,outcome,captured_by_user_id,captured_at,capture_channel,
              idempotency_key,created_at,updated_at,version)
            VALUES (?,?,?,?,?,?,?,'ADMITTED',?,now(),'ONLINE',?,now(),now(),0)
            """, UUID.randomUUID(), "MEAL-00000003", fixture.sessionId, fixture.assignmentId,
                fixture.studentId, "R271234A", "Example Student", UUID.randomUUID(), "scan-0003"));
        assertEquals("P0001", error.getSQLState());
        assertTrue(error.getMessage().contains("does not include"));
    }

    @Test
    void preservesAttendanceAndReversalAsAppendOnlyEvidence() throws SQLException {
        Fixture fixture = createOpenSessionFixture();
        UUID eventId = UUID.randomUUID();
        execute("""
            INSERT INTO meal_attendance_events(id,event_number,meal_service_session_id,student_dining_assignment_id,
              student_id,student_number,student_name,outcome,captured_by_user_id,captured_at,capture_channel,
              idempotency_key,created_at,updated_at,version)
            VALUES (?,?,?,?,?,?,?,'ADMITTED',?,now(),'ONLINE',?,now(),now(),0)
            """, eventId, "MEAL-00000004", fixture.sessionId, fixture.assignmentId, fixture.studentId,
                "R271234A", "Example Student", UUID.randomUUID(), "scan-0004");
        assertEquals("P0001", assertThrows(SQLException.class,
                () -> execute("UPDATE meal_attendance_events SET student_name='Changed' WHERE id=?", eventId)).getSQLState());

        UUID reversalId = UUID.randomUUID();
        execute("""
            INSERT INTO meal_attendance_reversals(id,meal_attendance_event_id,reason_code,reason,reversed_by_user_id,
              reversed_at,created_at,updated_at,version) VALUES (?,?,'CAPTURE_ERROR','Wrong student scanned',?,now(),now(),now(),0)
            """, reversalId, eventId, UUID.randomUUID());
        assertEquals("P0001", assertThrows(SQLException.class,
                () -> execute("DELETE FROM meal_attendance_reversals WHERE id=?", reversalId)).getSQLState());
    }

    private Fixture createOpenSessionFixture() throws SQLException {
        UUID maker = UUID.randomUUID(); UUID checker = UUID.randomUUID();
        UUID hallId = UUID.randomUUID(); UUID mealId = UUID.randomUUID(); UUID planId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID(); UUID assignmentId = UUID.randomUUID(); UUID sessionId = UUID.randomUUID();
        execute("INSERT INTO dining_halls(id,code,name,location_description,service_capacity,active,created_at,updated_at,version) VALUES (?,'MAIN','Main Dining Hall','Main campus',100,true,now(),now(),0)", hallId);
        execute("INSERT INTO meal_options(id,code,name,meal_category,active,created_at,updated_at,version) VALUES (?,'LUNCH','Lunch','LUNCH',true,now(),now(),0)", mealId);
        execute("""
            INSERT INTO dining_plans(id,code,plan_version,name,valid_from,status,prepared_by_user_id,approved_by_user_id,
              approved_at,approval_reason,created_at,updated_at,version)
            VALUES (?,'FULL',1,'Full board',current_date-1,'ACTIVE',?,?,now(),'independent approval',now(),now(),0)
            """, planId, maker, checker);
        execute("""
            INSERT INTO dining_plan_meals(id,dining_plan_id,meal_option_id,servings_per_service,
              monday,tuesday,wednesday,thursday,friday,saturday,sunday,created_at,updated_at,version)
            VALUES (?,?,?,1,true,true,true,true,true,true,true,now(),now(),0)
            """, UUID.randomUUID(), planId, mealId);
        execute("""
            INSERT INTO student_dining_assignments(id,assignment_number,student_id,student_number,student_name,
              academic_period_id,academic_period_code,dining_hall_id,dining_plan_id,effective_from,effective_until,
              status,prepared_by_user_id,approved_by_user_id,approved_at,approval_reason,billing_status,created_at,updated_at,version)
            VALUES (?,'DINE-00000001',?,'R271234A','Example Student',?,'2027-S1',?,?,current_date-1,current_date+30,
              'ACTIVE',?,?,now(),'eligibility verified','NOT_REQUESTED',now(),now(),0)
            """, assignmentId, studentId, UUID.randomUUID(), hallId, planId, maker, checker);
        execute("""
            INSERT INTO meal_service_sessions(id,session_number,dining_hall_id,meal_option_id,service_date,
              scheduled_opens_at,scheduled_closes_at,status,prepared_by_user_id,opened_by_user_id,opened_at,
              created_at,updated_at,version)
            VALUES (?,'SESSION-00000001',?,?,current_date,now()-interval '1 hour',now()+interval '1 hour',
              'OPEN',?,?,now(),now(),now(),0)
            """, sessionId, hallId, mealId, maker, checker);
        return new Fixture(sessionId, assignmentId, studentId);
    }

    private boolean tableExists(String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
            statement.setString(1, "public." + name);
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getBoolean(1); }
        }
    }

    private void assertStandardColumns(String table) throws SQLException {
        Set<String> expected = Set.of("id", "created_at", "updated_at", "created_by_user_id",
                "modified_by_user_id", "deleted_at", "deleted_by_user_id", "version");
        Set<String> actual = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name=?")) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) { while (result.next()) actual.add(result.getString(1)); }
        }
        assertTrue(actual.containsAll(expected), table + " is missing standard audit columns");
    }

    private void execute(String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            statement.executeUpdate();
        }
    }

    private record Fixture(UUID sessionId, UUID assignmentId, UUID studentId) {}
}
