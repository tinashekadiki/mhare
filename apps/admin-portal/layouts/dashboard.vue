<script setup lang="ts">
import type {
  EmhareAcademicPeriod,
  EmhareNotification,
} from "@emhare/portal-shell/types/emhare-ui";
import type { AcademicPeriodSummary } from "@emhare/portal-shell/types/academic";
import type { InAppNotificationSummary } from "@emhare/portal-shell/types/notifications";

const auth = useEmhareAuth();
const api = useEmhareApi();
const academicSetup = useAcademicSetup();
const academicPeriodContext = useAcademicPeriodContext();
const route = useRoute();
const inAppNotifications = ref<InAppNotificationSummary[]>([]);
const coreIdentityPermissions = [
  "CORE_INSTITUTION_MANAGE",
  "CORE_USER_MANAGE",
  "CORE_ROLE_MANAGE",
  "CORE_PERMISSION_MANAGE",
  "CORE_ROLE_ASSIGN",
  "CORE_REFERENCE_MANAGE",
  "CORE_AUDIT_READ",
  "CORE_WORKFLOW_MANAGE",
  "CORE_WORKFLOW_TASK",
];
const navigationAccessByGroupLabel: Record<
  string,
  { roleCodes: string[]; permissionPrefixes: string[] }
> = {
  "Academic Setup": {
    roleCodes: ["ACADEMIC_ADMIN"],
    permissionPrefixes: ["ACADEMIC_SETUP_"],
  },
  Admissions: {
    roleCodes: ["ADMISSIONS_OFFICER"],
    permissionPrefixes: ["ADMISSIONS_"],
  },
  Finance: {
    roleCodes: ["FINANCE_OFFICER"],
    permissionPrefixes: ["FINANCE_"],
  },
  "Student Records and Registration": {
    roleCodes: ["REGISTRY_OFFICER"],
    permissionPrefixes: ["STUDENT_RECORDS_"],
  },
  "Teaching and Assessment": {
    roleCodes: ["ACADEMIC_ADMIN", "ASSESSMENT_OFFICER"],
    permissionPrefixes: ["ASSESSMENT_RESULTS_"],
  },
  "Exams and Timetabling": {
    roleCodes: ["EXAMS_OFFICER", "EXAM_INVIGILATOR"],
    permissionPrefixes: ["EXAMS_"],
  },
  Accommodation: {
    roleCodes: ["ACCOMMODATION_OFFICER"],
    permissionPrefixes: ["ACCOMMODATION_"],
  },
  Dining: {
    roleCodes: ["DINING_OFFICER"],
    permissionPrefixes: ["DINING_"],
  },
  "Documents and Reporting": {
    roleCodes: ["DOCUMENTS_OFFICER", "REPORTING_OFFICER"],
    permissionPrefixes: ["DOCUMENTS_"],
  },
  Notifications: {
    roleCodes: ["NOTIFICATIONS_OFFICER"],
    permissionPrefixes: ["NOTIFICATIONS_"],
  },
  Communications: {
    roleCodes: ["COMMUNICATIONS_AUTHOR", "COMMUNICATIONS_APPROVER"],
    permissionPrefixes: ["COMMUNICATIONS_"],
  },
};

