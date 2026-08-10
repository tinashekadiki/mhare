<script setup lang="ts">
import Swal from 'sweetalert2'
import type { AcademicSetupOverview, RegistrationCatalogue } from '@emhare/portal-shell/types/academic'
import type {
  RegistrationStatus,
  RegistrationSummary,
  RegistrationType,
  StudentConversionSummary
} from '@emhare/portal-shell/types/student-records'

definePageMeta({ layout: 'dashboard' })

const api = useEmhareApi()
const toast = useToast()
const { showError } = useEmhareConfirm()
const academicPeriodContext = useAcademicPeriodContext()
const { semesterItems, studyPeriodLabel, toProgrammePeriodNumber, yearOfStudyItems } = useProgrammeStudyPeriod()
const registrations = ref<RegistrationSummary[]>([])
const conversions = ref<StudentConversionSummary[]>([])
const academicOverview = ref<AcademicSetupOverview | null>(null)
const registrationCatalogue = ref<RegistrationCatalogue | null>(null)
const loading = ref(false)
const catalogueLoading = ref(false)
const saving = ref(false)
const activeActionId = ref<string | null>(null)
const loadError = ref('')
const startModalOpen = ref(false)
const activeFilter = ref<'ALL' | RegistrationStatus>('ALL')
const selectedElectiveIds = ref<string[]>([])
const registrationForm = reactive({
  conversionId: '',
  academicPeriodId: '',
  yearOfStudy: 1,
  semesterNumber: 1,
  registrationType: 'NORMAL' as RegistrationType
})

const filterItems = [
  { label: 'All registrations', value: 'ALL' },
  { label: 'Draft', value: 'DRAFT' },
  { label: 'Academic review', value: 'SUBMITTED' },
  { label: 'Registry confirmation', value: 'ACADEMIC_APPROVED' },
  { label: 'Confirmed', value: 'CONFIRMED' },
  { label: 'Rejected', value: 'REJECTED' }
]

const candidateConversions = computed(() => conversions.value.filter(conversion =>
  conversion.status === 'COMPLETED'
  && conversion.studentStatus === 'ACTIVE'
  && conversion.programmeEnrolmentStatus === 'ACTIVE'
))
const candidateItems = computed(() => candidateConversions.value.map(conversion => ({
  label: `${conversion.studentNumber} · ${conversion.programmeCode} · ${conversion.programmeName}`,
  value: conversion.id
})))
const openPeriodItems = computed(() => (academicOverview.value?.academicPeriods ?? [])
  .filter(period => period.status === 'OPEN' && academicPeriodContext.matchesAcademicPeriod(period))
  .map(period => ({ label: `${period.code} · ${period.name}`, value: period.id })))
const selectedConversion = computed(() => candidateConversions.value.find(conversion =>
  conversion.id === registrationForm.conversionId
) ?? null)
const selectedProgramme = computed(() => (academicOverview.value?.programmes ?? []).find(programme =>
  programme.id === selectedConversion.value?.programmeId
) ?? null)
const yearOfStudyOptions = computed(() => yearOfStudyItems(selectedProgramme.value?.maximumDurationPeriods ?? 16))
const selectedProgrammePeriodNumber = computed(() =>
  toProgrammePeriodNumber(registrationForm.yearOfStudy, registrationForm.semesterNumber)
)
const compulsoryModules = computed(() => registrationCatalogue.value?.modules.filter(module =>
  module.moduleType === 'COMPULSORY'
) ?? [])
const electiveModules = computed(() => registrationCatalogue.value?.modules.filter(module =>
  module.moduleType !== 'COMPULSORY'
) ?? [])
const filteredRegistrations = computed(() => registrations.value.filter(registration =>
  activeFilter.value === 'ALL' || registration.status === activeFilter.value
))
const totals = computed(() => ({
  total: registrations.value.length,
  academicReview: registrations.value.filter(item => item.status === 'SUBMITTED').length,
  registryReview: registrations.value.filter(item => item.status === 'ACADEMIC_APPROVED').length,
  confirmed: registrations.value.filter(item => item.status === 'CONFIRMED').length
}))

watch(
  () => [registrationForm.conversionId, registrationForm.academicPeriodId, registrationForm.yearOfStudy, registrationForm.semesterNumber],
  () => {
    registrationCatalogue.value = null
    selectedElectiveIds.value = []
  }
)

onMounted(loadWorkspace)
watch(academicPeriodContext.selectedAcademicPeriodId, () => void loadWorkspace())

