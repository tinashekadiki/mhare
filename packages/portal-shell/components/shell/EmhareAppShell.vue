<script setup lang="ts">
import type {
  EmhareAcademicPeriod,
  EmhareEnvironment,
  EmhareNavigationGroup,
  EmhareNavigationItem,
  EmhareNotification,
  EmhareQuickAction,
} from "../../types/emhare-ui";

const props = withDefaults(
  defineProps<{
    appName?: string;
    appDescription?: string;
    navigationGroups?: EmhareNavigationGroup[];
    quickActions?: EmhareQuickAction[];
    academicPeriods?: EmhareAcademicPeriod[];
    selectedAcademicPeriodId?: string | null;
    academicPeriodsLoading?: boolean;
    academicPeriodsError?: string;
    showAcademicPeriodSwitcher?: boolean;
    environment?: EmhareEnvironment;
    notifications?: EmhareNotification[];
    userName?: string;
    userEmail?: string;
    requireAuthentication?: boolean;
  }>(),
  {
    appName: "eMhare",
    appDescription: "University operations",
    navigationGroups: () => [],
    quickActions: () => [],
    academicPeriods: () => [],
    selectedAcademicPeriodId: null,
    academicPeriodsLoading: false,
    academicPeriodsError: "",
    showAcademicPeriodSwitcher: false,
    environment: undefined,
    notifications: () => [],
    userName: "Operator",
    userEmail: "",
    requireAuthentication: true,
  },
);

const emit = defineEmits<{
  "quick-action": [action: EmhareQuickAction];
  "period-change": [period: EmhareAcademicPeriod];
  "notification-select": [notification: EmhareNotification];
  logout: [];
}>();

const route = useRoute();
const auth = useEmhareAuth();
const searchOpen = ref(false);
const search = ref("");
const selectedPeriodId = ref<string | undefined>();

const env = (import.meta as unknown as { env?: Record<string, string | undefined> }).env;
const resolvedEnvironment = computed<EmhareEnvironment>(
  () =>
    props.environment ?? {
      name: env?.NUXT_PUBLIC_ENVIRONMENT_NAME ?? "Local",
      tone: (env?.NUXT_PUBLIC_ENVIRONMENT_TONE as EmhareEnvironment["tone"]) ?? "neutral",
    },
);

const selectedPeriod = computed(() =>
  props.academicPeriods.find((period) => period.id === selectedPeriodId.value),
);

function mapNavigationItem(item: EmhareNavigationItem): EmhareNavigationItem {
  return {
    label: item.label,
    icon: item.icon,
    to: item.to,
    badge: item.badge,
    children: item.children?.map(mapNavigationItem),
  };
}

const navigationItems = computed(() =>
  props.navigationGroups.flatMap((group) => {
    const items = group.items.map(mapNavigationItem);
    if (!group.label) {
      return items;
    }
    return [
      {
        label: group.label,
        icon: group.icon,
        type: "trigger" as const,
        defaultOpen: true,
        children: items,
      },
    ];
  }),
);

const breadcrumbs = computed(() => {
  const segments = route.path.split("/").filter(Boolean);
  if (!segments.length) {
    return [{ label: "Home", to: "/" }];
  }
  return segments.map((segment, index) => ({
    label: segment
      .split("-")
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
      .join(" "),
    to: `/${segments.slice(0, index + 1).join("/")}`,
  }));
});

const searchableItems = computed(() =>
  props.navigationGroups
    .flatMap((group) => group.items.flatMap((item) => [item, ...(item.children ?? [])]))
    .filter((item) => item.label.toLowerCase().includes(search.value.toLowerCase())),
);

const quickActionItems = computed(() =>
  props.quickActions.map((action) => ({
    label: action.label,
    icon: action.icon,
    description: action.description,
    onSelect: () => handleQuickAction(action),
  })),
);

const notificationItems = computed(() =>
  props.notifications.length
    ? props.notifications.map((notification) => ({
        label: notification.title,
        description: [notification.description, notification.time].filter(Boolean).join(" · "),
        icon: notification.readAt ? "i-lucide-mail-open" : "i-lucide-mail",
        onSelect: () => emit("notification-select", notification),
      }))
    : [{ label: "No notifications", disabled: true }],
);
const unreadNotificationCount = computed(
  () => props.notifications.filter((notification) => !notification.readAt).length,
);

