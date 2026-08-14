<script setup lang="ts">
import type {
  AdmissionsOperationalReport,
  AdmissionsPipelineReport,
  AdmissionsReportDefinition,
  AdmissionsReportFilterOption
} from '@emhare/portal-shell/types/admissions'

defineOptions({ name: 'AdmissionsOperationalReportPage' })
definePageMeta({ layout: 'dashboard' })

const api = useEmhareApi()
const route = useRoute()
const toast = useToast()
const ALL_FILTER_OPTION_VALUE = '__ALL__'
const loading = ref(true)
const exportingFormat = ref<'xlsx' | 'pdf' | null>(null)
const errorMessage = ref('')
const exportErrorMessage = ref('')
const report = ref<AdmissionsOperationalReport | null>(null)
const filterOptions = ref<AdmissionsPipelineReport['filterOptions']>(emptyFilterOptions())
const filters = ref({
  intakeId: ALL_FILTER_OPTION_VALUE,
  programmeId: ALL_FILTER_OPTION_VALUE,
  applicationTypeId: ALL_FILTER_OPTION_VALUE,
  categoryCode: ALL_FILTER_OPTION_VALUE,
  genderCode: ALL_FILTER_OPTION_VALUE
})

const reportCode = computed(() => String(route.params.reportCode ?? '')
  .replaceAll('-', '_')
  .toUpperCase() as AdmissionsReportDefinition['code'])
const availableExportFormats = computed(() => (report.value?.definition.formats ?? [])
  .filter(format => format === 'XLSX' || format === 'PDF') as Array<'XLSX' | 'PDF'>)
const maximumChartValue = computed(() => Math.max(1, ...(report.value?.chart.map(point => point.value) ?? [1])))

onMounted(async () => {
  await Promise.all([loadFilterOptions(), loadReport()])
  loading.value = false
})

async function loadFilterOptions() {
  try {
    const pipeline = await api.request<AdmissionsPipelineReport>('/api/admissions/reports/pipeline-summary')
    filterOptions.value = pipeline.filterOptions
  } catch {
    filterOptions.value = emptyFilterOptions()
  }
}

async function loadReport() {
  loading.value = true
  errorMessage.value = ''
  try {
    const query = reportQueryParameters()
    const suffix = query.size ? `?${query}` : ''
    report.value = await api.request<AdmissionsOperationalReport>(
      `/api/admissions/reports/${reportCode.value}${suffix}`
    )
  } catch (error) {
    report.value = null
    errorMessage.value = api.errorMessage(error, 'The requested admissions report could not be loaded.')
  } finally {
    loading.value = false
  }
}

async function exportReport(format: 'xlsx' | 'pdf') {
  if (!report.value) return
  exportingFormat.value = format
  exportErrorMessage.value = ''
  try {
    const query = reportQueryParameters()
    query.set('format', format)
    const reportBlob = await api.request<Blob>(
      `/api/admissions/reports/${report.value.definition.code}/export?${query}`,
      { responseType: 'blob' }
    )
    downloadBlob(reportBlob, exportFileName(report.value.definition.code, format))
    toast.add({
      title: 'Report download started',
      description: `${format === 'xlsx' ? 'Excel workbook' : 'PDF'} export is being downloaded.`,
      color: 'success',
      icon: 'i-lucide-download'
    })
  } catch (error) {
    exportErrorMessage.value = api.errorMessage(error, 'The selected report could not be exported.')
  } finally {
    exportingFormat.value = null
  }
}

function downloadBlob(reportBlob: Blob, fileName: string) {
  const downloadUrl = URL.createObjectURL(reportBlob)
  const link = document.createElement('a')
  link.href = downloadUrl
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(downloadUrl)
}

function exportFileName(code: AdmissionsReportDefinition['code'], format: 'xlsx' | 'pdf') {
  const timestamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z')
  return `${code.toLowerCase().replaceAll('_', '-')}-${timestamp}.${format}`
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
  void loadReport()
}

function options(items: AdmissionsReportFilterOption[], allLabel: string) {
  return [
    { label: allLabel, value: ALL_FILTER_OPTION_VALUE },
    ...items.map(item => ({ label: `${item.code} · ${item.label}`, value: item.value }))
  ]
}

function exportLabel(format: 'XLSX' | 'PDF') {
  return format === 'XLSX' ? 'Export Excel workbook' : 'Export PDF'
}

function exportIcon(format: 'XLSX' | 'PDF') {
  return format === 'XLSX' ? 'i-lucide-file-spreadsheet' : 'i-lucide-file-down'
}

