package zw.ac.uz.emhare.finance.payment.infrastructure.persistence;

import zw.ac.uz.emhare.finance.payment.domain.model.ExchangeRate;

import zw.ac.uz.emhare.finance.billing.domain.model.*;
import zw.ac.uz.emhare.finance.catalogue.domain.model.*;
import zw.ac.uz.emhare.finance.collections.domain.model.*;
import zw.ac.uz.emhare.finance.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.finance.payment.*;
import zw.ac.uz.emhare.finance.payment.domain.model.*;
import zw.ac.uz.emhare.finance.payment.provider.infrastructure.persistence.model.*;
import zw.ac.uz.emhare.finance.student.domain.model.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

    List<ExchangeRate> findAllByDeletedAtIsNullOrderByEffectiveFromDescSourceCurrencyCodeAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rate from ExchangeRate rate where rate.id=:id and rate.deletedAt is null")
    java.util.Optional<ExchangeRate> findLockedByIdAndDeletedAtIsNull(@Param("id") UUID id);

    @Query("""
            select rate
            from ExchangeRate rate
            where rate.sourceCurrencyCode = :sourceCurrencyCode
              and rate.baseCurrencyCode = 'USD'
              and rate.status = zw.ac.uz.emhare.finance.payment.domain.model.ExchangeRateStatus.ACTIVE
              and rate.effectiveFrom <= :effectiveAt
              and (rate.effectiveTo is null or rate.effectiveTo > :effectiveAt)
              and rate.deletedAt is null
            order by rate.effectiveFrom desc
            """)
    List<ExchangeRate> findEffectiveRates(
            @Param("sourceCurrencyCode") String sourceCurrencyCode,
            @Param("effectiveAt") Instant effectiveAt);
}
