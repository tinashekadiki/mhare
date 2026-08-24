package zw.ac.uz.emhare.communications.content.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Governed public-content category. @author Tinashe K */
@Audited
@Entity
@Table(name = "communication_categories")
@SQLRestriction("deleted_at IS NULL")
public class CommunicationCategory extends AuditableEntity {

  @Column(nullable = false, unique = true, length = 80)
  private String code;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(length = 500)
  private String description;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(nullable = false)
  private boolean active;

  protected CommunicationCategory() {}

  public CommunicationCategory(
      String code, String name, String description, int displayOrder, boolean active) {
    this.code = required(code, "Category code").toUpperCase();
    this.name = required(name, "Category name");
    this.description = description;
    this.displayOrder = displayOrder;
    this.active = active;
  }

  public void update(
      String name, String description, int displayOrder, boolean active, long expectedVersion) {
    requireVersion(expectedVersion);
    this.name = required(name, "Category name");
    this.description = description;
    this.displayOrder = displayOrder;
    this.active = active;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public boolean isActive() {
    return active;
  }

  private void requireVersion(long expectedVersion) {
    if (getVersion() != expectedVersion) {
      throw new IllegalArgumentException(
          "Category changed since it was opened. Refresh and try again.");
    }
  }

  private static String required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required.");
    }
    return value.trim();
  }
}
