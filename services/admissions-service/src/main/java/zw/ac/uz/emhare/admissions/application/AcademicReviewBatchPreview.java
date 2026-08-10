package zw.ac.uz.emhare.admissions.application;

import java.util.List;
import java.util.UUID;

/** Read-only programme and academic-unit batches available for release. @author Tinashe K */
public record AcademicReviewBatchPreview(
        int totalApplicants,
        int totalEligibleApplicants,
        List<ProgrammeBatch> programmes,
        List<AcademicUnitBatch> academicUnits) {

    public record ProgrammeBatch(
            UUID programmeId,
            String programmeCode,
            String programmeName,
            UUID owningAcademicUnitId,
            String owningAcademicUnitName,
            int applicantCount,
            int eligibleApplicantCount) { }

    public record AcademicUnitBatch(
            UUID academicUnitId,
            String academicUnitTypeCode,
            String academicUnitCode,
            String academicUnitName,
            int applicantCount,
            int eligibleApplicantCount) { }
}
