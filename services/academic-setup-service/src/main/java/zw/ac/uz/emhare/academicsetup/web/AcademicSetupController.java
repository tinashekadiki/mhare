package zw.ac.uz.emhare.academicsetup.web;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.academicsetup.application.AcademicSetupService;
import zw.ac.uz.emhare.academicsetup.application.CurriculumModuleAmendmentService;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupCommands.AddCurriculumModule;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupCommands.CreateAcademicModule;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupCommands.CreateAcademicPeriod;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupCommands.CreateAcademicPeriodType;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupCommands.CreateAcademicUnit;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupCommands.CreateAcademicUnitType;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupCommands.CreateAcademicYear;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupCommands.CreateIntake;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupCommands.CreateProgramme;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupCommands.CreateProgrammeLevel;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupCommands.CreateProgrammeType;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupCommands.CreateProgrammeVersion;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupCommands.UpdateProgramme;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupCommands.UpdateCurriculumModule;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupCommands.RemoveCurriculumModule;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupCommands.RetireProgrammeVersion;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupCommands.VersionedAction;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupCommands.UpdateAcademicYear;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupCommands.UpdateAcademicPeriodType;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupCommands.UpdateAcademicPeriod;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupCommands.UpdateIntake;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupViews.AcademicModuleSummary;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupViews.AdmissionsCatalogue;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupViews.AdmissionsIntakeOption;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupViews.AcademicPeriodSummary;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupViews.AcademicPeriodTypeSummary;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupViews.AcademicSetupOverview;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupViews.AcademicUnitSummary;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupViews.AcademicUnitTypeSummary;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupViews.AcademicYearSummary;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupViews.CurriculumModuleSummary;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupViews.CurriculumModuleUsageSummary;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupViews.IntakeSummary;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupViews.ProgrammeLevelSummary;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupViews.ProgrammeHierarchyResolution;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupViews.ProgrammeSummary;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupViews.ProgrammeTypeSummary;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupViews.ProgrammeVersionSummary;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupViews.RegistrationCatalogue;

/** @author Tinashe K */
@RestController
@RequestMapping("/api/academic")
@PreAuthorize("isAuthenticated()")
public class AcademicSetupController {

    private static final String ACADEMIC_SETUP_ADMIN = "hasAnyRole('system-admin', 'academic-admin')";

    private final AcademicSetupService academicSetupService;
    private final CurriculumModuleAmendmentService curriculumModuleAmendmentService;

    public AcademicSetupController(
            AcademicSetupService academicSetupService,
            CurriculumModuleAmendmentService curriculumModuleAmendmentService) {
        this.academicSetupService = academicSetupService;
        this.curriculumModuleAmendmentService = curriculumModuleAmendmentService;
    }

    @GetMapping("/overview")
    public AcademicSetupOverview overview() {
        return academicSetupService.overview();
    }

    @GetMapping("/admissions-catalogue")
    public AdmissionsCatalogue admissionsCatalogue(
            @RequestParam UUID academicYearId,
            @RequestParam UUID intakeId) {
        return academicSetupService.admissionsCatalogue(academicYearId, intakeId);
    }

    @GetMapping("/admissions-intakes")
    public List<AdmissionsIntakeOption> openAdmissionsIntakes() {
        return academicSetupService.openAdmissionsIntakes();
    }

    @GetMapping("/admissions-intakes/{intakeId}")
    public AdmissionsIntakeOption admissionsIntake(@PathVariable UUID intakeId) {
        return academicSetupService.admissionsIntake(intakeId);
    }

    @GetMapping("/registration-catalogue")
    public RegistrationCatalogue registrationCatalogue(
            @RequestParam UUID academicPeriodId,
            @RequestParam UUID programmeVersionId,
            @RequestParam int periodNumber) {
        return academicSetupService.registrationCatalogue(academicPeriodId, programmeVersionId, periodNumber);
    }

    @PostMapping("/unit-types")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public ResponseEntity<AcademicUnitTypeSummary> createAcademicUnitType(
            @Valid @RequestBody CreateAcademicUnitType request) {
        AcademicUnitTypeSummary created = academicSetupService.createAcademicUnitType(request);
        return ResponseEntity.created(URI.create("/api/academic/unit-types/" + created.id())).body(created);
    }

    @PostMapping("/units")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public ResponseEntity<AcademicUnitSummary> createAcademicUnit(@Valid @RequestBody CreateAcademicUnit request) {
        AcademicUnitSummary created = academicSetupService.createAcademicUnit(request);
        return ResponseEntity.created(URI.create("/api/academic/units/" + created.id())).body(created);
    }

    @PostMapping("/years")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public ResponseEntity<AcademicYearSummary> createAcademicYear(@Valid @RequestBody CreateAcademicYear request) {
        AcademicYearSummary created = academicSetupService.createAcademicYear(request);
        return ResponseEntity.created(URI.create("/api/academic/years/" + created.id())).body(created);
    }

