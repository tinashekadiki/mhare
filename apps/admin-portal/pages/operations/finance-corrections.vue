<script setup lang="ts">
import Swal from "sweetalert2";
import type {
  FinanceBillingRegister,
  FinanceCollectionsRegister,
  FinanceCreditNoteSummary,
} from "@emhare/portal-shell/types/finance";

definePageMeta({ layout: "dashboard" });
type CreditLineDraft = {
  invoiceLineId: string;
  transactionAmount: number;
  baseAmount: number;
  reason: string;
};
const api = useEmhareApi();
const toast = useToast();
const { showError } = useEmhareConfirm();
const collections = ref<FinanceCollectionsRegister>({
  exchangeRates: [],
  payments: [],
  receipts: [],
  allocations: [],
  creditNotes: [],
});
const billing = ref<FinanceBillingRegister>({
  billingPolicies: [],
  billingEvents: [],
  invoices: [],
});
const loading = ref(false);
const operatingId = ref<string | null>(null);
const modalOpen = ref(false);
const activeDataset = ref<
  "credit-notes" | "allocation-reversals" | "payment-reversals"
>("credit-notes");
const form = reactive({
  invoiceId: "",
  creditNoteDate: new Date().toISOString().slice(0, 10),
  preparationReason: "",
  lines: [emptyLine()] as CreditLineDraft[],
});
const invoiceItems = computed(() =>
  billing.value.invoices.map((item) => ({
    label: `${item.invoiceNumber} · ${item.studentNumber} · ${money(item.grossTransactionAmount, item.transactionCurrencyCode)}`,
    value: item.id,
  })),
);
const selectedInvoice = computed(
  () =>
    billing.value.invoices.find((item) => item.id === form.invoiceId) ?? null,
);
const invoiceLineItems = computed(
  () =>
    selectedInvoice.value?.lines.map((item) => ({
      label: `Line ${item.lineNumber} · ${item.feeCode} · ${money(item.transactionAmount, selectedInvoice.value!.transactionCurrencyCode)}`,
      value: item.id,
    })) ?? [],
);
const totals = computed(() => ({
  transaction: form.lines.reduce(
    (sum, line) => sum + (line.transactionAmount || 0),
    0,
  ),
  base: form.lines.reduce((sum, line) => sum + (line.baseAmount || 0), 0),
}));
const reversedAllocations = computed(() =>
  collections.value.allocations.filter((item) => item.reversed),
);
const reversedPayments = computed(() =>
  collections.value.payments.filter((item) => item.reversed),
);
const counts = computed(() => ({
  draft: collections.value.creditNotes.filter((item) => item.status === "DRAFT")
    .length,
  posted: collections.value.creditNotes.filter(
    (item) => item.status === "POSTED",
  ).length,
  postedBase: collections.value.creditNotes
    .filter((item) => item.status === "POSTED")
    .reduce((sum, item) => sum + item.baseAmount, 0),
  reversedPayments: collections.value.payments.filter((item) => item.reversed)
    .length,
  reversedAllocations: collections.value.allocations.filter(
    (item) => item.reversed,
  ).length,
}));
const datasetTabs = computed(() => [
  {
    label: "Credit notes",
    value: "credit-notes",
    icon: "i-lucide-file-minus-2",
    badge: collections.value.creditNotes.length,
  },
  {
    label: "Allocation reversals",
    value: "allocation-reversals",
    icon: "i-lucide-unlink",
    badge: counts.value.reversedAllocations,
  },
  {
    label: "Payment reversals",
    value: "payment-reversals",
    icon: "i-lucide-rotate-ccw",
    badge: counts.value.reversedPayments,
  },
]);
onMounted(load);
async function load() {
  loading.value = true;
  try {
    [collections.value, billing.value] = await Promise.all([
      api.request<FinanceCollectionsRegister>("/api/finance/collections"),
      api.request<FinanceBillingRegister>("/api/finance/billing"),
    ]);
  } catch (error) {
    await showError(
      "Correction register could not be loaded",
      api.errorMessage(error),
    );
  } finally {
    loading.value = false;
  }
}
function openModal() {
  Object.assign(form, {
    invoiceId: "",
    creditNoteDate: new Date().toISOString().slice(0, 10),
    preparationReason: "",
    lines: [emptyLine()],
  });
  modalOpen.value = true;
}
function invoiceChanged() {
  form.lines.splice(0, form.lines.length, emptyLine());
}
function addLine() {
  form.lines.push(emptyLine());
}
function removeLine(index: number) {
  if (form.lines.length > 1) form.lines.splice(index, 1);
}
async function createCredit() {
  if (
    !form.invoiceId ||
    !form.preparationReason.trim() ||
    !form.lines.every(
      (line) =>
        line.invoiceLineId &&
        line.transactionAmount > 0 &&
        line.baseAmount > 0 &&
        line.reason.trim(),
    )
  )
    return;
  operatingId.value = "create";
  try {
    await api.request("/api/finance/collections/credit-notes", {
      method: "POST",
      body: {
        invoiceId: form.invoiceId,
        creditNoteDate: form.creditNoteDate,
        preparationReason: form.preparationReason.trim(),
        lines: form.lines.map((line) => ({
          ...line,
          reason: line.reason.trim(),
        })),
      },
    });
    modalOpen.value = false;
    await load();
    toast.add({
      title: "Draft credit note submitted",
      description:
        "A different Finance operator must verify and post the correction.",
      color: "success",
    });
  } catch (error) {
    await showError(
      "Credit note could not be created",
      api.errorMessage(error),
    );
  } finally {
    operatingId.value = null;
  }
}
async function postCredit(note: FinanceCreditNoteSummary) {
  const result = await Swal.fire({
    title: "Post credit note?",
    text: "Confirm the original invoice line, correction authority, transaction currency amount, and USD base amount. Posting is immutable.",
    input: "textarea",
    inputLabel: "Independent posting evidence",
    icon: "warning",
    showCancelButton: true,
    confirmButtonText: "Post credit note",
    confirmButtonColor: "#006633",
    inputValidator: (value) =>
      value.trim() ? undefined : "A complete reason is required.",
  });
  if (!result.isConfirmed || !result.value?.trim()) return;
  operatingId.value = note.id;
  try {
    await api.request(`/api/finance/collections/credit-notes/${note.id}/post`, {
      method: "POST",
      body: { reason: result.value.trim(), expectedVersion: note.version },
    });
    await load();
    toast.add({ title: "Credit note posted", color: "success" });
  } catch (error) {
    await showError("Credit note could not be posted", api.errorMessage(error));
  } finally {
    operatingId.value = null;
  }
}
function emptyLine(): CreditLineDraft {
  return { invoiceLineId: "", transactionAmount: 0, baseAmount: 0, reason: "" };
}
function money(value: number, currency = "USD") {
  return new Intl.NumberFormat("en-ZW", { style: "currency", currency }).format(
    value,
  );
}
function date(value: string) {
  return new Intl.DateTimeFormat("en-ZW", {
    dateStyle: "medium",
    timeStyle: value.includes("T") ? "short" : undefined,
  }).format(new Date(value));
}
</script>

