// Author: Tinashe K

import type { ApplicationRouteOption, ApplicantApplicationWorkspace } from '../types/admissions'

export function routeForApplication(
  routes: ApplicationRouteOption[],
  applicationTypeId: string | undefined,
  intakeId: string | undefined,
): ApplicationRouteOption | null {
  if (!applicationTypeId || !intakeId) return null
  return routes.find(route => route.applicationTypeId === applicationTypeId && route.intakeId === intakeId) ?? null
}

export function entryOptionSelectionsByProgramme(
  workspace: Pick<ApplicantApplicationWorkspace, 'application' | 'programmeEntryPreferences'>,
): Record<string, string[]> {
  return Object.fromEntries(workspace.application.programmeChoices.map(choice => [
    choice.programmeId,
    workspace.programmeEntryPreferences
      .filter(preference => preference.programmeChoiceId === choice.id)
      .sort((left, right) => left.preferenceRank - right.preferenceRank)
      .map(preference => preference.entryOptionId),
  ]))
}
