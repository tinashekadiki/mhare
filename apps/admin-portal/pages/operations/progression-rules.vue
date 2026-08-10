<script setup lang="ts">
import Swal from 'sweetalert2'
import type {
  ProgressionDecisionCode,
  ProgressionRosterSummary,
  ProgressionRuleSetSummary
} from '@emhare/portal-shell/types/assessment'

definePageMeta({ layout: 'dashboard' })

type OutcomeDraft = {
  priority: number
  decisionCode: ProgressionDecisionCode
  decisionLabel: string
  minimumWeightedAverage: number | null
  minimumPassedCredits: number | null
  maximumFailedCredits: number | null
  maximumFailedModules: number | null
  requireAllCompulsoryPassed: boolean
  nextProgrammePeriodNumber: number | null
  fallbackOutcome: boolean
}

const api = useEmhareApi()
const toast = useToast()
const { showError } = useEmhareConfirm()
const { fromProgrammePeriodNumber, studyPeriodLabel } = useProgrammeStudyPeriod()
const ruleSets = ref<ProgressionRuleSetSummary[]>([])
const rosters = ref<ProgressionRosterSummary[]>([])
const loading = ref(false)
const saving = ref(false)
const modalOpen = ref(false)
const form = reactive({ ruleCode: '', ruleName: '', rosterScopeId: '' })
const outcomes = ref<OutcomeDraft[]>([])
const nextStudyPeriodItems = computed(() => {
  const configuredMaximum = Math.max(8, ...rosters.value.map(roster => roster.programmePeriodNumber + 2))
  return [
    { label: 'No next year or semester', value: null },
    ...Array.from({ length: configuredMaximum }, (_, index) => ({
      label: studyPeriodLabel(index + 1),
      value: index + 1
    }))
  ]
})

const programmeScopeItems = computed(() => {
  const uniqueScopes = new Map<string, ProgressionRosterSummary>()
  for (const roster of rosters.value) {
    const key = `${roster.programmeVersionId}:${roster.programmePeriodNumber}`
    if (!uniqueScopes.has(key)) uniqueScopes.set(key, roster)
  }
  return [...uniqueScopes.values()].map(roster => ({
    label: `Programme ${shortId(roster.programmeId)} · version ${shortId(roster.programmeVersionId)} · ${studyPeriodLabel(roster.programmePeriodNumber)}`,
    value: roster.id
  }))
})
const ruleSetGuidance = computed(() => programmeScopeItems.value.length
  ? []
  : ['Confirm at least one student registration roster so its programme version, year of study, and semester can own progression rules.'])

onMounted(load)

