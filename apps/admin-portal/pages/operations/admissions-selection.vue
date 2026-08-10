<script setup lang="ts">
import type {
  AdmissionsApplicationSummary,
  SelectionDecisionSummary,
  SelectionRoundSummary
} from '@emhare/portal-shell/types/admissions'

definePageMeta({ layout: 'dashboard' })

const api = useEmhareApi()
const toast = useToast()
const { confirmAction, showError } = useEmhareConfirm()
const academicPeriodContext = useAcademicPeriodContext()

const applications = ref<AdmissionsApplicationSummary[]>([])
const selectionRounds = ref<SelectionRoundSummary[]>([])
const decisions = ref<SelectionDecisionSummary[]>([])
const selectedRoundId = ref('')
const loading = ref(false)
const activeActionId = ref<string | null>(null)
const loadError = ref('')
const roundModalOpen = ref(false)

const roundState = reactive({ intakeId: '', code: '', name: '' })

const intakeItems = computed(() => Array.from(new Map(applications.value.map(application => [
  application.intakeId,
  { label: application.intakeCode, value: application.intakeId }
])).values()))

const roundItems = computed(() => selectionRounds.value.map(round => ({
  label: `${round.code} · ${round.name} · ${formatStatus(round.status)}`,
  value: round.id
})))


const selectedRound = computed(() => selectionRounds.value.find(round => round.id === selectedRoundId.value) ?? null)
const newRoundGuidance = computed(() => intakeItems.value.length
  ? []
  : ['Open an intake and receive at least one application before starting a selection round.'])

onMounted(loadSelectionWorkspace)
watch(selectedRoundId, loadDecisions)
watch(academicPeriodContext.selectedAcademicPeriodId, () => void loadSelectionWorkspace())

async function loadSelectionWorkspace() {
  loading.value = true
  loadError.value = ''
  try {
    const [applicationResponse, roundResponse] = await Promise.all([
      api.request<AdmissionsApplicationSummary[]>('/api/admissions/applications'),
      api.request<SelectionRoundSummary[]>('/api/admissions/selection-rounds'),
      academicPeriodContext.ensureIntakes()
    ])
    applications.value = applicationResponse.filter(application => (
      academicPeriodContext.matchesIntake(application.intakeId)
    ))
    selectionRounds.value = roundResponse.filter(round => (
      academicPeriodContext.matchesIntake(round.intakeId)
    ))
    const firstRound = selectionRounds.value[0]
    if (!selectionRounds.value.some(round => round.id === selectedRoundId.value)) {
      selectedRoundId.value = firstRound?.id ?? ''
    }
    if (selectedRoundId.value) await loadDecisions()
  } catch (error) {
    loadError.value = api.errorMessage(error, 'The selection workspace could not be loaded.')
  } finally {
    loading.value = false
  }
}

async function loadDecisions() {
  if (!selectedRoundId.value) {
    decisions.value = []
    return
  }
  try {
    decisions.value = await api.request<SelectionDecisionSummary[]>(
      `/api/admissions/selection-rounds/${selectedRoundId.value}/decisions`
    )
  } catch (error) {
    await showError('Selection decisions unavailable', api.errorMessage(error))
  }
}

function openRoundModal() {
  Object.assign(roundState, {
    intakeId: intakeItems.value[0]?.value ?? '',
    code: '',
    name: ''
  })
  roundModalOpen.value = true
}

async function createSelectionRound() {
  if (!roundState.intakeId || !roundState.code.trim() || !roundState.name.trim()) return
  activeActionId.value = 'create-round'
  try {
    await api.request(`/api/admissions/intakes/${roundState.intakeId}/prepare-selection`, { method: 'POST' })
    const created = await api.request<SelectionRoundSummary>('/api/admissions/selection-rounds', {
      method: 'POST',
      body: {
        intakeId: roundState.intakeId,
        code: roundState.code.trim(),
        name: roundState.name.trim()
      }
    })
    selectionRounds.value = [created, ...selectionRounds.value]
    selectedRoundId.value = created.id
    roundModalOpen.value = false
    toast.add({ title: 'Selection round created', description: 'Open it before recording decisions.', color: 'success' })
  } catch (error) {
    await showError('Selection round could not be created', api.errorMessage(error))
  } finally {
    activeActionId.value = null
  }
}

async function transitionRound(round: SelectionRoundSummary, action: 'open' | 'approve' | 'close') {
  const confirmed = await confirmAction({
    title: `${formatStatus(action)} selection round?`,
    text: action === 'approve'
      ? `${round.code} decisions become the approved basis for offer generation.`
      : `${round.code} will move to ${action}.`,
    confirmButtonText: formatStatus(action),
    icon: 'question'
  })
  if (!confirmed) return
  activeActionId.value = round.id
  try {
    const updated = await api.request<SelectionRoundSummary>(
      `/api/admissions/selection-rounds/${round.id}/${action}`,
      { method: 'POST' }
    )
    selectionRounds.value = selectionRounds.value.map(existing => existing.id === updated.id ? updated : existing)
    const completedAction = action === 'close' ? 'closed' : `${action}ed`
    toast.add({ title: `Selection round ${completedAction}`, description: updated.code, color: 'success' })
  } catch (error) {
    await showError('Selection round transition failed', api.errorMessage(error))
  } finally {
    activeActionId.value = null
  }
}

function roundTone(status: SelectionRoundSummary['status']) {
  if (status === 'OPEN') return 'info' as const
  if (status === 'APPROVED') return 'success' as const
  return 'neutral' as const
}

