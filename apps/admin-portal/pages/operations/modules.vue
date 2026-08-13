<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { AcademicModuleSummary } from '@emhare/portal-shell/types/academic'

definePageMeta({ layout: 'dashboard' })

const api = useEmhareApi()
const toast = useToast()
const { confirmAction, showError } = useEmhareConfirm()
const academicSetup = useAcademicSetup()
const search = ref('')
const statusFilter = ref('ALL')
const moduleModalOpen = ref(false)
const saving = ref(false)
const activeModuleId = ref<string | null>(null)
const moduleForm = reactive({
  id: null as string | null,
  status: 'DRAFT' as AcademicModuleSummary['status'],
  owningAcademicUnitId: '', code: '', name: '', description: '',
  creditValue: 12, academicLevel: 1, legacyCourseCode: '', expectedVersion: 0
})

const columns: TableColumn<AcademicModuleSummary>[] = [
  { accessorKey: 'code', header: 'Module code' },
  { accessorKey: 'name', header: 'Module' },
  { accessorKey: 'owningAcademicUnitName', header: 'Owner' },
  { accessorKey: 'creditValue', header: 'Credits' },
  { accessorKey: 'academicLevel', header: 'Level' },
  { accessorKey: 'status', header: 'Status' },
  { id: 'actions', header: 'Actions' }
]

const modules = computed(() => academicSetup.overview.value?.modules ?? [])
const leafUnits = computed(() => {
  const overview = academicSetup.overview.value
  if (!overview) return []
  const parentIds = new Set(overview.academicUnits.map(unit => unit.parentId).filter(Boolean))
  const leafTypeIds = new Set(overview.academicUnitTypes.filter(type => type.leafAllowed).map(type => type.id))
  return overview.academicUnits
    .filter(unit => unit.status === 'ACTIVE' && leafTypeIds.has(unit.academicUnitTypeId) && !parentIds.has(unit.id))
    .map(unit => ({ label: `${unit.code} · ${unit.name}`, value: unit.id }))
})
const moduleOwnerGuidance = computed(() => leafUnits.value.length
  ? []
  : ['Create an active academic unit at a hierarchy level that can own academic records, and keep that unit without child units.'])
const filteredModules = computed(() => modules.value.filter((module) => {
  const query = search.value.trim().toLowerCase()
  return (!query || `${module.code} ${module.name} ${module.owningAcademicUnitName}`.toLowerCase().includes(query))
    && (statusFilter.value === 'ALL' || module.status === statusFilter.value)
}))
const statusItems = [
  { label: 'All statuses', value: 'ALL' },
  { label: 'Draft', value: 'DRAFT' },
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Inactive', value: 'INACTIVE' },
  { label: 'Retired', value: 'RETIRED' }
]

onMounted(async () => {
  try { await academicSetup.ensureOverview() } catch { /* Render shared error. */ }
})

function openModuleCreation() {
  Object.assign(moduleForm, {
    id: null, status: 'DRAFT',
    owningAcademicUnitId: leafUnits.value[0]?.value ?? '', code: '', name: '', description: '',
    creditValue: 12, academicLevel: 1, legacyCourseCode: '', expectedVersion: 0
  })
  moduleModalOpen.value = true
}

function editModule(module: AcademicModuleSummary) {
  Object.assign(moduleForm, {
    id: module.id, status: module.status,
    owningAcademicUnitId: module.owningAcademicUnitId, code: module.code,
    name: module.name, description: module.description,
    creditValue: Number(module.creditValue), academicLevel: module.academicLevel,
    legacyCourseCode: module.legacyCourseCode ?? '', expectedVersion: module.version
  })
  moduleModalOpen.value = true
}

async function saveModule() {
  saving.value = true
  const editing = Boolean(moduleForm.id)
  try {
    const createdModuleCode = moduleForm.code.trim()
    await api.request(editing ? `/api/academic/modules/${moduleForm.id}` : '/api/academic/modules', {
      method: editing ? 'PUT' : 'POST',
      body: {
        owningAcademicUnitId: moduleForm.owningAcademicUnitId,
        code: moduleForm.code,
        name: moduleForm.name,
        description: moduleForm.description,
        creditValue: moduleForm.creditValue,
        academicLevel: moduleForm.academicLevel,
        legacyCourseCode: moduleForm.legacyCourseCode || null,
        ...(editing ? { expectedVersion: moduleForm.expectedVersion } : {})
      }
    })
    await academicSetup.loadOverview()
    search.value = createdModuleCode
    statusFilter.value = 'ALL'
    moduleModalOpen.value = false
    toast.add(editing
      ? { title: 'Module updated', color: 'success', icon: 'i-lucide-book-open' }
      : { title: 'Module created in draft', description: 'Review the definition before activation.', color: 'success', icon: 'i-lucide-book-open' })
  } catch (error) {
    await showError(`Module could not be ${editing ? 'updated' : 'created'}`, api.errorMessage(error))
  } finally {
    saving.value = false
  }
}

