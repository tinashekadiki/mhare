export type OfferingStatus = 'DRAFT' | 'ACTIVE' | 'CLOSED'
export type SchemeStatus = 'DRAFT' | 'APPROVED' | 'SUPERSEDED'
export type ComponentType = 'COURSEWORK' | 'PRACTICAL' | 'IN_CLASS_TEST' | 'FINAL_EXAM' | 'OTHER'
export type MarkStatus = 'CAPTURED' | 'SUBMITTED' | 'SUPERSEDED'
export type AmendmentStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED'

export type AssessmentComponentSummary = {
  id: string
  code: string
  name: string
  componentType: ComponentType
  weightPercent: number
  maximumMark: number
  captureOpensAt: string
  captureClosesAt: string
  sortOrder: number
}

export type AssessmentSchemeSummary = {
  id: string
  schemeVersion: number
  name: string
  status: SchemeStatus
  approvalReason: string | null
  approvedByUserId: string | null
  approvedAt: string | null
  version: number
  components: AssessmentComponentSummary[]
}

export type AssessmentOfferingSummary = {
  id: string
  moduleId: string
  moduleCode: string
  moduleName: string
  academicPeriodId: string
  academicPeriodCode: string
  academicPeriodName: string
  assignedInstructorUserId: string
  status: OfferingStatus
  version: number
  rosterCount: number
  schemes: AssessmentSchemeSummary[]
}

export type AssessmentRosterSource = {
  moduleId: string
  moduleCode: string
  moduleName: string
  academicPeriodId: string
  academicPeriodCode: string
  academicPeriodName: string
  eligibleStudentCount: number
  offeringCreated: boolean
}

export type AssessmentRosterMark = {
  rosterEntryId: string
  studentId: string
  studentNumber: string
  studentName: string
  componentId: string
  componentCode: string
  markId: string | null
  revisionNumber: number | null
  score: number | null
  status: MarkStatus | null
  markVersion: number
}

export type MarkAmendmentSummary = {
  id: string
  originalMarkId: string
  originalScore: number
  proposedScore: number
  reason: string
  status: AmendmentStatus
  requestedByUserId: string
  requestedAt: string
  decidedByUserId: string | null
  decidedAt: string | null
  decisionReason: string | null
  replacementMarkId: string | null
  version: number
}

export type AssessmentCalculationRun = {
  id: string
  offeringId: string
  schemeId: string
  rosterCount: number
  completeResultCount: number
  incompleteResultCount: number
  status: 'RUNNING' | 'COMPLETED' | 'FAILED'
  initiatedAt: string
  publicationEvidenceAvailable: boolean
  outcomes: Array<{
    rosterEntryId: string
    studentNumber: string
    weightedTotal: number | null
    complete: boolean
    missingComponentCodes: string | null
  }>
}

export type GradingSchemeSummary = {
  id: string
  code: string
  name: string
  schemeVersion: number
  status: 'DRAFT' | 'APPROVED' | 'SUPERSEDED'
  version: number
  bands: Array<{ id: string, minimumMark: number, maximumMark: number, grade: string, remark: string, passing: boolean }>
}

export type ResultBatchSummary = {
  id: string
  calculationRunId: string
  batchNumber: string
  moduleCode: string
  moduleName: string
  academicPeriodCode: string
  status: 'DRAFT' | 'SUBMITTED' | 'MODERATED' | 'APPROVED' | 'PUBLISHED' | 'REJECTED'
  statusReason: string
  version: number
  resultCount: number
  submittedByUserId: string | null
  submittedAt: string | null
  moderatedByUserId: string | null
  moderatedAt: string | null
  approvedByUserId: string | null
  approvedAt: string | null
  publishedByUserId: string | null
  publishedAt: string | null
  results: Array<{ id: string, studentNumber: string, courseworkMark: number, examinationMark: number, finalMark: number, grade: string, remark: string, status: 'PASS' | 'FAIL' }>
}

export type PublishedResultSummary = {
  id: string
  resultBatchId: string
  moduleResultId: string
  studentId: string
  studentNumber: string
  moduleId: string
  moduleCode: string
  moduleName: string
  academicPeriodId: string
  academicPeriodCode: string
  finalMark: number
  grade: string
  remark: string
  publicationVersion: number
  supersedesPublishedResultId: string | null
  resultAmendmentId: string | null
  publishedByUserId: string
  publishedAt: string
}

