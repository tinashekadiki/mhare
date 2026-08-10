<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { AcademicPeriodSummary, AcademicSetupOverview, RegistrationCatalogue } from '@emhare/portal-shell/types/academic'
import type {
  RegisteredModuleSummary,
  RegistrationSummary,
  StudentProgrammeEnrolmentSummary,
  StudentWorkspaceSummary
} from '@emhare/portal-shell/types/student-records'

const auth = useEmhareAuth()
const api = useEmhareApi()
const toast = useToast()
const { confirmAction, showError } = useEmhareConfirm()
const {
  semesterItems,
  studyPeriodLabel,
  toProgrammePeriodNumber,
  yearOfStudyItems
} = useProgrammeStudyPeriod()

const activeSection = ref('OVERVIEW')
const loading = ref(true)
const loadError = ref('')
const workspace = ref<StudentWorkspaceSummary | null>(null)
const registrations = ref<RegistrationSummary[]>([])
const academicOverview = ref<AcademicSetupOverview | null>(null)
const registrationDrawerOpen = ref(false)
const registrationDetailsOpen = ref(false)
const selectedRegistration = ref<RegistrationSummary | null>(null)
const registrationCatalogue = ref<RegistrationCatalogue | null>(null)
const loadingCatalogue = ref(false)
const savingRegistration = ref(false)
const submittingRegistrationId = ref<string | null>(null)
const registrationFormError = ref('')
const catalogueRequestSequence = ref(0)

const registrationForm = reactive({
  programmeEnrolmentId: '',
  academicPeriodId: '',
  yearOfStudy: 1,
  semesterNumber: 1,
  selectedElectiveCurriculumModuleIds: [] as string[]
})

const sectionItems = [
  { label: 'Overview', value: 'OVERVIEW', icon: 'i-lucide-layout-dashboard' },
  { label: 'Registrations', value: 'REGISTRATIONS', icon: 'i-lucide-clipboard-check' },
  { label: 'My Modules', value: 'MODULES', icon: 'i-lucide-book-open-check' }
]

const enrolmentColumns: TableColumn<StudentProgrammeEnrolmentSummary>[] = [
  { accessorKey: 'programmeCode', header: 'Programme' },
  { accessorKey: 'programmeName', header: 'Programme name' },
  { accessorKey: 'commencementDate', header: 'Commenced' },
  { accessorKey: 'status', header: 'Status' }
]

const registrationColumns: TableColumn<RegistrationSummary>[] = [
  { accessorKey: 'academicPeriodName', header: 'Academic period' },
  { accessorKey: 'programmeCode', header: 'Programme' },
  { accessorKey: 'programmePeriodNumber', header: 'Study stage' },
  { accessorKey: 'totalCredits', header: 'Credits' },
  { accessorKey: 'status', header: 'Status' },
  { id: 'actions', header: 'Actions' }
]

const moduleColumns: TableColumn<RegisteredModuleSummary>[] = [
  { accessorKey: 'moduleCode', header: 'Module code' },
  { accessorKey: 'moduleName', header: 'Module' },
  { accessorKey: 'curriculumModuleType', header: 'Requirement' },
  { accessorKey: 'creditValue', header: 'Credits' },
  { accessorKey: 'minimumMarkRequired', header: 'Pass mark' }
]

