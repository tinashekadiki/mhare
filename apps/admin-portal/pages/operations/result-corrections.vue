<script setup lang="ts">
import Swal from 'sweetalert2'
import type {
  PublishedResultAmendmentSummary,
  PublishedResultPage,
  PublishedResultSummary,
  ResultCorrectionSource
} from '@emhare/portal-shell/types/assessment'

definePageMeta({ layout: 'dashboard' })

const api = useEmhareApi()
const auth = useEmhareAuth()
const toast = useToast()
const { showError } = useEmhareConfirm()
const academicPeriodContext = useAcademicPeriodContext()
const publishedResultPage = ref<PublishedResultPage>({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 })
const amendments = ref<PublishedResultAmendmentSummary[]>([])
const correctionSources = ref<ResultCorrectionSource[]>([])
const loading = ref(false)
const saving = ref(false)
const requestModalOpen = ref(false)
const selectedPublishedResult = ref<PublishedResultSummary | null>(null)
const searchStudentNumber = ref('')
const requestForm = reactive({ replacementModuleResultId: '', reason: '' })

const operatorUserId = computed(() => auth.currentUserProfile.value?.user.id ?? null)
const queueCounts = computed(() => ({
  review: amendments.value.filter(item => item.status === 'REQUESTED').length,
  approval: amendments.value.filter(item => item.status === 'REVIEWED').length,
  application: amendments.value.filter(item => item.status === 'APPROVED').length,
  applied: amendments.value.filter(item => item.status === 'APPLIED').length
}))
const correctionSourceItems = computed(() => correctionSources.value.map(source => ({
  label: `${source.batchNumber} · ${source.finalMark}% · ${source.grade} · ${source.remark}`,
  value: source.moduleResultId
})))

onMounted(() => load())
watch(academicPeriodContext.selectedAcademicPeriodId, () => void load(0))

