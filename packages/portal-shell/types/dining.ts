export type DiningPlanStatus = "DRAFT" | "ACTIVE" | "RETIRED";
export type DiningAssignmentStatus = "DRAFT" | "ACTIVE" | "SUSPENDED" | "ENDED" | "CANCELLED";
export type DiningSessionStatus = "PLANNED" | "OPEN" | "CLOSED" | "RECONCILED" | "CANCELLED";
export type DiningDietaryStatus = "ACTIVE" | "RESOLVED" | "EXPIRED";

export interface DiningHallSummary {
  id: string;
  code: string;
  name: string;
  locationDescription: string;
  serviceCapacity: number;
  active: boolean;
  version: number;
}

export interface MealOptionSummary {
  id: string;
  code: string;
  name: string;
  description: string | null;
  mealCategory: "BREAKFAST" | "LUNCH" | "DINNER" | "OTHER";
  active: boolean;
  version: number;
}

export interface MealServiceTimeSummary {
  id: string;
  diningHallId: string;
  diningHallCode: string;
  mealOptionId: string;
  mealOptionCode: string;
  dayOfWeek: number;
  serviceOpensAt: string;
  serviceClosesAt: string;
  graceClosesAt: string;
  active: boolean;
  version: number;
}

export interface DiningPlanSummary {
  id: string;
  code: string;
  planVersion: number;
  name: string;
  description: string | null;
  financeFeeCatalogueId: string | null;
  validFrom: string;
  validUntil: string | null;
  status: DiningPlanStatus;
  preparedByUserId: string;
  approvedByUserId: string | null;
  approvedAt: string | null;
  approvalReason: string | null;
  version: number;
}

export interface DiningPlanMealSummary {
  id: string;
  diningPlanId: string;
  diningPlanCode: string;
  mealOptionId: string;
  mealOptionCode: string;
  servingsPerService: number;
  serviceDays: number[];
  version: number;
}

export interface DiningHallAssignmentRuleSummary {
  id: string;
  diningHallId: string;
  diningHallCode: string;
  ruleDimension: "SURNAME_PREFIX" | "RESIDENCE_HALL" | "PROGRAMME" | "STUDENT_GROUP";
  comparisonOperator: "EQUALS" | "STARTS_WITH" | "IN";
  comparisonValue: string;
  priorityRank: number;
  active: boolean;
  version: number;
}

export interface DiningAttendantAssignmentSummary {
  id: string;
  diningHallId: string;
  diningHallCode: string;
  staffId: string;
  staffNumber: string;
  staffName: string;
  effectiveFrom: string;
  effectiveUntil: string | null;
  roleCode: "ATTENDANT" | "SUPERVISOR" | "MANAGER";
  active: boolean;
  version: number;
}

export interface DiningSetupRegister {
  diningHalls: DiningHallSummary[];
  mealOptions: MealOptionSummary[];
  serviceTimes: MealServiceTimeSummary[];
  diningPlans: DiningPlanSummary[];
  planMeals: DiningPlanMealSummary[];
  hallAssignmentRules: DiningHallAssignmentRuleSummary[];
  attendantAssignments: DiningAttendantAssignmentSummary[];
}

export interface StudentDiningAssignmentSummary {
  id: string;
  assignmentNumber: string;
  studentId: string;
  studentNumber: string;
  studentName: string;
  academicPeriodId: string;
  academicPeriodCode: string;
  programmeCode: string;
  studentGroupCode: string | null;
  diningHallId: string;
  diningHallCode: string;
  diningPlanId: string;
  diningPlanCode: string;
  accommodationAllocationId: string | null;
  effectiveFrom: string;
  effectiveUntil: string;
  status: DiningAssignmentStatus;
  preparedByUserId: string;
  approvedByUserId: string | null;
  approvedAt: string | null;
  approvalReason: string | null;
  endedByUserId: string | null;
  endedAt: string | null;
  endReason: string | null;
  billingStatus: "NOT_REQUESTED" | "PENDING" | "ACCEPTED" | "FAILED";
  version: number;
}

export interface StudentDietaryRequirementSummary {
  id: string;
  studentId: string;
  studentNumber: string;
  requirementCode: string;
  description: string;
  severity: "INFORMATION" | "IMPORTANT" | "CRITICAL";
  clinicalDocumentId: string | null;
  effectiveFrom: string;
  effectiveUntil: string | null;
  status: DiningDietaryStatus;
  recordedByUserId: string;
  resolvedByUserId: string | null;
  resolvedAt: string | null;
  resolutionReason: string | null;
  version: number;
}

export interface MealServiceSessionSummary {
  id: string;
  sessionNumber: string;
  diningHallId: string;
  diningHallCode: string;
  mealOptionId: string;
  mealOptionCode: string;
  serviceDate: string;
  scheduledOpensAt: string;
  scheduledClosesAt: string;
  status: DiningSessionStatus;
  preparedByUserId: string;
  openedByUserId: string | null;
  openedAt: string | null;
  closedByUserId: string | null;
  closedAt: string | null;
  reconciledByUserId: string | null;
  reconciledAt: string | null;
  reconciliationReason: string | null;
  expectedServings: number | null;
  countedServings: number | null;
  netAdmitted: number;
  version: number;
}

export interface MealAttendanceSummary {
  id: string;
  eventNumber: string;
  sessionId: string;
  sessionNumber: string;
  assignmentId: string | null;
  studentId: string;
  studentNumber: string;
  studentName: string;
  outcome: "ADMITTED" | "DENIED";
  denialReasonCode: string | null;
  denialReason: string | null;
  capturedByUserId: string;
  capturedAt: string;
  captureChannel: "ONLINE" | "OFFLINE_SYNC" | "MANUAL";
  deviceId: string | null;
  idempotencyKey: string;
  reversed: boolean;
}

export interface MealAttendanceReversalSummary {
  id: string;
  attendanceEventId: string;
  eventNumber: string;
  reasonCode: string;
  reason: string;
  reversedByUserId: string;
  reversedAt: string;
}

export interface DiningWorkflowEventSummary {
  id: string;
  aggregateType: "ASSIGNMENT" | "DIETARY_REQUIREMENT" | "MEAL_SESSION";
  aggregateId: string;
  previousState: string | null;
  newState: string;
  eventType: string;
  reason: string;
  actorUserId: string;
  occurredAt: string;
}

export interface DiningAttendanceStatisticSummary {
  dimension: "DINING_HALL" | "MEAL_OPTION" | "ACADEMIC_PERIOD" | "PROGRAMME" | "STUDENT_GROUP";
  groupCode: string;
  admitted: number;
  denied: number;
  reversed: number;
  netAdmitted: number;
}

export interface DiningOperationsRegister {
  assignments: StudentDiningAssignmentSummary[];
  dietaryRequirements: StudentDietaryRequirementSummary[];
  sessions: MealServiceSessionSummary[];
  attendanceEvents: MealAttendanceSummary[];
  reversals: MealAttendanceReversalSummary[];
  workflowEvents: DiningWorkflowEventSummary[];
  attendanceStatistics: DiningAttendanceStatisticSummary[];
}