const activeEnrolments = computed(() =>
  workspace.value?.programmeEnrolments.filter(enrolment => enrolment.status === 'ACTIVE') ?? []
)
const selectedEnrolment = computed(() =>
  activeEnrolments.value.find(enrolment => enrolment.id === registrationForm.programmeEnrolmentId) ?? null
)
const selectedProgramme = computed(() =>
  academicOverview.value?.programmes.find(programme => programme.id === selectedEnrolment.value?.programmeId) ?? null
)
const yearOfStudyOptions = computed(() =>
  yearOfStudyItems(selectedProgramme.value?.maximumDurationPeriods ?? 2)
)
const programmeEnrolmentItems = computed(() => activeEnrolments.value.map(enrolment => ({
  label: `${enrolment.programmeCode} · ${enrolment.programmeName}`,
  value: enrolment.id,
  description: `Commenced ${formatDate(enrolment.commencementDate)}`
})))
const openAcademicPeriods = computed(() =>
  (academicOverview.value?.academicPeriods ?? [])
    .filter(period => period.status === 'OPEN')
    .sort((left, right) => left.startDate.localeCompare(right.startDate))
)
const academicPeriodItems = computed(() => openAcademicPeriods.value.map(period => ({
  label: `${period.code} · ${period.name}`,
  value: period.id,
  description: `${formatDate(period.startDate)} – ${formatDate(period.endDate)}`
})))
const programmePeriodNumber = computed(() =>
  toProgrammePeriodNumber(registrationForm.yearOfStudy, registrationForm.semesterNumber)
)
const compulsoryModules = computed(() =>
  registrationCatalogue.value?.modules.filter(module => module.moduleType === 'COMPULSORY') ?? []
)
const electiveModules = computed(() =>
  registrationCatalogue.value?.modules.filter(module => module.moduleType !== 'COMPULSORY') ?? []
)
const electiveItems = computed(() => electiveModules.value.map(module => ({
  label: `${module.moduleCode} · ${module.moduleName}`,
  value: module.curriculumModuleId,
  description: `${Number(module.creditValue).toFixed(2)} credits · ${module.moduleType.toLowerCase()}`
})))
const registrationFormReady = computed(() => Boolean(
  registrationForm.programmeEnrolmentId
  && registrationForm.academicPeriodId
  && registrationCatalogue.value
  && registrationCatalogue.value.modules.length
  && !loadingCatalogue.value
))
const registrationStartGuidance = computed(() => {
  const instructions: string[] = []
  if (!activeEnrolments.value.length) instructions.push('Registry must activate your programme enrolment before you can register.')
  if (!openAcademicPeriods.value.length) instructions.push('Registry must open an academic period for student registration.')
  return instructions
})
const registrationFormGuidance = computed(() => {
  const instructions: string[] = []
  if (!registrationForm.programmeEnrolmentId) instructions.push('Select your active programme enrolment.')
  if (!registrationForm.academicPeriodId) instructions.push('Select an open academic period.')
  if (loadingCatalogue.value) instructions.push('Wait for the curriculum Module catalogue to finish loading.')
  else if (!registrationCatalogue.value?.modules.length) instructions.push('Registry must attach active Modules to the applicable approved curriculum version.')
  return instructions
})
const latestConfirmedRegistration = computed(() =>
  registrations.value.find(registration => registration.status === 'CONFIRMED') ?? null
)
const currentModules = computed(() => latestConfirmedRegistration.value?.modules ?? [])
const totalConfirmedCredits = computed(() =>
  registrations.value
    .filter(registration => registration.status === 'CONFIRMED')
    .reduce((total, registration) => total + Number(registration.totalCredits), 0)
)

onMounted(loadWorkspace)

watch(
  () => [
    registrationForm.programmeEnrolmentId,
    registrationForm.academicPeriodId,
    registrationForm.yearOfStudy,
    registrationForm.semesterNumber
  ],
  () => {
    const timer = window.setTimeout(() => void loadRegistrationCatalogue(), 250)
    onWatcherCleanup(() => window.clearTimeout(timer))
  }
)

async function loadWorkspace() {
  loading.value = true
  loadError.value = ''
  try {
    await auth.syncCoreUser()
    const [studentWorkspace, ownedRegistrations, overview] = await Promise.all([
      api.request<StudentWorkspaceSummary>('/api/student-records/me'),
      api.request<RegistrationSummary[]>('/api/student-records/registrations/mine'),
      api.request<AcademicSetupOverview>('/api/academic/overview')
    ])
    workspace.value = studentWorkspace
    registrations.value = ownedRegistrations
    academicOverview.value = overview
  } catch (error) {
    loadError.value = api.errorMessage(error, 'The student workspace could not be loaded.')
  } finally {
    loading.value = false
  }
}

