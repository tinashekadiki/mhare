package zw.ac.uz.emhare.accommodation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
class AccommodationMigrationTest {
    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
                    .withDatabaseName("emhare_accommodation")
                    .withUsername("emhare_service")
                    .withPassword("emhare_test_password");

    private Connection connection;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @BeforeEach
    void connect() throws SQLException {
        connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        connection.setAutoCommit(false);
    }

    @AfterEach
    void rollback() throws SQLException {
        if (connection != null) {
            connection.rollback();
            connection.close();
        }
    }

    @Test
    void createsCompleteBusinessAndAuditSchema() throws SQLException {
        assertCount(17, """
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema='public' AND table_name IN (
                  'accommodation_premises','accommodation_room_types','residence_halls','accommodation_rooms',
                  'accommodation_room_facilities','accommodation_room_facility_assignments',
                  'accommodation_application_periods','accommodation_rates','accommodation_groups',
                  'accommodation_group_rules','accommodation_blacklist_entries','accommodation_applications',
                  'accommodation_waitlist_entries','room_allocations','room_allocation_events','room_swaps',
                  'accommodation_damage_records')
                """);
        assertCount(17, """
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema='public' AND table_name LIKE '%_aud'
                """);
    }

    @Test
    void rejectsApplicationOutsideOpenPeriodAndForBlacklistedStudent() throws SQLException {
        UUID closedPeriodId = insertApplicationPeriod("APPLICATION_CLOSED");
        SQLException closedPeriodException = assertThrows(
                SQLException.class, () -> insertSubmittedApplication(closedPeriodId, UUID.randomUUID(), "FEMALE"));
        assertEquals("P0001", closedPeriodException.getSQLState());
        connection.rollback();

        UUID openPeriodId = insertApplicationPeriod("APPLICATION_OPEN");
        UUID blacklistedStudentId = UUID.randomUUID();
        execute("""
                INSERT INTO accommodation_blacklist_entries(
                  id,student_id,student_number,reason_code,reason,effective_from,status,
                  imposed_by_user_id,imposed_at,created_at,updated_at,version)
                VALUES (?,?,'STU-BLACKLISTED','DAMAGE','Outstanding residence damage',current_date-1,
                  'ACTIVE',?,now(),now(),now(),0)
                """, UUID.randomUUID(), blacklistedStudentId, UUID.randomUUID());
        SQLException blacklistException = assertThrows(
                SQLException.class,
                () -> insertSubmittedApplication(openPeriodId, blacklistedStudentId, "FEMALE"));
        assertEquals("P0001", blacklistException.getSQLState());
    }

    @Test
    void enforcesMakerCheckerForPeriodRateAndAllocationApproval() throws SQLException {
        UUID samePeriodActor = UUID.randomUUID();
        SQLException periodException = assertThrows(SQLException.class, () -> execute("""
                INSERT INTO accommodation_application_periods(
                  id,academic_period_id,academic_period_code,code,name,applications_open_at,applications_close_at,
                  occupancy_starts_on,occupancy_ends_on,allocation_cutoff_at,status,prepared_by_user_id,
                  approved_by_user_id,approved_at,approval_reason,created_at,updated_at,version)
                VALUES (?,?, '2027-S1',?,'Residence 2027',now()-interval '1 day',now()+interval '1 day',
                  current_date+2,current_date+90,now()+interval '2 days','APPLICATION_OPEN',?,?,now(),
                  'Approved',now(),now(),0)
                """, UUID.randomUUID(), UUID.randomUUID(), uniqueCode("PER"), samePeriodActor, samePeriodActor));
        assertEquals("23514", periodException.getSQLState());
        connection.rollback();

        AllocationFixture rateFixture = insertAllocationFixture(1, "ANY", "FEMALE");
        UUID sameRateActor = UUID.randomUUID();
        SQLException rateException = assertThrows(SQLException.class, () -> execute("""
                INSERT INTO accommodation_rates(
                  id,application_period_id,room_type_id,rate_version,finance_fee_catalogue_id,
                  transaction_currency_code,indicative_transaction_amount,base_currency_code,
                  indicative_base_amount,rating_status,effective_from,status,prepared_by_user_id,
                  approved_by_user_id,approved_at,approval_reason,created_at,updated_at,version)
                VALUES (?,?,?,2,?,'USD',500,'USD',500,'RATED',now()-interval '1 day','ACTIVE',?,?,now(),
                  'Approved',now(),now(),0)
                """, UUID.randomUUID(), rateFixture.periodId(), rateFixture.roomTypeId(), UUID.randomUUID(),
                sameRateActor, sameRateActor));
        assertEquals("23514", rateException.getSQLState());
        connection.rollback();

        AllocationFixture fixture = insertAllocationFixture(1, "ANY", "FEMALE");
        UUID sameAllocationActor = UUID.randomUUID();
        AllocationFixture finalFixture = fixture;
        SQLException allocationException = assertThrows(SQLException.class, () -> execute("""
                INSERT INTO room_allocations(
                  id,allocation_number,accommodation_application_id,room_id,accommodation_rate_id,
                  occupancy_starts_on,occupancy_ends_on,status,allocated_by_user_id,allocated_at,
                  approved_by_user_id,approved_at,approval_reason,created_at,updated_at,version)
                VALUES (?,?,?, ?,?,current_date+2,current_date+90,'ALLOCATED',?,now(),?,now(),
                  'Approved',now(),now(),0)
                """, UUID.randomUUID(), uniqueCode("ALL"), finalFixture.applicationId(), finalFixture.roomId(),
                finalFixture.rateId(), sameAllocationActor, sameAllocationActor));
        assertEquals("23514", allocationException.getSQLState());
    }