export type PublishedResultPage = {
  content: PublishedResultSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type ResultCorrectionSource = {
  moduleResultId: string
  resultBatchId: string
  batchNumber: string
  courseworkMark: number
  examinationMark: number
  finalMark: number
  grade: string
  remark: string
  approvedAt: string
}

export type PublishedResultAmendmentSummary = {
  id: string
  amendmentNumber: string
  originalPublishedResultId: string
  originalPublicationVersion: number
  replacementResultBatchId: string
  replacementModuleResultId: string
  studentNumber: string
  moduleCode: string
  moduleName: string
  academicPeriodCode: string
  originalFinalMark: number
  originalGrade: string
  originalRemark: string
  proposedFinalMark: number
  proposedGrade: string
  proposedRemark: string
  requestReason: string
  status: 'REQUESTED' | 'REVIEWED' | 'APPROVED' | 'APPLIED' | 'REJECTED'
  version: number
  requestedByUserId: string
  requestedAt: string
  reviewedByUserId: string | null
  reviewedAt: string | null
  reviewReason: string | null
  approvedByUserId: string | null
  approvedAt: string | null
  approvalReason: string | null
  appliedByUserId: string | null
  appliedAt: string | null
  rejectedByUserId: string | null
  rejectedAt: string | null
  rejectionReason: string | null
}

export type ProgressionDecisionCode = 'PROCEED' | 'PROCEED_WITH_CARRY' | 'REPEAT' | 'EXCLUDE'

export type ProgressionOutcomeSummary = {
  id: string
  priority: number
  decisionCode: ProgressionDecisionCode
  decisionLabel: string
  minimumWeightedAverage: number | null
  minimumPassedCredits: number | null
  maximumFailedCredits: number | null
  maximumFailedModules: number | null
  requireAllCompulsoryPassed: boolean
  nextProgrammePeriodNumber: number | null
  fallbackOutcome: boolean
}

export type ProgressionRuleSetSummary = {
  id: string
  ruleCode: string
  ruleName: string
  programmeId: string
  programmeVersionId: string
  programmePeriodNumber: number
  ruleVersion: number
  status: 'DRAFT' | 'APPROVED' | 'SUPERSEDED'
  version: number
  approvedByUserId: string | null
  approvedAt: string | null
  outcomes: ProgressionOutcomeSummary[]
}

export type ProgressionRosterSummary = {
  id: string
  studentId: string
  studentNumber: string
  programmeId: string
  programmeVersionId: string
  academicPeriodCode: string
  programmePeriodNumber: number
  eligibleModules: number
  publishedModules: number
  readyForProgression: boolean
}

export type ProgressionDecisionSummary = {
  id: string
  decisionNumber: string
  decisionVersion: number
  supersedesDecisionId: string | null
  progressionRuleSetId: string
  progressionRuleCode: string
  registrationRosterImportId: string
  studentId: string
  studentNumber: string
  programmeId: string
  programmeVersionId: string
  academicPeriodCode: string
  programmePeriodNumber: number
  decisionCode: ProgressionDecisionCode
  decisionLabel: string
  nextProgrammePeriodNumber: number | null
  attemptedCredits: number
  passedCredits: number
  failedCredits: number
  failedModules: number
  failedCompulsoryModules: number
  weightedAverage: number
  status: 'CALCULATED' | 'REVIEWED' | 'APPROVED' | 'PUBLISHED' | 'REJECTED'
  statusReason: string
  version: number
  calculatedByUserId: string
  calculatedAt: string
  reviewedByUserId: string | null
  reviewedAt: string | null
  approvedByUserId: string | null
  approvedAt: string | null
  publishedByUserId: string | null
  publishedAt: string | null
  results: Array<{
    publishedResultId: string
    moduleCode: string
    moduleName: string
    curriculumModuleType: 'COMPULSORY' | 'ELECTIVE' | 'OPTIONAL'
    creditValue: number
    finalMark: number
    grade: string
    remark: string
    passing: boolean
    publicationVersion: number
  }>
}
