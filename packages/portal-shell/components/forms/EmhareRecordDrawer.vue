<script setup lang="ts">
const props = withDefaults(defineProps<{
  open: boolean
  title: string
  description?: string
  submitLabel?: string
  submitIcon?: string
  busy?: boolean
  submitDisabled?: boolean
  submitDisabledReason?: string
  showBack?: boolean
  backLabel?: string
  width?: 'md' | 'lg' | 'xl' | 'wide'
  presentation?: 'sidepanel' | 'page'
}>(), {
  description: undefined,
  submitLabel: 'Save',
  submitIcon: 'i-lucide-save',
  busy: false,
  submitDisabled: false,
  submitDisabledReason: 'Complete all required fields before continuing.',
  showBack: false,
  backLabel: 'Back',
  width: 'lg',
  presentation: 'sidepanel'
})

const emit = defineEmits<{
  'update:open': [open: boolean]
  submit: []
  back: []
  close: []
}>()

const drawerUi = computed(() => ({
  content: {
    md: 'w-screen max-w-full sm:w-[30rem] sm:max-w-[calc(100vw-2rem)]',
    lg: 'w-screen max-w-full sm:w-[38rem] sm:max-w-[calc(100vw-2rem)]',
    xl: 'w-screen max-w-full sm:w-[52rem] sm:max-w-[calc(100vw-2rem)]',
    wide: 'w-screen max-w-full sm:w-[56rem] lg:w-[64rem] sm:max-w-[calc(100vw-2rem)]'
  }[props.width],
  header: 'border-b border-muted bg-elevated/60 px-5 py-4 sm:px-6',
  body: 'flex-1 min-w-0 overflow-y-auto px-5 py-5 sm:px-6',
  footer: 'border-t border-muted bg-default px-5 py-4 sm:px-6'
}))

const workspaceTitleId = useId()
const workspaceDescriptionId = useId()
const workspaceElement = ref<HTMLElement | null>(null)
let elementFocusedBeforeWorkspace: HTMLElement | null = null

function setRouteContentInactive(inactive: boolean) {
  const routeContent = document.getElementById('emhare-route-content')
  if (!routeContent) return

  if (inactive) {
    routeContent.classList.add('invisible')
    routeContent.setAttribute('aria-hidden', 'true')
  } else {
    routeContent.classList.remove('invisible')
    routeContent.removeAttribute('aria-hidden')
  }
}

watch(
  () => props.open,
  async (open) => {
    if (props.presentation !== 'page') return

    if (open) {
      setRouteContentInactive(true)
      elementFocusedBeforeWorkspace = document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null
      await nextTick()
      workspaceElement.value?.querySelector<HTMLElement>('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])')?.focus()
      return
    }

    setRouteContentInactive(false)
    if (elementFocusedBeforeWorkspace?.isConnected) {
      elementFocusedBeforeWorkspace.focus()
    }
    elementFocusedBeforeWorkspace = null
  }
)

onBeforeUnmount(() => {
  if (props.presentation === 'page' && props.open) {
    setRouteContentInactive(false)
  }
})

function updateOpen(open: boolean) {
  emit('update:open', open)
  if (!open) {
    emit('close')
  }
}
</script>

<template>
  <USlideover
    v-if="presentation === 'sidepanel'"
    :open="open"
    side="right"
    :title="title"
    :description="description"
    :dismissible="false"
    :close="!busy"
    :ui="drawerUi"
    @update:open="updateOpen"
  >
    <template #body>
      <div class="min-w-0 space-y-5">
        <slot name="body">
          <slot />
        </slot>
      </div>
    </template>

    <template #footer>
      <slot name="footer">
        <div class="flex w-full items-center justify-between gap-3">
          <UButton
            v-if="showBack"
            :label="backLabel"
            icon="i-lucide-arrow-left"
            color="neutral"
            variant="ghost"
            :disabled="busy"
            @click="emit('back')"
          />
          <span v-else aria-hidden="true" />
          <div class="flex items-center justify-end gap-3">
            <UButton
              label="Cancel"
              color="neutral"
              variant="ghost"
              :disabled="busy"
              @click="updateOpen(false)"
            />
            <EmhareGuidedActionButton
              :icon="submitIcon"
              :label="submitLabel"
              color="primary"
              :loading="busy"
              :disabled="busy"
              guidance-title="Required information is incomplete"
              :guidance-instructions="submitDisabled && !busy ? [submitDisabledReason] : []"
              @click="emit('submit')"
            />
          </div>
        </div>
      </slot>
    </template>
  </USlideover>

  <Teleport
    v-else-if="open"
    defer
    to="#emhare-main-workspace"
  >
    <section
      ref="workspaceElement"
      data-emhare-form-presentation="page"
      class="pointer-events-auto flex h-full min-h-0 w-full flex-col bg-default"
      role="region"
      :aria-labelledby="workspaceTitleId"
      :aria-describedby="description ? workspaceDescriptionId : undefined"
    >
      <header class="shrink-0 border-b border-muted bg-elevated/60">
        <UContainer class="flex w-full max-w-6xl items-start gap-4 py-4 sm:items-center sm:py-5">
          <UButton
            :label="showBack ? backLabel : 'Back'"
            icon="i-lucide-arrow-left"
            color="neutral"
            variant="ghost"
            :disabled="busy"
            class="min-h-11 shrink-0"
            @click="showBack ? emit('back') : updateOpen(false)"
          />
          <div class="min-w-0 flex-1">
            <p class="text-xs font-semibold uppercase tracking-[0.16em] text-primary">
              Form workspace
            </p>
            <h1 :id="workspaceTitleId" class="mt-1 text-xl font-semibold text-highlighted sm:text-2xl">
              {{ title }}
            </h1>
            <p v-if="description" :id="workspaceDescriptionId" class="mt-1 max-w-3xl text-sm text-muted">
              {{ description }}
            </p>
          </div>
        </UContainer>
      </header>

      <main class="min-h-0 flex-1 overflow-y-auto">
        <UContainer class="w-full max-w-6xl py-6 sm:py-8">
          <div class="min-w-0 space-y-6">
            <slot name="body">
              <slot />
            </slot>
          </div>
        </UContainer>
      </main>

      <footer class="shrink-0 border-t border-muted bg-default">
        <UContainer class="w-full max-w-6xl py-4">
          <slot name="footer">
            <div class="flex w-full flex-col-reverse gap-3 sm:flex-row sm:items-center sm:justify-between">
              <UButton
                v-if="showBack"
                :label="backLabel"
                icon="i-lucide-arrow-left"
                color="neutral"
                variant="ghost"
                :disabled="busy"
                class="min-h-11"
                @click="emit('back')"
              />
              <span v-else aria-hidden="true" />
              <div class="flex flex-col-reverse gap-3 sm:flex-row sm:items-center sm:justify-end">
                <UButton
                  label="Cancel"
                  color="neutral"
                  variant="outline"
                  :disabled="busy"
                  class="min-h-11"
                  @click="updateOpen(false)"
                />
                <EmhareGuidedActionButton
                  :icon="submitIcon"
                  :label="submitLabel"
                  color="primary"
                  :loading="busy"
                  :disabled="busy"
                  class="min-h-11"
                  guidance-title="Required information is incomplete"
                  :guidance-instructions="submitDisabled && !busy ? [submitDisabledReason] : []"
                  @click="emit('submit')"
                />
              </div>
            </div>
          </slot>
        </UContainer>
      </footer>
    </section>
  </Teleport>
</template>
