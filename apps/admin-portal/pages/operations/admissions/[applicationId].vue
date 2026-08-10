<script setup lang="ts">
import Swal from 'sweetalert2'
import type {
  ApplicantApplicationWorkspace,
  ApplicantQualificationSitting,
  ApplicationDocumentRequirementState,
  ApplicationWorkspaceSection,
  AdmissionsApplicationSummary,
  AdmissionsApplicationWorkflowProgress,
  AdmissionsApplicationWorkflowStage
} from '@emhare/portal-shell/types/admissions'
import type { UploadedDocumentDownload } from '@emhare/portal-shell/types/documents'

definePageMeta({ layout: 'dashboard' })

const route = useRoute()
const api = useEmhareApi()
const toast = useToast()
const { confirmAction, showError, showSuccess } = useEmhareConfirm()

const workspace = ref<ApplicantApplicationWorkspace | null>(null)
const loading = ref(false)
const refreshing = ref(false)
const loadError = ref('')
const movingToReview = ref(false)
const returningToDraft = ref(false)
const selectedDocument = ref<ApplicationDocumentRequirementState | null>(null)
const documentDownload = ref<UploadedDocumentDownload | null>(null)
const loadingDocumentPreview = ref(false)
const documentPreviewError = ref('')
const downloadingDocument = ref(false)
const savingDocumentDecision = ref(false)

const applicationId = computed(() => {
  const routeParameter = route.params.applicationId
  return Array.isArray(routeParameter) ? routeParameter[0] ?? '' : routeParameter ?? ''
})
const academicReviewAssignmentId = computed(() => {
  const queryValue = route.query.academicReviewAssignmentId
  return Array.isArray(queryValue) ? queryValue[0] ?? '' : queryValue ?? ''
})
const isAcademicRecommendationProfile = computed(() => Boolean(academicReviewAssignmentId.value))
const returnToWorkflowPath = computed(() => isAcademicRecommendationProfile.value
  ? '/operations/admissions-recommendations'
  : '/operations/admissions')

const application = computed(() => workspace.value?.application ?? null)
const profile = computed(() => workspace.value?.profile ?? null)
const completedSections = computed(() => workspace.value?.sections.filter(section => (
  ['COMPLETE', 'VERIFIED'].includes(section.status)
)).length ?? 0)
const documentCounts = computed(() => {
  const register = workspace.value?.documents
  return {
    total: register?.requirements.length ?? 0,
    uploaded: register?.requirements.filter(requirement => Boolean(requirement.documentId)).length ?? 0,
    pending: register?.pendingRequirementCodes.length ?? 0,
    missing: register?.missingRequirementCodes.length ?? 0,
    rejected: register?.rejectedRequirementCodes.length ?? 0
  }
})
const previewIsImage = computed(() => documentDownload.value?.mimeType.startsWith('image/') ?? false)
const previewIsPdf = computed(() => documentDownload.value?.mimeType === 'application/pdf')
const applicantInitials = computed(() => {
  if (!profile.value) return 'AP'
  return `${profile.value.firstName.charAt(0)}${profile.value.lastName.charAt(0)}`.toUpperCase()
})

useHead({
  title: computed(() => application.value
    ? `${application.value.applicationNumber} · Admissions review`
    : 'Admissions review')
})

onMounted(loadApplication)
watch(applicationId, (currentId, previousId) => {
  if (previousId && currentId !== previousId) loadApplication()
})

