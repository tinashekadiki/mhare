package zw.ac.uz.emhare.admissions.domain.model;

/** @author Tinashe K */
public enum ApplicationStatus {
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
    /** Retained only so historical selection-round audit events remain readable. */
    SHORTLISTED,
    /** Retained only so historical selection-round audit events remain readable. */
    WAITLISTED,
    /** Retained only so historical selection-round audit events remain readable. */
    SELECTED,
    OFFERED,
    ACCEPTED,
    DECLINED,
    WITHDRAWN,
    CONVERTED
}