<template>
  <UDashboardPanel
    ><template #header
      ><UDashboardNavbar title="Finance corrections"
        ><template #leading><UDashboardSidebarCollapse /></template
        ><template #right
          ><UButton
            label="Refresh"
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="outline"
            :loading="loading"
            @click="load" /></template></UDashboardNavbar></template
    ><template #body
      ><div class="space-y-5 p-4 sm:p-6">
        <UAlert
          color="primary"
          variant="soft"
          icon="i-lucide-git-compare-arrows"
          title="Correct by addition, never by rewriting"
          description="Posted invoices, receipts, payments, and allocations remain immutable. Credit notes and reversal records preserve the original evidence and require independent operators."
        />
        <section class="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
          <UCard
            v-for="item in [
              {
                label: 'Credit notes awaiting posting',
                value: counts.draft,
                tone: 'text-warning',
              },
              {
                label: 'Posted credit notes',
                value: counts.posted,
                tone: 'text-success',
              },
              {
                label: 'Posted USD credits',
                value: money(counts.postedBase),
                tone: 'text-success',
              },
              {
                label: 'Reversed payments',
                value: counts.reversedPayments,
                tone: 'text-muted',
              },
              {
                label: 'Reversed allocations',
                value: counts.reversedAllocations,
                tone: 'text-muted',
              },
            ]"
            :key="item.label"
            :ui="{ body: 'p-4' }"
            ><p class="text-xs uppercase text-muted">{{ item.label }}</p>
            <p class="mt-2 text-2xl font-semibold" :class="item.tone">
              {{ item.value }}
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
            v-if="activeDataset === 'credit-notes'"
            label="Prepare credit note"
            icon="i-lucide-file-minus-2"
            @click="openModal"
          />
        </div>
        <UCard v-if="activeDataset === 'credit-notes'" :ui="{ body: 'p-0' }"
          ><div class="border-b border-muted p-4">
            <p class="text-xs uppercase text-primary">
              Controlled correction register
            </p>
            <h2 class="mt-1 text-lg font-semibold">Invoice credit notes</h2>
          </div>
          <EmharePaginatedCollection :items="collections.creditNotes" v-slot="{ items: paginatedCreditNotes }">
          <div class="overflow-x-auto">
            <table class="w-full min-w-[1050px] text-left text-sm">
              <thead class="bg-muted/40 text-xs uppercase text-muted">
                <tr>
                  <th class="px-4 py-3">Credit note</th>
                  <th class="px-4 py-3">Original invoice</th>
                  <th class="px-4 py-3">Transaction credit</th>
                  <th class="px-4 py-3">USD credit</th>
                  <th class="px-4 py-3">Line evidence</th>
                  <th class="px-4 py-3">Control state</th>
                  <th class="px-4 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="note in paginatedCreditNotes"
                  :key="note.id"
                  class="border-t border-muted"
                >
                  <td class="px-4 py-3">
                    <p class="font-mono text-xs text-primary">
                      {{ note.creditNoteNumber }}
                    </p>
                    <p class="mt-1 text-xs text-muted">
                      {{ date(note.creditNoteDate) }}
                    </p>
                  </td>
                  <td class="px-4 py-3 font-medium">
                    {{ note.invoiceNumber }}
                  </td>
                  <td class="px-4 py-3 font-medium">
                    {{
                      money(
                        note.transactionAmount,
                        note.transactionCurrencyCode,
                      )
                    }}
                  </td>
                  <td class="px-4 py-3 font-medium text-success">
                    {{ money(note.baseAmount) }}
                  </td>
                  <td class="px-4 py-3">
                    <p>
                      {{ note.lines.length }} linked line{{
                        note.lines.length === 1 ? "" : "s"
                      }}
                    </p>
                    <p class="text-xs text-muted">
                      No original invoice value changed
                    </p>
                  </td>
                  <td class="px-4 py-3">
                    <UBadge
                      :label="note.status"
                      :color="note.status === 'POSTED' ? 'success' : 'warning'"
                      variant="subtle"
                    />
                  </td>
                  <td class="px-4 py-3 text-right">
                    <UButton
                      v-if="note.status === 'DRAFT'"
                      label="Verify and post"
                      size="xs"
                      :loading="operatingId === note.id"
                      @click="postCredit(note)"
                    />
                    <p v-else class="text-xs text-muted">
                      {{ date(note.postedAt!) }}
                    </p>
                  </td>
                </tr>
                <tr v-if="!collections.creditNotes.length">
                  <td colspan="7" class="px-4 py-8 text-center text-muted">
                    No credit notes have been prepared.
                  </td>
                </tr>
              </tbody>
            </table>
          </div></EmharePaginatedCollection></UCard
        >
        <UCard
          v-else-if="activeDataset === 'allocation-reversals'"
          :ui="{ body: 'p-0' }"
          ><div class="border-b border-muted p-4">
            <p class="text-xs uppercase text-primary">Allocation corrections</p>
            <h2 class="mt-1 text-lg font-semibold">Reversal evidence</h2>
          </div>
          <EmharePaginatedCollection :items="reversedAllocations" v-slot="{ items: paginatedReversedAllocations }">
          <div class="divide-y divide-muted">
            <div
              v-for="allocation in paginatedReversedAllocations"
              :key="allocation.id"
              class="p-4"
            >
              <div class="flex justify-between gap-3">
                <div>
                  <p class="font-mono text-xs text-primary">
                    {{ allocation.reversalNumber }}
                  </p>
                  <p class="mt-1 font-medium">
                    {{ allocation.paymentNumber }} →
                    {{ allocation.invoiceNumber }}
                  </p>
                </div>
                <p class="font-medium">
                  {{
                    money(
                      allocation.transactionAmount,
                      allocation.transactionCurrencyCode,
                    )
                  }}
                </p>
              </div>
            </div>
            <p
              v-if="!counts.reversedAllocations"
              class="p-6 text-center text-sm text-muted"
            >
              No allocation reversals.
            </p>
          </div></EmharePaginatedCollection></UCard
        ><UCard v-else :ui="{ body: 'p-0' }"
          ><div class="border-b border-muted p-4">
            <p class="text-xs uppercase text-primary">Payment corrections</p>
            <h2 class="mt-1 text-lg font-semibold">Reversed payments</h2>
          </div>
          <EmharePaginatedCollection :items="reversedPayments" v-slot="{ items: paginatedReversedPayments }">
          <div class="divide-y divide-muted">
            <div
              v-for="payment in paginatedReversedPayments"
              :key="payment.id"
              class="p-4"
            >
              <div class="flex justify-between gap-3">
                <div>
                  <p class="font-mono text-xs text-primary">
                    {{ payment.paymentNumber }}
                  </p>
                  <p class="mt-1 font-medium">{{ payment.payerName }}</p>
                  <p class="text-xs text-muted">
                    Original receipt {{ payment.receiptNumber }}
                  </p>
                </div>
                <p class="font-medium">
                  {{
                    money(
                      payment.transactionAmount,
                      payment.transactionCurrencyCode,
                    )
                  }}
                </p>
              </div>
            </div>
            <p
              v-if="!counts.reversedPayments"
              class="p-6 text-center text-sm text-muted"
            >
              No payment reversals.
            </p>
          </div></EmharePaginatedCollection></UCard
        >
      </div></template
    ></UDashboardPanel
  >

  <EmhareRecordDrawer
    v-model:open="modalOpen"
    presentation="page"
    title="Prepare credit note"
    description="Reference exact immutable invoice lines. A different Finance operator must post the completed draft."
    ><template #body
      ><div class="space-y-5">
        <UFormField label="Posted invoice" required
          ><USelect
            v-model="form.invoiceId"
            :items="invoiceItems"
            class="w-full"
            @update:model-value="invoiceChanged"
        /></UFormField>
        <div class="grid gap-4 sm:grid-cols-2">
          <UFormField label="Credit-note date" required
            ><UInput
              v-model="form.creditNoteDate"
              type="date"
              class="w-full" /></UFormField
          ><UFormField label="Preparation authority" required
            ><UInput
              v-model="form.preparationReason"
              class="w-full"
              placeholder="Reference the approved correction authority"
          /></UFormField>
        </div>
        <section class="rounded-lg border border-muted">
          <div
            class="flex items-center justify-between border-b border-muted p-3"
          >
            <div>
              <h3 class="font-medium">Correction lines</h3>
              <p class="text-xs text-muted">
                Transaction and USD amounts must reconcile exactly to the
                submitted total.
              </p>
            </div>
            <UButton
              label="Add line"
              icon="i-lucide-plus"
              size="xs"
              color="neutral"
              variant="outline"
              @click="addLine"
            />
          </div>
          <div class="space-y-3 p-3">
            <div
              v-for="(line, index) in form.lines"
              :key="index"
              class="grid gap-3 rounded-md bg-muted/30 p-3 sm:grid-cols-[1.4fr_0.7fr_0.7fr_1.5fr_auto]"
            >
              <UFormField label="Invoice line" required
                ><USelect
                  v-model="line.invoiceLineId"
                  :items="invoiceLineItems"
                  class="w-full" /></UFormField
              ><UFormField
                :label="`${selectedInvoice?.transactionCurrencyCode || 'Transaction'} amount`"
                required
                ><UInput
                  v-model.number="line.transactionAmount"
                  type="number"
                  min="0.01"
                  step="0.01" /></UFormField
              ><UFormField label="USD amount" required
                ><UInput
                  v-model.number="line.baseAmount"
                  type="number"
                  min="0.01"
                  step="0.01" /></UFormField
              ><UFormField label="Line reason" required
                ><UInput v-model="line.reason" /></UFormField
              ><EmhareGuidedActionButton
                icon="i-lucide-trash-2"
                color="error"
                variant="ghost"
                class="self-end"
                guidance-title="Journal line cannot be removed"
                :guidance-instructions="form.lines.length === 1 ? ['A correction journal must retain at least one line.'] : []"
                @click="removeLine(index)"
              />
            </div>
          </div>
        </section>
        <div class="flex justify-end gap-5 rounded-lg bg-muted/40 p-3 text-sm">
          <span
            >Transaction total
            <strong>{{
              money(
                totals.transaction,
                selectedInvoice?.transactionCurrencyCode,
              )
            }}</strong></span
          ><span
            >USD total <strong>{{ money(totals.base) }}</strong></span
          >
        </div>
      </div></template
    ><template #footer
      ><div class="flex w-full justify-end gap-2">
        <UButton
          label="Cancel"
          color="neutral"
          variant="outline"
          @click="modalOpen = false"
        /><UButton
          label="Submit draft credit note"
          :loading="operatingId === 'create'"
          @click="createCredit"
        /></div></template
  ></EmhareRecordDrawer>
</template>
