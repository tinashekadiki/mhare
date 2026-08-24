package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Immutable document rule captured for one application draft. @author Tinashe K */
@Audited
@Entity
@Table(name = "application_document_requirement_snapshots")
@SQLRestriction("deleted_at IS NULL")
public class ApplicationDocumentRequirementSnapshot extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "application_id", nullable = false)
  private Application application;

  @Column(name = "requirement_code", nullable = false, length = 60)
  private String requirementCode;

  @Column(name = "requirement_name", nullable = false, length = 160)
  private String requirementName;

  @Column(name = "is_required", nullable = false)
  private boolean required;

  @Column(name = "capture_section_code", nullable = false, length = 60)
  private String captureSectionCode;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "applicant_category_codes", nullable = false, columnDefinition = "varchar(30)[]")
  private String[] applicantCategoryCodes;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  protected ApplicationDocumentRequirementSnapshot() {}

  public ApplicationDocumentRequirementSnapshot(
      Application application,
      ApplicationTypeDocumentRequirement requirement,
      java.util.List<String> applicantCategoryCodes) {
    this.application = application;
    this.requirementCode = requirement.getRequirementCode();
    this.requirementName = requirement.getRequirementName();
    this.required = requirement.isRequired();
    this.captureSectionCode = requirement.getCaptureSectionCode();
    this.applicantCategoryCodes = applicantCategoryCodes.toArray(String[]::new);
    this.sortOrder = requirement.getSortOrder();
  }

  public String getRequirementCode() {
    return requirementCode;
  }

  public String getRequirementName() {
    return requirementName;
  }

  public boolean isRequired() {
    return required;
  }

  public String getCaptureSectionCode() {
    return captureSectionCode;
  }

  public java.util.List<String> getApplicantCategoryCodes() {
    return applicantCategoryCodes == null
        ? java.util.List.of()
        : java.util.List.of(applicantCategoryCodes);
  }

  public int getSortOrder() {
    return sortOrder;
  }
}
