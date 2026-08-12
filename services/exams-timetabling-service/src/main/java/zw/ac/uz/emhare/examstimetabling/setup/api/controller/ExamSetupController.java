package zw.ac.uz.emhare.examstimetabling.setup.api.controller;

import zw.ac.uz.emhare.examstimetabling.setup.*;
import zw.ac.uz.emhare.examstimetabling.setup.api.model.*;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.examstimetabling.setup.api.model.ExamSetupApiModels.*;

/** @author Tinashe K */
@RestController @RequestMapping("/api/exams/setup")
@PreAuthorize("hasAnyAuthority('ROLE_system-admin','ROLE_academic-admin','ROLE_exams-officer')")
public class ExamSetupController {
    private final ExamSetupService service; private final EmhareCurrentUserResolver currentUserResolver;
    public ExamSetupController(ExamSetupService service,EmhareCurrentUserResolver currentUserResolver){this.service=service;this.currentUserResolver=currentUserResolver;}
    @GetMapping public SetupRegister register(){return service.register();}
    @PostMapping("/venue-types") public VenueTypeSummary createVenueType(@Valid @RequestBody CreateVenueType request){return service.createVenueType(request);}
    @PostMapping("/venues") public VenueSummary createVenue(@Valid @RequestBody CreateVenue request){return service.createVenue(request);}
    @PostMapping("/venues/{id}/availability") public VenueSummary addAvailability(@PathVariable UUID id,@Valid @RequestBody AddAvailability request){return service.addAvailability(id,request);}
    @PostMapping("/sessions") public SessionSummary createSession(@Valid @RequestBody CreateSession request){return service.createSession(request);}
    @PostMapping("/sessions/{id}/slots") public SessionSummary addSlot(@PathVariable UUID id,@Valid @RequestBody CreateSlot request){return service.addSlot(id,request);}
    @PostMapping("/sessions/{id}/approve") public SessionSummary approveSession(Authentication authentication,@PathVariable UUID id,@Valid @RequestBody WorkflowDecision request){return service.approveSession(id,request,actor(authentication));}
    @PostMapping("/requirements") public RequirementSummary createRequirement(@Valid @RequestBody CreateRequirement request){return service.createRequirement(request);}
    @PostMapping("/requirements/{id}/approve") public RequirementSummary approveRequirement(Authentication authentication,@PathVariable UUID id,@Valid @RequestBody WorkflowDecision request){return service.approveRequirement(id,request,actor(authentication));}
    private UUID actor(Authentication authentication){return currentUserResolver.fromAuthentication(authentication).orElseThrow(()->new IllegalStateException("Authenticated user is required.")).auditUserId();}
}