async function load() {
  loading.value = true
  try {
    ;[ruleSets.value, rosters.value] = await Promise.all([
      api.request<ProgressionRuleSetSummary[]>('/api/results/progression/rule-sets'),
      api.request<ProgressionRosterSummary[]>('/api/results/progression/rosters')
    ])
  } catch (error) {
    await showError('Progression rules could not be loaded', api.errorMessage(error))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  const firstScope = rosters.value[0]
  const firstStudyPeriod = fromProgrammePeriodNumber(firstScope?.programmePeriodNumber ?? 1)
  form.ruleCode = firstScope ? `PROG-Y${firstStudyPeriod.yearOfStudy}-S${firstStudyPeriod.semesterNumber}` : ''
  form.ruleName = firstScope ? `${studyPeriodLabel(firstScope.programmePeriodNumber)} progression` : ''
  form.rosterScopeId = firstScope?.id ?? ''
  outcomes.value = defaultOutcomes(firstScope?.programmePeriodNumber ?? 1)
  modalOpen.value = true
}

function defaultOutcomes(programmePeriodNumber: number): OutcomeDraft[] {
  return [
    {
      priority: 1,
      decisionCode: 'PROCEED',
      decisionLabel: 'Proceed to the next semester',
      minimumWeightedAverage: 50,
      minimumPassedCredits: null,
      maximumFailedCredits: 0,
      maximumFailedModules: 0,
      requireAllCompulsoryPassed: true,
      nextProgrammePeriodNumber: programmePeriodNumber + 1,
      fallbackOutcome: false
    },
    {
      priority: 2,
      decisionCode: 'PROCEED_WITH_CARRY',
      decisionLabel: 'Proceed with carry Modules',
      minimumWeightedAverage: 50,
      minimumPassedCredits: null,
      maximumFailedCredits: 12,
      maximumFailedModules: 1,
      requireAllCompulsoryPassed: false,
      nextProgrammePeriodNumber: programmePeriodNumber + 1,
      fallbackOutcome: false
    },
    {
      priority: 3,
      decisionCode: 'REPEAT',
      decisionLabel: 'Repeat the current semester',
      minimumWeightedAverage: null,
      minimumPassedCredits: null,
      maximumFailedCredits: null,
      maximumFailedModules: null,
      requireAllCompulsoryPassed: false,
      nextProgrammePeriodNumber: programmePeriodNumber,
      fallbackOutcome: true
    }
  ]
}

async function createRuleSet() {
  const scope = rosters.value.find(roster => roster.id === form.rosterScopeId)
  if (!scope || !form.ruleCode.trim() || !form.ruleName.trim()) return
  saving.value = true
  try {
    await api.request('/api/results/progression/rule-sets', {
      method: 'POST',
      body: {
        ruleCode: form.ruleCode.trim(),
        ruleName: form.ruleName.trim(),
        programmeId: scope.programmeId,
        programmeVersionId: scope.programmeVersionId,
        programmePeriodNumber: scope.programmePeriodNumber,
        outcomes: outcomes.value
      }
    })
    modalOpen.value = false
    await load()
    toast.add({
      title: 'Draft progression rule created',
      description: 'Review every ordered threshold before academic approval.',
      color: 'success'
    })
  } catch (error) {
    await showError('Progression rule could not be created', api.errorMessage(error))
  } finally {
    saving.value = false
  }
}

async function approve(ruleSet: ProgressionRuleSetSummary) {
  const result = await Swal.fire({
    title: 'Approve progression rule set?',
    text: 'Approval locks every threshold. It will supersede the currently approved rule for this programme version, year of study, and semester.',
    icon: 'warning',
    input: 'textarea',
    inputLabel: 'Academic authority and approval reason',
    inputPlaceholder: 'Record the board, policy reference, and approval basis.',
    showCancelButton: true,
    confirmButtonText: 'Approve rule set',
    confirmButtonColor: '#006633',
    inputValidator: value => value.trim() ? undefined : 'An approval reason is required.'
  })
  if (!result.isConfirmed || !result.value?.trim()) return
  try {
    await api.request(`/api/results/progression/rule-sets/${ruleSet.id}/approve`, {
      method: 'POST',
      body: { expectedVersion: ruleSet.version, reason: result.value.trim() }
    })
    await load()
    toast.add({
      title: 'Progression rule approved',
      description: `${ruleSet.ruleCode} v${ruleSet.ruleVersion} is now authoritative.`,
      color: 'success'
    })
  } catch (error) {
    await showError('Progression rule could not be approved', api.errorMessage(error))
  }
}

function thresholdLabel(outcome: ProgressionRuleSetSummary['outcomes'][number]) {
  if (outcome.fallbackOutcome) return 'Final fallback when no earlier outcome matches'
  const thresholds = []
  if (outcome.minimumWeightedAverage != null) thresholds.push(`average ≥ ${outcome.minimumWeightedAverage}%`)
  if (outcome.minimumPassedCredits != null) thresholds.push(`passed credits ≥ ${outcome.minimumPassedCredits}`)
  if (outcome.maximumFailedCredits != null) thresholds.push(`failed credits ≤ ${outcome.maximumFailedCredits}`)
  if (outcome.maximumFailedModules != null) thresholds.push(`failed Modules ≤ ${outcome.maximumFailedModules}`)
  if (outcome.requireAllCompulsoryPassed) thresholds.push('all compulsory Modules passed')
  return thresholds.join(' · ')
}

function shortId(value: string) {
  return value.slice(0, 8)
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Progression rule governance">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right>
          <EmhareGuidedActionButton label="New rule set" icon="i-lucide-plus" guidance-title="Progression rule setup required" :guidance-instructions="ruleSetGuidance" guidance-action-label="Open Student registration" @guidance-action="navigateTo('/operations/student-registrations')" @click="openCreate" />
          <UButton label="Refresh" icon="i-lucide-refresh-cw" color="neutral" variant="outline" :loading="loading" @click="load" />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="space-y-5 p-4 sm:p-6">
        <UAlert
          color="primary"
          variant="soft"
          icon="i-lucide-list-tree"
          title="Versioned, programme-owned progression policy"
          description="Rules are scoped to an exact programme version, year of study, and semester. Ordered relational thresholds produce one deterministic decision, with a mandatory final fallback and immutable approval evidence."
        />

        <EmharePaginatedCollection :items="ruleSets" v-slot="{ items: paginatedRuleSets }">
          <div class="grid gap-4 xl:grid-cols-2">
          <UCard v-for="ruleSet in paginatedRuleSets" :key="ruleSet.id" :ui="{ body: 'p-4' }">
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p class="text-xs font-medium text-primary">{{ ruleSet.ruleCode }} · version {{ ruleSet.ruleVersion }}</p>
                <h2 class="mt-1 font-semibold">{{ ruleSet.ruleName }}</h2>
                <p class="mt-1 text-xs text-muted">Programme {{ shortId(ruleSet.programmeId) }} · {{ studyPeriodLabel(ruleSet.programmePeriodNumber) }}</p>
              </div>
              <UBadge :label="ruleSet.status" :color="ruleSet.status === 'APPROVED' ? 'success' : ruleSet.status === 'DRAFT' ? 'warning' : 'neutral'" variant="subtle" />
            </div>
            <EmharePaginatedCollection :items="ruleSet.outcomes" :initial-page-size="5" v-slot="{ items: paginatedOutcomes }">
            <div class="mt-4 space-y-2">
              <div v-for="outcome in paginatedOutcomes" :key="outcome.id" class="rounded-md border border-muted p-3">
                <div class="flex items-start justify-between gap-3">
                  <div>
                    <p class="text-sm font-medium">{{ outcome.priority }}. {{ outcome.decisionLabel }}</p>
                    <p class="mt-1 text-xs text-muted">{{ thresholdLabel(outcome) }}</p>
                  </div>
                  <UBadge :label="outcome.decisionCode.replaceAll('_', ' ')" color="neutral" variant="soft" />
                </div>
              </div>
            </div>
            </EmharePaginatedCollection>
            <div v-if="ruleSet.status === 'DRAFT'" class="mt-4 flex justify-end">
              <UButton label="Approve rule set" icon="i-lucide-badge-check" @click="approve(ruleSet)" />
            </div>
          </UCard>
          </div>
        </EmharePaginatedCollection>

        <UAlert v-if="!loading && !programmeScopeItems.length" color="warning" variant="soft" title="No registered programme scope" description="A confirmed student registration roster is required before its programme version, year of study, and semester can own progression rules." />
        <UAlert v-else-if="!loading && !ruleSets.length" color="neutral" variant="soft" title="No progression rules" description="Create the first programme-owned progression policy." />
      </div>
    </template>
  </UDashboardPanel>

  <EmhareRecordDrawer v-model:open="modalOpen" title="Create progression rule set" description="Define ordered outcomes for one programme version, year of study, and semester" width="xl">
    <template #body>
      <div class="space-y-4">
        <div class="grid gap-3 md:grid-cols-3">
          <UFormField label="Programme scope" class="md:col-span-3">
            <USelect v-model="form.rosterScopeId" :items="programmeScopeItems" class="w-full" />
          </UFormField>
          <UFormField label="Rule code"><UInput v-model="form.ruleCode" class="w-full" /></UFormField>
          <UFormField label="Rule name" class="md:col-span-2"><UInput v-model="form.ruleName" class="w-full" /></UFormField>
        </div>

        <div v-for="(outcome, index) in outcomes" :key="index" class="rounded-lg border border-muted p-3">
          <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
            <UFormField label="Priority"><UInput v-model.number="outcome.priority" type="number" min="1" class="w-full" /></UFormField>
            <UFormField label="Decision">
              <USelect v-model="outcome.decisionCode" :items="['PROCEED', 'PROCEED_WITH_CARRY', 'REPEAT', 'EXCLUDE']" class="w-full" />
            </UFormField>
            <UFormField label="Decision label" class="md:col-span-2"><UInput v-model="outcome.decisionLabel" class="w-full" /></UFormField>
            <UFormField label="Minimum average %"><UInput v-model.number="outcome.minimumWeightedAverage" type="number" min="0" max="100" step="0.01" class="w-full" :disabled="outcome.fallbackOutcome" /></UFormField>
            <UFormField label="Minimum passed credits"><UInput v-model.number="outcome.minimumPassedCredits" type="number" min="0" step="0.01" class="w-full" :disabled="outcome.fallbackOutcome" /></UFormField>
            <UFormField label="Maximum failed credits"><UInput v-model.number="outcome.maximumFailedCredits" type="number" min="0" step="0.01" class="w-full" :disabled="outcome.fallbackOutcome" /></UFormField>
            <UFormField label="Maximum failed Modules"><UInput v-model.number="outcome.maximumFailedModules" type="number" min="0" class="w-full" :disabled="outcome.fallbackOutcome" /></UFormField>
            <UFormField label="Next year and semester"><USelect v-model="outcome.nextProgrammePeriodNumber" :items="nextStudyPeriodItems" value-key="value" class="w-full" /></UFormField>
            <UFormField label="All compulsory passed"><USwitch v-model="outcome.requireAllCompulsoryPassed" :disabled="outcome.fallbackOutcome" /></UFormField>
            <UFormField label="Final fallback"><USwitch v-model="outcome.fallbackOutcome" /></UFormField>
            <div class="flex items-end justify-end"><EmhareGuidedActionButton label="Remove" color="error" variant="soft" guidance-title="Outcome cannot be removed" :guidance-instructions="outcomes.length <= 2 ? ['A progression rule set must retain at least one conditional outcome and one final fallback outcome.'] : []" @click="outcomes.splice(index, 1)" /></div>
          </div>
        </div>
        <UButton label="Add outcome" icon="i-lucide-plus" color="neutral" variant="outline" @click="outcomes.push({ priority: outcomes.length + 1, decisionCode: 'EXCLUDE', decisionLabel: '', minimumWeightedAverage: null, minimumPassedCredits: null, maximumFailedCredits: null, maximumFailedModules: null, requireAllCompulsoryPassed: false, nextProgrammePeriodNumber: null, fallbackOutcome: false })" />
      </div>
    </template>
    <template #footer>
      <div class="flex w-full justify-end gap-2">
        <UButton label="Cancel" color="neutral" variant="outline" @click="modalOpen = false" />
        <EmhareGuidedActionButton label="Save draft rule set" :loading="saving" guidance-title="Progression rule details are incomplete" :guidance-instructions="[...(!form.rosterScopeId ? ['Select a programme scope.'] : []), ...(!form.ruleCode.trim() ? ['Enter a rule code.'] : []), ...(!form.ruleName.trim() ? ['Enter a rule name.'] : [])]" @click="createRuleSet" />
      </div>
    </template>
  </EmhareRecordDrawer>
</template>
