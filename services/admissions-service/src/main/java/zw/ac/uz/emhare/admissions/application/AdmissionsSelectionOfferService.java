package zw.ac.uz.emhare.admissions.application;

import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.integration.AdmissionsIntegrationOutboxService;
import zw.ac.uz.emhare.common.persistence.EmhareRevisionContext;

/** @author Tinashe K */
@Service
public class AdmissionsSelectionOfferService {

    private final AdmissionCycleRepository admissionCycleRepository;
    private final AdmissionsIntakeProjectionService admissionsIntakeProjectionService;
    private final AdmissionCycleArchiveSummaryRepository admissionCycleArchiveSummaryRepository;
    private final ApplicationTypeRepository applicationTypeRepository;
    private final ApplicationTypeDocumentRequirementRepository applicationTypeDocumentRequirementRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationProgrammeChoiceRepository programmeChoiceRepository;
    private final AdmissionRequirementSetRepository requirementSetRepository;
    private final ApplicationEvaluationRepository evaluationRepository;
    private final QualificationEligibilityService qualificationEligibilityService;
    private final ApplicationStatusEventRepository applicationStatusEventRepository;
    private final SelectionRoundRepository selectionRoundRepository;
    private final SelectionDecisionRepository selectionDecisionRepository;
    private final AcademicReviewAssignmentRepository academicReviewAssignmentRepository;
    private final OfferBatchRepository offerBatchRepository;
    private final AdmissionOfferRepository offerRepository;
    private final OfferConditionRepository offerConditionRepository;
    private final OfferDispatchRepository offerDispatchRepository;
    private final OfferResponseRepository offerResponseRepository;
    private final OfferStatusEventRepository offerStatusEventRepository;
    private final AdmissionsIdentifierGenerator identifierGenerator;
    private final AdmissionsIntegrationOutboxService integrationOutboxService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AdmissionsSelectionOfferService(
            AdmissionCycleRepository admissionCycleRepository,
            AdmissionsIntakeProjectionService admissionsIntakeProjectionService,
            AdmissionCycleArchiveSummaryRepository admissionCycleArchiveSummaryRepository,
            ApplicationTypeRepository applicationTypeRepository,
            ApplicationTypeDocumentRequirementRepository applicationTypeDocumentRequirementRepository,
            ApplicationRepository applicationRepository,
            ApplicationProgrammeChoiceRepository programmeChoiceRepository,
            AdmissionRequirementSetRepository requirementSetRepository,
            ApplicationEvaluationRepository evaluationRepository,
            QualificationEligibilityService qualificationEligibilityService,
            ApplicationStatusEventRepository applicationStatusEventRepository,
            SelectionRoundRepository selectionRoundRepository,
            SelectionDecisionRepository selectionDecisionRepository,
            AcademicReviewAssignmentRepository academicReviewAssignmentRepository,
            OfferBatchRepository offerBatchRepository,
            AdmissionOfferRepository offerRepository,
            OfferConditionRepository offerConditionRepository,
            OfferDispatchRepository offerDispatchRepository,
            OfferResponseRepository offerResponseRepository,
            OfferStatusEventRepository offerStatusEventRepository,
            AdmissionsIdentifierGenerator identifierGenerator,
            AdmissionsIntegrationOutboxService integrationOutboxService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.admissionCycleRepository = admissionCycleRepository;
        this.admissionsIntakeProjectionService = admissionsIntakeProjectionService;
        this.admissionCycleArchiveSummaryRepository = admissionCycleArchiveSummaryRepository;
        this.applicationTypeRepository = applicationTypeRepository;
        this.applicationTypeDocumentRequirementRepository = applicationTypeDocumentRequirementRepository;
        this.applicationRepository = applicationRepository;
        this.programmeChoiceRepository = programmeChoiceRepository;
        this.requirementSetRepository = requirementSetRepository;
        this.evaluationRepository = evaluationRepository;
        this.qualificationEligibilityService = qualificationEligibilityService;
        this.applicationStatusEventRepository = applicationStatusEventRepository;
        this.selectionRoundRepository = selectionRoundRepository;
        this.selectionDecisionRepository = selectionDecisionRepository;
        this.academicReviewAssignmentRepository = academicReviewAssignmentRepository;
        this.offerBatchRepository = offerBatchRepository;
        this.offerRepository = offerRepository;
        this.offerConditionRepository = offerConditionRepository;
        this.offerDispatchRepository = offerDispatchRepository;
        this.offerResponseRepository = offerResponseRepository;
        this.offerStatusEventRepository = offerStatusEventRepository;
        this.identifierGenerator = identifierGenerator;
        this.integrationOutboxService = integrationOutboxService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public List<ApplicationTypeSummary> listApplicationTypes() {
        return applicationTypeRepository.findAllByDeletedAtIsNullOrderByNameAsc().stream()
                .map(ApplicationTypeSummary::from)
                .toList();
    }

    @Transactional
    public ApplicationTypeSummary createApplicationType(
            String code,
            String name,
            boolean requiresEmploymentHistory,
            boolean requiresReferees,
            UUID financeFeeStructureId,
            String financeFeeStructureCode,
            String financeFeeStructureName,
            boolean active) {
        String normalizedCode = requiredText(code, "Application type code").toUpperCase(Locale.ROOT);
        if (applicationTypeRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull(normalizedCode)) {
            throw new IllegalStateException("An application type with this code already exists.");
        }
        ApplicationType applicationType = new ApplicationType(
                normalizedCode, name, requiresEmploymentHistory, requiresReferees, active);
        applicationType.associateFeeStructure(financeFeeStructureId, financeFeeStructureCode, financeFeeStructureName);
        ApplicationType savedApplicationType = applicationTypeRepository.saveAndFlush(applicationType);
        applicationTypeDocumentRequirementRepository.saveAllAndFlush(defaultDocumentRequirements(savedApplicationType));
        return ApplicationTypeSummary.from(savedApplicationType);
    }

    private List<ApplicationTypeDocumentRequirement> defaultDocumentRequirements(ApplicationType applicationType) {
        return List.of(
                new ApplicationTypeDocumentRequirement(
                        applicationType, "IDENTITY_DOCUMENT", "Identity document", true, 10),
                new ApplicationTypeDocumentRequirement(
                        applicationType, "ACADEMIC_QUALIFICATION_EVIDENCE",
                        "Academic qualification evidence", true, 20));
    }

    @Transactional
    public ApplicationTypeSummary updateApplicationType(
            UUID applicationTypeId,
            String name,
            boolean requiresEmploymentHistory,
            boolean requiresReferees,
            UUID financeFeeStructureId,
            String financeFeeStructureCode,
            String financeFeeStructureName,
            boolean active,
            String changeReason,
            long expectedVersion) {
        String normalizedChangeReason = requiredText(changeReason, "Application type change reason");
        if (normalizedChangeReason.length() < 10) {
            throw new IllegalArgumentException(
                    "Application type change reason must contain at least 10 characters.");
        }
        String correlationId = EmhareRevisionContext.getCorrelationId().orElse(null);
        EmhareRevisionContext.setRequestMetadata(correlationId, normalizedChangeReason);
        try {
            ApplicationType applicationType = applicationType(applicationTypeId);
            applicationType.update(
                    name, requiresEmploymentHistory, requiresReferees, active,
                    financeFeeStructureId, financeFeeStructureCode, financeFeeStructureName, expectedVersion);
            return ApplicationTypeSummary.from(applicationTypeRepository.saveAndFlush(applicationType));
        } finally {
            EmhareRevisionContext.setRequestMetadata(correlationId, null);
        }
    }

    @Transactional
    public List<AdmissionCycleSummary> listAdmissionCycles() {
        return admissionCycleRepository.findAll().stream()
                .filter(cycle -> !cycle.isDeleted())
                .map(AdmissionCycleSummary::from)
                .toList();
    }

    @Transactional
    public AdmissionCycleSummary createAdmissionCycle(
            UUID academicYearId,
            UUID intakeId,
            String code,
            String name,
            Instant opensAt,
            Instant closesAt,
            int maximumProgrammeChoices,
            UUID applicationTypeId) {
        if (!closesAt.isAfter(opensAt)) {
            throw new IllegalArgumentException("Admission cycle close date must be after the open date.");
        }
        ApplicationType applicationType = applicationTypeId == null
                ? null
                : applicationTypeRepository.findById(applicationTypeId)
                        .orElseThrow(() -> new IllegalArgumentException("Application type not found."));
        AdmissionCycle cycle = new AdmissionCycle(academicYearId, intakeId, code, name, opensAt, closesAt, applicationType);
        cycle.configureMaximumProgrammeChoices(maximumProgrammeChoices);
        return AdmissionCycleSummary.from(admissionCycleRepository.saveAndFlush(cycle));
    }

    @Transactional
    public AdmissionCycleSummary updateAdmissionCycle(
            UUID admissionCycleId,
            UUID academicYearId,
            UUID intakeId,
            String code,
            String name,
            Instant opensAt,
            Instant closesAt,
            int maximumProgrammeChoices,
            UUID applicationTypeId,
            String changeReason,
            long expectedVersion) {
        ApplicationType applicationType = applicationTypeId == null
                ? null
                : applicationTypeRepository.findById(applicationTypeId)
                        .orElseThrow(() -> new IllegalArgumentException("Application type not found."));
        AdmissionCycle cycle = admissionCycle(admissionCycleId);
        cycle.update(
                academicYearId, intakeId, code, name, opensAt, closesAt,
                maximumProgrammeChoices, applicationType, changeReason, expectedVersion);
        return AdmissionCycleSummary.from(admissionCycleRepository.saveAndFlush(cycle));
    }

    @Transactional
    public void openAdmissionCycle(UUID admissionCycleId) {
        admissionCycle(admissionCycleId).open(clock.instant());
    }

    @Transactional
    public void closeApplications(UUID admissionCycleId) {
        admissionCycle(admissionCycleId).closeApplications();
    }

    @Transactional
    public void beginSelection(UUID admissionCycleId) {
        admissionCycle(admissionCycleId).beginSelection();
    }

    @Transactional
    public void prepareSelection(UUID admissionCycleId) {
        AdmissionCycle cycle = admissionCycle(admissionCycleId);
        if (cycle.getStatus() == AdmissionCycleStatus.SELECTION) return;
        if (cycle.getStatus() == AdmissionCycleStatus.OPEN) cycle.closeApplications();
        cycle.beginSelection();
    }

    @Transactional
    public void prepareSelectionForIntake(UUID intakeId) {
        var intake = admissionsIntakeProjectionService.requireIntake(intakeId);
        if (!"CLOSED".equals(intake.status())) {
            throw new IllegalStateException(
                    "Close the intake in Academic Setup before starting selection.");
        }
        prepareSelection(admissionsIntakeProjectionService.requireProjection(intakeId).getId());
    }

    @Transactional
    public void completeAdmissionCycle(UUID admissionCycleId) {
        admissionCycle(admissionCycleId).complete();
    }

    @Transactional
    public AdmissionCycleArchiveSummaryView archiveAdmissionCycle(UUID admissionCycleId, UUID actorUserId) {
        AdmissionCycle cycle = admissionCycle(admissionCycleId);
        List<Application> applications = applicationRepository.findByAdmissionCycleId(admissionCycleId);
        Map<ApplicationStatus, Long> byStatus = applications.stream()
                .collect(java.util.stream.Collectors.groupingBy(Application::getStatus, java.util.stream.Collectors.counting()));
        cycle.archive();
        AdmissionCycleArchiveSummary summary = new AdmissionCycleArchiveSummary(
                cycle,
                applications.size(),
                countAtLeast(byStatus, ApplicationStatus.SUBMITTED, ApplicationStatus.PAYMENT_PENDING,
                        ApplicationStatus.UNDER_REVIEW, ApplicationStatus.INCOMPLETE, ApplicationStatus.ELIGIBLE,
                        ApplicationStatus.NOT_ELIGIBLE, ApplicationStatus.UNDER_ACADEMIC_REVIEW,
                        ApplicationStatus.ADMITTED, ApplicationStatus.REJECTED,
                        ApplicationStatus.OFFERED, ApplicationStatus.ACCEPTED, ApplicationStatus.DECLINED,
                        ApplicationStatus.WITHDRAWN, ApplicationStatus.CONVERTED),
                countAtLeast(byStatus, ApplicationStatus.ELIGIBLE, ApplicationStatus.UNDER_ACADEMIC_REVIEW,
                        ApplicationStatus.ADMITTED, ApplicationStatus.REJECTED, ApplicationStatus.OFFERED,
                        ApplicationStatus.ACCEPTED, ApplicationStatus.DECLINED, ApplicationStatus.WITHDRAWN,
                        ApplicationStatus.CONVERTED),
                countAtLeast(byStatus, ApplicationStatus.ADMITTED, ApplicationStatus.OFFERED,
                        ApplicationStatus.ACCEPTED, ApplicationStatus.DECLINED, ApplicationStatus.WITHDRAWN,
                        ApplicationStatus.CONVERTED),
                countAtLeast(byStatus, ApplicationStatus.OFFERED, ApplicationStatus.ACCEPTED,
                        ApplicationStatus.DECLINED, ApplicationStatus.WITHDRAWN, ApplicationStatus.CONVERTED),
                countAtLeast(byStatus, ApplicationStatus.ACCEPTED, ApplicationStatus.CONVERTED),
                countAtLeast(byStatus, ApplicationStatus.CONVERTED),
                actorUserId,
                clock.instant());
        return AdmissionCycleArchiveSummaryView.from(admissionCycleArchiveSummaryRepository.saveAndFlush(summary));
    }

    private int countAtLeast(Map<ApplicationStatus, Long> byStatus, ApplicationStatus... statuses) {
        int total = 0;
        for (ApplicationStatus status : statuses) {
            total += byStatus.getOrDefault(status, 0L).intValue();
        }
        return total;
    }

    @Transactional
    public AdmissionRequirementSetSummary createRequirementSet(
            UUID programmeId,
            UUID applicationTypeId,
            UUID intakeId,
            String versionCode,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            java.math.BigDecimal minimumTotalPoints,
            java.math.BigDecimal maleCutoffPoints,
            java.math.BigDecimal femaleCutoffPoints,
            boolean requiresEnglish,
            boolean requiresMathematicsOrScience,
            Map<String, Object> advancedRules,
            String advancedRulesVersion) {
        ApplicationType applicationType = applicationTypeRepository.findById(applicationTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Application type not found."));
        AdmissionCycle cycle = intakeId == null ? null : admissionsIntakeProjectionService.requireProjection(intakeId);
        String advancedRulesJson = advancedRules == null ? null : serialize(advancedRules);
        AdmissionRequirementSet requirementSet = requirementSetRepository.save(new AdmissionRequirementSet(
                programmeId, applicationType, cycle, requiredText(versionCode, "Requirement-set version"),
                effectiveFrom, effectiveTo, minimumTotalPoints, maleCutoffPoints, femaleCutoffPoints,
                requiresEnglish, requiresMathematicsOrScience, advancedRulesJson,
                advancedRulesJson == null ? null : requiredText(advancedRulesVersion, "Advanced-rules version")));
        return AdmissionRequirementSetSummary.from(requirementSet);
    }

    @Transactional
    public AdmissionRequirementSetSummary approveRequirementSet(UUID requirementSetId, UUID actorUserId) {
        AdmissionRequirementSet requirementSet = requirementSetRepository.findById(requirementSetId)
                .orElseThrow(() -> new IllegalArgumentException("Admission requirement set not found."));
        List<AdmissionRequirementSet> supersededRequirementSets = requirementSetRepository.findApprovedForRouteForUpdate(
                        requirementSet.getProgrammeId(),
                        requirementSet.getApplicationType().getId(),
                        requirementSet.getAdmissionCycle() == null ? null : requirementSet.getAdmissionCycle().getId())
                .stream()
                .filter(existing -> !existing.getId().equals(requirementSet.getId()))
                .filter(existing -> existing.overlapsEffectivePeriod(requirementSet))
                .toList();
        supersededRequirementSets.forEach(AdmissionRequirementSet::retire);
        if (!supersededRequirementSets.isEmpty()) {
            requirementSetRepository.saveAllAndFlush(supersededRequirementSets);
        }
        requirementSet.approve(actorUserId, clock.instant());
        return AdmissionRequirementSetSummary.from(requirementSetRepository.saveAndFlush(requirementSet));
    }

    @Transactional
    public List<AdmissionRequirementSetSummary> listRequirementSets() {
        return requirementSetRepository.findAllByDeletedAtIsNullOrderByEffectiveFromDesc().stream()
                .map(AdmissionRequirementSetSummary::from)
                .toList();
    }

    @Transactional
    public EvaluationSummary recordEvaluation(RecordEvaluationCommand command) {
        Application application = application(command.applicationId());
        ApplicationProgrammeChoice choice = programmeChoice(command.programmeChoiceId());
        if (!choice.getApplication().getId().equals(application.getId())) {
            throw new IllegalArgumentException("Programme choice does not belong to the application.");
        }
        AdmissionRequirementSet requirementSet = requirementSetRepository.findById(command.requirementSetId())
                .orElseThrow(() -> new IllegalArgumentException("Admission requirement set not found."));
        if (!requirementSet.isApprovedAndEffectiveFor(
                choice.getProgrammeId(),
                application.getApplicationType().getId(),
                application.getAdmissionCycle().getId(),
                LocalDate.now(clock))) {
            throw new IllegalStateException("The requirement set is not approved and effective for this application route.");
        }
        if (evaluationRepository.existsByProgrammeChoiceIdAndRequirementSetIdAndDeletedAtIsNull(
                choice.getId(), requirementSet.getId())) {
            throw new IllegalStateException("This programme choice has already been evaluated against that requirement-set version.");
        }

        EvaluationStatus evaluationStatus = parseEnum(EvaluationStatus.class, command.status(), "evaluation status");
        QualificationEligibilityService.RequirementEvaluation requirementEvaluation =
                qualificationEligibilityService.evaluateRequirements(application, requirementSet);
        if (evaluationStatus == EvaluationStatus.ELIGIBLE && !requirementEvaluation.missingRequirements().isEmpty()) {
            throw new IllegalStateException(
                    "Application does not satisfy the approved requirement set: "
                            + String.join(", ", requirementEvaluation.missingRequirements()));
        }
        List<Map<String, Object>> evaluationMissingEvidence = new java.util.ArrayList<>(
                requirementEvaluation.missingRequirementEvidence());
        if (command.missingRequirements() != null) {
            evaluationMissingEvidence.addAll(command.missingRequirements());
        }
        Map<String, Object> evaluationRuleEvidence = new java.util.LinkedHashMap<>(
                requirementEvaluation.ruleEvidence());
        if (command.ruleResults() != null && !command.ruleResults().isEmpty()) {
            evaluationRuleEvidence.put("officerEvidence", command.ruleResults());
        }
        Instant evaluatedAt = clock.instant();
        ApplicationStatus previousApplicationStatus = application.getStatus();
        ApplicationEvaluation evaluation = evaluationRepository.save(new ApplicationEvaluation(
                application,
                choice,
                requirementSet,
                evaluationStatus,
                requirementEvaluation.totalPoints(),
                command.rankScore(),
                serialize(evaluationMissingEvidence),
                serialize(evaluationRuleEvidence),
                evaluatedAt,
                command.actorUserId()));
        choice.recordEvaluation(evaluationStatus, requiredText(command.summary(), "Evaluation summary"));

        List<ApplicationProgrammeChoice> applicationChoices = programmeChoiceRepository
                .findAllByApplicationIdOrderByChoiceRankAsc(application.getId());
        boolean anyEligible = applicationChoices.stream()
                .anyMatch(item -> item.getChoiceStatus() == ProgrammeChoiceStatus.ELIGIBLE);
        boolean allFinal = applicationChoices.stream()
                .allMatch(item -> item.getChoiceStatus() == ProgrammeChoiceStatus.ELIGIBLE
                        || item.getChoiceStatus() == ProgrammeChoiceStatus.INELIGIBLE);
        application.applyEvaluationOutcome(anyEligible, allFinal, command.summary());
        recordApplicationStatusChange(application, previousApplicationStatus, command.summary(), command.actorUserId());
        return EvaluationSummary.from(evaluation, application.getId());
    }

    @Transactional
    public SelectionRoundSummary createSelectionRound(UUID intakeId, String code, String name) {
        AdmissionCycle cycle = admissionsIntakeProjectionService.requireProjection(intakeId);
        if (cycle.getStatus() != AdmissionCycleStatus.SELECTION) {
            throw new IllegalStateException("Selection rounds can only be created after the intake closes for applications.");
        }
        return SelectionRoundSummary.from(selectionRoundRepository.save(new SelectionRound(cycle, code, name)));
    }

    @Transactional
    public List<SelectionRoundSummary> listSelectionRounds() {
        return selectionRoundRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .map(SelectionRoundSummary::from)
                .toList();
    }

    @Transactional
    public List<SelectionDecisionSummary> listSelectionDecisions(UUID selectionRoundId) {
        selectionRound(selectionRoundId);
        return selectionDecisionRepository.findAllBySelectionRoundIdAndDeletedAtIsNullOrderByRankPositionAsc(selectionRoundId)
                .stream().map(SelectionDecisionSummary::from).toList();
    }

    @Transactional
    public OfferBatchSummary createOfferBatch(
            UUID intakeId,
            UUID selectionRoundId,
            String code,
            String name,
            String scopeTypeCode,
            UUID scopeId) {
        AdmissionCycle cycle = admissionsIntakeProjectionService.requireProjection(intakeId);
        SelectionRound selectionRound = selectionRound(selectionRoundId);
        if (selectionRound.getStatus() != SelectionRoundStatus.APPROVED) {
            throw new IllegalStateException("Offer batches require an approved selection round.");
        }
        OfferBatchScopeType scopeType = parseEnum(OfferBatchScopeType.class, scopeTypeCode, "offer batch scope");
        return OfferBatchSummary.from(offerBatchRepository.save(
                new OfferBatch(cycle, selectionRound, code, name, scopeType, scopeId)));
    }

    @Transactional
    public List<OfferBatchSummary> listOfferBatches() {
        return offerBatchRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .map(OfferBatchSummary::from)
                .toList();
    }

    @Transactional
    public AdmissionOfferSummary createOffer(CreateAdmissionOfferCommand command) {
        OfferBatch batch = offerBatch(command.offerBatchId());
        ApplicationProgrammeChoice choice = programmeChoice(command.programmeChoiceId());
        SelectionDecision approvedSelection = selectionDecisionRepository.findApprovedSelectionForChoice(choice.getId())
                .orElseThrow(() -> new IllegalStateException("An approved selection decision is required before creating an offer."));
        if (!approvedSelection.getSelectionRound().getId().equals(batch.getSelectionRound().getId())) {
            throw new IllegalStateException("Selected decision does not belong to the offer batch selection round.");
        }
        Application application = choice.getApplication();
        Instant now = clock.instant();
        AdmissionOffer offer = offerRepository.saveAndFlush(new AdmissionOffer(
                application,
                choice,
                batch,
                identifierGenerator.nextOfferNumber(batch.getAdmissionCycle()),
                parseEnum(OfferType.class, command.offerType(), "offer type"),
                command.conditionsText(),
                command.acceptanceDeadline(),
                command.registrationDate(),
                command.orientationDate(),
                command.commencementDate(),
                null,
                now));
        List<OfferCondition> conditions = command.conditions() == null ? List.of() : command.conditions().stream()
                .map(condition -> new OfferCondition(offer, condition.code(), condition.description(), condition.required()))
                .toList();
        conditions = offerConditionRepository.saveAll(conditions);
        offerStatusEventRepository.save(new OfferStatusEvent(
                offer, null, OfferStatus.DRAFT, "Offer draft generated from approved selection.", command.actorUserId(), now));
        integrationOutboxService.enqueueOfferLetterRequested(offer, command.actorUserId());
        return AdmissionOfferSummary.from(offer, conditions, null);
    }

    @Transactional
    public void linkStoredOfferLetter(UUID offerId, long offerVersion, UUID generatedDocumentId) {
        AdmissionOffer offer = offer(offerId);
        if (offer.getVersion() < offerVersion) {
            throw new IllegalStateException("Stored offer letter references a future offer version.");
        }
        offer.linkGeneratedDocument(generatedDocumentId);
        offerRepository.saveAndFlush(offer);
    }

    @Transactional
    public AdmissionOfferSummary approveOffer(UUID offerId, UUID actorUserId) {
        AdmissionOffer offer = offer(offerId);
        OfferStatus previousStatus = offer.getStatus();
        if (previousStatus == OfferStatus.APPROVED) return offerSummary(offer);
        offer.approve(actorUserId, clock.instant());
        Application application = offer.getApplication();
        ApplicationStatus previousApplicationStatus = application.getStatus();
        application.markOffered("Approved offer " + offer.getOfferNumber());
        offer.getProgrammeChoice().markOffered("Approved offer " + offer.getOfferNumber());
        recordOfferStatusChange(offer, previousStatus, "Offer approved for dispatch.", actorUserId);
        recordApplicationStatusChange(application, previousApplicationStatus, "Offer approved for dispatch.", actorUserId);
        return offerSummary(offer);
    }

    @Transactional
    public AdmissionOfferSummary dispatchOffer(
            UUID offerId,
            String deliveryMethodCode,
            String sentTo,
            String providerMessageId,
            UUID actorUserId) {
        AdmissionOffer offer = offer(offerId);
        OfferStatus previousStatus = offer.getStatus();
        Instant now = clock.instant();
        offer.markSent(now);
        offerDispatchRepository.save(new OfferDispatch(offer, deliveryMethodCode, sentTo, providerMessageId, now));
        recordOfferStatusChange(offer, previousStatus, "Offer dispatched to applicant.", actorUserId);
        integrationOutboxService.enqueueOfferDispatchedNotification(offer);
        return offerSummary(offer);
    }

    @Transactional
    public AdmissionOfferSummary resolveOfferCondition(
            UUID offerId,
            UUID conditionId,
            String resolutionCode,
            String notes,
            UUID actorUserId) {
        AdmissionOffer offer = offer(offerId);
        if (offer.getStatus() != OfferStatus.SENT && offer.getStatus() != OfferStatus.ACCEPTED) {
            throw new IllegalStateException("Conditions can only be resolved for a sent or accepted offer.");
        }
        OfferCondition condition = offerConditionRepository
                .findByIdAndOfferIdAndDeletedAtIsNull(conditionId, offerId)
                .orElseThrow(() -> new IllegalArgumentException("Offer condition not found."));
        OfferConditionStatus resolution = parseEnum(
                OfferConditionStatus.class, resolutionCode, "offer condition resolution");
        if (resolution == OfferConditionStatus.SATISFIED) {
            condition.satisfy(actorUserId, notes, clock.instant());
        } else if (resolution == OfferConditionStatus.WAIVED) {
            condition.waive(actorUserId, notes, clock.instant());
        } else {
            throw new IllegalArgumentException("An offer condition can only be satisfied or waived.");
        }
        if (offer.getStatus() == OfferStatus.ACCEPTED) {
            enqueueConversionWhenReady(offer, clock.instant());
        }
        return offerSummary(offer);
    }

    @Transactional
    public AdmissionOfferSummary withdrawOffer(UUID offerId, String reason, UUID actorUserId) {
        AdmissionOffer offer = offer(offerId);
        OfferStatus previousOfferStatus = offer.getStatus();
        Application application = offer.getApplication();
        ApplicationStatus previousApplicationStatus = application.getStatus();
        offer.withdraw(actorUserId, reason);
        if (previousOfferStatus == OfferStatus.APPROVED || previousOfferStatus == OfferStatus.SENT) {
            application.reopenAfterOfferClosed("Offer withdrawn: " + reason);
            offer.getProgrammeChoice().reopenAfterOfferClosed("Offer withdrawn: " + reason);
        }
        recordOfferStatusChange(offer, previousOfferStatus, "Offer withdrawn: " + reason, actorUserId);
        recordApplicationStatusChange(application, previousApplicationStatus, "Offer withdrawn: " + reason, actorUserId);
        return offerSummary(offer);
    }

    @Transactional
    public AdmissionOfferSummary expireOffer(UUID offerId, UUID actorUserId) {
        AdmissionOffer offer = offer(offerId);
        OfferStatus previousOfferStatus = offer.getStatus();
        Application application = offer.getApplication();
        ApplicationStatus previousApplicationStatus = application.getStatus();
        String reason = "Acceptance deadline elapsed.";
        offer.expire(clock.instant(), reason);
        application.reopenAfterOfferClosed(reason);
        offer.getProgrammeChoice().reopenAfterOfferClosed(reason);
        recordOfferStatusChange(offer, previousOfferStatus, reason, actorUserId);
        recordApplicationStatusChange(application, previousApplicationStatus, reason, actorUserId);
        return offerSummary(offer);
    }

    @Transactional
    public boolean hasUnresolvedRequiredConditions(UUID offerId) {
        offer(offerId);
        return offerConditionRepository.countByOfferIdAndRequiredTrueAndStatusAndDeletedAtIsNull(
                offerId, OfferConditionStatus.PENDING) > 0;
    }

    @Transactional
    public void completeStudentConversion(
            UUID conversionRequestId,
            UUID applicationId,
            UUID offerId,
            UUID studentId,
            String studentNumber,
            UUID actorUserId) {
        AdmissionOffer offer = offer(offerId);
        Application application = application(applicationId);
        if (!offer.getApplication().getId().equals(application.getId())) {
            throw new IllegalArgumentException("Converted offer does not belong to the application.");
        }
        if (offer.getStatus() == OfferStatus.CONVERTED) {
            if (!studentId.equals(offer.getConvertedStudentId())) {
                throw new IllegalStateException("Offer was already converted to another student record.");
            }
            return;
        }
        if (hasUnresolvedRequiredConditions(offerId)) {
            throw new IllegalStateException("Required offer conditions must be resolved before conversion completion.");
        }
        OfferStatus previousOfferStatus = offer.getStatus();
        ApplicationStatus previousApplicationStatus = application.getStatus();
        offer.markConverted(conversionRequestId, studentId, studentNumber, clock.instant());
        application.markConverted("Converted to student " + studentNumber + ".");
        offer.getProgrammeChoice().markConverted("Converted to student " + studentNumber + ".");
        recordOfferStatusChange(offer, previousOfferStatus, "Student conversion completed.", actorUserId);
        recordApplicationStatusChange(application, previousApplicationStatus, "Student conversion completed.", actorUserId);
        integrationOutboxService.enqueueStudentConversionNotification(offer);
    }

    @Transactional
    public AdmissionOfferSummary respondToOffer(
            UUID offerId,
            UUID applicantUserId,
            String responseCode,
            String notes) {
        AdmissionOffer offer = offerRepository
                .findByIdAndApplicationApplicantUserIdAndDeletedAtIsNull(offerId, applicantUserId)
                .orElseThrow(() -> new IllegalArgumentException("Offer not found."));
        OfferResponseType responseType = parseEnum(OfferResponseType.class, responseCode, "offer response");
        OfferResponse existingResponse = offerResponseRepository.findByOfferId(offerId).orElse(null);
        if (existingResponse != null) {
            if (existingResponse.getResponse() != responseType) {
                throw new IllegalStateException("The immutable offer response has already been recorded.");
            }
            return offerSummary(offer);
        }
        Instant now = clock.instant();
        if (now.isAfter(offer.getAcceptanceDeadline())) {
            throw new IllegalStateException("The offer acceptance deadline has passed.");
        }
        OfferResponse response = offerResponseRepository.saveAndFlush(
                new OfferResponse(offer, responseType, now, applicantUserId, notes));
        OfferStatus previousOfferStatus = offer.getStatus();
        Application application = offer.getApplication();
        ApplicationStatus previousApplicationStatus = application.getStatus();
        offer.respond(responseType);
        application.recordOfferResponse(responseType, "Applicant " + responseType.name().toLowerCase(Locale.ROOT) + " the offer.");
        offer.getProgrammeChoice().recordOfferResponse(responseType, "Applicant response recorded.");
        if (responseType == OfferResponseType.ACCEPTED) {
            enqueueConversionWhenReady(offer, now);
        }
        recordOfferStatusChange(offer, previousOfferStatus, "Applicant response recorded.", applicantUserId);
        recordApplicationStatusChange(application, previousApplicationStatus, "Applicant response recorded.", applicantUserId);
        integrationOutboxService.enqueueOfferResponseNotification(offer);
        return AdmissionOfferSummary.from(
                offer,
                offerConditionRepository.findAllByOfferIdAndDeletedAtIsNullOrderByConditionCodeAsc(offerId),
                response);
    }

    @Transactional
    public List<AdmissionOfferSummary> listOffers() {
        return offerRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .map(this::offerSummary)
                .toList();
    }

    @Transactional
    public List<AdmissionOfferSummary> listApplicantOffers(UUID applicantUserId) {
        return offerRepository.findAllByApplicationApplicantUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(applicantUserId).stream()
                .filter(offer -> offer.getStatus() != OfferStatus.DRAFT && offer.getStatus() != OfferStatus.APPROVED)
                .map(this::offerSummary)
                .toList();
    }

    private AdmissionOfferSummary offerSummary(AdmissionOffer offer) {
        return AdmissionOfferSummary.from(
                offer,
                offerConditionRepository.findAllByOfferIdAndDeletedAtIsNullOrderByConditionCodeAsc(offer.getId()),
                offerResponseRepository.findByOfferId(offer.getId()).orElse(null));
    }

    private void enqueueConversionWhenReady(AdmissionOffer offer, Instant now) {
        if (offer.getConversionEventId() != null) return;
        long pendingRequiredConditions = offerConditionRepository
                .countByOfferIdAndRequiredTrueAndStatusAndDeletedAtIsNull(
                        offer.getId(), OfferConditionStatus.PENDING);
        if (pendingRequiredConditions == 0) {
            UUID eventId = offer.requestConversion(now);
            integrationOutboxService.enqueueAcceptedOfferReadyForConversion(eventId, offer);
        }
    }

    private void recordApplicationStatusChange(
            Application application,
            ApplicationStatus previousStatus,
            String reason,
            UUID actorUserId) {
        if (previousStatus != application.getStatus()) {
            applicationStatusEventRepository.save(new ApplicationStatusEvent(
                    application, previousStatus, application.getStatus(), reason, actorUserId));
        }
    }

    private void recordOfferStatusChange(
            AdmissionOffer offer,
            OfferStatus previousStatus,
            String reason,
            UUID actorUserId) {
        offerStatusEventRepository.save(new OfferStatusEvent(
                offer, previousStatus, offer.getStatus(), reason, actorUserId, clock.instant()));
    }

    private AdmissionCycle admissionCycle(UUID id) {
        return admissionCycleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Admission cycle not found."));
    }

    private ApplicationType applicationType(UUID id) {
        return applicationTypeRepository.findById(id)
                .filter(applicationType -> !applicationType.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Application type not found."));
    }

    private Application application(UUID id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
    }

    private ApplicationProgrammeChoice programmeChoice(UUID id) {
        return programmeChoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Programme choice not found."));
    }

    private SelectionRound selectionRound(UUID id) {
        return selectionRoundRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Selection round not found."));
    }

    private OfferBatch offerBatch(UUID id) {
        return offerBatchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Offer batch not found."));
    }

    private AdmissionOffer offer(UUID id) {
        return offerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Offer not found."));
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Evaluation evidence could not be serialized.", exception);
        }
    }

    private static String requiredText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumType, String value, String label) {
        try {
            return Enum.valueOf(enumType, requiredText(value, label).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown " + label + ": " + value, exception);
        }
    }
}
