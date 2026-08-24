// Author: Tinashe K

import type { AcademicSetupOverview } from "../types/academic";
import type { AdmissionsPipelineReport } from "../types/admissions";
import type {
  AssessmentOfferingSummary,
  MarkAmendmentSummary,
  ProgressionDecisionSummary,
  ResultBatchSummary,
} from "../types/assessment";
import type {
  AccommodationOperationsRegister,
  AccommodationSetupRegister,
} from "../types/accommodation";
import type { DiningOperationsRegister, DiningSetupRegister } from "../types/dining";
import type { OfficialDocumentSummary, UploadedDocumentSummary } from "../types/documents";
import type {
  ExamInvigilationWorkspace,
  ExamSetupRegister,
  ExamTimetableRunSummary,
} from "../types/exams";
import type {
  FinanceBillingRegister,
  FinanceCollectionsRegister,
  FinanceFeeCatalogueRegister,
  FinanceStudentAccountSummary,
} from "../types/finance";
import type { NotificationRegister } from "../types/notifications";
import type { RegistrationSummary, StudentConversionSummary } from "../types/student-records";

export type OperationalDashboardKey =
  | "core"
  | "academic-setup"
  | "admissions"
  | "finance"
  | "student-records"
  | "assessment-results"
  | "exams-timetabling"
  | "accommodation"
  | "dining"
  | "documents"
  | "notifications";

export type OperationalDashboardTone =
  "neutral" | "primary" | "success" | "warning" | "info" | "error";

export type OperationalDashboardModule = {
  key: OperationalDashboardKey;
  label: string;
  shortLabel: string;
  description: string;
  icon: string;
  dashboardPath: string;
  workspacePath: string;
};

export type OperationalDashboardMetric = {
  label: string;
  value: number;
  hint: string;
  icon: string;
  tone: OperationalDashboardTone;
};

export type OperationalDashboardAction = {
  label: string;
  value: number;
  description: string;
  to: string;
  icon: string;
  tone: OperationalDashboardTone;
};

export type OperationalDashboardDistribution = {
  label: string;
  value: number;
};

export type OperationalDashboardLink = {
  label: string;
  description: string;
  to: string;
  icon: string;
};

export type OperationalDashboardSnapshot = OperationalDashboardModule & {
  available: boolean;
  generatedAt: string;
  scopeNote: string;
  metrics: OperationalDashboardMetric[];
  summaryMetrics?: OperationalDashboardDistribution[];
  trend?: OperationalDashboardDistribution[];
  actions: OperationalDashboardAction[];
  distribution: OperationalDashboardDistribution[];
  links: OperationalDashboardLink[];
  errorMessage?: string;
};

export type OperationalDashboardApi = {
  request(path: string): Promise<unknown>;
};

type CoreStatistics = {
  userCount: number;
  roleCount: number;
  permissionCount: number;
  lookupSetCount: number;
};

type CoreOperationalReport = {
  generatedAt: string;
  inventory: CoreStatistics;
  loginSessionsLast24Hours: number;
  auditEventsLast24Hours: number;
};

type CoreWorkflowTask = {
  status: "OPEN" | "CLAIMED" | "COMPLETED" | "CANCELLED";
};

export const operationalDashboardModules: OperationalDashboardModule[] = [
  moduleDefinition(
    "core",
    "Core and Identity",
    "Core",
    "Identity, access, reference data and workflow control.",
    "i-lucide-shield-check",
    "/operations/dashboard/core",
    "/operations/core",
  ),
  moduleDefinition(
    "academic-setup",
    "Academic Setup",
    "Academic",
    "Academic structures, calendars, programmes, Modules and curriculum readiness.",
    "i-lucide-school",
    "/operations/dashboard/academic-setup",
    "/operations/academic-structure",
  ),
  moduleDefinition(
    "admissions",
    "Admissions",
    "Admissions",
    "Applicant demand, payment clearance and rolling admissions workload.",
    "i-lucide-file-check-2",
    "/operations/admissions-dashboard",
    "/operations/admissions",
  ),
  moduleDefinition(
    "finance",
    "Finance",
    "Finance",
    "Pricing, billing, cash reconciliation and student-account evidence.",
    "i-lucide-receipt-text",
    "/operations/dashboard/finance",
    "/operations/finance-fees",
  ),
  moduleDefinition(
    "student-records",
    "Student Records and Registration",
    "Student Records",
    "Accepted-offer conversion and governed Module registration.",
    "i-lucide-user-round-check",
    "/operations/dashboard/student-records",
    "/operations/student-conversions",
  ),
  moduleDefinition(
    "assessment-results",
    "Teaching, Assessment and Results",
    "Assessment",
    "Assessment setup, mark amendments, result release and progression.",
    "i-lucide-clipboard-pen-line",
    "/operations/dashboard/assessment-results",
    "/operations/assessment-schemes",
  ),
  moduleDefinition(
    "exams-timetabling",
    "Exams and Timetabling",
    "Exams",
    "Exam readiness, timetable governance and room operations.",
    "i-lucide-calendar-clock",
    "/operations/dashboard/exams-timetabling",
    "/operations/exam-setup",
  ),
  moduleDefinition(
    "accommodation",
    "Accommodation",
    "Accommodation",
    "Inventory, applications, allocations, occupancy and billing handoff.",
    "i-lucide-building-2",
    "/operations/dashboard/accommodation",
    "/operations/accommodation",
  ),
  moduleDefinition(
    "dining",
    "Dining",
    "Dining",
    "Dining plans, student assignments, service sessions and meal access.",
    "i-lucide-utensils",
    "/operations/dashboard/dining",
    "/operations/dining",
  ),
  moduleDefinition(
    "documents",
    "Documents and Reporting",
    "Documents",
    "Uploaded evidence verification and stored official records.",
    "i-lucide-files",
    "/operations/dashboard/documents",
    "/operations/documents",
  ),
  moduleDefinition(
    "notifications",
    "Notifications",
    "Notifications",
    "Templates, delivery queues, provider evidence and retry workload.",
    "i-lucide-send",
    "/operations/dashboard/notifications",
    "/operations/notifications",
  ),
];

