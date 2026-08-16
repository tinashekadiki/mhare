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
const availableModules = computed(() => modules.value.filter(module => module.available))
const unavailableModules = computed(() => modules.value.filter(module => !module.available))
const attentionCount = computed(() => availableModules.value.reduce(
  (total, module) => total + module.actions.reduce((moduleTotal, action) => moduleTotal + action.value, 0),
  0
))

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
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Operations">
        <template #leading>
          <UDashboardSidebarCollapse />
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
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <span class="text-sm text-muted">{{ refreshedAtLabel() }}</span>
        </template>
        <template #right>
          <UBadge color="primary" variant="soft" label="University operations" />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <UContainer
        data-testid="operations-dashboard-content"
        class="w-full max-w-none space-y-6 py-4 sm:py-6 [--ui-primary:var(--ui-color-primary-800)] dark:[--ui-primary:var(--ui-color-primary-300)]"
      >
        <UAlert
          v-if="accessRestricted"
          color="warning"
          variant="subtle"
          icon="i-lucide-triangle-alert"
          title="Access restricted"
          description="Your assigned role does not include that operational workspace. Dashboards below only show data your current permissions can read."
        />

        <section aria-labelledby="operations-pulse-heading" class="space-y-3">
          <div class="flex flex-wrap items-end justify-between gap-3">
            <div>
              <p class="text-xs font-semibold uppercase tracking-wide text-primary">Cross-module control</p>
              <h2 id="operations-pulse-heading" class="mt-1 text-lg font-semibold text-highlighted">Operational pulse</h2>
            </div>
            <p class="max-w-2xl text-xs leading-5 text-muted">Every count comes from its owning service. Unavailable or unauthorised modules are identified explicitly and are never represented as zero.</p>
          </div>
          <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <EmhareKpiCard label="Module dashboards" :value="operationalDashboardModules.length" hint="Implemented operational domains" icon="i-lucide-layout-dashboard" tone="primary" />
            <EmhareKpiCard label="Reporting now" :value="availableModules.length" hint="Modules that returned live metrics" icon="i-lucide-activity" tone="success" />
            <EmhareKpiCard label="Work items" :value="attentionCount" hint="Actionable records across available modules" icon="i-lucide-list-checks" tone="warning" />
            <EmhareKpiCard label="Unavailable" :value="unavailableModules.length" hint="Service or permission failures" icon="i-lucide-unplug" :tone="unavailableModules.length ? 'error' : 'neutral'" />
          </div>
        </section>

        <section aria-labelledby="module-dashboards-heading" class="space-y-3">
          <div>
            <p class="text-xs font-semibold uppercase tracking-wide text-primary">Owned service evidence</p>
            <h2 id="module-dashboards-heading" class="mt-1 text-lg font-semibold text-highlighted">Module dashboards</h2>
          </div>
          <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            <UCard
              v-for="module in modules"
              :key="module.key"
              :data-testid="`operations-module-${module.key}`"
              :ui="{ body: 'p-5', footer: 'p-3 sm:px-5' }"
              class="flex min-h-64 flex-col"
            >
              <div class="flex items-start justify-between gap-4">
                <span class="grid size-10 shrink-0 place-items-center rounded-md bg-primary/10 text-primary">
                  <UIcon :name="module.icon" class="size-5" />
                </span>
                <UBadge
                  :label="loadingModuleKeys.has(module.key) ? 'Loading' : module.available ? 'Live' : 'Unavailable'"
                  :color="loadingModuleKeys.has(module.key) ? 'neutral' : module.available ? 'success' : 'error'"
                  variant="subtle"
                />
              </div>
              <h3 class="mt-4 text-base font-semibold text-highlighted">{{ module.label }}</h3>
              <p class="mt-1 text-sm leading-5 text-muted">{{ module.description }}</p>

              <div v-if="loadingModuleKeys.has(module.key)" class="mt-5 grid grid-cols-2 gap-3 border-t border-muted pt-4">
                <USkeleton class="h-14 rounded-md" />
                <USkeleton class="h-14 rounded-md" />
              </div>
              <div v-else-if="module.available" class="mt-5 grid grid-cols-2 gap-3 border-t border-muted pt-4">
                <div v-for="metric in module.metrics.slice(0, 2)" :key="metric.label">
                  <p class="text-2xl font-semibold tabular-nums text-highlighted">{{ metric.value }}</p>
                  <p class="mt-1 text-xs text-muted">{{ metric.label }}</p>
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
