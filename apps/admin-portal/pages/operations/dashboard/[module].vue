<!-- Author: Tinashe K -->
<script setup lang="ts">
import {
  isOperationalDashboardKey,
  loadOperationalDashboard,
  operationalDashboardModules,
  type OperationalDashboardSnapshot,
} from "@emhare/portal-shell/utils/operational-dashboard";

defineOptions({ name: "OperationalModuleDashboardPage" });
definePageMeta({ layout: "dashboard" });

const route = useRoute();
const api = useEmhareApi();
const academicPeriodContext = useAcademicPeriodContext();
const loading = ref(true);
const errorMessage = ref("");
const snapshot = ref<OperationalDashboardSnapshot | null>(null);

const moduleKey = computed(() => String(route.params.module ?? ""));
const moduleDefinition = computed(() =>
  operationalDashboardModules.find((module) => module.key === moduleKey.value),
);

if (!isOperationalDashboardKey(moduleKey.value)) {
  throw createError({ statusCode: 404, statusMessage: "Operational dashboard not found" });
}

onMounted(loadDashboard);
watch(moduleKey, loadDashboard);
watch(academicPeriodContext.selectedAcademicPeriodId, () => void loadDashboard());

async function loadDashboard() {
  if (!isOperationalDashboardKey(moduleKey.value)) return;
  if (moduleKey.value === "admissions") {
    await navigateTo("/operations/admissions-dashboard", { replace: true });
    return;
  }

  loading.value = true;
  errorMessage.value = "";
  try {
    snapshot.value = await loadOperationalDashboard(api, moduleKey.value, {
      academicPeriodId: academicPeriodContext.selectedAcademicPeriodId.value,
    });
  } catch (error) {
    snapshot.value = null;
    errorMessage.value = api.errorMessage(
      error,
      `${moduleDefinition.value?.label ?? "This module"} overview could not be loaded.`,
    );
  } finally {
    loading.value = false;
  }
}

function refreshedAtLabel() {
  if (!snapshot.value?.generatedAt) return "Not yet refreshed";
  return `Updated ${new Date(snapshot.value.generatedAt).toLocaleString()}`;
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar :title="`${moduleDefinition?.label ?? 'Module'} overview`">
        <template #right>
          <UButton
            v-if="moduleDefinition"
            :to="moduleDefinition.workspacePath"
            label="Open workspace"
            :icon="moduleDefinition.icon"
            color="primary"
          />
          <UButton
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="outline"
            :aria-label="`Refresh ${moduleDefinition?.label ?? 'module'} overview`"
            :loading="loading"
            @click="loadDashboard"
          />
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <span class="text-sm text-muted">{{ refreshedAtLabel() }}</span>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <UContainer
        :data-testid="`operational-dashboard-${moduleKey}`"
        class="w-full max-w-none space-y-6 py-4 sm:py-6 [--ui-primary:var(--ui-color-primary-800)] dark:[--ui-primary:var(--ui-color-primary-300)]"
      >
        <UAlert
          v-if="errorMessage"
          color="error"
          variant="soft"
          icon="i-lucide-circle-alert"
          :title="`${moduleDefinition?.label ?? 'Module'} overview unavailable`"
          :description="errorMessage"
        >
          <template #actions>
            <UButton
              label="Try again"
              icon="i-lucide-refresh-cw"
              color="error"
              variant="soft"
              @click="loadDashboard"
            />
          </template>
        </UAlert>

        <div
          v-if="loading && !snapshot"
          aria-label="Loading operational dashboard"
          class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4"
        >
          <USkeleton v-for="index in 8" :key="index" class="h-40 rounded-xl" />
        </div>

        <EmhareOperationalDashboard v-else-if="snapshot" :snapshot="snapshot" />
      </UContainer>
    </template>
  </UDashboardPanel>
</template>