const navigationGroups = [
  {
    items: [
      {
        label: "Operations",
        icon: "i-lucide-layout-dashboard",
        to: "/operations",
      },
    ],
  },
  {
    label: "Core and Identity",
    icon: "i-lucide-shield-check",
    items: [
      {
        label: "Core overview",
        icon: "i-lucide-layout-dashboard",
        to: "/operations/dashboard/core",
      },
      {
        label: "Core Identity",
        icon: "i-lucide-shield-check",
        to: "/operations/core",
      },
    ],
  },
  {
    label: "Academic Setup",
    icon: "i-lucide-network",
    items: [
      {
        label: "Academic overview",
        icon: "i-lucide-layout-dashboard",
        to: "/operations/dashboard/academic-setup",
      },
      {
        label: "Academic structure",
        icon: "i-lucide-network",
        to: "/operations/academic-structure",
      },
      {
        label: "Academic calendar",
        icon: "i-lucide-calendar-range",
        to: "/operations/academic-calendar",
      },
      {
        label: "Programmes",
        icon: "i-lucide-graduation-cap",
        to: "/operations/programmes",
      },
      {
        label: "Modules",
        icon: "i-lucide-book-open",
        to: "/operations/modules",
      },
      {
        label: "Curriculum",
        icon: "i-lucide-list-tree",
        to: "/operations/curriculum",
      },
    ],
  },
  {
    label: "Admissions",
    icon: "i-lucide-file-check-2",
    items: [
      {
        label: "Admissions overview",
        icon: "i-lucide-layout-dashboard",
        to: "/operations/admissions-dashboard",
      },
      {
        label: "Admissions workflow",
        icon: "i-lucide-workflow",
        to: "/operations/admissions",
      },
      {
        label: "Application types",
        icon: "i-lucide-files",
        to: "/operations/application-types",
      },
      {
        label: "Programme requirements",
        icon: "i-lucide-list-checks",
        to: "/operations/programme-requirements",
      },
      {
        label: "Applicant register",
        icon: "i-lucide-users",
        to: "/operations/applicants",
      },
      {
        label: "Admissions reports",
        icon: "i-lucide-chart-no-axes-combined",
        to: "/operations/admissions-reports",
      },
    ],
  },
  {
    label: "Finance",
    icon: "i-lucide-receipt-text",
    items: [
      {
        label: "Finance overview",
        icon: "i-lucide-layout-dashboard",
        to: "/operations/dashboard/finance",
      },
      {
        label: "Fee catalogue",
        icon: "i-lucide-badge-dollar-sign",
        to: "/operations/finance-fees",
      },
      {
        label: "Billing and invoices",
        icon: "i-lucide-files",
        to: "/operations/finance-billing",
      },
      {
        label: "Cash collections",
        icon: "i-lucide-landmark",
        to: "/operations/finance-collections",
      },
      {
        label: "Finance corrections",
        icon: "i-lucide-git-compare-arrows",
        to: "/operations/finance-corrections",
      },
      {
        label: "Student accounts",
        icon: "i-lucide-notebook-tabs",
        to: "/operations/finance-accounts",
      },
    ],
  },
  {
    label: "Student Records and Registration",
    icon: "i-lucide-user-round-check",
    items: [
      {
        label: "Student Records overview",
        icon: "i-lucide-layout-dashboard",
        to: "/operations/dashboard/student-records",
      },
      {
        label: "Student conversions",
        icon: "i-lucide-user-round-check",
        to: "/operations/student-conversions",
      },
      {
        label: "Student registration",
        icon: "i-lucide-clipboard-list",
        to: "/operations/student-registrations",
      },
    ],
  },
  {
    label: "Teaching and Assessment",
    icon: "i-lucide-clipboard-pen-line",
    items: [
      {
        label: "Assessment overview",
        icon: "i-lucide-layout-dashboard",
        to: "/operations/dashboard/assessment-results",
      },
      {
        label: "Assessment schemes",
        icon: "i-lucide-list-checks",
        to: "/operations/assessment-schemes",
      },
      {
        label: "Mark capture",
        icon: "i-lucide-clipboard-pen-line",
        to: "/operations/assessment-capture",
      },
      {
        label: "Mark amendments",
        icon: "i-lucide-history",
        to: "/operations/assessment-amendments",
      },
      {
        label: "Grading schemes",
        icon: "i-lucide-chart-no-axes-column-increasing",
        to: "/operations/grading-schemes",
      },
      {
        label: "Result board",
        icon: "i-lucide-stamp",
        to: "/operations/result-batches",
      },
      {
        label: "Result corrections",
        icon: "i-lucide-git-compare-arrows",
        to: "/operations/result-corrections",
      },
      {
        label: "Progression rules",
        icon: "i-lucide-list-tree",
        to: "/operations/progression-rules",
      },
      {
        label: "Progression decisions",
        icon: "i-lucide-route",
        to: "/operations/progression-decisions",
      },
    ],
  },
  {
    label: "Exams and Timetabling",
    icon: "i-lucide-calendar-clock",
    items: [
      {
        label: "Exams overview",
        icon: "i-lucide-layout-dashboard",
        to: "/operations/dashboard/exams-timetabling",
      },
      {
        label: "Exam setup",
        icon: "i-lucide-building-2",
        to: "/operations/exam-setup",
      },
      {
        label: "Exam timetables",
        icon: "i-lucide-calendar-clock",
        to: "/operations/exam-timetables",
      },
      {
        label: "Invigilation",
        icon: "i-lucide-clipboard-check",
        to: "/operations/exam-invigilation",
      },
    ],
  },
  {
    label: "Accommodation",
    icon: "i-lucide-building-2",
    items: [
      {
        label: "Accommodation overview",
        icon: "i-lucide-layout-dashboard",
        to: "/operations/dashboard/accommodation",
      },
      {
        label: "Accommodation",
        icon: "i-lucide-building-2",
        to: "/operations/accommodation",
        children: [
          {
            label: "Inventory and periods",
            icon: "i-lucide-bed-single",
            to: "/operations/accommodation",
          },
          {
            label: "Applications and occupancy",
            icon: "i-lucide-clipboard-check",
            to: "/operations/accommodation-operations",
          },
        ],
      },
    ],
  },
  {
    label: "Dining",
    icon: "i-lucide-utensils",
    items: [
      {
        label: "Dining overview",
        icon: "i-lucide-layout-dashboard",
        to: "/operations/dashboard/dining",
      },
      {
        label: "Dining",
        icon: "i-lucide-utensils",
        to: "/operations/dining",
        children: [
          {
            label: "Halls, plans and attendants",
            icon: "i-lucide-notebook-tabs",
            to: "/operations/dining",
          },
          {
            label: "Assignments and meal access",
            icon: "i-lucide-scan-line",
            to: "/operations/dining-operations",
          },
        ],
      },
    ],
  },
  {
    label: "Documents and Reporting",
    icon: "i-lucide-files",
    items: [
      {
        label: "Documents overview",
        icon: "i-lucide-layout-dashboard",
        to: "/operations/dashboard/documents",
      },
      {
        label: "Documents",
        icon: "i-lucide-files",
        to: "/operations/documents",
      },
    ],
  },
  {
    label: "Communications",
    icon: "i-lucide-megaphone",
    items: [
      {
        label: "Public content",
        icon: "i-lucide-newspaper",
        to: "/operations/communications",
      },
    ],
  },
  {
    label: "Notifications",
    icon: "i-lucide-send",
    items: [
      {
        label: "Notifications overview",
        icon: "i-lucide-layout-dashboard",
        to: "/operations/dashboard/notifications",
      },
      {
        label: "Notifications",
        icon: "i-lucide-send",
        to: "/operations/notifications",
      },
    ],
  },
];

