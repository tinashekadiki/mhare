<script setup lang="ts">
import Swal from 'sweetalert2'
import type { TableColumn } from '@nuxt/ui'
import type { CurriculumModuleSummary, CurriculumModuleUsageSummary, ProgrammeVersionSummary } from '@emhare/portal-shell/types/academic'

definePageMeta({ layout: 'dashboard' })

const route = useRoute()
const api = useEmhareApi()
const toast = useToast()
const { confirmAction, showError } = useEmhareConfirm()
const { semesterItems, studyPeriodLabel, toProgrammePeriodNumber, fromProgrammePeriodNumber, yearOfStudyItems } = useProgrammeStudyPeriod()
const academicSetup = useAcademicSetup()
const selectedProgrammeId = ref(typeof route.query.programmeId === 'string' ? route.query.programmeId : '')
const selectedVersionId = ref('')
const programmeVersions = ref<ProgrammeVersionSummary[]>([])
const curriculumModules = ref<CurriculumModuleSummary[]>([])
const loadingDetail = ref(false)
const saving = ref(false)
const activeVersionActionId = ref<string | null>(null)
const versionModalOpen = ref(false)
const curriculumModalOpen = ref(false)
const curriculumDrawerMode = ref<'add' | 'edit'>('add')
const selectedCurriculumModule = ref<CurriculumModuleSummary | null>(null)
const versionForm = reactive({ versionCode: '', effectiveFrom: '', effectiveTo: '' })
const curriculumForm = reactive({ moduleId: '', yearOfStudy: 1, semesterNumber: 1, moduleType: 'COMPULSORY', creditValue: 12, minimumMarkRequired: 50, sortOrder: 1, changeReason: '' })

const curriculumColumns: TableColumn<CurriculumModuleSummary>[] = [
  { accessorKey: 'sortOrder', header: '#' },
  { accessorKey: 'moduleCode', header: 'Module code' },
  { accessorKey: 'moduleName', header: 'Module' },
  { accessorKey: 'periodNumber', header: 'Study stage' },
  { accessorKey: 'moduleType', header: 'Requirement' },
  { accessorKey: 'creditValue', header: 'Credits' },
  { accessorKey: 'minimumMarkRequired', header: 'Pass mark' },
  { id: 'actions', header: 'Actions' }
]

const curriculumDrawerTitle = computed(() => curriculumDrawerMode.value === 'add'
  ? 'Add Module to curriculum'
  : `Amend ${selectedCurriculumModule.value?.moduleCode ?? 'curriculum Module'}`)
const curriculumDrawerDescription = computed(() => curriculumDrawerMode.value === 'add'
  ? 'Place an active Module in this governed programme version.'
  : 'Correct the study stage, requirement, credits, pass mark, or ordering with an audited reason.')

const programmes = computed(() => academicSetup.overview.value?.programmes ?? [])
const modules = computed(() => academicSetup.overview.value?.modules ?? [])
const programmeItems = computed(() => programmes.value.map(programme => ({ label: `${programme.code} · ${programme.name}`, value: programme.id })))
const selectedProgramme = computed(() => programmes.value.find(programme => programme.id === selectedProgrammeId.value))
const yearOfStudyOptions = computed(() => yearOfStudyItems(selectedProgramme.value?.maximumDurationPeriods ?? 16))
const selectedVersion = computed(() => programmeVersions.value.find(version => version.id === selectedVersionId.value))
const availableModuleItems = computed(() => {
  const attachedIds = new Set(curriculumModules.value.map(item => item.moduleId))
  return modules.value
    .filter(module => module.status === 'ACTIVE' && !attachedIds.has(module.id))
    .map(module => ({ label: `${module.code} · ${module.name} · ${Number(module.creditValue).toFixed(2)} credits`, value: module.id }))
})
const newVersionGuidance = computed(() => {
  if (!programmes.value.length) return ['Create a programme before starting a curriculum version.']
  if (!selectedProgrammeId.value) return ['Select the programme whose curriculum you want to version.']
  return []
})
const addModuleGuidance = computed(() => {
  if (availableModuleItems.value.length) return []
  if (!modules.value.some(module => module.status === 'ACTIVE')) {
    return ['Create and activate at least one Module in the Module catalogue.']
  }
  return ['Every active Module is already attached to this curriculum version. Create or activate another Module, or select a different version.']
})
const approveVersionGuidance = computed(() => curriculumModules.value.length
  ? []
  : ['Add at least one Module to this draft curriculum before approving it.'])
