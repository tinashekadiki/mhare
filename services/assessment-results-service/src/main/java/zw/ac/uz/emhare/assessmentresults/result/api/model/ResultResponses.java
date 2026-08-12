package zw.ac.uz.emhare.assessmentresults.result.api.model;

import zw.ac.uz.emhare.assessmentresults.result.domain.model.GradingScheme;
import zw.ac.uz.emhare.assessmentresults.result.domain.model.ModuleResult;
import zw.ac.uz.emhare.assessmentresults.result.domain.model.PublishedResultAmendment;
import zw.ac.uz.emhare.assessmentresults.result.domain.model.ResultBatch;

import zw.ac.uz.emhare.assessmentresults.result.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** @author Tinashe K */
public final class ResultResponses {
    private ResultResponses() {
    }

    public record GradingSummary(
            UUID id,
            String code,
            String name,
            int schemeVersion,
            GradingScheme.Status status,
            long version,
            List<BandSummary> bands) {
    }

    public record BandSummary(
            UUID id,
            BigDecimal minimumMark,
            BigDecimal maximumMark,
            String grade,
            String remark,
            boolean passing) {
    }

    public record BatchSummary(
            UUID id,
            UUID calculationRunId,
            String batchNumber,
            String moduleCode,
            String moduleName,
            String academicPeriodCode,
            ResultBatch.Status status,
            String statusReason,
            long version,
            int resultCount,
            UUID submittedByUserId,
            Instant submittedAt,
            UUID moderatedByUserId,
            Instant moderatedAt,
            UUID approvedByUserId,
            Instant approvedAt,
            UUID publishedByUserId,
            Instant publishedAt,
            List<ModuleResultSummary> results) {
    }

    public record ModuleResultSummary(
            UUID id,
            String studentNumber,
            BigDecimal courseworkMark,
            BigDecimal examinationMark,
            BigDecimal finalMark,
            String grade,
            String remark,
            ModuleResult.Status status) {
    }

    public record PublishedResultSummary(
            UUID id,
            UUID resultBatchId,
            UUID moduleResultId,
            UUID studentId,
            String studentNumber,
            UUID moduleId,
            String moduleCode,
            String moduleName,
            UUID academicPeriodId,
            String academicPeriodCode,
            BigDecimal finalMark,
            String grade,
            String remark,
            int publicationVersion,
            UUID supersedesPublishedResultId,
            UUID resultAmendmentId,
            UUID publishedByUserId,
            Instant publishedAt) {
    }

    public record PublishedResultPage(
            List<PublishedResultSummary> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record CorrectionSourceSummary(
            UUID moduleResultId,
            UUID resultBatchId,
            String batchNumber,
            BigDecimal courseworkMark,
            BigDecimal examinationMark,
            BigDecimal finalMark,
            String grade,
            String remark,
            Instant approvedAt) {
    }

    public record PublishedResultAmendmentSummary(
            UUID id,
            String amendmentNumber,
            UUID originalPublishedResultId,
            int originalPublicationVersion,
            UUID replacementResultBatchId,
            UUID replacementModuleResultId,
            String studentNumber,
            String moduleCode,
            String moduleName,
            String academicPeriodCode,
            BigDecimal originalFinalMark,
            String originalGrade,
            String originalRemark,
            BigDecimal proposedFinalMark,
            String proposedGrade,
            String proposedRemark,
            String requestReason,
            PublishedResultAmendment.Status status,
            long version,
            UUID requestedByUserId,
            Instant requestedAt,
            UUID reviewedByUserId,
            Instant reviewedAt,
            String reviewReason,
            UUID approvedByUserId,
            Instant approvedAt,
            String approvalReason,
            UUID appliedByUserId,
            Instant appliedAt,
            UUID rejectedByUserId,
            Instant rejectedAt,
            String rejectionReason) {
    }
}
