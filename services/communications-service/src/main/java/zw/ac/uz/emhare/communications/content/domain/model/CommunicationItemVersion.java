package zw.ac.uz.emhare.communications.content.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.WorkflowStatus;

/** Immutable-after-approval structured content version. @author Tinashe K */
@Audited
@Entity
@Table(name = "communication_item_versions")
@SQLRestriction("deleted_at IS NULL")
public class CommunicationItemVersion extends AuditableEntity {

  @Column(name = "item_id", nullable = false)
  private UUID itemId;

  @Column(name = "version_number", nullable = false)
  private int versionNumber;

  @Column(nullable = false, length = 240)
  private String title;

  @Column(nullable = false, length = 600)
  private String summary;

  @Column(name = "schema_version", nullable = false)
  private int schemaVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "structured_content", nullable = false, columnDefinition = "jsonb")
  private String structuredContent;

  @Enumerated(EnumType.STRING)
  @Column(name = "workflow_status", nullable = false, length = 20)
  private WorkflowStatus workflowStatus;

  @Column(name = "authored_by_user_id", nullable = false)
  private UUID authoredByUserId;

  @Column(name = "submitted_by_user_id")
  private UUID submittedByUserId;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "decided_by_user_id")
  private UUID decidedByUserId;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "decision_reason", length = 1000)
  private String decisionReason;

  @Column(name = "hero_media_asset_id")
  private UUID heroMediaAssetId;

  @Column(name = "external_url", length = 1000)
  private String externalUrl;

  protected CommunicationItemVersion() {}

  public CommunicationItemVersion(
      UUID itemId,
      int versionNumber,
      String title,
      String summary,
      String structuredContent,
      UUID authoredByUserId,
      UUID heroMediaAssetId,
      String externalUrl) {
    this.itemId = itemId;
    this.versionNumber = versionNumber;
    this.title = required(title, "Title");
    this.summary = required(summary, "Summary");
    this.schemaVersion = 1;
    this.structuredContent = structuredContent;
    this.workflowStatus = WorkflowStatus.DRAFT;
    this.authoredByUserId = authoredByUserId;
    this.heroMediaAssetId = heroMediaAssetId;
    this.externalUrl = blankToNull(externalUrl);
  }

  public void edit(
      String title,
      String summary,
      String structuredContent,
      UUID heroMediaAssetId,
      String externalUrl,
      long expectedVersion) {
    requireVersion(expectedVersion);
    if (workflowStatus != WorkflowStatus.DRAFT && workflowStatus != WorkflowStatus.REJECTED) {
      throw new IllegalStateException("Only draft or rejected versions can be edited.");
    }
    this.title = required(title, "Title");
    this.summary = required(summary, "Summary");
    this.structuredContent = structuredContent;
    this.heroMediaAssetId = heroMediaAssetId;
    this.externalUrl = blankToNull(externalUrl);
    if (workflowStatus == WorkflowStatus.REJECTED) {
      workflowStatus = WorkflowStatus.DRAFT;
      decidedByUserId = null;
      decidedAt = null;
      decisionReason = null;
    }
  }

  public void submit(UUID actorUserId, Instant now, long expectedVersion) {
    requireVersion(expectedVersion);
    if (workflowStatus != WorkflowStatus.DRAFT) {
      throw new IllegalStateException("Only a draft can be submitted for review.");
    }
    workflowStatus = WorkflowStatus.IN_REVIEW;
    submittedByUserId = actorUserId;
    submittedAt = now;
  }

  public void approve(UUID actorUserId, Instant now, long expectedVersion) {
    requireVersion(expectedVersion);
    if (workflowStatus != WorkflowStatus.IN_REVIEW) {
      throw new IllegalStateException("Only a version in review can be approved.");
    }
    if (authoredByUserId.equals(actorUserId)) {
      throw new IllegalArgumentException("Authors cannot approve their own content version.");
    }
    workflowStatus = WorkflowStatus.APPROVED;
    decidedByUserId = actorUserId;
    decidedAt = now;
    decisionReason = null;
  }

  public void reject(UUID actorUserId, Instant now, String reason, long expectedVersion) {
    requireVersion(expectedVersion);
    if (workflowStatus != WorkflowStatus.IN_REVIEW) {
      throw new IllegalStateException("Only a version in review can be rejected.");
    }
    String validatedReason = required(reason, "Rejection reason");
    workflowStatus = WorkflowStatus.REJECTED;
    decidedByUserId = actorUserId;
    decidedAt = now;
    decisionReason = validatedReason;
  }

  public UUID getItemId() {
    return itemId;
  }

  public int getVersionNumber() {
    return versionNumber;
  }

  public String getTitle() {
    return title;
  }

  public String getSummary() {
    return summary;
  }

  public int getSchemaVersion() {
    return schemaVersion;
  }

  public String getStructuredContent() {
    return structuredContent;
  }

  public WorkflowStatus getWorkflowStatus() {
    return workflowStatus;
  }

  public UUID getAuthoredByUserId() {
    return authoredByUserId;
  }

  public UUID getSubmittedByUserId() {
    return submittedByUserId;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public UUID getDecidedByUserId() {
    return decidedByUserId;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public String getDecisionReason() {
    return decisionReason;
  }

  public UUID getHeroMediaAssetId() {
    return heroMediaAssetId;
  }

  public String getExternalUrl() {
    return externalUrl;
  }

  private void requireVersion(long expectedVersion) {
    if (getVersion() != expectedVersion) {
      throw new IllegalArgumentException(
          "Content changed since it was opened. Refresh and try again.");
    }
  }

  private static String required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required.");
    }
    return value.trim();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
