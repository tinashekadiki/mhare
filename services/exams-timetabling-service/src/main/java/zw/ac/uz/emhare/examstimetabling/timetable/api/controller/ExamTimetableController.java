package zw.ac.uz.emhare.examstimetabling.timetable.api.controller;

import zw.ac.uz.emhare.examstimetabling.timetable.*;
import zw.ac.uz.emhare.examstimetabling.timetable.api.model.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.examstimetabling.timetable.api.model.ExamTimetableApiModels.*;

/** @author Tinashe K */
@RestController @RequestMapping("/api/timetabling")
@PreAuthorize("hasAnyAuthority('ROLE_system-admin','ROLE_academic-admin','ROLE_exams-officer')")
public class ExamTimetableController {
    private final GovernedExamTimetableService service;private final EmhareCurrentUserResolver currentUserResolver;
    public ExamTimetableController(GovernedExamTimetableService service,EmhareCurrentUserResolver currentUserResolver){this.service=service;this.currentUserResolver=currentUserResolver;}
    @GetMapping("/runs") public List<RunSummary> runs(){return service.runs();}
    @PostMapping("/runs") public RunSummary generate(Authentication authentication,@Valid @RequestBody GenerateTimetable request){return service.generate(request,actor(authentication));}
    @PostMapping("/runs/{id}/{action:review|approve|publish|reject}") public RunSummary move(Authentication authentication,@PathVariable UUID id,@PathVariable String action,@Valid @RequestBody WorkflowDecision request){return service.move(id,action,request,actor(authentication));}
    @GetMapping("/students/{studentId}") public List<StudentEntrySummary> studentTimetable(@PathVariable UUID studentId){return service.publishedStudentTimetable(studentId);}
    private UUID actor(Authentication authentication){return currentUserResolver.fromAuthentication(authentication).orElseThrow(()->new IllegalStateException("Authenticated user is required.")).auditUserId();}
}
