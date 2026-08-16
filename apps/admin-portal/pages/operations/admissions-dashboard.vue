<!-- Author: Tinashe K -->
<script setup lang="ts">
import type {
  AdmissionsPipelineReport,
  AdmissionsReportDimensionCount,
  AdmissionsReportFilterOption
} from '@emhare/portal-shell/types/admissions'

defineOptions({ name: 'AdmissionsDashboardPage' })
definePageMeta({
  layout: 'dashboard',
  requiredAnyPermissions: ['ADMISSIONS_APPLICATION_REVIEW']
})

type DashboardTone = 'neutral' | 'primary' | 'success' | 'warning' | 'info'

type ActionMetric = {
  code: string
  label: string
  count: number
  hint: string
  icon: string
  tone: DashboardTone
  actionLabel: string
  to: string
}

const ALL_FILTER_OPTION_VALUE = '__ALL__'
const api = useEmhareApi()
const loading = ref(true)
const errorMessage = ref('')
const report = ref<AdmissionsPipelineReport | null>(null)
const filters = ref({
  intakeId: ALL_FILTER_OPTION_VALUE,
  programmeId: ALL_FILTER_OPTION_VALUE,
  applicationTypeId: ALL_FILTER_OPTION_VALUE,
  categoryCode: ALL_FILTER_OPTION_VALUE,
  genderCode: ALL_FILTER_OPTION_VALUE
})

const statusCounts = computed(() => dimensionMap(report.value?.statusCounts))
const paymentCounts = computed(() => dimensionMap(report.value?.paymentCounts))
const clearedPayments = computed(() => count(paymentCounts.value, 'PAID') + count(paymentCounts.value, 'WAIVED'))

const actionMetrics = computed<ActionMetric[]>(() => [
  metric(
    'PAYMENT_ATTENTION',
    'Payment attention',
    count(paymentCounts.value, 'PENDING'),
    'Fee-required applications without confirmed payment or waiver.',
    'i-lucide-credit-card',
    'warning',
    'Open verification queue',
    '/operations/admissions?stage=VERIFICATION'
  ),
  metric(
    'VERIFICATION',
    'Verification',
    statusTotal('SUBMITTED', 'PAYMENT_PENDING', 'INCOMPLETE'),
    'Submitted cases still completing Admissions clearance checks.',
    'i-lucide-shield-check',
    'primary',
    'Open verification queue',
    '/operations/admissions?stage=VERIFICATION'
  ),
  metric(
    'ELIGIBILITY',
    'Eligibility processing',
    statusTotal('UNDER_REVIEW'),
    'Payment-cleared cases being evaluated against governed requirements.',
    'i-lucide-list-checks',
    'info',
    'Open eligibility queue',
    '/operations/admissions?stage=ELIGIBILITY'
  ),
  metric(
    'ACADEMIC_REVIEW',
    'Academic review',
    statusTotal('UNDER_ACADEMIC_REVIEW'),
    'Eligible choices awaiting academic recommendation or Admissions decision.',
    'i-lucide-building-2',
    'warning',
    'Open academic review queue',
    '/operations/admissions?stage=ACADEMIC_REVIEW'
  ),
  metric(
    'OFFER_PREPARATION',
    'Offer preparation',
    statusTotal('ADMITTED'),
    'Admitted applications that have not yet reached applicant response.',
    'i-lucide-file-signature',
    'primary',
    'Open offer queue',
    '/operations/admissions?stage=OFFER'
  ),
  metric(
    'AWAITING_RESPONSE',
    'Awaiting response',
    statusTotal('OFFERED'),
    'Published offers awaiting an applicant acceptance or decline.',
    'i-lucide-mail-question',
    'info',
    'Open response queue',
    '/operations/admissions?stage=RESPONSE'
  ),
  metric(
    'ACCEPTED',
    'Accepted for conversion',
    statusTotal('ACCEPTED'),
    'Accepted offers ready for the controlled student conversion handoff.',
    'i-lucide-user-round-plus',
    'success',
    'Open conversion queue',
    '/operations/student-conversions'
  ),
  metric(
    'CONVERTED',
    'Converted',
    statusTotal('CONVERTED'),
    'Applications already converted into Student Records.',
    'i-lucide-user-round-check',
    'success',
    'Open Student Records',
    '/operations/student-records'
  )
])