const totalCredits = computed(() => curriculumModules.value.reduce((total, item) => total + Number(item.creditValue), 0))
const versionStatusItems = computed(() => programmeVersions.value.map(version => ({
  label: `${version.versionCode} · ${version.status} · ${version.curriculumModuleCount} Modules`,
  value: version.id
})))

onMounted(async () => {
  try {
    await academicSetup.ensureOverview()
    if (!selectedProgrammeId.value && programmes.value.length) selectedProgrammeId.value = programmes.value[0]!.id
  } catch {
    // Render shared error.
  }
})

watch(selectedProgrammeId, async (programmeId) => {
  selectedVersionId.value = ''
  programmeVersions.value = []
  curriculumModules.value = []
  if (!programmeId) return
  await loadProgrammeVersions(programmeId)
}, { immediate: true })

watch(selectedVersionId, async (versionId) => {
  curriculumModules.value = []
  if (!versionId) return
  await loadCurriculum(versionId)
})

watch(versionModalOpen, open => open && Object.assign(versionForm, { versionCode: '', effectiveFrom: '', effectiveTo: '' }))
watch(curriculumModalOpen, (open) => {
  if (open && curriculumDrawerMode.value === 'add') Object.assign(curriculumForm, {
    moduleId: availableModuleItems.value[0]?.value ?? '', yearOfStudy: 1, semesterNumber: 1,
    moduleType: 'COMPULSORY', creditValue: 12, minimumMarkRequired: 50,
    sortOrder: curriculumModules.value.length + 1, changeReason: ''
  })
})
watch(() => curriculumForm.moduleId, (moduleId) => {
  const module = modules.value.find(candidate => candidate.id === moduleId)
  if (module) curriculumForm.creditValue = Number(module.creditValue)
})

async function loadProgrammeVersions(programmeId: string) {
  loadingDetail.value = true
  try {
    programmeVersions.value = await api.request<ProgrammeVersionSummary[]>(`/api/academic/programmes/${programmeId}/versions`)
    selectedVersionId.value = programmeVersions.value[0]?.id ?? ''
  } catch (error) {
    await showError('Programme versions could not be loaded', api.errorMessage(error))
  } finally {
    loadingDetail.value = false
  }
}

async function loadCurriculum(versionId: string) {
  loadingDetail.value = true
  try {
    curriculumModules.value = await api.request<CurriculumModuleSummary[]>(`/api/academic/programme-versions/${versionId}/curriculum`)
  } catch (error) {
    await showError('Curriculum could not be loaded', api.errorMessage(error))
  } finally {
    loadingDetail.value = false
  }
}

async function createVersion() {
  if (!selectedProgrammeId.value) return
  saving.value = true
  try {
    const created = await api.request<ProgrammeVersionSummary>(`/api/academic/programmes/${selectedProgrammeId.value}/versions`, {
      method: 'POST', body: { ...versionForm, effectiveTo: versionForm.effectiveTo || null }
    })
    await loadProgrammeVersions(selectedProgrammeId.value)
    selectedVersionId.value = created.id
    versionModalOpen.value = false
    toast.add({ title: 'Draft curriculum version created', color: 'success', icon: 'i-lucide-git-branch-plus' })
  } catch (error) {
    await showError('Programme version could not be created', api.errorMessage(error))
  } finally {
    saving.value = false
  }
}

async function addCurriculumModule() {
  if (!selectedVersionId.value) return
  saving.value = true
  try {
    await api.request(`/api/academic/programme-versions/${selectedVersionId.value}/curriculum`, {
      method: 'POST',
      body: {
        moduleId: curriculumForm.moduleId,
        periodNumber: toProgrammePeriodNumber(curriculumForm.yearOfStudy, curriculumForm.semesterNumber),
        moduleType: curriculumForm.moduleType,
        creditValue: curriculumForm.creditValue,
        minimumMarkRequired: curriculumForm.minimumMarkRequired,
        sortOrder: curriculumForm.sortOrder,
        changeReason: curriculumForm.changeReason
      }
    })
    await refreshSelectedVersion()
    curriculumModalOpen.value = false
    toast.add({ title: 'Module added to curriculum', color: 'success', icon: 'i-lucide-list-plus' })
  } catch (error) {
    await showError('Module could not be added', api.errorMessage(error))
  } finally {
    saving.value = false
  }
}

