// Author: Tinashe K

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AcademicModuleSummary } from '../../../../packages/portal-shell/types/academic'

Object.assign(globalThis, { computed, nextTick, onMounted, reactive, ref, watch })
vi.stubGlobal('definePageMeta', vi.fn())

describe('Module catalogue editing', () => {
  const moduleRecord: AcademicModuleSummary = {
    id: 'module-1',
    code: 'CSC101',
    name: 'Communication Skills',
    description: 'Academic communication fundamentals.',
    owningAcademicUnitId: 'unit-1',
    owningAcademicUnitName: 'Computer Science Department',
    creditValue: 10,
    academicLevel: 1,
    status: 'DRAFT',
    legacyCourseCode: 'CS101',
    version: 3
  }

  const request = vi.fn(async () => undefined)
  const loadOverview = vi.fn(async () => undefined)
  const toastAdd = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('useEmhareApi', () => ({ request, errorMessage: vi.fn() }))
    vi.stubGlobal('useToast', () => ({ add: toastAdd }))
    vi.stubGlobal('useEmhareConfirm', () => ({ confirmAction: vi.fn(), showError: vi.fn() }))
    vi.stubGlobal('navigateTo', vi.fn())
    vi.stubGlobal('useAcademicSetup', () => ({
      overview: ref({
        modules: [moduleRecord],
        academicUnitTypes: [{ id: 'type-1', status: 'ACTIVE', leafAllowed: true }],
        academicUnits: [{ id: 'unit-1', code: 'CS', name: 'Computer Science Department', status: 'ACTIVE', academicUnitTypeId: 'type-1', parentId: null }]
      }),
      loading: ref(false),
      loadError: ref(null),
      ensureOverview: vi.fn(async () => undefined),
      loadOverview
    }))
  })

  it('prefills a Module and sends the optimistic update payload', async () => {
    const ModulePage = (await import('../../pages/operations/modules.vue')).default
    const wrapper = shallowMount(ModulePage)
    const viewModel = wrapper.vm as unknown as {
      moduleModalOpen: boolean
      moduleForm: Record<string, unknown> & { name: string }
      editModule: (module: AcademicModuleSummary) => void
      saveModule: () => Promise<void>
    }

    viewModel.editModule(moduleRecord)
    expect(viewModel.moduleModalOpen).toBe(true)
    expect(viewModel.moduleForm).toMatchObject({
      id: 'module-1',
      owningAcademicUnitId: 'unit-1',
      code: 'CSC101',
      name: 'Communication Skills',
      creditValue: 10,
      academicLevel: 1,
      expectedVersion: 3
    })

    viewModel.moduleForm.name = 'Professional Communication Skills'
    await viewModel.saveModule()
    await flushPromises()

    expect(request).toHaveBeenCalledWith('/api/academic/modules/module-1', {
      method: 'PUT',
      body: {
        owningAcademicUnitId: 'unit-1',
        code: 'CSC101',
        name: 'Professional Communication Skills',
        description: 'Academic communication fundamentals.',
        creditValue: 10,
        academicLevel: 1,
        legacyCourseCode: 'CS101',
        expectedVersion: 3
      }
    })
    expect(loadOverview).toHaveBeenCalledOnce()
    expect(toastAdd).toHaveBeenCalledWith(expect.objectContaining({ title: 'Module updated' }))
    expect(viewModel.moduleModalOpen).toBe(false)
  })
})
