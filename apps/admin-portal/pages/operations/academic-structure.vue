<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { AcademicUnitSummary, AcademicUnitTypeSummary } from '@emhare/portal-shell/types/academic'

definePageMeta({ layout: 'dashboard' })

const api = useEmhareApi()
const toast = useToast()
const { showError } = useEmhareConfirm()
const academicSetup = useAcademicSetup()
const unitTypeModalOpen = ref(false)
const academicUnitModalOpen = ref(false)
const saving = ref(false)
const activeDataset = ref('units')
const unitTypeForm = reactive({
  id: null as string | null,
  code: '',
  name: '',
  levelOrder: 1,
  leafAllowed: false,
  expectedVersion: 0
})
const academicUnitForm = reactive({
  id: null as string | null,
  academicUnitTypeId: '', parentId: '', code: '', name: '',
  legacyFacultyCode: '', legacyDepartmentCode: '', expectedVersion: 0
})

const unitTypeColumns: TableColumn<AcademicUnitTypeSummary>[] = [
  { accessorKey: 'levelOrder', header: 'Level' },
  { accessorKey: 'code', header: 'Code' },
  { accessorKey: 'name', header: 'Unit type' },
  { accessorKey: 'leafAllowed', header: 'Can own academic records' },
  { accessorKey: 'status', header: 'Status' },
  { id: 'actions', header: 'Actions' }
]

const unitColumns: TableColumn<AcademicUnitSummary & { depth: number, parentName: string }>[] = [
  { accessorKey: 'code', header: 'Code' },
  { accessorKey: 'name', header: 'Academic unit' },
  { accessorKey: 'academicUnitTypeCode', header: 'Type' },
  { accessorKey: 'parentName', header: 'Parent' },
  { accessorKey: 'status', header: 'Status' },
  { id: 'actions', header: 'Actions' }
]

const unitTypes = computed(() => academicSetup.overview.value?.academicUnitTypes ?? [])
const academicUnits = computed(() => academicSetup.overview.value?.academicUnits ?? [])
const nextLevelOrder = computed(() => Math.max(0, ...unitTypes.value.map(unitType => unitType.levelOrder)) + 1)
const unitTypeItems = computed(() => unitTypes.value
  .filter(unitType => unitType.status === 'ACTIVE')
  .map(unitType => ({ label: `${unitType.levelOrder}. ${unitType.name}`, value: unitType.id })))
const academicUnitGuidance = computed(() => unitTypeItems.value.length
  ? []
  : ['Create and activate at least one hierarchy level before creating an academic unit.'])
const selectedUnitType = computed(() => unitTypes.value.find(unitType => unitType.id === academicUnitForm.academicUnitTypeId))
const parentItems = computed(() => {
  const requiredParentLevel = (selectedUnitType.value?.levelOrder ?? 1) - 1
  return academicUnits.value
    .filter(unit => unit.status === 'ACTIVE')
    .filter(unit => unitTypes.value.find(type => type.id === unit.academicUnitTypeId)?.levelOrder === requiredParentLevel)
    .map(unit => ({ label: `${unit.code} · ${unit.name}`, value: unit.id }))
})
const hierarchyRows = computed(() => buildHierarchyRows(academicUnits.value))
const datasets = computed(() => [
  { label: 'Academic units', value: 'units', icon: 'i-lucide-building-2', badge: academicUnits.value.length },
  { label: 'Hierarchy levels', value: 'unit-types', icon: 'i-lucide-layers-3', badge: unitTypes.value.length }
])

onMounted(async () => {
  try {
    await academicSetup.ensureOverview()
  } catch {
    // The shared error state is rendered below.
  }
})

function openAcademicUnitCreation() {
  Object.assign(academicUnitForm, {
      id: null,
      academicUnitTypeId: unitTypes.value[0]?.id ?? '', parentId: '', code: '', name: '',
      legacyFacultyCode: '', legacyDepartmentCode: '', expectedVersion: 0
  })
  academicUnitModalOpen.value = true
}

watch(() => academicUnitForm.academicUnitTypeId, () => {
  academicUnitForm.parentId = ''
})

function createUnitType() {
  Object.assign(unitTypeForm, {
    id: null,
    code: '',
    name: '',
    levelOrder: nextLevelOrder.value,
    leafAllowed: false,
    expectedVersion: 0
  })
  unitTypeModalOpen.value = true
}