function openAddCurriculumModule() {
  curriculumDrawerMode.value = 'add'
  selectedCurriculumModule.value = null
  curriculumModalOpen.value = true
}

function openCurriculumModuleAmendment(curriculumModule: CurriculumModuleSummary) {
  const studyStage = fromProgrammePeriodNumber(curriculumModule.periodNumber)
  curriculumDrawerMode.value = 'edit'
  selectedCurriculumModule.value = curriculumModule
  Object.assign(curriculumForm, {
    moduleId: curriculumModule.moduleId,
    yearOfStudy: studyStage.yearOfStudy,
    semesterNumber: studyStage.semesterNumber,
    moduleType: curriculumModule.moduleType,
    creditValue: Number(curriculumModule.creditValue),
    minimumMarkRequired: curriculumModule.minimumMarkRequired == null ? 50 : Number(curriculumModule.minimumMarkRequired),
    sortOrder: curriculumModule.sortOrder,
    changeReason: ''
  })
  curriculumModalOpen.value = true
}

async function updateCurriculumModule() {
  const curriculumModule = selectedCurriculumModule.value
  if (!selectedVersionId.value || !curriculumModule) return
  saving.value = true
  try {
    await api.request(`/api/academic/programme-versions/${selectedVersionId.value}/curriculum/${curriculumModule.id}`, {
      method: 'PUT',
      body: {
        periodNumber: toProgrammePeriodNumber(curriculumForm.yearOfStudy, curriculumForm.semesterNumber),
        moduleType: curriculumForm.moduleType,
        creditValue: curriculumForm.creditValue,
        minimumMarkRequired: curriculumForm.minimumMarkRequired,
        sortOrder: curriculumForm.sortOrder,
        changeReason: curriculumForm.changeReason,
        expectedVersion: curriculumModule.version
      }
    })
    await refreshSelectedVersion()
    curriculumModalOpen.value = false
    toast.add({ title: 'Curriculum Module amended', description: curriculumModule.moduleCode, color: 'success', icon: 'i-lucide-file-pen-line' })
  } catch (error) {
    await showError('Curriculum Module could not be amended', api.errorMessage(error))
  } finally {
    saving.value = false
  }
}

async function saveCurriculumModule() {
  if (curriculumDrawerMode.value === 'add') await addCurriculumModule()
  else await updateCurriculumModule()
}

async function removeCurriculumModule(curriculumModule: CurriculumModuleSummary) {
  if (!selectedVersionId.value) return
  try {
    const usage = await api.request<CurriculumModuleUsageSummary>(
      `/api/academic/programme-versions/${selectedVersionId.value}/curriculum/${curriculumModule.id}/usage`
    )
    if (!usage.removable) {
      await Swal.fire({
        title: 'Module cannot be removed',
        text: `${curriculumModule.moduleCode} is referenced by ${usage.registrationCount} student registration(s) and ${usage.resultCount} result record(s). Amend its placement instead; existing academic evidence must remain traceable.`,
        icon: 'error',
        confirmButtonText: 'Close',
        confirmButtonColor: '#20743a'
      })
      return
    }
    const result = await Swal.fire({
      title: `Remove ${curriculumModule.moduleCode} from this curriculum?`,
      text: 'Student Records and Results both report no usage. The removal remains in the audit history.',
      input: 'textarea',
      inputLabel: 'Amendment reason',
      inputPlaceholder: 'Record the curriculum committee decision or correction evidence.',
      inputAttributes: { maxlength: '1000' },
      inputValidator: value => value.trim().length >= 10 ? undefined : 'Provide at least 10 characters of amendment evidence.',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Remove Module',
      cancelButtonText: 'Cancel',
      confirmButtonColor: '#b42318'
    })
    if (!result.isConfirmed) return
    await api.request(`/api/academic/programme-versions/${selectedVersionId.value}/curriculum/${curriculumModule.id}/removal`, {
      method: 'POST',
      body: { changeReason: result.value.trim(), expectedVersion: curriculumModule.version }
    })
    await refreshSelectedVersion()
    toast.add({ title: 'Curriculum Module removed', description: curriculumModule.moduleCode, color: 'success', icon: 'i-lucide-list-minus' })
  } catch (error) {
    await showError('Curriculum Module could not be removed', api.errorMessage(error))
  }
}

