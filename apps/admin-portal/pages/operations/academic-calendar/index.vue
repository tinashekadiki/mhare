<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { AcademicPeriodSummary, AcademicPeriodTypeSummary, AcademicYearSummary, IntakeSummary } from '@emhare/portal-shell/types/academic'

definePageMeta({ layout: 'dashboard' })

const api = useEmhareApi()
const toast = useToast()
const { confirmAction, showError } = useEmhareConfirm()
const academicSetup = useAcademicSetup()
const saving = ref(false)
const activeDataset = ref('years')
const activeYearActionId = ref<string | null>(null)
const activeCalendarActionKey = ref<string | null>(null)
const yearModalOpen = ref(false)
const periodTypeModalOpen = ref(false)
const periodModalOpen = ref(false)
const intakeModalOpen = ref(false)
const yearForm = reactive({ id: null as string | null, name: '', startDate: '', endDate: '', changeReason: '', expectedVersion: 0 })
const periodTypeForm = reactive({ id: null as string | null, code: '', name: '', sortOrder: 1, changeReason: '', expectedVersion: 0 })
const periodForm = reactive({ id: null as string | null, status: 'DRAFT' as AcademicPeriodSummary['status'], academicYearId: '', academicPeriodTypeId: '', code: '', name: '', startDate: '', endDate: '', changeReason: '', expectedVersion: 0 })
const intakeForm = reactive({
  id: null as string | null,
  status: 'DRAFT' as IntakeSummary['status'],
  academicYearId: '',
  code: '',
  name: '',
  startsOn: '',
  endsOn: '',
  offerAcceptanceDeadline: '',
  registrationDate: '',
  orientationDate: '',
  commencementDate: '',
  maximumProgrammeChoices: 3,
  programmeLevelIds: [] as string[],
  programmeIds: [] as string[],
  changeReason: '',
  expectedVersion: 0
})

const yearColumns: TableColumn<AcademicYearSummary>[] = [
  { accessorKey: 'name', header: 'Academic year' },
  { accessorKey: 'startDate', header: 'Starts' },
  { accessorKey: 'endDate', header: 'Ends' },
  { accessorKey: 'status', header: 'Status' },
  { id: 'actions', header: 'Actions' }
]
const periodColumns: TableColumn<AcademicPeriodSummary>[] = [
  { accessorKey: 'code', header: 'Code' },
  { accessorKey: 'name', header: 'Academic period' },
  { accessorKey: 'academicYearName', header: 'Year' },
  { accessorKey: 'academicPeriodTypeName', header: 'Type' },
  { accessorKey: 'startDate', header: 'Dates' },
  { accessorKey: 'status', header: 'Status' },
  { id: 'actions', header: 'Actions' }
]
const periodTypeColumns: TableColumn<AcademicPeriodTypeSummary>[] = [
  { accessorKey: 'sortOrder', header: 'Order' },
  { accessorKey: 'code', header: 'Code' },
  { accessorKey: 'name', header: 'Period type' },
  { accessorKey: 'status', header: 'Status' },
  { id: 'actions', header: 'Actions' }
]
const intakeColumns: TableColumn<IntakeSummary>[] = [
  { accessorKey: 'code', header: 'Code' },
  { accessorKey: 'name', header: 'Intake' },
  { accessorKey: 'academicYearName', header: 'Academic year' },
  { id: 'eligibility', header: 'Programme eligibility' },
  { accessorKey: 'maximumProgrammeChoices', header: 'Maximum choices' },
  { accessorKey: 'startsOn', header: 'Application window' },
  { accessorKey: 'status', header: 'Status' },
  { id: 'actions', header: 'Actions' }
]

