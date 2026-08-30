package zw.ac.uz.emhare.documentsreporting.upload.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.ac.uz.emhare.documentsreporting.upload.domain.model.DocumentOcrExtraction;
import zw.ac.uz.emhare.documentsreporting.upload.ocr.DocumentOcrStatus;

/**
 * @author Tinashe K
 */
public interface DocumentOcrExtractionRepository
    extends JpaRepository<DocumentOcrExtraction, UUID> {
  Optional<DocumentOcrExtraction> findByUploadedDocumentIdAndDeletedAtIsNull(UUID documentId);

  @Query(value = """
      SELECT extraction.*
      FROM document_ocr_extractions extraction
      JOIN uploaded_documents document ON document.id = extraction.uploaded_document_id
      WHERE extraction.status = :#{#status.name()}
        AND extraction.next_attempt_at <= :now
        AND extraction.deleted_at IS NULL
        AND document.deleted_at IS NULL
      ORDER BY extraction.queued_at ASC
      LIMIT 1
      FOR UPDATE OF extraction SKIP LOCKED
      """, nativeQuery = true)
  Optional<DocumentOcrExtraction>
      findFirstByStatusAndNextAttemptAtLessThanEqualAndDeletedAtIsNullOrderByQueuedAtAsc(
          @Param("status") DocumentOcrStatus status, @Param("now") Instant now);
}
