package zw.ac.uz.emhare.finance.collections;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import zw.ac.uz.emhare.finance.collections.StudentAccountPayment.PaymentChannel;

/** @author Tinashe K */
public final class FinanceCollectionsContracts {
    private FinanceCollectionsContracts() {}
    public record CreateExchangeRate(@NotBlank @Pattern(regexp="[A-Za-z]{3}") String sourceCurrencyCode,@NotNull @DecimalMin("0.00000001") BigDecimal rateToBase,@NotNull Instant effectiveFrom,Instant effectiveTo,@NotBlank @Size(max=120) String sourceName,@Size(max=160) String sourceReference) {}
    public record ControlledDecision(@NotBlank @Size(max=1000) String reason,@Min(0) long expectedVersion) {}
    public record CapturePayment(UUID studentFinanceAccountId,@NotBlank @Size(max=200) String payerName,@NotBlank @Size(max=50) String providerCode,@NotBlank @Size(max=160) String providerTransactionReference,@NotNull PaymentChannel paymentChannel,@NotBlank @Pattern(regexp="[A-Za-z]{3}") String transactionCurrencyCode,@NotNull @DecimalMin("0.01") BigDecimal transactionAmount,@NotNull Instant paidAt,@NotBlank @Size(max=128) String providerEventFingerprint) {}
    public record AllocatePayment(@NotNull UUID invoiceId,@NotNull @DecimalMin("0.01") BigDecimal transactionAmount,@NotBlank @Size(max=1000) String reason,@Min(0) long expectedPaymentVersion) {}
    public record ResolveSuspense(@NotNull UUID studentFinanceAccountId,@NotBlank @Size(max=1000) String reason,@Min(0) long expectedPaymentVersion) {}
    public record CreditNoteLineInput(@NotNull UUID invoiceLineId,@NotNull @DecimalMin("0.01") BigDecimal transactionAmount,@NotNull @DecimalMin("0.01") BigDecimal baseAmount,@NotBlank @Size(max=500) String reason) {}
    public record CreateCreditNote(@NotNull UUID invoiceId,@NotNull LocalDate creditNoteDate,@NotBlank @Size(max=1000) String preparationReason,@NotEmpty @Size(max=200) List<@Valid CreditNoteLineInput> lines) {}

    public record ExchangeRateSummary(UUID id,String sourceCurrencyCode,String baseCurrencyCode,BigDecimal rateToBase,Instant effectiveFrom,Instant effectiveTo,String sourceName,String sourceReference,String status,UUID preparedByUserId,UUID approvedByUserId,Instant approvedAt,UUID retiredByUserId,Instant retiredAt,long version) {}
    public record PaymentSummary(UUID id,String paymentNumber,UUID studentFinanceAccountId,String accountNumber,String payerName,String providerCode,String providerTransactionReference,PaymentChannel paymentChannel,String transactionCurrencyCode,BigDecimal transactionAmount,String baseCurrencyCode,UUID exchangeRateId,BigDecimal baseAmount,StudentAccountPayment.RatingStatus ratingStatus,Instant paidAt,StudentAccountPayment.ReconciliationStatus reconciliationStatus,UUID capturedByUserId,Instant capturedAt,UUID reconciledByUserId,Instant reconciledAt,boolean inSuspense,boolean reversed,String receiptNumber,long version) {}
    public record ReceiptSummary(UUID id,UUID paymentId,String paymentNumber,String receiptNumber,UUID studentFinanceAccountId,String accountNumber,Instant issuedAt) {}
    public record AllocationSummary(UUID id,String allocationNumber,UUID paymentId,String paymentNumber,UUID invoiceId,String invoiceNumber,String transactionCurrencyCode,BigDecimal transactionAmount,BigDecimal paymentBaseAmount,BigDecimal invoiceBaseAmount,BigDecimal realisedExchangeDifference,UUID allocatedByUserId,Instant allocatedAt,boolean reversed,String reversalNumber,long version) {}
    public record CreditNoteLineSummary(UUID id,int lineNumber,UUID invoiceLineId,BigDecimal transactionAmount,BigDecimal baseAmount,String reason) {}
    public record CreditNoteSummary(UUID id,String creditNoteNumber,UUID invoiceId,String invoiceNumber,String transactionCurrencyCode,BigDecimal transactionAmount,String baseCurrencyCode,BigDecimal baseAmount,LocalDate creditNoteDate,FinanceCreditNote.Status status,UUID preparedByUserId,Instant preparedAt,UUID postedByUserId,Instant postedAt,long version,List<CreditNoteLineSummary> lines) {}
    public record CollectionsRegister(List<ExchangeRateSummary> exchangeRates,List<PaymentSummary> payments,List<ReceiptSummary> receipts,List<AllocationSummary> allocations,List<CreditNoteSummary> creditNotes) {}
    public record StudentAccountSummary(UUID id,String accountNumber,UUID studentId,String studentNumber,String primaryEmail,String status,BigDecimal baseBalance) {}
    public record StatementLine(String lineType,String reference,Instant occurredAt,String description,String transactionCurrencyCode,BigDecimal transactionDebit,BigDecimal transactionCredit,String baseCurrencyCode,BigDecimal baseDebit,BigDecimal baseCredit,BigDecimal runningBaseBalance) {}
    public record StudentAccountStatement(StudentAccountSummary account,List<StatementLine> lines) {}
}
