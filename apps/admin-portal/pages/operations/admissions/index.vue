<script setup lang="ts">
import type { AdmissionsWorkItemPage, AdmissionsWorkItemRow } from '@emhare/portal-shell/types/admissions'

defineOptions({ name: 'AdmissionsWorkItemsPage' })
definePageMeta({ layout: 'dashboard' })
const route = useRoute()
const api = useEmhareApi()
const rows = ref<AdmissionsWorkItemRow[]>([])
const page = ref(0)
const size = ref(25)
const total = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const search = ref('')
const stage = ref(typeof route.query.stage === 'string' ? route.query.stage.toUpperCase() : 'ALL')
const outcome = ref('')
const printOpen = ref(false)
const printIntakeId = ref('')
const printProgrammeId = ref('')
const printCount = ref<number | null>(null)
const printLoading = ref(false)

const stageOptions = [
  { label: 'All stages', value: 'ALL' }, { label: 'Verification', value: 'VERIFICATION' },
  { label: 'Eligibility', value: 'ELIGIBILITY' }, { label: 'Academic review', value: 'ACADEMIC_REVIEW' },
  { label: 'Admission decision', value: 'ADMISSION_DECISION' }, { label: 'Offer', value: 'OFFER' },
  { label: 'Response', value: 'RESPONSE' }
]
const intakeOptions = computed(() => Array.from(new Map(rows.value.map(row => [row.intakeId, { label: row.intakeCode, value: row.intakeId }])).values()))
const programmeOptions = computed(() => Array.from(new Map(rows.value.filter(row => row.programmeId).map(row => [row.programmeId!, { label: `${row.programmeCode} · ${row.programmeName}`, value: row.programmeId! }])).values()))

onMounted(load)
watch([stage, outcome, size], () => { page.value = 0; void load() })
let searchTimer: ReturnType<typeof setTimeout> | undefined
watch(search, () => { clearTimeout(searchTimer); searchTimer = setTimeout(() => { page.value = 0; void load() }, 300) })

async function load() {
  loading.value = true; errorMessage.value = ''
  try {
    const query = new URLSearchParams({ page: String(page.value), size: String(size.value) })
    if (search.value.trim()) query.set('search', search.value.trim())
    if (stage.value !== 'ALL') query.set('stage', stage.value)
    if (outcome.value) query.set('outcome', outcome.value)
    const response = await api.request<AdmissionsWorkItemPage>(`/api/admissions/work-items?${query}`)
    rows.value = response.content; total.value = response.totalElements; totalPages.value = response.totalPages
  } catch (error) { errorMessage.value = api.errorMessage(error, 'Admissions work items could not be loaded.') }
  finally { loading.value = false }
}

async function previewPrintCount() {
  if (!printIntakeId.value || !printProgrammeId.value) return
  printLoading.value = true
  try {
    const query = new URLSearchParams({ intakeId: printIntakeId.value, programmeId: printProgrammeId.value })
    const response = await api.request<{ count: number }>(`/api/documents/offer-letters/preview-count?${query}`)
    printCount.value = response.count
  } finally { printLoading.value = false }
}

