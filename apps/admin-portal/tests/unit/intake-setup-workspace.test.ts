// Author: Tinashe K

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

Object.assign(globalThis, { computed, nextTick, onMounted, reactive, ref, watch })
vi.stubGlobal('definePageMeta', vi.fn())

const PassThrough = {
  template: '<div><slot name="header"/><slot name="body"/><slot/></div>'
}

describe('Intake setup workspace', () => {
  const request = vi.fn(async (path: string) => {
    if (path === '/api/admissions/application-types') {
      return [{
        id: 'route-undergrad',
        code: 'UNDERGRAD',
        name: 'Undergraduate and Diploma',
        requiresEmploymentHistory: false,
        requiresReferees: false,
        financeFeeStructureId: 'fee-1',
        financeFeeStructureCode: 'UAF',
        financeFeeStructureName: 'Undergraduate application fee',
        active: true,
        version: 2
      }]
    }
    if (path === '/api/finance/fee-structures') {
      return { structures: [] }
    }
    if (path === '/api/admissions/application-types/route-undergrad/route-configuration') {
      return {
        applicationTypeId: 'route-undergrad',
        code: 'UNDERGRAD',
        name: 'Undergraduate and Diploma',
        active: true,
        readyForActivation: true,
        readinessBlockers: [],
        programmes: [
          { programmeId: 'programme-1', programmeCode: 'HACCN', programmeName: 'Accounting Honours' },
          { programmeId: 'programme-2', programmeCode: 'HCS', programmeName: 'Computer Science' }
        ],
        sections: [],
        documents: [],
        feePolicyStatus: 'FEE_STRUCTURE',
        version: 2
      }
    }
    throw new Error(`Unexpected request: ${path}`)
  })

  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('useRoute', () => ({ params: { intakeId: 'new' } }))
    vi.stubGlobal('useEmhareApi', () => ({ request, errorMessage: (_error: unknown, fallback = 'Request failed') => fallback }))
    vi.stubGlobal('useToast', () => ({ add: vi.fn() }))
    vi.stubGlobal('useEmhareConfirm', () => ({ showError: vi.fn() }))
    vi.stubGlobal('navigateTo', vi.fn())
    vi.stubGlobal('useAcademicSetup', () => ({
      overview: ref({
        academicYears: [{ id: 'year-1', name: '2027 Academic Year', startDate: '2027-01-01', endDate: '2027-12-31', status: 'OPEN', changeReason: '', version: 1 }],
        programmeLevels: [{ id: 'level-1', code: 'UG', name: 'Undergraduate', status: 'ACTIVE', version: 1 }],
        programmes: [
          { id: 'programme-1', code: 'HACCN', name: 'Accounting Honours', programmeLevelId: 'level-1', programmeLevelName: 'Undergraduate', status: 'ACTIVE' },
          { id: 'programme-2', code: 'HCS', name: 'Computer Science', programmeLevelId: 'level-1', programmeLevelName: 'Undergraduate', status: 'ACTIVE' }
        ],
        intakes: []
      }),
      ensureOverview: vi.fn(async () => undefined),
      loadOverview: vi.fn(async () => undefined)
    }))
  })

  it('derives read-only route coverage from Application Types instead of asking for Programmes again', async () => {
    const IntakeSetupPage = (await import('../../pages/operations/academic-calendar/intakes/[intakeId].vue')).default
    const wrapper = shallowMount(IntakeSetupPage, {
      global: {
        stubs: {
          UDashboardPanel: PassThrough,
          UDashboardNavbar: PassThrough,
          UDashboardToolbar: PassThrough,
          UFormField: PassThrough
        }
      }
    })
    await flushPromises()

    const viewModel = wrapper.vm as unknown as {
      setupStep: number
      intakeForm: { programmeLevelIds: string[], programmeIds: string[] }
      intakeRouteSetups: Array<{ feeMode: 'FEE_STRUCTURE' | 'FEE_FREE' }>
      openingChangeReason: string
      routeProgrammes: (routeSetup: unknown) => Array<{ id: string, code: string }>
      configureApplicationRoutes: (activateRoutes: boolean) => Promise<void>
    }
    viewModel.intakeForm.programmeLevelIds = ['level-1']
    viewModel.intakeForm.programmeIds = ['programme-1', 'programme-2']
    viewModel.setupStep = 3
    await nextTick()

    const undergradRouteSetup = viewModel.intakeRouteSetups[0]
    expect(undergradRouteSetup).toBeDefined()
    if (!undergradRouteSetup) throw new Error('Expected the UNDERGRAD route configuration to load.')
    expect(viewModel.routeProgrammes(undergradRouteSetup).map(programme => programme.code)).toEqual(['HACCN', 'HCS'])
    expect(wrapper.find('[aria-label="UNDERGRAD Programmes"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Managed in Application Types')

    undergradRouteSetup.feeMode = 'FEE_FREE'
    viewModel.openingChangeReason = 'Verification of intake route ownership'
    await viewModel.configureApplicationRoutes(false)
    expect(request).toHaveBeenLastCalledWith('/api/admissions/application-types/route-undergrad/route-configuration', {
      method: 'PUT',
      body: expect.objectContaining({
        programmes: [
          { programmeId: 'programme-1', programmeCode: 'HACCN', programmeName: 'Accounting Honours' },
          { programmeId: 'programme-2', programmeCode: 'HCS', programmeName: 'Computer Science' }
        ]
      })
    })
  })

  it('keeps the wizard actions floating and explains incomplete stages on click', async () => {
    const IntakeSetupPage = (await import('../../pages/operations/academic-calendar/intakes/[intakeId].vue')).default
    const wrapper = shallowMount(IntakeSetupPage, {
      global: { stubs: { UDashboardPanel: PassThrough, UDashboardNavbar: PassThrough, UDashboardToolbar: PassThrough } }
    })
    await flushPromises()

    const footer = wrapper.get('[data-testid="intake-wizard-actions"]')
    expect(footer.classes()).toContain('fixed')
    expect(wrapper.find('emhareguidedactionbutton').exists()).toBe(true)
  })
})
