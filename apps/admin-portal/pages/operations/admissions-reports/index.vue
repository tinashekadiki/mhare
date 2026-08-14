<script setup lang="ts">
import type { AdmissionsReportDefinition } from '@emhare/portal-shell/types/admissions'

defineOptions({ name: 'AdmissionsReportsCataloguePage' })
definePageMeta({ layout: 'dashboard' })

type ReportCode = AdmissionsReportDefinition['code']

const api = useEmhareApi()
const loading = ref(true)
const errorMessage = ref('')
const reportCatalogue = ref<AdmissionsReportDefinition[]>([])

const reportIcons: Record<ReportCode, string> = {
  APPLICATION_DEMAND: 'i-lucide-chart-no-axes-combined',
  EXECUTIVE_STATISTICS: 'i-lucide-chart-column-big',
  APPLICANT_REGISTERS: 'i-lucide-rows-3',
  SPECIAL_CATEGORY_REGISTERS: 'i-lucide-accessibility',
  SELECTION_SCHEDULES: 'i-lucide-clipboard-check',
  INTAKE_MOVEMENTS: 'i-lucide-arrow-left-right',
  ADMISSIONS_ANALYSIS: 'i-lucide-chart-spline',
  OFFER_LETTERS: 'i-lucide-mails'
}

const formatLabels: Record<string, string> = {
  SCREEN: 'Screen',
  BAR_CHART: 'Bar chart',
  GRAPH: 'Graph',
  XLSX: 'Excel',
  PDF: 'PDF',
  EMAIL: 'Email'
}

onMounted(loadCatalogue)

async function loadCatalogue() {
  loading.value = true
  errorMessage.value = ''
  try {
    reportCatalogue.value = await api.request<AdmissionsReportDefinition[]>('/api/admissions/reports/catalogue')
  } catch (error) {
    reportCatalogue.value = []
    errorMessage.value = api.errorMessage(error, 'The admissions report catalogue could not be loaded.')
  } finally {
    loading.value = false
  }
}

function reportPath(reportCode: ReportCode) {
  if (reportCode === 'OFFER_LETTERS') return '/operations/admissions-offers'
  return `/operations/admissions-reports/${reportCode.toLowerCase().replaceAll('_', '-')}`
}

function reportActionLabel(reportCode: ReportCode) {
  return reportCode === 'OFFER_LETTERS' ? 'Open offer-letter workspace' : 'Open report'
}

function formatLabel(format: string) {
  return formatLabels[format] ?? format
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Admissions reports" />
    </template>

    <template #body>
      <div class="mx-auto w-full max-w-[1480px] space-y-6 p-4 sm:p-6 lg:p-8">
        <section
          aria-labelledby="admissions-report-catalogue-heading"
          class="border-b border-default pb-5"
        >
          <div class="flex max-w-3xl items-start gap-3">
            <div class="flex size-10 shrink-0 items-center justify-center rounded-md bg-uzgreen-900 text-white dark:bg-uzgreen-800">
              <UIcon name="i-lucide-library-big" class="size-5" />
            </div>
            <div>
              <h1 id="admissions-report-catalogue-heading" class="text-xl font-semibold text-highlighted">
                Report catalogue
              </h1>
              <p class="mt-1 max-w-2xl text-sm leading-5 text-muted">
                Open a report to apply filters, review results and export.
              </p>
            </div>
          </div>
        </section>

        <UAlert
          v-if="errorMessage"
          color="error"
          variant="soft"
          icon="i-lucide-circle-alert"
          title="Report catalogue unavailable"
          :description="errorMessage"
        >
          <template #actions>
            <UButton
              label="Try again"
              icon="i-lucide-refresh-cw"
              color="error"
              variant="soft"
              aria-label="Retry loading admissions reports"
              @click="loadCatalogue"
            />
          </template>
        </UAlert>

        <div v-if="loading" aria-label="Loading admissions report catalogue" class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <USkeleton v-for="index in 6" :key="index" class="h-72 rounded-lg" />
        </div>

        <section
          v-else-if="reportCatalogue.length"
          aria-label="Admissions report families"
          class="grid gap-4 md:grid-cols-2 xl:grid-cols-3"
        >
          <article
            v-for="reportDefinition in reportCatalogue"
            :key="reportDefinition.code"
            class="flex min-h-72 flex-col rounded-lg border border-default border-t-2 border-t-uzgreen-800 bg-default p-5"
          >
            <div class="flex size-9 items-center justify-center rounded-md bg-uzgreen-900 text-white dark:bg-uzgreen-800">
              <UIcon :name="reportIcons[reportDefinition.code]" class="size-4" />
            </div>

            <div class="mt-4">
              <p class="text-xs font-semibold uppercase tracking-wide text-uzgreen-800 dark:text-uzgreen-300">{{ reportDefinition.family }}</p>
              <h2 class="mt-1 text-lg font-semibold leading-snug text-highlighted">{{ reportDefinition.title }}</h2>
              <p class="mt-2 text-sm leading-5 text-muted">{{ reportDefinition.description }}</p>
            </div>

            <div class="mt-4 flex flex-wrap gap-1.5" aria-label="Available formats">
              <UBadge
                v-for="format in reportDefinition.formats"
                :key="format"
                :label="formatLabel(format)"
                color="neutral"
                variant="soft"
                size="sm"
              />
            </div>

            <ul class="mt-4 space-y-2 border-t border-default pt-4 text-sm text-muted">
              <li v-for="variant in reportDefinition.variants.slice(0, 3)" :key="variant" class="flex items-start gap-2">
                <UIcon name="i-lucide-check" class="mt-0.5 size-4 shrink-0 text-primary" />
                <span class="leading-5">{{ variant }}</span>
              </li>
              <li v-if="reportDefinition.variants.length > 3" class="pl-6 text-xs font-medium text-primary">
                +{{ reportDefinition.variants.length - 3 }} more covered outputs
              </li>
            </ul>

            <div class="mt-auto pt-5">
              <UButton
                :to="reportPath(reportDefinition.code)"
                :label="reportActionLabel(reportDefinition.code)"
                :icon="reportDefinition.code === 'OFFER_LETTERS' ? 'i-lucide-mails' : 'i-lucide-arrow-up-right'"
                trailing
                block
                color="neutral"
                variant="outline"
                class="justify-center border-uzgreen-800 text-uzgreen-900 hover:bg-uzgreen-50 dark:border-uzgreen-600 dark:text-uzgreen-200 dark:hover:bg-uzgreen-950"
              />
            </div>
          </article>
        </section>

        <section
          v-else-if="!errorMessage"
          class="rounded-lg border border-dashed border-default bg-elevated/40 px-6 py-16 text-center"
          aria-labelledby="empty-report-catalogue-heading"
        >
          <div class="mx-auto flex size-12 items-center justify-center rounded-xl bg-muted text-muted">
            <UIcon name="i-lucide-library" class="size-6" />
          </div>
          <h2 id="empty-report-catalogue-heading" class="mt-4 font-semibold text-highlighted">No report families are available</h2>
          <p class="mx-auto mt-1 max-w-md text-sm text-muted">The admissions report catalogue has not been configured yet.</p>
        </section>
      </div>
    </template>
  </UDashboardPanel>
</template>
