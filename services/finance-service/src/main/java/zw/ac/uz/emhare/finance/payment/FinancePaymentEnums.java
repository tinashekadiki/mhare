package zw.ac.uz.emhare.finance.payment;

enum ExchangeRateStatus {
    DRAFT,
    ACTIVE,
    RETIRED
}

enum MoneyRatingStatus {
    RATED,
    UNRATED
}

enum PaymentReferenceStatus {
    PENDING,
    PAID,
    EXPIRED,
    CANCELLED
}

enum ApplicationPaymentStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    REVERSED
}

enum FinanceReceiptStatus {
    PENDING_GENERATION,
    ISSUED,
    VOIDED
}
