<script setup lang="ts">
import type { IdentityName, IdentityNameCorrectionSummary } from "../../../types/admissions";

const props = withDefaults(
  defineProps<{
    correction: IdentityNameCorrectionSummary;
    mode?: "applicant" | "staff";
    loading?: boolean;
    editable?: boolean;
  }>(),
  { mode: "applicant", loading: false, editable: true },
);

const emit = defineEmits<{
  replace: [];
  corrected: [name: IdentityName];
  request: [name: IdentityName];
  approve: [];
  reject: [];
}>();

const editing = ref(false);
const correctedName = reactive<IdentityName>({ firstName: "", middleNames: null, lastName: "" });

watch(
  () => props.correction.documentName,
  (name) => {
    correctedName.firstName = name.firstName;
    correctedName.middleNames = name.middleNames;
    correctedName.lastName = name.lastName;
  },
  { immediate: true, deep: true },
);

const decisionPending = computed(() => props.correction.status === "REQUESTED");
const decided = computed(() => ["APPROVED", "REJECTED"].includes(props.correction.status));
const statusLabel = computed(
  () =>
    ({
      UNRESOLVED: "Review needed",
      OCR_REVIEWED: "Review needed",
      REQUESTED: "Awaiting staff approval",
      APPROVED: "Approved",
      REJECTED: "Rejected",
    })[props.correction.status],
);

function displayName(name: IdentityName) {
  return [name.firstName, name.middleNames, name.lastName].filter(Boolean).join(" ");
}

function saveCorrectedReading() {
  if (!correctedName.firstName.trim() || !correctedName.lastName.trim()) return;
  emit("corrected", {
    firstName: correctedName.firstName.trim(),
    middleNames: correctedName.middleNames?.trim() || null,
    lastName: correctedName.lastName.trim(),
  });
  editing.value = false;
}
</script>

<template>
  <section
    class="overflow-hidden rounded-xl border border-orange-300 bg-orange-50 shadow-sm"
    aria-labelledby="identity-name-mismatch-title"
    data-testid="identity-name-mismatch"
  >
    <header
      class="flex flex-wrap items-start justify-between gap-3 border-b border-orange-200 px-5 py-4"
    >
      <div class="flex items-start gap-3">
        <div
          class="grid size-10 shrink-0 place-items-center rounded-lg bg-orange-100 text-orange-700"
        >
          <UIcon name="i-lucide-badge-alert" class="size-5" />
        </div>
        <div>
          <h2 id="identity-name-mismatch-title" class="font-semibold text-slate-950">
            Identity name mismatch
          </h2>
          <p class="mt-1 text-sm text-slate-600">
            The registered account name has not been overwritten. Compare the identity document and
            choose the correct action.
          </p>
        </div>
      </div>
      <UBadge :label="statusLabel" color="warning" variant="subtle" />
    </header>

    <div class="grid gap-px bg-orange-200 md:grid-cols-2">
      <div class="bg-white p-5">
        <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">
          Registered account
        </p>
        <p class="mt-2 text-lg font-semibold text-slate-950">
          {{ displayName(correction.registeredName) }}
        </p>
        <p class="mt-1 text-sm text-slate-500">Current protected account name</p>
      </div>
      <div class="bg-white p-5">
        <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">
          Identity document
        </p>
        <p class="mt-2 text-lg font-semibold text-slate-950">
          {{ displayName(correction.documentName) }}
        </p>
        <p class="mt-1 text-sm text-slate-500">OCR reading, editable before a request</p>
      </div>
    </div>

    <div v-if="editing" class="border-t border-orange-200 bg-white p-5">
      <p class="mb-4 text-sm font-medium text-slate-800">
        Correct the OCR reading to match what is printed on the document.
      </p>
      <div class="grid gap-4 md:grid-cols-3">
        <EmhareFormField v-model="correctedName.firstName" label="Document first name" required />
        <EmhareFormField v-model="correctedName.middleNames" label="Document middle names" />
        <EmhareFormField v-model="correctedName.lastName" label="Document last name" required />
      </div>
      <div class="mt-4 flex justify-end gap-2">
        <UButton label="Cancel" color="neutral" variant="ghost" @click="editing = false" />
        <UButton
          label="Save corrected OCR reading"
          :loading="loading"
          @click="saveCorrectedReading"
        />
      </div>
    </div>

    <div
      v-if="correction.requestReason"
      class="border-t border-orange-200 px-5 py-3 text-sm text-slate-700"
    >
      <span class="font-medium">Applicant reason:</span> {{ correction.requestReason }}
    </div>
    <div
      v-if="correction.decisionReason"
      class="border-t border-orange-200 px-5 py-3 text-sm text-slate-700"
    >
      <span class="font-medium">Decision:</span> {{ correction.decisionReason }}
    </div>

    <footer class="flex flex-wrap gap-2 border-t border-orange-200 bg-white px-5 py-4">
      <template v-if="mode === 'applicant'">
        <UButton
          label="Replace document"
          icon="i-lucide-refresh-cw"
          color="neutral"
          variant="outline"
          :disabled="!editable"
          @click="emit('replace')"
        />
        <UButton
          label="Correct OCR reading"
          icon="i-lucide-scan-text"
          color="neutral"
          variant="outline"
          :disabled="!editable || decisionPending || decided"
          @click="editing = true"
        />
        <UButton
          label="Request official-name correction"
          icon="i-lucide-file-signature"
          color="warning"
          :loading="loading"
          :disabled="!editable || decisionPending || decided"
          @click="emit('request', correction.documentName)"
        />
        <p v-if="decisionPending" class="w-full text-sm text-slate-600">
          Your request is awaiting staff approval. You can continue completing this draft.
        </p>
      </template>
      <template v-else-if="decisionPending">
        <UButton
          label="Approve official-name correction"
          icon="i-lucide-badge-check"
          :loading="loading"
          @click="emit('approve')"
        />
        <UButton
          label="Reject correction"
          icon="i-lucide-ban"
          color="error"
          variant="outline"
          :loading="loading"
          @click="emit('reject')"
        />
      </template>
    </footer>
  </section>
</template>
