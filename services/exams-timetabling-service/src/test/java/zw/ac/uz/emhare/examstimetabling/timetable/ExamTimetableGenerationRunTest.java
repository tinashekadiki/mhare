package zw.ac.uz.emhare.examstimetabling.timetable;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.examstimetabling.setup.ExamSession;

/** @author Tinashe K */
class ExamTimetableGenerationRunTest {
    @Test void requiresFourIndependentWorkflowActors(){
        UUID generator=UUID.randomUUID();UUID reviewer=UUID.randomUUID();UUID approver=UUID.randomUUID();UUID publisher=UUID.randomUUID();
        ExamSession session=new ExamSession(UUID.randomUUID(),"2027-S1","FINAL","Final examinations",ExamSession.AssessmentType.FINAL_EXAM,LocalDate.now(),LocalDate.now().plusDays(7));
        session.approve(UUID.randomUUID(),"Approved session",Instant.now(),0);
        ExamTimetableGenerationRun run=new ExamTimetableGenerationRun(session,"RUN-1",10,1,1,0,Map.of("algorithm","test"),generator,Instant.now());
        assertThrows(IllegalStateException.class,()->run.review(generator,"Self review",Instant.now(),0));
        run.review(reviewer,"Independent review",Instant.now(),0);
        assertThrows(IllegalStateException.class,()->run.approve(reviewer,"Self approval",Instant.now(),0));
        run.approve(approver,"Independent approval",Instant.now(),0);
        assertThrows(IllegalStateException.class,()->run.publish(approver,"Self publication",Instant.now(),0));
        run.publish(publisher,"Controlled publication",Instant.now(),0);
        assertEquals(ExamTimetableGenerationRun.Status.PUBLISHED,run.getStatus());
    }
}