async function approveVersion(version: ProgrammeVersionSummary) {
  const confirmed = await confirmAction({
    title: `Approve curriculum ${version.versionCode}?`,
    text: 'Approval makes this version operational. Later amendments remain audited, and Module removal is blocked when registrations or results exist.',
    confirmButtonText: 'Approve curriculum',
    icon: 'warning'
  })
  if (!confirmed) return
  activeVersionActionId.value = version.id
  try {
    await api.request(`/api/academic/programme-versions/${version.id}/approve`, { method: 'POST', body: { expectedVersion: version.version } })
    await refreshSelectedVersion()
    toast.add({ title: 'Curriculum version approved', description: 'The version is operational and remains governed by amendment safeguards.', color: 'success', icon: 'i-lucide-shield-check' })
  } catch (error) {
    await showError('Curriculum version could not be approved', api.errorMessage(error))
  } finally {
    activeVersionActionId.value = null
  }
}

async function retireVersion(version: ProgrammeVersionSummary) {
  const result = await Swal.fire({
    title: `Retire curriculum ${version.versionCode}?`,
    text: 'Historical student and result records will continue to reference this version.',
    input: 'date',
    inputLabel: 'Last effective date',
    inputValue: new Date().toISOString().slice(0, 10),
    icon: 'warning',
    showCancelButton: true,
    confirmButtonText: 'Retire version',
    cancelButtonText: 'Cancel',
    confirmButtonColor: '#20743a',
    inputValidator: value => value ? undefined : 'A retirement date is required.'
  })
  if (!result.isConfirmed) return
  activeVersionActionId.value = version.id
  try {
    await api.request(`/api/academic/programme-versions/${version.id}/retire`, {
      method: 'POST', body: { expectedVersion: version.version, retirementDate: result.value }
    })
    await refreshSelectedVersion()
    toast.add({ title: 'Curriculum version retired', color: 'success', icon: 'i-lucide-archive' })
  } catch (error) {
    await showError('Curriculum version could not be retired', api.errorMessage(error))
  } finally {
    activeVersionActionId.value = null
  }
}

async function refreshSelectedVersion() {
  const versionId = selectedVersionId.value
  await loadProgrammeVersions(selectedProgrammeId.value)
  selectedVersionId.value = versionId
  await loadCurriculum(versionId)
}