const quickActions = [
  {
    id: "new-user",
    label: "New user",
    icon: "i-lucide-user-plus",
    to: "/operations/core",
  },
  {
    id: "new-application",
    label: "Admissions queue",
    icon: "i-lucide-file-check-2",
    to: "/operations/admissions",
  },
  {
    id: "programme-requirements",
    label: "Programme requirements",
    icon: "i-lucide-list-checks",
    to: "/operations/programme-requirements",
  },
  {
    id: "student-conversions",
    label: "Student conversion queue",
    icon: "i-lucide-user-round-check",
    to: "/operations/student-conversions",
  },
  {
    id: "student-registrations",
    label: "Student registration queue",
    icon: "i-lucide-clipboard-list",
    to: "/operations/student-registrations",
  },
  {
    id: "assessment-capture",
    label: "Assessment mark capture",
    icon: "i-lucide-clipboard-pen-line",
    to: "/operations/assessment-capture",
  },
  {
    id: "result-board",
    label: "Result board queue",
    icon: "i-lucide-stamp",
    to: "/operations/result-batches",
  },
  {
    id: "result-corrections",
    label: "Result correction queue",
    icon: "i-lucide-git-compare-arrows",
    to: "/operations/result-corrections",
  },
  {
    id: "progression-decisions",
    label: "Progression decision queue",
    icon: "i-lucide-route",
    to: "/operations/progression-decisions",
  },
  {
    id: "official-documents",
    label: "Official document register",
    icon: "i-lucide-file-check-2",
    to: "/operations/documents",
  },
  {
    id: "exam-setup",
    label: "Exam setup",
    icon: "i-lucide-building-2",
    to: "/operations/exam-setup",
  },
  {
    id: "exam-timetables",
    label: "Exam timetable queue",
    icon: "i-lucide-calendar-clock",
    to: "/operations/exam-timetables",
  },
  {
    id: "exam-invigilation",
    label: "Invigilation register",
    icon: "i-lucide-clipboard-check",
    to: "/operations/exam-invigilation",
  },
  {
    id: "finance-fees",
    label: "Fee catalogue and pricing",
    icon: "i-lucide-badge-dollar-sign",
    to: "/operations/finance-fees",
  },
  {
    id: "finance-billing",
    label: "Billing approval queue",
    icon: "i-lucide-receipt-text",
    to: "/operations/finance-billing",
  },
  {
    id: "finance-collections",
    label: "Cash reconciliation queue",
    icon: "i-lucide-landmark",
    to: "/operations/finance-collections",
  },
  {
    id: "module-setup",
    label: "Module setup",
    icon: "i-lucide-book-open",
    to: "/operations/modules",
  },
  {
    id: "programme-setup",
    label: "Programme setup",
    icon: "i-lucide-graduation-cap",
    to: "/operations/programmes",
  },
];

