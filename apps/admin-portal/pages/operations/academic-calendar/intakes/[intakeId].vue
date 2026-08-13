<script setup lang="ts">
import type { AcademicYearSummary, IntakeSummary, ProgrammeSummary } from '@emhare/portal-shell/types/academic'
import type { AdmissionsApplicationTypeSummary } from '@emhare/portal-shell/types/admissions'
import type { FinanceFeeStructureRegister, FinanceFeeStructureSummary } from '@emhare/portal-shell/types/finance'

definePageMeta({ layout: 'dashboard' })

type RouteProgrammeMapping = {
  programmeId: string
  programmeCode: string
  programmeName: string
}

type RouteSectionConfiguration = {
  code: string
  name: string
  required: boolean
  repeatable: boolean
  minimumRecords: number
  sortOrder: number
}

type RouteDocumentConfiguration = {
  code: string
  name: string
  required: boolean
  sortOrder: number
}

type ApplicationRouteConfiguration = {
  applicationTypeId: string
  code: string
  name: string
  active: boolean
  readyForActivation: boolean
  readinessBlockers: string[]
  programmes: RouteProgrammeMapping[]
  sections: RouteSectionConfiguration[]
  documents: RouteDocumentConfiguration[]
  feePolicyStatus: 'UNCONFIGURED' | 'FEE_STRUCTURE' | 'FEE_FREE'
  version: number
}

type IntakeRouteSetup = {
  applicationType: AdmissionsApplicationTypeSummary
  configuration: ApplicationRouteConfiguration
  feeMode: 'FEE_STRUCTURE' | 'FEE_FREE'
  feeStructureId: string
}

type ProgrammeQuotaSetup = {
  id: string | null
  programmeId: string
  programmeCode: string
  programmeName: string
  capacity: number
  reservedCapacity: number
  version: number
}

const route = useRoute()
const api = useEmhareApi()
const toast = useToast()
const { showError } = useEmhareConfirm()
const academicSetup = useAcademicSetup()
const saving = ref(false)
const loadingWorkspace = ref(true)
const workspaceError = ref('')
const setupStep = ref<1 | 2 | 3 | 4 | 5>(1)
const openingConfigurationLoading = ref(false)
const openingConfigurationError = ref('')
const applicationFeeStructures = ref<FinanceFeeStructureSummary[]>([])
const intakeRouteSetups = ref<IntakeRouteSetup[]>([])
const programmeQuotaSetups = ref<ProgrammeQuotaSetup[]>([])
const openingChangeReason = ref('')
const intakeForm = reactive({
  id: null as string | null,
  status: 'DRAFT' as IntakeSummary['status'],
  academicYearId: '',
  code: '',
  name: '',
  startsOn: '',
  endsOn: '',
  maximumProgrammeChoices: 3,
  programmeLevelIds: [] as string[],
  programmeIds: [] as string[],
  expectedVersion: 0
})

const routeIntakeId = computed(() => {
  const value = route.params.intakeId
  return Array.isArray(value) ? value[0] ?? 'new' : value ?? 'new'
})
const isNewIntake = computed(() => routeIntakeId.value === 'new')
const overview = computed(() => academicSetup.overview.value)
const academicYears = computed(() => overview.value?.academicYears ?? [])
const programmeLevels = computed(() => overview.value?.programmeLevels ?? [])
const programmes = computed(() => overview.value?.programmes ?? [])
const yearItems = computed(() => academicYears.value
  .filter(year => year.status !== 'ARCHIVED')
  .map(year => ({ label: year.name, value: year.id })))
const programmeLevelItems = computed(() => programmeLevels.value
  .filter(level => level.status === 'ACTIVE' || intakeForm.programmeLevelIds.includes(level.id))
  .map(level => ({ label: `${level.code} · ${level.name}`, value: level.id })))
const specificProgrammeItems = computed(() => programmes.value
  .filter(programme => intakeForm.programmeLevelIds.includes(programme.programmeLevelId))
  .filter(programme => programme.status === 'ACTIVE' || intakeForm.programmeIds.includes(programme.id))
  .map(programme => ({ label: `${programme.code} · ${programme.name}`, value: programme.id })))
const selectedAcademicYear = computed<AcademicYearSummary | null>(() =>
  academicYears.value.find(year => year.id === intakeForm.academicYearId) ?? null)
const selectedProgrammeLevels = computed(() => programmeLevels.value
  .filter(level => intakeForm.programmeLevelIds.includes(level.id)))
const selectedProgrammes = computed(() => programmes.value
  .filter(programme => intakeForm.programmeIds.includes(programme.id)))
const programmesCoveredByIntake = computed<ProgrammeSummary[]>(() => {
  if (selectedProgrammes.value.length) return selectedProgrammes.value
  return programmes.value.filter(programme =>
    programme.status === 'ACTIVE' && intakeForm.programmeLevelIds.includes(programme.programmeLevelId))
})
const activeApplicationFeeItems = computed(() => applicationFeeStructures.value
  .filter(structure => structure.feeContext === 'APPLICATION' && structure.status === 'ACTIVE')
  .map(structure => ({
    label: `${structure.code} · ${structure.name}`,
    value: structure.id,
    description: `${structure.programmeLevelCode}${structure.applicantCategoryCode ? ` · ${structure.applicantCategoryCode}` : ''}`
  })))
const feePolicyItems = [
  { label: 'Use application fee', value: 'FEE_STRUCTURE' },
  { label: 'Fee-free applications', value: 'FEE_FREE' }
]

