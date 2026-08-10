<script setup lang="ts">
import Swal from "sweetalert2";
import type {
  FinanceBillingRegister,
  FinanceCollectionsRegister,
  FinanceExchangeRateSummary,
  FinancePaymentAllocationSummary,
  FinancePaymentChannel,
  FinanceStudentAccountSummary,
  FinanceStudentPaymentSummary,
} from "@emhare/portal-shell/types/finance";

definePageMeta({ layout: "dashboard" });
const api = useEmhareApi();
const toast = useToast();
const { showError } = useEmhareConfirm();
const register = ref<FinanceCollectionsRegister>({
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
const accounts = ref<FinanceStudentAccountSummary[]>([]);
const loading = ref(false);
const operatingId = ref<string | null>(null);
const queueFilter = ref<
  "ALL" | "UNRATED" | "PENDING" | "SUSPENSE" | "RECONCILED" | "REVERSED"
>("ALL");
const activeDataset = ref<"payments" | "rates" | "allocations">("payments");
const rateModalOpen = ref(false);
const paymentModalOpen = ref(false);
const suspenseModalOpen = ref(false);
const allocationModalOpen = ref(false);
const selectedPayment = ref<FinanceStudentPaymentSummary | null>(null);
const rateForm = reactive({
  sourceCurrencyCode: "ZWG",
  rateToBase: 0,
  effectiveFrom: "",
  effectiveTo: "",
  sourceName: "Reserve Bank of Zimbabwe",
  sourceReference: "",
});
const paymentForm = reactive({
  studentFinanceAccountId: "",
  payerName: "",
  providerCode: "",
  providerTransactionReference: "",
  paymentChannel: "BANK_TRANSFER" as FinancePaymentChannel,
  transactionCurrencyCode: "USD",
  transactionAmount: 0,
  paidAt: "",
  providerEventFingerprint: "",
});
const suspenseForm = reactive({ studentFinanceAccountId: "", reason: "" });
const allocationForm = reactive({
  invoiceId: "",
  transactionAmount: 0,
  reason: "",
});
const channelItems = [
  "CASH",
  "BANK_TRANSFER",
  "CARD",
  "MOBILE_MONEY",
  "ONLINE",
  "OTHER",
].map((value) => ({ label: title(value), value }));
const currencyItems = [
  { label: "USD · base currency", value: "USD" },
  { label: "ZWG · effective rate required", value: "ZWG" },
];
const filterItems = [
  { label: "All payments", value: "ALL" },
  { label: "Awaiting rate", value: "UNRATED" },
  { label: "Awaiting reconciliation", value: "PENDING" },
  { label: "In suspense", value: "SUSPENSE" },
  { label: "Reconciled", value: "RECONCILED" },
  { label: "Reversed", value: "REVERSED" },
];
const datasetTabs = computed(() => [
  {
    label: "Payments",
    value: "payments",
    icon: "i-lucide-banknote",
    badge: register.value.payments.length,
  },
  {
    label: "Exchange rates",
    value: "rates",
    icon: "i-lucide-arrow-left-right",
    badge: register.value.exchangeRates.length,
  },
  {
    label: "Allocations",
    value: "allocations",
    icon: "i-lucide-link",
    badge: register.value.allocations.length,
  },
]);
const accountItems = computed(() =>
  accounts.value.map((item) => ({
    label: `${item.studentNumber} · ${item.accountNumber}`,
    value: item.id,
  })),
);
const invoiceItems = computed(() =>
  billing.value.invoices
    .filter(
      (item) =>
        !selectedPayment.value ||
        (item.studentFinanceAccountId ===
          selectedPayment.value.studentFinanceAccountId &&
          item.transactionCurrencyCode ===
            selectedPayment.value.transactionCurrencyCode),
    )
    .map((item) => ({
      label: `${item.invoiceNumber} · ${item.studentNumber} · ${money(item.grossTransactionAmount, item.transactionCurrencyCode)}`,
      value: item.id,
    })),
);
const counts = computed(() => ({
  unrated: register.value.payments.filter(
    (item) => item.ratingStatus === "UNRATED",
  ).length,
  pending: register.value.payments.filter(
    (item) =>
      item.reconciliationStatus === "PENDING" && item.ratingStatus === "RATED",
  ).length,
  suspense: register.value.payments.filter(
    (item) => item.inSuspense && item.reconciliationStatus === "RECONCILED",
  ).length,
  reconciled: register.value.payments.filter(
    (item) => item.reconciliationStatus === "RECONCILED" && !item.reversed,
  ).length,
  reversed: register.value.payments.filter((item) => item.reversed).length,
}));
const visiblePayments = computed(() =>
  register.value.payments.filter(
    (item) =>
      queueFilter.value === "ALL" ||
      (queueFilter.value === "UNRATED" && item.ratingStatus === "UNRATED") ||
      (queueFilter.value === "PENDING" &&
        item.reconciliationStatus === "PENDING" &&
        item.ratingStatus === "RATED") ||
      (queueFilter.value === "SUSPENSE" &&
        item.inSuspense &&
        item.reconciliationStatus === "RECONCILED") ||
      (queueFilter.value === "RECONCILED" &&
        item.reconciliationStatus === "RECONCILED" &&
        !item.reversed) ||
      (queueFilter.value === "REVERSED" && item.reversed),
  ),
);

onMounted(load);
async function load() {
  loading.value = true;
  try {
    [register.value, billing.value, accounts.value] = await Promise.all([
      api.request<FinanceCollectionsRegister>("/api/finance/collections"),
      api.request<FinanceBillingRegister>("/api/finance/billing"),
      api.request<FinanceStudentAccountSummary[]>(
        "/api/finance/collections/accounts",
      ),
    ]);
  } catch (error) {
    await showError(
      "Collections workspace could not be loaded",
      api.errorMessage(error),
    );
  } finally {
    loading.value = false;
  }
}
function openRate() {
  Object.assign(rateForm, {
    sourceCurrencyCode: "ZWG",
    rateToBase: 0,
    effectiveFrom: "",
    effectiveTo: "",
    sourceName: "Reserve Bank of Zimbabwe",
    sourceReference: "",
  });
  rateModalOpen.value = true;
}
async function createRate() {
  if (
    rateForm.rateToBase <= 0 ||
    !rateForm.effectiveFrom ||
    !rateForm.sourceName.trim()
  )
    return;
  await perform(
    "rate",
    async () => {
      await api.request("/api/finance/collections/exchange-rates", {
        method: "POST",
        body: {
          ...rateForm,
          sourceName: rateForm.sourceName.trim(),
          sourceReference: rateForm.sourceReference.trim() || null,
          effectiveFrom: new Date(rateForm.effectiveFrom).toISOString(),
          effectiveTo: rateForm.effectiveTo
            ? new Date(rateForm.effectiveTo).toISOString()
            : null,
        },
      });
      rateModalOpen.value = false;
    },
    "Draft exchange rate created",
  );
}
async function moveRate(
  rate: FinanceExchangeRateSummary,
  action: "approve" | "retire",
) {
  const result = await Swal.fire({
    title:
      action === "approve" ? "Approve exchange rate?" : "Retire exchange rate?",
    text:
      action === "approve"
        ? "Confirm the published source, conversion factor, and complete non-overlapping effective window."
        : "The rate remains attached to historical transactions but cannot rate new ones.",
    input: "textarea",
    inputLabel:
      action === "approve"
        ? "Independent treasury approval evidence"
        : "Retirement reason",
    showCancelButton: true,
    confirmButtonText: action === "approve" ? "Approve rate" : "Retire rate",
    confirmButtonColor: "#006633",
    inputValidator: (value) =>
      value.trim() ? undefined : "A complete reason is required.",
  });
  if (!result.isConfirmed || !result.value?.trim()) return;
  await perform(
    rate.id,
    () =>
      api.request(
        `/api/finance/collections/exchange-rates/${rate.id}/${action}`,
        {
          method: "POST",
          body: { reason: result.value.trim(), expectedVersion: rate.version },
        },
      ),
    action === "approve" ? "Exchange rate approved" : "Exchange rate retired",
  );
}
function openPayment() {
  Object.assign(paymentForm, {
    studentFinanceAccountId: "",
    payerName: "",
    providerCode: "",
    providerTransactionReference: "",
    paymentChannel: "BANK_TRANSFER",
    transactionCurrencyCode: "USD",
    transactionAmount: 0,
    paidAt: "",
    providerEventFingerprint: "",
  });
  paymentModalOpen.value = true;
}
async function capturePayment() {
  if (
    !paymentForm.payerName.trim() ||
    !paymentForm.providerCode.trim() ||
    !paymentForm.providerTransactionReference.trim() ||
    paymentForm.transactionAmount <= 0 ||
    !paymentForm.paidAt ||
    !paymentForm.providerEventFingerprint.trim()
  )
    return;
  await perform(
    "payment",
    async () => {
      await api.request("/api/finance/collections/payments", {
        method: "POST",
        body: {
          ...paymentForm,
          studentFinanceAccountId: paymentForm.studentFinanceAccountId || null,
          paidAt: new Date(paymentForm.paidAt).toISOString(),
        },
      });
      paymentModalOpen.value = false;
    },
    "Payment evidence captured",
  );
}
async function applyRate(payment: FinanceStudentPaymentSummary) {
  const result = await Swal.fire({
    title: "Apply effective exchange rate?",
    text: "Finance will select the single approved rate effective when the provider recorded payment. The original provider evidence remains unchanged.",
    icon: "question",
    showCancelButton: true,
    confirmButtonText: "Apply effective rate",
    confirmButtonColor: "#006633",
  });
  if (!result.isConfirmed) return;
  await perform(
    payment.id,
    () =>
      api.request(
        `/api/finance/collections/payments/${payment.id}/apply-rate?expectedVersion=${payment.version}`,
        { method: "POST" },
      ),
    "Payment rated in USD",
  );
}
async function decidePayment(
  payment: FinanceStudentPaymentSummary,
  action: "reconcile" | "reject",
) {
  const reconciling = action === "reconcile";
  const result = await Swal.fire({
    title: reconciling ? "Reconcile payment?" : "Reject payment evidence?",
    text: reconciling
      ? "Verify the provider transaction against the bank or settlement statement. A receipt is issued only after a student account is known."
      : "The captured evidence remains immutable and cannot return to the pending queue.",
    input: "textarea",
    inputLabel: reconciling
      ? "Independent reconciliation evidence"
      : "Rejection reason",
    showCancelButton: true,
    confirmButtonText: reconciling ? "Reconcile payment" : "Reject payment",
    confirmButtonColor: reconciling ? "#006633" : "#b42318",
    inputValidator: (value) =>
      value.trim() ? undefined : "A complete reason is required.",
  });
  if (!result.isConfirmed || !result.value?.trim()) return;
  await perform(
    payment.id,
    () =>
      api.request(`/api/finance/collections/payments/${payment.id}/${action}`, {
        method: "POST",
        body: { reason: result.value.trim(), expectedVersion: payment.version },
      }),
    reconciling ? "Payment reconciled" : "Payment rejected",
  );
}
function openSuspense(payment: FinanceStudentPaymentSummary) {
  selectedPayment.value = payment;
  Object.assign(suspenseForm, { studentFinanceAccountId: "", reason: "" });
  suspenseModalOpen.value = true;
}
async function resolveSuspense() {
  if (
    !selectedPayment.value ||
    !suspenseForm.studentFinanceAccountId ||
    !suspenseForm.reason.trim()
  )
    return;
  await perform(
    selectedPayment.value.id,
    async () => {
      await api.request(
        `/api/finance/collections/payments/${selectedPayment.value!.id}/resolve-suspense`,
        {
          method: "POST",
          body: {
            ...suspenseForm,
            reason: suspenseForm.reason.trim(),
            expectedPaymentVersion: selectedPayment.value!.version,
          },
        },
      );
      suspenseModalOpen.value = false;
    },
    "Suspense payment assigned and receipted",
  );
}
function openAllocation(payment: FinanceStudentPaymentSummary) {
  selectedPayment.value = payment;
  Object.assign(allocationForm, {
    invoiceId: "",
    transactionAmount: 0,
    reason: "",
  });
  allocationModalOpen.value = true;
}
async function allocate() {
  if (
    !selectedPayment.value ||
    !allocationForm.invoiceId ||
    allocationForm.transactionAmount <= 0 ||
    !allocationForm.reason.trim()
  )
    return;
  await perform(
    selectedPayment.value.id,
    async () => {
      await api.request(
        `/api/finance/collections/payments/${selectedPayment.value!.id}/allocations`,
        {
          method: "POST",
          body: {
            ...allocationForm,
            reason: allocationForm.reason.trim(),
            expectedPaymentVersion: selectedPayment.value!.version,
          },
        },
      );
      allocationModalOpen.value = false;
    },
    "Payment allocated to invoice",
  );
}
async function reverseAllocation(allocation: FinancePaymentAllocationSummary) {
  const reason = await reversalReason(
    "Reverse allocation?",
    "The original allocation remains in the audit trail. This action restores both payment and invoice balances.",
  );
  if (!reason) return;
  await perform(
    allocation.id,
    () =>
      api.request(
        `/api/finance/collections/allocations/${allocation.id}/reverse`,
        {
          method: "POST",
          body: { reason, expectedVersion: allocation.version },
        },
      ),
    "Allocation reversed",
  );
}
async function reversePayment(payment: FinanceStudentPaymentSummary) {
  const reason = await reversalReason(
    "Reverse reconciled payment?",
    "Every active allocation must be reversed first. The receipt and original payment remain visible as historical evidence.",
  );
  if (!reason) return;
  await perform(
    payment.id,
    () =>
      api.request(`/api/finance/collections/payments/${payment.id}/reverse`, {
        method: "POST",
        body: { reason, expectedVersion: payment.version },
      }),
    "Payment reversal recorded",
  );
}
async function reversalReason(titleText: string, text: string) {
  const result = await Swal.fire({
    title: titleText,
    text,
    input: "textarea",
    inputLabel: "Independent reversal authority",
    icon: "warning",
    showCancelButton: true,
    confirmButtonText: "Record reversal",
    confirmButtonColor: "#b42318",
    inputValidator: (value) =>
      value.trim() ? undefined : "A complete reason is required.",
  });
  return result.isConfirmed && result.value?.trim()
    ? result.value.trim()
    : null;
}
async function perform(
  id: string,
  action: () => Promise<unknown>,
  successTitle: string,
) {
  operatingId.value = id;
  try {
    await action();
    await load();
    toast.add({ title: successTitle, color: "success" });
  } catch (error) {
    await showError(
      "Collections operation could not be completed",
      api.errorMessage(error),
    );
  } finally {
    operatingId.value = null;
  }
}
function money(value: number | null | undefined, currency = "USD") {
  return value == null
    ? "Unrated"
    : new Intl.NumberFormat("en-ZW", { style: "currency", currency }).format(
        value,
      );
}
function date(value: string | null | undefined) {
  return value
    ? new Intl.DateTimeFormat("en-ZW", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(value))
    : "Open ended";
}
function title(value: string) {
  return value
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}
function statusColour(value: string) {
  return value === "RECONCILED" || value === "ACTIVE"
    ? "success"
    : value === "REJECTED" || value === "UNRATED" || value === "REVERSED"
      ? "error"
      : value === "PENDING" || value === "DRAFT"
        ? "warning"
        : "neutral";
}
</script>

<template>
  <UDashboardPanel
    ><template #header
      ><UDashboardNavbar title="Cash collection and reconciliation"
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
          icon="i-lucide-landmark"
          title="Provider evidence first, reconciliation second"
          description="Capture each provider transaction once. ZWG stays unrated until a valid effective rate exists. A different operator reconciles it; unmatched payments stay in suspense until independently assigned."
        />
        <section class="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
          <UCard
            v-for="item in [
              {
                label: 'Awaiting rate',
                value: counts.unrated,
                tone: 'text-error',
              },
              {
                label: 'Awaiting reconciliation',
                value: counts.pending,
                tone: 'text-warning',
              },
              {
                label: 'In suspense',
                value: counts.suspense,
                tone: 'text-warning',
              },
              {
                label: 'Reconciled',
                value: counts.reconciled,
                tone: 'text-success',
              },
              { label: 'Reversed', value: counts.reversed, tone: 'text-muted' },
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
            v-if="activeDataset === 'payments'"
            label="Capture payment"
            icon="i-lucide-banknote-arrow-down"
            @click="openPayment"
          />
          <UButton
            v-else-if="activeDataset === 'rates'"
            label="New exchange rate"
            icon="i-lucide-arrow-left-right"
            @click="openRate"
          />
        </div>
        <UCard v-if="activeDataset === 'payments'" :ui="{ body: 'p-0' }"
          ><div
            class="flex flex-wrap items-end justify-between gap-3 border-b border-muted p-4"
          >
            <div>
              <p class="text-xs uppercase text-primary">Operational queue</p>
              <h2 class="mt-1 text-lg font-semibold">Student payments</h2>
            </div>
            <USelect
              v-model="queueFilter"
              :items="filterItems"
              class="w-full sm:w-60"
            />
          </div>
          <EmharePaginatedCollection :items="visiblePayments" v-slot="{ items: paginatedPayments }">
          <div class="overflow-x-auto">
            <table class="w-full min-w-[1280px] text-left text-sm">
              <thead class="bg-muted/40 text-xs uppercase text-muted">
                <tr>
                  <th class="px-4 py-3">Payment</th>
                  <th class="px-4 py-3">Payer and provider</th>
                  <th class="px-4 py-3">Student account</th>
                  <th class="px-4 py-3">Transaction</th>
                  <th class="px-4 py-3">USD base</th>
                  <th class="px-4 py-3">Control state</th>
                  <th class="px-4 py-3">Receipt</th>
                  <th class="px-4 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="payment in paginatedPayments"
                  :key="payment.id"
                  class="border-t border-muted"
                >
                  <td class="px-4 py-3">
                    <p class="font-mono text-xs text-primary">
                      {{ payment.paymentNumber }}
                    </p>
                    <p class="mt-1 text-xs text-muted">
                      {{ date(payment.paidAt) }}
                    </p>
                  </td>
                  <td class="px-4 py-3">
                    <p class="font-medium">{{ payment.payerName }}</p>
                    <p class="text-xs text-muted">
                      {{ payment.providerCode }} ·
                      {{ payment.providerTransactionReference }}
                    </p>
                  </td>
                  <td class="px-4 py-3">
                    <p
                      :class="
                        payment.inSuspense
                          ? 'font-medium text-warning'
                          : 'font-medium'
                      "
                    >
                      {{
                        payment.inSuspense ? "Suspense" : payment.accountNumber
                      }}
                    </p>
                    <p class="text-xs text-muted">
                      {{ title(payment.paymentChannel) }}
                    </p>
                  </td>
                  <td class="px-4 py-3 font-medium">
                    {{
                      money(
                        payment.transactionAmount,
                        payment.transactionCurrencyCode,
                      )
                    }}
                  </td>
                  <td class="px-4 py-3">
                    <p
                      :class="
                        payment.ratingStatus === 'RATED'
                          ? 'font-medium text-success'
                          : 'font-medium text-error'
                      "
                    >
                      {{ money(payment.baseAmount) }}
                    </p>
                    <p class="text-xs text-muted">
                      {{ title(payment.ratingStatus) }}
                    </p>
                  </td>
                  <td class="px-4 py-3">
                    <div class="flex flex-wrap gap-1">
                      <UBadge
                        :label="title(payment.reconciliationStatus)"
                        :color="statusColour(payment.reconciliationStatus)"
                        variant="subtle"
                      /><UBadge
                        v-if="payment.reversed"
                        label="Reversed"
                        color="error"
                        variant="subtle"
                      />
                    </div>
                  </td>
                  <td class="px-4 py-3">
                    <p class="font-mono text-xs">
                      {{ payment.receiptNumber || "Not issued" }}
                    </p>
                  </td>
                  <td class="px-4 py-3">
                    <div class="flex justify-end gap-2">
                      <UButton
                        v-if="
                          payment.ratingStatus === 'UNRATED' &&
                          payment.reconciliationStatus === 'PENDING'
                        "
                        label="Apply rate"
                        size="xs"
                        :loading="operatingId === payment.id"
                        @click="applyRate(payment)"
                      /><UDropdownMenu
                        v-if="!payment.reversed"
                        :items="[
                          [
                            ...(payment.reconciliationStatus === 'PENDING' &&
                            payment.ratingStatus === 'RATED'
                              ? [
                                  {
                                    label: 'Reconcile payment',
                                    icon: 'i-lucide-badge-check',
                                    onSelect: () =>
                                      decidePayment(payment, 'reconcile'),
                                  },
                                  {
                                    label: 'Reject evidence',
                                    icon: 'i-lucide-circle-x',
                                    onSelect: () =>
                                      decidePayment(payment, 'reject'),
                                  },
                                ]
                              : []),
                            ...(payment.reconciliationStatus === 'RECONCILED' &&
                            payment.inSuspense
                              ? [
                                  {
                                    label: 'Resolve suspense',
                                    icon: 'i-lucide-user-round-search',
                                    onSelect: () => openSuspense(payment),
                                  },
                                ]
                              : []),
                            ...(payment.reconciliationStatus === 'RECONCILED' &&
                            !payment.inSuspense
                              ? [
                                  {
                                    label: 'Allocate to invoice',
                                    icon: 'i-lucide-link',
                                    onSelect: () => openAllocation(payment),
                                  },
                                  {
                                    label: 'Reverse payment',
                                    icon: 'i-lucide-rotate-ccw',
                                    onSelect: () => reversePayment(payment),
                                  },
                                ]
                              : []),
                          ],
                        ]"
                        ><UButton
                          icon="i-lucide-ellipsis"
                          size="xs"
                          color="neutral"
                          variant="outline"
                          aria-label="Payment actions"
                      /></UDropdownMenu>
                    </div>
                  </td>
                </tr>
                <tr v-if="!visiblePayments.length">
                  <td colspan="8" class="px-4 py-8 text-center text-muted">
                    No payments match this work queue.
                  </td>
                </tr>
              </tbody>
            </table>
          </div></EmharePaginatedCollection></UCard
        >
        <UCard v-else-if="activeDataset === 'rates'" :ui="{ body: 'p-0' }"
          ><div class="border-b border-muted p-4">
            <p class="text-xs uppercase text-primary">Treasury control</p>
            <h2 class="mt-1 text-lg font-semibold">Exchange-rate register</h2>
          </div>
          <EmharePaginatedCollection :items="register.exchangeRates" v-slot="{ items: paginatedExchangeRates }">
          <div class="divide-y divide-muted">
            <div
              v-for="rate in paginatedExchangeRates"
              :key="rate.id"
              class="flex items-start justify-between gap-4 p-4"
            >
              <div>
                <div class="flex items-center gap-2">
                  <p class="font-medium">
                    {{ rate.sourceCurrencyCode }} → USD ·
                    {{ rate.rateToBase }}
                  </p>
                  <UBadge
                    :label="rate.status"
                    :color="statusColour(rate.status)"
                    variant="subtle"
                  />
                </div>
                <p class="mt-1 text-xs text-muted">
                  {{ rate.sourceName
                  }}<span v-if="rate.sourceReference">
                    · {{ rate.sourceReference }}</span
                  >
                </p>
                <p class="mt-1 text-xs text-muted">
                  {{ date(rate.effectiveFrom) }} to
                  {{ date(rate.effectiveTo) }}
                </p>
              </div>
              <div class="flex gap-2">
                <UButton
                  v-if="rate.status === 'DRAFT'"
                  label="Approve"
                  size="xs"
                  :loading="operatingId === rate.id"
                  @click="moveRate(rate, 'approve')"
                /><UButton
                  v-if="rate.status === 'ACTIVE'"
                  label="Retire"
                  size="xs"
                  color="neutral"
                  variant="outline"
                  @click="moveRate(rate, 'retire')"
                />
              </div>
            </div>
            <p
              v-if="!register.exchangeRates.length"
              class="p-6 text-center text-sm text-muted"
            >
              No foreign-currency rates captured.
            </p>
          </div></EmharePaginatedCollection></UCard
        >
        <UCard v-else :ui="{ body: 'p-0' }"
          ><div class="border-b border-muted p-4">
            <p class="text-xs uppercase text-primary">Settlement audit</p>
            <h2 class="mt-1 text-lg font-semibold">Payment allocations</h2>
          </div>
          <EmharePaginatedCollection :items="register.allocations" v-slot="{ items: paginatedAllocations }">
          <div class="divide-y divide-muted">
            <div
              v-for="allocation in paginatedAllocations"
              :key="allocation.id"
              class="flex items-start justify-between gap-4 p-4"
            >
              <div>
                <div class="flex items-center gap-2">
                  <p class="font-mono text-xs text-primary">
                    {{ allocation.allocationNumber }}
                  </p>
                  <UBadge
                    v-if="allocation.reversed"
                    label="Reversed"
                    color="error"
                    variant="subtle"
                  />
                </div>
                <p class="mt-1 font-medium">
                  {{ allocation.paymentNumber }} →
                  {{ allocation.invoiceNumber }}
                </p>
                <p class="mt-1 text-xs text-muted">
                  {{
                    money(
                      allocation.transactionAmount,
                      allocation.transactionCurrencyCode,
                    )
                  }}
                  · payment USD {{ money(allocation.paymentBaseAmount) }} ·
                  invoice USD {{ money(allocation.invoiceBaseAmount) }}
                </p>
                <p
                  v-if="allocation.realisedExchangeDifference"
                  class="mt-1 text-xs font-medium text-warning"
                >
                  Realised FX
                  {{ money(allocation.realisedExchangeDifference) }}
                </p>
              </div>
              <UButton
                v-if="!allocation.reversed"
                label="Reverse"
                size="xs"
                color="error"
                variant="outline"
                @click="reverseAllocation(allocation)"
              />
            </div>
            <p
              v-if="!register.allocations.length"
              class="p-6 text-center text-sm text-muted"
            >
              No payment allocations recorded.
            </p>
          </div></EmharePaginatedCollection></UCard
        >
      </div></template
    ></UDashboardPanel
  >

  <EmhareRecordDrawer
    v-model:open="rateModalOpen"
    title="Capture exchange rate"
    description="Create a draft source rate. A different Finance operator must approve it before it can rate transactions."
    ><template #body
      ><div class="space-y-4">
        <div class="grid gap-4 sm:grid-cols-2">
          <UFormField label="Source currency" required
            ><UInput
              v-model="rateForm.sourceCurrencyCode"
              maxlength="3"
              class="w-full" /></UFormField
          ><UFormField label="USD per source unit" required
            ><UInput
              v-model.number="rateForm.rateToBase"
              type="number"
              min="0.00000001"
              step="0.00000001"
              class="w-full" /></UFormField
          ><UFormField label="Effective from" required
            ><UInput
              v-model="rateForm.effectiveFrom"
              type="datetime-local"
              class="w-full" /></UFormField
          ><UFormField label="Effective until"
            ><UInput
              v-model="rateForm.effectiveTo"
              type="datetime-local"
              class="w-full"
          /></UFormField>
        </div>
        <UFormField label="Published source" required
          ><UInput v-model="rateForm.sourceName" class="w-full" /></UFormField
        ><UFormField label="Source reference"
          ><UInput
            v-model="rateForm.sourceReference"
            class="w-full"
            placeholder="Circular, bulletin, or statement reference"
        /></UFormField></div></template
    ><template #footer
      ><div class="flex w-full justify-end gap-2">
        <UButton
          label="Cancel"
          color="neutral"
          variant="outline"
          @click="rateModalOpen = false"
        /><UButton
          label="Create draft rate"
          :loading="operatingId === 'rate'"
          @click="createRate"
        /></div></template
  ></EmhareRecordDrawer>
  <EmhareRecordDrawer
    v-model:open="paymentModalOpen"
    title="Capture payment evidence"
    description="Capture the provider transaction once. Leave the student account empty when the payment cannot yet be identified."
    ><template #body
      ><div class="space-y-4">
        <UFormField label="Student finance account"
          ><USelect
            v-model="paymentForm.studentFinanceAccountId"
            :items="accountItems"
            class="w-full"
            placeholder="Leave empty for suspense"
        /></UFormField>
        <div class="grid gap-4 sm:grid-cols-2">
          <UFormField label="Payer name" required
            ><UInput
              v-model="paymentForm.payerName"
              class="w-full" /></UFormField
          ><UFormField label="Payment channel" required
            ><USelect
              v-model="paymentForm.paymentChannel"
              :items="channelItems"
              class="w-full" /></UFormField
          ><UFormField label="Provider code" required
            ><UInput
              v-model="paymentForm.providerCode"
              class="w-full"
              placeholder="BANK, PAYNOW, ECOCASH" /></UFormField
          ><UFormField label="Provider transaction reference" required
            ><UInput
              v-model="paymentForm.providerTransactionReference"
              class="w-full" /></UFormField
          ><UFormField label="Transaction currency" required
            ><USelect
              v-model="paymentForm.transactionCurrencyCode"
              :items="currencyItems"
              class="w-full" /></UFormField
          ><UFormField label="Transaction amount" required
            ><UInput
              v-model.number="paymentForm.transactionAmount"
              type="number"
              min="0.01"
              step="0.01"
              class="w-full" /></UFormField
          ><UFormField label="Provider paid timestamp" required
            ><UInput
              v-model="paymentForm.paidAt"
              type="datetime-local"
              class="w-full" /></UFormField
          ><UFormField label="Provider event fingerprint" required
            ><UInput
              v-model="paymentForm.providerEventFingerprint"
              class="w-full"
          /></UFormField>
        </div>
        <UAlert
          v-if="paymentForm.transactionCurrencyCode !== 'USD'"
          color="warning"
          variant="soft"
          title="No fallback exchange rate"
          description="If no approved rate covers the provider paid timestamp, Finance stores this payment as unrated and blocks reconciliation."
        /></div></template
    ><template #footer
      ><div class="flex w-full justify-end gap-2">
        <UButton
          label="Cancel"
          color="neutral"
          variant="outline"
          @click="paymentModalOpen = false"
        /><UButton
          label="Capture immutable evidence"
          :loading="operatingId === 'payment'"
          @click="capturePayment"
        /></div></template
  ></EmhareRecordDrawer>
  <EmhareRecordDrawer
    v-model:open="suspenseModalOpen"
    title="Resolve suspense payment"
    description="Assign the reconciled payment after independently matching the payer evidence to a student finance account."
    ><template #body
      ><div class="space-y-4">
        <UFormField label="Student finance account" required
          ><USelect
            v-model="suspenseForm.studentFinanceAccountId"
            :items="accountItems"
            class="w-full" /></UFormField
        ><UFormField label="Matching evidence" required
          ><UTextarea
            v-model="suspenseForm.reason"
            :rows="4"
            class="w-full" /></UFormField
        ><UAlert
          color="primary"
          variant="soft"
          title="Receipt issuance"
          description="A receipt is issued from the original reconciled payment immediately after the account assignment succeeds."
        /></div></template
    ><template #footer
      ><div class="flex w-full justify-end gap-2">
        <UButton
          label="Cancel"
          color="neutral"
          variant="outline"
          @click="suspenseModalOpen = false"
        /><UButton
          label="Assign and issue receipt"
          :loading="operatingId === selectedPayment?.id"
          @click="resolveSuspense"
        /></div></template
  ></EmhareRecordDrawer>
  <EmhareRecordDrawer
    v-model:open="allocationModalOpen"
    title="Allocate payment to invoice"
    description="Finance retains transaction currency, payment-date USD basis, invoice-date USD basis, and the realised exchange difference."
    ><template #body
      ><div class="space-y-4">
        <UFormField label="Matching posted invoice" required
          ><USelect
            v-model="allocationForm.invoiceId"
            :items="invoiceItems"
            class="w-full" /></UFormField
        ><UFormField label="Transaction amount" required
          ><UInput
            v-model.number="allocationForm.transactionAmount"
            type="number"
            min="0.01"
            step="0.01"
            class="w-full" /></UFormField
        ><UFormField label="Allocation evidence" required
          ><UTextarea v-model="allocationForm.reason" :rows="4" class="w-full"
        /></UFormField></div></template
    ><template #footer
      ><div class="flex w-full justify-end gap-2">
        <UButton
          label="Cancel"
          color="neutral"
          variant="outline"
          @click="allocationModalOpen = false"
        /><UButton
          label="Allocate payment"
          :loading="operatingId === selectedPayment?.id"
          @click="allocate"
        /></div></template
  ></EmhareRecordDrawer>
</template>
