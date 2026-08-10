<script setup lang="ts">
import Swal from 'sweetalert2'
import type {
  ProgressionDecisionSummary,
  ProgressionRosterSummary,
  ProgressionRuleSetSummary
} from '@emhare/portal-shell/types/assessment'

definePageMeta({ layout: 'dashboard' })

const api = useEmhareApi()
const auth = useEmhareAuth()
const toast = useToast()
const { showError } = useEmhareConfirm()
const academicPeriodContext = useAcademicPeriodContext()
const { studyPeriodLabel } = useProgrammeStudyPeriod()
const rosters = ref<ProgressionRosterSummary[]>([])
const ruleSets = ref<ProgressionRuleSetSummary[]>([])
const decisions = ref<ProgressionDecisionSummary[]>([])
const selectedRosterId = ref('')
const selectedRuleSetId = ref('')
const loading = ref(false)
const saving = ref(false)

const operatorUserId = computed(() => auth.currentUserProfile.value?.user.id ?? null)
const readyRosters = computed(() => rosters.value.filter(roster => roster.readyForProgression))
const rosterItems = computed(() => readyRosters.value.map(roster => ({
  label: `${roster.studentNumber} · ${roster.academicPeriodCode} · ${studyPeriodLabel(roster.programmePeriodNumber)} · ${roster.publishedModules}/${roster.eligibleModules} published`,
  value: roster.id
})))
const selectedRoster = computed(() => rosters.value.find(roster => roster.id === selectedRosterId.value) ?? null)
const applicableRules = computed(() => {
  if (!selectedRoster.value) return []
  return ruleSets.value.filter(rule => rule.status === 'APPROVED'
    && rule.programmeId === selectedRoster.value?.programmeId
    && rule.programmeVersionId === selectedRoster.value?.programmeVersionId
    && rule.programmePeriodNumber === selectedRoster.value?.programmePeriodNumber)
})
const ruleItems = computed(() => applicableRules.value.map(rule => ({
  label: `${rule.ruleCode} v${rule.ruleVersion} · ${rule.ruleName}`,
  value: rule.id
})))
const calculationGuidance = computed(() => {
  if (!readyRosters.value.length) return ['Publish a complete result set for every eligible registered Module before calculating progression.']
  if (!selectedRosterId.value) return ['Select the student result set to evaluate.']
  if (!ruleItems.value.length) return ['Approve a progression rule for this exact programme version, year of study, and semester.']
  if (!selectedRuleSetId.value) return ['Select the approved progression rule to apply.']
  return []
})
const queueCounts = computed(() => ({
  review: decisions.value.filter(item => item.status === 'CALCULATED').length,
  approval: decisions.value.filter(item => item.status === 'REVIEWED').length,
  publication: decisions.value.filter(item => item.status === 'APPROVED').length,
  published: decisions.value.filter(item => item.status === 'PUBLISHED').length
}))

watch(selectedRosterId, () => {
  selectedRuleSetId.value = applicableRules.value[0]?.id ?? ''
})

onMounted(load)
watch(academicPeriodContext.selectedAcademicPeriodId, () => void load())

