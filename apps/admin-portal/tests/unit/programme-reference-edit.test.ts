// Author: Tinashe K

import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ProgrammeLevelSummary, ProgrammeTypeSummary } from '../../../../packages/portal-shell/types/academic'

Object.assign(globalThis, { computed, nextTick, onMounted, reactive, ref })
vi.stubGlobal('definePageMeta', vi.fn())

describe('Programme reference editing', () => {
  const request = vi.fn(async () => undefined)
  const loadOverview = vi.fn(async () => undefined)
  const toastAdd = vi.fn()
  const level: ProgrammeLevelSummary = { id: 'level-1', code: 'UNDERGRAD', name: 'Undergraduate', sortOrder: 1, status: 'ACTIVE', version: 2 }
  const type: ProgrammeTypeSummary = { id: 'type-1', code: 'DEGREE', name: 'Degree', status: 'ACTIVE', version: 4 }

  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('useEmhareApi', () => ({ request, errorMessage: vi.fn() }))
    vi.stubGlobal('useToast', () => ({ add: toastAdd }))
    vi.stubGlobal('useEmhareConfirm', () => ({ confirmAction: vi.fn(), showError: vi.fn() }))
    vi.stubGlobal('useProgrammeStudyPeriod', () => ({ durationLabel: vi.fn(), durationPeriodsFromYears: vi.fn(), durationYearsFromPeriods: vi.fn() }))
    vi.stubGlobal('useAcademicSetup', () => ({
      overview: ref({ programmes: [], programmeLevels: [level], programmeTypes: [type], academicUnitTypes: [], academicUnits: [] }),
      loading: ref(false), loadError: ref(null), ensureOverview: vi.fn(async () => undefined), loadOverview
    }))
  })

  it.each([
    ['level', level, '/api/academic/programme-levels/level-1', { name: 'Bachelor level', sortOrder: 2, expectedVersion: 2 }],
    ['type', type, '/api/academic/programme-types/type-1', { name: 'Academic degree', expectedVersion: 4 }]
  ] as const)('updates a programme %s while keeping its code stable', async (kind, record, endpoint, body) => {
    const ProgrammePage = (await import('../../pages/operations/programmes.vue')).default
    const wrapper = shallowMount(ProgrammePage)
    const viewModel = wrapper.vm as unknown as {
      referenceForm: { name: string, sortOrder: number }
      editReference: (kind: 'level' | 'type', record: ProgrammeLevelSummary | ProgrammeTypeSummary) => void
      saveReference: () => Promise<void>
    }

    viewModel.editReference(kind, record)
    viewModel.referenceForm.name = body.name
    if (kind === 'level') viewModel.referenceForm.sortOrder = 2
    await viewModel.saveReference()

    expect(request).toHaveBeenCalledWith(endpoint, { method: 'PUT', body })
    expect(loadOverview).toHaveBeenCalledOnce()
    expect(toastAdd).toHaveBeenCalledWith(expect.objectContaining({ title: `Programme ${kind} updated` }))
  })
})
