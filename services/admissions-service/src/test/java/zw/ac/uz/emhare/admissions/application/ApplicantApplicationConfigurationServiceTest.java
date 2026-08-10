package zw.ac.uz.emhare.admissions.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.AcademicAdmissionsIntake;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient;

@ExtendWith(MockitoExtension.class)
class ApplicantApplicationConfigurationServiceTest {

    @Mock
    private AdmissionsIntakeProjectionService admissionsIntakeProjectionService;

    @Mock
    private ApplicationTypeRepository applicationTypeRepository;

    @Mock
    private ApplicationFeeRepository applicationFeeRepository;

    @Mock
    private ApplicationTypeSectionRepository applicationTypeSectionRepository;

    @Mock
    private FinanceCatalogueClient financeCatalogueClient;

    private final Instant currentInstant = Instant.parse("2027-01-15T10:00:00Z");
    private final Clock clock = Clock.fixed(currentInstant, ZoneOffset.UTC);
    private ApplicantApplicationConfigurationService configurationService;

    @BeforeEach
    void setUp() {
        configurationService = new ApplicantApplicationConfigurationService(
                admissionsIntakeProjectionService,
                applicationTypeRepository,
                applicationFeeRepository,
                applicationTypeSectionRepository,
                financeCatalogueClient,
                clock);
    }

    @Test
    void getStartOptions_shouldReturnOnlyOpenIntakesAndEffectiveCategoryFee() {
        AdmissionCycle openCycle = cycle("2027-AUG", "August 2027", currentInstant.minusSeconds(60), currentInstant.plusSeconds(3600));
        openCycle.open(currentInstant);
        ApplicationType applicationType = new ApplicationType("UNDERGRAD", "Undergraduate", false, true);
        ReflectionTestUtils.setField(applicationType, "id", UUID.randomUUID());
        ApplicationFee applicationFee = new ApplicationFee(
                applicationType,
                "LOCAL",
                "USD",
                new BigDecimal("25.00"),
                LocalDate.now(clock).minusDays(1));

        when(admissionsIntakeProjectionService.openIntakes()).thenReturn(List.of(academicIntake(openCycle)));
        when(applicationTypeRepository.findAll()).thenReturn(List.of(applicationType));
        when(applicationFeeRepository.findEffectiveFees(applicationType.getId(), "LOCAL", LocalDate.now(clock)))
                .thenReturn(List.of(applicationFee));

        ApplicationStartOptionsSummary summary = configurationService.getStartOptions("local");

        assertEquals("LOCAL", summary.applicantCategoryCode());
        assertEquals(4, summary.applicantCategories().size());
        assertEquals(List.of("2027-AUG"), summary.intakes().stream().map(option -> option.code()).toList());
        assertEquals(1, summary.applicationTypes().size());
        assertTrue(summary.applicationTypes().getFirst().fee().required());
        assertEquals(new BigDecimal("25.00"), summary.applicationTypes().getFirst().fee().amount());
        assertEquals("USD", summary.applicationTypes().getFirst().fee().currencyCode());
        assertEquals(
                List.of("PERSONAL_DETAILS", "NEXT_OF_KIN", "QUALIFICATIONS", "REFEREES", "PROGRAMME_CHOICES", "DOCUMENTS", "PAYMENT", "REVIEW_DECLARATION"),
                summary.applicationTypes().getFirst().sections().stream().map(section -> section.code()).toList());
        assertEquals("Applicant details", summary.applicationTypes().getFirst().sections().getFirst().name());
    }

    @Test
    void getStartOptions_shouldPreferFinanceLinkedFeeStructureOverLegacyApplicationFee() {
        ApplicationType applicationType = new ApplicationType("UNDERGRAD", "Undergraduate", false, false);
        ReflectionTestUtils.setField(applicationType, "id", UUID.randomUUID());
        UUID feeStructureId = UUID.randomUUID();
        applicationType.associateFeeStructure(feeStructureId, "UG_APPLICATION", "UG_APPLICATION");
        when(admissionsIntakeProjectionService.openIntakes()).thenReturn(List.of());
        when(applicationTypeRepository.findAll()).thenReturn(List.of(applicationType));
        when(financeCatalogueClient.getApplicationFeeStructurePricing(feeStructureId))
                .thenReturn(new FinanceCatalogueClient.ApplicationFeeStructurePricing(
                        feeStructureId, "UG_APPLICATION", "UG_APPLICATION", "ACTIVE", "USD", new BigDecimal("40.00")));

        ApplicationStartOptionsSummary summary = configurationService.getStartOptions("LOCAL");

        assertTrue(summary.applicationTypes().getFirst().fee().required());
        assertEquals(new BigDecimal("40.00"), summary.applicationTypes().getFirst().fee().amount());
        assertEquals("USD", summary.applicationTypes().getFirst().fee().currencyCode());
    }

