<script setup lang="ts">
import Swal from 'sweetalert2'
import type { ApplicantCategoryOption } from '@emhare/portal-shell/types/admissions'
import type {
  FinanceChargeType,
  FinanceFeeCatalogueRegister,
  FinanceFeeCatalogueSummary,
  FinanceFeeRuleSummary,
  FinanceFeeScopeDimension,
  FinanceFeeContext,
  FinanceFeeStructureRegister,
  FinanceFeeStructureSummary,
  FinanceStudentDiscountRegister,
  FinanceStudentDiscountSummary,
  FinanceStudentDiscountTargetType
} from '@emhare/portal-shell/types/finance'

definePageMeta({ layout: 'dashboard' })

type ScopeDraft = { scopeDimension: FinanceFeeScopeDimension, referenceId: string, referenceCode: string, referenceName: string }

const api = useEmhareApi()
const { overview: academicOverview, ensureOverview } = useAcademicSetup()
const { studyPeriodLabel } = useProgrammeStudyPeriod()
const toast = useToast()
const { showError } = useEmhareConfirm()
const register = ref<FinanceFeeCatalogueRegister>({ catalogues: [] })
const structureRegister = ref<FinanceFeeStructureRegister>({ structures: [] })
const discountRegister = ref<FinanceStudentDiscountRegister>({ discounts: [] })
const applicantCategories = ref<ApplicantCategoryOption[]>([])
const loading = ref(false)
const saving = ref(false)
const operatingId = ref<string | null>(null)
const search = ref('')
const statusFilter = ref<'ALL' | 'DRAFT' | 'ACTIVE' | 'RETIRED'>('ALL')
const selectedCatalogueId = ref<string | null>(null)
const catalogueModalOpen = ref(false)
const ruleModalOpen = ref(false)
const structureDrawerOpen = ref(false)
const structureDrawerContext = ref<FinanceFeeContext>('APPLICATION')
const discountDrawerOpen = ref(false)
const expandedStructureIds = ref<string[]>([])
const activeRegisterTab = ref<'application-fees' | 'structures' | 'line-items' | 'discounts'>('application-fees')

const catalogueForm = reactive({
  code: '', name: '', description: '', chargeType: 'PROGRAMME' as FinanceChargeType,
  receivableAccountCode: '', revenueAccountCode: '', taxCode: ''
})
const ruleForm = reactive({
  transactionCurrencyCode: 'USD', transactionAmount: 0, effectiveFrom: '', effectiveUntil: '',
  scopes: [emptyScope()] as ScopeDraft[]
})
const discountForm = reactive({
  code: '', name: '', academicUnitId: '', programmeId: '', programmeLevelId: '', programmeStudyLevel: '',
  targetType: 'ALL_FEES' as FinanceStudentDiscountTargetType, feeCatalogueId: '',
  discountPercentage: 0, authorityReference: '',
  effectiveFrom: '', effectiveUntil: ''
})

const chargeTypeItems = ['APPLICATION', 'PROGRAMME', 'MODULE', 'ACCOMMODATION', 'DINING', 'GRADUATION', 'OTHER'].map(value => ({ label: title(value), value }))
const scopeDimensionItems = ['GLOBAL', 'ACADEMIC_PERIOD', 'APPLICATION_TYPE', 'PROGRAMME_LEVEL', 'APPLICANT_CATEGORY', 'PROGRAMME', 'MODULE', 'ACCOMMODATION_TYPE', 'DINING_PLAN', 'GRADUATION'].map(value => ({ label: title(value), value }))
const statusItems = [{ label: 'All statuses', value: 'ALL' }, { label: 'Draft', value: 'DRAFT' }, { label: 'Active', value: 'ACTIVE' }, { label: 'Retired', value: 'RETIRED' }]
const currencyItems = [{ label: 'USD · base currency', value: 'USD' }, { label: 'ZWG · effective rate required', value: 'ZWG' }]
const discountTargetItems = [{ label: 'All fee lines', value: 'ALL_FEES' }, { label: 'One fee line', value: 'FEE_LINE' }]
const counts = computed(() => ({
  total: register.value.catalogues.length,
  draft: register.value.catalogues.filter(item => item.status === 'DRAFT').length,
  active: register.value.catalogues.filter(item => item.status === 'ACTIVE').length,
  pendingRate: register.value.catalogues.flatMap(item => item.rules).filter(item => item.status === 'PENDING_RATE').length,
  awaitingApproval: register.value.catalogues.flatMap(item => item.rules).filter(item => item.status === 'DRAFT').length
}))
const applicationFeeStructures = computed(() => structureRegister.value.structures
  .filter(structure => structure.feeContext === 'APPLICATION'))
const studentFeeStructures = computed(() => structureRegister.value.structures
  .filter(structure => structure.feeContext !== 'APPLICATION'))
const applicationFeeCounts = computed(() => ({
  total: applicationFeeStructures.value.length,
  draft: applicationFeeStructures.value.filter(item => item.status === 'DRAFT').length,
  active: applicationFeeStructures.value.filter(item => item.status === 'ACTIVE').length,
  unrated: applicationFeeStructures.value.filter(item => item.lines.some(line => line.ratingStatus === 'UNRATED')).length
}))
const structureCounts = computed(() => ({
  total: studentFeeStructures.value.length,
  draft: studentFeeStructures.value.filter(item => item.status === 'DRAFT').length,
  active: studentFeeStructures.value.filter(item => item.status === 'ACTIVE').length,
  unrated: studentFeeStructures.value.filter(item => item.lines.some(line => line.ratingStatus === 'UNRATED')).length
}))
const discountCounts = computed(() => ({
  total: discountRegister.value.discounts.length,
  active: discountRegister.value.discounts.filter(item => item.status === 'ACTIVE').length,
  draft: discountRegister.value.discounts.filter(item => item.status === 'DRAFT').length,
  programme: discountRegister.value.discounts.filter(item => item.scopeType === 'PROGRAMME').length
}))
const registerTabs = computed(() => [
  { label: 'Application fees', value: 'application-fees', icon: 'i-lucide-file-check-2', badge: applicationFeeCounts.value.total },
  { label: 'Student fee structures', value: 'structures', icon: 'i-lucide-layers-3', badge: structureCounts.value.total },
  { label: 'Line-item catalogue', value: 'line-items', icon: 'i-lucide-list-tree', badge: counts.value.total },
  { label: 'Student discounts', value: 'discounts', icon: 'i-lucide-badge-percent', badge: discountCounts.value.total }
])
const visibleCatalogues = computed(() => {
  const query = search.value.trim().toLowerCase()
  return register.value.catalogues.filter(catalogue =>
    (statusFilter.value === 'ALL' || catalogue.status === statusFilter.value)
    && (!query || [catalogue.code, catalogue.name, catalogue.chargeType, catalogue.receivableAccountCode, catalogue.revenueAccountCode]
      .some(value => value.toLowerCase().includes(query)))
  )
})
const visibleLineRows = computed(() => visibleCatalogues.value.flatMap(catalogue => catalogue.rules.length
  ? catalogue.rules.map(rule => ({ rowKey: rule.id, catalogue, rule }))
  : [{ rowKey: `catalogue-${catalogue.id}`, catalogue, rule: null as FinanceFeeRuleSummary | null }]))
const academicUnitItems = computed(() => (academicOverview.value?.academicUnits ?? [])
  .filter(unit => unit.status === 'ACTIVE')
  .map(unit => ({ label: `${unit.code} · ${unit.name}`, value: unit.id })))
const programmeLevelItems = computed(() => (academicOverview.value?.programmeLevels ?? [])
  .filter(level => level.status === 'ACTIVE' && ['UG', 'PG'].includes(level.code.toUpperCase()))
  .map(level => ({ label: `${level.code} · ${level.name}`, value: level.id })))