function emptyFilterOptions(): AdmissionsPipelineReport['filterOptions'] {
  return { intakes: [], applicationTypes: [], programmes: [], categories: [], genders: [] }
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar :title="report?.definition.title ?? 'Admissions report'">
        <template #leading>
          <UButton
            to="/operations/admissions-reports"
            icon="i-lucide-arrow-left"
            label="Reports"
            color="neutral"
            variant="ghost"
          />
        </template>
        <template #right>
          <UButton
            v-for="format in availableExportFormats"
            :key="format"
            :label="exportLabel(format)"
            :icon="exportIcon(format)"
            color="primary"
            :variant="format === 'PDF' ? 'solid' : 'soft'"
            :loading="exportingFormat === format.toLowerCase()"
            :disabled="Boolean(exportingFormat) || !report?.rows.length"
            @click="exportReport(format.toLowerCase() as 'xlsx' | 'pdf')"
          />
          <UButton
            label="Refresh"
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="outline"
            :loading="loading"
            @click="loadReport"
          />
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar v-if="report">
        <template #left>
          <UBadge color="primary" variant="soft" :label="report.definition.family" />
          <span class="text-sm text-muted">{{ report.definition.formats.join(' · ') }}</span>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="space-y-5 p-4 sm:p-6">
        <div v-if="loading && !report" class="space-y-3">
          <USkeleton class="h-24" />
          <USkeleton class="h-72" />
        </div>

        <UAlert
          v-if="errorMessage"
          color="error"
          variant="soft"
          title="Report unavailable"
          :description="errorMessage"
        />
        <UAlert
          v-if="exportErrorMessage"
          color="error"
          variant="soft"
          title="Export unavailable"
          :description="exportErrorMessage"
        />

        <template v-if="report">
          <section aria-labelledby="report-description-heading">
            <p class="text-xs font-semibold uppercase tracking-wide text-primary">{{ report.definition.family }}</p>
            <h1 id="report-description-heading" class="mt-1 text-xl font-semibold text-highlighted">{{ report.definition.title }}</h1>
            <p class="mt-1 max-w-4xl text-sm text-muted">{{ report.definition.description }}</p>
          </section>

          <section aria-label="Report filters" class="grid gap-3 rounded-xl border border-muted bg-default p-4 sm:grid-cols-2 xl:grid-cols-6">
            <UFormField label="Intake">
              <USelect v-model="filters.intakeId" :items="options(filterOptions.intakes, 'All intakes')" value-key="value" class="w-full" />
            </UFormField>
            <UFormField label="Application route">
              <USelect v-model="filters.applicationTypeId" :items="options(filterOptions.applicationTypes, 'All routes')" value-key="value" class="w-full" />
            </UFormField>
            <UFormField label="Programme">
              <USelectMenu v-model="filters.programmeId" :items="options(filterOptions.programmes, 'All Programmes')" value-key="value" searchable class="w-full" />
            </UFormField>
            <UFormField label="Applicant category">
              <USelect v-model="filters.categoryCode" :items="options(filterOptions.categories, 'All categories')" value-key="value" class="w-full" />
            </UFormField>
            <UFormField label="Gender">
              <USelect v-model="filters.genderCode" :items="options(filterOptions.genders, 'All genders')" value-key="value" class="w-full" />
            </UFormField>
            <div class="flex items-end gap-2">
              <UButton label="Apply filters" icon="i-lucide-filter" class="flex-1 justify-center" :loading="loading" @click="loadReport" />
              <UButton icon="i-lucide-rotate-ccw" aria-label="Clear report filters" color="neutral" variant="outline" @click="clearFilters" />
            </div>
          </section>

          <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <div v-for="metric in report.metrics" :key="metric.label" class="rounded-xl border border-muted bg-default p-4">
              <p class="text-xs text-muted">{{ metric.label }}</p>
              <p class="mt-1 text-2xl font-semibold tabular-nums text-highlighted">{{ metric.value }}</p>
            </div>
          </div>

          <UAlert v-for="note in report.notes" :key="note" color="info" variant="soft" :description="note" />

          <section v-if="report.chart.length" aria-labelledby="visual-summary-heading" class="rounded-xl border border-muted bg-default p-4">
            <h2 id="visual-summary-heading" class="font-semibold text-highlighted">Visual summary</h2>
            <div class="mt-4 space-y-3">
              <div v-for="point in report.chart" :key="`${point.series}-${point.label}`" class="grid grid-cols-[minmax(8rem,14rem)_1fr_auto] items-center gap-3 text-sm">
                <span class="truncate text-muted" :title="point.label">{{ point.label }}</span>
                <div class="h-3 overflow-hidden rounded-full bg-elevated">
                  <div class="h-full rounded-full bg-primary" :style="{ width: `${Math.max(2, point.value / maximumChartValue * 100)}%` }" />
                </div>
                <span class="font-semibold tabular-nums text-highlighted">{{ point.value }}</span>
              </div>
            </div>
          </section>

          <section aria-labelledby="report-results-heading" class="space-y-2">
            <div class="flex items-center justify-between gap-3">
              <h2 id="report-results-heading" class="font-semibold text-highlighted">Report results</h2>
              <span class="text-xs text-muted">{{ report.rows.length }} rows</span>
            </div>
            <div class="overflow-x-auto rounded-xl border border-muted bg-default" aria-label="Admissions report results table" tabindex="0">
              <table class="min-w-full divide-y divide-muted text-sm">
                <thead class="text-left text-xs uppercase tracking-wide text-muted">
                  <tr><th v-for="column in report.columns" :key="column.key" class="whitespace-nowrap px-3 py-3">{{ column.label }}</th></tr>
                </thead>
                <tbody class="divide-y divide-muted">
                  <tr v-for="(values, rowIndex) in report.rows" :key="rowIndex">
                    <td v-for="(value, columnIndex) in values" :key="`${rowIndex}-${columnIndex}`" class="max-w-80 px-3 py-3 align-top text-muted first:font-medium first:text-highlighted">{{ value }}</td>
                  </tr>
                  <tr v-if="!report.rows.length"><td :colspan="report.columns.length" class="px-3 py-10 text-center text-muted">No records match the selected filters.</td></tr>
                </tbody>
              </table>
            </div>
          </section>

          <p class="text-right text-xs text-muted">Generated {{ new Date(report.generatedAt).toLocaleString() }}</p>
        </template>
      </div>
    </template>
  </UDashboardPanel>
</template>
