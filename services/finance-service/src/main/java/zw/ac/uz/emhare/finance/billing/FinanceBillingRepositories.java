package zw.ac.uz.emhare.finance.billing;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

interface FinanceBillingEventRepository extends JpaRepository<FinanceBillingEvent,UUID> {
    List<FinanceBillingEvent> findAllByDeletedAtIsNullOrderBySubmittedAtDescEventNumberDesc();
    Optional<FinanceBillingEvent> findBySourceServiceAndSourceEventIdAndSourceLineReferenceAndDeletedAtIsNull(String sourceService,UUID sourceEventId,String sourceLineReference);
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<FinanceBillingEvent> findLockedByIdAndDeletedAtIsNull(UUID id);
}
interface FinanceBillingEventScopeRepository extends JpaRepository<FinanceBillingEventScope,UUID> {
    List<FinanceBillingEventScope> findAllByBillingEventIdAndDeletedAtIsNullOrderByScopeDimensionAsc(UUID billingEventId);
}
interface FinanceBillingPolicyRepository extends JpaRepository<FinanceBillingPolicy,UUID> {
    List<FinanceBillingPolicy> findAllByDeletedAtIsNullOrderByCodeAscPolicyVersionDesc();
    Optional<FinanceBillingPolicy> findFirstByCodeIgnoreCaseAndDeletedAtIsNullOrderByPolicyVersionDesc(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<FinanceBillingPolicy> findLockedByIdAndDeletedAtIsNull(UUID id);
    @Query("SELECT policy FROM FinanceBillingPolicy policy WHERE policy.sourceEventType=:sourceEventType AND policy.status=zw.ac.uz.emhare.finance.billing.FinanceBillingPolicy.Status.ACTIVE AND policy.deletedAt IS NULL AND policy.effectiveFrom<=:effectiveAt AND (policy.effectiveUntil IS NULL OR policy.effectiveUntil>:effectiveAt) ORDER BY policy.code,policy.policyVersion")
    List<FinanceBillingPolicy> findActivePolicies(@Param("sourceEventType") String sourceEventType,@Param("effectiveAt") Instant effectiveAt);
}
