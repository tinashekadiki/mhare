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
import zw.ac.uz.emhare.admissions.application.command.*;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionCycle;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionCycleArchiveSummary;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionCycleStatus;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionOffer;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionQualificationRequirementGroup;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionQualificationRequirementItem;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionRequirementSet;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationEvaluation;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeChoice;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationStatus;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationStatusEvent;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationType;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationTypeDocumentRequirement;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationTypeSection;
import zw.ac.uz.emhare.admissions.domain.model.EvaluationStatus;
import zw.ac.uz.emhare.admissions.domain.model.OfferCondition;
import zw.ac.uz.emhare.admissions.domain.model.OfferConditionStatus;
import zw.ac.uz.emhare.admissions.domain.model.OfferDispatch;
import zw.ac.uz.emhare.admissions.domain.model.OfferResponse;
import zw.ac.uz.emhare.admissions.domain.model.OfferResponseType;
import zw.ac.uz.emhare.admissions.domain.model.OfferStatus;
import zw.ac.uz.emhare.admissions.domain.model.OfferStatusEvent;
import zw.ac.uz.emhare.admissions.domain.model.ProgrammeChoiceStatus;
import zw.ac.uz.emhare.admissions.domain.model.QualificationLevel;
import zw.ac.uz.emhare.admissions.domain.model.SelectionRound;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AcademicReviewAssignmentRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionCycleArchiveSummaryRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionCycleRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionOfferRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionQualificationRequirementGroupRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionQualificationRequirementItemRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionRequirementSetRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationEvaluationRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProgrammeChoiceRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationStatusEventRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeDocumentRequirementRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeSectionRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferBatchRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferConditionRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferDispatchRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferResponseRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferStatusEventRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.SelectionDecisionRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.SelectionRoundRepository;
import zw.ac.uz.emhare.admissions.integration.AdmissionsIntegrationOutboxService;
import zw.ac.uz.emhare.common.persistence.EmhareRevisionContext;

/**
 * @author Tinashe K
 */
@Service
public class AdmissionsSelectionOfferService {

