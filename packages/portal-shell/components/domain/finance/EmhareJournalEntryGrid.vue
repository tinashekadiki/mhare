<script setup lang="ts">
type JournalLine = {
  id: string
  account: string
  description?: string
  debit?: number
  credit?: number
}

const props = defineProps<{
  lines: JournalLine[]
}>()

const totalDebit = computed(() => props.lines.reduce((sum, line) => sum + Number(line.debit ?? 0), 0))
const totalCredit = computed(() => props.lines.reduce((sum, line) => sum + Number(line.credit ?? 0), 0))
const balanced = computed(() => totalDebit.value === totalCredit.value)
</script>

<template>
  <EmharePaginatedCollection :items="lines" v-slot="{ items: paginatedLines }">
  <div class="rounded-md border border-muted">
    <table class="w-full text-sm">
      <thead class="bg-elevated text-left text-muted">
        <tr>
          <th class="p-3">Account</th>
          <th class="p-3">Description</th>
          <th class="p-3 text-right">Debit</th>
          <th class="p-3 text-right">Credit</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="line in paginatedLines" :key="line.id" class="border-t border-muted">
          <td class="p-3 font-medium text-highlighted">{{ line.account }}</td>
          <td class="p-3 text-muted">{{ line.description }}</td>
          <td class="p-3 text-right"><EmhareMoneyDisplay :amount="line.debit ?? 0" /></td>
          <td class="p-3 text-right"><EmhareMoneyDisplay :amount="line.credit ?? 0" /></td>
        </tr>
      </tbody>
      <tfoot class="border-t border-muted bg-elevated">
        <tr>
          <td class="p-3 font-medium" colspan="2">
            <EmhareStatusPill :label="balanced ? 'Balanced' : 'Out of balance'" :tone="balanced ? 'success' : 'error'" />
          </td>
          <td class="p-3 text-right"><EmhareMoneyDisplay :amount="totalDebit" debit-credit="debit" /></td>
          <td class="p-3 text-right"><EmhareMoneyDisplay :amount="totalCredit" debit-credit="credit" /></td>
        </tr>
      </tfoot>
    </table>
  </div>
  </EmharePaginatedCollection>
</template>
