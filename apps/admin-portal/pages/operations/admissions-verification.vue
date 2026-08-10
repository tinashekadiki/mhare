<script setup lang="ts">
import type {
  AdmissionsVerificationQueue,
  AdmissionsApplicationSummary,
  ApplicantApplicationWorkspace,
  ApplicantQualificationSitting,
  ApplicationDocumentRequirementState
} from '@emhare/portal-shell/types/admissions'

definePageMeta({ layout: 'dashboard' })

type VerificationTab = 'sections' | 'qualifications' | 'documents'
type DrawerKind = 'section' | 'qualification' | 'document' | null

const api = useEmhareApi()
const { confirmAction, showError, showSuccess } = useEmhareConfirm()
const academicPeriodContext = useAcademicPeriodContext()
const queue = ref<AdmissionsVerificationQueue>({ applicationSections: [], qualifications: [], documents: [] })
const activeTab = ref<VerificationTab>('sections')
const loading = ref(false)
const working = ref(false)
const loadError = ref('')
const drawer = ref<DrawerKind>(null)
const selectedApplicationId = ref('')
const selectedSitting = ref<ApplicantQualificationSitting | null>(null)
const selectedDocument = ref<ApplicationDocumentRequirementState | null>(null)
const selectedWorkspace = ref<ApplicantApplicationWorkspace | null>(null)
const decisionForm = reactive({ decision: 'VERIFIED', reason: '' })

const tabs = computed(() => [
  { label: 'Application sections', value: 'sections', icon: 'i-lucide-list-checks', badge: queue.value.applicationSections.length },
  { label: 'Qualifications', value: 'qualifications', icon: 'i-lucide-graduation-cap', badge: queue.value.qualifications.length },
  { label: 'Documents', value: 'documents', icon: 'i-lucide-file-check-2', badge: queue.value.documents.reduce((count, row) => count + row.documents.pendingRequirementCodes.length, 0) }
])

onMounted(loadQueue)
watch(academicPeriodContext.selectedAcademicPeriodId, () => void loadQueue())

async function loadQueue() {
  loading.value = true
  loadError.value = ''
  try {
    const [queueResponse, applications] = await Promise.all([
      api.request<AdmissionsVerificationQueue>('/api/admissions/verification-queue'),
      api.request<AdmissionsApplicationSummary[]>('/api/admissions/applications'),
      academicPeriodContext.ensureIntakes()
    ])
    const visibleApplicationIds = new Set(applications
      .filter(application => academicPeriodContext.matchesIntake(application.intakeId))
      .map(application => application.id))
    queue.value = {
      applicationSections: queueResponse.applicationSections.filter(row => visibleApplicationIds.has(row.applicationId)),
      qualifications: queueResponse.qualifications.filter(row => visibleApplicationIds.has(row.applicationId)),
      documents: queueResponse.documents.filter(row => visibleApplicationIds.has(row.applicationId))
    }
  } catch (error) {
    loadError.value = api.errorMessage(error, 'The verification queues could not be loaded.')
  } finally {
    loading.value = false
  }
}

async function openSection(applicationId: string) {
  selectedApplicationId.value = applicationId
  working.value = true
  drawer.value = 'section'
  try {
    selectedWorkspace.value = await api.request<ApplicantApplicationWorkspace>(`/api/admissions/applications/${applicationId}/workspace/staff`)
  } catch (error) {
    drawer.value = null
    await showError('Application could not be opened', api.errorMessage(error))
  } finally {
    working.value = false
  }
}

function openQualification(applicationId: string, qualification: ApplicantQualificationSitting) {
  selectedApplicationId.value = applicationId
  selectedSitting.value = qualification
  Object.assign(decisionForm, { decision: 'VERIFIED', reason: '' })
  drawer.value = 'qualification'
}

function openDocument(applicationId: string, requirement: ApplicationDocumentRequirementState) {
  selectedApplicationId.value = applicationId
  selectedDocument.value = requirement
  Object.assign(decisionForm, { decision: 'VERIFIED', reason: '' })
  drawer.value = 'document'
}

