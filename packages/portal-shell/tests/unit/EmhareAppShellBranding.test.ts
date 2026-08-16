// Author: Tinashe K

import { config, flushPromises, shallowMount } from '@vue/test-utils'
import { computed, defineComponent, h, nextTick, onMounted, ref, watch } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import EmhareAppShell from '../../components/shell/EmhareAppShell.vue'

Object.assign(globalThis, { computed, nextTick, onMounted, ref, watch })
vi.stubGlobal('navigateTo', vi.fn())
vi.stubGlobal('useRoute', () => ({ path: '/operations', fullPath: '/operations' }))
config.global.renderStubDefaultSlot = true

describe('EmhareAppShell institution branding', () => {
  afterEach(() => {
    const propertyNames = Array.from(
      { length: document.documentElement.style.length },
      (_, index) => document.documentElement.style.item(index)
    )
    for (const propertyName of propertyNames) {
      if (propertyName.startsWith('--color-uzgreen-') || propertyName.startsWith('--color-uzgold-')) {
        document.documentElement.style.removeProperty(propertyName)
      }
    }
    vi.unstubAllGlobals()
    Object.assign(globalThis, { computed, nextTick, onMounted, ref, watch })
    vi.stubGlobal('navigateTo', vi.fn())
    vi.stubGlobal('useRoute', () => ({ path: '/operations', fullPath: '/operations' }))
  })

  it('applies and reacts to institution colours from the authenticated profile', async () => {
    const currentUserProfile = ref({
      user: { displayName: 'Operator' },
      roleAssignments: [],
      institutionBrandingJson: '{"primaryColor":"#040345","secondaryColor":"#f8b334"}'
    })
    vi.stubGlobal('useEmhareAuth', () => ({
      authenticated: ref(true),
      currentUserProfile,
      requireUser: vi.fn().mockResolvedValue(currentUserProfile.value),
      loadUser: vi.fn(),
      syncCoreUser: vi.fn()
    }))

    const SlotStub = defineComponent({
      setup(_, { slots }) {
        return () => h('div', [slots.default?.(), slots.header?.(), slots.footer?.()])
      }
    })
    const SidebarStub = defineComponent({
      setup(_, { slots }) {
        return () => h('div', [
          slots.header?.({ collapsed: false }),
          slots.default?.({ collapsed: false }),
          slots.footer?.({ collapsed: false })
        ])
      }
    })

    shallowMount(EmhareAppShell, {
      global: {
        stubs: {
          UDashboardGroup: SlotStub,
          UDashboardSidebar: SidebarStub,
          UDashboardPanel: SlotStub,
          UDashboardNavbar: SlotStub,
          UDashboardPanelContent: SlotStub
        }
      }
    })
    await flushPromises()

    expect(document.documentElement.style.getPropertyValue('--color-uzgreen-600')).toBe('#040345')
    expect(document.documentElement.style.getPropertyValue('--color-uzgold-500')).toBe('#f8b334')

    currentUserProfile.value = {
      ...currentUserProfile.value,
      institutionBrandingJson: '{"primaryColor":"#112266","secondaryColor":"#cc9900"}'
    }
    await nextTick()

    expect(document.documentElement.style.getPropertyValue('--color-uzgreen-600')).toBe('#112266')
    expect(document.documentElement.style.getPropertyValue('--color-uzgold-500')).toBe('#cc9900')
  })

  it('falls back to the governed UZ palette when branding is invalid', async () => {
    const currentUserProfile = ref({
      user: { displayName: 'Operator' },
      roleAssignments: [],
      institutionBrandingJson: 'invalid-json'
    })
    vi.stubGlobal('useEmhareAuth', () => ({
      authenticated: ref(true),
      currentUserProfile,
      requireUser: vi.fn().mockResolvedValue(currentUserProfile.value),
      loadUser: vi.fn(),
      syncCoreUser: vi.fn()
    }))
    const SlotStub = defineComponent({
      setup(_, { slots }) {
        return () => h('div', [slots.default?.(), slots.header?.(), slots.footer?.()])
      }
    })
    const SidebarStub = defineComponent({
      setup(_, { slots }) {
        return () => h('div', [
          slots.header?.({ collapsed: false }),
          slots.default?.({ collapsed: false }),
          slots.footer?.({ collapsed: false })
        ])
      }
    })

    shallowMount(EmhareAppShell, {
      global: {
        stubs: {
          UDashboardGroup: SlotStub,
          UDashboardSidebar: SidebarStub,
          UDashboardPanel: SlotStub,
          UDashboardNavbar: SlotStub,
          UDashboardPanelContent: SlotStub
        }
      }
    })
    await flushPromises()

    expect(document.documentElement.style.getPropertyValue('--color-uzgreen-600')).toBe('#20743a')
    expect(document.documentElement.style.getPropertyValue('--color-uzgold-500')).toBe('#f8b334')

    currentUserProfile.value = {
      ...currentUserProfile.value,
      institutionBrandingJson: '{"primaryColor":"navy","secondaryColor":"gold"}'
    }
    await nextTick()

    expect(document.documentElement.style.getPropertyValue('--color-uzgreen-600')).toBe('#20743a')
    expect(document.documentElement.style.getPropertyValue('--color-uzgold-500')).toBe('#f8b334')
  })
})