export function isOperationalDashboardKey(value: string): value is OperationalDashboardKey {
  return operationalDashboardModules.some((module) => module.key === value);
}

export async function loadOperationalDashboard(
  api: OperationalDashboardApi,
  key: OperationalDashboardKey,
): Promise<OperationalDashboardSnapshot> {
  switch (key) {
    case "core":
      return loadCoreDashboard(api);
    case "academic-setup":
      return loadAcademicDashboard(api);
    case "admissions":
      return loadAdmissionsDashboard(api);
    case "finance":
      return loadFinanceDashboard(api);
    case "student-records":
      return loadStudentRecordsDashboard(api);
    case "assessment-results":
      return loadAssessmentDashboard(api);
    case "exams-timetabling":
      return loadExamsDashboard(api);
    case "accommodation":
      return loadAccommodationDashboard(api);
    case "dining":
      return loadDiningDashboard(api);
    case "documents":
      return loadDocumentsDashboard(api);
    case "notifications":
      return loadNotificationsDashboard(api);
  }
}

export async function loadOperationsOverview(
  api: OperationalDashboardApi,
  keys: OperationalDashboardKey[] = operationalDashboardModules.map((module) => module.key),
): Promise<OperationalDashboardSnapshot[]> {
  const results = await Promise.allSettled(keys.map((key) => loadOperationalDashboard(api, key)));
  return results.map((result, index) => {
    if (result.status === "fulfilled") return result.value;
    const key = keys[index]!;
    const definition = getModule(key);
    return {
      ...definition,
      available: false,
      generatedAt: "",
      scopeNote:
        "No metrics are shown when this service cannot be reached or the current role cannot read it.",
      metrics: [],
      actions: [],
      distribution: [],
      links: workspaceLinks(definition),
      errorMessage: errorMessage(result.reason),
    };
  });
}

async function loadCoreDashboard(
  api: OperationalDashboardApi,
): Promise<OperationalDashboardSnapshot> {
  const [report, workflowTasks] = await Promise.all([
    request<CoreOperationalReport>(api, "/api/core/reports/overview"),
    request<CoreWorkflowTask[]>(api, "/api/core/workflows/tasks"),
  ]);
  const module = getModule("core");
  return snapshot(
    module,
    report.generatedAt,
    "Inventory counts are current; activity measures cover the last 24 hours.",
    [
      metric(
        "Users",
        report.inventory.userCount,
        "Provisioned local user records",
        "i-lucide-users",
        "primary",
      ),
      metric(
        "Roles",
        report.inventory.roleCount,
        `${report.inventory.permissionCount} governed permissions`,
        "i-lucide-shield-check",
        "info",
      ),
      metric(
        "Login sessions",
        report.loginSessionsLast24Hours,
        "Recorded in the last 24 hours",
        "i-lucide-log-in",
        "success",
      ),
      metric(
        "Audit events",
        report.auditEventsLast24Hours,
        `${report.inventory.lookupSetCount} managed lookup sets`,
        "i-lucide-history",
        "neutral",
      ),
    ],
    [
      action(
        "Open workflow tasks",
        countStatus(workflowTasks, "OPEN"),
        "Unclaimed governed work awaiting an authorised operator.",
        "/operations/core?tab=workflow",
        "i-lucide-inbox",
        "warning",
      ),
      action(
        "Claimed workflow tasks",
        countStatus(workflowTasks, "CLAIMED"),
        "Work already owned but not yet completed.",
        "/operations/core?tab=workflow",
        "i-lucide-user-check",
        "info",
      ),
    ],
    statusDistribution(workflowTasks, ["OPEN", "CLAIMED", "COMPLETED", "CANCELLED"]),
    [
      link(
        "Core identity workspace",
        "Manage the institution profile, identities, access, reference data and audit.",
        "/operations/core",
        "i-lucide-shield-check",
      ),
    ],
  );
}

