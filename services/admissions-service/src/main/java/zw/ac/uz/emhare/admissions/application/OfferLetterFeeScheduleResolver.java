package zw.ac.uz.emhare.admissions.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionOffer;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProgrammeOptionSnapshotRepository;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.AcademicUnitPathItem;
import zw.ac.uz.emhare.admissions.integration.FinanceCatalogueClient.ResolveAcademicFeeStructureRequest;
import zw.ac.uz.emhare.common.messaging.OfferLetterContentSnapshot.FeeLineSnapshot;
import zw.ac.uz.emhare.common.messaging.OfferLetterContentSnapshot.FeeScheduleSnapshot;

/** Resolves a Finance-owned, fully versioned fee snapshot for one offer letter. @author Tinashe K */
@Component
public class OfferLetterFeeScheduleResolver {
    private final ApplicationProgrammeOptionSnapshotRepository programmeSnapshotRepository;
    private final AcademicSetupCatalogueClient academicSetupCatalogueClient;
    private final FinanceCatalogueClient financeCatalogueClient;

    public OfferLetterFeeScheduleResolver(ApplicationProgrammeOptionSnapshotRepository programmeSnapshotRepository,
            AcademicSetupCatalogueClient academicSetupCatalogueClient, FinanceCatalogueClient financeCatalogueClient) {
        this.programmeSnapshotRepository = programmeSnapshotRepository;
        this.academicSetupCatalogueClient = academicSetupCatalogueClient;
        this.financeCatalogueClient = financeCatalogueClient;
    }

    public ResolvedOfferLetterCatalogue resolve(AdmissionOffer offer, String authorization, Instant pricingEffectiveAt) {
        if (authorization == null || authorization.isBlank()) return new ResolvedOfferLetterCatalogue(null, null);
        var application = offer.getApplication();
        var programme = programmeSnapshotRepository
                .findByApplicationIdAndProgrammeIdAndDeletedAtIsNull(application.getId(), offer.getProgrammeId())
                .orElseThrow(() -> new IllegalStateException("The immutable programme snapshot required for offer pricing was not found."));
        var hierarchy = academicSetupCatalogueClient.getProgrammeHierarchy(offer.getProgrammeId(), authorization);
        String highestAcademicUnitName = hierarchy.highestAcademicUnit() == null
                ? null : hierarchy.highestAcademicUnit().name();
        List<AcademicUnitPathItem> academicUnitPath = hierarchy.ancestorPath().stream()
                .map(unit -> new AcademicUnitPathItem(unit.id(), unit.code(), unit.name())).toList();
        FinanceCatalogueClient.ResolvedAcademicFeeStructure resolved;
        try {
            resolved = financeCatalogueClient.resolveAcademicFeeStructure(authorization,
                    new ResolveAcademicFeeStructureRequest("ACADEMIC",
                            Objects.requireNonNull(pricingEffectiveAt, "pricingEffectiveAt"),
                            null, offer.getProgrammeId(), academicUnitPath, programme.getProgrammeLevelId(),
                            programme.getProgrammeLevelCode(), programme.getProgrammeTypeId(),
                            application.getApplicant().getApplicantCategoryCode(), 1));
        } catch (IllegalStateException exception) {
            if (FinanceCatalogueClient.isMissingAcademicFeeStructure(exception)) {
                return new ResolvedOfferLetterCatalogue(highestAcademicUnitName, null);
            }
            throw exception;
        }
        if (!"ACTIVE".equalsIgnoreCase(resolved.status())) {
            throw new IllegalStateException("Finance did not return an active academic fee schedule.");
        }
        String currency = distinct(resolved.lines().stream().map(line -> line.transactionCurrencyCode()).toList(),
                "transaction currency");
        String baseCurrency = distinct(resolved.lines().stream().map(line -> line.baseCurrencyCode()).toList(),
                "base currency");
        UUID exchangeRateId = distinctNullable(resolved.lines().stream().map(line -> line.exchangeRateId()).toList(),
                "exchange-rate identity");
        BigDecimal exchangeRate = distinctNullable(resolved.lines().stream().map(line -> line.exchangeRateToBase()).toList(),
                "exchange-rate value");
        List<FeeLineSnapshot> lines = resolved.lines().stream()
                .map(line -> new FeeLineSnapshot(line.feeCode(),
                        line.description() == null || line.description().isBlank() ? line.feeName() : line.description(),
                        line.transactionAmount(), line.baseAmount())).toList();
        BigDecimal transactionTotal = lines.stream().map(FeeLineSnapshot::transactionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal baseTotal = lines.stream().allMatch(line -> line.baseAmount() != null)
                ? lines.stream().map(FeeLineSnapshot::baseAmount).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        FeeScheduleSnapshot feeSchedule = new FeeScheduleSnapshot(resolved.id(), resolved.version(), resolved.code(),
                currency, baseCurrency, exchangeRateId, exchangeRate, lines, transactionTotal, baseTotal);
        return new ResolvedOfferLetterCatalogue(highestAcademicUnitName, feeSchedule);
    }

    private <T> T distinct(List<T> values, String label) {
        T value = distinctNullable(values, label);
        return value == null ? missing(label) : value;
    }

    private <T> T distinctNullable(List<T> values, String label) {
        List<T> distinct = values.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.size() > 1) throw new IllegalStateException("Finance returned multiple " + label + " values.");
        return distinct.isEmpty() ? null : distinct.getFirst();
    }

    private <T> T missing(String label) {
        throw new IllegalStateException("Finance did not return a " + label + ".");
    }

    public record ResolvedOfferLetterCatalogue(String highestAcademicUnitName, FeeScheduleSnapshot feeSchedule) { }
}
