package zw.ac.uz.emhare.coreidentity.rbac.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
    name = "lookup_values",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_lookup_values_set_code",
            columnNames = {"lookup_set_id", "code"}))
public class LookupValue extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "lookup_set_id", nullable = false)
  private LookupSet lookupSet;

  @Column(nullable = false, length = 80)
  private String code;

  @Column(nullable = false, length = 150)
  private String name;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  protected LookupValue() {}

  public LookupValue(LookupSet lookupSet, String code, String name, int sortOrder, boolean active) {
    this.lookupSet = lookupSet;
    this.code = code;
    this.name = name;
    this.sortOrder = sortOrder;
    this.active = active;
  }

  public void update(String newName, int newSortOrder, boolean newActive) {
    name = newName;
    sortOrder = newSortOrder;
    active = newActive;
  }

  public LookupSet getLookupSet() {
    return lookupSet;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public boolean isActive() {
    return active;
  }
}
