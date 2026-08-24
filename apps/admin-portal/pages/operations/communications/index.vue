<script setup lang="ts">
import type {
  CommunicationCategory,
  CommunicationContentKind,
  CommunicationMediaAsset,
  EditorialCommunicationDetail,
  EditorialCommunicationItem,
  EditorialCommunicationPage,
  EventAttendanceMode,
  StructuredContentBlock,
} from "@emhare/portal-shell/types/communications";
import { communicationSlugPreview } from "@emhare/portal-shell/utils/communication-slug";

definePageMeta({ layout: "dashboard" });

const api = useEmhareApi();
const publicCommunications = usePublicCommunications();
const toast = useToast();
const { confirmAction, showError } = useEmhareConfirm();
const loading = ref(false);
const saving = ref(false);
const search = ref("");
const kindFilter = ref<CommunicationContentKind | undefined>();
const currentPage = ref(1);
const queue = ref<EditorialCommunicationPage>({
  items: [],
  page: 0,
  size: 20,
  totalItems: 0,
  totalPages: 0,
});
const categories = ref<CommunicationCategory[]>([]);
const selected = ref<EditorialCommunicationDetail | null>(null);
const creating = ref(false);
const decisionReason = ref("");
const publishFrom = ref("");
const publishUntil = ref("");
const heroImageFile = ref<File | null>(null);
const heroAlternativeText = ref("");
const uploadingHeroImage = ref(false);

const form = reactive({
  kind: "NEWS" as CommunicationContentKind,
  slug: "",
  categoryId: "",
  title: "",
  summary: "",
  structuredContent: [{ type: "PARAGRAPH", text: "" }] as StructuredContentBlock[],
  heroMediaAssetId: "",
  externalUrl: "",
  eventStartsAt: "",
  eventEndsAt: "",
  eventTimezone: "Africa/Harare",
  attendanceMode: "IN_PERSON" as EventAttendanceMode,
  venueName: "",
  address: "",
  onlineUrl: "",
});

const kindItems: Array<{ label: string; value: CommunicationContentKind }> = [
  { label: "News", value: "NEWS" },
  { label: "Notice", value: "NOTICE" },
  { label: "Alert", value: "ALERT" },
  { label: "Campaign", value: "CAMPAIGN" },
  { label: "Important link", value: "LINK" },
  { label: "Event", value: "EVENT" },
];

const categoryItems = computed(() =>
  categories.value
    .filter((category) => category.active)
    .map((category) => ({ label: category.name, value: category.id })),
);
const parsedBlocks = computed<StructuredContentBlock[]>(() => form.structuredContent);
const canEdit = computed(
  () => creating.value || ["DRAFT", "REJECTED"].includes(selected.value?.item.workflowStatus ?? ""),
);
const supportsHeroImage = computed(() => form.kind !== "LINK");
const generatedSlug = computed(() => form.slug || communicationSlugPreview(form.title));
const publicRoutePreview = computed(() => {
  if (form.kind === "LINK") return `Internal key: ${generatedSlug.value}`;
  const routePrefix =
    form.kind === "NEWS"
      ? "news"
      : form.kind === "EVENT"
        ? "events"
        : form.kind === "CAMPAIGN"
          ? "campaigns"
          : "notices";
  return `/${routePrefix}/${generatedSlug.value}`;
});
const heroImageUrl = computed(() =>
  form.heroMediaAssetId ? publicCommunications.mediaUrl(form.heroMediaAssetId) : "",
);

onMounted(load);

async function load(page = currentPage.value - 1) {
  loading.value = true;
  try {
    const query = new URLSearchParams({ page: String(page), size: "10" });
    if (search.value.trim()) query.set("query", search.value.trim());
    if (kindFilter.value) query.set("kind", kindFilter.value);
    const [queueResult, categoryResult] = await Promise.all([
      api.request<EditorialCommunicationPage>(`/api/communications/editorial/items?${query}`),
      api.request<CommunicationCategory[]>("/api/communications/editorial/categories"),
    ]);
    queue.value = queueResult;
    currentPage.value = queueResult.page + 1;
    categories.value = categoryResult;
  } catch (error) {
    await showError("Communications could not be loaded", api.errorMessage(error));
  } finally {
    loading.value = false;
  }
}