const currentPositions = computed(() => [
  { label: 'Application preparation', count: statusTotal('DRAFT') },
  { label: 'Verification', count: statusTotal('SUBMITTED', 'PAYMENT_PENDING', 'INCOMPLETE') },
  { label: 'Eligibility', count: statusTotal('UNDER_REVIEW', 'ELIGIBLE', 'NOT_ELIGIBLE') },
  { label: 'Academic review', count: statusTotal('UNDER_ACADEMIC_REVIEW') },
  { label: 'Admissions decision', count: statusTotal('ADMITTED', 'REJECTED') },
  { label: 'Offer', count: statusTotal('OFFERED') },
  { label: 'Response', count: statusTotal('ACCEPTED', 'DECLINED', 'WITHDRAWN') },
  { label: 'Converted', count: statusTotal('CONVERTED') }
])

const maximumPositionCount = computed(() => Math.max(1, ...currentPositions.value.map(position => position.count)))
const leadingProgrammes = computed(() => [...(report.value?.programmeStatistics ?? [])]
  .sort((first, second) => second.choices - first.choices)
  .slice(0, 6))
const maximumProgrammeDemand = computed(() => Math.max(1, ...leadingProgrammes.value.map(programme => programme.choices)))
const intakePulse = computed(() => [...(report.value?.intakeStatistics ?? [])]
  .sort((first, second) => second.applications - first.applications)
  .slice(0, 6))

onMounted(loadDashboard)

async function loadDashboard() {
  loading.value = true
  errorMessage.value = ''
  try {
    const query = reportQueryParameters()
    const suffix = query.size ? `?${query}` : ''
    report.value = await api.request<AdmissionsPipelineReport>(`/api/admissions/reports/pipeline-summary${suffix}`)
  } catch (error) {
    report.value = null
    errorMessage.value = api.errorMessage(error, 'The Admissions overview could not be loaded.')
  } finally {
    loading.value = false
  }
}

function metric(
  code: string,
  label: string,
  value: number,
  hint: string,
  icon: string,
  populatedTone: DashboardTone,
  actionLabel: string,
  to: string
): ActionMetric {
  return {
    code,
    label,
    count: value,
    hint,
    icon,
    tone: value > 0 ? populatedTone : 'neutral',
    actionLabel,
    to
  }
}

function dimensionMap(values: AdmissionsReportDimensionCount[] | undefined) {
  return new Map((values ?? []).map(value => [value.code, value.count]))
}

function count(values: Map<string, number>, code: string) {
  return values.get(code) ?? 0
}

function statusTotal(...codes: string[]) {
  return codes.reduce((total, code) => total + count(statusCounts.value, code), 0)
}

function statisticCount(values: AdmissionsReportDimensionCount[], ...codes: string[]) {
  const statistics = dimensionMap(values)
  return codes.reduce((total, code) => total + count(statistics, code), 0)
}

function reportQueryParameters() {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(filters.value)) {
    if (value !== ALL_FILTER_OPTION_VALUE) query.set(key, value)
  }
  return query
}

function clearFilters() {
  filters.value = {
    intakeId: ALL_FILTER_OPTION_VALUE,
    programmeId: ALL_FILTER_OPTION_VALUE,
    applicationTypeId: ALL_FILTER_OPTION_VALUE,
    categoryCode: ALL_FILTER_OPTION_VALUE,
    genderCode: ALL_FILTER_OPTION_VALUE
  }
  void loadDashboard()
}

function options(items: AdmissionsReportFilterOption[] | undefined, allLabel: string) {
  return [
    { label: allLabel, value: ALL_FILTER_OPTION_VALUE },
    ...(items ?? []).map(item => ({ label: `${item.code} · ${item.label}`, value: item.value }))
  ]
}

function toneClasses(tone: DashboardTone) {
  return {
    neutral: 'border-muted bg-elevated/40 text-muted',
    primary: 'border-primary/25 bg-primary/5 text-primary',
    success: 'border-success/25 bg-success/5 text-success',
    warning: 'border-warning/30 bg-warning/5 text-warning',
    info: 'border-info/25 bg-info/5 text-info'
  }[tone]
}