    @PutMapping("/years/{academicYearId}")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public AcademicYearSummary updateAcademicYear(
            @PathVariable UUID academicYearId,
            @Valid @RequestBody UpdateAcademicYear request) {
        return academicSetupService.updateAcademicYear(academicYearId, request);
    }

    @PostMapping("/years/{academicYearId}/open")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public AcademicYearSummary openAcademicYear(
            @PathVariable UUID academicYearId,
            @Valid @RequestBody VersionedAction request) {
        return academicSetupService.openAcademicYear(academicYearId, request.expectedVersion());
    }

    @PostMapping("/years/{academicYearId}/close")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public AcademicYearSummary closeAcademicYear(
            @PathVariable UUID academicYearId,
            @Valid @RequestBody VersionedAction request) {
        return academicSetupService.closeAcademicYear(academicYearId, request.expectedVersion());
    }

    @PostMapping("/period-types")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public ResponseEntity<AcademicPeriodTypeSummary> createAcademicPeriodType(
            @Valid @RequestBody CreateAcademicPeriodType request) {
        AcademicPeriodTypeSummary created = academicSetupService.createAcademicPeriodType(request);
        return ResponseEntity.created(URI.create("/api/academic/period-types/" + created.id())).body(created);
    }

    @PutMapping("/period-types/{academicPeriodTypeId}")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public AcademicPeriodTypeSummary updateAcademicPeriodType(
            @PathVariable UUID academicPeriodTypeId,
            @Valid @RequestBody UpdateAcademicPeriodType request) {
        return academicSetupService.updateAcademicPeriodType(academicPeriodTypeId, request);
    }

    @PostMapping("/periods")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public ResponseEntity<AcademicPeriodSummary> createAcademicPeriod(@Valid @RequestBody CreateAcademicPeriod request) {
        AcademicPeriodSummary created = academicSetupService.createAcademicPeriod(request);
        return ResponseEntity.created(URI.create("/api/academic/periods/" + created.id())).body(created);
    }

    @PutMapping("/periods/{academicPeriodId}")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public AcademicPeriodSummary updateAcademicPeriod(
            @PathVariable UUID academicPeriodId,
            @Valid @RequestBody UpdateAcademicPeriod request) {
        return academicSetupService.updateAcademicPeriod(academicPeriodId, request);
    }

    @PostMapping("/periods/{academicPeriodId}/open")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public AcademicPeriodSummary openAcademicPeriod(
            @PathVariable UUID academicPeriodId,
            @Valid @RequestBody VersionedAction request) {
        return academicSetupService.openAcademicPeriod(academicPeriodId, request.expectedVersion());
    }

    @PostMapping("/periods/{academicPeriodId}/close")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public AcademicPeriodSummary closeAcademicPeriod(
            @PathVariable UUID academicPeriodId,
            @Valid @RequestBody VersionedAction request) {
        return academicSetupService.closeAcademicPeriod(academicPeriodId, request.expectedVersion());
    }

    @PostMapping("/intakes")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public ResponseEntity<IntakeSummary> createIntake(@Valid @RequestBody CreateIntake request) {
        IntakeSummary created = academicSetupService.createIntake(request);
        return ResponseEntity.created(URI.create("/api/academic/intakes/" + created.id())).body(created);
    }

    @PutMapping("/intakes/{intakeId}")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public IntakeSummary updateIntake(
            @PathVariable UUID intakeId,
            @Valid @RequestBody UpdateIntake request) {
        return academicSetupService.updateIntake(intakeId, request);
    }

    @PostMapping("/intakes/{intakeId}/open")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public IntakeSummary openIntake(
            @PathVariable UUID intakeId,
            @Valid @RequestBody VersionedAction request) {
        return academicSetupService.openIntake(intakeId, request.expectedVersion());
    }

    @PostMapping("/intakes/{intakeId}/close")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public IntakeSummary closeIntake(
            @PathVariable UUID intakeId,
            @Valid @RequestBody VersionedAction request) {
        return academicSetupService.closeIntake(intakeId, request.expectedVersion());
    }

    @PostMapping("/programme-levels")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public ResponseEntity<ProgrammeLevelSummary> createProgrammeLevel(
            @Valid @RequestBody CreateProgrammeLevel request) {
        ProgrammeLevelSummary created = academicSetupService.createProgrammeLevel(request);
        return ResponseEntity.created(URI.create("/api/academic/programme-levels/" + created.id())).body(created);
    }

    @PostMapping("/programme-types")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public ResponseEntity<ProgrammeTypeSummary> createProgrammeType(
            @Valid @RequestBody CreateProgrammeType request) {
        ProgrammeTypeSummary created = academicSetupService.createProgrammeType(request);
        return ResponseEntity.created(URI.create("/api/academic/programme-types/" + created.id())).body(created);
    }

    @PostMapping("/programmes")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public ResponseEntity<ProgrammeSummary> createProgramme(@Valid @RequestBody CreateProgramme request) {
        ProgrammeSummary created = academicSetupService.createProgramme(request);
        return ResponseEntity.created(URI.create("/api/academic/programmes/" + created.id())).body(created);
    }

