<!-- Author: Tinashe K -->
<script setup lang="ts">
import {
  loadOperationsOverview,
  operationalDashboardModules,
  type OperationalDashboardDistribution,
  type OperationalDashboardSnapshot,
} from "@emhare/portal-shell/utils/operational-dashboard";

defineOptions({ name: "OperationsDashboardPage" });
definePageMeta({ layout: "dashboard" });

const route = useRoute();
const api = useEmhareApi();
const academicPeriodContext = useAcademicPeriodContext();
const loading = ref(true);
const refreshedAt = ref("");
const loadingModuleKeys = ref(new Set(operationalDashboardModules.map((module) => module.key)));
const modules = ref<OperationalDashboardSnapshot[]>(
  operationalDashboardModules.map((module) => ({
    ...module,
    available: false,
    generatedAt: "",
    scopeNote: "",
    metrics: [],
    actions: [],
    distribution: [],
    links: [],
  })),
);

const accessRestricted = computed(() => route.query.access === "restricted");
const displayModules = computed(() => {
  const priority = ["documents", "student-records", "academic-setup", "admissions"];
  return [...modules.value].sort((left, right) => {
    const leftPriority = priority.indexOf(left.key);
    const rightPriority = priority.indexOf(right.key);
    if (leftPriority === -1 && rightPriority === -1) return 0;
    if (leftPriority === -1) return 1;
    if (rightPriority === -1) return -1;
    return leftPriority - rightPriority;
  });
});
const availableModuleCount = computed(
  () => modules.value.filter((module) => module.available).length,
);
const attentionQueueCount = computed(() =>
  modules.value
    .filter((module) => module.available)
    .flatMap((module) => module.actions)
    .reduce((total, action) => total + action.value, 0),
);
const coveragePercentage = computed(() =>
  Math.round((availableModuleCount.value / modules.value.length) * 100),
);

onMounted(loadDashboard);
watch(academicPeriodContext.selectedAcademicPeriodId, () => void loadDashboard());

async function loadDashboard() {
  loading.value = true;
  loadingModuleKeys.value = new Set(operationalDashboardModules.map((module) => module.key));
  await Promise.all(
    operationalDashboardModules.map(async (module, index) => {
      const [loadedModule] = await loadOperationsOverview(api, [module.key], {
        academicPeriodId: academicPeriodContext.selectedAcademicPeriodId.value,
      });
      if (loadedModule) modules.value[index] = loadedModule;
      loadingModuleKeys.value.delete(module.key);
      loadingModuleKeys.value = new Set(loadingModuleKeys.value);
    }),
  );
  refreshedAt.value = new Date().toISOString();
  loading.value = false;
}

function refreshedAtLabel() {
  if (!refreshedAt.value) return "Not yet refreshed";
  return `Updated ${new Date(refreshedAt.value).toLocaleString()}`;
}

function moduleSummaryMetrics(module: OperationalDashboardSnapshot) {
  return module.summaryMetrics?.length ? module.summaryMetrics : module.metrics.slice(0, 2);
}

function metricValue(module: OperationalDashboardSnapshot, label: string) {
  return module.metrics.find((metric) => metric.label === label)?.value ?? 0;
}

function actionValue(module: OperationalDashboardSnapshot, label: string) {
  return module.actions.find((action) => action.label === label)?.value ?? 0;
}

function percentage(value: number, total: number) {
  return total > 0 ? Math.round((value / total) * 100) : 0;
}

function documentVerificationPercentage(module: OperationalDashboardSnapshot) {
  return percentage(
    metricValue(module, "Verified uploads"),
    metricValue(module, "Uploaded evidence"),
  );
}

function registrationTrend(module: OperationalDashboardSnapshot) {
  return module.trend?.length ? module.trend : module.distribution.slice(0, 6);
}

