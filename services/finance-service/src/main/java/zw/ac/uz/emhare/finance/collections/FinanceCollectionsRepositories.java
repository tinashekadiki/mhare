package zw.ac.uz.emhare.finance.collections;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface StudentAccountPaymentRepository extends JpaRepository<StudentAccountPayment,UUID> {
    List<StudentAccountPayment> findAllByDeletedAtIsNullOrderByPaidAtDescPaymentNumberDesc();
    Optional<StudentAccountPayment> findByProviderCodeAndProviderTransactionReferenceAndDeletedAtIsNull(String providerCode,String reference);
    Optional<StudentAccountPayment> findByProviderEventFingerprintAndDeletedAtIsNull(String fingerprint);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select payment from StudentAccountPayment payment where payment.id=:id and payment.deletedAt is null") Optional<StudentAccountPayment> findLockedById(@Param("id") UUID id);
}
interface StudentPaymentSuspenseResolutionRepository extends JpaRepository<StudentPaymentSuspenseResolution,UUID> {
    Optional<StudentPaymentSuspenseResolution> findByPaymentIdAndDeletedAtIsNull(UUID paymentId);
    List<StudentPaymentSuspenseResolution> findAllByDeletedAtIsNullOrderByResolvedAtDesc();
}
interface StudentPaymentReceiptRepository extends JpaRepository<StudentPaymentReceipt,UUID> {
    Optional<StudentPaymentReceipt> findByPaymentIdAndDeletedAtIsNull(UUID paymentId);
    List<StudentPaymentReceipt> findAllByDeletedAtIsNullOrderByIssuedAtDescReceiptNumberDesc();
}
interface StudentPaymentAllocationRepository extends JpaRepository<StudentPaymentAllocation,UUID> {
    List<StudentPaymentAllocation> findAllByDeletedAtIsNullOrderByAllocatedAtDescAllocationNumberDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select allocation from StudentPaymentAllocation allocation where allocation.id=:id and allocation.deletedAt is null") Optional<StudentPaymentAllocation> findLockedById(@Param("id") UUID id);
}
interface StudentPaymentAllocationReversalRepository extends JpaRepository<StudentPaymentAllocationReversal,UUID> {
    Optional<StudentPaymentAllocationReversal> findByAllocationIdAndDeletedAtIsNull(UUID allocationId);
    List<StudentPaymentAllocationReversal> findAllByDeletedAtIsNullOrderByReversedAtDesc();
}
interface StudentPaymentReversalRepository extends JpaRepository<StudentPaymentReversal,UUID> {
    Optional<StudentPaymentReversal> findByPaymentIdAndDeletedAtIsNull(UUID paymentId);
    List<StudentPaymentReversal> findAllByDeletedAtIsNullOrderByReversedAtDesc();
}
interface FinanceCreditNoteRepository extends JpaRepository<FinanceCreditNote,UUID> {
    List<FinanceCreditNote> findAllByDeletedAtIsNullOrderByPreparedAtDescCreditNoteNumberDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select note from FinanceCreditNote note where note.id=:id and note.deletedAt is null") Optional<FinanceCreditNote> findLockedById(@Param("id") UUID id);
}
interface FinanceCreditNoteLineRepository extends JpaRepository<FinanceCreditNoteLine,UUID> {
    List<FinanceCreditNoteLine> findAllByCreditNoteIdAndDeletedAtIsNullOrderByLineNumberAsc(UUID creditNoteId);
    List<FinanceCreditNoteLine> findAllByDeletedAtIsNull();
}
