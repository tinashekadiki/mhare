package zw.ac.uz.emhare.common.messaging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.common.messaging.OfferLetterContentSnapshot.FeeLineSnapshot;
import zw.ac.uz.emhare.common.messaging.OfferLetterContentSnapshot.FeeScheduleSnapshot;

/** @author Tinashe K */
class OfferLetterContentSnapshotTest {
    @Test
    void normalizesCurrenciesAndDefensivelyCopiesOptionalLists() {
        List<String> options = new java.util.ArrayList<>(List.of("Computer Science"));
        OfferLetterContentSnapshot snapshot = snapshot(options, null);
        OfferLetterContentSnapshot emptyOptions = snapshot(null, List.of("Identity document"));
        FeeScheduleSnapshot schedule = new FeeScheduleSnapshot(UUID.randomUUID(), 2, "UG-LOCAL", " usd ", " usd ",
                null, null, null, new BigDecimal("100.00"), new BigDecimal("100.00"));

        options.add("Statistics");

        assertEquals(List.of("Computer Science"), snapshot.studyOptions());
        assertEquals(List.of(), snapshot.requiredVerificationDocuments());
        assertEquals(List.of(), emptyOptions.studyOptions());
        assertEquals(List.of("Identity document"), emptyOptions.requiredVerificationDocuments());
        assertEquals(List.of(), schedule.lines());
        assertEquals("USD", schedule.transactionCurrencyCode());
        assertEquals("USD", schedule.baseCurrencyCode());
    }

    @Test
    void acceptsAnUnratedZwgScheduleWithoutUsdAmounts() {
        assertDoesNotThrow(() -> new FeeScheduleSnapshot(UUID.randomUUID(), 2, "UG-LOCAL", "ZWG", "USD",
                null, null, List.of(new FeeLineSnapshot("TUIT", "Tuition", new BigDecimal("2500.00"), null)),
                new BigDecimal("2500.00"), null));
    }

    @Test
    void rejectsAnInventedUsdAmountForAnUnratedZwgSchedule() {
        assertThrows(IllegalArgumentException.class,
                () -> new FeeScheduleSnapshot(UUID.randomUUID(), 2, "UG-LOCAL", "ZWG", "USD",
                        null, null,
                        List.of(new FeeLineSnapshot("TUIT", "Tuition", new BigDecimal("2500.00"),
                                new BigDecimal("2500.00"))),
                        new BigDecimal("2500.00"), new BigDecimal("2500.00")));
    }

    @Test
    void rejectsInvalidCurrencyAndPartialExchangeRateEvidence() {
        assertThrows(IllegalArgumentException.class, () -> schedule(null, "USD", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> schedule("ZW", "USD", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> schedule("ZWG", "EUR", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> schedule("ZWG", "USD", UUID.randomUUID(), null, null));
        assertThrows(IllegalArgumentException.class,
                () -> schedule("ZWG", "USD", null, new BigDecimal("0.04"), null));
        assertThrows(IllegalArgumentException.class,
                () -> schedule("ZWG", "USD", null, null, new BigDecimal("100.00")));
        assertThrows(IllegalArgumentException.class,
                () -> new FeeScheduleSnapshot(UUID.randomUUID(), 1, "UG", "ZWG", "USD", null, null,
                        List.of(new FeeLineSnapshot("TUIT", "Tuition", BigDecimal.ONE, BigDecimal.ONE)),
                        BigDecimal.ONE, null));
    }

    @Test
    void compatibilityConstructorsLeaveTheNewSnapshotFieldsEmpty() {
        Instant now = Instant.parse("2028-01-10T08:00:00Z");
        UUID requester = UUID.randomUUID();
        OfferLetterRequestedEvent versioned = new OfferLetterRequestedEvent(UUID.randomUUID(), 2, now,
                UUID.randomUUID(), 1, 4, "OFR-1", UUID.randomUUID(), "APP-1", "APL-1", "Applicant",
                "applicant@example.test", UUID.randomUUID(), UUID.randomUUID(), "BSC", "Programme",
                UUID.randomUUID(), "FIRM", null, now.plusSeconds(3600), LocalDate.now(), null,
                LocalDate.now(), requester);
        OfferLetterRequestedEvent legacy = new OfferLetterRequestedEvent(UUID.randomUUID(), 1, now,
                UUID.randomUUID(), 1, "OFR-2", UUID.randomUUID(), "APP-2", "APL-2", "Applicant",
                "applicant@example.test", UUID.randomUUID(), "BSC", "Programme", "FIRM", null,
                now.plusSeconds(3600), null, null, LocalDate.now(), requester);

        assertNull(versioned.contentSnapshot());
        assertEquals(4, versioned.documentVersion());
        assertNull(legacy.contentSnapshot());
        assertEquals(1, legacy.documentVersion());
    }

    private OfferLetterContentSnapshot snapshot(List<String> studyOptions, List<String> evidence) {
        return new OfferLetterContentSnapshot("University of Zimbabwe", "University of Zimbabwe", null, null,
                null, null, null, "LOCAL", "UG", "Undergraduate", "March", "Science", "BSc",
                "Undergraduate", "2028.1", studyOptions, evidence, null, "Registrar", "Registrar", "V1");
    }

    private FeeScheduleSnapshot schedule(String currency, String baseCurrency, UUID rateId, BigDecimal rate,
            BigDecimal baseTotal) {
        return new FeeScheduleSnapshot(UUID.randomUUID(), 1, "UG", currency, baseCurrency, rateId, rate,
                List.of(new FeeLineSnapshot("TUIT", "Tuition", new BigDecimal("2500.00"), null)),
                new BigDecimal("2500.00"), baseTotal);
    }
}
