<script setup lang="ts">
const emit = defineEmits<{
  approve: []
  reject: [reason: string]
}>()

const reason = ref('')
const rejecting = ref(false)
</script>

<template>
  <div class="rounded-md border border-muted p-4">
    <div class="flex flex-wrap gap-2">
      <UButton icon="i-lucide-check" label="Approve" color="success" @click="emit('approve')" />
      <UButton icon="i-lucide-x" label="Reject" color="error" variant="outline" @click="rejecting = !rejecting" />
    </div>
    <div v-if="rejecting" class="mt-4 space-y-3">
      <UTextarea v-model="reason" placeholder="Capture rejection reason" autoresize />
      <EmhareGuidedActionButton label="Submit rejection" color="error" guidance-title="Rejection reason required" :guidance-instructions="reason.trim() ? [] : ['Record the reason and supporting evidence before submitting the rejection.']" @click="emit('reject', reason)" />
    </div>
  </div>
</template>
