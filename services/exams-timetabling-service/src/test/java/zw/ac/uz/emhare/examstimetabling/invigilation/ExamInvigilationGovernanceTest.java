package zw.ac.uz.emhare.examstimetabling.invigilation;

import zw.ac.uz.emhare.examstimetabling.invigilation.domain.model.ExamAttendanceRecord;
import zw.ac.uz.emhare.examstimetabling.invigilation.domain.model.ExamAttendanceSession;
import zw.ac.uz.emhare.examstimetabling.invigilation.domain.model.ExamIncidentReport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.examstimetabling.timetable.domain.model.ExamStudentTimetableEntry;
import zw.ac.uz.emhare.examstimetabling.timetable.domain.model.ExamTimetableVenueAllocation;

/** @author Tinashe K */
class ExamInvigilationGovernanceTest {
    @Test void requiresCompleteAttendanceEvidenceBeforeClosure() {
        ExamAttendanceSession session=new ExamAttendanceSession(mock(ExamTimetableVenueAllocation.class),2,
                UUID.randomUUID(),Instant.now(),"Room opened against the published roster");
        ExamAttendanceRecord present=new ExamAttendanceRecord(session,mock(ExamStudentTimetableEntry.class));
        ExamAttendanceRecord absent=new ExamAttendanceRecord(session,mock(ExamStudentTimetableEntry.class));
        present.record(ExamAttendanceRecord.Status.PRESENT,null,UUID.randomUUID(),Instant.now(),0);
        assertThrows(IllegalArgumentException.class,()->absent.record(
                ExamAttendanceRecord.Status.ABSENT," ",UUID.randomUUID(),Instant.now(),0));
        assertThrows(IllegalStateException.class,()->session.close(
                UUID.randomUUID(),Instant.now(),"Incomplete register",0,1,0,0));
        absent.record(ExamAttendanceRecord.Status.ABSENT,"Candidate did not report and no authorised excuse was recorded.",
                UUID.randomUUID(),Instant.now(),0);
        session.close(UUID.randomUUID(),Instant.now(),"All seats and candidate outcomes reconciled.",0,1,1,0);
        assertEquals(ExamAttendanceSession.Status.CLOSED,session.getStatus());
        assertThrows(IllegalStateException.class,()->present.record(
                ExamAttendanceRecord.Status.ABSENT,"Late change",UUID.randomUUID(),Instant.now(),0));
    }

    @Test void requiresIndependentIncidentReviewerAndResolver() {
        UUID reporter=UUID.randomUUID();UUID reviewer=UUID.randomUUID();UUID resolver=UUID.randomUUID();
        ExamAttendanceSession session=new ExamAttendanceSession(mock(ExamTimetableVenueAllocation.class),1,
                UUID.randomUUID(),Instant.now(),"Room opened");
        ExamIncidentReport incident=new ExamIncidentReport(session,null,"INC-TEST-1",ExamIncidentReport.Type.DISRUPTION,
                ExamIncidentReport.Severity.HIGH,"Power interruption affected the examination room.",Instant.now(),reporter,Instant.now());
        assertThrows(IllegalStateException.class,()->incident.review(reporter,Instant.now(),"Self review",0));
        incident.review(reviewer,Instant.now(),"Operational evidence independently reviewed.",0);
        assertThrows(IllegalStateException.class,()->incident.resolve(reviewer,Instant.now(),"Self resolution",0));
        incident.resolve(resolver,Instant.now(),"Approved compensating time and recorded the examination board referral.",0);
        assertEquals(ExamIncidentReport.Status.RESOLVED,incident.getStatus());
    }
}
