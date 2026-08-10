<script setup lang="ts">
import Swal from "sweetalert2";
import type { MarkAmendmentSummary } from "@emhare/portal-shell/types/assessment";
definePageMeta({ layout: "dashboard" });
const api = useEmhareApi();
const toast = useToast();
const { showError } = useEmhareConfirm();
const amendments = ref<MarkAmendmentSummary[]>([]);
const loading = ref(false);
const activeId = ref<string | null>(null);
const pending = computed(() =>
  amendments.value.filter((item) => item.status === "REQUESTED"),
);
onMounted(loadQueue);
async function loadQueue() {
  loading.value = true;
  try {
    amendments.value = await api.request("/api/assessment-results/amendments");
  } catch (error) {
    await showError(
      "Amendment queue could not be loaded",
      api.errorMessage(error),
    );
  } finally {
    loading.value = false;
  }
}
async function decide(
  item: MarkAmendmentSummary,
  action: "approve" | "reject",
) {
  const result = await Swal.fire({
    title:
      action === "approve"
        ? "Approve mark amendment?"
        : "Reject mark amendment?",
    text:
      action === "approve"
        ? "Approval creates a new submitted revision and supersedes the original evidence."
        : "The original submitted score remains authoritative.",
    icon: action === "approve" ? "question" : "warning",
    input: "textarea",
    inputLabel: "Decision reason",
    inputPlaceholder: "Record the independent evidence reviewed.",
    showCancelButton: true,
    confirmButtonText:
      action === "approve" ? "Approve amendment" : "Reject amendment",
    confirmButtonColor: action === "approve" ? "#006633" : "#b42318",
    inputValidator: (value) =>
      value.trim() ? undefined : "A decision reason is required.",
  });
  if (!result.isConfirmed || !result.value?.trim()) return;
  activeId.value = item.id;
  try {
    await api.request(
      `/api/assessment-results/amendments/${item.id}/${action}`,
      {
        method: "POST",
        body: { expectedVersion: item.version, reason: result.value.trim() },
      },
    );
    await loadQueue();
    toast.add({
      title: `Amendment ${action === "approve" ? "approved" : "rejected"}`,
      description:
        action === "approve"
          ? "A replacement mark revision is now authoritative."
          : "The original submitted mark remains unchanged.",
      color: action === "approve" ? "success" : "warning",
    });
  } catch (error) {
    await showError(
      "Amendment decision could not be recorded",
      api.errorMessage(error),
    );
  } finally {
    activeId.value = null;
  }
}
</script>
<template>
  <UDashboardPanel
    ><template #header
      ><UDashboardNavbar title="Mark amendment review"
        ><template #leading><UDashboardSidebarCollapse /></template
        ><template #right
          ><UButton
            label="Refresh"
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="outline"
            :loading="loading"
            @click="loadQueue" /></template></UDashboardNavbar></template
    ><template #body
      ><div class="space-y-5 p-4 sm:p-6">
        <UAlert
          color="primary"
          variant="soft"
          icon="i-lucide-history"
          title="Independent correction control"
          description="Submitted marks are never overwritten. Approval creates a linked replacement revision; rejection leaves the original evidence authoritative."
        />
        <section class="grid gap-3 sm:grid-cols-3">
          <UCard :ui="{ body: 'p-4' }"
            ><p class="text-xs uppercase text-muted">All requests</p>
            <p class="mt-2 text-2xl font-semibold">
              {{ amendments.length }}
            </p></UCard
          ><UCard :ui="{ body: 'p-4' }"
            ><p class="text-xs uppercase text-warning">Awaiting decision</p>
            <p class="mt-2 text-2xl font-semibold">
              {{ pending.length }}
            </p></UCard
          ><UCard :ui="{ body: 'p-4' }"
            ><p class="text-xs uppercase text-success">Approved</p>
            <p class="mt-2 text-2xl font-semibold">
              {{
                amendments.filter((item) => item.status === "APPROVED").length
              }}
            </p></UCard
          >
        </section>
        <EmharePaginatedCollection :items="amendments" v-slot="{ items: paginatedAmendments }">
        <div class="space-y-3">
          <UCard v-for="item in paginatedAmendments" :key="item.id" :ui="{ body: 'p-4' }"
            ><div class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p class="text-xs text-muted">
                  Requested {{ new Date(item.requestedAt).toLocaleString() }}
                </p>
                <h2 class="mt-1 font-semibold">
                  {{ item.originalScore }} → {{ item.proposedScore }}
                </h2>
                <p class="mt-2 text-sm">{{ item.reason }}</p>
              </div>
              <UBadge
                :label="item.status"
                :color="
                  item.status === 'APPROVED'
                    ? 'success'
                    : item.status === 'REJECTED'
                      ? 'error'
                      : 'warning'
                "
                variant="subtle"
              />
            </div>
            <div
              v-if="item.status === 'REQUESTED'"
              class="mt-4 flex justify-end gap-2"
            >
              <UButton
                label="Reject"
                color="error"
                variant="soft"
                :loading="activeId === item.id"
                @click="decide(item, 'reject')"
              /><UButton
                label="Approve new revision"
                icon="i-lucide-badge-check"
                :loading="activeId === item.id"
                @click="decide(item, 'approve')"
              />
            </div>
            <p
              v-else-if="item.decisionReason"
              class="mt-3 rounded-md bg-elevated p-3 text-sm"
            >
              <span class="font-medium">Decision:</span>
              {{ item.decisionReason }}
            </p></UCard
          >
        </div>
        </EmharePaginatedCollection>
        <UAlert
          v-if="!loading && !amendments.length"
          color="neutral"
          variant="soft"
          title="No amendment requests"
          description="Submitted marks requiring correction will appear here."
        /></div></template
  ></UDashboardPanel>
</template>
