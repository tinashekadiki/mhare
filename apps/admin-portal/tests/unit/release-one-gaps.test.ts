// Author: Tinashe K

import { computed, defineComponent, h, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { config, flushPromises, mount, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import type { AcademicSetupOverview } from '../../../../packages/portal-shell/types/academic'
import type { FinanceFeeCatalogueSummary } from '../../../../packages/portal-shell/types/finance'

const sweetAlertFire = vi.fn()
vi.mock('sweetalert2', () => ({ default: { fire: sweetAlertFire } }))

Object.assign(globalThis, { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch })
vi.stubGlobal('definePageMeta', vi.fn())
vi.stubGlobal('useHead', vi.fn())
config.global.renderStubDefaultSlot = true

function vueFilesUnder(root: string): string[] {
  return readdirSync(root, { withFileTypes: true }).flatMap((entry) => {
    const absolutePath = join(root, entry.name)
    if (entry.isDirectory()) {
      if (['node_modules', '.nuxt', '.output'].includes(entry.name)) return []
      return vueFilesUnder(absolutePath)
    }
    return entry.name.endsWith('.vue') ? [absolutePath] : []
  })
}

function oversizedSidepanelForms(): string[] {
  const roots = [resolve('apps'), resolve('packages')]
  const violations: string[] = []
  for (const file of roots.flatMap(vueFilesUnder)) {
    const source = readFileSync(file, 'utf8')
    const recordDrawerPattern = /<EmhareRecordDrawer\b([\s\S]*?)<\/EmhareRecordDrawer>/g
    let drawerMatch: RegExpExecArray | null
    let drawerIndex = 0
    while ((drawerMatch = recordDrawerPattern.exec(source))) {
      drawerIndex += 1
      const drawer = drawerMatch[0]
      const openingTag = drawer.slice(0, drawer.indexOf('>') + 1)
      const fieldCount = drawer.match(/<(?:UFormField|EmhareFormField)\b/g)?.length ?? 0
      const sectionCount = drawer.match(/<EmhareFormSection\b/g)?.length ?? 0
      const hasJourney = /<EmhareJourneyStepper\b/.test(drawer)
      const isLargeForm = fieldCount >= 7 || sectionCount >= 2 || hasJourney
      const usesInShellPageWorkspace = /\bpresentation\s*=/.test(openingTag)
        && /(?:"page"|'page')/.test(openingTag)
      if (isLargeForm && !usesInShellPageWorkspace) {
        violations.push(`${file} drawer ${drawerIndex} (${fieldCount} fields, ${sectionCount} sections)`)
      }
    }
  }
  return violations
}

describe('Release 1 Core and Admissions operator usability', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('useEmhareAuth', () => ({ hasPermission: vi.fn(() => true), hasRole: vi.fn(() => true) }))
    vi.stubGlobal('useEmhareConfirm', () => ({
      confirmAction: vi.fn(),
      showError: vi.fn(),
      showSuccess: vi.fn()
    }))
  })

  it('configures shared bank details and offer dates at their authoritative owners', () => {
    const coreSource = readFileSync(resolve('apps/admin-portal/pages/operations/core.vue'), 'utf8')
    const intakeSource = readFileSync(resolve('apps/admin-portal/pages/operations/academic-calendar/intakes/[intakeId].vue'), 'utf8')
    const admissionsSource = readFileSync(resolve('apps/admin-portal/pages/operations/admissions/[applicationId].vue'), 'utf8')

    expect(coreSource).toContain('title="Bank details"')
    expect(coreSource).toContain('v-for="(bankAccount, index) in profileBankAccounts"')
    expect(coreSource).toContain('v-model="bankAccount.currencyCode"')
    expect(coreSource).toContain('v-model="bankAccount.accountNumber"')
    expect(coreSource).toContain('v-model="profileForm.registrarName"')
    expect(coreSource).toContain('label="Registrar name"')
    expect(coreSource).toContain('label="Choose registrar signature"')
    expect(coreSource).toContain('INSTITUTION_REGISTRAR_SIGNATURE')
    expect(coreSource).toContain('registrarSignatureDocumentId')
    expect(intakeSource).toContain('label="Offer acceptance deadline"')
    expect(intakeSource).toContain('label="Commencement date"')
    expect(admissionsSource).toContain('dates are taken from <strong>')
    expect(admissionsSource).not.toContain('id="offer-deadline"')
    expect(admissionsSource).not.toContain('id="offer-commencement"')
  })

  it('keeps every large form in an in-shell page workspace instead of a side panel', () => {
    expect(oversizedSidepanelForms()).toEqual([])
  })

  it('loads the Core audit register, session report and operational KPIs together', async () => {
    const request = vi.fn(async (path: string) => {
      if (path === '/api/core/audit-events') return [{ id: 'audit-1', occurredAt: '2026-08-12T08:00:00Z', summary: 'Updated user', subjectType: 'PLATFORM_USER', eventType: 'CORE_USER_UPDATED' }]
      if (path === '/api/core/login-events') return [{ id: 'login-1', occurredAt: '2026-08-12T08:00:00Z', username: 'admin', outcome: 'SUCCESS' }]
      if (path === '/api/core/reports/overview') return { inventory: { userCount: 12, roleCount: 4 }, auditEventsLast24Hours: 8, loginSessionsLast24Hours: 3 }
      return []
    })
    vi.stubGlobal('useEmhareApi', () => ({ request, errorMessage: vi.fn() }))
    vi.stubGlobal('useEmhareAuth', () => ({ hasPermission: (permission: string) => permission === 'CORE_AUDIT_READ' }))
    vi.stubGlobal('useAcademicSetup', () => ({ ensureOverview: vi.fn(), academicUnits: ref([]) }))

    const CorePage = (await import('../../pages/operations/core.vue')).default
    const SlotStub = defineComponent({
      setup(_, { slots }) {
        return () => h('div', [slots.default?.(), slots.header?.(), slots.body?.()])
      }
    })
    const wrapper = mount(CorePage, {
      global: {
        components: { UDashboardPanel: SlotStub, UDashboardNavbar: SlotStub, UDashboardToolbar: SlotStub, EmhareRegisterPanel: SlotStub },
        stubs: { EmhareDataTable: true, EmhareKpiCard: true, EmhareStatusPill: true, EmhareRecordDrawer: true }
      }
    })
    await flushPromises()

    expect(request).toHaveBeenCalledWith('/api/core/audit-events')
    expect(request).toHaveBeenCalledWith('/api/core/login-events')
    expect(request).toHaveBeenCalledWith('/api/core/reports/overview')
    expect(wrapper.exists()).toBe(true)
  })

  it('requires an actionable reason before returning an academic recommendation', async () => {
    const request = vi.fn(async () => undefined)
    vi.stubGlobal('useEmhareApi', () => ({ request, errorMessage: vi.fn() }))
    vi.stubGlobal('useRoute', () => ({ params: { applicationId: 'application-1' }, query: {} }))
    sweetAlertFire.mockResolvedValue({ isConfirmed: true, value: 'Please reconsider the verified science evidence.' })

    const AdmissionsPage = (await import('../../pages/operations/admissions/[applicationId].vue')).default
    const wrapper = shallowMount(AdmissionsPage, { global: { stubs: { teleport: true } } })
    await (wrapper.vm as unknown as { returnRecommendation: () => Promise<void> }).returnRecommendation()

    expect(sweetAlertFire).toHaveBeenCalledWith(expect.objectContaining({
      title: 'Return academic recommendation',
      input: 'textarea'
    }))
    const dialog = sweetAlertFire.mock.calls[0]?.[0] as { inputValidator: (value: string) => string | undefined }
    expect(dialog.inputValidator('too short')).toBe('Record at least 10 characters.')
    expect(dialog.inputValidator('Enough detail to act on.')).toBeUndefined()
    expect(request).toHaveBeenCalledWith(
      '/api/admissions/applications/application-1/choices//academic-recommendation/return',
      expect.objectContaining({ method: 'POST', body: { reason: 'Please reconsider the verified science evidence.' } })
    )
  })

  it('leaves the recommendation unchanged when the operator cancels the return dialog', async () => {
    const request = vi.fn(async () => undefined)
    vi.stubGlobal('useEmhareApi', () => ({ request, errorMessage: vi.fn() }))
    vi.stubGlobal('useRoute', () => ({ params: { applicationId: 'application-1' }, query: {} }))
    sweetAlertFire.mockResolvedValue({ isConfirmed: false })

    const AdmissionsPage = (await import('../../pages/operations/admissions/[applicationId].vue')).default
    const wrapper = shallowMount(AdmissionsPage)
    await (wrapper.vm as unknown as { returnRecommendation: () => Promise<void> }).returnRecommendation()

    expect(request).not.toHaveBeenCalledWith(expect.stringContaining('/academic-recommendation/return'), expect.anything())
  })

  it('generates an offer letter and previews the stored PDF in the operator-opened tab', async () => {
    vi.useFakeTimers()
    const previewTab = { location: { href: 'about:blank' }, close: vi.fn(), opener: window }
    const openWindow = vi.spyOn(window, 'open').mockReturnValue(previewTab as unknown as Window)
    const application = {
      id: 'application-61', applicationNumber: 'EMH-AUG-2026-00000061', applicantNumber: 'A000061',
      applicantName: 'Wesley Oneill', intakeId: 'intake-1', intakeCode: 'AUG-2026', applicationTypeId: 'type-1',
      applicationTypeName: 'Undergraduate', status: 'ADMITTED', paymentClearanceStatus: 'PAID',
      programmeChoices: []
    }
    const workspace = {
      application,
      profile: { firstName: 'Wesley', middleNames: null, lastName: 'Oneill', applicantNumber: 'A000061',
        applicantCategoryCode: 'LOCAL', primaryEmail: 'wesley@example.test', primaryPhone: null, completenessPercentage: 100 },
      sections: [], nextOfKin: [], employmentHistory: [], referees: [], priorUzDeclaration: null,
      professionalAchievementsDeclaredNone: true, professionalAchievements: [], programmeEntryPreferences: [],
      qualifications: [], documents: { requirements: [], pendingRequirementCodes: [], missingRequirementCodes: [],
        rejectedRequirementCodes: [], requiredDocumentsUploaded: true, requiredDocumentsVerified: true },
      readyForSubmission: true, missingRequirements: [], declarationAcceptedAt: null, declarationVersion: null,
      workflowProgress: { currentStageCode: 'OFFER', stages: [] }
    }
    const requestedCase = {
      workspace, academicReview: null, academicRecommendation: null, admissionDecision: null,
      offer: { id: 'offer-1', offerNumber: 'OFR-AUG-2026-00000001' },
      documentVersions: [{ id: 'version-6', version: 6, status: 'REQUESTED', generatedDocumentId: null,
        documentNumber: null, checksumSha256: null, requestedAt: '2026-08-16T14:00:00Z', storedAt: null,
        failureReason: null }],
      publications: [], auditHistory: [], blockers: [], availableActions: ['GENERATE_OFFER_DOCUMENT']
    }
    const storedCase = {
      ...requestedCase,
      documentVersions: [{ ...requestedCase.documentVersions[0], status: 'STORED',
        generatedDocumentId: 'generated-document-6', documentNumber: 'OFFER-OFR-AUG-2026-00000001-V6',
        storedAt: '2026-08-16T14:00:01Z' }]
    }
    let workItemReads = 0
    const request = vi.fn(async (path: string) => {
      if (path.includes('/work-items/')) {
        workItemReads += 1
        return workItemReads >= 3 ? storedCase : requestedCase
      }
      if (path.endsWith('/document-generation')) {
        return { id: 'version-6', documentVersion: 6, status: 'REQUESTED' }
      }
      if (path.includes('/api/documents/generated-document-6/download?disposition=inline')) {
        return { documentId: 'generated-document-6', downloadUrl: 'http://localhost:9000/offer-v6.pdf' }
      }
      return {}
    })
    vi.stubGlobal('useEmhareApi', () => ({ request, errorMessage: vi.fn() }))
    vi.stubGlobal('useRoute', () => ({ params: { applicationId: 'application-61' }, query: {} }))

    const AdmissionsPage = (await import('../../pages/operations/admissions/[applicationId].vue')).default
    const wrapper = shallowMount(AdmissionsPage)
    await flushPromises()

    const generation = (wrapper.vm as unknown as { generateOfferDocument: () => Promise<void> })
      .generateOfferDocument()
    await flushPromises()
    await vi.advanceTimersByTimeAsync(500)
    await generation

    expect(openWindow).toHaveBeenCalledWith('about:blank', '_blank')
    expect(request).toHaveBeenCalledWith('/api/admissions/offers/offer-1/document-generation', { method: 'POST' })
    expect(request).toHaveBeenCalledWith('/api/documents/generated-document-6/download?disposition=inline')
    expect(previewTab.location.href).toBe('http://localhost:9000/offer-v6.pdf')
    expect(previewTab.close).not.toHaveBeenCalled()
    vi.useRealTimers()
  })

  it('records a qualification decision from the consolidated applicant workspace', async () => {
    let decisionFails = false
    const application = {
      id: 'application-61', applicationNumber: 'EMH-AUG-2026-00000061', applicantNumber: 'A000061',
      applicantName: 'Wesley Oneill', intakeId: 'intake-1', intakeCode: 'AUG-2026', applicationTypeId: 'type-1',
      applicationTypeName: 'Undergraduate', status: 'SUBMITTED', paymentClearanceStatus: 'PAID',
      programmeChoices: [{ id: 'choice-1', programmeId: 'programme-1', programmeCode: 'HCS', programmeName: 'Computer Science',
        choiceRank: 1, choiceStatus: 'PENDING', awardName: 'BSc', owningAcademicUnitName: 'Computing', evaluationSummary: null,
        decisionReason: null }]
    }
    const qualification = {
      id: 'qualification-1', level: 'A_LEVEL', version: 3, verificationStatus: 'CAPTURED', examBody: null,
      institutionName: 'Harare High', centreNumber: 'H001', candidateNumber: '61', yearWritten: 2025,
      results: [{ id: 'result-1', subjectNameSnapshot: 'Mathematics', grade: 'B', points: 4 }]
    }
    const workflowProgress = {
      currentStageCode: 'VERIFICATION',
      stages: ['VERIFICATION', 'ELIGIBILITY', 'ACADEMIC_REVIEW', 'ADMISSION_DECISION', 'OFFER', 'RESPONSE'].map((code, index) => ({
        sequence: index + 1, code, label: code, state: index === 0 ? 'CURRENT' : 'PENDING', statusLabel: 'Waiting', detail: 'Workflow detail', occurredAt: null
      }))
    }
    const workItem = {
      workspace: {
        application,
        profile: { firstName: 'Wesley', middleNames: null, lastName: 'Oneill', applicantNumber: 'A000061',
          applicantCategoryCode: 'LOCAL', primaryEmail: 'wesley@example.test', primaryPhone: null, completenessPercentage: 100 },
        sections: [], nextOfKin: [], employmentHistory: [], referees: [], priorUzDeclaration: null,
        professionalAchievementsDeclaredNone: true, professionalAchievements: [], programmeEntryPreferences: [],
        qualifications: [qualification],
        documents: { requirements: [], pendingRequirementCodes: [], missingRequirementCodes: [], rejectedRequirementCodes: [], requiredDocumentsUploaded: true, requiredDocumentsVerified: true },
        readyForSubmission: true, missingRequirements: [], declarationAcceptedAt: null, declarationVersion: null, workflowProgress
      },
      academicReview: null, academicRecommendation: null, admissionDecision: null, offer: null,
      documentVersions: [], publications: [], auditHistory: [], blockers: [] as string[], availableActions: ['RECALCULATE_ELIGIBILITY']
    }
    const request = vi.fn(async (path: string) => {
      if (path.includes('/work-items/')) return workItem
      if (decisionFails) throw new Error('decision unavailable')
      return {}
    })
    const showError = vi.fn(async () => undefined)
    const showSuccess = vi.fn(async () => undefined)
    vi.stubGlobal('useEmhareApi', () => ({ request, errorMessage: vi.fn() }))
    vi.stubGlobal('useRoute', () => ({ params: { applicationId: 'application-61' }, query: {} }))
    vi.stubGlobal('useEmhareConfirm', () => ({ confirmAction: vi.fn(), showError, showSuccess }))
    sweetAlertFire.mockResolvedValue({ isConfirmed: true, value: 'Matched the uploaded certificate and subject results.' })

    const AdmissionsPage = (await import('../../pages/operations/admissions/[applicationId].vue')).default
    const SlotStub = defineComponent({
      setup(_, { slots }) {
        return () => h('div', [slots.header?.(), slots.leading?.(), slots.left?.(), slots.right?.(), slots.body?.(), slots.default?.(), slots.footer?.()])
      }
    })
    const ActionStub = defineComponent({
      inheritAttrs: false,
      props: ['label'],
      emits: ['click'],
      template: '<button @click="$emit(\'click\')">{{ label }}</button>'
    })
    const AlertStub = defineComponent({
      props: ['title', 'description'],
      template: '<div>{{ title }} {{ description }}</div>'
    })
    const StatusPillStub = defineComponent({
      props: ['label'],
      template: '<span>{{ label }}</span>'
    })
    const wrapper = mount(AdmissionsPage, {
      global: {
        stubs: {
          UDashboardPanel: SlotStub, UDashboardNavbar: SlotStub, UDashboardToolbar: SlotStub, UCard: SlotStub,
          UAlert: AlertStub, UButton: ActionStub, UBadge: SlotStub, UIcon: true, USkeleton: true, EmhareStatusPill: StatusPillStub,
          EmhareKpiCard: true, EmhareDescriptionList: true, EmhareFeedbackState: true, UDashboardSidebarCollapse: true
        }
      }
    })
    await flushPromises()
    expect(wrapper.text()).toContain('Current Admissions action')
    expect(wrapper.text()).toContain('Step 1 of 6')
    expect(wrapper.text()).toContain("current position from verification through response")
    await wrapper.findAll('button').find(button => button.text() === 'Verify qualification')!.trigger('click')
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === 'Reject qualification')!.trigger('click')
    await flushPromises()
    type MutableTestWorkItem = Omit<typeof workItem, 'academicReview' | 'auditHistory'> & {
      academicReview: null | {
        id: string, programmeChoiceId: string, status: string, recommendationAcademicUnitId: string,
        recommendationAcademicUnitName: string, claimedByUserId: string | null, claimedAt: string | null,
        completedAt: string | null, version: number
      }
      auditHistory: Array<{
        id: string, fromStatus: string | null, toStatus: string, reason: string,
        changedByUserId: string, changedAt: string
      }>
    }
    const admissionsViewModel = wrapper.vm as unknown as {
      workspace: typeof workItem.workspace
      workItem: MutableTestWorkItem
    }
    admissionsViewModel.workspace.qualifications[0]!.verificationStatus = 'VERIFIED'
    await nextTick()
    expect(wrapper.text()).toContain('Review starts automatically')
    admissionsViewModel.workspace.workflowProgress.currentStageCode = 'ELIGIBILITY'
    admissionsViewModel.workItem.availableActions = ['RECALCULATE_ELIGIBILITY', 'RESOLVE_ELIGIBILITY']
    admissionsViewModel.workItem.blockers = ['Duplicate check requires review.']
    await nextTick()
    expect(wrapper.text()).toContain('Processing blockers')
    expect(wrapper.findAll('button').some(button => button.text() === 'Recalculate eligibility')).toBe(true)
    expect(wrapper.findAll('button').some(button => button.text() === 'Resolve eligibility')).toBe(true)
    admissionsViewModel.workspace.application.programmeChoices[0]!.choiceStatus = 'UNDER_ACADEMIC_REVIEW'
    admissionsViewModel.workItem.academicReview = {
      id: 'review-1', programmeChoiceId: 'choice-1', status: 'OPEN', recommendationAcademicUnitId: 'faculty-1',
      recommendationAcademicUnitName: 'Faculty of Computing', claimedByUserId: null, claimedAt: null, completedAt: null, version: 0
    }
    admissionsViewModel.workItem.auditHistory = [{
      id: 'status-event-1', fromStatus: 'UNDER_REVIEW', toStatus: 'ELIGIBLE', reason: 'Meets the entry requirements.',
      changedByUserId: 'reviewer-1', changedAt: '2026-08-15T17:43:24Z'
    }]
    await nextTick()
    const recordedEligibility = wrapper.get('[data-testid="recorded-eligibility"]')
    expect(recordedEligibility.text()).toContain('Recorded eligibility')
    expect(recordedEligibility.text()).toContain('Eligible')
    expect(recordedEligibility.text()).toContain('Meets the entry requirements.')
    await (wrapper.vm as unknown as {
      recordQualificationDecision: (
        qualification: { id: string, level: string, version: number },
        decision: 'VERIFIED' | 'REJECTED'
      ) => Promise<void>
    }).recordQualificationDecision({ id: 'qualification-1', level: 'A_LEVEL', version: 3 }, 'VERIFIED')

    expect(request).toHaveBeenCalledWith(
      '/api/admissions/applications/application-61/qualifications/qualification-1/decision',
      {
        method: 'POST',
        body: {
          decision: 'VERIFIED',
          reason: 'Matched the uploaded certificate and subject results.',
          expectedVersion: 3
        }
      }
    )

    sweetAlertFire.mockResolvedValueOnce({ isConfirmed: false })
    await (wrapper.vm as unknown as {
      recordQualificationDecision: (qualification: { id: string, level: string, version: number }, decision: 'VERIFIED' | 'REJECTED') => Promise<void>
    }).recordQualificationDecision(qualification, 'REJECTED')

    sweetAlertFire.mockResolvedValueOnce({ isConfirmed: true, value: 'The certificate details do not match the captured results.' })
    await (wrapper.vm as unknown as {
      recordQualificationDecision: (qualification: { id: string, level: string, version: number }, decision: 'VERIFIED' | 'REJECTED') => Promise<void>
    }).recordQualificationDecision(qualification, 'REJECTED')
    expect(showSuccess).toHaveBeenCalledWith('Qualification rejected', 'The recorded reason is retained with the application evidence.')

    decisionFails = true
    sweetAlertFire.mockResolvedValueOnce({ isConfirmed: true, value: '' })
    await (wrapper.vm as unknown as {
      recordQualificationDecision: (qualification: { id: string, level: string, version: number }, decision: 'VERIFIED' | 'REJECTED') => Promise<void>
    }).recordQualificationDecision(qualification, 'VERIFIED')
    expect(showError).toHaveBeenCalledWith('Qualification decision could not be recorded', undefined)

    const rejectDialog = sweetAlertFire.mock.calls.find(call => call[0]?.title === 'Reject A Level evidence?')?.[0] as {
      inputValidator: (value: string) => string | undefined
    }
    expect(rejectDialog.inputValidator('short')).toContain('at least 10 characters')
    expect(rejectDialog.inputValidator('Enough detail to reject this evidence.')).toBeUndefined()
  })

  it('creates programme requirements from a dedicated Admissions setup workspace', async () => {
    let failRequirementSave = false
    let failRequirementLoad = false
    let failRequirementApproval = false
    let confirmRequirementApproval = true
    let canManageRequirements = true
    const requirementSet = {
      id: 'requirement-1', programmeId: 'programme-1', applicationTypeId: 'type-1', intakeId: 'intake-1',
      versionCode: 'HCS-AUG-2026.1', effectiveFrom: '2026-08-01', effectiveTo: null, status: 'DRAFT',
      minimumTotalPoints: 10, requiresEnglish: true, requiresMathematicsOrScience: true,
      advancedRulesVersion: null, approvedAt: null, qualificationGroups: []
    }
    const approvedRequirementSet = {
      ...requirementSet, id: 'requirement-2', status: 'APPROVED', effectiveTo: '2026-12-31',
      minimumTotalPoints: null, requiresEnglish: false, requiresMathematicsOrScience: false,
      qualificationGroups: undefined
    }
    const retiredRequirementSet = { ...requirementSet, id: 'requirement-3', status: 'RETIRED' }
    const request = vi.fn(async (path: string, options?: { method?: string }) => {
      if (failRequirementLoad && options?.method !== 'POST') throw new Error('catalogue unavailable')
      if (path === '/api/admissions/application-types') {
        return [{ id: 'type-1', code: 'UNDERGRAD', name: 'Undergraduate', active: true }]
      }
      if (path === '/api/admissions/requirement-sets' && options?.method === 'POST') {
        if (failRequirementSave) throw new Error('save unavailable')
        return requirementSet
      }
      if (path.includes('/approve')) {
        if (failRequirementApproval) throw new Error('approval unavailable')
        return requirementSet
      }
      if (path === '/api/admissions/requirement-sets') return [requirementSet, approvedRequirementSet, retiredRequirementSet]
      return undefined
    })
    const ensureOverview = vi.fn(async () => undefined)
    const confirmAction = vi.fn(async () => confirmRequirementApproval)
    const showError = vi.fn(async () => undefined)
    const routeQuery: Record<string, string | Array<string | null> | undefined> = {
      programmeId: 'programme-1', applicationTypeId: ['type-1', null], intakeId: 'intake-1'
    }
    const academicOverview = ref({
      programmes: [{ id: 'programme-1', code: 'HCS', name: 'Computer Science', status: 'ACTIVE' }],
      intakes: [{ id: 'intake-1', code: 'AUG-2026', name: 'August 2026', status: 'OPEN' }]
    })
    vi.stubGlobal('useEmhareApi', () => ({ request, errorMessage: vi.fn() }))
    vi.stubGlobal('useToast', () => ({ add: vi.fn() }))
    vi.stubGlobal('useEmhareConfirm', () => ({ confirmAction, showError, showSuccess: vi.fn() }))
    vi.stubGlobal('useRoute', () => ({ query: routeQuery }))
    vi.stubGlobal('useEmhareAuth', () => ({ hasPermission: () => canManageRequirements, hasRole: () => true }))
    vi.stubGlobal('useAcademicSetup', () => ({
      overview: academicOverview,
      ensureOverview
    }))
    vi.stubGlobal('useAcademicPeriodContext', () => ({ selectedAcademicPeriodId: ref(null), matchesIntake: () => true }))

    const ProgrammeRequirementsPage = (await import('../../pages/operations/programme-requirements.vue')).default
    const SlotStub = defineComponent({
      setup(_, { slots }) {
        return () => h('div', [slots.header?.(), slots.leading?.(), slots.right?.(), slots.body?.(), slots.default?.(), slots.footer?.()])
      }
    })
    const ActionStub = defineComponent({
      props: ['label'],
      emits: ['click'],
      template: '<button @click="$emit(\'click\')">{{ label }}</button>'
    })
    const wrapper = mount(ProgrammeRequirementsPage, {
      global: {
        stubs: {
          UDashboardPanel: SlotStub, UDashboardNavbar: SlotStub, UCard: SlotStub, UFormField: SlotStub,
          UButton: ActionStub, EmhareGuidedActionButton: ActionStub, UAlert: true, USelect: true,
          USelectMenu: true, UInput: true, UCheckbox: true, UEmpty: true, EmhareStatusPill: true,
          UDashboardSidebarCollapse: true, USkeleton: true
        }
      }
    })
    await flushPromises()
    const viewModel = wrapper.vm as unknown as {
      startCreation: () => void
      requirementForm: {
        programmeId: string, applicationTypeId: string, intakeId: string, versionCode: string,
        effectiveFrom: string, minimumTotalPoints: number | null,
        qualificationGroups: Array<{
          code: string, name: string, minimumSatisfiedItems: number,
          items: Array<{ qualificationLevel: string, minimumCount: number }>
        }>
      }
      saveRequirementSet: () => Promise<void>
      addQualificationGroup: () => void
      addQualificationItem: (group: unknown) => void
      approveRequirementSet: (requirementSetToApprove: { id: string, versionCode: string }) => Promise<void>
      loadRequirements: () => Promise<void>
      programmeLabel: (id: string) => string
      applicationTypeLabel: (id: string) => string
      intakeLabel: (id: string | null) => string
      formatStatus: (value: string) => string
      formatDate: (value: string | null) => string
    }
    await wrapper.findAll('button').find(button => button.text() === 'New requirement set')!.trigger('click')
    await nextTick()
    Object.assign(viewModel.requirementForm, {
      programmeId: 'programme-1', applicationTypeId: 'type-1', intakeId: 'intake-1',
      versionCode: 'HCS-AUG-2026.1', effectiveFrom: '2026-08-01', minimumTotalPoints: 10
    })
    await viewModel.saveRequirementSet()
    await nextTick()
    expect(wrapper.text()).toContain('HCS-AUG-2026.1')

    expect(ensureOverview).toHaveBeenCalledOnce()
    expect(request).toHaveBeenCalledWith('/api/admissions/requirement-sets', expect.objectContaining({
      method: 'POST',
      body: expect.objectContaining({
        programmeId: 'programme-1', applicationTypeId: 'type-1', intakeId: 'intake-1',
        versionCode: 'HCS-AUG-2026.1', minimumTotalPoints: 10
      })
    }))

    viewModel.startCreation()
    viewModel.addQualificationGroup()
    const qualificationGroup = viewModel.requirementForm.qualificationGroups[0]!
    qualificationGroup.name = 'Recognised entry qualification'
    viewModel.addQualificationItem(qualificationGroup)
    await nextTick()
    expect(wrapper.text()).toContain('Qualification group 1')
    expect(wrapper.text()).toContain('Add alternative route')
    Object.assign(viewModel.requirementForm, {
      programmeId: 'programme-1', applicationTypeId: 'type-1', intakeId: 'intake-1',
      versionCode: 'HCS-AUG-2026.2', effectiveFrom: '2026-08-01', minimumTotalPoints: 12
    })
    await viewModel.saveRequirementSet()
    await nextTick()
    expect(request).toHaveBeenCalledWith('/api/admissions/requirement-sets', expect.objectContaining({
      body: expect.objectContaining({
        qualificationGroups: [expect.objectContaining({
          code: 'ROUTE_1', name: 'Recognised entry qualification', minimumSatisfiedItems: 1,
          items: [expect.objectContaining({ sortOrder: 1 }), expect.objectContaining({ sortOrder: 2 })]
        })]
      })
    }))

    await viewModel.approveRequirementSet(requirementSet)
    expect(confirmAction).toHaveBeenCalled()
    expect(request).toHaveBeenCalledWith('/api/admissions/requirement-sets/requirement-1/approve', { method: 'POST' })

    confirmRequirementApproval = false
    await viewModel.approveRequirementSet(requirementSet)
    confirmRequirementApproval = true
    failRequirementApproval = true
    await viewModel.approveRequirementSet(requirementSet)
    expect(showError).toHaveBeenCalledWith('Programme requirements could not be approved', undefined)
    failRequirementApproval = false

    viewModel.startCreation()
    viewModel.requirementForm.programmeId = ''
    viewModel.requirementForm.applicationTypeId = ''
    viewModel.requirementForm.versionCode = ''
    viewModel.requirementForm.effectiveFrom = ''
    viewModel.addQualificationGroup()
    const invalidGroup = viewModel.requirementForm.qualificationGroups[0]!
    invalidGroup.code = ''
    invalidGroup.name = ''
    invalidGroup.items.splice(0, 1)
    invalidGroup.minimumSatisfiedItems = 2
    await viewModel.saveRequirementSet()
    expect(showError).toHaveBeenCalledWith('Programme requirements are incomplete', expect.stringContaining('Select the Programme'))

    Object.assign(viewModel.requirementForm, {
      programmeId: 'programme-1', applicationTypeId: 'type-1', versionCode: 'FAIL.1', effectiveFrom: '2026-08-01',
      qualificationGroups: []
    })
    failRequirementSave = true
    await viewModel.saveRequirementSet()
    expect(showError).toHaveBeenCalledWith('Programme requirements could not be saved', undefined)
    failRequirementSave = false

    failRequirementLoad = true
    ;(viewModel as unknown as { creating: boolean }).creating = false
    await viewModel.loadRequirements()
    await nextTick()
    expect(showError).not.toHaveBeenCalledWith('Programme requirements unavailable', expect.anything())
    failRequirementLoad = false

    expect(viewModel.programmeLabel('programme-1')).toBe('HCS · Computer Science')
    expect(viewModel.programmeLabel('missing')).toBe('missing')
    expect(viewModel.applicationTypeLabel('type-1')).toBe('UNDERGRAD · Undergraduate')
    expect(viewModel.applicationTypeLabel('missing')).toBe('missing')
    expect(viewModel.intakeLabel(null)).toBe('All intakes')
    expect(viewModel.intakeLabel('intake-1')).toBe('AUG-2026 · August 2026')
    expect(viewModel.intakeLabel('missing')).toBe('missing')
    expect(viewModel.formatStatus('REQUIRES_REVIEW')).toBe('Requires Review')
    expect(viewModel.formatDate(null)).toBe('No end date')

    ;(viewModel as unknown as { creating: boolean }).creating = false
    await nextTick()
    await wrapper.findAll('button').find(button => button.text() === 'New requirement set')!.trigger('click')
    await nextTick()
    await wrapper.findAll('button').find(button => button.text() === 'Add qualification group')!.trigger('click')
    await nextTick()
    await wrapper.findAll('button').find(button => button.text() === 'Add alternative route')!.trigger('click')
    await nextTick()
    await wrapper.findAll('button').find(button => button.text() === 'Remove route')!.trigger('click')
    await wrapper.findAll('button').find(button => button.text() === 'Remove group')!.trigger('click')
    await wrapper.findAll('button').find(button => button.text() === 'Cancel')!.trigger('click')

    routeQuery.programmeId = undefined
    routeQuery.applicationTypeId = [null]
    routeQuery.intakeId = undefined
    viewModel.startCreation()
    expect(viewModel.requirementForm.programmeId).toBe('programme-1')
    expect(viewModel.requirementForm.applicationTypeId).toBe('type-1')
    expect(viewModel.requirementForm.intakeId).toBe('intake-1')
    academicOverview.value = { programmes: [], intakes: [] }
    ;(viewModel as unknown as { applicationTypes: unknown[] }).applicationTypes = []
    viewModel.startCreation()
    expect(viewModel.requirementForm.programmeId).toBe('')
    expect(viewModel.requirementForm.applicationTypeId).toBe('')
    expect(viewModel.requirementForm.intakeId).toBe('')
    canManageRequirements = false
    ;(viewModel as unknown as { creating: boolean }).creating = false
    await nextTick()
  })
})

