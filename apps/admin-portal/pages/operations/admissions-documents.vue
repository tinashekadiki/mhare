<script setup lang="ts">
import type { AcademicUnitApplicationDocumentEntry, AdmissionsApplicationSummary } from '@emhare/portal-shell/types/admissions'

definePageMeta({ layout: 'dashboard' })

const api = useEmhareApi()
const academicSetup = useAcademicSetup()
const academicPeriodContext = useAcademicPeriodContext()

const selectedAcademicUnitId = ref('')
const register = ref<AcademicUnitApplicationDocumentEntry[]>([])
const loading = ref(false)
const loadError = ref('')

const overview = computed(() => academicSetup.overview.value)
const academicUnitItems = computed(() => (overview.value?.academicUnits ?? [])
  .filter(unit => unit.status === 'ACTIVE')
  .map(unit => ({ label: `${unit.code} · ${unit.name}`, value: unit.id })))

const totalMissing = computed(() => register.value.reduce((total, entry) => total + entry.documents.missingRequirementCodes.length, 0))
const totalPending = computed(() => register.value.reduce((total, entry) => total + entry.documents.pendingRequirementCodes.length, 0))
const totalRejected = computed(() => register.value.reduce((total, entry) => total + entry.documents.rejectedRequirementCodes.length, 0))

onMounted(async () => {
  await academicSetup.ensureOverview().catch(() => {})
  if (academicUnitItems.value.length) {
    selectedAcademicUnitId.value = academicUnitItems.value[0]!.value
  }
})

watch(selectedAcademicUnitId, async (unitId) => {
  if (!unitId) {
    register.value = []
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const [registerResponse, applications] = await Promise.all([
      api.request<AcademicUnitApplicationDocumentEntry[]>(`/api/admissions/academic-units/${unitId}/documents`),
      api.request<AdmissionsApplicationSummary[]>('/api/admissions/applications'),
      academicPeriodContext.ensureIntakes()
    ])
    const visibleApplicationIds = new Set(applications
      .filter(application => academicPeriodContext.matchesIntake(application.intakeId))
      .map(application => application.id))
    register.value = registerResponse.filter(entry => visibleApplicationIds.has(entry.applicationId))
  } catch (error) {
    loadError.value = api.errorMessage(error, 'Consolidated documents could not be loaded.')
  } finally {
    loading.value = false
  }
}, { immediate: true })

watch(academicPeriodContext.selectedAcademicPeriodId, () => {
  const unitId = selectedAcademicUnitId.value
  if (unitId) selectedAcademicUnitId.value = ''
  nextTick(() => { selectedAcademicUnitId.value = unitId })
})

function requirementTone(state: string) {
  if (state === 'VERIFIED') return 'success' as const
  if (state === 'PENDING') return 'info' as const
  if (state === 'REJECTED') return 'error' as const
  return 'neutral' as const
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Academic-unit document register">
        <template #leading><UDashboardSidebarCollapse /></template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <USelect v-model="selectedAcademicUnitId" :items="academicUnitItems" value-key="value" placeholder="Select academic unit" searchable class="w-full sm:w-96" />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="space-y-5 p-4 sm:p-6">
        <UAlert color="info" variant="soft" icon="i-lucide-folder-check" title="Consolidated document view" description="All applicant documents for programme choices owned by this academic unit, in one place, instead of split per application." />
        <div class="grid gap-3 sm:grid-cols-4">
          <EmhareKpiCard label="Applications" :value="register.length" icon="i-lucide-files" tone="primary" />
          <EmhareKpiCard label="Missing" :value="totalMissing" icon="i-lucide-circle-alert" tone="warning" />
          <EmhareKpiCard label="Pending verification" :value="totalPending" icon="i-lucide-clock" tone="info" />
          <EmhareKpiCard label="Rejected" :value="totalRejected" icon="i-lucide-circle-x" tone="error" />
        </div>
        <UAlert v-if="loadError" color="error" variant="soft" icon="i-lucide-circle-alert" title="Documents unavailable" :description="loadError" />

        <EmharePaginatedCollection v-slot="{ items: paginatedRegister }" :items="register">
          <div class="space-y-3">
            <UCard v-for="entry in paginatedRegister" :key="entry.applicationId" :ui="{ body: 'p-4' }">
              <div class="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p class="font-mono text-xs text-primary">{{ entry.applicationNumber }}</p>
                  <h2 class="mt-1 font-semibold text-highlighted">{{ entry.applicantName }}</h2>
                </div>
                <EmhareStatusPill :label="entry.applicationStatus" tone="neutral" />
              </div>
              <div class="mt-3 flex flex-wrap gap-2">
                <UBadge
                  v-for="requirement in entry.documents.requirements"
                  :key="requirement.requirementCode"
                  :label="`${requirement.requirementName} · ${requirement.state}`"
                  :color="requirementTone(requirement.state)"
                  variant="subtle"
                />
              </div>
            </UCard>
            <div v-if="!loading && !register.length" class="py-12 text-center">
              <UIcon name="i-lucide-folder-open" class="mx-auto size-8 text-muted" />
              <p class="mt-3 font-medium text-highlighted">No applications for this academic unit</p>
            </div>
          </div>
        </EmharePaginatedCollection>
      </div>
    </template>
  </UDashboardPanel>
</template>
