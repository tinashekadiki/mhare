<script setup lang="ts">
withDefaults(defineProps<{
  columns?: 1 | 2 | 3 | 4
}>(), {
  columns: 2
})
</script>

<template>
  <div
    class="grid gap-4"
    :class="{
      'md:grid-cols-1': columns === 1,
      'md:grid-cols-2': columns === 2,
      'md:grid-cols-3': columns === 3,
      'md:grid-cols-4': columns === 4
    }"
  >
    <slot />
  </div>
</template>