function openRegistrationDrawer() {
  const firstEnrolment = activeEnrolments.value[0]
  const firstPeriod = currentOrNextOpenPeriod(openAcademicPeriods.value)
  const nextPeriodNumber = nextProgrammePeriodNumber(firstEnrolment?.id)
  const nextStudyStage = fromInternalPeriod(nextPeriodNumber)
  Object.assign(registrationForm, {
    programmeEnrolmentId: firstEnrolment?.id ?? '',
    academicPeriodId: firstPeriod?.id ?? '',
    yearOfStudy: nextStudyStage.yearOfStudy,
    semesterNumber: nextStudyStage.semesterNumber,
    selectedElectiveCurriculumModuleIds: []
  })
  registrationCatalogue.value = null
  registrationFormError.value = ''
  registrationDrawerOpen.value = true
}

function nextProgrammePeriodNumber(programmeEnrolmentId?: string) {
  const latestRegistration = registrations.value.find(registration =>
    registration.programmeEnrolmentId === programmeEnrolmentId
    && registration.status !== 'CANCELLED')
  if (!latestRegistration) return 1
  return latestRegistration.status === 'CONFIRMED'
    ? latestRegistration.programmePeriodNumber + 1
    : latestRegistration.programmePeriodNumber
}

function fromInternalPeriod(periodNumber: number) {
  return {
    yearOfStudy: Math.ceil(periodNumber / 2),
    semesterNumber: ((periodNumber - 1) % 2) + 1
  }
}

async function loadRegistrationCatalogue() {
  const requestSequence = ++catalogueRequestSequence.value
  registrationCatalogue.value = null
  registrationForm.selectedElectiveCurriculumModuleIds = []
  registrationFormError.value = ''
  const enrolment = selectedEnrolment.value
  if (!registrationDrawerOpen.value || !enrolment || !registrationForm.academicPeriodId) return
  loadingCatalogue.value = true
  try {
    const loadedCatalogue = await api.request<RegistrationCatalogue>(
      `/api/academic/registration-catalogue?academicPeriodId=${encodeURIComponent(registrationForm.academicPeriodId)}&programmeVersionId=${encodeURIComponent(enrolment.programmeVersionId)}&periodNumber=${programmePeriodNumber.value}`
    )
    if (requestSequence === catalogueRequestSequence.value) {
      registrationCatalogue.value = loadedCatalogue
    }
  } catch (error) {
    if (requestSequence === catalogueRequestSequence.value) {
      registrationFormError.value = api.errorMessage(
        error,
        'No approved curriculum is available for this programme and study stage.'
      )
    }
  } finally {
    if (requestSequence === catalogueRequestSequence.value) {
      loadingCatalogue.value = false
    }
  }
}

async function createRegistration() {
  if (!registrationFormReady.value) return
  savingRegistration.value = true
  registrationFormError.value = ''
  try {
    const created = await api.request<RegistrationSummary>('/api/student-records/registrations/mine', {
      method: 'POST',
      body: {
        programmeEnrolmentId: registrationForm.programmeEnrolmentId,
        academicPeriodId: registrationForm.academicPeriodId,
        programmePeriodNumber: programmePeriodNumber.value,
        selectedElectiveCurriculumModuleIds: registrationForm.selectedElectiveCurriculumModuleIds
      }
    })
    registrations.value = [created, ...registrations.value]
    registrationDrawerOpen.value = false
    activeSection.value = 'REGISTRATIONS'
    toast.add({
      title: 'Draft registration created',
      description: 'Review the Modules and submit when the registration is complete.',
      color: 'success',
      icon: 'i-lucide-clipboard-check'
    })
  } catch (error) {
    registrationFormError.value = api.errorMessage(error, 'Registration could not be created.')
  } finally {
    savingRegistration.value = false
  }
}

async function submitRegistration(registration: RegistrationSummary) {
  const confirmed = await confirmAction({
    title: `Submit registration for ${registration.academicPeriodName}?`,
    text: 'You confirm that the selected Modules are correct. Academic and Registry approval will follow.',
    confirmButtonText: 'Confirm and submit',
    icon: 'question'
  })
  if (!confirmed) return
  submittingRegistrationId.value = registration.id
  try {
    const updated = await api.request<RegistrationSummary>(
      `/api/student-records/registrations/mine/${registration.id}/submit`,
      {
        method: 'POST',
        body: { expectedVersion: registration.version, declarationAccepted: true }
      }
    )
    replaceRegistration(updated)
    toast.add({
      title: 'Registration submitted',
      description: 'It is now awaiting academic approval.',
      color: 'success',
      icon: 'i-lucide-send'
    })
  } catch (error) {
    await showError('Registration could not be submitted', api.errorMessage(error))
  } finally {
    submittingRegistrationId.value = null
  }
}