const defaultPrimaryColor = "#001f6e";
const defaultSecondaryColor = "#cb920e";
const primaryPaletteMixes: Record<string, [number, string]> = {
  "50": [94, "#ffffff"],
  "100": [87, "#ffffff"],
  "200": [72, "#ffffff"],
  "300": [52, "#ffffff"],
  "400": [28, "#ffffff"],
  "500": [10, "#ffffff"],
  "600": [0, "#ffffff"],
  "700": [15, "#000000"],
  "800": [28, "#000000"],
  "900": [40, "#000000"],
  "950": [58, "#000000"],
};
const secondaryPaletteMixes: Record<string, [number, string]> = {
  "50": [94, "#ffffff"],
  "100": [84, "#ffffff"],
  "200": [68, "#ffffff"],
  "300": [45, "#ffffff"],
  "400": [20, "#ffffff"],
  "500": [0, "#ffffff"],
  "600": [18, "#000000"],
  "700": [32, "#000000"],
  "800": [44, "#000000"],
  "900": [55, "#000000"],
  "950": [68, "#000000"],
};

function normalizedHexColor(value: unknown, fallback: string) {
  if (typeof value !== "string" || !/^#[0-9a-f]{6}$/i.test(value.trim())) {
    return fallback;
  }
  return value.trim().toLowerCase();
}

function mixHexColors(baseColor: string, mixColor: string, mixPercentage: number) {
  const baseChannels = [1, 3, 5].map((index) =>
    Number.parseInt(baseColor.slice(index, index + 2), 16),
  );
  const mixChannels = [1, 3, 5].map((index) =>
    Number.parseInt(mixColor.slice(index, index + 2), 16),
  );
  const mixRatio = mixPercentage / 100;
  return `#${baseChannels
    .map((channel, index) =>
      Math.round(channel * (1 - mixRatio) + Number(mixChannels[index]) * mixRatio)
        .toString(16)
        .padStart(2, "0"),
    )
    .join("")}`;
}

function applyColorPalette(
  variablePrefix: string,
  baseColor: string,
  paletteMixes: Record<string, [number, string]>,
) {
  for (const [shade, [mixPercentage, mixColor]] of Object.entries(paletteMixes)) {
    document.documentElement.style.setProperty(
      `${variablePrefix}-${shade}`,
      mixPercentage === 0 ? baseColor : mixHexColors(baseColor, mixColor, mixPercentage),
    );
  }
}

function applyInstitutionBranding(brandingJson?: string) {
  if (import.meta.server) return;
  let branding: Record<string, unknown> = {};
  try {
    branding = JSON.parse(brandingJson || "{}") as Record<string, unknown>;
  } catch {
    branding = {};
  }
  const primaryColor = normalizedHexColor(branding.primaryColor, defaultPrimaryColor);
  const secondaryColor = normalizedHexColor(branding.secondaryColor, defaultSecondaryColor);
  applyColorPalette("--color-uzazure", primaryColor, primaryPaletteMixes);
  applyColorPalette("--color-uzorange", secondaryColor, secondaryPaletteMixes);
}

const periodItems = computed(() => {
  if (props.academicPeriodsLoading) {
    return [{ label: "Loading academic periods", icon: "i-lucide-refresh-cw", disabled: true }];
  }
  if (props.academicPeriodsError) {
    return [
      {
        label: "Academic periods unavailable",
        description: props.academicPeriodsError,
        icon: "i-lucide-circle-alert",
        disabled: true,
      },
    ];
  }
  return props.academicPeriods.length
    ? props.academicPeriods.map((period) => ({
        label: period.label,
        description: period.description,
        icon: period.id === selectedPeriodId.value ? "i-lucide-check" : undefined,
        onSelect: () => selectPeriod(period),
      }))
    : [{ label: "No academic periods configured", disabled: true }];
});

const userMenuItems = computed(() => [
  [{ label: props.userName, description: props.userEmail || "Signed in", disabled: true }],
  [{ label: "Sign out", icon: "i-lucide-log-out", onSelect: () => emit("logout") }],
]);

async function resolveAuthenticatedSession(returnTo = route.fullPath) {
  if (props.requireAuthentication) {
    await auth.requireUser(returnTo);
    return;
  }

  await auth.loadUser();
  if (auth.authenticated.value && !auth.currentUserProfile.value) {
    await auth.syncCoreUser();
  }
}

onMounted(async () => {
  await resolveAuthenticatedSession();
});

watch(
  [() => props.selectedAcademicPeriodId, () => props.academicPeriods],
  ([requestedAcademicPeriodId, academicPeriods]) => {
    const requestedPeriodExists = academicPeriods.some(
      (period) => period.id === requestedAcademicPeriodId,
    );
    const existingSelectionExists = academicPeriods.some(
      (period) => period.id === selectedPeriodId.value,
    );
    selectedPeriodId.value = requestedPeriodExists
      ? (requestedAcademicPeriodId ?? undefined)
      : existingSelectionExists
        ? selectedPeriodId.value
        : (academicPeriods.find((period) => period.current)?.id ?? academicPeriods[0]?.id);
  },
  { immediate: true },
);