const detailsIssue = computed(() => {
  if (!intakeForm.academicYearId || !intakeForm.code.trim() || !intakeForm.name.trim() || !intakeForm.startsOn || !intakeForm.endsOn) {
    return 'Complete the academic year, code, name, and application window before continuing.'
  }
  if (intakeForm.startsOn > intakeForm.endsOn) return 'The application end date must be on or after its start date.'
  const academicYear = selectedAcademicYear.value
  if (academicYear && (intakeForm.startsOn < academicYear.startDate || intakeForm.endsOn > academicYear.endDate)) {
    return `Keep the application window within ${academicYear.name}: ${formatDate(academicYear.startDate)} to ${formatDate(academicYear.endDate)}.`
  }
  if (intakeForm.maximumProgrammeChoices < 1 || intakeForm.maximumProgrammeChoices > 20) {
    return 'Maximum Programme choices must be between 1 and 20.'
  }
  return ''
})
const eligibilityIssue = computed(() => intakeForm.programmeLevelIds.length
  ? ''
  : 'Select at least one Programme Level before continuing.')
const routeConfigurationIssue = computed(() => {
  if (openingConfigurationLoading.value) return 'Admissions route and fee configuration is still loading.'
  if (openingConfigurationError.value) return openingConfigurationError.value
  if (!programmesCoveredByIntake.value.length) return 'Select at least one active Programme for this intake.'
  const unassignedProgramme = programmesCoveredByIntake.value.find(programme =>
    !intakeRouteSetups.value.some(applicationRoute => routeProgrammes(applicationRoute).some(routeProgramme => routeProgramme.id === programme.id)))
  if (unassignedProgramme) return `${unassignedProgramme.code} has no application route. Configure its Programme mapping in Application Types before continuing.`
  for (const applicationRoute of assignedRoutes.value) {
    if (applicationRoute.feeMode === 'FEE_STRUCTURE' && !applicationRoute.feeStructureId) {
      return `Select an application fee or record ${applicationRoute.applicationType.code} as fee-free.`
    }
    const unresolvedBlocker = applicationRoute.configuration.readinessBlockers.find(blocker =>
      !blocker.includes('programme mapping') && !blocker.includes('fee structure') && !blocker.includes('fee-free'))
    if (unresolvedBlocker) return `${applicationRoute.applicationType.code}: ${unresolvedBlocker}.`
  }
  return ''
})
const quotaIssue = computed(() => {
  if (!programmeQuotaSetups.value.length) return 'Add at least one Programme before configuring capacity.'
  const invalidQuota = programmeQuotaSetups.value.find(quota =>
    !Number.isInteger(quota.capacity) || quota.capacity < 1
    || !Number.isInteger(quota.reservedCapacity) || quota.reservedCapacity < 0
    || quota.reservedCapacity > quota.capacity)
  return invalidQuota ? `Enter a valid total and reserved capacity for ${invalidQuota.programmeCode}.` : ''
})
const openingIssue = computed(() => {
  if (detailsIssue.value) return detailsIssue.value
  if (eligibilityIssue.value) return eligibilityIssue.value
  if (routeConfigurationIssue.value) return routeConfigurationIssue.value
  if (quotaIssue.value) return quotaIssue.value
  if (selectedAcademicYear.value?.status !== 'OPEN') return 'Open the selected academic year before opening this intake.'
  if (openingChangeReason.value.trim().length < 10) return 'Record an opening reason of at least 10 characters for the audit trail.'
  return ''
})
const setupSteps = computed(() => [
  { number: 1, label: 'Intake details', description: 'Identity and dates', complete: !detailsIssue.value },
  { number: 2, label: 'Programme eligibility', description: 'Catalogue coverage', complete: !eligibilityIssue.value },
  { number: 3, label: 'Routes and fees', description: 'Applicant routes', complete: !routeConfigurationIssue.value },
  { number: 4, label: 'Programme quotas', description: 'Planning capacity', complete: !quotaIssue.value },
  { number: 5, label: 'Review and open', description: 'Audit and publish', complete: !openingIssue.value }
])
const assignedRoutes = computed(() => intakeRouteSetups.value.filter(applicationRoute => routeProgrammes(applicationRoute).length))
const planningCapacity = computed(() => programmeQuotaSetups.value.reduce((total, quota) => total + (quota.capacity || 0), 0))
const currentStepIssue = computed(() => {
  if (setupStep.value === 1) return detailsIssue.value
  if (setupStep.value === 2) return eligibilityIssue.value
  if (setupStep.value === 3) return routeConfigurationIssue.value
  if (setupStep.value === 4) return quotaIssue.value
  return openingIssue.value
})

watch(() => intakeForm.programmeLevelIds.slice(), (selectedProgrammeLevelIds) => {
  intakeForm.programmeIds = intakeForm.programmeIds.filter(programmeId => {
    const programme = programmes.value.find(candidate => candidate.id === programmeId)
    return Boolean(programme && selectedProgrammeLevelIds.includes(programme.programmeLevelId))
  })
})

watch(programmesCoveredByIntake, (coveredProgrammes) => {
  const existingQuotaByProgrammeId = new Map(programmeQuotaSetups.value.map(quota => [quota.programmeId, quota]))
  programmeQuotaSetups.value = coveredProgrammes.map(programme => existingQuotaByProgrammeId.get(programme.id) ?? {
    id: null,
    programmeId: programme.id,
    programmeCode: programme.code,
    programmeName: programme.name,
    capacity: 0,
    reservedCapacity: 0,
    version: 0
  })
})

onMounted(loadWorkspace)

