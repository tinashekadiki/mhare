<script setup lang="ts">
import type {
  ApplicantApplicationWorkspace,
  ApplicantEmploymentHistory,
  ApplicantNextOfKin,
  ApplicantQualificationResult,
  ApplicantQualificationSitting,
  ApplicantReferee,
  ApplicationWorkspaceSection,
  ApplicationDocumentRequirementState,
  ApplicationStartOptions,
  QualificationReferenceData
} from '@emhare/portal-shell/types/admissions'
import type { UploadedDocumentDownload, UploadedDocumentSummary } from '@emhare/portal-shell/types/documents'
import type { ApplicationHostedCheckout, ApplicationPaymentOptions } from '@emhare/portal-shell/types/finance'

definePageMeta({ public: true })

type CountryOption = { id: string, iso2Code: string, name: string, nationalityName: string }
type InlineEditorKind = 'kin' | 'employment' | 'referee' | 'qualification' | 'result' | 'document' | null
type QualificationResultDraft = {
  clientId: number
  subjectId: string
  grade: string
  principalSubject: boolean
  expectedVersion: number
}
type ApplicationPaymentReconciliation = {
  status: 'PENDING' | 'PAID' | 'EXPIRED' | 'CANCELLED'
  workflowCleared: boolean
}

const route = useRoute()
const router = useRouter()
const api = useEmhareApi()
const auth = useEmhareAuth()
const toast = useToast()
const { confirmAction, showError, showSuccess } = useEmhareConfirm()
const applicationId = computed(() => String(route.params.applicationId))

const workspace = ref<ApplicantApplicationWorkspace | null>(null)
const startOptions = ref<ApplicationStartOptions | null>(null)
const qualificationReferences = ref<QualificationReferenceData | null>(null)
const countries = ref<CountryOption[]>([])
const loading = ref(true)
const working = ref(false)
const loadError = ref('')
const activeSectionCode = ref('')
const inlineEditor = ref<InlineEditorKind>(null)
const editingId = ref<string | null>(null)
const selectedSittingId = ref<string | null>(null)
const profileSaveState = ref<'saved' | 'saving' | 'dirty' | 'error'>('saved')
const lastSavedAt = ref('')
const selectedReviewDocument = ref<ApplicationDocumentRequirementState | null>(null)
const reviewDocumentDownload = ref<UploadedDocumentDownload | null>(null)
const loadingReviewDocumentId = ref<string | null>(null)
const reviewDocumentError = ref('')
const paymentOptions = ref<ApplicationPaymentOptions | null>(null)
const paymentProofDocuments = ref<UploadedDocumentSummary[]>([])
const paymentDetailsLoading = ref(false)
const paymentReconciliationLoading = ref(false)
const paymentProofForm = reactive<{ file: File | File[] | null }>({ file: null })
const paymentCheckout = ref<ApplicationHostedCheckout | null>(null)
const paymentCheckoutOpen = ref(false)
const paymentCheckoutFrame = ref<HTMLIFrameElement | null>(null)
const paymentCheckoutPanel = ref<HTMLElement | null>(null)
let paymentCheckoutRequestPosted = false
let pendingPaymentReconciliationChecked = false
let profileSaveTimer: ReturnType<typeof setTimeout> | null = null
let applyingWorkspace = false

const profileForm = reactive({
  applicantCategoryCode: 'LOCAL', titleCode: '', firstName: '', middleNames: '', lastName: '',
  dateOfBirth: '', genderCode: '', maritalStatusCode: '', nationalIdNumber: '', passportNumber: '',
  countryId: '', nationalityCountryId: '', placeOfBirth: '', disabilityStatusCode: '', specialNeeds: '',
  sponsorTypeCode: '', primaryEmail: '', primaryPhone: '', postalAddress: '', residentialAddress: '', expectedVersion: 0
})
const kinForm = reactive({ fullName: '', relationshipCode: '', phoneNumber: '', email: '', address: '', primary: false, expectedVersion: 0 })
const employmentForm = reactive({ employerName: '', positionTitle: '', startedOn: '', endedOn: '', current: false, responsibilities: '', expectedVersion: 0 })
const refereeForm = reactive({ fullName: '', title: '', organisation: '', positionTitle: '', email: '', phoneNumber: '', expectedVersion: 0 })
const qualificationForm = reactive({ level: 'O_LEVEL', examBodyId: '', institutionName: '', centreNumber: '', candidateNumber: '', yearWritten: new Date().getFullYear(), countryId: '', documentId: '', expectedVersion: 0 })
const resultForms = ref<QualificationResultDraft[]>([])
let nextResultDraftId = 1
const programmeIds = ref<string[]>([])
const documentForm = reactive<{ requirementCode: string, file: File | File[] | null }>({ requirementCode: '', file: null })
const oLevelGradeItems = ['A', 'B', 'C']
const aLevelGradeItems = ['A', 'B', 'C', 'D', 'E']

const isDraft = computed(() => workspace.value?.application.status === 'DRAFT')
const workspaceSections = computed(() => workspace.value?.sections ?? [])
const activeSection = computed(() => workspaceSections.value.find(section => section.code === activeSectionCode.value) ?? null)
const activeSectionIndex = computed(() => workspaceSections.value.findIndex(section => section.code === activeSectionCode.value))
const previousWorkspaceSection = computed(() => activeSectionIndex.value > 0 ? workspaceSections.value[activeSectionIndex.value - 1] : null)
const nextWorkspaceSection = computed(() => {
  const nextIndex = activeSectionIndex.value + 1
  return nextIndex >= 0 && nextIndex < workspaceSections.value.length ? workspaceSections.value[nextIndex] : null
})
const profileReadyForAutosave = computed(() => Boolean(
  nullableString(profileForm.applicantCategoryCode)
  && nullableString(profileForm.firstName)
  && nullableString(profileForm.lastName)
  && localDate(profileForm.dateOfBirth)
  && nullableString(profileForm.genderCode)
  && (profileForm.applicantCategoryCode === 'LOCAL'
    ? nullableString(profileForm.nationalIdNumber)
    : nullableString(profileForm.passportNumber))
  && nullableString(profileForm.countryId)
  && nullableString(profileForm.nationalityCountryId)
  && nullableString(profileForm.primaryEmail)
  && nullableString(profileForm.primaryPhone)
  && nullableString(profileForm.residentialAddress)
))
const completedSectionCount = computed(() => workspace.value?.sections.filter(section => ['COMPLETE', 'VERIFIED'].includes(section.status)).length ?? 0)
const preDraftJourneySteps = computed(() => [
  {
    id: 'APPLICATION_ROUTE',
    title: 'Application route',
    description: workspace.value?.application.applicationTypeName ?? 'Application type and intake',
    icon: 'i-lucide-map',
    required: true,
    disabled: true,
    status: 'complete' as const,
  },
])
const completedJourneyStepCount = computed(() => preDraftJourneySteps.value.length + completedSectionCount.value)
const progressPercentage = computed(() => {
  const total = applicationJourneySections.value.length
  return total ? Math.round((completedJourneyStepCount.value / total) * 100) : 0
})
const applicationJourneySections = computed(() =>
  [
    ...preDraftJourneySteps.value,
    ...workspaceSections.value.map(section => ({
      id: section.code,
      title: displaySectionName(section),
      description: section.completionSummary ?? (section.required ? 'Required before submission' : 'Optional'),
      icon: section.code === 'PERSONAL_DETAILS' ? 'i-lucide-contact-round'
        : section.code === 'QUALIFICATIONS' ? 'i-lucide-graduation-cap'
          : section.code === 'PROGRAMME_CHOICES' ? 'i-lucide-list-ordered'
            : section.code === 'DOCUMENTS' ? 'i-lucide-folder-check'
              : section.code === 'PAYMENT' ? 'i-lucide-receipt-text'
                : section.code === 'REVIEW_DECLARATION' ? 'i-lucide-file-check-2'
                  : 'i-lucide-circle-dot',
      required: section.required,
      disabled: false,
      status: ['COMPLETE', 'VERIFIED'].includes(section.status)
        ? 'complete' as const
        : ['REJECTED', 'CORRECTION_REQUIRED'].includes(section.status)
          ? 'attention' as const
          : section.code === activeSectionCode.value
            ? 'current' as const
            : 'pending' as const,
    })),
  ]
)
const activeJourneyStepIndex = computed(() => preDraftJourneySteps.value.length + activeSectionIndex.value)
const selectedIntake = computed(() => startOptions.value?.intakes.find(intake => intake.id === workspace.value?.application.intakeId) ?? null)
const selectedResultSitting = computed(() => workspace.value?.qualifications.find(sitting => sitting.id === selectedSittingId.value) ?? null)
const resultQualificationLevel = computed(() => selectedResultSitting.value?.level ?? qualificationForm.level)
const activeSubjectCatalogueLevel = computed(() => inlineEditor.value === 'result' ? resultQualificationLevel.value : qualificationForm.level)
const resultGradeItems = computed(() => gradeItemsForQualificationLevel(resultQualificationLevel.value))
const resultBatchReady = computed(() => resultForms.value.length > 0
  && resultForms.value.every(result => Boolean(result.subjectId && result.grade))
  && new Set(resultForms.value.map(result => result.subjectId)).size === resultForms.value.length)
const programmeItems = computed(() => selectedIntake.value?.programmes.map(programme => ({
  label: `${programme.code} · ${programme.name}`,
  value: programme.id,
  description: `${programme.owningAcademicUnitName} · Curriculum ${programme.programmeVersionCode}`
})) ?? [])
const countryItems = computed(() => countries.value.map(country => ({ label: `${country.iso2Code} · ${country.name}`, value: country.id })))
const examBodyItems = computed(() => qualificationReferences.value?.examBodies.map(item => ({ label: `${item.code} · ${item.name}`, value: item.id })) ?? [])
const subjectItems = computed(() => {
  const source = activeSubjectCatalogueLevel.value === 'A_LEVEL'
    ? qualificationReferences.value?.aLevelSubjects
    : activeSubjectCatalogueLevel.value === 'O_LEVEL'
      ? qualificationReferences.value?.oLevelSubjects
      : qualificationReferences.value?.otherSubjects
  return source?.map(item => ({
    label: `${item.code} · ${item.name}`,
    value: item.id,
    description: item.scienceSubject ? 'Science subject' : undefined
  })) ?? []
})
const uploadableRequirements = computed(() => workspace.value?.documents.requirements.filter(requirement => ['MISSING', 'REJECTED'].includes(requirement.state)).map(requirement => ({
  label: `${requirement.requirementName}${requirement.required ? ' · Required' : ''}`,
  value: requirement.requirementCode
})) ?? [])
const reviewDocumentIsImage = computed(() => reviewDocumentDownload.value?.mimeType.startsWith('image/') ?? false)
const reviewDocumentIsPdf = computed(() => reviewDocumentDownload.value?.mimeType === 'application/pdf')

onMounted(async () => {
  window.addEventListener('message', handlePaymentCheckoutMessage)
  await auth.loadUser()
  if (!auth.authenticated.value) return
  await auth.syncCoreUser()
  await loadWorkspace(true)
})

onBeforeUnmount(() => {
  window.removeEventListener('message', handlePaymentCheckoutMessage)
})

watch(profileForm, () => {
  if (applyingWorkspace || !isDraft.value) return
  profileSaveState.value = 'dirty'
  if (profileSaveTimer) clearTimeout(profileSaveTimer)
  if (!profileReadyForAutosave.value) return
  profileSaveTimer = setTimeout(saveProfile, 900)
}, { deep: true })

watch(paymentCheckoutOpen, (open) => {
  if (open) return
  paymentCheckout.value = null
  paymentCheckoutRequestPosted = false
})

