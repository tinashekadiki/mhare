<script setup lang="ts">
const props = withDefaults(defineProps<{
  amount: number
  currency?: string
  tone?: 'neutral' | 'success' | 'warning' | 'error'
  debitCredit?: 'debit' | 'credit'
}>(), {
  currency: 'USD',
  tone: 'neutral',
  debitCredit: undefined
})

const formattedAmount = computed(() => new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: props.currency,
  currencyDisplay: 'narrowSymbol'
}).format(props.amount))
</script>

<template>
  <span class="inline-flex items-center gap-2 font-medium" :class="tone === 'error' ? 'text-error' : tone === 'success' ? 'text-success' : 'text-highlighted'">
    <span>{{ formattedAmount }}</span>
    <UBadge v-if="debitCredit" :label="debitCredit === 'debit' ? 'Dr' : 'Cr'" color="neutral" variant="soft" />
  </span>
</template>

