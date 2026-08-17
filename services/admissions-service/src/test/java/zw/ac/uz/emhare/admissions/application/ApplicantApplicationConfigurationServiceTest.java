package zw.ac.uz.emhare.admissions.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionCycle;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationType;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationTypeProgrammeMapping;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeProgrammeMappingRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeSectionRepository;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.AcademicAdmissionsIntake;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.AcademicProgrammeOption;

@ExtendWith(MockitoExtension.class)
class ApplicantApplicationConfigurationServiceTest {

  @Mock private AdmissionsIntakeProjectionService admissionsIntakeProjectionService;

  @Mock private ApplicationTypeRepository applicationTypeRepository;

  @Mock private ApplicationTypeSectionRepository applicationTypeSectionRepository;

  @Mock private ApplicationTypeProgrammeMappingRepository programmeMappingRepository;

  private final Instant currentInstant = Instant.parse("2027-01-15T10:00:00Z");
  private final Clock clock = Clock.fixed(currentInstant, ZoneOffset.UTC);
  private ApplicantApplicationConfigurationService configurationService;

  @BeforeEach
  void setUp() {
    configurationService =
        new ApplicantApplicationConfigurationService(
            admissionsIntakeProjectionService,
            applicationTypeRepository,
            applicationTypeSectionRepository,
            programmeMappingRepository);
  }

  @Test
  void getStartOptions_shouldNotPublishLegacyAdmissionsOwnedPricing() {
    AdmissionCycle openCycle =
        cycle(
            "2027-AUG",
            "August 2027",
            currentInstant.minusSeconds(60),
            currentInstant.plusSeconds(3600));
    openCycle.open(currentInstant);
    ApplicationType applicationType =
        new ApplicationType("UNDERGRAD", "Undergraduate", false, true);
    ReflectionTestUtils.setField(applicationType, "id", UUID.randomUUID());
    when(admissionsIntakeProjectionService.openIntakes())
        .thenReturn(List.of(academicIntake(openCycle)));
    when(applicationTypeRepository.findAll()).thenReturn(List.of(applicationType));

    ApplicationStartOptionsSummary summary = configurationService.getStartOptions("local");

    assertEquals("LOCAL", summary.applicantCategoryCode());
    assertEquals(4, summary.applicantCategories().size());
    assertEquals(
        List.of("2027-AUG"), summary.intakes().stream().map(option -> option.code()).toList());
    assertEquals(1, summary.applicationTypes().size());
    assertFalse(summary.applicationTypes().getFirst().fee().required());
    assertEquals("UNCONFIGURED", summary.applicationTypes().getFirst().fee().policyStatus());
    assertEquals(null, summary.applicationTypes().getFirst().fee().amount());
    assertEquals(
        List.of(
            "PERSONAL_DETAILS",
            "NEXT_OF_KIN",
            "QUALIFICATIONS",
            "REFEREES",
            "PROGRAMME_CHOICES",
            "DOCUMENTS",
            "PAYMENT",
            "REVIEW_DECLARATION"),
        summary.applicationTypes().getFirst().sections().stream()
            .map(section -> section.code())
            .toList());
    assertEquals(
        "Applicant details", summary.applicationTypes().getFirst().sections().getFirst().name());
  }

  @Test
  void getStartOptions_shouldDeferFinancePricingUntilProgrammeLevelIsKnown() {
    ApplicationType applicationType =
        new ApplicationType("UNDERGRAD", "Undergraduate", false, false);
    ReflectionTestUtils.setField(applicationType, "id", UUID.randomUUID());
    UUID feeStructureId = UUID.randomUUID();
    applicationType.associateFeeStructure(feeStructureId, "UG_APPLICATION", "UG_APPLICATION");
    when(admissionsIntakeProjectionService.openIntakes()).thenReturn(List.of());
    when(applicationTypeRepository.findAll()).thenReturn(List.of(applicationType));
    ApplicationStartOptionsSummary summary = configurationService.getStartOptions("LOCAL");

    assertTrue(summary.applicationTypes().getFirst().fee().required());
    assertEquals("FEE_STRUCTURE", summary.applicationTypes().getFirst().fee().policyStatus());
    assertEquals(null, summary.applicationTypes().getFirst().fee().amount());
    assertEquals(null, summary.applicationTypes().getFirst().fee().currencyCode());
  }

  @Test
  void getApplicantCategories_shouldReturnThePortalApplicantChoices() {
    assertEquals(
        List.of("LOCAL", "SADC", "INTERNATIONAL", "CLE"),
        configurationService.getApplicantCategories().stream()
            .map(option -> option.code())
            .toList());
    assertEquals(
        List.of(
            "Local applicant",
            "SADC applicant",
            "International applicant",
            "Continuing legal education applicant"),
        configurationService.getApplicantCategories().stream()
            .map(option -> option.label())
            .toList());
  }

