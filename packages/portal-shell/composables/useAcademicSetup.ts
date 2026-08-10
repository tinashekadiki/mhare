import type { AcademicSetupOverview } from '../types/academic'

function normalizeAcademicSetupOverview(receivedOverview: AcademicSetupOverview): AcademicSetupOverview {
  return {
    ...receivedOverview,
    intakes: (receivedOverview.intakes ?? []).map(intake => ({
      ...intake,
      programmeLevels: Array.isArray(intake.programmeLevels) ? intake.programmeLevels : [],
      specificProgrammes: Array.isArray(intake.specificProgrammes) ? intake.specificProgrammes : [],
      allProgrammesInSelectedLevels: intake.allProgrammesInSelectedLevels === true
    }))
  }
}

export function useAcademicSetup() {
  const api = useEmhareApi()
  const overview = useState<AcademicSetupOverview | null>('academic-setup-overview', () => null)
  const loading = useState<boolean>('academic-setup-loading', () => false)
  const loadError = useState<string>('academic-setup-load-error', () => '')

  async function loadOverview() {
    loading.value = true
    loadError.value = ''
    try {
      const receivedOverview = await api.request<AcademicSetupOverview>('/api/academic/overview')
      overview.value = normalizeAcademicSetupOverview(receivedOverview)
    } catch (error) {
      loadError.value = api.errorMessage(error, 'Academic Setup could not be loaded.')
      throw error
    } finally {
      loading.value = false
    }
  }

  async function ensureOverview() {
    if (!overview.value) {
      await loadOverview()
      return
    }
    overview.value = normalizeAcademicSetupOverview(overview.value)
  }

  return {
    overview,
    loading,
    loadError,
    loadOverview,
    ensureOverview
  }
}
