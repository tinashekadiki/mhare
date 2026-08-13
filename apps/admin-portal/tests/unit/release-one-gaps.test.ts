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
    vi.stubGlobal('useEmhareConfirm', () => ({
      confirmAction: vi.fn(),
      showError: vi.fn(),
      showSuccess: vi.fn()
    }))
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
