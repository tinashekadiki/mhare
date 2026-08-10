export type ResidentGenderPolicy = "ANY" | "FEMALE" | "MALE";
export type AccommodationRoomCondition =
  "AVAILABLE" | "MAINTENANCE" | "OUT_OF_SERVICE";
export type AccommodationPeriodStatus =
  | "DRAFT"
  | "APPLICATION_OPEN"
  | "APPLICATION_CLOSED"
  | "ALLOCATION_ACTIVE"
  | "CLOSED";

export interface AccommodationPremiseSummary {
  id: string;
  code: string;
  name: string;
  addressLine: string;
  suburb: string | null;
  landlordName: string | null;
  contactDetails: string | null;
  active: boolean;
  version: number;
}

export interface AccommodationRoomTypeSummary {
  id: string;
  code: string;
  name: string;
  description: string | null;
  defaultCapacity: number;
  active: boolean;
  version: number;
}

export interface ResidenceHallSummary {
  id: string;
  premiseId: string;
  premiseCode: string;
  code: string;
  name: string;
  residentGenderPolicy: ResidentGenderPolicy;
  wardenName: string | null;
  wardenContact: string | null;
  active: boolean;
  version: number;
}

export interface AccommodationRoomSummary {
  id: string;
  residenceHallId: string;
  residenceHallCode: string;
  roomTypeId: string;
  roomTypeCode: string;
  code: string;
  floorLabel: string | null;
  capacity: number;
  accessibilityReady: boolean;
  conditionStatus: AccommodationRoomCondition;
  conditionNotes: string | null;
  reservedForGroupId: string | null;
  active: boolean;
  version: number;
}

export interface AccommodationApplicationPeriodSummary {
  id: string;
  academicPeriodId: string;
  academicPeriodCode: string;
  code: string;
  name: string;
  applicationsOpenAt: string;
  applicationsCloseAt: string;
  occupancyStartsOn: string;
  occupancyEndsOn: string;
  allocationCutoffAt: string;
  status: AccommodationPeriodStatus;
  preparedByUserId: string;
  approvedByUserId: string | null;
  approvedAt: string | null;
  approvalReason: string | null;
  version: number;
}

export interface AccommodationSetupRegister {
  premises: AccommodationPremiseSummary[];
  roomTypes: AccommodationRoomTypeSummary[];
  residenceHalls: ResidenceHallSummary[];
  rooms: AccommodationRoomSummary[];
  applicationPeriods: AccommodationApplicationPeriodSummary[];
}

export type AccommodationRateStatus = "DRAFT" | "ACTIVE" | "RETIRED";
export type AccommodationRatingStatus = "RATED" | "UNRATED";
export type AccommodationApplicationStatus =
  | "SUBMITTED"
  | "ELIGIBLE"
  | "WAITLISTED"
  | "ALLOCATED"
  | "REJECTED"
  | "WITHDRAWN";
export type AccommodationPaymentState =
  "PAID" | "WAIVED" | "PART_PAID" | "UNPAID" | "UNKNOWN";
export type AccommodationWaitlistStatus =
  "ACTIVE" | "ALLOCATED" | "WITHDRAWN" | "REMOVED";
export type RoomAllocationStatus =
  | "PROPOSED"
  | "ALLOCATED"
  | "CHECKED_IN"
  | "CHECKED_OUT"
  | "WITHDRAWN"
  | "CANCELLED";

export interface AccommodationRateSummary {
  id: string;
  applicationPeriodId: string;
  applicationPeriodCode: string;
  roomTypeId: string;
  roomTypeCode: string;
  rateVersion: number;
  financeFeeCatalogueId: string;
  transactionCurrencyCode: string;
  indicativeTransactionAmount: number;
  baseCurrencyCode: "USD";
  exchangeRateId: string | null;
  indicativeBaseAmount: number | null;
  ratingStatus: AccommodationRatingStatus;
  effectiveFrom: string;
  effectiveUntil: string | null;
  status: AccommodationRateStatus;
  preparedByUserId: string;
  approvedByUserId: string | null;
  approvedAt: string | null;
  approvalReason: string | null;
  version: number;
}

export interface AccommodationApplicationSummary {
  id: string;
  applicationNumber: string;
  applicationPeriodId: string;
  applicationPeriodCode: string;
  studentId: string;
  studentNumber: string;
  studentName: string;
  primaryEmail: string;
  genderCode: string;
  disabilityCode: string | null;
  countryCode: string;
  locationCode: string | null;
  programmeId: string;
  programmeCode: string;
  programmeName: string;
  programmeLevel: number;
  sponsorCode: string | null;
  paymentState: AccommodationPaymentState;
  preferredRoomTypeId: string | null;
  preferredRoomTypeCode: string | null;
  specialRequirements: string | null;
  priorityScore: number;
  status: AccommodationApplicationStatus;
  submittedAt: string;
  evaluatedByUserId: string | null;
  evaluatedAt: string | null;
  evaluationReason: string | null;
  selectedGroupId: string | null;
  version: number;
}

export interface AccommodationWaitlistSummary {
  id: string;
  applicationId: string;
  applicationNumber: string;
  applicationPeriodId: string;
  applicationPeriodCode: string;
  waitlistPosition: number;
  priorityScore: number;
  status: AccommodationWaitlistStatus;
  enteredByUserId: string;
  enteredAt: string;
  removedByUserId: string | null;
  removedAt: string | null;
  removalReason: string | null;
  version: number;
}

export interface RoomAllocationSummary {
  id: string;
  allocationNumber: string;
  applicationId: string;
  applicationNumber: string;
  studentNumber: string;
  studentName: string;
  roomId: string;
  roomCode: string;
  residenceHallCode: string;
  accommodationRateId: string;
  transactionCurrencyCode: string;
  indicativeTransactionAmount: number;
  indicativeBaseAmount: number;
  occupancyStartsOn: string;
  occupancyEndsOn: string;
  status: RoomAllocationStatus;
  allocatedByUserId: string;
  allocatedAt: string;
  approvedByUserId: string | null;
  approvedAt: string | null;
  approvalReason: string | null;
  checkedInByUserId: string | null;
  checkedInAt: string | null;
  checkInNotes: string | null;
  checkedOutByUserId: string | null;
  checkedOutAt: string | null;
  checkOutNotes: string | null;
  endedByUserId: string | null;
  endedAt: string | null;
  endReason: string | null;
  billingStatus: "NOT_REQUESTED" | "PENDING" | "ACCEPTED" | "FAILED";
  version: number;
}

export interface RoomAllocationEventSummary {
  id: string;
  allocationId: string;
  allocationNumber: string;
  previousStatus: RoomAllocationStatus | null;
  newStatus: RoomAllocationStatus;
  eventType: string;
  fromRoomId: string | null;
  toRoomId: string | null;
  reason: string;
  actorUserId: string;
  occurredAt: string;
}

export interface AccommodationOperationsRegister {
  rates: AccommodationRateSummary[];
  applications: AccommodationApplicationSummary[];
  waitlistEntries: AccommodationWaitlistSummary[];
  allocations: RoomAllocationSummary[];
  allocationEvents: RoomAllocationEventSummary[];
}
