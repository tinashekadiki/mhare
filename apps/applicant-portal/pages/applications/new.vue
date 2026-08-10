<script setup lang="ts">
import type {
  AdmissionsApplicationSummary,
  ApplicationSectionOption,
  ApplicationStartOptions,
  ApplicationTypeOption,
} from '@emhare/portal-shell/types/admissions'

definePageMeta({ public: true })

const auth = useEmhareAuth()
const api = useEmhareApi()
const { showError } = useEmhareConfirm()

const startOptions = ref<ApplicationStartOptions | null>(null)
const loadingOptions = ref(false)
const creatingApplication = ref(false)
const attemptedCreate = ref(false)
const pageError = ref('')
type ApplicationStartStepCode = 'APPLICATION_ROUTE'

const activeStartStep = ref<ApplicationStartStepCode>('APPLICATION_ROUTE')

const form = reactive({
  applicantCategoryCode: 'LOCAL',
  intakeId: '',
  applicationTypeId: '',
})

const applicantCategoryItems = computed(() => startOptions.value?.applicantCategories.map(category => ({
  label: category.label,
  value: category.code,
})) ?? [])

const applicationTypeItems = computed(() => startOptions.value?.applicationTypes.map(applicationType => ({
  label: applicationType.name,
  value: applicationType.id,
  description: applicationType.fee.required
    ? `${formatMoney(applicationType.fee.amount, applicationType.fee.currencyCode)} application fee`
    : 'No application fee',
})) ?? [])

const intakeItems = computed(() => startOptions.value?.intakes
  .map(intake => ({
    label: intake.name,
    value: intake.id,
    description: `Closes ${formatDate(intake.endsOn)} · up to ${intake.maximumProgrammeChoices} choices`,
  })) ?? [])

const selectedApplicationType = computed<ApplicationTypeOption | null>(() =>
  startOptions.value?.applicationTypes.find(applicationType => applicationType.id === form.applicationTypeId) ?? null,
)

function defaultCompletionSections(applicationType?: Pick<ApplicationTypeOption, 'requiresEmploymentHistory' | 'requiresReferees'>): ApplicationSectionOption[] {
  const sections: ApplicationSectionOption[] = [
    { code: 'PERSONAL_DETAILS', name: 'Applicant details', required: true, repeatable: false, minimumRecords: 0, sortOrder: 10 },
    { code: 'NEXT_OF_KIN', name: 'Next of kin', required: true, repeatable: true, minimumRecords: 1, sortOrder: 20 },
    { code: 'QUALIFICATIONS', name: 'Qualifications', required: true, repeatable: true, minimumRecords: 1, sortOrder: 30 },
  ]
  if (applicationType?.requiresEmploymentHistory) {
    sections.push({ code: 'EMPLOYMENT_HISTORY', name: 'Employment history', required: true, repeatable: true, minimumRecords: 1, sortOrder: 40 })
  }
  if (applicationType?.requiresReferees) {
    sections.push({ code: 'REFEREES', name: 'Referees', required: true, repeatable: true, minimumRecords: 2, sortOrder: 50 })
  }
  return sections.concat([
    { code: 'PROGRAMME_CHOICES', name: 'Programme choices', required: true, repeatable: true, minimumRecords: 1, sortOrder: 60 },
    { code: 'DOCUMENTS', name: 'Supporting documents', required: true, repeatable: true, minimumRecords: 0, sortOrder: 70 },
    { code: 'PAYMENT', name: 'Application fee', required: false, repeatable: false, minimumRecords: 0, sortOrder: 80 },
    { code: 'REVIEW_DECLARATION', name: 'Review and declaration', required: true, repeatable: false, minimumRecords: 0, sortOrder: 90 },
  ])
}

const completionSections = computed(() => {
  const applicationType = selectedApplicationType.value
  const sections = applicationType?.sections?.length
    ? applicationType.sections
    : defaultCompletionSections(applicationType ?? undefined)
  return sections
  .filter(section => section.code !== 'PAYMENT' || selectedApplicationType.value?.fee.required)
  .map(section => ({
    ...section,
    name: section.code === 'PERSONAL_DETAILS'
      ? 'Applicant details'
      : section.code === 'PROGRAMME_CHOICES'
        ? 'Programme choices'
        : section.name,
    description: section.code === 'PAYMENT'
      ? 'Payment confirmation'
      : section.required
        ? 'Required before submission'
        : 'Complete when applicable',
  }))
})

const routeComplete = computed(() => Boolean(form.applicantCategoryCode && form.applicationTypeId && form.intakeId))
const canCreate = computed(() => routeComplete.value)
const startStepDefinitions = computed(() => [
  {
    id: 'APPLICATION_ROUTE' as const,
    title: 'Application route',
    description: 'Application type and intake',
    icon: 'i-lucide-map',
    required: true,
    disabled: false,
    complete: routeComplete.value,
  },
])