async function loadWorkspace() {
  loading.value = true
  loadError.value = ''
  try {
    const [registrationData, conversionData, academicData] = await Promise.all([
      api.request<RegistrationSummary[]>('/api/student-records/registrations'),
      api.request<StudentConversionSummary[]>('/api/student-records/conversions'),
      api.request<AcademicSetupOverview>('/api/academic/overview')
    ])
    registrations.value = registrationData.filter(registration => (
      academicPeriodContext.matchesAcademicPeriod(registration)
    ))
    conversions.value = conversionData
    academicOverview.value = academicData
  } catch (error) {
    loadError.value = api.errorMessage(error, 'Student registration operations could not be loaded.')
  } finally {
    loading.value = false
  }
}

function openStartRegistration() {
  registrationForm.conversionId = ''
  registrationForm.academicPeriodId = academicPeriodContext.selectedAcademicPeriodId.value ?? ''
  registrationForm.yearOfStudy = 1
  registrationForm.semesterNumber = 1
  registrationForm.registrationType = 'NORMAL'
  registrationCatalogue.value = null
  selectedElectiveIds.value = []
  startModalOpen.value = true
}

async function loadApprovedCurriculum() {
  if (!selectedConversion.value || !registrationForm.academicPeriodId) {
    await showError('Registration details are incomplete', 'Select a student, an open academic period, year of study, and semester.')
    return
  }
  catalogueLoading.value = true
  try {
    const params = new URLSearchParams({
      academicPeriodId: registrationForm.academicPeriodId,
      programmeVersionId: selectedConversion.value.programmeVersionId,
      periodNumber: String(selectedProgrammePeriodNumber.value)
    })
    registrationCatalogue.value = await api.request<RegistrationCatalogue>(
      `/api/academic/registration-catalogue?${params.toString()}`
    )
  } catch (error) {
    registrationCatalogue.value = null
    await showError('Approved curriculum could not be loaded', api.errorMessage(error))
  } finally {
    catalogueLoading.value = false
  }
}

function toggleElective(curriculumModuleId: string, selected: boolean) {
  selectedElectiveIds.value = selected
    ? [...new Set([...selectedElectiveIds.value, curriculumModuleId])]
    : selectedElectiveIds.value.filter(id => id !== curriculumModuleId)
}

async function startRegistration() {
  if (!selectedConversion.value || !registrationCatalogue.value) return
  saving.value = true
  try {
    const created = await api.request<RegistrationSummary>('/api/student-records/registrations', {
      method: 'POST',
      body: {
        studentId: selectedConversion.value.studentId,
        programmeEnrolmentId: selectedConversion.value.programmeEnrolmentId,
        academicPeriodId: registrationForm.academicPeriodId,
        programmePeriodNumber: selectedProgrammePeriodNumber.value,
        registrationType: registrationForm.registrationType,
        selectedElectiveCurriculumModuleIds: selectedElectiveIds.value
      }
    })
    registrations.value = [created, ...registrations.value]
    startModalOpen.value = false
    toast.add({
      title: 'Registration draft created',
      description: `${created.studentNumber} has ${created.modules.length} approved curriculum Module${created.modules.length === 1 ? '' : 's'}.`,
      color: 'success'
    })
  } catch (error) {
    await showError('Registration could not be started', api.errorMessage(error))
  } finally {
    saving.value = false
  }
}

async function moveRegistration(
  registration: RegistrationSummary,
  action: 'submit' | 'academic-approve' | 'confirm' | 'reject'
) {
  const labels = {
    submit: ['Submit registration?', 'Submit for academic review'],
    'academic-approve': ['Approve Module registration?', 'Record academic approval'],
    confirm: ['Confirm student registration?', 'Confirm registration'],
    reject: ['Reject registration?', 'Reject registration']
  } as const
  const result = await Swal.fire({
    title: labels[action][0],
    text: action === 'confirm'
      ? 'Confirmation publishes the authoritative Module roster to Assessment/Results and Exams.'
      : 'This decision is retained in the registration status history.',
    icon: action === 'reject' ? 'warning' : 'question',
    input: 'textarea',
    inputLabel: 'Decision reason',
    inputPlaceholder: 'Record the evidence and reason for this decision.',
    inputAttributes: { maxlength: '1000' },
    showCancelButton: true,
    confirmButtonText: labels[action][1],
    confirmButtonColor: action === 'reject' ? '#b42318' : '#006633',
    inputValidator: value => value.trim() ? undefined : 'A decision reason is required.'
  })
  if (!result.isConfirmed || !result.value?.trim()) return

  activeActionId.value = registration.id
  try {
    const updated = await api.request<RegistrationSummary>(
      `/api/student-records/registrations/${registration.id}/${action}`,
      { method: 'POST', body: { expectedVersion: registration.version, reason: result.value.trim() } }
    )
    registrations.value = registrations.value.map(existing => existing.id === updated.id ? updated : existing)
    toast.add({
      title: `Registration ${formatStatus(updated.status).toLowerCase()}`,
      description: `${updated.studentNumber} · ${updated.academicPeriodCode}`,
      color: updated.status === 'REJECTED' ? 'warning' : 'success'
    })
  } catch (error) {
    await showError('Registration decision could not be recorded', api.errorMessage(error))
  } finally {
    activeActionId.value = null
  }
}