function routeProgrammes(applicationRoute: IntakeRouteSetup) {
  const mappedProgrammeIds = new Set(applicationRoute.configuration.programmes.map(programme => programme.programmeId))
  return programmesCoveredByIntake.value.filter(programme => mappedProgrammeIds.has(programme.id))
}

async function loadWorkspace() {
  loadingWorkspace.value = true
  workspaceError.value = ''
  try {
    await academicSetup.ensureOverview()
    if (isNewIntake.value) {
      Object.assign(intakeForm, {
        id: null,
        status: 'DRAFT',
        academicYearId: academicYears.value.find(year => year.status === 'OPEN')?.id ?? academicYears.value[0]?.id ?? '',
        code: '',
        name: '',
        startsOn: '',
        endsOn: '',
        maximumProgrammeChoices: 3,
        programmeLevelIds: [],
        programmeIds: [],
        expectedVersion: 0
      })
    } else {
      const existingIntake = (overview.value?.intakes ?? []).find(intake => intake.id === routeIntakeId.value)
      if (!existingIntake) throw new Error('The selected intake no longer exists or is unavailable.')
      if (existingIntake.status !== 'DRAFT') {
        throw new Error('Only draft intakes use this setup workspace. Return to the calendar to correct an open or closed intake.')
      }
      Object.assign(intakeForm, {
        id: existingIntake.id,
        status: existingIntake.status,
        academicYearId: existingIntake.academicYearId,
        code: existingIntake.code,
        name: existingIntake.name,
        startsOn: existingIntake.startsOn,
        endsOn: existingIntake.endsOn,
        maximumProgrammeChoices: existingIntake.maximumProgrammeChoices,
        programmeLevelIds: existingIntake.programmeLevels.map(level => level.id),
        programmeIds: existingIntake.specificProgrammes.map(programme => programme.id),
        expectedVersion: existingIntake.version
      })
    }
    await loadAdmissionsOpeningConfiguration(intakeForm.id)
  } catch (error) {
    workspaceError.value = api.errorMessage(error, 'The intake setup workspace could not be loaded.')
  } finally {
    loadingWorkspace.value = false
  }
}

async function loadAdmissionsOpeningConfiguration(intakeId: string | null) {
  openingConfigurationLoading.value = true
  openingConfigurationError.value = ''
  try {
    const [applicationTypes, feeStructureRegister] = await Promise.all([
      api.request<AdmissionsApplicationTypeSummary[]>('/api/admissions/application-types'),
      api.request<FinanceFeeStructureRegister>('/api/finance/fee-structures')
    ])
    const supportedRouteCodes = new Set(['UNDERGRAD', 'POSTGRAD', 'MBA', 'EDUCATION'])
    const supportedApplicationTypes = applicationTypes.filter(applicationType => supportedRouteCodes.has(applicationType.code))
    applicationFeeStructures.value = feeStructureRegister.structures
    const configurations = await Promise.all(supportedApplicationTypes.map(applicationType =>
      api.request<ApplicationRouteConfiguration>(`/api/admissions/application-types/${applicationType.id}/route-configuration`)))
    intakeRouteSetups.value = supportedApplicationTypes.map(applicationType => {
      const configuration = configurations.find(candidate => candidate.applicationTypeId === applicationType.id)!
      return {
        applicationType,
        configuration,
        feeMode: applicationType.financeFeeStructureId
          ? 'FEE_STRUCTURE'
          : configuration.feePolicyStatus === 'FEE_FREE' ? 'FEE_FREE' : 'FEE_STRUCTURE',
        feeStructureId: applicationType.financeFeeStructureId ?? ''
      }
    })
    if (intakeId) {
      const quotas = await api.request<ProgrammeQuotaSetup[]>(`/api/admissions/intakes/${intakeId}/programme-quotas`)
      const quotaByProgrammeId = new Map(quotas.map(quota => [quota.programmeId, quota]))
      programmeQuotaSetups.value = programmesCoveredByIntake.value.map(programme => quotaByProgrammeId.get(programme.id) ?? {
        id: null,
        programmeId: programme.id,
        programmeCode: programme.code,
        programmeName: programme.name,
        capacity: 0,
        reservedCapacity: 0,
        version: 0
      })
    }
  } catch (error) {
    openingConfigurationError.value = api.errorMessage(
      error,
      'Admissions routes, application fees, and Programme quotas could not be loaded.')
  } finally {
    openingConfigurationLoading.value = false
  }
}

function goToStep(stepNumber: number) {
  if (stepNumber <= setupStep.value) setupStep.value = stepNumber as 1 | 2 | 3 | 4 | 5
}

function continueSetup() {
  if (currentStepIssue.value || setupStep.value === 5) return
  setupStep.value = (setupStep.value + 1) as 2 | 3 | 4 | 5
}

