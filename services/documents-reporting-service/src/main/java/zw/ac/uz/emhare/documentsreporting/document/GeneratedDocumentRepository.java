package zw.ac.uz.emhare.documentsreporting.document;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** @author Tinashe K */
public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, UUID> {
    boolean existsByDocumentTypeAndSourceProgressionDecisionIdAndSourceProgressionDecisionVersionAndDeletedAtIsNull(
            GeneratedDocument.DocumentType documentType, UUID progressionDecisionId, int decisionVersion);

    Optional<GeneratedDocument> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByOfferLetterIdAndDeletedAtIsNull(UUID offerLetterId);

    List<GeneratedDocument> findAllByDeletedAtIsNullOrderByRequestedAtDesc();

    @Query(value = """
            SELECT * FROM generated_documents
            WHERE status IN ('REQUESTED', 'FAILED')
              AND next_generation_attempt_at <= :now
              AND generation_attempt_count < 10
              AND deleted_at IS NULL
            ORDER BY next_generation_attempt_at, requested_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT 5
            """, nativeQuery = true)
    List<GeneratedDocument> lockNextGenerationBatch(@Param("now") Instant now);
}