async function loadAcademicDashboard(
  api: OperationalDashboardApi,
): Promise<OperationalDashboardSnapshot> {
  const overview = await request<AcademicSetupOverview>(api, "/api/academic/overview");
  const module = getModule("academic-setup");
  const draftCalendarRecords = [
    ...overview.academicYears,
    ...overview.academicPeriods,
    ...overview.intakes,
  ].filter((record) => record.status === "DRAFT").length;
  const dashboard = snapshot(
    module,
    now(),
    "Counts describe active governed catalogue records and the current intake calendar.",
    [
      metric(
        "Academic units",
        overview.academicUnits.filter((item) => item.status === "ACTIVE").length,
        "Active nodes in the governed hierarchy",
        "i-lucide-network",
        "primary",
      ),
      metric(
        "Active Programmes",
        overview.programmes.filter((item) => item.status === "ACTIVE").length,
        "Programme catalogue records",
        "i-lucide-graduation-cap",
        "info",
      ),
      metric(
        "Active Modules",
        overview.modules.filter((item) => item.status === "ACTIVE").length,
        "Reusable Module catalogue records",
        "i-lucide-book-open",
        "success",
      ),
      metric(
        "Open intakes",
        overview.intakes.filter((item) => item.status === "OPEN").length,
        `${overview.academicPeriods.filter((item) => item.status === "OPEN").length} open academic periods`,
        "i-lucide-calendar-check-2",
        "neutral",
      ),
    ],
    [
      action(
        "Draft calendar records",
        draftCalendarRecords,
        "Years, periods or intakes still awaiting controlled opening.",
        "/operations/academic-calendar",
        "i-lucide-calendar-range",
        "warning",
      ),
      action(
        "Inactive catalogue records",
        overview.programmes.filter((item) => item.status !== "ACTIVE").length +
          overview.modules.filter((item) => item.status !== "ACTIVE").length,
        "Programme and Module records not currently active.",
        "/operations/programmes",
        "i-lucide-archive",
        "neutral",
      ),
    ],
    overview.programmes.map((item) => item.status).reduce(distributionReducer, []),
    [
      link(
        "Academic structure",
        "Maintain hierarchy levels and academic units.",
        "/operations/academic-structure",
        "i-lucide-network",
      ),
      link(
        "Academic calendar",
        "Manage years, periods and admissions intakes.",
        "/operations/academic-calendar",
        "i-lucide-calendar-range",
      ),
      link(
        "Programmes",
        "Maintain Programme catalogue and reference data.",
        "/operations/programmes",
        "i-lucide-graduation-cap",
      ),
      link(
        "Modules",
        "Maintain the reusable Module catalogue.",
        "/operations/modules",
        "i-lucide-book-open",
      ),
      link(
        "Curriculum",
        "Version Programme curriculum and Module placement.",
        "/operations/curriculum",
        "i-lucide-list-tree",
      ),
    ],
  );
  return {
    ...dashboard,
    summaryMetrics: overview.academicUnitTypes
      .filter((unitType) => unitType.status === "ACTIVE")
      .sort((left, right) => left.levelOrder - right.levelOrder)
      .map((unitType) => ({
        label: unitType.name,
        value: overview.academicUnits.filter(
          (unit) => unit.status === "ACTIVE" && unit.academicUnitTypeId === unitType.id,
        ).length,
      })),
  };
}

async function loadAdmissionsDashboard(
  api: OperationalDashboardApi,
): Promise<OperationalDashboardSnapshot> {
  const report = await request<AdmissionsPipelineReport>(
    api,
    "/api/admissions/reports/pipeline-summary",
  );
  const status = new Map(report.statusCounts.map((item) => [item.code, item.count]));
  const payments = new Map(report.paymentCounts.map((item) => [item.code, item.count]));
  const module = getModule("admissions");
  return snapshot(
    module,
    report.generatedAt,
    "Applicants are deduplicated; ranked choices remain preference rows.",
    [
      metric(
        "Applications",
        report.totalApplications,
        "Distinct application records",
        "i-lucide-files",
        "primary",
      ),
      metric(
        "Applicants",
        report.totalApplicants,
        "Distinct people across applications",
        "i-lucide-users",
        "info",
      ),
      metric(
        "Payment cleared",
        value(payments, "PAID") + value(payments, "WAIVED"),
        "Confirmed payment or authorised waiver",
        "i-lucide-badge-check",
        "success",
      ),
      metric(
        "Ranked choices",
        report.rankedChoiceCounts.reduce((total, item) => total + item.choices, 0),
        "Choice rows, not applicant totals",
        "i-lucide-list-ordered",
        "neutral",
      ),
    ],
    [
      action(
        "Payment attention",
        value(payments, "PENDING"),
        "Fee-required applications without confirmed clearance.",
        "/operations/admissions?stage=VERIFICATION",
        "i-lucide-credit-card",
        "warning",
      ),
      action(
        "Verification",
        values(status, "SUBMITTED", "PAYMENT_PENDING", "INCOMPLETE"),
        "Submitted cases still completing Admissions clearance.",
        "/operations/admissions?stage=VERIFICATION",
        "i-lucide-shield-check",
        "primary",
      ),
      action(
        "Academic review",
        value(status, "UNDER_ACADEMIC_REVIEW"),
        "Eligible choices awaiting academic recommendation or decision.",
        "/operations/admissions?stage=ACADEMIC_REVIEW",
        "i-lucide-building-2",
        "warning",
      ),
      action(
        "Accepted for conversion",
        value(status, "ACCEPTED"),
        "Accepted offers ready for Student Records handoff.",
        "/operations/student-conversions",
        "i-lucide-user-round-plus",
        "success",
      ),
    ],
    report.statusCounts.map((item) => ({ label: title(item.code), value: item.count })),
    [
      link(
        "Admissions overview",
        "Use filters, demand evidence and the complete workflow workload.",
        "/operations/admissions-dashboard",
        "i-lucide-layout-dashboard",
      ),
      link(
        "Admissions workflow",
        "Open the consolidated rolling Admissions queue.",
        "/operations/admissions",
        "i-lucide-workflow",
      ),
      link(
        "Admissions reports",
        "Open governed analysis and export workspaces.",
        "/operations/admissions-reports",
        "i-lucide-chart-no-axes-combined",
      ),
    ],
  );
}

