package zw.ac.uz.emhare.dining.setup;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.dining.setup.DiningSetupContracts.*;

/** @author Tinashe K */
@RestController @RequestMapping("/api/dining/setup")
@PreAuthorize("hasAnyAuthority('ROLE_system-admin','ROLE_dining-officer')")
public class DiningSetupController {
    private final DiningSetupService service; private final EmhareCurrentUserResolver users;
    public DiningSetupController(DiningSetupService service,EmhareCurrentUserResolver users){this.service=service;this.users=users;}
    @GetMapping public SetupRegister register(){return service.register();}
    @PostMapping("/halls") public HallSummary createHall(@Valid @RequestBody CreateHall c){return service.createHall(c);}
    @PutMapping("/halls/{id}") public HallSummary updateHall(@PathVariable UUID id,@Valid @RequestBody UpdateHall c){return service.updateHall(id,c);}
    @PostMapping("/meal-options") public MealOptionSummary createOption(@Valid @RequestBody CreateMealOption c){return service.createOption(c);}
    @PutMapping("/meal-options/{id}") public MealOptionSummary updateOption(@PathVariable UUID id,@Valid @RequestBody UpdateMealOption c){return service.updateOption(id,c);}
    @PostMapping("/service-times") public ServiceTimeSummary createTime(@Valid @RequestBody CreateServiceTime c){return service.createTime(c);}
    @PutMapping("/service-times/{id}") public ServiceTimeSummary updateTime(@PathVariable UUID id,@Valid @RequestBody UpdateServiceTime c){return service.updateTime(id,c);}
    @PostMapping("/plans") public PlanSummary createPlan(Authentication a,@Valid @RequestBody CreatePlan c){return service.createPlan(c,actor(a));}
    @PostMapping("/plans/{id}/meals") public PlanMealSummary addPlanMeal(@PathVariable UUID id,@Valid @RequestBody AddPlanMeal c){return service.addPlanMeal(id,c);}
    @PostMapping("/plans/{id}/transition") public PlanSummary transitionPlan(Authentication a,@PathVariable UUID id,@Valid @RequestBody PlanTransition c){return service.transitionPlan(id,c,actor(a));}
    @PostMapping("/hall-assignment-rules") public HallAssignmentRuleSummary createAssignmentRule(@Valid @RequestBody CreateHallAssignmentRule c){return service.createAssignmentRule(c);}
    @PutMapping("/hall-assignment-rules/{id}") public HallAssignmentRuleSummary updateAssignmentRule(@PathVariable UUID id,@Valid @RequestBody UpdateHallAssignmentRule c){return service.updateAssignmentRule(id,c);}
    @PostMapping("/attendant-assignments") public AttendantAssignmentSummary createAttendantAssignment(@Valid @RequestBody CreateAttendantAssignment c){return service.createAttendantAssignment(c);}
    @PutMapping("/attendant-assignments/{id}") public AttendantAssignmentSummary updateAttendantAssignment(@PathVariable UUID id,@Valid @RequestBody UpdateAttendantAssignment c){return service.updateAttendantAssignment(id,c);}
    private UUID actor(Authentication a){return users.fromAuthentication(a).orElseThrow(()->new IllegalStateException("Authenticated user is required.")).auditUserId();}
}
