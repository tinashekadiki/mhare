<script setup lang="ts">
definePageMeta({ public: true, layout: 'workbench' })
</script>

<template>
  <UDashboardPanel>
    <template #body>
      <EmharePageHeader title="Student Components" description="Identity, status, balances, guardians, education history, checklists, and timeline patterns." icon="i-lucide-graduation-cap" />

      <div class="space-y-4 p-4">
        <EmhareStudentProfileHeader
          name="Tinashe Kadiki"
          student-number="R231234A"
          programme="BSc Computer Science"
          academic-status="Registered"
          registration-status="Financially Cleared"
          financial-status="Account clear"
          year-level="Year 3"
          period="Semester 1 · 2026"
        />

        <div class="grid gap-4 md:grid-cols-3">
          <EmhareKpiCard label="Account balance" value="$0.00" hint="No blocking balance" icon="i-lucide-receipt-text" />
          <EmhareKpiCard label="Credits completed" value="288" hint="72 remaining" icon="i-lucide-book-open" />
          <EmhareKpiCard label="GPA" value="3.72" hint="Current weighted GPA" icon="i-lucide-graduation-cap" />
        </div>

        <div class="grid gap-4 lg:grid-cols-2">
          <EmhareChecklist
            title="Admission checklist"
            :items="[
              { label: 'Offer accepted', status: 'complete' },
              { label: 'Documents verified', status: 'complete' },
              { label: 'Registration fee paid', status: 'complete' }
            ]"
          />
          <EmhareTimeline
            :items="[
              { title: 'Application submitted', description: 'Applicant portal submission completed.', time: '2026-01-12' },
              { title: 'Offer accepted', description: 'Accepted firm offer.', time: '2026-02-03' },
              { title: 'Registered', description: 'Semester registration approved.', time: '2026-03-01' }
            ]"
          />
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>

