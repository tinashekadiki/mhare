// Author: Tinashe K

import { computed, defineComponent, onMounted, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AdmissionsPipelineReport } from '../../../../packages/portal-shell/types/admissions'

Object.assign(globalThis, { computed, onMounted, ref })
vi.stubGlobal('defineOptions', vi.fn())
vi.stubGlobal('definePageMeta', vi.fn())

const pipelineReport: AdmissionsPipelineReport = {
  generatedAt: '2026-08-15T14:20:00Z',
  totalApplications: 24,
  totalApplicants: 22,
  statusCounts: [
    { code: 'DRAFT', count: 3 },
    { code: 'SUBMITTED', count: 4 },
    { code: 'PAYMENT_PENDING', count: 2 },
    { code: 'UNDER_REVIEW', count: 5 },
    { code: 'UNDER_ACADEMIC_REVIEW', count: 3 },
    { code: 'ADMITTED', count: 2 },
    { code: 'OFFERED', count: 2 },
    { code: 'ACCEPTED', count: 1 },
    { code: 'CONVERTED', count: 2 }
  ],
  paymentCounts: [
    { code: 'PENDING', count: 6 },
    { code: 'PAID', count: 14 },
    { code: 'WAIVED', count: 2 },
    { code: 'NOT_REQUIRED', count: 2 }
  ],
  categoryCounts: [{ code: 'LOCAL', count: 20 }, { code: 'INTERNATIONAL', count: 2 }],
  genderCounts: [{ code: 'FEMALE', count: 12 }, { code: 'MALE', count: 10 }],
  rankedChoiceCounts: [{ rank: 1, choices: 24, applications: 24 }, { rank: 2, choices: 18, applications: 18 }],
  intakeStatistics: [{
    intakeId: 'intake-1', intakeCode: 'AUG-2026', intakeName: 'August 2026', applications: 24, applicants: 22,
    statusCounts: [{ code: 'ACCEPTED', count: 1 }, { code: 'CONVERTED', count: 2 }],
    categoryCounts: [], genderCounts: [], rankedChoiceCounts: []
  }],
  programmeStatistics: [
    {
      programmeId: 'programme-1', programmeCode: 'HCS', programmeName: 'Computer Science',
      owningAcademicUnitName: 'Faculty of Science', applications: 12, applicants: 11, choices: 16,
      statusCounts: [{ code: 'ACCEPTED', count: 1 }, { code: 'CONVERTED', count: 1 }],
      categoryCounts: [], genderCounts: [], rankedChoiceCounts: []
    },
    {
      programmeId: 'programme-2', programmeCode: 'HACC', programmeName: 'Accountancy',
      owningAcademicUnitName: 'Faculty of Business', applications: 8, applicants: 8, choices: 10,
      statusCounts: [{ code: 'ADMITTED', count: 1 }], categoryCounts: [], genderCounts: [], rankedChoiceCounts: []
    }
  ],
  filterOptions: {
    intakes: [{ value: 'intake-1', code: 'AUG-2026', label: 'August 2026' }],
    applicationTypes: [{ value: 'type-1', code: 'UNDERGRAD', label: 'Undergraduate' }],
    programmes: [{ value: 'programme-1', code: 'HCS', label: 'Computer Science' }],
    categories: [{ value: 'LOCAL', code: 'LOCAL', label: 'Local' }],
    genders: [{ value: 'FEMALE', code: 'FEMALE', label: 'Female' }]
  }
}

const SlotStub = defineComponent({
  setup(_, { slots }) {
    return () => slots.default?.() ?? slots.body?.() ?? slots.header?.()
  }
})

const PanelStub = defineComponent({
  setup(_, { slots }) {
    return () => [
      slots.header?.(), slots.leading?.(), slots.left?.(), slots.right?.(),
      slots.body?.(), slots.actions?.(), slots.default?.(), slots.footer?.()
    ]
  }
})

const NavbarStub = defineComponent({
  props: ['title'],
  template: '<header><h1>{{ title }}</h1><slot name="leading" /><slot name="right" /><slot /></header>'
})

const ContainerStub = defineComponent({
  inheritAttrs: false,
  template: '<main v-bind="$attrs"><slot /></main>'
})

const ButtonStub = defineComponent({
  inheritAttrs: false,
  props: ['label', 'to'],
  emits: ['click'],
  template: '<a v-if="to" v-bind="$attrs" :href="to">{{ label }}<slot /></a><button v-else v-bind="$attrs" @click="$emit(\'click\')">{{ label }}<slot /></button>'
})

const SelectStub = defineComponent({
  inheritAttrs: false,
  props: ['modelValue', 'items'],
  emits: ['update:modelValue'],
  template: '<select v-bind="$attrs" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><option v-for="item in items" :key="item.value" :value="item.value">{{ item.label }}</option></select>'
})

