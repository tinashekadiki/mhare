package zw.ac.uz.emhare.finance.catalogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.finance.catalogue.FinanceFeeStructureContracts.AcademicUnitPathItem;
import zw.ac.uz.emhare.finance.catalogue.FinanceFeeStructureContracts.ResolveStructure;
import zw.ac.uz.emhare.finance.payment.ExchangeRateRepository;

/** @author Tinashe K */
class FinanceFeeStructureTest {
    private static final Instant EFFECTIVE_AT = Instant.parse("2027-02-01T00:00:00Z");
    private static final UUID UG_LEVEL_ID = UUID.randomUUID();
    private static final String UG_LEVEL_CODE = "UG";

    @Test
    void requiresAcademicApplicationAndAccommodationScopesToFollowTheirOwnRules() {
        UUID actor = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new FinanceFeeStructure("SCI-PERIOD", "Science period fees", null,
                FinanceFeeStructure.FeeContext.ACADEMIC, FinanceFeeStructure.ScopeType.ACADEMIC_UNIT,
                UUID.randomUUID(), "SCI", "Faculty of Science", UG_LEVEL_ID, UG_LEVEL_CODE, "Undergraduate",
                periodId, "2027-S1", "2027 Semester 1", 1, null, "USD", EFFECTIVE_AT, null, actor));
        assertThrows(IllegalArgumentException.class, () -> new FinanceFeeStructure("APP-WRONG", "Application", null,
                FinanceFeeStructure.FeeContext.APPLICATION, FinanceFeeStructure.ScopeType.PROGRAMME_TYPE,
                UUID.randomUUID(), "DEGREE", "Degree", UG_LEVEL_ID, UG_LEVEL_CODE, "Undergraduate",
                null, null, null, null, null, "USD", EFFECTIVE_AT, null, actor));
        assertThrows(IllegalArgumentException.class, () -> new FinanceFeeStructure("ACCOM-WRONG", "Accommodation", null,
                FinanceFeeStructure.FeeContext.ACCOMMODATION, FinanceFeeStructure.ScopeType.ACADEMIC_UNIT,
                UUID.randomUUID(), "SCI", "Faculty of Science", UG_LEVEL_ID, UG_LEVEL_CODE, "Undergraduate",
                null, null, null, null, null, "USD", EFFECTIVE_AT, null, actor));
        FinanceFeeStructure academic = new FinanceFeeStructure("SCI-FEES", "Science fees", null,
                FinanceFeeStructure.FeeContext.ACADEMIC, FinanceFeeStructure.ScopeType.ACADEMIC_UNIT,
                UUID.randomUUID(), "SCI", "Faculty of Science", UG_LEVEL_ID, UG_LEVEL_CODE, "Undergraduate",
                null, null, null, null, null, "USD", EFFECTIVE_AT, null, actor);
        assertEquals(FinanceFeeStructure.Status.DRAFT, academic.getStatus());
        FinanceFeeStructure application = new FinanceFeeStructure("APP-UG-LOCAL", "Local undergraduate application", null,
                FinanceFeeStructure.FeeContext.APPLICATION, FinanceFeeStructure.ScopeType.PROGRAMME_LEVEL,
                UG_LEVEL_ID, UG_LEVEL_CODE, "Undergraduate", UG_LEVEL_ID, UG_LEVEL_CODE, "Undergraduate",
                null, null, null, null, "LOCAL", "USD", EFFECTIVE_AT, null, actor);
        assertEquals("LOCAL", application.getApplicantCategoryCode());
        assertThrows(IllegalArgumentException.class, () -> new FinanceFeeStructure(
                "APP-UG-UNKNOWN", "Unsupported applicant category", null,
                FinanceFeeStructure.FeeContext.APPLICATION, FinanceFeeStructure.ScopeType.PROGRAMME_LEVEL,
                UG_LEVEL_ID, UG_LEVEL_CODE, "Undergraduate", UG_LEVEL_ID, UG_LEVEL_CODE, "Undergraduate",
                null, null, null, null, "UNKNOWN", "USD", EFFECTIVE_AT, null, actor));
    }

    @Test
    void programmeStructureReplacesLowerAndHigherAcademicUnitStructuresAsAWhole() {
        UUID periodId = UUID.randomUUID();
        UUID programmeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID facultyId = UUID.randomUUID();
        FinanceFeeStructure institution = activeStructure("INST", FinanceFeeStructure.ScopeType.INSTITUTION,
                null, periodId, null);
        FinanceFeeStructure faculty = activeStructure("FAC", FinanceFeeStructure.ScopeType.ACADEMIC_UNIT,
                facultyId, periodId, null);
        FinanceFeeStructure department = activeStructure("DEPT", FinanceFeeStructure.ScopeType.ACADEMIC_UNIT,
                departmentId, periodId, null);
        FinanceFeeStructure programme = activeStructure("PROG", FinanceFeeStructure.ScopeType.PROGRAMME,
                programmeId, periodId, null);
        GovernedFinanceFeeStructureService service = serviceWith(institution, faculty, department, programme);

        var resolved = service.resolve(new ResolveStructure(FinanceFeeStructure.FeeContext.ACADEMIC, EFFECTIVE_AT,
                periodId, programmeId,
                List.of(new AcademicUnitPathItem(departmentId, "ACC", "Accounting"),
                        new AcademicUnitPathItem(facultyId, "COM", "Commerce")),
                UG_LEVEL_ID, UG_LEVEL_CODE, null, null, 1));

        assertEquals("PROG", resolved.code());
    }

    @Test
    void nearestAcademicUnitReplacesItsParentWhenNoProgrammeOverrideExists() {
        UUID periodId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID facultyId = UUID.randomUUID();
        FinanceFeeStructure faculty = activeStructure("FAC", FinanceFeeStructure.ScopeType.ACADEMIC_UNIT,
                facultyId, periodId, null);
        FinanceFeeStructure department = activeStructure("DEPT", FinanceFeeStructure.ScopeType.ACADEMIC_UNIT,
                departmentId, periodId, null);
        GovernedFinanceFeeStructureService service = serviceWith(faculty, department);

        var resolved = service.resolve(new ResolveStructure(FinanceFeeStructure.FeeContext.ACADEMIC, EFFECTIVE_AT,
                periodId, UUID.randomUUID(),
                List.of(new AcademicUnitPathItem(departmentId, "ACC", "Accounting"),
                        new AcademicUnitPathItem(facultyId, "COM", "Commerce")),
                UG_LEVEL_ID, UG_LEVEL_CODE, null, null, 1));

        assertEquals("DEPT", resolved.code());
    }

    @Test
    void applicantCategoryOverrideWinsWithinTheMatchingProgrammeLevel() {
        UUID programmeLevelId = UUID.randomUUID();
        FinanceFeeStructure allApplicants = activeApplicationStructure("APP-UG-ALL", programmeLevelId, null);
        FinanceFeeStructure localApplicants = activeApplicationStructure("APP-UG-LOCAL", programmeLevelId, "LOCAL");
        GovernedFinanceFeeStructureService service = serviceWith(allApplicants, localApplicants);

        var local = service.resolve(new ResolveStructure(FinanceFeeStructure.FeeContext.APPLICATION, EFFECTIVE_AT,
                null, null, List.of(), programmeLevelId, UG_LEVEL_CODE, null, "LOCAL", null));
        var sadc = service.resolve(new ResolveStructure(FinanceFeeStructure.FeeContext.APPLICATION, EFFECTIVE_AT,
                null, null, List.of(), programmeLevelId, UG_LEVEL_CODE, null, "SADC", null));

        assertEquals("APP-UG-LOCAL", local.code());
        assertEquals("APP-UG-ALL", sadc.code());
    }

    @Test
    void institutionDefaultsDoNotCrossProgrammeLevels() {
        UUID postgraduateLevelId = UUID.randomUUID();
        FinanceFeeStructure undergraduate = activeStructure("UG-DEFAULT",
                FinanceFeeStructure.ScopeType.INSTITUTION, null, null, null);
        FinanceFeeStructure postgraduate = activeStructure("PG-DEFAULT",
                FinanceFeeStructure.ScopeType.INSTITUTION, null, null, null);
        when(postgraduate.getProgrammeLevelId()).thenReturn(postgraduateLevelId);
        when(postgraduate.getProgrammeLevelCode()).thenReturn("PG");
        GovernedFinanceFeeStructureService service = serviceWith(undergraduate, postgraduate);

        var resolved = service.resolve(new ResolveStructure(FinanceFeeStructure.FeeContext.ACADEMIC, EFFECTIVE_AT,
                null, UUID.randomUUID(), List.of(), postgraduateLevelId, "PG", null, null, 1));

        assertEquals("PG-DEFAULT", resolved.code());
    }

    private GovernedFinanceFeeStructureService serviceWith(FinanceFeeStructure... structures) {
        FinanceFeeStructureRepository structureRepository = mock(FinanceFeeStructureRepository.class);
        FinanceFeeCatalogueRepository catalogueRepository = mock(FinanceFeeCatalogueRepository.class);
        FinanceFeeRuleRepository ruleRepository = mock(FinanceFeeRuleRepository.class);
        FinanceFeeRuleScopeRepository scopeRepository = mock(FinanceFeeRuleScopeRepository.class);
        FinanceFeeStructureAttachmentRepository attachmentRepository = mock(FinanceFeeStructureAttachmentRepository.class);
        ExchangeRateRepository exchangeRateRepository = mock(ExchangeRateRepository.class);
        when(structureRepository.findAllByStatusAndDeletedAtIsNull(FinanceFeeStructure.Status.ACTIVE))
                .thenReturn(List.of(structures));
        when(ruleRepository.findAllByFeeStructureIdAndDeletedAtIsNullOrderByStructureLineNumberAsc(null))
                .thenReturn(List.of());
        return new GovernedFinanceFeeStructureService(structureRepository, catalogueRepository, ruleRepository,
                scopeRepository, attachmentRepository, exchangeRateRepository, Clock.fixed(EFFECTIVE_AT, ZoneOffset.UTC));
    }

    private FinanceFeeStructure activeStructure(String code, FinanceFeeStructure.ScopeType scopeType,
            UUID referenceId, UUID academicPeriodId, Integer programmePeriodNumber) {
        FinanceFeeStructure structure = mock(FinanceFeeStructure.class);
        when(structure.getCode()).thenReturn(code);
        when(structure.getName()).thenReturn(code);
        when(structure.getFeeContext()).thenReturn(FinanceFeeStructure.FeeContext.ACADEMIC);
        when(structure.getScopeType()).thenReturn(scopeType);
        when(structure.getScopeReferenceId()).thenReturn(referenceId);
        when(structure.getProgrammeLevelId()).thenReturn(UG_LEVEL_ID);
        when(structure.getProgrammeLevelCode()).thenReturn(UG_LEVEL_CODE);
        when(structure.getProgrammeLevelName()).thenReturn("Undergraduate");
        when(structure.getAcademicPeriodId()).thenReturn(academicPeriodId);
        when(structure.getProgrammePeriodNumber()).thenReturn(programmePeriodNumber);
        when(structure.getTransactionCurrencyCode()).thenReturn("USD");
        when(structure.getEffectiveFrom()).thenReturn(EFFECTIVE_AT.minusSeconds(86400));
        when(structure.getStatus()).thenReturn(FinanceFeeStructure.Status.ACTIVE);
        when(structure.getVersion()).thenReturn(1L);
        return structure;
    }

    private FinanceFeeStructure activeApplicationStructure(String code, UUID programmeLevelId, String applicantCategoryCode) {
        FinanceFeeStructure structure = mock(FinanceFeeStructure.class);
        when(structure.getCode()).thenReturn(code);
        when(structure.getName()).thenReturn(code);
        when(structure.getFeeContext()).thenReturn(FinanceFeeStructure.FeeContext.APPLICATION);
        when(structure.getScopeType()).thenReturn(FinanceFeeStructure.ScopeType.PROGRAMME_LEVEL);
        when(structure.getScopeReferenceId()).thenReturn(programmeLevelId);
        when(structure.getProgrammeLevelId()).thenReturn(programmeLevelId);
        when(structure.getProgrammeLevelCode()).thenReturn(UG_LEVEL_CODE);
        when(structure.getProgrammeLevelName()).thenReturn("Undergraduate");
        when(structure.getApplicantCategoryCode()).thenReturn(applicantCategoryCode);
        when(structure.getTransactionCurrencyCode()).thenReturn("USD");
        when(structure.getEffectiveFrom()).thenReturn(EFFECTIVE_AT.minusSeconds(86400));
        when(structure.getStatus()).thenReturn(FinanceFeeStructure.Status.ACTIVE);
        when(structure.getVersion()).thenReturn(1L);
        return structure;
    }
}