const activeStartStepDefinition = computed(() =>
  startStepDefinitions.value.find(step => step.id === activeStartStep.value) ?? startStepDefinitions.value[0],
)
const activeStartStepTitle = computed(() => activeStartStepDefinition.value?.title ?? 'Application route')
const activeStartStepDescription = computed(() => activeStartStepDefinition.value?.description ?? 'Application type and intake')

const activeStartStepIndex = computed(() =>
  startStepDefinitions.value.findIndex(step => step.id === activeStartStep.value),
)

const applicationJourneySteps = computed(() => [
  ...startStepDefinitions.value.map(step => ({
    id: step.id,
    title: step.title,
    description: step.description,
    icon: step.icon,
    required: step.required,
    disabled: step.disabled,
    status: step.complete
      ? 'complete' as const
      : step.id === activeStartStep.value
        ? 'current' as const
        : 'pending' as const,
  })),
  ...completionSections.value.map(section => ({
    id: section.code,
    title: section.name,
    description: section.description,
    icon: section.code === 'PERSONAL_DETAILS' ? 'i-lucide-contact-round'
      : section.code === 'QUALIFICATIONS' ? 'i-lucide-graduation-cap'
        : section.code === 'PROGRAMME_CHOICES' ? 'i-lucide-list-ordered'
          : section.code === 'DOCUMENTS' ? 'i-lucide-folder-check'
            : section.code === 'PAYMENT' ? 'i-lucide-receipt-text'
              : section.code === 'REVIEW_DECLARATION' ? 'i-lucide-file-check-2'
                : 'i-lucide-circle-dot',
    required: section.required,
    status: 'pending' as const,
    disabled: true,
  })),
])

const completedStartStepCount = computed(() => startStepDefinitions.value.filter(step => step.complete).length)
const applicationProgressPercentage = computed(() => {
  const total = applicationJourneySteps.value.length
  return total ? Math.round((completedStartStepCount.value / total) * 100) : 0
})

const startStepValidationMessage = computed(() => {
  if (activeStartStep.value === 'APPLICATION_ROUTE' && !routeComplete.value) return 'Choose an applicant category, an open application type, and an intake.'
  return ''
})

onMounted(async () => {
  await auth.loadUser()
  if (!auth.authenticated.value) return
  await auth.syncCoreUser()
  await loadStartOptions()
})

watch(() => form.applicantCategoryCode, async (categoryCode, previousCategoryCode) => {
  if (!auth.authenticated.value || categoryCode === previousCategoryCode) return
  Object.assign(form, { applicationTypeId: '', intakeId: '' })
  activeStartStep.value = 'APPLICATION_ROUTE'
  await loadStartOptions()
})

watch(() => form.applicationTypeId, () => {
  const currentIntakeIsAvailable = intakeItems.value.some(item => item.value === form.intakeId)
  if (!currentIntakeIsAvailable) form.intakeId = ''
})

function isStartStep(stepId: string): stepId is ApplicationStartStepCode {
  return stepId === 'APPLICATION_ROUTE'
}

function selectApplicationStep(stepId: string) {
  if (!isStartStep(stepId)) return
  const step = startStepDefinitions.value.find(item => item.id === stepId)
  if (step?.disabled) return
  activeStartStep.value = stepId
  attemptedCreate.value = false
}

async function continueStartJourney() {
  attemptedCreate.value = true
  if (startStepValidationMessage.value) return
  await createApplication()
}

async function loadStartOptions() {
  loadingOptions.value = true
  pageError.value = ''
  try {
    startOptions.value = await api.request<ApplicationStartOptions>(
      `/api/admissions/applications/start-options?applicantCategoryCode=${encodeURIComponent(form.applicantCategoryCode)}`,
    )
  } catch (error) {
    pageError.value = api.errorMessage(error, 'Application routes could not be loaded.')
  } finally {
    loadingOptions.value = false
  }
}

async function createApplication() {
  attemptedCreate.value = true
  if (!canCreate.value) {
    return
  }

  creatingApplication.value = true
  try {
    const application = await api.request<AdmissionsApplicationSummary>('/api/admissions/applications', {
      method: 'POST',
      body: {
        applicantCategoryCode: form.applicantCategoryCode,
        intakeId: form.intakeId,
        applicationTypeId: form.applicationTypeId,
        programmeIds: [],
      },
    })
    await navigateTo(`/applications/${application.id}`)
  } catch (error) {
    await showError('Application could not be started', api.errorMessage(error))
  } finally {
    creatingApplication.value = false
  }
}

function formatMoney(amount: number | null, currencyCode: string | null) {
  if (amount === null || !currencyCode) return 'No fee'
  return new Intl.NumberFormat('en-ZW', { style: 'currency', currency: currencyCode }).format(amount)
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('en-ZW', { dateStyle: 'medium' }).format(new Date(value))
}
</script>

