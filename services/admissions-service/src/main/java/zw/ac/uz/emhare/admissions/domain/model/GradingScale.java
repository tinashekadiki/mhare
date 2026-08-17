package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(
    name = "grading_scales",
    uniqueConstraints = @UniqueConstraint(name = "uk_grading_scales_code", columnNames = "code"))
public class GradingScale extends AuditableEntity {

  @Column(nullable = false, length = 50)
  private String code;

  @Column(nullable = false, length = 150)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private QualificationLevel level;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  protected GradingScale() {}

  public GradingScale(
      String code,
      String name,
      QualificationLevel level,
      LocalDate effectiveFrom,
      LocalDate effectiveTo) {
    this.code = code;
    this.name = name;
    this.level = level;
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
  }

  public String getCode() {
    return code;
  }

  public QualificationLevel getLevel() {
    return level;
  }

  public LocalDate getEffectiveFrom() {
    return effectiveFrom;
  }

  public LocalDate getEffectiveTo() {
    return effectiveTo;
  }

  public boolean coversDate(LocalDate date) {
    return !date.isBefore(effectiveFrom) && (effectiveTo == null || !date.isAfter(effectiveTo));
  }
}