async function load(page = publishedResultPage.value.page) {
  loading.value = true
  try {
    await academicPeriodContext.ensureAcademicPeriods()
    const query = new URLSearchParams({
      studentNumber: searchStudentNumber.value.trim(),
      page: String(page),
      size: String(publishedResultPage.value.size)
    })
    const [publishedResults, amendmentResponse] = await Promise.all([
      api.request<PublishedResultPage>(`/api/results/published-results?${query}`),
      api.request<PublishedResultAmendmentSummary[]>('/api/results/published-result-amendments')
    ])
    const content = publishedResults.content.filter(result => academicPeriodContext.matchesAcademicPeriod(result))
    publishedResultPage.value = {
      ...publishedResults,
      content,
      totalElements: content.length,
      totalPages: content.length ? 1 : 0,
      page: 0
    }
    amendments.value = amendmentResponse.filter(amendment => academicPeriodContext.matchesAcademicPeriod(amendment))
  } catch (error) {
    await showError('Published result controls could not be loaded', api.errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function search() {
  await load(0)
}

async function openRequest(publishedResult: PublishedResultSummary) {
  selectedPublishedResult.value = publishedResult
  requestForm.replacementModuleResultId = ''
  requestForm.reason = ''
  try {
    correctionSources.value = await api.request<ResultCorrectionSource[]>(
      `/api/results/published-results/${publishedResult.id}/correction-sources`
    )
    if (!correctionSources.value.length) {
      await showError(
        'No approved replacement evidence',
        'Complete the mark amendment, recalculate the Module result, and independently approve its replacement result batch first.'
      )
      return
    }
    requestModalOpen.value = true
  } catch (error) {
    await showError('Correction sources could not be loaded', api.errorMessage(error))
  }
}

async function requestCorrection() {
  if (!selectedPublishedResult.value || !requestForm.replacementModuleResultId || !requestForm.reason.trim()) return
  saving.value = true
  try {
    await api.request('/api/results/published-result-amendments', {
      method: 'POST',
      body: {
        originalPublishedResultId: selectedPublishedResult.value.id,
        replacementModuleResultId: requestForm.replacementModuleResultId,
        reason: requestForm.reason.trim()
      }
    })
    requestModalOpen.value = false
    await load()
    toast.add({
      title: 'Result correction requested',
      description: 'The original publication remains unchanged while independent review is pending.',
      color: 'success'
    })
  } catch (error) {
    await showError('Result correction could not be requested', api.errorMessage(error))
  } finally {
    saving.value = false
  }
}

function nextAction(amendment: PublishedResultAmendmentSummary) {
  const actorUserId = operatorUserId.value
  if (amendment.status === 'REQUESTED') {
    return actorUserId === amendment.requestedByUserId
      ? null
      : { action: 'review', label: 'Record independent review', title: 'Review result correction?' }
  }
  if (amendment.status === 'REVIEWED') {
    return actorUserId === amendment.requestedByUserId || actorUserId === amendment.reviewedByUserId
      ? null
      : { action: 'approve', label: 'Approve correction', title: 'Approve result correction?' }
  }
  if (amendment.status === 'APPROVED') {
    return actorUserId === amendment.approvedByUserId
      ? null
      : { action: 'apply', label: 'Release corrected version', title: 'Release corrected result version?' }
  }
  return null
}

function handoffMessage(amendment: PublishedResultAmendmentSummary) {
  if (nextAction(amendment)) return null
  if (amendment.status === 'REQUESTED') return 'Handoff required: the requester cannot review this correction.'
  if (amendment.status === 'REVIEWED') return 'Handoff required: the requester and reviewer cannot approve this correction.'
  if (amendment.status === 'APPROVED') return 'Handoff required: the approver cannot release the corrected version.'
  return null
}

async function move(amendment: PublishedResultAmendmentSummary, forcedAction?: 'reject') {
  const next = forcedAction
    ? { action: forcedAction, label: 'Reject correction', title: 'Reject result correction?' }
    : nextAction(amendment)
  if (!next) return
  const result = await Swal.fire({
    title: next.title,
    text: next.action === 'apply'
      ? 'This inserts a new immutable publication version. The original remains in the permanent lineage.'
      : 'The decision, actor, reason, and time are retained in the amendment history.',
    icon: next.action === 'reject' || next.action === 'apply' ? 'warning' : 'question',
    input: 'textarea',
    inputLabel: 'Decision reason',
    inputPlaceholder: 'Record the evidence reviewed and authority for this decision.',
    showCancelButton: true,
    confirmButtonText: next.label,
    confirmButtonColor: next.action === 'reject' ? '#B42318' : '#006633',
    inputValidator: value => value.trim() ? undefined : 'A decision reason is required.'
  })
  if (!result.isConfirmed || !result.value?.trim()) return
  try {
    await api.request(`/api/results/published-result-amendments/${amendment.id}/${next.action}`, {
      method: 'POST',
      body: { expectedVersion: amendment.version, reason: result.value.trim() }
    })
    await load()
    toast.add({
      title: next.action === 'apply' ? 'Corrected result version released' : `Correction ${next.action} recorded`,
      description: amendment.amendmentNumber,
      color: 'success'
    })
  } catch (error) {
    await showError('Correction decision could not be recorded', api.errorMessage(error))
  }
}

function statusColour(status: PublishedResultAmendmentSummary['status']) {
  if (status === 'APPLIED') return 'success'
  if (status === 'APPROVED') return 'primary'
  if (status === 'REVIEWED') return 'warning'
  if (status === 'REJECTED') return 'error'
  return 'neutral'
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Published result corrections">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right>
          <UButton label="Refresh" icon="i-lucide-refresh-cw" color="neutral" variant="outline" :loading="loading" @click="load()" />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="space-y-5 p-4 sm:p-6">
        <UAlert
          color="primary"
          variant="soft"
          icon="i-lucide-git-compare-arrows"
          title="Append-only correction control"
          description="Published results are never edited. A correction requires approved replacement calculation evidence, independent review and approval, then creates the next immutable publication version."
        />

        <section class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <UCard :ui="{ body: 'p-4' }"><p class="text-xs uppercase text-info">Review</p><p class="mt-2 text-2xl font-semibold">{{ queueCounts.review }}</p></UCard>
          <UCard :ui="{ body: 'p-4' }"><p class="text-xs uppercase text-warning">Approval</p><p class="mt-2 text-2xl font-semibold">{{ queueCounts.approval }}</p></UCard>
          <UCard :ui="{ body: 'p-4' }"><p class="text-xs uppercase text-primary">Release</p><p class="mt-2 text-2xl font-semibold">{{ queueCounts.application }}</p></UCard>
          <UCard :ui="{ body: 'p-4' }"><p class="text-xs uppercase text-success">Applied</p><p class="mt-2 text-2xl font-semibold">{{ queueCounts.applied }}</p></UCard>
        </section>

        <UCard :ui="{ body: 'p-4' }">
          <div class="flex flex-wrap items-end gap-3">
            <UFormField label="Student number" class="min-w-64 flex-1">
              <UInput v-model="searchStudentNumber" class="w-full" placeholder="Search current published results" @keyup.enter="search" />
            </UFormField>
            <UButton label="Search" icon="i-lucide-search" @click="search" />
          </div>
          <div class="mt-4 overflow-x-auto">
            <table class="w-full min-w-[760px] text-sm">
              <thead class="text-left text-xs uppercase text-muted">
                <tr><th class="py-2">Student</th><th>Module</th><th>Period</th><th>Version</th><th>Result</th><th>Published</th><th class="text-right">Control</th></tr>
              </thead>
              <tbody>
                <tr v-for="publishedResult in publishedResultPage.content" :key="publishedResult.id" class="border-t border-muted">
                  <td class="py-3 font-medium">{{ publishedResult.studentNumber }}</td>
                  <td>{{ publishedResult.moduleCode }} · {{ publishedResult.moduleName }}</td>
                  <td>{{ publishedResult.academicPeriodCode }}</td>
                  <td><UBadge :label="`v${publishedResult.publicationVersion}`" color="neutral" variant="subtle" /></td>
                  <td class="font-semibold">{{ publishedResult.finalMark }}% · {{ publishedResult.grade }} · {{ publishedResult.remark }}</td>
                  <td>{{ new Date(publishedResult.publishedAt).toLocaleString() }}</td>
                  <td class="text-right"><UButton label="Request correction" icon="i-lucide-file-pen-line" color="neutral" variant="outline" @click="openRequest(publishedResult)" /></td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="mt-4 flex flex-wrap items-center justify-between gap-3">
            <p class="text-xs text-muted">{{ publishedResultPage.totalElements }} current published result{{ publishedResultPage.totalElements === 1 ? '' : 's' }}</p>
            <div class="flex gap-2">
              <UButton label="Previous" color="neutral" variant="outline" :disabled="publishedResultPage.page === 0" @click="load(publishedResultPage.page - 1)" />
              <UButton label="Next" color="neutral" variant="outline" :disabled="publishedResultPage.page + 1 >= publishedResultPage.totalPages" @click="load(publishedResultPage.page + 1)" />
            </div>
          </div>
          <UAlert v-if="!loading && !publishedResultPage.content.length" class="mt-4" color="neutral" variant="soft" title="No current published results" description="Publish an approved result batch before requesting a correction." />
        </UCard>

        <section class="space-y-3">
          <div>
            <h2 class="text-base font-semibold">Correction decision queue</h2>
            <p class="text-sm text-muted">Each card preserves the original publication and the proposed replacement evidence side by side.</p>
          </div>
          <EmharePaginatedCollection v-slot="{ items: paginatedAmendments }" :items="amendments">
          <div class="space-y-3">
          <UCard v-for="amendment in paginatedAmendments" :key="amendment.id" :ui="{ body: 'p-4' }">
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p class="text-xs font-medium text-primary">{{ amendment.amendmentNumber }}</p>
                <h3 class="mt-1 font-semibold">{{ amendment.studentNumber }} · {{ amendment.moduleCode }} · {{ amendment.academicPeriodCode }}</h3>
                <p class="mt-1 text-xs text-muted">Original publication v{{ amendment.originalPublicationVersion }}</p>
              </div>
              <UBadge :label="amendment.status" :color="statusColour(amendment.status)" variant="subtle" />
            </div>
            <div class="mt-4 grid gap-3 md:grid-cols-2">
              <div class="rounded-md border border-muted p-3">
                <p class="text-xs uppercase text-muted">Permanent original</p>
                <p class="mt-2 text-lg font-semibold">{{ amendment.originalFinalMark }}% · {{ amendment.originalGrade }}</p>
                <p class="text-sm text-muted">{{ amendment.originalRemark }}</p>
              </div>
              <div class="rounded-md border border-primary/30 bg-primary/5 p-3">
                <p class="text-xs uppercase text-primary">Approved replacement evidence</p>
                <p class="mt-2 text-lg font-semibold">{{ amendment.proposedFinalMark }}% · {{ amendment.proposedGrade }}</p>
                <p class="text-sm text-muted">{{ amendment.proposedRemark }}</p>
              </div>
            </div>
            <p class="mt-3 text-sm">{{ amendment.requestReason }}</p>
            <div class="mt-4 flex flex-wrap items-center justify-between gap-3">
              <p v-if="handoffMessage(amendment)" class="text-xs font-medium text-warning">{{ handoffMessage(amendment) }}</p>
              <p v-else class="text-xs text-muted">Decision version {{ amendment.version }}</p>
              <div class="flex flex-wrap gap-2">
                <UButton v-if="['REQUESTED', 'REVIEWED'].includes(amendment.status) && amendment.requestedByUserId !== operatorUserId" label="Reject" icon="i-lucide-x" color="error" variant="soft" @click="move(amendment, 'reject')" />
                <UButton v-if="nextAction(amendment)" :label="nextAction(amendment)?.label" icon="i-lucide-arrow-right" @click="move(amendment)" />
              </div>
            </div>
          </UCard>
          </div>
          </EmharePaginatedCollection>
          <UAlert v-if="!loading && !amendments.length" color="neutral" variant="soft" title="No correction requests" description="The permanent correction queue is empty." />
        </section>
      </div>
    </template>
  </UDashboardPanel>

  <EmhareRecordDrawer v-model:open="requestModalOpen" title="Request published result correction" description="Link an immutable publication to approved replacement evidence" width="lg">
    <template #body>
      <div class="space-y-4">
        <UAlert
          v-if="selectedPublishedResult"
          color="warning"
          variant="soft"
          title="The published result will not be edited"
          :description="`${selectedPublishedResult.studentNumber} · ${selectedPublishedResult.moduleCode} · v${selectedPublishedResult.publicationVersion} remains permanent.`"
        />
        <UFormField label="Approved replacement result batch" required>
          <USelect v-model="requestForm.replacementModuleResultId" :items="correctionSourceItems" class="w-full" placeholder="Select replacement evidence" />
        </UFormField>
        <UFormField label="Correction reason" required>
          <UTextarea v-model="requestForm.reason" class="w-full" :rows="4" placeholder="Explain the source amendment, calculation evidence, and authority for correction." />
        </UFormField>
      </div>
    </template>
    <template #footer>
      <div class="flex w-full justify-end gap-2">
        <UButton label="Cancel" color="neutral" variant="outline" @click="requestModalOpen = false" />
        <EmhareGuidedActionButton label="Submit correction request" :loading="saving" guidance-title="Correction evidence is incomplete" :guidance-instructions="[...(!requestForm.replacementModuleResultId ? ['Select the replacement Module result evidence.'] : []), ...(!requestForm.reason.trim() ? ['Record the correction reason and authority.'] : [])]" @click="requestCorrection" />
      </div>
    </template>
  </EmhareRecordDrawer>
</template>