  @Test
  void getStartOptions_shouldExposeNoRequiredFeeWhenNoEffectiveConfigurationExists() {
    ApplicationType applicationType = new ApplicationType("POSTGRAD", "Postgraduate", true, true);
    ReflectionTestUtils.setField(applicationType, "id", UUID.randomUUID());
    when(admissionsIntakeProjectionService.openIntakes()).thenReturn(List.of());
    when(applicationTypeRepository.findAll()).thenReturn(List.of(applicationType));
    ApplicationStartOptionsSummary summary = configurationService.getStartOptions("SADC");

    assertFalse(summary.applicationTypes().getFirst().fee().required());
    assertEquals(null, summary.applicationTypes().getFirst().fee().amount());
  }

  @Test
  void getStartOptions_shouldExposeOpenIntakesFromAcademicSetup() {
    AdmissionCycle validCycle =
        cycle(
            "2027-AUG",
            "August 2027",
            currentInstant.minusSeconds(60),
            currentInstant.plusSeconds(3600));
    validCycle.open(currentInstant);

    when(admissionsIntakeProjectionService.openIntakes())
        .thenReturn(List.of(academicIntake(validCycle)));
    when(applicationTypeRepository.findAll()).thenReturn(List.of());

    ApplicationStartOptionsSummary summary = configurationService.getStartOptions("LOCAL");

    assertEquals(
        List.of("2027-AUG"), summary.intakes().stream().map(option -> option.code()).toList());
  }

  @Test
  void getStartOptions_shouldExposeOnlyConfiguredRouteAndIntakeProgrammeIntersections() {
    AdmissionCycle openCycle =
        cycle(
            "2027-AUG",
            "August 2027",
            currentInstant.minusSeconds(60),
            currentInstant.plusSeconds(3600));
    openCycle.open(currentInstant);
    ApplicationType mba = new ApplicationType("MBA", "MBA", true, true);
    ReflectionTestUtils.setField(mba, "id", UUID.randomUUID());
    UUID mbaProgrammeId = UUID.randomUUID();
    UUID unrelatedProgrammeId = UUID.randomUUID();
    AcademicProgrammeOption mbaProgramme = academicProgramme(mbaProgrammeId, "MBA");
    AcademicProgrammeOption unrelatedProgramme = academicProgramme(unrelatedProgrammeId, "MSC");
    ApplicationTypeProgrammeMapping mapping =
        new ApplicationTypeProgrammeMapping(
            mba, mbaProgrammeId, "MBA", "Master of Business Administration");

    when(admissionsIntakeProjectionService.openIntakes())
        .thenReturn(List.of(academicIntake(openCycle, List.of(mbaProgramme, unrelatedProgramme))));
    when(applicationTypeRepository.findAll()).thenReturn(List.of(mba));
    when(programmeMappingRepository
            .findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderByProgrammeCodeAsc(
                mba.getId()))
        .thenReturn(List.of(mapping));

    ApplicationStartOptionsSummary summary = configurationService.getStartOptions("LOCAL");

    assertEquals(1, summary.routes().size());
    assertEquals("MBA", summary.routes().getFirst().applicationTypeCode());
    assertEquals(
        List.of(mbaProgrammeId),
        summary.routes().getFirst().programmes().stream()
            .map(programme -> programme.id())
            .toList());
  }

  @Test
  void getStartOptions_shouldRejectUnknownApplicantCategory() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> configurationService.getStartOptions("UNKNOWN"));

    assertTrue(exception.getMessage().contains("Unsupported applicant category"));
  }

  private AdmissionCycle cycle(String code, String name, Instant opensAt, Instant closesAt) {
    AdmissionCycle admissionCycle =
        new AdmissionCycle(UUID.randomUUID(), UUID.randomUUID(), code, name, opensAt, closesAt);
    ReflectionTestUtils.setField(admissionCycle, "id", UUID.randomUUID());
    return admissionCycle;
  }

  private AcademicAdmissionsIntake academicIntake(AdmissionCycle projection) {
    return academicIntake(projection, List.of());
  }

  private AcademicAdmissionsIntake academicIntake(
      AdmissionCycle projection, List<AcademicProgrammeOption> programmes) {
    return new AcademicAdmissionsIntake(
        projection.getIntakeId(),
        projection.getAcademicYearId(),
        "2027",
        projection.getCode(),
        projection.getName(),
        LocalDate.parse("2027-01-01"),
        LocalDate.parse("2027-12-31"),
        "OPEN",
        3,
        programmes);
  }

  private AcademicProgrammeOption academicProgramme(UUID programmeId, String programmeCode) {
    return new AcademicProgrammeOption(
        programmeId,
        programmeCode,
        programmeCode,
        programmeCode,
        UUID.randomUUID(),
        "2027",
        UUID.randomUUID(),
        "Faculty",
        4,
        4);
  }
}
