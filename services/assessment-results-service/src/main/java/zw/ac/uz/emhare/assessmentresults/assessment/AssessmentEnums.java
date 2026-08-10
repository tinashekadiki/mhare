package zw.ac.uz.emhare.assessmentresults.assessment;

/** @author Tinashe K */
public final class AssessmentEnums {
    private AssessmentEnums() {}

    public enum OfferingStatus { DRAFT, ACTIVE, CLOSED }
    public enum SchemeStatus { DRAFT, APPROVED, SUPERSEDED }
    public enum ComponentType { COURSEWORK, PRACTICAL, IN_CLASS_TEST, FINAL_EXAM, OTHER }
    public enum MarkStatus { CAPTURED, SUBMITTED, SUPERSEDED }
    public enum CaptureMethod { MANUAL, UPLOAD, AMENDMENT }
    public enum AmendmentStatus { REQUESTED, APPROVED, REJECTED }
    public enum CalculationStatus { RUNNING, COMPLETED, FAILED }
}