async function applyFilters() {
  currentPage.value = 1;
  await load(0);
}

async function changePage(page: number) {
  currentPage.value = page;
  await load(page - 1);
}

function startDraft() {
  creating.value = true;
  selected.value = null;
  decisionReason.value = "";
  publishFrom.value = toLocalDateTime(new Date());
  publishUntil.value = "";
  Object.assign(form, {
    kind: "NEWS",
    slug: "",
    categoryId: "",
    title: "",
    summary: "",
    structuredContent: [{ type: "PARAGRAPH", text: "" }],
    heroMediaAssetId: "",
    externalUrl: "",
    eventStartsAt: "",
    eventEndsAt: "",
    eventTimezone: "Africa/Harare",
    attendanceMode: "IN_PERSON",
    venueName: "",
    address: "",
    onlineUrl: "",
  });
  heroImageFile.value = null;
  heroAlternativeText.value = "";
}

function closeEditor() {
  creating.value = false;
  selected.value = null;
}

async function openItem(item: EditorialCommunicationItem) {
  loading.value = true;
  try {
    const detail = await api.request<EditorialCommunicationDetail>(
      `/api/communications/editorial/items/${item.itemId}`,
    );
    selected.value = detail;
    creating.value = false;
    decisionReason.value = "";
    publishFrom.value = toLocalDateTime(new Date());
    publishUntil.value = "";
    Object.assign(form, {
      kind: detail.item.kind,
      slug: detail.item.slug,
      categoryId: detail.categoryId ?? "",
      title: detail.item.title,
      summary: detail.item.summary,
      structuredContent: structuredClone(detail.structuredContent),
      heroMediaAssetId: detail.heroMediaAssetId ?? "",
      externalUrl: detail.externalUrl ?? "",
      eventStartsAt: detail.event ? toLocalDateTime(new Date(detail.event.startsAt)) : "",
      eventEndsAt: detail.event ? toLocalDateTime(new Date(detail.event.endsAt)) : "",
      eventTimezone: detail.event?.timezone ?? "Africa/Harare",
      attendanceMode: detail.event?.attendanceMode ?? "IN_PERSON",
      venueName: detail.event?.venueName ?? "",
      address: detail.event?.address ?? "",
      onlineUrl: detail.event?.onlineUrl ?? "",
    });
    heroImageFile.value = null;
    heroAlternativeText.value = detail.item.title;
  } catch (error) {
    await showError("Content item could not be opened", api.errorMessage(error));
  } finally {
    loading.value = false;
  }
}

async function save() {
  saving.value = true;
  try {
    const structuredContent = form.structuredContent.map((block) => normalizeBlock(block));
    const body = {
      kind: form.kind,
      slug: creating.value ? null : form.slug,
      categoryId: form.categoryId || null,
      title: form.title,
      summary: form.summary,
      structuredContent,
      heroMediaAssetId: form.heroMediaAssetId || null,
      externalUrl: form.externalUrl || null,
      event: eventBody(),
    };
    let saved: EditorialCommunicationItem;
    if (creating.value) {
      saved = await api.request("/api/communications/editorial/items", { method: "POST", body });
    } else if (selected.value) {
      saved = await api.request(
        `/api/communications/editorial/versions/${selected.value.item.versionId}`,
        {
          method: "PUT",
          body: { ...body, expectedVersion: selected.value.item.expectedVersion },
        },
      );
    } else return;
    toast.add({ title: "Draft saved", color: "success", icon: "i-lucide-circle-check" });
    await load();
    await openItem(saved);
  } catch (error) {
    await showError(
      "Draft was not saved",
      api.errorMessage(error, "Check the required title, summary, and article blocks."),
    );
  } finally {
    saving.value = false;
  }
}