function editUnitType(unitType: AcademicUnitTypeSummary) {
  Object.assign(unitTypeForm, {
    id: unitType.id,
    code: unitType.code,
    name: unitType.name,
    levelOrder: unitType.levelOrder,
    leafAllowed: unitType.leafAllowed,
    expectedVersion: unitType.version
  })
  unitTypeModalOpen.value = true
}

async function saveUnitType() {
  saving.value = true
  try {
    const editing = Boolean(unitTypeForm.id)
    await api.request(
      editing ? `/api/academic/unit-types/${unitTypeForm.id}` : '/api/academic/unit-types',
      {
        method: editing ? 'PUT' : 'POST',
        body: editing
          ? {
              code: unitTypeForm.code,
              name: unitTypeForm.name,
              leafAllowed: unitTypeForm.leafAllowed,
              expectedVersion: unitTypeForm.expectedVersion
            }
          : {
              code: unitTypeForm.code,
              name: unitTypeForm.name,
              levelOrder: unitTypeForm.levelOrder,
              leafAllowed: unitTypeForm.leafAllowed
            }
      }
    )
    await academicSetup.loadOverview()
    unitTypeModalOpen.value = false
    toast.add({
      title: `Academic unit type ${editing ? 'updated' : 'created'}`,
      color: 'success',
      icon: 'i-lucide-network'
    })
  } catch (error) {
    await showError(
      `Unit type could not be ${unitTypeForm.id ? 'updated' : 'created'}`,
      api.errorMessage(error)
    )
  } finally {
    saving.value = false
  }
}

function editAcademicUnit(academicUnit: AcademicUnitSummary) {
  Object.assign(academicUnitForm, {
    id: academicUnit.id,
    academicUnitTypeId: academicUnit.academicUnitTypeId,
    parentId: academicUnit.parentId ?? '',
    code: academicUnit.code,
    name: academicUnit.name,
    legacyFacultyCode: academicUnit.legacyFacultyCode ?? '',
    legacyDepartmentCode: academicUnit.legacyDepartmentCode ?? '',
    expectedVersion: academicUnit.version
  })
  academicUnitModalOpen.value = true
}

async function saveAcademicUnit() {
  saving.value = true
  const editing = Boolean(academicUnitForm.id)
  try {
    await api.request(editing ? `/api/academic/units/${academicUnitForm.id}` : '/api/academic/units', {
      method: editing ? 'PUT' : 'POST',
      body: editing ? {
        name: academicUnitForm.name,
        legacyFacultyCode: academicUnitForm.legacyFacultyCode || null,
        legacyDepartmentCode: academicUnitForm.legacyDepartmentCode || null,
        expectedVersion: academicUnitForm.expectedVersion
      } : {
        academicUnitTypeId: academicUnitForm.academicUnitTypeId,
        parentId: academicUnitForm.parentId || null,
        code: academicUnitForm.code,
        name: academicUnitForm.name,
        legacyFacultyCode: academicUnitForm.legacyFacultyCode || null,
        legacyDepartmentCode: academicUnitForm.legacyDepartmentCode || null
      }
    })
    await academicSetup.loadOverview()
    academicUnitModalOpen.value = false
    toast.add({ title: `Academic unit ${editing ? 'updated' : 'created'}`, color: 'success', icon: 'i-lucide-building-2' })
  } catch (error) {
    await showError(`Academic unit could not be ${editing ? 'updated' : 'created'}`, api.errorMessage(error))
  } finally {
    saving.value = false
  }
}

