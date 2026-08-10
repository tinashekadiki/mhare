<script setup lang="ts">
defineOptions({ inheritAttrs: false })

const props = withDefaults(defineProps<{
  guidanceTitle?: string
  guidanceDescription?: string
  guidanceInstructions?: string[]
  guidanceActionLabel?: string
}>(), {
  guidanceTitle: 'Action unavailable',
  guidanceDescription: undefined,
  guidanceInstructions: () => [],
  guidanceActionLabel: undefined
})

const emit = defineEmits<{
  click: [event: MouseEvent]
  guidanceAction: []
}>()

const { showActionGuidance } = useEmhareConfirm()

async function handleClick(event: MouseEvent) {
  if (!props.guidanceInstructions.length) {
    emit('click', event)
    return
  }

  event.preventDefault()
  event.stopPropagation()
  const confirmed = await showActionGuidance({
    title: props.guidanceTitle,
    description: props.guidanceDescription,
    instructions: props.guidanceInstructions,
    actionLabel: props.guidanceActionLabel
  })

  if (confirmed && props.guidanceActionLabel) emit('guidanceAction')
}
</script>

<template>
  <UButton v-bind="$attrs" @click="handleClick" />
</template>
