<script setup lang="ts">
import Swal from 'sweetalert2'
import type { AssessmentCalculationRun, AssessmentOfferingSummary, AssessmentRosterMark, AssessmentComponentSummary } from '@emhare/portal-shell/types/assessment'

definePageMeta({ layout: 'dashboard' })
const api = useEmhareApi(); const toast = useToast(); const { showError } = useEmhareConfirm()
const academicPeriodContext = useAcademicPeriodContext()
const offerings = ref<AssessmentOfferingSummary[]>([]); const roster = ref<AssessmentRosterMark[]>([])
const offeringId = ref(''); const componentId = ref(''); const draftScores = reactive<Record<string, number | undefined>>({})
const loading = ref(false); const saving = ref(false); const activeMarkId = ref<string | null>(null); const calculation = ref<AssessmentCalculationRun | null>(null)
const activeOffering = computed(() => offerings.value.find(item => item.id === offeringId.value) ?? null)
const approvedScheme = computed(() => activeOffering.value?.schemes.find(item => item.status === 'APPROVED') ?? null)
const activeComponent = computed(() => approvedScheme.value?.components.find(item => item.id === componentId.value) ?? null)
const offeringItems = computed(() => offerings.value.filter(item => item.schemes.some(scheme => scheme.status === 'APPROVED')).map(item => ({ label: `${item.moduleCode} · ${item.moduleName} · ${item.academicPeriodCode}`, value: item.id })))
const componentItems = computed(() => approvedScheme.value?.components.map(item => ({ label: `${item.code} · ${item.name} · ${item.weightPercent}%`, value: item.id })) ?? [])
const submittedCount = computed(() => roster.value.filter(item => item.status === 'SUBMITTED').length)
const capturedCount = computed(() => roster.value.filter(item => item.status === 'CAPTURED').length)

watch(offeringId, () => { componentId.value = ''; roster.value = []; calculation.value = null })
watch(componentId, value => { if (value) loadRoster(); else roster.value = [] })
onMounted(loadOfferings)
watch(academicPeriodContext.selectedAcademicPeriodId, () => { offeringId.value = ''; componentId.value = ''; void loadOfferings() })

async function loadOfferings() { loading.value = true; try { const response = await api.request<AssessmentOfferingSummary[]>('/api/assessment-results/offerings'); offerings.value = response.filter(offering => academicPeriodContext.matchesAcademicPeriod(offering)) } catch (error) { await showError('Assessment offerings could not be loaded', api.errorMessage(error)) } finally { loading.value = false } }
async function loadRoster() { if (!componentId.value) return; loading.value = true; try { roster.value = await api.request(`/api/assessment-results/components/${componentId.value}/roster`); for (const row of roster.value) draftScores[row.rosterEntryId] = row.score ?? undefined } catch (error) { await showError('Mark capture roster could not be loaded', api.errorMessage(error)) } finally { loading.value = false } }

async function saveMarks() {
  if (!activeComponent.value) return
  const marks = roster.value.filter(row => row.status !== 'SUBMITTED' && draftScores[row.rosterEntryId] !== undefined).map(row => ({ rosterEntryId: row.rosterEntryId, score: Number(draftScores[row.rosterEntryId]), expectedVersion: row.markVersion }))
  if (!marks.length) return showError('No marks to save', 'Enter at least one score that has not already been submitted.')
  saving.value = true
  try { await api.request(`/api/assessment-results/components/${activeComponent.value.id}/marks`, { method: 'POST', body: { captureMethod: 'MANUAL', marks } }); await loadRoster(); toast.add({ title: 'Marks saved as captured', description: `${marks.length} mark${marks.length === 1 ? '' : 's'} remain editable until submission.`, color: 'success' }) }
  catch (error) { await showError('Marks could not be saved', api.errorMessage(error)) } finally { saving.value = false }
}

async function submitMark(row: AssessmentRosterMark) {
  if (!row.markId) return
  const result = await Swal.fire({ title: `Submit ${row.studentNumber}'s mark?`, text: 'The score becomes immutable. Any later correction must use the amendment workflow.', icon: 'question', showCancelButton: true, confirmButtonText: 'Submit mark', confirmButtonColor: '#006633' })
  if (!result.isConfirmed) return
  activeMarkId.value = row.markId
  try { await api.request(`/api/assessment-results/marks/${row.markId}/submit?expectedVersion=${row.markVersion}`, { method: 'POST' }); await loadRoster(); toast.add({ title: 'Mark submitted', description: `${row.studentNumber} is locked as assessment evidence.`, color: 'success' }) }
  catch (error) { await showError('Mark could not be submitted', api.errorMessage(error)) } finally { activeMarkId.value = null }
}

