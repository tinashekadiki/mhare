export type ExamSessionStatus = 'DRAFT' | 'APPROVED' | 'CLOSED'
export type ExamRequirementStatus = 'DRAFT' | 'APPROVED' | 'SUPERSEDED'
export type ExamTimetableStatus = 'GENERATED' | 'REVIEWED' | 'APPROVED' | 'PUBLISHED' | 'REJECTED'

export interface ExamAvailabilitySummary { id: string, availableFrom: string, availableUntil: string, notes?: string | null }
export interface ExamVenueTypeSummary { id: string, code: string, name: string, description?: string | null, active: boolean, version: number }
export interface ExamVenueSummary {
  id: string, venueTypeId: string, venueTypeCode: string, code: string, name: string, campusName: string,
  buildingName?: string | null, roomName?: string | null, examinationCapacity: number,
  accessibilityNotes?: string | null, active: boolean, version: number, availability: ExamAvailabilitySummary[]
}
export interface ExamSlotSummary { id: string, code: string, startsAt: string, endsAt: string }
export interface ExamSessionSummary {
  id: string, academicPeriodId: string, academicPeriodCode: string, code: string, name: string,
  assessmentType: 'FINAL_EXAM' | 'SUPPLEMENTARY' | 'DEFERRED' | 'SPECIAL', startsOn: string, endsOn: string,
  status: ExamSessionStatus, approvedByUserId?: string | null, approvedAt?: string | null,
  approvalReason?: string | null, version: number, slots: ExamSlotSummary[]
}
export interface ExamRequirementSummary {
  id: string, academicPeriodId: string, moduleId: string, moduleCode: string, moduleName: string,
  requirementVersion: number, durationMinutes: number, readingTimeMinutes: number,
  requiredVenueTypeId?: string | null, requiredVenueTypeCode?: string | null, specialRequirements?: string | null,
  status: ExamRequirementStatus, version: number
}
export interface ExamSetupRegister {
  venueTypes: ExamVenueTypeSummary[], venues: ExamVenueSummary[], sessions: ExamSessionSummary[], requirements: ExamRequirementSummary[]
}
export interface ExamVenueAllocationSummary { id: string, venueId: string, venueCode: string, venueName: string, allocatedCapacity: number }
export interface ExamMasterEntrySummary {
  id: string, moduleId: string, moduleCode: string, moduleName: string, candidateCount: number,
  slotId: string, slotCode: string, startsAt: string, endsAt: string, venues: ExamVenueAllocationSummary[]
}
export interface ExamTimetableRunSummary {
  id: string, examSessionId: string, sessionCode: string, sessionName: string, runNumber: string,
  status: ExamTimetableStatus, candidateCount: number, moduleCount: number, timetableEntryCount: number,
  conflictCount: number, generationPolicy: Record<string, unknown>, generatedByUserId: string, generatedAt: string,
  reviewedByUserId?: string | null, approvedByUserId?: string | null, publishedByUserId?: string | null,
  publishedAt?: string | null, version: number, entries: ExamMasterEntrySummary[]
}

export type ExamAttendanceSessionStatus = 'OPEN' | 'CLOSED'
export type ExamAttendanceStatus = 'EXPECTED' | 'PRESENT' | 'ABSENT' | 'EXCUSED'
export type ExamIncidentType = 'LATE_ARRIVAL' | 'SUSPECTED_MISCONDUCT' | 'MEDICAL' | 'EVACUATION' | 'DISRUPTION' | 'OTHER'
export type ExamIncidentSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type ExamIncidentStatus = 'REPORTED' | 'REVIEWED' | 'RESOLVED'
export interface ExamAttendanceRecordSummary {
  id: string, studentTimetableEntryId: string, studentId: string, studentNumber: string, seatNumber: number,
  attendanceStatus: ExamAttendanceStatus, recordedByUserId?: string | null, recordedAt?: string | null,
  evidenceNotes?: string | null, version: number
}
export interface ExamIncidentSummary {
  id: string, incidentNumber: string, studentTimetableEntryId?: string | null, studentNumber?: string | null,
  incidentType: ExamIncidentType, severity: ExamIncidentSeverity, description: string, occurredAt: string,
  status: ExamIncidentStatus, reportedByUserId: string, reportedAt: string, reviewedByUserId?: string | null,
  reviewedAt?: string | null, reviewReason?: string | null, resolvedByUserId?: string | null,
  resolvedAt?: string | null, resolution?: string | null, version: number
}
export interface ExamAttendanceSessionSummary {
  id: string, status: ExamAttendanceSessionStatus, expectedCandidateCount: number, presentCandidateCount: number,
  absentCandidateCount: number, excusedCandidateCount: number, outstandingCandidateCount: number,
  openedByUserId: string, openedAt: string, openingReason: string, closedByUserId?: string | null,
  closedAt?: string | null, closureReason?: string | null, version: number,
  attendanceRecords: ExamAttendanceRecordSummary[], incidents: ExamIncidentSummary[]
}
export interface ExamVenueOperationSummary {
  venueAllocationId: string, generationRunId: string, runNumber: string, masterTimetableEntryId: string,
  moduleCode: string, moduleName: string, scheduledStartsAt: string, scheduledEndsAt: string,
  venueId: string, venueCode: string, venueName: string, campusName: string,
  allocatedCandidateCount: number, attendanceSession?: ExamAttendanceSessionSummary | null
}
export interface ExamInvigilationWorkspace { venueOperations: ExamVenueOperationSummary[] }
