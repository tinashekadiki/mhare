<script setup lang="ts">
const navigationGroups = [
  {
    label: 'Foundation',
    items: [
      { label: 'Shell', icon: 'i-lucide-layout-dashboard', to: '/shell' },
      { label: 'Forms', icon: 'i-lucide-pencil', to: '/forms' },
      { label: 'Data Table', icon: 'i-lucide-columns-3', to: '/data-table' },
      { label: 'Feedback', icon: 'i-lucide-bell', to: '/feedback' }
    ]
  },
  {
    label: 'Domain',
    items: [
      { label: 'Student', icon: 'i-lucide-graduation-cap', to: '/student' },
      { label: 'Academic', icon: 'i-lucide-book-open', to: '/academic' },
      { label: 'Finance', icon: 'i-lucide-receipt-text', to: '/finance' },
      { label: 'Workflow', icon: 'i-lucide-list-checks', to: '/workflow' },
      { label: 'Documents', icon: 'i-lucide-files', to: '/documents' },
      { label: 'Audit/Admin', icon: 'i-lucide-shield-check', to: '/audit-admin' }
    ]
  }
]

const quickActions = [
  { id: 'new-form', label: 'Open form pattern', icon: 'i-lucide-pencil', to: '/forms' },
  { id: 'table-demo', label: 'Open table demo', icon: 'i-lucide-columns-3', to: '/data-table' }
]

const academicPeriods = [
  { id: '2026-s1', label: '2026 · Semester 1', description: 'Current teaching period', current: true },
  { id: '2026-s2', label: '2026 · Semester 2', description: 'Next teaching period' }
]
</script>

<template>
  <EmhareAppShell
    app-name="eMhare UI"
    app-description="Component workbench"
    :navigation-groups="navigationGroups"
    :quick-actions="quickActions"
    :academic-periods="academicPeriods"
    :environment="{ name: 'Workbench', tone: 'info' }"
    :notifications="[{ id: 'mock', title: 'Mock data only', description: 'Workbench components do not call backend services.' }]"
    user-name="Workbench"
    user-email="ui@emhare.local"
    :require-authentication="false"
  >
    <slot />
  </EmhareAppShell>
</template>
