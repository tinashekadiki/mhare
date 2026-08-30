package zw.ac.uz.emhare.accommodation.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import zw.ac.uz.emhare.accommodation.operations.domain.model.AccommodationRate;

/**
 * @author Tinashe K
 */
class AccommodationRateCurrencyGovernanceTest extends AccommodationGovernanceFixture {
  @Test
  void zwgWithoutEffectiveRateRemainsUnratedAndCannotBeActivated() {
    var rate = rate(" zwg ", null, null);
    assertEquals("ZWG", rate.getTransactionCurrencyCode());
    assertEquals("USD", rate.getBaseCurrencyCode());
    assertEquals(AccommodationRate.RatingStatus.UNRATED, rate.getRatingStatus());
    assertNull(rate.getExchangeRateId());
    assertNull(rate.getIndicativeBaseAmount());
    assertThrows(
        IllegalStateException.class,
        () ->
            rate.transition(
                AccommodationRate.Status.ACTIVE, CHECKER, "Cannot invent rate", NOW, 0));
    assertEquals(AccommodationRate.Status.DRAFT, rate.getStatus());
  }

  @Test
  void validExchangeEvidenceLinksForeignChargeToPositiveUsdBaseAmount() {
    UUID exchangeRate = UUID.randomUUID();
    var rate = rate("ZWG", exchangeRate, new BigDecimal("4.00"));
    assertEquals(AccommodationRate.RatingStatus.RATED, rate.getRatingStatus());
    assertEquals(exchangeRate, rate.getExchangeRateId());
    assertEquals(new BigDecimal("4.00"), rate.getIndicativeBaseAmount());
    assertEquals(new BigDecimal("100"), rate.getIndicativeTransactionAmount());
    rate.transition(
        AccommodationRate.Status.ACTIVE, CHECKER, " Effective Finance rate verified ", NOW, 0);
    assertEquals(CHECKER, rate.getApprovedByUserId());
    assertEquals(NOW, rate.getApprovedAt());
    assertEquals("Effective Finance rate verified", rate.getApprovalReason());
    rate.transition(
        AccommodationRate.Status.RETIRED,
        CHECKER,
        "Superseded by new rate",
        NOW.plusSeconds(60),
        0);
    assertEquals(AccommodationRate.Status.RETIRED, rate.getStatus());
    assertEquals(exchangeRate, rate.getExchangeRateId());
  }

  @Test
  void usdRateUsesItsTransactionAmountDirectlyWithoutExchangeRateReference() {
    var rate = rate("usd", null, null);
    assertEquals(rate.getIndicativeTransactionAmount(), rate.getIndicativeBaseAmount());
    assertEquals(AccommodationRate.RatingStatus.RATED, rate.getRatingStatus());
    assertNull(rate.getExchangeRateId());
    assertThrows(
        IllegalArgumentException.class, () -> rate("USD", UUID.randomUUID(), BigDecimal.ONE));
  }

  @ParameterizedTest
  @ValueSource(strings = {"base-without-rate", "rate-without-base", "zero-base", "negative-base"})
  void foreignCurrencyEvidenceCannotBePartiallyRated(String invalid) {
    BigDecimal amount =
        switch (invalid) {
          case "rate-without-base" -> null;
          case "zero-base" -> BigDecimal.ZERO;
          case "negative-base" -> BigDecimal.ONE.negate();
          default -> BigDecimal.ONE;
        };
    assertThrows(
        IllegalArgumentException.class,
        () -> rate("ZWG", invalid.equals("base-without-rate") ? null : UUID.randomUUID(), amount));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "period",
        "room-type",
        "finance-fee",
        "effective-from",
        "preparer",
        "version",
        "null-amount",
        "zero-amount",
        "negative-amount",
        "equal-window",
        "reversed-window"
      })
  void ratesRequireCompleteScopePositiveAmountsAndStrictEffectiveWindow(String invalid) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AccommodationRate(
                invalid.equals("period") ? null : openPeriod(),
                invalid.equals("room-type") ? null : roomType,
                invalid.equals("version") ? 0 : 1,
                invalid.equals("finance-fee") ? null : UUID.randomUUID(),
                "USD",
                invalid.equals("null-amount")
                    ? null
                    : invalid.equals("zero-amount")
                        ? BigDecimal.ZERO
                        : invalid.equals("negative-amount")
                            ? BigDecimal.ONE.negate()
                            : BigDecimal.TEN,
                null,
                null,
                invalid.equals("effective-from") ? null : NOW,
                invalid.equals("equal-window")
                    ? NOW
                    : invalid.equals("reversed-window") ? NOW.minusSeconds(1) : NOW.plusSeconds(60),
                invalid.equals("preparer") ? null : MAKER));
  }

  @Test
  void rateApprovalRequiresIndependentActorAndForwardOnlyVersionedLifecycle() {
    var rate = draftRate();
    assertThrows(
        IllegalStateException.class,
        () -> rate.transition(AccommodationRate.Status.ACTIVE, CHECKER, "Stale", NOW, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> rate.transition(AccommodationRate.Status.ACTIVE, null, "No actor", NOW, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> rate.transition(AccommodationRate.Status.ACTIVE, MAKER, "Self approval", NOW, 0));
    assertThrows(
        IllegalStateException.class,
        () ->
            rate.transition(AccommodationRate.Status.RETIRED, CHECKER, "Skip activation", NOW, 0));
    rate.transition(AccommodationRate.Status.ACTIVE, CHECKER, "Approved", NOW, 0);
    assertThrows(
        IllegalStateException.class,
        () ->
            rate.transition(
                AccommodationRate.Status.ACTIVE, CHECKER, "Repeated activation", NOW, 0));
    rate.transition(AccommodationRate.Status.RETIRED, CHECKER, "Retired", NOW, 0);
    assertThrows(
        IllegalStateException.class,
        () -> rate.transition(AccommodationRate.Status.ACTIVE, CHECKER, "Revive history", NOW, 0));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void currencyAndApprovalEvidenceCannotBeMissing(String value) {
    assertThrows(IllegalArgumentException.class, () -> rate(value, null, null));
    var rate = draftRate();
    assertThrows(
        IllegalArgumentException.class,
        () -> rate.transition(AccommodationRate.Status.ACTIVE, CHECKER, value, NOW, 0));
    assertEquals(AccommodationRate.Status.DRAFT, rate.getStatus());
  }

  private AccommodationRate rate(String currency, UUID rate, BigDecimal baseAmount) {
    return new AccommodationRate(
        openPeriod(),
        roomType,
        1,
        UUID.randomUUID(),
        currency,
        new BigDecimal("100"),
        rate,
        baseAmount,
        NOW,
        null,
        MAKER);
  }
}