function normalizeBlock(block: StructuredContentBlock): StructuredContentBlock {
  const normalized = { ...block };
  if (Array.isArray(normalized.items)) {
    normalized.items = normalized.items
      .filter((item): item is string => typeof item === "string" && item.trim().length > 0)
      .map((item) => item.trim());
  }
  if (Array.isArray(normalized.links)) {
    normalized.links = normalized.links.filter(
      (link) =>
        link &&
        typeof link === "object" &&
        "label" in link &&
        "url" in link &&
        String(link.label).trim() &&
        String(link.url).trim(),
    );
  }
  return normalized;
}

async function uploadHeroImage() {
  if (!heroImageFile.value || !heroAlternativeText.value.trim()) {
    await showError(
      "Image details required",
      "Choose an image and describe it for people who use screen readers.",
    );
    return;
  }
  uploadingHeroImage.value = true;
  try {
    const body = new FormData();
    body.set("file", heroImageFile.value);
    body.set("alternativeText", heroAlternativeText.value.trim());
    const media = await api.request<CommunicationMediaAsset>(
      "/api/communications/editorial/media",
      { method: "POST", body },
    );
    form.heroMediaAssetId = media.id;
    heroImageFile.value = null;
    toast.add({ title: "Cover image uploaded", color: "success", icon: "i-lucide-image-up" });
  } catch (error) {
    await showError("Cover image was not uploaded", api.errorMessage(error));
  } finally {
    uploadingHeroImage.value = false;
  }
}

async function transition(action: "submit" | "approve" | "reject") {
  if (!selected.value) return;
  if (action === "reject" && !decisionReason.value.trim()) {
    await showError(
      "Rejection reason required",
      "Record clear correction guidance before rejecting this version.",
    );
    return;
  }
  const confirmed = await confirmAction({
    title:
      action === "submit"
        ? "Submit for independent review?"
        : action === "approve"
          ? "Approve this public version?"
          : "Reject this version?",
    text:
      action === "approve"
        ? "Approval makes this version immutable. Publishing remains a separate action."
        : undefined,
    confirmButtonText: action === "submit" ? "Submit" : action === "approve" ? "Approve" : "Reject",
    destructive: action === "reject",
  });
  if (!confirmed) return;
  try {
    const body =
      action === "reject"
        ? {
            expectedVersion: selected.value.item.expectedVersion,
            reason: decisionReason.value.trim(),
          }
        : { expectedVersion: selected.value.item.expectedVersion };
    const updated = await api.request<EditorialCommunicationItem>(
      `/api/communications/editorial/versions/${selected.value.item.versionId}/${action}`,
      { method: "POST", body },
    );
    await load();
    await openItem(updated);
  } catch (error) {
    await showError("Workflow action failed", api.errorMessage(error));
  }
}

async function schedule() {
  if (!selected.value || !publishFrom.value) return;
  const confirmed = await confirmAction({
    title: "Publish this approved version?",
    text: "The configured window controls when it appears on the one public gateway.",
    confirmButtonText: "Schedule publication",
  });
  if (!confirmed) return;
  try {
    const updated = await api.request<EditorialCommunicationItem>(
      `/api/communications/editorial/versions/${selected.value.item.versionId}/publications`,
      {
        method: "POST",
        body: {
          publishFrom: new Date(publishFrom.value).toISOString(),
          publishUntil: publishUntil.value ? new Date(publishUntil.value).toISOString() : null,
          pinned: false,
          featured: form.kind === "CAMPAIGN",
          displayOrder: 0,
        },
      },
    );
    await load();
    await openItem(updated);
  } catch (error) {
    await showError("Publication could not be scheduled", api.errorMessage(error));
  }
}