watch(
  () => route.fullPath,
  async (fullPath) => {
    if (!props.requireAuthentication) {
      return;
    }
    await resolveAuthenticatedSession(fullPath);
  },
);

watch(() => auth.currentUserProfile.value?.institutionBrandingJson, applyInstitutionBranding, {
  immediate: true,
});

function handleQuickAction(action: EmhareQuickAction) {
  emit("quick-action", action);
  if (action.to) {
    navigateTo(action.to);
  }
}

function selectPeriod(period: EmhareAcademicPeriod) {
  selectedPeriodId.value = period.id;
  emit("period-change", period);
}
</script>

<template>
  <UDashboardGroup>
    <UDashboardSidebar collapsible resizable>
      <template #header="{ collapsed }">
        <EmhareProductBrand
          class="min-h-14 px-2"
          :label="appName"
          :description="appDescription"
          :show-copy="!collapsed"
        />
      </template>

      <template #default="{ collapsed }">
        <div class="space-y-4">
          <UNavigationMenu :collapsed="collapsed" :items="navigationItems" orientation="vertical" />
        </div>
      </template>

      <template #footer="{ collapsed }">
        <UDropdownMenu :items="userMenuItems">
          <UButton
            icon="i-lucide-circle-user-round"
            :label="collapsed ? undefined : userName"
            color="neutral"
            variant="ghost"
            block
          />
        </UDropdownMenu>
      </template>
    </UDashboardSidebar>

    <div class="relative flex min-w-0 flex-1 flex-col">
      <header class="sticky top-0 z-20 border-b border-muted bg-default/95 backdrop-blur">
        <div class="flex min-h-14 flex-wrap items-center gap-2 px-4">
          <UDashboardSidebarToggle class="lg:hidden" />
          <UDashboardSidebarCollapse class="hidden lg:inline-flex" />
          <UBreadcrumb :items="breadcrumbs" class="min-w-0 flex-1" />

          <UBadge
            :color="resolvedEnvironment.tone ?? 'neutral'"
            variant="subtle"
            :label="resolvedEnvironment.name"
          />

          <UDropdownMenu v-if="showAcademicPeriodSwitcher" :items="periodItems">
            <UButton
              data-testid="academic-period-switcher"
              icon="i-lucide-calendar-clock"
              :label="
                academicPeriodsLoading
                  ? 'Loading periods'
                  : (selectedPeriod?.label ?? 'Academic period')
              "
              :loading="academicPeriodsLoading"
              color="neutral"
              variant="ghost"
              aria-label="Academic period switcher"
            />
          </UDropdownMenu>

          <UButton
            icon="i-lucide-search"
            label="Search"
            color="neutral"
            variant="ghost"
            @click="searchOpen = true"
          />

          <UDropdownMenu :items="quickActionItems">
            <UButton icon="i-lucide-plus" label="Quick actions" color="primary" />
          </UDropdownMenu>

          <UDropdownMenu :items="notificationItems">
            <UButton
              icon="i-lucide-bell"
              color="neutral"
              variant="ghost"
              :label="unreadNotificationCount ? String(unreadNotificationCount) : undefined"
              aria-label="Notifications"
            />
          </UDropdownMenu>
        </div>
      </header>

      <div class="relative flex min-h-0 flex-1">
        <div id="emhare-route-content" class="contents">
          <slot />
        </div>

        <div
          id="emhare-main-workspace"
          class="pointer-events-none absolute inset-0 z-30"
          aria-live="polite"
        />
      </div>

      <div
        v-if="searchOpen"
        class="fixed inset-0 z-50 grid place-items-start bg-inverted/25 px-4 py-20"
        @click.self="searchOpen = false"
      >
        <div class="mx-auto w-full max-w-2xl rounded-lg border border-muted bg-default shadow-xl">
          <div class="border-b border-muted p-3">
            <UInput
              v-model="search"
              icon="i-lucide-search"
              autofocus
              placeholder="Search pages and actions"
            />
          </div>
          <div class="max-h-96 overflow-y-auto p-2">
            <NuxtLink
              v-for="item in searchableItems"
              :key="`${item.label}-${item.to}`"
              :to="item.to"
              class="flex items-center gap-3 rounded-md px-3 py-2 text-sm hover:bg-elevated"
              @click="searchOpen = false"
            >
              <UIcon v-if="item.icon" :name="item.icon" class="size-4 text-primary" />
              <span class="font-medium text-highlighted">{{ item.label }}</span>
              <span v-if="item.to" class="ml-auto text-xs text-muted">{{ item.to }}</span>
            </NuxtLink>
            <p v-if="!searchableItems.length" class="px-3 py-8 text-center text-sm text-muted">
              No matching pages.
            </p>
          </div>
        </div>
      </div>
    </div>
  </UDashboardGroup>
</template>