async function activateModule(module: AcademicModuleSummary) {
  const confirmed = await confirmAction({
    title: `Activate ${module.code}?`,
    text: 'The Module becomes available for draft curriculum versions.',
    confirmButtonText: 'Activate Module',
    icon: 'question'
  })
  if (!confirmed) return
  activeModuleId.value = module.id
  try {
    await api.request(`/api/academic/modules/${module.id}/activate`, { method: 'POST', body: { expectedVersion: module.version } })
    await academicSetup.loadOverview()
    toast.add({ title: 'Module activated', color: 'success', icon: 'i-lucide-badge-check' })
  } catch (error) {
    await showError('Module could not be activated', api.errorMessage(error))
  } finally {
    activeModuleId.value = null
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
      <UDashboardNavbar title="Module catalogue">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right><EmhareGuidedActionButton aria-label="New Module" label="New Module" icon="i-lucide-plus" color="primary" class="[&_[data-slot=label]]:hidden sm:[&_[data-slot=label]]:inline" guidance-title="Module owner setup required" :guidance-instructions="moduleOwnerGuidance" guidance-action-label="Open Academic structure" @guidance-action="navigateTo('/operations/academic-structure')" @click="openModuleCreation" /></template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left><UInput v-model="search" icon="i-lucide-search" placeholder="Search code, Module, or owner" class="w-full sm:w-96" /></template>
        <template #right><USelect v-model="statusFilter" :items="statusItems" value-key="value" class="w-44" /></template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="space-y-5 p-4 sm:p-6">
        <div class="grid gap-3 sm:grid-cols-3">
          <EmhareKpiCard label="Modules" :value="modules.length" icon="i-lucide-book-open" tone="primary" />
          <EmhareKpiCard label="Active" :value="modules.filter(module => module.status === 'ACTIVE').length" icon="i-lucide-badge-check" tone="success" />
          <EmhareKpiCard label="Draft" :value="modules.filter(module => module.status === 'DRAFT').length" icon="i-lucide-pencil-ruler" tone="warning" />
        </div>
        <UAlert color="info" variant="soft" icon="i-lucide-info" title="Reusable Module catalogue" description="Active Modules can be reused across programme versions. Curriculum placement and credit rules remain version-specific." />
        <UAlert v-if="moduleOwnerGuidance.length && !academicSetup.loading.value" color="warning" variant="soft" icon="i-lucide-network" title="No eligible academic owner" :description="moduleOwnerGuidance[0]" :actions="[{ label: 'Open Academic structure', to: '/operations/academic-structure', color: 'warning', variant: 'outline' }]" />
        <UAlert v-if="academicSetup.loadError.value" color="error" variant="soft" icon="i-lucide-circle-alert" title="Module catalogue unavailable" :description="academicSetup.loadError.value" />
        <UCard :ui="{ body: 'p-0' }">
          <EmharePaginatedTable :data="filteredModules" :columns="columns" :loading="academicSetup.loading.value" sticky>
            <template #code-cell="{ row }"><span class="font-mono font-semibold text-primary">{{ row.original.code }}</span></template>
            <template #name-cell="{ row }"><div><p class="font-medium text-highlighted">{{ row.original.name }}</p><p class="max-w-md truncate text-xs text-muted">{{ row.original.description }}</p></div></template>
            <template #creditValue-cell="{ row }">{{ Number(row.original.creditValue).toFixed(2) }}</template>
            <template #status-cell="{ row }"><EmhareStatusPill :label="row.original.status" :tone="statusTone(row.original.status)" /></template>
            <template #actions-cell="{ row }"><div class="flex justify-end gap-2"><UButton label="Edit" icon="i-lucide-pencil" color="neutral" variant="ghost" @click="editModule(row.original)" /><UButton v-if="row.original.status === 'DRAFT'" label="Activate" color="primary" variant="ghost" :loading="activeModuleId === row.original.id" @click="activateModule(row.original)" /></div></template>
            <template #empty><div class="py-12 text-center"><UIcon name="i-lucide-book-x" class="mx-auto size-8 text-muted" /><p class="mt-3 font-medium text-highlighted">No Modules match this view</p></div></template>
          </EmharePaginatedTable>
        </UCard>
      </div>
    </template>
  </UDashboardPanel>

  <EmhareRecordDrawer v-model:open="moduleModalOpen" presentation="page" :title="moduleForm.id ? 'Edit Module' : 'Create Module'" :description="moduleForm.id ? 'Correct the reusable Module definition. Active ownership and codes remain locked.' : 'Create a reusable Module definition in draft state.'">
    <template #body>
      <form id="module-form" class="grid gap-4 sm:grid-cols-2" @submit.prevent="saveModule">
        <UFormField label="Owning academic unit" required class="sm:col-span-2"><USelect v-model="moduleForm.owningAcademicUnitId" :items="leafUnits" value-key="value" class="w-full" :disabled="Boolean(moduleForm.id && moduleForm.status !== 'DRAFT')" /></UFormField>
        <UFormField label="Module code" required><UInput v-model="moduleForm.code" class="w-full" placeholder="CSC101" :disabled="Boolean(moduleForm.id && moduleForm.status !== 'DRAFT')" /></UFormField>
        <UFormField label="Academic level" required><UInput v-model.number="moduleForm.academicLevel" type="number" min="1" max="100" class="w-full" /></UFormField>
        <UFormField label="Module name" required class="sm:col-span-2"><UInput v-model="moduleForm.name" class="w-full" placeholder="Programming Fundamentals" /></UFormField>
        <UFormField label="Description" required class="sm:col-span-2"><UTextarea v-model="moduleForm.description" class="w-full" :rows="3" /></UFormField>
        <UFormField label="Credit value" required><UInput v-model.number="moduleForm.creditValue" type="number" min="0.01" step="0.01" class="w-full" /></UFormField>
        <UFormField label="Legacy course code"><UInput v-model="moduleForm.legacyCourseCode" class="w-full" /></UFormField>
      </form>
    </template>
    <template #footer><UButton label="Cancel" color="neutral" variant="outline" @click="moduleModalOpen = false" /><UButton type="submit" form="module-form" :label="moduleForm.id ? 'Save changes' : 'Create draft Module'" icon="i-lucide-check" :loading="saving" /></template>
  </EmhareRecordDrawer>
</template>
