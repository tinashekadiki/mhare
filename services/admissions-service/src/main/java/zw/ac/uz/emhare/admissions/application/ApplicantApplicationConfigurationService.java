package zw.ac.uz.emhare.admissions.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.admissions.application.ApplicationStartOptionsSummary.AdmissionIntakeOption;
import zw.ac.uz.emhare.admissions.application.ApplicationStartOptionsSummary.ApplicantCategoryOption;
import zw.ac.uz.emhare.admissions.application.ApplicationStartOptionsSummary.ApplicationFeeOption;
import zw.ac.uz.emhare.admissions.application.ApplicationStartOptionsSummary.ApplicationSectionOption;
import zw.ac.uz.emhare.admissions.application.ApplicationStartOptionsSummary.ApplicationTypeOption;
import zw.ac.uz.emhare.admissions.application.ApplicationStartOptionsSummary.ProgrammeOption;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.AcademicAdmissionsIntake;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient;

@Service
public class ApplicantApplicationConfigurationService {

    private final AdmissionsIntakeProjectionService admissionsIntakeProjectionService;
    private final ApplicationTypeRepository applicationTypeRepository;
    private final ApplicationFeeRepository applicationFeeRepository;
    private final ApplicationTypeSectionRepository applicationTypeSectionRepository;
    private final FinanceCatalogueClient financeCatalogueClient;
    private final Clock clock;

    public ApplicantApplicationConfigurationService(
            AdmissionsIntakeProjectionService admissionsIntakeProjectionService,
            ApplicationTypeRepository applicationTypeRepository,
            ApplicationFeeRepository applicationFeeRepository,
            ApplicationTypeSectionRepository applicationTypeSectionRepository,
            FinanceCatalogueClient financeCatalogueClient,
            Clock clock) {
        this.admissionsIntakeProjectionService = admissionsIntakeProjectionService;
        this.applicationTypeRepository = applicationTypeRepository;
        this.applicationFeeRepository = applicationFeeRepository;
        this.applicationTypeSectionRepository = applicationTypeSectionRepository;
        this.financeCatalogueClient = financeCatalogueClient;
        this.clock = clock;
    }

    @Transactional
    public ApplicationStartOptionsSummary getStartOptions(String applicantCategoryValue) {
        ApplicantCategoryCode applicantCategory = ApplicantCategoryCode.from(applicantCategoryValue);
        LocalDate effectiveDate = LocalDate.now(clock);

        List<AdmissionIntakeOption> intakes = admissionsIntakeProjectionService.openIntakes().stream()
                .sorted(Comparator.comparing(
                                (AcademicAdmissionsIntake value) -> value.endsOn())
                        .thenComparing(value -> value.name()))
                .map(this::toAdmissionIntakeOption)
                .toList();

        List<ApplicationTypeOption> applicationTypes = applicationTypeRepository.findAll().stream()
                .filter(ApplicationType::isActive)
                .sorted(Comparator.comparing(ApplicationType::getName))
                .map(applicationType -> toApplicationTypeOption(applicationType, applicantCategory, effectiveDate))
                .toList();

        return new ApplicationStartOptionsSummary(
                applicantCategory.name(),
                getApplicantCategories(),
                intakes,
                applicationTypes);
    }

    public List<ApplicantCategoryOption> getApplicantCategories() {
        return Arrays.stream(ApplicantCategoryCode.values())
                .map(category -> new ApplicantCategoryOption(category.name(), category.label()))
                .toList();
    }

    private AdmissionIntakeOption toAdmissionIntakeOption(
            AcademicAdmissionsIntake intake) {
        List<ProgrammeOption> programmes = intake.programmes().stream()
                .map(programme -> new ProgrammeOption(
                        programme.programmeId(), programme.programmeVersionId(),
                        programme.programmeCode(), programme.programmeName(), programme.awardName(),
                        programme.owningAcademicUnitName(), programme.programmeVersionCode()))
                .toList();
        return new AdmissionIntakeOption(
                intake.intakeId(), intake.code(), intake.name(), intake.startsOn(), intake.endsOn(),
                intake.maximumProgrammeChoices(),
                programmes);
    }

    private ApplicationTypeOption toApplicationTypeOption(
            ApplicationType applicationType,
            ApplicantCategoryCode applicantCategory,
            LocalDate effectiveDate) {
        return new ApplicationTypeOption(
                applicationType.getId(),
                applicationType.getCode(),
                applicationType.getName(),
                applicationType.requiresEmploymentHistory(),
                applicationType.requiresReferees(),
                resolveApplicationFeeOption(applicationType, applicantCategory, effectiveDate),
                sectionOptions(applicationType));
    }

    private ApplicationFeeOption resolveApplicationFeeOption(
            ApplicationType applicationType,
            ApplicantCategoryCode applicantCategory,
            LocalDate effectiveDate) {
        if (applicationType.getFinanceFeeStructureId() != null) {
            var pricing = financeCatalogueClient.getApplicationFeeStructurePricing(applicationType.getFinanceFeeStructureId());
            boolean paymentRequired = "ACTIVE".equals(pricing.status()) && pricing.totalTransactionAmount().signum() > 0;
            return new ApplicationFeeOption(paymentRequired, pricing.totalTransactionAmount(), pricing.transactionCurrencyCode());
        }

        List<ApplicationFee> effectiveFees = applicationFeeRepository.findEffectiveFees(
                applicationType.getId(), applicantCategory.name(), effectiveDate);
        if (effectiveFees.size() > 1) {
            throw new IllegalStateException(
                    "Multiple effective application fees are configured for application type "
                            + applicationType.getCode() + " and applicant category " + applicantCategory.name() + ".");
        }
        ApplicationFee applicationFee = effectiveFees.stream().findFirst().orElse(null);
        boolean paymentRequired = applicationFee != null && applicationFee.getAmount().signum() > 0;
        return applicationFee == null
                ? new ApplicationFeeOption(false, null, null)
                : new ApplicationFeeOption(paymentRequired, applicationFee.getAmount(), applicationFee.getCurrencyCode());
    }

    private List<ApplicationSectionOption> sectionOptions(ApplicationType applicationType) {
        List<ApplicationSectionOption> configuredSections = applicationTypeSectionRepository
                .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAsc(applicationType.getId())
                .stream()
                .map(section -> new ApplicationSectionOption(
                        section.getSectionCode(),
                        section.getSectionName(),
                        section.isRequired(),
                        section.isRepeatable(),
                        section.getMinimumRecords(),
                        section.getSortOrder()))
                .toList();
        if (!configuredSections.isEmpty()) return configuredSections;
        return ApplicationSectionTemplate.defaults(applicationType).stream()
                .map(section -> new ApplicationSectionOption(
                        section.code(), section.name(), section.required(), section.repeatable(),
                        section.minimumRecords(), section.sortOrder()))
                .toList();
    }
}
