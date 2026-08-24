export type GeneratedDocumentStatus = "REQUESTED" | "GENERATING" | "STORED" | "FAILED";

export interface OfficialDocumentSummary {
  id: string;
  documentNumber: string;
  documentType: "RESULT_SLIP";
  studentNumber: string;
  academicPeriodCode: string;
  decisionCode: string;
  decisionLabel: string;
  status: GeneratedDocumentStatus;
  templateCode: string;
  templateVersion: number;
  checksumSha256?: string | null;
  sizeBytes?: number | null;
  pageCount?: number | null;
  requestedAt: string;
  generatedAt?: string | null;
  generationAttemptCount: number;
  retryAvailable: boolean;
  lastFailureReason?: string | null;
  version: number;
}

export interface OfficialDocumentDownload {
  documentId: string;
  documentNumber: string;
  contentType: string;
  checksumSha256: string;
  downloadUrl: string;
  expiresAt: string;
}

export type UploadedDocumentOwnerType =
  "APPLICANT" | "APPLICATION" | "STUDENT" | "STAFF" | "FINANCE_RECORD" | "ACADEMIC_WORKFLOW";
export type UploadedDocumentVerificationStatus = "PENDING" | "VERIFIED" | "REJECTED";

export interface UploadedDocumentSummary {
  id: string;
  ownerType: UploadedDocumentOwnerType;
  ownerId: string;
  documentTypeCode: string;
  originalFileName: string;
  mimeType: string;
  fileSizeBytes: number;
  checksumSha256: string;
  uploadedByUserId: string;
  uploadedAt: string;
  verificationStatus: UploadedDocumentVerificationStatus;
  verifiedByUserId?: string | null;
  verifiedAt?: string | null;
  verificationComment?: string | null;
  rejectionReason?: string | null;
  replacesDocumentId?: string | null;
  extractionStatus?: DocumentOcrStatus | null;
  version: number;
}

export type DocumentOcrStatus = "QUEUED" | "PROCESSING" | "COMPLETED" | "FAILED" | "UNSUPPORTED";

export interface DocumentOcrExtractionSummary {
  documentId: string;
  status: DocumentOcrStatus;
  engineName: string;
  engineVersion: string;
  structuredExtractionJson: string | null;
  proposedFactsJson: string | null;
  confidenceJson: string | null;
  warningsJson: string | null;
  attemptCount: number;
  queuedAt: string;
  startedAt: string | null;
  completedAt: string | null;
  lastFailureCode: string | null;
  lastFailureMessage: string | null;
  version: number;
}

export interface UploadedDocumentDownload {
  documentId: string;
  originalFileName: string;
  mimeType: string;
  checksumSha256: string;
  downloadUrl: string;
  expiresAt: string;
}