const overview = computed(() => academicSetup.overview.value)
const academicYears = computed(() => overview.value?.academicYears ?? [])
const academicPeriods = computed(() => overview.value?.academicPeriods ?? [])
const periodTypes = computed(() => overview.value?.academicPeriodTypes ?? [])
const intakes = computed(() => overview.value?.intakes ?? [])
const programmeLevels = computed(() => overview.value?.programmeLevels ?? [])
const programmes = computed(() => overview.value?.programmes ?? [])
const yearItems = computed(() => academicYears.value.filter(year => year.status !== 'ARCHIVED').map(year => ({ label: year.name, value: year.id })))
const periodTypeItems = computed(() => periodTypes.value.filter(type => type.status === 'ACTIVE').map(type => ({ label: type.name, value: type.id })))
const programmeLevelItems = computed(() => programmeLevels.value
  .filter(level => level.status === 'ACTIVE' || intakeForm.programmeLevelIds.includes(level.id))
  .map(level => ({ label: `${level.code} · ${level.name}`, value: level.id })))
const specificProgrammeItems = computed(() => programmes.value
  .filter(programme => intakeForm.programmeLevelIds.includes(programme.programmeLevelId))
  .filter(programme => programme.status === 'ACTIVE' || intakeForm.programmeIds.includes(programme.id))
  .map(programme => ({ label: `${programme.code} · ${programme.name}`, value: programme.id })))
const periodTypeCodeLocked = computed(() => Boolean(periodTypeForm.id && academicPeriods.value.some(period => period.academicPeriodTypeId === periodTypeForm.id)))
const datasets = computed(() => [
  { label: 'Academic years', value: 'years', icon: 'i-lucide-calendar-range', badge: academicYears.value.length },
  { label: 'Period types', value: 'period-types', icon: 'i-lucide-tag', badge: periodTypes.value.length },
  { label: 'Academic periods', value: 'periods', icon: 'i-lucide-calendar-days', badge: academicPeriods.value.length },
  { label: 'Intakes', value: 'intakes', icon: 'i-lucide-log-in', badge: intakes.value.length }
])
const academicPeriodGuidance = computed(() => {
  const instructions: string[] = []
  if (!academicYears.value.length) instructions.push('Create an academic year before creating an academic period.')
  if (!periodTypes.value.length) instructions.push('Create an academic period type such as Semester, Term, Block, or Session.')
  return instructions
})
const intakeGuidance = computed(() => {
  const instructions: string[] = []
  if (!academicYears.value.length) instructions.push('Create an academic year before creating an intake.')
  if (!programmeLevelItems.value.length) instructions.push('Create an active Programme Level before defining intake eligibility.')
  return instructions
})
const intakeGuidanceActionLabel = computed(() => !academicYears.value.length
  ? 'Open Academic years'
  : 'Open Programme catalogue')

onMounted(async () => {
  try { await academicSetup.ensureOverview() } catch { /* Render shared error. */ }
})

function createAcademicYear() {
  Object.assign(yearForm, { id: null, name: '', startDate: '', endDate: '', changeReason: '', expectedVersion: 0 })
  yearModalOpen.value = true
}

function editAcademicYear(year: AcademicYearSummary) {
  Object.assign(yearForm, { id: year.id, name: year.name, startDate: year.startDate, endDate: year.endDate, changeReason: '', expectedVersion: year.version })
  yearModalOpen.value = true
}

function createPeriodType() {
  Object.assign(periodTypeForm, { id: null, code: '', name: '', sortOrder: periodTypes.value.length + 1, changeReason: '', expectedVersion: 0 })
  periodTypeModalOpen.value = true
}

function editPeriodType(periodType: AcademicPeriodTypeSummary) {
  Object.assign(periodTypeForm, { id: periodType.id, code: periodType.code, name: periodType.name, sortOrder: periodType.sortOrder, changeReason: '', expectedVersion: periodType.version })
  periodTypeModalOpen.value = true
}

function createAcademicPeriod() {
  Object.assign(periodForm, {
    id: null, status: 'DRAFT', academicYearId: academicYears.value[0]?.id ?? '',
    academicPeriodTypeId: periodTypes.value[0]?.id ?? '', code: '', name: '',
    startDate: '', endDate: '', changeReason: '', expectedVersion: 0
  })
  periodModalOpen.value = true
}

function editAcademicPeriod(period: AcademicPeriodSummary) {
  Object.assign(periodForm, {
    id: period.id, status: period.status, academicYearId: period.academicYearId,
    academicPeriodTypeId: period.academicPeriodTypeId, code: period.code, name: period.name,
    startDate: period.startDate, endDate: period.endDate, changeReason: '', expectedVersion: period.version
  })
  periodModalOpen.value = true
}