<template>
  <div class="min-h-screen bg-slate-50 text-slate-900">
    <EmhareTopNav :breadcrumbs="[{ label: 'Applications', to: '/' }, { label: 'New application' }]">
      <template #actions>
        <UButton label="Return to applications" icon="i-lucide-arrow-left" color="neutral" variant="ghost" to="/" />
      </template>
    </EmhareTopNav>

    <main class="mx-auto max-w-[80rem] px-4 py-8 sm:px-6 sm:py-10">
      <section v-if="!auth.authenticated.value" class="mx-auto max-w-xl py-16 text-center">
        <p class="text-xs font-bold uppercase tracking-[0.2em] text-uzgold-700">Application portal</p>
        <h1 class="mt-4 text-3xl font-semibold tracking-tight text-uzgreen-950">Sign in to start an application.</h1>
        <p class="mx-auto mt-4 max-w-md text-base leading-7 text-slate-600">An account is required to save and submit an application.</p>
        <UButton class="mt-8" label="Sign in to continue" icon="i-lucide-log-in" color="primary" size="xl" @click="auth.login('/applications/new')" />
      </section>

      <div v-else class="grid gap-6 lg:grid-cols-[16rem_minmax(0,1fr)] lg:items-start lg:gap-8">
        <aside class="lg:sticky lg:top-20 lg:self-start">
          <div class="mb-4 rounded-xl border border-slate-200 bg-white p-4">
            <div class="flex items-center justify-between text-sm">
              <span class="font-semibold text-slate-700">Progress</span>
              <span class="font-semibold text-uzgreen-700">{{ applicationProgressPercentage }}%</span>
            </div>
            <UProgress class="mt-2" :model-value="applicationProgressPercentage" color="primary" size="sm" />
            <p class="mt-2 text-xs text-slate-500">{{ completedStartStepCount }} of {{ applicationJourneySteps.length }} steps complete</p>
          </div>
          <EmhareVerticalStepper :steps="applicationJourneySteps" :current-step="activeStartStep" label="Application process" @update:current-step="selectApplicationStep" />
        </aside>

        <div class="min-w-0 space-y-6">
          <UAlert v-if="pageError" color="error" variant="soft" icon="i-lucide-circle-alert" title="Application routes are unavailable" :description="pageError" />

          <section id="application-step-editor" class="min-w-0 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div class="border-b border-slate-100 px-6 py-5 sm:px-8">
              <p class="text-xs font-bold tracking-[0.2em] text-uzgreen-700 uppercase">Step {{ activeStartStepIndex + 1 }} of {{ applicationJourneySteps.length }}</p>
              <h1 class="mt-1 text-lg font-semibold text-slate-900">{{ activeStartStepTitle }}</h1>
              <p class="mt-1 text-sm text-slate-500">{{ activeStartStepDescription }}</p>
            </div>

            <form id="application-start-journey" class="p-6 sm:p-8" @submit.prevent="continueStartJourney">
              <UAlert
                v-if="attemptedCreate && startStepValidationMessage"
                class="mb-6"
                color="warning"
                variant="soft"
                icon="i-lucide-circle-alert"
                title="Complete required fields"
                :description="startStepValidationMessage"
              />

              <div class="grid gap-6 lg:grid-cols-2">
                <div class="lg:col-span-2"><EmhareFormField v-model="form.applicantCategoryCode" type="select" label="Applicant category" description="Determines the applicable fee and evidence requirements." :items="applicantCategoryItems" required :disabled="loadingOptions" /></div>
                <EmhareFormField v-model="form.applicationTypeId" type="select" label="Application type" description="Select the applicable application type." :items="applicationTypeItems" placeholder="Select an application type" required :disabled="loadingOptions" />
                <EmhareFormField v-model="form.intakeId" type="select" label="Intake" description="Open intake and application window." :items="intakeItems" placeholder="Select an open intake" required :disabled="loadingOptions || !form.applicationTypeId" />
                <UAlert v-if="!loadingOptions && (!startOptions?.applicationTypes.length || !startOptions?.intakes.length)" class="lg:col-span-2" color="warning" variant="soft" icon="i-lucide-calendar-x-2" title="No application route is currently open" description="Admissions must activate an application type and Academic Setup must open an intake before a draft can be created." />
                <UAlert v-if="selectedApplicationType" class="lg:col-span-2" :color="selectedApplicationType.fee.required ? 'warning' : 'success'" variant="soft" icon="i-lucide-receipt-text" :title="selectedApplicationType.fee.required ? 'Application fee required' : 'No application fee'" :description="selectedApplicationType.fee.required ? `Pay ${formatMoney(selectedApplicationType.fee.amount, selectedApplicationType.fee.currencyCode)} online or upload proof of payment before submitting. Finance confirmation is required before Admissions review.` : 'This application route does not require a configured fee.'" />
              </div>
            </form>

            <footer class="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 bg-slate-50/60 px-6 py-4 sm:px-8">
              <span class="text-sm text-slate-500">Fields marked * are required.</span>
              <UButton
                label="Create draft"
                icon="i-lucide-file-plus-2"
                color="primary"
                variant="solid"
                size="lg"
                :loading="creatingApplication"
                @click="continueStartJourney"
              />
            </footer>
          </section>
        </div>
      </div>
    </main>
  </div>
</template>
