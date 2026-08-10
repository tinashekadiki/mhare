package zw.ac.uz.emhare.dining.setup;

import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.dining.setup.DiningSetupContracts.*;

/** @author Tinashe K */
@Service
public class DiningSetupService {
    private final DiningHallRepository hallRepository; private final MealOptionRepository optionRepository;
    private final MealServiceTimeRepository timeRepository; private final DiningPlanRepository planRepository;
    private final DiningPlanMealRepository planMealRepository;
    private final DiningHallAssignmentRuleRepository assignmentRuleRepository;
    private final DiningAttendantAssignmentRepository attendantAssignmentRepository;
    private final Clock clock;
    public DiningSetupService(DiningHallRepository halls,MealOptionRepository options,MealServiceTimeRepository times,
            DiningPlanRepository plans,DiningPlanMealRepository planMeals,DiningHallAssignmentRuleRepository assignmentRules,
            DiningAttendantAssignmentRepository attendantAssignments,Clock clock) {
        hallRepository=halls;optionRepository=options;timeRepository=times;planRepository=plans;planMealRepository=planMeals;
        assignmentRuleRepository=assignmentRules;attendantAssignmentRepository=attendantAssignments;this.clock=clock;
    }
    @Transactional(readOnly=true) public SetupRegister register(){return new SetupRegister(
        hallRepository.findAllByDeletedAtIsNullOrderByCodeAsc().stream().map(this::view).toList(),
        optionRepository.findAllByDeletedAtIsNullOrderByCodeAsc().stream().map(this::view).toList(),
        timeRepository.findAllByDeletedAtIsNullOrderByDiningHallCodeAscDayOfWeekAscServiceOpensAtAsc().stream().map(this::view).toList(),
        planRepository.findAllByDeletedAtIsNullOrderByCodeAscPlanVersionDesc().stream().map(this::view).toList(),
        planMealRepository.findAllByDeletedAtIsNullOrderByDiningPlanCodeAscMealOptionCodeAsc().stream().map(this::view).toList(),
        assignmentRuleRepository.findAllByDeletedAtIsNullOrderByPriorityRankAscDiningHallCodeAsc().stream().map(this::view).toList(),
        attendantAssignmentRepository.findAllByDeletedAtIsNullOrderByDiningHallCodeAscStaffNumberAsc().stream().map(this::view).toList());}
    @Transactional public HallSummary createHall(CreateHall c){return view(hallRepository.saveAndFlush(new DiningHall(c.code(),c.name(),c.locationDescription(),c.serviceCapacity())));}
    @Transactional public HallSummary updateHall(UUID id,UpdateHall c){DiningHall e=hall(id);e.update(c.code(),c.name(),c.locationDescription(),c.serviceCapacity(),c.active(),c.expectedVersion());return view(hallRepository.saveAndFlush(e));}
    @Transactional public MealOptionSummary createOption(CreateMealOption c){return view(optionRepository.saveAndFlush(new MealOption(c.code(),c.name(),c.description(),c.mealCategory())));}
    @Transactional public MealOptionSummary updateOption(UUID id,UpdateMealOption c){MealOption e=option(id);e.update(c.code(),c.name(),c.description(),c.mealCategory(),c.active(),c.expectedVersion());return view(optionRepository.saveAndFlush(e));}
    @Transactional public ServiceTimeSummary createTime(CreateServiceTime c){return view(timeRepository.saveAndFlush(new MealServiceTime(hall(c.diningHallId()),option(c.mealOptionId()),c.dayOfWeek(),c.serviceOpensAt(),c.serviceClosesAt(),c.graceClosesAt())));}
    @Transactional public ServiceTimeSummary updateTime(UUID id,UpdateServiceTime c){MealServiceTime e=timeRepository.findById(id).orElseThrow(()->new IllegalArgumentException("Meal service time was not found."));e.update(hall(c.diningHallId()),option(c.mealOptionId()),c.dayOfWeek(),c.serviceOpensAt(),c.serviceClosesAt(),c.graceClosesAt(),c.active(),c.expectedVersion());return view(timeRepository.saveAndFlush(e));}
    @Transactional public PlanSummary createPlan(CreatePlan c,UUID actor){return view(planRepository.saveAndFlush(new DiningPlan(c.code(),c.planVersion(),c.name(),c.description(),c.financeFeeCatalogueId(),c.validFrom(),c.validUntil(),actor)));}
    @Transactional public PlanMealSummary addPlanMeal(UUID planId,AddPlanMeal c){DiningPlan plan=plan(planId);boolean[] days={c.monday(),c.tuesday(),c.wednesday(),c.thursday(),c.friday(),c.saturday(),c.sunday()};return view(planMealRepository.saveAndFlush(new DiningPlanMeal(plan,option(c.mealOptionId()),c.servingsPerService(),days)));}
    @Transactional public PlanSummary transitionPlan(UUID id,PlanTransition c,UUID actor){DiningPlan plan=plan(id);if(c.targetStatus()==DiningPlan.Status.ACTIVE&&planMealRepository.countByDiningPlanId(id)==0)throw new IllegalStateException("A dining plan must contain at least one meal before activation.");plan.transition(c.targetStatus(),actor,c.reason(),clock.instant(),c.expectedVersion());return view(planRepository.saveAndFlush(plan));}
    @Transactional public HallAssignmentRuleSummary createAssignmentRule(CreateHallAssignmentRule c){return view(assignmentRuleRepository.saveAndFlush(new DiningHallAssignmentRule(hall(c.diningHallId()),c.ruleDimension(),c.comparisonOperator(),c.comparisonValue(),c.priorityRank())));}
    @Transactional public HallAssignmentRuleSummary updateAssignmentRule(UUID id,UpdateHallAssignmentRule c){DiningHallAssignmentRule e=assignmentRuleRepository.findById(id).orElseThrow(()->new IllegalArgumentException("Dining hall assignment rule was not found."));e.update(hall(c.diningHallId()),c.ruleDimension(),c.comparisonOperator(),c.comparisonValue(),c.priorityRank(),c.active(),c.expectedVersion());return view(assignmentRuleRepository.saveAndFlush(e));}
    @Transactional public AttendantAssignmentSummary createAttendantAssignment(CreateAttendantAssignment c){return view(attendantAssignmentRepository.saveAndFlush(new DiningAttendantAssignment(hall(c.diningHallId()),c.staffId(),c.staffNumber(),c.staffName(),c.effectiveFrom(),c.effectiveUntil(),c.roleCode())));}
    @Transactional public AttendantAssignmentSummary updateAttendantAssignment(UUID id,UpdateAttendantAssignment c){DiningAttendantAssignment e=attendantAssignmentRepository.findById(id).orElseThrow(()->new IllegalArgumentException("Dining attendant assignment was not found."));e.update(hall(c.diningHallId()),c.staffId(),c.staffNumber(),c.staffName(),c.effectiveFrom(),c.effectiveUntil(),c.roleCode(),c.active(),c.expectedVersion());return view(attendantAssignmentRepository.saveAndFlush(e));}
    private DiningHall hall(UUID id){return hallRepository.findById(id).orElseThrow(()->new IllegalArgumentException("Dining hall was not found."));}
    private MealOption option(UUID id){return optionRepository.findById(id).orElseThrow(()->new IllegalArgumentException("Meal option was not found."));}
    private DiningPlan plan(UUID id){return planRepository.findById(id).orElseThrow(()->new IllegalArgumentException("Dining plan was not found."));}
    private HallSummary view(DiningHall e){return new HallSummary(e.getId(),e.getCode(),e.getName(),e.getLocationDescription(),e.getServiceCapacity(),e.isActive(),e.getVersion());}
    private MealOptionSummary view(MealOption e){return new MealOptionSummary(e.getId(),e.getCode(),e.getName(),e.getDescription(),e.getMealCategory(),e.isActive(),e.getVersion());}
    private ServiceTimeSummary view(MealServiceTime e){return new ServiceTimeSummary(e.getId(),e.getDiningHall().getId(),e.getDiningHall().getCode(),e.getMealOption().getId(),e.getMealOption().getCode(),e.getDayOfWeek(),e.getServiceOpensAt(),e.getServiceClosesAt(),e.getGraceClosesAt(),e.isActive(),e.getVersion());}
    private PlanSummary view(DiningPlan e){return new PlanSummary(e.getId(),e.getCode(),e.getPlanVersion(),e.getName(),e.getDescription(),e.getFinanceFeeCatalogueId(),e.getValidFrom(),e.getValidUntil(),e.getStatus(),e.getPreparedByUserId(),e.getApprovedByUserId(),e.getApprovedAt(),e.getApprovalReason(),e.getVersion());}
    private PlanMealSummary view(DiningPlanMeal e){List<Integer> days=new ArrayList<>();if(e.isMonday())days.add(1);if(e.isTuesday())days.add(2);if(e.isWednesday())days.add(3);if(e.isThursday())days.add(4);if(e.isFriday())days.add(5);if(e.isSaturday())days.add(6);if(e.isSunday())days.add(7);return new PlanMealSummary(e.getId(),e.getDiningPlan().getId(),e.getDiningPlan().getCode(),e.getMealOption().getId(),e.getMealOption().getCode(),e.getServingsPerService(),days,e.getVersion());}
    private HallAssignmentRuleSummary view(DiningHallAssignmentRule e){return new HallAssignmentRuleSummary(e.getId(),e.getDiningHall().getId(),e.getDiningHall().getCode(),e.getRuleDimension(),e.getComparisonOperator(),e.getComparisonValue(),e.getPriorityRank(),e.isActive(),e.getVersion());}
    private AttendantAssignmentSummary view(DiningAttendantAssignment e){return new AttendantAssignmentSummary(e.getId(),e.getDiningHall().getId(),e.getDiningHall().getCode(),e.getStaffId(),e.getStaffNumber(),e.getStaffName(),e.getEffectiveFrom(),e.getEffectiveUntil(),e.getRoleCode(),e.isActive(),e.getVersion());}
}