function createIntake() {
  return navigateTo('/operations/academic-calendar/intakes/new')
}

function editIntake(intake: IntakeSummary) {
  if (intake.status === 'DRAFT') {
    return navigateTo(`/operations/academic-calendar/intakes/${intake.id}`)
  }
  Object.assign(intakeForm, {
    id: intake.id, status: intake.status, academicYearId: intake.academicYearId, code: intake.code,
    name: intake.name, startsOn: intake.startsOn, endsOn: intake.endsOn,
    offerAcceptanceDeadline: intake.offerAcceptanceDeadline?.slice(0, 10) ?? '',
    registrationDate: intake.registrationDate ?? '', orientationDate: intake.orientationDate ?? '',
    commencementDate: intake.commencementDate ?? '',
    maximumProgrammeChoices: intake.maximumProgrammeChoices,
    programmeLevelIds: intake.programmeLevels.map(programmeLevel => programmeLevel.id),
    programmeIds: intake.specificProgrammes.map(programme => programme.id),
    changeReason: '', expectedVersion: intake.version
  })
  intakeModalOpen.value = true
}

async function saveRecord(path: string, id: string | null, createBody: object, updateBody: object, closeModal: () => void, recordLabel: string) {
  saving.value = true
  try {
    await api.request(id ? `${path}/${id}` : path, { method: id ? 'PUT' : 'POST', body: id ? updateBody : createBody })
    await academicSetup.loadOverview()
    closeModal()
    toast.add({ title: `${recordLabel} ${id ? 'updated' : 'created'}`, color: 'success', icon: 'i-lucide-calendar-check' })
  } catch (error) {
    await showError(`${recordLabel} could not be ${id ? 'updated' : 'created'}`, api.errorMessage(error))
  } finally {
    saving.value = false
  }
}

function saveAcademicYear() {
  return saveRecord('/api/academic/years', yearForm.id,
    { name: yearForm.name, startDate: yearForm.startDate, endDate: yearForm.endDate },
    { name: yearForm.name, startDate: yearForm.startDate, endDate: yearForm.endDate, changeReason: yearForm.changeReason, expectedVersion: yearForm.expectedVersion },
    () => yearModalOpen.value = false, 'Academic year')
}

function savePeriodType() {
  return saveRecord('/api/academic/period-types', periodTypeForm.id,
    { code: periodTypeForm.code, name: periodTypeForm.name, sortOrder: periodTypeForm.sortOrder },
    { code: periodTypeForm.code, name: periodTypeForm.name, sortOrder: periodTypeForm.sortOrder, changeReason: periodTypeForm.changeReason, expectedVersion: periodTypeForm.expectedVersion },
    () => periodTypeModalOpen.value = false, 'Academic period type')
}

function saveAcademicPeriod() {
  const values = { academicYearId: periodForm.academicYearId, academicPeriodTypeId: periodForm.academicPeriodTypeId, code: periodForm.code, name: periodForm.name, startDate: periodForm.startDate, endDate: periodForm.endDate }
  return saveRecord('/api/academic/periods', periodForm.id, values,
    { ...values, changeReason: periodForm.changeReason, expectedVersion: periodForm.expectedVersion },
    () => periodModalOpen.value = false, 'Academic period')
}

async function saveIntakeCorrection() {
  const values = {
    academicYearId: intakeForm.academicYearId,
    code: intakeForm.code,
    name: intakeForm.name,
    startsOn: intakeForm.startsOn,
    endsOn: intakeForm.endsOn,
    offerAcceptanceDeadline: `${intakeForm.offerAcceptanceDeadline}T21:59:59Z`,
    registrationDate: intakeForm.registrationDate || null,
    orientationDate: intakeForm.orientationDate || null,
    commencementDate: intakeForm.commencementDate,
    maximumProgrammeChoices: intakeForm.maximumProgrammeChoices,
    programmeLevelIds: intakeForm.programmeLevelIds,
    programmeIds: intakeForm.programmeIds
  }
  return saveRecord('/api/academic/intakes', intakeForm.id, values,
    { ...values, changeReason: intakeForm.changeReason, expectedVersion: intakeForm.expectedVersion },
    () => intakeModalOpen.value = false, 'Intake')
}