async function withdraw() {
  if (!selected.value?.item.publicationId || !decisionReason.value.trim()) {
    await showError("Withdrawal reason required", "Record why the public item must be withdrawn.");
    return;
  }
  const confirmed = await confirmAction({
    title: "Withdraw this public item?",
    text: "It will stop appearing on the gateway immediately.",
    confirmButtonText: "Withdraw",
    destructive: true,
  });
  if (!confirmed) return;
  try {
    await api.request(
      `/api/communications/editorial/publications/${selected.value.item.publicationId}/withdraw`,
      {
        method: "POST",
        body: {
          expectedVersion: selected.value.item.publicationExpectedVersion ?? 0,
          reason: decisionReason.value.trim(),
        },
      },
    );
    await load();
    await openItem(selected.value.item);
  } catch (error) {
    await showError("Publication could not be withdrawn", api.errorMessage(error));
  }
}

function eventBody() {
  if (form.kind !== "EVENT") return null;
  return {
    startsAt: new Date(form.eventStartsAt).toISOString(),
    endsAt: new Date(form.eventEndsAt).toISOString(),
    timezone: form.eventTimezone,
    attendanceMode: form.attendanceMode,
    venueName: form.venueName || null,
    address: form.address || null,
    onlineUrl: form.onlineUrl || null,
  };
}

