<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { AdmissionsApplicationTypeSummary } from '@emhare/portal-shell/types/admissions'
import type { FinanceFeeStructureRegister, FinanceFeeStructureSummary } from '@emhare/portal-shell/types/finance'

definePageMeta({ layout: 'dashboard' })

const api = useEmhareApi()
const toast = useToast()
const { confirmAction, showError } = useEmhareConfirm()

const applicationTypes = ref<AdmissionsApplicationTypeSummary[]>([])
const feeStructures = ref<FinanceFeeStructureSummary[]>([])
const loading = ref(false)
const loadError = ref('')
const saving = ref(false)
const drawerOpen = ref(false)
const searchQuery = ref('')
const statusFilter = ref<'ALL' | 'ACTIVE' | 'INACTIVE'>('ALL')

const applicationTypeForm = reactive({
  id: null as string | null,
  code: '',
  name: '',
  requiresEmploymentHistory: false,
  requiresReferees: false,
  financeFeeStructureId: '',
  active: false,
  originalActive: false,
  changeReason: '',
  expectedVersion: 0
})

const columns: TableColumn<AdmissionsApplicationTypeSummary>[] = [
  { accessorKey: 'code', header: 'Code' },
  { accessorKey: 'name', header: 'Application type' },
  { id: 'feeStructure', header: 'Application fee' },
  { id: 'requirements', header: 'Additional sections' },
  { accessorKey: 'active', header: 'Status' },
  { id: 'actions', header: 'Actions' }
]

const statusItems = [
  { label: 'All statuses', value: 'ALL' },
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Inactive', value: 'INACTIVE' }
]

const feeStructureItems = computed(() => feeStructures.value
  .filter(structure => structure.feeContext === 'APPLICATION' && structure.status !== 'RETIRED')
  .map(structure => ({ label: `${structure.code} · ${structure.name}`, value: structure.id })))
const selectedFeeStructure = computed(() => feeStructures.value
  .find(structure => structure.id === applicationTypeForm.financeFeeStructureId) ?? null)

const editingApplicationType = computed(() => applicationTypeForm.id !== null)
const filteredApplicationTypes = computed(() => {
  const normalizedQuery = searchQuery.value.trim().toLowerCase()
  return applicationTypes.value.filter((applicationType) => {
    const matchesQuery = !normalizedQuery
      || applicationType.code.toLowerCase().includes(normalizedQuery)
      || applicationType.name.toLowerCase().includes(normalizedQuery)
    const matchesStatus = statusFilter.value === 'ALL'
      || (statusFilter.value === 'ACTIVE' && applicationType.active)
      || (statusFilter.value === 'INACTIVE' && !applicationType.active)
    return matchesQuery && matchesStatus
  })
})
const formGuidance = computed(() => {
  const instructions: string[] = []
  if (!applicationTypeForm.code.trim()) instructions.push('Enter a stable application type code.')
  else if (!/^[A-Za-z0-9_-]+$/.test(applicationTypeForm.code.trim())) instructions.push('Use only letters, numbers, hyphens, or underscores in the code.')
  if (!applicationTypeForm.name.trim()) instructions.push('Enter the applicant-facing application type name.')
  if (editingApplicationType.value && applicationTypeForm.changeReason.trim().length < 10) {
    instructions.push('Provide at least 10 characters explaining this audited change.')
  }
  return instructions
})

onMounted(loadApplicationTypes)

async function loadApplicationTypes() {
  loading.value = true
  loadError.value = ''
  try {
    const [applicationTypeResult, feeStructureResult] = await Promise.all([
      api.request<AdmissionsApplicationTypeSummary[]>('/api/admissions/application-types'),
      api.request<FinanceFeeStructureRegister>('/api/finance/fee-structures')
    ])
    applicationTypes.value = applicationTypeResult
    feeStructures.value = feeStructureResult.structures
  } catch (error) {
    loadError.value = api.errorMessage(error, 'Application types could not be loaded.')
  } finally {
    loading.value = false
  }
}

function openCreateDrawer() {
  Object.assign(applicationTypeForm, {
    id: null,
    code: '',
    name: '',
    requiresEmploymentHistory: false,
    requiresReferees: false,
    financeFeeStructureId: '',
    active: false,
    originalActive: false,
    changeReason: '',
    expectedVersion: 0
  })
  drawerOpen.value = true
}

