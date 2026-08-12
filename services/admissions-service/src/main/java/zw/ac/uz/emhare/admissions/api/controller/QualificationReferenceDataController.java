package zw.ac.uz.emhare.admissions.api.controller;

import zw.ac.uz.emhare.admissions.api.model.*;

import zw.ac.uz.emhare.admissions.*;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.GradeReferenceOption;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.QualificationReferenceData;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.QualificationReferenceManagementData;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.SubjectReferenceOption;
import zw.ac.uz.emhare.admissions.application.QualificationReferenceDataService;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient;

/** Qualification subject and grade reference endpoints. @author Tinashe K */
@RestController
@RequestMapping("/api/admissions/qualification-reference-data")
public class QualificationReferenceDataController {

    private final QualificationReferenceDataService referenceDataService;
    private final CoreIdentityClient coreIdentityClient;

    public QualificationReferenceDataController(
            QualificationReferenceDataService referenceDataService,
            CoreIdentityClient coreIdentityClient) {
        this.referenceDataService = referenceDataService;
        this.coreIdentityClient = coreIdentityClient;
    }

    @GetMapping
    public QualificationReferenceData activeReferenceData() {
        return referenceDataService.activeReferenceData();
    }

    @GetMapping("/manage")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_SETUP_MANAGE')")
    public QualificationReferenceManagementData managementReferenceData() {
        return referenceDataService.managementReferenceData();
    }

    @PostMapping("/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_SETUP_MANAGE')")
    public SubjectReferenceOption createSubject(@Valid @RequestBody SaveQualificationSubjectReferenceRequest request) {
        return referenceDataService.createSubject(
                request.level(), request.code(), request.name(), request.subjectGroupCode(),
                request.scienceSubject(), request.active());
    }

    @PutMapping("/subjects/{subjectId}")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_SETUP_MANAGE')")
    public SubjectReferenceOption updateSubject(
            @PathVariable("subjectId") UUID subjectId,
            @Valid @RequestBody SaveQualificationSubjectReferenceRequest request) {
        return referenceDataService.updateSubject(
                subjectId, request.code(), request.name(), request.subjectGroupCode(),
                request.scienceSubject(), request.active(), request.expectedVersion());
    }

    @DeleteMapping("/subjects/{subjectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_SETUP_MANAGE')")
    public void deleteSubject(
            Authentication authentication,
            @PathVariable("subjectId") UUID subjectId,
            @RequestParam(name = "expectedVersion") long expectedVersion) {
        referenceDataService.deleteSubject(subjectId, currentUserId(authentication), expectedVersion);
    }

    @PostMapping("/grades")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_SETUP_MANAGE')")
    public GradeReferenceOption createGrade(@Valid @RequestBody SaveQualificationGradeReferenceRequest request) {
        return referenceDataService.createGrade(
                request.level(), request.grade(), request.points(), request.pass(), request.sortOrder());
    }

    @PutMapping("/grades/{gradeId}")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_SETUP_MANAGE')")
    public GradeReferenceOption updateGrade(
            @PathVariable("gradeId") UUID gradeId,
            @Valid @RequestBody SaveQualificationGradeReferenceRequest request) {
        return referenceDataService.updateGrade(
                gradeId, request.grade(), request.points(), request.pass(),
                request.sortOrder(), request.expectedVersion());
    }

    @DeleteMapping("/grades/{gradeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_SETUP_MANAGE')")
    public void deleteGrade(
            Authentication authentication,
            @PathVariable("gradeId") UUID gradeId,
            @RequestParam(name = "expectedVersion") long expectedVersion) {
        referenceDataService.deleteGrade(gradeId, currentUserId(authentication), expectedVersion);
    }

    private UUID currentUserId(Authentication authentication) {
        return coreIdentityClient.syncCurrentUser(authentication).user().id();
    }
}
