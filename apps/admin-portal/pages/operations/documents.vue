<script setup lang="ts">
import Swal from 'sweetalert2'
import type {
  GeneratedDocumentStatus,
  OfficialDocumentDownload,
  OfficialDocumentSummary,
  UploadedDocumentDownload,
  UploadedDocumentSummary,
  UploadedDocumentVerificationStatus
} from '@emhare/portal-shell/types/documents'
import type {
  EmhareDataTableColumn,
  EmhareDataTableState
} from '@emhare/portal-shell/types/emhare-ui'

definePageMeta({ layout: 'dashboard' })

type DocumentsTab = 'uploaded' | 'official'
type DocumentsDrawer = 'upload' | 'verification' | null

const api = useEmhareApi()
const toast = useToast()
const { showError, showSuccess } = useEmhareConfirm()
const academicPeriodContext = useAcademicPeriodContext()
const activeTab = ref<DocumentsTab>('uploaded')
const uploadedDocuments = ref<UploadedDocumentSummary[]>([])
const officialDocuments = ref<OfficialDocumentSummary[]>([])
const loading = ref(false)
const loadErrors = reactive({ uploaded: '', official: '' })
const downloadingDocumentId = ref<string | null>(null)
const retryingDocumentId = ref<string | null>(null)
const drawerOpen = ref(false)
const drawerKind = ref<DocumentsDrawer>(null)
const drawerSaving = ref(false)
const selectedUpload = ref<UploadedDocumentSummary | null>(null)
const uploadedStatusFilter = ref<'ALL' | UploadedDocumentVerificationStatus>('ALL')
const officialStatusFilter = ref<'ALL' | GeneratedDocumentStatus>('ALL')

const uploadedTableState = ref<EmhareDataTableState>({
  page: 1,
  pageSize: 10,
  search: '',
  selectedKeys: [],
  visibleColumns: ['documentTypeCode', 'originalFileName', 'ownerType', 'ownerId', 'uploadedAt', 'fileSizeBytes', 'verificationStatus']
})
const officialTableState = ref<EmhareDataTableState>({
  page: 1,
  pageSize: 10,
  search: '',
  selectedKeys: [],
  visibleColumns: ['documentNumber', 'studentNumber', 'academicPeriodCode', 'decisionLabel', 'status', 'generatedAt', 'sizeBytes']
})

const uploadForm = reactive<{
  ownerType: string
  ownerId: string
  documentTypeCode: string
  replacesDocumentId: string
  file: File | File[] | null
}>({ ownerType: 'APPLICATION', ownerId: '', documentTypeCode: '', replacesDocumentId: '', file: null })
const verificationForm = reactive({ decision: 'VERIFIED' as 'VERIFIED' | 'REJECTED', comment: '', rejectionReason: '' })

const tabs = computed(() => [
  { label: `Uploaded evidence ${uploadedDocuments.value.length}`, value: 'uploaded', icon: 'i-lucide-files' },
  { label: `Official records ${officialDocuments.value.length}`, value: 'official', icon: 'i-lucide-file-check-2' }
])
const ownerTypeItems = [
  { label: 'Application', value: 'APPLICATION' },
  { label: 'Applicant', value: 'APPLICANT' },
  { label: 'Student', value: 'STUDENT' },
  { label: 'Staff member', value: 'STAFF' },
  { label: 'Finance record', value: 'FINANCE_RECORD' },
  { label: 'Academic workflow', value: 'ACADEMIC_WORKFLOW' }
]
const documentTypeItems = [
  { label: 'National identity document', value: 'NATIONAL_ID' },
  { label: 'Birth certificate', value: 'BIRTH_CERTIFICATE' },
  { label: 'Academic certificate', value: 'ACADEMIC_CERTIFICATE' },
  { label: 'Academic transcript', value: 'ACADEMIC_TRANSCRIPT' },
  { label: 'Proof of payment', value: 'PROOF_OF_PAYMENT' },
  { label: 'Passport photograph', value: 'PASSPORT_PHOTOGRAPH' },
  { label: 'Supporting document', value: 'SUPPORTING_DOCUMENT' }
]
const uploadedStatusItems = [
  { label: 'All statuses', value: 'ALL' },
  { label: 'Pending verification', value: 'PENDING' },
  { label: 'Verified', value: 'VERIFIED' },
  { label: 'Rejected', value: 'REJECTED' }
]
const officialStatusItems = [
  { label: 'All statuses', value: 'ALL' },
  { label: 'Queued', value: 'REQUESTED' },
  { label: 'Generating', value: 'GENERATING' },
  { label: 'Stored', value: 'STORED' },
  { label: 'Failed', value: 'FAILED' }
]
const verificationDecisionItems = [
  { label: 'Verify document', value: 'VERIFIED' },
  { label: 'Reject and request replacement', value: 'REJECTED' }
]

