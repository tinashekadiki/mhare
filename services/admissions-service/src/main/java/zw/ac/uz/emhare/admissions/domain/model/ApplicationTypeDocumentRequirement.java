package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Locale;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/**
 * @author Tinashe K
 */
@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(
    name = "application_type_document_requirements",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_application_type_document_requirement",
            columnNames = {"application_type_id", "requirement_code"}))
public class ApplicationTypeDocumentRequirement extends AuditableEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "application_type_id", nullable = false)
  private ApplicationType applicationType;

  @Column(name = "requirement_code", nullable = false, length = 80)
  private String requirementCode;

  @Column(name = "requirement_name", nullable = false, length = 150)
  private String requirementName;

  @Column(name = "is_required", nullable = false)
  private boolean required;

  @Column(name = "capture_section_code", nullable = false, length = 60)
  private String captureSectionCode;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  protected ApplicationTypeDocumentRequirement() {}

  public ApplicationTypeDocumentRequirement(
      ApplicationType applicationType,
      String requirementCode,
      String requirementName,
      boolean required,
      String captureSectionCode,
      int sortOrder) {
    if (sortOrder <= 0)
      throw new IllegalArgumentException("Document requirement sort order must be positive.");
    this.applicationType = applicationType;
    this.requirementCode =
        requireText(requirementCode, "Requirement code").toUpperCase(Locale.ROOT);
    this.requirementName = requireText(requirementName, "Requirement name");
    this.required = required;
    this.captureSectionCode = normalizeSectionCode(captureSectionCode);
    this.sortOrder = sortOrder;
    this.active = true;
  }

  public ApplicationTypeDocumentRequirement(
      ApplicationType applicationType,
      String requirementCode,
      String requirementName,
      boolean required,
      int sortOrder) {
    this(
        applicationType,
        requirementCode,
        requirementName,
        required,
        "SUPPORTING_DOCUMENTS",
        sortOrder);
  }

  private String requireText(String value, String field) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(field + " is required.");
    return value.trim();
  }

  public ApplicationType getApplicationType() {
    return applicationType;
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

  public int getSortOrder() {
    return sortOrder;
  }

  public boolean isActive() {
    return active;
  }

  public void configure(
      String requirementName, boolean required, String captureSectionCode, int sortOrder) {
    if (sortOrder < 1)
      throw new IllegalArgumentException("Document requirement sort order must be positive.");
    this.requirementName = requireText(requirementName, "Requirement name");
    this.required = required;
    this.captureSectionCode = normalizeSectionCode(captureSectionCode);
    this.sortOrder = sortOrder;
    this.active = true;
  }

  public void configure(String requirementName, boolean required, int sortOrder) {
    configure(requirementName, required, captureSectionCode, sortOrder);
  }

  public void deactivate() {
    active = false;
  }

  private String normalizeSectionCode(String value) {
    return requireText(value, "Capture section code")
        .toUpperCase(Locale.ROOT)
        .replaceAll("[^A-Z0-9_]", "_");
  }
}
