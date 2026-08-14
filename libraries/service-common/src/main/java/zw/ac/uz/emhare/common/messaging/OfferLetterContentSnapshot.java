package zw.ac.uz.emhare.common.messaging;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Immutable, versioned content used to reproduce an official offer letter. @author Tinashe K */
public record OfferLetterContentSnapshot(
        String institutionName,
        String institutionLegalName,
        String institutionPostalAddress,
        String institutionTelephone,
        String institutionEmail,
        String institutionWebsite,
        String applicantPostalAddress,
        String applicantCategoryCode,
        String applicationRouteCode,
        String applicationRouteName,
        String intakeName,
        String academicUnitName,
        String awardName,
        String programmeLevelName,
        String programmeVersionCode,
        List<String> studyOptions,
        List<String> requiredVerificationDocuments,
        FeeScheduleSnapshot feeSchedule,
        String signatoryName,
        String signatoryTitle,
        String contentPolicyVersion) {

    public OfferLetterContentSnapshot {
        studyOptions = studyOptions == null ? List.of() : List.copyOf(studyOptions);
        requiredVerificationDocuments = requiredVerificationDocuments == null
                ? List.of() : List.copyOf(requiredVerificationDocuments);
    }

    public record FeeScheduleSnapshot(
            UUID financeFeeStructureId,
            long financeFeeStructureVersion,
            String financeFeeStructureCode,
            String transactionCurrencyCode,
            String baseCurrencyCode,
            UUID exchangeRateId,
            BigDecimal exchangeRateToBase,
            List<FeeLineSnapshot> lines,
            BigDecimal transactionTotal,
            BigDecimal baseTotal) {
        public FeeScheduleSnapshot {
            lines = lines == null ? List.of() : List.copyOf(lines);
            String currency = transactionCurrencyCode == null ? null
                    : transactionCurrencyCode.trim().toUpperCase(Locale.ROOT);
            String baseCurrency = baseCurrencyCode == null ? null
                    : baseCurrencyCode.trim().toUpperCase(Locale.ROOT);
            if (currency == null || currency.length() != 3 || !"USD".equals(baseCurrency)) {
                throw new IllegalArgumentException("Offer-letter fee snapshots require a transaction currency and USD base currency.");
            }
            boolean hasRateId = exchangeRateId != null;
            boolean hasRateValue = exchangeRateToBase != null;
            if (hasRateId != hasRateValue) {
                throw new IllegalArgumentException("Exchange-rate identity and value must be captured together.");
            }
            if (!"USD".equals(currency) && !hasRateId && (baseTotal != null
                    || lines.stream().anyMatch(line -> line.baseAmount() != null))) {
                throw new IllegalArgumentException("Unrated non-USD fees cannot carry invented USD base amounts.");
            }
            transactionCurrencyCode = currency;
            baseCurrencyCode = baseCurrency;
        }
    }

    public record FeeLineSnapshot(String code, String description, BigDecimal transactionAmount, BigDecimal baseAmount) { }
}
