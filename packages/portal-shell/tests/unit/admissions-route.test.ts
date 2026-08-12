// Author: Tinashe K

import { describe, expect, it } from 'vitest'
import { entryOptionSelectionsByProgramme, routeForApplication } from '../../utils/admissions-route'

describe('route-aware admissions helpers', () => {
  it('selects the exact application type and intake intersection', () => {
    const mba = { applicationTypeId: 'mba', intakeId: 'jan', programmes: [{ id: 'mba-programme' }] }
    const postgraduate = { applicationTypeId: 'postgrad', intakeId: 'jan', programmes: [{ id: 'msc-programme' }] }

    expect(routeForApplication([mba, postgraduate] as never[], 'mba', 'jan')).toBe(mba)
    expect(routeForApplication([mba, postgraduate] as never[], 'mba', 'aug')).toBeNull()
  })

  it('restores ranked entry preferences against their programme choice', () => {
    const selections = entryOptionSelectionsByProgramme({
      application: {
        programmeChoices: [
          { id: 'choice-1', programmeId: 'programme-1' },
          { id: 'choice-2', programmeId: 'programme-2' },
        ],
      },
      programmeEntryPreferences: [
        { programmeChoiceId: 'choice-1', entryOptionId: 'second', preferenceRank: 2 },
        { programmeChoiceId: 'choice-1', entryOptionId: 'first', preferenceRank: 1 },
      ],
    } as never)

    expect(selections).toEqual({ 'programme-1': ['first', 'second'], 'programme-2': [] })
  })
})
