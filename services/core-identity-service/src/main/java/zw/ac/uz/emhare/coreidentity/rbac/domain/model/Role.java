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
    name = "roles",
    uniqueConstraints = @UniqueConstraint(name = "uk_roles_code", columnNames = "code"))
public class Role extends AuditableEntity {

  @Column(nullable = false, length = 80)
  private String code;

  @Column(nullable = false, length = 150)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private RoleScope scope;

  @Column(name = "is_system_managed", nullable = false)
  private boolean systemManaged;

  protected Role() {}

  public Role(String code, String name, RoleScope scope, boolean systemManaged) {
    this.code = code;
    this.name = name;
    this.scope = scope;
    this.systemManaged = systemManaged;
  }

  public void update(String newName, RoleScope newScope, boolean newSystemManaged) {
    name = newName;
    scope = newScope;
    systemManaged = newSystemManaged;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public RoleScope getScope() {
    return scope;
  }

  public boolean isSystemManaged() {
    return systemManaged;
  }
}
