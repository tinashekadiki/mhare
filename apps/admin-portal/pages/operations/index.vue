<!-- Author: Tinashe K -->
<script setup lang="ts">
import {
  loadOperationsOverview,
  operationalDashboardModules,
  type OperationalDashboardSnapshot
} from '@emhare/portal-shell/utils/operational-dashboard'

defineOptions({ name: 'OperationsDashboardPage' })
definePageMeta({ layout: 'dashboard' })

const route = useRoute()
const api = useEmhareApi()
const loading = ref(true)
const refreshedAt = ref('')
const loadingModuleKeys = ref(new Set(operationalDashboardModules.map(module => module.key)))
const modules = ref<OperationalDashboardSnapshot[]>(operationalDashboardModules.map(module => ({
  ...module,
  available: false,
  generatedAt: '',
  scopeNote: '',
  metrics: [],
  actions: [],
  distribution: [],
  links: []
})))

const accessRestricted = computed(() => route.query.access === 'restricted')

onMounted(loadDashboard)

async function loadDashboard() {
  loading.value = true
  loadingModuleKeys.value = new Set(operationalDashboardModules.map(module => module.key))
  await Promise.all(operationalDashboardModules.map(async (module, index) => {
    const [loadedModule] = await loadOperationsOverview(api, [module.key])
    if (loadedModule) modules.value[index] = loadedModule
    loadingModuleKeys.value.delete(module.key)
    loadingModuleKeys.value = new Set(loadingModuleKeys.value)
  }))
  refreshedAt.value = new Date().toISOString()
  loading.value = false
}

function refreshedAtLabel() {
  if (!refreshedAt.value) return 'Not yet refreshed'
  return `Updated ${new Date(refreshedAt.value).toLocaleString()}`
}

function moduleSummaryMetrics(module: OperationalDashboardSnapshot) {
  return module.summaryMetrics?.length ? module.summaryMetrics : module.metrics.slice(0, 2)
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardToolbar>
        <template #left>
          <span class="text-sm text-muted">{{ refreshedAtLabel() }}</span>
        </template>
        <template #right>
          <UButton
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="outline"
            aria-label="Refresh Operations dashboard"
            :loading="loading"
            @click="loadDashboard"
          />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <UContainer
        data-testid="operations-dashboard-content"
        class="w-full max-w-none space-y-6 py-4 sm:py-6 [--ui-primary:var(--ui-color-primary-800)] dark:[--ui-primary:var(--ui-color-primary-300)]"
      >
        <h1 class="sr-only">Operations</h1>
        <UAlert
          v-if="accessRestricted"
          color="warning"
          variant="subtle"
          icon="i-lucide-triangle-alert"
          title="Access restricted"
          description="Your assigned role does not include that operational workspace. Dashboards below only show data your current permissions can read."
        />

        <section aria-label="Operational areas">
          <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            <UCard
              v-for="module in modules"
              :key="module.key"
              :data-testid="`operations-module-${module.key}`"
              :ui="{ body: 'p-4 sm:p-5', footer: 'p-3 sm:px-5' }"
              class="flex min-h-52 flex-col"
            >
              <div class="flex items-center gap-3">
                <span class="grid size-10 shrink-0 place-items-center rounded-md bg-primary/10 text-primary">
                  <UIcon :name="module.icon" class="size-5" />
                </span>
                <h2 class="min-w-0 flex-1 truncate text-base font-semibold text-highlighted">{{ module.label }}</h2>
                <UBadge
                  v-if="loadingModuleKeys.has(module.key) || !module.available"
                  :label="loadingModuleKeys.has(module.key) ? 'Loading' : 'Unavailable'"
                  :color="loadingModuleKeys.has(module.key) ? 'neutral' : 'error'"
                  variant="subtle"
                />
              </div>

              <div v-if="loadingModuleKeys.has(module.key)" class="mt-4 grid grid-cols-2 gap-3 border-t border-muted pt-4">
                <USkeleton class="h-14 rounded-md" />
                <USkeleton class="h-14 rounded-md" />
              </div>
              <div v-else-if="module.available" class="mt-4 grid grid-cols-2 gap-x-4 gap-y-3 border-t border-muted pt-4">
                <div
                  v-for="metric in moduleSummaryMetrics(module)"
                  :key="metric.label"
                  data-testid="operations-summary-metric"
                >
                  <p data-testid="operations-summary-value" class="text-2xl font-semibold tabular-nums text-highlighted">{{ metric.value }}</p>
                  <p data-testid="operations-summary-label" class="mt-0.5 truncate text-xs text-muted">{{ metric.label }}</p>
                </div>
              </div>
              <UAlert
                v-else
                class="mt-4"
                color="error"
                variant="soft"
                title="Metrics not available"
                :description="module.errorMessage"
              />

              <template #footer>
                <UButton
                  :to="module.dashboardPath"
                  label="Open dashboard"
                  icon="i-lucide-arrow-up-right"
                  trailing
                  color="neutral"
                  variant="ghost"
                  class="-mx-2 w-[calc(100%+1rem)] justify-between"
                />
              </template>
            </UCard>
          </div>
        </section>
      </UContainer>
    </template>
  </UDashboardPanel>
</template>