const uploadedColumns: EmhareDataTableColumn[] = [
  { key: 'documentTypeCode', label: 'Document type', sortable: true, frozen: true },
  { key: 'originalFileName', label: 'File', sortable: true },
  { key: 'ownerType', label: 'Record type', sortable: true },
  { key: 'ownerId', label: 'Record ID' },
  { key: 'uploadedAt', label: 'Uploaded', sortable: true },
  { key: 'fileSizeBytes', label: 'Size', align: 'right' },
  { key: 'verificationStatus', label: 'Verification', sortable: true },
  { key: 'verifiedAt', label: 'Decision time', sortable: true, hidden: true },
  { key: 'uploadedByUserId', label: 'Uploaded by', hidden: true }
]
const officialColumns: EmhareDataTableColumn[] = [
  { key: 'documentNumber', label: 'Document', sortable: true, frozen: true },
  { key: 'studentNumber', label: 'Student', sortable: true },
  { key: 'academicPeriodCode', label: 'Academic period', sortable: true },
  { key: 'decisionLabel', label: 'Decision', sortable: true },
  { key: 'status', label: 'Generation', sortable: true },
  { key: 'generatedAt', label: 'Stored', sortable: true },
  { key: 'sizeBytes', label: 'Size', align: 'right' },
  { key: 'generationAttemptCount', label: 'Attempts', align: 'right', hidden: true }
]

const filteredUploadedDocuments = computed(() => uploadedStatusFilter.value === 'ALL'
  ? uploadedDocuments.value
  : uploadedDocuments.value.filter(document => document.verificationStatus === uploadedStatusFilter.value))
const filteredOfficialDocuments = computed(() => officialStatusFilter.value === 'ALL'
  ? officialDocuments.value.filter(document => academicPeriodContext.matchesAcademicPeriod(document))
  : officialDocuments.value.filter(document => academicPeriodContext.matchesAcademicPeriod(document) && document.status === officialStatusFilter.value))
const uploadedCounts = computed(() => ({
  total: uploadedDocuments.value.length,
  pending: uploadedDocuments.value.filter(document => document.verificationStatus === 'PENDING').length,
  verified: uploadedDocuments.value.filter(document => document.verificationStatus === 'VERIFIED').length,
  rejected: uploadedDocuments.value.filter(document => document.verificationStatus === 'REJECTED').length
}))
const officialCounts = computed(() => ({
  total: officialDocuments.value.length,
  queued: officialDocuments.value.filter(document => ['REQUESTED', 'GENERATING'].includes(document.status)).length,
  stored: officialDocuments.value.filter(document => document.status === 'STORED').length,
  failed: officialDocuments.value.filter(document => document.status === 'FAILED').length
}))
const drawerTitle = computed(() => drawerKind.value === 'upload' ? 'Upload governed document' : 'Review uploaded document')
const drawerDescription = computed(() => drawerKind.value === 'upload'
  ? 'Store evidence against an owned business record. File content is inspected and checksum-protected.'
  : 'Record one auditable verification decision. Rejected evidence must be replaced with a new document version.')
const drawerSubmitDisabled = computed(() => {
  if (drawerKind.value === 'upload') {
    return !uploadForm.ownerType || !uploadForm.ownerId.trim() || !uploadForm.documentTypeCode || !selectedFile.value
  }
  if (!selectedUpload.value || selectedUpload.value.verificationStatus !== 'PENDING') return true
  return verificationForm.decision === 'REJECTED' && verificationForm.rejectionReason.trim().length < 10
})
const selectedFile = computed(() => Array.isArray(uploadForm.file) ? uploadForm.file[0] : uploadForm.file)

onMounted(load)

