<script setup lang="ts">
import Swal from 'sweetalert2'
import type { TableColumn } from '@nuxt/ui'
import type { ProgrammeSummary } from '@emhare/portal-shell/types/academic'

definePageMeta({ layout: 'dashboard' })

const api = useEmhareApi()
const toast = useToast()
const { confirmAction, showError } = useEmhareConfirm()
const { durationLabel, durationPeriodsFromYears, durationYearsFromPeriods } = useProgrammeStudyPeriod()
const academicSetup = useAcademicSetup()
const search = ref('')
const activeDataset = ref<'programmes' | 'levels' | 'types'>('programmes')
const programmeModalOpen = ref(false)
const referenceModalOpen = ref(false)
const referenceKind = ref<'level' | 'type'>('level')
const saving = ref(false)
const activeProgrammeId = ref<string | null>(null)
const referenceForm = reactive({ code: '', name: '', sortOrder: 1 })
const programmeForm = reactive({
  id: null as string | null, status: 'DRAFT' as ProgrammeSummary['status'],
  owningAcademicUnitId: '', programmeTypeId: '', programmeLevelId: '', code: '', name: '',
  awardName: '', minimumDurationYears: 4, maximumDurationYears: 6, legacyProgrammeCode: '',
  changeReason: '', expectedVersion: 0
})

const columns: TableColumn<ProgrammeSummary>[] = [
  { accessorKey: 'code', header: 'Programme code' },
  { accessorKey: 'name', header: 'Programme' },
  { accessorKey: 'owningAcademicUnitName', header: 'Owner' },
  { accessorKey: 'programmeLevelName', header: 'Level' },
  { accessorKey: 'minimumDurationPeriods', header: 'Duration' },
  { accessorKey: 'status', header: 'Status' },
  { id: 'actions', header: 'Actions' }
]

const overview = computed(() => academicSetup.overview.value)
const programmes = computed(() => overview.value?.programmes ?? [])
const programmeLevels = computed(() => overview.value?.programmeLevels ?? [])
const programmeTypes = computed(() => overview.value?.programmeTypes ?? [])
const activeLeafUnitTypes = computed(() => (overview.value?.academicUnitTypes ?? [])
  .filter(type => type.status === 'ACTIVE' && type.leafAllowed))
const leafUnits = computed(() => {
  if (!overview.value) return []
  const parentIds = new Set(overview.value.academicUnits.map(unit => unit.parentId).filter(Boolean))
  const leafTypeIds = new Set(activeLeafUnitTypes.value.map(type => type.id))
  return overview.value.academicUnits
    .filter(unit => unit.status === 'ACTIVE' && leafTypeIds.has(unit.academicUnitTypeId) && !parentIds.has(unit.id))
    .map(unit => ({ label: `${unit.code} · ${unit.name}`, value: unit.id }))
})
const programmeLevelItems = computed(() => programmeLevels.value.filter(level => level.status === 'ACTIVE').map(level => ({ label: level.name, value: level.id })))
const programmeTypeItems = computed(() => programmeTypes.value.filter(type => type.status === 'ACTIVE').map(type => ({ label: type.name, value: type.id })))
const missingProgrammePrerequisites = computed(() => {
  if (!overview.value) return []

  const prerequisites: Array<{
    key: 'academic-unit' | 'programme-level' | 'programme-type'
    instruction: string
    actionLabel: string
    target: '/operations/academic-structure' | 'levels' | 'types'
  }> = []

  if (!leafUnits.value.length) {
    const instruction = activeLeafUnitTypes.value.length
      ? 'Create an active academic unit using a hierarchy level that can own academic records, and keep the unit without child units.'
      : 'Create and activate an academic hierarchy level that can own academic records, then create an active academic unit at that level without child units.'
    prerequisites.push({
      key: 'academic-unit',
      instruction,
      actionLabel: 'Open academic structure',
      target: '/operations/academic-structure'
    })
  }
  if (!programmeLevelItems.value.length) {
    prerequisites.push({
      key: 'programme-level',
      instruction: 'Create and activate at least one programme level from the Programme levels tab.',
      actionLabel: 'Open programme levels',
      target: 'levels'
    })
  }
  if (!programmeTypeItems.value.length) {
    prerequisites.push({
      key: 'programme-type',
      instruction: 'Create and activate at least one programme type from the Programme types tab.',
      actionLabel: 'Open programme types',
      target: 'types'
    })
  }

  return prerequisites
})
const programmeCreationGuidance = computed(() => missingProgrammePrerequisites.value
  .map(prerequisite => prerequisite.instruction)
  .join(' '))
