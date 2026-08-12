package zw.ac.uz.emhare.admissions.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.admissions.api.model.ConfigureApplicationRouteRequest;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationType;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationTypeDocumentRequirement;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationTypeProgrammeMapping;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationTypeSection;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeDocumentRequirementRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeProgrammeMappingRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationTypeSectionRepository;

/** @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class ApplicationRouteConfigurationServiceTest {
    @Mock private ApplicationTypeRepository applicationTypeRepository;
    @Mock private ApplicationTypeProgrammeMappingRepository mappingRepository;
    @Mock private ApplicationTypeSectionRepository sectionRepository;
    @Mock private ApplicationTypeDocumentRequirementRepository documentRepository;

    private ApplicationRouteConfigurationService service;
    private ApplicationType mba;
    private UUID mbaId;
    private ApplicationTypeProgrammeMapping mapping;
    private ApplicationTypeDocumentRequirement document;
    private List<ApplicationTypeSection> sections;

    @BeforeEach
    void setUp() {
        service = new ApplicationRouteConfigurationService(
                applicationTypeRepository, mappingRepository, sectionRepository, documentRepository,
                Clock.fixed(Instant.parse("2027-01-15T10:00:00Z"), ZoneOffset.UTC));
        mba = new ApplicationType("MBA", "Master of Business Administration", true, true, false);
        mbaId = UUID.randomUUID();
        ReflectionTestUtils.setField(mba, "id", mbaId);
        mapping = new ApplicationTypeProgrammeMapping(mba, UUID.randomUUID(), "MBA", "MBA");
        document = new ApplicationTypeDocumentRequirement(mba, "DEGREE_CERTIFICATE", "Degree certificate", true, 10);
        sections = mbaSections(3);
        when(applicationTypeRepository.findById(mbaId)).thenReturn(Optional.of(mba));
        when(mappingRepository.findAllByApplicationTypeIdAndDeletedAtIsNull(mbaId)).thenReturn(List.of(mapping));
        when(mappingRepository.findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderByProgrammeCodeAsc(mbaId))
                .thenReturn(List.of(mapping));
        when(sectionRepository.findAllByApplicationTypeIdAndDeletedAtIsNull(mbaId)).thenReturn(sections);
        when(sectionRepository.findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAsc(mbaId))
                .thenReturn(sections);
        when(documentRepository.findAllByApplicationTypeIdAndDeletedAtIsNull(mbaId)).thenReturn(List.of(document));
        when(documentRepository.findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscRequirementCodeAsc(mbaId))
                .thenReturn(List.of(document));
        org.mockito.Mockito.lenient().when(applicationTypeRepository.saveAndFlush(any(ApplicationType.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void atomicallyActivatesACompleteMbaRouteWithThreeReferencesAndAuditedFeeFreeDecision() {
        var result = service.configure(mbaId, UUID.randomUUID(), request(sections, true));

        assertTrue(result.active());
        assertTrue(result.readyForActivation());
        assertEquals("FEE_FREE", result.feePolicyStatus());
        assertEquals(List.of(mapping.getProgrammeId()),
                result.programmes().stream().map(
                        ApplicationRouteConfigurationSummary.ProgrammeMappingSummary::programmeId).toList());
        assertEquals(List.of("DEGREE_CERTIFICATE"),
                result.documents().stream().map(
                        ApplicationRouteConfigurationSummary.DocumentRequirementSummary::code).toList());
        assertEquals(3, result.sections().stream()
                .filter(section -> "REFEREES".equals(section.code())).findFirst().orElseThrow().minimumRecords());
    }

    @Test
    void refusesMbaActivationWhenOnlyTwoReferencesAreConfigured() {
        sections = mbaSections(2);
        when(sectionRepository.findAllByApplicationTypeIdAndDeletedAtIsNull(mbaId)).thenReturn(sections);
        when(sectionRepository.findAllByApplicationTypeIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAsc(mbaId))
                .thenReturn(sections);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.configure(mbaId, UUID.randomUUID(), request(sections, true)));

        assertTrue(exception.getMessage().contains("exactly 3 completed responses"));
    }

    private ConfigureApplicationRouteRequest request(List<ApplicationTypeSection> configuredSections, boolean activate) {
        return new ConfigureApplicationRouteRequest(
                List.of(new ConfigureApplicationRouteRequest.ProgrammeMappingInput(
                        mapping.getProgrammeId(), mapping.getProgrammeCode(), mapping.getProgrammeName())),
                configuredSections.stream().map(section -> new ConfigureApplicationRouteRequest.SectionInput(
                        section.getSectionCode(), section.getSectionName(), section.isRequired(), section.isRepeatable(),
                        section.getMinimumRecords(), section.getSortOrder())).toList(),
                List.of(new ConfigureApplicationRouteRequest.DocumentInput(
                        document.getRequirementCode(), document.getRequirementName(), true, document.getSortOrder())),
                true, "Senate approved this route as fee free.", activate,
                "Configure the complete MBA admissions route.", 0);
    }

    private List<ApplicationTypeSection> mbaSections(int references) {
        return List.of(
                section("PERSONAL_DETAILS", 0, 10), section("NEXT_OF_KIN", 1, 20),
                section("QUALIFICATIONS", 1, 30), section("PRIOR_UZ_STUDY", 1, 35),
                section("PROFESSIONAL_ACHIEVEMENTS", 1, 38), section("EMPLOYMENT_HISTORY", 1, 40),
                section("REFEREES", references, 50), section("PROGRAMME_CHOICES", 1, 60),
                section("DOCUMENTS", 0, 70), section("PAYMENT", 0, 80),
                section("REVIEW_DECLARATION", 0, 90));
    }

    private ApplicationTypeSection section(String code, int minimumRecords, int sortOrder) {
        return new ApplicationTypeSection(mba, code, code.replace('_', ' '), true,
                minimumRecords > 0, minimumRecords, sortOrder);
    }
}