function openEditDrawer(applicationType: AdmissionsApplicationTypeSummary) {
  Object.assign(applicationTypeForm, {
    id: applicationType.id,
    code: applicationType.code,
    name: applicationType.name,
    requiresEmploymentHistory: applicationType.requiresEmploymentHistory,
    requiresReferees: applicationType.requiresReferees,
    financeFeeStructureId: applicationType.financeFeeStructureId ?? '',
    active: applicationType.active,
    originalActive: applicationType.active,
    changeReason: '',
    expectedVersion: applicationType.version
  })
  drawerOpen.value = true
}

async function saveApplicationType() {
  if (formGuidance.value.length) return
  const applicationTypeId = applicationTypeForm.id
  if (applicationTypeId && applicationTypeForm.active !== applicationTypeForm.originalActive) {
    const activating = applicationTypeForm.active
    const confirmed = await confirmAction({
      title: `${activating ? 'Activate' : 'Deactivate'} ${applicationTypeForm.name}?`,
      text: activating
        ? 'Applicants will be able to select this application route while an eligible intake is open.'
        : 'Applicants will no longer be able to start applications using this route. Existing records remain unchanged.',
      confirmButtonText: activating ? 'Activate type' : 'Deactivate type',
      icon: activating ? 'question' : 'warning',
      destructive: !activating
    })
    if (!confirmed) return
  }

  saving.value = true
  try {
    const requestBody = applicationTypeId
      ? {
          name: applicationTypeForm.name.trim(),
          requiresEmploymentHistory: applicationTypeForm.requiresEmploymentHistory,
          requiresReferees: applicationTypeForm.requiresReferees,
          financeFeeStructureId: selectedFeeStructure.value?.id ?? null,
          financeFeeStructureCode: selectedFeeStructure.value?.code ?? null,
          financeFeeStructureName: selectedFeeStructure.value?.name ?? null,
          active: applicationTypeForm.active,
          changeReason: applicationTypeForm.changeReason.trim(),
          expectedVersion: applicationTypeForm.expectedVersion
        }
      : {
          code: applicationTypeForm.code.trim().toUpperCase(),
          name: applicationTypeForm.name.trim(),
          requiresEmploymentHistory: applicationTypeForm.requiresEmploymentHistory,
          requiresReferees: applicationTypeForm.requiresReferees,
          financeFeeStructureId: selectedFeeStructure.value?.id ?? null,
          financeFeeStructureCode: selectedFeeStructure.value?.code ?? null,
          financeFeeStructureName: selectedFeeStructure.value?.name ?? null,
          active: applicationTypeForm.active
        }
    await api.request(
      applicationTypeId ? `/api/admissions/application-types/${applicationTypeId}` : '/api/admissions/application-types',
      { method: applicationTypeId ? 'PUT' : 'POST', body: requestBody }
    )
    await loadApplicationTypes()
    drawerOpen.value = false
    toast.add({
      title: `Application type ${applicationTypeId ? 'updated' : 'created'}`,
      description: applicationTypeForm.active ? 'The route is available to applicants.' : 'The route remains inactive until it is activated.',
      color: 'success',
      icon: 'i-lucide-badge-check'
    })
  } catch (error) {
    await showError(
      `Application type could not be ${applicationTypeId ? 'updated' : 'created'}`,
      api.errorMessage(error)
    )
  } finally {
    saving.value = false
  }
}

function requirementLabels(applicationType: AdmissionsApplicationTypeSummary) {
  const labels: string[] = []
  if (applicationType.requiresEmploymentHistory) labels.push('Employment history')
  if (applicationType.requiresReferees) labels.push('Referees')
  return labels
}

function feeStructureLabel(applicationType: AdmissionsApplicationTypeSummary) {
  return applicationType.financeFeeStructureCode && applicationType.financeFeeStructureName
    ? `${applicationType.financeFeeStructureCode} · ${applicationType.financeFeeStructureName}`
    : 'No fee structure associated'
}