function handleIntakeGuidanceAction() {
  if (!academicYears.value.length) {
    activeDataset.value = 'years'
    return
  }
  return navigateTo('/operations/programmes')
}

async function changeAcademicYearStatus(year: AcademicYearSummary, action: 'open' | 'close') {
  const confirmed = await confirmAction({
    title: `${action === 'open' ? 'Open' : 'Close'} ${year.name}?`,
    text: action === 'open'
      ? 'The year becomes available to controlled downstream operational setup.'
      : 'New operational activity should no longer be attached to this year.',
    confirmButtonText: action === 'open' ? 'Open year' : 'Close year',
    icon: action === 'open' ? 'question' : 'warning'
  })
  if (!confirmed) return
  activeYearActionId.value = year.id
  try {
    await api.request(`/api/academic/years/${year.id}/${action}`, { method: 'POST', body: { expectedVersion: year.version } })
    await academicSetup.loadOverview()
    toast.add({ title: `Academic year ${action === 'open' ? 'opened' : 'closed'}`, color: 'success' })
  } catch (error) {
    await showError('Academic year status could not be changed', api.errorMessage(error))
  } finally {
    activeYearActionId.value = null
  }
}

async function changeCalendarRecordStatus(
  record: AcademicPeriodSummary | IntakeSummary,
  recordType: 'periods' | 'intakes',
  action: 'open' | 'close'
) {
  const recordLabel = recordType === 'periods' ? 'academic period' : 'intake'
  const confirmed = await confirmAction({
    title: `${action === 'open' ? 'Open' : 'Close'} ${record.name}?`,
    text: action === 'open'
      ? `The ${recordLabel} becomes available to downstream operations while its academic year remains open.`
      : `The ${recordLabel} will no longer accept new operational activity.`,
    confirmButtonText: `${action === 'open' ? 'Open' : 'Close'} ${recordLabel}`,
    icon: action === 'open' ? 'question' : 'warning'
  })
  if (!confirmed) return
  activeCalendarActionKey.value = `${recordType}-${record.id}`
  try {
    await api.request(`/api/academic/${recordType}/${record.id}/${action}`, {
      method: 'POST',
      body: { expectedVersion: record.version }
    })
    await academicSetup.loadOverview()
    toast.add({ title: `${record.name} ${action === 'open' ? 'opened' : 'closed'}`, color: 'success' })
  } catch (error) {
    await showError(`${record.name} could not be ${action === 'open' ? 'opened' : 'closed'}`, api.errorMessage(error))
  } finally {
    activeCalendarActionKey.value = null
  }
}