const programmeItems = computed(() => (academicOverview.value?.programmes ?? [])
  .filter(programme => programme.status === 'ACTIVE'
    && programme.programmeLevelId === discountForm.programmeLevelId
    && (!discountForm.academicUnitId || programme.owningAcademicUnitId === discountForm.academicUnitId))
  .map(programme => ({ label: `${programme.code} · ${programme.name}`, value: programme.id })))
const feeLineItems = computed(() => register.value.catalogues.map(catalogue => ({ label: `${catalogue.code} · ${catalogue.name}`, value: catalogue.id })))
const selectedDiscountProgramme = computed(() => (academicOverview.value?.programmes ?? [])
  .find(programme => programme.id === discountForm.programmeId))
const maximumDiscountStudyPeriods = computed(() => selectedDiscountProgramme.value?.maximumDurationPeriods
  ?? Math.max(2, ...(academicOverview.value?.programmes ?? [])
    .filter(programme => programme.programmeLevelId === discountForm.programmeLevelId)
    .map(programme => programme.maximumDurationPeriods)))
const programmeStudyLevelItems = computed(() => Array.from({ length: maximumDiscountStudyPeriods.value }, (_, index) => {
  const periodNumber = index + 1
  const year = Math.ceil(periodNumber / 2)
  const semester = ((periodNumber - 1) % 2) + 1
  return { label: `${year}.${semester} · ${studyPeriodLabel(periodNumber)}`, value: `${year}.${semester}` }
}))
const discountScopeSummary = computed(() => discountForm.programmeId
  ? `Programme-specific${discountForm.academicUnitId ? ' within the selected academic unit' : ''}`
  : discountForm.academicUnitId ? 'Academic-unit discount' : 'Global discount')
const discountFormValid = computed(() => Boolean(discountForm.code.trim() && discountForm.name.trim()
  && discountForm.discountPercentage > 0 && discountForm.discountPercentage < 100
  && discountForm.authorityReference.trim() && discountForm.effectiveFrom
  && discountForm.programmeLevelId && discountForm.programmeStudyLevel
  && (discountForm.targetType === 'ALL_FEES' || discountForm.feeCatalogueId)
  && (!selectedDiscountProgramme.value
    || selectedDiscountProgramme.value.programmeLevelId === discountForm.programmeLevelId)))
const selectedCatalogue = computed(() => register.value.catalogues.find(item => item.id === selectedCatalogueId.value) ?? null)
const ruleFormValid = computed(() => ruleForm.transactionAmount > 0 && Boolean(ruleForm.effectiveFrom) && ruleForm.scopes.length > 0 && ruleForm.scopes.every(scope => scope.scopeDimension === 'GLOBAL' || Boolean(scope.referenceName.trim() && (scope.referenceId.trim() || scope.referenceCode.trim()))))

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [catalogueResult, structureResult, discountResult, applicantCategoryResult] = await Promise.all([
      api.request<FinanceFeeCatalogueRegister>('/api/finance/fee-catalogues'),
      api.request<FinanceFeeStructureRegister>('/api/finance/fee-structures'),
      api.request<FinanceStudentDiscountRegister>('/api/finance/student-discounts'),
      api.request<ApplicantCategoryOption[]>('/api/admissions/applications/applicant-categories'),
      ensureOverview()
    ])
    register.value = catalogueResult
    structureRegister.value = structureResult
    discountRegister.value = discountResult
    applicantCategories.value = applicantCategoryResult
  } catch (error) {
    await showError('Fee catalogue register could not be loaded', api.errorMessage(error))
  } finally {
    loading.value = false
  }
}

function openDiscountDrawer() {
  Object.assign(discountForm, {
    code: '', name: '', academicUnitId: '', programmeId: '', programmeLevelId: '', programmeStudyLevel: '',
    targetType: 'ALL_FEES', feeCatalogueId: '', discountPercentage: 0, authorityReference: '',
    effectiveFrom: '', effectiveUntil: ''
  })
  discountDrawerOpen.value = true
}

function openStructureDrawer(context: FinanceFeeContext) {
  structureDrawerContext.value = context
  structureDrawerOpen.value = true
}

function changeDiscountTarget() { if (discountForm.targetType === 'ALL_FEES') discountForm.feeCatalogueId = '' }

watch(() => [discountForm.programmeLevelId, discountForm.academicUnitId], () => {
  if (discountForm.programmeId && !programmeItems.value.some(item => item.value === discountForm.programmeId)) {
    discountForm.programmeId = ''
  }
  if (discountForm.programmeStudyLevel
    && !programmeStudyLevelItems.value.some(item => item.value === discountForm.programmeStudyLevel)) {
    discountForm.programmeStudyLevel = ''
  }
})

function academicUnitDepth(unitId: string) {
  const units = academicOverview.value?.academicUnits ?? []
  let depth = 1
  let unit = units.find(item => item.id === unitId)
  const visited = new Set<string>()
  while (unit?.parentId && !visited.has(unit.id)) {
    visited.add(unit.id)
    depth++
    unit = units.find(item => item.id === unit?.parentId)
  }
  return depth
}

async function createDiscount() {
  if (!discountFormValid.value) return
  const overview = academicOverview.value
  if (!overview) { await showError('Student discount could not be created', 'Academic setup has not loaded.'); return }
  const academicUnit = overview.academicUnits.find(item => item.id === discountForm.academicUnitId)
  const programme = overview.programmes.find(item => item.id === discountForm.programmeId)
  const programmeLevel = overview.programmeLevels.find(item => item.id === discountForm.programmeLevelId)
  if (!programmeLevel || !['UG', 'PG'].includes(programmeLevel.code.toUpperCase())) {
    await showError('Student discount could not be created', 'Select a valid UG or PG programme level.')
    return
  }
  saving.value = true
  try {
    await api.request('/api/finance/student-discounts', { method: 'POST', body: {
      code: discountForm.code.trim(), name: discountForm.name.trim(),
      academicUnitId: academicUnit?.id ?? null, academicUnitCode: academicUnit?.code ?? null,
      academicUnitName: academicUnit?.name ?? null,
      academicUnitDepth: academicUnit ? academicUnitDepth(academicUnit.id) : 0,
      programmeId: programme?.id ?? null, programmeCode: programme?.code ?? null, programmeName: programme?.name ?? null,
      programmeLevelId: programmeLevel.id, programmeLevelCode: programmeLevel.code,
      programmeLevelName: programmeLevel.name, programmeStudyLevel: discountForm.programmeStudyLevel,
      targetType: discountForm.targetType, feeCatalogueId: discountForm.targetType === 'FEE_LINE' ? discountForm.feeCatalogueId : null,
      discountPercentage: discountForm.discountPercentage,
      authorityReference: discountForm.authorityReference.trim(), effectiveFrom: new Date(discountForm.effectiveFrom).toISOString(),
      effectiveUntil: discountForm.effectiveUntil ? new Date(discountForm.effectiveUntil).toISOString() : null
    } })
    discountDrawerOpen.value = false
    await load()
    toast.add({ title: 'Draft student discount created', color: 'success' })
  } catch (error) { await showError('Student discount could not be created', api.errorMessage(error)) }
  finally { saving.value = false }
}

async function moveDiscount(discount: FinanceStudentDiscountSummary, action: 'activate' | 'retire') {
  const activating = action === 'activate'
  const result = await Swal.fire({ title: activating ? 'Activate student discount?' : 'Retire student discount?',
    text: activating ? 'An independent Finance operator must confirm the authority, percentage, target, scope, and effective window.' : 'The discount will no longer apply to new billing events.',
    input: 'textarea', inputLabel: activating ? 'Independent approval evidence' : 'Retirement reason', showCancelButton: true,
    confirmButtonText: activating ? 'Activate discount' : 'Retire discount', confirmButtonColor: '#006633',
    inputValidator: value => value.trim() ? undefined : 'A complete reason is required.' })
  if (!result.isConfirmed || !result.value?.trim()) return
  await perform(discount.id, () => api.request(`/api/finance/student-discounts/${discount.id}/${action}`, { method: 'POST', body: { reason: result.value.trim(), expectedVersion: discount.version } }), activating ? 'Student discount activated' : 'Student discount retired')
}