function generatedAtLabel(value: string | undefined) {
  if (!value) return 'Not yet refreshed'
  return `Updated ${new Date(value).toLocaleString()}`
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Admissions overview">
        <template #right>
          <UButton
            to="/operations/admissions"
            label="Open workflow"
            icon="i-lucide-workflow"
            color="primary"
          />
          <UButton
            to="/operations/admissions-reports"
            label="View reports"
            icon="i-lucide-chart-no-axes-combined"
            color="neutral"
            variant="outline"
          />
          <UButton
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="outline"
            aria-label="Refresh Admissions overview"
            :loading="loading"
            @click="loadDashboard"
          />
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <span class="text-sm text-muted">{{ generatedAtLabel(report?.generatedAt) }}</span>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <UContainer
        data-testid="admissions-dashboard-content"
        class="w-full max-w-none space-y-6 py-4 sm:py-6 [--ui-primary:var(--ui-color-primary-800)] dark:[--ui-primary:var(--ui-color-primary-300)]"
      >
        <UAlert
          v-if="errorMessage"
          color="error"
          variant="soft"
          icon="i-lucide-circle-alert"
          title="Admissions overview unavailable"
          :description="errorMessage"
        >
          <template #actions>
            <UButton
              label="Try again"
              icon="i-lucide-refresh-cw"
              color="error"
              variant="soft"
              @click="loadDashboard"
            />
          </template>
        </UAlert>

        <section
          aria-label="Admissions dashboard filters"
          class="grid gap-3 rounded-xl border border-muted bg-default p-4 sm:grid-cols-2 xl:grid-cols-6"
        >
          <UFormField label="Intake">
            <USelect
              v-model="filters.intakeId"
              data-testid="admissions-dashboard-intake-filter"
              :items="options(report?.filterOptions.intakes, 'All intakes')"
              value-key="value"
              class="w-full"
            />
          </UFormField>
          <UFormField label="Application route">
            <USelect
              v-model="filters.applicationTypeId"
              :items="options(report?.filterOptions.applicationTypes, 'All routes')"
              value-key="value"
              class="w-full"
            />
          </UFormField>
          <UFormField label="Programme">
            <USelectMenu
              v-model="filters.programmeId"
              :items="options(report?.filterOptions.programmes, 'All Programmes')"
              value-key="value"
              searchable
              class="w-full"
            />
          </UFormField>
          <UFormField label="Applicant category">
            <USelect
              v-model="filters.categoryCode"
              :items="options(report?.filterOptions.categories, 'All categories')"
              value-key="value"
              class="w-full"
            />
          </UFormField>
          <UFormField label="Gender">
            <USelect
              v-model="filters.genderCode"
              :items="options(report?.filterOptions.genders, 'All genders')"
              value-key="value"
              class="w-full"
            />
          </UFormField>
          <div class="flex items-end gap-2">
            <UButton
              data-testid="admissions-dashboard-apply-filters"
              label="Apply filters"
              icon="i-lucide-filter"
              class="flex-1 justify-center"
              :loading="loading"
              @click="loadDashboard"
            />
            <UButton
              icon="i-lucide-rotate-ccw"
              aria-label="Clear Admissions dashboard filters"
              color="neutral"
              variant="outline"
              @click="clearFilters"
            />
          </div>
        </section>

        <div v-if="loading && !report" class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4" aria-label="Loading Admissions overview">
          <USkeleton v-for="index in 8" :key="index" class="h-40 rounded-xl" />
        </div>

        <template v-else-if="report">
          <section aria-labelledby="admissions-snapshot-heading" class="space-y-3">
            <div class="flex flex-wrap items-end justify-between gap-3">
              <div>
                <p class="text-xs font-semibold uppercase tracking-wide text-primary">Current scope</p>
                <h2 id="admissions-snapshot-heading" class="mt-1 text-lg font-semibold text-highlighted">Admissions snapshot</h2>
              </div>
              <p class="text-xs text-muted">Applicant totals are deduplicated across applications.</p>
            </div>
            <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              <EmhareKpiCard
                data-testid="admissions-total-applications"
                label="Applications"
                :value="report.totalApplications"
                hint="Distinct application records"
                icon="i-lucide-files"
                tone="primary"
              />
              <EmhareKpiCard
                data-testid="admissions-total-applicants"
                label="Applicants"
                :value="report.totalApplicants"
                :hint="`${report.totalApplicants} distinct people`"
                icon="i-lucide-users"
                tone="info"
              />
              <EmhareKpiCard
                label="Payment cleared"
                :value="clearedPayments"
                hint="Confirmed or authorised waiver"
                icon="i-lucide-badge-check"
                tone="success"
              />
              <EmhareKpiCard
                label="Ranked choices"
                :value="report.rankedChoiceCounts.reduce((total, rank) => total + rank.choices, 0)"
                hint="Choice rows, not applicant totals"
                icon="i-lucide-list-ordered"
                tone="neutral"
              />
            </div>
          </section>

          <section aria-labelledby="work-requiring-attention-heading" class="space-y-3">
            <div>
              <p class="text-xs font-semibold uppercase tracking-wide text-primary">Act from the dashboard</p>
              <h2 id="work-requiring-attention-heading" class="mt-1 text-lg font-semibold text-highlighted">Workflow workload</h2>
            </div>
            <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              <UCard
                v-for="item in actionMetrics"
                :key="item.code"
                :data-testid="`admissions-action-${item.code.toLowerCase()}`"
                :ui="{ body: 'p-4' }"
                class="border transition-colors hover:border-primary/40"
              >
                <div class="flex items-start justify-between gap-4">
                  <div>
                    <p class="text-sm font-medium text-muted">{{ item.label }}</p>
                    <p class="mt-1 text-3xl font-semibold tabular-nums text-highlighted">{{ item.count }}</p>
                  </div>
                  <div class="grid size-10 shrink-0 place-items-center rounded-md border" :class="toneClasses(item.tone)">
                    <UIcon :name="item.icon" class="size-5" />
                  </div>
                </div>
                <p class="mt-3 min-h-10 text-xs leading-5 text-muted">{{ item.hint }}</p>
                <UButton
                  :to="item.to"
                  :label="item.actionLabel"
                  icon="i-lucide-arrow-up-right"
                  trailing
                  color="neutral"
                  variant="ghost"
                  class="mt-3 -mx-2 w-[calc(100%+1rem)] justify-between"
                />
              </UCard>
            </div>
          </section>

          <div class="grid gap-4 xl:grid-cols-[minmax(0,1.05fr)_minmax(0,.95fr)]">
            <section aria-labelledby="current-position-heading" class="rounded-xl border border-muted bg-default p-4 sm:p-5">
              <div class="flex items-start justify-between gap-4">
                <div>
                  <p class="text-xs font-semibold uppercase tracking-wide text-primary">Workflow distribution</p>
                  <h2 id="current-position-heading" class="mt-1 font-semibold text-highlighted">Current position</h2>
                  <p class="mt-1 text-xs text-muted">Each application appears in one current position.</p>
                </div>
                <UBadge label="Live scope" color="primary" variant="soft" />
              </div>
              <div class="mt-5 space-y-3">
                <div
                  v-for="position in currentPositions"
                  :key="position.label"
                  class="grid grid-cols-[minmax(8rem,11rem)_1fr_auto] items-center gap-3 text-sm"
                >
                  <span class="truncate text-muted">{{ position.label }}</span>
                  <div class="h-2.5 overflow-hidden rounded-full bg-elevated">
                    <div
                      class="h-full rounded-full bg-primary transition-[width] duration-300"
                      :style="{ width: `${position.count ? Math.max(3, position.count / maximumPositionCount * 100) : 0}%` }"
                    />
                  </div>
                  <span class="w-8 text-right font-semibold tabular-nums text-highlighted">{{ position.count }}</span>
                </div>
              </div>
            </section>

            <section aria-labelledby="intake-pulse-heading" class="rounded-xl border border-muted bg-default p-4 sm:p-5">
              <div>
                <p class="text-xs font-semibold uppercase tracking-wide text-primary">Application windows</p>
                <h2 id="intake-pulse-heading" class="mt-1 font-semibold text-highlighted">Intake pulse</h2>
              </div>
              <div v-if="intakePulse.length" class="mt-4 divide-y divide-muted">
                <div v-for="intake in intakePulse" :key="intake.intakeId" class="grid grid-cols-[1fr_auto] gap-4 py-3 first:pt-0 last:pb-0">
                  <div class="min-w-0">
                    <p class="font-medium text-highlighted">{{ intake.intakeCode }}</p>
                    <p class="truncate text-xs text-muted">{{ intake.intakeName }}</p>
                  </div>
                  <div class="text-right text-xs text-muted">
                    <p><strong class="text-sm text-highlighted">{{ intake.applications }}</strong> applications</p>
                    <p>{{ statisticCount(intake.statusCounts, 'ACCEPTED', 'CONVERTED') }} accepted or converted</p>
                  </div>
                </div>
              </div>
              <p v-else class="mt-6 text-sm text-muted">No intake statistics match this scope.</p>
            </section>
          </div>

          <section aria-labelledby="programme-demand-heading" class="rounded-xl border border-muted bg-default p-4 sm:p-5">
            <div class="flex flex-wrap items-start justify-between gap-4">
              <div>
                <p class="text-xs font-semibold uppercase tracking-wide text-primary">Planning evidence</p>
                <h2 id="programme-demand-heading" class="mt-1 font-semibold text-highlighted">Programme demand</h2>
                <p class="mt-1 text-xs text-muted">Ranked choices show preference demand; applications and applicants remain distinct.</p>
              </div>
              <UButton
                to="/operations/admissions-reports/application-demand"
                label="Open demand report"
                icon="i-lucide-chart-no-axes-combined"
                color="neutral"
                variant="outline"
              />
            </div>
            <div v-if="leadingProgrammes.length" class="mt-5 grid gap-3 lg:grid-cols-2">
              <article
                v-for="programme in leadingProgrammes"
                :key="programme.programmeId"
                class="rounded-lg border border-muted bg-elevated/25 p-4"
              >
                <div class="flex items-start justify-between gap-4">
                  <div class="min-w-0">
                    <p class="font-mono text-xs font-semibold text-primary">{{ programme.programmeCode }}</p>
                    <h3 class="truncate font-medium text-highlighted">{{ programme.programmeName }}</h3>
                    <p class="truncate text-xs text-muted">{{ programme.owningAcademicUnitName ?? 'Academic unit not recorded' }}</p>
                  </div>
                  <span class="shrink-0 text-sm font-semibold tabular-nums text-highlighted">{{ programme.choices }} choices</span>
                </div>
                <div class="mt-3 h-2 overflow-hidden rounded-full bg-default">
                  <div class="h-full rounded-full bg-primary" :style="{ width: `${programme.choices / maximumProgrammeDemand * 100}%` }" />
                </div>
                <div class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted">
                  <span>{{ programme.applications }} applications</span>
                  <span>{{ programme.applicants }} applicants</span>
                  <span>{{ statisticCount(programme.statusCounts, 'ACCEPTED', 'CONVERTED') }} accepted or converted</span>
                </div>
              </article>
            </div>
            <p v-else class="mt-6 text-sm text-muted">No Programme demand matches this scope.</p>
          </section>

          <section class="flex flex-col gap-4 rounded-xl border border-primary/20 bg-primary/5 p-5 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 class="font-semibold text-highlighted">Need analysis or an official export?</h2>
              <p class="mt-1 text-sm text-muted">The dashboard answers what needs attention now. Reports retain detailed tables, analysis and governed exports.</p>
            </div>
            <UButton
              to="/operations/admissions-reports"
              label="View reports"
              icon="i-lucide-arrow-up-right"
              trailing
              color="primary"
              class="shrink-0"
            />
          </section>
        </template>

        <UEmpty
          v-else-if="!errorMessage"
          icon="i-lucide-layout-dashboard"
          title="No Admissions data is available"
          description="Change the filters or confirm that applications exist for the selected scope."
          variant="outline"
          class="min-h-64"
        />
      </UContainer>
    </template>
  </UDashboardPanel>
</template>
