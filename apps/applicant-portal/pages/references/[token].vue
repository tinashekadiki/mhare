<script setup lang="ts">
definePageMeta({ public: true, layout: false })

type ReferenceRequest = {
  applicantName: string
  applicationNumber: string
  applicationTypeName: string
  refereeName: string
  refereeOrganisation: string
  status: 'SENT' | 'OPENED' | 'SUBMITTED'
  expiresAt: string
  submittedAt: string | null
}

const route = useRoute()
const api = useEmhareApi()
const environment = (import.meta as unknown as { env?: Record<string, string | undefined> }).env
const publicApiBaseUrl = environment?.NUXT_PUBLIC_API_BASE ?? 'http://localhost:8080'
const { showError } = useEmhareConfirm()
const loading = ref(true)
const submitting = ref(false)
const loadError = ref('')
const referenceRequest = ref<ReferenceRequest | null>(null)
const token = computed(() => String(route.params.token ?? ''))
const recommendationItems = [
  { label: 'Strongly recommend', value: 'STRONGLY_RECOMMEND' },
  { label: 'Recommend', value: 'RECOMMEND' },
  { label: 'Recommend with reservations', value: 'RECOMMEND_WITH_RESERVATIONS' },
  { label: 'Do not recommend', value: 'DO_NOT_RECOMMEND' },
]
const form = reactive({
  relationshipToApplicant: '',
  yearsKnown: 0,
  recommendation: '',
  comments: '',
  declarationAccepted: false,
})
const formComplete = computed(() => form.relationshipToApplicant.trim().length > 0
  && form.yearsKnown >= 0
  && Boolean(form.recommendation)
  && form.comments.trim().length >= 20
  && form.declarationAccepted)

onMounted(loadReferenceRequest)

async function loadReferenceRequest() {
  loading.value = true
  loadError.value = ''
  try {
    referenceRequest.value = await $fetch<ReferenceRequest>(
      `${publicApiBaseUrl}/api/admissions/referee-references/${encodeURIComponent(token.value)}`,
    )
  } catch (error) {
    loadError.value = api.errorMessage(error, 'This reference request could not be opened.')
  } finally {
    loading.value = false
  }
}

async function submitReference() {
  if (!formComplete.value) return
  submitting.value = true
  try {
    referenceRequest.value = await $fetch<ReferenceRequest>(
      `${publicApiBaseUrl}/api/admissions/referee-references/${encodeURIComponent(token.value)}`,
      { method: 'POST', body: form },
    )
  } catch (error) {
    await showError('Reference could not be submitted', api.errorMessage(error))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="min-h-screen bg-gradient-to-b from-primary-50 to-white px-4 py-8 dark:from-primary-950 dark:to-neutral-950 sm:py-12">
    <div class="mx-auto max-w-3xl space-y-5">
      <header class="flex items-center gap-3">
        <div class="flex size-11 items-center justify-center rounded-xl bg-primary text-lg font-bold text-white">e</div>
        <div>
          <p class="text-sm font-semibold text-primary">eMhare Admissions</p>
          <h1 class="text-2xl font-bold text-highlighted">Confidential reference</h1>
        </div>
      </header>

      <UCard v-if="loading">
        <div class="flex items-center gap-3 py-8 text-muted"><UIcon name="i-lucide-loader-circle" class="size-5 animate-spin" /> Loading reference request…</div>
      </UCard>

      <UAlert v-else-if="loadError" color="error" variant="soft" icon="i-lucide-circle-alert" title="Reference request unavailable" :description="loadError" />

      <UCard v-else-if="referenceRequest?.status === 'SUBMITTED'">
        <div class="space-y-3 py-4 text-center">
          <UIcon name="i-lucide-circle-check-big" class="mx-auto size-12 text-success" />
          <h2 class="text-xl font-semibold text-highlighted">Reference submitted</h2>
          <p class="text-muted">Thank you. Your confidential reference for {{ referenceRequest.applicantName }} has been received.</p>
          <p class="text-xs text-muted">Application {{ referenceRequest.applicationNumber }}</p>
        </div>
      </UCard>

      <template v-else-if="referenceRequest">
        <UCard>
          <div class="grid gap-4 sm:grid-cols-2">
            <div><p class="text-xs font-semibold uppercase tracking-wide text-muted">Applicant</p><p class="mt-1 font-semibold text-highlighted">{{ referenceRequest.applicantName }}</p></div>
            <div><p class="text-xs font-semibold uppercase tracking-wide text-muted">Application</p><p class="mt-1 font-semibold text-highlighted">{{ referenceRequest.applicationNumber }}</p></div>
            <div><p class="text-xs font-semibold uppercase tracking-wide text-muted">Application type</p><p class="mt-1 text-highlighted">{{ referenceRequest.applicationTypeName }}</p></div>
            <div><p class="text-xs font-semibold uppercase tracking-wide text-muted">Referee</p><p class="mt-1 text-highlighted">{{ referenceRequest.refereeName }} · {{ referenceRequest.refereeOrganisation }}</p></div>
          </div>
        </UCard>

        <UCard>
          <form class="space-y-5" @submit.prevent="submitReference">
            <div>
              <h2 class="text-lg font-semibold text-highlighted">Your assessment</h2>
              <p class="mt-1 text-sm text-muted">This response is confidential and is not editable after submission.</p>
            </div>
            <div class="grid gap-4 sm:grid-cols-2">
              <EmhareFormField v-model="form.relationshipToApplicant" label="Relationship to applicant" placeholder="e.g. Line manager" required />
              <EmhareFormField v-model="form.yearsKnown" type="number" label="Years known" :min="0" :max="100" required />
              <div class="sm:col-span-2"><EmhareFormField v-model="form.recommendation" type="select" label="Recommendation" :items="recommendationItems" required /></div>
              <div class="sm:col-span-2"><EmhareFormField v-model="form.comments" type="textarea" label="Reference comments" placeholder="Comment on the applicant's leadership, judgement, academic readiness, and suitability for postgraduate study." required /></div>
            </div>
            <UCheckbox v-model="form.declarationAccepted" label="I confirm that this confidential reference is accurate and represents my own assessment." />
            <div class="flex justify-end">
              <UButton type="submit" label="Submit confidential reference" icon="i-lucide-send" color="primary" :disabled="!formComplete" :loading="submitting" />
            </div>
          </form>
        </UCard>
      </template>
    </div>
  </main>
</template>
