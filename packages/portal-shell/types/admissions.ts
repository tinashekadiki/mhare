export type AdmissionsPaymentSummary = {
  financePaymentReferenceId: string;
  reference: string;
  amountDue: number;
  currencyCode: string;
  baseCurrencyCode: string;
  baseAmountDue: number | null;
  ratingStatus: "RATED" | "UNRATED";
  status: "PENDING" | "PAID" | "CANCELLED" | "EXPIRED";
  requiredForSubmission: boolean;
  workflowCleared: boolean;
  paidAt: string | null;
};

export type AdmissionsApplicationSummary = {
  id: string;
  applicationNumber: string;
  applicantNumber: string;
  applicantName: string;
  intakeId: string;
  intakeCode: string;
  applicationTypeId: string;
  applicationTypeName: string;
  status: string;
  paymentRequired: boolean;
  paymentClearanceStatus:
    "NOT_REQUIRED" | "PENDING" | "UNRATED" | "PAID" | "WAIVED";
  paymentWaiverReason: string | null;
  canSubmit: boolean;
  canEnterReview: boolean;
  calculatedTotalPoints: number | null;
  pointsCalculatedAt: string | null;
  admissionsClearanceStatus: "NOT_CONFIRMED" | "CONFIRMED";
  confirmedByUserId: string | null;
  confirmedAt: string | null;
  confirmationReason: string | null;
  payment: AdmissionsPaymentSummary | null;
  programmeChoices: AdmissionsProgrammeChoiceSummary[];
};

export type ApplicantRegisterRow = {
  id: string;
  applicantNumber: string;
  displayName: string;
  applicantCategoryCode: "LOCAL" | "SADC" | "INTERNATIONAL" | "CLE";
  primaryEmail: string;
  primaryPhone: string | null;
  profileCompletenessPercentage: number;
  applicationCount: number;
  latestApplicationNumber: string | null;
  latestApplicationStatus: string | null;
  latestIntakeCode: string | null;
  updatedAt: string;
  version: number;
};

