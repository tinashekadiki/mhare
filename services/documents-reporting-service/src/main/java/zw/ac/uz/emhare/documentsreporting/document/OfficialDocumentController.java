package zw.ac.uz.emhare.documentsreporting.document;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.documentsreporting.document.DocumentViews.DocumentDownload;
import zw.ac.uz.emhare.documentsreporting.document.DocumentViews.DocumentSummary;
import zw.ac.uz.emhare.documentsreporting.document.DocumentViews.RetryDocument;

/** @author Tinashe K */
@RestController
@RequestMapping("/api/documents")
@PreAuthorize("hasAnyAuthority('ROLE_system-admin','ROLE_academic-admin')")
public class OfficialDocumentController {

    private final OfficialDocumentService documentService;

    public OfficialDocumentController(OfficialDocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public List<DocumentSummary> documents() {
        return documentService.documents();
    }

    @GetMapping("/{documentId}/download")
    public DocumentDownload download(@PathVariable UUID documentId) {
        return documentService.download(documentId);
    }

    @PostMapping("/{documentId}/retry")
    public DocumentSummary retry(
            @PathVariable UUID documentId,
            @Valid @RequestBody RetryDocument command) {
        return documentService.retry(documentId, command.expectedVersion());
    }
}
