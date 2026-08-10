export function useAcademicPeriodContext() {
  const academicSetup = useAcademicSetup()
  const selectedAcademicPeriodId = useCookie<string | null>('emhare-academic-period-id', {
    default: () => null,
    maxAge: 60 * 60 * 24 * 365,
    sameSite: 'lax'
  })

  const selectedAcademicPeriod = computed(() => (
    academicSetup.overview.value?.academicPeriods.find(period => period.id === selectedAcademicPeriodId.value) ?? null
  ))
  const selectedAcademicYearId = computed(() => selectedAcademicPeriod.value?.academicYearId ?? null)
  const selectedAcademicPeriodCode = computed(() => selectedAcademicPeriod.value?.code ?? null)

  function selectAcademicPeriod(academicPeriodId: string | null) {
    selectedAcademicPeriodId.value = academicPeriodId
  }

  function matchesAcademicPeriod(record: {
    academicPeriodId?: string | null
    academicPeriodCode?: string | null
    id?: string | null
    code?: string | null
  }) {
    if (!selectedAcademicPeriodId.value) return true
    const academicPeriodId = record.academicPeriodId ?? record.id
    const academicPeriodCode = record.academicPeriodCode ?? record.code
    if (academicPeriodId) return academicPeriodId === selectedAcademicPeriodId.value
    if (academicPeriodCode) return Boolean(selectedAcademicPeriodCode.value)
      && academicPeriodCode === selectedAcademicPeriodCode.value
    return false
  }

  function matchesIntake(intakeId: string | null | undefined) {
    if (!selectedAcademicYearId.value) return true
    return (academicSetup.overview.value?.intakes ?? []).some(intake => (
      intake.id === intakeId && intake.academicYearId === selectedAcademicYearId.value
    ))
  }

  async function ensureIntakes() {
    await ensureAcademicPeriods()
  }

  async function ensureAcademicPeriods() {
    await academicSetup.ensureOverview()
  }

  return {
    selectedAcademicPeriodId,
    selectedAcademicPeriod,
    selectedAcademicYearId,
    selectedAcademicPeriodCode,
    selectAcademicPeriod,
    matchesAcademicPeriod,
    matchesIntake,
    ensureAcademicPeriods,
    ensureIntakes
  }
}
