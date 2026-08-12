// Author: Tinashe K

import { describe, expect, it } from 'vitest'
import { useProgrammeStudyPeriod } from '../../composables/useProgrammeStudyPeriod'

describe('useProgrammeStudyPeriod', () => {
  const studyPeriod = useProgrammeStudyPeriod()

  it('converts a study year and semester to the curriculum period number', () => {
    expect(studyPeriod.toProgrammePeriodNumber(3, 2)).toBe(6)
    expect(studyPeriod.toProgrammePeriodNumber(0, 8)).toBe(2)
  })

  it('converts a curriculum period number back to year and semester', () => {
    expect(studyPeriod.fromProgrammePeriodNumber(5)).toEqual({
      yearOfStudy: 3,
      semesterNumber: 1
    })
    expect(studyPeriod.fromProgrammePeriodNumber(0)).toEqual({
      yearOfStudy: 1,
      semesterNumber: 1
    })
  })

  it('formats single and ranged programme durations', () => {
    expect(studyPeriod.durationLabel(8, 8)).toBe('4 years')
    expect(studyPeriod.durationLabel(2, 4)).toBe('1–2 years')
  })

  it('builds year options from the maximum programme duration', () => {
    expect(studyPeriod.yearOfStudyItems(5)).toEqual([
      { label: 'Year 1', value: 1 },
      { label: 'Year 2', value: 2 },
      { label: 'Year 3', value: 3 }
    ])
  })
})