async function loadFinanceDashboard(
  api: OperationalDashboardApi,
): Promise<OperationalDashboardSnapshot> {
  const [fees, billing, collections, accounts] = await Promise.all([
    request<FinanceFeeCatalogueRegister>(api, "/api/finance/fee-catalogues"),
    request<FinanceBillingRegister>(api, "/api/finance/billing"),
    request<FinanceCollectionsRegister>(api, "/api/finance/collections"),
    request<FinanceStudentAccountSummary[]>(api, "/api/finance/collections/accounts"),
  ]);
  const pricingAttention = fees.catalogues
    .flatMap((item) => item.rules)
    .filter((item) => item.status === "DRAFT" || item.status === "PENDING_RATE").length;
  const activePayments = collections.payments.filter((item) => !item.reversed);
  const module = getModule("finance");
  return snapshot(
    module,
    now(),
    "All monetary evidence is reported in its governed state; unrated transactions are never treated as USD-rated.",
    [
      metric(
        "Active fee definitions",
        fees.catalogues.filter((item) => item.status === "ACTIVE").length,
        "Governed catalogue entries available to pricing",
        "i-lucide-badge-dollar-sign",
        "primary",
      ),
      metric(
        "Posted invoices",
        billing.invoices.length,
        "Immutable receivable documents",
        "i-lucide-receipt-text",
        "info",
      ),
      metric(
        "Student accounts",
        accounts.length,
        "Permanent student-number accounts",
        "i-lucide-notebook-tabs",
        "success",
      ),
      metric(
        "Reconciled payments",
        activePayments.filter((item) => item.reconciliationStatus === "RECONCILED").length,
        "Non-reversed cash with reconciliation evidence",
        "i-lucide-landmark",
        "neutral",
      ),
    ],
    [
      action(
        "Pricing attention",
        pricingAttention,
        "Draft or unrated effective prices awaiting Finance action.",
        "/operations/finance-fees",
        "i-lucide-badge-dollar-sign",
        "warning",
      ),
      action(
        "Billing approval",
        billing.billingEvents.filter((item) => item.status === "PENDING_APPROVAL").length,
        "Authoritative charge events awaiting independent approval.",
        "/operations/finance-billing",
        "i-lucide-stamp",
        "warning",
      ),
      action(
        "Cash reconciliation",
        activePayments.filter((item) => item.reconciliationStatus === "PENDING").length,
        "Captured payments awaiting reconciliation.",
        "/operations/finance-collections",
        "i-lucide-landmark",
        "info",
      ),
      action(
        "Unrated payments",
        activePayments.filter((item) => item.ratingStatus === "UNRATED").length,
        "Transactions held until an effective exchange rate exists.",
        "/operations/finance-collections",
        "i-lucide-circle-dollar-sign",
        "error",
      ),
      action(
        "Draft credit notes",
        collections.creditNotes.filter((item) => item.status === "DRAFT").length,
        "Prepared corrections awaiting independent posting.",
        "/operations/finance-corrections",
        "i-lucide-git-compare-arrows",
        "neutral",
      ),
    ],
    distributionFrom(activePayments, (item) => item.reconciliationStatus),
    financeLinks(),
  );
}

async function loadStudentRecordsDashboard(
  api: OperationalDashboardApi,
): Promise<OperationalDashboardSnapshot> {
  const [conversions, registrations] = await Promise.all([
    request<StudentConversionSummary[]>(api, "/api/student-records/conversions"),
    request<RegistrationSummary[]>(api, "/api/student-records/registrations"),
  ]);
  const module = getModule("student-records");
  const dashboard = snapshot(
    module,
    now(),
    "Conversion and registration are separate governed lifecycles; totals are not merged.",
    [
      metric(
        "Converted students",
        conversions.filter((item) => item.status === "COMPLETED").length,
        "Completed accepted-offer conversions",
        "i-lucide-user-round-check",
        "success",
      ),
      metric(
        "Registration records",
        registrations.length,
        "Distinct student-period registration records",
        "i-lucide-clipboard-list",
        "primary",
      ),
      metric(
        "Confirmed registrations",
        registrations.filter((item) => item.status === "CONFIRMED").length,
        "Confirmed downstream roster evidence",
        "i-lucide-badge-check",
        "info",
      ),
      metric(
        "Registered Modules",
        registrations
          .filter((item) => item.status === "CONFIRMED")
          .reduce((total, item) => total + item.modules.length, 0),
        "Module rows on confirmed registrations",
        "i-lucide-book-open-check",
        "neutral",
      ),
    ],
    [
      action(
        "Failed conversions",
        conversions.filter((item) => item.status === "FAILED").length,
        "Provisioning failures requiring controlled retry.",
        "/operations/student-conversions",
        "i-lucide-triangle-alert",
        "error",
      ),
      action(
        "Conversions in progress",
        conversions.filter((item) => item.status === "PROVISIONING").length,
        "Cross-service provisioning still underway.",
        "/operations/student-conversions",
        "i-lucide-loader-circle",
        "info",
      ),
      action(
        "Academic approval",
        registrations.filter((item) => item.status === "SUBMITTED").length,
        "Submitted Module registrations awaiting academic review.",
        "/operations/student-registrations",
        "i-lucide-user-check",
        "warning",
      ),
      action(
        "Registration confirmation",
        registrations.filter((item) => item.status === "ACADEMIC_APPROVED").length,
        "Approved registrations awaiting Registry confirmation.",
        "/operations/student-registrations",
        "i-lucide-stamp",
        "primary",
      ),
    ],
    distributionFrom(registrations, (item) => item.status),
    [
      link(
        "Student conversions",
        "Complete accepted-offer provisioning and recover failures.",
        "/operations/student-conversions",
        "i-lucide-user-round-check",
      ),
      link(
        "Student registration",
        "Build and govern curriculum-backed Module registrations.",
        "/operations/student-registrations",
        "i-lucide-clipboard-list",
      ),
    ],
  );
  return { ...dashboard, trend: monthlyRegistrationTrend(registrations) };
}

