package zw.ac.uz.emhare.admissions.application;

enum AdmissionCycleStatus {
    DRAFT,
    OPEN,
    CLOSED,
    SELECTION,
    OFFERS,
    COMPLETED,
    ARCHIVED
}

enum ApplicationStatus {
    DRAFT,
    SUBMITTED,
    PAYMENT_PENDING,
    UNDER_REVIEW,
    INCOMPLETE,
    ELIGIBLE,
    NOT_ELIGIBLE,
    UNDER_ACADEMIC_REVIEW,
    ADMITTED,
    REJECTED,
    OFFERED,
    ACCEPTED,
    DECLINED,
    WITHDRAWN,
    CONVERTED
}

enum ApplicationClearanceOutcome {
    CONFIRMED,
    INVALIDATED
}

enum AcademicReviewAssignmentStatus {
    OPEN,
    CLAIMED,
    RECOMMENDED,
    RETURNED,
    COMPLETED,
    CANCELLED
}

enum AcademicRecommendationReviewStatus {
    PENDING,
    APPROVED,
    RETURNED,
    OVERRIDDEN
}

enum AcademicReviewStatus {
    OPEN,
    CLAIMED,
    RECOMMENDED,
    RETURNED,
    COMPLETED,
    CANCELLED
}

enum RecommendationOutcome {
    RECOMMEND_ADMIT,
    RECOMMEND_REJECT
}

enum DecisionOutcome {
    ADMIT,
    REJECT
}

enum ProgrammeChoiceStatus {
    PENDING,
    ELIGIBLE,
    CONDITIONALLY_ELIGIBLE,
    INELIGIBLE,
    REQUIRES_REVIEW,
    UNDER_ACADEMIC_REVIEW,
    ADMITTED,
    OFFERED,
    CONVERTED,
    REJECTED
}

enum SelectionRoundStatus {
    DRAFT,
    OPEN,
    APPROVED,
    CLOSED
}

enum SelectionDecisionType {
    SHORTLIST,
    SELECT,
    REJECT,
    WAITLIST
}

enum OfferBatchScopeType {
    INSTITUTION,
    ACADEMIC_UNIT,
    PROGRAMME
}

enum OfferBatchStatus {
    DRAFT,
    APPROVED,
    DISPATCHED,
    CLOSED
}

enum OfferType {
    FIRM,
    CONDITIONAL
}

enum OfferStatus {
    DRAFT,
    APPROVED,
    SENT,
    ACCEPTED,
    DECLINED,
    EXPIRED,
    WITHDRAWN,
    CONVERTED
}

enum OfferConditionStatus {
    PENDING,
    SATISFIED,
    WAIVED
}

enum OfferDispatchStatus {
    SENT,
    DELIVERED,
    FAILED,
    BOUNCED
}

enum OfferResponseType {
    ACCEPTED,
    DECLINED
}

enum QualificationLevel {
    O_LEVEL,
    A_LEVEL,
    DIPLOMA,
    DEGREE,
    PROFESSIONAL,
    OTHER
}

enum SubjectLevel {
    O_LEVEL,
    A_LEVEL,
    OTHER
}

enum QualificationResultStatus {
    CAPTURED,
    VERIFIED,
    REJECTED
}

enum ApplicationSectionStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETE,
    VERIFIED,
    REJECTED,
    CORRECTION_REQUIRED
}

enum RefereeVerificationStatus {
    PENDING,
    VERIFIED,
    REJECTED
}

enum RequirementSetStatus {
    DRAFT,
    APPROVED,
    RETIRED
}

enum SubjectRequirementType {
    COMPULSORY,
    ONE_OF,
    ANY_OF,
    EXCLUDED,
    WEIGHTED
}

enum EvaluationStatus {
    ELIGIBLE,
    CONDITIONALLY_ELIGIBLE,
    NOT_ELIGIBLE,
    REQUIRES_REVIEW
}

enum PaymentReferenceStatus {
    PENDING,
    PAID,
    EXPIRED,
    CANCELLED
}

enum AccommodationRequestStatus {
    REQUESTED,
    NOT_REQUIRED,
    REFERRED,
    CANCELLED
}

enum AdmissionExamStatus {
    REQUIRED,
    SCHEDULED,
    SAT,
    WAIVED,
    NOT_REQUIRED
}
