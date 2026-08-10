<script setup lang="ts">
withDefaults(defineProps<{
  name: string
  studentNumber: string
  programme: string
  academicStatus?: string
  registrationStatus?: string
  financialStatus?: string
  yearLevel?: string
  period?: string
  photoUrl?: string
}>(), {
  academicStatus: undefined,
  registrationStatus: undefined,
  financialStatus: undefined,
  yearLevel: undefined,
  period: undefined,
  photoUrl: undefined
})
</script>

<template>
  <div class="flex flex-col gap-4 rounded-md border border-muted p-4 sm:flex-row sm:items-center">
    <UAvatar :src="photoUrl" :alt="name" size="3xl" icon="i-lucide-circle-user-round" />
    <div class="min-w-0 flex-1">
      <h2 class="truncate text-xl font-semibold text-highlighted">
        {{ name }}
      </h2>
      <p class="mt-1 text-sm text-muted">
        {{ studentNumber }} · {{ programme }}
      </p>
      <div class="mt-3 flex flex-wrap gap-2">
        <EmhareStatusPill v-if="academicStatus" :label="academicStatus" tone="primary" />
        <EmhareStatusPill v-if="registrationStatus" :label="registrationStatus" tone="success" />
        <EmhareStatusPill v-if="financialStatus" :label="financialStatus" tone="success" />
        <EmhareStatusPill v-if="yearLevel" :label="yearLevel" tone="neutral" />
        <EmhareStatusPill v-if="period" :label="period" tone="neutral" />
      </div>
    </div>
  </div>
</template>

