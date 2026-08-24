package zw.ac.uz.emhare.admissions.application;

import jakarta.transaction.Transactional;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import zw.ac.uz.emhare.admissions.api.model.ConfigureApplicationRouteRequest;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationType;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationTypeDocumentRequirement;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationTypeDocumentRequirementCategory;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationTypeProgrammeMapping;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationTypeSection;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeDocumentRequirementCategoryRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeDocumentRequirementRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeProgrammeMappingRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeSectionRepository;
import zw.ac.uz.emhare.common.persistence.EmhareRevisionContext;

/** Atomically configures and validates an admissions route before activation. @author Tinashe K */
@Service
public class ApplicationRouteConfigurationService {
  private static final Set<String> SUPPORTED_SECTIONS =
      Set.of(
          "PERSONAL_DETAILS",
          "NEXT_OF_KIN",
          "QUALIFICATIONS",
          "PRIOR_UZ_STUDY",
          "PROFESSIONAL_ACHIEVEMENTS",
          "EMPLOYMENT_HISTORY",
          "REFEREES",
          "PROGRAMME_CHOICES",
          "DOCUMENTS",
          "PAYMENT",
          "REVIEW_DECLARATION");

  private final ApplicationTypeRepository applicationTypeRepository;
  private final ApplicationTypeProgrammeMappingRepository mappingRepository;
  private final ApplicationTypeSectionRepository sectionRepository;
  private final ApplicationTypeDocumentRequirementRepository documentRepository;
  private final ApplicationTypeDocumentRequirementCategoryRepository documentCategoryRepository;
  private final Clock clock;

  public ApplicationRouteConfigurationService(
      ApplicationTypeRepository applicationTypeRepository,
      ApplicationTypeProgrammeMappingRepository mappingRepository,
      ApplicationTypeSectionRepository sectionRepository,
      ApplicationTypeDocumentRequirementRepository documentRepository,
      ApplicationTypeDocumentRequirementCategoryRepository documentCategoryRepository,
      Clock clock) {
    this.applicationTypeRepository = applicationTypeRepository;
    this.mappingRepository = mappingRepository;
    this.sectionRepository = sectionRepository;
    this.documentRepository = documentRepository;
    this.documentCategoryRepository = documentCategoryRepository;
    this.clock = clock;
  }

  @Transactional
  public ApplicationRouteConfigurationSummary configure(
      UUID applicationTypeId, UUID actorUserId, ConfigureApplicationRouteRequest request) {
    ApplicationType applicationType =
        applicationTypeRepository
            .findById(applicationTypeId)
            .orElseThrow(() -> new IllegalArgumentException("Application type was not found."));
    requireDistinct(
        request.programmes().stream()
            .map(ConfigureApplicationRouteRequest.ProgrammeMappingInput::programmeId)
            .toList(),
        "Programme mappings must be distinct.");
    requireDistinct(
        request.sections().stream().map(section -> normalize(section.code())).toList(),
        "Section codes must be distinct.");
    requireDistinct(
        request.documents().stream().map(document -> normalize(document.code())).toList(),
        "Document requirement codes must be distinct.");
    if (request.sections().stream()
        .map(section -> normalize(section.code()))
        .anyMatch(code -> !SUPPORTED_SECTIONS.contains(code))) {
      throw new IllegalArgumentException(
          "Route configuration contains an unsupported application section.");
    }

    String correlationId = EmhareRevisionContext.getCorrelationId().orElse(null);
    EmhareRevisionContext.setRequestMetadata(correlationId, request.changeReason());
    try {
      synchronizeMappings(applicationType, request.programmes());
      synchronizeSections(applicationType, request.sections());
      synchronizeDocuments(applicationType, request.documents(), actorUserId);
      if (request.feeFree()) {
        applicationType.recordFeeFreeDecision(
            actorUserId, request.feeFreeReason(), clock.instant());
      }
      List<String> blockers = readinessBlockers(applicationType);
      if (request.activate() && !blockers.isEmpty()) {
        throw new IllegalStateException(
            "Application route is not ready for activation: " + String.join("; ", blockers));
      }
      applicationType.setActive(request.activate(), request.expectedVersion());
      applicationTypeRepository.saveAndFlush(applicationType);
      return summary(applicationType, blockers);
    } finally {
      EmhareRevisionContext.setRequestMetadata(correlationId, null);
    }
  }

