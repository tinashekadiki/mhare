<script setup lang="ts">
import type { AcademicSetupOverview } from '../../../types/academic'
import type { ApplicantCategoryOption } from '../../../types/admissions'
import type {
  FinanceChargeType,
  FinanceFeeCatalogueSummary,
  FinanceFeeContext,
  FinanceFeeStructureScopeType
} from '../../../types/finance'

type SelectItem = { label: string, value: string }
type LineDraft = {
  feeCatalogueId: string
  feeCode: string
  feeName: string
  description: string
  amount: number
  receivableAccountCode: string
  revenueAccountCode: string
  taxCode: string
}

const props = defineProps<{
  catalogues: FinanceFeeCatalogueSummary[]
  academicOverview: AcademicSetupOverview | null
  applicantCategories: ApplicantCategoryOption[]
}>()
const emit = defineEmits<{ created: [] }>()
const open = defineModel<boolean>('open', { default: false })

const api = useEmhareApi()
const { showError } = useEmhareConfirm()
const toast = useToast()
const saving = ref(false)
const form = reactive({
  code: '',
  name: '',
  description: '',
  feeContext: 'ACADEMIC' as FinanceFeeContext,
  scopeType: 'INSTITUTION' as FinanceFeeStructureScopeType,
  scopeReferenceId: '',
  programmeLevelId: '',
  applicantCategoryCode: '',
  transactionCurrencyCode: 'USD',
  effectiveFrom: localDateTimeValue(new Date()),
  effectiveUntil: '',
  lines: [newLine('TUITION', 'Tuition', 'ACADEMIC')] as LineDraft[]
})

const contextItems = [
  { label: 'Academic fee structure', value: 'ACADEMIC', icon: 'i-lucide-graduation-cap', description: 'Registration billing for programmes and academic units.' },
  { label: 'Application fee', value: 'APPLICATION', icon: 'i-lucide-file-check-2', description: 'Application charge per programme level and applicant category.' },
  { label: 'Accommodation fee', value: 'ACCOMMODATION', icon: 'i-lucide-building-2', description: 'Global accommodation charge.' }
]
const academicScopeItems = [
  { label: 'Institution default', value: 'INSTITUTION' },
  { label: 'Academic unit', value: 'ACADEMIC_UNIT' },
  { label: 'Programme override', value: 'PROGRAMME' }
]
const currencyItems = [
  { label: 'USD · base currency', value: 'USD' },
  { label: 'ZWG · effective exchange rate required', value: 'ZWG' }
]
const linePresets = [
  { label: 'Tuition', icon: 'i-lucide-graduation-cap', code: 'TUITION', name: 'Tuition' },
  { label: 'Student levy', icon: 'i-lucide-users', code: 'STUDENT-LEVY', name: 'Student levy' },
  { label: 'Sport fees', icon: 'i-lucide-trophy', code: 'SPORT-FEES', name: 'Sport fees' },
  { label: 'Blank line', icon: 'i-lucide-plus', code: '', name: '' }
]

const academicUnitItems = computed<SelectItem[]>(() => (props.academicOverview?.academicUnits ?? [])
  .filter(unit => unit.status === 'ACTIVE')
  .map(unit => ({ label: `${unit.code} · ${unit.name}`, value: unit.id })))
const programmeItems = computed<SelectItem[]>(() => (props.academicOverview?.programmes ?? [])
  .filter(programme => programme.status === 'ACTIVE'
    && (!form.programmeLevelId || programme.programmeLevelId === form.programmeLevelId))
  .map(programme => ({ label: `${programme.code} · ${programme.name}`, value: programme.id })))
const programmeLevelItems = computed<SelectItem[]>(() => (props.academicOverview?.programmeLevels ?? [])
  .filter(level => level.status === 'ACTIVE')
  .map(level => ({ label: `${level.code} · ${level.name}`, value: level.id })))
const applicantCategoryItems = computed<SelectItem[]>(() =>
  props.applicantCategories.map(category => ({ label: category.label, value: category.code })))
const catalogueItems = computed<SelectItem[]>(() => props.catalogues
  .filter(catalogue => catalogue.status !== 'RETIRED')
  .map(catalogue => ({ label: `${catalogue.code} · ${catalogue.name}`, value: catalogue.id })))