function feeStructureDetail(applicationType: AdmissionsApplicationTypeSummary) {
  const structure = feeStructures.value.find(item => item.id === applicationType.financeFeeStructureId)
  if (!structure) {
    return applicationType.financeFeeStructureId
      ? 'Stored Finance snapshot'
      : 'Applications using this route are treated as fee-free until Finance is linked.'
  }
  const category = structure.applicantCategoryCode ? ` · ${structure.applicantCategoryCode}` : ''
  return `${structure.programmeLevelCode} · ${structure.status}${category}`
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Application types">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right>
          <UButton label="Refresh" icon="i-lucide-refresh-cw" color="neutral" variant="outline" :loading="loading" @click="loadApplicationTypes" />
          <UButton label="New application type" icon="i-lucide-plus" color="primary" @click="openCreateDrawer" />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="space-y-5 p-4 sm:p-6">
        <UAlert
          color="primary"
          variant="soft"
          icon="i-lucide-route"
          title="Govern applicant routes before opening admissions"
          description="Application types control the application sections applicants must complete. Only active types are exposed in the applicant portal."
        />
        <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <EmhareKpiCard label="Application types" :value="applicationTypes.length" icon="i-lucide-files" tone="primary" />
          <EmhareKpiCard label="Active" :value="applicationTypes.filter(type => type.active).length" icon="i-lucide-circle-check" tone="success" />
          <EmhareKpiCard label="Inactive" :value="applicationTypes.filter(type => !type.active).length" icon="i-lucide-circle-pause" tone="warning" />
          <EmhareKpiCard label="With additional sections" :value="applicationTypes.filter(type => type.requiresEmploymentHistory || type.requiresReferees).length" icon="i-lucide-list-checks" tone="neutral" />
        </div>
        <UAlert v-if="loadError" color="error" variant="soft" icon="i-lucide-circle-alert" title="Application types unavailable" :description="loadError" />

        <EmhareRegisterPanel
          title="Application type register"
          description="Stable route codes, applicant-facing names, conditional sections, and availability."
          :record-count="filteredApplicationTypes.length"
        >
          <template #actions>
            <UInput v-model="searchQuery" icon="i-lucide-search" placeholder="Search code or name" class="w-full sm:w-64" />
            <USelect v-model="statusFilter" :items="statusItems" value-key="value" class="w-40" aria-label="Filter by status" />
          </template>
          <UCard :ui="{ body: 'p-0' }">
            <EmharePaginatedTable :data="filteredApplicationTypes" :columns="columns" :loading="loading" sticky>
              <template #code-cell="{ row }">
                <span class="font-mono font-semibold text-primary">{{ row.original.code }}</span>
              </template>
              <template #feeStructure-cell="{ row }">
                <div class="max-w-xs">
                  <p :class="row.original.financeFeeStructureId ? 'font-medium text-highlighted' : 'text-muted'">
                    {{ feeStructureLabel(row.original) }}
                  </p>
                  <p class="text-xs text-muted">{{ feeStructureDetail(row.original) }}</p>
                </div>
              </template>
              <template #requirements-cell="{ row }">
                <div v-if="requirementLabels(row.original).length" class="flex flex-wrap gap-1">
                  <UBadge v-for="label in requirementLabels(row.original)" :key="label" :label="label" color="neutral" variant="subtle" />
                </div>
                <span v-else class="text-muted">Standard sections</span>
              </template>
              <template #active-cell="{ row }">
                <EmhareStatusPill :label="row.original.active ? 'Active' : 'Inactive'" :tone="row.original.active ? 'success' : 'warning'" />
              </template>
              <template #actions-cell="{ row }">
                <UButton label="Edit" icon="i-lucide-pencil" color="neutral" variant="ghost" @click="openEditDrawer(row.original)" />
              </template>
              <template #empty>
                <EmhareFeedbackState
                  state="empty"
                  title="No application types found"
                  :description="applicationTypes.length ? 'Adjust the search or status filter.' : 'Create the first application type before opening applications.'"
                />
              </template>
            </EmharePaginatedTable>
          </UCard>
        </EmhareRegisterPanel>
      </div>
    </template>
  </UDashboardPanel>

  <EmhareRecordDrawer
    v-model:open="drawerOpen"
    :title="editingApplicationType ? 'Edit application type' : 'Create application type'"
    :description="editingApplicationType ? 'Maintain the applicant route. Every correction is retained in audit history.' : 'Define a governed route applicants may use when admissions are open.'"
    :submit-label="editingApplicationType ? 'Save changes' : 'Create application type'"
    :busy="saving"
    :submit-disabled="formGuidance.length > 0"
    :submit-disabled-reason="formGuidance.join(' ')"
    @submit="saveApplicationType"
  >
    <template #body>
      <UAlert
        v-if="editingApplicationType"
        color="info"
        variant="soft"
        icon="i-lucide-lock-keyhole"
        title="Stable operational code"
        description="The code is locked after creation so integrations and historical application records keep the same route identifier."
      />
      <UAlert
        v-else
        color="warning"
        variant="soft"
        icon="i-lucide-circle-pause"
        title="Start inactive unless this route is ready"
        description="An active type becomes available to applicants while an eligible intake is open."
      />
      <form id="application-type-form" class="space-y-5" @submit.prevent="saveApplicationType">
        <div class="grid gap-4 sm:grid-cols-2">
          <UFormField label="Code" description="Letters, numbers, hyphens, or underscores." required>
            <UInput v-model="applicationTypeForm.code" :disabled="editingApplicationType" maxlength="50" placeholder="UNDERGRAD" class="w-full" />
          </UFormField>
          <UFormField label="Name" required>
            <UInput v-model="applicationTypeForm.name" maxlength="150" placeholder="Undergraduate" class="w-full" />
          </UFormField>
        </div>

        <UCard variant="subtle" :ui="{ body: 'space-y-3' }">
          <div>
            <h3 class="font-medium text-highlighted">Application fee structure</h3>
            <p class="mt-1 text-sm text-muted">Associate this applicant route with the Finance-governed application fee schedule.</p>
          </div>
          <UFormField label="Fee structure" description="Create and activate application fee schedules in Finance before applicant go-live.">
            <USelectMenu
              v-model="applicationTypeForm.financeFeeStructureId"
              :items="feeStructureItems"
              value-key="value"
              searchable
              clearable
              class="w-full"
              placeholder="No fee structure associated"
            />
          </UFormField>
          <UAlert
            v-if="!feeStructureItems.length"
            color="warning"
            variant="soft"
            icon="i-lucide-badge-dollar-sign"
            title="No application fee structures found"
            description="Open Finance → Fee catalogue and pricing, create an Application fee structure, then return here to link it to the application type."
          />
        </UCard>

        <UCard variant="subtle" :ui="{ body: 'space-y-4' }">
          <div>
            <h3 class="font-medium text-highlighted">Required application sections</h3>
            <p class="mt-1 text-sm text-muted">Turn on only the evidence sections that apply to this route.</p>
          </div>
          <div class="flex items-center justify-between gap-4">
            <div>
              <p class="text-sm font-medium text-highlighted">Employment history</p>
              <p class="text-xs text-muted">Applicants must provide their relevant work history.</p>
            </div>
            <USwitch v-model="applicationTypeForm.requiresEmploymentHistory" aria-label="Require employment history" />
          </div>
          <div class="flex items-center justify-between gap-4">
            <div>
              <p class="text-sm font-medium text-highlighted">Referees</p>
              <p class="text-xs text-muted">Applicants must provide referee details.</p>
            </div>
            <USwitch v-model="applicationTypeForm.requiresReferees" aria-label="Require referees" />
          </div>
        </UCard>

        <div class="flex items-center justify-between gap-4 rounded-lg border border-muted p-4">
          <div>
            <p class="text-sm font-medium text-highlighted">Active for applications</p>
            <p class="text-xs text-muted">Only active application types are offered in the applicant portal.</p>
          </div>
          <USwitch v-model="applicationTypeForm.active" aria-label="Active for applications" />
        </div>

        <UFormField
          v-if="editingApplicationType"
          label="Change reason"
          description="Required for the audited correction history. Enter at least 10 characters."
          required
        >
          <UTextarea v-model="applicationTypeForm.changeReason" :rows="4" minlength="10" maxlength="1000" placeholder="Explain why this application route is being changed." class="w-full" />
        </UFormField>
      </form>
    </template>
  </EmhareRecordDrawer>
</template>
