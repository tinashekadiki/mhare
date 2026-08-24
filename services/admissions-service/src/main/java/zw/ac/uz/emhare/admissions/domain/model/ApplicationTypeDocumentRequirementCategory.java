package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Locale;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Applicant-category applicability for one route document rule. @author Tinashe K */
@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "application_type_document_requirement_categories")
public class ApplicationTypeDocumentRequirementCategory extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "document_requirement_id", nullable = false)
  private ApplicationTypeDocumentRequirement documentRequirement;

  @Column(name = "applicant_category_code", nullable = false, length = 30)
  private String applicantCategoryCode;

  protected ApplicationTypeDocumentRequirementCategory() {}

  public ApplicationTypeDocumentRequirementCategory(
      ApplicationTypeDocumentRequirement documentRequirement, String applicantCategoryCode) {
    this.documentRequirement = documentRequirement;
    this.applicantCategoryCode = normalize(applicantCategoryCode);
  }

  public ApplicationTypeDocumentRequirement getDocumentRequirement() {
    return documentRequirement;
  }

  public String getApplicantCategoryCode() {
    return applicantCategoryCode;
  }

  private String normalize(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Applicant category code is required.");
    }
    return ApplicantCategoryCode.from(value.trim().toUpperCase(Locale.ROOT)).name();
  }
}