function openRegistrationDetails(registration: RegistrationSummary) {
  selectedRegistration.value = registration
  registrationDetailsOpen.value = true
}

function replaceRegistration(updated: RegistrationSummary) {
  const index = registrations.value.findIndex(registration => registration.id === updated.id)
  if (index >= 0) registrations.value.splice(index, 1, updated)
  if (selectedRegistration.value?.id === updated.id) selectedRegistration.value = updated
}

function currentOrNextOpenPeriod(periods: AcademicPeriodSummary[]) {
  const today = new Date().toISOString().slice(0, 10)
  return periods.find(period => period.startDate <= today && period.endDate >= today)
    ?? periods.find(period => period.startDate > today)
    ?? periods[0]
}

function statusTone(status: string) {
  if (status === 'ACTIVE' || status === 'CONFIRMED') return 'success' as const
  if (status === 'DRAFT' || status === 'SUBMITTED' || status === 'ACADEMIC_APPROVED') return 'info' as const
  if (status === 'REJECTED' || status === 'SUSPENDED' || status === 'WITHDRAWN') return 'error' as const
  return 'neutral' as const
}

function formatDate(value?: string | null) {
  if (!value) return 'Not captured'
  return new Intl.DateTimeFormat('en-ZW', { dateStyle: 'medium' }).format(new Date(`${value}T00:00:00`))
}
</script>

