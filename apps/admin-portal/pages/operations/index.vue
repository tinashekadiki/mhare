<script setup lang="ts">
definePageMeta({ layout: 'dashboard' })

const route = useRoute()
const auth = useEmhareAuth()
const accessRestricted = computed(() => route.query.access === 'restricted')

const serviceCards = [
  { label: 'Core Identity', icon: 'i-lucide-shield-check', state: 'Operational foundation', tone: 'success', to: '/operations/core' },
  { label: 'Academic Setup', icon: 'i-lucide-school', state: 'Governed operational slice', tone: 'success', to: '/operations/academic-structure' },
  { label: 'Admissions', icon: 'i-lucide-file-check-2', state: 'Evaluation, selection and governed offers', tone: 'success', to: '/operations/admissions' },
  { label: 'Finance', icon: 'i-lucide-receipt-text', state: 'Governed pricing, billing, collections, corrections and student accounts', tone: 'success', to: '/operations/finance' },
  { label: 'Student Records', icon: 'i-lucide-graduation-cap', state: 'Conversion and governed registration operational', tone: 'success', to: '/operations/student-registrations' },
  { label: 'Assessment & Results', icon: 'i-lucide-clipboard-pen-line', state: 'Evidence, moderation, publication, corrections and progression operational', tone: 'success', to: '/operations/assessment' },
  { label: 'Exams', icon: 'i-lucide-calendar-clock', state: 'Published scheduling, invigilation, attendance and incidents operational', tone: 'success', to: '/operations/exams' },
  { label: 'Accommodation', icon: 'i-lucide-building-2', state: 'Planned', tone: 'neutral', to: '/operations/accommodation' },
  { label: 'Documents', icon: 'i-lucide-files', state: 'Official result slips generated and stored', tone: 'success', to: '/operations/documents' }
] as const
const visibleServiceCards = computed(() => serviceCards.filter((serviceCard) =>
  serviceCard.label !== 'Core Identity'
  || [
    'CORE_INSTITUTION_MANAGE',
    'CORE_USER_MANAGE',
    'CORE_ROLE_MANAGE',
    'CORE_PERMISSION_MANAGE',
    'CORE_ROLE_ASSIGN',
    'CORE_REFERENCE_MANAGE',
    'CORE_AUDIT_READ',
    'CORE_WORKFLOW_MANAGE',
    'CORE_WORKFLOW_TASK'
  ].some(auth.hasPermission)
))

function serviceStatusLabel(tone: string) {
  if (tone === 'success') return 'Ready'
  if (tone === 'warning') return 'Partial'
  if (tone === 'info') return 'Next'
  return 'Planned'
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Operations">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <span class="text-sm text-muted">Delivery status by service-owned operational domain</span>
        </template>
        <template #right>
          <UBadge color="primary" variant="soft" label="ERP operational workspaces" />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="space-y-4 p-4">
        <UAlert
          v-if="accessRestricted"
          color="warning"
          variant="subtle"
          icon="i-lucide-triangle-alert"
          title="Access restricted"
          description="Your assigned role does not include permission for that operational workspace. Contact a system administrator if your duties have changed."
        />
        <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <NuxtLink
          v-for="serviceCard in visibleServiceCards"
          :key="serviceCard.label"
          :to="serviceCard.to"
          :aria-label="`Open ${serviceCard.label}`"
          class="block rounded-lg outline-primary/25 focus-visible:outline-3"
        >
          <UCard :ui="{ body: 'p-4' }">
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0">
                <p class="truncate text-sm font-medium text-highlighted">
                  {{ serviceCard.label }}
                </p>
                <p class="mt-1 text-xs text-muted">
                  {{ serviceCard.state }}
                </p>
              </div>
              <div class="flex items-center gap-2">
                <UBadge :color="serviceCard.tone" variant="subtle" size="sm" :label="serviceStatusLabel(serviceCard.tone)" />
                <UIcon :name="serviceCard.icon" class="size-5 shrink-0 text-primary" />
              </div>
            </div>
          </UCard>
        </NuxtLink>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
