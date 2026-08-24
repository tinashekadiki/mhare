package zw.ac.uz.emhare.communications.content.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.PublicationStatus;

/** Publication window for one approved content version. @author Tinashe K */
@Audited
@Entity
@Table(name = "communication_publications")
@SQLRestriction("deleted_at IS NULL")
public class CommunicationPublication extends AuditableEntity {

  @Column(name = "item_id", nullable = false)
  private UUID itemId;

  @Column(name = "version_id", nullable = false)
  private UUID versionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PublicationStatus status;

  @Column(name = "publish_from", nullable = false)
  private Instant publishFrom;

  @Column(name = "publish_until")
  private Instant publishUntil;

  @Column(nullable = false)
  private boolean pinned;

  @Column(nullable = false)
  private boolean featured;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(name = "withdrawn_at")
  private Instant withdrawnAt;

  @Column(name = "withdrawn_by_user_id")
  private UUID withdrawnByUserId;

  @Column(name = "withdrawal_reason", length = 1000)
  private String withdrawalReason;

  protected CommunicationPublication() {}

  public CommunicationPublication(
      UUID itemId,
      UUID versionId,
      Instant publishFrom,
      Instant publishUntil,
      boolean pinned,
      boolean featured,
      int displayOrder,
      Instant now) {
    if (publishUntil != null && !publishUntil.isAfter(publishFrom)) {
      throw new IllegalArgumentException("Publication end must be after its start.");
    }
    this.itemId = itemId;
    this.versionId = versionId;
    this.publishFrom = publishFrom;
    this.publishUntil = publishUntil;
    this.pinned = pinned;
    this.featured = featured;
    this.displayOrder = displayOrder;
    this.status = effectiveStatus(now);
  }

  public PublicationStatus effectiveStatus(Instant now) {
    if (status == PublicationStatus.WITHDRAWN) {
      return status;
    }
    if (publishUntil != null && !publishUntil.isAfter(now)) {
      return PublicationStatus.EXPIRED;
    }
    if (publishFrom.isAfter(now)) {
      return PublicationStatus.SCHEDULED;
    }
    return PublicationStatus.LIVE;
  }

  public boolean isPublicAt(Instant now) {
    return effectiveStatus(now) == PublicationStatus.LIVE;
  }

  public void withdraw(UUID actorUserId, Instant now, String reason, long expectedVersion) {
    if (getVersion() != expectedVersion) {
      throw new IllegalArgumentException(
          "Publication changed since it was opened. Refresh and try again.");
    }
    if (status == PublicationStatus.WITHDRAWN) {
      return;
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("Withdrawal reason is required.");
    }
    status = PublicationStatus.WITHDRAWN;
    withdrawnAt = now;
    withdrawnByUserId = actorUserId;
    withdrawalReason = reason.trim();
  }

  public UUID getItemId() {
    return itemId;
  }

  public UUID getVersionId() {
    return versionId;
  }

  public PublicationStatus getStatus() {
    return status;
  }

  public Instant getPublishFrom() {
    return publishFrom;
  }

  public Instant getPublishUntil() {
    return publishUntil;
  }

  public boolean isPinned() {
    return pinned;
  }

  public boolean isFeatured() {
    return featured;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public Instant getWithdrawnAt() {
    return withdrawnAt;
  }

  public String getWithdrawalReason() {
    return withdrawalReason;
  }
}
