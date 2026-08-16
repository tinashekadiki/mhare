package zw.ac.uz.emhare.admissions.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionOffer;
import zw.ac.uz.emhare.admissions.domain.model.Applicant;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeOptionSnapshot;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProgrammeOptionSnapshotRepository;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.AcademicUnitHierarchyNode;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.ProgrammeHierarchyResolution;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolvedAcademicFeeLine;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolvedAcademicFeeStructure;

/** @author Tinashe K */
class OfferLetterFeeScheduleResolverTest {
    @Test
    void capturesFinanceStructureVersionLinesAndUsdRatingEvidence() {
        ApplicationProgrammeOptionSnapshotRepository programmeRepository =
                mock(ApplicationProgrammeOptionSnapshotRepository.class);
        AcademicSetupCatalogueClient academicClient = mock(AcademicSetupCatalogueClient.class);
        FinanceCatalogueClient financeClient = mock(FinanceCatalogueClient.class);
        AdmissionOffer offer = mock(AdmissionOffer.class);
        Application application = mock(Application.class);
        Applicant applicant = mock(Applicant.class);
        ApplicationProgrammeOptionSnapshot programme = mock(ApplicationProgrammeOptionSnapshot.class);
        UUID applicationId = UUID.randomUUID();
        UUID programmeId = UUID.randomUUID();
        UUID structureId = UUID.randomUUID();
        UUID rateId = UUID.randomUUID();
        UUID academicUnitId = UUID.randomUUID();
        when(offer.getApplication()).thenReturn(application); when(offer.getProgrammeId()).thenReturn(programmeId);
        when(offer.getCommencementDate()).thenReturn(LocalDate.parse("2028-03-04"));
        when(application.getId()).thenReturn(applicationId); when(application.getApplicant()).thenReturn(applicant);
        when(applicant.getApplicantCategoryCode()).thenReturn("LOCAL");
        when(programme.getProgrammeLevelId()).thenReturn(UUID.randomUUID());
        when(programme.getProgrammeLevelCode()).thenReturn("UNDERGRADUATE");
        when(programme.getProgrammeTypeId()).thenReturn(UUID.randomUUID());
        when(programmeRepository.findByApplicationIdAndProgrammeIdAndDeletedAtIsNull(applicationId, programmeId))
                .thenReturn(Optional.of(programme));
        AcademicUnitHierarchyNode unit = new AcademicUnitHierarchyNode(academicUnitId, UUID.randomUUID(), "FACULTY",
                null, "SCI", "Faculty of Science", "ACTIVE", null, null, 1);
        when(academicClient.getProgrammeHierarchy(programmeId, "Bearer token"))
                .thenReturn(new ProgrammeHierarchyResolution(programmeId, "BSC-CS", "Computer Science", unit, unit,
                        List.of(unit)));
        List<ResolvedAcademicFeeLine> lines = List.of(
                line("TUIT", "Tuition", "ZWG", new BigDecimal("2500.00"), new BigDecimal("100.00"), rateId,
                        new BigDecimal("0.04")),
                line("REG", "Registration", "ZWG", new BigDecimal("500.00"), new BigDecimal("20.00"), rateId,
                        new BigDecimal("0.04")));
        when(financeClient.resolveAcademicFeeStructure(eq("Bearer token"), any()))
                .thenReturn(new ResolvedAcademicFeeStructure(structureId, "UG-SCI-2028", "UG Science", "ACADEMIC",
                        "ACTIVE", 7, "ZWG", lines));
        OfferLetterFeeScheduleResolver resolver = new OfferLetterFeeScheduleResolver(
                programmeRepository, academicClient, financeClient);

        Instant generatedAt = Instant.parse("2028-03-04T15:30:00Z");
        var result = resolver.resolve(offer, "Bearer token", generatedAt);

        assertEquals("Faculty of Science", result.highestAcademicUnitName());
        assertEquals(structureId, result.feeSchedule().financeFeeStructureId());
        assertEquals(7, result.feeSchedule().financeFeeStructureVersion());
        assertEquals("ZWG", result.feeSchedule().transactionCurrencyCode());
        assertEquals(new BigDecimal("3000.00"), result.feeSchedule().transactionTotal());
        assertEquals(new BigDecimal("120.00"), result.feeSchedule().baseTotal());
        assertEquals(rateId, result.feeSchedule().exchangeRateId());
        assertEquals(2, result.feeSchedule().lines().size());
        ArgumentCaptor<FinanceCatalogueClient.ResolveAcademicFeeStructureRequest> request =
                ArgumentCaptor.forClass(FinanceCatalogueClient.ResolveAcademicFeeStructureRequest.class);
        verify(financeClient).resolveAcademicFeeStructure(eq("Bearer token"), request.capture());
        assertEquals(generatedAt, request.getValue().effectiveAt());
    }