const selectedContext = computed(() => contextItems.find(item => item.value === form.feeContext)!)
const scopeReferenceItems = computed(() => {
  if (form.scopeType === 'ACADEMIC_UNIT') return academicUnitItems.value
  if (form.scopeType === 'PROGRAMME') return programmeItems.value
  if (form.scopeType === 'PROGRAMME_LEVEL') return programmeLevelItems.value
  return []
})
const selectedScopeReference = computed(() => {
  if (form.scopeType === 'ACADEMIC_UNIT') return props.academicOverview?.academicUnits.find(item => item.id === form.scopeReferenceId)
  if (form.scopeType === 'PROGRAMME') return props.academicOverview?.programmes.find(item => item.id === form.scopeReferenceId)
  if (form.scopeType === 'PROGRAMME_LEVEL') return props.academicOverview?.programmeLevels.find(item => item.id === form.programmeLevelId)
  return undefined
})
const selectedProgrammeLevel = computed(() => props.academicOverview?.programmeLevels
  .find(item => item.id === form.programmeLevelId))
const programmeLevelLockedToProgramme = computed(() => form.feeContext === 'ACADEMIC'
  && form.scopeType === 'PROGRAMME' && Boolean(form.scopeReferenceId))
const structureTotal = computed(() => form.lines.reduce((total, line) => total + Number(line.amount || 0), 0))
const guidance = computed(() => {
  const instructions: string[] = []
  if (!form.code.trim()) instructions.push('Enter a stable structure code.')
  if (!form.name.trim()) instructions.push('Enter a fee structure name.')
  if (!form.programmeLevelId) instructions.push('Select the programme level for this fee structure.')
  if (!['INSTITUTION', 'GLOBAL', 'PROGRAMME_LEVEL'].includes(form.scopeType) && !form.scopeReferenceId) instructions.push('Select the applicable scope record.')
  if (!form.effectiveFrom) instructions.push('Enter the effective-from date and time.')
  if (!form.lines.length) instructions.push('Add at least one fee line item.')
  form.lines.forEach((line, index) => {
    const prefix = `Line ${index + 1}`
    if (!line.feeCatalogueId && (!line.feeCode.trim() || !line.feeName.trim())) instructions.push(`${prefix}: select an existing definition or enter a code and name.`)
    if (!line.feeCatalogueId && (!line.receivableAccountCode.trim() || !line.revenueAccountCode.trim())) instructions.push(`${prefix}: enter both posting accounts.`)
    if (Number(line.amount) <= 0) instructions.push(`${prefix}: enter an amount greater than zero.`)
  })
  return instructions
})

watch(() => form.feeContext, (context) => {
  form.scopeReferenceId = ''
  form.programmeLevelId = ''
  form.applicantCategoryCode = ''
  form.scopeType = context === 'ACADEMIC' ? 'INSTITUTION' : context === 'APPLICATION' ? 'PROGRAMME_LEVEL' : 'GLOBAL'
  const defaultLine = context === 'APPLICATION'
    ? newLine('APPLICATION', 'Application fee', context)
    : context === 'ACCOMMODATION'
      ? newLine('ACCOMMODATION', 'Accommodation', context)
      : newLine('TUITION', 'Tuition', context)
  form.lines.splice(0, form.lines.length, defaultLine)
})

watch(() => [form.feeContext, form.scopeType, form.scopeReferenceId], () => {
  if (form.feeContext !== 'ACADEMIC' || form.scopeType !== 'PROGRAMME' || !form.scopeReferenceId) return
  const programme = props.academicOverview?.programmes.find(item => item.id === form.scopeReferenceId)
  if (programme) form.programmeLevelId = programme.programmeLevelId
})

watch(open, (isOpen) => {
  if (isOpen) reset()
})

function reset() {
  Object.assign(form, {
    code: '',
    name: '',
    description: '',
    feeContext: 'ACADEMIC',
    scopeType: 'INSTITUTION',
    scopeReferenceId: '',
    programmeLevelId: '',
    applicantCategoryCode: '',
    transactionCurrencyCode: 'USD',
    effectiveFrom: localDateTimeValue(new Date()),
    effectiveUntil: ''
  })
  form.lines.splice(0, form.lines.length, newLine('TUITION', 'Tuition', 'ACADEMIC'))
}

function newLine(code = '', name = '', context: FinanceFeeContext = form.feeContext): LineDraft {
  const accounts = defaultPostingAccounts(code, context)
  return {
    feeCatalogueId: '',
    feeCode: code,
    feeName: name,
    description: name,
    amount: 0,
    receivableAccountCode: accounts.receivable,
    revenueAccountCode: accounts.revenue,
    taxCode: ''
  }
}

function addLine(code = '', name = '') {
  form.lines.push(newLine(code, name))
}