    @Test
    void preventsActivationOfAnUnratedZwgRate() throws SQLException {
        UUID periodId = insertApplicationPeriod("ALLOCATION_ACTIVE");
        UUID roomTypeId = insertRoomType();
        SQLException exception = assertThrows(SQLException.class, () -> execute("""
                INSERT INTO accommodation_rates(
                  id,application_period_id,room_type_id,rate_version,finance_fee_catalogue_id,
                  transaction_currency_code,indicative_transaction_amount,base_currency_code,
                  rating_status,effective_from,status,prepared_by_user_id,approved_by_user_id,
                  approved_at,approval_reason,created_at,updated_at,version)
                VALUES (?,?,?,1,?,'ZWG',7500,'USD','UNRATED',now()-interval '1 day','ACTIVE',?,?,now(),
                  'Approved after review',now(),now(),0)
                """, UUID.randomUUID(), periodId, roomTypeId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        assertEquals("23514", exception.getSQLState());
    }

    @Test
    void enforcesWaitlistPositionWithinApplicationPeriod() throws SQLException {
        UUID periodId = insertApplicationPeriod("APPLICATION_OPEN");
        UUID firstApplicationId = insertSubmittedApplication(periodId, UUID.randomUUID(), "FEMALE");
        UUID secondApplicationId = insertSubmittedApplication(periodId, UUID.randomUUID(), "FEMALE");
        updateApplicationStatus(firstApplicationId, "WAITLISTED");
        updateApplicationStatus(secondApplicationId, "WAITLISTED");
        insertWaitlistEntry(periodId, firstApplicationId, 1);
        SQLException exception = assertThrows(
                SQLException.class, () -> insertWaitlistEntry(periodId, secondApplicationId, 1));
        assertEquals("23505", exception.getSQLState());
    }

    @Test
    void rejectsGenderMismatchAndOverCapacityAllocation() throws SQLException {
        AllocationFixture genderFixture = insertAllocationFixture(1, "FEMALE", "MALE");
        SQLException genderException = assertThrows(
                SQLException.class, () -> insertProposedAllocation(genderFixture));
        assertEquals("P0001", genderException.getSQLState());
        connection.rollback();

        AllocationFixture capacityFixture = insertAllocationFixture(1, "ANY", "FEMALE");
        insertProposedAllocation(capacityFixture);
        UUID secondApplicationId = insertSubmittedApplication(
                reopenPeriod(capacityFixture.periodId()), UUID.randomUUID(), "MALE");
        updateApplicationStatus(secondApplicationId, "ELIGIBLE");
        activateAllocationPeriod(capacityFixture.periodId());
        AllocationFixture secondAllocation = capacityFixture.withApplicationId(secondApplicationId);
        SQLException capacityException = assertThrows(
                SQLException.class, () -> insertProposedAllocation(secondAllocation));
        assertEquals("P0001", capacityException.getSQLState());
    }

    @Test
    void permitsCheckInAfterApplicationBecomesAllocated() throws SQLException {
        AllocationFixture fixture = insertAllocationFixture(1, "ANY", "FEMALE");
        UUID allocationId = insertProposedAllocation(fixture);
        UUID approver = UUID.randomUUID();
        execute("""
                UPDATE room_allocations SET status='ALLOCATED',approved_by_user_id=?,approved_at=now(),
                  approval_reason='Capacity and eligibility verified',updated_at=now() WHERE id=?
                """, approver, allocationId);
        execute("UPDATE accommodation_applications SET status='ALLOCATED',updated_at=now() WHERE id=?",
                fixture.applicationId());
        execute("""
                UPDATE room_allocations SET status='CHECKED_IN',checked_in_by_user_id=?,checked_in_at=now(),
                  check_in_notes='Identity and room condition verified',updated_at=now() WHERE id=?
                """, UUID.randomUUID(), allocationId);
        assertCount(1, "SELECT count(*) FROM room_allocations WHERE id='" + allocationId + "' AND status='CHECKED_IN'");
    }

    @Test
    void keepsAllocationEventsImmutable() throws SQLException {
        AllocationFixture fixture = insertAllocationFixture(1, "ANY", "FEMALE");
        UUID allocationId = insertProposedAllocation(fixture);
        UUID eventId = UUID.randomUUID();
        execute("""
                INSERT INTO room_allocation_events(
                  id,room_allocation_id,new_status,event_type,reason,actor_user_id,occurred_at,
                  created_at,updated_at,version)
                VALUES (?,?,'PROPOSED','PROPOSED','Eligible application proposed for room',?,now(),now(),now(),0)
                """, eventId, allocationId, UUID.randomUUID());
        UUID eventToUpdateId = eventId;
        SQLException updateException = assertThrows(
                SQLException.class,
                () -> execute("UPDATE room_allocation_events SET reason='Changed evidence' WHERE id=?", eventToUpdateId));
        assertEquals("P0001", updateException.getSQLState());
        connection.rollback();

        fixture = insertAllocationFixture(1, "ANY", "FEMALE");
        allocationId = insertProposedAllocation(fixture);
        eventId = UUID.randomUUID();
        execute("""
                INSERT INTO room_allocation_events(
                  id,room_allocation_id,new_status,event_type,reason,actor_user_id,occurred_at,
                  created_at,updated_at,version)
                VALUES (?,?,'PROPOSED','PROPOSED','Eligible application proposed for room',?,now(),now(),now(),0)
                """, eventId, allocationId, UUID.randomUUID());
        UUID finalEventId = eventId;
        SQLException deleteException = assertThrows(
                SQLException.class, () -> execute("DELETE FROM room_allocation_events WHERE id=?", finalEventId));
        assertEquals("P0001", deleteException.getSQLState());
    }

    private AllocationFixture insertAllocationFixture(int capacity, String hallGender, String studentGender)
            throws SQLException {
        UUID periodId = insertApplicationPeriod("APPLICATION_OPEN");
        UUID roomTypeId = insertRoomType();
        UUID roomId = insertRoom(roomTypeId, capacity, hallGender);
        UUID applicationId = insertSubmittedApplication(periodId, UUID.randomUUID(), studentGender);
        updateApplicationStatus(applicationId, "ELIGIBLE");
        activateAllocationPeriod(periodId);
        UUID rateId = insertActiveUsdRate(periodId, roomTypeId);
        return new AllocationFixture(periodId, roomTypeId, roomId, applicationId, rateId);
    }

    private UUID insertApplicationPeriod(String status) throws SQLException {
        UUID periodId = UUID.randomUUID();
        UUID preparedBy = UUID.randomUUID();
        boolean draft = "DRAFT".equals(status);
        execute("""
                INSERT INTO accommodation_application_periods(
                  id,academic_period_id,academic_period_code,code,name,applications_open_at,applications_close_at,
                  occupancy_starts_on,occupancy_ends_on,allocation_cutoff_at,status,prepared_by_user_id,
                  approved_by_user_id,approved_at,approval_reason,created_at,updated_at,version)
                VALUES (?,?, '2027-S1',?,'Residence 2027',now()-interval '1 day',now()+interval '1 day',
                  current_date+2,current_date+90,now()+interval '2 days',?,?,?, ?,?,now(),now(),0)
                """, periodId, UUID.randomUUID(), uniqueCode("PER"), status, preparedBy,
                draft ? null : UUID.randomUUID(), draft ? null : java.time.OffsetDateTime.now(),
                draft ? null : "Approved for controlled operations");
        return periodId;
    }

    private UUID reopenPeriod(UUID periodId) throws SQLException {
        execute("UPDATE accommodation_application_periods SET status='APPLICATION_OPEN',updated_at=now() WHERE id=?", periodId);
        return periodId;
    }

    private void activateAllocationPeriod(UUID periodId) throws SQLException {
        execute("UPDATE accommodation_application_periods SET status='ALLOCATION_ACTIVE',updated_at=now() WHERE id=?", periodId);
    }

    private UUID insertRoomType() throws SQLException {
        UUID roomTypeId = UUID.randomUUID();
        execute("""
                INSERT INTO accommodation_room_types(
                  id,code,name,default_capacity,active,created_at,updated_at,version)
                VALUES (?,?,?,1,true,now(),now(),0)
                """, roomTypeId, uniqueCode("TYPE"), "Standard room");
        return roomTypeId;
    }

    private UUID insertRoom(UUID roomTypeId, int capacity, String hallGender) throws SQLException {
        UUID premiseId = UUID.randomUUID();
        execute("""
                INSERT INTO accommodation_premises(
                  id,code,name,address_line,active,created_at,updated_at,version)
                VALUES (?,?,?,'630 Churchill Avenue',true,now(),now(),0)
                """, premiseId, uniqueCode("PREM"), "Main residence estate");
        UUID hallId = UUID.randomUUID();
        execute("""
                INSERT INTO residence_halls(
                  id,premise_id,code,name,resident_gender_policy,active,created_at,updated_at,version)
                VALUES (?,?,?,?,?,true,now(),now(),0)
                """, hallId, premiseId, uniqueCode("HALL"), "Residence Hall", hallGender);
        UUID roomId = UUID.randomUUID();
        execute("""
                INSERT INTO accommodation_rooms(
                  id,residence_hall_id,room_type_id,code,capacity,condition_status,active,
                  created_at,updated_at,version)
                VALUES (?,?,?,?,?,'AVAILABLE',true,now(),now(),0)
                """, roomId, hallId, roomTypeId, uniqueCode("ROOM"), capacity);
        return roomId;
    }

    private UUID insertSubmittedApplication(UUID periodId, UUID studentId, String gender) throws SQLException {
        UUID applicationId = UUID.randomUUID();
        execute("""
                INSERT INTO accommodation_applications(
                  id,application_number,application_period_id,student_id,student_number,student_name,
                  primary_email,gender_code,country_code,programme_id,programme_code,programme_name,
                  programme_level,payment_state,priority_score,status,submitted_at,created_at,updated_at,version)
                VALUES (?,?,?, ?,?,?, ?,?,'ZWE',?,'BACC','Bachelor of Accounting',1,'PAID',0,
                  'SUBMITTED',now(),now(),now(),0)
                """, applicationId, uniqueCode("APP"), periodId, studentId, uniqueCode("STU"),
                "Accommodation Applicant", "applicant@example.test", gender, UUID.randomUUID());
        return applicationId;
    }

    private void updateApplicationStatus(UUID applicationId, String status) throws SQLException {
        execute("""
                UPDATE accommodation_applications SET status=?,evaluated_by_user_id=?,evaluated_at=now(),
                  evaluation_reason='Eligibility rules evaluated',updated_at=now() WHERE id=?
                """, status, UUID.randomUUID(), applicationId);
    }

    private void insertWaitlistEntry(UUID periodId, UUID applicationId, int position) throws SQLException {
        execute("""
                INSERT INTO accommodation_waitlist_entries(
                  id,accommodation_application_id,application_period_id,waitlist_position,priority_score,
                  status,entered_by_user_id,entered_at,created_at,updated_at,version)
                VALUES (?,?,?, ?,100,'ACTIVE',?,now(),now(),now(),0)
                """, UUID.randomUUID(), applicationId, periodId, position, UUID.randomUUID());
    }

    private UUID insertActiveUsdRate(UUID periodId, UUID roomTypeId) throws SQLException {
        UUID rateId = UUID.randomUUID();
        execute("""
                INSERT INTO accommodation_rates(
                  id,application_period_id,room_type_id,rate_version,finance_fee_catalogue_id,
                  transaction_currency_code,indicative_transaction_amount,base_currency_code,
                  indicative_base_amount,rating_status,effective_from,status,prepared_by_user_id,
                  approved_by_user_id,approved_at,approval_reason,created_at,updated_at,version)
                VALUES (?,?,?,1,?,'USD',500,'USD',500,'RATED',now()-interval '1 day','ACTIVE',?,?,now(),
                  'Rate and finance catalogue mapping approved',now(),now(),0)
                """, rateId, periodId, roomTypeId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        return rateId;
    }

    private UUID insertProposedAllocation(AllocationFixture fixture) throws SQLException {
        UUID allocationId = UUID.randomUUID();
        execute("""
                INSERT INTO room_allocations(
                  id,allocation_number,accommodation_application_id,room_id,accommodation_rate_id,
                  occupancy_starts_on,occupancy_ends_on,status,allocated_by_user_id,allocated_at,
                  created_at,updated_at,version)
                VALUES (?,?,?, ?,?,current_date+2,current_date+90,'PROPOSED',?,now(),now(),now(),0)
                """, allocationId, uniqueCode("ALL"), fixture.applicationId(), fixture.roomId(),
                fixture.rateId(), UUID.randomUUID());
        return allocationId;
    }

    private String uniqueCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void assertCount(int expected, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            assertEquals(expected, result.getInt(1));
        }
    }

    private void execute(String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }

    private record AllocationFixture(
            UUID periodId, UUID roomTypeId, UUID roomId, UUID applicationId, UUID rateId) {
        AllocationFixture withApplicationId(UUID replacementApplicationId) {
            return new AllocationFixture(periodId, roomTypeId, roomId, replacementApplicationId, rateId);
        }
    }
}