function canAccessNavigationGroup(groupLabel?: string) {
  if (!groupLabel || groupLabel === "Operations" || auth.isSystemAdministrator.value) {
    return true;
  }
  if (groupLabel === "Core and Identity") {
    return coreIdentityPermissions.some(auth.hasPermission);
  }
  const accessRule = navigationAccessByGroupLabel[groupLabel];
  if (!accessRule) {
    return false;
  }
  return (
    accessRule.roleCodes.some(auth.hasRole) ||
    accessRule.permissionPrefixes.some(auth.hasPermissionPrefix)
  );
}

const visibleNavigationGroups = computed(() =>
  navigationGroups.filter((group) => canAccessNavigationGroup(group.label)),
);
const visibleQuickActions = computed(() =>
  quickActions.filter((action) => {
    if (action.id === "new-user" && !auth.hasPermission("CORE_USER_MANAGE")) {
      return false;
    }
    const matchingNavigationGroup = navigationGroups.find((group) =>
      group.items.some((item) => item.to === action.to),
    );
    return !matchingNavigationGroup || canAccessNavigationGroup(matchingNavigationGroup.label);
  }),
);

const academicPeriodScopedRoutes = new Set([
  "/operations/admissions-dashboard",
  "/operations/admissions",
  "/operations/admissions-reports",
  "/operations/admissions-verification",
  "/operations/admissions-evaluation",
  "/operations/programme-requirements",
  "/operations/admissions-selection",
  "/operations/admissions-offers",
  "/operations/admissions-documents",
  "/operations/student-registrations",
  "/operations/assessment-schemes",
  "/operations/assessment-capture",
  "/operations/result-batches",
  "/operations/result-corrections",
  "/operations/progression-decisions",
  "/operations/exam-setup",
  "/operations/exam-timetables",
  "/operations/accommodation-operations",
  "/operations/dining-operations",
  "/operations/documents",
]);
const showAcademicPeriodSwitcher = computed(() => academicPeriodScopedRoutes.has(route.path));