async function requestAmendment(row: AssessmentRosterMark) {
  if (!row.markId || !activeComponent.value) return
  const result = await Swal.fire({ title: `Request correction for ${row.studentNumber}?`, html: `<p class="mb-3 text-sm">Current submitted score: <strong>${row.score}</strong> / ${activeComponent.value.maximumMark}</p>`, icon: 'warning', input: 'number', inputLabel: 'Proposed replacement score', inputAttributes: { min: '0', max: String(activeComponent.value.maximumMark), step: '0.01' }, showCancelButton: true, confirmButtonText: 'Continue', confirmButtonColor: '#006633', inputValidator: value => value === '' || Number(value) < 0 || Number(value) > Number(activeComponent.value?.maximumMark) ? 'Enter a score within the component maximum.' : undefined })
  if (!result.isConfirmed) return
  const reason = await Swal.fire({ title: 'Record amendment evidence', input: 'textarea', inputLabel: 'Reason', inputPlaceholder: 'Explain the error and identify the source evidence.', inputAttributes: { maxlength: '1000' }, showCancelButton: true, confirmButtonText: 'Submit amendment request', confirmButtonColor: '#006633', inputValidator: value => value.trim() ? undefined : 'An amendment reason is required.' })
  if (!reason.isConfirmed || !reason.value?.trim()) return
  try { await api.request(`/api/assessment-results/marks/${row.markId}/amendments`, { method: 'POST', body: { proposedScore: Number(result.value), reason: reason.value.trim() } }); toast.add({ title: 'Amendment requested', description: 'The submitted mark remains authoritative until independent approval.', color: 'success' }) }
  catch (error) { await showError('Amendment request could not be recorded', api.errorMessage(error)) }
}

async function calculateResults() {
  if (!activeOffering.value) return
  try { const completed = await api.request<AssessmentCalculationRun>(`/api/assessment-results/offerings/${activeOffering.value.id}/calculations`, { method: 'POST' }); calculation.value = completed; toast.add({ title: 'Aggregate calculation complete', description: `${completed.completeResultCount} complete; ${completed.incompleteResultCount} held as incomplete.`, color: completed.incompleteResultCount ? 'warning' : 'success' }) }
  catch (error) { await showError('Aggregate calculation could not run', api.errorMessage(error)) }
}
function componentWindow(component: AssessmentComponentSummary) { return `${new Date(component.captureOpensAt).toLocaleString()} – ${new Date(component.captureClosesAt).toLocaleString()}` }
</script>