async function load() {
  loading.value = true
  loadErrors.uploaded = ''
  loadErrors.official = ''
  const [uploadsResult, officialResult] = await Promise.allSettled([
    api.request<UploadedDocumentSummary[]>('/api/documents/uploads'),
    api.request<OfficialDocumentSummary[]>('/api/documents')
  ])
  if (uploadsResult.status === 'fulfilled') uploadedDocuments.value = uploadsResult.value
  else loadErrors.uploaded = api.errorMessage(uploadsResult.reason)
  if (officialResult.status === 'fulfilled') officialDocuments.value = officialResult.value
  else loadErrors.official = api.errorMessage(officialResult.reason)
  loading.value = false
}

function normalizeSearch(value: unknown) {
  return String(value ?? '').toLowerCase()
}

function tableRows<T extends Record<string, unknown>>(rows: T[], state: EmhareDataTableState) {
  let result = [...rows]
  const search = state.search?.trim().toLowerCase()
  if (search) result = result.filter(row => Object.values(row).some(value => normalizeSearch(value).includes(search)))
  const sort = state.sort?.[0]
  if (sort) {
    result.sort((left, right) => {
      const leftValue = normalizeSearch(left[sort.key])
      const rightValue = normalizeSearch(right[sort.key])
      return sort.direction === 'asc' ? leftValue.localeCompare(rightValue) : rightValue.localeCompare(leftValue)
    })
  }
  const start = (state.page - 1) * state.pageSize
  return result.slice(start, start + state.pageSize)
}

function tableTotal<T extends Record<string, unknown>>(rows: T[], state: EmhareDataTableState) {
  const search = state.search?.trim().toLowerCase()
  return search ? rows.filter(row => Object.values(row).some(value => normalizeSearch(value).includes(search))).length : rows.length
}

function openUploadDrawer() {
  Object.assign(uploadForm, { ownerType: 'APPLICATION', ownerId: '', documentTypeCode: '', replacesDocumentId: '', file: null })
  drawerKind.value = 'upload'
  drawerOpen.value = true
}

function openVerificationDrawer(document: UploadedDocumentSummary) {
  selectedUpload.value = document
  Object.assign(verificationForm, {
    decision: document.verificationStatus === 'REJECTED' ? 'REJECTED' : 'VERIFIED',
    comment: document.verificationComment ?? '',
    rejectionReason: document.rejectionReason ?? ''
  })
  drawerKind.value = 'verification'
  drawerOpen.value = true
}

function closeDrawer() {
  drawerKind.value = null
  selectedUpload.value = null
  Object.assign(verificationForm, { decision: 'VERIFIED', comment: '', rejectionReason: '' })
}

async function submitDrawer() {
  if (drawerSubmitDisabled.value) return
  drawerSaving.value = true
  try {
    if (drawerKind.value === 'upload') await uploadDocument()
    if (drawerKind.value === 'verification') await recordVerificationDecision()
    drawerOpen.value = false
    closeDrawer()
    await load()
  } catch (error) {
    await showError(
      drawerKind.value === 'upload' ? 'Document could not be uploaded' : 'Verification decision could not be recorded',
      api.errorMessage(error))
  } finally {
    drawerSaving.value = false
  }
}

async function uploadDocument() {
  const file = selectedFile.value
  if (!file) return
  const body = new FormData()
  body.append('ownerType', uploadForm.ownerType)
  body.append('ownerId', uploadForm.ownerId.trim())
  body.append('documentTypeCode', uploadForm.documentTypeCode)
  if (uploadForm.replacesDocumentId.trim()) body.append('replacesDocumentId', uploadForm.replacesDocumentId.trim())
  body.append('file', file)
  const uploaded = await api.request<UploadedDocumentSummary>('/api/documents/uploads', { method: 'POST', body })
  await showSuccess('Document uploaded', `${uploaded.originalFileName} is pending independent verification.`)
}

async function recordVerificationDecision() {
  const document = selectedUpload.value
  if (!document) return
  if (verificationForm.decision === 'VERIFIED') {
    await api.request(`/api/documents/uploads/${document.id}/verify`, {
      method: 'POST',
      body: { expectedVersion: document.version, comment: verificationForm.comment.trim() || null }
    })
    await showSuccess('Document verified', `${document.originalFileName} is now accepted evidence.`)
  } else {
    await api.request(`/api/documents/uploads/${document.id}/reject`, {
      method: 'POST',
      body: { expectedVersion: document.version, reason: verificationForm.rejectionReason.trim() }
    })
    await showSuccess('Replacement requested', 'The owner has been notified with the recorded rejection reason.')
  }
}

