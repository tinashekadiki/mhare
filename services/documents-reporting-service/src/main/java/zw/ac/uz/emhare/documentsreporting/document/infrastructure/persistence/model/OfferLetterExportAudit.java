package zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Append-only evidence for non-mutating offer-letter exports. @author Tinashe K */
@Audited
@Entity
@Table(name = "offer_letter_export_audits")
@SQLRestriction("deleted_at IS NULL")
public class OfferLetterExportAudit extends AuditableEntity {
  @Column(name = "requested_by_user_id", nullable = false)
  private UUID requestedByUserId;

  @Column(name = "intake_id", nullable = false)
  private UUID intakeId;

  @Column(name = "programme_id", nullable = false)
  private UUID programmeId;

  @Column(name = "export_format", nullable = false, length = 30)
  private String exportFormat;

  @Column(name = "included_document_count", nullable = false)
  private int includedDocumentCount;

  @Column(name = "requested_at", nullable = false)
  private Instant requestedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "checksum_sha256", length = 64)
  private String checksumSha256;

  protected OfferLetterExportAudit() {}

  public OfferLetterExportAudit(
      UUID actor, UUID intakeId, UUID programmeId, String format, int count, Instant requestedAt) {
    this.requestedByUserId = actor;
    this.intakeId = intakeId;
    this.programmeId = programmeId;
    this.exportFormat = format;
    this.includedDocumentCount = count;
    this.requestedAt = requestedAt;
  }

  public void complete(String checksum, Instant now) {
    this.checksumSha256 = checksum;
    this.completedAt = now;
  }
}