function useExistingDefinition(line: LineDraft) {
  if (!line.feeCatalogueId) return
  const catalogue = props.catalogues.find(item => item.id === line.feeCatalogueId)
  if (!catalogue) return
  Object.assign(line, {
    feeCode: catalogue.code,
    feeName: catalogue.name,
    description: catalogue.description ?? catalogue.name,
    receivableAccountCode: catalogue.receivableAccountCode,
    revenueAccountCode: catalogue.revenueAccountCode,
    taxCode: catalogue.taxCode ?? ''
  })
}

function removeLine(index: number) {
  if (form.lines.length > 1) form.lines.splice(index, 1)
}

async function createStructure() {
  if (guidance.value.length) return
  const reference = selectedScopeReference.value
  saving.value = true
  try {
    await api.request('/api/finance/fee-structures', {
      method: 'POST',
      body: {
        code: form.code.trim().toUpperCase(),
        name: form.name.trim(),
        description: form.description.trim() || null,
        feeContext: form.feeContext,
        scopeType: form.scopeType,
        scopeReferenceId: reference?.id ?? null,
        scopeReferenceCode: reference?.code ?? null,
        scopeReferenceName: reference?.name ?? null,
        programmeLevelId: selectedProgrammeLevel.value?.id ?? null,
        programmeLevelCode: selectedProgrammeLevel.value?.code ?? null,
        programmeLevelName: selectedProgrammeLevel.value?.name ?? null,
        academicPeriodId: null,
        academicPeriodCode: null,
        academicPeriodName: null,
        programmePeriodNumber: null,
        applicantCategoryCode: form.feeContext === 'APPLICATION' ? form.applicantCategoryCode.trim() || null : null,
        transactionCurrencyCode: form.transactionCurrencyCode,
        effectiveFrom: new Date(form.effectiveFrom).toISOString(),
        effectiveUntil: form.effectiveUntil ? new Date(form.effectiveUntil).toISOString() : null,
        lines: form.lines.map(line => ({
          feeCatalogueId: line.feeCatalogueId || null,
          feeCode: line.feeCatalogueId ? null : line.feeCode.trim().toUpperCase(),
          feeName: line.feeCatalogueId ? null : line.feeName.trim(),
          description: line.description.trim() || null,
          chargeType: contextChargeType(form.feeContext),
          receivableAccountCode: line.feeCatalogueId ? null : line.receivableAccountCode.trim().toUpperCase(),
          revenueAccountCode: line.feeCatalogueId ? null : line.revenueAccountCode.trim().toUpperCase(),
          taxCode: line.feeCatalogueId ? null : line.taxCode.trim().toUpperCase() || null,
          amount: Number(line.amount)
        })),
        attachments: []
      }
    })
    open.value = false
    toast.add({ title: 'Draft fee structure created', description: 'A different Finance operator must verify and activate the complete schedule.', color: 'success' })
    emit('created')
  } catch (error) {
    await showError('Fee structure could not be created', api.errorMessage(error))
  } finally {
    saving.value = false
  }
}

function contextChargeType(context: FinanceFeeContext): FinanceChargeType {
  if (context === 'APPLICATION') return 'APPLICATION'
  if (context === 'ACCOMMODATION') return 'ACCOMMODATION'
  return 'PROGRAMME'
}

function defaultPostingAccounts(code: string, context: FinanceFeeContext) {
  const normalized = code.toUpperCase()
  if (context === 'APPLICATION') return { receivable: 'AR-APPLICATION', revenue: 'REV-APPLICATION-FEES' }
  if (context === 'ACCOMMODATION') return { receivable: 'AR-STUDENT', revenue: 'REV-ACCOMMODATION' }
  if (normalized.includes('LEVY')) return { receivable: 'AR-STUDENT', revenue: 'REV-STUDENT-LEVY' }
  if (normalized.includes('SPORT')) return { receivable: 'AR-STUDENT', revenue: 'REV-SPORT-FEES' }
  if (normalized.includes('TUITION')) return { receivable: 'AR-STUDENT', revenue: 'REV-TUITION' }
  return { receivable: 'AR-STUDENT', revenue: 'REV-FEES' }
}

function localDateTimeValue(date: Date) {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}
</script>