async function loadApplication(options: { background?: boolean, preferredDocumentId?: string | null } = {}) {
  if (!applicationId.value) return
  if (options.background) refreshing.value = true
  else loading.value = true
  loadError.value = ''

  try {
    const workspaceEndpoint = isAcademicRecommendationProfile.value
      ? `/api/admissions/academic-reviews/${academicReviewAssignmentId.value}/application-workspace`
      : `/api/admissions/applications/${applicationId.value}/workspace/staff`
    const loadedWorkspace = await api.request<ApplicantApplicationWorkspace>(
      workspaceEndpoint
    )
    workspace.value = loadedWorkspace
    synchronizeSelectedDocument(options.preferredDocumentId)
  } catch (error) {
    loadError.value = api.errorMessage(error, 'The applicant application could not be loaded.')
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function synchronizeSelectedDocument(preferredDocumentId?: string | null) {
  const requirements = workspace.value?.documents.requirements ?? []
  const currentDocumentId = preferredDocumentId ?? selectedDocument.value?.documentId
  const nextDocument = requirements.find(requirement => requirement.documentId === currentDocumentId)
    ?? requirements.find(requirement => requirement.state === 'PENDING' && requirement.documentId)
    ?? requirements.find(requirement => requirement.documentId)
    ?? requirements[0]
    ?? null

  if (!nextDocument) {
    selectedDocument.value = null
    documentDownload.value = null
    documentPreviewError.value = ''
    return
  }

  const previewAlreadyLoaded = nextDocument.documentId
    && nextDocument.documentId === documentDownload.value?.documentId
  selectedDocument.value = nextDocument
  if (!previewAlreadyLoaded) loadDocumentPreview(nextDocument)
}

async function selectDocument(requirement: ApplicationDocumentRequirementState) {
  selectedDocument.value = requirement
  await loadDocumentPreview(requirement)
}

async function loadDocumentPreview(requirement: ApplicationDocumentRequirementState) {
  documentDownload.value = null
  documentPreviewError.value = ''
  if (!requirement.documentId) return

  loadingDocumentPreview.value = true
  try {
    documentDownload.value = await api.request<UploadedDocumentDownload>(
      `/api/documents/uploads/${requirement.documentId}/download?disposition=inline`
    )
  } catch (error) {
    documentPreviewError.value = api.errorMessage(error, 'The document preview could not be loaded.')
  } finally {
    loadingDocumentPreview.value = false
  }
}

async function moveToReview() {
  if (!application.value) return
  const confirmed = await confirmAction({
    title: 'Start admissions review?',
    text: `${application.value.applicationNumber} is submitted and financially cleared. It will enter the active review queue.`,
    confirmButtonText: 'Start review',
    icon: 'question'
  })
  if (!confirmed) return

  movingToReview.value = true
  try {
    const updatedApplication = await api.request<AdmissionsApplicationSummary>(
      `/api/admissions/applications/${application.value.id}/review`,
      { method: 'POST', body: { reason: 'Application accepted into the admissions review queue.' } }
    )
    if (workspace.value) workspace.value.application = updatedApplication
    toast.add({
      title: 'Review started',
      description: `${updatedApplication.applicationNumber} is now under review.`,
      color: 'success',
      icon: 'i-lucide-file-check-2'
    })
  } catch (error) {
    await showError('Review could not be started', api.errorMessage(error))
  } finally {
    movingToReview.value = false
  }
}

async function returnApplicationToDraft() {
  if (!application.value) return
  const result = await Swal.fire({
    title: 'Return application to draft?',
    text: `${application.value.applicationNumber} will reopen for the applicant to make corrections and submit again.`,
    input: 'textarea',
    inputLabel: 'Required correction',
    inputPlaceholder: 'Explain exactly what the applicant must edit',
    inputAttributes: { maxlength: '1000' },
    inputValidator: value => value.trim().length >= 10
      ? undefined
      : 'Record at least 10 characters explaining the required correction.',
    icon: 'warning',
    showCancelButton: true,
    confirmButtonText: 'Return to draft',
    cancelButtonText: 'Cancel',
    confirmButtonColor: '#20743a'
  })
  if (!result.isConfirmed) return

  returningToDraft.value = true
  try {
    await api.request<AdmissionsApplicationSummary>(
      `/api/admissions/applications/${application.value.id}/return-to-draft`,
      { method: 'POST', body: { reason: result.value.trim() } }
    )
    await loadApplication({ background: true })
    await showSuccess(
      'Application returned to draft',
      'The applicant can now edit the application and submit it again.'
    )
  } catch (error) {
    await showError('Application could not be returned to draft', api.errorMessage(error))
  } finally {
    returningToDraft.value = false
  }
}

function reviewGuidance() {
  if (!application.value || application.value.canEnterReview) return []
  if (application.value.paymentRequired && !['PAID', 'WAIVED'].includes(application.value.paymentClearanceStatus)) {
    return ['Confirm the application fee in Finance or record an authorised fee waiver before starting review.']
  }
  return ['Complete the required application sections and document checks before starting review.']
}

async function verifySelectedDocument() {
  const requirement = selectedDocument.value
  if (!requirement?.documentId || requirement.state !== 'PENDING') return

  const result = await Swal.fire({
    title: 'Verify this document?',
    text: `${requirement.requirementName} will be accepted as supporting evidence.`,
    input: 'textarea',
    inputLabel: 'Verification comment (optional)',
    inputPlaceholder: 'Record what was checked and matched',
    inputAttributes: { maxlength: '500' },
    icon: 'question',
    showCancelButton: true,
    confirmButtonText: 'Verify document',
    cancelButtonText: 'Cancel',
    confirmButtonColor: '#20743a'
  })
  if (!result.isConfirmed) return

  await saveDocumentDecision('VERIFIED', String(result.value ?? '').trim() || null)
}

async function rejectSelectedDocument() {
  const requirement = selectedDocument.value
  if (!requirement?.documentId || requirement.state !== 'PENDING') return

  const result = await Swal.fire({
    title: 'Request a replacement?',
    text: `${requirement.requirementName} will be rejected and the applicant will receive the reason.`,
    input: 'textarea',
    inputLabel: 'Rejection reason',
    inputPlaceholder: 'State the missing, unreadable, expired, or mismatched evidence',
    inputAttributes: { maxlength: '500' },
    inputValidator: value => value.trim().length >= 10
      ? undefined
      : 'Record at least 10 characters explaining what must be corrected.',
    icon: 'warning',
    showCancelButton: true,
    confirmButtonText: 'Request replacement',
    cancelButtonText: 'Cancel',
    confirmButtonColor: '#dc2626'
  })
  if (!result.isConfirmed) return

  await saveDocumentDecision('REJECTED', result.value.trim())
}

async function saveDocumentDecision(decision: 'VERIFIED' | 'REJECTED', commentOrReason: string | null) {
  const requirement = selectedDocument.value
  if (!requirement?.documentId) return

  savingDocumentDecision.value = true
  try {
    const action = decision === 'VERIFIED' ? 'verify' : 'reject'
    const body = decision === 'VERIFIED'
      ? { expectedVersion: requirement.documentVersion, comment: commentOrReason }
      : { expectedVersion: requirement.documentVersion, reason: commentOrReason }
    await api.request(`/api/documents/uploads/${requirement.documentId}/${action}`, { method: 'POST', body })
    await refreshDocumentProjection(requirement.documentId, decision)
    if (decision === 'VERIFIED') {
      await showSuccess('Document verified', `${requirement.requirementName} is now accepted evidence.`)
    } else {
      await showSuccess('Replacement requested', 'The applicant will receive the recorded reason and can upload corrected evidence.')
    }
  } catch (error) {
    await showError('Document decision could not be recorded', api.errorMessage(error))
  } finally {
    savingDocumentDecision.value = false
  }
}

async function refreshDocumentProjection(documentId: string, expectedState: 'VERIFIED' | 'REJECTED') {
  for (let attempt = 0; attempt < 4; attempt += 1) {
    await loadApplication({ background: true, preferredDocumentId: documentId })
    const projectedDocument = workspace.value?.documents.requirements.find(requirement => requirement.documentId === documentId)
    if (projectedDocument?.state === expectedState) return
    await new Promise(resolve => window.setTimeout(resolve, 200 * (attempt + 1)))
  }
}

async function downloadSelectedDocument() {
  const documentId = selectedDocument.value?.documentId
  if (!documentId) return

  downloadingDocument.value = true
  try {
    const download = await api.request<UploadedDocumentDownload>(
      `/api/documents/uploads/${documentId}/download`
    )
    const link = window.document.createElement('a')
    link.href = download.downloadUrl
    link.target = '_blank'
    link.rel = 'noopener noreferrer'
    link.download = download.originalFileName
    link.click()
  } catch (error) {
    await showError('Document could not be downloaded', api.errorMessage(error))
  } finally {
    downloadingDocument.value = false
  }
}

async function expandDocumentPreview() {
  const download = documentDownload.value
  if (!download) return

  const previewTitle = selectedDocument.value?.fileName
    ?? selectedDocument.value?.requirementName
    ?? download.originalFileName

  await Swal.fire({
    title: previewTitle,
    html: '<div data-expanded-document-preview></div>',
    width: 'min(96vw, 88rem)',
    padding: '1rem',
    heightAuto: false,
    background: '#ffffff',
    color: '#0f172a',
    showConfirmButton: false,
    showCloseButton: true,
    closeButtonAriaLabel: 'Close expanded document preview',
    customClass: {
      popup: 'border-t-4 border-primary',
      title: 'break-all pr-10 text-left text-xl',
      htmlContainer: 'm-0 overflow-hidden rounded-lg border border-gray-200 bg-gray-50'
    },
    didOpen: () => {
      const popup = Swal.getPopup()
      if (popup) {
        popup.style.borderTop = '4px solid #20743a'
        popup.style.opacity = '1'
      }

      const title = Swal.getTitle()
      if (title) {
        title.style.margin = '0 0 0.75rem'
        title.style.paddingRight = '3rem'
        title.style.fontSize = '1.25rem'
        title.style.lineHeight = '1.5'
        title.style.textAlign = 'left'
        title.style.wordBreak = 'break-word'
      }

      const htmlContainer = Swal.getHtmlContainer()
      if (htmlContainer) htmlContainer.style.margin = '0'
      const previewHost = htmlContainer?.querySelector<HTMLElement>('[data-expanded-document-preview]')
      if (!previewHost) return

      if (download.mimeType.startsWith('image/')) {
        const image = window.document.createElement('img')
        image.src = download.downloadUrl
        image.alt = `${selectedDocument.value?.requirementName ?? 'Application document'} expanded preview`
        image.dataset.testid = 'expanded-document-preview-image'
        image.style.cssText = 'display:block;width:100%;height:min(78vh,56rem);object-fit:contain;background:#f8fafc;'
        previewHost.append(image)
        return
      }

      if (download.mimeType === 'application/pdf') {
        const frame = window.document.createElement('iframe')
        frame.src = download.downloadUrl
        frame.title = `${selectedDocument.value?.requirementName ?? 'Application document'} expanded preview`
        frame.dataset.testid = 'expanded-document-preview-frame'
        frame.style.cssText = 'display:block;width:100%;height:min(78vh,56rem);border:0;background:white;'
        previewHost.append(frame)
        return
      }

      const message = window.document.createElement('div')
      message.className = 'flex min-h-80 items-center justify-center p-8 text-center text-sm text-gray-600'
      message.textContent = 'A larger inline preview is not available for this file type. Download the document to review it.'
      previewHost.append(message)
    },
    willClose: () => {
      const expandedFrame = Swal.getHtmlContainer()?.querySelector<HTMLIFrameElement>('[data-testid="expanded-document-preview-frame"]')
      if (expandedFrame) expandedFrame.src = 'about:blank'
    }
  })
}

function applicationStatusTone(status: string) {
  if (status === 'SUBMITTED' || status === 'UNDER_REVIEW') return 'info' as const
  if (['ELIGIBLE', 'SHORTLISTED', 'SELECTED', 'OFFERED', 'ACCEPTED', 'CONVERTED'].includes(status)) return 'success' as const
  if (['INCOMPLETE', 'NOT_ELIGIBLE', 'DECLINED', 'WITHDRAWN'].includes(status)) return 'error' as const
  return 'neutral' as const
}

function paymentStatusTone(status: AdmissionsApplicationSummary['paymentClearanceStatus']) {
  if (['PAID', 'WAIVED', 'NOT_REQUIRED'].includes(status)) return 'success' as const
  return 'warning' as const
}

function sectionStatusTone(status: ApplicationWorkspaceSection['status']) {
  if (status === 'COMPLETE' || status === 'VERIFIED') return 'success' as const
  if (status === 'REJECTED' || status === 'CORRECTION_REQUIRED') return 'error' as const
  return 'warning' as const
}

function qualificationStatusTone(status: ApplicantQualificationSitting['verificationStatus']) {
  if (status === 'VERIFIED') return 'success' as const
  if (status === 'REJECTED') return 'error' as const
  return 'warning' as const
}

function documentStatusTone(state: ApplicationDocumentRequirementState['state']) {
  if (state === 'VERIFIED') return 'success' as const
  if (state === 'REJECTED' || state === 'MISSING') return 'error' as const
  return 'warning' as const
}

function refereeStatusTone(status: string) {
  if (status === 'VERIFIED') return 'success' as const
  if (status === 'REJECTED') return 'error' as const
  return 'warning' as const
}

function workflowStageTone(state: AdmissionsApplicationWorkflowStage['state']) {
  if (state === 'COMPLETED') return 'success' as const
  if (state === 'CURRENT') return 'warning' as const
  return 'neutral' as const
}

function workflowStageIcon(state: AdmissionsApplicationWorkflowStage['state']) {
  if (state === 'COMPLETED') return 'i-lucide-check'
  if (state === 'CURRENT') return 'i-lucide-circle-dot'
  if (state === 'NOT_APPLICABLE') return 'i-lucide-minus'
  return 'i-lucide-lock-keyhole'
}

function workflowStageMarkerClasses(state: AdmissionsApplicationWorkflowStage['state']) {
  if (state === 'COMPLETED') return 'border-primary bg-primary text-white'
  if (state === 'CURRENT') return 'border-warning bg-warning/10 text-warning'
  return 'border-muted bg-elevated text-muted'
}

function currentWorkflowStep(progress: AdmissionsApplicationWorkflowProgress) {
  return progress.stages.find(stage => stage.code === progress.currentStageCode)?.sequence ?? 1
}

function paymentStatusLabel(status: AdmissionsApplicationSummary['paymentClearanceStatus']) {
  return {
    NOT_REQUIRED: 'Fee not required',
    PENDING: 'Payment pending',
    UNRATED: 'Rate pending',
    PAID: 'Payment cleared',
    WAIVED: 'Fee waived'
  }[status]
}

function formatStatus(value: string | null | undefined) {
  if (!value) return 'Not captured'
  return value.toLowerCase().replaceAll('_', ' ').replace(/(^|\s)\S/g, character => character.toUpperCase())
}

function formatDate(value: string | null | undefined) {
  if (!value) return 'Not captured'
  return new Intl.DateTimeFormat('en-ZW', { dateStyle: 'medium' }).format(new Date(value))
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return 'Not captured'
  return new Intl.DateTimeFormat('en-ZW', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}
</script>

<template>
  <UDashboardPanel data-testid="admissions-application-detail">
    <template #header>
      <UDashboardNavbar :title="application?.applicationNumber ?? 'Applicant application'">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            :label="isAcademicRecommendationProfile ? 'Back to recommendations' : 'Back to queue'"
            icon="i-lucide-arrow-left"
            color="neutral"
            variant="ghost"
            :to="returnToWorkflowPath"
          />
          <UButton
            label="Refresh"
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="outline"
            :loading="refreshing"
            @click="loadApplication({ background: true })"
          />
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar v-if="application">
        <template #left>
          <span class="font-mono text-xs text-muted">{{ application.applicantNumber }}</span>
          <span class="text-xs text-muted">{{ application.intakeCode }} · {{ application.applicationTypeName }}</span>
        </template>
        <template #right>
          <EmhareStatusPill :label="formatStatus(application.status)" :tone="applicationStatusTone(application.status)" />
          <EmhareStatusPill
            :label="paymentStatusLabel(application.paymentClearanceStatus)"
            :tone="paymentStatusTone(application.paymentClearanceStatus)"
            icon="i-lucide-receipt-text"
          />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div v-if="loading" class="space-y-4 p-4 sm:p-6">
        <USkeleton class="h-40 w-full rounded-xl" />
        <div class="grid gap-4 xl:grid-cols-12">
          <USkeleton class="h-[42rem] xl:col-span-7" />
          <USkeleton class="h-[42rem] xl:col-span-5" />
        </div>
      </div>

      <div v-else-if="loadError" class="p-4 sm:p-6">
        <UAlert
          color="error"
          variant="soft"
          icon="i-lucide-circle-alert"
          title="Applicant application unavailable"
          :description="loadError"
          :actions="[{ label: 'Return to workflow', to: returnToWorkflowPath, color: 'neutral', variant: 'outline' }]"
        />
      </div>

      <div v-else-if="workspace && application && profile" class="space-y-5 p-4 sm:p-6">
        <UAlert
          v-if="isAcademicRecommendationProfile"
          color="info"
          variant="soft"
          icon="i-lucide-eye"
          title="Applicant profile for academic-unit recommendation"
          description="This is a read-only consolidated view. Return to the recommendation queue to claim the application or record your recommendation."
        />
        <section class="overflow-hidden rounded-xl border border-muted bg-default shadow-sm" aria-labelledby="applicant-name">
          <div class="h-1.5 bg-primary" />
          <div class="flex flex-col gap-5 p-5 lg:flex-row lg:items-center lg:justify-between">
            <div class="flex min-w-0 items-center gap-4">
              <div class="flex size-14 shrink-0 items-center justify-center rounded-xl bg-primary text-lg font-semibold text-white shadow-sm">
                {{ applicantInitials }}
              </div>
              <div class="min-w-0">
                <div class="flex flex-wrap items-center gap-2">
                  <h1 id="applicant-name" class="truncate text-2xl font-semibold tracking-tight text-highlighted">
                    {{ application.applicantName }}
                  </h1>
                  <EmhareStatusPill :label="formatStatus(profile.applicantCategoryCode)" tone="neutral" />
                </div>
                <p class="mt-1 text-sm text-muted">
                  {{ profile.primaryEmail }}<span v-if="profile.primaryPhone"> · {{ profile.primaryPhone }}</span>
                </p>
                <div class="mt-2 flex flex-wrap gap-x-4 gap-y-1 font-mono text-xs text-muted">
                  <span>{{ profile.applicantNumber }}</span>
                  <span>{{ application.applicationNumber }}</span>
                </div>
              </div>
            </div>

            <div v-if="!isAcademicRecommendationProfile" class="flex flex-wrap items-center gap-2 lg:justify-end">
              <UButton
                v-if="['SUBMITTED', 'UNDER_REVIEW'].includes(application.status)"
                label="Request applicant correction"
                icon="i-lucide-undo-2"
                color="warning"
                variant="outline"
                :loading="returningToDraft"
                @click="returnApplicationToDraft"
              />
              <EmhareGuidedActionButton
                v-if="application.status === 'SUBMITTED'"
                label="Start review"
                icon="i-lucide-file-check-2"
                color="primary"
                guidance-title="Application cannot enter review yet"
                :guidance-instructions="reviewGuidance()"
                :guidance-action-label="application.paymentRequired && !['PAID', 'WAIVED'].includes(application.paymentClearanceStatus) ? 'Open Cash collections' : undefined"
                :loading="movingToReview"
                @guidance-action="navigateTo('/operations/finance-collections')"
                @click="moveToReview"
              />
              <UButton
                label="Verification queues"
                icon="i-lucide-shield-check"
                color="neutral"
                variant="outline"
                to="/operations/admissions-verification"
              />
            </div>
          </div>

          <div class="grid border-t border-muted bg-elevated/40 sm:grid-cols-2 lg:grid-cols-4">
            <div class="border-b border-muted px-5 py-3 sm:border-r lg:border-b-0">
              <p class="text-xs uppercase tracking-wide text-muted">Intake</p>
              <p class="mt-1 text-sm font-medium text-highlighted">{{ application.intakeCode }}</p>
            </div>
            <div class="border-b border-muted px-5 py-3 lg:border-r lg:border-b-0">
              <p class="text-xs uppercase tracking-wide text-muted">Application type</p>
              <p class="mt-1 text-sm font-medium text-highlighted">{{ application.applicationTypeName }}</p>
            </div>
            <div class="border-b border-muted px-5 py-3 sm:border-r sm:border-b-0 lg:border-r">
              <p class="text-xs uppercase tracking-wide text-muted">Workflow</p>
              <p class="mt-1 text-sm font-medium text-highlighted">{{ formatStatus(application.status) }}</p>
            </div>
            <div class="px-5 py-3">
              <p class="text-xs uppercase tracking-wide text-muted">Payment reference</p>
              <p class="mt-1 truncate font-mono text-sm font-medium text-highlighted">{{ application.payment?.reference ?? 'Not required' }}</p>
            </div>
          </div>
        </section>

        <div class="grid items-start gap-5 xl:grid-cols-12">
          <main class="space-y-5 xl:col-span-7 2xl:col-span-8">
            <section aria-labelledby="readiness-heading">
              <UCard :ui="{ body: 'p-5 sm:p-5' }">
                <div class="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <h2 id="readiness-heading" class="text-lg font-semibold text-highlighted">Review readiness</h2>
                    <p class="mt-1 text-sm text-muted">A single view of completeness before a governed admissions decision.</p>
                  </div>
                  <EmhareStatusPill
                    :label="workspace.readyForSubmission ? 'Application complete' : 'Attention required'"
                    :tone="workspace.readyForSubmission ? 'success' : 'warning'"
                  />
                </div>

                <div class="mt-4 grid gap-3 sm:grid-cols-2">
                  <EmhareKpiCard label="Profile" :value="`${profile.completenessPercentage}%`" icon="i-lucide-user-round-check" :tone="profile.completenessPercentage === 100 ? 'success' : 'warning'" />
                  <EmhareKpiCard label="Sections" :value="`${completedSections}/${workspace.sections.length}`" icon="i-lucide-list-checks" :tone="completedSections === workspace.sections.length ? 'success' : 'warning'" />
                  <EmhareKpiCard label="Documents" :value="`${documentCounts.uploaded}/${documentCounts.total}`" icon="i-lucide-files" :tone="workspace.documents.requiredDocumentsUploaded ? 'success' : 'error'" />
                  <EmhareKpiCard label="Programme choices" :value="application.programmeChoices.length" icon="i-lucide-list-ordered" :tone="application.programmeChoices.length ? 'success' : 'error'" />
                  <EmhareKpiCard label="ZIMSEC points" :value="application.calculatedTotalPoints ?? 'Pending submission'" icon="i-lucide-calculator" :tone="application.calculatedTotalPoints != null ? 'success' : 'neutral'" />
                </div>

                <UAlert
                  v-if="workspace.missingRequirements.length"
                  class="mt-4"
                  color="warning"
                  variant="soft"
                  icon="i-lucide-triangle-alert"
                  title="Outstanding requirements"
                  :description="workspace.missingRequirements.map(formatStatus).join(' · ')"
                />
              </UCard>
            </section>

            <section aria-labelledby="personal-details-heading">
              <UCard :ui="{ body: 'p-5 sm:p-5' }">
                <div class="flex items-center gap-2">
                  <UIcon name="i-lucide-contact" class="size-5 text-primary" />
                  <h2 id="personal-details-heading" class="text-lg font-semibold text-highlighted">Personal and contact details</h2>
                </div>
                <EmhareDescriptionList
                  class="mt-4"
                  :items="[
                    { label: 'Full name', value: `${profile.titleCode ? `${profile.titleCode} ` : ''}${profile.firstName} ${profile.middleNames ?? ''} ${profile.lastName}`.replace(/\s+/g, ' ').trim() },
                    { label: 'Date of birth', value: formatDate(profile.dateOfBirth) },
                    { label: 'Gender', value: formatStatus(profile.genderCode) },
                    { label: 'Marital status', value: formatStatus(profile.maritalStatusCode) },
                    { label: 'National ID', value: profile.nationalIdNumber },
                    { label: 'Passport number', value: profile.passportNumber },
                    { label: 'Place of birth', value: profile.placeOfBirth },
                    { label: 'Sponsor type', value: formatStatus(profile.sponsorTypeCode) },
                    { label: 'Disability status', value: formatStatus(profile.disabilityStatusCode) },
                    { label: 'Special needs', value: profile.specialNeeds },
                    { label: 'Postal address', value: profile.postalAddress },
                    { label: 'Residential address', value: profile.residentialAddress }
                  ]"
                />
              </UCard>
            </section>

            <section aria-labelledby="programme-choices-heading">
              <UCard :ui="{ body: 'p-5 sm:p-5' }">
                <div class="flex items-center justify-between gap-3">
                  <div class="flex items-center gap-2">
                    <UIcon name="i-lucide-list-ordered" class="size-5 text-primary" />
                    <h2 id="programme-choices-heading" class="text-lg font-semibold text-highlighted">Programme choices</h2>
                  </div>
                  <span class="text-sm text-muted">{{ application.programmeChoices.length }} recorded</span>
                </div>

                <div v-if="application.programmeChoices.length" class="mt-4 space-y-3">
                  <article
                    v-for="choice in application.programmeChoices"
                    :key="choice.id"
                    class="grid gap-3 rounded-lg border border-muted p-4 sm:grid-cols-[auto_1fr_auto] sm:items-center"
                  >
                    <span class="flex size-9 items-center justify-center rounded-lg bg-primary/10 font-semibold text-primary">{{ choice.choiceRank }}</span>
                    <div class="min-w-0">
                      <p class="font-medium text-highlighted">{{ choice.programmeCode }} · {{ choice.programmeName }}</p>
                      <p class="mt-1 text-sm text-muted">{{ choice.awardName }} · {{ choice.owningAcademicUnitName }}</p>
                      <p v-if="choice.evaluationSummary" class="mt-2 text-sm text-muted">{{ choice.evaluationSummary }}</p>
                      <p v-if="choice.decisionReason" class="mt-1 text-sm text-error">{{ choice.decisionReason }}</p>
                    </div>
                    <EmhareStatusPill :label="formatStatus(choice.choiceStatus)" :tone="applicationStatusTone(choice.choiceStatus)" />
                  </article>
                </div>
                <EmhareFeedbackState v-else state="empty" title="No programme choices" description="The applicant has not recorded a programme choice." />
              </UCard>
            </section>

            <section aria-labelledby="qualifications-heading">
              <UCard :ui="{ body: 'p-5 sm:p-5' }">
                <div class="flex items-center justify-between gap-3">
                  <div class="flex items-center gap-2">
                    <UIcon name="i-lucide-graduation-cap" class="size-5 text-primary" />
                    <h2 id="qualifications-heading" class="text-lg font-semibold text-highlighted">Academic evidence</h2>
                  </div>
                  <span class="text-sm text-muted">{{ workspace.qualifications.length }} sittings</span>
                </div>

                <div v-if="workspace.qualifications.length" class="mt-4 space-y-4">
                  <article v-for="qualification in workspace.qualifications" :key="qualification.id" class="overflow-hidden rounded-lg border border-muted">
                    <div class="flex flex-wrap items-start justify-between gap-3 bg-elevated/50 px-4 py-3">
                      <div>
                        <p class="font-medium text-highlighted">{{ formatStatus(qualification.level) }}</p>
                        <p class="mt-1 text-sm text-muted">
                          {{ qualification.examBody?.name ?? qualification.institutionName ?? 'Institution not captured' }}
                          <span v-if="qualification.yearWritten"> · {{ qualification.yearWritten }}</span>
                        </p>
                        <p v-if="qualification.centreNumber || qualification.candidateNumber" class="mt-1 font-mono text-xs text-muted">
                          {{ qualification.centreNumber ?? 'No centre' }} · {{ qualification.candidateNumber ?? 'No candidate number' }}
                        </p>
                      </div>
                      <EmhareStatusPill :label="formatStatus(qualification.verificationStatus)" :tone="qualificationStatusTone(qualification.verificationStatus)" />
                    </div>
                    <div class="overflow-x-auto">
                      <table class="w-full text-left text-sm">
                        <thead class="border-y border-muted text-xs uppercase tracking-wide text-muted">
                          <tr>
                            <th class="px-4 py-2 font-medium">Subject</th>
                            <th class="px-4 py-2 font-medium">Grade</th>
                            <th class="px-4 py-2 text-right font-medium">Points</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="result in qualification.results" :key="result.id" class="border-b border-muted last:border-b-0">
                            <td class="px-4 py-2.5 text-highlighted">{{ result.subjectNameSnapshot }}</td>
                            <td class="px-4 py-2.5 font-semibold text-highlighted">{{ result.grade }}</td>
                            <td class="px-4 py-2.5 text-right text-muted">{{ result.points ?? '—' }}</td>
                          </tr>
                          <tr v-if="!qualification.results.length">
                            <td colspan="3" class="px-4 py-6 text-center text-muted">No subject results captured.</td>
                          </tr>
                        </tbody>
                      </table>
                    </div>
                  </article>
                </div>
                <EmhareFeedbackState v-else state="empty" title="No academic evidence" description="No qualification sittings have been captured." />
              </UCard>
            </section>

            <section aria-labelledby="supporting-details-heading">
              <UCard :ui="{ body: 'p-5 sm:p-5' }">
                <div class="flex items-center gap-2">
                  <UIcon name="i-lucide-users-round" class="size-5 text-primary" />
                  <h2 id="supporting-details-heading" class="text-lg font-semibold text-highlighted">Supporting details</h2>
                </div>

                <div class="mt-4 grid gap-5 lg:grid-cols-2">
                  <div>
                    <h3 class="text-sm font-semibold text-highlighted">Next of kin</h3>
                    <div v-if="workspace.nextOfKin.length" class="mt-2 space-y-2">
                      <div v-for="contact in workspace.nextOfKin" :key="contact.id" class="rounded-lg border border-muted p-3">
                        <div class="flex items-center justify-between gap-2">
                          <p class="font-medium text-highlighted">{{ contact.fullName }}</p>
                          <EmhareStatusPill v-if="contact.primary" label="Primary" tone="primary" />
                        </div>
                        <p class="mt-1 text-sm text-muted">{{ formatStatus(contact.relationshipCode) }} · {{ contact.phoneNumber }}</p>
                        <p v-if="contact.email" class="mt-1 text-sm text-muted">{{ contact.email }}</p>
                      </div>
                    </div>
                    <p v-else class="mt-2 text-sm text-muted">No next-of-kin details captured.</p>
                  </div>

                  <div>
                    <h3 class="text-sm font-semibold text-highlighted">Referees</h3>
                    <div v-if="workspace.referees.length" class="mt-2 space-y-2">
                      <div v-for="referee in workspace.referees" :key="referee.id" class="rounded-lg border border-muted p-3">
                        <div class="flex items-center justify-between gap-2">
                          <p class="font-medium text-highlighted">{{ referee.fullName }}</p>
                          <EmhareStatusPill :label="formatStatus(referee.verificationStatus)" :tone="refereeStatusTone(referee.verificationStatus)" />
                        </div>
                        <p class="mt-1 text-sm text-muted">{{ referee.organisation }}<span v-if="referee.positionTitle"> · {{ referee.positionTitle }}</span></p>
                        <p class="mt-1 text-sm text-muted">{{ referee.email }}<span v-if="referee.phoneNumber"> · {{ referee.phoneNumber }}</span></p>
                      </div>
                    </div>
                    <p v-else class="mt-2 text-sm text-muted">No referee details required or captured.</p>
                  </div>
                </div>

                <div v-if="workspace.employmentHistory.length" class="mt-5 border-t border-muted pt-5">
                  <h3 class="text-sm font-semibold text-highlighted">Employment history</h3>
                  <div class="mt-2 grid gap-2 lg:grid-cols-2">
                    <div v-for="employment in workspace.employmentHistory" :key="employment.id" class="rounded-lg border border-muted p-3">
                      <p class="font-medium text-highlighted">{{ employment.positionTitle }}</p>
                      <p class="mt-1 text-sm text-muted">{{ employment.employerName }}</p>
                      <p class="mt-1 text-xs text-muted">{{ formatDate(employment.startedOn) }} – {{ employment.current ? 'Present' : formatDate(employment.endedOn) }}</p>
                    </div>
                  </div>
                </div>
              </UCard>
            </section>

            <section aria-labelledby="sections-heading">
              <UCard :ui="{ body: 'p-5 sm:p-5' }">
                <div class="flex items-center justify-between gap-3">
                  <div class="flex items-center gap-2">
                    <UIcon name="i-lucide-list-checks" class="size-5 text-primary" />
                    <h2 id="sections-heading" class="text-lg font-semibold text-highlighted">Application sections</h2>
                  </div>
                  <span class="text-sm text-muted">{{ completedSections }}/{{ workspace.sections.length }} complete</span>
                </div>
                <div class="mt-4 grid gap-2 md:grid-cols-2">
                  <div v-for="section in workspace.sections" :key="section.id" class="rounded-lg border border-muted p-3">
                    <div class="flex items-center justify-between gap-2">
                      <p class="font-medium text-highlighted">{{ section.name }}</p>
                      <EmhareStatusPill :label="formatStatus(section.status)" :tone="sectionStatusTone(section.status)" />
                    </div>
                    <p class="mt-1 text-sm text-muted">{{ section.completionSummary ?? (section.required ? 'Required section' : 'Optional section') }}</p>
                  </div>
                </div>
              </UCard>
            </section>
          </main>

          <aside class="space-y-5 xl:sticky xl:top-4 xl:col-span-5 2xl:col-span-4">
            <UCard :ui="{ body: 'p-4 sm:p-4' }" data-testid="application-workflow-progress">
              <div class="flex items-start justify-between gap-3">
                <div>
                  <div class="flex items-center gap-2">
                    <UIcon name="i-lucide-git-branch" class="size-5 text-primary" />
                    <h2 class="text-lg font-semibold text-highlighted">Admissions workflow</h2>
                  </div>
                  <p class="mt-1 text-sm text-muted">The applicant's current position from confirmation to offer.</p>
                </div>
                <UBadge color="primary" variant="subtle" size="sm">
                  Step {{ currentWorkflowStep(workspace.workflowProgress) }} of 5
                </UBadge>
              </div>

              <ol class="mt-5" aria-label="Applicant Admissions workflow progress">
                <li
                  v-for="(stage, stageIndex) in workspace.workflowProgress.stages"
                  :key="stage.code"
                  class="relative grid grid-cols-[2.25rem_minmax(0,1fr)] gap-3 pb-5 last:pb-0"
                >
                  <div class="relative flex justify-center">
                    <span
                      v-if="stageIndex < workspace.workflowProgress.stages.length - 1"
                      class="absolute left-1/2 top-9 h-[calc(100%-1.25rem)] w-px -translate-x-1/2 bg-muted"
                      aria-hidden="true"
                    />
                    <span
                      class="relative z-10 flex size-9 items-center justify-center rounded-full border-2"
                      :class="workflowStageMarkerClasses(stage.state)"
                    >
                      <UIcon :name="workflowStageIcon(stage.state)" class="size-4" />
                    </span>
                  </div>
                  <div class="min-w-0 pt-0.5">
                    <div class="flex flex-wrap items-start justify-between gap-2">
                      <div class="min-w-0">
                        <p class="text-xs font-semibold uppercase tracking-wide text-muted">
                          {{ stage.sequence }} · {{ stage.label }}
                        </p>
                        <p class="mt-1 font-medium text-highlighted">{{ stage.statusLabel }}</p>
                      </div>
                      <EmhareStatusPill
                        :label="stage.state === 'NOT_APPLICABLE' ? 'Not applicable' : formatStatus(stage.state)"
                        :tone="workflowStageTone(stage.state)"
                      />
                    </div>
                    <p class="mt-1 text-sm text-muted">{{ stage.detail }}</p>
                    <p v-if="stage.occurredAt" class="mt-1 text-xs text-muted">
                      {{ formatDateTime(stage.occurredAt) }}
                    </p>
                  </div>
                </li>
              </ol>
            </UCard>

            <UCard :ui="{ body: 'p-0 sm:p-0' }" data-testid="application-documents-panel">
              <div class="border-b border-muted p-4">
                <div class="flex items-start justify-between gap-3">
                  <div>
                    <div class="flex items-center gap-2">
                      <UIcon name="i-lucide-files" class="size-5 text-primary" />
                      <h2 id="documents-heading" class="text-lg font-semibold text-highlighted">Documents</h2>
                    </div>
                    <p class="mt-1 text-sm text-muted">Select evidence to preview without leaving this application.</p>
                  </div>
                  <EmhareStatusPill
                    :label="workspace.documents.requiredDocumentsVerified ? 'Verified' : `${documentCounts.pending + documentCounts.missing + documentCounts.rejected} outstanding`"
                    :tone="workspace.documents.requiredDocumentsVerified ? 'success' : 'warning'"
                  />
                </div>

                <div class="mt-3 grid grid-cols-3 gap-2 text-center">
                  <div class="rounded-md bg-elevated px-2 py-2">
                    <p class="text-lg font-semibold text-highlighted">{{ documentCounts.uploaded }}</p>
                    <p class="text-xs text-muted">Uploaded</p>
                  </div>
                  <div class="rounded-md bg-elevated px-2 py-2">
                    <p class="text-lg font-semibold text-warning">{{ documentCounts.pending }}</p>
                    <p class="text-xs text-muted">Pending</p>
                  </div>
                  <div class="rounded-md bg-elevated px-2 py-2">
                    <p class="text-lg font-semibold text-error">{{ documentCounts.missing + documentCounts.rejected }}</p>
                    <p class="text-xs text-muted">Needs action</p>
                  </div>
                </div>
              </div>

              <div v-if="workspace.documents.requirements.length" class="max-h-60 overflow-y-auto border-b border-muted p-2">
                <button
                  v-for="requirement in workspace.documents.requirements"
                  :key="requirement.requirementCode"
                  type="button"
                  class="flex w-full items-center gap-3 rounded-lg border px-3 py-2.5 text-left transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                  :class="selectedDocument?.requirementCode === requirement.requirementCode
                    ? 'border-primary bg-primary/5'
                    : 'border-transparent hover:border-muted hover:bg-elevated'"
                  @click="selectDocument(requirement)"
                >
                  <span class="flex size-9 shrink-0 items-center justify-center rounded-md bg-elevated text-primary">
                    <UIcon :name="requirement.documentId ? 'i-lucide-file-text' : 'i-lucide-file-question'" class="size-4" />
                  </span>
                  <span class="min-w-0 flex-1">
                    <span class="block truncate text-sm font-medium text-highlighted">{{ requirement.requirementName }}</span>
                    <span class="mt-0.5 block truncate text-xs text-muted">{{ requirement.fileName ?? (requirement.required ? 'Required upload missing' : 'Optional upload missing') }}</span>
                  </span>
                  <EmhareStatusPill :label="formatStatus(requirement.state)" :tone="documentStatusTone(requirement.state)" />
                </button>
              </div>

              <div v-if="selectedDocument" class="border-b border-muted p-4">
                <div class="flex flex-wrap items-start justify-between gap-3">
                  <div class="min-w-0">
                    <p class="truncate font-medium text-highlighted">{{ selectedDocument.fileName ?? selectedDocument.requirementName }}</p>
                    <p class="mt-1 text-xs text-muted">{{ selectedDocument.mimeType ?? 'No uploaded file' }}</p>
                  </div>
                  <div class="flex flex-wrap gap-1">
                    <UButton
                      v-if="selectedDocument.state === 'PENDING' && !isAcademicRecommendationProfile"
                      label="Verify"
                      icon="i-lucide-shield-check"
                      color="primary"
                      variant="soft"
                      size="xs"
                      :loading="savingDocumentDecision"
                      @click="verifySelectedDocument"
                    />
                    <UButton
                      v-if="selectedDocument.state === 'PENDING' && !isAcademicRecommendationProfile"
                      label="Reject"
                      icon="i-lucide-file-x-2"
                      color="error"
                      variant="soft"
                      size="xs"
                      :disabled="savingDocumentDecision"
                      @click="rejectSelectedDocument"
                    />
                    <UButton
                      v-if="documentDownload"
                      label="Expand preview"
                      icon="i-lucide-maximize-2"
                      color="neutral"
                      variant="soft"
                      size="xs"
                      @click="expandDocumentPreview"
                    />
                    <UButton
                      v-if="documentDownload"
                      label="Download"
                      aria-label="Download selected document"
                      icon="i-lucide-download"
                      color="neutral"
                      variant="ghost"
                      size="xs"
                      :loading="downloadingDocument"
                      @click="downloadSelectedDocument"
                    />
                  </div>
                </div>
                <p v-if="selectedDocument.rejectionReason" class="mt-2 rounded-md bg-error/10 p-2 text-xs text-error">
                  {{ selectedDocument.rejectionReason }}
                </p>
              </div>

              <div class="relative min-h-64 border-t border-muted bg-[#edf1f5] p-2" data-testid="document-preview">
                <div v-if="loadingDocumentPreview" class="absolute inset-0 flex items-center justify-center">
                  <div class="text-center">
                    <UIcon name="i-lucide-loader-circle" class="mx-auto size-7 animate-spin text-primary" />
                    <p class="mt-2 text-sm text-muted">Preparing secure preview…</p>
                  </div>
                </div>

                <UAlert
                  v-else-if="documentPreviewError"
                  class="m-4"
                  color="error"
                  variant="soft"
                  title="Preview unavailable"
                  :description="documentPreviewError"
                />

                <img
                  v-else-if="documentDownload && previewIsImage"
                  :src="documentDownload.downloadUrl"
                  :alt="selectedDocument?.requirementName ?? 'Application document'"
                  class="h-64 w-full rounded-md object-contain bg-white p-3 shadow-sm"
                >

                <iframe
                  v-else-if="documentDownload && previewIsPdf"
                  :src="documentDownload.downloadUrl"
                  :title="`${selectedDocument?.requirementName ?? 'Application document'} preview`"
                  class="h-64 w-full rounded-md border-0 bg-white shadow-sm"
                  data-testid="document-preview-frame"
                />

                <div v-else-if="documentDownload" class="flex min-h-64 items-center justify-center p-8 text-center">
                  <div>
                    <span class="mx-auto flex size-14 items-center justify-center rounded-xl bg-primary/10 text-primary">
                      <UIcon name="i-lucide-file-down" class="size-7" />
                    </span>
                    <p class="mt-4 font-medium text-highlighted">Inline preview is not available for this file type</p>
                    <p class="mt-1 text-sm text-muted">Download the file to review its contents.</p>
                    <UButton class="mt-4" label="Download document" icon="i-lucide-download" color="primary" variant="soft" @click="downloadSelectedDocument" />
                  </div>
                </div>

                <div v-else class="flex min-h-64 items-center justify-center p-8 text-center">
                  <div>
                    <span class="mx-auto flex size-14 items-center justify-center rounded-xl bg-elevated text-muted">
                      <UIcon :name="selectedDocument ? 'i-lucide-file-question' : 'i-lucide-files'" class="size-7" />
                    </span>
                    <p class="mt-4 font-medium text-highlighted">{{ selectedDocument ? 'No file uploaded' : 'No documents configured' }}</p>
                    <p class="mt-1 text-sm text-muted">
                      {{ selectedDocument ? 'This requirement cannot be previewed until the applicant uploads evidence.' : 'Document requirements will appear here when configured.' }}
                    </p>
                  </div>
                </div>
              </div>

              <div v-if="documentDownload" class="border-t border-muted px-4 py-2.5 text-xs text-muted">
                Secure preview expires {{ formatDateTime(documentDownload.expiresAt) }} · SHA-256 {{ documentDownload.checksumSha256.slice(0, 12) }}…
              </div>
            </UCard>
          </aside>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
