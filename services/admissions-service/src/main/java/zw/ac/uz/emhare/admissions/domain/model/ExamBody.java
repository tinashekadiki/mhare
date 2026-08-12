package zw.ac.uz.emhare.admissions.domain.model;

import zw.ac.uz.emhare.admissions.application.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@Table(name = "exam_bodies", uniqueConstraints = @UniqueConstraint(name = "uk_exam_bodies_code", columnNames = "code"))
public class ExamBody extends AuditableEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "country_id")
    private UUID countryId;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected ExamBody() {
    }

    public ExamBody(String code, String name, UUID countryId) {
        this.code = code;
        this.name = name;
        this.countryId = countryId;
        this.active = true;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public UUID getCountryId() { return countryId; }
    public boolean isActive() { return active; }
}
