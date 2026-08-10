package zw.ac.uz.emhare.dining.setup;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

/** @author Tinashe K */
public final class DiningSetupContracts {
    private DiningSetupContracts() {}
    public record CreateHall(@NotBlank String code,@NotBlank String name,@NotBlank String locationDescription,@Min(1) int serviceCapacity) {}
    public record UpdateHall(@NotBlank String code,@NotBlank String name,@NotBlank String locationDescription,@Min(1) int serviceCapacity,boolean active,@Min(0) long expectedVersion) {}
    public record CreateMealOption(@NotBlank String code,@NotBlank String name,String description,@NotNull MealOption.Category mealCategory) {}
    public record UpdateMealOption(@NotBlank String code,@NotBlank String name,String description,@NotNull MealOption.Category mealCategory,boolean active,@Min(0) long expectedVersion) {}
    public record CreateServiceTime(@NotNull UUID diningHallId,@NotNull UUID mealOptionId,@Min(1) @Max(7) int dayOfWeek,@NotNull LocalTime serviceOpensAt,@NotNull LocalTime serviceClosesAt,@NotNull LocalTime graceClosesAt) {}
    public record UpdateServiceTime(@NotNull UUID diningHallId,@NotNull UUID mealOptionId,@Min(1) @Max(7) int dayOfWeek,@NotNull LocalTime serviceOpensAt,@NotNull LocalTime serviceClosesAt,@NotNull LocalTime graceClosesAt,boolean active,@Min(0) long expectedVersion) {}
    public record CreatePlan(@NotBlank String code,@Min(1) int planVersion,@NotBlank String name,String description,UUID financeFeeCatalogueId,@NotNull LocalDate validFrom,LocalDate validUntil) {}
    public record AddPlanMeal(@NotNull UUID mealOptionId,@Min(1) int servingsPerService,boolean monday,boolean tuesday,boolean wednesday,boolean thursday,boolean friday,boolean saturday,boolean sunday) {}
    public record CreateHallAssignmentRule(@NotNull UUID diningHallId,@NotNull DiningHallAssignmentRule.Dimension ruleDimension,@NotNull DiningHallAssignmentRule.Operator comparisonOperator,@NotBlank @Size(max=200) String comparisonValue,@Min(1) int priorityRank) {}
    public record UpdateHallAssignmentRule(@NotNull UUID diningHallId,@NotNull DiningHallAssignmentRule.Dimension ruleDimension,@NotNull DiningHallAssignmentRule.Operator comparisonOperator,@NotBlank @Size(max=200) String comparisonValue,@Min(1) int priorityRank,boolean active,@Min(0) long expectedVersion) {}
    public record CreateAttendantAssignment(@NotNull UUID diningHallId,@NotNull UUID staffId,@NotBlank String staffNumber,@NotBlank String staffName,@NotNull LocalDate effectiveFrom,LocalDate effectiveUntil,@NotNull DiningAttendantAssignment.Role roleCode) {}
    public record UpdateAttendantAssignment(@NotNull UUID diningHallId,@NotNull UUID staffId,@NotBlank String staffNumber,@NotBlank String staffName,@NotNull LocalDate effectiveFrom,LocalDate effectiveUntil,@NotNull DiningAttendantAssignment.Role roleCode,boolean active,@Min(0) long expectedVersion) {}
    public record PlanTransition(@NotNull DiningPlan.Status targetStatus,@NotBlank @Size(max=1000) String reason,@Min(0) long expectedVersion) {}
    public record HallSummary(UUID id,String code,String name,String locationDescription,int serviceCapacity,boolean active,long version) {}
    public record MealOptionSummary(UUID id,String code,String name,String description,MealOption.Category mealCategory,boolean active,long version) {}
    public record ServiceTimeSummary(UUID id,UUID diningHallId,String diningHallCode,UUID mealOptionId,String mealOptionCode,int dayOfWeek,LocalTime serviceOpensAt,LocalTime serviceClosesAt,LocalTime graceClosesAt,boolean active,long version) {}
    public record PlanSummary(UUID id,String code,int planVersion,String name,String description,UUID financeFeeCatalogueId,LocalDate validFrom,LocalDate validUntil,DiningPlan.Status status,UUID preparedByUserId,UUID approvedByUserId,Instant approvedAt,String approvalReason,long version) {}
    public record PlanMealSummary(UUID id,UUID diningPlanId,String diningPlanCode,UUID mealOptionId,String mealOptionCode,int servingsPerService,List<Integer> serviceDays,long version) {}
    public record HallAssignmentRuleSummary(UUID id,UUID diningHallId,String diningHallCode,DiningHallAssignmentRule.Dimension ruleDimension,DiningHallAssignmentRule.Operator comparisonOperator,String comparisonValue,int priorityRank,boolean active,long version) {}
    public record AttendantAssignmentSummary(UUID id,UUID diningHallId,String diningHallCode,UUID staffId,String staffNumber,String staffName,LocalDate effectiveFrom,LocalDate effectiveUntil,DiningAttendantAssignment.Role roleCode,boolean active,long version) {}
    public record SetupRegister(List<HallSummary> diningHalls,List<MealOptionSummary> mealOptions,List<ServiceTimeSummary> serviceTimes,List<PlanSummary> diningPlans,List<PlanMealSummary> planMeals,List<HallAssignmentRuleSummary> hallAssignmentRules,List<AttendantAssignmentSummary> attendantAssignments) {}
}
