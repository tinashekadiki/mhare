package zw.ac.uz.emhare.assessmentresults.result;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.assessmentresults.assessment.AssessmentCalculationEvidenceService;
import zw.ac.uz.emhare.assessmentresults.assessment.AssessmentModuleOffering;
import zw.ac.uz.emhare.assessmentresults.integration.AssessmentResultsIntegrationOutboxService;
import zw.ac.uz.emhare.assessmentresults.result.ResultCommands.Band;
import zw.ac.uz.emhare.assessmentresults.result.ResultCommands.CreateGradingScheme;
import zw.ac.uz.emhare.assessmentresults.result.ResultCommands.CreateResultBatch;
import zw.ac.uz.emhare.assessmentresults.result.ResultCommands.Decision;
import zw.ac.uz.emhare.assessmentresults.result.ResultCommands.RequestPublishedResultAmendment;
import zw.ac.uz.emhare.assessmentresults.result.ResultViews.BandSummary;
import zw.ac.uz.emhare.assessmentresults.result.ResultViews.BatchSummary;
import zw.ac.uz.emhare.assessmentresults.result.ResultViews.CorrectionSourceSummary;
import zw.ac.uz.emhare.assessmentresults.result.ResultViews.GradingSummary;
import zw.ac.uz.emhare.assessmentresults.result.ResultViews.ModuleResultSummary;
import zw.ac.uz.emhare.assessmentresults.result.ResultViews.PublishedResultAmendmentSummary;
import zw.ac.uz.emhare.assessmentresults.result.ResultViews.PublishedResultPage;
import zw.ac.uz.emhare.assessmentresults.result.ResultViews.PublishedResultSummary;