describe('Release 1 Finance application-fee usability', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('useToast', () => ({ add: vi.fn() }))
    vi.stubGlobal('useProgrammeStudyPeriod', () => ({ studyPeriodLabel: vi.fn((value: number) => `Period ${value}`) }))
    vi.stubGlobal('useEmhareConfirm', () => ({
      confirmAction: vi.fn(),
      showError: vi.fn(),
      showSuccess: vi.fn()
    }))
  })

  it('opens Finance on a dedicated application-fee register', async () => {
    const applicationFee = {
      id: 'application-fee-1', code: 'APP-UG-LOCAL', name: 'Local undergraduate application fee',
      feeContext: 'APPLICATION', scopeType: 'PROGRAMME_LEVEL', programmeLevelId: 'level-1',
      programmeLevelCode: 'UG', programmeLevelName: 'Undergraduate', applicantCategoryCode: 'LOCAL',
      transactionCurrencyCode: 'USD', effectiveFrom: '2026-08-01T00:00:00Z', effectiveUntil: null,
      status: 'ACTIVE', preparedByUserId: 'user-1', version: 1,
      lines: [{ feeRuleId: 'rule-1', lineNumber: 1, feeCatalogueId: 'catalogue-1', feeCode: 'APPLICATION-FEE',
        feeName: 'Application fee', description: 'Application fee', chargeType: 'APPLICATION',
        receivableAccountCode: 'AR-APPLICATION', revenueAccountCode: 'REV-APPLICATION-FEES',
        transactionAmount: 25, transactionCurrencyCode: 'USD', baseAmount: 25, ratingStatus: 'RATED', status: 'APPROVED' }],
      attachments: []
    }
    const academicFee = { ...applicationFee, id: 'academic-fee-1', feeContext: 'ACADEMIC' }
    const request = vi.fn(async (path: string) => {
      if (path === '/api/finance/fee-catalogues') return { catalogues: [] }
      if (path === '/api/finance/fee-structures') return { structures: [applicationFee, academicFee] }
      if (path === '/api/finance/student-discounts') return { discounts: [] }
      if (path === '/api/admissions/applications/applicant-categories') return [{ code: 'LOCAL', label: 'Local applicant' }]
      return undefined
    })
    vi.stubGlobal('useEmhareApi', () => ({ request, errorMessage: vi.fn() }))
    vi.stubGlobal('useAcademicSetup', () => ({ overview: ref({ programmeLevels: [], programmes: [], academicUnits: [] }), ensureOverview: vi.fn() }))

    const FinanceFeesPage = (await import('../../pages/operations/finance-fees.vue')).default
    const wrapper = shallowMount(FinanceFeesPage)
    await flushPromises()
    const viewModel = wrapper.vm as unknown as {
      activeRegisterTab: string
      applicationFeeStructures: Array<{ id: string }>
      structureDrawerContext: string
      structureDrawerOpen: boolean
      openStructureDrawer: (context: string) => void
    }

    expect(viewModel.activeRegisterTab).toBe('application-fees')
    expect(viewModel.applicationFeeStructures.map(item => item.id)).toEqual(['application-fee-1'])
    viewModel.openStructureDrawer('APPLICATION')
    expect(viewModel.structureDrawerContext).toBe('APPLICATION')
    expect(viewModel.structureDrawerOpen).toBe(true)
  })

  it('presets the shared drawer for one reusable application-fee definition', async () => {
    const request = vi.fn(async () => ({}))
    vi.stubGlobal('useEmhareApi', () => ({ request, errorMessage: vi.fn() }))
    const FeeStructureDrawer = (await import('../../../../packages/portal-shell/components/domain/finance/EmhareFeeStructureDrawer.vue')).default
    const wrapper = shallowMount(FeeStructureDrawer, {
      props: {
        open: false,
        initialContext: 'APPLICATION',
        catalogues: [{
          id: 'catalogue-1', code: 'APPLICATION-FEE', name: 'Application fee', description: 'Application charge',
          chargeType: 'APPLICATION', receivableAccountCode: 'AR-APPLICATION', revenueAccountCode: 'REV-APPLICATION-FEES',
          taxCode: null, baseCurrencyCode: 'USD', status: 'ACTIVE', preparedByUserId: 'user-1', version: 1, rules: []
        }],
        academicOverview: {
          academicUnitTypes: [],
          academicYears: [],
          academicPeriodTypes: [],
          academicPeriods: [],
          intakes: [],
          programmeLevels: [{ id: 'level-1', code: 'UG', name: 'Undergraduate', sortOrder: 10, status: 'ACTIVE', version: 0 }],
          programmeTypes: [],
          programmes: [],
          academicUnits: [],
          modules: []
        },
        applicantCategories: [{ code: 'LOCAL', label: 'Local applicant' }]
      }
    })
    await wrapper.setProps({ open: true })
    await nextTick()
    const viewModel = wrapper.vm as unknown as {
      form: {
        feeContext: string
        code: string
        name: string
        programmeLevelId: string
        applicantCategoryCode: string
        effectiveFrom: string
        lines: Array<{ feeCatalogueId: string, amount: number }>
      }
      createStructure: () => Promise<void>
    }

    expect(viewModel.form.feeContext).toBe('APPLICATION')
    expect(viewModel.form.lines).toHaveLength(1)
    expect(viewModel.form.lines[0]?.feeCatalogueId).toBe('catalogue-1')
    Object.assign(viewModel.form, {
      code: 'APP-UG-LOCAL', name: 'Local undergraduate application fee',
      programmeLevelId: 'level-1', applicantCategoryCode: 'LOCAL'
    })
    viewModel.form.lines[0]!.amount = 25
    await viewModel.createStructure()

    expect(request).toHaveBeenCalledWith('/api/finance/fee-structures', expect.objectContaining({
      method: 'POST',
      body: expect.objectContaining({
        feeContext: 'APPLICATION', scopeType: 'PROGRAMME_LEVEL', applicantCategoryCode: 'LOCAL',
        lines: [expect.objectContaining({ feeCatalogueId: 'catalogue-1', amount: 25 })]
      })
    }))
  })

  it('covers each application-fee readiness and Finance register state', async () => {
    const ratedLine = {
      feeRuleId: 'rated-rule', lineNumber: 1, feeCatalogueId: 'catalogue-1', feeCode: 'APPLICATION-FEE',
      feeName: 'Application fee', description: 'Application fee', chargeType: 'APPLICATION',
      receivableAccountCode: 'AR-APPLICATION', revenueAccountCode: 'REV-APPLICATION-FEES', taxCode: null,
      transactionAmount: 25, transactionCurrencyCode: 'USD', baseAmount: 25, ratingStatus: 'RATED', status: 'APPROVED'
    }
    const baseStructure = {
      code: 'APP-UG', name: 'Undergraduate application fee', feeContext: 'APPLICATION',
      scopeType: 'PROGRAMME_LEVEL', programmeLevelId: 'level-1', programmeLevelCode: 'UG',
      programmeLevelName: 'Undergraduate', transactionCurrencyCode: 'USD',
      effectiveFrom: '2026-08-01T00:00:00Z', effectiveUntil: null, preparedByUserId: 'user-1',
      version: 1, attachments: [], lines: [ratedLine]
    }
    const activeFee = { ...baseStructure, id: 'active-fee', status: 'ACTIVE', applicantCategoryCode: null }
    const draftFee = {
      ...baseStructure, id: 'draft-fee', status: 'DRAFT', applicantCategoryCode: 'LOCAL',
      lines: [{ ...ratedLine, feeRuleId: 'unrated-rule', transactionCurrencyCode: 'ZWG', baseAmount: null, ratingStatus: 'UNRATED', status: 'PENDING_RATE' }]
    }
    const retiredFee = { ...baseStructure, id: 'retired-fee', status: 'RETIRED', applicantCategoryCode: 'UNKNOWN' }
    const studentFee = { ...baseStructure, id: 'student-fee', status: 'ACTIVE', feeContext: 'ACADEMIC', applicantCategoryCode: null }
    const request = vi.fn(async (path: string) => {
      if (path === '/api/finance/fee-catalogues') return { catalogues: [
        { id: 'catalogue-1', code: 'APPLICATION-FEE', name: 'Application fee', chargeType: 'APPLICATION',
          receivableAccountCode: 'AR-APPLICATION', revenueAccountCode: 'REV-APPLICATION-FEES', baseCurrencyCode: 'USD',
          status: 'ACTIVE', preparedByUserId: 'user-1', version: 1, rules: [
            { id: 'price-1', ruleVersion: 1, transactionCurrencyCode: 'USD', transactionAmount: 25, baseCurrencyCode: 'USD',
              baseAmount: 25, ratingStatus: 'RATED', effectiveFrom: '2026-08-01T00:00:00Z', status: 'DRAFT',
              preparedByUserId: 'user-1', version: 0, scopes: [] },
            { id: 'price-2', ruleVersion: 2, transactionCurrencyCode: 'ZWG', transactionAmount: 1000, baseCurrencyCode: 'USD',
              baseAmount: null, ratingStatus: 'UNRATED', effectiveFrom: '2026-08-01T00:00:00Z', status: 'PENDING_RATE',
              preparedByUserId: 'user-1', version: 0, scopes: [] }
          ] },
        { id: 'catalogue-2', code: 'TUITION', name: 'Tuition', chargeType: 'PROGRAMME',
          receivableAccountCode: 'AR-STUDENT', revenueAccountCode: 'REV-TUITION', baseCurrencyCode: 'USD',
          status: 'DRAFT', preparedByUserId: 'user-1', version: 0, rules: [] }
      ] }
      if (path === '/api/finance/fee-structures') return { structures: [activeFee, draftFee, retiredFee, studentFee] }
      if (path === '/api/finance/student-discounts') return { discounts: [
        { id: 'discount-1', code: 'GLOBAL', name: 'Global', scopeType: 'INSTITUTION', academicUnitDepth: 0,
          programmeLevelId: 'level-1', programmeLevelCode: 'UG', programmeLevelName: 'Undergraduate', programmeStudyLevel: '1.1',
          targetType: 'ALL_FEES', discountPercentage: 10, authorityReference: 'Minute 1', effectiveFrom: '2026-08-01T00:00:00Z',
          status: 'ACTIVE', preparedByUserId: 'user-1', version: 1 },
        { id: 'discount-2', code: 'PROGRAMME', name: 'Programme', scopeType: 'PROGRAMME', academicUnitDepth: 2,
          programmeId: 'programme-1', programmeCode: 'BACC', programmeName: 'Accountancy', programmeLevelId: 'level-1',
          programmeLevelCode: 'UG', programmeLevelName: 'Undergraduate', programmeStudyLevel: '3.1', targetType: 'FEE_LINE',
          feeCode: 'TUITION', feeName: 'Tuition', discountPercentage: 15, authorityReference: 'Minute 2',
          effectiveFrom: '2026-08-01T00:00:00Z', status: 'DRAFT', preparedByUserId: 'user-1', version: 0 }
      ] }
      if (path === '/api/admissions/applications/applicant-categories') return [{ code: 'LOCAL', label: 'Local applicant' }]
      return undefined
    })
    vi.stubGlobal('useEmhareApi', () => ({ request, errorMessage: vi.fn() }))
    vi.stubGlobal('useAcademicSetup', () => ({ overview: ref({ programmeLevels: [], programmes: [], academicUnits: [] }), ensureOverview: vi.fn() }))

    const PassThrough = defineComponent({
      inheritAttrs: false,
      setup(_, { slots }) {
        return () => h('div', Object.values(slots).flatMap(slot => slot?.({ close: vi.fn() }) ?? []))
      }
    })
    const Collection = defineComponent({
      props: { items: { type: Array, default: () => [] } },
      setup(props, { slots }) { return () => h('div', slots.default?.({ items: props.items }) ?? []) }
    })
    const FinanceFeesPage = (await import('../../pages/operations/finance-fees.vue')).default
    const wrapper = mount(FinanceFeesPage, {
      global: {
        components: {
          UDashboardPanel: PassThrough, UDashboardNavbar: PassThrough, UDashboardToolbar: PassThrough,
          UCard: PassThrough, UCollapsible: PassThrough, EmhareRegisterPanel: PassThrough,
          EmharePaginatedCollection: Collection, EmhareRecordDrawer: PassThrough
        },
        stubs: { EmhareFeeStructureDrawer: true, EmhareKpiCard: true, EmhareFeedbackState: true }
      }
    })
    await flushPromises()
    const viewModel = wrapper.vm as any

    expect(viewModel.applicationFeeCounts).toEqual({ total: 3, draft: 1, active: 1, unrated: 1 })
    expect(viewModel.structureCounts).toEqual({ total: 1, draft: 0, active: 1, unrated: 0 })
    expect(viewModel.applicationFeeCategory(activeFee)).toBe('All applicant categories')
    expect(viewModel.applicationFeeCategory(draftFee)).toBe('Local applicant')
    expect(viewModel.applicationFeeCategory(retiredFee)).toBe('Unknown')
    expect(viewModel.applicationFeeReady(activeFee)).toBe(true)
    expect(viewModel.applicationFeeReady(draftFee)).toBe(false)
    expect(viewModel.applicationFeeReady({ ...activeFee, lines: draftFee.lines })).toBe(false)
    viewModel.toggleStructureDetails('student-fee', true)
    expect(viewModel.structureDetailsOpen('student-fee')).toBe(true)
    viewModel.toggleStructureDetails('student-fee', false)
    expect(viewModel.structureDetailsOpen('student-fee')).toBe(false)

    for (const tab of ['structures', 'line-items', 'discounts', 'application-fees']) {
      viewModel.activeRegisterTab = tab
      await nextTick()
    }
    expect(wrapper.exists()).toBe(true)
  })

  it('covers shared fee drawer contexts, presets, scope choices and fallback definitions', async () => {
    vi.stubGlobal('useEmhareApi', () => ({ request: vi.fn(async () => ({})), errorMessage: vi.fn() }))
    const applicationDefinition: FinanceFeeCatalogueSummary = {
      id: 'application-definition', code: 'APPLICATION-FEE', name: 'Application fee', description: 'Application charge',
      chargeType: 'APPLICATION', receivableAccountCode: 'AR-APPLICATION', revenueAccountCode: 'REV-APPLICATION-FEES',
      taxCode: null, baseCurrencyCode: 'USD', status: 'ACTIVE', preparedByUserId: 'user-1', version: 1, rules: []
    }
    const academicOverview: AcademicSetupOverview = {
      academicUnitTypes: [], academicYears: [], academicPeriodTypes: [], academicPeriods: [], intakes: [], programmeTypes: [], modules: [],
      academicUnits: [{
        id: 'unit-1', academicUnitTypeId: 'unit-type-1', academicUnitTypeCode: 'DEPARTMENT', parentId: null,
        code: 'ACC', name: 'Accounting', status: 'ACTIVE', legacyFacultyCode: null, legacyDepartmentCode: null, version: 0
      }],
      programmeLevels: [{ id: 'level-1', code: 'UG', name: 'Undergraduate', sortOrder: 10, status: 'ACTIVE', version: 0 }],
      programmes: [{
        id: 'programme-1', code: 'BACC', name: 'Accountancy', awardName: 'Bachelor of Accountancy',
        owningAcademicUnitId: 'unit-1', owningAcademicUnitName: 'Accounting', programmeTypeId: 'programme-type-1',
        programmeTypeName: 'Degree', programmeLevelId: 'level-1', programmeLevelName: 'Undergraduate',
        minimumDurationPeriods: 8, maximumDurationPeriods: 10, status: 'ACTIVE', legacyProgrammeCode: null,
        changeReason: 'Created for testing', version: 0
      }]
    }
    const PassThrough = defineComponent({
      inheritAttrs: false,
      setup(_, { slots }) { return () => h('div', Object.values(slots).flatMap(slot => slot?.({ close: vi.fn() }) ?? [])) }
    })
    const FeeStructureDrawer = (await import('../../../../packages/portal-shell/components/domain/finance/EmhareFeeStructureDrawer.vue')).default
    const wrapper = mount(FeeStructureDrawer, {
      props: {
        open: true, catalogues: [applicationDefinition], academicOverview,
        applicantCategories: [{ code: 'LOCAL', label: 'Local applicant' }]
      },
      global: {
        components: { EmhareRecordDrawer: PassThrough, UFormField: PassThrough, UDropdownMenu: PassThrough },
        stubs: { UIcon: true, UInput: true, UInputNumber: true, USelect: true, USelectMenu: true, UTextarea: true, UButton: true, UBadge: true, UAlert: true }
      }
    })
    await nextTick()
    const viewModel = wrapper.vm as any

    viewModel.reset('APPLICATION')
    expect(viewModel.form.lines[0].feeCatalogueId).toBe('application-definition')
    expect(viewModel.drawerTitle).toBe('Configure application fee')
    viewModel.form.scopeType = 'PROGRAMME_LEVEL'
    viewModel.form.programmeLevelId = 'level-1'
    expect(viewModel.selectedScopeReference.id).toBe('level-1')

    viewModel.reset('ACCOMMODATION')
    expect(viewModel.form.scopeType).toBe('GLOBAL')
    viewModel.reset('ACADEMIC')
    expect(viewModel.drawerTitle).toBe('Create fee structure')
    viewModel.form.scopeType = 'ACADEMIC_UNIT'
    expect(viewModel.scopeReferenceItems[0].value).toBe('unit-1')
    viewModel.form.scopeType = 'PROGRAMME'
    viewModel.form.scopeReferenceId = 'programme-1'
    await nextTick()
    expect(viewModel.selectedScopeReference.id).toBe('programme-1')
    expect(viewModel.form.programmeLevelId).toBe('level-1')

    viewModel.addLine('STUDENT-LEVY', 'Student levy')
    expect(viewModel.form.lines).toHaveLength(2)
    viewModel.removeLine(1)
    viewModel.removeLine(0)
    expect(viewModel.form.lines).toHaveLength(1)
    viewModel.form.lines[0].feeCatalogueId = 'missing'
    viewModel.useExistingDefinition(viewModel.form.lines[0])
    viewModel.form.lines[0].feeCatalogueId = ''
    viewModel.useExistingDefinition(viewModel.form.lines[0])

    await wrapper.setProps({ catalogues: [applicationDefinition, { ...applicationDefinition, id: 'application-definition-2', code: 'APPLICATION-FEE-2' }] })
    viewModel.reset('APPLICATION')
    expect(viewModel.form.lines[0].feeCatalogueId).toBe('')
    await wrapper.setProps({ initialContext: 'ACCOMMODATION' })
    await nextTick()
    expect(viewModel.form.feeContext).toBe('ACCOMMODATION')
  })
})
