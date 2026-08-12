package zw.ac.uz.emhare.coreidentity.rbac.domain.model;

import zw.ac.uz.emhare.coreidentity.rbac.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@Table(name = "lookup_sets", uniqueConstraints = @UniqueConstraint(name = "uk_lookup_sets_code", columnNames = "code"))
public class LookupSet extends AuditableEntity {

    @Column(nullable = false, length = 80)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    protected LookupSet() {
    }

    public LookupSet(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public void update(String newName, String newDescription) {
        name = newName;
        description = newDescription;
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
}
