package zw.ac.uz.emhare.finance.billing.infrastructure.persistence;

import zw.ac.uz.emhare.finance.billing.domain.model.FinanceBillingPolicy;

import zw.ac.uz.emhare.finance.billing.*;
import zw.ac.uz.emhare.finance.billing.domain.model.*;
import zw.ac.uz.emhare.finance.catalogue.domain.model.*;
import zw.ac.uz.emhare.finance.collections.domain.model.*;
import zw.ac.uz.emhare.finance.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.finance.payment.domain.model.*;
import zw.ac.uz.emhare.finance.payment.provider.infrastructure.persistence.model.*;
import zw.ac.uz.emhare.finance.student.domain.model.*;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

/** Spring Data persistence adapter. @author Tinashe K */
public interface FinanceBillingPolicyRepository extends JpaRepository<FinanceBillingPolicy,UUID> {
    List<FinanceBillingPolicy> findAllByDeletedAtIsNullOrderByCodeAscPolicyVersionDesc();
    Optional<FinanceBillingPolicy> findFirstByCodeIgnoreCaseAndDeletedAtIsNullOrderByPolicyVersionDesc(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<FinanceBillingPolicy> findLockedByIdAndDeletedAtIsNull(UUID id);
    @Query("SELECT policy FROM FinanceBillingPolicy policy WHERE policy.sourceEventType=:sourceEventType AND policy.status=zw.ac.uz.emhare.finance.billing.domain.model.FinanceBillingPolicy.Status.ACTIVE AND policy.deletedAt IS NULL AND policy.effectiveFrom<=:effectiveAt AND (policy.effectiveUntil IS NULL OR policy.effectiveUntil>:effectiveAt) ORDER BY policy.code,policy.policyVersion")
    List<FinanceBillingPolicy> findActivePolicies(@Param("sourceEventType") String sourceEventType,@Param("effectiveAt") Instant effectiveAt);
}
