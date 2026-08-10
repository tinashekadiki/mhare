package zw.ac.uz.emhare.finance.catalogue;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** @author Tinashe K */
interface FinanceFeeCatalogueRepository extends JpaRepository<FinanceFeeCatalogue,UUID> {
    List<FinanceFeeCatalogue> findAllByDeletedAtIsNullOrderByCodeAsc();
    Optional<FinanceFeeCatalogue> findByCodeIgnoreCaseAndDeletedAtIsNull(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<FinanceFeeCatalogue> findLockedByIdAndDeletedAtIsNull(UUID id);
}
interface FinanceFeeRuleRepository extends JpaRepository<FinanceFeeRule,UUID> {
    List<FinanceFeeRule> findAllByFeeCatalogueIdAndDeletedAtIsNullOrderByRuleVersionDesc(UUID catalogueId);
    Optional<FinanceFeeRule> findFirstByFeeCatalogueIdAndDeletedAtIsNullOrderByRuleVersionDesc(UUID catalogueId);
    List<FinanceFeeRule> findAllByFeeStructureIdAndDeletedAtIsNullOrderByStructureLineNumberAsc(UUID feeStructureId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<FinanceFeeRule> findLockedByIdAndDeletedAtIsNull(UUID id);
}
interface FinanceFeeStructureRepository extends JpaRepository<FinanceFeeStructure,UUID> {
    List<FinanceFeeStructure> findAllByDeletedAtIsNullOrderByCreatedAtDesc();
    List<FinanceFeeStructure> findAllByStatusAndDeletedAtIsNull(FinanceFeeStructure.Status status);
    Optional<FinanceFeeStructure> findByCodeIgnoreCaseAndDeletedAtIsNull(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<FinanceFeeStructure> findLockedByIdAndDeletedAtIsNull(UUID id);
}
interface FinanceFeeStructureAttachmentRepository extends JpaRepository<FinanceFeeStructureAttachment,UUID> {
    List<FinanceFeeStructureAttachment> findAllByFeeStructureIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID feeStructureId);
}
interface FinanceStudentDiscountRuleRepository extends JpaRepository<FinanceStudentDiscountRule,UUID> {
    List<FinanceStudentDiscountRule> findAllByDeletedAtIsNullOrderByCreatedAtDesc();
    List<FinanceStudentDiscountRule> findAllByStatusAndDeletedAtIsNull(FinanceStudentDiscountRule.Status status);
    Optional<FinanceStudentDiscountRule> findByCodeIgnoreCaseAndDeletedAtIsNull(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FinanceStudentDiscountRule> findLockedByIdAndDeletedAtIsNull(UUID id);
}
interface FinanceStudentDiscountRuleProgrammeRepository extends JpaRepository<FinanceStudentDiscountRuleProgramme,UUID> {
    List<FinanceStudentDiscountRuleProgramme> findAllByDiscountRuleIdAndDeletedAtIsNullOrderByProgrammeCodeAsc(UUID ruleId);
    List<FinanceStudentDiscountRuleProgramme> findAllByProgrammeIdAndDeletedAtIsNull(UUID programmeId);
}
interface FinanceStudentDiscountRuleProgrammePeriodRepository extends JpaRepository<FinanceStudentDiscountRuleProgrammePeriod,UUID> {
    List<FinanceStudentDiscountRuleProgrammePeriod> findAllByDiscountRuleProgrammeIdInAndDeletedAtIsNull(
            List<UUID> discountRuleProgrammeIds);
}
interface FinanceFeeRuleScopeRepository extends JpaRepository<FinanceFeeRuleScope,UUID> {
    List<FinanceFeeRuleScope> findAllByFeeRuleIdAndDeletedAtIsNullOrderByScopeDimensionAsc(UUID ruleId);
}