async function loadWorkspace(loadReferences = false) {
  loading.value = true
  loadError.value = ''
  try {
    const loaded = await api.request<ApplicantApplicationWorkspace>(`/api/admissions/applications/${applicationId.value}/workspace`)
    if (loadReferences) {
      const [options, references, countryRows] = await Promise.all([
        api.request<ApplicationStartOptions>(`/api/admissions/applications/start-options?applicantCategoryCode=${encodeURIComponent(loaded.profile.applicantCategoryCode)}`),
        api.request<QualificationReferenceData>('/api/admissions/qualification-reference-data'),
        api.request<CountryOption[]>('/api/core/reference/countries')
      ])
      startOptions.value = options
      qualificationReferences.value = references
      countries.value = countryRows
    }
    let currentWorkspace = loaded
    if (!pendingPaymentReconciliationChecked && loaded.application.payment?.status === 'PENDING') {
      pendingPaymentReconciliationChecked = true
      try {
        const reconciliation = await requestPaymentReconciliation()
        if (reconciliation.workflowCleared) {
          currentWorkspace = await waitForPaymentProjection(loaded)
        }
      } catch {
        // Keep the payment page usable when a background status check is temporarily unavailable.
      }
    }
    applyWorkspace(currentWorkspace)
    await loadPaymentDetails()
    prepareInlineEditorForSection(activeSectionCode.value)
  } catch (error) {
    loadError.value = api.errorMessage(error, 'The application workspace could not be loaded.')
  } finally {
    loading.value = false
  }
}

async function loadPaymentDetails() {
  const payment = workspace.value?.application.payment
  if (!workspace.value?.application.paymentRequired || !payment) {
    paymentOptions.value = null
    paymentProofDocuments.value = []
    return
  }
  paymentDetailsLoading.value = true
  try {
    const [options, documents] = await Promise.all([
      api.request<ApplicationPaymentOptions>(`/api/finance/application-payment-references/by-application/${applicationId.value}/payment-options`),
      api.request<UploadedDocumentSummary[]>(`/api/documents/uploads?ownerType=FINANCE_RECORD&ownerId=${encodeURIComponent(payment.financePaymentReferenceId)}`),
    ])
    paymentOptions.value = options
    paymentProofDocuments.value = documents.filter(document => document.documentTypeCode === 'PROOF_OF_PAYMENT')
  } catch (error) {
    await showError('Payment options could not be loaded', api.errorMessage(error))
  } finally {
    paymentDetailsLoading.value = false
  }
}

async function uploadPaymentProof() {
  const payment = workspace.value?.application.payment
  const file = Array.isArray(paymentProofForm.file) ? paymentProofForm.file[0] : paymentProofForm.file
  if (!payment || !file) return
  working.value = true
  try {
    const rejectedProof = paymentProofDocuments.value.find(document => document.verificationStatus === 'REJECTED')
    const formData = new FormData()
    formData.append('ownerType', 'FINANCE_RECORD')
    formData.append('ownerId', payment.financePaymentReferenceId)
    formData.append('documentTypeCode', 'PROOF_OF_PAYMENT')
    if (rejectedProof) formData.append('replacesDocumentId', rejectedProof.id)
    formData.append('file', file)
    await api.request<UploadedDocumentSummary>('/api/documents/uploads', { method: 'POST', body: formData })
    paymentProofForm.file = null
    await loadWorkspace()
    await showSuccess('Proof of payment uploaded', 'Finance will independently verify the evidence and reconcile the payment.')
  } catch (error) {
    await showError('Proof of payment could not be uploaded', api.errorMessage(error))
  } finally {
    working.value = false
  }
}