async function saveDecision() {
  if (drawer.value === 'qualification' && selectedSitting.value) {
    working.value = true
    try {
      await api.request(`/api/admissions/applications/${selectedApplicationId.value}/qualifications/${selectedSitting.value.id}/decision`, {
        method: 'POST',
        body: { decision: decisionForm.decision, reason: decisionForm.reason || null, expectedVersion: selectedSitting.value.version }
      })
      drawer.value = null
      await loadQueue()
      await showSuccess('Qualification decision recorded', 'The immutable verification evidence has been added to the application audit history.')
    } catch (error) {
      await showError('Decision could not be recorded', api.errorMessage(error))
    } finally {
      working.value = false
    }
    return
  }

  if (drawer.value === 'document' && selectedDocument.value?.documentId) {
    if (decisionForm.decision === 'REJECTED' && decisionForm.reason.trim().length < 10) {
      await showError('Rejection reason required', 'Record at least 10 characters explaining what evidence must be corrected.')
      return
    }
    const confirmed = await confirmAction({
      title: `${decisionForm.decision === 'VERIFIED' ? 'Verify' : 'Reject'} this document?`,
      text: 'This decision is published through the documents service and propagated to Admissions as an auditable event.',
      confirmButtonText: 'Record decision',
      destructive: decisionForm.decision === 'REJECTED'
    })
    if (!confirmed) return
    working.value = true
    try {
      const action = decisionForm.decision === 'VERIFIED' ? 'verify' : 'reject'
      const body = action === 'verify'
        ? { expectedVersion: selectedDocument.value.documentVersion, comment: decisionForm.reason || null }
        : { expectedVersion: selectedDocument.value.documentVersion, reason: decisionForm.reason }
      await api.request(`/api/documents/uploads/${selectedDocument.value.documentId}/${action}`, { method: 'POST', body })
      drawer.value = null
      await showSuccess('Document decision recorded', 'Admissions will update when the verification event is consumed.')
      await loadQueue()
    } catch (error) {
      await showError('Document decision could not be recorded', api.errorMessage(error))
    } finally {
      working.value = false
    }
  }
}

function pendingDocuments(row: AdmissionsVerificationQueue['documents'][number]) {
  return row.documents.requirements.filter(requirement => requirement.state === 'PENDING')
}

