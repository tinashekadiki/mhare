const SEMESTERS_PER_ACADEMIC_YEAR = 2

export function useProgrammeStudyPeriod() {
  function toProgrammePeriodNumber(yearOfStudy: number, semesterNumber: number) {
    const safeYear = Math.max(1, Math.trunc(yearOfStudy))
    const safeSemester = Math.min(
      SEMESTERS_PER_ACADEMIC_YEAR,
      Math.max(1, Math.trunc(semesterNumber))
    )
    return ((safeYear - 1) * SEMESTERS_PER_ACADEMIC_YEAR) + safeSemester
  }

  function fromProgrammePeriodNumber(programmePeriodNumber: number) {
    const safePeriodNumber = Math.max(1, Math.trunc(programmePeriodNumber))
    return {
      yearOfStudy: Math.ceil(safePeriodNumber / SEMESTERS_PER_ACADEMIC_YEAR),
      semesterNumber: ((safePeriodNumber - 1) % SEMESTERS_PER_ACADEMIC_YEAR) + 1
    }
  }

  function studyPeriodLabel(programmePeriodNumber: number) {
    const { yearOfStudy, semesterNumber } = fromProgrammePeriodNumber(programmePeriodNumber)
    return `Year ${yearOfStudy} · Semester ${semesterNumber}`
  }

  function durationYearsFromPeriods(durationPeriods: number) {
    return Number((durationPeriods / SEMESTERS_PER_ACADEMIC_YEAR).toFixed(1))
  }

  function durationPeriodsFromYears(durationYears: number) {
    return Math.max(1, Math.round(durationYears * SEMESTERS_PER_ACADEMIC_YEAR))
  }

  function durationLabel(minimumDurationPeriods: number, maximumDurationPeriods: number) {
    const minimumYears = durationYearsFromPeriods(minimumDurationPeriods)
    const maximumYears = durationYearsFromPeriods(maximumDurationPeriods)
    return minimumYears === maximumYears
      ? `${minimumYears} ${minimumYears === 1 ? 'year' : 'years'}`
      : `${minimumYears}–${maximumYears} years`
  }

  function yearOfStudyItems(maximumDurationPeriods = 16) {
    const maximumYear = Math.max(1, Math.ceil(maximumDurationPeriods / SEMESTERS_PER_ACADEMIC_YEAR))
    return Array.from({ length: maximumYear }, (_, index) => ({
      label: `Year ${index + 1}`,
      value: index + 1
    }))
  }

  const semesterItems = [
    { label: 'Semester 1', value: 1 },
    { label: 'Semester 2', value: 2 }
  ]

  return {
    semestersPerAcademicYear: SEMESTERS_PER_ACADEMIC_YEAR,
    semesterItems,
    toProgrammePeriodNumber,
    fromProgrammePeriodNumber,
    studyPeriodLabel,
    durationYearsFromPeriods,
    durationPeriodsFromYears,
    durationLabel,
    yearOfStudyItems
  }
}
