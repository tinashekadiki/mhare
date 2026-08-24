package zw.ac.uz.emhare.documentsreporting.upload.api.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import zw.ac.uz.emhare.documentsreporting.upload.*;
import zw.ac.uz.emhare.documentsreporting.upload.api.model.*;
import zw.ac.uz.emhare.documentsreporting.upload.api.model.UploadedDocumentResponses.RejectUploadedDocument;
import zw.ac.uz.emhare.documentsreporting.upload.api.model.UploadedDocumentResponses.UploadedDocumentDownload;
import zw.ac.uz.emhare.documentsreporting.upload.api.model.UploadedDocumentResponses.UploadedDocumentSummary;
import zw.ac.uz.emhare.documentsreporting.upload.api.model.UploadedDocumentResponses.VerifyUploadedDocument;
import zw.ac.uz.emhare.documentsreporting.upload.ocr.DocumentOcrService;
import zw.ac.uz.emhare.documentsreporting.upload.ocr.DocumentOcrViews.DocumentOcrExtractionSummary;

/**
 * @author Tinashe K
 */
@Validated
@RestController
@RequestMapping("/api/documents/uploads")
@PreAuthorize("isAuthenticated()")
public class UploadedDocumentController {
  private final UploadedDocumentService service;
  private final DocumentOcrService documentOcrService;

  public UploadedDocumentController(
      UploadedDocumentService service, DocumentOcrService documentOcrService) {
    this.service = service;
    this.documentOcrService = documentOcrService;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public UploadedDocumentSummary upload(
      @RequestParam("ownerType") @NotBlank String ownerType,
      @RequestParam("ownerId") @NotNull UUID ownerId,
      @RequestParam("documentTypeCode") @NotBlank String documentTypeCode,
      @RequestParam(name = "replacesDocumentId", required = false) UUID replacesDocumentId,
      @RequestParam("file") MultipartFile file) {
    return service.upload(ownerType, ownerId, documentTypeCode, replacesDocumentId, file);
  }

  @GetMapping
  public List<UploadedDocumentSummary> documents(
      @RequestParam(name = "ownerType", required = false) String ownerType,
      @RequestParam(name = "ownerId", required = false) UUID ownerId) {
    return service.documents(ownerType, ownerId);
  }

  @GetMapping("/{documentId}/download")
  public UploadedDocumentDownload download(
      @PathVariable("documentId") UUID documentId,
      @RequestParam(name = "disposition", defaultValue = "attachment") String disposition) {
    return service.download(documentId, disposition);
  }

  @GetMapping("/{documentId}")
  public UploadedDocumentSummary document(@PathVariable("documentId") UUID documentId) {
    return service.document(documentId);
  }

  @GetMapping("/{documentId}/ocr-extraction")
  public DocumentOcrExtractionSummary extraction(@PathVariable("documentId") UUID documentId) {
    service.document(documentId);
    return documentOcrService.extraction(documentId);
  }

  @PostMapping("/{documentId}/ocr-extraction/retry")
  public DocumentOcrExtractionSummary retryExtraction(@PathVariable("documentId") UUID documentId) {
    service.document(documentId);
    return documentOcrService.retry(documentId);
  }

  @PostMapping("/{documentId}/verify")
  public UploadedDocumentSummary verify(
      @PathVariable("documentId") UUID documentId,
      @Valid @RequestBody VerifyUploadedDocument request) {
    return service.verify(documentId, request.expectedVersion(), request.comment());
  }

  @PostMapping("/{documentId}/reject")
  public UploadedDocumentSummary reject(
      @PathVariable("documentId") UUID documentId,
      @Valid @RequestBody RejectUploadedDocument request) {
    return service.reject(documentId, request.expectedVersion(), request.reason());
  }
}