    @Test
    void leavesFeesUnresolvedOnlyForNonHttpCompatibilityCalls() {
        OfferLetterFeeScheduleResolver resolver = new OfferLetterFeeScheduleResolver(
                mock(ApplicationProgrammeOptionSnapshotRepository.class), mock(AcademicSetupCatalogueClient.class),
                mock(FinanceCatalogueClient.class));

        assertNull(resolver.resolve(mock(AdmissionOffer.class), null, Instant.now()).feeSchedule());
        assertNull(resolver.resolve(mock(AdmissionOffer.class), "   ", Instant.now()).highestAcademicUnitName());
    }

    @Test
    void leavesFeesUnresolvedWhenFinanceHasNoMatchingAcademicSchedule() {
        Fixture fixture = fixture(active(List.of(line("TUIT", "Tuition", "USD", BigDecimal.ONE,
                BigDecimal.ONE, null, null))));
        when(fixture.financeClient().resolveAcademicFeeStructure(eq("Bearer token"), any()))
                .thenThrow(new IllegalStateException("No active fee structure matches this billing context."));

        var result = fixture.resolver().resolve(fixture.offer(), "Bearer token", Instant.now());
        assertNull(result.feeSchedule());
        assertEquals("Science", result.highestAcademicUnitName());
    }

    @Test
    void preservesOtherFinancePricingRejections() {
        Fixture fixture = fixture(active(List.of(line("TUIT", "Tuition", "USD", BigDecimal.ONE,
                BigDecimal.ONE, null, null))));
        IllegalStateException ambiguousPricing = new IllegalStateException(
                "Multiple active fee structures match this billing context.");
        when(fixture.financeClient().resolveAcademicFeeStructure(eq("Bearer token"), any()))
                .thenThrow(ambiguousPricing);

        IllegalStateException rejection = assertThrows(IllegalStateException.class,
                () -> fixture.resolver().resolve(fixture.offer(), "Bearer token", Instant.now()));

        assertEquals(ambiguousPricing, rejection);
    }

    @Test
    void rejectsMissingProgrammeSnapshotAndInactiveFinanceSchedule() {
        Fixture missing = fixture(active(List.of(line("TUIT", "Tuition", "USD", BigDecimal.ONE,
                BigDecimal.ONE, null, null))));
        when(missing.programmeRepository().findByApplicationIdAndProgrammeIdAndDeletedAtIsNull(any(), any()))
                .thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,
                () -> missing.resolver().resolve(missing.offer(), "Bearer token", Instant.now()));