    @Test
    void getApplicantCategories_shouldReturnThePortalApplicantChoices() {
        assertEquals(
                List.of("LOCAL", "SADC", "INTERNATIONAL", "CLE"),
                configurationService.getApplicantCategories().stream().map(option -> option.code()).toList());
        assertEquals(
                List.of("Local applicant", "SADC applicant", "International applicant", "Continuing legal education applicant"),
                configurationService.getApplicantCategories().stream().map(option -> option.label()).toList());
    }

    @Test
    void getStartOptions_shouldExposeNoRequiredFeeWhenNoEffectiveConfigurationExists() {
        ApplicationType applicationType = new ApplicationType("POSTGRAD", "Postgraduate", true, true);
        ReflectionTestUtils.setField(applicationType, "id", UUID.randomUUID());
        when(admissionsIntakeProjectionService.openIntakes()).thenReturn(List.of());
        when(applicationTypeRepository.findAll()).thenReturn(List.of(applicationType));
        when(applicationFeeRepository.findEffectiveFees(applicationType.getId(), "SADC", LocalDate.now(clock)))
                .thenReturn(List.of());

        ApplicationStartOptionsSummary summary = configurationService.getStartOptions("SADC");

        assertFalse(summary.applicationTypes().getFirst().fee().required());
        assertEquals(null, summary.applicationTypes().getFirst().fee().amount());
    }

    @Test
    void getStartOptions_shouldExposeOpenIntakesFromAcademicSetup() {
        AdmissionCycle validCycle = cycle(
                "2027-AUG", "August 2027", currentInstant.minusSeconds(60), currentInstant.plusSeconds(3600));
        validCycle.open(currentInstant);

        when(admissionsIntakeProjectionService.openIntakes()).thenReturn(List.of(academicIntake(validCycle)));
        when(applicationTypeRepository.findAll()).thenReturn(List.of());

        ApplicationStartOptionsSummary summary = configurationService.getStartOptions("LOCAL");

        assertEquals(List.of("2027-AUG"), summary.intakes().stream().map(option -> option.code()).toList());
    }

    @Test
    void getStartOptions_shouldRejectAmbiguousEffectiveFees() {
        ApplicationType applicationType = new ApplicationType("UNDERGRAD", "Undergraduate", false, false);
        ReflectionTestUtils.setField(applicationType, "id", UUID.randomUUID());
        ApplicationFee firstFee = new ApplicationFee(applicationType, "LOCAL", "USD", new BigDecimal("25.00"), LocalDate.now(clock));
        ApplicationFee secondFee = new ApplicationFee(applicationType, "LOCAL", "USD", new BigDecimal("30.00"), LocalDate.now(clock));
        when(admissionsIntakeProjectionService.openIntakes()).thenReturn(List.of());
        when(applicationTypeRepository.findAll()).thenReturn(List.of(applicationType));
        when(applicationFeeRepository.findEffectiveFees(applicationType.getId(), "LOCAL", LocalDate.now(clock)))
                .thenReturn(List.of(firstFee, secondFee));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> configurationService.getStartOptions("LOCAL"));

        assertTrue(exception.getMessage().contains("Multiple effective application fees"));
    }

    @Test
    void getStartOptions_shouldRejectUnknownApplicantCategory() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> configurationService.getStartOptions("UNKNOWN"));

        assertTrue(exception.getMessage().contains("Unsupported applicant category"));
    }

    private AdmissionCycle cycle(String code, String name, Instant opensAt, Instant closesAt) {
        AdmissionCycle admissionCycle = new AdmissionCycle(UUID.randomUUID(), UUID.randomUUID(), code, name, opensAt, closesAt);
        ReflectionTestUtils.setField(admissionCycle, "id", UUID.randomUUID());
        return admissionCycle;
    }

    private AcademicAdmissionsIntake academicIntake(AdmissionCycle projection) {
        return new AcademicAdmissionsIntake(
                projection.getIntakeId(), projection.getAcademicYearId(), "2027",
                projection.getCode(), projection.getName(), LocalDate.parse("2027-01-01"),
                LocalDate.parse("2027-12-31"), "OPEN", 3, List.of());
    }
}