async function loadAssessmentDashboard(
  api: OperationalDashboardApi,
): Promise<OperationalDashboardSnapshot> {
  const [offerings, amendments, batches, decisions] = await Promise.all([
    request<AssessmentOfferingSummary[]>(api, "/api/assessment-results/offerings"),
    request<MarkAmendmentSummary[]>(api, "/api/assessment-results/amendments"),
    request<ResultBatchSummary[]>(api, "/api/results/batches"),
    request<ProgressionDecisionSummary[]>(api, "/api/results/progression/decisions"),
  ]);
  const approvedSchemes = offerings.reduce(
    (total, offering) =>
      total + offering.schemes.filter((item) => item.status === "APPROVED").length,
    0,
  );
  const module = getModule("assessment-results");
  return snapshot(
    module,
    now(),
    "Published result counts come only from governed published batches.",
    [
      metric(
        "Active offerings",
        offerings.filter((item) => item.status === "ACTIVE").length,
        "Module offerings open for controlled mark capture",
        "i-lucide-clipboard-pen-line",
        "primary",
      ),
      metric(
        "Approved schemes",
        approvedSchemes,
        "Approved component and weighting versions",
        "i-lucide-list-checks",
        "info",
      ),
      metric(
        "Published results",
        batches
          .filter((item) => item.status === "PUBLISHED")
          .reduce((total, item) => total + item.resultCount, 0),
        "Immutable result rows in published batches",
        "i-lucide-stamp",
        "success",
      ),
      metric(
        "Published progression",
        decisions.filter((item) => item.status === "PUBLISHED").length,
        "Released student academic-standing decisions",
        "i-lucide-route",
        "neutral",
      ),
    ],
    [
      action(
        "Mark amendments",
        amendments.filter((item) => item.status === "REQUESTED").length,
        "Submitted mark changes awaiting independent decision.",
        "/operations/assessment-amendments",
        "i-lucide-history",
        "warning",
      ),
      action(
        "Result moderation",
        batches.filter((item) => item.status === "SUBMITTED").length,
        "Submitted result batches awaiting moderation.",
        "/operations/result-batches",
        "i-lucide-search-check",
        "warning",
      ),
      action(
        "Result approval",
        batches.filter((item) => item.status === "MODERATED").length,
        "Moderated batches awaiting independent approval.",
        "/operations/result-batches",
        "i-lucide-badge-check",
        "primary",
      ),
      action(
        "Result publication",
        batches.filter((item) => item.status === "APPROVED").length,
        "Approved batches awaiting controlled publication.",
        "/operations/result-batches",
        "i-lucide-send",
        "info",
      ),
      action(
        "Progression release",
        decisions.filter((item) => ["CALCULATED", "REVIEWED", "APPROVED"].includes(item.status))
          .length,
        "Calculated decisions not yet published to students.",
        "/operations/progression-decisions",
        "i-lucide-route",
        "neutral",
      ),
    ],
    distributionFrom(batches, (item) => item.status),
    assessmentLinks(),
  );
}

async function loadExamsDashboard(
  api: OperationalDashboardApi,
): Promise<OperationalDashboardSnapshot> {
  const [setup, runs, invigilation] = await Promise.all([
    request<ExamSetupRegister>(api, "/api/exams/setup"),
    request<ExamTimetableRunSummary[]>(api, "/api/timetabling/runs"),
    request<ExamInvigilationWorkspace>(api, "/api/exams/invigilation"),
  ]);
  const attendanceSessions = invigilation.venueOperations
    .map((item) => item.attendanceSession)
    .filter((item) => item != null);
  const incidents = attendanceSessions.flatMap((item) => item.incidents);
  const module = getModule("exams-timetabling");
  return snapshot(
    module,
    now(),
    "Timetable metrics describe governed generation runs; room operations come from published venue allocations.",
    [
      metric(
        "Approved sessions",
        setup.sessions.filter((item) => item.status === "APPROVED").length,
        "Exam sessions ready for timetable governance",
        "i-lucide-calendar-check-2",
        "primary",
      ),
      metric(
        "Active venues",
        setup.venues.filter((item) => item.active).length,
        "Configured examination venues",
        "i-lucide-building-2",
        "info",
      ),
      metric(
        "Published timetables",
        runs.filter((item) => item.status === "PUBLISHED").length,
        "Immutable candidate timetable runs",
        "i-lucide-calendar-clock",
        "success",
      ),
      metric(
        "Room operations",
        invigilation.venueOperations.length,
        "Published venue and Module allocations",
        "i-lucide-clipboard-check",
        "neutral",
      ),
    ],
    [
      action(
        "Draft exam setup",
        setup.sessions.filter((item) => item.status === "DRAFT").length +
          setup.requirements.filter((item) => item.status === "DRAFT").length,
        "Draft sessions and Module requirements awaiting approval.",
        "/operations/exam-setup",
        "i-lucide-file-pen-line",
        "warning",
      ),
      action(
        "Timetable review",
        runs.filter((item) => ["GENERATED", "REVIEWED", "APPROVED"].includes(item.status)).length,
        "Generated runs not yet published.",
        "/operations/exam-timetables",
        "i-lucide-calendar-search",
        "warning",
      ),
      action(
        "Open attendance registers",
        attendanceSessions.filter((item) => item.status === "OPEN").length,
        "Venue registers still recording candidate attendance.",
        "/operations/exam-invigilation",
        "i-lucide-clipboard-list",
        "info",
      ),
      action(
        "Unresolved incidents",
        incidents.filter((item) => item.status !== "RESOLVED").length,
        "Reported examination incidents awaiting resolution.",
        "/operations/exam-invigilation",
        "i-lucide-triangle-alert",
        "error",
      ),
    ],
    distributionFrom(runs, (item) => item.status),
    [
      link(
        "Exam setup",
        "Govern sessions, venues, availability and Module requirements.",
        "/operations/exam-setup",
        "i-lucide-building-2",
      ),
      link(
        "Exam timetables",
        "Generate, review, approve and publish candidate timetables.",
        "/operations/exam-timetables",
        "i-lucide-calendar-clock",
      ),
      link(
        "Invigilation",
        "Operate attendance registers and examination incidents.",
        "/operations/exam-invigilation",
        "i-lucide-clipboard-check",
      ),
    ],
  );
}

