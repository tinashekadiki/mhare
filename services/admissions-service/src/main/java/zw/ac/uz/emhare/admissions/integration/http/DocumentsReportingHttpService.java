package zw.ac.uz.emhare.admissions.integration.http;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import zw.ac.uz.emhare.admissions.integration.DocumentsReportingClient.DocumentOcrExtractionSnapshot;
import zw.ac.uz.emhare.admissions.integration.DocumentsReportingClient.UploadedDocumentSnapshot;

/** Consumer-owned Admissions view of uploaded documents. @author Tinashe K */
@HttpExchange(accept = "application/json")
public interface DocumentsReportingHttpService {

  @GetExchange("/api/documents/uploads/{documentId}")
  UploadedDocumentSnapshot getUploadedDocument(@PathVariable("documentId") UUID documentId);

  @GetExchange("/api/documents/uploads")
  List<UploadedDocumentSnapshot> getUploadedDocuments(
      @RequestParam("ownerType") String ownerType, @RequestParam("ownerId") UUID ownerId);

  @GetExchange("/api/documents/uploads/{documentId}/ocr-extraction")
  DocumentOcrExtractionSnapshot getOcrExtraction(@PathVariable("documentId") UUID documentId);
}
