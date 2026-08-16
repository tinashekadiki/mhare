// Author: Tinashe K

import { computed, defineComponent, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

Object.assign(globalThis, { computed, nextTick, onMounted, reactive, ref, watch })

const SearchableSelectStub = defineComponent({
  name: 'USelectMenu',
  inheritAttrs: false,
  props: {
    modelValue: { type: String, default: '' },
    items: { type: Array, default: () => [] },
    searchInput: { type: [Boolean, Object], default: true }
  },
  template: '<button :aria-label="$attrs[\'aria-label\']" />'
})
const LayoutSlotStub = defineComponent({
  template: '<div><slot /><slot name="header" /><slot name="body" /><slot name="leading" /><slot name="left" /><slot name="right" /></div>'
})

describe('Curriculum programme search', () => {
  beforeEach(() => {
    vi.stubGlobal('definePageMeta', vi.fn())
    vi.stubGlobal('useRoute', () => ({ query: {} }))
    vi.stubGlobal('useToast', () => ({ add: vi.fn() }))
    vi.stubGlobal('useEmhareConfirm', () => ({ confirmAction: vi.fn(), showError: vi.fn() }))
    vi.stubGlobal('useProgrammeStudyPeriod', () => ({
      semesterItems: [],
      studyPeriodLabel: vi.fn(),
      toProgrammePeriodNumber: vi.fn(),
      fromProgrammePeriodNumber: vi.fn(),
      yearOfStudyItems: vi.fn(() => [])
    }))
    vi.stubGlobal('useAcademicSetup', () => ({
      loadError: ref(''),
      loading: ref(false),
      overview: ref({
        programmes: [
          { id: 'programme-1', code: 'HACCN', name: 'Bachelor of Accounting Honours', maximumDurationPeriods: 8 },
          { id: 'programme-2', code: 'HCS', name: 'Bachelor of Science Honours Degree in Computer Science', maximumDurationPeriods: 8 }
        ],
        modules: []
      }),
      ensureOverview: vi.fn()
    }))
    vi.stubGlobal('useEmhareApi', () => ({ request: vi.fn(async () => []), errorMessage: vi.fn() }))
    vi.stubGlobal('navigateTo', vi.fn())
  })

  it('renders the curriculum programme selector with a searchable programme prompt', async () => {
    const CurriculumPage = (await import('../../pages/operations/curriculum.vue')).default
    const wrapper = mount(CurriculumPage, {
      global: {
        components: {
          UDashboardPanel: LayoutSlotStub,
          UDashboardNavbar: LayoutSlotStub,
          UDashboardToolbar: LayoutSlotStub
        },
        stubs: { USelectMenu: SearchableSelectStub }
      }
    })

    await nextTick()

    const programmeSelector = wrapper.findComponent(SearchableSelectStub)
    expect(programmeSelector.exists()).toBe(true)
    expect(programmeSelector.attributes('aria-label')).toBe('Programme')
    expect(programmeSelector.props('searchInput')).toEqual({ placeholder: 'Search programmes' })
    expect(programmeSelector.props('items')).toHaveLength(2)
  })
})