const filteredProgrammes = computed(() => {
  const query = search.value.trim().toLowerCase()
  return programmes.value.filter(programme => !query || `${programme.code} ${programme.name} ${programme.owningAcademicUnitName}`.toLowerCase().includes(query))
})
const filteredProgrammeLevels = computed(() => {
  const query = search.value.trim().toLowerCase()
  return programmeLevels.value.filter(level => !query || `${level.code} ${level.name}`.toLowerCase().includes(query))
})
const filteredProgrammeTypes = computed(() => {
  const query = search.value.trim().toLowerCase()
  return programmeTypes.value.filter(type => !query || `${type.code} ${type.name}`.toLowerCase().includes(query))
})
const datasetTabs = computed(() => [
  { label: 'Programmes', value: 'programmes', icon: 'i-lucide-graduation-cap', badge: programmes.value.length },
  { label: 'Programme levels', value: 'levels', icon: 'i-lucide-layers', badge: programmeLevels.value.length },
  { label: 'Programme types', value: 'types', icon: 'i-lucide-tags', badge: programmeTypes.value.length }
])

onMounted(async () => {
  try { await academicSetup.ensureOverview() } catch { /* Render shared error. */ }
})

function initialiseProgrammeForm() {
  Object.assign(programmeForm, {
    id: null, status: 'DRAFT',
    owningAcademicUnitId: leafUnits.value[0]?.value ?? '', programmeTypeId: programmeTypeItems.value[0]?.value ?? '',
    programmeLevelId: programmeLevelItems.value[0]?.value ?? '', code: '', name: '', awardName: '',
    minimumDurationYears: 4, maximumDurationYears: 6, legacyProgrammeCode: '',
    changeReason: '', expectedVersion: 0
  })
  programmeModalOpen.value = true
}

async function startProgrammeCreation() {
  if (!overview.value) {
    await Swal.fire({
      title: 'Programme setup is still loading',
      text: 'Wait for the programme catalogue to finish loading, then select New programme again.',
      icon: 'info',
      confirmButtonColor: '#20743a'
    })
    return
  }

  const nextPrerequisite = missingProgrammePrerequisites.value[0]
  if (!nextPrerequisite) {
    initialiseProgrammeForm()
    return
  }

  const prerequisiteList = missingProgrammePrerequisites.value
    .map(prerequisite => `<li class="mb-2">${prerequisite.instruction}</li>`)
    .join('')
  const result = await Swal.fire({
    title: 'Complete programme setup',
    html: `<p class="mb-3 text-left">Complete the following before creating a programme:</p><ul class="list-disc pl-5 text-left">${prerequisiteList}</ul>`,
    icon: 'info',
    showCancelButton: true,
    confirmButtonText: nextPrerequisite.actionLabel,
    cancelButtonText: 'Stay here',
    confirmButtonColor: '#20743a'
  })
  if (!result.isConfirmed) return

  if (nextPrerequisite.target === 'levels' || nextPrerequisite.target === 'types') {
    activeDataset.value = nextPrerequisite.target
    return
  }
  await navigateTo(nextPrerequisite.target)
}

function editProgramme(programme: ProgrammeSummary) {
  Object.assign(programmeForm, {
    id: programme.id, status: programme.status,
    owningAcademicUnitId: programme.owningAcademicUnitId, programmeTypeId: programme.programmeTypeId,
    programmeLevelId: programme.programmeLevelId, code: programme.code, name: programme.name,
    awardName: programme.awardName, minimumDurationYears: durationYearsFromPeriods(programme.minimumDurationPeriods),
    maximumDurationYears: durationYearsFromPeriods(programme.maximumDurationPeriods), legacyProgrammeCode: programme.legacyProgrammeCode ?? '',
    changeReason: '', expectedVersion: programme.version
  })
  programmeModalOpen.value = true
}

function openReferenceModal(kind: 'level' | 'type') {
  referenceKind.value = kind
  Object.assign(referenceForm, { code: '', name: '', sortOrder: programmeLevels.value.length + 1 })
  referenceModalOpen.value = true
}

async function createReference() {
  saving.value = true
  try {
    const isLevel = referenceKind.value === 'level'
    const createdReferenceCode = referenceForm.code.trim()
    await api.request(isLevel ? '/api/academic/programme-levels' : '/api/academic/programme-types', {
      method: 'POST',
      body: isLevel ? referenceForm : { code: referenceForm.code, name: referenceForm.name }
    })
    await academicSetup.loadOverview()
    search.value = createdReferenceCode
    referenceModalOpen.value = false
    toast.add({ title: `Programme ${referenceKind.value} created`, color: 'success', icon: 'i-lucide-tags' })
  } catch (error) {
    await showError(`Programme ${referenceKind.value} could not be created`, api.errorMessage(error))
  } finally {
    saving.value = false
  }
}

