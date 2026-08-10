export type StudentConversionSummary = {
  id: string
  status: 'PROVISIONING' | 'COMPLETED' | 'FAILED'
  financeProvisioningStatus: 'PENDING' | 'COMPLETED' | 'FAILED'
  portalProvisioningStatus: 'PENDING' | 'COMPLETED' | 'FAILED'
  sourceApplicationId: string
  sourceOfferId: string
  studentId: string
  studentNumber: string
  studentStatus: 'PROVISIONING' | 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN' | 'INACTIVE'
  programmeEnrolmentId: string
  programmeId: string
  programmeVersionId: string
  programmeCode: string
  programmeName: string
  programmeEnrolmentStatus: 'PROVISIONING' | 'ACTIVE' | 'DEFERRED' | 'SUSPENDED' | 'TRANSFERRED' | 'WITHDRAWN' | 'COMPLETED'
  requestedAt: string
  completedAt: string | null
  failureReason: string | null
  retryCount: number
  lastRetryAt: string | null
  lastRetryByUserId: string | null
  lastRetryReason: string | null
}

export type RegistrationStatus = 'DRAFT' | 'SUBMITTED' | 'ACADEMIC_APPROVED' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED'
export type RegistrationType = 'NORMAL' | 'LATE' | 'AMENDMENT'

export type StudentProgrammeEnrolmentSummary = {
  id: string
  programmeId: string
  programmeVersionId: string
  programmeCode: string
  programmeName: string
  intakeId: string
  commencementDate: string
  status: 'PROVISIONING' | 'ACTIVE' | 'DEFERRED' | 'SUSPENDED' | 'TRANSFERRED' | 'WITHDRAWN' | 'COMPLETED'
  statusReason: string
  approvedAt: string | null
}

export type StudentWorkspaceSummary = {
  id: string
  studentNumber: string
  firstName: string
  middleNames: string | null
  lastName: string
  primaryEmail: string
  primaryPhone: string | null
  dateOfBirth: string | null
  genderCode: string | null
  disabilityStatusCode: string | null
  status: 'PROVISIONING' | 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN' | 'INACTIVE'
  activatedAt: string | null
  programmeEnrolments: StudentProgrammeEnrolmentSummary[]
}

export type RegisteredModuleSummary = {
  id: string
  curriculumModuleId: string
  moduleId: string
  moduleCode: string
  moduleName: string
  curriculumModuleType: 'COMPULSORY' | 'ELECTIVE' | 'OPTIONAL'
  creditValue: number
  minimumMarkRequired: number | null
  selectionSource: 'AUTO_COMPULSORY' | 'STUDENT_ELECTIVE' | 'STAFF_ELECTIVE' | 'CARRY' | 'REPEAT'
}

export type RegistrationSummary = {
  id: string
  registrationNumber: string
  studentId: string
  studentNumber: string
  studentName: string
  programmeEnrolmentId: string
  programmeCode: string
  programmeName: string
  academicPeriodId: string
  academicPeriodCode: string
  academicPeriodName: string
  academicPeriodStartsOn: string
  academicPeriodEndsOn: string
  programmePeriodNumber: number
  registrationType: RegistrationType
  status: RegistrationStatus
  statusReason: string
  initiatedAt: string
  submittedAt: string | null
  academicApprovedAt: string | null
  confirmedAt: string | null
  version: number
  totalCredits: number
  modules: RegisteredModuleSummary[]
}
