<script setup lang="ts">
definePageMeta({ public: true, layout: 'workbench' })
</script>

<template>
  <UDashboardPanel>
    <template #body>
      <EmharePageHeader
        title="Application Shell"
        description="Shared top bar, sidebar, breadcrumbs, global search, notifications, environment, academic period, and quick actions."
        badge="Shared"
        icon="i-lucide-layout-dashboard"
      >
        <template #actions>
          <UButton icon="i-lucide-plus" label="Primary action" color="primary" />
        </template>
      </EmharePageHeader>

      <div class="grid gap-4 p-4 md:grid-cols-3">
        <EmhareKpiCard label="Reusable areas" value="6" hint="Shell, forms, tables, feedback, display, domain" icon="i-lucide-columns-3" />
        <EmhareKpiCard label="Portal consumers" value="3" hint="Admin, applicant, student" icon="i-lucide-layout-dashboard" />
        <EmhareKpiCard label="Auth boundary" value="OIDC" hint="Kept in shared portal shell" icon="i-lucide-shield-check" />
      </div>

      <div class="p-4">
        <EmhareDescriptionList
          :items="[
            { label: 'No organisation switcher', value: 'Confirmed' },
            { label: 'Environment', value: 'Workbench' },
            { label: 'Academic period', value: 'Configurable selector' },
            { label: 'Quick actions', value: 'Dropdown launcher' }
          ]"
        />
      </div>
    </template>
  </UDashboardPanel>
</template>

