// Author: Tinashe K

import { defineComponent, nextTick, onMounted, ref, type Component } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

Object.assign(globalThis, { onMounted, ref })
vi.stubGlobal('definePageMeta', vi.fn())

describe('Admissions reports catalogue', () => {
  const DashboardPanelStub = defineComponent({
    template: '<div><slot name="header" /><slot name="body" /></div>'
  })
  const NavbarStub = defineComponent({
    props: ['title'],
    template: '<header><h1>{{ title }}</h1><slot name="right" /></header>'
  })
  const ButtonStub = defineComponent({
    inheritAttrs: false,
    props: ['label', 'to'],
    emits: ['click'],
    template: '<a v-if="to" :href="to">{{ label }}</a><button v-else :aria-label="$attrs[\'aria-label\']" @click="$emit(\'click\')">{{ label }}</button>'
  })
  const BadgeStub = defineComponent({
    props: ['label'],
    template: '<span class="badge-stub">{{ label }}</span>'
  })
  const AlertStub = defineComponent({
    props: ['title', 'description'],
    template: '<div class="alert-stub">{{ title }} {{ description }}<slot name="actions" /></div>'
  })
  const SkeletonStub = defineComponent({
    template: '<div class="skeleton-stub" />'
  })
  const IconStub = defineComponent({
    template: '<span class="icon-stub" />'
  })

  const catalogue = [
    {
      code: 'APPLICATION_DEMAND',
      family: 'Application demand',
      title: 'Programme and academic-unit demand',
      description: 'Compare distinct applications and ranked Programme choices.',
      formats: ['SCREEN', 'BAR_CHART', 'PDF'],
      variants: [
        'Programme application report',
        'Academic-unit Programme statistics',
        'Ranked-choice demand by gender',
        'Offered counts',
        'Accepted counts'
      ]
    },
    {
      code: 'EXECUTIVE_STATISTICS',
      family: 'Executive statistics',
      title: 'Admissions executive statistics',
      description: 'Outcome and conversion totals.',
      formats: ['SCREEN', 'XLSX', 'PDF'],
      variants: ['Accepted-applicant statistics']
    },
    {
      code: 'OFFER_LETTERS',
      family: 'Offer letters',
      title: 'Governed offer-letter operations',
      description: 'Generate and publish current offer letters.',
      formats: ['PDF', 'EMAIL'],
      variants: ['Local', 'International']
    }
  ]

  function mountReportsPage(ReportsPage: Component) {
    return mount(ReportsPage, {
      global: {
        stubs: {
          UDashboardPanel: DashboardPanelStub,
          UDashboardNavbar: NavbarStub,
          UButton: ButtonStub,
          UBadge: BadgeStub,
          UAlert: AlertStub,
          USkeleton: SkeletonStub,
          UIcon: IconStub
        }
      }
    })
  }

  const request = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    request.mockReset()
    request.mockResolvedValue(catalogue)
    vi.stubGlobal('useEmhareApi', () => ({
      request,
      errorMessage: vi.fn((_error, fallback) => fallback)
    }))
  })

  it('loads only the catalogue and keeps report statistics inside report pages', async () => {
    const ReportsPage = (await import('../../pages/operations/admissions-reports/index.vue')).default
    const wrapper = mountReportsPage(ReportsPage)
    await flushPromises()

    expect(request).toHaveBeenCalledOnce()
    expect(request).toHaveBeenCalledWith('/api/admissions/reports/catalogue')
    expect(wrapper.text()).toContain('Report catalogue')
    expect(wrapper.text()).toContain('Open a report to apply filters, review results and export.')
    expect(wrapper.text()).not.toContain('Current pipeline status')
    expect(wrapper.text()).not.toContain('Detailed application register')
    expect(wrapper.text()).not.toContain('Distinct pipeline totals')
    expect(wrapper.findAll('select')).toHaveLength(0)
  })

  it('uses a flat operational UZ treatment without generated-dashboard decoration', async () => {
    const ReportsPage = (await import('../../pages/operations/admissions-reports/index.vue')).default
    const wrapper = mountReportsPage(ReportsPage)
    await flushPromises()

    expect(wrapper.text()).toContain('Report catalogue')
    expect(wrapper.text()).not.toContain('Admissions intelligence')
    expect(wrapper.html()).toContain('uzgreen-900')
    expect(wrapper.html()).not.toContain('bg-gradient')
    expect(wrapper.html()).not.toContain('blur-3xl')
    expect(wrapper.html()).not.toContain('hover:-translate')
    expect(wrapper.html()).not.toContain('padStart')
  })

  it('shows concise report cards with formats and dedicated destinations', async () => {
    const ReportsPage = (await import('../../pages/operations/admissions-reports/index.vue')).default
    const wrapper = mountReportsPage(ReportsPage)
    await flushPromises()

    expect(wrapper.text()).toContain('Programme and academic-unit demand')
    expect(wrapper.text()).toContain('Screen')
    expect(wrapper.text()).toContain('Bar chart')
    expect(wrapper.text()).toContain('Excel')
    expect(wrapper.text()).toContain('+2 more covered outputs')

    const links = wrapper.findAll('a')
    expect(links.map(link => [link.text(), link.attributes('href')])).toEqual([
      ['Open report', '/operations/admissions-reports/application-demand'],
      ['Open report', '/operations/admissions-reports/executive-statistics'],
      ['Open offer-letter workspace', '/operations/admissions-offers']
    ])
  })

  it('shows catalogue loading placeholders without rendering report-data skeletons', async () => {
    request.mockReturnValue(new Promise(() => undefined))
    const ReportsPage = (await import('../../pages/operations/admissions-reports/index.vue')).default
    const wrapper = mountReportsPage(ReportsPage)
    await Promise.resolve()
    await nextTick()

    expect(wrapper.findAll('.skeleton-stub')).toHaveLength(6)
  })

  it('shows a clear empty catalogue state', async () => {
    request.mockResolvedValue([])
    const ReportsPage = (await import('../../pages/operations/admissions-reports/index.vue')).default
    const wrapper = mountReportsPage(ReportsPage)
    await flushPromises()

    expect(wrapper.text()).toContain('No report families are available')
    expect(wrapper.text()).toContain('The admissions report catalogue has not been configured yet.')
  })

  it('shows a retry action when catalogue loading fails', async () => {
    request.mockRejectedValueOnce(new Error('unavailable')).mockResolvedValueOnce(catalogue)
    const ReportsPage = (await import('../../pages/operations/admissions-reports/index.vue')).default
    const wrapper = mountReportsPage(ReportsPage)
    await flushPromises()

    expect(wrapper.text()).toContain('Report catalogue unavailable')
    expect(wrapper.text()).toContain('The admissions report catalogue could not be loaded.')

    await wrapper.get('[aria-label="Retry loading admissions reports"]').trigger('click')
    await flushPromises()

    expect(request).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('Programme and academic-unit demand')
    expect(wrapper.text()).not.toContain('Report catalogue unavailable')
  })
})
