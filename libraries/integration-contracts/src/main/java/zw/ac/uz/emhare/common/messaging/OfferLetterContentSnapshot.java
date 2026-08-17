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
    BankDetailsSnapshot bankDetails,
    List<BankAccountSnapshot> bankAccounts,
    String signatorySignatureDocumentId,
    String signatoryName,
    String signatoryTitle,
    String contentPolicyVersion) {

  public OfferLetterContentSnapshot {
    studyOptions = studyOptions == null ? List.of() : List.copyOf(studyOptions);
    requiredVerificationDocuments =
        requiredVerificationDocuments == null
            ? List.of()
            : List.copyOf(requiredVerificationDocuments);
    bankAccounts = bankAccounts == null ? List.of() : List.copyOf(bankAccounts);
  }

  public OfferLetterContentSnapshot(
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
      BankDetailsSnapshot bankDetails,
      List<BankAccountSnapshot> bankAccounts,
      String signatoryName,
      String signatoryTitle,
      String contentPolicyVersion) {
    this(
        institutionName,
        institutionLegalName,
        institutionPostalAddress,
        institutionTelephone,
        institutionEmail,
        institutionWebsite,
        applicantPostalAddress,
        applicantCategoryCode,
        applicationRouteCode,
        applicationRouteName,
        intakeName,
        academicUnitName,
        awardName,
        programmeLevelName,
        programmeVersionCode,
        studyOptions,
        requiredVerificationDocuments,
        feeSchedule,
        bankDetails,
        bankAccounts,
        null,
        signatoryName,
        signatoryTitle,
        contentPolicyVersion);
  }

  public OfferLetterContentSnapshot(
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
      BankDetailsSnapshot bankDetails,
      String signatoryName,
      String signatoryTitle,
      String contentPolicyVersion) {
    this(
        institutionName,
        institutionLegalName,
        institutionPostalAddress,
        institutionTelephone,
        institutionEmail,
        institutionWebsite,
        applicantPostalAddress,
        applicantCategoryCode,
        applicationRouteCode,
        applicationRouteName,
        intakeName,
        academicUnitName,
        awardName,
        programmeLevelName,
        programmeVersionCode,
        studyOptions,
        requiredVerificationDocuments,
        feeSchedule,
        bankDetails,
        List.of(),
        null,
        signatoryName,
        signatoryTitle,
        contentPolicyVersion);
  }

  public OfferLetterContentSnapshot(
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
    this(
        institutionName,
        institutionLegalName,
        institutionPostalAddress,
        institutionTelephone,
        institutionEmail,
        institutionWebsite,
        applicantPostalAddress,
        applicantCategoryCode,
        applicationRouteCode,
        applicationRouteName,
        intakeName,
        academicUnitName,
        awardName,
        programmeLevelName,
        programmeVersionCode,
        studyOptions,
        requiredVerificationDocuments,
        feeSchedule,
        null,
        List.of(),
        null,
        signatoryName,
        signatoryTitle,
        contentPolicyVersion);
  }

  public record BankDetailsSnapshot(
      String bankName,
      String branchName,
      String accountName,
      String accountNumber,
      String branchSortCode,
      String swiftCode,
      String paymentReferenceInstructions) {}

  public record BankAccountSnapshot(
      String currencyCode,
      String bankName,
      String branchName,
      String accountName,
      String accountNumber,
      String branchSortCode,
      String swiftCode,
      String paymentReferenceInstructions) {
    public BankAccountSnapshot {
      String normalizedCurrencyCode =
          currencyCode == null ? null : currencyCode.trim().toUpperCase(Locale.ROOT);
      if (normalizedCurrencyCode == null || normalizedCurrencyCode.length() != 3) {
        throw new IllegalArgumentException(
            "A three-letter currency code is required for each bank account.");
      }
      if (bankName == null
          || bankName.isBlank()
          || accountNumber == null
          || accountNumber.isBlank()) {
        throw new IllegalArgumentException(
            "A bank name and account number are required for each bank account.");
      }
      currencyCode = normalizedCurrencyCode;
      bankName = bankName.trim();
      accountNumber = accountNumber.trim();
    }
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
      String currency =
          transactionCurrencyCode == null
              ? null
              : transactionCurrencyCode.trim().toUpperCase(Locale.ROOT);
      String baseCurrency =
          baseCurrencyCode == null ? null : baseCurrencyCode.trim().toUpperCase(Locale.ROOT);
      if (currency == null || currency.length() != 3 || !"USD".equals(baseCurrency)) {
        throw new IllegalArgumentException(
            "Offer-letter fee snapshots require a transaction currency and USD base currency.");
      }
      boolean hasRateId = exchangeRateId != null;
      boolean hasRateValue = exchangeRateToBase != null;
      if (hasRateId != hasRateValue) {
        throw new IllegalArgumentException(
            "Exchange-rate identity and value must be captured together.");
      }
      if (!"USD".equals(currency)
          && !hasRateId
          && (baseTotal != null || lines.stream().anyMatch(line -> line.baseAmount() != null))) {
        throw new IllegalArgumentException(
            "Unrated non-USD fees cannot carry invented USD base amounts.");
      }
      transactionCurrencyCode = currency;
      baseCurrencyCode = baseCurrency;
    }
  }

  public record FeeLineSnapshot(
      String code, String description, BigDecimal transactionAmount, BigDecimal baseAmount) {}
}
