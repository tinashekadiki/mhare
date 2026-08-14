// Author: Tinashe K

import { computed, defineComponent, nextTick, onMounted, ref, type Component } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

Object.assign(globalThis, { computed, onMounted, ref })
vi.stubGlobal('definePageMeta', vi.fn())

describe('Admissions operational report page', () => {
  const FilterSelectStub = defineComponent({
    name: 'FilterSelectStub',
    props: ['items', 'modelValue'],
    emits: ['update:modelValue'],
    template: '<div class="filter-select-stub" />'
  })
  const DashboardPanelStub = defineComponent({
    template: '<div><slot name="header" /><slot name="body" /></div>'
  })
  const DefaultSlotStub = defineComponent({
    template: '<div><slot /></div>'
  })
  const NamedSlotsStub = defineComponent({
    template: '<section><slot name="leading" /><slot name="left" /><slot name="right" /><slot /></section>'
  })
  const ButtonStub = defineComponent({
    inheritAttrs: false,
    props: ['label', 'to'],
    emits: ['click'],
    template: '<a v-if="to" :href="to">{{ label }}</a><button v-else :aria-label="$attrs[\'aria-label\']" @click="$emit(\'click\')">{{ label }}</button>'
  })
  const AlertStub = defineComponent({
    props: ['title', 'description'],
    template: '<div class="alert-stub">{{ title }} {{ description }}</div>'
  })
  const SkeletonStub = defineComponent({ template: '<div class="skeleton-stub" />' })

  const request = vi.fn()
  const toastAdd = vi.fn()
  const objectUrl = 'blob:operational-report-export'
  let anchorClick: ReturnType<typeof vi.spyOn>

  function pipelineResponse() {
    return {
      generatedAt: '2026-08-14T09:00:00Z', totalApplications: 2, totalApplicants: 2,
      statusCounts: [], paymentCounts: [], categoryCounts: [], genderCounts: [], rankedChoiceCounts: [],
      intakeStatistics: [], programmeStatistics: [],
      filterOptions: {
        intakes: [{ value: 'intake-1', code: 'AUG-2026', label: 'August 2026' }],
        applicationTypes: [{ value: 'type-1', code: 'UNDERGRAD', label: 'Undergraduate' }],
        programmes: [{ value: 'programme-1', code: 'HCS', label: 'Computer Science' }],
        categories: [{ value: 'LOCAL', code: 'LOCAL', label: 'Local' }],
        genders: [{ value: 'FEMALE', code: 'FEMALE', label: 'Female' }]
      }
    }
  }

  function operationalReportResponse() {
    return {
      definition: {
        code: 'APPLICATION_DEMAND', family: 'Application demand', title: 'Programme and academic-unit demand',
        description: 'Compare Programme demand and outcomes.', formats: ['SCREEN', 'BAR_CHART', 'XLSX', 'PDF'],
        variants: ['Programme application report']
      },
      generatedAt: '2026-08-14T09:00:00Z',
      metrics: [{ label: 'Applications', value: '2' }, { label: 'Applicants', value: '2' }],
      columns: [{ key: 'programme', label: 'Programme' }, { key: 'choices', label: 'Choices' }],
      rows: [['HCS · Computer Science', '3']],
      chart: [{ label: 'HCS', value: 3, series: 'Programme choices' }],
      notes: ['Applications and applicants are distinct.']
    }
  }

  function mountReportPage(ReportPage: Component) {
    return mount(ReportPage, {
      global: {
        stubs: {
          UDashboardPanel: DashboardPanelStub,
          UDashboardNavbar: NamedSlotsStub,
          UDashboardToolbar: NamedSlotsStub,
          UFormField: DefaultSlotStub,
          UButton: ButtonStub,
          UAlert: AlertStub,
          UBadge: DefaultSlotStub,
          USelect: FilterSelectStub,
          USelectMenu: FilterSelectStub,
          USkeleton: SkeletonStub
        }
      }
    })
  }

  beforeEach(() => {
    vi.clearAllMocks()
    request.mockReset()
    request.mockImplementation((path: string) => {
      if (path.includes('/export?')) return Promise.resolve(new Blob(['export']))
      if (path.includes('/pipeline-summary')) return Promise.resolve(pipelineResponse())
      return Promise.resolve(operationalReportResponse())
    })
    vi.stubGlobal('useRoute', () => ({ params: { reportCode: 'application-demand' } }))
    vi.stubGlobal('useToast', () => ({ add: toastAdd }))
    vi.stubGlobal('useEmhareApi', () => ({ request, errorMessage: vi.fn((_error, fallback) => fallback) }))
    Object.assign(URL, {
      createObjectURL: vi.fn(() => objectUrl),
      revokeObjectURL: vi.fn()
    })
    anchorClick = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
  })

  it('loads the route report as a complete dedicated workspace', async () => {
    const ReportPage = (await import('../../pages/operations/admissions-reports/[reportCode].vue')).default
    const wrapper = mountReportPage(ReportPage)
    await flushPromises()

    expect(request).toHaveBeenCalledWith('/api/admissions/reports/pipeline-summary')
    expect(request).toHaveBeenCalledWith('/api/admissions/reports/APPLICATION_DEMAND')
    expect(wrapper.text()).toContain('Programme and academic-unit demand')
    expect(wrapper.text()).toContain('HCS · Computer Science')
    expect(wrapper.text()).toContain('Visual summary')
    expect(wrapper.get('a').attributes('href')).toBe('/operations/admissions-reports')
  })

  it('applies selected filters to the visible report', async () => {
    const ReportPage = (await import('../../pages/operations/admissions-reports/[reportCode].vue')).default
    const wrapper = mountReportPage(ReportPage)
    await flushPromises()
    const selects = wrapper.findAllComponents(FilterSelectStub)

    selects[0]!.vm.$emit('update:modelValue', 'intake-1')
    selects[2]!.vm.$emit('update:modelValue', 'programme-1')
    await nextTick()
    await wrapper.findAll('button').find(button => button.text() === 'Apply filters')!.trigger('click')
    await flushPromises()

    expect(request).toHaveBeenLastCalledWith(
      '/api/admissions/reports/APPLICATION_DEMAND?intakeId=intake-1&programmeId=programme-1'
    )
  })

  it.each([
    ['Export Excel workbook', 'xlsx'],
    ['Export PDF', 'pdf']
  ])('downloads %s using the active filters', async (label, format) => {
    const ReportPage = (await import('../../pages/operations/admissions-reports/[reportCode].vue')).default
    const wrapper = mountReportPage(ReportPage)
    await flushPromises()
    const selects = wrapper.findAllComponents(FilterSelectStub)
    selects[0]!.vm.$emit('update:modelValue', 'intake-1')
    selects[3]!.vm.$emit('update:modelValue', 'LOCAL')
    await nextTick()

    await wrapper.findAll('button').find(button => button.text() === label)!.trigger('click')
    await flushPromises()

    expect(request).toHaveBeenLastCalledWith(
      `/api/admissions/reports/APPLICATION_DEMAND/export?intakeId=intake-1&categoryCode=LOCAL&format=${format}`,
      { responseType: 'blob' }
    )
    expect(URL.createObjectURL).toHaveBeenCalledWith(expect.any(Blob))
    expect(anchorClick).toHaveBeenCalledOnce()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith(objectUrl)
    expect(toastAdd).toHaveBeenCalledWith(expect.objectContaining({ title: 'Report download started' }))
  })

  it('clears filters and reloads the report', async () => {
    const ReportPage = (await import('../../pages/operations/admissions-reports/[reportCode].vue')).default
    const wrapper = mountReportPage(ReportPage)
    await flushPromises()
    wrapper.findAllComponents(FilterSelectStub)[0]!.vm.$emit('update:modelValue', 'intake-1')
    await nextTick()

    await wrapper.get('[aria-label="Clear report filters"]').trigger('click')
    await flushPromises()

    expect(request).toHaveBeenLastCalledWith('/api/admissions/reports/APPLICATION_DEMAND')
    const optionValues = wrapper.findAllComponents(FilterSelectStub)
      .flatMap(select => (select.props('items') as Array<{ value: string }>).map(item => item.value))
    expect(optionValues).not.toContain('')
  })

  it('shows an actionable error when an export fails', async () => {
    request.mockImplementation((path: string) => {
      if (path.includes('/export?')) return Promise.reject(new Error('unavailable'))
      if (path.includes('/pipeline-summary')) return Promise.resolve(pipelineResponse())
      return Promise.resolve(operationalReportResponse())
    })
    const ReportPage = (await import('../../pages/operations/admissions-reports/[reportCode].vue')).default
    const wrapper = mountReportPage(ReportPage)
    await flushPromises()

    await wrapper.findAll('button').find(button => button.text() === 'Export PDF')!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Export unavailable')
    expect(wrapper.text()).toContain('The selected report could not be exported.')
    expect(anchorClick).not.toHaveBeenCalled()
  })
})
