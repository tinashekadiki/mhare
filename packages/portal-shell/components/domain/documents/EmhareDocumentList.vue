<script setup lang="ts">
type DocumentVerificationStatus = 'pending' | 'verified' | 'rejected' | 'expired'

defineProps<{
  documents: Array<{
    id: string
    name: string
    category: string
    status: DocumentVerificationStatus
    expiresAt?: string
  }>
}>()

const statusTone = {
  pending: 'warning',
  verified: 'success',
  rejected: 'error',
  expired: 'error'
} as const

function documentStatusTone(status: DocumentVerificationStatus) {
  return statusTone[status]
}
</script>

<template>
  <EmharePaginatedCollection :items="documents" v-slot="{ items: paginatedDocuments }">
  <div class="rounded-md border border-muted">
    <div v-for="document in paginatedDocuments" :key="document.id" class="flex flex-wrap items-center gap-3 border-b border-muted p-3 last:border-b-0">
      <UIcon name="i-lucide-file-text" class="size-5 text-primary" />
      <div class="min-w-0 flex-1">
        <p class="truncate text-sm font-medium text-highlighted">{{ document.name }}</p>
        <p class="text-xs text-muted">{{ document.category }}<span v-if="document.expiresAt"> · Expires {{ document.expiresAt }}</span></p>
      </div>
      <EmhareStatusPill :label="document.status" :tone="documentStatusTone(document.status)" />
      <div class="flex gap-1">
        <UButton icon="i-lucide-eye" color="neutral" variant="ghost" size="xs" aria-label="Preview document" />
        <UButton icon="i-lucide-download" color="neutral" variant="ghost" size="xs" aria-label="Download document" />
      </div>
    </div>
    <EmhareFeedbackState v-if="!documents.length" state="empty" title="No documents" description="Uploaded documents will appear here." />
  </div>
  </EmharePaginatedCollection>
</template>