function versionTone(status: string) {
  if (status === 'APPROVED') return 'success' as const
  if (status === 'RETIRED') return 'neutral' as const
  return 'info' as const
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Curriculum versions">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right><EmhareGuidedActionButton aria-label="New curriculum version" label="New version" icon="i-lucide-git-branch-plus" color="primary" class="[&_[data-slot=label]]:hidden sm:[&_[data-slot=label]]:inline" guidance-title="Curriculum version setup required" :guidance-instructions="newVersionGuidance" :guidance-action-label="!programmes.length ? 'Open Programmes' : undefined" @guidance-action="navigateTo('/operations/programmes')" @click="versionModalOpen = true" /></template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left><USelect v-model="selectedProgrammeId" :items="programmeItems" value-key="value" placeholder="Select programme" searchable class="w-full sm:w-[30rem]" /></template>
        <template #right><USelect v-model="selectedVersionId" :items="versionStatusItems" value-key="value" placeholder="Select version" class="w-72" :disabled="!programmeVersions.length" /></template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="space-y-5 p-4 sm:p-6">
        <UAlert v-if="academicSetup.loadError.value" color="error" variant="soft" icon="i-lucide-circle-alert" title="Curriculum setup unavailable" :description="academicSetup.loadError.value" />
        <UAlert v-if="!programmes.length && !academicSetup.loading.value" color="warning" variant="soft" icon="i-lucide-graduation-cap" title="No programme selected" description="Create a programme before building its curriculum." />

        <template v-if="selectedProgramme">
          <div class="grid gap-3 sm:grid-cols-4">
            <EmhareKpiCard label="Programme" :value="selectedProgramme.code" :hint="selectedProgramme.name" icon="i-lucide-graduation-cap" tone="primary" />
            <EmhareKpiCard label="Versions" :value="programmeVersions.length" icon="i-lucide-git-branch" tone="primary" />
            <EmhareKpiCard label="Curriculum Modules" :value="curriculumModules.length" icon="i-lucide-list-tree" tone="success" />
            <EmhareKpiCard label="Total credits" :value="totalCredits.toFixed(2)" icon="i-lucide-sigma" tone="warning" />
          </div>

          <UCard v-if="selectedVersion" :ui="{ body: 'p-4' }">
            <div class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
              <div>
                <div class="flex items-center gap-2"><h2 class="font-semibold text-highlighted">Version {{ selectedVersion.versionCode }}</h2><EmhareStatusPill :label="selectedVersion.status" :tone="versionTone(selectedVersion.status)" /></div>
                <p class="mt-1 text-sm text-muted">Effective {{ selectedVersion.effectiveFrom }}<span v-if="selectedVersion.effectiveTo"> to {{ selectedVersion.effectiveTo }}</span><span v-else> with no scheduled end</span></p>
                <p v-if="selectedVersion.approvedAt" class="mt-1 text-xs text-muted">Approved {{ new Date(selectedVersion.approvedAt).toLocaleString('en-ZW') }}</p>
              </div>
              <div class="flex flex-wrap gap-2">
                <EmhareGuidedActionButton v-if="selectedVersion.status !== 'RETIRED'" label="Add Module" icon="i-lucide-list-plus" color="neutral" variant="outline" guidance-title="Module setup required" :guidance-instructions="addModuleGuidance" guidance-action-label="Open Modules" @guidance-action="navigateTo('/operations/modules')" @click="openAddCurriculumModule" />
                <EmhareGuidedActionButton v-if="selectedVersion.status === 'DRAFT'" label="Approve curriculum" icon="i-lucide-shield-check" color="primary" guidance-title="Curriculum cannot be approved yet" :guidance-instructions="approveVersionGuidance" :loading="activeVersionActionId === selectedVersion.id" @click="approveVersion(selectedVersion)" />
                <UButton v-if="selectedVersion.status === 'APPROVED'" label="Retire version" icon="i-lucide-archive" color="warning" variant="outline" :loading="activeVersionActionId === selectedVersion.id" @click="retireVersion(selectedVersion)" />
              </div>
            </div>
          </UCard>

          <UAlert v-if="selectedVersion?.status === 'APPROVED'" color="success" variant="soft" icon="i-lucide-shield-check" title="Approved curriculum remains amendable" description="You may add Modules or amend placement details. Removal is permitted only when Student Records and Results both confirm that the Module has no usage." />
          <UAlert v-if="selectedVersion?.status === 'DRAFT' && addModuleGuidance.length" color="warning" variant="soft" icon="i-lucide-list-plus" title="No Module is currently available to add" :description="addModuleGuidance[0]" :actions="[{ label: 'Open Modules', to: '/operations/modules', color: 'warning', variant: 'outline' }]" />

          <UCard :ui="{ body: 'p-0' }">
            <template #header><div><h2 class="font-semibold text-highlighted">Curriculum Module placement</h2><p class="text-sm text-muted">Year, semester, requirement, credits, ordering, and minimum pass rules.</p></div></template>
            <EmharePaginatedTable :data="curriculumModules" :columns="curriculumColumns" :loading="loadingDetail" sticky>
              <template #moduleCode-cell="{ row }"><span class="font-mono font-semibold text-primary">{{ row.original.moduleCode }}</span></template>
              <template #periodNumber-cell="{ row }"><span class="whitespace-nowrap">{{ studyPeriodLabel(row.original.periodNumber) }}</span></template>
              <template #moduleType-cell="{ row }"><EmhareStatusPill :label="row.original.moduleType" :tone="row.original.moduleType === 'COMPULSORY' ? 'info' : 'neutral'" /></template>
              <template #creditValue-cell="{ row }">{{ Number(row.original.creditValue).toFixed(2) }}</template>
              <template #minimumMarkRequired-cell="{ row }">{{ row.original.minimumMarkRequired == null ? 'Institution rule' : `${Number(row.original.minimumMarkRequired).toFixed(2)}%` }}</template>
              <template #actions-cell="{ row }">
                <div v-if="selectedVersion?.status !== 'RETIRED'" class="flex justify-end gap-1">
                  <UButton label="Edit" icon="i-lucide-pencil" color="neutral" variant="ghost" @click="openCurriculumModuleAmendment(row.original)" />
                  <UButton label="Remove" icon="i-lucide-list-minus" color="error" variant="ghost" @click="removeCurriculumModule(row.original)" />
                </div>
                <span v-else class="text-xs text-muted">Historical</span>
              </template>
              <template #empty><div class="py-12 text-center"><UIcon name="i-lucide-list-x" class="mx-auto size-8 text-muted" /><p class="mt-3 font-medium text-highlighted">No Modules in this version</p><p class="mt-1 text-sm text-muted">{{ addModuleGuidance[0] ?? 'Add an active Module to this governed curriculum version.' }}</p></div></template>
            </EmharePaginatedTable>
          </UCard>
        </template>
      </div>
    </template>
  </UDashboardPanel>

  <EmhareRecordDrawer v-model:open="versionModalOpen" title="Create programme version" description="Start a new governed curriculum lifecycle in draft.">
    <template #body><form id="version-form" class="grid gap-4 sm:grid-cols-2" @submit.prevent="createVersion"><UFormField label="Version code" required class="sm:col-span-2"><UInput v-model="versionForm.versionCode" class="w-full" placeholder="2027.1" /></UFormField><UFormField label="Effective from" required><UInput v-model="versionForm.effectiveFrom" type="date" class="w-full" /></UFormField><UFormField label="Effective to"><UInput v-model="versionForm.effectiveTo" type="date" class="w-full" /></UFormField></form></template>
    <template #footer><UButton label="Cancel" color="neutral" variant="outline" @click="versionModalOpen = false" /><UButton type="submit" form="version-form" label="Create draft version" :loading="saving" /></template>
  </EmhareRecordDrawer>

  <EmhareRecordDrawer v-model:open="curriculumModalOpen" presentation="page" :title="curriculumDrawerTitle" :description="curriculumDrawerDescription">
    <template #body>
      <form id="curriculum-form" class="grid gap-4 sm:grid-cols-2" @submit.prevent="saveCurriculumModule">
        <UFormField v-if="curriculumDrawerMode === 'add'" label="Module" required class="sm:col-span-2"><USelect v-model="curriculumForm.moduleId" :items="availableModuleItems" value-key="value" searchable class="w-full" /></UFormField>
        <UAlert v-else color="info" variant="soft" icon="i-lucide-book-open-check" :title="`${selectedCurriculumModule?.moduleCode} · ${selectedCurriculumModule?.moduleName}`" description="The Module identity remains fixed; amend its placement and academic rules below." class="sm:col-span-2" />
        <UFormField label="Year of study" required><USelect v-model="curriculumForm.yearOfStudy" :items="yearOfStudyOptions" value-key="value" class="w-full" /></UFormField>
        <UFormField label="Semester" required><USelect v-model="curriculumForm.semesterNumber" :items="semesterItems" value-key="value" class="w-full" /></UFormField>
        <UFormField label="Requirement type" required><USelect v-model="curriculumForm.moduleType" :items="[{ label: 'Compulsory', value: 'COMPULSORY' }, { label: 'Elective', value: 'ELECTIVE' }, { label: 'Optional', value: 'OPTIONAL' }]" value-key="value" class="w-full" /></UFormField>
        <UFormField label="Credit value" required><UInput v-model.number="curriculumForm.creditValue" type="number" min="0.01" step="0.01" class="w-full" /></UFormField>
        <UFormField label="Minimum pass mark"><UInput v-model.number="curriculumForm.minimumMarkRequired" type="number" min="0" max="100" step="0.01" class="w-full" /></UFormField>
        <UFormField label="Sort order" required><UInput v-model.number="curriculumForm.sortOrder" type="number" min="1" class="w-full" /></UFormField>
        <UFormField label="Amendment reason" description="Stored with the audited curriculum revision." required class="sm:col-span-2"><UTextarea v-model="curriculumForm.changeReason" :rows="3" autoresize :maxrows="6" class="w-full" placeholder="Record the curriculum committee decision, correction evidence, or implementation reason." /></UFormField>
      </form>
    </template>
    <template #footer><UButton label="Cancel" color="neutral" variant="outline" @click="curriculumModalOpen = false" /><UButton type="submit" form="curriculum-form" :label="curriculumDrawerMode === 'add' ? 'Add Module' : 'Save amendment'" :icon="curriculumDrawerMode === 'add' ? 'i-lucide-list-plus' : 'i-lucide-save'" :loading="saving" :disabled="curriculumForm.changeReason.trim().length < 10" /></template>
  </EmhareRecordDrawer>
</template>
