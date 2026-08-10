package zw.ac.uz.emhare.examstimetabling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** @author Tinashe K */
@Testcontainers
class ExamTimetablingMigrationTest {
    @Container static final PostgreSQLContainer POSTGRESQL=new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("emhare_exams_timetabling").withUsername("emhare_service").withPassword("emhare_test_password");
    private Connection connection;
    @BeforeAll static void migrate(){Flyway.configure().dataSource(POSTGRESQL.getJdbcUrl(),POSTGRESQL.getUsername(),POSTGRESQL.getPassword()).locations("classpath:db/migration").load().migrate();}
    @BeforeEach void connect() throws SQLException {connection=DriverManager.getConnection(POSTGRESQL.getJdbcUrl(),POSTGRESQL.getUsername(),POSTGRESQL.getPassword());connection.setAutoCommit(false);}
    @AfterEach void rollback() throws SQLException {if(connection!=null){connection.rollback();connection.close();}}

    @Test void createsCompleteBusinessAndAuditSchema() throws SQLException {
        assertCount(16,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('exam_registration_imports','exam_candidate_modules','exam_venue_types','exam_venues','exam_venue_availability_windows','exam_sessions','exam_session_slots','module_exam_requirements','exam_timetable_generation_runs','exam_master_timetable_entries','exam_timetable_venue_allocations','exam_student_timetable_entries','exam_timetable_run_events','exam_attendance_sessions','exam_attendance_records','exam_incident_reports')");
        assertCount(16,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND (table_name LIKE 'exam%_aud' OR table_name='module_exam_requirements_aud')");
    }

    @Test void keepsConfirmedCandidateSourceEvidenceImmutable() throws SQLException {
        UUID importId=insertRegistrationImport();
        SQLException exception=assertThrows(SQLException.class,()->execute("UPDATE exam_registration_imports SET student_number='CHANGED' WHERE id=?",importId));
        assertEquals("P0001",exception.getSQLState());
    }

    @Test void rejectsVenueAllocationBeyondCertifiedCapacity() throws SQLException {
        TimetableFixture fixture=insertTimetableFixture();
        SQLException exception=assertThrows(SQLException.class,()->execute("""
            INSERT INTO exam_timetable_venue_allocations(id,master_timetable_entry_id,venue_id,allocated_capacity,created_at,updated_at,version)
            VALUES (?,?,?,31,now(),now(),0)
            """,UUID.randomUUID(),fixture.masterEntryId(),fixture.venueId()));
        assertEquals("P0001",exception.getSQLState());
    }

    @Test void rejectsWorkflowActorReuseAtDatabaseBoundary() throws SQLException {
        UUID sessionId=insertApprovedSession(); UUID actor=UUID.randomUUID();
        SQLException exception=assertThrows(SQLException.class,()->execute("""
            INSERT INTO exam_timetable_generation_runs(id,exam_session_id,run_number,status,candidate_count,module_count,
              timetable_entry_count,conflict_count,generation_policy,generated_by_user_id,generated_at,reviewed_by_user_id,
              reviewed_at,review_reason,created_at,updated_at,version)
            VALUES (?,?,?,'REVIEWED',1,1,1,0,'{}',?,now(),?,now(),'Reviewed',now(),now(),0)
            """,UUID.randomUUID(),sessionId,"RUN-"+UUID.randomUUID(),actor,actor));
        assertEquals("23514",exception.getSQLState());
    }

    @Test void rejectsAttendanceForAnUnpublishedTimetableAllocation() throws SQLException {
        TimetableFixture timetableFixture=insertTimetableFixture();
        UUID venueAllocationId=UUID.randomUUID();
        execute("INSERT INTO exam_timetable_venue_allocations(id,master_timetable_entry_id,venue_id,allocated_capacity,created_at,updated_at,version) VALUES (?,?,?,1,now(),now(),0)",venueAllocationId,timetableFixture.masterEntryId(),timetableFixture.venueId());
        SQLException exception=assertThrows(SQLException.class,()->execute("INSERT INTO exam_attendance_sessions(id,venue_allocation_id,status,expected_candidate_count,opened_by_user_id,opened_at,opening_reason,created_at,updated_at,version) VALUES (?,?,'OPEN',1,?,now(),'Open room',now(),now(),0)",UUID.randomUUID(),venueAllocationId,UUID.randomUUID()));
        assertEquals("P0001",exception.getSQLState());
    }

    @Test void rejectsAttendanceClosureWhileCandidatesRemainExpected() throws SQLException {
        InvigilationFixture fixture=insertPublishedInvigilationFixture();
        UUID attendanceSessionId=openAttendanceSession(fixture);
        execute("INSERT INTO exam_attendance_records(id,attendance_session_id,student_timetable_entry_id,attendance_status,created_at,updated_at,version) VALUES (?,?,?,'EXPECTED',now(),now(),0)",UUID.randomUUID(),attendanceSessionId,fixture.studentTimetableEntryId());
        SQLException exception=assertThrows(SQLException.class,()->execute("UPDATE exam_attendance_sessions SET status='CLOSED',closed_by_user_id=?,closed_at=now(),closure_reason='Room reconciled',updated_at=now() WHERE id=?",UUID.randomUUID(),attendanceSessionId));
        assertEquals("P0001",exception.getSQLState());
    }

    @Test void rejectsIncidentReviewByTheOriginalReporter() throws SQLException {
        InvigilationFixture fixture=insertPublishedInvigilationFixture();
        UUID attendanceSessionId=openAttendanceSession(fixture);UUID reporter=UUID.randomUUID();UUID incidentId=UUID.randomUUID();
        execute("INSERT INTO exam_incident_reports(id,attendance_session_id,student_timetable_entry_id,incident_number,incident_type,severity,description,occurred_at,status,reported_by_user_id,reported_at,created_at,updated_at,version) VALUES (?,?,?,?,'MEDICAL','HIGH','Candidate required medical assistance',now(),'REPORTED',?,now(),now(),now(),0)",incidentId,attendanceSessionId,fixture.studentTimetableEntryId(),"INC-"+UUID.randomUUID(),reporter);
        SQLException exception=assertThrows(SQLException.class,()->execute("UPDATE exam_incident_reports SET status='REVIEWED',reviewed_by_user_id=?,reviewed_at=now(),review_reason='Evidence reviewed',updated_at=now() WHERE id=?",reporter,incidentId));
        assertEquals("23514",exception.getSQLState());
    }

    private TimetableFixture insertTimetableFixture() throws SQLException {
        UUID periodId=UUID.randomUUID();UUID sessionId=UUID.randomUUID();
        execute("INSERT INTO exam_sessions(id,academic_period_id,academic_period_code,code,name,assessment_type,starts_on,ends_on,status,created_at,updated_at,version) VALUES (?,?,'2027-S1',?,'Final examinations','FINAL_EXAM',current_date,current_date+7,'DRAFT',now(),now(),0)",sessionId,periodId,"EX-"+UUID.randomUUID());
        UUID slotId=UUID.randomUUID();
        execute("INSERT INTO exam_session_slots(id,exam_session_id,code,starts_at,ends_at,created_at,updated_at,version) VALUES (?,?,'AM',now()+interval '1 day',now()+interval '1 day 3 hours',now(),now(),0)",slotId,sessionId);
        execute("UPDATE exam_sessions SET status='APPROVED',approved_by_user_id=?,approved_at=now(),approval_reason='Approved',updated_at=now() WHERE id=?",UUID.randomUUID(),sessionId);
        UUID venueTypeId=UUID.randomUUID();execute("INSERT INTO exam_venue_types(id,code,name,active,created_at,updated_at,version) VALUES (?,'HALL','Hall',true,now(),now(),0)",venueTypeId);
        UUID venueId=UUID.randomUUID();execute("INSERT INTO exam_venues(id,venue_type_id,code,name,campus_name,examination_capacity,active,created_at,updated_at,version) VALUES (?,?,'H1','Hall 1','Main',30,true,now(),now(),0)",venueId,venueTypeId);
        execute("INSERT INTO exam_venue_availability_windows(id,venue_id,available_from,available_until,created_at,updated_at,version) VALUES (?,?,now(),now()+interval '3 days',now(),now(),0)",UUID.randomUUID(),venueId);
        UUID moduleId=UUID.randomUUID();UUID requirementId=UUID.randomUUID();execute("INSERT INTO module_exam_requirements(id,academic_period_id,module_id,module_code,module_name,requirement_version,duration_minutes,reading_time_minutes,status,approved_by_user_id,approved_at,approval_reason,created_at,updated_at,version) VALUES (?,?,?,'ACC101','Accounting',1,180,0,'APPROVED',?,now(),'Approved',now(),now(),0)",requirementId,periodId,moduleId,UUID.randomUUID());
        UUID runId=UUID.randomUUID();execute("INSERT INTO exam_timetable_generation_runs(id,exam_session_id,run_number,status,candidate_count,module_count,timetable_entry_count,conflict_count,generation_policy,generated_by_user_id,generated_at,created_at,updated_at,version) VALUES (?,?,?,'GENERATED',31,1,1,0,'{}',?,now(),now(),now(),0)",runId,sessionId,"RUN-"+UUID.randomUUID(),UUID.randomUUID());
        UUID entryId=UUID.randomUUID();execute("INSERT INTO exam_master_timetable_entries(id,generation_run_id,exam_session_slot_id,module_exam_requirement_id,module_id,module_code,module_name,candidate_count,scheduled_starts_at,scheduled_ends_at,created_at,updated_at,version) VALUES (?,?,?,?,?,'ACC101','Accounting',31,now()+interval '1 day',now()+interval '1 day 3 hours',now(),now(),0)",entryId,runId,slotId,requirementId,moduleId);
        return new TimetableFixture(entryId,venueId);
    }
    private InvigilationFixture insertPublishedInvigilationFixture() throws SQLException {
        String suffix=UUID.randomUUID().toString().substring(0,8);UUID periodId=UUID.randomUUID();UUID sessionId=UUID.randomUUID();
        execute("INSERT INTO exam_sessions(id,academic_period_id,academic_period_code,code,name,assessment_type,starts_on,ends_on,status,created_at,updated_at,version) VALUES (?,?,'2027-S1',?,'Final examinations','FINAL_EXAM',current_date,current_date+7,'DRAFT',now(),now(),0)",sessionId,periodId,"EX-"+suffix);
        UUID slotId=UUID.randomUUID();
        execute("INSERT INTO exam_session_slots(id,exam_session_id,code,starts_at,ends_at,created_at,updated_at,version) VALUES (?,?,?,now()+interval '1 day',now()+interval '1 day 3 hours',now(),now(),0)",slotId,sessionId,"AM-"+suffix);
        execute("UPDATE exam_sessions SET status='APPROVED',approved_by_user_id=?,approved_at=now(),approval_reason='Approved',updated_at=now() WHERE id=?",UUID.randomUUID(),sessionId);
        UUID venueTypeId=UUID.randomUUID();execute("INSERT INTO exam_venue_types(id,code,name,active,created_at,updated_at,version) VALUES (?,?,?,true,now(),now(),0)",venueTypeId,"H-"+suffix,"Hall "+suffix);
        UUID venueId=UUID.randomUUID();execute("INSERT INTO exam_venues(id,venue_type_id,code,name,campus_name,examination_capacity,active,created_at,updated_at,version) VALUES (?,?,?,?, 'Main',1,true,now(),now(),0)",venueId,venueTypeId,"V-"+suffix,"Venue "+suffix);
        execute("INSERT INTO exam_venue_availability_windows(id,venue_id,available_from,available_until,created_at,updated_at,version) VALUES (?,?,now(),now()+interval '3 days',now(),now(),0)",UUID.randomUUID(),venueId);
        UUID moduleId=UUID.randomUUID();UUID requirementId=UUID.randomUUID();
        execute("INSERT INTO module_exam_requirements(id,academic_period_id,module_id,module_code,module_name,requirement_version,duration_minutes,reading_time_minutes,status,approved_by_user_id,approved_at,approval_reason,created_at,updated_at,version) VALUES (?,?,?,?,?,1,180,0,'APPROVED',?,now(),'Approved',now(),now(),0)",requirementId,periodId,moduleId,"MOD-"+suffix,"Module "+suffix,UUID.randomUUID());
        UUID generator=UUID.randomUUID();UUID reviewer=UUID.randomUUID();UUID approver=UUID.randomUUID();UUID publisher=UUID.randomUUID();UUID runId=UUID.randomUUID();
        execute("INSERT INTO exam_timetable_generation_runs(id,exam_session_id,run_number,status,candidate_count,module_count,timetable_entry_count,conflict_count,generation_policy,generated_by_user_id,generated_at,reviewed_by_user_id,reviewed_at,review_reason,approved_by_user_id,approved_at,approval_reason,published_by_user_id,published_at,publication_reason,created_at,updated_at,version) VALUES (?,?,?,'PUBLISHED',1,1,1,0,'{}',?,now(),?,now(),'Reviewed',?,now(),'Approved',?,now(),'Published',now(),now(),0)",runId,sessionId,"RUN-"+suffix,generator,reviewer,approver,publisher);
        UUID masterEntryId=UUID.randomUUID();execute("INSERT INTO exam_master_timetable_entries(id,generation_run_id,exam_session_slot_id,module_exam_requirement_id,module_id,module_code,module_name,candidate_count,scheduled_starts_at,scheduled_ends_at,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,1,now()+interval '1 day',now()+interval '1 day 3 hours',now(),now(),0)",masterEntryId,runId,slotId,requirementId,moduleId,"MOD-"+suffix,"Module "+suffix);
        UUID venueAllocationId=UUID.randomUUID();execute("INSERT INTO exam_timetable_venue_allocations(id,master_timetable_entry_id,venue_id,allocated_capacity,created_at,updated_at,version) VALUES (?,?,?,1,now(),now(),0)",venueAllocationId,masterEntryId,venueId);
        UUID registrationImportId=insertRegistrationImport(periodId,suffix);UUID candidateModuleId=UUID.randomUUID();
        execute("INSERT INTO exam_candidate_modules(id,registration_import_id,registration_module_id,curriculum_module_id,module_id,module_code,module_name,eligibility_status,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,'ELIGIBLE',now(),now(),0)",candidateModuleId,registrationImportId,UUID.randomUUID(),UUID.randomUUID(),moduleId,"MOD-"+suffix,"Module "+suffix);
        UUID studentTimetableEntryId=UUID.randomUUID();
        execute("INSERT INTO exam_student_timetable_entries(id,generation_run_id,master_timetable_entry_id,venue_allocation_id,registration_import_id,candidate_module_id,student_id,student_number,module_id,module_code,scheduled_starts_at,scheduled_ends_at,seat_number,attendance_status,created_at,updated_at,version) SELECT ?,?,?,?,?,?,student_id,student_number,?, ?,now()+interval '1 day',now()+interval '1 day 3 hours',1,'EXPECTED',now(),now(),0 FROM exam_registration_imports WHERE id=?",studentTimetableEntryId,runId,masterEntryId,venueAllocationId,registrationImportId,candidateModuleId,moduleId,"MOD-"+suffix,registrationImportId);
        return new InvigilationFixture(venueAllocationId,studentTimetableEntryId);
    }
    private UUID openAttendanceSession(InvigilationFixture fixture) throws SQLException {UUID id=UUID.randomUUID();execute("INSERT INTO exam_attendance_sessions(id,venue_allocation_id,status,expected_candidate_count,opened_by_user_id,opened_at,opening_reason,created_at,updated_at,version) VALUES (?,?,'OPEN',1,?,now(),'Invigilator opened the room',now(),now(),0)",id,fixture.venueAllocationId(),UUID.randomUUID());return id;}
    private UUID insertRegistrationImport() throws SQLException {UUID id=UUID.randomUUID();execute("INSERT INTO exam_registration_imports(id,source_event_id,registration_session_id,student_id,student_number,programme_enrolment_id,programme_id,programme_version_id,academic_period_id,academic_period_code,academic_period_name,academic_period_starts_on,academic_period_ends_on,imported_at,created_at,updated_at,version) VALUES (?,?,?,?,'STU001',?,?,?,?, '2027-S1','Semester 1',current_date,current_date+90,now(),now(),now(),0)",id,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());return id;}
    private UUID insertRegistrationImport(UUID periodId,String suffix) throws SQLException {UUID id=UUID.randomUUID();execute("INSERT INTO exam_registration_imports(id,source_event_id,registration_session_id,student_id,student_number,programme_enrolment_id,programme_id,programme_version_id,academic_period_id,academic_period_code,academic_period_name,academic_period_starts_on,academic_period_ends_on,imported_at,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,?,?,'2027-S1','Semester 1',current_date,current_date+90,now(),now(),now(),0)",id,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"STU-"+suffix,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),periodId);return id;}
    private UUID insertApprovedSession() throws SQLException{return insertApprovedSession(UUID.randomUUID());}
    private UUID insertApprovedSession(UUID periodId) throws SQLException {UUID id=UUID.randomUUID();execute("INSERT INTO exam_sessions(id,academic_period_id,academic_period_code,code,name,assessment_type,starts_on,ends_on,status,approved_by_user_id,approved_at,approval_reason,created_at,updated_at,version) VALUES (?,?,'2027-S1',?,'Final examinations','FINAL_EXAM',current_date,current_date+7,'APPROVED',?,now(),'Approved',now(),now(),0)",id,periodId,"EX-"+UUID.randomUUID(),UUID.randomUUID());return id;}
    private void assertCount(int expected,String sql) throws SQLException {try(Statement statement=connection.createStatement();ResultSet result=statement.executeQuery(sql)){result.next();assertEquals(expected,result.getInt(1));}}
    private void execute(String sql,Object... values) throws SQLException {try(PreparedStatement statement=connection.prepareStatement(sql)){for(int index=0;index<values.length;index++)statement.setObject(index+1,values[index]);statement.executeUpdate();}}
    private record TimetableFixture(UUID masterEntryId,UUID venueId) {}
    private record InvigilationFixture(UUID venueAllocationId,UUID studentTimetableEntryId) {}
}