export type ApplicantRegisterPage = {
  content: ApplicantRegisterRow[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type ApplicantProfile = {
  id: string;
  userId: string;
  applicantNumber: string;
  applicantCategoryCode: "LOCAL" | "SADC" | "INTERNATIONAL" | "CLE";
  titleCode: string | null;
  firstName: string;
  middleNames: string | null;
  lastName: string;
  dateOfBirth: string | null;
  genderCode: string | null;
  maritalStatusCode: string | null;
  nationalIdNumber: string | null;
  passportNumber: string | null;
  countryId: string | null;
  nationalityCountryId: string | null;
  placeOfBirth: string | null;
  disabilityStatusCode: string | null;
  specialNeeds: string | null;
  sponsorTypeCode: string | null;
  primaryEmail: string;
  primaryPhone: string | null;
  postalAddress: string | null;
  residentialAddress: string | null;
  completenessPercentage: number;
  missingRequiredFields: string[];
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type ApplicantDetails = {
  profile: ApplicantProfile;
  applications: AdmissionsApplicationSummary[];
};

export type AdmissionsProgrammeChoiceSummary = {
  id: string;
  programmeId: string;
  programmeVersionId: string;
  programmeCode: string;
  programmeName: string;
  awardName: string;
  owningAcademicUnitName: string;
  programmeVersionCode: string;
  choiceRank: number;
  choiceStatus: string;
  evaluationSummary: string | null;
  decisionReason: string | null;
};

export type SelectionRoundSummary = {
  id: string;
  intakeId: string;
  intakeCode: string;
  code: string;
  name: string;
  status: "DRAFT" | "OPEN" | "APPROVED" | "CLOSED";
  openedAt: string | null;
  approvedAt: string | null;
  closedAt: string | null;
};

export type AdmissionRequirementSetSummary = {
  id: string;
  programmeId: string;
  applicationTypeId: string;
  intakeId: string | null;
  versionCode: string;
  effectiveFrom: string;
  effectiveTo: string | null;
  status: "DRAFT" | "APPROVED" | "RETIRED";
  minimumTotalPoints: number | null;
  requiresEnglish: boolean;
  requiresMathematicsOrScience: boolean;
  advancedRulesVersion: string | null;
  approvedAt: string | null;
};

export type SelectionDecisionSummary = {
  id: string;
  selectionRoundId: string;
  programmeChoiceId: string;
  applicationNumber: string;
  programmeCode: string;
  programmeName: string;
  decision: "SHORTLIST" | "SELECT" | "REJECT" | "WAITLIST";
  rankPosition: number | null;
  quotaTypeCode: string | null;
  reason: string;
  decidedByUserId: string;
  decidedAt: string;
};

export type AcademicUnitRecommendationSummary = {
  id: string;
  assignmentId: string;
  sequence: number;
  recommendation: "SHORTLIST" | "SELECT" | "REJECT" | "WAITLIST";
  rankPosition: number | null;
  quotaTypeCode: string | null;
  reason: string;
  recommendedByUserId: string;
  recommendedAt: string;
  reviewStatus: "PENDING" | "APPROVED" | "RETURNED" | "OVERRIDDEN";
  reviewedByUserId: string | null;
  reviewedAt: string | null;
  reviewReason: string | null;
  finalDecision: "SHORTLIST" | "SELECT" | "REJECT" | "WAITLIST" | null;
  version: number;
};

export type AcademicReviewSummary = {
  id: string;
  selectionRoundId: string;
  applicationId: string;
  applicationNumber: string;
  applicantNumber: string;
  applicantName: string;
  programmeChoiceId: string;
  programmeCode: string;
  programmeName: string;
  choiceRank: number;
  owningAcademicUnitId: string;
  owningAcademicUnitCode: string;
  owningAcademicUnitName: string;
  recommendationAcademicUnitId: string;
  recommendationAcademicUnitCode: string;
  recommendationAcademicUnitName: string;
  hierarchyPathJson: string;
  status:
    "OPEN" | "CLAIMED" | "RECOMMENDED" | "RETURNED" | "COMPLETED" | "CANCELLED";
  releaseAttempt: number;
  releasedByUserId: string;
  releasedAt: string;
  dueAt: string | null;
  claimedByUserId: string | null;
  claimedAt: string | null;
  version: number;
  latestRecommendation: AcademicUnitRecommendationSummary | null;
};

export type AcademicReviewBatchPreview = {
  totalApplicants: number;
  totalEligibleApplicants: number;
  programmes: Array<{
    programmeId: string;
    programmeCode: string;
    programmeName: string;
    owningAcademicUnitId: string;
    owningAcademicUnitName: string;
    applicantCount: number;
    eligibleApplicantCount: number;
  }>;
  academicUnits: Array<{
    academicUnitId: string;
    academicUnitTypeCode: string;
    academicUnitCode: string;
    academicUnitName: string;
    applicantCount: number;
    eligibleApplicantCount: number;
  }>;
};

export type OfferBatchSummary = {
  id: string;
  intakeId: string;
  selectionRoundId: string;
  code: string;
  name: string;
  scopeType: "INSTITUTION" | "ACADEMIC_UNIT" | "PROGRAMME";
  scopeId: string | null;
  status: "DRAFT" | "APPROVED" | "DISPATCHED" | "CLOSED";
  approvedAt: string | null;
  dispatchedAt: string | null;
  closedAt: string | null;
};

export type AdmissionOfferSummary = {
  id: string;
  offerBatchId: string;
  offerNumber: string;
  applicationId: string;
  applicationNumber: string;
  applicantNumber: string;
  applicantName: string;
  programmeChoiceId: string;
  programmeId: string;
  programmeVersionId: string;
  programmeCode: string;
  programmeName: string;
  intakeId: string;
  offerType: "FIRM" | "CONDITIONAL";
  status:
    | "DRAFT"
    | "APPROVED"
    | "SENT"
    | "ACCEPTED"
    | "DECLINED"
    | "EXPIRED"
    | "WITHDRAWN"
    | "CONVERTED";
  conditionsText: string | null;
  acceptanceDeadline: string;
  registrationDate: string | null;
  orientationDate: string | null;
  commencementDate: string;
  generatedDocumentId: string | null;
  approvedAt: string | null;
  sentAt: string | null;
  expiredAt: string | null;
  expiryReason: string | null;
  conversionRequestedAt: string | null;
  conversionRequestId: string | null;
  convertedStudentId: string | null;
  convertedStudentNumber: string | null;
  convertedAt: string | null;
  conditions: Array<{
    id: string;
    code: string;
    description: string;
    required: boolean;
    status: "PENDING" | "SATISFIED" | "WAIVED";
    resolvedByUserId: string | null;
    resolvedAt: string | null;
    resolutionNotes: string | null;
  }>;
  response: {
    response: "ACCEPTED" | "DECLINED";
    respondedAt: string;
    notes: string | null;
  } | null;
};

export type AdmissionsWorkflowApplicantListItem = {
  id: string;
  applicationNumber: string;
  applicantNumber?: string | null;
  applicantName?: string | null;
  programmeLabel?: string | null;
  detail?: string | null;
  statusLabel: string;
  statusTone: "neutral" | "primary" | "success" | "warning" | "error" | "info";
  href?: string | null;
};

export type AdmissionsWorkflowBatchView = {
  id: string;
  code: string;
  title: string;
  subtitle?: string | null;
  stageLabel: string;
  statusLabel: string;
  statusTone: "neutral" | "primary" | "success" | "warning" | "error" | "info";
  applicants: AdmissionsWorkflowApplicantListItem[];
};

export type ApplicantCategoryOption = {
  code: string;
  label: string;
};

export type AdmissionsApplicationTypeSummary = {
  id: string;
  code: string;
  name: string;
  requiresEmploymentHistory: boolean;
  requiresReferees: boolean;
  financeFeeStructureId: string | null;
  financeFeeStructureCode: string | null;
  financeFeeStructureName: string | null;
  active: boolean;
  version: number;
};

export type AdmissionIntakeOption = {
  id: string;
  code: string;
  name: string;
  startsOn: string;
  endsOn: string;
  maximumProgrammeChoices: number;
  programmes: ProgrammeOption[];
};

export type ProgrammeOption = {
  id: string;
  programmeVersionId: string;
  code: string;
  name: string;
  awardName: string;
  owningAcademicUnitName: string;
  programmeVersionCode: string;
};

export type ApplicationTypeOption = {
  id: string;
  code: string;
  name: string;
  requiresEmploymentHistory: boolean;
  requiresReferees: boolean;
  fee: {
    required: boolean;
    amount: number | null;
    currencyCode: string | null;
  };
  sections: ApplicationSectionOption[];
};

export type ApplicationSectionOption = {
  code: string;
  name: string;
  required: boolean;
  repeatable: boolean;
  minimumRecords: number;
  sortOrder: number;
};

export type ApplicationStartOptions = {
  applicantCategoryCode: string;
  applicantCategories: ApplicantCategoryOption[];
  intakes: AdmissionIntakeOption[];
  applicationTypes: ApplicationTypeOption[];
};

export type ApplicationDocumentState =
  "MISSING" | "PENDING" | "VERIFIED" | "REJECTED";

export type ApplicationDocumentRequirementState = {
  requirementCode: string;
  requirementName: string;
  required: boolean;
  state: ApplicationDocumentState;
  applicationDocumentId: string | null;
  documentId: string | null;
  fileName: string | null;
  mimeType: string | null;
  checksumSha256: string | null;
  linkedAt: string | null;
  verifiedByUserId: string | null;
  verifiedAt: string | null;
  rejectionReason: string | null;
  documentVersion: number;
  version: number;
};

export type ApplicationDocumentRegister = {
  applicationId: string;
  applicationNumber: string;
  requiredDocumentsUploaded: boolean;
  requiredDocumentsVerified: boolean;
  missingRequirementCodes: string[];
  pendingRequirementCodes: string[];
  rejectedRequirementCodes: string[];
  requirements: ApplicationDocumentRequirementState[];
};

export type AcademicUnitApplicationDocumentEntry = {
  applicationId: string;
  applicationNumber: string;
  applicantName: string;
  applicationStatus: string;
  documents: ApplicationDocumentRegister;
};

export type ApplicationSectionStatus =
  | "NOT_STARTED"
  | "IN_PROGRESS"
  | "COMPLETE"
  | "VERIFIED"
  | "REJECTED"
  | "CORRECTION_REQUIRED";

export type ApplicationWorkspaceSection = {
  id: string;
  code: string;
  name: string;
  required: boolean;
  repeatable: boolean;
  minimumRecords: number;
  sortOrder: number;
  status: ApplicationSectionStatus;
  completedAt: string | null;
  completionSummary: string | null;
  version: number;
};

export type ApplicantNextOfKin = {
  id: string;
  fullName: string;
  relationshipCode: string;
  phoneNumber: string;
  email: string | null;
  address: string | null;
  primary: boolean;
  version: number;
};

export type ApplicantEmploymentHistory = {
  id: string;
  employerName: string;
  positionTitle: string;
  startedOn: string;
  endedOn: string | null;
  current: boolean;
  responsibilities: string | null;
  version: number;
};

export type ApplicantReferee = {
  id: string;
  fullName: string;
  title: string | null;
  organisation: string;
  positionTitle: string | null;
  email: string;
  phoneNumber: string | null;
  verificationStatus: "PENDING" | "VERIFIED" | "REJECTED";
  referenceDocumentId: string | null;
  rejectionReason: string | null;
  invitationStatus:
    "NOT_SENT" | "SENT" | "OPENED" | "SUBMITTED" | "REVOKED" | "EXPIRED";
  invitedAt: string | null;
  referenceSubmittedAt: string | null;
  version: number;
};

export type AdmissionsReferenceOption = {
  id: string;
  code: string;
  name: string;
  scienceSubject: boolean | null;
};

export type ApplicantQualificationResult = {
  id: string;
  subject: AdmissionsReferenceOption | null;
  subjectNameSnapshot: string;
  grade: string;
  mark: number | null;
  points: number | null;
  principalSubject: boolean | null;
  resultStatus: "CAPTURED" | "VERIFIED" | "REJECTED";
  version: number;
};

export type ApplicantQualificationSitting = {
  id: string;
  level: string;
  examBody: AdmissionsReferenceOption | null;
  institutionName: string | null;
  centreNumber: string | null;
  candidateNumber: string | null;
  yearWritten: number | null;
  countryId: string | null;
  documentId: string | null;
  verificationStatus: "CAPTURED" | "VERIFIED" | "REJECTED";
  verifiedByUserId: string | null;
  verifiedAt: string | null;
  rejectionReason: string | null;
  results: ApplicantQualificationResult[];
  version: number;
};

export type QualificationReferenceData = {
  examBodies: AdmissionsReferenceOption[];
  oLevelSubjects: AdmissionsReferenceOption[];
  aLevelSubjects: AdmissionsReferenceOption[];
  otherSubjects: AdmissionsReferenceOption[];
};

export type ApplicantApplicationWorkspace = {
  application: AdmissionsApplicationSummary;
  profile: ApplicantProfile;
  sections: ApplicationWorkspaceSection[];
  nextOfKin: ApplicantNextOfKin[];
  employmentHistory: ApplicantEmploymentHistory[];
  referees: ApplicantReferee[];
  qualifications: ApplicantQualificationSitting[];
  documents: ApplicationDocumentRegister;
  readyForSubmission: boolean;
  missingRequirements: string[];
  declarationAcceptedAt: string | null;
  declarationVersion: string | null;
  workflowProgress: AdmissionsApplicationWorkflowProgress;
};

export type AdmissionsApplicationWorkflowProgress = {
  currentStageCode: "CONFIRM" | "RELEASE" | "RECOMMEND" | "DECIDE" | "OFFER";
  stages: AdmissionsApplicationWorkflowStage[];
};

export type AdmissionsApplicationWorkflowStage = {
  sequence: number;
  code: "CONFIRM" | "RELEASE" | "RECOMMEND" | "DECIDE" | "OFFER";
  label: string;
  state: "COMPLETED" | "CURRENT" | "PENDING" | "NOT_APPLICABLE";
  statusLabel: string;
  detail: string;
  occurredAt: string | null;
};

export type AdmissionsVerificationQueue = {
  applicationSections: Array<{
    applicationId: string;
    applicationNumber: string;
    applicantName: string;
    sectionCode: string;
    sectionName: string;
    status: ApplicationSectionStatus;
    completionSummary: string | null;
    version: number;
  }>;
  qualifications: Array<{
    applicationId: string;
    applicationNumber: string;
    applicantName: string;
    qualification: ApplicantQualificationSitting;
  }>;
  documents: Array<{
    applicationId: string;
    applicationNumber: string;
    applicantName: string;
    documents: ApplicationDocumentRegister;
  }>;
};