async function loadAccommodationDashboard(
  api: OperationalDashboardApi,
): Promise<OperationalDashboardSnapshot> {
  const [setup, operations] = await Promise.all([
    request<AccommodationSetupRegister>(api, "/api/accommodation/setup"),
    request<AccommodationOperationsRegister>(api, "/api/accommodation/operations"),
  ]);
  const usableRooms = setup.rooms.filter(
    (item) => item.active && item.conditionStatus === "AVAILABLE",
  );
  const activeAllocations = operations.allocations.filter((item) =>
    ["ALLOCATED", "CHECKED_IN"].includes(item.status),
  );
  const module = getModule("accommodation");
  return snapshot(
    module,
    now(),
    "Capacity uses active rooms in available condition; allocations remain separate from application totals.",
    [
      metric(
        "Available rooms",
        usableRooms.length,
        "Active rooms not in maintenance or out of service",
        "i-lucide-bed-single",
        "primary",
      ),
      metric(
        "Usable beds",
        usableRooms.reduce((total, item) => total + item.capacity, 0),
        "Capacity across available rooms",
        "i-lucide-bed-double",
        "info",
      ),
      metric(
        "Applications",
        operations.applications.length,
        "Accommodation application records",
        "i-lucide-clipboard-list",
        "neutral",
      ),
      metric(
        "Current allocations",
        activeAllocations.length,
        `${activeAllocations.filter((item) => item.status === "CHECKED_IN").length} checked in`,
        "i-lucide-key-round",
        "success",
      ),
    ],
    [
      action(
        "Rate attention",
        operations.rates.filter(
          (item) => item.status === "DRAFT" || item.ratingStatus === "UNRATED",
        ).length,
        "Draft or unrated accommodation prices awaiting governance.",
        "/operations/accommodation-operations",
        "i-lucide-badge-dollar-sign",
        "warning",
      ),
      action(
        "Application processing",
        operations.applications.filter((item) => ["SUBMITTED", "ELIGIBLE"].includes(item.status))
          .length,
        "Applications awaiting eligibility or allocation action.",
        "/operations/accommodation-operations",
        "i-lucide-clipboard-check",
        "warning",
      ),
      action(
        "Active waitlist",
        operations.waitlistEntries.filter((item) => item.status === "ACTIVE").length,
        "Prioritised students still awaiting a room.",
        "/operations/accommodation-operations",
        "i-lucide-list-ordered",
        "info",
      ),
      action(
        "Proposed allocations",
        operations.allocations.filter((item) => item.status === "PROPOSED").length,
        "Room allocations awaiting controlled approval.",
        "/operations/accommodation-operations",
        "i-lucide-key-round",
        "primary",
      ),
      action(
        "Billing failures",
        operations.allocations.filter((item) => item.billingStatus === "FAILED").length,
        "Allocation billing handoffs requiring recovery.",
        "/operations/accommodation-operations",
        "i-lucide-triangle-alert",
        "error",
      ),
    ],
    distributionFrom(operations.allocations, (item) => item.status),
    [
      link(
        "Inventory and periods",
        "Manage premises, halls, rooms and application periods.",
        "/operations/accommodation",
        "i-lucide-bed-single",
      ),
      link(
        "Applications and occupancy",
        "Process rates, applications, waitlists and room allocations.",
        "/operations/accommodation-operations",
        "i-lucide-clipboard-check",
      ),
    ],
  );
}

async function loadDiningDashboard(
  api: OperationalDashboardApi,
): Promise<OperationalDashboardSnapshot> {
  const [setup, operations] = await Promise.all([
    request<DiningSetupRegister>(api, "/api/dining/setup"),
    request<DiningOperationsRegister>(api, "/api/dining/operations"),
  ]);
  const module = getModule("dining");
  return snapshot(
    module,
    now(),
    "Meal attendance is counted from event evidence; reversed events are excluded from net admitted meals.",
    [
      metric(
        "Active dining halls",
        setup.diningHalls.filter((item) => item.active).length,
        "Halls available for service routing",
        "i-lucide-utensils",
        "primary",
      ),
      metric(
        "Active plans",
        setup.diningPlans.filter((item) => item.status === "ACTIVE").length,
        "Governed student meal entitlements",
        "i-lucide-notebook-tabs",
        "info",
      ),
      metric(
        "Active assignments",
        operations.assignments.filter((item) => item.status === "ACTIVE").length,
        "Students with current dining access",
        "i-lucide-users",
        "success",
      ),
      metric(
        "Net meals admitted",
        operations.attendanceEvents.filter((item) => item.outcome === "ADMITTED" && !item.reversed)
          .length,
        "Admitted attendance events excluding reversals",
        "i-lucide-scan-line",
        "neutral",
      ),
    ],
    [
      action(
        "Draft dining plans",
        setup.diningPlans.filter((item) => item.status === "DRAFT").length,
        "Plans awaiting entitlement completion and activation.",
        "/operations/dining",
        "i-lucide-file-pen-line",
        "warning",
      ),
      action(
        "Planned sessions",
        operations.sessions.filter((item) => item.status === "PLANNED").length,
        "Meal service sessions awaiting opening.",
        "/operations/dining-operations",
        "i-lucide-calendar-clock",
        "neutral",
      ),
      action(
        "Open sessions",
        operations.sessions.filter((item) => item.status === "OPEN").length,
        "Meal services currently accepting attendance.",
        "/operations/dining-operations",
        "i-lucide-door-open",
        "info",
      ),
      action(
        "Critical dietary needs",
        operations.dietaryRequirements.filter(
          (item) => item.status === "ACTIVE" && item.severity === "CRITICAL",
        ).length,
        "Active clinical restrictions requiring service attention.",
        "/operations/dining-operations",
        "i-lucide-heart-pulse",
        "error",
      ),
      action(
        "Denied meal access",
        operations.attendanceEvents.filter((item) => item.outcome === "DENIED" && !item.reversed)
          .length,
        "Non-reversed denials retained for operational review.",
        "/operations/dining-operations",
        "i-lucide-shield-x",
        "warning",
      ),
    ],
    distributionFrom(operations.sessions, (item) => item.status),
    [
      link(
        "Halls, plans and attendants",
        "Maintain dining setup and controlled entitlement plans.",
        "/operations/dining",
        "i-lucide-notebook-tabs",
      ),
      link(
        "Assignments and meal access",
        "Operate student access, dietary needs and service sessions.",
        "/operations/dining-operations",
        "i-lucide-scan-line",
      ),
    ],
  );
}