<template>
  <div class="min-h-screen bg-default">
    <header class="border-b border-muted bg-elevated/70">
      <UContainer class="flex min-h-16 items-center justify-between gap-4 py-3">
        <div class="flex min-w-0 items-center gap-3">
          <div class="flex size-9 shrink-0 items-center justify-center rounded-md bg-primary text-sm font-bold text-inverted">e</div>
          <div class="min-w-0">
            <p class="truncate font-semibold text-highlighted">eMhare Student</p>
            <p class="truncate text-xs text-muted">Academic self-service</p>
          </div>
        </div>
        <div class="flex items-center gap-3">
          <div class="hidden text-right sm:block">
            <p class="text-sm font-medium text-highlighted">{{ auth.displayName.value }}</p>
            <p class="text-xs text-muted">{{ workspace?.studentNumber ?? 'Student account' }}</p>
          </div>
          <UButton label="Sign out" icon="i-lucide-log-out" color="neutral" variant="outline" @click="auth.logout" />
        </div>
      </UContainer>
    </header>

    <main>
      <UContainer class="space-y-5 py-5 sm:py-7">
        <EmharePageHeader
          title="Student workspace"
          description="Registration, approved Modules, and the institutional student record linked to your account."
          icon="i-lucide-graduation-cap"
          :badge="workspace?.studentNumber"
        >
          <template #actions>
            <UButton label="Refresh" icon="i-lucide-refresh-cw" color="neutral" variant="outline" :loading="loading" @click="loadWorkspace" />
            <EmhareGuidedActionButton
              label="Start registration"
              icon="i-lucide-clipboard-plus"
              guidance-title="Registration is not open yet"
              :guidance-instructions="registrationStartGuidance"
              @click="openRegistrationDrawer"
            />
          </template>
        </EmharePageHeader>

        <EmhareFeedbackState v-if="loading" state="loading" title="Loading student workspace" description="Retrieving the governed student record and registrations." />
        <EmhareFeedbackState v-else-if="loadError" state="error" title="Student workspace unavailable" :description="loadError">
          <UButton label="Try again" icon="i-lucide-refresh-cw" @click="loadWorkspace" />
        </EmhareFeedbackState>

        <template v-else-if="workspace">
          <UAlert
            v-if="workspace.status !== 'ACTIVE'"
            color="warning"
            variant="soft"
            icon="i-lucide-shield-alert"
            title="Student account is not active"
            :description="`Current status: ${workspace.status}. Registration remains unavailable until Registry completes activation.`"
          />

          <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <EmhareKpiCard label="Student number" :value="workspace.studentNumber" hint="Institutional identifier" icon="i-lucide-id-card" tone="primary" />
            <EmhareKpiCard label="Active programmes" :value="activeEnrolments.length" hint="Current programme enrolments" icon="i-lucide-graduation-cap" tone="success" />
            <EmhareKpiCard label="Confirmed registrations" :value="registrations.filter(item => item.status === 'CONFIRMED').length" hint="Approved academic periods" icon="i-lucide-badge-check" tone="success" />
            <EmhareKpiCard label="Confirmed credits" :value="totalConfirmedCredits.toFixed(2)" hint="Across confirmed registrations" icon="i-lucide-sigma" tone="warning" />
          </div>

          <div class="rounded-md border border-muted bg-elevated/35 p-1">
            <UTabs v-model="activeSection" :items="sectionItems" :content="false" value-key="value" color="primary" variant="pill" />
          </div>

          <section v-if="activeSection === 'OVERVIEW'" class="grid gap-5 xl:grid-cols-[0.8fr_1.2fr]">
            <UCard>
              <template #header>
                <div>
                  <h2 class="font-semibold text-highlighted">Student identity</h2>
                  <p class="text-sm text-muted">Read-only information governed by Registry.</p>
                </div>
              </template>
              <EmhareDescriptionList :items="[
                { label: 'Full name', value: [workspace.firstName, workspace.middleNames, workspace.lastName].filter(Boolean).join(' ') },
                { label: 'Student number', value: workspace.studentNumber },
                { label: 'Email', value: workspace.primaryEmail },
                { label: 'Phone', value: workspace.primaryPhone },
                { label: 'Date of birth', value: formatDate(workspace.dateOfBirth) },
                { label: 'Account status', value: workspace.status }
              ]" />
            </UCard>

            <UCard :ui="{ body: 'p-0' }">
              <template #header>
                <div>
                  <h2 class="font-semibold text-highlighted">Programme enrolments</h2>
                  <p class="text-sm text-muted">Current and historical programme assignments.</p>
                </div>
              </template>
              <EmharePaginatedTable :data="workspace.programmeEnrolments" :columns="enrolmentColumns">
                <template #programmeCode-cell="{ row }"><span class="font-mono font-semibold text-primary">{{ row.original.programmeCode }}</span></template>
                <template #commencementDate-cell="{ row }">{{ formatDate(row.original.commencementDate) }}</template>
                <template #status-cell="{ row }"><EmhareStatusPill :label="row.original.status" :tone="statusTone(row.original.status)" /></template>
                <template #empty><div class="py-10 text-center text-sm text-muted">No programme enrolments are linked to this student.</div></template>
              </EmharePaginatedTable>
            </UCard>
          </section>

          <section v-else-if="activeSection === 'REGISTRATIONS'">
            <UCard :ui="{ body: 'p-0' }">
              <template #header>
                <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <h2 class="font-semibold text-highlighted">Registration history</h2>
                    <p class="text-sm text-muted">Draft, submitted, approved, confirmed, and rejected registration sessions.</p>
                  </div>
                  <EmhareGuidedActionButton label="Start registration" icon="i-lucide-clipboard-plus" guidance-title="Registration is not open yet" :guidance-instructions="registrationStartGuidance" @click="openRegistrationDrawer" />
                </div>
              </template>
              <EmharePaginatedTable :data="registrations" :columns="registrationColumns" sticky>
                <template #programmeCode-cell="{ row }"><span class="font-mono font-semibold text-primary">{{ row.original.programmeCode }}</span></template>
                <template #programmePeriodNumber-cell="{ row }"><span class="whitespace-nowrap">{{ studyPeriodLabel(row.original.programmePeriodNumber) }}</span></template>
                <template #totalCredits-cell="{ row }">{{ Number(row.original.totalCredits).toFixed(2) }}</template>
                <template #status-cell="{ row }"><EmhareStatusPill :label="row.original.status" :tone="statusTone(row.original.status)" /></template>
                <template #actions-cell="{ row }">
                  <div class="flex items-center justify-end gap-2">
                    <UButton :aria-label="`View ${row.original.academicPeriodName} registration`" icon="i-lucide-eye" color="neutral" variant="ghost" @click="openRegistrationDetails(row.original)" />
                    <UButton
                      v-if="row.original.status === 'DRAFT'"
                      :aria-label="`Submit ${row.original.academicPeriodName} registration`"
                      icon="i-lucide-send"
                      color="primary"
                      variant="soft"
                      :loading="submittingRegistrationId === row.original.id"
                      @click="submitRegistration(row.original)"
                    />
                  </div>
                </template>
                <template #empty><div class="py-12 text-center"><UIcon name="i-lucide-clipboard-x" class="mx-auto size-8 text-muted" /><p class="mt-3 font-medium text-highlighted">No registration history</p><p class="mt-1 text-sm text-muted">Start registration when an academic period is open.</p></div></template>
              </EmharePaginatedTable>
            </UCard>
          </section>

          <section v-else>
            <UCard :ui="{ body: 'p-0' }">
              <template #header>
                <div>
                  <h2 class="font-semibold text-highlighted">My confirmed Modules</h2>
                  <p class="text-sm text-muted">{{ latestConfirmedRegistration ? `${latestConfirmedRegistration.academicPeriodName} · ${studyPeriodLabel(latestConfirmedRegistration.programmePeriodNumber)}` : 'Modules appear after Registry confirms a registration.' }}</p>
                </div>
              </template>
              <EmharePaginatedTable :data="currentModules" :columns="moduleColumns">
                <template #moduleCode-cell="{ row }"><span class="font-mono font-semibold text-primary">{{ row.original.moduleCode }}</span></template>
                <template #curriculumModuleType-cell="{ row }"><EmhareStatusPill :label="row.original.curriculumModuleType" :tone="row.original.curriculumModuleType === 'COMPULSORY' ? 'info' : 'neutral'" /></template>
                <template #creditValue-cell="{ row }">{{ Number(row.original.creditValue).toFixed(2) }}</template>
                <template #minimumMarkRequired-cell="{ row }">{{ row.original.minimumMarkRequired == null ? 'Institution rule' : `${Number(row.original.minimumMarkRequired).toFixed(2)}%` }}</template>
                <template #empty><div class="py-12 text-center"><UIcon name="i-lucide-book-dashed" class="mx-auto size-8 text-muted" /><p class="mt-3 font-medium text-highlighted">No confirmed Modules</p><p class="mt-1 text-sm text-muted">Complete the registration approval workflow to populate this register.</p></div></template>
              </EmharePaginatedTable>
            </UCard>
          </section>
        </template>
      </UContainer>
    </main>
  </div>

  <EmhareRecordDrawer
    v-model:open="registrationDrawerOpen"
    title="Start academic registration"
    description="Select the academic period and study stage. Compulsory Modules are included automatically."
    width="xl"
  >
    <template #body>
      <form id="student-registration-form" class="space-y-5" @submit.prevent="createRegistration">
        <EmhareErrorSummary :errors="registrationFormError ? [registrationFormError] : []" title="Registration cannot continue" />

        <div class="grid gap-4 sm:grid-cols-2">
          <UFormField label="Programme enrolment" required class="sm:col-span-2">
            <USelect v-model="registrationForm.programmeEnrolmentId" :items="programmeEnrolmentItems" value-key="value" class="w-full" />
          </UFormField>
          <UFormField label="Academic period" required class="sm:col-span-2">
            <USelect v-model="registrationForm.academicPeriodId" :items="academicPeriodItems" value-key="value" class="w-full" />
          </UFormField>
          <UFormField label="Year of study" required>
            <USelect v-model="registrationForm.yearOfStudy" :items="yearOfStudyOptions" value-key="value" class="w-full" />
          </UFormField>
          <UFormField label="Semester" required>
            <USelect v-model="registrationForm.semesterNumber" :items="semesterItems" value-key="value" class="w-full" />
          </UFormField>
        </div>

        <EmhareFeedbackState v-if="loadingCatalogue" state="loading" title="Validating approved curriculum" description="Loading the Modules available for this study stage." />

        <template v-else-if="registrationCatalogue">
          <UAlert
            color="info"
            variant="soft"
            icon="i-lucide-calendar-range"
            :title="`${registrationCatalogue.academicPeriodCode} · ${registrationCatalogue.academicPeriodName}`"
            :description="`${studyPeriodLabel(registrationCatalogue.periodNumber)} · ${registrationCatalogue.programmeCode} · curriculum ${registrationCatalogue.programmeVersionCode}`"
          />

          <div>
            <h3 class="text-sm font-semibold text-highlighted">Compulsory Modules</h3>
            <p class="mt-1 text-xs text-muted">These Modules are governed by the approved curriculum and cannot be removed.</p>
            <div class="mt-3 space-y-2">
              <div v-for="module in compulsoryModules" :key="module.curriculumModuleId" class="flex items-start justify-between gap-4 rounded-md border border-muted bg-elevated/40 p-3">
                <div>
                  <p class="text-sm font-medium text-highlighted"><span class="font-mono text-primary">{{ module.moduleCode }}</span> · {{ module.moduleName }}</p>
                  <p class="mt-1 text-xs text-muted">{{ Number(module.creditValue).toFixed(2) }} credits</p>
                </div>
                <EmhareStatusPill label="Included" tone="success" />
              </div>
            </div>
          </div>

          <div v-if="electiveItems.length">
            <UFormField label="Elective Modules" description="Select only the electives you intend to register.">
              <UCheckboxGroup v-model="registrationForm.selectedElectiveCurriculumModuleIds" :items="electiveItems" class="mt-2" />
            </UFormField>
          </div>
        </template>
      </form>
    </template>
    <template #footer>
      <div class="flex w-full items-center justify-end gap-3">
        <UButton label="Cancel" color="neutral" variant="ghost" :disabled="savingRegistration" @click="registrationDrawerOpen = false" />
        <EmhareGuidedActionButton type="submit" form="student-registration-form" label="Create draft registration" icon="i-lucide-clipboard-plus" :loading="savingRegistration" guidance-title="Registration details are incomplete" :guidance-instructions="registrationFormGuidance" />
      </div>
    </template>
  </EmhareRecordDrawer>

  <EmhareRecordDrawer
    v-model:open="registrationDetailsOpen"
    title="Registration details"
    description="Governed status, programme context, and registered Modules."
    width="xl"
  >
    <template #body>
      <template v-if="selectedRegistration">
        <EmhareDescriptionList :items="[
          { label: 'Academic period', value: selectedRegistration.academicPeriodName },
          { label: 'Study stage', value: studyPeriodLabel(selectedRegistration.programmePeriodNumber) },
          { label: 'Programme', value: `${selectedRegistration.programmeCode} · ${selectedRegistration.programmeName}` },
          { label: 'Registration type', value: selectedRegistration.registrationType },
          { label: 'Status', value: selectedRegistration.status },
          { label: 'Total credits', value: Number(selectedRegistration.totalCredits).toFixed(2) }
        ]" />
        <UAlert color="neutral" variant="soft" icon="i-lucide-message-square-text" title="Latest status reason" :description="selectedRegistration.statusReason" />
        <div>
          <h3 class="mb-3 text-sm font-semibold text-highlighted">Registered Modules</h3>
          <EmharePaginatedTable :data="selectedRegistration.modules" :columns="moduleColumns">
            <template #moduleCode-cell="{ row }"><span class="font-mono font-semibold text-primary">{{ row.original.moduleCode }}</span></template>
            <template #creditValue-cell="{ row }">{{ Number(row.original.creditValue).toFixed(2) }}</template>
            <template #minimumMarkRequired-cell="{ row }">{{ row.original.minimumMarkRequired == null ? 'Institution rule' : `${Number(row.original.minimumMarkRequired).toFixed(2)}%` }}</template>
          </EmharePaginatedTable>
        </div>
      </template>
    </template>
    <template #footer>
      <div class="flex w-full justify-end">
        <UButton label="Close" color="neutral" variant="outline" @click="registrationDetailsOpen = false" />
      </div>
    </template>
  </EmhareRecordDrawer>
</template>