function formatStatus(value: string) {
  return value.toLowerCase().split('_').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(' ')
}

function statusTone(status: RegistrationStatus) {
  if (status === 'CONFIRMED') return 'success' as const
  if (status === 'REJECTED' || status === 'CANCELLED') return 'error' as const
  if (status === 'ACADEMIC_APPROVED') return 'warning' as const
  return status === 'SUBMITTED' ? 'info' as const : 'neutral' as const
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Student registration">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right>
          <UButton label="Start registration" icon="i-lucide-file-plus-2" color="primary" @click="openStartRegistration" />
          <UButton label="Refresh" icon="i-lucide-refresh-cw" color="neutral" variant="outline" :loading="loading" @click="loadWorkspace" />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="space-y-6 p-4 sm:p-6">
        <UAlert
          color="primary"
          variant="soft"
          icon="i-lucide-shield-check"
          title="Authoritative Module registration"
          description="Compulsory Modules come from the approved programme version. Academic approval and Registry confirmation are separate decisions; only confirmed registrations feed Assessment/Results and Exams."
        />
        <UAlert v-if="loadError" color="error" variant="soft" icon="i-lucide-triangle-alert" title="Registration workspace unavailable" :description="loadError" />

        <section class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4" aria-label="Registration queue summary">
          <UCard :ui="{ body: 'p-4' }"><p class="text-xs font-medium uppercase tracking-wide text-muted">Total</p><p class="mt-2 text-2xl font-semibold text-highlighted">{{ totals.total }}</p></UCard>
          <UCard :ui="{ body: 'p-4' }"><p class="text-xs font-medium uppercase tracking-wide text-info">Academic review</p><p class="mt-2 text-2xl font-semibold text-highlighted">{{ totals.academicReview }}</p></UCard>
          <UCard :ui="{ body: 'p-4' }"><p class="text-xs font-medium uppercase tracking-wide text-warning">Registry review</p><p class="mt-2 text-2xl font-semibold text-highlighted">{{ totals.registryReview }}</p></UCard>
          <UCard :ui="{ body: 'p-4' }"><p class="text-xs font-medium uppercase tracking-wide text-success">Confirmed</p><p class="mt-2 text-2xl font-semibold text-highlighted">{{ totals.confirmed }}</p></UCard>
        </section>

        <section class="space-y-3" aria-labelledby="registration-queue-heading">
          <div class="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
            <div><p class="text-xs font-medium uppercase tracking-wide text-primary">Controlled work queue</p><h2 id="registration-queue-heading" class="mt-1 text-lg font-semibold text-highlighted">Registration decisions</h2></div>
            <USelect v-model="activeFilter" :items="filterItems" value-key="value" aria-label="Filter registrations" class="w-full sm:w-56" />
          </div>

          <div v-if="loading && !registrations.length" class="space-y-3"><USkeleton v-for="index in 3" :key="index" class="h-48 w-full" /></div>
          <EmharePaginatedCollection v-else v-slot="{ items: paginatedRegistrations }" :items="filteredRegistrations">
          <div class="space-y-3">
            <UCard v-for="registration in paginatedRegistrations" :key="registration.id" variant="outline">
              <div class="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
                <div class="min-w-0 flex-1">
                  <div class="flex flex-wrap items-center gap-2">
                    <p class="font-mono text-xs text-muted">{{ registration.studentNumber }}</p>
                    <EmhareStatusPill :label="formatStatus(registration.status)" :tone="statusTone(registration.status)" />
                    <UBadge color="neutral" variant="soft">{{ formatStatus(registration.registrationType) }}</UBadge>
                  </div>
                  <h3 class="mt-2 font-semibold text-highlighted">{{ registration.studentName }} · {{ registration.programmeCode }}</h3>
                  <p class="mt-1 text-sm text-muted">{{ registration.academicPeriodCode }} · {{ studyPeriodLabel(registration.programmePeriodNumber) }} · {{ registration.totalCredits }} credits</p>
                  <p class="mt-3 text-sm text-highlighted">{{ registration.statusReason }}</p>
                  <div class="mt-3 flex flex-wrap gap-2">
                    <UBadge v-for="module in registration.modules" :key="module.id" color="primary" variant="subtle">
                      {{ module.moduleCode }} · {{ module.creditValue }} credits
                    </UBadge>
                  </div>
                </div>
                <div class="flex flex-wrap gap-2 xl:max-w-sm xl:justify-end">
                  <UButton v-if="registration.status === 'DRAFT'" label="Submit" icon="i-lucide-send" :loading="activeActionId === registration.id" @click="moveRegistration(registration, 'submit')" />
                  <UButton v-if="registration.status === 'SUBMITTED'" label="Academic approve" icon="i-lucide-badge-check" :loading="activeActionId === registration.id" @click="moveRegistration(registration, 'academic-approve')" />
                  <UButton v-if="registration.status === 'ACADEMIC_APPROVED'" label="Registry confirm" icon="i-lucide-shield-check" :loading="activeActionId === registration.id" @click="moveRegistration(registration, 'confirm')" />
                  <UButton v-if="registration.status === 'SUBMITTED' || registration.status === 'ACADEMIC_APPROVED'" label="Reject" icon="i-lucide-circle-x" color="error" variant="soft" :loading="activeActionId === registration.id" @click="moveRegistration(registration, 'reject')" />
                </div>
              </div>
            </UCard>
            <UEmpty v-if="!filteredRegistrations.length" title="No registrations in this queue" description="Start a registration from an active student programme enrolment and open academic period." icon="i-lucide-clipboard-list" />
          </div>
          </EmharePaginatedCollection>
        </section>
      </div>
    </template>
  </UDashboardPanel>

  <EmhareRecordDrawer v-model:open="startModalOpen" title="Start student registration" description="Build the Module load from the approved programme version and open academic period." width="xl">
    <template #body>
      <form id="student-registration-form" class="space-y-4" @submit.prevent="startRegistration">
        <div class="grid gap-4 sm:grid-cols-2">
          <UFormField label="Active student enrolment" required class="sm:col-span-2"><USelect v-model="registrationForm.conversionId" :items="candidateItems" value-key="value" searchable class="w-full" /></UFormField>
          <UFormField label="Open academic period" required><USelect v-model="registrationForm.academicPeriodId" :items="openPeriodItems" value-key="value" class="w-full" /></UFormField>
          <UFormField label="Year of study" required><USelect v-model="registrationForm.yearOfStudy" :items="yearOfStudyOptions" value-key="value" class="w-full" /></UFormField>
          <UFormField label="Semester" required><USelect v-model="registrationForm.semesterNumber" :items="semesterItems" value-key="value" class="w-full" /></UFormField>
          <UFormField label="Registration type" required><USelect v-model="registrationForm.registrationType" :items="[{label:'Normal',value:'NORMAL'},{label:'Late',value:'LATE'},{label:'Amendment',value:'AMENDMENT'}]" value-key="value" class="w-full" /></UFormField>
          <div class="flex items-end"><UButton label="Load approved curriculum" icon="i-lucide-list-tree" color="neutral" variant="outline" :loading="catalogueLoading" @click="loadApprovedCurriculum" /></div>
        </div>

        <div v-if="registrationCatalogue" class="space-y-3 rounded-lg border border-muted p-4">
          <div><p class="text-sm font-semibold text-highlighted">{{ registrationCatalogue.programmeCode }} · {{ registrationCatalogue.academicPeriodCode }}</p><p class="text-xs text-muted">Programme version {{ registrationCatalogue.programmeVersionCode }} · {{ studyPeriodLabel(registrationCatalogue.periodNumber) }}</p></div>
          <div><p class="text-xs font-medium uppercase tracking-wide text-muted">Automatically included</p><div class="mt-2 grid gap-2 sm:grid-cols-2"><div v-for="module in compulsoryModules" :key="module.curriculumModuleId" class="rounded-md bg-primary/5 p-3 text-sm"><span class="font-medium text-highlighted">{{ module.moduleCode }}</span><span class="text-muted"> · {{ module.moduleName }} · {{ module.creditValue }} credits</span></div></div></div>
          <div v-if="electiveModules.length"><p class="text-xs font-medium uppercase tracking-wide text-muted">Elective and optional selection</p><div class="mt-2 grid gap-2 sm:grid-cols-2"><label v-for="module in electiveModules" :key="module.curriculumModuleId" class="flex cursor-pointer items-start gap-3 rounded-md border border-muted p-3"><UCheckbox :model-value="selectedElectiveIds.includes(module.curriculumModuleId)" @update:model-value="value => toggleElective(module.curriculumModuleId, Boolean(value))" /><span class="text-sm"><span class="font-medium text-highlighted">{{ module.moduleCode }}</span><span class="block text-xs text-muted">{{ module.moduleName }} · {{ module.creditValue }} credits</span></span></label></div></div>
        </div>
      </form>
    </template>
    <template #footer><UButton label="Cancel" color="neutral" variant="ghost" @click="startModalOpen = false" /><EmhareGuidedActionButton type="submit" form="student-registration-form" label="Create registration draft" icon="i-lucide-file-plus-2" :loading="saving" guidance-title="Approved curriculum not loaded" :guidance-instructions="registrationCatalogue ? [] : ['Select an active student enrolment, open academic period, year, and semester, then choose Load approved curriculum.']" /></template>
  </EmhareRecordDrawer>
</template>
