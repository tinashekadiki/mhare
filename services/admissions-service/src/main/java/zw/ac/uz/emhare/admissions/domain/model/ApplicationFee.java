package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "application_fees")
public class ApplicationFee extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "application_type_id", nullable = false)
  private ApplicationType applicationType;

  @Column(name = "applicant_category_code", nullable = false, length = 30)
  private String applicantCategoryCode;

  @Column(name = "currency_code", nullable = false, length = 3)
  private String currencyCode;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  protected ApplicationFee() {}

  public ApplicationFee(
      ApplicationType applicationType,
      String applicantCategoryCode,
      String currencyCode,
      BigDecimal amount,
      LocalDate effectiveFrom) {
    this.applicationType = applicationType;
    this.applicantCategoryCode = applicantCategoryCode;
    this.currencyCode = currencyCode;
    this.amount = amount;
    this.effectiveFrom = effectiveFrom;
    this.active = true;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrencyCode() {
    return currencyCode;
  }
}