function formatStatus(value: string) {
  return value.toLowerCase().replaceAll('_', ' ').replace(/(^|\s)\S/g, character => character.toUpperCase())
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Admissions verification">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right><UButton label="Refresh" icon="i-lucide-refresh-cw" color="neutral" variant="outline" :loading="loading" @click="loadQueue" /></template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <UTabs v-model="activeTab" :items="tabs" value-key="value" color="primary" variant="link" class="w-full" :content="false" />
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="space-y-5 p-4 sm:p-6">
        <EmhareAdmissionsWorkflowNav current-stage="confirm" />
        <UAlert color="primary" variant="soft" icon="i-lucide-shield-check" title="Independent verification workspace" description="Review one governed evidence class at a time. Each decision uses optimistic locking and becomes auditable workflow evidence." />
        <UAlert v-if="loadError" color="error" variant="soft" icon="i-lucide-circle-alert" title="Verification queues unavailable" :description="loadError" />

        <EmharePaginatedCollection v-if="activeTab === 'sections'" :items="queue.applicationSections" :initial-page-size="10" v-slot="{ items }">
          <div class="space-y-3">
            <UCard v-for="row in items" :key="`${row.applicationId}-${row.sectionCode}`" :ui="{ body: 'p-4' }">
              <div class="flex flex-wrap items-center justify-between gap-3"><div><p class="font-mono text-xs text-primary">{{ row.applicationNumber }}</p><h2 class="font-semibold">{{ row.applicantName }}</h2><p class="text-sm text-muted">{{ row.sectionName }} · {{ row.completionSummary }}</p></div><div class="flex items-center gap-2"><EmhareStatusPill :label="formatStatus(row.status)" :tone="row.status === 'COMPLETE' ? 'success' : 'warning'" /><UButton label="Review application" icon="i-lucide-panel-right-open" color="neutral" variant="outline" @click="openSection(row.applicationId)" /></div></div>
            </UCard>
            <EmhareFeedbackState v-if="!loading && !queue.applicationSections.length" state="empty" title="No application sections awaiting review" />
          </div>
        </EmharePaginatedCollection>

        <EmharePaginatedCollection v-else-if="activeTab === 'qualifications'" :items="queue.qualifications" :initial-page-size="10" v-slot="{ items }">
          <div class="space-y-3">
            <UCard v-for="row in items" :key="row.qualification.id" :ui="{ body: 'p-4' }"><div class="flex flex-wrap items-center justify-between gap-3"><div><p class="font-mono text-xs text-primary">{{ row.applicationNumber }}</p><h2 class="font-semibold">{{ row.applicantName }}</h2><p class="text-sm text-muted">{{ formatStatus(row.qualification.level) }} · {{ row.qualification.examBody?.name ?? row.qualification.institutionName }} · {{ row.qualification.yearWritten }}</p><p class="text-sm text-muted">{{ row.qualification.results.length }} subject results</p></div><UButton label="Review qualification" icon="i-lucide-panel-right-open" @click="openQualification(row.applicationId, row.qualification)" /></div></UCard>
            <EmhareFeedbackState v-if="!loading && !queue.qualifications.length" state="empty" title="No qualifications awaiting verification" />
          </div>
        </EmharePaginatedCollection>

        <EmharePaginatedCollection v-else :items="queue.documents" :initial-page-size="10" v-slot="{ items }">
          <div class="space-y-3">
            <template v-for="row in items" :key="row.applicationId"><UCard v-for="requirement in pendingDocuments(row)" :key="`${row.applicationId}-${requirement.requirementCode}`" :ui="{ body: 'p-4' }"><div class="flex flex-wrap items-center justify-between gap-3"><div><p class="font-mono text-xs text-primary">{{ row.applicationNumber }}</p><h2 class="font-semibold">{{ row.applicantName }}</h2><p class="text-sm text-muted">{{ requirement.requirementName }} · {{ requirement.fileName }}</p></div><UButton label="Review document" icon="i-lucide-panel-right-open" @click="openDocument(row.applicationId, requirement)" /></div></UCard></template>
            <EmhareFeedbackState v-if="!loading && !queue.documents.some(row => pendingDocuments(row).length)" state="empty" title="No documents awaiting verification" />
          </div>
        </EmharePaginatedCollection>
      </div>
    </template>

  </UDashboardPanel>

  <EmhareRecordDrawer :open="drawer !== null" :title="drawer === 'qualification' ? 'Verify qualification' : drawer === 'document' ? 'Verify document' : 'Review application sections'" :description="drawer === 'section' ? 'Inspect the server-calculated completeness of every application section.' : 'Record an independent verification decision.'" :busy="working" :submit-label="'Record decision'" @update:open="value => { if (!value) drawer = null }" @submit="saveDecision">
    <template #body>
      <div v-if="drawer === 'section'" class="space-y-3"><div v-for="section in selectedWorkspace?.sections ?? []" :key="section.id" class="rounded-lg border border-muted p-4"><div class="flex items-center justify-between gap-3"><p class="font-medium">{{ section.name }}</p><EmhareStatusPill :label="formatStatus(section.status)" :tone="section.status === 'COMPLETE' ? 'success' : 'warning'" /></div><p class="mt-1 text-sm text-muted">{{ section.completionSummary }}</p></div></div>
      <div v-else-if="drawer === 'qualification' && selectedSitting" class="space-y-4"><UAlert color="primary" variant="soft" :title="`${formatStatus(selectedSitting.level)} · ${selectedSitting.examBody?.name ?? selectedSitting.institutionName}`" :description="`${selectedSitting.yearWritten} · ${selectedSitting.results.length} results`" /><div class="space-y-2"><div v-for="result in selectedSitting.results" :key="result.id" class="flex items-center justify-between rounded-lg border border-muted p-3"><span>{{ result.subjectNameSnapshot }}</span><span class="font-semibold">{{ result.grade }}</span></div></div><EmhareFormField v-model="decisionForm.decision" type="radio" label="Decision" :items="[{label:'Verified',value:'VERIFIED'},{label:'Rejected',value:'REJECTED'}]" required /><EmhareFormField v-model="decisionForm.reason" type="textarea" label="Verification evidence or rejection reason" :required="decisionForm.decision === 'REJECTED'" /></div>
      <div v-else-if="drawer === 'document' && selectedDocument" class="space-y-4"><EmhareDescriptionList :items="[{label:'Requirement',value:selectedDocument.requirementName},{label:'File',value:selectedDocument.fileName ?? 'Unavailable'},{label:'MIME type',value:selectedDocument.mimeType ?? 'Unavailable'},{label:'SHA-256',value:selectedDocument.checksumSha256 ?? 'Unavailable'},{label:'Current state',value:selectedDocument.state}]" /><EmhareFormField v-model="decisionForm.decision" type="radio" label="Decision" :items="[{label:'Verified',value:'VERIFIED'},{label:'Rejected',value:'REJECTED'}]" required /><EmhareFormField v-model="decisionForm.reason" type="textarea" label="Verification comment or rejection reason" :required="decisionForm.decision === 'REJECTED'" /></div>
    </template>
    <template v-if="drawer === 'section'" #footer><div class="flex w-full justify-end"><UButton label="Close" color="neutral" variant="outline" @click="drawer = null" /></div></template>
  </EmhareRecordDrawer>
</template>