async function loadDocumentsDashboard(
  api: OperationalDashboardApi,
): Promise<OperationalDashboardSnapshot> {
  const [uploads, officialDocuments] = await Promise.all([
    request<UploadedDocumentSummary[]>(api, "/api/documents/uploads"),
    request<OfficialDocumentSummary[]>(api, "/api/documents"),
  ]);
  const module = getModule("documents");
  return snapshot(
    module,
    now(),
    "Uploaded evidence and generated official records are separate governed collections.",
    [
      metric(
        "Uploaded evidence",
        uploads.length,
        "Private evidence records in object storage",
        "i-lucide-upload",
        "primary",
      ),
      metric(
        "Verified uploads",
        uploads.filter((item) => item.verificationStatus === "VERIFIED").length,
        "Evidence with a recorded verification decision",
        "i-lucide-badge-check",
        "success",
      ),
      metric(
        "Stored official records",
        officialDocuments.filter((item) => item.status === "STORED").length,
        "Generated documents ready for controlled download",
        "i-lucide-files",
        "info",
      ),
      metric(
        "Generation failures",
        officialDocuments.filter((item) => item.status === "FAILED").length,
        "Official outputs requiring retry or investigation",
        "i-lucide-file-warning",
        "error",
      ),
    ],
    [
      action(
        "Verification queue",
        uploads.filter((item) => item.verificationStatus === "PENDING").length,
        "Uploaded evidence awaiting an authorised decision.",
        "/operations/documents?tab=uploaded",
        "i-lucide-shield-check",
        "warning",
      ),
      action(
        "Rejected evidence",
        uploads.filter((item) => item.verificationStatus === "REJECTED").length,
        "Rejected uploads retained with decision evidence.",
        "/operations/documents?tab=uploaded",
        "i-lucide-file-x-2",
        "neutral",
      ),
      action(
        "Generation in progress",
        officialDocuments.filter(
          (item) => item.status === "REQUESTED" || item.status === "GENERATING",
        ).length,
        "Official documents not yet stored.",
        "/operations/documents?tab=official",
        "i-lucide-loader-circle",
        "info",
      ),
      action(
        "Retry official records",
        officialDocuments.filter((item) => item.status === "FAILED" && item.retryAvailable).length,
        "Failed generations currently eligible for controlled retry.",
        "/operations/documents?tab=official",
        "i-lucide-refresh-cw",
        "error",
      ),
    ],
    distributionFrom(officialDocuments, (item) => item.status),
    [
      link(
        "Document register",
        "Verify uploaded evidence and operate generated official records.",
        "/operations/documents",
        "i-lucide-files",
      ),
    ],
  );
}

async function loadNotificationsDashboard(
  api: OperationalDashboardApi,
): Promise<OperationalDashboardSnapshot> {
  const register = await request<NotificationRegister>(api, "/api/notifications");
  const module = getModule("notifications");
  return snapshot(
    module,
    now(),
    "Delivery state is operational evidence and never changes the originating business decision.",
    [
      metric(
        "Active templates",
        register.templates.filter((item) => item.status === "ACTIVE").length,
        "Governed templates available for delivery",
        "i-lucide-file-code-2",
        "primary",
      ),
      metric(
        "Sent requests",
        register.requests.filter((item) => item.status === "SENT").length,
        "Requests accepted by the configured provider",
        "i-lucide-send",
        "success",
      ),
      metric(
        "Delivered callbacks",
        register.providerCallbacks.filter((item) => item.deliveryStatus === "DELIVERED").length,
        "Provider-confirmed delivery evidence",
        "i-lucide-webhook",
        "info",
      ),
      metric(
        "Unread in-app",
        register.inAppNotifications.filter((item) => !item.readAt).length,
        "Recipient-owned messages not yet read",
        "i-lucide-bell",
        "neutral",
      ),
    ],
    [
      action(
        "Delivery queue",
        register.requests.filter((item) =>
          ["QUEUED", "PROCESSING", "RETRY_SCHEDULED"].includes(item.status),
        ).length,
        "Requests still awaiting terminal provider outcome.",
        "/operations/notifications?dataset=requests",
        "i-lucide-send",
        "info",
      ),
      action(
        "Failed deliveries",
        register.requests.filter((item) => item.status === "FAILED").length,
        "Delivery requests eligible for investigated retry.",
        "/operations/notifications?dataset=requests",
        "i-lucide-triangle-alert",
        "error",
      ),
      action(
        "Dead integration events",
        register.eventInbox.filter((item) => item.status === "DEAD").length,
        "Business intents requiring manual recovery.",
        "/operations/notifications?dataset=inbox",
        "i-lucide-inbox",
        "error",
      ),
      action(
        "Bounced messages",
        register.providerCallbacks.filter((item) => item.deliveryStatus === "BOUNCED").length,
        "Provider-confirmed bounces requiring recipient review.",
        "/operations/notifications?dataset=callbacks",
        "i-lucide-mail-warning",
        "warning",
      ),
    ],
    distributionFrom(register.requests, (item) => item.status),
    [
      link(
        "Notification operations",
        "Operate templates, consent, delivery, retries and provider evidence.",
        "/operations/notifications",
        "i-lucide-send",
      ),
    ],
  );
}