    @PutMapping("/programmes/{programmeId}")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public ProgrammeSummary updateProgramme(
            @PathVariable UUID programmeId,
            @Valid @RequestBody UpdateProgramme request) {
        return academicSetupService.updateProgramme(programmeId, request);
    }

    @GetMapping("/programmes/{programmeId}/hierarchy")
    public ProgrammeHierarchyResolution programmeHierarchy(
            @PathVariable("programmeId") UUID programmeId) {
        return academicSetupService.resolveProgrammeHierarchy(programmeId);
    }

    @PostMapping("/programmes/{programmeId}/activate")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public ProgrammeSummary activateProgramme(
            @PathVariable UUID programmeId,
            @Valid @RequestBody VersionedAction request) {
        return academicSetupService.activateProgramme(programmeId, request.expectedVersion());
    }

    @GetMapping("/programmes/{programmeId}/versions")
    public List<ProgrammeVersionSummary> programmeVersions(@PathVariable UUID programmeId) {
        return academicSetupService.programmeVersions(programmeId);
    }

    @PostMapping("/programmes/{programmeId}/versions")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public ResponseEntity<ProgrammeVersionSummary> createProgrammeVersion(
            @PathVariable UUID programmeId,
            @Valid @RequestBody CreateProgrammeVersion request) {
        ProgrammeVersionSummary created = academicSetupService.createProgrammeVersion(programmeId, request);
        return ResponseEntity.created(URI.create("/api/academic/programme-versions/" + created.id())).body(created);
    }

    @PostMapping("/modules")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public ResponseEntity<AcademicModuleSummary> createAcademicModule(
            @Valid @RequestBody CreateAcademicModule request) {
        AcademicModuleSummary created = academicSetupService.createAcademicModule(request);
        return ResponseEntity.created(URI.create("/api/academic/modules/" + created.id())).body(created);
    }

    @PostMapping("/modules/{moduleId}/activate")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public AcademicModuleSummary activateAcademicModule(
            @PathVariable UUID moduleId,
            @Valid @RequestBody VersionedAction request) {
        return academicSetupService.activateAcademicModule(moduleId, request.expectedVersion());
    }

    @GetMapping("/programme-versions/{programmeVersionId}/curriculum")
    public List<CurriculumModuleSummary> curriculum(@PathVariable UUID programmeVersionId) {
        return academicSetupService.curriculum(programmeVersionId);
    }

    @PostMapping("/programme-versions/{programmeVersionId}/curriculum")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public ResponseEntity<CurriculumModuleSummary> addCurriculumModule(
            @PathVariable UUID programmeVersionId,
            @Valid @RequestBody AddCurriculumModule request) {
        CurriculumModuleSummary created = academicSetupService.addCurriculumModule(programmeVersionId, request);
        return ResponseEntity.created(URI.create(
                "/api/academic/programme-versions/" + programmeVersionId + "/curriculum/" + created.id()))
                .body(created);
    }

    @PutMapping("/programme-versions/{programmeVersionId}/curriculum/{curriculumModuleId}")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public CurriculumModuleSummary updateCurriculumModule(
            @PathVariable("programmeVersionId") UUID programmeVersionId,
            @PathVariable("curriculumModuleId") UUID curriculumModuleId,
            @Valid @RequestBody UpdateCurriculumModule request) {
        return academicSetupService.updateCurriculumModule(programmeVersionId, curriculumModuleId, request);
    }

    @GetMapping("/programme-versions/{programmeVersionId}/curriculum/{curriculumModuleId}/usage")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public CurriculumModuleUsageSummary curriculumModuleUsage(
            @PathVariable("programmeVersionId") UUID programmeVersionId,
            @PathVariable("curriculumModuleId") UUID curriculumModuleId) {
        return curriculumModuleAmendmentService.usage(programmeVersionId, curriculumModuleId);
    }

    @PostMapping("/programme-versions/{programmeVersionId}/curriculum/{curriculumModuleId}/removal")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public ResponseEntity<Void> removeCurriculumModule(
            @PathVariable("programmeVersionId") UUID programmeVersionId,
            @PathVariable("curriculumModuleId") UUID curriculumModuleId,
            @Valid @RequestBody RemoveCurriculumModule request) {
        curriculumModuleAmendmentService.remove(programmeVersionId, curriculumModuleId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/programme-versions/{programmeVersionId}/approve")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public ProgrammeVersionSummary approveProgrammeVersion(
            @PathVariable UUID programmeVersionId,
            @Valid @RequestBody VersionedAction request) {
        return academicSetupService.approveProgrammeVersion(programmeVersionId, request.expectedVersion());
    }

    @PostMapping("/programme-versions/{programmeVersionId}/retire")
    @PreAuthorize(ACADEMIC_SETUP_ADMIN)
    public ProgrammeVersionSummary retireProgrammeVersion(
            @PathVariable UUID programmeVersionId,
            @Valid @RequestBody RetireProgrammeVersion request) {
        return academicSetupService.retireProgrammeVersion(
                programmeVersionId, request.expectedVersion(), request.retirementDate());
    }
}
