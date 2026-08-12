package zw.ac.uz.emhare.finance.catalogue;

import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeCatalogue;
import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceStudentDiscountRule;
import zw.ac.uz.emhare.finance.catalogue.infrastructure.persistence.FinanceFeeCatalogueRepository;
import zw.ac.uz.emhare.finance.catalogue.infrastructure.persistence.FinanceStudentDiscountRuleRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceStudentDiscountApiModels.ResolveDiscount;

/** @author Tinashe K */
class FinanceStudentDiscountResolutionTest {
    private static final Instant EFFECTIVE_AT = Instant.parse("2026-08-09T12:00:00Z");
    private static final UUID PROGRAMME_LEVEL_ID = UUID.randomUUID();
    private static final UUID ACADEMIC_UNIT_ID = UUID.randomUUID();
    private static final UUID PROGRAMME_ID = UUID.randomUUID();

    @Test
    void programmeDiscountOverridesAcademicUnitAndGlobalDiscounts() {
        UUID feeId = UUID.randomUUID();
        FinanceStudentDiscountRule global = rule("GLOBAL", null, null,
                FinanceStudentDiscountRule.TargetType.ALL_FEES, null, "5.00");
        FinanceStudentDiscountRule unit = rule("UNIT", ACADEMIC_UNIT_ID, null,
                FinanceStudentDiscountRule.TargetType.ALL_FEES, null, "10.00");
        FinanceStudentDiscountRule programme = rule("PROGRAMME", null, PROGRAMME_ID,
                FinanceStudentDiscountRule.TargetType.ALL_FEES, null, "15.00");

        var applied = service(global, unit, programme).resolve(command(feeId, "3.1")).orElseThrow();

        assertEquals("PROGRAMME", applied.code());
        assertEquals(new BigDecimal("15.00"), applied.percentage());
    }

    @Test
    void feeLineDiscountOverridesAllFeesAtTheSameScope() {
        UUID feeId = UUID.randomUUID();
        FinanceStudentDiscountRule allFees = rule("ALL", null, PROGRAMME_ID,
                FinanceStudentDiscountRule.TargetType.ALL_FEES, null, "10.00");
        FinanceStudentDiscountRule feeLine = rule("TUITION", null, PROGRAMME_ID,
                FinanceStudentDiscountRule.TargetType.FEE_LINE, feeId, "20.00");

        assertEquals("TUITION", service(allFees, feeLine).resolve(command(feeId, "3.1")).orElseThrow().code());
    }

    @Test
    void everyConfiguredStudentDimensionMustMatch() {
        UUID feeId = UUID.randomUUID();
        FinanceStudentDiscountRule programmeAndUnit = rule("ATTACHMENT", ACADEMIC_UNIT_ID, PROGRAMME_ID,
                FinanceStudentDiscountRule.TargetType.ALL_FEES, null, "25.00");
        GovernedFinanceStudentDiscountService service = service(programmeAndUnit);

        assertTrue(service.resolve(command(feeId, "3.1")).isPresent());
        assertTrue(service.resolve(new ResolveDiscount(feeId, PROGRAMME_ID, UUID.randomUUID(),
                PROGRAMME_LEVEL_ID, "UG", "3.1", EFFECTIVE_AT)).isEmpty());
        assertTrue(service.resolve(new ResolveDiscount(feeId, UUID.randomUUID(), ACADEMIC_UNIT_ID,
                PROGRAMME_LEVEL_ID, "UG", "3.1", EFFECTIVE_AT)).isEmpty());
        assertTrue(service.resolve(command(feeId, "3.2")).isEmpty());
        assertTrue(service.resolve(new ResolveDiscount(feeId, PROGRAMME_ID, ACADEMIC_UNIT_ID,
                UUID.randomUUID(), "PG", "3.1", EFFECTIVE_AT)).isEmpty());
    }

    @Test
    void equalPriorityMatchesAreRejected() {
        UUID feeId = UUID.randomUUID();
        FinanceStudentDiscountRule first = rule("FIRST", null, PROGRAMME_ID,
                FinanceStudentDiscountRule.TargetType.ALL_FEES, null, "10.00");
        FinanceStudentDiscountRule second = rule("SECOND", null, PROGRAMME_ID,
                FinanceStudentDiscountRule.TargetType.ALL_FEES, null, "12.00");

        assertThrows(IllegalStateException.class, () -> service(first, second).resolve(command(feeId, "3.1")));
    }

    private ResolveDiscount command(UUID feeId, String studyLevel) {
        return new ResolveDiscount(feeId, PROGRAMME_ID, ACADEMIC_UNIT_ID,
                PROGRAMME_LEVEL_ID, "UG", studyLevel, EFFECTIVE_AT);
    }

    private GovernedFinanceStudentDiscountService service(FinanceStudentDiscountRule... rules) {
        FinanceStudentDiscountRuleRepository ruleRepository = mock(FinanceStudentDiscountRuleRepository.class);
        when(ruleRepository.findAllByStatusAndDeletedAtIsNull(FinanceStudentDiscountRule.Status.ACTIVE))
                .thenReturn(List.of(rules));
        return new GovernedFinanceStudentDiscountService(ruleRepository,
                mock(FinanceFeeCatalogueRepository.class), Clock.fixed(EFFECTIVE_AT, ZoneOffset.UTC));
    }

    private FinanceStudentDiscountRule rule(String code, UUID academicUnitId, UUID programmeId,
            FinanceStudentDiscountRule.TargetType targetType, UUID feeId, String percentage) {
        FinanceFeeCatalogue catalogue = null;
        if (feeId != null) {
            catalogue = mock(FinanceFeeCatalogue.class);
            when(catalogue.getId()).thenReturn(feeId);
        }
        FinanceStudentDiscountRule rule = new FinanceStudentDiscountRule(
                code, code,
                academicUnitId, academicUnitId == null ? null : "UNIT", academicUnitId == null ? null : "Unit", 2,
                programmeId, programmeId == null ? null : "BSC", programmeId == null ? null : "Programme",
                PROGRAMME_LEVEL_ID, "UG", "Undergraduate", "3.1", targetType, catalogue,
                new BigDecimal(percentage), "Authority", EFFECTIVE_AT.minusSeconds(60), null, UUID.randomUUID());
        ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(rule, "status", FinanceStudentDiscountRule.Status.ACTIVE);
        return rule;
    }
}