function lineChartPoints(module: OperationalDashboardSnapshot) {
  const trend = registrationTrend(module);
  if (!trend.length) return "10,76 230,76";
  const maximum = Math.max(...trend.map((point) => point.value), 1);
  if (trend.length === 1) {
    const y = 76 - (trend[0]!.value / maximum) * 56;
    return `10,${y.toFixed(1)} 230,${y.toFixed(1)}`;
  }
  return trend
    .map((point, index) => {
      const x = 10 + (index * 220) / (trend.length - 1);
      const y = 76 - (point.value / maximum) * 56;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(" ");
}

function lineChartArea(module: OperationalDashboardSnapshot) {
  const points = lineChartPoints(module);
  const coordinates = points.split(" ");
  const firstX = coordinates[0]?.split(",")[0] ?? "10";
  const lastX = coordinates.at(-1)?.split(",")[0] ?? "230";
  return `M ${firstX} 82 L ${points.replaceAll(",", " ")} L ${lastX} 82 Z`;
}

function barWidth(value: number, rows: OperationalDashboardDistribution[]) {
  const maximum = Math.max(...rows.map((row) => row.value), 1);
  return Math.max(value > 0 ? 12 : 3, Math.round((value / maximum) * 100));
}

function academicRows(module: OperationalDashboardSnapshot) {
  return module.summaryMetrics?.slice(0, 4) ?? [];
}

function admissionsFunnel(module: OperationalDashboardSnapshot) {
  return [
    { label: "Applications", value: metricValue(module, "Applications"), width: 100 },
    { label: "Verification", value: actionValue(module, "Verification"), width: 82 },
    { label: "Academic review", value: actionValue(module, "Academic review"), width: 64 },
    { label: "Accepted", value: actionValue(module, "Accepted for conversion"), width: 46 },
  ];
}

function genericChartRows(module: OperationalDashboardSnapshot) {
  const source = module.distribution.length ? module.distribution : moduleSummaryMetrics(module);
  return source.slice(0, 3);
}

function moduleAccentClass(module: OperationalDashboardSnapshot) {
  const accents: Partial<Record<OperationalDashboardSnapshot["key"], string>> = {
    core: "bg-[#173e8f]",
    finance: "bg-[#0f766e]",
    "assessment-results": "bg-[#6d28d9]",
    "exams-timetabling": "bg-[#2563eb]",
    accommodation: "bg-[#059669]",
    dining: "bg-[#ea8a16]",
    notifications: "bg-[#0891b2]",
  };
  return accents[module.key] ?? "bg-[#173e8f]";
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardToolbar>
        <template #left>
          <div class="flex items-center gap-3">
            <span
              class="grid size-9 place-items-center rounded-xl bg-[#062d65] text-white shadow-sm"
            >
              <UIcon name="i-lucide-chart-no-axes-combined" class="size-4" />
            </span>
            <div>
              <p class="text-sm font-semibold text-[#10213d]">Operations intelligence</p>
              <p class="text-xs text-[#64748b]">{{ refreshedAtLabel() }}</p>
            </div>
          </div>
        </template>
        <template #right>
          <UButton
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="outline"
            aria-label="Refresh Operations dashboard"
            :loading="loading"
            class="rounded-xl"
            @click="loadDashboard"
          />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <UContainer
        data-testid="operations-dashboard-content"
        class="min-h-full w-full max-w-none space-y-5 bg-[#f4f7fb] px-4 py-5 sm:px-6 lg:px-7 [--ui-primary:var(--ui-color-primary-800)]"
      >
        <h1 class="sr-only">Operations</h1>
        <UAlert
          v-if="accessRestricted"
          color="warning"
          variant="subtle"
          icon="i-lucide-triangle-alert"
          title="Access restricted"
          description="Your assigned role does not include that operational workspace. Dashboards below only show data your current permissions can read."
        />

        <section
          class="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between"
          aria-labelledby="operations-heading"
        >
          <div>
            <p class="text-[0.68rem] font-bold uppercase tracking-[0.24em] text-[#2563eb]">
              Executive overview
            </p>
            <h2
              id="operations-heading"
              class="mt-1 text-2xl font-bold tracking-[-0.035em] text-[#0b1730] sm:text-3xl"
            >
              Institution pulse
            </h2>
            <p class="mt-1 max-w-2xl text-sm text-[#64748b]">
              Live operational evidence across the university, organised for quick decisions and
              direct action.
            </p>
          </div>
          <div class="flex items-center gap-2 text-xs font-medium text-[#52627a]">
            <span
              class="size-2 rounded-full bg-[#10b981] shadow-[0_0_0_4px_rgba(16,185,129,0.12)]"
            />
            {{ availableModuleCount }} of {{ modules.length }} services reporting
          </div>
        </section>

        <section aria-label="Operational analytics">
          <div class="grid auto-rows-fr gap-4 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
            <article
              data-testid="operations-analytics-widget"
              data-testid-secondary="operations-executive-overview"
              class="group relative flex min-h-[18rem] flex-col overflow-hidden rounded-2xl border border-[#dce4ef] bg-[#082f66] p-5 text-white shadow-[0_12px_32px_rgba(15,35,70,0.12)]"
            >
              <div data-testid="operations-executive-overview" class="contents">
                <div
                  class="absolute -right-14 -top-14 size-40 rounded-full border-[28px] border-white/5"
                />
                <div class="relative flex items-start justify-between">
                  <div>
                    <p class="text-[0.65rem] font-bold uppercase tracking-[0.2em] text-[#8fc2ff]">
                      Live command view
                    </p>
                    <h3 class="mt-2 text-xl font-semibold">Institution pulse</h3>
                  </div>
                  <span
                    class="grid size-10 place-items-center rounded-xl bg-white/10 text-[#f4a62a]"
                  >
                    <UIcon name="i-lucide-gauge" class="size-5" />
                  </span>
                </div>
                <div class="relative mt-6 grid grid-cols-2 gap-3">
                  <div class="rounded-xl border border-white/10 bg-white/[0.07] p-3">
                    <p class="text-3xl font-semibold tabular-nums">{{ availableModuleCount }}</p>
                    <p class="mt-1 text-xs text-blue-100/75">Live services</p>
                  </div>
                  <div class="rounded-xl border border-white/10 bg-white/[0.07] p-3">
                    <p class="text-3xl font-semibold tabular-nums">{{ attentionQueueCount }}</p>
                    <p class="mt-1 text-xs text-blue-100/75">Open workload signals</p>
                  </div>
                </div>
                <div class="relative mt-auto pt-5">
                  <div class="mb-2 flex items-center justify-between text-xs">
                    <span class="text-blue-100/75">Operational coverage</span>
                    <span class="font-semibold">{{ coveragePercentage }}%</span>
                  </div>
                  <div class="h-2 overflow-hidden rounded-full bg-white/10">
                    <div
                      class="h-full rounded-full bg-gradient-to-r from-[#24c7a8] to-[#f4a62a] transition-all duration-700"
                      :style="{ width: `${coveragePercentage}%` }"
                    />
                  </div>
                </div>
              </div>
            </article>

            <article
              v-for="module in displayModules"
              :key="module.key"
              data-testid="operations-analytics-widget"
              class="group flex min-h-[18rem] flex-col overflow-hidden rounded-2xl border border-[#dce4ef] bg-white shadow-[0_8px_24px_rgba(15,35,70,0.055)] transition duration-200 hover:-translate-y-0.5 hover:border-[#c6d4e6] hover:shadow-[0_14px_30px_rgba(15,35,70,0.09)]"
            >
              <div
                :data-testid="`operations-module-${module.key}`"
                class="flex min-h-0 flex-1 flex-col p-5 pb-3"
              >
                <header class="flex items-start gap-3">
                  <span
                    class="grid size-10 shrink-0 place-items-center rounded-xl bg-[#eff4ff] text-[#173e8f]"
                  >
                    <UIcon :name="module.icon" class="size-[1.15rem]" />
                  </span>
                  <div class="min-w-0 flex-1">
                    <p class="text-[0.62rem] font-bold uppercase tracking-[0.18em] text-[#8290a6]">
                      {{ module.shortLabel }}
                    </p>
                    <h3
                      class="mt-0.5 line-clamp-2 text-[0.95rem] font-semibold leading-5 text-[#111c33]"
                    >
                      {{ module.label }}
                    </h3>
                  </div>
                  <UBadge
                    v-if="loadingModuleKeys.has(module.key) || !module.available"
                    :label="loadingModuleKeys.has(module.key) ? 'Loading' : 'Unavailable'"
                    :color="loadingModuleKeys.has(module.key) ? 'neutral' : 'error'"
                    variant="subtle"
                    size="xs"
                  />
                </header>

                <div
                  v-if="loadingModuleKeys.has(module.key)"
                  class="mt-5 grid flex-1 grid-cols-2 gap-3"
                >
                  <USkeleton class="h-20 rounded-xl" />
                  <USkeleton class="h-20 rounded-xl" />
                  <USkeleton class="col-span-2 h-16 rounded-xl" />
                </div>

                <UAlert
                  v-else-if="!module.available"
                  class="mt-5"
                  color="error"
                  variant="soft"
                  title="Metrics not available"
                  :description="module.errorMessage"
                />

                <template v-else-if="module.key === 'documents'">
                  <div
                    data-testid="document-verification-donut"
                    class="mt-4 flex flex-1 items-center gap-5"
                  >
                    <div class="relative size-32 shrink-0">
                      <svg
                        viewBox="0 0 120 120"
                        class="size-full -rotate-90"
                        role="img"
                        aria-label="Document verification rate"
                      >
                        <circle
                          cx="60"
                          cy="60"
                          r="46"
                          fill="none"
                          stroke="#edf2f7"
                          stroke-width="12"
                        />
                        <circle
                          cx="60"
                          cy="60"
                          r="46"
                          fill="none"
                          stroke="#12a981"
                          stroke-linecap="round"
                          stroke-width="12"
                          pathLength="100"
                          :stroke-dasharray="`${documentVerificationPercentage(module)} 100`"
                        />
                      </svg>
                      <div class="absolute inset-0 grid place-content-center text-center">
                        <strong class="text-2xl font-bold tabular-nums text-[#0b1730]"
                          >{{ documentVerificationPercentage(module) }}%</strong
                        >
                        <span
                          class="text-[0.62rem] font-semibold uppercase tracking-wide text-[#7a889e]"
                          >verified</span
                        >
                      </div>
                    </div>
                    <div class="min-w-0 flex-1 space-y-3">
                      <div>
                        <p class="text-2xl font-semibold tabular-nums text-[#111c33]">
                          {{ metricValue(module, "Uploaded evidence") }}
                        </p>
                        <p class="text-xs text-[#718096]">Uploaded evidence</p>
                      </div>
                      <div class="flex items-center gap-2 text-xs text-[#52627a]">
                        <span class="size-2 rounded-full bg-[#12a981]" />
                        {{ metricValue(module, "Verified uploads") }} verified
                      </div>
                      <div class="flex items-center gap-2 text-xs text-[#52627a]">
                        <span class="size-2 rounded-full bg-[#f4a62a]" />
                        {{ actionValue(module, "Verification queue") }} awaiting review
                      </div>
                    </div>
                  </div>
                </template>

                <template v-else-if="module.key === 'student-records'">
                  <div data-testid="registration-trend-chart" class="mt-4 flex flex-1 flex-col">
                    <div class="flex items-end justify-between">
                      <div>
                        <p class="text-2xl font-semibold tabular-nums text-[#111c33]">
                          {{ metricValue(module, "Registration records") }}
                        </p>
                        <p class="text-xs text-[#718096]">Registration records</p>
                      </div>
                      <span
                        class="rounded-full bg-[#f0ebff] px-2.5 py-1 text-[0.65rem] font-semibold text-[#6d28d9]"
                        >Last 6 active months</span
                      >
                    </div>
                    <svg
                      viewBox="0 0 240 92"
                      class="mt-3 h-24 w-full overflow-visible"
                      role="img"
                      aria-label="Student registration trend over time"
                    >
                      <defs>
                        <linearGradient id="registration-area" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="0%" stop-color="#6d28d9" stop-opacity="0.24" />
                          <stop offset="100%" stop-color="#6d28d9" stop-opacity="0" />
                        </linearGradient>
                      </defs>
                      <path d="M 10 76 H 230" stroke="#e7eaf0" stroke-width="1" />
                      <path :d="lineChartArea(module)" fill="url(#registration-area)" />
                      <polyline
                        :points="lineChartPoints(module)"
                        fill="none"
                        stroke="#5421a8"
                        stroke-linecap="round"
                        stroke-linejoin="round"
                        stroke-width="3"
                      />
                    </svg>
                    <div
                      class="mt-auto flex justify-between text-[0.62rem] font-medium text-[#8491a5]"
                    >
                      <span v-for="point in registrationTrend(module)" :key="point.label">{{
                        point.label
                      }}</span>
                    </div>
                  </div>
                </template>

                <template v-else-if="module.key === 'academic-setup'">
                  <div data-testid="academic-capacity-bars" class="mt-4 flex flex-1 flex-col">
                    <div class="flex items-center justify-between">
                      <p class="text-xs font-semibold text-[#52627a]">Active units by type</p>
                      <span class="text-[0.62rem] uppercase tracking-wide text-[#8a97aa]"
                        >Structure capacity</span
                      >
                    </div>
                    <div class="mt-4 space-y-3.5">
                      <div
                        v-for="(metric, index) in academicRows(module)"
                        :key="metric.label"
                        data-testid="operations-summary-metric"
                      >
                        <div class="mb-1.5 flex items-center justify-between text-xs">
                          <span
                            data-testid="operations-summary-label"
                            class="font-medium text-[#52627a]"
                            >{{ metric.label }}</span
                          >
                          <span
                            data-testid="operations-summary-value"
                            class="font-semibold tabular-nums text-[#111c33]"
                            >{{ metric.value }}</span
                          >
                        </div>
                        <div class="h-2.5 overflow-hidden rounded-full bg-[#eef2f7]">
                          <div
                            class="h-full rounded-full transition-all duration-700"
                            :class="
                              index % 3 === 0
                                ? 'bg-[#2563eb]'
                                : index % 3 === 1
                                  ? 'bg-[#13a881]'
                                  : 'bg-[#f4a62a]'
                            "
                            :style="{ width: `${barWidth(metric.value, academicRows(module))}%` }"
                          />
                        </div>
                      </div>
                    </div>
                  </div>
                </template>

                <template v-else-if="module.key === 'admissions'">
                  <div data-testid="admissions-conversion-funnel" class="mt-4 flex flex-1 flex-col">
                    <div class="mb-3 flex items-end justify-between">
                      <div>
                        <p class="text-2xl font-semibold tabular-nums text-[#111c33]">
                          {{ metricValue(module, "Applications") }}
                        </p>
                        <p class="text-xs text-[#718096]">Applications in pipeline</p>
                      </div>
                      <UIcon name="i-lucide-filter" class="size-4 text-[#ea8a16]" />
                    </div>
                    <div class="space-y-1.5">
                      <div
                        v-for="(stage, index) in admissionsFunnel(module)"
                        :key="stage.label"
                        class="mx-auto"
                        :style="{ width: `${stage.width}%` }"
                      >
                        <div
                          class="flex h-8 items-center justify-between px-4 text-[0.68rem] font-semibold text-white"
                          :class="
                            index === 0
                              ? 'bg-[#173e8f]'
                              : index === 1
                                ? 'bg-[#2563eb]'
                                : index === 2
                                  ? 'bg-[#12a981]'
                                  : 'bg-[#ea8a16]'
                          "
                          style="
                            clip-path: polygon(6% 0, 94% 0, 100% 50%, 94% 100%, 6% 100%, 0 50%);
                          "
                        >
                          <span class="truncate">{{ stage.label }}</span>
                          <span class="tabular-nums">{{ stage.value }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </template>

                <template v-else>
                  <div class="mt-4 grid grid-cols-2 gap-3">
                    <div
                      v-for="metric in moduleSummaryMetrics(module)"
                      :key="metric.label"
                      data-testid="operations-summary-metric"
                      class="rounded-xl bg-[#f7f9fc] p-3"
                    >
                      <p
                        data-testid="operations-summary-value"
                        class="text-2xl font-semibold tabular-nums text-[#111c33]"
                      >
                        {{ metric.value }}
                      </p>
                      <p
                        data-testid="operations-summary-label"
                        class="mt-0.5 truncate text-[0.68rem] text-[#718096]"
                      >
                        {{ metric.label }}
                      </p>
                    </div>
                  </div>
                  <div class="mt-4 space-y-2.5">
                    <div
                      v-for="row in genericChartRows(module)"
                      :key="row.label"
                      class="flex items-center gap-3"
                    >
                      <span class="w-20 truncate text-[0.65rem] font-medium text-[#718096]">{{
                        row.label
                      }}</span>
                      <div class="h-1.5 flex-1 overflow-hidden rounded-full bg-[#eef2f7]">
                        <div
                          class="h-full rounded-full"
                          :class="moduleAccentClass(module)"
                          :style="{ width: `${barWidth(row.value, genericChartRows(module))}%` }"
                        />
                      </div>
                      <span
                        class="w-6 text-right text-[0.65rem] font-semibold tabular-nums text-[#52627a]"
                        >{{ row.value }}</span
                      >
                    </div>
                  </div>
                </template>
              </div>

              <NuxtLink
                :to="module.dashboardPath"
                class="flex items-center justify-between border-t border-[#e8edf4] px-5 py-3 text-xs font-semibold text-[#173e8f] transition hover:bg-[#f7f9fc] hover:text-[#0b2e70]"
              >
                <span>Open dashboard</span>
                <UIcon
                  name="i-lucide-arrow-up-right"
                  class="size-4 transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5"
                />
              </NuxtLink>
            </article>
          </div>
        </section>
      </UContainer>
    </template>
  </UDashboardPanel>
</template>