async function moveStructure(structure: FinanceFeeStructureSummary, action: 'activate' | 'retire') {
  const activating = action === 'activate'
  const result = await Swal.fire({
    title: activating ? 'Activate complete fee structure?' : 'Retire fee structure?',
    text: activating
      ? 'Verify the scope, every line amount, posting account, effective window, and total. Activation makes this complete schedule eligible for billing and requires an independent Finance operator.'
      : 'The structure will stop matching new billing events. Existing invoices remain unchanged.',
    input: 'textarea',
    inputLabel: activating ? 'Independent schedule approval evidence' : 'Retirement reason',
    inputPlaceholder: 'Record the approved fee schedule reference and checks performed.',
    icon: activating ? 'question' : 'warning',
    showCancelButton: true,
    confirmButtonText: activating ? 'Activate structure' : 'Retire structure',
    confirmButtonColor: '#006633',
    inputValidator: value => value.trim() ? undefined : 'A complete reason is required.'
  })
  if (!result.isConfirmed || !result.value?.trim()) return
  await perform(structure.id, () => api.request(`/api/finance/fee-structures/${structure.id}/${action}`, {
    method: 'POST',
    body: { reason: result.value.trim(), expectedVersion: structure.version }
  }), activating ? 'Fee structure activated' : 'Fee structure retired')
}

function openCatalogueModal() {
  Object.assign(catalogueForm, { code: '', name: '', description: '', chargeType: 'PROGRAMME', receivableAccountCode: '', revenueAccountCode: '', taxCode: '' })
  catalogueModalOpen.value = true
}

async function createCatalogue() {
  if (!catalogueForm.code.trim() || !catalogueForm.name.trim() || !catalogueForm.receivableAccountCode.trim() || !catalogueForm.revenueAccountCode.trim()) return
  saving.value = true
  try {
    await api.request('/api/finance/fee-catalogues', {
      method: 'POST',
      body: { ...catalogueForm, taxCode: catalogueForm.taxCode.trim() || null, description: catalogueForm.description.trim() || null }
    })
    catalogueModalOpen.value = false
    await load()
    toast.add({ title: 'Draft fee definition created', color: 'success' })
  } catch (error) {
    await showError('Fee definition could not be created', api.errorMessage(error))
  } finally {
    saving.value = false
  }
}

async function moveCatalogue(catalogue: FinanceFeeCatalogueSummary, action: 'activate' | 'retire') {
  const activating = action === 'activate'
  const result = await Swal.fire({
    title: activating ? 'Activate fee definition?' : 'Retire fee definition?',
    text: activating
      ? 'An independent Finance operator must confirm the charge classification and posting accounts before pricing can be approved.'
      : 'Retirement prevents new pricing from being approved; historical billings retain this definition.',
    input: 'textarea', inputLabel: activating ? 'Activation control evidence' : 'Retirement reason',
    inputPlaceholder: 'Record the policy, account, and approval evidence.', icon: activating ? 'question' : 'warning',
    showCancelButton: true, confirmButtonText: activating ? 'Activate definition' : 'Retire definition', confirmButtonColor: '#006633',
    inputValidator: value => value.trim() ? undefined : 'A complete reason is required.'
  })
  if (!result.isConfirmed || !result.value?.trim()) return
  await perform(catalogue.id, () => api.request(`/api/finance/fee-catalogues/${catalogue.id}/${action}`, { method: 'POST', body: { reason: result.value.trim(), expectedVersion: catalogue.version } }), activating ? 'Fee definition activated' : 'Fee definition retired')
}

function openRuleModal(catalogue: FinanceFeeCatalogueSummary) {
  selectedCatalogueId.value = catalogue.id
  Object.assign(ruleForm, { transactionCurrencyCode: 'USD', transactionAmount: 0, effectiveFrom: '', effectiveUntil: '', scopes: [emptyScope()] })
  ruleModalOpen.value = true
}

function addScope() {
  if (ruleForm.scopes.some(scope => scope.scopeDimension === 'GLOBAL')) return
  ruleForm.scopes.push(emptyScope('ACADEMIC_PERIOD'))
}

function removeScope(index: number) {
  if (ruleForm.scopes.length > 1) ruleForm.scopes.splice(index, 1)
}

function changeScopeDimension(scope: ScopeDraft) {
  scope.referenceId = ''
  scope.referenceCode = ''
  scope.referenceName = ''
  if (scope.scopeDimension === 'GLOBAL') ruleForm.scopes.splice(0, ruleForm.scopes.length, scope)
}

async function createRule() {
  if (!selectedCatalogue.value || !ruleFormValid.value) return
  saving.value = true
  try {
    await api.request(`/api/finance/fee-catalogues/${selectedCatalogue.value.id}/rules`, {
      method: 'POST',
      body: {
        transactionCurrencyCode: ruleForm.transactionCurrencyCode,
        transactionAmount: ruleForm.transactionAmount,
        effectiveFrom: new Date(ruleForm.effectiveFrom).toISOString(),
        effectiveUntil: ruleForm.effectiveUntil ? new Date(ruleForm.effectiveUntil).toISOString() : null,
        scopes: ruleForm.scopes.map(scope => ({
          scopeDimension: scope.scopeDimension,
          referenceId: scope.referenceId.trim() || null,
          referenceCode: scope.referenceCode.trim() || null,
          referenceName: scope.referenceName.trim() || null
        }))
      }
    })
    ruleModalOpen.value = false
    await load()
    toast.add({ title: ruleForm.transactionCurrencyCode === 'USD' ? 'Draft effective price created' : 'Foreign-currency price captured for rating', color: 'success' })
  } catch (error) {
    await showError('Effective price could not be created', api.errorMessage(error))
  } finally {
    saving.value = false
  }
}

async function rateRule(rule: FinanceFeeRuleSummary) {
  await perform(rule.id, () => api.request(`/api/finance/fee-catalogues/rules/${rule.id}/rate?expectedVersion=${rule.version}`, { method: 'POST' }), 'Effective exchange rate applied')
}

async function moveRule(rule: FinanceFeeRuleSummary, action: 'approve' | 'retire') {
  const approving = action === 'approve'
  const result = await Swal.fire({
    title: approving ? 'Approve effective price?' : 'Retire effective price?',
    text: approving
      ? 'Approval makes this rated amount available to matching billing events. Overlapping approved prices are rejected.'
      : 'Retirement stops future billing events from selecting this price; posted transactions are unchanged.',
    input: 'textarea', inputLabel: approving ? 'Independent price approval evidence' : 'Retirement reason',
    inputPlaceholder: 'Record the authorised fee schedule and verification performed.', icon: approving ? 'question' : 'warning',
    showCancelButton: true, confirmButtonText: approving ? 'Approve price' : 'Retire price', confirmButtonColor: '#006633',
    inputValidator: value => value.trim() ? undefined : 'A complete reason is required.'
  })
  if (!result.isConfirmed || !result.value?.trim()) return
  await perform(rule.id, () => api.request(`/api/finance/fee-catalogues/rules/${rule.id}/${action}`, { method: 'POST', body: { reason: result.value.trim(), expectedVersion: rule.version } }), approving ? 'Effective price approved' : 'Effective price retired')
}

