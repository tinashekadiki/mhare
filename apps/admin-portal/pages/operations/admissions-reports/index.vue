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

const formatColors: Record<string, 'neutral' | 'info' | 'success' | 'error' | 'secondary'> = {
  SCREEN: 'neutral',
  BAR_CHART: 'info',
  GRAPH: 'info',
  XLSX: 'success',
  PDF: 'error',
  EMAIL: 'secondary'
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

function formatColor(format: string) {
  return formatColors[format] ?? 'neutral'
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Admissions reports" />
    </template>

    <template #body>
      <UContainer
        data-testid="admissions-reports-content"
        class="w-full max-w-none space-y-6 py-4 sm:py-6 [--ui-primary:var(--ui-color-primary-800)] dark:[--ui-primary:var(--ui-color-primary-300)]"
      >
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

        <UPageGrid v-if="loading" aria-label="Loading admissions report catalogue" class="gap-4 xl:grid-cols-3">
          <USkeleton v-for="index in 6" :key="index" class="h-72 rounded-lg" />
        </UPageGrid>

        <UPageGrid
          v-else-if="reportCatalogue.length"
          aria-label="Admissions report families"
          class="gap-4 xl:grid-cols-3"
        >
          <UPageCard
            v-for="reportDefinition in reportCatalogue"
            :key="reportDefinition.code"
            :icon="reportIcons[reportDefinition.code]"
            :title="reportDefinition.title"
            :description="reportDefinition.description"
            variant="outline"
            class="min-h-72"
            :ui="{
              container: 'p-5 sm:p-5',
              wrapper: 'h-full items-stretch',
              header: 'mb-3',
              leading: 'mb-3',
              leadingIcon: 'size-5 text-toned',
              title: 'text-lg leading-snug',
              description: 'mt-2 text-sm leading-5 text-muted',
              footer: 'mt-auto border-t border-muted pt-3'
            }"
          >
            <template #header>
              <UBadge :label="reportDefinition.family" color="neutral" variant="subtle" size="sm" />
            </template>

            <template #title>
              <h2>{{ reportDefinition.title }}</h2>
            </template>

            <template #description>
              <p>{{ reportDefinition.description }}</p>
              <div class="mt-4 flex flex-wrap gap-1.5" aria-label="Available formats">
                <UBadge
                  v-for="format in reportDefinition.formats"
                  :key="format"
                  :label="formatLabel(format)"
                  :color="formatColor(format)"
                  variant="subtle"
                  size="sm"
                />
              </div>
              <USeparator class="my-4" />
              <ul class="space-y-2 text-sm text-muted">
                <li v-for="variant in reportDefinition.variants.slice(0, 3)" :key="variant" class="flex items-start gap-2">
                  <span class="mt-2 size-1.5 shrink-0 rounded-full bg-accented" aria-hidden="true" />
                  <span class="leading-5">{{ variant }}</span>
                </li>
                <li v-if="reportDefinition.variants.length > 3" class="pl-3.5 text-xs text-dimmed">
                  +{{ reportDefinition.variants.length - 3 }} more covered outputs
                </li>
              </ul>
            </template>

            <template #footer>
              <UButton
                :to="reportPath(reportDefinition.code)"
                :label="reportActionLabel(reportDefinition.code)"
                :icon="reportDefinition.code === 'OFFER_LETTERS' ? 'i-lucide-mails' : 'i-lucide-arrow-up-right'"
                trailing
                block
                color="primary"
                variant="ghost"
                class="-mx-2 justify-between"
              />
            </template>
          </UPageCard>
        </UPageGrid>

        <UEmpty
          v-else-if="!errorMessage"
          icon="i-lucide-library"
          title="No report families are available"
          description="The admissions report catalogue has not been configured yet."
          variant="outline"
          class="min-h-64"
        />
      </UContainer>
    </template>
  </UDashboardPanel>
</template>