async function openUploadedDocument(document: UploadedDocumentSummary) {
  downloadingDocumentId.value = document.id
  try {
    const download = await api.request<UploadedDocumentDownload>(`/api/documents/uploads/${document.id}/download`)
    openDownload(download.downloadUrl)
  } catch (error) {
    await showError('Uploaded document could not be opened', api.errorMessage(error))
  } finally {
    downloadingDocumentId.value = null
  }
}

async function openOfficialDocument(document: OfficialDocumentSummary) {
  if (document.status !== 'STORED') {
    toast.add({ title: 'Document is not stored yet', description: 'Only successfully generated records can be opened.', color: 'warning' })
    return
  }
  downloadingDocumentId.value = document.id
  try {
    const download = await api.request<OfficialDocumentDownload>(`/api/documents/${document.id}/download`)
    openDownload(download.downloadUrl)
  } catch (error) {
    await showError('Official document could not be opened', api.errorMessage(error))
  } finally {
    downloadingDocumentId.value = null
  }
}

function openDownload(downloadUrl: string) {
  const link = window.document.createElement('a')
  link.href = downloadUrl
  link.target = '_blank'
  link.rel = 'noopener noreferrer'
  link.click()
}

async function retry(document: OfficialDocumentSummary) {
  if (!document.retryAvailable) {
    toast.add({ title: 'Retry is not available', description: 'Only failed generation attempts can be queued again.', color: 'warning' })
    return
  }
  const confirmation = await Swal.fire({
    title: 'Retry official document generation?',
    text: 'The same immutable progression evidence and approved template version will be used. The failed attempt remains in the audit history.',
    icon: 'question',
    showCancelButton: true,
    confirmButtonText: 'Queue retry',
    confirmButtonColor: '#006633'
  })
  if (!confirmation.isConfirmed) return
  retryingDocumentId.value = document.id
  try {
    await api.request(`/api/documents/${document.id}/retry`, { method: 'POST', body: { expectedVersion: document.version } })
    await load()
    toast.add({ title: 'Document retry queued', description: document.documentNumber, color: 'success' })
  } catch (error) {
    await showError('Document retry could not be queued', api.errorMessage(error))
  } finally {
    retryingDocumentId.value = null
  }
}

function uploadedRowAction(payload: { action: { id: string }, row: Record<string, unknown> }) {
  const document = payload.row as unknown as UploadedDocumentSummary
  if (payload.action.id === 'open') return openUploadedDocument(document)
  if (payload.action.id === 'review') openVerificationDrawer(document)
}

function officialRowAction(payload: { action: { id: string }, row: Record<string, unknown> }) {
  const document = payload.row as unknown as OfficialDocumentSummary
  if (payload.action.id === 'open') return openOfficialDocument(document)
  if (payload.action.id === 'retry') return retry(document)
}

function uploadedStatusColour(status: UploadedDocumentVerificationStatus) {
  if (status === 'VERIFIED') return 'success'
  if (status === 'REJECTED') return 'error'
  return 'warning'
}

function officialStatusColour(status: GeneratedDocumentStatus) {
  if (status === 'STORED') return 'success'
  if (status === 'FAILED') return 'error'
  if (status === 'GENERATING') return 'warning'
  return 'neutral'
}