  @Transactional
  public ApplicationRouteConfigurationSummary configuration(UUID applicationTypeId) {
    ApplicationType applicationType =
        applicationTypeRepository
            .findById(applicationTypeId)
            .orElseThrow(() -> new IllegalArgumentException("Application type was not found."));
    return summary(applicationType, readinessBlockers(applicationType));
  }

  private void synchronizeMappings(
      ApplicationType applicationType,
      List<ConfigureApplicationRouteRequest.ProgrammeMappingInput> inputs) {
    Map<UUID, ApplicationTypeProgrammeMapping> existing =
        mappingRepository
            .findAllByApplicationTypeIdAndDeletedAtIsNull(applicationType.getId())
            .stream()
            .collect(
                Collectors.toMap(
                    ApplicationTypeProgrammeMapping::getProgrammeId, Function.identity()));
    Set<UUID> requested =
        inputs.stream()
            .map(ConfigureApplicationRouteRequest.ProgrammeMappingInput::programmeId)
            .collect(Collectors.toSet());
    existing.values().stream()
        .filter(mapping -> !requested.contains(mapping.getProgrammeId()))
        .forEach(ApplicationTypeProgrammeMapping::deactivate);
    inputs.forEach(
        input ->
            existing
                .computeIfAbsent(
                    input.programmeId(),
                    ignored ->
                        new ApplicationTypeProgrammeMapping(
                            applicationType,
                            input.programmeId(),
                            input.programmeCode(),
                            input.programmeName()))
                .refresh(input.programmeCode(), input.programmeName()));
    mappingRepository.saveAllAndFlush(existing.values());
  }

  private void synchronizeSections(
      ApplicationType applicationType, List<ConfigureApplicationRouteRequest.SectionInput> inputs) {
    Map<String, ApplicationTypeSection> existing =
        sectionRepository
            .findAllByApplicationTypeIdAndDeletedAtIsNull(applicationType.getId())
            .stream()
            .collect(
                Collectors.toMap(
                    section -> normalize(section.getSectionCode()), Function.identity()));
    Set<String> requested =
        inputs.stream().map(input -> normalize(input.code())).collect(Collectors.toSet());
    existing.entrySet().stream()
        .filter(entry -> !requested.contains(entry.getKey()))
        .forEach(entry -> entry.getValue().deactivate());
    inputs.forEach(
        input ->
            existing
                .computeIfAbsent(
                    normalize(input.code()),
                    ignored ->
                        new ApplicationTypeSection(
                            applicationType,
                            normalize(input.code()),
                            input.name(),
                            input.required(),
                            input.repeatable(),
                            input.minimumRecords(),
                            input.sortOrder()))
                .configure(
                    input.name(),
                    input.required(),
                    input.repeatable(),
                    input.minimumRecords(),
                    input.sortOrder()));
    sectionRepository.saveAllAndFlush(existing.values());
  }

