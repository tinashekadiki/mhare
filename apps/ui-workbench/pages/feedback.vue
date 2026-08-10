<script setup lang="ts">
definePageMeta({ public: true, layout: 'workbench' })

const confirm = useEmhareConfirm()
</script>

<template>
  <UDashboardPanel>
    <template #body>
      <EmharePageHeader title="Feedback" description="Status, alerts, empty/loading/error states, and SweetAlert-backed confirmations." icon="i-lucide-bell" />

      <div class="grid gap-4 p-4 md:grid-cols-2">
        <UAlert color="info" variant="soft" icon="i-lucide-info" title="Information banner" description="Persistent operational messages stay in context." />
        <UAlert color="warning" variant="soft" icon="i-lucide-triangle-alert" title="Warning message" description="Use alerts for decisions the user must act on." />
        <EmhareFeedbackState state="empty" title="No applications" description="Records will appear after filters are cleared." />
        <EmhareFeedbackState state="loading" title="Loading records" description="Skeleton loaders are used inside dense surfaces." />
        <EmhareFeedbackState state="error" title="Service unavailable" description="The API did not return a usable response." />
        <div class="rounded-md border border-muted p-6">
          <h2 class="text-sm font-semibold text-highlighted">Dialogs</h2>
          <p class="mt-1 text-sm text-muted">Important and destructive actions use SweetAlert.</p>
          <div class="mt-4 flex gap-2">
            <UButton label="Confirm action" color="primary" @click="confirm.confirmAction({ title: 'Confirm action?', text: 'This is the standard confirmation path.' })" />
            <UButton label="Delete action" color="error" variant="outline" @click="confirm.confirmAction({ title: 'Delete record?', text: 'This uses the destructive SweetAlert path.', destructive: true })" />
          </div>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>