function formatTimestamp(value?: string | null) {
  return value ? new Intl.DateTimeFormat('en-ZW', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—'
}

function formatBytes(value?: number | null) {
  if (!value) return '—'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Documents and records">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right>
          <UButton
            v-if="activeTab === 'uploaded'"
            label="Upload document"
            icon="i-lucide-upload"
            color="primary"
            @click="openUploadDrawer"
          />
          <UButton label="Refresh" icon="i-lucide-refresh-cw" color="neutral" variant="outline" :loading="loading" @click="load" />
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <UTabs v-model="activeTab" :items="tabs" :content="false" color="primary" variant="pill" />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="space-y-4 p-4">
        <section v-if="activeTab === 'uploaded'" class="space-y-4">
          <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <EmhareKpiCard label="Uploaded" :value="uploadedCounts.total" icon="i-lucide-files" />
            <EmhareKpiCard label="Pending verification" :value="uploadedCounts.pending" icon="i-lucide-clock-3" tone="warning" />
            <EmhareKpiCard label="Verified" :value="uploadedCounts.verified" icon="i-lucide-shield-check" tone="success" />
            <EmhareKpiCard label="Rejected" :value="uploadedCounts.rejected" icon="i-lucide-file-x-2" tone="error" />
          </div>

          <UAlert v-if="loadErrors.uploaded" color="error" variant="soft" title="Uploaded evidence could not be loaded" :description="loadErrors.uploaded" />
          <EmhareRegisterPanel
            title="Uploaded evidence"
            description="Private source documents awaiting, or retaining, an auditable verification decision."
            :record-count="filteredUploadedDocuments.length"
          >
            <template #actions>
              <USelect v-model="uploadedStatusFilter" :items="uploadedStatusItems" class="w-52" />
            </template>
            <EmhareDataTable
              :columns="uploadedColumns"
              :rows="tableRows(filteredUploadedDocuments as unknown as Record<string, unknown>[], uploadedTableState)"
              :total="tableTotal(filteredUploadedDocuments as unknown as Record<string, unknown>[], uploadedTableState)"
              :state="uploadedTableState"
              :loading="loading"
              :row-actions="[
                { id: 'open', label: 'Open evidence', icon: 'i-lucide-external-link' },
                { id: 'review', label: 'Review decision', icon: 'i-lucide-shield-check' }
              ]"
              @update:state="uploadedTableState = $event"
              @row-action="uploadedRowAction"
            >
              <template #documentTypeCode-cell="{ value }"><span class="font-medium">{{ String(value).replaceAll('_', ' ') }}</span></template>
              <template #ownerType-cell="{ value }">{{ String(value).replaceAll('_', ' ') }}</template>
              <template #ownerId-cell="{ value }"><code class="text-xs">{{ value }}</code></template>
              <template #uploadedAt-cell="{ value }">{{ formatTimestamp(String(value)) }}</template>
              <template #verifiedAt-cell="{ value }">{{ formatTimestamp(value ? String(value) : null) }}</template>
              <template #fileSizeBytes-cell="{ value }">{{ formatBytes(Number(value)) }}</template>
              <template #verificationStatus-cell="{ value }">
                <EmhareStatusPill :label="String(value)" :tone="uploadedStatusColour(value as UploadedDocumentVerificationStatus)" />
              </template>
            </EmhareDataTable>
          </EmhareRegisterPanel>
        </section>

        <section v-else class="space-y-4">
          <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <EmhareKpiCard label="Official records" :value="officialCounts.total" icon="i-lucide-file-check-2" />
            <EmhareKpiCard label="In generation" :value="officialCounts.queued" icon="i-lucide-loader-circle" tone="warning" />
            <EmhareKpiCard label="Stored" :value="officialCounts.stored" icon="i-lucide-archive" tone="success" />
            <EmhareKpiCard label="Failed" :value="officialCounts.failed" icon="i-lucide-file-warning" tone="error" />
          </div>

          <UAlert v-if="loadErrors.official" color="error" variant="soft" title="Official records could not be loaded" :description="loadErrors.official" />
          <EmhareRegisterPanel
            title="Generated official records"
            description="Immutable institution-issued records generated from approved workflow evidence."
            :record-count="filteredOfficialDocuments.length"
          >
            <template #actions>
              <USelect v-model="officialStatusFilter" :items="officialStatusItems" class="w-48" />
            </template>
            <EmhareDataTable
              :columns="officialColumns"
              :rows="tableRows(filteredOfficialDocuments as unknown as Record<string, unknown>[], officialTableState)"
              :total="tableTotal(filteredOfficialDocuments as unknown as Record<string, unknown>[], officialTableState)"
              :state="officialTableState"
              :loading="loading"
              :row-actions="[
                { id: 'open', label: 'Open PDF', icon: 'i-lucide-file-down' },
                { id: 'retry', label: 'Retry generation', icon: 'i-lucide-rotate-ccw', tone: 'warning' }
              ]"
              @update:state="officialTableState = $event"
              @row-action="officialRowAction"
            >
              <template #documentNumber-cell="{ value }"><code class="text-xs font-medium text-primary">{{ value }}</code></template>
              <template #status-cell="{ value }">
                <EmhareStatusPill :label="String(value)" :tone="officialStatusColour(value as GeneratedDocumentStatus)" />
              </template>
              <template #generatedAt-cell="{ value }">{{ formatTimestamp(value ? String(value) : null) }}</template>
              <template #sizeBytes-cell="{ value }">{{ formatBytes(value ? Number(value) : null) }}</template>
            </EmhareDataTable>
          </EmhareRegisterPanel>
        </section>

        <EmhareRecordDrawer
          v-model:open="drawerOpen"
          presentation="page"
          :title="drawerTitle"
          :description="drawerDescription"
          :submit-label="drawerKind === 'upload' ? 'Upload document' : verificationForm.decision === 'VERIFIED' ? 'Verify document' : 'Reject document'"
          :submit-icon="drawerKind === 'upload' ? 'i-lucide-upload' : verificationForm.decision === 'VERIFIED' ? 'i-lucide-shield-check' : 'i-lucide-file-x-2'"
          :busy="drawerSaving"
          :submit-disabled="drawerSubmitDisabled"
          width="lg"
          @submit="submitDrawer"
          @close="closeDrawer"
        >
          <div v-if="drawerKind === 'upload'" class="space-y-4">
            <UAlert color="primary" variant="soft" icon="i-lucide-lock-keyhole" title="Private governed evidence" description="Only genuine PDF, JPEG, and PNG files up to 10 MB are accepted. A SHA-256 checksum is recorded at intake." />
            <EmhareFormField v-model="uploadForm.ownerType" type="select" name="ownerType" label="Record type" :items="ownerTypeItems" required />
            <EmhareFormField v-model="uploadForm.ownerId" name="ownerId" label="Record ID" placeholder="UUID of the application, student, staff member, or workflow" required />
            <EmhareFormField v-model="uploadForm.documentTypeCode" type="searchable-select" name="documentTypeCode" label="Document type" :items="documentTypeItems" placeholder="Select the evidence type" required />
            <EmhareFormField v-model="uploadForm.replacesDocumentId" name="replacesDocumentId" label="Rejected document being replaced" description="Leave blank for a new document. A replacement must point to rejected evidence for the same record and document type." placeholder="Rejected document UUID" />
            <EmhareFormField v-model="uploadForm.file" type="drop-file" name="file" label="Document file" required />
          </div>

          <div v-else-if="drawerKind === 'verification' && selectedUpload" class="space-y-4">
            <UAlert
              :color="uploadedStatusColour(selectedUpload.verificationStatus)"
              variant="soft"
              :title="selectedUpload.originalFileName"
              :description="`${selectedUpload.documentTypeCode.replaceAll('_', ' ')} · ${selectedUpload.verificationStatus}`"
            />
            <EmhareDescriptionList :items="[
              { label: 'Business record', value: `${selectedUpload.ownerType.replaceAll('_', ' ')} · ${selectedUpload.ownerId}` },
              { label: 'Uploaded', value: formatTimestamp(selectedUpload.uploadedAt) },
              { label: 'Content', value: `${selectedUpload.mimeType} · ${formatBytes(selectedUpload.fileSizeBytes)}` },
              { label: 'SHA-256', value: selectedUpload.checksumSha256 }
            ]" />
            <UButton label="Open evidence in a new tab" icon="i-lucide-external-link" color="neutral" variant="outline" :loading="downloadingDocumentId === selectedUpload.id" @click="openUploadedDocument(selectedUpload)" />

            <template v-if="selectedUpload.verificationStatus === 'PENDING'">
              <EmhareFormField v-model="verificationForm.decision" type="select" name="verificationDecision" label="Decision" :items="verificationDecisionItems" required />
              <EmhareFormField
                v-if="verificationForm.decision === 'VERIFIED'"
                v-model="verificationForm.comment"
                type="textarea"
                name="verificationComment"
                label="Verification comment"
                placeholder="Record the checks performed and evidence matched"
              />
              <EmhareFormField
                v-else
                v-model="verificationForm.rejectionReason"
                type="textarea"
                name="rejectionReason"
                label="Rejection reason"
                description="Be specific enough for the owner to correct the evidence. At least 10 characters."
                placeholder="Explain exactly what must be replaced or corrected"
                required
              />
            </template>
            <UAlert
              v-else
              color="neutral"
              variant="soft"
              title="Decision is final"
              :description="selectedUpload.verificationStatus === 'REJECTED' ? selectedUpload.rejectionReason ?? 'No rejection reason recorded.' : selectedUpload.verificationComment ?? 'Evidence verified.'"
            />
          </div>
        </EmhareRecordDrawer>
      </div>
    </template>
  </UDashboardPanel>
</template>