function toLocalDateTime(date: Date) {
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function statusTone(status: string) {
  return status === "APPROVED" || status === "LIVE"
    ? "success"
    : status === "REJECTED" || status === "WITHDRAWN"
      ? "error"
      : "warning";
}
</script>

<template>
  <UDashboardPanel id="communications">
    <template #header>
      <UDashboardNavbar title="Communications">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right
          ><UButton label="New public item" icon="i-lucide-plus" @click="startDraft"
        /></template>
      </UDashboardNavbar>
    </template>
    <template #body>
      <div class="space-y-5">
        <template v-if="!creating && !selected">
          <UAlert
            color="primary"
            variant="soft"
            icon="i-lucide-shield-check"
            title="One governed public gateway"
            description="Create and manage the news, notices, campaigns, links, and events shown on the public eMhare gateway."
          />
          <section class="overflow-hidden rounded-2xl border border-default bg-default">
            <div class="border-b border-default p-5">
              <div class="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
                <div>
                  <p class="text-xs font-extrabold uppercase tracking-[0.16em] text-uzazure-700">
                    Public content
                  </p>
                  <h2 class="mt-1 text-xl font-bold text-highlighted">Articles and updates</h2>
                  <p class="mt-1 text-sm text-muted">
                    {{ queue.totalItems }} item{{ queue.totalItems === 1 ? "" : "s" }} across all
                    publication stages
                  </p>
                </div>
                <div class="grid gap-2 sm:grid-cols-[minmax(14rem,1fr)_12rem_auto]">
                  <UInput
                    v-model="search"
                    icon="i-lucide-search"
                    placeholder="Search title or URL"
                    @keyup.enter="applyFilters"
                  />
                  <USelect
                    v-model="kindFilter"
                    :items="[{ label: 'All content', value: undefined }, ...kindItems]"
                    value-key="value"
                  />
                  <UButton
                    label="Apply"
                    icon="i-lucide-filter"
                    color="neutral"
                    variant="outline"
                    :loading="loading"
                    @click="applyFilters"
                  />
                </div>
              </div>
            </div>

            <div v-if="loading" class="grid gap-3 p-5">
              <USkeleton v-for="index in 5" :key="index" class="h-16 w-full" />
            </div>
            <div v-else-if="queue.items.length" class="overflow-x-auto">
              <table class="w-full min-w-[48rem] text-left text-sm">
                <thead class="bg-elevated text-xs font-bold uppercase tracking-wide text-muted">
                  <tr>
                    <th class="px-5 py-3">Title</th>
                    <th class="px-5 py-3">Placement</th>
                    <th class="px-5 py-3">Workflow</th>
                    <th class="px-5 py-3">Publication</th>
                    <th class="px-5 py-3 text-right">Action</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-default">
                  <tr v-for="item in queue.items" :key="item.itemId" class="hover:bg-uzazure-50/60">
                    <td class="px-5 py-4">
                      <p class="font-semibold text-highlighted">{{ item.title }}</p>
                      <p class="mt-1 text-xs text-muted">
                        /{{ item.slug }} · version {{ item.versionNumber }}
                      </p>
                    </td>
                    <td class="px-5 py-4 text-muted">
                      {{ kindItems.find((kind) => kind.value === item.kind)?.label }}
                    </td>
                    <td class="px-5 py-4">
                      <EmhareStatusPill
                        :label="item.workflowStatus.replace('_', ' ')"
                        :tone="statusTone(item.workflowStatus)"
                      />
                    </td>
                    <td class="px-5 py-4">
                      <EmhareStatusPill
                        v-if="item.publicationStatus"
                        :label="item.publicationStatus"
                        :tone="statusTone(item.publicationStatus)"
                      />
                      <span v-else class="text-xs text-muted">Not published</span>
                    </td>
                    <td class="px-5 py-4 text-right">
                      <UButton
                        label="Open editor"
                        icon="i-lucide-pencil-line"
                        color="neutral"
                        variant="outline"
                        size="sm"
                        @click="openItem(item)"
                      />
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-else class="grid place-items-center px-6 py-16 text-center">
              <UIcon name="i-lucide-newspaper" class="size-10 text-uzazure-700" />
              <h3 class="mt-4 font-semibold text-highlighted">No matching public content</h3>
              <p class="mt-1 text-sm text-muted">Change the filters or create the first item.</p>
            </div>

            <footer
              v-if="queue.totalPages > 1"
              class="flex flex-wrap items-center justify-between gap-3 border-t border-default px-5 py-4"
            >
              <p class="text-sm text-muted">Page {{ currentPage }} of {{ queue.totalPages }}</p>
              <div class="flex items-center gap-2">
                <UButton
                  label="Previous"
                  icon="i-lucide-chevron-left"
                  color="neutral"
                  variant="outline"
                  size="sm"
                  :disabled="currentPage === 1"
                  @click="changePage(currentPage - 1)"
                />
                <UButton
                  label="Next"
                  trailing-icon="i-lucide-chevron-right"
                  color="neutral"
                  variant="outline"
                  size="sm"
                  :disabled="currentPage === queue.totalPages"
                  @click="changePage(currentPage + 1)"
                />
              </div>
            </footer>
          </section>
        </template>

        <template v-else>
          <div class="flex flex-wrap items-center justify-between gap-3">
            <UButton
              label="Back to articles"
              icon="i-lucide-arrow-left"
              color="neutral"
              variant="ghost"
              @click="closeEditor"
            />
            <p class="text-sm text-muted">{{ publicRoutePreview }}</p>
          </div>
          <section class="grid min-w-0 gap-5 2xl:grid-cols-[minmax(0,1fr)_minmax(20rem,.72fr)]">
            <div class="rounded-2xl border border-default bg-default p-5">
              <div class="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <p class="text-xs font-bold uppercase tracking-wide text-primary">
                    {{ creating ? "New draft" : `Version ${selected?.item.versionNumber}` }}
                  </p>
                  <h2 class="mt-1 text-xl font-semibold text-highlighted">Content editor</h2>
                </div>
                <EmhareStatusPill
                  v-if="selected"
                  :label="selected.item.workflowStatus.replace('_', ' ')"
                  :tone="statusTone(selected.item.workflowStatus)"
                />
              </div>
              <div class="mt-5 grid gap-4 sm:grid-cols-2">
                <UFormField label="Content kind" required
                  ><USelect
                    v-model="form.kind"
                    :items="kindItems"
                    value-key="value"
                    :disabled="!creating"
                    class="w-full"
                /></UFormField>
                <UFormField label="Category"
                  ><USelect
                    v-model="form.categoryId"
                    :items="categoryItems"
                    value-key="value"
                    :disabled="!canEdit"
                    class="w-full"
                /></UFormField>
                <UFormField label="Title" required class="sm:col-span-2"
                  ><UInput v-model="form.title" :disabled="!canEdit" class="w-full"
                /></UFormField>
                <UFormField
                  label="Public address"
                  description="Generated automatically from the title; duplicate titles receive a numeric suffix."
                  class="sm:col-span-2"
                >
                  <div
                    class="rounded-lg border border-default bg-elevated px-3 py-2 font-mono text-sm text-muted"
                  >
                    {{ publicRoutePreview }}
                  </div>
                </UFormField>
                <UFormField label="Summary" required class="sm:col-span-2"
                  ><UTextarea v-model="form.summary" :rows="3" :disabled="!canEdit" class="w-full"
                /></UFormField>
                <UFormField
                  v-if="form.kind === 'LINK'"
                  label="Destination URL"
                  required
                  class="sm:col-span-2"
                  ><UInput v-model="form.externalUrl" :disabled="!canEdit" class="w-full"
                /></UFormField>
                <template v-if="form.kind === 'EVENT'">
                  <UFormField label="Starts" required
                    ><UInput
                      v-model="form.eventStartsAt"
                      type="datetime-local"
                      :disabled="!canEdit"
                      class="w-full" /></UFormField
                  ><UFormField label="Ends" required
                    ><UInput
                      v-model="form.eventEndsAt"
                      type="datetime-local"
                      :disabled="!canEdit"
                      class="w-full"
                  /></UFormField>
                  <UFormField label="Timezone" required
                    ><UInput
                      v-model="form.eventTimezone"
                      :disabled="!canEdit"
                      class="w-full" /></UFormField
                  ><UFormField label="Attendance mode" required
                    ><USelect
                      v-model="form.attendanceMode"
                      :items="
                        ['IN_PERSON', 'ONLINE', 'HYBRID'].map((value) => ({
                          label: value.replace('_', ' '),
                          value,
                        }))
                      "
                      value-key="value"
                      :disabled="!canEdit"
                      class="w-full"
                  /></UFormField>
                  <UFormField label="Venue"
                    ><UInput
                      v-model="form.venueName"
                      :disabled="!canEdit"
                      class="w-full" /></UFormField
                  ><UFormField label="Online URL"
                    ><UInput v-model="form.onlineUrl" :disabled="!canEdit" class="w-full"
                  /></UFormField>
                </template>
                <div
                  v-if="supportsHeroImage"
                  class="sm:col-span-2 rounded-xl border border-default p-4"
                >
                  <div class="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <h3 class="font-semibold text-highlighted">Cover image</h3>
                      <p class="mt-1 text-sm text-muted">
                        Used on the gateway card and at the top of the public article.
                      </p>
                    </div>
                    <UButton
                      v-if="form.heroMediaAssetId && canEdit"
                      label="Remove"
                      icon="i-lucide-x"
                      color="error"
                      variant="ghost"
                      size="sm"
                      @click="form.heroMediaAssetId = ''"
                    />
                  </div>
                  <img
                    v-if="heroImageUrl"
                    :src="heroImageUrl"
                    :alt="heroAlternativeText || form.title"
                    class="mt-4 max-h-72 w-full rounded-lg bg-slate-100 object-contain"
                  />
                  <div v-if="canEdit" class="mt-4 grid gap-3 sm:grid-cols-2">
                    <UFileUpload
                      v-model="heroImageFile"
                      accept="image/jpeg,image/png,image/webp"
                      label="Choose a cover image"
                      description="JPEG, PNG or WebP"
                      class="sm:col-span-2 w-full"
                    />
                    <UInput
                      v-model="heroAlternativeText"
                      placeholder="Describe the image for screen readers"
                      class="w-full"
                    />
                    <UButton
                      label="Upload cover"
                      icon="i-lucide-upload"
                      class="w-fit"
                      :loading="uploadingHeroImage"
                      :disabled="!heroImageFile"
                      @click="uploadHeroImage"
                    />
                  </div>
                </div>
              </div>

              <div class="mt-6 border-t border-default pt-6">
                <div class="mb-4">
                  <h3 class="text-lg font-semibold text-highlighted">Article body</h3>
                  <p class="mt-1 text-sm text-muted">
                    Add blocks with the buttons, write normally, then drag tiles to change their
                    order.
                  </p>
                </div>
                <EmhareStructuredContentEditor
                  v-model="form.structuredContent"
                  :disabled="!canEdit"
                />
              </div>
              <div class="mt-5 flex flex-wrap items-center gap-3 border-t border-default pt-5">
                <UButton
                  v-if="canEdit"
                  label="Save draft"
                  icon="i-lucide-save"
                  :loading="saving"
                  @click="save"
                />
                <UButton
                  v-if="selected?.item.workflowStatus === 'DRAFT'"
                  label="Submit for review"
                  icon="i-lucide-send"
                  color="neutral"
                  variant="outline"
                  @click="transition('submit')"
                />
              </div>
              <div
                v-if="selected && selected.item.workflowStatus !== 'DRAFT'"
                class="mt-5 rounded-xl bg-elevated p-4"
              >
                <UFormField label="Decision or withdrawal evidence"
                  ><UTextarea v-model="decisionReason" :rows="3" class="w-full"
                /></UFormField>
                <div class="mt-3 flex flex-wrap gap-2">
                  <UButton
                    v-if="selected.item.workflowStatus === 'IN_REVIEW'"
                    label="Approve"
                    icon="i-lucide-check"
                    @click="transition('approve')"
                  /><UButton
                    v-if="selected.item.workflowStatus === 'IN_REVIEW'"
                    label="Reject"
                    icon="i-lucide-x"
                    color="error"
                    variant="soft"
                    @click="transition('reject')"
                  />
                </div>
                <div
                  v-if="selected.item.workflowStatus === 'APPROVED' && !selected.item.publicationId"
                  class="mt-4 grid gap-3 sm:grid-cols-2"
                >
                  <UFormField label="Publish from" required
                    ><UInput
                      v-model="publishFrom"
                      type="datetime-local"
                      class="w-full" /></UFormField
                  ><UFormField label="Publish until"
                    ><UInput
                      v-model="publishUntil"
                      type="datetime-local"
                      class="w-full" /></UFormField
                  ><UButton
                    label="Schedule publication"
                    icon="i-lucide-calendar-days"
                    class="sm:col-span-2 sm:w-fit"
                    @click="schedule"
                  />
                </div>
                <UButton
                  v-if="
                    selected.item.publicationId && selected.item.publicationStatus !== 'WITHDRAWN'
                  "
                  label="Withdraw publication"
                  icon="i-lucide-x-circle"
                  color="error"
                  variant="soft"
                  class="mt-4"
                  @click="withdraw"
                />
              </div>
            </div>

            <aside class="rounded-2xl border border-default bg-[#f6f4ee] p-5">
              <p class="text-xs font-bold uppercase tracking-[0.18em] text-uzazure-700">
                Responsive public preview
              </p>
              <h2 class="mt-3 font-serif text-3xl font-semibold text-slate-950">
                {{ form.title || "Untitled public item" }}
              </h2>
              <p class="mt-3 text-sm leading-6 text-slate-600">
                {{ form.summary || "Add a concise summary for the public listing." }}
              </p>
              <img
                v-if="heroImageUrl"
                :src="heroImageUrl"
                :alt="heroAlternativeText || form.title"
                class="mt-6 max-h-80 w-full rounded-xl object-cover"
              />
              <div class="mt-6 rounded-2xl bg-white p-5">
                <EmhareStructuredContent :blocks="parsedBlocks" />
                <p v-if="!parsedBlocks.length" class="text-sm text-slate-500">
                  Add a content block to preview the article body.
                </p>
              </div>
            </aside>
          </section>
        </template>
      </div>
    </template>
  </UDashboardPanel>
</template>
