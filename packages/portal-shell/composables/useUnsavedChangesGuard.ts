export function useUnsavedChangesGuard(isDirty: Ref<boolean> | ComputedRef<boolean>) {
  const confirm = useEmhareConfirm()

  async function confirmLeave() {
    if (!isDirty.value) {
      return true
    }
    return confirm.confirmAction({
      title: 'Discard unsaved changes?',
      text: 'Your current edits have not been saved.',
      confirmButtonText: 'Discard changes',
      destructive: true
    })
  }

  onBeforeRouteLeave(async () => confirmLeave())

  if (import.meta.client) {
    window.addEventListener('beforeunload', (event) => {
      if (!isDirty.value) {
        return
      }
      event.preventDefault()
      event.returnValue = ''
    })
  }

  return { confirmLeave }
}

