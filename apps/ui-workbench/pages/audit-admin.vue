<script setup lang="ts">
definePageMeta({ public: true, layout: 'workbench' })
</script>

<template>
  <UDashboardPanel>
    <template #body>
      <EmharePageHeader title="Audit and Admin Components" description="Audit trail, before/after values, access denied, sensitive masking, permission matrix, settings and operational status." icon="i-lucide-shield-check" />

      <div class="space-y-4 p-4">
        <EmharePermissionMatrix
          :roles="['SYSTEM_ADMIN', 'ADMISSIONS_OFFICER', 'FINANCE_OFFICER']"
          :permissions="[
            { code: 'CORE_USER_MANAGE', label: 'Manage users', roles: ['SYSTEM_ADMIN'] },
            { code: 'ADMISSIONS_APPLICATION_REVIEW', label: 'Review applications', roles: ['SYSTEM_ADMIN', 'ADMISSIONS_OFFICER'] },
            { code: 'ADMISSIONS_PAYMENT_OVERRIDE', label: 'Override fee gate', roles: ['SYSTEM_ADMIN', 'FINANCE_OFFICER'] }
          ]"
        />

        <EmhareBeforeAfterViewer
          :changes="[
            { field: 'status', before: 'SUBMITTED', after: 'UNDER_REVIEW' },
            { field: 'reviewer', before: null, after: 'Admissions Officer' },
            { field: 'modified_by', before: 'system', after: 'codex.admin@example.test' }
          ]"
        />
      </div>
    </template>
  </UDashboardPanel>
</template>