  private void synchronizeDocuments(
      ApplicationType applicationType,
      List<ConfigureApplicationRouteRequest.DocumentInput> inputs,
      UUID actorUserId) {
    Map<String, ApplicationTypeDocumentRequirement> existing =
        documentRepository
            .findAllByApplicationTypeIdAndDeletedAtIsNull(applicationType.getId())
            .stream()
            .collect(
                Collectors.toMap(
                    document -> normalize(document.getRequirementCode()), Function.identity()));
    Set<String> requested =
        inputs.stream().map(input -> normalize(input.code())).collect(Collectors.toSet());
    existing.entrySet().stream()
        .filter(entry -> !requested.contains(entry.getKey()))
        .forEach(entry -> entry.getValue().deactivate());
    inputs.forEach(
        input ->
            existing
                .computeIfAbsent(
                    normalize(input.code()),
                    ignored ->
                        new ApplicationTypeDocumentRequirement(
                            applicationType,
                            normalize(input.code()),
                            input.name(),
                            input.required(),
                            normalize(input.captureSectionCode()),
                            input.sortOrder()))
                .configure(
                    input.name(),
                    input.required(),
                    normalize(input.captureSectionCode()),
                    input.sortOrder()));
    documentRepository.saveAllAndFlush(existing.values());
    synchronizeDocumentCategories(existing, inputs, actorUserId);
  }

  private void synchronizeDocumentCategories(
      Map<String, ApplicationTypeDocumentRequirement> requirements,
      List<ConfigureApplicationRouteRequest.DocumentInput> inputs,
      UUID actorUserId) {
    for (ConfigureApplicationRouteRequest.DocumentInput input : inputs) {
      ApplicationTypeDocumentRequirement requirement = requirements.get(normalize(input.code()));
      List<ApplicationTypeDocumentRequirementCategory> current =
          documentCategoryRepository.findAllByDocumentRequirementIdAndDeletedAtIsNull(
              requirement.getId());
      Set<String> requested =
          input.applicantCategoryCodes().stream()
              .map(this::normalizeApplicantCategory)
              .collect(Collectors.toCollection(LinkedHashSet::new));
      current.stream()
          .filter(category -> !requested.contains(category.getApplicantCategoryCode()))
          .forEach(category -> category.markDeleted(actorUserId));
      Set<String> existingCodes =
          current.stream()
              .filter(category -> !category.isDeleted())
              .map(ApplicationTypeDocumentRequirementCategory::getApplicantCategoryCode)
              .collect(Collectors.toSet());
      requested.stream()
          .filter(code -> !existingCodes.contains(code))
          .forEach(
              code ->
                  current.add(new ApplicationTypeDocumentRequirementCategory(requirement, code)));
      documentCategoryRepository.saveAll(current);
    }
    documentCategoryRepository.flush();
  }

  private List<String> readinessBlockers(ApplicationType applicationType) {
    List<String> blockers = new ArrayList<>();
    if (mappingRepository
        .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderByProgrammeCodeAsc(
            applicationType.getId())
        .isEmpty()) blockers.add("at least one active programme mapping is required");
    List<ApplicationTypeSection> sections =
        sectionRepository
            .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAsc(
                applicationType.getId());
    validateRouteEvidence(applicationType.getCode(), sections, blockers);
    if (documentRepository
        .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscRequirementCodeAsc(
            applicationType.getId())
        .stream()
        .noneMatch(ApplicationTypeDocumentRequirement::isRequired)) {
      blockers.add("at least one required document definition is required");
    }
    if ("UNCONFIGURED".equals(applicationType.getFeePolicyStatus())) {
      blockers.add("an explicit fee structure or audited fee-free decision is required");
    }
    return List.copyOf(blockers);
  }