async function exportLetters(format: 'MERGED_PDF' | 'ZIP') {
  if (!printIntakeId.value || !printProgrammeId.value) return
  const query = new URLSearchParams({ intakeId: printIntakeId.value, programmeId: printProgrammeId.value, format })
  window.open(`/api/documents/offer-letters/export?${query}`, '_blank', 'noopener')
}
function format(value: string | null | undefined) { return value ? value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, letter => letter.toUpperCase()) : '—' }
function tone(value: string) { if (['ACCEPTED','ADMITTED','CONVERTED'].includes(value)) return 'success' as const; if (['REJECTED','DECLINED','FAILED'].includes(value)) return 'error' as const; if (value.includes('PENDING')) return 'warning' as const; return 'info' as const }
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Admissions">
        <template #right>
          <UButton label="Print offer letters" icon="i-lucide-printer" color="primary" @click="printOpen = true" />
          <UButton icon="i-lucide-refresh-cw" color="neutral" variant="outline" :loading="loading" @click="load" />
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left><span class="text-sm text-muted">{{ total }} rolling applicant cases</span></template>
      </UDashboardToolbar>
    </template>
    <template #body>
      <div class="space-y-4 p-4 sm:p-6">
        <div class="grid gap-3 rounded-xl border border-muted bg-default p-4 md:grid-cols-[minmax(16rem,1fr)_14rem_14rem]">
          <UInput v-model="search" icon="i-lucide-search" placeholder="Applicant, application number, email…" />
          <USelect v-model="stage" :items="stageOptions" placeholder="All stages" />
          <UInput v-model="outcome" placeholder="Outcome filter" />
        </div>
        <UAlert v-if="errorMessage" color="error" variant="soft" title="Admissions unavailable" :description="errorMessage" />
        <div class="overflow-x-auto rounded-xl border border-muted bg-default">
          <table class="min-w-full divide-y divide-muted text-sm">
            <thead class="bg-elevated/60 text-left text-xs uppercase tracking-wide text-muted"><tr>
              <th class="px-4 py-3">Applicant</th><th class="px-4 py-3">Application</th><th class="px-4 py-3">Intake / type</th>
              <th class="px-4 py-3">Programme</th><th class="px-4 py-3">Points</th><th class="px-4 py-3">Payment</th>
              <th class="px-4 py-3">Stage</th><th class="px-4 py-3">Updated</th><th class="px-4 py-3"><span class="sr-only">Open</span></th>
            </tr></thead>
            <tbody class="divide-y divide-muted">
              <tr v-for="row in rows" :key="row.applicationId" class="hover:bg-elevated/40">
                <td class="px-4 py-3"><p class="font-medium text-highlighted">{{ row.applicantName }}</p><p class="font-mono text-xs text-muted">{{ row.applicantNumber }}</p></td>
                <td class="px-4 py-3"><p class="font-mono text-highlighted">{{ row.applicationNumber }}</p><EmhareStatusPill :label="format(row.outcome)" :tone="tone(row.outcome)" /></td>
                <td class="px-4 py-3"><p>{{ row.intakeCode }}</p><p class="text-xs text-muted">{{ row.applicationTypeName }}</p></td>
                <td class="px-4 py-3"><p>{{ row.programmeCode ?? '—' }}</p><p class="max-w-56 truncate text-xs text-muted">{{ row.programmeName }}</p></td>
                <td class="px-4 py-3 tabular-nums">{{ row.points ?? '—' }}</td>
                <td class="px-4 py-3"><EmhareStatusPill :label="format(row.paymentState)" :tone="tone(row.paymentState)" /></td>
                <td class="px-4 py-3"><p class="font-medium">{{ format(row.stage) }}</p><p v-if="row.blockers.length" class="max-w-52 truncate text-xs text-warning">{{ row.blockers[0] }}</p></td>
                <td class="whitespace-nowrap px-4 py-3 text-muted">{{ new Date(row.lastActivityAt).toLocaleDateString() }}</td>
                <td class="px-4 py-3"><UButton label="Open" icon="i-lucide-arrow-right" trailing color="neutral" variant="outline" :to="`/operations/admissions/${row.applicationId}`" /></td>
              </tr>
              <tr v-if="!loading && !rows.length"><td colspan="9" class="px-4 py-12 text-center text-muted">No applicant cases match these filters.</td></tr>
            </tbody>
          </table>
          <div v-if="loading" class="p-6"><USkeleton class="h-40" /></div>
        </div>
        <div class="flex items-center justify-between">
          <span class="text-sm text-muted">Page {{ totalPages ? page + 1 : 0 }} of {{ totalPages }}</span>
          <div class="flex gap-2"><UButton label="Previous" color="neutral" variant="outline" :disabled="page === 0" @click="page--; load()" /><UButton label="Next" color="neutral" variant="outline" :disabled="page + 1 >= totalPages" @click="page++; load()" /></div>
        </div>
      </div>
    </template>
  </UDashboardPanel>

  <UModal v-model:open="printOpen" title="Print offer letters" description="Only current portal-published letters are included.">
    <template #body>
      <div class="space-y-4">
        <UFormField label="Intake" required><USelect v-model="printIntakeId" :items="intakeOptions" class="w-full" @update:model-value="printCount = null" /></UFormField>
        <UFormField label="Programme" required><USelect v-model="printProgrammeId" :items="programmeOptions" class="w-full" @update:model-value="printCount = null" /></UFormField>
        <UAlert v-if="printCount !== null" color="info" variant="soft" :title="`${printCount} current published letter${printCount === 1 ? '' : 's'}`" description="Drafts, failed generations, superseded versions and withdrawn offers are excluded." />
        <div class="flex flex-wrap justify-end gap-2"><UButton label="Preview count" color="neutral" variant="outline" :disabled="!printIntakeId || !printProgrammeId" :loading="printLoading" @click="previewPrintCount" /><UButton label="Merged PDF" icon="i-lucide-files" :disabled="printCount === 0 || printCount === null" @click="exportLetters('MERGED_PDF')" /><UButton label="ZIP" icon="i-lucide-file-archive" color="neutral" :disabled="printCount === 0 || printCount === null" @click="exportLetters('ZIP')" /></div>
      </div>
    </template>
  </UModal>
</template>
