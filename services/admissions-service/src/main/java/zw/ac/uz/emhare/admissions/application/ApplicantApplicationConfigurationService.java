package zw.ac.uz.emhare.admissions.application;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.admissions.application.ApplicationStartOptionsSummary.AdmissionIntakeOption;
import zw.ac.uz.emhare.admissions.application.ApplicationStartOptionsSummary.ApplicantCategoryOption;
import zw.ac.uz.emhare.admissions.application.ApplicationStartOptionsSummary.ApplicationFeeOption;
import zw.ac.uz.emhare.admissions.application.ApplicationStartOptionsSummary.ApplicationRouteOption;
import zw.ac.uz.emhare.admissions.application.ApplicationStartOptionsSummary.ApplicationSectionOption;
import zw.ac.uz.emhare.admissions.application.ApplicationStartOptionsSummary.ApplicationTypeOption;
import zw.ac.uz.emhare.admissions.application.ApplicationStartOptionsSummary.EntryOption;
import zw.ac.uz.emhare.admissions.application.ApplicationStartOptionsSummary.ProgrammeOption;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantCategoryCode;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationType;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeProgrammeMappingRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeSectionRepository;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.AcademicAdmissionsIntake;

@Service
public class ApplicantApplicationConfigurationService {

  private final AdmissionsIntakeProjectionService admissionsIntakeProjectionService;
  private final ApplicationTypeRepository applicationTypeRepository;
  private final ApplicationTypeSectionRepository applicationTypeSectionRepository;
  private final ApplicationTypeProgrammeMappingRepository programmeMappingRepository;

  public ApplicantApplicationConfigurationService(
      AdmissionsIntakeProjectionService admissionsIntakeProjectionService,
      ApplicationTypeRepository applicationTypeRepository,
      ApplicationTypeSectionRepository applicationTypeSectionRepository,
      ApplicationTypeProgrammeMappingRepository programmeMappingRepository) {
    this.admissionsIntakeProjectionService = admissionsIntakeProjectionService;
    this.applicationTypeRepository = applicationTypeRepository;
    this.applicationTypeSectionRepository = applicationTypeSectionRepository;
    this.programmeMappingRepository = programmeMappingRepository;
  }

  @Transactional
  public ApplicationStartOptionsSummary getStartOptions(String applicantCategoryValue) {
    ApplicantCategoryCode applicantCategory = ApplicantCategoryCode.from(applicantCategoryValue);

    List<AcademicAdmissionsIntake> openIntakes =
        admissionsIntakeProjectionService.openIntakes().stream()
            .sorted(
                Comparator.comparing((AcademicAdmissionsIntake value) -> value.endsOn())
                    .thenComparing(value -> value.name()))
            .toList();
    List<AdmissionIntakeOption> intakes =
        openIntakes.stream().map(this::toAdmissionIntakeOption).toList();

    List<ApplicationType> activeApplicationTypes =
        applicationTypeRepository.findAll().stream()
            .filter(ApplicationType::isActive)
            .sorted(Comparator.comparing(ApplicationType::getName))
            .toList();
    List<ApplicationTypeOption> applicationTypes =
        activeApplicationTypes.stream().map(this::toApplicationTypeOption).toList();

    List<ApplicationRouteOption> routes =
        activeApplicationTypes.stream()
            .flatMap(
                applicationType ->
                    openIntakes.stream().map(intake -> routeOption(applicationType, intake)))
            .filter(route -> !route.programmes().isEmpty())
            .toList();

    return new ApplicationStartOptionsSummary(
        applicantCategory.name(), getApplicantCategories(), intakes, applicationTypes, routes);
  }

  public List<ApplicantCategoryOption> getApplicantCategories() {
    return Arrays.stream(ApplicantCategoryCode.values())
        .map(category -> new ApplicantCategoryOption(category.name(), category.label()))
        .toList();
  }

  private AdmissionIntakeOption toAdmissionIntakeOption(AcademicAdmissionsIntake intake) {
    List<ProgrammeOption> programmes =
        intake.programmes().stream().map(this::programmeOption).toList();
    return new AdmissionIntakeOption(
        intake.intakeId(),
        intake.code(),
        intake.name(),
        intake.startsOn(),
        intake.endsOn(),
        intake.maximumProgrammeChoices(),
        programmes);
  }

  private ApplicationRouteOption routeOption(
      ApplicationType applicationType, AcademicAdmissionsIntake intake) {
    java.util.Set<java.util.UUID> configuredProgrammeIds =
        programmeMappingRepository
            .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderByProgrammeCodeAsc(
                applicationType.getId())
            .stream()
            .map(mapping -> mapping.getProgrammeId())
            .collect(java.util.stream.Collectors.toSet());
    List<ProgrammeOption> programmes =
        intake.programmes().stream()
            .filter(programme -> configuredProgrammeIds.contains(programme.programmeId()))
            .map(this::programmeOption)
            .toList();
    return new ApplicationRouteOption(
        applicationType.getId(),
        applicationType.getCode(),
        applicationType.getName(),
        intake.intakeId(),
        intake.code(),
        intake.name(),
        intake.maximumProgrammeChoices(),
        programmes);
  }

  private ProgrammeOption programmeOption(
      zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.AcademicProgrammeOption
          programme) {
    return new ProgrammeOption(
        programme.programmeId(),
        programme.programmeVersionId(),
        programme.programmeCode(),
        programme.programmeName(),
        programme.awardName(),
        programme.owningAcademicUnitName(),
        programme.programmeVersionCode(),
        programme.programmeTypeCode(),
        programme.programmeTypeName(),
        programme.programmeLevelCode(),
        programme.programmeLevelName(),
        programme.minimumEntryOptionSelections(),
        programme.maximumEntryOptionSelections(),
        programme.entryOptions().stream()
            .map(
                option ->
                    new EntryOption(
                        option.id(),
                        option.code(),
                        option.name(),
                        option.description(),
                        option.sortOrder()))
            .toList());
  }

  private ApplicationTypeOption toApplicationTypeOption(ApplicationType applicationType) {
    return new ApplicationTypeOption(
        applicationType.getId(),
        applicationType.getCode(),
        applicationType.getName(),
        applicationType.requiresEmploymentHistory(),
        applicationType.requiresReferees(),
        resolveApplicationFeeOption(applicationType),
        sectionOptions(applicationType));
  }

  private ApplicationFeeOption resolveApplicationFeeOption(ApplicationType applicationType) {
    boolean financeResolutionRequired =
        "FEE_STRUCTURE".equals(applicationType.getFeePolicyStatus());
    return new ApplicationFeeOption(
        financeResolutionRequired, applicationType.getFeePolicyStatus(), null, null);
  }

  private List<ApplicationSectionOption> sectionOptions(ApplicationType applicationType) {
    List<ApplicationSectionOption> configuredSections =
        applicationTypeSectionRepository
            .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAsc(
                applicationType.getId())
            .stream()
            .map(
                section ->
                    new ApplicationSectionOption(
                        section.getSectionCode(),
                        section.getSectionName(),
                        section.isRequired(),
                        section.isRepeatable(),
                        section.getMinimumRecords(),
                        section.getSortOrder()))
            .toList();
    if (!configuredSections.isEmpty()) return configuredSections;
    return ApplicationSectionTemplate.defaults(applicationType).stream()
        .map(
            section ->
                new ApplicationSectionOption(
                    section.code(),
                    section.name(),
                    section.required(),
                    section.repeatable(),
                    section.minimumRecords(),
                    section.sortOrder()))
        .toList();
  }
}