  private void validateRouteEvidence(
      String routeCode, List<ApplicationTypeSection> sections, List<String> blockers) {
    Map<String, ApplicationTypeSection> byCode =
        sections.stream()
            .collect(Collectors.toMap(ApplicationTypeSection::getSectionCode, Function.identity()));
    for (String common :
        List.of(
            "PERSONAL_DETAILS",
            "NEXT_OF_KIN",
            "QUALIFICATIONS",
            "PROGRAMME_CHOICES",
            "PAYMENT",
            "REVIEW_DECLARATION")) {
      if (!isRequired(byCode.get(common))) blockers.add(common + " must be a required section");
    }
    int referenceThreshold =
        switch (routeCode) {
          case "POSTGRAD" -> 2;
          case "MBA", "EDUCATION" -> 3;
          default -> 0;
        };
    if (referenceThreshold > 0) {
      ApplicationTypeSection referees = byCode.get("REFEREES");
      if (!isRequired(referees) || referees.getMinimumRecords() != referenceThreshold) {
        blockers.add(
            "REFEREES must require exactly " + referenceThreshold + " completed responses");
      }
      if (!isRequired(byCode.get("EMPLOYMENT_HISTORY")))
        blockers.add("EMPLOYMENT_HISTORY must be required");
    }
    if ("MBA".equals(routeCode)) {
      if (!isRequired(byCode.get("PRIOR_UZ_STUDY")))
        blockers.add("PRIOR_UZ_STUDY must be required");
      if (!isRequired(byCode.get("PROFESSIONAL_ACHIEVEMENTS")))
        blockers.add("PROFESSIONAL_ACHIEVEMENTS must be required");
    }
  }

  private ApplicationRouteConfigurationSummary summary(
      ApplicationType applicationType, List<String> blockers) {
    List<ApplicationTypeProgrammeMapping> mappings =
        mappingRepository
            .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderByProgrammeCodeAsc(
                applicationType.getId());
    List<ApplicationTypeSection> sections =
        sectionRepository
            .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAsc(
                applicationType.getId());
    List<ApplicationTypeDocumentRequirement> documents =
        documentRepository
            .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscRequirementCodeAsc(
                applicationType.getId());
    Map<UUID, List<String>> categoriesByRequirement =
        documentCategoryRepository
            .findAllByDocumentRequirementIdInAndDeletedAtIsNull(
                documents.stream().map(ApplicationTypeDocumentRequirement::getId).toList())
            .stream()
            .collect(
                Collectors.groupingBy(
                    category -> category.getDocumentRequirement().getId(),
                    Collectors.mapping(
                        ApplicationTypeDocumentRequirementCategory::getApplicantCategoryCode,
                        Collectors.toList())));
    return new ApplicationRouteConfigurationSummary(
        applicationType.getId(),
        applicationType.getCode(),
        applicationType.getName(),
        applicationType.isActive(),
        blockers.isEmpty(),
        blockers,
        mappings.size(),
        mappings.stream()
            .map(
                mapping ->
                    new ApplicationRouteConfigurationSummary.ProgrammeMappingSummary(
                        mapping.getProgrammeId(),
                        mapping.getProgrammeCode(),
                        mapping.getProgrammeName()))
            .toList(),
        sections.stream()
            .map(
                section ->
                    new ApplicationStartOptionsSummary.ApplicationSectionOption(
                        section.getSectionCode(),
                        section.getSectionName(),
                        section.isRequired(),
                        section.isRepeatable(),
                        section.getMinimumRecords(),
                        section.getSortOrder()))
            .toList(),
        (int) documents.stream().filter(ApplicationTypeDocumentRequirement::isRequired).count(),
        documents.stream()
            .map(
                document ->
                    new ApplicationRouteConfigurationSummary.DocumentRequirementSummary(
                        document.getRequirementCode(),
                        document.getRequirementName(),
                        document.isRequired(),
                        document.getCaptureSectionCode(),
                        categoriesByRequirement.getOrDefault(document.getId(), List.of()),
                        document.getSortOrder()))
            .toList(),
        applicationType.getFeePolicyStatus(),
        applicationType.getVersion());
  }

  private boolean isRequired(ApplicationTypeSection section) {
    return section != null && section.isRequired();
  }

  private String normalize(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeApplicantCategory(String value) {
    return zw.ac.uz.emhare.admissions.domain.model.ApplicantCategoryCode.from(normalize(value))
        .name();
  }

  private void requireDistinct(List<?> values, String message) {
    if (new LinkedHashSet<>(values).size() != values.size())
      throw new IllegalArgumentException(message);
  }
}