const academicPeriods = computed<EmhareAcademicPeriod[]>(() => {
  const periods = [...(academicSetup.overview.value?.academicPeriods ?? [])].sort((left, right) =>
    right.startDate.localeCompare(left.startDate),
  );
  const recommendedAcademicPeriodId = recommendedAcademicPeriod(periods)?.id;
  return periods.map((period) => ({
    id: period.id,
    label: `${period.academicYearName} · ${period.name}`,
    description: `${formatAcademicPeriodDate(period.startDate)} – ${formatAcademicPeriodDate(period.endDate)} · ${formatStatus(period.status)}`,
    current: period.id === recommendedAcademicPeriodId,
  }));
});

watch(
  academicPeriods,
  (periods) => {
    if (!periods.length) {
      return;
    }
    const selectedPeriodExists = periods.some(
      (period) => period.id === academicPeriodContext.selectedAcademicPeriodId.value,
    );
    if (!selectedPeriodExists) {
      academicPeriodContext.selectAcademicPeriod(
        periods.find((period) => period.current)?.id ?? periods[0]?.id ?? null,
      );
    }
  },
  { immediate: true },
);

function recommendedAcademicPeriod(periods: AcademicPeriodSummary[]) {
  const today = new Date();
  const todayIsoDate = [
    today.getFullYear(),
    String(today.getMonth() + 1).padStart(2, "0"),
    String(today.getDate()).padStart(2, "0"),
  ].join("-");
  return (
    periods.find(
      (period) =>
        period.status === "OPEN" &&
        period.startDate <= todayIsoDate &&
        period.endDate >= todayIsoDate,
    ) ??
    periods.find((period) => period.startDate <= todayIsoDate && period.endDate >= todayIsoDate) ??
    periods.find((period) => period.status === "OPEN") ??
    periods[0]
  );
}

function formatAcademicPeriodDate(isoDate: string) {
  return new Intl.DateTimeFormat("en-ZW", {
    day: "numeric",
    month: "short",
    year: "numeric",
  }).format(new Date(`${isoDate}T00:00:00`));
}

function formatStatus(status: string) {
  return status.charAt(0) + status.slice(1).toLowerCase();
}

const notifications = computed<EmhareNotification[]>(() =>
  inAppNotifications.value.map((notification) => ({
    id: notification.id,
    title: notification.title || "Notification",
    description: notification.body,
    tone: notification.readAt ? "neutral" : "primary",
    time: new Intl.DateTimeFormat("en-ZW", {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(new Date(notification.deliveredAt)),
    readAt: notification.readAt,
    version: notification.version,
  })),
);

async function loadMyNotifications() {
  try {
    inAppNotifications.value = await api.request<InAppNotificationSummary[]>(
      "/api/notifications/my-inbox",
    );
  } catch {
    inAppNotifications.value = [];
  }
}

async function handleNotificationSelection(notification: EmhareNotification) {
  if (notification.readAt || notification.version === undefined) return;
  const updated = await api.request<InAppNotificationSummary>(
    `/api/notifications/my-inbox/${notification.id}/read`,
    {
      method: "PATCH",
      body: { expectedVersion: notification.version },
    },
  );
  const index = inAppNotifications.value.findIndex((item) => item.id === updated.id);
  if (index >= 0) inAppNotifications.value[index] = updated;
}

onMounted(() => {
  void loadMyNotifications();
  void academicSetup.ensureOverview().catch(() => undefined);
});
</script>

<template>
  <EmhareAppShell
    :navigation-groups="visibleNavigationGroups"
    :quick-actions="visibleQuickActions"
    :academic-periods="academicPeriods"
    :selected-academic-period-id="academicPeriodContext.selectedAcademicPeriodId.value"
    :academic-periods-loading="academicSetup.loading.value"
    :academic-periods-error="academicSetup.loadError.value"
    :show-academic-period-switcher="showAcademicPeriodSwitcher"
    :notifications="notifications"
    :user-name="auth.displayName.value"
    :user-email="auth.currentUserProfile.value?.user.email"
    @period-change="academicPeriodContext.selectAcademicPeriod($event.id)"
    @notification-select="handleNotificationSelection"
    @logout="auth.logout"
  >
    <slot />
  </EmhareAppShell>
</template>