function moduleDefinition(
  key: OperationalDashboardKey,
  label: string,
  shortLabel: string,
  description: string,
  icon: string,
  dashboardPath: string,
  workspacePath: string,
): OperationalDashboardModule {
  return { key, label, shortLabel, description, icon, dashboardPath, workspacePath };
}

function getModule(key: OperationalDashboardKey) {
  return operationalDashboardModules.find((module) => module.key === key)!;
}

function snapshot(
  module: OperationalDashboardModule,
  generatedAt: string,
  scopeNote: string,
  metrics: OperationalDashboardMetric[],
  actions: OperationalDashboardAction[],
  distribution: OperationalDashboardDistribution[],
  links: OperationalDashboardLink[],
): OperationalDashboardSnapshot {
  return {
    ...module,
    available: true,
    generatedAt,
    scopeNote,
    metrics,
    actions,
    distribution,
    links,
  };
}

function monthlyRegistrationTrend(
  registrations: RegistrationSummary[],
): OperationalDashboardDistribution[] {
  const monthlyCounts = new Map<string, number>();
  for (const registration of registrations) {
    const initiatedAt = new Date(registration.initiatedAt);
    if (Number.isNaN(initiatedAt.getTime())) continue;
    const monthKey = `${initiatedAt.getUTCFullYear()}-${String(initiatedAt.getUTCMonth() + 1).padStart(2, "0")}`;
    monthlyCounts.set(monthKey, (monthlyCounts.get(monthKey) ?? 0) + 1);
  }
  return [...monthlyCounts.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .slice(-6)
    .map(([monthKey, value]) => ({
      label: new Intl.DateTimeFormat("en", { month: "short", timeZone: "UTC" }).format(
        new Date(`${monthKey}-01T00:00:00Z`),
      ),
      value,
    }));
}

function metric(
  label: string,
  value: number,
  hint: string,
  icon: string,
  tone: OperationalDashboardTone,
): OperationalDashboardMetric {
  return { label, value, hint, icon, tone };
}

function action(
  label: string,
  value: number,
  description: string,
  to: string,
  icon: string,
  tone: OperationalDashboardTone,
): OperationalDashboardAction {
  return { label, value, description, to, icon, tone: value > 0 ? tone : "neutral" };
}

function link(
  label: string,
  description: string,
  to: string,
  icon: string,
): OperationalDashboardLink {
  return { label, description, to, icon };
}

function workspaceLinks(module: OperationalDashboardModule) {
  return [link(`Open ${module.shortLabel}`, module.description, module.workspacePath, module.icon)];
}

function financeLinks(): OperationalDashboardLink[] {
  return [
    link(
      "Fee catalogue",
      "Govern fee definitions and effective pricing.",
      "/operations/finance-fees",
      "i-lucide-badge-dollar-sign",
    ),
    link(
      "Billing and invoices",
      "Approve charge events and post invoices.",
      "/operations/finance-billing",
      "i-lucide-receipt-text",
    ),
    link(
      "Cash collections",
      "Rate, reconcile, receipt and allocate cash.",
      "/operations/finance-collections",
      "i-lucide-landmark",
    ),
    link(
      "Finance corrections",
      "Post controlled credit notes and reversals.",
      "/operations/finance-corrections",
      "i-lucide-git-compare-arrows",
    ),
    link(
      "Student accounts",
      "Inspect chronological student-account evidence.",
      "/operations/finance-accounts",
      "i-lucide-notebook-tabs",
    ),
  ];
}

function assessmentLinks(): OperationalDashboardLink[] {
  return [
    link(
      "Assessment schemes",
      "Govern Module offerings and component rules.",
      "/operations/assessment-schemes",
      "i-lucide-list-checks",
    ),
    link(
      "Mark capture",
      "Capture and submit marks against confirmed rosters.",
      "/operations/assessment-capture",
      "i-lucide-clipboard-pen-line",
    ),
    link(
      "Mark amendments",
      "Decide controlled changes to submitted evidence.",
      "/operations/assessment-amendments",
      "i-lucide-history",
    ),
    link(
      "Result board",
      "Moderate, approve and publish result batches.",
      "/operations/result-batches",
      "i-lucide-stamp",
    ),
    link(
      "Result corrections",
      "Govern published result amendments.",
      "/operations/result-corrections",
      "i-lucide-git-compare-arrows",
    ),
    link(
      "Progression decisions",
      "Calculate and release academic standing.",
      "/operations/progression-decisions",
      "i-lucide-route",
    ),
  ];
}

async function request<T>(api: OperationalDashboardApi, path: string): Promise<T> {
  return api.request(path) as Promise<T>;
}

function countStatus<T extends { status: string }>(items: T[], status: string) {
  return items.filter((item) => item.status === status).length;
}

function statusDistribution<T extends { status: string }>(items: T[], statuses: string[]) {
  return statuses.map((status) => ({ label: title(status), value: countStatus(items, status) }));
}

function distributionFrom<T>(items: T[], classifier: (item: T) => string) {
  return items
    .map(classifier)
    .reduce(distributionReducer, [] as OperationalDashboardDistribution[]);
}

function distributionReducer(distribution: OperationalDashboardDistribution[], status: string) {
  const label = title(status);
  const existing = distribution.find((item) => item.label === label);
  if (existing) existing.value += 1;
  else distribution.push({ label, value: 1 });
  return distribution;
}

function value(counts: Map<string, number>, key: string) {
  return counts.get(key) ?? 0;
}

function values(counts: Map<string, number>, ...keys: string[]) {
  return keys.reduce((total, key) => total + value(counts, key), 0);
}

function title(value: string) {
  return value
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function now() {
  return new Date().toISOString();
}

function errorMessage(error: unknown) {
  if (error instanceof Error && error.message) return error.message;
  return "This module could not provide operational metrics.";
}
