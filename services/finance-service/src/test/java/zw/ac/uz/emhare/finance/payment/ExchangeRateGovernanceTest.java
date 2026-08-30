package zw.ac.uz.emhare.finance.payment;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import zw.ac.uz.emhare.finance.payment.domain.model.ExchangeRate;

/**
 * @author Tinashe K
 */
class ExchangeRateGovernanceTest {
  private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  private static final UUID PREPARER = UUID.randomUUID(), APPROVER = UUID.randomUUID();

  @Test
  void approvalAndRetirementPreserveRateSourceAndIndependentOperatorEvidence() {
    var rate = rate(new BigDecimal("0.04"), NOW, NOW.plusSeconds(60), " Central bank ");
    assertEquals("ZWG", rate.getSourceCurrencyCode());
    assertEquals("USD", rate.getBaseCurrencyCode());
    assertEquals("Central bank", rate.getSourceName());
    assertNull(rate.getSourceReference());
    assertEquals("DRAFT", rate.getStatus());
    assertThrows(IllegalStateException.class, () -> rate.retire(APPROVER, NOW, "Retire"));
    assertThrows(IllegalArgumentException.class, () -> rate.approve(null, NOW, "Approve"));
    assertThrows(IllegalArgumentException.class, () -> rate.approve(APPROVER, null, "Approve"));
    assertThrows(IllegalStateException.class, () -> rate.approve(PREPARER, NOW, "Approve"));
    assertThrows(IllegalArgumentException.class, () -> rate.approve(APPROVER, NOW, null));
    assertThrows(IllegalArgumentException.class, () -> rate.approve(APPROVER, NOW, " "));
    rate.approve(APPROVER, NOW, "Independent rate verification");
    assertEquals(APPROVER, rate.getApprovedByUserId());
    assertEquals(NOW, rate.getApprovedAt());
    assertThrows(IllegalStateException.class, () -> rate.approve(APPROVER, NOW, "Duplicate"));
    assertThrows(NullPointerException.class, () -> rate.retire(null, NOW, "Retire"));
    assertThrows(NullPointerException.class, () -> rate.retire(APPROVER, null, "Retire"));
    assertThrows(IllegalArgumentException.class, () -> rate.retire(APPROVER, NOW, " "));
    rate.retire(APPROVER, NOW.plusSeconds(30), "Superseded source publication");
    assertEquals("RETIRED", rate.getStatus());
    assertEquals(APPROVER, rate.getRetiredByUserId());
    assertEquals(NOW.plusSeconds(30), rate.getRetiredAt());
    assertEquals(new BigDecimal("0.04"), rate.getRateToBase());
    assertThrows(IllegalStateException.class, () -> rate.retire(APPROVER, NOW, "Duplicate"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " ", "ZW", "ZWGG"})
  void currencyRequiresExactlyThreeCharacters(String code) {
    assertThrows(IllegalArgumentException.class, () -> ExchangeRate.normalizeCurrencyCode(code));
  }

  @Test
  void usdBaseCurrencyDoesNotAcceptExchangeRateCapture() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExchangeRate("USD", BigDecimal.ONE, NOW, null, "Bank", null, PREPARER));
  }

  @ParameterizedTest
  @MethodSource("invalidRates")
  void exchangeRateCannotDefaultToZeroOrNegativeOrMissing(BigDecimal value) {
    assertThrows(IllegalArgumentException.class, () -> rate(value, NOW, null, "Bank"));
  }

  static Stream<Arguments> invalidRates() {
    return Stream.of(
        Arguments.of((BigDecimal) null),
        Arguments.of(BigDecimal.ZERO),
        Arguments.of(BigDecimal.ONE.negate()));
  }

  @Test
  void effectiveDatesAndRateSourceAreRequired() {
    assertThrows(IllegalArgumentException.class, () -> rate(BigDecimal.ONE, null, null, "Bank"));
    assertThrows(IllegalArgumentException.class, () -> rate(BigDecimal.ONE, NOW, NOW, "Bank"));
    assertThrows(
        IllegalArgumentException.class,
        () -> rate(BigDecimal.ONE, NOW, NOW.minusSeconds(1), "Bank"));
    assertThrows(IllegalArgumentException.class, () -> rate(BigDecimal.ONE, NOW, null, null));
    assertThrows(IllegalArgumentException.class, () -> rate(BigDecimal.ONE, NOW, null, " "));
  }

  private ExchangeRate rate(BigDecimal value, Instant from, Instant until, String source) {
    return new ExchangeRate(" zwg ", value, from, until, source, null, PREPARER);
  }
}
