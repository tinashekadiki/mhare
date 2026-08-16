export type ReferenceStatus = 'ACTIVE' | 'INACTIVE'
export type CalendarStatus = 'DRAFT' | 'OPEN' | 'CLOSED' | 'ARCHIVED'
export type AcademicOfferingStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'RETIRED'
export type ProgrammeVersionStatus = 'DRAFT' | 'APPROVED' | 'RETIRED'
export type CurriculumModuleType = 'COMPULSORY' | 'ELECTIVE' | 'OPTIONAL'

export interface AcademicUnitTypeSummary {
  id: string
  code: string
  name: string
  levelOrder: number
  leafAllowed: boolean
  status: ReferenceStatus
  version: number
}

export interface AcademicUnitSummary {
  id: string
  academicUnitTypeId: string
  academicUnitTypeCode: string
  parentId: string | null
  code: string
  name: string
  status: ReferenceStatus
  legacyFacultyCode: string | null
  legacyDepartmentCode: string | null
  version: number
}

export interface AcademicYearSummary {
  id: string
  name: string
  startDate: string
  endDate: string
  status: CalendarStatus
  changeReason: string
  version: number
}

export interface AcademicPeriodTypeSummary {
  id: string
  code: string
  name: string
  sortOrder: number
  status: ReferenceStatus
  changeReason: string
  version: number
}

export interface AcademicPeriodSummary {
  id: string
  academicYearId: string
  academicYearName: string
  academicPeriodTypeId: string
  academicPeriodTypeName: string
  code: string
  name: string
  startDate: string
  endDate: string
  status: CalendarStatus
  changeReason: string
  version: number
}

export interface IntakeSummary {
  id: string
  academicYearId: string
  academicYearName: string
  code: string
  name: string
  startsOn: string
  endsOn: string
  offerAcceptanceDeadline: string | null
  registrationDate: string | null
  orientationDate: string | null
  commencementDate: string | null
  status: CalendarStatus
  maximumProgrammeChoices: number
  changeReason: string
  programmeLevels: IntakeProgrammeLevelSummary[]
  specificProgrammes: IntakeProgrammeSummary[]
  allProgrammesInSelectedLevels: boolean
  version: number
}

export interface IntakeProgrammeLevelSummary {
  id: string
  code: string
  name: string
}

export interface IntakeProgrammeSummary {
  id: string
  code: string
  name: string
  programmeLevelId: string
  programmeLevelName: string
}

export interface ProgrammeLevelSummary {
  id: string
  code: string
  name: string
  sortOrder: number
  status: ReferenceStatus
  version: number
}

export interface ProgrammeTypeSummary {
  id: string
  code: string
  name: string
  status: ReferenceStatus
  version: number
}

export interface ProgrammeSummary {
  id: string
  code: string
  name: string
  awardName: string
  owningAcademicUnitId: string
  owningAcademicUnitName: string
  programmeTypeId: string
  programmeTypeName: string
  programmeLevelId: string
  programmeLevelName: string
  minimumDurationPeriods: number
  maximumDurationPeriods: number
  status: AcademicOfferingStatus
  legacyProgrammeCode: string | null
  changeReason: string
  version: number
}

export interface ProgrammeVersionSummary {
  id: string
  programmeId: string
  programmeCode: string
  versionCode: string
  effectiveFrom: string
  effectiveTo: string | null
  status: ProgrammeVersionStatus
  approvedByUserId: string | null
  approvedAt: string | null
  version: number
  curriculumModuleCount: number
  totalCredits: number
}

export interface AcademicModuleSummary {
  id: string
  code: string
  name: string
  description: string
  owningAcademicUnitId: string
  owningAcademicUnitName: string
  creditValue: number
  academicLevel: number
  status: AcademicOfferingStatus
  legacyCourseCode: string | null
  version: number
}

export interface CurriculumModuleSummary {
  id: string
  programmeVersionId: string
  moduleId: string
  moduleCode: string
  moduleName: string
  periodNumber: number
  moduleType: CurriculumModuleType
  creditValue: number
  minimumMarkRequired: number | null
  sortOrder: number
  version: number
}

export interface CurriculumModuleUsageSummary {
  curriculumModuleId: string
  registrationCount: number
  resultCount: number
  removable: boolean
}

export interface AcademicSetupOverview {
  academicUnitTypes: AcademicUnitTypeSummary[]
  academicUnits: AcademicUnitSummary[]
  academicYears: AcademicYearSummary[]
  academicPeriodTypes: AcademicPeriodTypeSummary[]
  academicPeriods: AcademicPeriodSummary[]
  intakes: IntakeSummary[]
  programmeLevels: ProgrammeLevelSummary[]
  programmeTypes: ProgrammeTypeSummary[]
  programmes: ProgrammeSummary[]
  modules: AcademicModuleSummary[]
}

export interface RegistrationModuleOption {
  curriculumModuleId: string
  moduleId: string
  moduleCode: string
  moduleName: string
  moduleType: 'COMPULSORY' | 'ELECTIVE' | 'OPTIONAL'
  creditValue: number
  minimumMarkRequired: number | null
  sortOrder: number
}

export interface RegistrationCatalogue {
  academicPeriodId: string
  academicPeriodCode: string
  academicPeriodName: string
  academicPeriodStartsOn: string
  academicPeriodEndsOn: string
  programmeVersionId: string
  programmeId: string
  programmeCode: string
  programmeName: string
  programmeVersionCode: string
  periodNumber: number
  modules: RegistrationModuleOption[]
}