const KpiStub = defineComponent({
  props: ['label', 'value', 'hint'],
  template: '<article class="kpi-stub"><h2>{{ label }}</h2><strong>{{ value }}</strong><p>{{ hint }}</p></article>'
})

const AlertStub = defineComponent({
  props: ['title', 'description'],
  template: '<section class="alert-stub"><h2>{{ title }}</h2><p>{{ description }}</p><slot name="actions" /></section>'
})

const EmptyStub = defineComponent({
  props: ['title', 'description'],
  template: '<section class="empty-stub"><h2>{{ title }}</h2><p>{{ description }}</p></section>'
})

function mountDashboard(component: object) {
  return mount(component, {
    global: {
      stubs: {
        UDashboardPanel: PanelStub,
        UDashboardNavbar: NavbarStub,
        UDashboardToolbar: PanelStub,
        UContainer: ContainerStub,
        UCard: SlotStub,
        UFormField: SlotStub,
        USelect: SelectStub,
        USelectMenu: SelectStub,
        UButton: ButtonStub,
        UAlert: AlertStub,
        UBadge: SlotStub,
        UIcon: true,
        USkeleton: true,
        UEmpty: EmptyStub,
        EmhareKpiCard: KpiStub
      }
    }
  })
}

describe('Admissions operational dashboard', () => {
  const request = vi.fn(async () => pipelineReport)

  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('useEmhareApi', () => ({
      request,
      errorMessage: vi.fn((_error: unknown, fallback: string) => fallback)
    }))
  })

  it('shows actionable workload and keeps admissions outcomes distinct', async () => {
    const DashboardPage = (await import('../../pages/operations/admissions-dashboard.vue')).default
    const wrapper = mountDashboard(DashboardPage)
    await flushPromises()

    expect(request).toHaveBeenCalledWith('/api/admissions/reports/pipeline-summary')
    expect(wrapper.text()).toContain('Admissions overview')
    expect(wrapper.findAll('h1').filter(heading => heading.text() === 'Admissions overview')).toHaveLength(1)
    expect(wrapper.get('[data-testid="admissions-dashboard-content"]').classes()).toContain('max-w-none')
    expect(wrapper.text()).toContain('24')
    expect(wrapper.text()).toContain('22 distinct people')
    expect(wrapper.text()).toContain('Payment attention')
    expect(wrapper.text()).toContain('Verification')
    expect(wrapper.text()).toContain('Eligibility processing')
    expect(wrapper.text()).toContain('Academic review')
    expect(wrapper.text()).toContain('Offer preparation')
    expect(wrapper.text()).toContain('Awaiting response')
    expect(wrapper.text()).toContain('Accepted for conversion')
    expect(wrapper.text()).toContain('Converted')
    expect(wrapper.text()).toContain('Programme demand')
    expect(wrapper.text()).toContain('16 choices')
    expect(wrapper.text()).toContain('12 applications')
  })

  it('drills action cards into filtered workflow queues and keeps reports separate', async () => {
    const DashboardPage = (await import('../../pages/operations/admissions-dashboard.vue')).default
    const wrapper = mountDashboard(DashboardPage)
    await flushPromises()

    const links = wrapper.findAll('a').map(link => ({ text: link.text(), href: link.attributes('href') }))
    expect(links).toContainEqual({ text: 'Open verification queue', href: '/operations/admissions?stage=VERIFICATION' })
    expect(links).toContainEqual({ text: 'Open academic review queue', href: '/operations/admissions?stage=ACADEMIC_REVIEW' })
    expect(links).toContainEqual({ text: 'View reports', href: '/operations/admissions-reports' })
  })

  it('applies the same governed filters to the dashboard request', async () => {
    const DashboardPage = (await import('../../pages/operations/admissions-dashboard.vue')).default
    const wrapper = mountDashboard(DashboardPage)
    await flushPromises()

    await wrapper.get('[data-testid="admissions-dashboard-intake-filter"]').setValue('intake-1')
    await wrapper.get('[data-testid="admissions-dashboard-apply-filters"]').trigger('click')
    await flushPromises()

    expect(request).toHaveBeenLastCalledWith('/api/admissions/reports/pipeline-summary?intakeId=intake-1')
  })

  it('applies every supported reporting dimension in a single governed request', async () => {
    const DashboardPage = (await import('../../pages/operations/admissions-dashboard.vue')).default
    const wrapper = mountDashboard(DashboardPage)
    await flushPromises()

    const filters = wrapper.findAll('select')
    await filters[0]!.setValue('intake-1')
    await filters[1]!.setValue('type-1')
    await filters[2]!.setValue('programme-1')
    await filters[3]!.setValue('LOCAL')
    await filters[4]!.setValue('FEMALE')
    await wrapper.get('[data-testid="admissions-dashboard-apply-filters"]').trigger('click')
    await flushPromises()

    expect(request).toHaveBeenLastCalledWith(
      '/api/admissions/reports/pipeline-summary?intakeId=intake-1&programmeId=programme-1&applicationTypeId=type-1&categoryCode=LOCAL&genderCode=FEMALE'
    )
  })

  it('clears the selected scope and reloads the institution-wide overview', async () => {
    const DashboardPage = (await import('../../pages/operations/admissions-dashboard.vue')).default
    const wrapper = mountDashboard(DashboardPage)
    await flushPromises()

    await wrapper.get('[data-testid="admissions-dashboard-intake-filter"]').setValue('intake-1')
    await wrapper.get('[aria-label="Clear Admissions dashboard filters"]').trigger('click')
    await flushPromises()

    expect(request).toHaveBeenLastCalledWith('/api/admissions/reports/pipeline-summary')
    expect((wrapper.get('[data-testid="admissions-dashboard-intake-filter"]').element as HTMLSelectElement).value)
      .toBe('__ALL__')
  })

  it('renders zero-state workload safely when the selected scope has no cases', async () => {
    request.mockResolvedValueOnce({
      ...pipelineReport,
      generatedAt: '',
      totalApplications: 0,
      totalApplicants: 0,
      statusCounts: [],
      paymentCounts: [],
      rankedChoiceCounts: [],
      intakeStatistics: [],
      programmeStatistics: []
    })
    const DashboardPage = (await import('../../pages/operations/admissions-dashboard.vue')).default
    const wrapper = mountDashboard(DashboardPage)
    await flushPromises()

    expect(wrapper.text()).toContain('Not yet refreshed')
    expect(wrapper.text()).toContain('No intake statistics match this scope.')
    expect(wrapper.text()).toContain('No Programme demand matches this scope.')
    expect(wrapper.findAll('.kpi-stub').every(card => card.text().includes('0'))).toBe(true)
  })

  it('shows loading and no-data states without exposing stale workload', async () => {
    let resolveRequest: ((value: AdmissionsPipelineReport) => void) | undefined
    request.mockImplementationOnce(() => new Promise((resolve) => {
      resolveRequest = resolve
    }))
    const DashboardPage = (await import('../../pages/operations/admissions-dashboard.vue')).default
    const wrapper = mountDashboard(DashboardPage)

    expect(wrapper.find('[aria-label="Loading Admissions overview"]').exists()).toBe(true)
    resolveRequest?.(null as unknown as AdmissionsPipelineReport)
    await flushPromises()

    expect(wrapper.text()).toContain('No Admissions data is available')
    expect(wrapper.findAll('.kpi-stub')).toHaveLength(0)
  })

  it('sorts operational summaries and renders missing ownership safely', async () => {
    request.mockResolvedValueOnce({
      ...pipelineReport,
      intakeStatistics: [
        ...pipelineReport.intakeStatistics,
        {
          ...pipelineReport.intakeStatistics[0]!,
          intakeId: 'intake-2',
          intakeCode: 'JAN-2027',
          intakeName: 'January 2027',
          applications: 30
        }
      ],
      programmeStatistics: [
        ...pipelineReport.programmeStatistics,
        {
          ...pipelineReport.programmeStatistics[0]!,
          programmeId: 'programme-3',
          programmeCode: 'HENG',
          programmeName: 'Engineering',
          owningAcademicUnitName: null,
          choices: 20
        }
      ]
    })
    const DashboardPage = (await import('../../pages/operations/admissions-dashboard.vue')).default
    const wrapper = mountDashboard(DashboardPage)
    await flushPromises()

    const intakePulseText = wrapper.get('section[aria-labelledby="intake-pulse-heading"]').text()
    const programmeDemandText = wrapper.get('section[aria-labelledby="programme-demand-heading"]').text()
    expect(intakePulseText.indexOf('JAN-2027')).toBeLessThan(intakePulseText.indexOf('AUG-2026'))
    expect(programmeDemandText.indexOf('HENG')).toBeLessThan(programmeDemandText.indexOf('HCS'))
    expect(wrapper.text()).toContain('Academic unit not recorded')
  })

  it('shows a retryable error without presenting stale metrics', async () => {
    request.mockRejectedValueOnce(new Error('report unavailable'))
    const DashboardPage = (await import('../../pages/operations/admissions-dashboard.vue')).default
    const wrapper = mountDashboard(DashboardPage)
    await flushPromises()

    expect(wrapper.text()).toContain('Admissions overview unavailable')
    expect(wrapper.text()).toContain('The Admissions overview could not be loaded.')
    expect(wrapper.findAll('.kpi-stub')).toHaveLength(0)

    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Admissions snapshot')
  })
})
