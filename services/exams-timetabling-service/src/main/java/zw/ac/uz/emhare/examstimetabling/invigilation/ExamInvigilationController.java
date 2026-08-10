package zw.ac.uz.emhare.examstimetabling.invigilation;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.examstimetabling.invigilation.ExamInvigilationContracts.*;

/** @author Tinashe K */
@RestController @RequestMapping("/api/exams/invigilation")
@PreAuthorize("hasAnyAuthority('ROLE_system-admin','ROLE_academic-admin','ROLE_exams-officer','ROLE_exam-invigilator')")
public class ExamInvigilationController {
    private final GovernedExamInvigilationService invigilationService;
    private final EmhareCurrentUserResolver currentUserResolver;
    public ExamInvigilationController(GovernedExamInvigilationService invigilationService,EmhareCurrentUserResolver currentUserResolver) {
        this.invigilationService=invigilationService;this.currentUserResolver=currentUserResolver;
    }
    @GetMapping public InvigilationWorkspace workspace(){return invigilationService.workspace();}
    @PostMapping("/venue-allocations/{venueAllocationId}/attendance-session")
    public AttendanceSessionSummary open(Authentication authentication,@PathVariable UUID venueAllocationId,
            @Valid @RequestBody OpenAttendanceSession command){return invigilationService.open(venueAllocationId,command,actor(authentication));}
    @PutMapping("/attendance-records/{attendanceRecordId}")
    public AttendanceSessionSummary record(Authentication authentication,@PathVariable UUID attendanceRecordId,
            @Valid @RequestBody RecordAttendance command){return invigilationService.recordAttendance(attendanceRecordId,command,actor(authentication));}
    @PostMapping("/attendance-sessions/{attendanceSessionId}/close")
    public AttendanceSessionSummary close(Authentication authentication,@PathVariable UUID attendanceSessionId,
            @Valid @RequestBody CloseAttendanceSession command){return invigilationService.close(attendanceSessionId,command,actor(authentication));}
    @PostMapping("/attendance-sessions/{attendanceSessionId}/incidents")
    public AttendanceSessionSummary reportIncident(Authentication authentication,@PathVariable UUID attendanceSessionId,
            @Valid @RequestBody ReportIncident command){return invigilationService.reportIncident(attendanceSessionId,command,actor(authentication));}
    @PostMapping("/incidents/{incidentId}/{action:review|resolve}")
    public AttendanceSessionSummary moveIncident(Authentication authentication,@PathVariable UUID incidentId,
            @PathVariable String action,@Valid @RequestBody IncidentWorkflowDecision command){return invigilationService.moveIncident(incidentId,action,command,actor(authentication));}
    private UUID actor(Authentication authentication){return currentUserResolver.fromAuthentication(authentication)
            .orElseThrow(()->new IllegalStateException("Authenticated user is required.")).auditUserId();}
}
