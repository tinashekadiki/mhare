package zw.ac.uz.emhare.communications.content.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.ContentKind;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.ItemLifecycleStatus;

/** Stable identity and route for a versioned public item. @author Tinashe K */
@Audited
@Entity
@Table(name = "communication_items")
@SQLRestriction("deleted_at IS NULL")
public class CommunicationItem extends AuditableEntity {

  @Column(name = "category_id")
  private UUID categoryId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ContentKind kind;

  @Column(nullable = false, length = 180)
  private String slug;

  @Enumerated(EnumType.STRING)
  @Column(name = "lifecycle_status", nullable = false, length = 20)
  private ItemLifecycleStatus lifecycleStatus;

  protected CommunicationItem() {}

  public CommunicationItem(ContentKind kind, String slug, UUID categoryId) {
    this.kind = kind;
    this.slug = normalizeSlug(slug);
    this.categoryId = categoryId;
    this.lifecycleStatus = ItemLifecycleStatus.ACTIVE;
  }

  public void archive() {
    lifecycleStatus = ItemLifecycleStatus.ARCHIVED;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public ContentKind getKind() {
    return kind;
  }

  public String getSlug() {
    return slug;
  }

  public ItemLifecycleStatus getLifecycleStatus() {
    return lifecycleStatus;
  }

  public static String normalizeSlug(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Slug is required.");
    }
    String normalized =
        value.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    if (normalized.isBlank()) {
      throw new IllegalArgumentException("Slug must contain letters or numbers.");
    }
    return normalized;
  }
}
