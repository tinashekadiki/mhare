<script setup lang="ts">
import Swal from 'sweetalert2'
import type { StudentConversionSummary } from '@emhare/portal-shell/types/student-records'

definePageMeta({ layout: 'dashboard' })

const api = useEmhareApi()
const toast = useToast()
const { showError } = useEmhareConfirm()
const conversions = ref<StudentConversionSummary[]>([])
const loading = ref(false)
const activeActionId = ref<string | null>(null)
const loadError = ref('')
const activeFilter = ref<'ALL' | 'ATTENTION' | 'PROVISIONING' | 'COMPLETED'>('ALL')

const filterItems = [
  { label: 'All', value: 'ALL' },
  { label: 'Needs attention', value: 'ATTENTION' },
  { label: 'In progress', value: 'PROVISIONING' },
  { label: 'Completed', value: 'COMPLETED' }
]

const totals = computed(() => ({
  total: conversions.value.length,
  provisioning: conversions.value.filter(conversion => conversion.status === 'PROVISIONING').length,
  failed: conversions.value.filter(conversion => conversion.status === 'FAILED').length,
  completed: conversions.value.filter(conversion => conversion.status === 'COMPLETED').length
}))

const filteredConversions = computed(() => conversions.value.filter((conversion) => {
  if (activeFilter.value === 'ALL') return true
  if (activeFilter.value === 'ATTENTION') return conversion.status === 'FAILED'
  return conversion.status === activeFilter.value
}))

onMounted(loadConversions)

async function loadConversions() {
  loading.value = true
  loadError.value = ''
  try {
    conversions.value = await api.request<StudentConversionSummary[]>('/api/student-records/conversions')
  } catch (error) {
    loadError.value = api.errorMessage(error, 'Student conversion operations could not be loaded.')
  } finally {
    loading.value = false
  }
}

async function retryConversion(conversion: StudentConversionSummary) {
  const result = await Swal.fire({
    title: 'Retry student provisioning?',
    text: 'A new event is created only for each pending or failed provisioning operation. Completed operations remain unchanged.',
    icon: 'question',
    input: 'textarea',
    inputLabel: 'Retry reason',
    inputPlaceholder: 'Record what was corrected and why this retry is authorised.',
    inputAttributes: { maxlength: '1000' },
    showCancelButton: true,
    confirmButtonText: 'Retry provisioning',
    confirmButtonColor: '#006633',
    cancelButtonText: 'Cancel',
    inputValidator: value => value.trim() ? undefined : 'A retry reason is required.'
  })
  if (!result.isConfirmed || !result.value?.trim()) return

  activeActionId.value = conversion.id
  try {
    const updated = await api.request<StudentConversionSummary>(
      `/api/student-records/conversions/${conversion.id}/retry`,
      { method: 'POST', body: { reason: result.value.trim() } }
    )
    conversions.value = conversions.value.map(existing => existing.id === updated.id ? updated : existing)
    toast.add({
      title: 'Provisioning retry queued',
      description: `${updated.studentNumber} has ${updated.retryCount} recorded retry attempt${updated.retryCount === 1 ? '' : 's'}.`,
      color: 'success'
    })
  } catch (error) {
    await showError('Provisioning could not be retried', api.errorMessage(error))
  } finally {
    activeActionId.value = null
  }
}

function formatStatus(value: string) {
  return value.toLowerCase().split('_')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

function formatDateTime(value: string | null) {
  if (!value) return 'Not completed'
  return new Intl.DateTimeFormat('en-ZW', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value))
}

function conversionTone(status: StudentConversionSummary['status']) {
  if (status === 'COMPLETED') return 'success' as const
  if (status === 'FAILED') return 'error' as const
  return 'info' as const
}

