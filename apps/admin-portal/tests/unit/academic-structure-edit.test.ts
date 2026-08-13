// Author: Tinashe K

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AcademicUnitSummary, AcademicUnitTypeSummary } from '../../../../packages/portal-shell/types/academic'

Object.assign(globalThis, { computed, nextTick, onMounted, reactive, ref, watch })
vi.stubGlobal('definePageMeta', vi.fn())

describe('Academic structure hierarchy-level editing', () => {
  const unitType: AcademicUnitTypeSummary = {
    id: 'unit-type-1',
    code: 'FACULTY',
    name: 'Faculty',
    levelOrder: 1,
    leafAllowed: false,
    status: 'ACTIVE',
    version: 4
  }

  const request = vi.fn(async () => undefined)
  const loadOverview = vi.fn(async () => undefined)
  const toastAdd = vi.fn()
  const showError = vi.fn(async () => undefined)
  const errorMessage = vi.fn(() => 'The hierarchy level changed elsewhere.')

  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('useEmhareApi', () => ({ request, errorMessage }))
    vi.stubGlobal('useToast', () => ({ add: toastAdd }))
    vi.stubGlobal('useEmhareConfirm', () => ({ showError }))
    vi.stubGlobal('useAcademicSetup', () => ({
      overview: ref({ academicUnitTypes: [unitType], academicUnits: [] }),
      loading: ref(false),
      loadError: ref(null),
      ensureOverview: vi.fn(async () => undefined),
      loadOverview
    }))
  })

  it('prefills the selected level and sends an optimistic PUT update', async () => {
    const AcademicStructurePage = (await import('../../pages/operations/academic-structure.vue')).default
    const wrapper = shallowMount(AcademicStructurePage)
    const viewModel = wrapper.vm as unknown as {
      unitTypeModalOpen: boolean
      unitTypeForm: {
        id: string | null
        code: string
        name: string
        levelOrder: number
        leafAllowed: boolean
        expectedVersion: number
      }
      editUnitType: (record: AcademicUnitTypeSummary) => void
      saveUnitType: () => Promise<void>
    }

    viewModel.editUnitType(unitType)
    expect(viewModel.unitTypeModalOpen).toBe(true)
    expect(viewModel.unitTypeForm).toMatchObject({
      id: 'unit-type-1',
      code: 'FACULTY',
      name: 'Faculty',
      levelOrder: 1,
      leafAllowed: false,
      expectedVersion: 4
    })

    viewModel.unitTypeForm.name = 'Academic Faculty'
    await viewModel.saveUnitType()
    await flushPromises()

    expect(request).toHaveBeenCalledWith('/api/academic/unit-types/unit-type-1', {
      method: 'PUT',
      body: {
        code: 'FACULTY',
        name: 'Academic Faculty',
        leafAllowed: false,
        expectedVersion: 4
      }
    })
    expect(loadOverview).toHaveBeenCalledOnce()
    expect(viewModel.unitTypeModalOpen).toBe(false)
    expect(toastAdd).toHaveBeenCalledWith(expect.objectContaining({
      title: 'Academic unit type updated',
      color: 'success'
    }))
  })

  it('resets stale edit state before creating the next hierarchy level', async () => {
    const AcademicStructurePage = (await import('../../pages/operations/academic-structure.vue')).default
    const wrapper = shallowMount(AcademicStructurePage)
    const viewModel = wrapper.vm as unknown as {
      unitTypeModalOpen: boolean
      unitTypeForm: {
        id: string | null
        code: string
        name: string
        levelOrder: number
        leafAllowed: boolean
        expectedVersion: number
      }
      editUnitType: (record: AcademicUnitTypeSummary) => void
      createUnitType: () => void
      saveUnitType: () => Promise<void>
    }

    viewModel.editUnitType(unitType)
    viewModel.createUnitType()
    expect(viewModel.unitTypeForm).toMatchObject({
      id: null,
      code: '',
      name: '',
      levelOrder: 2,
      leafAllowed: false,
      expectedVersion: 0
    })

    Object.assign(viewModel.unitTypeForm, {
      code: 'INSTITUTE',
      name: 'Institute',
      leafAllowed: true
    })
    await viewModel.saveUnitType()
    await flushPromises()

    expect(request).toHaveBeenCalledWith('/api/academic/unit-types', {
      method: 'POST',
      body: {
        code: 'INSTITUTE',
        name: 'Institute',
        levelOrder: 2,
        leafAllowed: true
      }
    })
    expect(toastAdd).toHaveBeenCalledWith(expect.objectContaining({
      title: 'Academic unit type created'
    }))
  })

  it('keeps the edit drawer open and presents an API failure in SweetAlert', async () => {
    request.mockRejectedValueOnce(new Error('conflict'))
    const AcademicStructurePage = (await import('../../pages/operations/academic-structure.vue')).default
    const wrapper = shallowMount(AcademicStructurePage)
    const viewModel = wrapper.vm as unknown as {
      unitTypeModalOpen: boolean
      editUnitType: (record: AcademicUnitTypeSummary) => void
      saveUnitType: () => Promise<void>
    }

    viewModel.editUnitType(unitType)
    await viewModel.saveUnitType()

    expect(viewModel.unitTypeModalOpen).toBe(true)
    expect(errorMessage).toHaveBeenCalledWith(expect.any(Error))
    expect(showError).toHaveBeenCalledWith(
      'Unit type could not be updated',
      'The hierarchy level changed elsewhere.'
    )
    expect(loadOverview).not.toHaveBeenCalled()
  })

  it('edits descriptive academic-unit details without changing its hierarchy identity', async () => {
    const academicUnit: AcademicUnitSummary = {
      id: 'unit-1', academicUnitTypeId: 'unit-type-1', academicUnitTypeCode: 'FACULTY',
      parentId: null, code: 'SCI', name: 'Faculty of Science', status: 'ACTIVE',
      legacyFacultyCode: 'SCI', legacyDepartmentCode: null, version: 2
    }
    vi.stubGlobal('useAcademicSetup', () => ({
      overview: ref({ academicUnitTypes: [unitType], academicUnits: [academicUnit] }),
      loading: ref(false), loadError: ref(null), ensureOverview: vi.fn(async () => undefined), loadOverview
    }))
    const AcademicStructurePage = (await import('../../pages/operations/academic-structure.vue')).default
    const wrapper = shallowMount(AcademicStructurePage)
    const viewModel = wrapper.vm as unknown as {
      academicUnitModalOpen: boolean
      academicUnitForm: Record<string, unknown> & { name: string }
      editAcademicUnit: (record: AcademicUnitSummary) => void
      saveAcademicUnit: () => Promise<void>
    }

    viewModel.editAcademicUnit(academicUnit)
    viewModel.academicUnitForm.name = 'Faculty of Science and Technology'
    await viewModel.saveAcademicUnit()

    expect(request).toHaveBeenCalledWith('/api/academic/units/unit-1', {
      method: 'PUT',
      body: {
        name: 'Faculty of Science and Technology',
        legacyFacultyCode: 'SCI',
        legacyDepartmentCode: null,
        expectedVersion: 2
      }
    })
    expect(viewModel.academicUnitModalOpen).toBe(false)
    expect(toastAdd).toHaveBeenCalledWith(expect.objectContaining({ title: 'Academic unit updated' }))
  })
})
