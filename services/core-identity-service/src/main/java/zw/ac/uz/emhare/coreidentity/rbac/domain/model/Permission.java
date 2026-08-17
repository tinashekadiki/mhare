package zw.ac.uz.emhare.coreidentity.rbac.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.coreidentity.rbac.*;

@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(
    name = "permissions",
    uniqueConstraints = @UniqueConstraint(name = "uk_permissions_code", columnNames = "code"))
public class Permission extends AuditableEntity {

  @Column(nullable = false, length = 120)
  private String code;

  @Column(nullable = false, length = 180)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private PermissionCategory category;

  @Column(length = 500)
  private String description;

  protected Permission() {}

  public Permission(String code, String name, PermissionCategory category, String description) {
    this.code = code;
    this.name = name;
    this.category = category;
    this.description = description;
  }

  public void update(String newName, PermissionCategory newCategory, String newDescription) {
    name = newName;
    category = newCategory;
    description = newDescription;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public PermissionCategory getCategory() {
    return category;
  }

  public String getDescription() {
    return description;
  }
}
