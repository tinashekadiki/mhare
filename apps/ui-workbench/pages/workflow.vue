<script setup lang="ts">
definePageMeta({ public: true, layout: 'workbench' })
</script>

<template>
  <UDashboardPanel>
    <template #body>
      <EmharePageHeader title="Workflow Components" description="Approval timeline, approve/reject actions, assignment, escalation, history and comments patterns." icon="i-lucide-list-checks" />

      <div class="grid gap-4 p-4 lg:grid-cols-2">
        <EmhareApprovalTimeline
          :steps="[
            { label: 'Submitted', actor: 'Applicant', status: 'complete', time: '2026-01-12' },
            { label: 'Payment verified', actor: 'Finance officer', status: 'complete', time: '2026-01-13' },
            { label: 'Academic review', actor: 'Admissions officer', status: 'current' },
            { label: 'Final decision', status: 'pending' }
          ]"
        />
        <EmhareApprovalActions />
      </div>
    </template>
  </UDashboardPanel>
</template>

