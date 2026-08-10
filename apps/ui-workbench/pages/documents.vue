<script setup lang="ts">
definePageMeta({ public: true, layout: 'workbench' })

const documents = [
  { id: '1', name: 'National ID', category: 'Identity', status: 'verified' as const },
  { id: '2', name: 'A Level Certificate', category: 'Qualification', status: 'pending' as const },
  { id: '3', name: 'Medical report', category: 'Admissions', status: 'expired' as const, expiresAt: '2026-03-31' }
]
</script>

<template>
  <UDashboardPanel>
    <template #body>
      <EmharePageHeader title="Document Components" description="Viewer, attachment list, upload, verification, expiry, preview, replacement and version history patterns." icon="i-lucide-files" />

      <div class="grid gap-4 p-4 lg:grid-cols-[1fr_22rem]">
        <EmhareDocumentList :documents="documents" />
        <UCard>
          <template #header>
            <h2 class="text-sm font-semibold text-highlighted">Upload area</h2>
          </template>
          <EmhareFormField label="Replace document" type="drop-file" multiple />
        </UCard>
      </div>
    </template>
  </UDashboardPanel>
</template>