async function startOnlinePayment() {
  if (!paymentOptions.value?.onlinePayment.available || !workspace.value?.application.payment) return
  working.value = true
  try {
    paymentCheckout.value = await api.request<ApplicationHostedCheckout>(
      `/api/finance/application-payment-references/by-application/${applicationId.value}/online-checkouts`,
      { method: 'POST', body: { emailAddress: workspace.value.profile.primaryEmail } },
    )
    paymentCheckoutRequestPosted = false
    paymentCheckoutOpen.value = true
    await nextTick()
    paymentCheckoutPanel.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch (error) {
    await showError('Online payment could not be started', api.errorMessage(error))
  } finally {
    working.value = false
  }
}

async function requestPaymentReconciliation(attemptId?: string) {
  return api.request<ApplicationPaymentReconciliation>(
    `/api/finance/application-payment-references/by-application/${applicationId.value}/online-checkouts/reconcile`,
    { method: 'POST', body: attemptId ? { attemptId } : {} },
  )
}

async function waitForPaymentProjection(initialWorkspace: ApplicantApplicationWorkspace) {
  let latestWorkspace = initialWorkspace
  for (let attemptNumber = 0; attemptNumber < 12; attemptNumber += 1) {
    if (attemptNumber > 0) {
      await new Promise(resolve => setTimeout(resolve, 400))
    }
    latestWorkspace = await api.request<ApplicantApplicationWorkspace>(
      `/api/admissions/applications/${applicationId.value}/workspace`,
    )
    if (latestWorkspace.application.paymentClearanceStatus === 'PAID') {
      return latestWorkspace
    }
  }
  return latestWorkspace
}

async function checkPaymentStatus() {
  paymentReconciliationLoading.value = true
  try {
    const reconciliation = await requestPaymentReconciliation()
    if (!reconciliation.workflowCleared) {
      await showError('Payment not yet confirmed', 'The bank has not confirmed a completed payment yet. Please try again shortly.')
      return
    }
    const refreshedWorkspace = await waitForPaymentProjection(workspace.value!)
    applyWorkspace(refreshedWorkspace)
    await loadPaymentDetails()
    await showSuccess('Payment confirmed', 'Your application fee has been cleared.')
  } catch (error) {
    await showError('Payment status could not be checked', api.errorMessage(error))
  } finally {
    paymentReconciliationLoading.value = false
  }
}

function postPaymentRequestToCheckout() {
  const checkout = paymentCheckout.value
  const checkoutWindow = paymentCheckoutFrame.value?.contentWindow
  if (!checkout || !checkoutWindow || paymentCheckoutRequestPosted) return
  const checkoutOrigin = new URL(checkout.embeddedCheckoutUrl).origin
  const form = Object.entries(checkout.formParameters).map(([name, value]) => ({
    id: name,
    name,
    type: 'hidden',
    value,
  }))
  checkoutWindow.postMessage(JSON.stringify({ form }), checkoutOrigin)
  paymentCheckoutRequestPosted = true
}

async function handlePaymentCheckoutMessage(event: MessageEvent) {
  const checkout = paymentCheckout.value
  if (!checkout
    || event.source !== paymentCheckoutFrame.value?.contentWindow
    || ![new URL(checkout.embeddedCheckoutUrl).origin, checkout.returnMessageOrigin].includes(event.origin)) return
  let result: Record<string, unknown>
  try {
    const parsed = typeof event.data === 'string' ? JSON.parse(event.data) : event.data
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return
    result = parsed as Record<string, unknown>
  } catch {
    return
  }
  const status = String(result.Lite_Payment_Card_Status ?? '')
  if (!status) return
  const expectedMerchantTrace = checkout.formParameters.Lite_Merchant_Trace
  const returnedMerchantTrace = String(result.Lite_Merchant_Trace ?? '')
  if (returnedMerchantTrace && returnedMerchantTrace !== expectedMerchantTrace) return

  if (status !== '0') {
    paymentCheckoutOpen.value = false
    await loadWorkspace()
    await showError('Payment was not completed', 'No payment has been confirmed. You can try again or upload proof of a bank payment.')
    return
  }

  paymentReconciliationLoading.value = true
  try {
    const reconciliation = await requestPaymentReconciliation(checkout.attemptId)
    paymentCheckoutOpen.value = false
    if (!reconciliation.workflowCleared) {
      await showError('Payment confirmation pending', 'The bank response has not been verified yet. Use Check payment status to try again.')
      return
    }
    const refreshedWorkspace = await waitForPaymentProjection(workspace.value!)
    applyWorkspace(refreshedWorkspace)
    await loadPaymentDetails()
    await showSuccess('Payment confirmed', 'Your application fee has been cleared.')
  } catch (error) {
    paymentCheckoutOpen.value = false
    await showError('Payment confirmation pending', api.errorMessage(error, 'The bank response could not be verified yet. Use Check payment status to try again.'))
  } finally {
    paymentReconciliationLoading.value = false
  }
}

function cancelOnlinePayment() {
  paymentCheckoutOpen.value = false
}

function applyWorkspace(value: ApplicantApplicationWorkspace) {
  applyingWorkspace = true
  const sections = normaliseWorkspaceSections(value)
  workspace.value = { ...value, sections }
  const profile = value.profile
  Object.assign(profileForm, {
    applicantCategoryCode: profile.applicantCategoryCode,
    titleCode: profile.titleCode ?? '', firstName: profile.firstName, middleNames: profile.middleNames ?? '', lastName: profile.lastName,
    dateOfBirth: profile.dateOfBirth ?? '', genderCode: profile.genderCode ?? '', maritalStatusCode: profile.maritalStatusCode ?? '',
    nationalIdNumber: profile.nationalIdNumber ?? '', passportNumber: profile.passportNumber ?? '', countryId: profile.countryId ?? '',
    nationalityCountryId: profile.nationalityCountryId ?? '', placeOfBirth: profile.placeOfBirth ?? '', disabilityStatusCode: profile.disabilityStatusCode ?? '',
    specialNeeds: profile.specialNeeds ?? '', sponsorTypeCode: profile.sponsorTypeCode ?? '', primaryEmail: profile.primaryEmail,
    primaryPhone: profile.primaryPhone ?? '', postalAddress: profile.postalAddress ?? '', residentialAddress: profile.residentialAddress ?? '',
    expectedVersion: profile.version
  })
  programmeIds.value = value.application.programmeChoices.sort((a, b) => a.choiceRank - b.choiceRank).map(choice => choice.programmeId)
  if (!activeSectionCode.value || !sections.some(section => section.code === activeSectionCode.value)) {
    activeSectionCode.value = sections.find(section => !['COMPLETE', 'VERIFIED'].includes(section.status))?.code ?? sections[0]?.code ?? ''
  }
  nextTick(() => { applyingWorkspace = false })
}

function normaliseWorkspaceSections(value: ApplicantApplicationWorkspace): ApplicationWorkspaceSection[] {
  const sections = Array.isArray(value.sections) ? value.sections : []
  if (sections.length) {
    return sections
      .filter(section => section.code !== 'PAYMENT' || value.application.paymentRequired)
      .sort((left, right) => left.sortOrder - right.sortOrder)
  }
  const configuredSections = startOptions.value?.applicationTypes
    .find(applicationType => applicationType.id === value.application.applicationTypeId)
    ?.sections
  const sourceSections = configuredSections?.length ? configuredSections : fallbackApplicationSections(value)
  return sourceSections
    .filter(section => section.code !== 'PAYMENT' || value.application.paymentRequired)
    .sort((left, right) => left.sortOrder - right.sortOrder)
    .map(section => ({
      id: `fallback-${section.code}`,
      code: section.code,
      name: section.name,
      required: section.required,
      repeatable: section.repeatable,
      minimumRecords: section.minimumRecords,
      sortOrder: section.sortOrder,
      ...sectionProgress(value, section.code, section.minimumRecords),
      version: 0,
    }))
}

function fallbackApplicationSections(value: ApplicantApplicationWorkspace) {
  const source = [
    { code: 'PERSONAL_DETAILS', name: 'Applicant details', required: true, repeatable: false, minimumRecords: 0, sortOrder: 10 },
    { code: 'NEXT_OF_KIN', name: 'Next of kin', required: true, repeatable: true, minimumRecords: 1, sortOrder: 20 },
    { code: 'QUALIFICATIONS', name: 'Qualifications', required: true, repeatable: true, minimumRecords: 1, sortOrder: 30 },
    { code: 'PROGRAMME_CHOICES', name: 'Programme choices', required: true, repeatable: true, minimumRecords: 1, sortOrder: 60 },
    { code: 'DOCUMENTS', name: 'Supporting documents', required: true, repeatable: true, minimumRecords: 0, sortOrder: 70 },
    { code: 'PAYMENT', name: 'Application fee', required: value.application.paymentRequired, repeatable: false, minimumRecords: 0, sortOrder: 80 },
    { code: 'REVIEW_DECLARATION', name: 'Review and declaration', required: true, repeatable: false, minimumRecords: 0, sortOrder: 90 },
  ]
  return source
}

function sectionProgress(value: ApplicantApplicationWorkspace, code: string, minimumRecords: number): Pick<ApplicationWorkspaceSection, 'status' | 'completedAt' | 'completionSummary'> {
  const minimum = Math.max(1, minimumRecords)
  if (code === 'PERSONAL_DETAILS') {
    const complete = value.profile.missingRequiredFields.length === 0
    return { status: complete ? 'COMPLETE' : 'IN_PROGRESS', completedAt: complete ? value.profile.updatedAt : null, completionSummary: complete ? 'Applicant details complete.' : value.profile.missingRequiredFields.join(', ') }
  }
  if (code === 'NEXT_OF_KIN') return countSectionProgress(value.nextOfKin.length, minimum)
  if (code === 'QUALIFICATIONS') return countSectionProgress(value.qualifications.filter(sitting => sitting.results.length > 0).length, minimum)
  if (code === 'PROGRAMME_CHOICES') return countSectionProgress(value.application.programmeChoices.length, minimum)
  if (code === 'DOCUMENTS') {
    return { status: value.documents.requiredDocumentsUploaded ? 'COMPLETE' : 'IN_PROGRESS', completedAt: null, completionSummary: value.documents.requiredDocumentsUploaded ? 'Required documents uploaded.' : 'Required documents are missing or rejected.' }
  }
  if (code === 'PAYMENT') {
    return { status: value.application.canEnterReview ? 'COMPLETE' : 'IN_PROGRESS', completedAt: null, completionSummary: value.application.canEnterReview ? 'Application fee cleared.' : 'Application fee confirmation or waiver is required.' }
  }
  if (code === 'REVIEW_DECLARATION') {
    return { status: value.declarationAcceptedAt ? 'COMPLETE' : 'IN_PROGRESS', completedAt: value.declarationAcceptedAt, completionSummary: value.declarationAcceptedAt ? 'Applicant declaration accepted.' : 'Review and accept the applicant declaration.' }
  }
  return { status: 'IN_PROGRESS', completedAt: null, completionSummary: 'Complete this section.' }
}

function countSectionProgress(count: number, minimum: number): Pick<ApplicationWorkspaceSection, 'status' | 'completedAt' | 'completionSummary'> {
  return {
    status: count >= minimum ? 'COMPLETE' : 'IN_PROGRESS',
    completedAt: null,
    completionSummary: `${count} of ${minimum} required record(s) captured.`,
  }
}

function displaySectionName(section: Pick<ApplicationWorkspaceSection, 'code' | 'name'> | null | undefined) {
  if (!section) return ''
  if (section.code === 'PERSONAL_DETAILS') return 'Applicant details'
  return section.code === 'PROGRAMME_CHOICES' ? 'Programme choices' : section.name
}

function activateSection(sectionCode: string) {
  if (!workspaceSections.value.some(section => section.code === sectionCode)) return
  activeSectionCode.value = sectionCode
  prepareInlineEditorForSection(sectionCode)
  nextTick(() => {
    document.getElementById('application-section-editor')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

function prepareInlineEditorForSection(sectionCode: string) {
  if (!isDraft.value) {
    inlineEditor.value = null
    return
  }
  if (sectionCode === 'NEXT_OF_KIN') openKin()
  else if (sectionCode === 'EMPLOYMENT_HISTORY') openEmployment()
  else if (sectionCode === 'REFEREES') openReferee()
  else if (sectionCode === 'QUALIFICATIONS') openQualification()
  else if (sectionCode === 'DOCUMENTS' && uploadableRequirements.value.length) openDocumentUpload()
  else inlineEditor.value = null
}

function activatePreviousSection() {
  if (previousWorkspaceSection.value) activateSection(previousWorkspaceSection.value.code)
}

async function saveActiveSectionBeforeLeaving(): Promise<boolean> {
  if (activeSectionCode.value !== 'PERSONAL_DETAILS' || !isDraft.value) return true
  if (!profileReadyForAutosave.value) {
    profileSaveState.value = 'dirty'
    return false
  }
  if (profileSaveTimer) {
    clearTimeout(profileSaveTimer)
    profileSaveTimer = null
  }
  if (profileSaveState.value !== 'saved') await saveProfile()
  return profileSaveState.value === 'saved'
}

async function activateNextSection() {
  if (!await saveActiveSectionBeforeLeaving()) return
  if (nextWorkspaceSection.value) activateSection(nextWorkspaceSection.value.code)
}

async function saveProfile() {
  if (!isDraft.value || profileSaveState.value === 'saving') return
  profileSaveState.value = 'saving'
  try {
    const updated = await api.request<ApplicantApplicationWorkspace>(`/api/admissions/applications/${applicationId.value}/profile`, {
      method: 'PUT', body: profileRequestBody()
    })
    applyWorkspace(updated)
    profileSaveState.value = 'saved'
    lastSavedAt.value = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  } catch (error) {
    profileSaveState.value = 'error'
    await showError('Profile could not be saved', api.errorMessage(error))
  }
}

async function saveDraft() {
  if (!isDraft.value) return
  if (activeSectionCode.value === 'PERSONAL_DETAILS') {
    const saved = await saveActiveSectionBeforeLeaving()
    if (saved) toast.add({ title: 'Draft saved', color: 'success', icon: 'i-lucide-check' })
    return
  }
  toast.add({ title: 'Draft up to date', description: 'Changes in this section save automatically as you go.', color: 'success', icon: 'i-lucide-check' })
}

function nullableBody<T extends Record<string, unknown>>(value: T) {
  return Object.fromEntries(Object.entries(value).map(([key, fieldValue]) => [key, fieldValue === '' ? null : fieldValue]))
}

function nullableString(value: unknown) {
  if (typeof value === 'string') return value === '' ? null : value
  if (value && typeof value === 'object' && 'value' in value && typeof value.value === 'string') {
    return value.value === '' ? null : value.value
  }
  return null
}

function localDate(value: unknown) {
  const text = nullableString(value) ?? (value && typeof value === 'object' && 'toString' in value && typeof value.toString === 'function'
    ? value.toString()
    : null)
  return text && /^\d{4}-\d{2}-\d{2}$/.test(text) ? text : null
}

function profileRequestBody() {
  return {
    applicantCategoryCode: nullableString(profileForm.applicantCategoryCode),
    titleCode: nullableString(profileForm.titleCode),
    middleNames: nullableString(profileForm.middleNames),
    dateOfBirth: localDate(profileForm.dateOfBirth),
    genderCode: nullableString(profileForm.genderCode),
    maritalStatusCode: nullableString(profileForm.maritalStatusCode),
    nationalIdNumber: nullableString(profileForm.nationalIdNumber),
    passportNumber: nullableString(profileForm.passportNumber),
    countryId: nullableString(profileForm.countryId),
    nationalityCountryId: nullableString(profileForm.nationalityCountryId),
    placeOfBirth: nullableString(profileForm.placeOfBirth),
    disabilityStatusCode: nullableString(profileForm.disabilityStatusCode),
    specialNeeds: nullableString(profileForm.specialNeeds),
    sponsorTypeCode: nullableString(profileForm.sponsorTypeCode),
    primaryEmail: nullableString(profileForm.primaryEmail),
    primaryPhone: nullableString(profileForm.primaryPhone),
    postalAddress: nullableString(profileForm.postalAddress),
    residentialAddress: nullableString(profileForm.residentialAddress),
    expectedVersion: Number(profileForm.expectedVersion),
  }
}

function openKin(record?: ApplicantNextOfKin) {
  editingId.value = record?.id ?? null
  Object.assign(kinForm, record ? { ...record, email: record.email ?? '', address: record.address ?? '' } : { fullName: '', relationshipCode: '', phoneNumber: '', email: '', address: '', primary: false, expectedVersion: 0 })
  inlineEditor.value = 'kin'
  focusInlineEditor(record != null)
}

function openEmployment(record?: ApplicantEmploymentHistory) {
  editingId.value = record?.id ?? null
  Object.assign(employmentForm, record ? { ...record, endedOn: record.endedOn ?? '', responsibilities: record.responsibilities ?? '' } : { employerName: '', positionTitle: '', startedOn: '', endedOn: '', current: false, responsibilities: '', expectedVersion: 0 })
  inlineEditor.value = 'employment'
  focusInlineEditor(record != null)
}

function openReferee(record?: ApplicantReferee) {
  editingId.value = record?.id ?? null
  Object.assign(refereeForm, record ? { ...record, title: record.title ?? '', positionTitle: record.positionTitle ?? '', phoneNumber: record.phoneNumber ?? '', expectedVersion: record.version } : { fullName: '', title: '', organisation: '', positionTitle: '', email: '', phoneNumber: '', expectedVersion: 0 })
  inlineEditor.value = 'referee'
  focusInlineEditor(record != null)
}

function openQualification(record?: ApplicantQualificationSitting) {
  editingId.value = record?.id ?? null
  Object.assign(qualificationForm, record ? {
    level: record.level, examBodyId: record.examBody?.id ?? '', institutionName: record.institutionName ?? '', centreNumber: record.centreNumber ?? '',
    candidateNumber: record.candidateNumber ?? '', yearWritten: record.yearWritten ?? new Date().getFullYear(), countryId: record.countryId ?? '', documentId: record.documentId ?? '', expectedVersion: record.version
  } : { level: 'O_LEVEL', examBodyId: '', institutionName: '', centreNumber: '', candidateNumber: '', yearWritten: new Date().getFullYear(), countryId: '', documentId: '', expectedVersion: 0 })
  inlineEditor.value = 'qualification'
  focusInlineEditor(record != null)
}

function openResult(sitting: ApplicantQualificationSitting, record?: ApplicantQualificationResult) {
  selectedSittingId.value = sitting.id
  editingId.value = record?.id ?? null
  resultForms.value = [createResultDraft(record)]
  if (resultForms.value[0]!.grade && !gradeItemsForQualificationLevel(sitting.level).includes(resultForms.value[0]!.grade)) {
    resultForms.value[0]!.grade = ''
  }
  inlineEditor.value = 'result'
  focusInlineEditor(true)
}

function createResultDraft(record?: ApplicantQualificationResult): QualificationResultDraft {
  return {
    clientId: nextResultDraftId++,
    subjectId: record?.subject?.id ?? '',
    grade: record?.grade ?? '',
    principalSubject: record?.principalSubject ?? false,
    expectedVersion: record?.version ?? 0,
  }
}

function addResultDraft() {
  resultForms.value.push(createResultDraft())
}

function removeResultDraft(index: number) {
  if (resultForms.value.length === 1) return
  resultForms.value.splice(index, 1)
}

function subjectItemsForResultDraft(index: number) {
  const currentSubjectId = resultForms.value[index]?.subjectId
  const alreadyCapturedSubjectIds = new Set(
    selectedResultSitting.value?.results
      .filter(result => result.id !== editingId.value)
      .map(result => result.subject?.id)
      .filter((subjectId): subjectId is string => Boolean(subjectId)) ?? [],
  )
  const otherDraftSubjectIds = new Set(
    resultForms.value
      .filter((_, resultIndex) => resultIndex !== index)
      .map(result => result.subjectId)
      .filter(Boolean),
  )
  return subjectItems.value.filter(item => item.value === currentSubjectId
    || (!alreadyCapturedSubjectIds.has(item.value) && !otherDraftSubjectIds.has(item.value)))
}

function focusInlineEditor(shouldFocus: boolean) {
  if (!shouldFocus) return
  nextTick(() => document.getElementById('inline-record-editor')?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

function gradeItemsForQualificationLevel(level: string | null | undefined): string[] {
  return level === 'O_LEVEL' ? oLevelGradeItems : aLevelGradeItems
}

async function saveInlineRecord() {
  working.value = true
  try {
    let path = ''
    let body: Record<string, unknown> = {}
    const savedEditor = inlineEditor.value
    const savedSittingId = selectedSittingId.value
    if (inlineEditor.value === 'kin') {
      path = `/api/admissions/applications/${applicationId.value}/next-of-kin${editingId.value ? `/${editingId.value}` : ''}`
      body = nullableBody(kinForm)
    } else if (inlineEditor.value === 'employment') {
      path = `/api/admissions/applications/${applicationId.value}/employment-history${editingId.value ? `/${editingId.value}` : ''}`
      body = nullableBody(employmentForm)
    } else if (inlineEditor.value === 'referee') {
      path = `/api/admissions/applications/${applicationId.value}/referees${editingId.value ? `/${editingId.value}` : ''}`
      body = nullableBody(refereeForm)
    } else if (inlineEditor.value === 'qualification') {
      path = `/api/admissions/applications/${applicationId.value}/qualifications${editingId.value ? `/${editingId.value}` : ''}`
      body = nullableBody(qualificationForm)
    } else if (inlineEditor.value === 'result' && selectedSittingId.value) {
      if (editingId.value) {
        path = `/api/admissions/applications/${applicationId.value}/qualifications/${selectedSittingId.value}/results/${editingId.value}`
        const result = resultForms.value[0]!
        body = {
          subjectId: result.subjectId,
          grade: result.grade,
          principalSubject: result.principalSubject,
          expectedVersion: result.expectedVersion,
        }
      } else {
        path = `/api/admissions/applications/${applicationId.value}/qualifications/${selectedSittingId.value}/results/batch`
        body = {
          results: resultForms.value.map(result => ({
            subjectId: result.subjectId,
            grade: result.grade,
            principalSubject: result.principalSubject,
          })),
        }
      }
    } else return
    const updated = await api.request<ApplicantApplicationWorkspace>(path, { method: editingId.value ? 'PUT' : 'POST', body })
    applyWorkspace(updated)
    if (savedEditor === 'result' && savedSittingId) {
      const updatedSitting = updated.qualifications.find(sitting => sitting.id === savedSittingId)
      if (updatedSitting) openResult(updatedSitting)
      else openQualification()
    } else {
      prepareInlineEditorForSection(activeSectionCode.value)
    }
    toast.add({ title: 'Draft saved', color: 'success', icon: 'i-lucide-check' })
  } catch (error) {
    await showError('Record could not be saved', api.errorMessage(error))
  } finally {
    working.value = false
  }
}

async function resendRefereeInvitation(record: ApplicantReferee) {
  working.value = true
  try {
    const updated = await api.request<ApplicantApplicationWorkspace>(
      `/api/admissions/applications/${applicationId.value}/referees/${record.id}/invitation?expectedVersion=${record.version}`,
      { method: 'POST' },
    )
    applyWorkspace(updated)
    toast.add({
      title: 'Reference invitation sent',
      description: `A new secure reference request was sent to ${record.email}.`,
      color: 'success',
      icon: 'i-lucide-mail-check',
    })
  } catch (error) {
    await showError('Reference invitation could not be sent', api.errorMessage(error))
  } finally {
    working.value = false
  }
}

async function removeRecord(kind: 'next-of-kin' | 'employment-history' | 'referees' | 'qualifications', id: string, version: number) {
  const confirmed = await confirmAction({ title: 'Remove this draft record?', text: 'The record will remain in the audit history.', confirmButtonText: 'Remove', destructive: true })
  if (!confirmed) return
  try {
    const updated = await api.request<ApplicantApplicationWorkspace>(`/api/admissions/applications/${applicationId.value}/${kind}/${id}?expectedVersion=${version}`, { method: 'DELETE' })
    applyWorkspace(updated)
    prepareInlineEditorForSection(activeSectionCode.value)
  } catch (error) {
    await showError('Record could not be removed', api.errorMessage(error))
  }
}

async function saveProgrammeChoices() {
  if (!programmeIds.value.length) return
  working.value = true
  try {
    applyWorkspace(await api.request<ApplicantApplicationWorkspace>(`/api/admissions/applications/${applicationId.value}/programme-choices`, {
      method: 'PUT', body: { programmeIds: programmeIds.value }
    }))
    await showSuccess('Programme choices saved', 'The displayed order is your preference ranking.')
  } catch (error) {
    await showError('Programme choices could not be saved', api.errorMessage(error))
  } finally {
    working.value = false
  }
}

function openDocumentUpload(requirement?: ApplicationDocumentRequirementState) {
  documentForm.requirementCode = requirement?.requirementCode ?? uploadableRequirements.value[0]?.value ?? ''
  documentForm.file = null
  inlineEditor.value = 'document'
  focusInlineEditor(requirement != null)
}

async function uploadDocument() {
  const file = Array.isArray(documentForm.file) ? documentForm.file[0] : documentForm.file
  const requirement = workspace.value?.documents.requirements.find(item => item.requirementCode === documentForm.requirementCode)
  if (!file || !requirement) return
  working.value = true
  try {
    const formData = new FormData()
    formData.append('ownerType', 'APPLICATION')
    formData.append('ownerId', applicationId.value)
    formData.append('documentTypeCode', requirement.requirementCode)
    if (requirement.state === 'REJECTED' && requirement.documentId) formData.append('replacesDocumentId', requirement.documentId)
    formData.append('file', file)
    const uploaded = await api.request<UploadedDocumentSummary>('/api/documents/uploads', { method: 'POST', body: formData })
    await api.request(`/api/admissions/applications/${applicationId.value}/documents`, { method: 'POST', body: { documentId: uploaded.id, requirementCode: requirement.requirementCode } })
    await loadWorkspace()
    await showSuccess('Document uploaded', 'The evidence is pending independent staff verification.')
  } catch (error) {
    await showError('Document could not be uploaded', api.errorMessage(error))
  } finally {
    working.value = false
  }
}

async function acceptDeclaration() {
  const confirmed = await confirmAction({ title: 'Accept the declaration?', text: 'You confirm that the application is complete and the information supplied is accurate.', confirmButtonText: 'Accept declaration' })
  if (!confirmed) return
  try {
    applyWorkspace(await api.request<ApplicantApplicationWorkspace>(`/api/admissions/applications/${applicationId.value}/declaration`, {
      method: 'PUT', body: { accepted: true, declarationVersion: '2026.1' }
    }))
  } catch (error) {
    await showError('Declaration could not be recorded', api.errorMessage(error))
  }
}

async function submitApplication() {
  if (!workspace.value?.readyForSubmission) return
  const confirmed = await confirmAction({
    title: 'Submit this application?',
    text: 'Your draft will be locked and sent to Admissions for independent verification. Corrections after submission require an audited staff action.',
    confirmButtonText: 'Submit application'
  })
  if (!confirmed) return
  working.value = true
  try {
    await api.request(`/api/admissions/applications/${applicationId.value}/submission`, { method: 'POST' })
    await showSuccess('Application submitted', 'Admissions can now review your application.')
    await router.push('/')
  } catch (error) {
    await showError('Application could not be submitted', api.errorMessage(error))
  } finally {
    working.value = false
  }
}

async function previewApplicationDocument(requirement: ApplicationDocumentRequirementState) {
  if (!requirement.documentId) return
  selectedReviewDocument.value = requirement
  reviewDocumentDownload.value = null
  reviewDocumentError.value = ''
  loadingReviewDocumentId.value = requirement.documentId
  try {
    reviewDocumentDownload.value = await api.request<UploadedDocumentDownload>(
      `/api/documents/uploads/${requirement.documentId}/download`,
    )
    nextTick(() => document.getElementById('application-document-preview')?.scrollIntoView({ behavior: 'smooth', block: 'center' }))
  } catch (error) {
    reviewDocumentError.value = api.errorMessage(error, 'The document preview could not be loaded.')
  } finally {
    loadingReviewDocumentId.value = null
  }
}

function closeApplicationDocumentPreview() {
  selectedReviewDocument.value = null
  reviewDocumentDownload.value = null
  reviewDocumentError.value = ''
}

function downloadReviewDocument() {
  if (!reviewDocumentDownload.value) return
  const link = window.document.createElement('a')
  link.href = reviewDocumentDownload.value.downloadUrl
  link.target = '_blank'
  link.rel = 'noopener noreferrer'
  link.download = reviewDocumentDownload.value.originalFileName
  link.click()
}

function countryName(countryId: string | null | undefined) {
  if (!countryId) return null
  const country = countries.value.find(item => item.id === countryId)
  return country ? `${country.iso2Code} · ${country.name}` : countryId
}

function formatReviewDate(value: string | null | undefined) {
  if (!value) return null
  const date = /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(`${value}T00:00:00`) : new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('en-ZW', { dateStyle: 'medium' }).format(date)
}

function yesOrNo(value: boolean | null | undefined) {
  return value ? 'Yes' : 'No'
}

function sectionTone(status: string) {
  if (['COMPLETE', 'VERIFIED'].includes(status)) return 'success' as const
  if (['REJECTED', 'CORRECTION_REQUIRED'].includes(status)) return 'error' as const
  if (status === 'IN_PROGRESS') return 'warning' as const
  return 'neutral' as const
}

function formatStatus(value: string) {
  return value.toLowerCase().replaceAll('_', ' ').replace(/(^|\s)\S/g, character => character.toUpperCase())
}
</script>

<template>
  <div class="min-h-screen bg-slate-50">
    <EmhareTopNav :breadcrumbs="[{ label: 'Applications', to: '/' }, { label: workspace?.application.applicationNumber ?? 'Application workspace' }]">
      <template #meta>
        <EmhareDraftSaveIndicator v-if="isDraft" :state="profileSaveState" :saved-at="lastSavedAt" />
        <EmhareStatusPill v-if="workspace" :label="formatStatus(workspace.application.status)" :tone="workspace.application.status === 'DRAFT' ? 'warning' : 'info'" />
      </template>
      <template #actions>
        <UButton v-if="isDraft" label="Save draft" icon="i-lucide-save" color="neutral" variant="outline" @click="saveDraft" />
        <UButton label="Return to applications" icon="i-lucide-arrow-left" color="neutral" variant="ghost" @click="router.push('/')" />
      </template>
    </EmhareTopNav>

    <main class="mx-auto max-w-[80rem] px-4 py-8 sm:px-6 sm:py-10">
      <EmhareFeedbackState v-if="loading" state="loading" title="Loading application workspace" />
      <EmhareFeedbackState v-else-if="loadError" state="error" title="Application unavailable" :description="loadError" action-label="Retry" @action="loadWorkspace(true)" />

      <div v-else-if="workspace" class="grid gap-6 lg:grid-cols-[16rem_minmax(0,1fr)] lg:items-start lg:gap-8">
        <aside class="lg:sticky lg:top-20 lg:self-start">
          <div class="mb-4 rounded-xl border border-slate-200 bg-white p-4">
            <div class="flex items-center justify-between text-sm">
              <span class="font-semibold text-slate-700">Progress</span>
              <span class="font-semibold text-uzgreen-700">{{ progressPercentage }}%</span>
            </div>
            <UProgress class="mt-2" :model-value="progressPercentage" color="primary" size="sm" />
            <p class="mt-2 text-xs text-slate-500">{{ completedJourneyStepCount }} of {{ applicationJourneySections.length }} steps complete</p>
          </div>
          <EmhareVerticalStepper :steps="applicationJourneySections" :current-step="activeSectionCode" label="Application process" @update:current-step="activateSection" />
        </aside>

        <section id="application-section-editor" class="min-w-0 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div class="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 px-6 py-5 sm:px-8">
            <div>
              <div class="flex items-center gap-2"><h1 class="text-lg font-semibold text-slate-900">{{ displaySectionName(activeSection) }}</h1><EmhareStatusPill v-if="activeSection" :label="formatStatus(activeSection.status)" :tone="sectionTone(activeSection.status)" /></div>
              <p class="mt-1 text-sm text-slate-500">{{ activeSection?.completionSummary ?? 'Complete the required information for this section.' }}</p>
            </div>
          </div>

          <div class="p-6 sm:p-8">
            <div v-if="activeSectionCode === 'PERSONAL_DETAILS'" class="space-y-8">
              <UAlert color="primary" variant="soft" icon="i-lucide-save" title="Changes save automatically" :description="profileReadyForAutosave ? 'Registered names are locked and identity duplicates are validated by the server.' : 'Complete the required fields before these applicant details can be saved.'" />

              <EmhareFormSection title="Identity" description="Names and identifying details as they will appear on official documents.">
                <EmhareFormField v-model="profileForm.applicantCategoryCode" type="select" label="Applicant category" :items="[{label:'Local',value:'LOCAL'},{label:'SADC',value:'SADC'},{label:'International',value:'INTERNATIONAL'},{label:'Credit transfer',value:'CLE'}]" required :disabled="!isDraft" />
                <EmhareFormField v-model="profileForm.titleCode" type="select" label="Title" :items="['Mr','Mrs','Ms','Miss','Dr','Prof']" :disabled="!isDraft" />
                <EmhareFormField v-model="profileForm.firstName" label="First name" description="Taken from your registered account." required readonly />
                <EmhareFormField v-model="profileForm.lastName" label="Last name" description="Taken from your registered account." required readonly />
                <EmhareFormField v-model="profileForm.middleNames" label="Middle names" :disabled="!isDraft" />
                <EmhareFormField v-model="profileForm.dateOfBirth" type="date" label="Date of birth" required :disabled="!isDraft" />
                <EmhareFormField v-model="profileForm.genderCode" type="select" label="Gender" :items="['FEMALE','MALE','OTHER','PREFER_NOT_TO_SAY']" required :disabled="!isDraft" />
                <EmhareFormField v-model="profileForm.maritalStatusCode" type="select" label="Marital status" :items="['SINGLE','MARRIED','DIVORCED','WIDOWED']" :disabled="!isDraft" />
                <EmhareFormField v-if="profileForm.applicantCategoryCode === 'LOCAL'" v-model="profileForm.nationalIdNumber" label="National ID number" description="Checked for duplicate applications in this intake." required :disabled="!isDraft" />
                <EmhareFormField v-else v-model="profileForm.passportNumber" label="Passport number" required :disabled="!isDraft" />
              </EmhareFormSection>

              <EmhareFormSection title="Residency and contact" description="Where the applicant lives and how Admissions can reach them.">
                <EmhareFormField v-model="profileForm.countryId" type="searchable-select" label="Country of residence" :items="countryItems" required :disabled="!isDraft" />
                <EmhareFormField v-model="profileForm.nationalityCountryId" type="searchable-select" label="Nationality" :items="countryItems" required :disabled="!isDraft" />
                <EmhareFormField v-model="profileForm.primaryEmail" type="email" label="Email" required :disabled="!isDraft" />
                <EmhareFormField v-model="profileForm.primaryPhone" type="phone" label="Phone number" required :disabled="!isDraft" />
                <EmhareFormField v-model="profileForm.residentialAddress" type="textarea" label="Residential address" required :disabled="!isDraft" />
                <EmhareFormField v-model="profileForm.postalAddress" type="textarea" label="Postal address" :disabled="!isDraft" />
              </EmhareFormSection>

              <EmhareFormSection title="Additional information" description="Only needed where it applies to the applicant.">
                <EmhareFormField v-model="profileForm.disabilityStatusCode" type="select" label="Disability status" :items="[{label:'None',value:'NONE'},{label:'Declared',value:'DECLARED'}]" :disabled="!isDraft" />
                <EmhareFormField v-if="profileForm.disabilityStatusCode === 'DECLARED'" v-model="profileForm.specialNeeds" type="textarea" label="Support requirements" :disabled="!isDraft" />
              </EmhareFormSection>
            </div>

            <div v-else-if="activeSectionCode === 'NEXT_OF_KIN'" class="space-y-4">
              <EmhareInlineRecordForm
                v-if="isDraft && inlineEditor === 'kin'"
                :title="editingId ? 'Edit next of kin' : 'Next of kin details'"
                description="Capture the person Admissions should contact when necessary."
                :show-cancel="Boolean(editingId)"
                :busy="working"
                @cancel="openKin()"
                @submit="saveInlineRecord"
              >
                <div class="grid gap-4 md:grid-cols-2">
                  <EmhareFormField v-model="kinForm.fullName" label="Full name" required />
                  <EmhareFormField v-model="kinForm.relationshipCode" type="select" label="Relationship" :items="['PARENT','SPOUSE','SIBLING','GUARDIAN','RELATIVE','OTHER']" required />
                  <EmhareFormField v-model="kinForm.phoneNumber" type="phone" label="Phone number" required />
                  <EmhareFormField v-model="kinForm.email" type="email" label="Email" />
                  <EmhareFormField v-model="kinForm.address" type="textarea" label="Address" />
                  <EmhareFormField v-model="kinForm.primary" type="toggle" label="Primary contact" />
                </div>
              </EmhareInlineRecordForm>
              <h2 v-if="workspace.nextOfKin.length" class="pt-2 text-base font-semibold text-highlighted">Saved next of kin</h2>
              <EmharePaginatedCollection :items="workspace.nextOfKin" :initial-page-size="5" v-slot="{ items }">
                <div class="space-y-3">
                  <div v-for="record in items" :key="record.id" class="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-muted p-4">
                    <div><p class="font-medium">{{ record.fullName }} <UBadge v-if="record.primary" label="Primary" color="primary" variant="soft" /></p><p class="text-sm text-muted">{{ formatStatus(record.relationshipCode) }} · {{ record.phoneNumber }}</p></div>
                    <div v-if="isDraft" class="flex gap-2"><UButton icon="i-lucide-pencil" label="Edit" color="neutral" variant="ghost" @click="openKin(record)" /><UButton icon="i-lucide-trash-2" label="Remove" color="error" variant="ghost" @click="removeRecord('next-of-kin', record.id, record.version)" /></div>
                  </div>
                </div>
              </EmharePaginatedCollection>
            </div>

            <div v-else-if="activeSectionCode === 'EMPLOYMENT_HISTORY'" class="space-y-4">
              <EmhareInlineRecordForm
                v-if="isDraft && inlineEditor === 'employment'"
                :title="editingId ? 'Edit employment' : 'Employment details'"
                description="Capture one employment record at a time."
                :show-cancel="Boolean(editingId)"
                :busy="working"
                @cancel="openEmployment()"
                @submit="saveInlineRecord"
              >
                <div class="grid gap-4 md:grid-cols-2">
                  <EmhareFormField v-model="employmentForm.employerName" label="Employer" required />
                  <EmhareFormField v-model="employmentForm.positionTitle" label="Position" required />
                  <EmhareFormField v-model="employmentForm.startedOn" type="date" label="Started on" required />
                  <EmhareFormField v-model="employmentForm.current" type="toggle" label="Current employment" />
                  <EmhareFormField v-if="!employmentForm.current" v-model="employmentForm.endedOn" type="date" label="Ended on" />
                  <div class="md:col-span-2"><EmhareFormField v-model="employmentForm.responsibilities" type="textarea" label="Responsibilities" /></div>
                </div>
              </EmhareInlineRecordForm>
              <h2 v-if="workspace.employmentHistory.length" class="pt-2 text-base font-semibold text-highlighted">Saved employment history</h2>
              <EmharePaginatedCollection :items="workspace.employmentHistory" :initial-page-size="5" v-slot="{ items }"><div class="space-y-3"><div v-for="record in items" :key="record.id" class="flex items-center justify-between gap-3 rounded-lg border border-muted p-4"><div><p class="font-medium">{{ record.positionTitle }} · {{ record.employerName }}</p><p class="text-sm text-muted">{{ record.startedOn }} – {{ record.current ? 'Current' : record.endedOn }}</p></div><UButton v-if="isDraft" icon="i-lucide-pencil" label="Edit" color="neutral" variant="ghost" @click="openEmployment(record)" /></div></div></EmharePaginatedCollection>
            </div>

            <div v-else-if="activeSectionCode === 'REFEREES'" class="space-y-4">
              <EmhareInlineRecordForm
                v-if="isDraft && inlineEditor === 'referee'"
                :title="editingId ? 'Edit referee' : 'Referee details'"
                description="Capture a referee. A secure reference request is emailed when the record is saved."
                :show-cancel="Boolean(editingId)"
                :busy="working"
                @cancel="openReferee()"
                @submit="saveInlineRecord"
              >
                <div class="grid gap-4 md:grid-cols-2">
                  <EmhareFormField v-model="refereeForm.title" label="Title" />
                  <EmhareFormField v-model="refereeForm.fullName" label="Full name" required />
                  <EmhareFormField v-model="refereeForm.organisation" label="Organisation" required />
                  <EmhareFormField v-model="refereeForm.positionTitle" label="Position" />
                  <EmhareFormField v-model="refereeForm.email" type="email" label="Email" required />
                  <EmhareFormField v-model="refereeForm.phoneNumber" type="phone" label="Phone number" />
                </div>
              </EmhareInlineRecordForm>
              <h2 v-if="workspace.referees.length" class="pt-2 text-base font-semibold text-highlighted">Saved referees</h2>
              <EmharePaginatedCollection :items="workspace.referees" :initial-page-size="5" v-slot="{ items }">
                <div class="space-y-3">
                  <div v-for="record in items" :key="record.id" class="flex flex-col gap-3 rounded-lg border border-muted p-4 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <p class="font-medium">{{ record.fullName }}</p>
                      <p class="text-sm text-muted">{{ record.organisation }} · {{ record.email }}</p>
                      <p v-if="record.invitedAt" class="mt-1 text-xs text-muted">
                        {{ record.invitationStatus === 'SUBMITTED' ? 'Reference received' : 'Invitation sent' }}
                        · {{ formatReviewDate(record.referenceSubmittedAt || record.invitedAt) }}
                      </p>
                    </div>
                    <div class="flex flex-wrap items-center gap-2">
                      <EmhareStatusPill
                        :label="record.invitationStatus === 'SUBMITTED' ? 'Reference received' : formatStatus(record.invitationStatus)"
                        :tone="record.invitationStatus === 'SUBMITTED' ? 'success' : record.invitationStatus === 'EXPIRED' || record.invitationStatus === 'REVOKED' ? 'error' : 'warning'"
                      />
                      <UButton
                        v-if="isDraft && record.invitationStatus !== 'SUBMITTED'"
                        icon="i-lucide-send"
                        :label="record.invitationStatus === 'NOT_SENT' ? 'Send invitation' : 'Resend'"
                        color="primary"
                        variant="soft"
                        :loading="working"
                        @click="resendRefereeInvitation(record)"
                      />
                      <UButton v-if="isDraft" icon="i-lucide-pencil" label="Edit" color="neutral" variant="ghost" @click="openReferee(record)" />
                    </div>
                  </div>
                </div>
              </EmharePaginatedCollection>
            </div>

            <div v-else-if="activeSectionCode === 'QUALIFICATIONS'" class="space-y-5">
              <EmhareInlineRecordForm
                v-if="isDraft && inlineEditor === 'qualification'"
                :title="editingId ? 'Edit qualification sitting' : 'Qualification sitting'"
                description="Capture the examination sitting before adding its subject results."
                :show-cancel="Boolean(editingId)"
                :busy="working"
                @cancel="openQualification()"
                @submit="saveInlineRecord"
              >
                <div class="grid gap-4 md:grid-cols-2">
                  <EmhareFormField v-model="qualificationForm.level" type="select" label="Qualification level" :items="[{label:'O Level',value:'O_LEVEL'},{label:'A Level',value:'A_LEVEL'},{label:'Diploma',value:'DIPLOMA'},{label:'Degree',value:'DEGREE'},{label:'Other',value:'OTHER'}]" required />
                  <EmhareFormField v-model="qualificationForm.examBodyId" type="searchable-select" label="Exam body" :items="examBodyItems" :required="['O_LEVEL', 'A_LEVEL'].includes(qualificationForm.level)" />
                  <EmhareFormField v-model="qualificationForm.institutionName" label="School or institution" />
                  <EmhareFormField v-model="qualificationForm.yearWritten" type="number" label="Year written" :min="1900" :max="2200" required />
                  <EmhareFormField v-model="qualificationForm.centreNumber" label="Centre number" />
                  <EmhareFormField v-model="qualificationForm.candidateNumber" label="Candidate number" />
                  <EmhareFormField v-model="qualificationForm.countryId" type="searchable-select" label="Country" :items="countryItems" />
                </div>
              </EmhareInlineRecordForm>

              <EmhareInlineRecordForm
                v-else-if="isDraft && inlineEditor === 'result'"
                :title="editingId ? 'Edit subject result' : 'Add subject results'"
                :description="selectedResultSitting ? `${formatStatus(selectedResultSitting.level)} · ${selectedResultSitting.yearWritten}. Add all subjects for this sitting, then save them together.` : 'Capture managed subjects and grades.'"
                :submit-label="editingId ? 'Save result' : `Save ${resultForms.length} subject${resultForms.length === 1 ? '' : 's'}`"
                :show-cancel="true"
                :submit-disabled="!resultBatchReady"
                cancel-label="Back to qualification sitting"
                :busy="working"
                @cancel="openQualification()"
                @submit="saveInlineRecord"
              >
                <div class="space-y-4">
                  <div
                    v-for="(result, resultIndex) in resultForms"
                    :key="result.clientId"
                    class="rounded-lg border border-muted bg-default p-4"
                  >
                    <div class="mb-4 flex items-center justify-between gap-3">
                      <p class="font-medium text-highlighted">Subject {{ resultIndex + 1 }}</p>
                      <UButton
                        v-if="!editingId && resultForms.length > 1"
                        :aria-label="`Remove subject ${resultIndex + 1}`"
                        icon="i-lucide-trash-2"
                        color="error"
                        variant="ghost"
                        @click="removeResultDraft(resultIndex)"
                      />
                    </div>
                    <div class="grid gap-4 md:grid-cols-2">
                      <EmhareFormField v-model="result.subjectId" type="searchable-select" label="Managed subject" :items="subjectItemsForResultDraft(resultIndex)" required />
                      <EmhareFormField v-model="result.grade" type="select" label="Grade" :items="resultGradeItems" required />
                      <EmhareFormField v-if="resultQualificationLevel === 'A_LEVEL'" v-model="result.principalSubject" type="toggle" label="Principal subject" />
                    </div>
                  </div>

                  <UButton
                    v-if="!editingId"
                    label="Add another subject"
                    icon="i-lucide-plus"
                    color="neutral"
                    variant="outline"
                    @click="addResultDraft"
                  />

                  <UAlert color="info" variant="soft" title="ZIMSEC points are automatic" description="A Level grades are calculated on submission: A = 5, B = 4, C = 3, D = 2, E = 1." />
                </div>
              </EmhareInlineRecordForm>

              <h2 v-if="workspace.qualifications.length" class="pt-2 text-base font-semibold text-highlighted">Saved qualifications and results</h2>
              <EmharePaginatedCollection :items="workspace.qualifications" :initial-page-size="5" v-slot="{ items }">
                <div class="space-y-4">
                  <article v-for="sitting in items" :key="sitting.id" class="overflow-hidden rounded-lg border border-muted">
                    <header class="flex flex-wrap items-center justify-between gap-3 bg-muted/30 p-4"><div><p class="font-semibold">{{ formatStatus(sitting.level) }} · {{ sitting.examBody?.name ?? sitting.institutionName }}</p><p class="text-sm text-muted">{{ sitting.yearWritten }} · Candidate {{ sitting.candidateNumber || 'not supplied' }}</p></div><div class="flex items-center gap-2"><EmhareStatusPill :label="formatStatus(sitting.verificationStatus)" :tone="sitting.verificationStatus === 'VERIFIED' ? 'success' : sitting.verificationStatus === 'REJECTED' ? 'error' : 'warning'" /><UButton v-if="isDraft" icon="i-lucide-pencil" label="Edit" color="neutral" variant="ghost" @click="openQualification(sitting)" /><UButton v-if="isDraft" icon="i-lucide-plus" label="Add result" @click="openResult(sitting)" /></div></header>
                    <div class="divide-y divide-muted"><div v-for="result in sitting.results" :key="result.id" class="flex items-center justify-between gap-3 px-4 py-3"><div><span class="font-medium">{{ result.subjectNameSnapshot }}</span><span class="ml-3 text-sm text-muted">Grade {{ result.grade }}<template v-if="result.points != null"> · {{ result.points }} points</template></span></div><UButton v-if="isDraft" icon="i-lucide-pencil" label="Edit" color="neutral" variant="ghost" @click="openResult(sitting, result)" /></div><p v-if="!sitting.results.length" class="p-4 text-sm text-muted">No subject results captured.</p></div>
                  </article>
                </div>
              </EmharePaginatedCollection>
            </div>

            <div v-else-if="activeSectionCode === 'PROGRAMME_CHOICES'" class="space-y-5">
              <UAlert color="primary" variant="soft" icon="i-lucide-list-ordered" title="Preference order matters" description="Select Programmes in first-to-last preference order. The server validates intake eligibility and maximum choices." />
              <EmhareFormField v-model="programmeIds" type="multi-select" label="Programme choices" :items="programmeItems" required :disabled="!isDraft" placeholder="Search Programmes" />
              <ol class="space-y-2"><li v-for="(programmeId, index) in programmeIds" :key="programmeId" class="flex items-start gap-3 rounded-lg border border-muted px-4 py-3"><UBadge :label="String(index + 1)" color="primary" /><div><p class="font-medium">{{ programmeItems.find(item => item.value === programmeId)?.label }}</p><p class="mt-0.5 text-sm text-muted">{{ programmeItems.find(item => item.value === programmeId)?.description }}</p></div></li></ol>
              <div class="flex justify-end"><UButton v-if="isDraft" label="Save choices" icon="i-lucide-save" :loading="working" :disabled="!programmeIds.length" @click="saveProgrammeChoices" /></div>
            </div>

            <div v-else-if="activeSectionCode === 'DOCUMENTS'" class="space-y-4">
              <template v-if="workspace.documents.requirements.length">
                <EmhareInlineRecordForm
                  v-if="isDraft && uploadableRequirements.length && inlineEditor === 'document'"
                  title="Upload supporting evidence"
                  description="Select a requirement and attach its evidence directly in this section."
                  submit-label="Upload evidence"
                  submit-icon="i-lucide-upload"
                  :busy="working"
                  @submit="uploadDocument"
                >
                  <div class="grid gap-4 md:grid-cols-2">
                    <EmhareFormField v-model="documentForm.requirementCode" type="select" label="Document requirement" :items="uploadableRequirements" required />
                    <EmhareFormField v-model="documentForm.file" type="drop-file" label="Document file" required />
                  </div>
                </EmhareInlineRecordForm>
                <h2 class="pt-2 text-base font-semibold text-highlighted">Document requirements</h2>
                <div class="space-y-3"><div v-for="requirement in workspace.documents.requirements" :key="requirement.requirementCode" class="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-muted p-4"><div><p class="font-medium">{{ requirement.requirementName }} <span v-if="requirement.required" class="text-error">*</span></p><p class="text-sm text-muted">{{ requirement.fileName ?? 'No file uploaded' }}<template v-if="requirement.rejectionReason"> · {{ requirement.rejectionReason }}</template></p></div><div class="flex items-center gap-2"><EmhareStatusPill :label="formatStatus(requirement.state)" :tone="requirement.state === 'VERIFIED' ? 'success' : requirement.state === 'PENDING' ? 'warning' : 'error'" /><UButton v-if="isDraft && ['MISSING','REJECTED'].includes(requirement.state)" label="Upload" icon="i-lucide-upload" color="neutral" variant="outline" @click="openDocumentUpload(requirement)" /></div></div></div>
              </template>
              <EmhareFeedbackState
                v-else
                state="empty"
                title="Document requirements are not configured"
                description="Admissions must configure required document evidence for this application type before the application can be submitted."
              />
            </div>

            <div v-else-if="activeSectionCode === 'PAYMENT'" class="space-y-4">
              <UAlert v-if="!workspace.application.paymentRequired" color="success" variant="soft" icon="i-lucide-circle-check" title="No application fee required" />
              <template v-else>
                <section class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
                  <div class="grid gap-5 px-5 py-5 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center sm:px-6">
                    <div class="flex items-center gap-4">
                      <span class="flex size-11 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
                        <UIcon name="i-lucide-receipt-text" class="size-5" />
                      </span>
                      <div class="min-w-0">
                        <p class="text-sm font-medium text-slate-500">Application fee</p>
                        <p class="mt-1 text-2xl font-bold tracking-tight text-slate-950">
                          {{ workspace.application.payment ? `${workspace.application.payment.currencyCode} ${workspace.application.payment.amountDue}` : 'Pending' }}
                        </p>
                      </div>
                    </div>
                    <div class="grid gap-1 sm:text-right">
                      <p class="text-xs font-semibold uppercase tracking-[0.14em] text-slate-400">Payment reference</p>
                      <p class="font-mono text-sm font-semibold text-slate-800">{{ workspace.application.payment?.reference ?? 'Pending' }}</p>
                      <EmhareStatusPill class="mt-1 sm:ml-auto" :label="formatStatus(workspace.application.paymentClearanceStatus)" :tone="workspace.application.paymentClearanceStatus === 'PAID' ? 'success' : 'warning'" />
                    </div>
                  </div>
                </section>

                <UAlert
                  v-if="workspace.application.paymentClearanceStatus === 'UNRATED'"
                  color="warning"
                  variant="soft"
                  icon="i-lucide-badge-dollar-sign"
                  title="Awaiting an effective exchange rate"
                  description="The transaction remains unrated until Finance supplies a valid effective rate. The system never assumes a rate of 1."
                />

                <UAlert
                  v-if="workspace.application.paymentClearanceStatus === 'PAID'"
                  color="success"
                  variant="soft"
                  icon="i-lucide-circle-check"
                  title="Application fee confirmed"
                  description="Finance has reconciled this payment. No further payment action is required."
                />

                <template v-else>
                  <section
                    v-if="paymentCheckoutOpen && paymentCheckout"
                    ref="paymentCheckoutPanel"
                    class="scroll-mt-6 overflow-hidden rounded-2xl border border-primary/25 bg-white shadow-sm"
                  >
                    <header class="flex flex-wrap items-start justify-between gap-4 border-b border-slate-200 bg-primary/5 px-5 py-4 sm:px-6">
                      <div class="flex items-start gap-3">
                        <span class="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary text-white">
                          <UIcon name="i-lucide-lock-keyhole" class="size-5" />
                        </span>
                        <div>
                          <p class="text-xs font-bold uppercase tracking-[0.16em] text-primary">Secure card payment</p>
                          <h2 class="mt-1 text-lg font-semibold text-slate-950">
                            Pay {{ workspace.application.payment?.currencyCode }} {{ workspace.application.payment?.amountDue }}
                          </h2>
                          <p class="mt-1 text-sm text-slate-600">Complete the payment form below without leaving your application.</p>
                        </div>
                      </div>
                      <UButton
                        color="neutral"
                        variant="ghost"
                        icon="i-lucide-x"
                        label="Cancel payment"
                        @click="cancelOnlinePayment"
                      />
                    </header>
                    <div class="bg-slate-50 p-3 sm:p-5">
                      <div class="overflow-hidden rounded-xl border border-slate-200 bg-white">
                        <iframe
                          ref="paymentCheckoutFrame"
                          :src="paymentCheckout.embeddedCheckoutUrl"
                          title="Secure card payment"
                          class="h-[46rem] min-h-[40rem] w-full border-0 bg-white"
                          allow="payment"
                          @load="postPaymentRequestToCheckout"
                        />
                      </div>
                    </div>
                    <footer class="flex items-center gap-2 border-t border-slate-200 px-5 py-3 text-xs text-slate-500 sm:px-6">
                      <UIcon name="i-lucide-shield-check" class="size-4 text-primary" />
                      Card details are handled securely by the bank and are not stored by eMhare.
                    </footer>
                  </section>

                  <section v-else class="overflow-hidden rounded-2xl border border-primary/25 bg-gradient-to-br from-primary/10 via-white to-white shadow-sm">
                    <div class="grid gap-6 p-5 sm:p-6 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-center">
                      <div>
                        <div class="flex items-start gap-4">
                          <span class="flex size-11 shrink-0 items-center justify-center rounded-xl bg-primary text-white shadow-sm">
                            <UIcon name="i-lucide-credit-card" class="size-5" />
                          </span>
                          <div>
                            <p class="text-xs font-bold uppercase tracking-[0.16em] text-primary">Recommended</p>
                            <h2 class="mt-1 text-lg font-semibold text-slate-950">Pay online</h2>
                            <p class="mt-1 max-w-xl text-sm leading-6 text-slate-600">Complete a secure card payment inside your application and receive confirmation without uploading a receipt.</p>
                          </div>
                        </div>
                        <div class="mt-5 grid gap-3 text-sm text-slate-600 sm:grid-cols-3">
                          <span class="flex items-center gap-2"><UIcon name="i-lucide-shield-check" class="size-4 text-primary" /> Secure checkout</span>
                          <span class="flex items-center gap-2"><UIcon name="i-lucide-lock-keyhole" class="size-4 text-primary" /> Card details stay private</span>
                          <span class="flex items-center gap-2"><UIcon name="i-lucide-monitor-check" class="size-4 text-primary" /> No site redirect</span>
                        </div>
                      </div>
                      <div class="lg:min-w-48">
                        <USkeleton v-if="paymentDetailsLoading" class="h-11 w-full" />
                        <template v-else-if="paymentOptions?.onlinePayment">
                          <UButton
                            v-if="paymentOptions.onlinePayment.available"
                            block
                            size="lg"
                            icon="i-lucide-lock-keyhole"
                            :label="workspace.application.payment ? `Pay ${workspace.application.payment.currencyCode} ${workspace.application.payment.amountDue} now` : 'Pay now'"
                            :loading="working"
                            @click="startOnlinePayment"
                          />
                          <UButton
                            v-if="paymentOptions.onlinePayment.available"
                            class="mt-2"
                            block
                            color="neutral"
                            variant="outline"
                            icon="i-lucide-refresh-cw"
                            label="Check payment status"
                            :loading="paymentReconciliationLoading"
                            @click="checkPaymentStatus"
                          />
                          <UAlert
                            v-else
                            color="neutral"
                            variant="soft"
                            icon="i-lucide-clock-3"
                            title="Online payment unavailable"
                            :description="paymentOptions.onlinePayment.availabilityMessage"
                          />
                        </template>
                      </div>
                    </div>
                  </section>

                  <div v-if="!paymentCheckoutOpen" class="flex items-center gap-4 py-1" aria-hidden="true">
                    <span class="h-px flex-1 bg-slate-200" />
                    <span class="text-xs font-bold uppercase tracking-[0.16em] text-slate-400">Or pay by bank transfer</span>
                    <span class="h-px flex-1 bg-slate-200" />
                  </div>

                  <section v-if="!paymentCheckoutOpen" class="rounded-2xl border border-slate-200 bg-slate-50/70 p-5 sm:p-6">
                    <div class="grid gap-5 lg:grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)] lg:items-end">
                      <div>
                        <div class="flex items-center gap-3">
                          <span class="flex size-10 items-center justify-center rounded-xl bg-white text-slate-600 shadow-sm ring-1 ring-slate-200">
                            <UIcon name="i-lucide-landmark" class="size-5" />
                          </span>
                          <div>
                            <h2 class="font-semibold text-slate-900">Already paid by bank?</h2>
                            <p class="mt-1 text-sm leading-6 text-slate-600">Upload your deposit or transfer receipt. Finance will verify it separately.</p>
                          </div>
                        </div>
                      </div>
                      <div class="grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-end">
                        <EmhareFormField
                          v-model="paymentProofForm.file"
                          type="drop-file"
                          label="Proof of payment"
                          description="PDF, PNG, or JPEG"
                          accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
                          required
                        />
                        <UButton
                          class="sm:mb-px"
                          size="lg"
                          icon="i-lucide-upload"
                          label="Upload proof"
                          :loading="working"
                          :disabled="!paymentProofForm.file"
                          @click="uploadPaymentProof"
                        />
                      </div>
                    </div>
                  </section>
                </template>

                <section v-if="paymentProofDocuments.length" class="overflow-hidden rounded-xl border border-muted">
                  <header class="border-b border-muted bg-elevated/40 px-5 py-4">
                    <h2 class="font-semibold text-highlighted">Uploaded payment evidence</h2>
                    <p class="mt-1 text-sm text-muted">Finance reviews this evidence separately from application documents.</p>
                  </header>
                  <div class="divide-y divide-muted">
                    <div v-for="document in paymentProofDocuments" :key="document.id" class="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
                      <div>
                        <p class="font-medium text-highlighted">{{ document.originalFileName }}</p>
                        <p class="mt-1 text-sm text-muted">Uploaded {{ formatReviewDate(document.uploadedAt) }}<template v-if="document.rejectionReason"> · {{ document.rejectionReason }}</template></p>
                      </div>
                      <EmhareStatusPill
                        :label="formatStatus(document.verificationStatus)"
                        :tone="document.verificationStatus === 'VERIFIED' ? 'success' : document.verificationStatus === 'REJECTED' ? 'error' : 'warning'"
                      />
                    </div>
                  </div>
                </section>
              </template>
            </div>

            <div v-else-if="activeSectionCode === 'REVIEW_DECLARATION'" class="space-y-6">
              <UAlert :color="workspace.readyForSubmission ? 'success' : 'warning'" variant="soft" :icon="workspace.readyForSubmission ? 'i-lucide-circle-check' : 'i-lucide-triangle-alert'" :title="workspace.readyForSubmission ? 'Ready for submission' : 'Application is not ready'" :description="workspace.readyForSubmission ? 'All server-side submission gates have passed.' : 'Resolve every item below before submitting.'" />
              <div v-if="workspace.missingRequirements.length" class="rounded-lg border border-warning/40 bg-warning/5 p-4"><h2 class="font-semibold">Missing requirements</h2><ul class="mt-3 list-disc space-y-2 pl-5 text-sm"><li v-for="requirement in workspace.missingRequirements" :key="requirement">{{ requirement }}</li></ul></div>

              <section class="overflow-hidden rounded-xl border border-muted">
                <header class="flex items-center gap-3 border-b border-muted bg-elevated/40 px-5 py-4">
                  <UIcon name="i-lucide-file-text" class="size-5 text-primary" />
                  <div><h2 class="font-semibold text-highlighted">Application overview</h2><p class="text-sm text-muted">The application record that will be submitted to Admissions.</p></div>
                </header>
                <dl class="grid gap-x-6 gap-y-5 p-5 md:grid-cols-2">
                  <EmhareReviewField label="Application number" :value="workspace.application.applicationNumber" />
                  <EmhareReviewField label="Application status" :value="formatStatus(workspace.application.status)" />
                  <EmhareReviewField label="Application type" :value="workspace.application.applicationTypeName" />
                  <EmhareReviewField label="Intake" :value="workspace.application.intakeCode" />
                  <EmhareReviewField label="Applicant number" :value="workspace.application.applicantNumber" />
                  <EmhareReviewField label="Calculated total points" :value="workspace.application.calculatedTotalPoints" />
                </dl>
              </section>

              <section class="overflow-hidden rounded-xl border border-muted">
                <header class="flex items-center gap-3 border-b border-muted bg-elevated/40 px-5 py-4">
                  <UIcon name="i-lucide-contact-round" class="size-5 text-primary" />
                  <div><h2 class="font-semibold text-highlighted">Applicant details</h2><p class="text-sm text-muted">Identity, residency, and contact information.</p></div>
                </header>
                <dl class="grid gap-x-6 gap-y-5 p-5 md:grid-cols-2">
                  <EmhareReviewField label="Applicant category" :value="formatStatus(workspace.profile.applicantCategoryCode)" />
                  <EmhareReviewField label="Title" :value="workspace.profile.titleCode" />
                  <EmhareReviewField label="First name" :value="workspace.profile.firstName" />
                  <EmhareReviewField label="Middle names" :value="workspace.profile.middleNames" />
                  <EmhareReviewField label="Last name" :value="workspace.profile.lastName" />
                  <EmhareReviewField label="Date of birth" :value="formatReviewDate(workspace.profile.dateOfBirth)" />
                  <EmhareReviewField label="Gender" :value="workspace.profile.genderCode ? formatStatus(workspace.profile.genderCode) : null" />
                  <EmhareReviewField label="Marital status" :value="workspace.profile.maritalStatusCode ? formatStatus(workspace.profile.maritalStatusCode) : null" />
                  <EmhareReviewField label="National ID number" :value="workspace.profile.nationalIdNumber" />
                  <EmhareReviewField label="Passport number" :value="workspace.profile.passportNumber" />
                  <EmhareReviewField label="Country of residence" :value="countryName(workspace.profile.countryId)" />
                  <EmhareReviewField label="Nationality" :value="countryName(workspace.profile.nationalityCountryId)" />
                  <EmhareReviewField label="Place of birth" :value="workspace.profile.placeOfBirth" />
                  <EmhareReviewField label="Sponsor type" :value="workspace.profile.sponsorTypeCode ? formatStatus(workspace.profile.sponsorTypeCode) : null" />
                  <EmhareReviewField label="Email" :value="workspace.profile.primaryEmail" />
                  <EmhareReviewField label="Phone number" :value="workspace.profile.primaryPhone" />
                  <EmhareReviewField label="Residential address" :value="workspace.profile.residentialAddress" wide />
                  <EmhareReviewField label="Postal address" :value="workspace.profile.postalAddress" wide />
                  <EmhareReviewField label="Disability status" :value="workspace.profile.disabilityStatusCode ? formatStatus(workspace.profile.disabilityStatusCode) : null" />
                  <EmhareReviewField label="Support requirements" :value="workspace.profile.specialNeeds" />
                </dl>
              </section>

              <section class="overflow-hidden rounded-xl border border-muted">
                <header class="flex items-center gap-3 border-b border-muted bg-elevated/40 px-5 py-4">
                  <UIcon name="i-lucide-users-round" class="size-5 text-primary" />
                  <div><h2 class="font-semibold text-highlighted">Next of kin</h2><p class="text-sm text-muted">{{ workspace.nextOfKin.length }} contact record{{ workspace.nextOfKin.length === 1 ? '' : 's' }} supplied.</p></div>
                </header>
                <div class="divide-y divide-muted">
                  <dl v-for="record in workspace.nextOfKin" :key="record.id" class="grid gap-x-6 gap-y-4 p-5 md:grid-cols-2">
                    <EmhareReviewField label="Full name" :value="record.fullName" />
                    <EmhareReviewField label="Relationship" :value="formatStatus(record.relationshipCode)" />
                    <EmhareReviewField label="Phone number" :value="record.phoneNumber" />
                    <EmhareReviewField label="Email" :value="record.email" />
                    <EmhareReviewField label="Address" :value="record.address" />
                    <EmhareReviewField label="Primary contact" :value="yesOrNo(record.primary)" />
                  </dl>
                  <p v-if="!workspace.nextOfKin.length" class="p-5 text-sm text-muted">No next-of-kin records supplied.</p>
                </div>
              </section>

              <section v-if="workspace.employmentHistory.length" class="overflow-hidden rounded-xl border border-muted">
                <header class="flex items-center gap-3 border-b border-muted bg-elevated/40 px-5 py-4">
                  <UIcon name="i-lucide-briefcase-business" class="size-5 text-primary" />
                  <div><h2 class="font-semibold text-highlighted">Employment history</h2><p class="text-sm text-muted">Employment records included in this application.</p></div>
                </header>
                <div class="divide-y divide-muted">
                  <dl v-for="record in workspace.employmentHistory" :key="record.id" class="grid gap-x-6 gap-y-4 p-5 md:grid-cols-2">
                    <EmhareReviewField label="Employer" :value="record.employerName" />
                    <EmhareReviewField label="Position" :value="record.positionTitle" />
                    <EmhareReviewField label="Started" :value="formatReviewDate(record.startedOn)" />
                    <EmhareReviewField label="Ended" :value="record.current ? 'Current employment' : formatReviewDate(record.endedOn)" />
                    <EmhareReviewField label="Responsibilities" :value="record.responsibilities" wide />
                  </dl>
                </div>
              </section>

              <section v-if="workspace.referees.length" class="overflow-hidden rounded-xl border border-muted">
                <header class="flex items-center gap-3 border-b border-muted bg-elevated/40 px-5 py-4">
                  <UIcon name="i-lucide-user-round-check" class="size-5 text-primary" />
                  <div><h2 class="font-semibold text-highlighted">Referees</h2><p class="text-sm text-muted">References supplied for this application.</p></div>
                </header>
                <div class="divide-y divide-muted">
                  <dl v-for="record in workspace.referees" :key="record.id" class="grid gap-x-6 gap-y-4 p-5 md:grid-cols-2">
                    <EmhareReviewField label="Full name" :value="[record.title, record.fullName].filter(Boolean).join(' ')" />
                    <EmhareReviewField label="Organisation" :value="record.organisation" />
                    <EmhareReviewField label="Position" :value="record.positionTitle" />
                    <EmhareReviewField label="Verification status" :value="formatStatus(record.verificationStatus)" />
                    <EmhareReviewField label="Email" :value="record.email" />
                    <EmhareReviewField label="Phone number" :value="record.phoneNumber" />
                  </dl>
                </div>
              </section>

              <section class="overflow-hidden rounded-xl border border-muted">
                <header class="flex items-center gap-3 border-b border-muted bg-elevated/40 px-5 py-4">
                  <UIcon name="i-lucide-graduation-cap" class="size-5 text-primary" />
                  <div><h2 class="font-semibold text-highlighted">Qualifications and results</h2><p class="text-sm text-muted">Every sitting and managed subject result captured for evaluation.</p></div>
                </header>
                <div class="divide-y divide-muted">
                  <article v-for="sitting in workspace.qualifications" :key="sitting.id" class="p-5">
                    <div class="flex flex-wrap items-start justify-between gap-3">
                      <div><h3 class="font-semibold text-highlighted">{{ formatStatus(sitting.level) }} · {{ sitting.examBody?.name ?? sitting.institutionName ?? 'Qualification sitting' }}</h3><p class="mt-1 text-sm text-muted">{{ sitting.yearWritten ?? 'Year not supplied' }} · Candidate {{ sitting.candidateNumber ?? 'not supplied' }}</p></div>
                      <EmhareStatusPill :label="formatStatus(sitting.verificationStatus)" :tone="sitting.verificationStatus === 'VERIFIED' ? 'success' : sitting.verificationStatus === 'REJECTED' ? 'error' : 'warning'" />
                    </div>
                    <dl class="mt-4 grid gap-x-6 gap-y-4 md:grid-cols-2">
                      <EmhareReviewField label="School or institution" :value="sitting.institutionName" />
                      <EmhareReviewField label="Country" :value="countryName(sitting.countryId)" />
                      <EmhareReviewField label="Centre number" :value="sitting.centreNumber" />
                      <EmhareReviewField label="Candidate number" :value="sitting.candidateNumber" />
                    </dl>
                    <div class="mt-5 overflow-hidden rounded-lg border border-muted">
                      <div class="grid grid-cols-[minmax(0,1fr)_5rem_6rem] gap-3 bg-elevated/60 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-muted"><span>Subject</span><span>Grade</span><span>Points</span></div>
                      <div v-for="result in sitting.results" :key="result.id" class="grid grid-cols-[minmax(0,1fr)_5rem_6rem] gap-3 border-t border-muted px-4 py-3 text-sm"><span class="font-medium text-highlighted">{{ result.subjectNameSnapshot }}<span v-if="result.principalSubject" class="ml-2 text-xs text-primary">Principal</span></span><span>{{ result.grade }}</span><span>{{ result.points ?? '—' }}</span></div>
                      <p v-if="!sitting.results.length" class="border-t border-muted px-4 py-3 text-sm text-muted">No subject results supplied.</p>
                    </div>
                  </article>
                  <p v-if="!workspace.qualifications.length" class="p-5 text-sm text-muted">No qualification sittings supplied.</p>
                </div>
              </section>

              <section class="overflow-hidden rounded-xl border border-muted">
                <header class="flex items-center gap-3 border-b border-muted bg-elevated/40 px-5 py-4">
                  <UIcon name="i-lucide-list-ordered" class="size-5 text-primary" />
                  <div><h2 class="font-semibold text-highlighted">Programme choices</h2><p class="text-sm text-muted">Choices are shown in submitted preference order.</p></div>
                </header>
                <ol class="divide-y divide-muted">
                  <li v-for="choice in workspace.application.programmeChoices" :key="choice.id" class="flex items-start gap-4 p-5">
                    <UBadge :label="String(choice.choiceRank)" color="primary" />
                    <div><h3 class="font-semibold text-highlighted">{{ choice.programmeCode }} · {{ choice.programmeName }}</h3><p class="mt-1 text-sm text-muted">{{ choice.awardName }} · {{ choice.owningAcademicUnitName }} · Curriculum {{ choice.programmeVersionCode }}</p></div>
                  </li>
                  <li v-if="!workspace.application.programmeChoices.length" class="p-5 text-sm text-muted">No programme choices supplied.</li>
                </ol>
              </section>

              <section class="overflow-hidden rounded-xl border border-muted">
                <header class="flex items-center gap-3 border-b border-muted bg-elevated/40 px-5 py-4">
                  <UIcon name="i-lucide-folder-check" class="size-5 text-primary" />
                  <div><h2 class="font-semibold text-highlighted">Supporting documents</h2><p class="text-sm text-muted">Review every uploaded file before accepting the declaration.</p></div>
                </header>
                <div class="divide-y divide-muted">
                  <div v-for="requirement in workspace.documents.requirements" :key="requirement.requirementCode" class="flex flex-wrap items-center justify-between gap-4 p-5">
                    <div><h3 class="font-medium text-highlighted">{{ requirement.requirementName }} <span v-if="requirement.required" class="text-error">*</span></h3><p class="mt-1 text-sm text-muted">{{ requirement.fileName ?? 'No file uploaded' }}<template v-if="requirement.mimeType"> · {{ requirement.mimeType }}</template></p><p v-if="requirement.rejectionReason" class="mt-1 text-sm text-error">{{ requirement.rejectionReason }}</p></div>
                    <div class="flex items-center gap-2">
                      <EmhareStatusPill :label="formatStatus(requirement.state)" :tone="requirement.state === 'VERIFIED' ? 'success' : requirement.state === 'PENDING' ? 'warning' : 'error'" />
                      <UButton v-if="requirement.documentId" :aria-label="`Preview ${requirement.requirementName}`" label="Preview" icon="i-lucide-eye" color="neutral" variant="outline" :loading="loadingReviewDocumentId === requirement.documentId" @click="previewApplicationDocument(requirement)" />
                    </div>
                  </div>
                  <p v-if="!workspace.documents.requirements.length" class="p-5 text-sm text-muted">No document requirements configured.</p>
                </div>

                <div v-if="selectedReviewDocument" id="application-document-preview" class="border-t border-muted bg-elevated/20">
                  <div class="flex flex-wrap items-center justify-between gap-3 border-b border-muted px-5 py-3">
                    <div><p class="font-medium text-highlighted">{{ selectedReviewDocument.requirementName }}</p><p class="text-sm text-muted">{{ selectedReviewDocument.fileName }}</p></div>
                    <div class="flex items-center gap-2"><UButton v-if="reviewDocumentDownload" label="Download" icon="i-lucide-download" color="neutral" variant="outline" @click="downloadReviewDocument" /><UButton aria-label="Close document preview" icon="i-lucide-x" color="neutral" variant="ghost" @click="closeApplicationDocumentPreview" /></div>
                  </div>
                  <UAlert v-if="reviewDocumentError" class="m-5" color="error" variant="soft" title="Preview unavailable" :description="reviewDocumentError" />
                  <img v-else-if="reviewDocumentDownload && reviewDocumentIsImage" :src="reviewDocumentDownload.downloadUrl" :alt="selectedReviewDocument.requirementName" class="h-[32rem] w-full object-contain p-4">
                  <iframe v-else-if="reviewDocumentDownload && reviewDocumentIsPdf" :src="reviewDocumentDownload.downloadUrl" :title="`${selectedReviewDocument.requirementName} preview`" class="h-[32rem] w-full border-0 bg-white" />
                  <div v-else-if="reviewDocumentDownload" class="flex min-h-48 items-center justify-center p-8 text-center"><div><UIcon name="i-lucide-file-down" class="mx-auto size-8 text-primary" /><p class="mt-3 font-medium text-highlighted">Inline preview is unavailable for this file type</p><UButton class="mt-4" label="Download document" icon="i-lucide-download" @click="downloadReviewDocument" /></div></div>
                </div>
              </section>

              <section class="overflow-hidden rounded-xl border border-muted">
                <header class="flex items-center gap-3 border-b border-muted bg-elevated/40 px-5 py-4">
                  <UIcon name="i-lucide-receipt-text" class="size-5 text-primary" />
                  <div><h2 class="font-semibold text-highlighted">Application fee</h2><p class="text-sm text-muted">Payment evidence permits submission; Finance clearance is required before Admissions review.</p></div>
                </header>
                <dl class="grid gap-x-6 gap-y-5 p-5 md:grid-cols-2">
                  <EmhareReviewField label="Fee required" :value="yesOrNo(workspace.application.paymentRequired)" />
                  <EmhareReviewField label="Clearance status" :value="formatStatus(workspace.application.paymentClearanceStatus)" />
                  <EmhareReviewField label="Payment reference" :value="workspace.application.payment?.reference" />
                  <EmhareReviewField label="Amount due" :value="workspace.application.payment ? `${workspace.application.payment.currencyCode} ${workspace.application.payment.amountDue}` : null" />
                  <EmhareReviewField label="USD base amount" :value="workspace.application.payment?.baseAmountDue == null ? null : `USD ${workspace.application.payment.baseAmountDue}`" />
                  <EmhareReviewField label="Rating status" :value="workspace.application.payment?.ratingStatus ? formatStatus(workspace.application.payment.ratingStatus) : null" />
                  <EmhareReviewField label="Payment status" :value="workspace.application.payment?.status ? formatStatus(workspace.application.payment.status) : null" />
                  <EmhareReviewField label="Paid at" :value="formatReviewDate(workspace.application.payment?.paidAt)" />
                  <EmhareReviewField v-if="workspace.application.paymentWaiverReason" label="Waiver reason" :value="workspace.application.paymentWaiverReason" wide />
                </dl>
                <div v-if="paymentProofDocuments.length" class="border-t border-muted px-5 py-4">
                  <p class="text-xs font-medium uppercase tracking-wide text-muted">Proof of payment</p>
                  <div class="mt-3 space-y-2">
                    <div v-for="document in paymentProofDocuments" :key="document.id" class="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-muted px-4 py-3">
                      <div><p class="font-medium text-highlighted">{{ document.originalFileName }}</p><p class="text-sm text-muted">Uploaded {{ formatReviewDate(document.uploadedAt) }}</p></div>
                      <EmhareStatusPill :label="formatStatus(document.verificationStatus)" :tone="document.verificationStatus === 'VERIFIED' ? 'success' : document.verificationStatus === 'REJECTED' ? 'error' : 'warning'" />
                    </div>
                  </div>
                </div>
              </section>

              <div class="rounded-lg border border-muted p-4"><h2 class="font-semibold">Declaration</h2><p class="mt-2 text-sm text-muted">I declare that the information and evidence supplied are complete and accurate. I understand that material misrepresentation may invalidate the application or any resulting offer.</p><div class="mt-4"><EmhareStatusPill v-if="workspace.declarationAcceptedAt" label="Declaration accepted" tone="success" /><UButton v-else-if="isDraft" label="Accept declaration" icon="i-lucide-signature" @click="acceptDeclaration" /></div></div>
              <div class="flex justify-end"><UButton v-if="isDraft" label="Submit application" icon="i-lucide-send" color="primary" variant="solid" size="lg" :loading="working" :disabled="!workspace.readyForSubmission" @click="submitApplication" /></div>
            </div>

            <EmhareFeedbackState v-else state="empty" title="Section not configured" description="Admissions has not attached an editor to this section definition." />
          </div>

          <footer class="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 bg-slate-50/60 px-6 py-4 sm:px-8">
            <UButton
              v-if="previousWorkspaceSection"
              :label="`Back: ${displaySectionName(previousWorkspaceSection)}`"
              icon="i-lucide-arrow-left"
              color="neutral"
              variant="outline"
              @click="activatePreviousSection"
            />
            <p class="text-sm text-slate-500" :class="{ 'mr-auto': !previousWorkspaceSection }">
              Step {{ activeJourneyStepIndex + 1 }} of {{ applicationJourneySections.length }}
            </p>
            <UButton
              v-if="nextWorkspaceSection"
              :label="`Continue: ${displaySectionName(nextWorkspaceSection)}`"
              color="primary"
              variant="solid"
              trailing-icon="i-lucide-arrow-right"
              @click="activateNextSection"
            />
            <p v-else class="text-sm font-medium text-uzgreen-800">Review the declaration and submit when every requirement is complete.</p>
          </footer>
        </section>
      </div>
    </main>

  </div>
</template>
