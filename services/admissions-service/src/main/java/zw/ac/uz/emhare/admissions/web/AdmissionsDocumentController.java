package zw.ac.uz.emhare.admissions.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.admissions.application.AdmissionsDocumentService;
import zw.ac.uz.emhare.admissions.application.AdmissionsDocumentViews.AcademicUnitApplicationDocumentEntry;
import zw.ac.uz.emhare.admissions.application.AdmissionsDocumentViews.ApplicationDocumentRegister;
import zw.ac.uz.emhare.admissions.application.AdmissionsDocumentViews.DocumentRequirementSummary;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient;

/** @author Tinashe K */
@RestController
@RequestMapping("/api/admissions")
public class AdmissionsDocumentController {
    private final AdmissionsDocumentService documentService;
    private final CoreIdentityClient coreIdentityClient;

    public AdmissionsDocumentController(
            AdmissionsDocumentService documentService,
            CoreIdentityClient coreIdentityClient) {
        this.documentService = documentService;
        this.coreIdentityClient = coreIdentityClient;
    }

    @GetMapping("/application-types/{applicationTypeId}/document-requirements")
    public List<DocumentRequirementSummary> requirements(@PathVariable UUID applicationTypeId) {
        return documentService.requirements(applicationTypeId);
    }

    @PostMapping("/application-types/{applicationTypeId}/document-requirements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_SETUP_MANAGE')")
    public DocumentRequirementSummary createRequirement(
            @PathVariable UUID applicationTypeId,
            @Valid @RequestBody CreateDocumentRequirementRequest request) {
        return documentService.createRequirement(
                applicationTypeId,
                request.requirementCode(),
                request.requirementName(),
                request.required(),
                request.sortOrder());
    }

    @GetMapping("/applications/{applicationId}/documents/mine")
    public ApplicationDocumentRegister applicantRegister(
            Authentication authentication,
            @PathVariable UUID applicationId) {
        UUID applicantUserId = coreIdentityClient.syncCurrentUser(authentication).user().id();
        return documentService.applicantRegister(applicationId, applicantUserId);
    }

    @PostMapping("/applications/{applicationId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_APPLY')")
    public ApplicationDocumentRegister linkDocument(
            Authentication authentication,
            @PathVariable UUID applicationId,
            @Valid @RequestBody LinkApplicationDocumentRequest request) {
        UUID applicantUserId = coreIdentityClient.syncCurrentUser(authentication).user().id();
        return documentService.linkApplicantDocument(
                applicationId, applicantUserId, request.documentId(), request.requirementCode());
    }

    @GetMapping("/applications/{applicationId}/documents")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW')")
    public ApplicationDocumentRegister staffRegister(@PathVariable UUID applicationId) {
        return documentService.staffRegister(applicationId);
    }

    @GetMapping("/academic-units/{academicUnitId}/documents")
    @PreAuthorize("@admissionsRbac.has(authentication, 'ADMISSIONS_APPLICATION_REVIEW')")
    public List<AcademicUnitApplicationDocumentEntry> academicUnitRegister(@PathVariable UUID academicUnitId) {
        return documentService.academicUnitRegister(academicUnitId);
    }
}
