// Author: Tinashe K

import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  shallowRef,
  watch,
} from 'vue'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

Object.assign(globalThis, {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  shallowRef,
  watch,
})
vi.stubGlobal('definePageMeta', vi.fn())
vi.stubGlobal('useHead', vi.fn())

describe('Core user edit access assignments', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.resetModules()
    vi.stubGlobal('useEmhareAuth', () => ({
      hasPermission: vi.fn(() => true),
      hasRole: vi.fn(() => true),
    }))
    vi.stubGlobal('useEmhareConfirm', () => ({
      confirmAction: vi.fn(),
      showError: vi.fn(),
      showSuccess: vi.fn(),
    }))
  })

  it('loads and persists an edited academic-unit scope from the user form', async () => {
    const originalAcademicUnitId = '11111111-1111-4111-8111-111111111111'
    const reassignedAcademicUnitId = '22222222-2222-4222-8222-222222222222'
    const userId = '33333333-3333-4333-8333-333333333333'
    const roleId = '44444444-4444-4444-8444-444444444444'
    const assignmentId = '55555555-5555-4555-8555-555555555555'
    const systemRoleId = '66666666-6666-4666-8666-666666666666'
    const systemAssignmentId = '77777777-7777-4777-8777-777777777777'
    const request = vi.fn(async (path: string) => {
      if (path === '/api/core/institution-profile') return null
      if (path === '/api/core/countries') return []
      if (path === '/api/core/statistics') {
        return { userCount: 1, roleCount: 1, permissionCount: 1, lookupSetCount: 1 }
      }
      if (path === '/api/core/roles') {
        return [
          {
            id: roleId,
            code: 'ACADEMIC_REVIEWER',
            name: 'Academic Reviewer',
            scope: 'ACADEMIC_UNIT',
            systemManaged: true,
          },
          {
            id: systemRoleId,
            code: 'FINANCE_OFFICER',
            name: 'Finance Officer',
            scope: 'SYSTEM',
            systemManaged: true,
          },
        ]
      }
      if (path === `/api/core/users/${userId}/role-assignments`) {
        return [
          {
            id: assignmentId,
            roleId,
            roleCode: 'ACADEMIC_REVIEWER',
            roleName: 'Academic Reviewer',
            academicUnitId: originalAcademicUnitId,
            startsAt: '2026-08-01T08:00:00Z',
          },
          {
            id: systemAssignmentId,
            roleId: systemRoleId,
            roleCode: 'FINANCE_OFFICER',
            roleName: 'Finance Officer',
            academicUnitId: null,
            startsAt: '2026-08-01T08:00:00Z',
          },
        ]
      }
      if (path === '/api/core/users') return []
      return undefined
    })
    vi.stubGlobal('useEmhareApi', () => ({ request, errorMessage: vi.fn() }))
    vi.stubGlobal('useAcademicSetup', () => ({
      overview: ref({
        academicUnits: [
          { id: originalAcademicUnitId, code: 'SCI', name: 'Science', academicUnitTypeCode: 'FACULTY', status: 'ACTIVE' },
          { id: reassignedAcademicUnitId, code: 'ARTS', name: 'Arts', academicUnitTypeCode: 'FACULTY', status: 'ACTIVE' },
        ],
      }),
      ensureOverview: vi.fn(async () => undefined),
    }))

    const CorePage = (await import('../../pages/operations/core.vue')).default
    const wrapper = shallowMount(CorePage, {
      global: {
        stubs: {
          EmhareRecordDrawer: true,
          EmhareDataTable: true,
          EmhareRegisterPanel: true,
          EmhareKpiCard: true,
          EmhareStatusPill: true,
        },
      },
    })
    await flushPromises()

    const viewModel = wrapper.vm as unknown as {
      editUser: (row: Record<string, unknown>) => Promise<void>
      saveUser: () => Promise<void>
      userAccessDrafts: Array<{ assignmentId?: string; academicUnitId: string }>
    }
    await viewModel.editUser({
      id: userId,
      username: 'academic.reviewer',
      email: 'academic.reviewer@example.test',
      displayName: 'Academic Reviewer',
      status: 'ACTIVE',
    })

    expect(viewModel.userAccessDrafts).toEqual([
      expect.objectContaining({
        assignmentId,
        academicUnitId: originalAcademicUnitId,
      }),
      expect.objectContaining({
        assignmentId: systemAssignmentId,
        academicUnitId: '',
      }),
    ])

    viewModel.userAccessDrafts[0]!.academicUnitId = reassignedAcademicUnitId
    await viewModel.saveUser()

    expect(request).toHaveBeenCalledWith(
      `/api/core/users/${userId}/role-assignments/${assignmentId}/academic-unit`,
      {
        method: 'PUT',
        body: { academicUnitId: reassignedAcademicUnitId },
      },
    )
    const academicUnitUpdatePaths = request.mock.calls
      .map(([path]) => path)
      .filter((path) => path.includes('/academic-unit'))
    expect(academicUnitUpdatePaths).toEqual([
      `/api/core/users/${userId}/role-assignments/${assignmentId}/academic-unit`,
    ])
  })
})
