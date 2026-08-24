package zw.ac.uz.emhare.documentsreporting.upload.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import zw.ac.uz.emhare.documentsreporting.upload.domain.model.DocumentOcrExtraction;
import zw.ac.uz.emhare.documentsreporting.upload.ocr.DocumentOcrStatus;

/**
 * @author Tinashe K
 */
public interface DocumentOcrExtractionRepository
    extends JpaRepository<DocumentOcrExtraction, UUID> {
  Optional<DocumentOcrExtraction> findByUploadedDocumentIdAndDeletedAtIsNull(UUID documentId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<DocumentOcrExtraction>
      findFirstByStatusAndNextAttemptAtLessThanEqualAndDeletedAtIsNullOrderByQueuedAtAsc(
          DocumentOcrStatus status, Instant now);
}