function buildHierarchyRows(units: AcademicUnitSummary[]) {
  const byParent = new Map<string | null, AcademicUnitSummary[]>()
  for (const unit of units) {
    const siblings = byParent.get(unit.parentId) ?? []
    siblings.push(unit)
    byParent.set(unit.parentId, siblings)
  }
  for (const siblings of byParent.values()) {
    siblings.sort((left, right) => left.name.localeCompare(right.name))
  }
  const rows: Array<AcademicUnitSummary & { depth: number, parentName: string }> = []
  const visited = new Set<string>()
  const append = (parentId: string | null, depth: number) => {
    for (const unit of byParent.get(parentId) ?? []) {
      if (visited.has(unit.id)) continue
      visited.add(unit.id)
      rows.push({
        ...unit,
        depth,
        parentName: unit.parentId ? units.find(candidate => candidate.id === unit.parentId)?.name ?? 'Unknown' : 'Institution'
      })
      append(unit.id, depth + 1)
    }
  }
  append(null, 0)
  return rows
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Academic structure">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right>
          <UButton label="Refresh" icon="i-lucide-refresh-cw" color="neutral" variant="outline" :loading="academicSetup.loading.value" @click="academicSetup.loadOverview" />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="space-y-5 p-4 sm:p-6">
        <div class="grid gap-3 sm:grid-cols-3">
          <EmhareKpiCard label="Hierarchy levels" :value="unitTypes.length" icon="i-lucide-layers-3" tone="primary" />
          <EmhareKpiCard label="Academic units" :value="academicUnits.length" icon="i-lucide-building-2" tone="success" />
          <EmhareKpiCard label="Leaf owners" :value="unitTypes.filter(type => type.leafAllowed).length" icon="i-lucide-git-branch" tone="warning" />
        </div>

        <UAlert
          color="info" variant="soft" icon="i-lucide-shield-check"
          title="Governed hierarchy"
          description="Unit types define hierarchy depth. Only active leaf-eligible units without children can own programmes or Modules."
        />
        <UAlert v-if="academicSetup.loadError.value" color="error" variant="soft" icon="i-lucide-circle-alert" title="Academic structure unavailable" :description="academicSetup.loadError.value" />

        <UTabs v-model="activeDataset" :items="datasets" :content="false" color="primary" variant="pill" />

        <EmhareRegisterPanel
          v-if="activeDataset === 'units'"
          title="Academic unit tree"
          description="Parent-child ownership used by programmes, Modules, roles, workflows, and reporting."
          :record-count="academicUnits.length"
        >
          <template #actions>
            <EmhareGuidedActionButton label="Create academic unit" icon="i-lucide-plus" color="primary" guidance-title="Academic unit setup required" :guidance-instructions="academicUnitGuidance" guidance-action-label="Open Hierarchy levels" @guidance-action="activeDataset = 'unit-types'" @click="openAcademicUnitCreation" />
          </template>
          <div class="overflow-hidden rounded-md border border-muted">
            <EmharePaginatedTable :data="hierarchyRows" :columns="unitColumns" :loading="academicSetup.loading.value">
            <template #name-cell="{ row }">
              <div class="flex items-center gap-2" :style="{ paddingLeft: `${row.original.depth * 20}px` }">
                <UIcon :name="row.original.depth ? 'i-lucide-corner-down-right' : 'i-lucide-building-2'" class="size-4 text-primary" />
                <span class="font-medium text-highlighted">{{ row.original.name }}</span>
              </div>
            </template>
            <template #status-cell="{ row }"><EmhareStatusPill :label="row.original.status" :tone="row.original.status === 'ACTIVE' ? 'success' : 'neutral'" /></template>
            <template #actions-cell="{ row }"><div class="flex justify-end"><UButton label="Edit" icon="i-lucide-pencil" color="neutral" variant="ghost" @click="editAcademicUnit(row.original)" /></div></template>
            <template #empty><div class="py-10 text-center text-sm text-muted">Create the first hierarchy level and academic unit.</div></template>
            </EmharePaginatedTable>
          </div>
        </EmhareRegisterPanel>

        <EmhareRegisterPanel
          v-if="activeDataset === 'unit-types'"
          title="Hierarchy levels"
          description="Configured levels are sequential and institution-owned."
          :record-count="unitTypes.length"
        >
          <template #actions>
            <UButton label="Create unit type" icon="i-lucide-plus" color="primary" @click="createUnitType" />
          </template>
          <div class="overflow-hidden rounded-md border border-muted">
            <EmharePaginatedTable :data="unitTypes" :columns="unitTypeColumns" :loading="academicSetup.loading.value">
              <template #leafAllowed-cell="{ row }">
                <EmhareStatusPill :label="row.original.leafAllowed ? 'Eligible owner' : 'Container only'" :tone="row.original.leafAllowed ? 'success' : 'neutral'" />
              </template>
              <template #status-cell="{ row }"><EmhareStatusPill :label="row.original.status" :tone="row.original.status === 'ACTIVE' ? 'success' : 'neutral'" /></template>
              <template #actions-cell="{ row }">
                <div class="flex justify-end">
                  <UButton label="Edit" icon="i-lucide-pencil" color="neutral" variant="ghost" @click="editUnitType(row.original)" />
                </div>
              </template>
              <template #empty><div class="py-10 text-center text-sm text-muted">Create the first hierarchy level.</div></template>
            </EmharePaginatedTable>
          </div>
        </EmhareRegisterPanel>
      </div>
    </template>
  </UDashboardPanel>

  <EmhareRecordDrawer
    v-model:open="unitTypeModalOpen"
    :title="unitTypeForm.id ? 'Edit academic unit type' : 'Create academic unit type'"
    :description="unitTypeForm.id ? 'Update this controlled hierarchy level without changing its position.' : 'Add the next controlled level in the institution hierarchy.'"
  >
    <template #body>
      <form id="unit-type-form" class="grid gap-4 sm:grid-cols-2" @submit.prevent="saveUnitType">
        <UFormField label="Code" required><UInput v-model="unitTypeForm.code" class="w-full" placeholder="FACULTY" /></UFormField>
        <UFormField label="Level order" required description="Hierarchy positions are fixed after creation."><UInput v-model.number="unitTypeForm.levelOrder" type="number" min="1" class="w-full" :disabled="Boolean(unitTypeForm.id)" /></UFormField>
        <UFormField label="Name" required class="sm:col-span-2"><UInput v-model="unitTypeForm.name" class="w-full" placeholder="College" /></UFormField>
        <UCheckbox v-model="unitTypeForm.leafAllowed" label="Units at this level may own programmes and Modules" class="sm:col-span-2" />
      </form>
    </template>
    <template #footer>
      <UButton label="Cancel" color="neutral" variant="outline" @click="unitTypeModalOpen = false" />
      <UButton type="submit" form="unit-type-form" :label="unitTypeForm.id ? 'Save changes' : 'Create unit type'" icon="i-lucide-check" :loading="saving" />
    </template>
  </EmhareRecordDrawer>

  <EmhareRecordDrawer v-model:open="academicUnitModalOpen" presentation="page" :title="academicUnitForm.id ? 'Edit academic unit' : 'Create academic unit'" :description="academicUnitForm.id ? 'Correct descriptive details without changing the governed hierarchy identity.' : 'Place the unit at a valid point in the governed hierarchy.'">
    <template #body>
      <form id="academic-unit-form" class="grid gap-4 sm:grid-cols-2" @submit.prevent="saveAcademicUnit">
        <UFormField label="Unit type" required><USelect v-model="academicUnitForm.academicUnitTypeId" :items="unitTypeItems" value-key="value" class="w-full" :disabled="Boolean(academicUnitForm.id)" /></UFormField>
        <UFormField v-if="(selectedUnitType?.levelOrder ?? 1) > 1" label="Parent unit" required><USelect v-model="academicUnitForm.parentId" :items="parentItems" value-key="value" class="w-full" placeholder="Select parent" :disabled="Boolean(academicUnitForm.id)" /></UFormField>
        <UFormField label="Code" required><UInput v-model="academicUnitForm.code" class="w-full" placeholder="SCI" :disabled="Boolean(academicUnitForm.id)" /></UFormField>
        <UFormField label="Name" required><UInput v-model="academicUnitForm.name" class="w-full" placeholder="College of Science" /></UFormField>
        <UFormField label="Legacy faculty code"><UInput v-model="academicUnitForm.legacyFacultyCode" class="w-full" /></UFormField>
        <UFormField label="Legacy department code"><UInput v-model="academicUnitForm.legacyDepartmentCode" class="w-full" /></UFormField>
      </form>
    </template>
    <template #footer>
      <UButton label="Cancel" color="neutral" variant="outline" @click="academicUnitModalOpen = false" />
      <UButton type="submit" form="academic-unit-form" :label="academicUnitForm.id ? 'Save changes' : 'Create academic unit'" icon="i-lucide-check" :loading="saving" />
    </template>
  </EmhareRecordDrawer>
</template>
