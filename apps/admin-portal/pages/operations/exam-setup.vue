<script setup lang="ts">
import Swal from "sweetalert2";
import type { AcademicSetupOverview } from "@emhare/portal-shell/types/academic";
import type {
  ExamRequirementSummary,
  ExamSessionSummary,
  ExamSetupRegister,
  ExamVenueSummary,
} from "@emhare/portal-shell/types/exams";

definePageMeta({ layout: "dashboard" });
const api = useEmhareApi();
const toast = useToast();
const { showError } = useEmhareConfirm();
const academicPeriodContext = useAcademicPeriodContext();
const register = ref<ExamSetupRegister>({
  venueTypes: [],
  venues: [],
  sessions: [],
  requirements: [],
});
const academic = ref<AcademicSetupOverview | null>(null);
const loading = ref(false);
const saving = ref(false);
const activeDataset = ref<
  "sessions" | "venues" | "venue-types" | "requirements"
>("sessions");
const modal = ref<
  | "venueType"
  | "venue"
  | "session"
  | "slot"
  | "availability"
  | "requirement"
  | null
>(null);
const selectedSession = ref<ExamSessionSummary | null>(null);
const selectedVenue = ref<ExamVenueSummary | null>(null);
const venueTypeForm = reactive({ code: "", name: "", description: "" });
const venueForm = reactive({
  venueTypeId: "",
  code: "",
  name: "",
  campusName: "",
  buildingName: "",
  roomName: "",
  examinationCapacity: 1,
  accessibilityNotes: "",
});
const sessionForm = reactive({
  academicPeriodId: "",
  code: "",
  name: "",
  assessmentType: "FINAL_EXAM",
  startsOn: "",
  endsOn: "",
});
const slotForm = reactive({ code: "", startsAt: "", endsAt: "" });
const availabilityForm = reactive({
  availableFrom: "",
  availableUntil: "",
  notes: "",
});
const requirementForm = reactive({
  academicPeriodId: "",
  moduleId: "",
  durationMinutes: 180,
  readingTimeMinutes: 15,
  requiredVenueTypeId: "",
  specialRequirements: "",
});

const openPeriods = computed(
  () =>
    academic.value?.academicPeriods.filter((item) => item.status === "OPEN" && academicPeriodContext.matchesAcademicPeriod(item)) ??
    [],
);
const periodItems = computed(() =>
  openPeriods.value.map((item) => ({
    label: `${item.code} · ${item.name}`,
    value: item.id,
  })),
);
const moduleItems = computed(() =>
  (academic.value?.modules ?? [])
    .filter((item) => item.status === "ACTIVE")
    .map((item) => ({ label: `${item.code} · ${item.name}`, value: item.id })),
);
const venueTypeItems = computed(() =>
  register.value.venueTypes
    .filter((item) => item.active)
    .map((item) => ({ label: `${item.code} · ${item.name}`, value: item.id })),
);
const approvedSessions = computed(
  () =>
    register.value.sessions.filter((item) => item.status === "APPROVED").length,
);
const approvedRequirements = computed(
  () =>
    register.value.requirements.filter((item) => item.status === "APPROVED")
      .length,
);
const availableCapacity = computed(() =>
  register.value.venues.reduce(
    (sum, item) => sum + item.examinationCapacity,
    0,
  ),
);
const datasetTabs = computed(() => [
  {
    label: "Sessions",
    value: "sessions",
    icon: "i-lucide-calendar-days",
    badge: register.value.sessions.length,
  },
  {
    label: "Venues",
    value: "venues",
    icon: "i-lucide-building-2",
    badge: register.value.venues.length,
  },
  {
    label: "Venue types",
    value: "venue-types",
    icon: "i-lucide-tags",
    badge: register.value.venueTypes.length,
  },
  {
    label: "Module requirements",
    value: "requirements",
    icon: "i-lucide-file-check-2",
    badge: register.value.requirements.length,
  },
]);
onMounted(load);
watch(academicPeriodContext.selectedAcademicPeriodId, () => void load());

