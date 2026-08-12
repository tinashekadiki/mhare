package zw.ac.uz.emhare.documentsreporting.document.api.controller;

import zw.ac.uz.emhare.documentsreporting.document.*;
import zw.ac.uz.emhare.documentsreporting.document.api.model.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.documentsreporting.document.api.model.DocumentResponses.DocumentDownload;
import zw.ac.uz.emhare.documentsreporting.document.api.model.DocumentResponses.DocumentSummary;
import zw.ac.uz.emhare.documentsreporting.document.api.model.DocumentResponses.RetryDocument;
import zw.ac.uz.emhare.documentsreporting.integration.CoreIdentityClient;
import org.springframework.security.core.Authentication;

/** @author Tinashe K */
@RestController
@RequestMapping("/api/documents")
public class OfficialDocumentController {

    private final OfficialDocumentService documentService;
    private final CoreIdentityClient coreIdentityClient;

    public OfficialDocumentController(OfficialDocumentService documentService, CoreIdentityClient coreIdentityClient) {
        this.documentService = documentService;
        this.coreIdentityClient = coreIdentityClient;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_system-admin','ROLE_academic-admin','ROLE_admissions-officer')")
    public List<DocumentSummary> documents() {
        return documentService.documents();
    }

    @GetMapping("/{documentId}/download")
    @PreAuthorize("hasAnyAuthority('ROLE_system-admin','ROLE_academic-admin','ROLE_admissions-officer')")
    public DocumentDownload download(@PathVariable("documentId") UUID documentId,
            @RequestParam(value = "disposition", defaultValue = "inline") String disposition) {
        return documentService.download(documentId, disposition);
    }

    @GetMapping("/{documentId}/applicant-download")
    @PreAuthorize("isAuthenticated()")
    public DocumentDownload applicantDownload(Authentication authentication,
            @PathVariable("documentId") UUID documentId,
            @RequestParam(value = "disposition", defaultValue = "inline") String disposition) {
        UUID applicantUserId = coreIdentityClient.syncCurrentUser(authentication).user().id();
        return documentService.applicantOfferDownload(documentId, applicantUserId, disposition);
    }

    @PostMapping("/{documentId}/retry")
    @PreAuthorize("hasAnyAuthority('ROLE_system-admin','ROLE_academic-admin','ROLE_admissions-officer')")
    public DocumentSummary retry(
            @PathVariable("documentId") UUID documentId,
            @Valid @RequestBody RetryDocument request) {
        return documentService.retry(documentId, request.expectedVersion());
    }
}