<template>
  <UDashboardPanel>
    <template #header><UDashboardNavbar title="Assessment mark capture"><template #leading><UDashboardSidebarCollapse /></template><template #right><UButton label="Refresh" icon="i-lucide-refresh-cw" color="neutral" variant="outline" :loading="loading" @click="loadOfferings" /></template></UDashboardNavbar></template>
    <template #body><div class="space-y-5 p-4 sm:p-6">
      <UAlert color="primary" variant="soft" icon="i-lucide-clipboard-pen-line" title="Controlled capture workspace" description="Capture is limited to the approved scheme and open component window. Submitted scores are immutable and corrections move to the amendment queue." />
      <UCard :ui="{ body: 'p-4' }"><div class="grid gap-4 lg:grid-cols-2"><UFormField label="Module offering"><USelect v-model="offeringId" :items="offeringItems" class="w-full" placeholder="Select an approved Module offering" /></UFormField><UFormField label="Assessment component"><USelect v-model="componentId" :items="componentItems" class="w-full" placeholder="Select component" :disabled="!offeringId" /></UFormField></div><p v-if="activeComponent" class="mt-3 text-xs text-muted">Capture window: {{ componentWindow(activeComponent) }} · maximum {{ activeComponent.maximumMark }}</p></UCard>
      <section v-if="activeComponent" class="grid gap-3 sm:grid-cols-3"><UCard :ui="{ body: 'p-4' }"><p class="text-xs uppercase text-muted">Roster</p><p class="mt-2 text-2xl font-semibold">{{ roster.length }}</p></UCard><UCard :ui="{ body: 'p-4' }"><p class="text-xs uppercase text-warning">Captured</p><p class="mt-2 text-2xl font-semibold">{{ capturedCount }}</p></UCard><UCard :ui="{ body: 'p-4' }"><p class="text-xs uppercase text-success">Submitted</p><p class="mt-2 text-2xl font-semibold">{{ submittedCount }}</p></UCard></section>
      <UCard v-if="activeComponent" :ui="{ body: 'p-0' }"><EmharePaginatedCollection v-slot="{ items: paginatedRoster }" :items="roster"><div class="overflow-x-auto"><table class="w-full min-w-[720px] text-sm"><thead class="border-b border-muted bg-elevated/50 text-left text-xs uppercase text-muted"><tr><th class="px-4 py-3">Student</th><th class="px-4 py-3">Revision</th><th class="px-4 py-3">Score / {{ activeComponent.maximumMark }}</th><th class="px-4 py-3">State</th><th class="px-4 py-3 text-right">Action</th></tr></thead><tbody><tr v-for="row in paginatedRoster" :key="row.rosterEntryId" class="border-b border-muted last:border-0"><td class="px-4 py-3"><p class="font-medium">{{ row.studentNumber }}</p></td><td class="px-4 py-3">{{ row.revisionNumber ?? '—' }}</td><td class="px-4 py-3"><UInput v-model.number="draftScores[row.rosterEntryId]" type="number" min="0" :max="activeComponent.maximumMark" step="0.01" class="w-32" :disabled="row.status === 'SUBMITTED'" /></td><td class="px-4 py-3"><UBadge :label="row.status ?? 'NOT CAPTURED'" :color="row.status === 'SUBMITTED' ? 'success' : row.status === 'CAPTURED' ? 'warning' : 'neutral'" variant="subtle" /></td><td class="px-4 py-3 text-right"><UButton v-if="row.status === 'CAPTURED'" label="Submit" size="sm" icon="i-lucide-lock-keyhole" :loading="activeMarkId === row.markId" @click="submitMark(row)" /><UButton v-else-if="row.status === 'SUBMITTED'" label="Request amendment" size="sm" color="neutral" variant="outline" icon="i-lucide-history" @click="requestAmendment(row)" /></td></tr></tbody></table></div></EmharePaginatedCollection><div class="flex flex-wrap justify-end gap-2 border-t border-muted p-4"><UButton label="Save captured marks" icon="i-lucide-save" color="neutral" variant="outline" :loading="saving" @click="saveMarks" /><UButton label="Run aggregate calculation" icon="i-lucide-calculator" @click="calculateResults" /></div></UCard>
      <UAlert v-if="componentId && !loading && !roster.length" color="warning" variant="soft" title="No eligible students" description="The authoritative registration roster contains no eligible students for this Module." />
      <UCard v-if="calculation" :ui="{ body: 'p-4' }"><div class="flex flex-wrap items-center justify-between gap-3"><div><h2 class="font-semibold">Calculation evidence</h2><p class="text-sm text-muted">Run {{ calculation.id }} · {{ new Date(calculation.initiatedAt).toLocaleString() }}</p></div><UBadge :label="`${calculation.completeResultCount} complete · ${calculation.incompleteResultCount} incomplete`" :color="calculation.incompleteResultCount ? 'warning' : 'success'" variant="subtle" /></div><EmharePaginatedCollection v-slot="{ items: paginatedOutcomes }" :items="calculation.outcomes"><div class="mt-3 grid gap-2 sm:grid-cols-2 xl:grid-cols-3"><div v-for="outcome in paginatedOutcomes" :key="outcome.rosterEntryId" class="rounded-md border border-muted p-3"><p class="font-medium">{{ outcome.studentNumber }}</p><p v-if="outcome.complete" class="mt-1 text-xl font-semibold text-primary">{{ outcome.weightedTotal }}%</p><p v-else class="mt-1 text-sm text-warning">Missing {{ outcome.missingComponentCodes }}</p></div></div></EmharePaginatedCollection></UCard>
    </div></template>
  </UDashboardPanel>
</template>