async function load() {
  loading.value = true
  try {
    await academicPeriodContext.ensureAcademicPeriods()
    const [rosterResponse, ruleSetResponse, decisionResponse] = await Promise.all([
      api.request<ProgressionRosterSummary[]>('/api/results/progression/rosters'),
      api.request<ProgressionRuleSetSummary[]>('/api/results/progression/rule-sets'),
      api.request<ProgressionDecisionSummary[]>('/api/results/progression/decisions')
    ])
    rosters.value = rosterResponse.filter(roster => academicPeriodContext.matchesAcademicPeriod(roster))
    ruleSets.value = ruleSetResponse
    decisions.value = decisionResponse.filter(decision => academicPeriodContext.matchesAcademicPeriod(decision))
    if (!rosters.value.some(roster => roster.id === selectedRosterId.value)) selectedRosterId.value = ''
    if (!selectedRosterId.value && readyRosters.value.length) selectedRosterId.value = readyRosters.value[0]!.id
  } catch (error) {
    await showError('Progression workspace could not be loaded', api.errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function calculateDecision() {
  if (!selectedRosterId.value || !selectedRuleSetId.value) return
  const roster = selectedRoster.value
  const confirmation = await Swal.fire({
    title: 'Calculate progression decision?',
    text: 'The calculation will snapshot every current published Module result and apply the first matching outcome in the approved rule set.',
    icon: 'question',
    showCancelButton: true,
    confirmButtonText: 'Calculate decision',
    confirmButtonColor: '#006633'
  })
  if (!confirmation.isConfirmed) return
  saving.value = true
  try {
    await api.request('/api/results/progression/decisions', {
      method: 'POST',
      body: {
        registrationRosterImportId: selectedRosterId.value,
        progressionRuleSetId: selectedRuleSetId.value
      }
    })
    await load()
    toast.add({
      title: 'Progression decision calculated',
      description: `${roster?.studentNumber ?? 'Student'} is ready for independent academic review.`,
      color: 'success'
    })
  } catch (error) {
    await showError('Progression decision could not be calculated', api.errorMessage(error))
  } finally {
    saving.value = false
  }
}

function nextAction(decision: ProgressionDecisionSummary) {
  const actor = operatorUserId.value
  if (decision.status === 'CALCULATED') {
    return actor === decision.calculatedByUserId ? null : { action: 'review', label: 'Record independent review', title: 'Review progression decision?' }
  }
  if (decision.status === 'REVIEWED') {
    return actor === decision.calculatedByUserId || actor === decision.reviewedByUserId
      ? null
      : { action: 'approve', label: 'Approve decision', title: 'Approve progression decision?' }
  }
  if (decision.status === 'APPROVED') {
    return actor === decision.calculatedByUserId || actor === decision.reviewedByUserId || actor === decision.approvedByUserId
      ? null
      : { action: 'publish', label: 'Publish decision', title: 'Publish progression decision?' }
  }
  return null
}

function handoffMessage(decision: ProgressionDecisionSummary) {
  if (nextAction(decision)) return null
  if (decision.status === 'CALCULATED') return 'Handoff required: the calculator cannot review this decision.'
  if (decision.status === 'REVIEWED') return 'Handoff required: calculation and review actors cannot approve this decision.'
  if (decision.status === 'APPROVED') return 'Handoff required: publication requires a fourth independent actor.'
  return null
}

async function move(decision: ProgressionDecisionSummary, forcedAction?: 'reject') {
  const next = forcedAction
    ? { action: 'reject', label: 'Reject decision', title: 'Reject progression decision?' }
    : nextAction(decision)
  if (!next) return
  const result = await Swal.fire({
    title: next.title,
    text: next.action === 'publish'
      ? 'Publication makes this the controlled official progression decision. Later result corrections require a new append-only decision version.'
      : 'The actor, time, reason, and transition are retained in the decision history.',
    icon: next.action === 'reject' || next.action === 'publish' ? 'warning' : 'question',
    input: 'textarea',
    inputLabel: 'Decision reason',
    inputPlaceholder: 'Record evidence reviewed and the academic authority for this action.',
    showCancelButton: true,
    confirmButtonText: next.label,
    confirmButtonColor: next.action === 'reject' ? '#B42318' : '#006633',
    inputValidator: value => value.trim() ? undefined : 'A decision reason is required.'
  })
  if (!result.isConfirmed || !result.value?.trim()) return
  try {
    await api.request(`/api/results/progression/decisions/${decision.id}/${next.action}`, {
      method: 'POST',
      body: { expectedVersion: decision.version, reason: result.value.trim() }
    })
    await load()
    toast.add({
      title: next.action === 'publish' ? 'Progression decision published' : `Progression ${next.action} recorded`,
      description: decision.decisionNumber,
      color: 'success'
    })
  } catch (error) {
    await showError('Progression action could not be recorded', api.errorMessage(error))
  }
}

function statusColour(status: ProgressionDecisionSummary['status']) {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'APPROVED') return 'primary'
  if (status === 'REVIEWED') return 'warning'
  if (status === 'REJECTED') return 'error'
  return 'neutral'
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Programme progression decisions">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right><UButton label="Refresh" icon="i-lucide-refresh-cw" color="neutral" variant="outline" :loading="loading" @click="load" /></template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="space-y-5 p-4 sm:p-6">
        <UAlert
          color="primary"
          variant="soft"
          icon="i-lucide-route"
          title="Evidence-bound academic standing"
          description="Each decision snapshots a complete set of current published Module results, credit-weighted metrics, the exact approved rule version, and four independently controlled workflow stages."
        />

        <section class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <UCard :ui="{ body: 'p-4' }"><p class="text-xs uppercase text-info">Review</p><p class="mt-2 text-2xl font-semibold">{{ queueCounts.review }}</p></UCard>
          <UCard :ui="{ body: 'p-4' }"><p class="text-xs uppercase text-warning">Approval</p><p class="mt-2 text-2xl font-semibold">{{ queueCounts.approval }}</p></UCard>
          <UCard :ui="{ body: 'p-4' }"><p class="text-xs uppercase text-primary">Publication</p><p class="mt-2 text-2xl font-semibold">{{ queueCounts.publication }}</p></UCard>
          <UCard :ui="{ body: 'p-4' }"><p class="text-xs uppercase text-success">Published</p><p class="mt-2 text-2xl font-semibold">{{ queueCounts.published }}</p></UCard>
        </section>

        <UCard :ui="{ body: 'p-4' }">
          <div class="grid gap-3 lg:grid-cols-[1fr_1fr_auto] lg:items-end">
            <UFormField label="Complete published result set">
              <USelect v-model="selectedRosterId" :items="rosterItems" class="w-full" placeholder="Select student, year, and semester" />
            </UFormField>
            <UFormField label="Applicable approved rule">
              <USelect v-model="selectedRuleSetId" :items="ruleItems" class="w-full" placeholder="Select progression rule" />
            </UFormField>
            <EmhareGuidedActionButton label="Calculate decision" icon="i-lucide-calculator" :loading="saving" guidance-title="Progression calculation is not ready" :guidance-instructions="calculationGuidance" :guidance-action-label="selectedRosterId && !ruleItems.length ? 'Open Progression rules' : undefined" @guidance-action="navigateTo('/operations/progression-rules')" @click="calculateDecision" />
          </div>
          <p v-if="selectedRosterId && !ruleItems.length" class="mt-3 text-sm text-warning">No approved progression rule matches this exact programme version, year of study, and semester.</p>
        </UCard>

        <EmharePaginatedCollection v-slot="{ items: paginatedDecisions }" :items="decisions">
        <div class="space-y-4">
          <UCard v-for="decision in paginatedDecisions" :key="decision.id" :ui="{ body: 'p-4' }">
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p class="font-mono text-xs text-primary">{{ decision.decisionNumber }}</p>
                <h2 class="mt-1 text-lg font-semibold">{{ decision.studentNumber }} · {{ decision.decisionLabel }}</h2>
                <p class="mt-1 text-sm text-muted">{{ decision.academicPeriodCode }} · {{ studyPeriodLabel(decision.programmePeriodNumber) }} · decision version {{ decision.decisionVersion }}</p>
              </div>
              <UBadge :label="decision.status" :color="statusColour(decision.status)" variant="subtle" />
            </div>

            <div class="mt-4 grid gap-2 sm:grid-cols-3 xl:grid-cols-6">
              <div class="rounded-md border border-muted p-3"><p class="text-xs text-muted">Weighted average</p><p class="mt-1 font-semibold">{{ decision.weightedAverage }}%</p></div>
              <div class="rounded-md border border-muted p-3"><p class="text-xs text-muted">Attempted credits</p><p class="mt-1 font-semibold">{{ decision.attemptedCredits }}</p></div>
              <div class="rounded-md border border-muted p-3"><p class="text-xs text-muted">Passed credits</p><p class="mt-1 font-semibold text-success">{{ decision.passedCredits }}</p></div>
              <div class="rounded-md border border-muted p-3"><p class="text-xs text-muted">Failed credits</p><p class="mt-1 font-semibold" :class="decision.failedCredits ? 'text-error' : 'text-success'">{{ decision.failedCredits }}</p></div>
              <div class="rounded-md border border-muted p-3"><p class="text-xs text-muted">Failed Modules</p><p class="mt-1 font-semibold">{{ decision.failedModules }}</p></div>
              <div class="rounded-md border border-muted p-3"><p class="text-xs text-muted">Compulsory failures</p><p class="mt-1 font-semibold">{{ decision.failedCompulsoryModules }}</p></div>
            </div>

            <EmharePaginatedCollection v-slot="{ items: paginatedResults }" :items="decision.results">
            <div class="mt-4 overflow-x-auto">
              <table class="w-full min-w-[760px] text-sm">
                <thead class="text-left text-xs uppercase text-muted"><tr><th class="py-2">Module</th><th>Type</th><th>Credits</th><th>Final</th><th>Grade</th><th>Publication</th></tr></thead>
                <tbody>
                  <tr v-for="result in paginatedResults" :key="result.publishedResultId" class="border-t border-muted">
                    <td class="py-2"><p class="font-medium">{{ result.moduleCode }}</p><p class="text-xs text-muted">{{ result.moduleName }}</p></td>
                    <td>{{ result.curriculumModuleType }}</td><td>{{ result.creditValue }}</td>
                    <td class="font-semibold">{{ result.finalMark }}%</td><td>{{ result.grade }} · {{ result.remark }}</td><td>v{{ result.publicationVersion }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            </EmharePaginatedCollection>

            <div class="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-muted pt-4">
              <div><p class="text-xs font-medium text-primary">{{ decision.progressionRuleCode }} · {{ decision.decisionCode.replaceAll('_', ' ') }}</p><p class="mt-1 text-xs text-muted">{{ decision.statusReason }}</p><p v-if="handoffMessage(decision)" class="mt-1 text-xs text-warning">{{ handoffMessage(decision) }}</p></div>
              <div class="flex gap-2">
                <EmhareGuidedActionButton v-if="decision.status === 'CALCULATED' || decision.status === 'REVIEWED'" label="Reject" color="error" variant="soft" guidance-title="Independent actor required" :guidance-instructions="operatorUserId === decision.calculatedByUserId ? ['The operator who calculated this decision cannot reject it. Sign in as a different authorised academic operator.'] : []" @click="move(decision, 'reject')" />
                <UButton v-if="nextAction(decision)" :label="nextAction(decision)?.label" icon="i-lucide-arrow-right" @click="move(decision)" />
              </div>
            </div>
          </UCard>
        </div>
        </EmharePaginatedCollection>

        <UAlert v-if="!loading && !readyRosters.length" color="warning" variant="soft" title="No complete result set" description="Every eligible registered Module must have a current published result before progression can be calculated." />
        <UAlert v-if="!loading && !decisions.length" color="neutral" variant="soft" title="No progression decisions" description="Choose a complete result set and its approved programme-owned rule." />
      </div>
    </template>
  </UDashboardPanel>
</template>