/** @author Tinashe K */
@Service
public class ResultPublicationService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal SMALLEST_MARK_INCREMENT = new BigDecimal("0.01");

    private final GradingSchemeRepository gradingSchemeRepository;
    private final GradingBandRepository gradingBandRepository;
    private final ResultBatchRepository resultBatchRepository;
    private final ModuleResultRepository moduleResultRepository;
    private final ResultBatchStatusEventRepository resultBatchStatusEventRepository;
    private final PublishedResultRepository publishedResultRepository;
    private final PublishedResultAmendmentRepository amendmentRepository;
    private final PublishedResultAmendmentEventRepository amendmentEventRepository;
    private final AssessmentCalculationEvidenceService calculationEvidenceService;
    private final AssessmentResultsIntegrationOutboxService integrationOutboxService;
    private final Clock clock;

    public ResultPublicationService(
            GradingSchemeRepository gradingSchemeRepository,
            GradingBandRepository gradingBandRepository,
            ResultBatchRepository resultBatchRepository,
            ModuleResultRepository moduleResultRepository,
            ResultBatchStatusEventRepository resultBatchStatusEventRepository,
            PublishedResultRepository publishedResultRepository,
            PublishedResultAmendmentRepository amendmentRepository,
            PublishedResultAmendmentEventRepository amendmentEventRepository,
            AssessmentCalculationEvidenceService calculationEvidenceService,
            AssessmentResultsIntegrationOutboxService integrationOutboxService,
            Clock clock) {
        this.gradingSchemeRepository = gradingSchemeRepository;
        this.gradingBandRepository = gradingBandRepository;
        this.resultBatchRepository = resultBatchRepository;
        this.moduleResultRepository = moduleResultRepository;
        this.resultBatchStatusEventRepository = resultBatchStatusEventRepository;
        this.publishedResultRepository = publishedResultRepository;
        this.amendmentRepository = amendmentRepository;
        this.amendmentEventRepository = amendmentEventRepository;
        this.calculationEvidenceService = calculationEvidenceService;
        this.integrationOutboxService = integrationOutboxService;
        this.clock = clock;
    }

    @Transactional
    public GradingSummary createGrading(CreateGradingScheme command) {
        validateBands(command.bands());
        int schemeVersion = gradingSchemeRepository
                .findAllByDeletedAtIsNullOrderByCodeAscSchemeVersionDesc().stream()
                .filter(item -> item.getCode().equalsIgnoreCase(command.code()))
                .mapToInt(GradingScheme::getSchemeVersion)
                .max()
                .orElse(0) + 1;
        GradingScheme scheme = gradingSchemeRepository.saveAndFlush(
                new GradingScheme(command.code(), command.name(), schemeVersion));
        List<GradingBand> bands = command.bands().stream()
                .map(item -> new GradingBand(
                        scheme,
                        item.minimumMark(),
                        item.maximumMark(),
                        item.grade(),
                        item.remark(),
                        item.passing(),
                        item.sortOrder()))
                .toList();
        gradingBandRepository.saveAll(bands);
        return gradingView(scheme, bands);
    }

    @Transactional
    public GradingSummary approveGrading(UUID id, Decision decision, UUID actorUserId) {
        GradingScheme scheme = requireGradingScheme(id);
        List<GradingBand> bands = gradingBands(id);
        validateEntityBands(bands);
        scheme.approve(actorUserId, decision.reason(), clock.instant(), decision.expectedVersion());
        return gradingView(gradingSchemeRepository.saveAndFlush(scheme), bands);
    }

    @Transactional
    public BatchSummary createBatch(CreateResultBatch command, UUID actorUserId) {
        if (resultBatchRepository.existsByCalculationRunIdAndDeletedAtIsNull(command.calculationRunId())) {
            throw new IllegalStateException("This calculation run already has a result batch.");
        }
        GradingScheme gradingScheme = requireGradingScheme(command.gradingSchemeId());
        if (gradingScheme.getStatus() != GradingScheme.Status.APPROVED) {
            throw new IllegalStateException("An approved grading scheme is required.");
        }
        List<GradingBand> gradingBands = gradingBands(gradingScheme.getId());
        var calculationEvidence = calculationEvidenceService.requireComplete(command.calculationRunId());
        AssessmentModuleOffering offering = calculationEvidence.run().getModuleOffering();
        String batchNumber = "RES-" + offering.getAcademicPeriodCode() + "-"
                + offering.getModuleCode() + "-" + clock.instant().toEpochMilli();
        ResultBatch resultBatch = resultBatchRepository.saveAndFlush(
                new ResultBatch(calculationEvidence.run(), gradingScheme, batchNumber));
        List<ModuleResult> results = calculationEvidence.outcomes().stream()
                .map(item -> {
                    GradingBand matchingBand = gradingBands.stream()
                            .filter(candidate -> candidate.contains(item.outcome().getWeightedTotal()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    "Approved grading bands do not cover a calculated mark."));
                    return new ModuleResult(
                            resultBatch,
                            item.outcome(),
                            item.courseworkContribution(),
                            item.examinationContribution(),
                            matchingBand);
                })
                .toList();
        moduleResultRepository.saveAll(results);
        resultBatchStatusEventRepository.save(new ResultBatchStatusEvent(
                resultBatch,
                null,
                "Result batch materialised from completed calculation evidence.",
                actorUserId,
                clock.instant()));
        return batchView(resultBatch, results);
    }

    @Transactional
    public BatchSummary moveBatch(
            UUID resultBatchId,
            String action,
            Decision decision,
            UUID actorUserId) {
        ResultBatch resultBatch = requireResultBatch(resultBatchId);
        List<ModuleResult> moduleResults = moduleResults(resultBatchId);
        if ("publish".equals(action)) {
            ensureNoPublishedResultsExist(moduleResults);
        }
        Instant occurredAt = clock.instant();
        ResultBatch.Status previousStatus = switch (action) {
            case "submit" -> resultBatch.submit(
                    actorUserId, decision.reason(), occurredAt, decision.expectedVersion());
            case "moderate" -> resultBatch.moderate(
                    actorUserId, decision.reason(), occurredAt, decision.expectedVersion());
            case "approve" -> resultBatch.approve(
                    actorUserId, decision.reason(), occurredAt, decision.expectedVersion());
            case "publish" -> resultBatch.publish(
                    actorUserId, decision.reason(), occurredAt, decision.expectedVersion());
            default -> throw new IllegalArgumentException("Unsupported result action.");
        };
        ResultBatch savedBatch = resultBatchRepository.saveAndFlush(resultBatch);
        resultBatchStatusEventRepository.save(new ResultBatchStatusEvent(
                savedBatch, previousStatus, decision.reason(), actorUserId, occurredAt));
        if (savedBatch.getStatus() == ResultBatch.Status.PUBLISHED) {
            List<PublishedResult> publishedResults = publishedResultRepository.saveAllAndFlush(moduleResults.stream()
                    .map(result -> new PublishedResult(savedBatch, result, actorUserId, occurredAt))
                    .toList());
            publishedResults.forEach(integrationOutboxService::enqueuePublishedResult);
        }
        return batchView(savedBatch, moduleResults);
    }

    @Transactional
    public PublishedResultAmendmentSummary requestAmendment(
            RequestPublishedResultAmendment command,
            UUID actorUserId) {
        PublishedResult originalResult = requirePublishedResult(command.originalPublishedResultId());
        requireCurrentPublishedResult(originalResult);
        ModuleResult replacementResult = requireModuleResult(command.replacementModuleResultId());
        validateReplacementEvidence(originalResult, replacementResult);
        Instant requestedAt = clock.instant();
        String amendmentNumber = "AMEND-" + requestedAt.toEpochMilli() + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        PublishedResultAmendment amendment = amendmentRepository.saveAndFlush(
                new PublishedResultAmendment(
                        amendmentNumber,
                        originalResult,
                        replacementResult,
                        command.reason(),
                        actorUserId,
                        requestedAt));
        amendmentEventRepository.save(new PublishedResultAmendmentEvent(
                amendment, null, command.reason(), actorUserId, requestedAt));
        return amendmentView(amendment);
    }

    @Transactional
    public PublishedResultAmendmentSummary moveAmendment(
            UUID amendmentId,
            String action,
            Decision decision,
            UUID actorUserId) {
        PublishedResultAmendment amendment = requireAmendment(amendmentId);
        requireCurrentPublishedResult(amendment.getOriginalPublishedResult());
        Instant occurredAt = clock.instant();
        PublishedResultAmendment.Status previousStatus = switch (action) {
            case "review" -> amendment.review(
                    actorUserId, decision.reason(), occurredAt, decision.expectedVersion());
            case "approve" -> amendment.approve(
                    actorUserId, decision.reason(), occurredAt, decision.expectedVersion());
            case "apply" -> amendment.apply(
                    actorUserId, decision.reason(), occurredAt, decision.expectedVersion());
            case "reject" -> amendment.reject(
                    actorUserId, decision.reason(), occurredAt, decision.expectedVersion());
            default -> throw new IllegalArgumentException("Unsupported result amendment action.");
        };
        PublishedResultAmendment savedAmendment = amendmentRepository.saveAndFlush(amendment);
        amendmentEventRepository.save(new PublishedResultAmendmentEvent(
                savedAmendment, previousStatus, decision.reason(), actorUserId, occurredAt));
        if (savedAmendment.getStatus() == PublishedResultAmendment.Status.APPLIED) {
            PublishedResult replacementPublication = publishedResultRepository.saveAndFlush(new PublishedResult(
                    savedAmendment.getOriginalPublishedResult(),
                    savedAmendment,
                    actorUserId,
                    occurredAt));
            integrationOutboxService.enqueuePublishedResult(replacementPublication);
        }
        return amendmentView(savedAmendment);
    }

    @Transactional(readOnly = true)
    public List<BatchSummary> batches() {
        return resultBatchRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .map(batch -> batchView(batch, moduleResults(batch.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GradingSummary> grading() {
        return gradingSchemeRepository.findAllByDeletedAtIsNullOrderByCodeAscSchemeVersionDesc().stream()
                .map(item -> gradingView(item, gradingBands(item.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PublishedResultPage publishedResults(String studentNumber, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        var publishedResultPage = publishedResultRepository.findCurrentPublishedResults(
                studentNumber == null ? "" : studentNumber.trim(),
                PageRequest.of(safePage, safeSize));
        return new PublishedResultPage(
                publishedResultPage.getContent().stream().map(this::publishedResultView).toList(),
                publishedResultPage.getNumber(),
                publishedResultPage.getSize(),
                publishedResultPage.getTotalElements(),
                publishedResultPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<CorrectionSourceSummary> correctionSources(UUID publishedResultId) {
        PublishedResult originalResult = requirePublishedResult(publishedResultId);
        requireCurrentPublishedResult(originalResult);
        return moduleResultRepository.findCorrectionSources(
                        originalResult.getStudentId(),
                        originalResult.getModuleId(),
                        originalResult.getAcademicPeriodId(),
                        ResultBatch.Status.APPROVED).stream()
                .filter(source -> differsFrom(originalResult, source))
                .map(this::correctionSourceView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublishedResultAmendmentSummary> amendments() {
        return amendmentRepository.findAllByDeletedAtIsNullOrderByRequestedAtDesc().stream()
                .map(this::amendmentView)
                .toList();
    }

    private void ensureNoPublishedResultsExist(List<ModuleResult> moduleResults) {
        for (ModuleResult moduleResult : moduleResults) {
            var rosterImport = moduleResult.getRosterEntry().getRosterImport();
            AssessmentModuleOffering offering = moduleResult.getResultBatch().getModuleOffering();
            if (publishedResultRepository
                    .findFirstByStudentIdAndModuleIdAndAcademicPeriodIdAndDeletedAtIsNullOrderByPublicationVersionDesc(
                            rosterImport.getStudentId(),
                            offering.getModuleId(),
                            offering.getAcademicPeriodId())
                    .isPresent()) {
                throw new IllegalStateException(
                        "A published result already exists for at least one student; use a governed amendment.");
            }
        }
    }

    private void validateReplacementEvidence(PublishedResult originalResult, ModuleResult replacementResult) {
        ResultBatch replacementBatch = replacementResult.getResultBatch();
        if (replacementBatch.getStatus() != ResultBatch.Status.APPROVED) {
            throw new IllegalStateException("Replacement evidence must belong to an approved result batch.");
        }
        var replacementRosterImport = replacementResult.getRosterEntry().getRosterImport();
        AssessmentModuleOffering replacementOffering = replacementBatch.getModuleOffering();
        if (!replacementRosterImport.getStudentId().equals(originalResult.getStudentId())
                || !replacementOffering.getModuleId().equals(originalResult.getModuleId())
                || !replacementOffering.getAcademicPeriodId().equals(originalResult.getAcademicPeriodId())) {
            throw new IllegalStateException(
                    "Replacement evidence must match the original student, Module, and academic period.");
        }
        if (!differsFrom(originalResult, replacementResult)) {
            throw new IllegalStateException("A published result amendment must change the published result.");
        }
    }

    private boolean differsFrom(PublishedResult originalResult, ModuleResult replacementResult) {
        return originalResult.getFinalMark().compareTo(replacementResult.getFinalMark()) != 0
                || !originalResult.getGrade().equals(replacementResult.getGrade())
                || !originalResult.getRemark().equals(replacementResult.getRemark());
    }

    private void requireCurrentPublishedResult(PublishedResult publishedResult) {
        PublishedResult currentResult = publishedResultRepository
                .findFirstByStudentIdAndModuleIdAndAcademicPeriodIdAndDeletedAtIsNullOrderByPublicationVersionDesc(
                        publishedResult.getStudentId(),
                        publishedResult.getModuleId(),
                        publishedResult.getAcademicPeriodId())
                .orElseThrow(() -> new IllegalStateException("The current published result was not found."));
        if (!currentResult.getId().equals(publishedResult.getId())) {
            throw new IllegalStateException("Only the current published result version can be amended.");
        }
    }

    private void validateBands(List<Band> bands) {
        if (bands == null || bands.isEmpty()) {
            throw new IllegalArgumentException("At least one grading band is required.");
        }
        List<Band> sortedBands = bands.stream()
                .sorted(Comparator.comparing(Band::minimumMark))
                .toList();
        if (sortedBands.getFirst().minimumMark().compareTo(BigDecimal.ZERO) != 0
                || sortedBands.getLast().maximumMark().compareTo(ONE_HUNDRED) != 0) {
            throw new IllegalArgumentException("Grading bands must cover 0 through 100.");
        }
        for (int index = 1; index < sortedBands.size(); index++) {
            BigDecimal expectedMinimum = sortedBands.get(index - 1).maximumMark()
                    .add(SMALLEST_MARK_INCREMENT);
            if (sortedBands.get(index).minimumMark().compareTo(expectedMinimum) != 0) {
                throw new IllegalArgumentException(
                        "Grading bands must cover every two-decimal mark without gaps or overlaps.");
            }
        }
    }

    private void validateEntityBands(List<GradingBand> bands) {
        if (bands.isEmpty()) {
            throw new IllegalStateException("Grading scheme has no bands.");
        }
        List<GradingBand> sortedBands = bands.stream()
                .sorted(Comparator.comparing(GradingBand::getMinimumMark))
                .toList();
        if (sortedBands.getFirst().getMinimumMark().compareTo(BigDecimal.ZERO) != 0
                || sortedBands.getLast().getMaximumMark().compareTo(ONE_HUNDRED) != 0) {
            throw new IllegalStateException("Grading bands must cover 0 through 100.");
        }
        for (int index = 1; index < sortedBands.size(); index++) {
            BigDecimal expectedMinimum = sortedBands.get(index - 1).getMaximumMark()
                    .add(SMALLEST_MARK_INCREMENT);
            if (sortedBands.get(index).getMinimumMark().compareTo(expectedMinimum) != 0) {
                throw new IllegalStateException(
                        "Grading bands must cover every two-decimal mark without gaps or overlaps.");
            }
        }
    }

    private GradingScheme requireGradingScheme(UUID id) {
        return gradingSchemeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Grading scheme was not found."));
    }

    private ResultBatch requireResultBatch(UUID id) {
        return resultBatchRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Result batch was not found."));
    }

    private ModuleResult requireModuleResult(UUID id) {
        return moduleResultRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Module result was not found."));
    }

    private PublishedResult requirePublishedResult(UUID id) {
        return publishedResultRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Published result was not found."));
    }

    private PublishedResultAmendment requireAmendment(UUID id) {
        return amendmentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Published result amendment was not found."));
    }

    private List<GradingBand> gradingBands(UUID gradingSchemeId) {
        return gradingBandRepository
                .findAllByGradingSchemeIdAndDeletedAtIsNullOrderBySortOrderAsc(gradingSchemeId);
    }

    private List<ModuleResult> moduleResults(UUID resultBatchId) {
        return moduleResultRepository.findAllByResultBatchIdAndDeletedAtIsNull(resultBatchId);
    }

    private GradingSummary gradingView(GradingScheme scheme, List<GradingBand> bands) {
        return new GradingSummary(
                scheme.getId(),
                scheme.getCode(),
                scheme.getName(),
                scheme.getSchemeVersion(),
                scheme.getStatus(),
                scheme.getVersion(),
                bands.stream()
                        .map(band -> new BandSummary(
                                band.getId(),
                                band.getMinimumMark(),
                                band.getMaximumMark(),
                                band.getGrade(),
                                band.getRemark(),
                                band.isPassing()))
                        .toList());
    }

    private BatchSummary batchView(ResultBatch batch, List<ModuleResult> results) {
        AssessmentModuleOffering offering = batch.getModuleOffering();
        return new BatchSummary(
                batch.getId(),
                batch.getCalculationRun().getId(),
                batch.getBatchNumber(),
                offering.getModuleCode(),
                offering.getModuleName(),
                offering.getAcademicPeriodCode(),
                batch.getStatus(),
                batch.getStatusReason(),
                batch.getVersion(),
                results.size(),
                batch.getSubmittedByUserId(),
                batch.getSubmittedAt(),
                batch.getModeratedByUserId(),
                batch.getModeratedAt(),
                batch.getApprovedByUserId(),
                batch.getApprovedAt(),
                batch.getPublishedByUserId(),
                batch.getPublishedAt(),
                results.stream()
                        .map(result -> new ModuleResultSummary(
                                result.getId(),
                                result.getRosterEntry().getRosterImport().getStudentNumber(),
                                result.getCourseworkMark(),
                                result.getExaminationMark(),
                                result.getFinalMark(),
                                result.getGrade(),
                                result.getRemark(),
                                result.getResultStatus()))
                        .toList());
    }

    private PublishedResultSummary publishedResultView(PublishedResult publishedResult) {
        return new PublishedResultSummary(
                publishedResult.getId(),
                publishedResult.getResultBatch().getId(),
                publishedResult.getModuleResult().getId(),
                publishedResult.getStudentId(),
                publishedResult.getStudentNumber(),
                publishedResult.getModuleId(),
                publishedResult.getModuleCode(),
                publishedResult.getModuleName(),
                publishedResult.getAcademicPeriodId(),
                publishedResult.getAcademicPeriodCode(),
                publishedResult.getFinalMark(),
                publishedResult.getGrade(),
                publishedResult.getRemark(),
                publishedResult.getPublicationVersion(),
                publishedResult.getSupersedesPublishedResultId(),
                publishedResult.getResultAmendmentId(),
                publishedResult.getPublishedByUserId(),
                publishedResult.getPublishedAt());
    }

    private CorrectionSourceSummary correctionSourceView(ModuleResult moduleResult) {
        ResultBatch resultBatch = moduleResult.getResultBatch();
        return new CorrectionSourceSummary(
                moduleResult.getId(),
                resultBatch.getId(),
                resultBatch.getBatchNumber(),
                moduleResult.getCourseworkMark(),
                moduleResult.getExaminationMark(),
                moduleResult.getFinalMark(),
                moduleResult.getGrade(),
                moduleResult.getRemark(),
                resultBatch.getApprovedAt());
    }

    private PublishedResultAmendmentSummary amendmentView(PublishedResultAmendment amendment) {
        PublishedResult originalResult = amendment.getOriginalPublishedResult();
        return new PublishedResultAmendmentSummary(
                amendment.getId(),
                amendment.getAmendmentNumber(),
                originalResult.getId(),
                originalResult.getPublicationVersion(),
                amendment.getReplacementResultBatch().getId(),
                amendment.getReplacementModuleResult().getId(),
                originalResult.getStudentNumber(),
                originalResult.getModuleCode(),
                originalResult.getModuleName(),
                originalResult.getAcademicPeriodCode(),
                originalResult.getFinalMark(),
                originalResult.getGrade(),
                originalResult.getRemark(),
                amendment.getProposedFinalMark(),
                amendment.getProposedGrade(),
                amendment.getProposedRemark(),
                amendment.getRequestReason(),
                amendment.getStatus(),
                amendment.getVersion(),
                amendment.getRequestedByUserId(),
                amendment.getRequestedAt(),
                amendment.getReviewedByUserId(),
                amendment.getReviewedAt(),
                amendment.getReviewReason(),
                amendment.getApprovedByUserId(),
                amendment.getApprovedAt(),
                amendment.getApprovalReason(),
                amendment.getAppliedByUserId(),
                amendment.getAppliedAt(),
                amendment.getRejectedByUserId(),
                amendment.getRejectedAt(),
                amendment.getRejectionReason());
    }
}