        ResolvedAcademicFeeStructure inactive = new ResolvedAcademicFeeStructure(UUID.randomUUID(), "UG", "UG",
                "ACADEMIC", "DRAFT", 1, "USD", List.of(line("TUIT", "Tuition", "USD", BigDecimal.ONE,
                        BigDecimal.ONE, null, null)));
        Fixture inactiveFixture = fixture(inactive);
        assertThrows(IllegalStateException.class,
                () -> inactiveFixture.resolver().resolve(inactiveFixture.offer(), "Bearer token", Instant.now()));
    }

    @Test
    void keepsUnratedNonUsdScheduleUnratedAndFallsBackToFeeName() {
        ResolvedAcademicFeeLine unrated = new ResolvedAcademicFeeLine(UUID.randomUUID(), 1, UUID.randomUUID(),
                "TUIT", "Tuition fee", " ", new BigDecimal("2500.00"), "ZWG", "USD", null, null,
                null, "UNRATED", "APPROVED");
        Fixture fixture = fixture(active(List.of(unrated)));

        var result = fixture.resolver().resolve(fixture.offer(), "Bearer token", Instant.now()).feeSchedule();

        assertEquals("Tuition fee", result.lines().getFirst().description());
        assertNull(result.baseTotal());
        assertNull(result.exchangeRateId());
    }

    @Test
    void rejectsAmbiguousOrIncompleteFinanceCurrencyEvidence() {
        Fixture multipleCurrencies = fixture(active(List.of(
                line("A", "A", "USD", BigDecimal.ONE, BigDecimal.ONE, null, null),
                line("B", "B", "ZWG", BigDecimal.ONE, null, null, null))));
        assertThrows(IllegalStateException.class,
                () -> multipleCurrencies.resolver().resolve(multipleCurrencies.offer(), "Bearer token", Instant.now()));

        ResolvedAcademicFeeLine missingBase = new ResolvedAcademicFeeLine(UUID.randomUUID(), 1, UUID.randomUUID(),
                "A", "A", "A", BigDecimal.ONE, "USD", null, null, null, BigDecimal.ONE,
                "RATED", "APPROVED");
        Fixture incomplete = fixture(active(List.of(missingBase)));
        assertThrows(IllegalStateException.class,
                () -> incomplete.resolver().resolve(incomplete.offer(), "Bearer token", Instant.now()));

        UUID firstRate = UUID.randomUUID();
        UUID secondRate = UUID.randomUUID();
        Fixture multipleRates = fixture(active(List.of(
                line("A", "A", "ZWG", BigDecimal.ONE, BigDecimal.ONE, firstRate, BigDecimal.ONE),
                line("B", "B", "ZWG", BigDecimal.ONE, BigDecimal.ONE, secondRate, BigDecimal.ONE))));
        assertThrows(IllegalStateException.class,
                () -> multipleRates.resolver().resolve(multipleRates.offer(), "Bearer token", Instant.now()));
    }

    private Fixture fixture(ResolvedAcademicFeeStructure response) {
        ApplicationProgrammeOptionSnapshotRepository programmeRepository =
                mock(ApplicationProgrammeOptionSnapshotRepository.class);
        AcademicSetupCatalogueClient academicClient = mock(AcademicSetupCatalogueClient.class);
        FinanceCatalogueClient financeClient = mock(FinanceCatalogueClient.class);
        AdmissionOffer offer = mock(AdmissionOffer.class);
        Application application = mock(Application.class);
        Applicant applicant = mock(Applicant.class);
        ApplicationProgrammeOptionSnapshot programme = mock(ApplicationProgrammeOptionSnapshot.class);
        UUID applicationId = UUID.randomUUID();
        UUID programmeId = UUID.randomUUID();
        when(offer.getApplication()).thenReturn(application);
        when(offer.getProgrammeId()).thenReturn(programmeId);
        when(offer.getCommencementDate()).thenReturn(LocalDate.parse("2028-03-04"));
        when(application.getId()).thenReturn(applicationId);
        when(application.getApplicant()).thenReturn(applicant);
        when(applicant.getApplicantCategoryCode()).thenReturn("LOCAL");
        when(programmeRepository.findByApplicationIdAndProgrammeIdAndDeletedAtIsNull(applicationId, programmeId))
                .thenReturn(Optional.of(programme));
        AcademicUnitHierarchyNode unit = new AcademicUnitHierarchyNode(UUID.randomUUID(), null, "FACULTY", null,
                "SCI", "Science", "ACTIVE", null, null, 1);
        when(academicClient.getProgrammeHierarchy(programmeId, "Bearer token"))
                .thenReturn(new ProgrammeHierarchyResolution(programmeId, "BSC", "Programme", unit, unit,
                        List.of(unit)));
        when(financeClient.resolveAcademicFeeStructure(eq("Bearer token"), any())).thenReturn(response);
        return new Fixture(new OfferLetterFeeScheduleResolver(programmeRepository, academicClient, financeClient),
                offer, programmeRepository, financeClient);
    }

    private ResolvedAcademicFeeStructure active(List<ResolvedAcademicFeeLine> lines) {
        return new ResolvedAcademicFeeStructure(UUID.randomUUID(), "UG", "UG", "ACADEMIC", "ACTIVE", 1,
                lines.getFirst().transactionCurrencyCode(), lines);
    }

    private ResolvedAcademicFeeLine line(String code, String description, String currency,
            BigDecimal transactionAmount, BigDecimal baseAmount, UUID exchangeRateId, BigDecimal rate) {
        return new ResolvedAcademicFeeLine(UUID.randomUUID(), 1, UUID.randomUUID(), code, description, description,
                transactionAmount, currency, "USD", exchangeRateId, rate, baseAmount, "RATED", "APPROVED");
    }

    private record Fixture(OfferLetterFeeScheduleResolver resolver, AdmissionOffer offer,
            ApplicationProgrammeOptionSnapshotRepository programmeRepository,
            FinanceCatalogueClient financeClient) { }
}