async function saveProgramme() {
  saving.value = true
  const isEdit = Boolean(programmeForm.id)
  try {
    const savedProgrammeCode = programmeForm.code.trim()
    const body: Record<string, unknown> = {
      owningAcademicUnitId: programmeForm.owningAcademicUnitId,
      programmeTypeId: programmeForm.programmeTypeId,
      programmeLevelId: programmeForm.programmeLevelId,
      code: programmeForm.code,
      name: programmeForm.name,
      awardName: programmeForm.awardName,
      minimumDurationPeriods: durationPeriodsFromYears(programmeForm.minimumDurationYears),
      maximumDurationPeriods: durationPeriodsFromYears(programmeForm.maximumDurationYears),
      legacyProgrammeCode: programmeForm.legacyProgrammeCode || null
    }
    if (isEdit) {
      body.changeReason = programmeForm.changeReason
      body.expectedVersion = programmeForm.expectedVersion
    }
    await api.request(isEdit ? `/api/academic/programmes/${programmeForm.id}` : '/api/academic/programmes', {
      method: isEdit ? 'PUT' : 'POST',
      body
    })
    await academicSetup.loadOverview()
    search.value = savedProgrammeCode
    programmeModalOpen.value = false
    toast.add(isEdit
      ? { title: 'Programme updated', color: 'success', icon: 'i-lucide-graduation-cap' }
      : { title: 'Programme created in draft', description: 'Build and approve a curriculum version before activation.', color: 'success', icon: 'i-lucide-graduation-cap' })
  } catch (error) {
    await showError(`Programme could not be ${isEdit ? 'updated' : 'created'}`, api.errorMessage(error))
  } finally {
    saving.value = false
  }
}

async function activateProgramme(programme: ProgrammeSummary) {
  const confirmed = await confirmAction({
    title: `Activate ${programme.code}?`,
    text: 'The programme becomes available to downstream Admissions and Student Records. An approved curriculum is required.',
    confirmButtonText: 'Activate programme',
    icon: 'question'
  })
  if (!confirmed) return
  activeProgrammeId.value = programme.id
  try {
    await api.request(`/api/academic/programmes/${programme.id}/activate`, { method: 'POST', body: { expectedVersion: programme.version } })
    await academicSetup.loadOverview()
    toast.add({ title: 'Programme activated', color: 'success', icon: 'i-lucide-badge-check' })
  } catch (error) {
    await showError('Programme could not be activated', api.errorMessage(error))
  } finally {
    activeProgrammeId.value = null
  }
}

