package zw.ac.uz.emhare.communications.content.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Idempotent authenticated publication read evidence. @author Tinashe K */
@Audited
@Entity
@Table(name = "communication_read_receipts")
@SQLRestriction("deleted_at IS NULL")
public class CommunicationReadReceipt extends AuditableEntity {

  @Column(name = "publication_id", nullable = false)
  private UUID publicationId;

  @Column(name = "reader_user_id", nullable = false)
  private UUID readerUserId;

  @Column(name = "read_at", nullable = false)
  private Instant readAt;

  protected CommunicationReadReceipt() {}

  public CommunicationReadReceipt(UUID publicationId, UUID readerUserId, Instant readAt) {
    this.publicationId = publicationId;
    this.readerUserId = readerUserId;
    this.readAt = readAt;
  }

  public UUID getPublicationId() {
    return publicationId;
  }

  public UUID getReaderUserId() {
    return readerUserId;
  }

  public Instant getReadAt() {
    return readAt;
  }
}