async function load() {
  loading.value = true;
  try {
    const [registerResponse, academicResponse] = await Promise.all([
      api.request<ExamSetupRegister>("/api/exams/setup"),
      api.request<AcademicSetupOverview>("/api/academic/overview"),
    ]);
    register.value = {
      ...registerResponse,
      sessions: registerResponse.sessions.filter(session => academicPeriodContext.matchesAcademicPeriod(session)),
      requirements: registerResponse.requirements.filter(requirement => academicPeriodContext.matchesAcademicPeriod(requirement))
    };
    academic.value = academicResponse;
  } catch (error) {
    await showError("Exam setup could not be loaded", api.errorMessage(error));
  } finally {
    loading.value = false;
  }
}
async function save(
  path: string,
  body: Record<string, unknown>,
  success: string,
) {
  saving.value = true;
  try {
    await api.request(path, { method: "POST", body });
    modal.value = null;
    await load();
    toast.add({ title: success, color: "success" });
  } catch (error) {
    await showError(
      `${success} could not be completed`,
      api.errorMessage(error),
    );
  } finally {
    saving.value = false;
  }
}
function selectedPeriod(id: string) {
  return openPeriods.value.find((item) => item.id === id);
}
function selectedModule(id: string) {
  return academic.value?.modules.find((item) => item.id === id);
}
async function createVenueType() {
  await save(
    "/api/exams/setup/venue-types",
    venueTypeForm,
    "Venue type created",
  );
}
async function createVenue() {
  await save("/api/exams/setup/venues", venueForm, "Exam venue created");
}
async function createSession() {
  const period = selectedPeriod(sessionForm.academicPeriodId);
  if (!period)
    return showError(
      "Academic period required",
      "Select an open academic period.",
    );
  await save(
    "/api/exams/setup/sessions",
    { ...sessionForm, academicPeriodCode: period.code },
    "Draft exam session created",
  );
}
async function createRequirement() {
  const module = selectedModule(requirementForm.moduleId);
  if (!module) return showError("Module required", "Select an active Module.");
  await save(
    "/api/exams/setup/requirements",
    {
      ...requirementForm,
      moduleCode: module.code,
      moduleName: module.name,
      requiredVenueTypeId: requirementForm.requiredVenueTypeId || null,
    },
    "Draft Module requirement created",
  );
}
async function addSlot() {
  if (selectedSession.value)
    await save(
      `/api/exams/setup/sessions/${selectedSession.value.id}/slots`,
      {
        code: slotForm.code,
        startsAt: new Date(slotForm.startsAt).toISOString(),
        endsAt: new Date(slotForm.endsAt).toISOString(),
      },
      "Exam slot added",
    );
}
async function addAvailability() {
  if (selectedVenue.value)
    await save(
      `/api/exams/setup/venues/${selectedVenue.value.id}/availability`,
      {
        availableFrom: new Date(availabilityForm.availableFrom).toISOString(),
        availableUntil: new Date(availabilityForm.availableUntil).toISOString(),
        notes: availabilityForm.notes,
      },
      "Venue availability added",
    );
}
function openSlot(session: ExamSessionSummary) {
  selectedSession.value = session;
  Object.assign(slotForm, {
    code: "",
    startsAt: `${session.startsOn}T08:00`,
    endsAt: `${session.startsOn}T12:00`,
  });
  modal.value = "slot";
}
function openAvailability(venue: ExamVenueSummary) {
  selectedVenue.value = venue;
  Object.assign(availabilityForm, {
    availableFrom: "",
    availableUntil: "",
    notes: "",
  });
  modal.value = "availability";
}
async function approve(
  kind: "sessions" | "requirements",
  item: ExamSessionSummary | ExamRequirementSummary,
) {
  const result = await Swal.fire({
    title:
      kind === "sessions"
        ? "Approve exam session?"
        : "Approve Module exam requirement?",
    text:
      kind === "sessions"
        ? "Approval locks the session window and slot plan for timetable generation."
        : "Approval creates the authoritative duration and venue requirement version.",
    input: "textarea",
    inputLabel: "Approval reason",
    icon: "question",
    showCancelButton: true,
    confirmButtonText: "Record approval",
    confirmButtonColor: "#006633",
    inputValidator: (value) =>
      value.trim() ? undefined : "An approval reason is required.",
  });
  if (!result.isConfirmed || !result.value?.trim()) return;
  await save(
    `/api/exams/setup/${kind}/${item.id}/approve`,
    { reason: result.value.trim(), expectedVersion: item.version },
    "Approval recorded",
  );
}
function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-ZW", { dateStyle: "medium" }).format(
    new Date(`${value}T00:00:00`),
  );
}
function formatTime(value: string) {
  return new Intl.DateTimeFormat("en-ZW", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}
</script>

<template>
  <UDashboardPanel>
    <template #header
      ><UDashboardNavbar title="Exam setup"
        ><template #leading><UDashboardSidebarCollapse /></template
        ><template #right
          ><UButton
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="outline"
            label="Refresh"
            :loading="loading"
            @click="load" /></template></UDashboardNavbar
    ></template>
    <template #body
      ><div class="space-y-5 p-4 sm:p-6">
        <UAlert
          color="primary"
          variant="soft"
          icon="i-lucide-shield-check"
          title="Approved inputs before scheduling"
          description="Define certified venue capacity and availability, bounded session slots, and versioned Module duration requirements. Timetable generation cannot use drafts or undocumented capacity."
        />
        <section class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <UCard :ui="{ body: 'p-4' }"
            ><p class="text-xs uppercase text-muted">Certified venues</p>
            <p class="mt-2 text-2xl font-semibold">
              {{ register.venues.length }}
            </p></UCard
          ><UCard :ui="{ body: 'p-4' }"
            ><p class="text-xs uppercase text-muted">Exam capacity</p>
            <p class="mt-2 text-2xl font-semibold">
              {{ availableCapacity }}
            </p></UCard
          ><UCard :ui="{ body: 'p-4' }"
            ><p class="text-xs uppercase text-success">Approved sessions</p>
            <p class="mt-2 text-2xl font-semibold">
              {{ approvedSessions }}
            </p></UCard
          ><UCard :ui="{ body: 'p-4' }"
            ><p class="text-xs uppercase text-success">Approved requirements</p>
            <p class="mt-2 text-2xl font-semibold">
              {{ approvedRequirements }}
            </p></UCard
          >
        </section>
        <div class="flex flex-wrap items-center justify-between gap-3">
          <UTabs
            v-model="activeDataset"
            :items="datasetTabs"
            value-key="value"
            class="min-w-0 flex-1"
          />
          <UButton
            v-if="activeDataset === 'sessions'"
            label="New exam session"
            icon="i-lucide-calendar-plus"
            @click="modal = 'session'"
          />
          <EmhareGuidedActionButton
            v-else-if="activeDataset === 'venues'"
            label="New venue"
            icon="i-lucide-building-2"
            guidance-title="Exam venue setup required"
            :guidance-instructions="register.venueTypes.length ? [] : ['Create an active exam venue type before creating a venue.']"
            @click="modal = 'venue'"
          />
          <UButton
            v-else-if="activeDataset === 'venue-types'"
            label="New venue type"
            icon="i-lucide-tags"
            @click="modal = 'venueType'"
          />
          <UButton
            v-else
            label="New Module requirement"
            icon="i-lucide-file-plus-2"
            @click="modal = 'requirement'"
          />
        </div>
        <EmhareRegisterPanel
          v-if="activeDataset === 'venue-types'"
          title="Venue types"
          description="Govern reusable examination venue classifications before certifying individual venues."
          :count="register.venueTypes.length"
        >
          <EmharePaginatedCollection
            v-slot="{ items: paginatedVenueTypes }"
            :items="register.venueTypes"
          >
            <div class="overflow-x-auto">
              <table class="w-full min-w-[720px] text-left text-sm">
                <thead class="bg-muted/40 text-xs uppercase text-muted">
                  <tr>
                    <th class="px-4 py-3">Code</th>
                    <th class="px-4 py-3">Name</th>
                    <th class="px-4 py-3">Description</th>
                    <th class="px-4 py-3">Status</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="venueType in paginatedVenueTypes"
                    :key="venueType.id"
                    class="border-t border-muted"
                  >
                    <td class="px-4 py-3 font-mono text-xs text-primary">
                      {{ venueType.code }}
                    </td>
                    <td class="px-4 py-3 font-medium">{{ venueType.name }}</td>
                    <td class="px-4 py-3 text-muted">
                      {{ venueType.description || "No description" }}
                    </td>
                    <td class="px-4 py-3">
                      <UBadge
                        :label="venueType.active ? 'ACTIVE' : 'INACTIVE'"
                        :color="venueType.active ? 'success' : 'neutral'"
                        variant="subtle"
                      />
                    </td>
                  </tr>
                  <tr v-if="!register.venueTypes.length">
                    <td colspan="4" class="px-4 py-8 text-center text-muted">
                      No venue types configured.
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </EmharePaginatedCollection>
        </EmhareRegisterPanel>
        <div
          v-else-if="activeDataset === 'sessions' || activeDataset === 'venues'"
        >
          <section v-if="activeDataset === 'sessions'" class="space-y-3">
            <div class="flex items-center justify-between">
              <h2 class="text-base font-semibold">Sessions and slots</h2>
              <span class="text-xs text-muted"
                >{{ register.sessions.length }} records</span
              >
            </div>
            <EmharePaginatedCollection
              v-slot="{ items: paginatedSessions }"
              :items="register.sessions"
            >
              <div class="space-y-3">
                <UCard
                  v-for="session in paginatedSessions"
                  :key="session.id"
                  :ui="{ body: 'p-4' }"
                  ><div class="flex items-start justify-between gap-3">
                    <div>
                      <p class="text-xs font-medium text-primary">
                        {{ session.academicPeriodCode }} · {{ session.code }}
                      </p>
                      <h3 class="mt-1 font-semibold">{{ session.name }}</h3>
                      <p class="mt-1 text-sm text-muted">
                        {{ formatDate(session.startsOn) }} –
                        {{ formatDate(session.endsOn) }} ·
                        {{ session.assessmentType.replaceAll("_", " ") }}
                      </p>
                    </div>
                    <UBadge
                      :label="session.status"
                      :color="
                        session.status === 'APPROVED' ? 'success' : 'warning'
                      "
                      variant="subtle"
                    />
                  </div>
                  <EmharePaginatedCollection :items="session.slots" :initial-page-size="5" v-slot="{ items: paginatedSlots }">
                  <div class="mt-3 space-y-2">
                    <div
                      v-for="slot in paginatedSlots"
                      :key="slot.id"
                      class="flex items-center justify-between rounded-md border border-muted px-3 py-2 text-sm"
                    >
                      <span class="font-medium">{{ slot.code }}</span
                      ><span class="text-muted"
                        >{{ formatTime(slot.startsAt) }} –
                        {{ formatTime(slot.endsAt) }}</span
                      >
                    </div>
                  </div>
                  </EmharePaginatedCollection>
                  <div
                    v-if="session.status === 'DRAFT'"
                    class="mt-4 flex gap-2"
                  >
                    <UButton
                      label="Add slot"
                      icon="i-lucide-clock-3"
                      color="neutral"
                      variant="outline"
                      @click="openSlot(session)"
                    /><EmhareGuidedActionButton
                      label="Approve session"
                      icon="i-lucide-badge-check"
                      guidance-title="Exam session cannot be approved yet"
                      :guidance-instructions="session.slots.length ? [] : ['Add at least one exam slot before approving this session.']"
                      @click="approve('sessions', session)"
                    /></div
                ></UCard>
              </div>
            </EmharePaginatedCollection>
            <UAlert
              v-if="!loading && !register.sessions.length"
              color="neutral"
              variant="soft"
              title="No exam sessions"
              description="Create a bounded session, add its operational slots, then record approval."
            />
          </section>
          <section v-else class="space-y-3">
            <div class="flex items-center justify-between">
              <h2 class="text-base font-semibold">Venues and availability</h2>
              <span class="text-xs text-muted"
                >{{ register.venues.length }} records</span
              >
            </div>
            <EmharePaginatedCollection
              v-slot="{ items: paginatedVenues }"
              :items="register.venues"
            >
              <div class="space-y-3">
                <UCard
                  v-for="venue in paginatedVenues"
                  :key="venue.id"
                  :ui="{ body: 'p-4' }"
                  ><div class="flex items-start justify-between gap-3">
                    <div>
                      <p class="text-xs font-medium text-primary">
                        {{ venue.code }} · {{ venue.venueTypeCode }}
                      </p>
                      <h3 class="mt-1 font-semibold">{{ venue.name }}</h3>
                      <p class="mt-1 text-sm text-muted">
                        {{ venue.campusName }} · certified capacity
                        {{ venue.examinationCapacity }}
                      </p>
                    </div>
                    <UBadge
                      :label="venue.active ? 'ACTIVE' : 'INACTIVE'"
                      :color="venue.active ? 'success' : 'neutral'"
                      variant="subtle"
                    />
                  </div>
                  <EmharePaginatedCollection :items="venue.availability" :initial-page-size="5" v-slot="{ items: paginatedAvailabilityWindows }">
                  <div class="mt-3 flex flex-wrap gap-2">
                    <UBadge
                      v-for="window in paginatedAvailabilityWindows"
                      :key="window.id"
                      :label="`${formatTime(window.availableFrom)} – ${formatTime(window.availableUntil)}`"
                      color="neutral"
                      variant="outline"
                    />
                  </div>
                  </EmharePaginatedCollection>
                  <UButton
                    class="mt-3"
                    label="Add availability"
                    icon="i-lucide-calendar-range"
                    size="sm"
                    color="neutral"
                    variant="outline"
                    @click="openAvailability(venue)"
                /></UCard>
              </div>
            </EmharePaginatedCollection>
            <UAlert
              v-if="!loading && !register.venues.length"
              color="neutral"
              variant="soft"
              title="No certified venues"
              description="Create a venue type, venue capacity record, and availability window."
            />
          </section>
        </div>
        <section v-else class="space-y-3">
          <h2 class="text-base font-semibold">
            Versioned Module exam requirements
          </h2>
          <EmharePaginatedCollection
            v-slot="{ items: paginatedRequirements }"
            :items="register.requirements"
          >
            <div class="grid gap-3 lg:grid-cols-2">
              <UCard
                v-for="requirement in paginatedRequirements"
                :key="requirement.id"
                :ui="{ body: 'p-4' }"
                ><div class="flex items-start justify-between gap-3">
                  <div>
                    <p class="text-xs font-medium text-primary">
                      {{ requirement.moduleCode }} · version
                      {{ requirement.requirementVersion }}
                    </p>
                    <h3 class="mt-1 font-semibold">
                      {{ requirement.moduleName }}
                    </h3>
                    <p class="mt-1 text-sm text-muted">
                      {{ requirement.durationMinutes }} min +
                      {{ requirement.readingTimeMinutes }} min reading ·
                      {{
                        requirement.requiredVenueTypeCode ||
                        "Any certified venue"
                      }}
                    </p>
                  </div>
                  <UBadge
                    :label="requirement.status"
                    :color="
                      requirement.status === 'APPROVED'
                        ? 'success'
                        : requirement.status === 'DRAFT'
                          ? 'warning'
                          : 'neutral'
                    "
                    variant="subtle"
                  />
                </div>
                <UButton
                  v-if="requirement.status === 'DRAFT'"
                  class="mt-4"
                  label="Approve requirement"
                  icon="i-lucide-badge-check"
                  @click="approve('requirements', requirement)"
              /></UCard>
            </div>
          </EmharePaginatedCollection>
        </section></div
    ></template>
  </UDashboardPanel>

  <EmhareRecordDrawer
    :open="modal === 'venueType'"
    title="Create venue type"
    @update:open="
      (value) => {
        if (!value) modal = null;
      }
    "
    ><template #body
      ><div class="space-y-4">
        <UFormField label="Code" required
          ><UInput v-model="venueTypeForm.code" class="w-full" /></UFormField
        ><UFormField label="Name" required
          ><UInput v-model="venueTypeForm.name" class="w-full" /></UFormField
        ><UFormField label="Description"
          ><UTextarea v-model="venueTypeForm.description" class="w-full"
        /></UFormField></div></template
    ><template #footer
      ><UButton
        label="Create venue type"
        :loading="saving"
        @click="createVenueType" /></template
  ></EmhareRecordDrawer>
  <EmhareRecordDrawer
    :open="modal === 'venue'"
    title="Create certified exam venue"
    @update:open="
      (value) => {
        if (!value) modal = null;
      }
    "
    ><template #body
      ><div class="grid gap-4 sm:grid-cols-2">
        <UFormField label="Venue type" required
          ><USelect
            v-model="venueForm.venueTypeId"
            :items="venueTypeItems"
            class="w-full" /></UFormField
        ><UFormField label="Code" required
          ><UInput v-model="venueForm.code" class="w-full" /></UFormField
        ><UFormField label="Name" required
          ><UInput v-model="venueForm.name" class="w-full" /></UFormField
        ><UFormField label="Campus" required
          ><UInput v-model="venueForm.campusName" class="w-full" /></UFormField
        ><UFormField label="Building"
          ><UInput v-model="venueForm.buildingName" /></UFormField
        ><UFormField label="Room"
          ><UInput v-model="venueForm.roomName" /></UFormField
        ><UFormField label="Certified exam capacity" required
          ><UInput
            v-model.number="venueForm.examinationCapacity"
            type="number"
            min="1" /></UFormField
        ><UFormField label="Accessibility notes"
          ><UInput v-model="venueForm.accessibilityNotes"
        /></UFormField></div></template
    ><template #footer
      ><UButton
        label="Create venue"
        :loading="saving"
        @click="createVenue" /></template
  ></EmhareRecordDrawer>
  <EmhareRecordDrawer
    :open="modal === 'session'"
    title="Create exam session"
    @update:open="
      (value) => {
        if (!value) modal = null;
      }
    "
    ><template #body
      ><div class="space-y-4">
        <UFormField label="Open academic period" required
          ><USelect
            v-model="sessionForm.academicPeriodId"
            :items="periodItems"
            class="w-full"
        /></UFormField>
        <div class="grid gap-4 sm:grid-cols-2">
          <UFormField label="Code" required
            ><UInput v-model="sessionForm.code" /></UFormField
          ><UFormField label="Name" required
            ><UInput v-model="sessionForm.name" /></UFormField
          ><UFormField label="Assessment type"
            ><USelect
              v-model="sessionForm.assessmentType"
              :items="[
                'FINAL_EXAM',
                'SUPPLEMENTARY',
                'DEFERRED',
                'SPECIAL',
              ]" /></UFormField
          ><span /><UFormField label="Starts on"
            ><UInput v-model="sessionForm.startsOn" type="date" /></UFormField
          ><UFormField label="Ends on"
            ><UInput v-model="sessionForm.endsOn" type="date"
          /></UFormField>
        </div></div></template
    ><template #footer
      ><UButton
        label="Create draft session"
        :loading="saving"
        @click="createSession" /></template
  ></EmhareRecordDrawer>
  <EmhareRecordDrawer
    :open="modal === 'slot'"
    title="Add session slot"
    @update:open="
      (value) => {
        if (!value) modal = null;
      }
    "
    ><template #body
      ><div class="space-y-4">
        <UFormField label="Slot code" required
          ><UInput v-model="slotForm.code" /></UFormField
        ><UFormField label="Starts at" required
          ><UInput
            v-model="slotForm.startsAt"
            type="datetime-local" /></UFormField
        ><UFormField label="Ends at" required
          ><UInput v-model="slotForm.endsAt" type="datetime-local"
        /></UFormField></div></template
    ><template #footer
      ><UButton label="Add slot" :loading="saving" @click="addSlot" /></template
  ></EmhareRecordDrawer>
  <EmhareRecordDrawer
    :open="modal === 'availability'"
    title="Add venue availability"
    @update:open="
      (value) => {
        if (!value) modal = null;
      }
    "
    ><template #body
      ><div class="space-y-4">
        <UFormField label="Available from" required
          ><UInput
            v-model="availabilityForm.availableFrom"
            type="datetime-local" /></UFormField
        ><UFormField label="Available until" required
          ><UInput
            v-model="availabilityForm.availableUntil"
            type="datetime-local" /></UFormField
        ><UFormField label="Notes"
          ><UTextarea v-model="availabilityForm.notes"
        /></UFormField></div></template
    ><template #footer
      ><UButton
        label="Add availability"
        :loading="saving"
        @click="addAvailability" /></template
  ></EmhareRecordDrawer>
  <EmhareRecordDrawer
    :open="modal === 'requirement'"
    title="Create Module exam requirement"
    @update:open="
      (value) => {
        if (!value) modal = null;
      }
    "
    ><template #body
      ><div class="space-y-4">
        <UFormField label="Academic period" required
          ><USelect
            v-model="requirementForm.academicPeriodId"
            :items="periodItems"
            class="w-full" /></UFormField
        ><UFormField label="Module" required
          ><USelect
            v-model="requirementForm.moduleId"
            :items="moduleItems"
            searchable
            class="w-full"
        /></UFormField>
        <div class="grid gap-4 sm:grid-cols-2">
          <UFormField label="Writing minutes"
            ><UInput
              v-model.number="requirementForm.durationMinutes"
              type="number"
              min="15"
              max="480" /></UFormField
          ><UFormField label="Reading minutes"
            ><UInput
              v-model.number="requirementForm.readingTimeMinutes"
              type="number"
              min="0"
              max="120"
          /></UFormField>
        </div>
        <UFormField label="Required venue type"
          ><USelect
            v-model="requirementForm.requiredVenueTypeId"
            :items="venueTypeItems"
            class="w-full" /></UFormField
        ><UFormField label="Special requirements"
          ><UTextarea v-model="requirementForm.specialRequirements"
        /></UFormField></div></template
    ><template #footer
      ><UButton
        label="Create draft requirement"
        :loading="saving"
        @click="createRequirement" /></template
  ></EmhareRecordDrawer>
</template>
