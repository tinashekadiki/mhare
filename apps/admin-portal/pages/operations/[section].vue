<script setup lang="ts">
definePageMeta({ layout: "dashboard" });

const route = useRoute();

const sectionTitles: Record<string, string> = {
  accommodation: "Accommodation",
  admissions: "Admissions",
  applicants: "Applicants",
  core: "Core Identity",
  documents: "Documents",
  assessment: "Assessment & Results",
  exams: "Exams",
  finance: "Finance",
  modules: "Modules",
  students: "Student Records",
};

const sectionWorkflows: Record<string, string[]> = {
  core: [
    "Institution profile and branding defaults",
    "Keycloak-linked user catalogue without duplicate authentication ownership",
    "System and academic-unit scoped roles",
    "Permission catalogue and role permission grants",
    "User role assignments and RBAC authorization checks",
  ],
  admissions: [
    "Intakes and application types",
    "Applicant profiles and authenticated applications",
    "Programme choices, qualification evidence, and documents",
    "Application fee gate before review and selection",
    "Requirement rules, evaluation results, accommodation requests, and admissions exams",
  ],
};

const sectionActions: Record<
  string,
  Array<{ label: string; description: string; icon: string; to: string }>
> = {
  admissions: [
    {
      label: "Admissions Workflow",
      description:
        "Follow applicant batches through confirmation, release, academic recommendation, final decision, and offer.",
      icon: "i-lucide-workflow",
      to: "/operations/admissions",
    },
  ],
  students: [
    {
      label: "Accepted-offer conversions",
      description:
        "Track provisioning through Student Records, Finance, Core Identity, and Admissions.",
      icon: "i-lucide-user-round-check",
      to: "/operations/student-conversions",
    },
    {
      label: "Module registration",
      description:
        "Build approved curriculum loads, record academic approval, and confirm downstream rosters.",
      icon: "i-lucide-clipboard-list",
      to: "/operations/student-registrations",
    },
  ],
  assessment: [
    {
      label: "Assessment schemes",
      description:
        "Version component weights, maxima, capture windows, and approval evidence.",
      icon: "i-lucide-list-checks",
      to: "/operations/assessment-schemes",
    },
    {
      label: "Mark capture",
      description:
        "Capture and submit scores against confirmed rosters and approved rules.",
      icon: "i-lucide-clipboard-pen-line",
      to: "/operations/assessment-capture",
    },
    {
      label: "Mark amendments",
      description:
        "Independently decide corrections without overwriting submitted evidence.",
      icon: "i-lucide-history",
      to: "/operations/assessment-amendments",
    },
    {
      label: "Grading schemes",
      description:
        "Version complete, gap-free grade bands before any result batch uses them.",
      icon: "i-lucide-chart-no-axes-column-increasing",
      to: "/operations/grading-schemes",
    },
    {
      label: "Result board",
      description:
        "Moderate, independently approve, and publish immutable results from exact calculation evidence.",
      icon: "i-lucide-stamp",
      to: "/operations/result-batches",
    },
    {
      label: "Published result corrections",
      description:
        "Retain every official result version while independently reviewing and releasing corrections.",
      icon: "i-lucide-git-compare-arrows",
      to: "/operations/result-corrections",
    },
    {
      label: "Progression rules",
      description:
        "Version programme-owned thresholds and approve one deterministic rule set for each period.",
      icon: "i-lucide-list-tree",
      to: "/operations/progression-rules",
    },
    {
      label: "Progression decisions",
      description:
        "Calculate academic standing from complete published evidence and independently release the official decision.",
      icon: "i-lucide-route",
      to: "/operations/progression-decisions",
    },
  ],
  exams: [
    {
      label: "Exam setup",
      description:
        "Certify venue capacity and availability, bound session slots, and approve versioned Module requirements.",
      icon: "i-lucide-building-2",
      to: "/operations/exam-setup",
    },
    {
      label: "Timetable control",
      description:
        "Generate complete clash-free allocations, independently review and approve them, then publish candidate timetables.",
      icon: "i-lucide-calendar-clock",
      to: "/operations/exam-timetables",
    },
    {
      label: "Invigilation register",
      description:
        "Reconcile published room rosters, candidate attendance, and independently governed examination incidents.",
      icon: "i-lucide-clipboard-check",
      to: "/operations/exam-invigilation",
    },
  ],
  finance: [
    {
      label: "Fee catalogue and pricing",
      description:
        "Govern charge definitions, posting accounts, effective prices, scope, exchange-rate evidence, and independent approvals.",
      icon: "i-lucide-badge-dollar-sign",
      to: "/operations/finance-fees",
    },
    {
      label: "Billing and invoices",
      description:
        "Approve authoritative charge events, activate registration policies, and post immutable student invoices.",
      icon: "i-lucide-receipt-text",
      to: "/operations/finance-billing",
    },
    {
      label: "Cash collection and reconciliation",
      description:
        "Capture provider evidence, rate ZWG, resolve suspense, issue receipts, allocate cash, and record realised FX.",
      icon: "i-lucide-landmark",
      to: "/operations/finance-collections",
    },
    {
      label: "Finance corrections",
      description:
        "Post maker-checker credit notes and inspect append-only payment and allocation reversals.",
      icon: "i-lucide-git-compare-arrows",
      to: "/operations/finance-corrections",
    },
    {
      label: "Student account inquiry",
      description:
        "Review chronological invoice, payment, reversal, credit-note, and running USD balance evidence.",
      icon: "i-lucide-notebook-tabs",
      to: "/operations/finance-accounts",
    },
  ],
};

const sectionKey = computed(() => String(route.params.section ?? "operations"));
const sectionTitle = computed(
  () => sectionTitles[sectionKey.value] ?? "Operations",
);
const workflowItems = computed(() => sectionWorkflows[sectionKey.value] ?? []);
const actionItems = computed(() => sectionActions[sectionKey.value] ?? []);
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar :title="sectionTitle">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="p-4">
        <UAlert
          color="primary"
          variant="soft"
          icon="i-lucide-shield-check"
          :title="
            actionItems.length
              ? `${sectionTitle} operations`
              : `${sectionTitle} delivery status`
          "
          :description="
            actionItems.length
              ? 'Use the governed workspaces below to move records through each controlled lifecycle.'
              : 'This domain is queued for implementation from the accepted requirements and ADRs.'
          "
        />
        <div
          v-if="actionItems.length"
          class="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4"
        >
          <NuxtLink
            v-for="actionItem in actionItems"
            :key="actionItem.to"
            :to="actionItem.to"
            class="rounded-lg outline-primary/25 focus-visible:outline-3"
          >
            <UCard class="h-full" :ui="{ body: 'p-4' }">
              <div class="flex items-start gap-3">
                <UIcon
                  :name="actionItem.icon"
                  class="mt-0.5 size-5 shrink-0 text-primary"
                />
                <div>
                  <p class="text-sm font-medium text-highlighted">
                    {{ actionItem.label }}
                  </p>
                  <p class="mt-1 text-xs text-muted">
                    {{ actionItem.description }}
                  </p>
                </div>
              </div>
            </UCard>
          </NuxtLink>
        </div>
        <div
          v-if="workflowItems.length"
          class="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3"
        >
          <UCard
            v-for="workflowItem in workflowItems"
            :key="workflowItem"
            :ui="{ body: 'p-4' }"
          >
            <div class="flex items-start gap-3">
              <UIcon
                name="i-lucide-file-check-2"
                class="mt-0.5 size-5 shrink-0 text-primary"
              />
              <p class="text-sm text-highlighted">
                {{ workflowItem }}
              </p>
            </div>
          </UCard>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
