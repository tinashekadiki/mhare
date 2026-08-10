package zw.ac.uz.emhare.documentsreporting.upload;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface UploadedDocumentRepository extends JpaRepository<UploadedDocument, UUID> {
    Optional<UploadedDocument> findByIdAndDeletedAtIsNull(UUID id);
    List<UploadedDocument> findAllByDeletedAtIsNullOrderByUploadedAtDesc();
    List<UploadedDocument> findAllByUploadedByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(UUID uploadedByUserId);
    List<UploadedDocument> findAllByOwnerTypeAndOwnerIdAndDeletedAtIsNullOrderByUploadedAtDesc(
            UploadedDocument.OwnerType ownerType, UUID ownerId);
    List<UploadedDocument> findAllByOwnerTypeAndOwnerIdAndUploadedByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(
            UploadedDocument.OwnerType ownerType, UUID ownerId, UUID uploadedByUserId);
}