function decisionTone(decision: SelectionDecisionSummary['decision']) {
  if (decision === 'SELECT') return 'success' as const
  if (decision === 'REJECT') return 'error' as const
  if (decision === 'WAITLIST') return 'warning' as const
  return 'info' as const
}

function formatStatus(status: string) {
  return status.toLowerCase().split('_').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(' ')
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Admissions selection rounds">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right>
          <EmhareGuidedActionButton label="New round" icon="i-lucide-plus" color="primary" guidance-title="Selection round setup required" :guidance-instructions="newRoundGuidance" guidance-action-label="Open Intakes" @guidance-action="navigateTo('/operations/academic-calendar')" @click="openRoundModal" />
          <UButton label="Refresh" icon="i-lucide-refresh-cw" color="neutral" variant="outline" :loading="loading" @click="loadSelectionWorkspace" />
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left><USelect v-model="selectedRoundId" :items="roundItems" value-key="value" placeholder="Select a selection round" class="w-full sm:w-96" /></template>
        <template #right><UButton label="Release academic reviews" icon="i-lucide-send" color="primary" to="/operations/admissions-academic-release" /></template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="space-y-5 p-4 sm:p-6">
        <UAlert
          color="info"
          variant="soft"
          icon="i-lucide-scale"
          title="Controlled selection"
          description="Selection rounds govern academic-unit release and final Admissions decisions. Advisory recommendations are reviewed on the separate Admissions decision page before they become selection decisions."
        />
        <UAlert v-if="loadError" color="error" variant="soft" title="Selection workspace unavailable" :description="loadError" />

        <UCard v-if="selectedRound" variant="outline">
          <div class="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <p class="font-mono text-xs text-muted">{{ selectedRound.code }} · {{ selectedRound.intakeCode }}</p>
              <h2 class="mt-1 text-xl font-semibold text-highlighted">{{ selectedRound.name }}</h2>
              <p class="mt-2 text-sm text-muted">{{ decisions.length }} recorded decision{{ decisions.length === 1 ? '' : 's' }}</p>
            </div>
            <div class="flex flex-wrap items-center gap-2">
              <EmhareStatusPill :label="formatStatus(selectedRound.status)" :tone="roundTone(selectedRound.status)" />
              <UButton v-if="selectedRound.status === 'DRAFT'" label="Open round" color="primary" :loading="activeActionId === selectedRound.id" @click="transitionRound(selectedRound, 'open')" />
              <EmhareGuidedActionButton v-if="selectedRound.status === 'OPEN'" label="Approve decisions" icon="i-lucide-badge-check" color="primary" guidance-title="Selection round cannot be approved yet" :guidance-instructions="decisions.length ? [] : ['Record at least one selection decision before approving this round.']" :loading="activeActionId === selectedRound.id" @click="transitionRound(selectedRound, 'approve')" />
              <UButton v-if="['OPEN', 'APPROVED'].includes(selectedRound.status)" label="Close" color="neutral" variant="outline" :loading="activeActionId === selectedRound.id" @click="transitionRound(selectedRound, 'close')" />
            </div>
          </div>
        </UCard>

        <section class="space-y-3" aria-labelledby="selection-decisions-heading">
          <h2 id="selection-decisions-heading" class="text-lg font-semibold text-highlighted">Selection decision register</h2>
          <EmharePaginatedCollection v-slot="{ items: paginatedDecisions }" :items="decisions">
          <div class="overflow-hidden rounded-lg border border-muted bg-default">
            <div v-for="decision in paginatedDecisions" :key="decision.id" class="flex flex-col gap-3 border-b border-muted p-4 last:border-b-0 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p class="font-medium text-highlighted">{{ decision.applicationNumber }} · {{ decision.programmeCode }}</p>
                <p class="mt-1 text-sm text-muted">{{ decision.programmeName }}</p>
                <p class="mt-1 text-xs text-muted">Rank {{ decision.rankPosition ?? 'Not assigned' }} · Quota {{ decision.quotaTypeCode ?? 'Not assigned' }} · {{ decision.reason }}</p>
              </div>
              <EmhareStatusPill :label="formatStatus(decision.decision)" :tone="decisionTone(decision.decision)" />
            </div>
            <UEmpty v-if="!decisions.length && !loading" title="No decisions in this round" description="Record evaluated choices, then approve the complete round before creating offer batches." />
          </div>
          </EmharePaginatedCollection>
        </section>
      </div>
    </template>
  </UDashboardPanel>

  <EmhareRecordDrawer v-model:open="roundModalOpen" title="Create selection round" description="Establish a governed decision window for one closed intake.">
    <template #body>
      <form id="selection-round-form" class="space-y-4" @submit.prevent="createSelectionRound">
        <UFormField label="Intake" required><USelect v-model="roundState.intakeId" :items="intakeItems" value-key="value" class="w-full" /></UFormField>
        <div class="grid gap-4 sm:grid-cols-2">
          <UFormField label="Round code" required><UInput v-model="roundState.code" placeholder="2027-R1" maxlength="50" class="w-full" /></UFormField>
          <UFormField label="Round name" required><UInput v-model="roundState.name" placeholder="First merit selection" maxlength="180" class="w-full" /></UFormField>
        </div>
        <UAlert color="warning" variant="soft" title="Intake must be closed" description="Close the intake in Academic calendar before creating its selection round." />
      </form>
    </template>
    <template #footer>
      <UButton label="Cancel" color="neutral" variant="outline" @click="roundModalOpen = false" />
      <UButton type="submit" form="selection-round-form" label="Create round" color="primary" :loading="activeActionId === 'create-round'" />
    </template>
  </EmhareRecordDrawer>

</template>