async function perform(id: string, action: () => Promise<unknown>, successTitle: string) {
  operatingId.value = id
  try {
    await action()
    await load()
    toast.add({ title: successTitle, color: 'success' })
  } catch (error) {
    await showError('Finance operation could not be completed', api.errorMessage(error))
  } finally {
    operatingId.value = null
  }
}

function emptyScope(scopeDimension: FinanceFeeScopeDimension = 'GLOBAL'): ScopeDraft {
  return { scopeDimension, referenceId: '', referenceCode: '', referenceName: '' }
}
function title(value: string) { return value.toLowerCase().split('_').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(' ') }
function ruleScopeLabel(rule: FinanceFeeRuleSummary) { return rule.scopes.map(scope => scope.scopeDimension === 'GLOBAL' ? 'All eligible records' : `${title(scope.scopeDimension)} · ${scope.referenceCode || scope.referenceName || ''}`).join(' · ') }
function money(value: number | null | undefined, currency = 'USD') { return value == null ? 'Unrated' : new Intl.NumberFormat('en-ZW', { style: 'currency', currency }).format(value) }
function date(value: string | null | undefined) { return value ? new Intl.DateTimeFormat('en-ZW', { dateStyle: 'medium' }).format(new Date(value)) : 'Open ended' }
function catalogueColour(status: FinanceFeeCatalogueSummary['status']) { return status === 'ACTIVE' ? 'success' : status === 'DRAFT' ? 'warning' : 'neutral' }
function ruleColour(status: FinanceFeeRuleSummary['status']) { return status === 'APPROVED' ? 'success' : status === 'PENDING_RATE' ? 'error' : status === 'DRAFT' ? 'warning' : 'neutral' }
function structureColour(status: FinanceFeeStructureSummary['status']) { return status === 'ACTIVE' ? 'success' : status === 'DRAFT' ? 'warning' : 'neutral' }
function structureScope(structure: FinanceFeeStructureSummary) {
  if (structure.feeContext === 'ACCOMMODATION') return 'Global accommodation rate'
  if (structure.scopeType === 'INSTITUTION') return 'Institution default'
  const scope = `${title(structure.scopeType)} · ${structure.scopeReferenceCode ? `${structure.scopeReferenceCode} · ` : ''}${structure.scopeReferenceName ?? ''}`
  return scope
}
function structureTotal(structure: FinanceFeeStructureSummary) {
  return structure.lines.reduce((total, line) => total + line.transactionAmount, 0)
}
function lineItemsLabel(structure: FinanceFeeStructureSummary) {
  return `${structureDetailsOpen(structure.id) ? 'Hide' : 'Show'} line items (${structure.lines.length})`
}
function structureDetailsOpen(structureId: string) {
  return expandedStructureIds.value.includes(structureId)
}
function structureHasUnratedLines(structure: FinanceFeeStructureSummary) {
  return structure.lines.some(line => line.ratingStatus === 'UNRATED')
}
function applicationFeeCategory(structure: FinanceFeeStructureSummary) {
  if (!structure.applicantCategoryCode) return 'All applicant categories'
  return applicantCategories.value.find(category => category.code === structure.applicantCategoryCode)?.label
    ?? title(structure.applicantCategoryCode)
}
function applicationFeeReady(structure: FinanceFeeStructureSummary) {
  return structure.status === 'ACTIVE' && !structureHasUnratedLines(structure)
}
function toggleStructureDetails(structureId: string, open: boolean) {
  expandedStructureIds.value = open
    ? [...new Set([...expandedStructureIds.value, structureId])]
    : expandedStructureIds.value.filter(id => id !== structureId)
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Finance fee configuration">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right>
          <UButton icon="i-lucide-refresh-cw" color="neutral" variant="outline" aria-label="Refresh" :loading="loading" @click="load"><span class="hidden sm:inline">Refresh</span></UButton>
          <UButton v-if="activeRegisterTab === 'application-fees'" icon="i-lucide-file-plus-2" aria-label="Configure application fee" @click="openStructureDrawer('APPLICATION')"><span class="hidden sm:inline">Configure application fee</span></UButton>
          <UButton v-else-if="activeRegisterTab === 'line-items'" icon="i-lucide-list-plus" aria-label="New line definition" @click="openCatalogueModal"><span class="hidden sm:inline">New line definition</span></UButton>
          <UButton v-else-if="activeRegisterTab === 'discounts'" icon="i-lucide-badge-percent" aria-label="New student discount" @click="openDiscountDrawer"><span class="hidden sm:inline">New student discount</span></UButton>
          <UButton v-else icon="i-lucide-plus" aria-label="New student fee structure" @click="openStructureDrawer('ACADEMIC')"><span class="hidden sm:inline">New student fee structure</span></UButton>
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <span class="text-sm text-muted">Application charges, student fee schedules, posting definitions, and authorised discounts</span>
        </template>
      </UDashboardToolbar>
    </template>
    <template #body>
      <div class="space-y-5 p-4 sm:p-6">
        <section v-if="activeRegisterTab === 'application-fees'" class="grid grid-cols-2 gap-3 xl:grid-cols-4">
          <EmhareKpiCard label="Application fees" :value="applicationFeeCounts.total" icon="i-lucide-file-check-2" tone="primary" />
          <EmhareKpiCard label="Ready for applications" :value="applicationFeeCounts.active" icon="i-lucide-circle-check" tone="success" />
          <EmhareKpiCard label="Awaiting approval" :value="applicationFeeCounts.draft" icon="i-lucide-stamp" tone="warning" />
          <EmhareKpiCard label="Awaiting exchange rate" :value="applicationFeeCounts.unrated" icon="i-lucide-refresh-cw" tone="error" />
        </section>

        <section v-else-if="activeRegisterTab === 'structures'" class="grid grid-cols-2 gap-3 xl:grid-cols-4">
          <EmhareKpiCard label="Fee structures" :value="structureCounts.total" icon="i-lucide-layers-3" tone="primary" />
          <EmhareKpiCard label="Active" :value="structureCounts.active" icon="i-lucide-circle-check" tone="success" />
          <EmhareKpiCard label="Awaiting approval" :value="structureCounts.draft" icon="i-lucide-stamp" tone="warning" />
          <EmhareKpiCard label="Awaiting exchange rate" :value="structureCounts.unrated" icon="i-lucide-refresh-cw" tone="error" />
        </section>

        <section v-else-if="activeRegisterTab === 'line-items'" class="grid grid-cols-2 gap-3 xl:grid-cols-4">
          <EmhareKpiCard label="Line definitions" :value="counts.total" icon="i-lucide-list-tree" tone="primary" />
          <EmhareKpiCard label="Active" :value="counts.active" icon="i-lucide-circle-check" tone="success" />
          <EmhareKpiCard label="Awaiting approval" :value="counts.awaitingApproval" icon="i-lucide-stamp" tone="warning" />
          <EmhareKpiCard label="Awaiting exchange rate" :value="counts.pendingRate" icon="i-lucide-refresh-cw" tone="error" />
        </section>

        <section v-else class="grid grid-cols-2 gap-3 xl:grid-cols-4">
          <EmhareKpiCard label="Student discounts" :value="discountCounts.total" icon="i-lucide-badge-percent" tone="primary" />
          <EmhareKpiCard label="Active" :value="discountCounts.active" icon="i-lucide-circle-check" tone="success" />
          <EmhareKpiCard label="Awaiting approval" :value="discountCounts.draft" icon="i-lucide-stamp" tone="warning" />
          <EmhareKpiCard label="Programme-specific" :value="discountCounts.programme" icon="i-lucide-graduation-cap" tone="primary" />
        </section>

        <UTabs v-model="activeRegisterTab" :items="registerTabs" color="primary" variant="pill" :content="false" />

        <div v-if="activeRegisterTab === 'application-fees'" class="space-y-5">
          <UAlert color="primary" variant="soft" icon="i-lucide-shield-check" title="Application payment gate" description="Finance owns the amount, currency, effective dates, reconciliation, and receipt. Admissions can proceed only after the configured fee is confirmed or an authorised waiver is recorded." />
          <EmhareRegisterPanel
            title="Application fee register"
            description="One clear charge per programme level and applicant category. Active, rated fees can be linked to Admissions application types."
            :record-count="applicationFeeStructures.length"
          >
            <EmharePaginatedCollection :items="applicationFeeStructures" v-slot="{ items: paginatedApplicationFees }">
              <div class="grid gap-4 lg:grid-cols-2">
                <UCard v-for="structure in paginatedApplicationFees" :key="structure.id" :ui="{ body: 'p-4 sm:p-5' }">
                  <div class="flex flex-wrap items-start justify-between gap-3">
                    <div class="min-w-0">
                      <div class="flex flex-wrap items-center gap-2">
                        <span class="font-mono text-xs font-semibold text-primary">{{ structure.code }}</span>
                        <UBadge :label="structure.status" :color="structureColour(structure.status)" variant="subtle" />
                        <UBadge v-if="applicationFeeReady(structure)" label="Ready" color="success" variant="outline" />
                      </div>
                      <h2 class="mt-2 text-lg font-semibold text-highlighted">{{ structure.name }}</h2>
                      <p class="mt-1 text-sm text-muted">{{ structure.programmeLevelCode }} · {{ structure.programmeLevelName }} · {{ applicationFeeCategory(structure) }}</p>
                    </div>
                    <p class="text-2xl font-semibold tabular-nums text-primary">{{ money(structureTotal(structure), structure.transactionCurrencyCode) }}</p>
                  </div>
                  <div class="mt-4 grid gap-3 border-y border-muted py-4 text-sm sm:grid-cols-2">
                    <div><p class="text-xs uppercase text-muted">Effective from</p><p class="mt-1 font-medium">{{ date(structure.effectiveFrom) }}</p></div>
                    <div><p class="text-xs uppercase text-muted">Effective until</p><p class="mt-1 font-medium">{{ date(structure.effectiveUntil) }}</p></div>
                    <div><p class="text-xs uppercase text-muted">USD base status</p><p class="mt-1 font-medium" :class="structureHasUnratedLines(structure) ? 'text-error' : 'text-success'">{{ structureHasUnratedLines(structure) ? 'Exchange rate required' : 'Rated and available' }}</p></div>
                    <div><p class="text-xs uppercase text-muted">Posting</p><p class="mt-1 font-medium">{{ structure.lines[0]?.receivableAccountCode }} → {{ structure.lines[0]?.revenueAccountCode }}</p></div>
                  </div>
                  <div class="mt-4 flex flex-wrap justify-end gap-2">
                    <UButton v-if="structure.status === 'DRAFT'" label="Verify and activate" icon="i-lucide-stamp" :loading="operatingId === structure.id" :disabled="structureHasUnratedLines(structure)" @click="moveStructure(structure, 'activate')" />
                    <UButton v-else-if="structure.status === 'ACTIVE'" label="Link to application types" icon="i-lucide-arrow-right" color="neutral" variant="outline" to="/operations/application-types" />
                    <UButton v-if="structure.status === 'ACTIVE'" label="Retire" color="neutral" variant="ghost" :loading="operatingId === structure.id" @click="moveStructure(structure, 'retire')" />
                  </div>
                </UCard>
                <EmhareFeedbackState v-if="!loading && !applicationFeeStructures.length" state="empty" title="No application fees configured" description="Configure the first fee by programme level. Add category-specific fees only when the amount differs." />
              </div>
            </EmharePaginatedCollection>
          </EmhareRegisterPanel>
        </div>

        <div v-else-if="activeRegisterTab === 'structures'" class="space-y-5">
          <UAlert color="primary" variant="soft" icon="i-lucide-git-branch" title="One complete schedule wins" description="Schedules first match programme level. Within that level, programme fees replace academic-unit fees; the nearest academic unit replaces its parent; institution fees are the fallback. Charges are never mixed across levels or hierarchy scopes." />

          <EmhareRegisterPanel
            title="Fee structure register"
            description="Complete fee schedules, their precedence scope, effective window, line total, and maker-checker state."
            :record-count="studentFeeStructures.length"
          >
            <EmharePaginatedCollection :items="studentFeeStructures" v-slot="{ items: paginatedStructures }">
            <div class="space-y-4">
            <UCard v-for="structure in paginatedStructures" :key="structure.id" :ui="{ body: 'p-0' }">
              <div class="grid gap-4 p-4 sm:p-5 xl:grid-cols-[1fr_auto]">
                <div class="min-w-0 space-y-3">
                  <div class="flex flex-wrap items-center gap-2">
                    <span class="font-mono text-xs font-semibold text-primary">{{ structure.code }}</span>
                    <UBadge :label="title(structure.feeContext)" color="neutral" variant="outline" />
                    <UBadge :label="`${structure.programmeLevelCode} · ${structure.programmeLevelName}`" color="primary" variant="subtle" />
                    <UBadge :label="structure.status" :color="structureColour(structure.status)" variant="subtle" />
                  </div>
                  <div>
                    <h2 class="text-lg font-semibold text-highlighted">{{ structure.name }}</h2>
                    <p class="mt-1 text-sm font-medium text-primary">{{ structureScope(structure) }}</p>
                  </div>
                  <div class="grid gap-3 text-sm sm:grid-cols-3">
                    <div>
                      <p class="text-xs uppercase text-muted">Total</p>
                      <p class="font-semibold text-highlighted">{{ money(structureTotal(structure), structure.transactionCurrencyCode) }}</p>
                    </div>
                    <div>
                      <p class="text-xs uppercase text-muted">Effective window</p>
                      <p class="font-medium">{{ date(structure.effectiveFrom) }} to {{ date(structure.effectiveUntil) }}</p>
                    </div>
                    <div><p class="text-xs uppercase text-muted">Lines</p><p class="font-medium">{{ structure.lines.length }} governed charges</p></div>
                  </div>
                </div>
                <div class="flex flex-wrap items-start justify-end gap-2">
                  <UButton v-if="structure.status === 'DRAFT'" label="Verify and activate" icon="i-lucide-stamp" size="sm" :loading="operatingId === structure.id" :disabled="structureHasUnratedLines(structure)" @click="moveStructure(structure, 'activate')" />
                  <UButton v-if="structure.status === 'ACTIVE'" label="Retire" color="neutral" variant="outline" size="sm" :loading="operatingId === structure.id" @click="moveStructure(structure, 'retire')" />
                </div>
              </div>
              <UCollapsible
                :open="structureDetailsOpen(structure.id)"
                :ui="{ content: 'border-t border-muted' }"
                @update:open="toggleStructureDetails(structure.id, $event)"
              >
                <div class="flex items-center justify-between border-t border-muted px-4 py-3 sm:px-5">
                  <UButton
                    :label="lineItemsLabel(structure)"
                    :icon="structureDetailsOpen(structure.id) ? 'i-lucide-chevron-up' : 'i-lucide-chevron-down'"
                    color="neutral"
                    variant="ghost"
                    size="sm"
                  />
                  <p class="hidden text-xs text-muted sm:block">
                    {{ structure.lines.length }} lines · {{ money(structureTotal(structure), structure.transactionCurrencyCode) }}
                  </p>
                </div>
                <template #content>
                  <div class="space-y-4 p-4 sm:p-5">
                    <div class="overflow-x-auto rounded-md border border-muted">
                      <table class="w-full min-w-[760px] text-left text-sm">
                        <thead class="bg-muted/40 text-xs uppercase text-muted">
                          <tr><th class="px-4 py-3">Line item</th><th class="px-4 py-3">Invoice description</th><th class="px-4 py-3">Posting accounts</th><th class="px-4 py-3 text-right">Amount</th></tr>
                        </thead>
                        <tbody>
                          <tr v-for="line in structure.lines" :key="line.feeRuleId" class="border-t border-muted first:border-t-0">
                            <td class="px-4 py-3"><p class="font-medium">{{ line.lineNumber }}. {{ line.feeName }}</p><p class="font-mono text-xs text-muted">{{ line.feeCode }}</p></td>
                            <td class="px-4 py-3">{{ line.description }}</td>
                            <td class="px-4 py-3"><p>{{ line.receivableAccountCode }}</p><p class="text-xs text-muted">Revenue {{ line.revenueAccountCode }}<span v-if="line.taxCode"> · {{ line.taxCode }}</span></p></td>
                            <td class="px-4 py-3 text-right"><p class="font-semibold">{{ money(line.transactionAmount, line.transactionCurrencyCode) }}</p><p v-if="line.ratingStatus === 'UNRATED'" class="text-xs font-medium text-error">Rate required</p></td>
                          </tr>
                        </tbody>
                        <tfoot><tr class="border-t border-muted bg-primary/5"><td colspan="3" class="px-4 py-3 font-semibold">Complete schedule total</td><td class="px-4 py-3 text-right font-semibold text-primary">{{ money(structureTotal(structure), structure.transactionCurrencyCode) }}</td></tr></tfoot>
                      </table>
                    </div>
                  </div>
                </template>
              </UCollapsible>
            </UCard>
              <EmhareFeedbackState v-if="!loading && !studentFeeStructures.length" state="empty" title="No student fee structures configured" description="Create the institution fallback or a programme-specific structure, then add Tuition, Student Levy, Sport Fees, and other invoice lines." />
            </div>
            </EmharePaginatedCollection>
          </EmhareRegisterPanel>
        </div>

        <div v-else-if="activeRegisterTab === 'line-items'" class="space-y-5">
          <EmhareRegisterPanel
            title="Line-item catalogue"
            description="Reusable posting definitions and advanced prices for programme, Module, dining, graduation, and other event billing."
            :record-count="visibleLineRows.length"
          >
            <div class="space-y-4">
              <UCard :ui="{ body: 'p-4' }"><div class="grid gap-3 sm:grid-cols-[1fr_220px]"><UInput v-model="search" icon="i-lucide-search" placeholder="Search fee, charge type, or posting account" /><USelect v-model="statusFilter" :items="statusItems" /></div></UCard>
              <UCard :ui="{ body: 'p-0' }">
                <EmharePaginatedCollection :items="visibleLineRows" v-slot="{ items: paginatedRows }">
                  <div class="overflow-x-auto">
                    <table class="w-full min-w-[1320px] text-left text-sm">
                      <thead class="bg-muted/40 text-xs uppercase text-muted"><tr><th class="px-3 py-2.5">Definition</th><th class="px-3 py-2.5">Posting</th><th class="px-3 py-2.5">Version and scope</th><th class="px-3 py-2.5">Transaction</th><th class="px-3 py-2.5">USD base</th><th class="px-3 py-2.5">Effective window</th><th class="px-3 py-2.5">State</th><th class="px-3 py-2.5 text-right">Actions</th></tr></thead>
                      <tbody>
                        <tr v-for="row in paginatedRows" :key="row.rowKey" class="border-t border-muted align-top">
                          <td class="px-3 py-2.5"><div class="flex items-center gap-2"><span class="font-mono text-xs font-semibold text-primary">{{ row.catalogue.code }}</span><UBadge :label="title(row.catalogue.chargeType)" color="neutral" variant="outline" /></div><p class="mt-1 font-medium text-highlighted">{{ row.catalogue.name }}</p></td>
                          <td class="px-3 py-2.5"><p>{{ row.catalogue.receivableAccountCode }}</p><p class="text-xs text-muted">Revenue {{ row.catalogue.revenueAccountCode }}<span v-if="row.catalogue.taxCode"> · {{ row.catalogue.taxCode }}</span></p></td>
                          <td class="px-3 py-2.5"><template v-if="row.rule"><p class="font-medium">Price version {{ row.rule.ruleVersion }}</p><p class="mt-1 max-w-[330px] text-xs text-muted">{{ ruleScopeLabel(row.rule) }}</p></template><span v-else class="text-muted">No effective price</span></td>
                          <td class="px-3 py-2.5 font-medium">{{ row.rule ? money(row.rule.transactionAmount, row.rule.transactionCurrencyCode) : '—' }}</td>
                          <td class="px-3 py-2.5"><template v-if="row.rule"><p :class="row.rule.ratingStatus === 'UNRATED' ? 'font-medium text-error' : 'font-medium text-success'">{{ money(row.rule.baseAmount, row.rule.baseCurrencyCode) }}</p><p class="text-xs text-muted">{{ row.rule.ratingStatus === 'RATED' ? 'Rated' : 'Rate required' }}</p></template><span v-else>—</span></td>
                          <td class="px-3 py-2.5"><template v-if="row.rule"><p>{{ date(row.rule.effectiveFrom) }}</p><p class="text-xs text-muted">to {{ date(row.rule.effectiveUntil) }}</p></template><span v-else>—</span></td>
                          <td class="px-3 py-2.5"><div class="flex flex-col items-start gap-1"><UBadge :label="row.catalogue.status" :color="catalogueColour(row.catalogue.status)" variant="subtle" /><UBadge v-if="row.rule" :label="row.rule.status.replace('_', ' ')" :color="ruleColour(row.rule.status)" variant="subtle" /></div></td>
                          <td class="px-3 py-2.5"><div class="flex justify-end gap-1"><UButton v-if="row.catalogue.status === 'DRAFT'" label="Activate" size="xs" :loading="operatingId === row.catalogue.id" @click="moveCatalogue(row.catalogue, 'activate')" /><UButton v-if="row.catalogue.status === 'ACTIVE'" label="Add price" size="xs" color="neutral" variant="outline" @click="openRuleModal(row.catalogue)" /><UButton v-if="row.rule?.status === 'PENDING_RATE'" label="Apply rate" size="xs" :loading="operatingId === row.rule.id" @click="rateRule(row.rule)" /><UButton v-if="row.rule?.status === 'DRAFT'" label="Approve" size="xs" :loading="operatingId === row.rule.id" @click="moveRule(row.rule, 'approve')" /><UButton v-if="row.rule?.status === 'APPROVED'" label="Retire price" size="xs" color="neutral" variant="outline" :loading="operatingId === row.rule.id" @click="moveRule(row.rule, 'retire')" /></div></td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </EmharePaginatedCollection>
              </UCard>
              <UAlert v-if="!loading && !visibleCatalogues.length" color="neutral" variant="soft" title="No fee definitions match" description="Create a governed fee definition or adjust the current search and status filters." />
            </div>
          </EmhareRegisterPanel>
        </div>

        <div v-else class="space-y-5">
          <UAlert color="primary" variant="soft" icon="i-lucide-git-compare-arrows" title="One matching discount wins" description="A programme discount overrides an academic-unit discount, which overrides a global discount. Programme level and study level must always match; any selected academic unit and programme must also match the student's registration." />
          <EmhareRegisterPanel title="Student discount register" description="Standalone percentage reductions for attachment and other authorised student arrangements." :record-count="discountRegister.discounts.length">
            <UCard :ui="{ body: 'p-0' }">
              <EmharePaginatedCollection :items="discountRegister.discounts" v-slot="{ items: paginatedDiscounts }">
                <div class="overflow-x-auto"><table class="w-full min-w-[1180px] text-left text-sm">
                  <thead class="bg-muted/40 text-xs uppercase text-muted"><tr><th class="px-3 py-2.5">Discount</th><th class="px-3 py-2.5">Priority scope</th><th class="px-3 py-2.5">Fee target</th><th class="px-3 py-2.5">Eligibility</th><th class="px-3 py-2.5 text-right">Reduction</th><th class="px-3 py-2.5">Effective window</th><th class="px-3 py-2.5">State</th><th class="px-3 py-2.5 text-right">Actions</th></tr></thead>
                  <tbody><tr v-for="discount in paginatedDiscounts" :key="discount.id" class="border-t border-muted align-top">
                    <td class="px-3 py-2.5"><p class="font-mono text-xs font-semibold text-primary">{{ discount.code }}</p><p class="mt-1 font-medium text-highlighted">{{ discount.name }}</p><p class="mt-1 max-w-xs text-xs text-muted">{{ discount.authorityReference }}</p></td>
                    <td class="px-3 py-2.5"><p class="font-medium">{{ discount.programmeId ? 'Programme' : discount.academicUnitId ? 'Academic unit' : 'Global' }}</p><p v-if="discount.programmeId" class="text-xs text-muted">{{ discount.programmeCode }} · {{ discount.programmeName }}</p><p v-if="discount.academicUnitId" class="text-xs text-muted">{{ discount.academicUnitCode }} · {{ discount.academicUnitName }}</p><p v-if="!discount.programmeId && !discount.academicUnitId" class="text-xs text-muted">All matching students</p></td>
                    <td class="px-3 py-2.5"><p class="font-medium">{{ discount.targetType === 'ALL_FEES' ? 'All fee lines' : discount.feeCode }}</p><p v-if="discount.feeName" class="text-xs text-muted">{{ discount.feeName }}</p></td>
                    <td class="px-3 py-2.5"><p class="font-medium">{{ discount.programmeLevelCode }} · {{ discount.programmeLevelName }}</p><p class="text-xs text-muted">Study level {{ discount.programmeStudyLevel }}</p></td>
                    <td class="px-3 py-2.5 text-right text-lg font-semibold text-primary">{{ discount.discountPercentage }}%</td>
                    <td class="px-3 py-2.5"><p>{{ date(discount.effectiveFrom) }}</p><p class="text-xs text-muted">to {{ date(discount.effectiveUntil) }}</p></td>
                    <td class="px-3 py-2.5"><UBadge :label="discount.status" :color="structureColour(discount.status)" variant="subtle" /></td>
                    <td class="px-3 py-2.5"><div class="flex justify-end gap-1"><UButton v-if="discount.status === 'DRAFT'" label="Activate" size="xs" :loading="operatingId === discount.id" @click="moveDiscount(discount, 'activate')" /><UButton v-if="discount.status === 'ACTIVE'" label="Retire" size="xs" color="neutral" variant="outline" :loading="operatingId === discount.id" @click="moveDiscount(discount, 'retire')" /></div></td>
                  </tr></tbody>
                </table></div>
              </EmharePaginatedCollection>
            </UCard>
            <EmhareFeedbackState v-if="!loading && !discountRegister.discounts.length" state="empty" title="No student discounts configured" description="Create an institution, academic-unit, or programme discount and record the approving authority." />
          </EmhareRegisterPanel>
        </div>
      </div>
    </template>
  </UDashboardPanel>

  <EmhareFeeStructureDrawer v-model:open="structureDrawerOpen" :initial-context="structureDrawerContext" :catalogues="register.catalogues" :academic-overview="academicOverview" :applicant-categories="applicantCategories" @created="load" />

  <EmhareRecordDrawer v-model:open="discountDrawerOpen" presentation="page" width="wide" title="Create student discount" description="Record one governed percentage reduction. Scope specificity determines priority and only one discount is applied to a fee line.">
    <template #body>
      <div class="space-y-5">
        <section class="rounded-lg border border-muted p-4">
          <h3 class="font-semibold text-highlighted">Discount identity</h3>
          <div class="mt-4 grid gap-4 sm:grid-cols-2"><UFormField label="Discount code" required><UInput v-model="discountForm.code" class="w-full" placeholder="ATTACHMENT-2026" /></UFormField><UFormField label="Discount name" required><UInput v-model="discountForm.name" class="w-full" placeholder="Student attachment discount" /></UFormField></div>
          <UFormField label="Authority reference" required class="mt-4"><UTextarea v-model="discountForm.authorityReference" class="w-full" :rows="2" placeholder="Committee minute, policy, circular, or approval reference" /></UFormField>
        </section>
        <section class="rounded-lg border border-muted p-4">
          <h3 class="font-semibold text-highlighted">Student applicability</h3>
          <p class="mt-1 text-xs text-muted">Leave both academic unit and programme blank for a global discount. Programme level and study level are always required.</p>
          <div class="mt-4 grid gap-4 sm:grid-cols-2">
            <UFormField label="Programme level" description="UG or PG" required><USelectMenu v-model="discountForm.programmeLevelId" :items="programmeLevelItems" value-key="value" searchable class="w-full" placeholder="Select UG or PG" aria-label="Programme level" /></UFormField>
            <UFormField label="Programme study level" description="The student's current year and semester" required><USelect v-model="discountForm.programmeStudyLevel" :items="programmeStudyLevelItems" class="w-full" placeholder="Select, for example 3.1" aria-label="Programme study level" :disabled="!discountForm.programmeLevelId" /></UFormField>
            <UFormField label="Academic unit" description="Optional"><USelectMenu v-model="discountForm.academicUnitId" :items="academicUnitItems" value-key="value" searchable clearable class="w-full" placeholder="All academic units" /></UFormField>
            <UFormField label="Programme" description="Optional"><USelectMenu v-model="discountForm.programmeId" :items="programmeItems" value-key="value" searchable clearable class="w-full" placeholder="All programmes" :disabled="!discountForm.programmeLevelId" /></UFormField>
          </div>
          <UAlert class="mt-4" color="primary" variant="soft" icon="i-lucide-filter" :title="discountScopeSummary" :description="`Applies only to ${discountForm.programmeLevelId ? programmeLevelItems.find(item => item.value === discountForm.programmeLevelId)?.label ?? 'the selected level' : 'the selected programme level'} students at study level ${discountForm.programmeStudyLevel || 'not yet selected'}.`" />
          <div class="mt-4 grid gap-4 sm:grid-cols-2"><UFormField label="Apply reduction to" required><USelect v-model="discountForm.targetType" :items="discountTargetItems" class="w-full" @update:model-value="changeDiscountTarget" /></UFormField><UFormField v-if="discountForm.targetType === 'FEE_LINE'" label="Fee line" required><USelect v-model="discountForm.feeCatalogueId" :items="feeLineItems" class="w-full" searchable /></UFormField></div>
        </section>
        <section class="rounded-lg border border-muted p-4">
          <h3 class="font-semibold text-highlighted">Eligibility and effectivity</h3>
          <p class="mt-1 text-xs text-muted">The rate is applied only after the complete student applicability rule matches.</p>
          <div class="mt-4 grid gap-4 sm:grid-cols-3"><UFormField label="Discount rate" description="Percentage" required><UInput v-model.number="discountForm.discountPercentage" type="number" min="0.0001" max="99.9999" step="0.01" class="w-full" aria-label="Discount rate" /></UFormField><UFormField label="Effective from" required><UInput v-model="discountForm.effectiveFrom" type="datetime-local" class="w-full" aria-label="Effective from" /></UFormField><UFormField label="Effective until"><UInput v-model="discountForm.effectiveUntil" type="datetime-local" class="w-full" aria-label="Effective until" /></UFormField></div>
        </section>
      </div>
    </template>
    <template #footer><div class="flex w-full justify-end gap-2"><UButton label="Cancel" color="neutral" variant="outline" @click="discountDrawerOpen = false" /><EmhareGuidedActionButton label="Create draft discount" icon="i-lucide-save" :loading="saving" guidance-title="Discount details are incomplete" :guidance-instructions="[...(!discountForm.code.trim() ? ['Enter a discount code.'] : []), ...(!discountForm.name.trim() ? ['Enter a discount name.'] : []), ...(!discountForm.authorityReference.trim() ? ['Record the approval authority.'] : []), ...(!discountForm.programmeLevelId ? ['Select UG or PG programme level.'] : []), ...(!discountForm.programmeStudyLevel ? ['Select the programme study level, such as 3.1 or 3.2.'] : []), ...(discountForm.discountPercentage <= 0 || discountForm.discountPercentage >= 100 ? ['Enter a discount rate greater than zero and less than 100.'] : []), ...(!discountForm.effectiveFrom ? ['Enter the effective-from date and time.'] : []), ...(discountForm.targetType === 'FEE_LINE' && !discountForm.feeCatalogueId ? ['Select the fee line.'] : [])]" @click="createDiscount" /></div></template>
  </EmhareRecordDrawer>

  <EmhareRecordDrawer v-model:open="catalogueModalOpen" presentation="page" title="Create fee definition" description="Create the institutional charge and posting-account policy. A different Finance operator must activate it.">
    <template #body><div class="space-y-4"><div class="grid gap-4 sm:grid-cols-2"><UFormField label="Fee code" required><UInput v-model="catalogueForm.code" class="w-full" placeholder="TUITION-UG" /></UFormField><UFormField label="Charge type" required><USelect v-model="catalogueForm.chargeType" :items="chargeTypeItems" class="w-full" /></UFormField></div><UFormField label="Fee name" required><UInput v-model="catalogueForm.name" class="w-full" placeholder="Undergraduate tuition" /></UFormField><UFormField label="Policy description"><UTextarea v-model="catalogueForm.description" :rows="3" class="w-full" /></UFormField><div class="grid gap-4 sm:grid-cols-2"><UFormField label="Receivable account code" required><UInput v-model="catalogueForm.receivableAccountCode" class="w-full" placeholder="AR-STUDENT" /></UFormField><UFormField label="Revenue account code" required><UInput v-model="catalogueForm.revenueAccountCode" class="w-full" placeholder="REV-TUITION" /></UFormField></div><div class="grid gap-4 sm:grid-cols-2"><UFormField label="Tax code"><UInput v-model="catalogueForm.taxCode" class="w-full" /></UFormField><UFormField label="Base currency"><UInput model-value="USD" disabled class="w-full" /></UFormField></div></div></template>
    <template #footer><div class="flex w-full justify-end gap-2"><UButton label="Cancel" color="neutral" variant="outline" @click="catalogueModalOpen = false" /><EmhareGuidedActionButton label="Create draft definition" icon="i-lucide-save" :loading="saving" guidance-title="Fee definition details are incomplete" :guidance-instructions="[...(!catalogueForm.code.trim() ? ['Enter a fee code.'] : []), ...(!catalogueForm.name.trim() ? ['Enter a fee name.'] : []), ...(!catalogueForm.receivableAccountCode.trim() ? ['Enter the receivable account code.'] : []), ...(!catalogueForm.revenueAccountCode.trim() ? ['Enter the revenue account code.'] : [])]" @click="createCatalogue" /></div></template>
  </EmhareRecordDrawer>

  <EmhareRecordDrawer v-model:open="ruleModalOpen" presentation="page" :title="`Add effective price · ${selectedCatalogue?.code ?? ''}`" description="A price is selected only when every scope dimension and effective date matches the billing event.">
    <template #body><div class="space-y-5"><div class="grid gap-4 sm:grid-cols-3"><UFormField label="Transaction currency" required><USelect v-model="ruleForm.transactionCurrencyCode" :items="currencyItems" class="w-full" /></UFormField><UFormField label="Transaction amount" required><UInput v-model.number="ruleForm.transactionAmount" type="number" min="0.01" step="0.01" class="w-full" /></UFormField><UFormField label="Base currency"><UInput model-value="USD" disabled class="w-full" /></UFormField><UFormField label="Effective from" required><UInput v-model="ruleForm.effectiveFrom" type="datetime-local" class="w-full" /></UFormField><UFormField label="Effective until"><UInput v-model="ruleForm.effectiveUntil" type="datetime-local" class="w-full" /></UFormField></div><UAlert v-if="ruleForm.transactionCurrencyCode !== 'USD'" color="warning" variant="soft" title="Foreign-currency price requires effective rate evidence" description="If Finance has not captured a valid rate for the effective date, this price remains unrated and cannot be approved or used for billing." /><section class="rounded-lg border border-muted"><div class="flex items-center justify-between border-b border-muted p-3"><div><h3 class="font-medium">Applicability scope</h3><p class="text-xs text-muted">Use one row per dimension. Global cannot be combined with another scope.</p></div><EmhareGuidedActionButton label="Add dimension" size="xs" icon="i-lucide-plus" color="neutral" variant="outline" guidance-title="Another scope dimension cannot be added" :guidance-instructions="ruleForm.scopes.some(scope => scope.scopeDimension === 'GLOBAL') ? ['Global scope cannot be combined with another dimension. Change or remove the Global scope first.'] : ruleForm.scopes.length >= 9 ? ['A price rule can contain at most nine scope dimensions.'] : []" @click="addScope" /></div><div class="space-y-3 p-3"><div v-for="(scope, index) in ruleForm.scopes" :key="index" class="grid gap-3 rounded-md bg-muted/30 p-3 sm:grid-cols-[190px_1fr_1fr_auto]"><UFormField label="Dimension" required><USelect v-model="scope.scopeDimension" :items="scopeDimensionItems" class="w-full" @update:model-value="changeScopeDimension(scope)" /></UFormField><template v-if="scope.scopeDimension !== 'GLOBAL'"><UFormField label="Reference code or UUID" required><div class="grid grid-cols-2 gap-2"><UInput v-model="scope.referenceCode" placeholder="Code" /><UInput v-model="scope.referenceId" placeholder="UUID" /></div></UFormField><UFormField label="Reference name" required><UInput v-model="scope.referenceName" class="w-full" /></UFormField></template><div v-else class="self-end pb-2 text-sm text-muted sm:col-span-2">Applies to every eligible record for this fee definition.</div><EmhareGuidedActionButton icon="i-lucide-trash-2" color="error" variant="ghost" class="self-end" guidance-title="Scope cannot be removed" :guidance-instructions="ruleForm.scopes.length === 1 ? ['A fee price must retain at least one applicability scope.'] : []" aria-label="Remove scope" @click="removeScope(index)" /></div></div></section></div></template>
    <template #footer><div class="flex w-full justify-end gap-2"><UButton label="Cancel" color="neutral" variant="outline" @click="ruleModalOpen = false" /><EmhareGuidedActionButton label="Create draft price" icon="i-lucide-calendar-plus" :loading="saving" guidance-title="Fee price details are incomplete" :guidance-instructions="[...(ruleForm.transactionAmount <= 0 ? ['Enter a transaction amount greater than zero.'] : []), ...(!ruleForm.effectiveFrom ? ['Enter the effective-from date and time.'] : []), ...(!ruleForm.scopes.length ? ['Add at least one applicability scope.'] : []), ...(!ruleForm.scopes.every(scope => scope.scopeDimension === 'GLOBAL' || Boolean(scope.referenceName.trim() && (scope.referenceId.trim() || scope.referenceCode.trim()))) ? ['Complete the reference name and code or UUID for every non-global scope.'] : [])]" @click="createRule" /></div></template>
  </EmhareRecordDrawer>
</template>