function statusTone(status: string) {
  if (status === 'ACTIVE') return 'success' as const
  if (status === 'DRAFT') return 'info' as const
  if (status === 'INACTIVE') return 'warning' as const
  return 'neutral' as const
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Programme catalogue">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right>
          <UButton label="Refresh" icon="i-lucide-refresh-cw" color="neutral" variant="outline" :loading="academicSetup.loading.value" @click="academicSetup.loadOverview" />
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar><template #left><UInput v-model="search" icon="i-lucide-search" placeholder="Search the current register" class="w-full sm:w-96" /></template></UDashboardToolbar>
    </template>

    <template #body>
      <div class="space-y-5 p-4 sm:p-6">
        <div class="grid gap-3 sm:grid-cols-4">
          <EmhareKpiCard label="Programmes" :value="programmes.length" icon="i-lucide-graduation-cap" tone="primary" />
          <EmhareKpiCard label="Active" :value="programmes.filter(programme => programme.status === 'ACTIVE').length" icon="i-lucide-badge-check" tone="success" />
          <EmhareKpiCard label="Programme levels" :value="programmeLevels.length" icon="i-lucide-layers" tone="primary" />
          <EmhareKpiCard label="Programme types" :value="programmeTypes.length" icon="i-lucide-tags" tone="warning" />
        </div>
        <UAlert color="info" variant="soft" icon="i-lucide-history" title="Version-controlled curricula" description="A programme is the stable catalogue identity. Curriculum-changing decisions belong to approved programme versions and never overwrite history." />
        <UAlert v-if="academicSetup.loadError.value" color="error" variant="soft" icon="i-lucide-circle-alert" title="Programme catalogue unavailable" :description="academicSetup.loadError.value" />
        <div class="flex flex-wrap items-center justify-between gap-3">
          <UTabs v-model="activeDataset" :items="datasetTabs" value-key="value" class="min-w-0 flex-1" />
          <UButton v-if="activeDataset === 'programmes'" label="New programme" icon="i-lucide-plus" @click="startProgrammeCreation" />
          <UButton v-else-if="activeDataset === 'levels'" label="New programme level" icon="i-lucide-layers" @click="openReferenceModal('level')" />
          <UButton v-else label="New programme type" icon="i-lucide-tags" @click="openReferenceModal('type')" />
        </div>
        <UAlert
          v-if="activeDataset === 'programmes' && missingProgrammePrerequisites.length"
          color="warning"
          variant="soft"
          icon="i-lucide-list-checks"
          title="Programme setup required"
          :description="programmeCreationGuidance"
        />
        <UCard v-if="activeDataset === 'programmes'" :ui="{ body: 'p-0' }">
          <EmharePaginatedTable :data="filteredProgrammes" :columns="columns" :loading="academicSetup.loading.value" sticky>
            <template #code-cell="{ row }"><span class="font-mono font-semibold text-primary">{{ row.original.code }}</span></template>
            <template #name-cell="{ row }"><div><p class="font-medium text-highlighted">{{ row.original.name }}</p><p class="text-xs text-muted">{{ row.original.awardName }}</p></div></template>
            <template #minimumDurationPeriods-cell="{ row }"><span class="whitespace-nowrap">{{ durationLabel(row.original.minimumDurationPeriods, row.original.maximumDurationPeriods) }}</span></template>
            <template #status-cell="{ row }"><EmhareStatusPill :label="row.original.status" :tone="statusTone(row.original.status)" /></template>
            <template #actions-cell="{ row }"><div class="flex justify-end gap-2"><UButton label="Edit" icon="i-lucide-pencil" color="neutral" variant="ghost" @click="editProgramme(row.original)" /><UButton label="Curriculum" color="neutral" variant="ghost" icon="i-lucide-list-tree" :to="`/operations/curriculum?programmeId=${row.original.id}`" /><UButton v-if="row.original.status === 'DRAFT'" label="Activate" color="primary" variant="ghost" :loading="activeProgrammeId === row.original.id" @click="activateProgramme(row.original)" /></div></template>
            <template #empty><div class="py-12 text-center"><UIcon name="i-lucide-graduation-cap" class="mx-auto size-8 text-muted" /><p class="mt-3 font-medium text-highlighted">No programmes configured</p><p class="mx-auto mt-1 max-w-2xl text-sm text-muted">{{ programmeCreationGuidance || 'Select New programme to add the first programme.' }}</p></div></template>
          </EmharePaginatedTable>
        </UCard>
        <EmhareRegisterPanel v-else-if="activeDataset === 'levels'" title="Programme levels" description="Govern the ordered qualification levels used by programme records." :count="programmeLevels.length">
          <EmharePaginatedCollection v-slot="{ items: paginatedLevels }" :items="filteredProgrammeLevels">
          <div class="overflow-x-auto">
            <table class="w-full min-w-[720px] text-left text-sm">
              <thead class="bg-muted/40 text-xs uppercase text-muted"><tr><th class="px-4 py-3">Order</th><th class="px-4 py-3">Code</th><th class="px-4 py-3">Name</th><th class="px-4 py-3">Status</th></tr></thead>
              <tbody>
                <tr v-for="level in paginatedLevels" :key="level.id" class="border-t border-muted"><td class="px-4 py-3">{{ level.sortOrder }}</td><td class="px-4 py-3 font-mono text-xs text-primary">{{ level.code }}</td><td class="px-4 py-3 font-medium">{{ level.name }}</td><td class="px-4 py-3"><EmhareStatusPill :label="level.status" :tone="statusTone(level.status)" /></td></tr>
                <tr v-if="!filteredProgrammeLevels.length"><td colspan="4" class="px-4 py-8 text-center text-muted">No programme levels match this view.</td></tr>
              </tbody>
            </table>
          </div>
          </EmharePaginatedCollection>
        </EmhareRegisterPanel>
        <EmhareRegisterPanel v-else title="Programme types" description="Govern programme classifications used throughout academic setup and admissions." :count="programmeTypes.length">
          <EmharePaginatedCollection v-slot="{ items: paginatedProgrammeTypes }" :items="filteredProgrammeTypes">
          <div class="overflow-x-auto">
            <table class="w-full min-w-[640px] text-left text-sm">
              <thead class="bg-muted/40 text-xs uppercase text-muted"><tr><th class="px-4 py-3">Code</th><th class="px-4 py-3">Name</th><th class="px-4 py-3">Status</th></tr></thead>
              <tbody>
                <tr v-for="programmeType in paginatedProgrammeTypes" :key="programmeType.id" class="border-t border-muted"><td class="px-4 py-3 font-mono text-xs text-primary">{{ programmeType.code }}</td><td class="px-4 py-3 font-medium">{{ programmeType.name }}</td><td class="px-4 py-3"><EmhareStatusPill :label="programmeType.status" :tone="statusTone(programmeType.status)" /></td></tr>
                <tr v-if="!filteredProgrammeTypes.length"><td colspan="3" class="px-4 py-8 text-center text-muted">No programme types match this view.</td></tr>
              </tbody>
            </table>
          </div>
          </EmharePaginatedCollection>
        </EmhareRegisterPanel>
      </div>
    </template>
  </UDashboardPanel>

  <EmhareRecordDrawer v-model:open="referenceModalOpen" :title="`Create programme ${referenceKind}`" description="Add controlled reference data used by programme records.">
    <template #body><form id="programme-reference-form" class="grid gap-4 sm:grid-cols-2" @submit.prevent="createReference"><UFormField label="Code" required><UInput v-model="referenceForm.code" class="w-full" /></UFormField><UFormField v-if="referenceKind === 'level'" label="Sort order" required><UInput v-model.number="referenceForm.sortOrder" type="number" min="1" class="w-full" /></UFormField><UFormField label="Name" required class="sm:col-span-2"><UInput v-model="referenceForm.name" class="w-full" /></UFormField></form></template>
    <template #footer><UButton label="Cancel" color="neutral" variant="outline" @click="referenceModalOpen = false" /><UButton type="submit" form="programme-reference-form" :label="`Create ${referenceKind}`" :loading="saving" /></template>
  </EmhareRecordDrawer>

  <EmhareRecordDrawer v-model:open="programmeModalOpen" :title="programmeForm.id ? 'Edit programme' : 'Create programme'" description="Create the stable programme identity; curriculum content is versioned separately.">
    <template #body>
      <form id="programme-form" class="grid gap-4 sm:grid-cols-2" @submit.prevent="saveProgramme">
        <UAlert v-if="programmeForm.id && programmeForm.status !== 'DRAFT'" color="info" variant="soft" icon="i-lucide-lock-keyhole" title="Operational identity locked" description="Owning academic unit and programme code cannot change after the programme leaves draft." class="sm:col-span-2" />
        <UFormField label="Owning academic unit" required class="sm:col-span-2"><USelect v-model="programmeForm.owningAcademicUnitId" :items="leafUnits" value-key="value" class="w-full" :disabled="Boolean(programmeForm.id && programmeForm.status !== 'DRAFT')" /></UFormField>
        <UFormField label="Programme type" required><USelect v-model="programmeForm.programmeTypeId" :items="programmeTypeItems" value-key="value" class="w-full" /></UFormField>
        <UFormField label="Programme level" required><USelect v-model="programmeForm.programmeLevelId" :items="programmeLevelItems" value-key="value" class="w-full" /></UFormField>
        <UFormField label="Programme code" description="Up to 5 characters." required><UInput v-model="programmeForm.code" class="w-full" placeholder="BSCIT" maxlength="5" :disabled="Boolean(programmeForm.id && programmeForm.status !== 'DRAFT')" /></UFormField>
        <UFormField label="Legacy programme code"><UInput v-model="programmeForm.legacyProgrammeCode" class="w-full" /></UFormField>
        <UFormField label="Programme name" required class="sm:col-span-2"><UInput v-model="programmeForm.name" class="w-full" /></UFormField>
        <UFormField label="Award name" required class="sm:col-span-2"><UInput v-model="programmeForm.awardName" class="w-full" /></UFormField>
        <UFormField label="Minimum duration (years)" required><UInput v-model.number="programmeForm.minimumDurationYears" type="number" min="0.5" step="0.5" class="w-full" /></UFormField>
        <UFormField label="Maximum duration (years)" required><UInput v-model.number="programmeForm.maximumDurationYears" type="number" min="0.5" step="0.5" class="w-full" /></UFormField>
        <UFormField v-if="programmeForm.id" label="Change reason" description="Recorded in the audited programme history." required class="sm:col-span-2"><UTextarea v-model="programmeForm.changeReason" class="w-full" :rows="3" minlength="10" maxlength="1000" placeholder="Explain why this programme record is being corrected." /></UFormField>
      </form>
    </template>
    <template #footer><UButton label="Cancel" color="neutral" variant="outline" @click="programmeModalOpen = false" /><UButton type="submit" form="programme-form" :label="programmeForm.id ? 'Save changes' : 'Create draft programme'" icon="i-lucide-check" :loading="saving" /></template>
  </EmhareRecordDrawer>
</template>