  private final AdmissionCycleRepository admissionCycleRepository;
  private final AdmissionsIntakeProjectionService admissionsIntakeProjectionService;
  private final AdmissionCycleArchiveSummaryRepository admissionCycleArchiveSummaryRepository;
  private final ApplicationTypeRepository applicationTypeRepository;
  private final ApplicationTypeDocumentRequirementRepository
      applicationTypeDocumentRequirementRepository;
  private final ApplicationTypeSectionRepository applicationTypeSectionRepository;
  private final ApplicationRepository applicationRepository;
  private final ApplicationProgrammeChoiceRepository programmeChoiceRepository;
  private final AdmissionRequirementSetRepository requirementSetRepository;
  private final AdmissionQualificationRequirementGroupRepository qualificationGroupRepository;
  private final AdmissionQualificationRequirementItemRepository qualificationItemRepository;
  private final ApplicationEvaluationRepository evaluationRepository;
  private final QualificationEligibilityService qualificationEligibilityService;
  private final AdvancedAdmissionRuleEvaluator advancedRuleEvaluator;
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
  private final AdmissionsIntegrationOutboxService integrationOutboxService;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public AdmissionsSelectionOfferService(
      AdmissionCycleRepository admissionCycleRepository,
      AdmissionsIntakeProjectionService admissionsIntakeProjectionService,
      AdmissionCycleArchiveSummaryRepository admissionCycleArchiveSummaryRepository,
      ApplicationTypeRepository applicationTypeRepository,
      ApplicationTypeDocumentRequirementRepository applicationTypeDocumentRequirementRepository,
      ApplicationTypeSectionRepository applicationTypeSectionRepository,
      ApplicationRepository applicationRepository,
      ApplicationProgrammeChoiceRepository programmeChoiceRepository,
      AdmissionRequirementSetRepository requirementSetRepository,
      AdmissionQualificationRequirementGroupRepository qualificationGroupRepository,
      AdmissionQualificationRequirementItemRepository qualificationItemRepository,
      ApplicationEvaluationRepository evaluationRepository,
      QualificationEligibilityService qualificationEligibilityService,
      AdvancedAdmissionRuleEvaluator advancedRuleEvaluator,
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
      AdmissionsIntegrationOutboxService integrationOutboxService,
      ObjectMapper objectMapper,
      Clock clock) {
    this.admissionCycleRepository = admissionCycleRepository;
    this.admissionsIntakeProjectionService = admissionsIntakeProjectionService;
    this.admissionCycleArchiveSummaryRepository = admissionCycleArchiveSummaryRepository;
    this.applicationTypeRepository = applicationTypeRepository;
    this.applicationTypeDocumentRequirementRepository =
        applicationTypeDocumentRequirementRepository;
    this.applicationTypeSectionRepository = applicationTypeSectionRepository;
    this.applicationRepository = applicationRepository;
    this.programmeChoiceRepository = programmeChoiceRepository;
    this.requirementSetRepository = requirementSetRepository;
    this.qualificationGroupRepository = qualificationGroupRepository;
    this.qualificationItemRepository = qualificationItemRepository;
    this.evaluationRepository = evaluationRepository;
    this.qualificationEligibilityService = qualificationEligibilityService;
    this.advancedRuleEvaluator = advancedRuleEvaluator;
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
    if (active) {
      throw new IllegalArgumentException(
          "Create application routes as inactive, then use route configuration to validate and activate them.");
    }
    String normalizedCode = requiredText(code, "Application type code").toUpperCase(Locale.ROOT);
    if (applicationTypeRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull(normalizedCode)) {
      throw new IllegalStateException("An application type with this code already exists.");
    }
    ApplicationType applicationType =
        new ApplicationType(
            normalizedCode, name, requiresEmploymentHistory, requiresReferees, active);
    applicationType.associateFeeStructure(
        financeFeeStructureId, financeFeeStructureCode, financeFeeStructureName);
    ApplicationType savedApplicationType = applicationTypeRepository.saveAndFlush(applicationType);
    applicationTypeDocumentRequirementRepository.saveAllAndFlush(
        defaultDocumentRequirements(savedApplicationType));
    applicationTypeSectionRepository.saveAllAndFlush(defaultSections(savedApplicationType));
    return ApplicationTypeSummary.from(savedApplicationType);
  }

  private List<ApplicationTypeSection> defaultSections(ApplicationType applicationType) {
    return ApplicationSectionTemplate.defaults(applicationType).stream()
        .map(
            section ->
                new ApplicationTypeSection(
                    applicationType,
                    section.code(),
                    section.name(),
                    section.required(),
                    section.repeatable(),
                    section.minimumRecords(),
                    section.sortOrder()))
        .toList();
  }

