<script setup lang="ts">
withDefaults(defineProps<{
  title?: string
  errors?: Array<string | { message: string }>
}>(), {
  title: 'Review the highlighted fields',
  errors: () => []
})
</script>

<template>
  <UAlert
    v-if="errors.length"
    color="error"
    variant="soft"
    icon="i-lucide-circle-alert"
    :title="title"
  >
    <template #description>
      <ul class="mt-2 list-disc space-y-1 pl-5 text-sm">
        <li v-for="(error, index) in errors" :key="index">
          {{ typeof error === 'string' ? error : error.message }}
        </li>
      </ul>
    </template>
  </UAlert>
</template>