<template>
  <EmhareRecordDrawer
    v-model:open="open"
    title="Create fee structure"
    description="Build one reusable governed fee schedule. Configure authorised student reductions separately in the discount register."
    submit-label="Create draft structure"
    width="wide"
    :busy="saving"
    :submit-disabled="guidance.length > 0"
    :submit-disabled-reason="guidance.join(' ')"
    @submit="createStructure"
  >
    <template #body>
      <form id="fee-structure-form" class="space-y-6" @submit.prevent="createStructure">
        <section class="rounded-lg border border-muted bg-elevated/30 p-4 sm:p-5">
          <div class="mb-5 flex items-start gap-3 border-b border-muted pb-4">
            <div class="flex size-10 shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary">
              <UIcon name="i-lucide-layers-3" class="size-5" />
            </div>
            <div>
              <h3 class="font-semibold text-highlighted">Structure purpose</h3>
              <p class="mt-1 text-sm text-muted">{{ selectedContext.description }}</p>
            </div>
          </div>
          <div class="grid items-start gap-x-4 gap-y-5 sm:grid-cols-2">
            <UFormField label="Fee class" required class="sm:col-span-2">
              <USelect v-model="form.feeContext" :items="contextItems" value-key="value" label-key="label" class="w-full" aria-label="Fee class" />
            </UFormField>
            <UFormField label="Structure code" hint="Stable reference" required>
              <UInput v-model="form.code" maxlength="50" placeholder="BACC-BASE-FEES" class="w-full" />
            </UFormField>
            <UFormField label="Structure name" required>
              <UInput v-model="form.name" maxlength="160" placeholder="BAcc base fee schedule" class="w-full" />
            </UFormField>
            <UFormField label="Description" class="sm:col-span-2">
              <UTextarea v-model="form.description" :rows="3" maxlength="1000" autoresize class="w-full" />
            </UFormField>
          </div>
        </section>

        <section class="rounded-lg border border-muted bg-default p-4">
          <div class="mb-4 flex items-start justify-between gap-3">
            <div>
              <h3 class="font-semibold text-highlighted">Applicability and precedence</h3>
              <p class="mt-1 text-sm text-muted">Base structures are not tied to an academic period.</p>
            </div>
            <UBadge label="Complete schedule wins" color="primary" variant="subtle" />
          </div>
          <UFormField label="Programme level" description="Every schedule is isolated to one level, such as UG or PG." required class="mb-4">
            <USelectMenu v-model="form.programmeLevelId" :items="programmeLevelItems" value-key="value" searchable class="w-full" placeholder="Select programme level" aria-label="Programme level" :disabled="programmeLevelLockedToProgramme" />
          </UFormField>
          <template v-if="form.feeContext === 'ACADEMIC'">
            <div class="grid gap-4 sm:grid-cols-2">
              <UFormField label="Scope level" required>
                <USelect v-model="form.scopeType" :items="academicScopeItems" value-key="value" class="w-full" @update:model-value="form.scopeReferenceId = ''" />
              </UFormField>
              <UFormField v-if="form.scopeType !== 'INSTITUTION'" :label="form.scopeType === 'PROGRAMME' ? 'Programme' : 'Academic unit'" required>
                <USelectMenu v-model="form.scopeReferenceId" :items="scopeReferenceItems" value-key="value" searchable class="w-full" placeholder="Search by code or name" :aria-label="form.scopeType === 'PROGRAMME' ? 'Programme' : 'Academic unit'" />
              </UFormField>
            </div>
            <UAlert class="mt-4" color="info" variant="soft" icon="i-lucide-git-branch" title="Programme → nearest unit → institution" description="A programme structure replaces every unit structure. Without one, the programme's owning unit is checked from the lowest unit upward." />
          </template>
          <template v-else-if="form.feeContext === 'APPLICATION'">
            <div class="grid gap-4 sm:grid-cols-2">
              <UFormField label="Applicant category" description="Uses the same choices applicants select in the application portal.">
                <USelect v-model="form.applicantCategoryCode" :items="applicantCategoryItems" value-key="value" label-key="label" placeholder="All applicant categories" class="w-full" aria-label="Applicant category" />
              </UFormField>
            </div>
          </template>
          <UAlert v-else color="info" variant="soft" icon="i-lucide-building-2" title="Programme-level accommodation pricing" description="The selected UG or PG level applies to this complete schedule. Room and residence variations remain accommodation-service dimensions." />
        </section>

        <section class="rounded-lg border border-muted bg-default p-4">
          <div class="mb-4 flex flex-wrap items-start justify-between gap-3">
            <div>
              <h3 class="font-semibold text-highlighted">Fee line items</h3>
              <p class="mt-1 text-sm text-muted">Default posting accounts are filled in and remain editable before Finance activation.</p>
            </div>
            <UDropdownMenu :items="[linePresets.map(item => ({ label: item.label, icon: item.icon, onSelect: () => addLine(item.code, item.name) }))]">
              <UButton label="Add line item" icon="i-lucide-plus" color="neutral" variant="outline" />
            </UDropdownMenu>
          </div>
          <div v-for="(line, index) in form.lines" :key="index" class="mb-3 rounded-md border border-muted p-3 last:mb-0">
            <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
              <div>
                <div class="flex items-center gap-2">
                  <UBadge :label="String(index + 1)" color="primary" variant="subtle" />
                  <h4 class="font-medium text-highlighted">{{ line.feeName || 'New fee line' }}</h4>
                </div>
                <p class="mt-1 text-xs text-muted">{{ line.receivableAccountCode || 'Receivable account' }} → {{ line.revenueAccountCode || 'Revenue account' }}</p>
              </div>
              <UButton icon="i-lucide-trash-2" color="error" variant="ghost" aria-label="Remove fee line" :disabled="form.lines.length === 1" @click="removeLine(index)" />
            </div>
            <div class="grid items-start gap-x-4 gap-y-5 sm:grid-cols-2">
              <UFormField label="Reuse definition" description="Optional" class="sm:col-span-2">
                <USelectMenu v-model="line.feeCatalogueId" :items="catalogueItems" value-key="value" searchable clearable class="w-full" placeholder="Create inline definition" @update:model-value="useExistingDefinition(line)" />
              </UFormField>
              <UFormField label="Amount" required>
                <UInputNumber v-model="line.amount" :min="0.01" :step="0.01" class="w-full" />
              </UFormField>
              <UFormField label="Invoice description">
                <UInput v-model="line.description" maxlength="500" :placeholder="line.feeName" class="w-full" />
              </UFormField>
            </div>
            <div v-if="!line.feeCatalogueId" class="mt-5 grid items-start gap-x-4 gap-y-5 border-t border-muted pt-5 sm:grid-cols-2">
              <UFormField label="Fee code" required>
                <UInput v-model="line.feeCode" maxlength="50" placeholder="TUITION" class="w-full" />
              </UFormField>
              <UFormField label="Fee name" required>
                <UInput v-model="line.feeName" maxlength="160" placeholder="Tuition" class="w-full" />
              </UFormField>
              <UFormField label="Receivable account" required>
                <UInput v-model="line.receivableAccountCode" maxlength="50" placeholder="AR-STUDENT" class="w-full" />
              </UFormField>
              <UFormField label="Revenue account" required>
                <UInput v-model="line.revenueAccountCode" maxlength="50" placeholder="REV-TUITION" class="w-full" />
              </UFormField>
              <UFormField label="Tax code">
                <UInput v-model="line.taxCode" maxlength="30" class="w-full" />
              </UFormField>
            </div>
          </div>
          <div class="mt-4">
            <div class="rounded-md bg-primary/5 p-4">
              <p class="text-sm font-medium text-muted">Schedule total</p>
              <p class="mt-1 text-xl font-semibold text-primary">{{ new Intl.NumberFormat('en-ZW', { style: 'currency', currency: form.transactionCurrencyCode }).format(structureTotal) }}</p>
            </div>
          </div>
        </section>

        <section class="rounded-lg border border-muted bg-default p-4">
          <div class="mb-4">
            <h3 class="font-semibold text-highlighted">Currency and effectivity</h3>
            <p class="mt-1 text-sm text-muted">USD is the accounting base. A ZWG schedule remains blocked until an effective exchange rate exists.</p>
          </div>
          <div class="grid gap-4 sm:grid-cols-2">
            <UFormField label="Transaction currency" required>
              <USelect v-model="form.transactionCurrencyCode" :items="currencyItems" value-key="value" class="w-full" />
            </UFormField>
            <UFormField label="Base currency">
              <UInput model-value="USD" disabled class="w-full" />
            </UFormField>
            <UFormField label="Effective from" required>
              <UInput v-model="form.effectiveFrom" type="datetime-local" class="w-full" />
            </UFormField>
            <UFormField label="Effective until">
              <UInput v-model="form.effectiveUntil" type="datetime-local" class="w-full" />
            </UFormField>
          </div>
          <UAlert v-if="form.transactionCurrencyCode !== 'USD'" class="mt-4" color="warning" variant="soft" icon="i-lucide-triangle-alert" title="Exchange-rate evidence required" description="No fallback rate is used. The draft cannot be activated or billed until Finance has captured a rate effective at the schedule start." />
        </section>
      </form>
    </template>
  </EmhareRecordDrawer>
</template>