  private List<ApplicationTypeDocumentRequirement> defaultDocumentRequirements(
      ApplicationType applicationType) {
    return List.of(
        new ApplicationTypeDocumentRequirement(
            applicationType, "IDENTITY_DOCUMENT", "Identity document", true, 10),
        new ApplicationTypeDocumentRequirement(
            applicationType,
            "ACADEMIC_QUALIFICATION_EVIDENCE",
            "Academic qualification evidence",
            true,
            20));
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
      if (active != applicationType.isActive()) {
        throw new IllegalArgumentException(
            "Use the atomic route-configuration endpoint to change application-route activation.");
      }
      applicationType.update(
          name,
          requiresEmploymentHistory,
          requiresReferees,
          active,
          financeFeeStructureId,
          financeFeeStructureCode,
          financeFeeStructureName,
          expectedVersion);
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
    ApplicationType applicationType =
        applicationTypeId == null
            ? null
            : applicationTypeRepository
                .findById(applicationTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Application type not found."));
    AdmissionCycle cycle =
        new AdmissionCycle(
            academicYearId, intakeId, code, name, opensAt, closesAt, applicationType);
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
    ApplicationType applicationType =
        applicationTypeId == null
            ? null
            : applicationTypeRepository
                .findById(applicationTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Application type not found."));
    AdmissionCycle cycle = admissionCycle(admissionCycleId);
    cycle.update(
        academicYearId,
        intakeId,
        code,
        name,
        opensAt,
        closesAt,
        maximumProgrammeChoices,
        applicationType,
        changeReason,
        expectedVersion);
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
  public AdmissionCycleArchiveSummaryView archiveAdmissionCycle(
      UUID admissionCycleId, UUID actorUserId) {
    AdmissionCycle cycle = admissionCycle(admissionCycleId);
    List<Application> applications = applicationRepository.findByIntakeId(cycle.getIntakeId());
    Map<ApplicationStatus, Long> byStatus =
        applications.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    Application::getStatus, java.util.stream.Collectors.counting()));
    cycle.archive();
    AdmissionCycleArchiveSummary summary =
        new AdmissionCycleArchiveSummary(
            cycle,
            applications.size(),
            countAtLeast(
                byStatus,
                ApplicationStatus.SUBMITTED,
                ApplicationStatus.PAYMENT_PENDING,
                ApplicationStatus.UNDER_REVIEW,
                ApplicationStatus.INCOMPLETE,
                ApplicationStatus.ELIGIBLE,
                ApplicationStatus.NOT_ELIGIBLE,
                ApplicationStatus.UNDER_ACADEMIC_REVIEW,
                ApplicationStatus.ADMITTED,
                ApplicationStatus.REJECTED,
                ApplicationStatus.OFFERED,
                ApplicationStatus.ACCEPTED,
                ApplicationStatus.DECLINED,
                ApplicationStatus.WITHDRAWN,
                ApplicationStatus.CONVERTED),
            countAtLeast(
                byStatus,
                ApplicationStatus.ELIGIBLE,
                ApplicationStatus.UNDER_ACADEMIC_REVIEW,
                ApplicationStatus.ADMITTED,
                ApplicationStatus.REJECTED,
                ApplicationStatus.OFFERED,
                ApplicationStatus.ACCEPTED,
                ApplicationStatus.DECLINED,
                ApplicationStatus.WITHDRAWN,
                ApplicationStatus.CONVERTED),
            countAtLeast(
                byStatus,
                ApplicationStatus.ADMITTED,
                ApplicationStatus.OFFERED,
                ApplicationStatus.ACCEPTED,
                ApplicationStatus.DECLINED,
                ApplicationStatus.WITHDRAWN,
                ApplicationStatus.CONVERTED),
            countAtLeast(
                byStatus,
                ApplicationStatus.OFFERED,
                ApplicationStatus.ACCEPTED,
                ApplicationStatus.DECLINED,
                ApplicationStatus.WITHDRAWN,
                ApplicationStatus.CONVERTED),
            countAtLeast(byStatus, ApplicationStatus.ACCEPTED, ApplicationStatus.CONVERTED),
            countAtLeast(byStatus, ApplicationStatus.CONVERTED),
            actorUserId,
            clock.instant());
    return AdmissionCycleArchiveSummaryView.from(
        admissionCycleArchiveSummaryRepository.saveAndFlush(summary));
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
      String advancedRulesVersion,
      List<QualificationRequirementGroupInput> qualificationGroups) {
    ApplicationType applicationType =
        applicationTypeRepository
            .findById(applicationTypeId)
            .orElseThrow(() -> new IllegalArgumentException("Application type not found."));
    String advancedRulesJson = advancedRules == null ? null : serialize(advancedRules);
    AdmissionRequirementSet requirementSet =
        requirementSetRepository.saveAndFlush(
            new AdmissionRequirementSet(
                programmeId,
                applicationType,
                intakeId,
                requiredText(versionCode, "Requirement-set version"),
                effectiveFrom,
                effectiveTo,
                minimumTotalPoints,
                maleCutoffPoints,
                femaleCutoffPoints,
                requiresEnglish,
                requiresMathematicsOrScience,
                advancedRulesJson,
                advancedRulesJson == null
                    ? null
                    : requiredText(advancedRulesVersion, "Advanced-rules version")));
    java.util.Set<String> groupCodes = new java.util.HashSet<>();
    for (QualificationRequirementGroupInput input :
        qualificationGroups == null
            ? List.<QualificationRequirementGroupInput>of()
            : qualificationGroups) {
      String normalizedCode =
          requiredText(input.code(), "Qualification group code").toUpperCase(Locale.ROOT);
      if (!groupCodes.add(normalizedCode))
        throw new IllegalArgumentException("Qualification group codes must be unique.");
      if (input.items() == null
          || input.items().isEmpty()
          || input.minimumSatisfiedItems() > input.items().size()) {
        throw new IllegalArgumentException(
            "Qualification group minimum must be achievable by its configured items.");
      }
      AdmissionQualificationRequirementGroup group =
          qualificationGroupRepository.saveAndFlush(
              new AdmissionQualificationRequirementGroup(
                  requirementSet,
                  normalizedCode,
                  input.name(),
                  input.minimumSatisfiedItems(),
                  input.sortOrder()));
      qualificationItemRepository.saveAll(
          input.items().stream()
              .map(
                  item ->
                      new AdmissionQualificationRequirementItem(
                          group,
                          qualificationLevel(item.qualificationLevel()),
                          item.minimumCount(),
                          item.minimumTotalPoints(),
                          item.minimumDurationMonths(),
                          item.sortOrder()))
              .toList());
    }
    return requirementSetSummary(requirementSet);
  }

  @Transactional
  public AdmissionRequirementSetSummary approveRequirementSet(
      UUID requirementSetId, UUID actorUserId) {
    AdmissionRequirementSet requirementSet =
        requirementSetRepository
            .findById(requirementSetId)
            .orElseThrow(
                () -> new IllegalArgumentException("Admission requirement set not found."));
    if (requirementSet.getAdvancedRulesJson() != null) {
      advancedRuleEvaluator.validate(
          requirementSet.getAdvancedRulesVersion(), requirementSet.getAdvancedRulesJson());
    }
    List<AdmissionRequirementSet> supersededRequirementSets =
        requirementSetRepository
            .findApprovedForRouteForUpdate(
                requirementSet.getProgrammeId(),
                requirementSet.getApplicationType().getId(),
                requirementSet.getIntakeId())
            .stream()
            .filter(existing -> !existing.getId().equals(requirementSet.getId()))
            .filter(existing -> existing.overlapsEffectivePeriod(requirementSet))
            .toList();
    supersededRequirementSets.forEach(AdmissionRequirementSet::retire);
    if (!supersededRequirementSets.isEmpty()) {
      requirementSetRepository.saveAllAndFlush(supersededRequirementSets);
    }
    requirementSet.approve(actorUserId, clock.instant());
    return requirementSetSummary(requirementSetRepository.saveAndFlush(requirementSet));
  }

  @Transactional
  public List<AdmissionRequirementSetSummary> listRequirementSets() {
    return requirementSetRepository.findAllByDeletedAtIsNullOrderByEffectiveFromDesc().stream()
        .map(this::requirementSetSummary)
        .toList();
  }

  private AdmissionRequirementSetSummary requirementSetSummary(
      AdmissionRequirementSet requirementSet) {
    return AdmissionRequirementSetSummary.from(requirementSet)
        .withQualificationGroups(
            qualificationGroupRepository
                .findAllByRequirementSetIdAndDeletedAtIsNullOrderBySortOrderAsc(
                    requirementSet.getId())
                .stream()
                .map(
                    group ->
                        new AdmissionRequirementSetSummary.QualificationRequirementGroupSummary(
                            group.getId(),
                            group.getGroupCode(),
                            group.getName(),
                            group.getMinimumSatisfiedItems(),
                            group.getSortOrder(),
                            qualificationItemRepository
                                .findAllByRequirementGroupIdAndDeletedAtIsNullOrderBySortOrderAsc(
                                    group.getId())
                                .stream()
                                .map(
                                    item ->
                                        new AdmissionRequirementSetSummary
                                            .QualificationRequirementItemSummary(
                                            item.getId(),
                                            item.getQualificationLevel().name(),
                                            item.getMinimumCount(),
                                            item.getMinimumTotalPoints(),
                                            item.getMinimumDurationMonths(),
                                            item.getSortOrder()))
                                .toList()))
                .toList());
  }

  @Transactional
  public EvaluationSummary recordEvaluation(RecordEvaluationCommand command) {
    Application application = application(command.applicationId());
    ApplicationProgrammeChoice choice = programmeChoice(command.programmeChoiceId());
    if (!choice.getApplication().getId().equals(application.getId())) {
      throw new IllegalArgumentException("Programme choice does not belong to the application.");
    }
    AdmissionRequirementSet requirementSet =
        requirementSetRepository
            .findById(command.requirementSetId())
            .orElseThrow(
                () -> new IllegalArgumentException("Admission requirement set not found."));
    if (!requirementSet.isApprovedAndEffectiveFor(
        choice.getProgrammeId(),
        application.getApplicationType().getId(),
        application.getIntakeId(),
        LocalDate.now(clock))) {
      throw new IllegalStateException(
          "The requirement set is not approved and effective for this application route.");
    }
    if (evaluationRepository.existsByProgrammeChoiceIdAndRequirementSetIdAndDeletedAtIsNull(
        choice.getId(), requirementSet.getId())) {
      throw new IllegalStateException(
          "This programme choice has already been evaluated against that requirement-set version.");
    }

    EvaluationStatus evaluationStatus =
        parseEnum(EvaluationStatus.class, command.status(), "evaluation status");
    QualificationEligibilityService.RequirementEvaluation requirementEvaluation =
        qualificationEligibilityService.evaluateRequirements(application, requirementSet);
    if (evaluationStatus == EvaluationStatus.ELIGIBLE
        && !requirementEvaluation.missingRequirements().isEmpty()) {
      throw new IllegalStateException(
          "Application does not satisfy the approved requirement set: "
              + String.join(", ", requirementEvaluation.missingRequirements()));
    }
    List<Map<String, Object>> evaluationMissingEvidence =
        new java.util.ArrayList<>(requirementEvaluation.missingRequirementEvidence());
    if (command.missingRequirements() != null) {
      evaluationMissingEvidence.addAll(command.missingRequirements());
    }
    Map<String, Object> evaluationRuleEvidence =
        new java.util.LinkedHashMap<>(requirementEvaluation.ruleEvidence());
    if (command.ruleResults() != null && !command.ruleResults().isEmpty()) {
      evaluationRuleEvidence.put("officerEvidence", command.ruleResults());
    }
    Instant evaluatedAt = clock.instant();
    ApplicationStatus previousApplicationStatus = application.getStatus();
    ApplicationEvaluation evaluation =
        evaluationRepository.save(
            new ApplicationEvaluation(
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
    choice.recordEvaluation(
        evaluationStatus, requiredText(command.summary(), "Evaluation summary"));

    List<ApplicationProgrammeChoice> applicationChoices =
        programmeChoiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(application.getId());
    boolean anyEligible =
        applicationChoices.stream()
            .anyMatch(item -> item.getChoiceStatus() == ProgrammeChoiceStatus.ELIGIBLE);
    boolean allFinal =
        applicationChoices.stream()
            .allMatch(
                item ->
                    item.getChoiceStatus() == ProgrammeChoiceStatus.ELIGIBLE
                        || item.getChoiceStatus() == ProgrammeChoiceStatus.INELIGIBLE);
    application.applyEvaluationOutcome(anyEligible, allFinal, command.summary());
    recordApplicationStatusChange(
        application, previousApplicationStatus, command.summary(), command.actorUserId());
    return EvaluationSummary.from(evaluation, application.getId());
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
    return selectionDecisionRepository
        .findAllBySelectionRoundIdAndDeletedAtIsNullOrderByRankPositionAsc(selectionRoundId)
        .stream()
        .map(SelectionDecisionSummary::from)
        .toList();
  }

  @Transactional
  public List<OfferBatchSummary> listOfferBatches() {
    return offerBatchRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc().stream()
        .map(OfferBatchSummary::from)
        .toList();
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
    recordApplicationStatusChange(
        application, previousApplicationStatus, "Offer approved for dispatch.", actorUserId);
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
    offerDispatchRepository.save(
        new OfferDispatch(offer, deliveryMethodCode, sentTo, providerMessageId, now));
    recordOfferStatusChange(offer, previousStatus, "Offer dispatched to applicant.", actorUserId);
    integrationOutboxService.enqueueOfferDispatchedNotification(offer);
    return offerSummary(offer);
  }

  @Transactional
  public AdmissionOfferSummary resolveOfferCondition(
      UUID offerId, UUID conditionId, String resolutionCode, String notes, UUID actorUserId) {
    AdmissionOffer offer = offer(offerId);
    if (offer.getStatus() != OfferStatus.SENT && offer.getStatus() != OfferStatus.ACCEPTED) {
      throw new IllegalStateException(
          "Conditions can only be resolved for a sent or accepted offer.");
    }
    OfferCondition condition =
        offerConditionRepository
            .findByIdAndOfferIdAndDeletedAtIsNull(conditionId, offerId)
            .orElseThrow(() -> new IllegalArgumentException("Offer condition not found."));
    OfferConditionStatus resolution =
        parseEnum(OfferConditionStatus.class, resolutionCode, "offer condition resolution");
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
    recordApplicationStatusChange(
        application, previousApplicationStatus, "Offer withdrawn: " + reason, actorUserId);
    integrationOutboxService.enqueueCurrentOfferPublicationStatus(offer);
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
    integrationOutboxService.enqueueCurrentOfferPublicationStatus(offer);
    return offerSummary(offer);
  }

  @Transactional
  public boolean hasUnresolvedRequiredConditions(UUID offerId) {
    offer(offerId);
    return offerConditionRepository.countByOfferIdAndRequiredTrueAndStatusAndDeletedAtIsNull(
            offerId, OfferConditionStatus.PENDING)
        > 0;
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
      throw new IllegalStateException(
          "Required offer conditions must be resolved before conversion completion.");
    }
    OfferStatus previousOfferStatus = offer.getStatus();
    ApplicationStatus previousApplicationStatus = application.getStatus();
    offer.markConverted(conversionRequestId, studentId, studentNumber, clock.instant());
    application.markConverted("Converted to student " + studentNumber + ".");
    offer.getProgrammeChoice().markConverted("Converted to student " + studentNumber + ".");
    recordOfferStatusChange(
        offer, previousOfferStatus, "Student conversion completed.", actorUserId);
    recordApplicationStatusChange(
        application, previousApplicationStatus, "Student conversion completed.", actorUserId);
    integrationOutboxService.enqueueCurrentOfferPublicationStatus(offer);
    integrationOutboxService.enqueueStudentConversionNotification(offer);
  }

  @Transactional
  public AdmissionOfferSummary respondToOffer(
      UUID offerId, UUID applicantUserId, String responseCode, String notes) {
    AdmissionOffer offer =
        offerRepository
            .findByIdAndApplicationApplicantUserIdAndDeletedAtIsNull(offerId, applicantUserId)
            .orElseThrow(() -> new IllegalArgumentException("Offer not found."));
    OfferResponseType responseType =
        parseEnum(OfferResponseType.class, responseCode, "offer response");
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
    OfferResponse response =
        offerResponseRepository.saveAndFlush(
            new OfferResponse(
                offer, offer.getCurrentPublication(), responseType, now, applicantUserId, notes));
    OfferStatus previousOfferStatus = offer.getStatus();
    Application application = offer.getApplication();
    ApplicationStatus previousApplicationStatus = application.getStatus();
    offer.respond(responseType);
    application.recordOfferResponse(
        responseType, "Applicant " + responseType.name().toLowerCase(Locale.ROOT) + " the offer.");
    offer.getProgrammeChoice().recordOfferResponse(responseType, "Applicant response recorded.");
    if (responseType == OfferResponseType.ACCEPTED) {
      enqueueConversionWhenReady(offer, now);
    }
    recordOfferStatusChange(
        offer, previousOfferStatus, "Applicant response recorded.", applicantUserId);
    recordApplicationStatusChange(
        application, previousApplicationStatus, "Applicant response recorded.", applicantUserId);
    integrationOutboxService.enqueueCurrentOfferPublicationStatus(offer);
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
    return offerRepository
        .findAllByApplicationApplicantUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(applicantUserId)
        .stream()
        .filter(
            offer ->
                offer.getStatus() != OfferStatus.DRAFT && offer.getStatus() != OfferStatus.APPROVED)
        .map(this::offerSummary)
        .toList();
  }

  private AdmissionOfferSummary offerSummary(AdmissionOffer offer) {
    return AdmissionOfferSummary.from(
        offer,
        offerConditionRepository.findAllByOfferIdAndDeletedAtIsNullOrderByConditionCodeAsc(
            offer.getId()),
        offerResponseRepository.findByOfferId(offer.getId()).orElse(null));
  }

  private void enqueueConversionWhenReady(AdmissionOffer offer, Instant now) {
    if (offer.getConversionEventId() != null) return;
    long pendingRequiredConditions =
        offerConditionRepository.countByOfferIdAndRequiredTrueAndStatusAndDeletedAtIsNull(
            offer.getId(), OfferConditionStatus.PENDING);
    if (pendingRequiredConditions == 0) {
      UUID eventId = offer.requestConversion(now);
      integrationOutboxService.enqueueAcceptedOfferReadyForConversion(eventId, offer);
    }
  }

  private void recordApplicationStatusChange(
      Application application, ApplicationStatus previousStatus, String reason, UUID actorUserId) {
    if (previousStatus != application.getStatus()) {
      applicationStatusEventRepository.save(
          new ApplicationStatusEvent(
              application, previousStatus, application.getStatus(), reason, actorUserId));
    }
  }

  private void recordOfferStatusChange(
      AdmissionOffer offer, OfferStatus previousStatus, String reason, UUID actorUserId) {
    offerStatusEventRepository.save(
        new OfferStatusEvent(
            offer, previousStatus, offer.getStatus(), reason, actorUserId, clock.instant()));
  }

  private AdmissionCycle admissionCycle(UUID id) {
    return admissionCycleRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Admission cycle not found."));
  }

  private ApplicationType applicationType(UUID id) {
    return applicationTypeRepository
        .findById(id)
        .filter(applicationType -> !applicationType.isDeleted())
        .orElseThrow(() -> new IllegalArgumentException("Application type not found."));
  }

  private Application application(UUID id) {
    return applicationRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Application not found."));
  }

  private ApplicationProgrammeChoice programmeChoice(UUID id) {
    return programmeChoiceRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Programme choice not found."));
  }

  private SelectionRound selectionRound(UUID id) {
    return selectionRoundRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Selection round not found."));
  }

  private AdmissionOffer offer(UUID id) {
    return offerRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Offer not found."));
  }

  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new IllegalArgumentException("Evaluation evidence could not be serialized.", exception);
    }
  }

  private QualificationLevel qualificationLevel(String value) {
    return parseEnum(QualificationLevel.class, value, "qualification level");
  }

  public record QualificationRequirementGroupInput(
      String code,
      String name,
      int minimumSatisfiedItems,
      int sortOrder,
      List<QualificationRequirementItemInput> items) {}

  public record QualificationRequirementItemInput(
      String qualificationLevel,
      int minimumCount,
      java.math.BigDecimal minimumTotalPoints,
      Integer minimumDurationMonths,
      int sortOrder) {}

  private static String requiredText(String value, String label) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(label + " is required.");
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