async function configureApplicationRoutes(activateRoutes: boolean) {
  for (const applicationRoute of assignedRoutes.value) {
    let expectedVersion = applicationRoute.configuration.version
    if (applicationRoute.feeMode === 'FEE_STRUCTURE') {
      const feeStructure = applicationFeeStructures.value.find(structure => structure.id === applicationRoute.feeStructureId)
      if (!feeStructure) throw new Error(`The selected ${applicationRoute.applicationType.code} application fee is no longer active.`)
      if (applicationRoute.applicationType.financeFeeStructureId !== feeStructure.id) {
        const updatedApplicationType = await api.request<AdmissionsApplicationTypeSummary>(
          `/api/admissions/application-types/${applicationRoute.applicationType.id}`,
          {
            method: 'PUT',
            body: {
              name: applicationRoute.applicationType.name,
              requiresEmploymentHistory: applicationRoute.applicationType.requiresEmploymentHistory,
              requiresReferees: applicationRoute.applicationType.requiresReferees,
              financeFeeStructureId: feeStructure.id,
              financeFeeStructureCode: feeStructure.code,
              financeFeeStructureName: feeStructure.name,
              active: applicationRoute.applicationType.active,
              changeReason: openingChangeReason.value.trim(),
              expectedVersion
            }
          })
        applicationRoute.applicationType = updatedApplicationType
        expectedVersion = updatedApplicationType.version
      }
    }

    applicationRoute.configuration = await api.request<ApplicationRouteConfiguration>(
      `/api/admissions/application-types/${applicationRoute.applicationType.id}/route-configuration`,
      {
        method: 'PUT',
        body: {
          programmes: applicationRoute.configuration.programmes,
          sections: applicationRoute.configuration.sections,
          documents: applicationRoute.configuration.documents,
          feeFree: applicationRoute.feeMode === 'FEE_FREE',
          feeFreeReason: applicationRoute.feeMode === 'FEE_FREE'
            ? `Fee-free decision recorded while opening ${intakeForm.name}. ${openingChangeReason.value.trim()}`
            : null,
          activate: activateRoutes || applicationRoute.applicationType.active,
          changeReason: openingChangeReason.value.trim(),
          expectedVersion
        }
      })
  }
}

async function configureProgrammeQuotas(intakeId: string) {
  await api.request(`/api/admissions/intakes/${intakeId}/programme-quotas`, {
    method: 'PUT',
    body: {
      quotas: programmeQuotaSetups.value.map(quota => ({
        programmeId: quota.programmeId,
        programmeCode: quota.programmeCode,
        programmeName: quota.programmeName,
        quotaTypeCode: 'GENERAL',
        capacity: quota.capacity,
        reservedCapacity: quota.reservedCapacity,
        expectedVersion: quota.version
      })),
      changeReason: openingChangeReason.value.trim()
    }
  })
}

async function saveIntake(openAfterSave: boolean) {
  const values = {
    academicYearId: intakeForm.academicYearId,
    code: intakeForm.code,
    name: intakeForm.name,
    startsOn: intakeForm.startsOn,
    endsOn: intakeForm.endsOn,
    maximumProgrammeChoices: intakeForm.maximumProgrammeChoices,
    programmeLevelIds: intakeForm.programmeLevelIds,
    programmeIds: intakeForm.programmeIds
  }
  const updatingExistingIntake = Boolean(intakeForm.id)
  saving.value = true
  try {
    let savedIntake = await api.request<IntakeSummary>(
      intakeForm.id ? `/api/academic/intakes/${intakeForm.id}` : '/api/academic/intakes',
      {
        method: intakeForm.id ? 'PUT' : 'POST',
        body: intakeForm.id
          ? { ...values, changeReason: openingChangeReason.value.trim(), expectedVersion: intakeForm.expectedVersion }
          : values
      })
    Object.assign(intakeForm, {
      id: savedIntake.id,
      status: savedIntake.status,
      expectedVersion: savedIntake.version
    })
    await configureApplicationRoutes(openAfterSave)
    await configureProgrammeQuotas(savedIntake.id)
    if (openAfterSave) {
      savedIntake = await api.request<IntakeSummary>(`/api/academic/intakes/${savedIntake.id}/open`, {
        method: 'POST',
        body: { expectedVersion: savedIntake.version }
      })
    }
    await academicSetup.loadOverview()
    toast.add({
      title: openAfterSave
        ? `${savedIntake.name} configured and opened`
        : `Intake ${updatingExistingIntake ? 'updated' : 'saved as draft'}`,
      color: 'success',
      icon: openAfterSave ? 'i-lucide-door-open' : 'i-lucide-save'
    })
    await navigateTo('/operations/academic-calendar')
  } catch (error) {
    await showError(
      openAfterSave ? 'Admissions opening could not be completed' : `Intake could not be ${updatingExistingIntake ? 'updated' : 'created'}`,
      `${api.errorMessage(error)}${intakeForm.id ? ' The intake remains saved as a draft so you can correct the issue and retry.' : ''}`)
  } finally {
    saving.value = false
  }
}

function formatDate(value: string) {
  if (!value) return 'Not set'
  return new Intl.DateTimeFormat('en-ZW', { day: '2-digit', month: 'short', year: 'numeric' })
    .format(new Date(`${value}T00:00:00`))
}
</script>

