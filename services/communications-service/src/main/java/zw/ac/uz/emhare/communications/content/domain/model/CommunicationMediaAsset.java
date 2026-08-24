package zw.ac.uz.emhare.communications.content.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.MediaStatus;

/** Accessible S3-backed Communications media metadata. @author Tinashe K */
@Audited
@Entity
@Table(name = "communication_media_assets")
@SQLRestriction("deleted_at IS NULL")
public class CommunicationMediaAsset extends AuditableEntity {

  public static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");

  @Column(name = "storage_key", nullable = false, unique = true, length = 500)
  private String storageKey;

  @Column(name = "original_file_name", nullable = false, length = 255)
  private String originalFileName;

  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "checksum_sha256", nullable = false, length = 64)
  private String checksumSha256;

  @Column(name = "alternative_text", nullable = false, length = 500)
  private String alternativeText;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MediaStatus status;

  @Column(name = "uploaded_by_user_id", nullable = false)
  private UUID uploadedByUserId;

  protected CommunicationMediaAsset() {}

  public CommunicationMediaAsset(
      String storageKey,
      String originalFileName,
      String contentType,
      long sizeBytes,
      String checksumSha256,
      String alternativeText,
      UUID uploadedByUserId) {
    validate(contentType, sizeBytes, alternativeText);
    this.storageKey = storageKey;
    this.originalFileName = originalFileName;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.checksumSha256 = checksumSha256;
    this.alternativeText = alternativeText.trim();
    this.status = MediaStatus.READY;
    this.uploadedByUserId = uploadedByUserId;
  }

  public static void validate(String contentType, long sizeBytes, String alternativeText) {
    if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
      throw new IllegalArgumentException("Media must be a JPEG, PNG, WebP, or PDF file.");
    }
    if (sizeBytes <= 0) {
      throw new IllegalArgumentException("Media file must not be empty.");
    }
    if (alternativeText == null || alternativeText.isBlank()) {
      throw new IllegalArgumentException("Alternative text is required for public media.");
    }
  }

  public String getStorageKey() {
    return storageKey;
  }

  public String getOriginalFileName() {
    return originalFileName;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public String getChecksumSha256() {
    return checksumSha256;
  }

  public String getAlternativeText() {
    return alternativeText;
  }

  public MediaStatus getStatus() {
    return status;
  }
}
