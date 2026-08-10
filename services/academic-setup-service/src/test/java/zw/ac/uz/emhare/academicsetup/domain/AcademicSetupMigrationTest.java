package zw.ac.uz.emhare.academicsetup.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

/** @author Tinashe K */
@Testcontainers
class AcademicSetupMigrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("emhare_academic_setup")
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
    void rejectsHierarchyThatSkipsAConfiguredUnitTypeLevel() throws SQLException {
        UUID rootTypeId = insertAcademicUnitType("ROOT", 1, false);
        insertAcademicUnitType("MIDDLE", 2, false);
        UUID leafTypeId = insertAcademicUnitType("LEAF", 3, true);
        UUID rootUnitId = insertAcademicUnit(rootTypeId, null, "ROOT_UNIT");

        SQLException exception = assertThrows(
                SQLException.class,
                () -> insertAcademicUnit(leafTypeId, rootUnitId, "INVALID_LEAF"));

        assertEquals("23514", exception.getSQLState());
    }

    @Test
    void preventsAnOwningAcademicUnitFromReceivingChildren() throws SQLException {
        UUID rootTypeId = insertAcademicUnitType("FACULTY", 1, true);
        UUID childTypeId = insertAcademicUnitType("DEPARTMENT", 2, true);
        UUID ownerId = insertAcademicUnit(rootTypeId, null, "SCIENCE");
        insertProgramme(ownerId);

        SQLException exception = assertThrows(
                SQLException.class,
                () -> insertAcademicUnit(childTypeId, ownerId, "COMPUTING"));

        assertEquals("23514", exception.getSQLState());
    }

    @Test
    void rejectsAcademicPeriodsOutsideTheirAcademicYear() throws SQLException {
        UUID academicYearId = insertAcademicYear("2027", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
        UUID periodTypeId = insertAcademicPeriodType("SEMESTER");

        SQLException exception = assertThrows(SQLException.class, () -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO academic_periods (
                        id, academic_year_id, academic_period_type_id, code, name,
                        start_date, end_date, status, created_at, updated_at, version
                    ) VALUES (?, ?, ?, '2028-S1', 'Semester 1', ?, ?, 'DRAFT', now(), now(), 0)
                    """)) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, academicYearId);
                statement.setObject(3, periodTypeId);
                statement.setDate(4, Date.valueOf(LocalDate.of(2027, 12, 1)));
                statement.setDate(5, Date.valueOf(LocalDate.of(2028, 3, 31)));
                statement.executeUpdate();
            }
        });

        assertEquals("23514", exception.getSQLState());
    }

    @Test
    void rejectsOverlappingNonArchivedAcademicYears() throws SQLException {
        insertAcademicYear("2027", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));

        SQLException exception = assertThrows(
                SQLException.class,
                () -> insertAcademicYear("2027-OVERLAP", LocalDate.of(2027, 7, 1), LocalDate.of(2028, 6, 30)));

        assertEquals("23P01", exception.getSQLState());
    }

    @Test
    void databaseProtectsOpenIntakeIdentityDuringCorrection() throws SQLException {
        UUID academicYearId = insertAcademicYear("2030", LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31));
        UUID intakeId = UUID.randomUUID();
        execute("""
                INSERT INTO intakes (
                    id, academic_year_id, code, name, starts_on, ends_on, status,
                    change_reason, created_at, updated_at, version
                ) VALUES (?, ?, 'JAN-2030', 'January 2030 Intake', DATE '2030-01-01', DATE '2030-03-31',
                    'OPEN', 'Initial record creation.', now(), now(), 0)
                """, intakeId, academicYearId);

        SQLException exception = assertThrows(SQLException.class, () -> execute("""
                UPDATE intakes
                SET code = 'FEB-2030', change_reason = 'Attempted identity correction.', updated_at = now()
                WHERE id = ?
                """, intakeId));

        assertEquals("23514", exception.getSQLState());
    }

    @Test
    void databaseProtectsActiveProgrammeIdentityDuringCorrection() throws SQLException {
        UUID leafTypeId = insertAcademicUnitType("FACULTY_PROG", 1, true);
        UUID ownerId = insertAcademicUnit(leafTypeId, null, "FACULTY_A");
        UUID otherOwnerId = insertAcademicUnit(leafTypeId, null, "FACULTY_B");
        UUID programmeId = insertProgramme(ownerId);
        execute("UPDATE programmes SET status = 'ACTIVE', version = 1 WHERE id = ?", programmeId);

        SQLException exception = assertThrows(SQLException.class, () -> execute("""
                UPDATE programmes
                SET owning_academic_unit_id = ?, change_reason = 'Attempted identity correction.', updated_at = now()
                WHERE id = ?
                """, otherOwnerId, programmeId));

        assertEquals("23514", exception.getSQLState());
    }

    @Test
    void approvedCurriculumAllowsGovernedAmendmentsButProtectsIdentityAndRetiredHistory() throws SQLException {
        UUID leafTypeId = insertAcademicUnitType("SCHOOL", 1, true);
        UUID ownerId = insertAcademicUnit(leafTypeId, null, "ENGINEERING");
        UUID programmeId = insertProgramme(ownerId);
        UUID moduleId = insertAcademicModule(ownerId);
        UUID programmeVersionId = UUID.randomUUID();
        UUID curriculumModuleId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();

        execute("""
                INSERT INTO programme_versions (
                    id, programme_id, version_code, effective_from, status,
                    created_at, updated_at, version
                ) VALUES (?, ?, '2027.1', DATE '2027-01-01', 'DRAFT', now(), now(), 0)
                """, programmeVersionId, programmeId);
        execute("""
                INSERT INTO curriculum_modules (
                    id, programme_version_id, module_id, period_number, module_type,
                    credit_value, minimum_mark_required, sort_order,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, 1, 'COMPULSORY', 12.00, 50.00, 1, now(), now(), 0)
                """, curriculumModuleId, programmeVersionId, moduleId);
        execute("""
                UPDATE programme_versions
                SET status = 'APPROVED', approved_by_user_id = ?, approved_at = now(), updated_at = now(), version = 1
                WHERE id = ?
                """, approverId, programmeVersionId);

        execute("UPDATE curriculum_modules SET period_number = 2, version = 1 WHERE id = ?", curriculumModuleId);
        assertEquals(2, queryInteger("SELECT period_number FROM curriculum_modules WHERE id = ?", curriculumModuleId));

        UUID replacementModuleId = insertAcademicModule(ownerId);
        var identityChangeSavepoint = connection.setSavepoint();
        SQLException identityChangeException = assertThrows(SQLException.class, () -> execute(
                "UPDATE curriculum_modules SET module_id = ? WHERE id = ?",
                replacementModuleId, curriculumModuleId));
        assertEquals("23514", identityChangeException.getSQLState());
        connection.rollback(identityChangeSavepoint);

        execute("UPDATE programme_versions SET status = 'RETIRED', effective_to = DATE '2027-12-31', version = 2 WHERE id = ?", programmeVersionId);
        SQLException retiredHistoryException = assertThrows(SQLException.class, () -> execute(
                "UPDATE curriculum_modules SET period_number = 3 WHERE id = ?",
                curriculumModuleId));
        assertEquals("23514", retiredHistoryException.getSQLState());
    }

    @Test
    void intakeProgrammeTargetsSupportDraftSetupAndRejectOperationalMutation() throws SQLException {
        UUID academicYearId = insertAcademicYear(
                "2032", LocalDate.of(2032, 1, 1), LocalDate.of(2032, 12, 31));
        UUID intakeId = UUID.randomUUID();
        execute("""
                INSERT INTO intakes (
                    id, academic_year_id, code, name, starts_on, ends_on, status,
                    change_reason, created_at, updated_at, version
                ) VALUES (?, ?, 'JAN-2032', 'January 2032 Intake', DATE '2032-01-01', DATE '2032-03-31',
                    'DRAFT', 'Initial record creation.', now(), now(), 0)
                """, intakeId, academicYearId);
        UUID leafTypeId = insertAcademicUnitType("TARGET_OWNER", 1, true);
        UUID ownerId = insertAcademicUnit(leafTypeId, null, "TARGET_SCHOOL");
        UUID programmeId = insertProgramme(ownerId);
        UUID programmeLevelId = queryUuid(
                "SELECT programme_level_id FROM programmes WHERE id = ?", programmeId);
        execute("UPDATE programmes SET status = 'ACTIVE', version = 1 WHERE id = ?", programmeId);

        UUID programmeLevelTargetId = UUID.randomUUID();
        UUID programmeTargetId = UUID.randomUUID();
        execute("""
                INSERT INTO intake_programme_level_targets (
                    id, intake_id, programme_level_id, created_at, updated_at, version
                ) VALUES (?, ?, ?, now(), now(), 0)
                """, programmeLevelTargetId, intakeId, programmeLevelId);
        execute("""
                INSERT INTO intake_programme_targets (
                    id, intake_id, programme_id, created_at, updated_at, version
                ) VALUES (?, ?, ?, now(), now(), 0)
                """, programmeTargetId, intakeId, programmeId);

        assertEquals(1, queryInteger(
                "SELECT count(*) FROM intake_programme_targets WHERE intake_id = ? AND deleted_at IS NULL",
                intakeId));
        execute("UPDATE intakes SET status = 'OPEN', version = 1 WHERE id = ?", intakeId);

        SQLException operationalMutation = assertThrows(SQLException.class, () -> execute(
                "UPDATE intake_programme_targets SET deleted_at = now(), version = 1 WHERE id = ?",
                programmeTargetId));
        assertEquals("23514", operationalMutation.getSQLState());
    }

    private UUID insertAcademicUnitType(String code, int levelOrder, boolean leafAllowed) throws SQLException {
        UUID id = UUID.randomUUID();
        execute("""
                INSERT INTO academic_unit_types (
                    id, code, name, level_order, is_leaf_allowed, status,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now(), 0)
                """, id, code, code, levelOrder, leafAllowed);
        return id;
    }

    private UUID insertAcademicUnit(UUID typeId, UUID parentId, String code) throws SQLException {
        UUID id = UUID.randomUUID();
        execute("""
                INSERT INTO academic_units (
                    id, academic_unit_type_id, parent_id, code, name, status,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now(), 0)
                """, id, typeId, parentId, code, code);
        return id;
    }

    private UUID insertAcademicYear(String name, LocalDate startDate, LocalDate endDate) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO academic_years (
                    id, name, start_date, end_date, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, 'DRAFT', now(), now(), 0)
                """)) {
            statement.setObject(1, id);
            statement.setString(2, name);
            statement.setDate(3, Date.valueOf(startDate));
            statement.setDate(4, Date.valueOf(endDate));
            statement.executeUpdate();
        }
        return id;
    }

    private UUID insertAcademicPeriodType(String code) throws SQLException {
        UUID id = UUID.randomUUID();
        execute("""
                INSERT INTO academic_period_types (
                    id, code, name, sort_order, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, 1, 'ACTIVE', now(), now(), 0)
                """, id, code, code);
        return id;
    }

    private UUID insertProgramme(UUID ownerId) throws SQLException {
        UUID programmeLevelId = UUID.randomUUID();
        UUID programmeTypeId = UUID.randomUUID();
        UUID programmeId = UUID.randomUUID();
        execute("""
                INSERT INTO programme_levels (
                    id, code, name, sort_order, status, created_at, updated_at, version
                ) VALUES (?, ?, 'Undergraduate', 1, 'ACTIVE', now(), now(), 0)
                """, programmeLevelId, "UG_" + programmeLevelId.toString().substring(0, 8).toUpperCase());
        execute("""
                INSERT INTO programme_types (
                    id, code, name, status, created_at, updated_at, version
                ) VALUES (?, ?, 'Degree', 'ACTIVE', now(), now(), 0)
                """, programmeTypeId, "DEGREE_" + programmeTypeId.toString().substring(0, 8).toUpperCase());
        execute("""
                INSERT INTO programmes (
                    id, owning_academic_unit_id, programme_type_id, programme_level_id,
                    code, name, award_name, minimum_duration_periods, maximum_duration_periods,
                    status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, 'Information Technology', 'Bachelor of Science', 8, 12,
                    'DRAFT', now(), now(), 0)
                """, programmeId, ownerId, programmeTypeId, programmeLevelId,
                "P" + programmeId.toString().substring(0, 4).toUpperCase());
        return programmeId;
    }

    private UUID insertAcademicModule(UUID ownerId) throws SQLException {
        UUID moduleId = UUID.randomUUID();
        execute("""
                INSERT INTO modules (
                    id, owning_academic_unit_id, code, name, description,
                    credit_value, academic_level, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, 'Programming Fundamentals', 'Foundational Module',
                    12.00, 1, 'ACTIVE', now(), now(), 0)
                """, moduleId, ownerId, "CSC_" + moduleId.toString().substring(0, 8).toUpperCase());
        return moduleId;
    }

    private void execute(String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }

    private int queryInteger(String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private UUID queryUuid(String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getObject(1, UUID.class);
            }
        }
    }
}