function provisioningTone(status: StudentConversionSummary['financeProvisioningStatus']) {
  if (status === 'COMPLETED') return 'success' as const
  if (status === 'FAILED') return 'error' as const
  return 'warning' as const
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Student conversion operations">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right>
          <UButton
            label="Refresh"
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="outline"
            :loading="loading"
            @click="loadConversions"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="space-y-6 p-4 sm:p-6">
        <UAlert
          color="primary"
          variant="soft"
          icon="i-lucide-shield-check"
          title="Controlled student activation"
          description="An accepted offer creates a provisioning record. The student becomes Active only after both the USD Finance account and STUDENT portal role are confirmed."
        />
        <UAlert
          v-if="loadError"
          color="error"
          variant="soft"
          icon="i-lucide-triangle-alert"
          title="Conversion queue unavailable"
          :description="loadError"
        />

        <section class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4" aria-label="Student conversion summary">
          <UCard :ui="{ body: 'p-4' }">
            <p class="text-xs font-medium uppercase tracking-wide text-muted">Total requests</p>
            <p class="mt-2 text-2xl font-semibold text-highlighted">{{ totals.total }}</p>
          </UCard>
          <UCard :ui="{ body: 'p-4' }">
            <p class="text-xs font-medium uppercase tracking-wide text-info">In progress</p>
            <p class="mt-2 text-2xl font-semibold text-highlighted">{{ totals.provisioning }}</p>
          </UCard>
          <UCard :ui="{ body: 'p-4' }">
            <p class="text-xs font-medium uppercase tracking-wide text-error">Needs attention</p>
            <p class="mt-2 text-2xl font-semibold text-highlighted">{{ totals.failed }}</p>
          </UCard>
          <UCard :ui="{ body: 'p-4' }">
            <p class="text-xs font-medium uppercase tracking-wide text-success">Completed</p>
            <p class="mt-2 text-2xl font-semibold text-highlighted">{{ totals.completed }}</p>
          </UCard>
        </section>

        <section class="space-y-3" aria-labelledby="student-conversion-queue-heading">
          <div class="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <p class="text-xs font-medium uppercase tracking-wide text-primary">Operational work queue</p>
              <h2 id="student-conversion-queue-heading" class="mt-1 text-lg font-semibold text-highlighted">Accepted-offer conversions</h2>
            </div>
            <USelect
              v-model="activeFilter"
              :items="filterItems"
              value-key="value"
              aria-label="Filter student conversions"
              class="w-full sm:w-48"
            />
          </div>

          <div v-if="loading && !conversions.length" class="space-y-3" aria-label="Loading student conversions">
            <USkeleton v-for="index in 3" :key="index" class="h-44 w-full" />
          </div>

          <EmharePaginatedCollection v-else v-slot="{ items: paginatedConversions }" :items="filteredConversions">
          <div class="space-y-3">
            <UCard v-for="conversion in paginatedConversions" :key="conversion.id" variant="outline">
              <div class="grid gap-4 xl:grid-cols-[minmax(0,1.5fr)_minmax(18rem,1fr)] xl:items-start">
                <div>
                  <div class="flex flex-wrap items-center gap-2">
                    <p class="font-mono text-xs text-muted">{{ conversion.studentNumber }}</p>
                    <EmhareStatusPill :label="formatStatus(conversion.status)" :tone="conversionTone(conversion.status)" />
                  </div>
                  <h3 class="mt-2 font-semibold text-highlighted">{{ conversion.programmeCode }} · {{ conversion.programmeName }}</h3>
                  <dl class="mt-3 grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-3">
                    <div>
                      <dt class="text-xs text-muted">Requested</dt>
                      <dd class="mt-1 font-medium text-highlighted">{{ formatDateTime(conversion.requestedAt) }}</dd>
                    </div>
                    <div>
                      <dt class="text-xs text-muted">Completed</dt>
                      <dd class="mt-1 font-medium text-highlighted">{{ formatDateTime(conversion.completedAt) }}</dd>
                    </div>
                    <div>
                      <dt class="text-xs text-muted">Student status</dt>
                      <dd class="mt-1 font-medium text-highlighted">{{ formatStatus(conversion.studentStatus) }}</dd>
                    </div>
                  </dl>
                  <UAlert
                    v-if="conversion.failureReason"
                    class="mt-3"
                    color="error"
                    variant="soft"
                    icon="i-lucide-triangle-alert"
                    title="Manual intervention required"
                    :description="conversion.failureReason"
                  />
                  <div v-if="conversion.status !== 'COMPLETED'" class="mt-3 flex flex-wrap items-center gap-3">
                    <UButton
                      label="Retry provisioning"
                      icon="i-lucide-rotate-ccw"
                      color="primary"
                      variant="soft"
                      :loading="activeActionId === conversion.id"
                      @click="retryConversion(conversion)"
                    />
                    <p class="text-xs text-muted">
                      {{ conversion.retryCount }} recorded retr{{ conversion.retryCount === 1 ? 'y' : 'ies' }}
                      <span v-if="conversion.lastRetryAt"> · Last {{ formatDateTime(conversion.lastRetryAt) }}</span>
                    </p>
                  </div>
                </div>

                <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-1">
                  <div class="rounded-lg border border-muted p-3">
                    <div class="flex items-center justify-between gap-3">
                      <div>
                        <p class="text-xs text-muted">Finance account</p>
                        <p class="mt-1 text-sm font-medium text-highlighted">USD base ledger</p>
                      </div>
                      <EmhareStatusPill
                        :label="formatStatus(conversion.financeProvisioningStatus)"
                        :tone="provisioningTone(conversion.financeProvisioningStatus)"
                      />
                    </div>
                  </div>
                  <div class="rounded-lg border border-muted p-3">
                    <div class="flex items-center justify-between gap-3">
                      <div>
                        <p class="text-xs text-muted">Portal access</p>
                        <p class="mt-1 text-sm font-medium text-highlighted">STUDENT role</p>
                      </div>
                      <EmhareStatusPill
                        :label="formatStatus(conversion.portalProvisioningStatus)"
                        :tone="provisioningTone(conversion.portalProvisioningStatus)"
                      />
                    </div>
                  </div>
                </div>
              </div>
            </UCard>

            <UEmpty
              v-if="!filteredConversions.length"
              title="No conversions in this queue"
              description="Accepted offers appear here after all required conditions are resolved."
              icon="i-lucide-user-round-check"
            />
          </div>
          </EmharePaginatedCollection>
        </section>
      </div>
    </template>
  </UDashboardPanel>
</template>
