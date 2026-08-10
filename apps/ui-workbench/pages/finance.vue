<script setup lang="ts">
definePageMeta({ public: true, layout: 'workbench' })

const lines = [
  { id: '1', account: '1100 · Cash', description: 'Application fee receipt', debit: 25, credit: 0 },
  { id: '2', account: '4100 · Application Fees', description: 'Recognise application income', debit: 0, credit: 25 }
]
</script>

<template>
  <UDashboardPanel>
    <template #body>
      <EmharePageHeader title="Finance Components" description="Money, debit/credit indicators, accounts, journals, invoices, allocations, periods, posting and reversal states." icon="i-lucide-receipt-text" />

      <div class="space-y-4 p-4">
        <div class="grid gap-4 md:grid-cols-3">
          <EmhareKpiCard label="Applicant fees" value="$12,450.00" hint="USD base currency" icon="i-lucide-receipt-text" />
          <EmhareKpiCard label="Unrated ZWG" value="7" hint="Waiting for effective rate" icon="i-lucide-triangle-alert" />
          <EmhareKpiCard label="Posted batches" value="43" hint="Current financial period" icon="i-lucide-check-circle" />
        </div>
        <EmhareJournalEntryGrid :lines="lines" />
      </div>
    </template>
  </UDashboardPanel>
</template>