<template>
  <UDashboardPanel data-testid="intake-setup-workspace">
    <template #header>
      <UDashboardNavbar title="Intake setup">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right>
          <UButton label="Return to calendar" icon="i-lucide-arrow-left" color="neutral" variant="outline" to="/operations/academic-calendar" />
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <span class="text-sm text-muted">Academic calendar / Intakes / {{ isNewIntake ? 'New intake' : intakeForm.code || 'Draft intake' }}</span>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div v-if="loadingWorkspace" class="flex min-h-[32rem] items-center justify-center">
        <div class="text-center">
          <UIcon name="i-lucide-loader-circle" class="mx-auto size-8 animate-spin text-primary" />
          <p class="mt-3 text-sm text-muted">Loading intake configuration…</p>
        </div>
      </div>

      <div v-else-if="workspaceError" class="mx-auto max-w-4xl p-6 lg:p-10">
        <UAlert color="error" variant="soft" icon="i-lucide-circle-alert" title="Intake setup unavailable" :description="workspaceError" />
        <UButton label="Return to academic calendar" icon="i-lucide-arrow-left" class="mt-4" to="/operations/academic-calendar" />
      </div>

      <div v-else class="min-h-full bg-[radial-gradient(circle_at_top_right,rgba(32,116,58,0.08),transparent_34%)] p-4 pb-28 sm:p-6 sm:pb-28 lg:p-8 lg:pb-28">
        <div class="mx-auto max-w-[96rem] space-y-6">
          <header class="relative overflow-hidden rounded-2xl bg-[#0b3d24] px-6 py-7 text-white shadow-sm sm:px-8 lg:px-10">
            <div class="absolute inset-y-0 right-0 hidden w-[34rem] opacity-20 lg:block" aria-hidden="true">
              <div class="absolute right-20 top-[-8rem] size-80 rounded-full border border-[#e9bd45]/70" />
              <div class="absolute right-[-3rem] top-[-3rem] size-64 rounded-full border border-white/30" />
              <div class="absolute bottom-[-7rem] right-52 size-52 rounded-full bg-[#e9bd45]/20" />
            </div>
            <div class="relative grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem] lg:items-end">
              <div>
                <p class="text-xs font-bold uppercase tracking-[0.22em] text-[#e9bd45]">University admissions opening</p>
                <h1 class="mt-3 max-w-3xl font-serif text-3xl font-semibold leading-tight sm:text-4xl">
                  {{ intakeForm.id ? `Complete ${intakeForm.name}` : 'Create and open an intake' }}
                </h1>
                <p class="mt-3 max-w-2xl text-sm leading-6 text-white/75 sm:text-base">
                  Configure the calendar window, Programme catalogue, applicant routes, fees, and planning quotas in one governed workspace.
                </p>
              </div>
              <div class="grid grid-cols-2 gap-px overflow-hidden rounded-xl border border-white/15 bg-white/15">
                <div class="bg-[#0b3d24]/80 p-4">
                  <p class="text-[0.68rem] font-bold uppercase tracking-wider text-white/55">Current stage</p>
                  <p class="mt-1 text-lg font-semibold">{{ setupStep }} of 5</p>
                </div>
                <div class="bg-[#0b3d24]/80 p-4">
                  <p class="text-[0.68rem] font-bold uppercase tracking-wider text-white/55">Draft state</p>
                  <p class="mt-1 text-lg font-semibold">{{ intakeForm.id ? 'Saved' : 'Not saved' }}</p>
                </div>
              </div>
            </div>
          </header>

          <div class="grid gap-6 xl:grid-cols-[18rem_minmax(0,1fr)] xl:items-start">
            <aside class="xl:sticky xl:top-6">
              <nav aria-label="Intake setup progress" class="overflow-hidden rounded-xl border border-muted bg-default shadow-sm">
                <div class="border-b border-muted bg-elevated px-5 py-4">
                  <p class="text-xs font-bold uppercase tracking-[0.16em] text-primary">Opening sequence</p>
                  <p class="mt-1 text-sm text-muted">Complete each stage in order.</p>
                </div>
                <ol class="flex overflow-x-auto p-2 xl:block xl:overflow-visible">
                  <li v-for="step in setupSteps" :key="step.number" class="min-w-48 xl:min-w-0">
                    <button
                      type="button"
                      class="group flex w-full items-center gap-3 rounded-lg px-3 py-3 text-left transition-colors"
                      :class="step.number === setupStep ? 'bg-primary text-white' : 'text-muted hover:bg-elevated hover:text-highlighted'"
                      :disabled="step.number > setupStep"
                      @click="goToStep(step.number)"
                    >
                      <span
                        class="flex size-9 shrink-0 items-center justify-center rounded-full border text-sm font-bold"
                        :class="step.number === setupStep
                          ? 'border-white/30 bg-white/15'
                          : step.complete ? 'border-success/30 bg-success/10 text-success' : 'border-muted bg-default'"
                      >
                        <UIcon v-if="step.complete && step.number < setupStep" name="i-lucide-check" class="size-4" />
                        <span v-else>{{ step.number }}</span>
                      </span>
                      <span class="min-w-0">
                        <span class="block text-sm font-semibold">{{ step.label }}</span>
                        <span class="mt-0.5 block text-xs" :class="step.number === setupStep ? 'text-white/70' : 'text-muted'">{{ step.description }}</span>
                      </span>
                    </button>
                  </li>
                </ol>
              </nav>

              <div class="mt-4 hidden rounded-xl border border-muted bg-default p-5 shadow-sm xl:block">
                <p class="text-xs font-bold uppercase tracking-[0.14em] text-muted">Opening scope</p>
                <dl class="mt-3 space-y-3 text-sm">
                  <div class="flex justify-between gap-3"><dt class="text-muted">Programmes</dt><dd class="font-semibold text-highlighted">{{ programmesCoveredByIntake.length }}</dd></div>
                  <div class="flex justify-between gap-3"><dt class="text-muted">Routes</dt><dd class="font-semibold text-highlighted">{{ assignedRoutes.length }}</dd></div>
                  <div class="flex justify-between gap-3"><dt class="text-muted">Capacity</dt><dd class="font-semibold text-highlighted">{{ planningCapacity }}</dd></div>
                </dl>
              </div>
            </aside>

            <main class="min-w-0 overflow-hidden rounded-xl border border-muted bg-default shadow-sm">
              <div class="border-b border-muted px-5 py-5 sm:px-7 lg:px-9">
                <div class="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <p class="text-xs font-bold uppercase tracking-[0.18em] text-primary">Step {{ setupStep }} of 5</p>
                    <h2 class="mt-2 text-2xl font-semibold text-highlighted">
                      {{ setupStep === 1
                        ? 'Define the intake'
                        : setupStep === 2
                          ? 'Choose Programme eligibility'
                          : setupStep === 3
                            ? 'Confirm routes and application fees'
                            : setupStep === 4
                              ? 'Set Programme planning quotas'
                              : 'Review and open applications' }}
                    </h2>
                  </div>
                  <UBadge :label="intakeForm.id ? 'Saved draft' : 'Unsaved draft'" color="warning" variant="soft" size="lg" />
                </div>
              </div>

              <form class="min-h-[30rem] px-5 py-6 sm:px-7 lg:px-9 lg:py-8" @submit.prevent>
                <div v-if="setupStep === 1" class="grid max-w-5xl gap-5 md:grid-cols-2">
                  <UFormField label="Academic year" description="The institution-wide year that contains this application window." required class="md:col-span-2">
                    <USelect v-model="intakeForm.academicYearId" :items="yearItems" value-key="value" class="w-full" />
                  </UFormField>
                  <UFormField label="Intake code" description="Letters, numbers, hyphens, and underscores only." required>
                    <UInput v-model="intakeForm.code" class="w-full" placeholder="AUG-2027" maxlength="50" />
                  </UFormField>
                  <UFormField label="Applicant-facing name" required>
                    <UInput v-model="intakeForm.name" class="w-full" placeholder="August 2027 Intake" />
                  </UFormField>
                  <UFormField label="Applications open" required><UInput v-model="intakeForm.startsOn" type="date" class="w-full" /></UFormField>
                  <UFormField label="Applications close" required><UInput v-model="intakeForm.endsOn" type="date" class="w-full" /></UFormField>
                  <UFormField label="Maximum Programme choices" description="The most Programmes an applicant may rank." required class="md:col-span-2">
                    <UInput v-model.number="intakeForm.maximumProgrammeChoices" type="number" min="1" max="20" class="w-full md:max-w-xs" />
                  </UFormField>
                  <UAlert v-if="detailsIssue" color="warning" variant="soft" icon="i-lucide-info" title="Complete this stage" :description="detailsIssue" class="md:col-span-2" />
                </div>

                <div v-else-if="setupStep === 2" class="grid max-w-6xl gap-6 lg:grid-cols-2">
                  <section class="rounded-xl border border-muted p-5 sm:p-6">
                    <p class="text-xs font-bold uppercase tracking-[0.14em] text-primary">1 · Select levels</p>
                    <UFormField label="Programme Levels" description="Choose every applicant level offered in this intake." required class="mt-4">
                      <USelectMenu v-model="intakeForm.programmeLevelIds" :items="programmeLevelItems" value-key="value" label-key="label" multiple aria-label="Programme Levels" placeholder="Search and select Programme Levels" class="w-full" />
                    </UFormField>
                  </section>
                  <section class="rounded-xl border border-muted p-5 sm:p-6">
                    <p class="text-xs font-bold uppercase tracking-[0.14em] text-primary">2 · Refine coverage</p>
                    <UFormField label="Specific Programmes" description="Leave empty to include every active Programme in the selected levels." class="mt-4">
                      <USelectMenu v-model="intakeForm.programmeIds" :items="specificProgrammeItems" value-key="value" label-key="label" multiple aria-label="Specific Programmes" placeholder="All active Programmes in selected levels" :disabled="!intakeForm.programmeLevelIds.length" class="w-full" />
                    </UFormField>
                  </section>
                  <UAlert
                    :color="intakeForm.programmeIds.length ? 'warning' : 'success'"
                    variant="soft"
                    :icon="intakeForm.programmeIds.length ? 'i-lucide-list-filter' : 'i-lucide-layers-3'"
                    :title="intakeForm.programmeIds.length ? 'Specific Programme whitelist' : 'Programme Level coverage'"
                    :description="intakeForm.programmeIds.length
                      ? `Only the ${intakeForm.programmeIds.length} selected Programme${intakeForm.programmeIds.length === 1 ? '' : 's'} will be available.`
                      : 'Every active Programme in the selected Programme Levels will be available.'"
                    class="lg:col-span-2"
                  />
                  <UAlert v-if="eligibilityIssue" color="warning" variant="soft" icon="i-lucide-info" title="Complete this stage" :description="eligibilityIssue" class="lg:col-span-2" />
                </div>

                <div v-else-if="setupStep === 3" class="space-y-5">
                  <UAlert v-if="openingConfigurationLoading" color="info" variant="soft" icon="i-lucide-loader-circle" title="Loading admissions configuration" description="Checking application routes and active Finance fee structures." />
                  <UAlert v-else-if="openingConfigurationError" color="error" variant="soft" icon="i-lucide-circle-alert" title="Admissions configuration unavailable" :description="openingConfigurationError" />
                  <template v-else>
                    <UAlert color="primary" variant="soft" icon="i-lucide-route" title="Confirm route coverage and application fees" description="Programme eligibility was selected in the previous step. Route coverage is managed centrally in Application Types and is shown here for confirmation only." />
                    <div class="grid gap-5 2xl:grid-cols-2">
                      <section v-for="applicationRoute in assignedRoutes" :key="applicationRoute.applicationType.id" class="rounded-xl border border-muted bg-default p-5 sm:p-6">
                        <div class="mb-5 flex flex-wrap items-start justify-between gap-3">
                          <div>
                            <div class="flex items-center gap-2">
                              <span class="font-mono text-xs font-bold text-primary">{{ applicationRoute.applicationType.code }}</span>
                              <EmhareStatusPill :label="applicationRoute.applicationType.active ? 'ACTIVE' : 'INACTIVE'" :tone="applicationRoute.applicationType.active ? 'success' : 'warning'" />
                            </div>
                            <h3 class="mt-2 text-lg font-semibold text-highlighted">{{ applicationRoute.applicationType.name }}</h3>
                          </div>
                          <UBadge :label="`${routeProgrammes(applicationRoute).length} Programme${routeProgrammes(applicationRoute).length === 1 ? '' : 's'}`" color="primary" variant="soft" />
                        </div>
                        <div class="space-y-4">
                          <div class="rounded-lg border border-muted bg-elevated/60 p-4" data-testid="route-programme-coverage">
                            <div class="flex flex-wrap items-center justify-between gap-2">
                              <div>
                                <p class="text-sm font-semibold text-highlighted">Programmes covered in this intake</p>
                                <p class="mt-1 text-xs text-muted">Managed in Application Types</p>
                              </div>
                              <UButton label="Open Application Types" icon="i-lucide-external-link" color="neutral" variant="ghost" size="xs" to="/operations/application-types" />
                            </div>
                            <div v-if="routeProgrammes(applicationRoute).length" class="mt-3 flex flex-wrap gap-2">
                              <UBadge v-for="programme in routeProgrammes(applicationRoute)" :key="programme.id" :label="`${programme.code} · ${programme.name}`" color="neutral" variant="soft" />
                            </div>
                          </div>
                          <div class="grid gap-4 sm:grid-cols-2">
                            <UFormField label="Application fee policy">
                              <USelect v-model="applicationRoute.feeMode" :items="feePolicyItems" value-key="value" :aria-label="`${applicationRoute.applicationType.code} fee policy`" class="w-full" />
                            </UFormField>
                            <UFormField v-if="applicationRoute.feeMode === 'FEE_STRUCTURE'" label="Active application fee">
                              <USelect v-model="applicationRoute.feeStructureId" :items="activeApplicationFeeItems" value-key="value" :aria-label="`${applicationRoute.applicationType.code} application fee`" placeholder="Select fee" class="w-full" />
                            </UFormField>
                            <UAlert v-else color="warning" variant="soft" icon="i-lucide-receipt-text" title="Fee-free route" description="The final audit reason records this decision." />
                          </div>
                        </div>
                        <UAlert
                          v-if="routeProgrammes(applicationRoute).length && applicationRoute.configuration.readinessBlockers.some(blocker => !blocker.includes('programme mapping') && !blocker.includes('fee structure') && !blocker.includes('fee-free'))"
                          class="mt-4"
                          color="warning"
                          variant="soft"
                          title="Route setup still has requirements"
                          :description="applicationRoute.configuration.readinessBlockers.join('; ')"
                        />
                      </section>
                    </div>
                    <UAlert v-if="routeConfigurationIssue" color="warning" variant="soft" icon="i-lucide-info" title="Complete this stage" :description="routeConfigurationIssue" />
                  </template>
                </div>

                <div v-else-if="setupStep === 4" class="space-y-5">
                  <UAlert color="info" variant="soft" icon="i-lucide-chart-no-axes-column" title="Planning capacity only" description="Programme quotas support planning and reporting. They never reject an applicant or override an admission decision." />
                  <section class="overflow-hidden rounded-xl border border-muted bg-default">
                    <div class="hidden grid-cols-[minmax(0,1fr)_12rem_12rem] gap-5 border-b border-muted bg-elevated px-6 py-3 text-xs font-bold uppercase tracking-wider text-muted lg:grid">
                      <span>Programme</span><span>Total capacity</span><span>Reserved capacity</span>
                    </div>
                    <div v-for="quota in programmeQuotaSetups" :key="quota.programmeId" class="grid gap-4 border-b border-muted px-5 py-5 last:border-b-0 lg:grid-cols-[minmax(0,1fr)_12rem_12rem] lg:items-center lg:px-6">
                      <div>
                        <p class="font-mono text-xs font-bold text-primary">{{ quota.programmeCode }}</p>
                        <p class="mt-1 font-medium text-highlighted">{{ quota.programmeName }}</p>
                      </div>
                      <UFormField label="Total capacity" required class="lg:[&>label]:sr-only">
                        <UInput v-model.number="quota.capacity" type="number" min="1" :aria-label="`${quota.programmeCode} total capacity`" class="w-full" />
                      </UFormField>
                      <UFormField label="Reserved capacity" description="Included in total" class="lg:[&>label]:sr-only">
                        <UInput v-model.number="quota.reservedCapacity" type="number" min="0" :max="quota.capacity || undefined" :aria-label="`${quota.programmeCode} reserved capacity`" class="w-full" />
                      </UFormField>
                    </div>
                  </section>
                  <UAlert v-if="quotaIssue" color="warning" variant="soft" icon="i-lucide-info" title="Complete this stage" :description="quotaIssue" />
                </div>

                <div v-else class="grid gap-6 2xl:grid-cols-[minmax(0,1.35fr)_minmax(22rem,0.65fr)]">
                  <section class="overflow-hidden rounded-xl border border-muted bg-default">
                    <div class="border-b border-muted bg-elevated px-5 py-4 sm:px-6">
                      <p class="text-xs font-bold uppercase tracking-[0.14em] text-primary">Opening summary</p>
                      <h3 class="mt-1 text-xl font-semibold text-highlighted">{{ intakeForm.name }}</h3>
                      <p class="text-sm text-muted">{{ intakeForm.code }} · {{ selectedAcademicYear?.name }}</p>
                    </div>
                    <dl class="grid divide-y divide-muted text-sm sm:grid-cols-2 sm:divide-x sm:divide-y-0">
                      <div class="p-5 sm:p-6">
                        <dt class="text-xs font-bold uppercase tracking-wider text-muted">Application window</dt>
                        <dd class="mt-2 font-semibold text-highlighted">{{ formatDate(intakeForm.startsOn) }} – {{ formatDate(intakeForm.endsOn) }}</dd>
                      </div>
                      <div class="p-5 sm:p-6">
                        <dt class="text-xs font-bold uppercase tracking-wider text-muted">Applicant choices</dt>
                        <dd class="mt-2 font-semibold text-highlighted">Up to {{ intakeForm.maximumProgrammeChoices }} Programmes</dd>
                      </div>
                    </dl>
                    <div class="grid gap-5 border-t border-muted p-5 sm:grid-cols-3 sm:p-6">
                      <div><p class="text-3xl font-semibold text-highlighted">{{ programmesCoveredByIntake.length }}</p><p class="mt-1 text-sm text-muted">Programmes</p></div>
                      <div><p class="text-3xl font-semibold text-highlighted">{{ assignedRoutes.length }}</p><p class="mt-1 text-sm text-muted">Application routes</p></div>
                      <div><p class="text-3xl font-semibold text-highlighted">{{ planningCapacity }}</p><p class="mt-1 text-sm text-muted">Planned places</p></div>
                    </div>
                    <div class="border-t border-muted p-5 sm:p-6">
                      <p class="text-xs font-bold uppercase tracking-wider text-muted">Programme Levels</p>
                      <div class="mt-3 flex flex-wrap gap-2"><UBadge v-for="level in selectedProgrammeLevels" :key="level.id" :label="level.name" color="neutral" variant="soft" /></div>
                      <p class="mt-5 text-xs font-bold uppercase tracking-wider text-muted">Routes opening</p>
                      <div class="mt-3 flex flex-wrap gap-2"><UBadge v-for="applicationRoute in assignedRoutes" :key="applicationRoute.applicationType.id" :label="applicationRoute.applicationType.code" color="primary" variant="soft" /></div>
                    </div>
                  </section>

                  <section class="rounded-xl border border-muted bg-elevated p-5 sm:p-6">
                    <p class="text-xs font-bold uppercase tracking-[0.14em] text-primary">Final governance check</p>
                    <h3 class="mt-2 text-xl font-semibold text-highlighted">Record why this intake is ready</h3>
                    <p class="mt-2 text-sm leading-6 text-muted">One reason is recorded across the audited intake, route, fee, and quota configuration.</p>
                    <UFormField label="Opening reason" required class="mt-5">
                      <UTextarea v-model="openingChangeReason" class="w-full" :rows="6" minlength="10" maxlength="1000" placeholder="Explain why this admissions intake is ready to open." />
                    </UFormField>
                    <UAlert
                      class="mt-5"
                      :color="openingIssue ? 'warning' : 'success'"
                      variant="soft"
                      :icon="openingIssue ? 'i-lucide-circle-alert' : 'i-lucide-badge-check'"
                      :title="openingIssue ? 'Opening requirement outstanding' : 'Ready to open applications'"
                      :description="openingIssue || 'The intake, application route coverage, fees, and planning quotas will be confirmed before applications open.'"
                    />
                  </section>
                </div>
              </form>

              <footer data-testid="intake-wizard-actions" class="fixed bottom-4 left-1/2 z-40 flex w-[min(calc(100vw-2rem),56rem)] -translate-x-1/2 flex-wrap items-center justify-between gap-3 rounded-xl border border-muted bg-default/95 px-4 py-3 shadow-xl backdrop-blur sm:px-5">
                <UButton label="Cancel" color="neutral" variant="ghost" to="/operations/academic-calendar" />
                <div class="ml-auto flex flex-wrap items-center gap-2">
                  <UButton v-if="setupStep > 1" label="Back" icon="i-lucide-arrow-left" color="neutral" variant="outline" @click="setupStep = (setupStep - 1) as 1 | 2 | 3 | 4" />
                  <EmhareGuidedActionButton
                    v-if="setupStep < 5"
                    :label="setupStep === 1 ? 'Continue to eligibility' : setupStep === 2 ? 'Continue to routes and fees' : setupStep === 3 ? 'Continue to Programme quotas' : 'Review admissions opening'"
                    trailing-icon="i-lucide-arrow-right"
                    guidance-title="Complete this stage before continuing"
                    :guidance-instructions="currentStepIssue ? [currentStepIssue] : []"
                    :guidance-action-label="setupStep === 3 && routeConfigurationIssue.includes('Application Types') ? 'Open Application Types' : undefined"
                    @guidance-action="navigateTo('/operations/application-types')"
                    @click="continueSetup"
                  />
                  <template v-else>
                    <UButton label="Save draft" icon="i-lucide-save" color="neutral" variant="outline" :loading="saving" @click="saveIntake(false)" />
                    <UButton :label="intakeForm.id ? 'Save and open intake' : 'Create and open intake'" icon="i-lucide-door-open" :loading="saving" :disabled="Boolean(openingIssue)" @click="saveIntake(true)" />
                  </template>
                </div>
              </footer>
            </main>
          </div>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