function calendarTone(status: string) {
  if (status === 'OPEN') return 'success' as const
  if (status === 'CLOSED') return 'warning' as const
  if (status === 'ARCHIVED') return 'neutral' as const
  return 'info' as const
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('en-ZW', { day: '2-digit', month: 'short', year: 'numeric' }).format(new Date(`${value}T00:00:00`))
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Academic calendar">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right>
          <UButton label="Refresh" icon="i-lucide-refresh-cw" color="neutral" variant="outline" :loading="academicSetup.loading.value" @click="academicSetup.loadOverview" />
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left><span class="text-sm text-muted">Shared dates for Admissions, Registration, Finance, Exams, and Accommodation</span></template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="space-y-5 p-4 sm:p-6">
        <div class="grid gap-3 sm:grid-cols-4">
          <EmhareKpiCard label="Academic years" :value="academicYears.length" icon="i-lucide-calendar-range" tone="primary" />
          <EmhareKpiCard label="Open years" :value="academicYears.filter(year => year.status === 'OPEN').length" icon="i-lucide-calendar-check" tone="success" />
          <EmhareKpiCard label="Academic periods" :value="academicPeriods.length" icon="i-lucide-calendar-days" tone="primary" />
          <EmhareKpiCard label="Intakes" :value="intakes.length" icon="i-lucide-log-in" tone="warning" />
        </div>
        <UAlert v-if="academicSetup.loadError.value" color="error" variant="soft" icon="i-lucide-circle-alert" title="Academic calendar unavailable" :description="academicSetup.loadError.value" />

        <UTabs v-model="activeDataset" :items="datasets" :content="false" color="primary" variant="pill" />

        <EmhareRegisterPanel
          v-if="activeDataset === 'years'"
          title="Academic years"
          description="Lifecycle control for institution-wide operational dates."
          :record-count="academicYears.length"
        >
          <template #actions><UButton label="Create academic year" icon="i-lucide-plus" color="primary" @click="createAcademicYear" /></template>
          <div class="overflow-hidden rounded-md border border-muted">
            <EmharePaginatedTable :data="academicYears" :columns="yearColumns" :loading="academicSetup.loading.value">
            <template #startDate-cell="{ row }">{{ formatDate(row.original.startDate) }}</template>
            <template #endDate-cell="{ row }">{{ formatDate(row.original.endDate) }}</template>
            <template #status-cell="{ row }"><EmhareStatusPill :label="row.original.status" :tone="calendarTone(row.original.status)" /></template>
            <template #actions-cell="{ row }">
              <div class="flex justify-end gap-1">
                <UButton label="Edit" icon="i-lucide-pencil" color="neutral" variant="ghost" @click="editAcademicYear(row.original)" />
                <UButton v-if="row.original.status === 'DRAFT'" label="Open" color="primary" variant="ghost" :loading="activeYearActionId === row.original.id" @click="changeAcademicYearStatus(row.original, 'open')" />
                <UButton v-else-if="row.original.status === 'OPEN'" label="Close" color="warning" variant="ghost" :loading="activeYearActionId === row.original.id" @click="changeAcademicYearStatus(row.original, 'close')" />
              </div>
            </template>
            <template #empty><div class="py-10 text-center text-sm text-muted">Create an academic year to begin calendar setup.</div></template>
            </EmharePaginatedTable>
          </div>
        </EmhareRegisterPanel>

        <EmhareRegisterPanel
          v-if="activeDataset === 'period-types'"
          title="Academic period types"
          description="Controlled definitions such as semester, term, block, and session."
          :record-count="periodTypes.length"
        >
          <template #actions><UButton label="Create period type" icon="i-lucide-plus" color="primary" @click="createPeriodType" /></template>
          <div class="overflow-hidden rounded-md border border-muted">
            <EmharePaginatedTable :data="periodTypes" :columns="periodTypeColumns" :loading="academicSetup.loading.value">
              <template #status-cell="{ row }"><EmhareStatusPill :label="row.original.status" :tone="row.original.status === 'ACTIVE' ? 'success' : 'neutral'" /></template>
              <template #actions-cell="{ row }"><div class="flex justify-end"><UButton label="Edit" icon="i-lucide-pencil" color="neutral" variant="ghost" @click="editPeriodType(row.original)" /></div></template>
              <template #empty><div class="py-10 text-center text-sm text-muted">Create a period type before adding academic periods.</div></template>
            </EmharePaginatedTable>
          </div>
        </EmhareRegisterPanel>

        <EmhareRegisterPanel
          v-if="activeDataset === 'periods'"
          title="Academic periods"
          description="Periods must remain within their academic year."
          :record-count="academicPeriods.length"
        >
          <template #actions><EmhareGuidedActionButton label="Create academic period" icon="i-lucide-plus" color="primary" guidance-title="Academic period setup required" :guidance-instructions="academicPeriodGuidance" :guidance-action-label="!academicYears.length ? 'Open Academic years' : 'Open Period types'" @guidance-action="activeDataset = !academicYears.length ? 'years' : 'period-types'" @click="createAcademicPeriod" /></template>
          <div class="overflow-hidden rounded-md border border-muted">
            <EmharePaginatedTable :data="academicPeriods" :columns="periodColumns" :loading="academicSetup.loading.value">
              <template #startDate-cell="{ row }"><span class="whitespace-nowrap">{{ formatDate(row.original.startDate) }} – {{ formatDate(row.original.endDate) }}</span></template>
              <template #status-cell="{ row }"><EmhareStatusPill :label="row.original.status" :tone="calendarTone(row.original.status)" /></template>
              <template #actions-cell="{ row }">
                <div class="flex justify-end gap-1">
                  <UButton label="Edit" icon="i-lucide-pencil" color="neutral" variant="ghost" @click="editAcademicPeriod(row.original)" />
                  <UButton v-if="row.original.status === 'DRAFT'" label="Open" color="primary" variant="ghost" :loading="activeCalendarActionKey === `periods-${row.original.id}`" @click="changeCalendarRecordStatus(row.original, 'periods', 'open')" />
                  <UButton v-else-if="row.original.status === 'OPEN'" label="Close" color="warning" variant="ghost" :loading="activeCalendarActionKey === `periods-${row.original.id}`" @click="changeCalendarRecordStatus(row.original, 'periods', 'close')" />
                </div>
              </template>
              <template #empty><div class="py-8 text-center text-sm text-muted">No academic periods configured.</div></template>
            </EmharePaginatedTable>
          </div>
        </EmhareRegisterPanel>

        <EmhareRegisterPanel
          v-if="activeDataset === 'intakes'"
          title="Intakes"
          description="Admission entry cohorts linked to the shared calendar."
          :record-count="intakes.length"
        >
          <template #actions><EmhareGuidedActionButton label="Create intake" icon="i-lucide-plus" color="primary" guidance-title="Intake setup required" :guidance-instructions="intakeGuidance" :guidance-action-label="intakeGuidanceActionLabel" @guidance-action="handleIntakeGuidanceAction" @click="createIntake" /></template>
          <div class="overflow-hidden rounded-md border border-muted">
            <EmharePaginatedTable :data="intakes" :columns="intakeColumns" :loading="academicSetup.loading.value">
              <template #startsOn-cell="{ row }"><span class="whitespace-nowrap">{{ formatDate(row.original.startsOn) }} – {{ formatDate(row.original.endsOn) }}</span></template>
              <template #eligibility-cell="{ row }">
                <div class="min-w-48">
                  <p class="font-medium text-highlighted">{{ row.original.programmeLevels.length }} Programme Level{{ row.original.programmeLevels.length === 1 ? '' : 's' }}</p>
                  <p class="text-xs text-muted">
                    {{ row.original.allProgrammesInSelectedLevels ? 'All active Programmes in selected levels' : `${row.original.specificProgrammes.length} specific Programme${row.original.specificProgrammes.length === 1 ? '' : 's'}` }}
                  </p>
                </div>
              </template>
              <template #status-cell="{ row }"><EmhareStatusPill :label="row.original.status" :tone="calendarTone(row.original.status)" /></template>
              <template #actions-cell="{ row }">
                <div class="flex justify-end gap-1">
                  <UButton :label="row.original.status === 'DRAFT' ? 'Continue setup' : 'Edit'" :icon="row.original.status === 'DRAFT' ? 'i-lucide-list-checks' : 'i-lucide-pencil'" color="neutral" variant="ghost" @click="editIntake(row.original)" />
                  <UButton v-if="row.original.status === 'OPEN'" label="Close" color="warning" variant="ghost" :loading="activeCalendarActionKey === `intakes-${row.original.id}`" @click="changeCalendarRecordStatus(row.original, 'intakes', 'close')" />
                </div>
              </template>
              <template #empty><div class="py-8 text-center text-sm text-muted">No intakes configured.</div></template>
            </EmharePaginatedTable>
          </div>
        </EmhareRegisterPanel>
      </div>
    </template>
  </UDashboardPanel>

  <EmhareRecordDrawer v-model:open="yearModalOpen" :title="yearForm.id ? 'Edit academic year' : 'Create academic year'" description="Dates cannot overlap another academic year and must continue to contain all linked periods and intakes.">
    <template #body>
      <form id="year-form" class="grid gap-4 sm:grid-cols-2" @submit.prevent="saveAcademicYear">
        <UFormField label="Name" required class="sm:col-span-2"><UInput v-model="yearForm.name" class="w-full" placeholder="2027 Academic Year" /></UFormField>
        <UFormField label="Start date" required><UInput v-model="yearForm.startDate" type="date" class="w-full" /></UFormField>
        <UFormField label="End date" required><UInput v-model="yearForm.endDate" type="date" class="w-full" /></UFormField>
        <UFormField v-if="yearForm.id" label="Change reason" description="Recorded in the audited calendar history." required class="sm:col-span-2"><UTextarea v-model="yearForm.changeReason" class="w-full" :rows="3" minlength="10" maxlength="1000" placeholder="Explain why this calendar record is being corrected." /></UFormField>
      </form>
    </template>
    <template #footer><div class="flex w-full justify-end gap-2"><UButton label="Cancel" color="neutral" variant="outline" @click="yearModalOpen = false" /><UButton type="submit" form="year-form" :label="yearForm.id ? 'Save changes' : 'Create academic year'" icon="i-lucide-save" :loading="saving" /></div></template>
  </EmhareRecordDrawer>

  <EmhareRecordDrawer v-model:open="periodTypeModalOpen" :title="periodTypeForm.id ? 'Edit academic period type' : 'Create academic period type'" description="Maintain controlled definitions such as semester, term, block, or session.">
    <template #body>
      <form id="period-type-form" class="grid gap-4 sm:grid-cols-2" @submit.prevent="savePeriodType">
        <UFormField label="Code" :description="periodTypeCodeLocked ? 'Code is locked because academic periods reference this type.' : undefined" required><UInput v-model="periodTypeForm.code" class="w-full" placeholder="SEMESTER" :disabled="periodTypeCodeLocked" /></UFormField>
        <UFormField label="Sort order" required><UInput v-model.number="periodTypeForm.sortOrder" type="number" min="1" class="w-full" /></UFormField>
        <UFormField label="Name" required class="sm:col-span-2"><UInput v-model="periodTypeForm.name" class="w-full" placeholder="Semester" /></UFormField>
        <UFormField v-if="periodTypeForm.id" label="Change reason" description="Recorded in the audited reference history." required class="sm:col-span-2"><UTextarea v-model="periodTypeForm.changeReason" class="w-full" :rows="3" minlength="10" maxlength="1000" /></UFormField>
      </form>
    </template>
    <template #footer><div class="flex w-full justify-end gap-2"><UButton label="Cancel" color="neutral" variant="outline" @click="periodTypeModalOpen = false" /><UButton type="submit" form="period-type-form" :label="periodTypeForm.id ? 'Save changes' : 'Create period type'" icon="i-lucide-save" :loading="saving" /></div></template>
  </EmhareRecordDrawer>

  <EmhareRecordDrawer v-model:open="periodModalOpen" presentation="page" :title="periodForm.id ? 'Edit academic period' : 'Create academic period'" description="Active identity fields are locked; names and dates remain correctable with audit evidence.">
    <template #body>
      <form id="period-form" class="grid gap-4 sm:grid-cols-2" @submit.prevent="saveAcademicPeriod">
        <UAlert v-if="periodForm.id && periodForm.status !== 'DRAFT'" color="info" variant="soft" icon="i-lucide-lock-keyhole" title="Operational identity locked" description="Academic year, period type, and code cannot change after the period leaves draft." class="sm:col-span-2" />
        <UFormField label="Academic year" required><USelect v-model="periodForm.academicYearId" :items="yearItems" value-key="value" class="w-full" :disabled="Boolean(periodForm.id && periodForm.status !== 'DRAFT')" /></UFormField>
        <UFormField label="Period type" required><USelect v-model="periodForm.academicPeriodTypeId" :items="periodTypeItems" value-key="value" class="w-full" :disabled="Boolean(periodForm.id && periodForm.status !== 'DRAFT')" /></UFormField>
        <UFormField label="Code" description="Use letters, numbers, hyphens, or underscores. Spaces are not allowed." required><UInput v-model="periodForm.code" class="w-full" placeholder="2027-S1" maxlength="50" :disabled="Boolean(periodForm.id && periodForm.status !== 'DRAFT')" /></UFormField>
        <UFormField label="Name" required><UInput v-model="periodForm.name" class="w-full" placeholder="Semester 1" /></UFormField>
        <UFormField label="Start date" required><UInput v-model="periodForm.startDate" type="date" class="w-full" /></UFormField>
        <UFormField label="End date" required><UInput v-model="periodForm.endDate" type="date" class="w-full" /></UFormField>
        <UFormField v-if="periodForm.id" label="Change reason" description="Required for the audited correction history." required class="sm:col-span-2"><UTextarea v-model="periodForm.changeReason" class="w-full" :rows="3" minlength="10" maxlength="1000" /></UFormField>
      </form>
    </template>
    <template #footer><div class="flex w-full justify-end gap-2"><UButton label="Cancel" color="neutral" variant="outline" @click="periodModalOpen = false" /><UButton type="submit" form="period-form" :label="periodForm.id ? 'Save changes' : 'Create academic period'" icon="i-lucide-save" :loading="saving" /></div></template>
  </EmhareRecordDrawer>

  <EmhareRecordDrawer v-model:open="intakeModalOpen" presentation="page" title="Edit intake" description="Correct the published intake details with an audited reason.">
    <template #body>
      <form id="intake-form" class="grid gap-4 sm:grid-cols-2" @submit.prevent="saveIntakeCorrection">
        <UAlert color="info" variant="soft" icon="i-lucide-lock-keyhole" title="Operational identity locked" description="Academic year, intake code, and Programme eligibility cannot change after opening." class="sm:col-span-2" />
        <UFormField label="Academic year" required class="sm:col-span-2"><USelect v-model="intakeForm.academicYearId" :items="yearItems" value-key="value" class="w-full" disabled /></UFormField>
        <UFormField label="Code" required><UInput v-model="intakeForm.code" class="w-full" disabled /></UFormField>
        <UFormField label="Name" required><UInput v-model="intakeForm.name" class="w-full" /></UFormField>
        <UFormField label="Starts on" required><UInput v-model="intakeForm.startsOn" type="date" class="w-full" /></UFormField>
        <UFormField label="Ends on" required><UInput v-model="intakeForm.endsOn" type="date" class="w-full" /></UFormField>
        <UFormField label="Offer acceptance deadline" required><UInput v-model="intakeForm.offerAcceptanceDeadline" type="date" class="w-full" /></UFormField>
        <UFormField label="Commencement date" required><UInput v-model="intakeForm.commencementDate" type="date" class="w-full" /></UFormField>
        <UFormField label="Registration date"><UInput v-model="intakeForm.registrationDate" type="date" class="w-full" /></UFormField>
        <UFormField label="Orientation date"><UInput v-model="intakeForm.orientationDate" type="date" class="w-full" /></UFormField>
        <UFormField label="Maximum Programme choices" required class="sm:col-span-2"><UInput v-model.number="intakeForm.maximumProgrammeChoices" type="number" min="1" max="20" class="w-full" /></UFormField>
        <UFormField label="Programme Levels" class="sm:col-span-2"><USelectMenu v-model="intakeForm.programmeLevelIds" :items="programmeLevelItems" value-key="value" label-key="label" multiple aria-label="Programme Levels" disabled class="w-full" /></UFormField>
        <UFormField label="Specific Programmes" class="sm:col-span-2"><USelectMenu v-model="intakeForm.programmeIds" :items="specificProgrammeItems" value-key="value" label-key="label" multiple aria-label="Specific Programmes" disabled class="w-full" /></UFormField>
        <UFormField label="Change reason" description="Required for the audited correction history." required class="sm:col-span-2"><UTextarea v-model="intakeForm.changeReason" class="w-full" :rows="3" minlength="10" maxlength="1000" placeholder="Explain the intake correction." /></UFormField>
      </form>
    </template>
    <template #footer>
      <div class="flex w-full justify-end gap-2">
        <UButton label="Cancel" color="neutral" variant="outline" @click="intakeModalOpen = false" />
        <UButton type="submit" form="intake-form" label="Save changes" icon="i-lucide-save" :loading="saving" />
      </div>
    </template>
  </EmhareRecordDrawer>
</template>
