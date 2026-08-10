<script setup lang="ts">
definePageMeta({ public: true, layout: 'workbench' })

const period = ref('2026-s1')
const moduleId = ref('cs101')
const periods = [
  { id: '2026-s1', label: '2026 · Semester 1', current: true },
  { id: '2026-s2', label: '2026 · Semester 2' }
]
const modules = [
  { id: 'cs101', code: 'CS101', title: 'Programming Fundamentals', credits: 12 },
  { id: 'cs204', code: 'CS204', title: 'Data Structures', credits: 12 },
  { id: 'mth101', code: 'MTH101', title: 'Calculus', credits: 12 }
]
</script>

<template>
  <UDashboardPanel>
    <template #body>
      <EmharePageHeader title="Academic Components" description="Academic period, programme, Module selection, curriculum, rules, result and transcript patterns." icon="i-lucide-book-open" />

      <div class="space-y-4 p-4">
        <div class="grid gap-4 md:grid-cols-2">
          <EmhareAcademicPeriodSelector v-model="period" :periods="periods" />
          <EmhareModuleSelector v-model="moduleId" :modules="modules" />
        </div>

        <div class="grid gap-4 md:grid-cols-3">
          <EmhareKpiCard label="Registered credits" value="60" hint="Maximum allowed 72" icon="i-lucide-book-open" />
          <EmhareKpiCard label="Prerequisites" value="Clear" hint="No blocking rules" icon="i-lucide-check-circle" />
          <EmhareKpiCard label="Progress" value="80%" hint="Programme completion" icon="i-lucide-graduation-cap" />
        </div>

        <UAlert color="warning" variant="soft" icon="i-lucide-triangle-alert" title="Registration rule warning" description="CS204 requires CS101 completion before submission." />
      </div>
    </template>
  </UDashboardPanel>
</template>

