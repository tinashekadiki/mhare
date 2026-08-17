package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Configures a governed section for one application route. @author Tinashe K */
@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(
    name = "application_type_sections",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_application_type_sections_code",
            columnNames = {"application_type_id", "section_code"}))
public class ApplicationTypeSection extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "application_type_id", nullable = false)
  private ApplicationType applicationType;

  @Column(name = "section_code", nullable = false, length = 60)
  private String sectionCode;

  @Column(name = "section_name", nullable = false, length = 150)
  private String sectionName;

  @Column(name = "is_required", nullable = false)
  private boolean required;

  @Column(name = "is_repeatable", nullable = false)
  private boolean repeatable;

  @Column(name = "minimum_records", nullable = false)
  private int minimumRecords;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  protected ApplicationTypeSection() {}

  public ApplicationTypeSection(
      ApplicationType applicationType,
      String sectionCode,
      String sectionName,
      boolean required,
      boolean repeatable,
      int minimumRecords,
      int sortOrder) {
    this.applicationType = applicationType;
    this.sectionCode = sectionCode;
    this.sectionName = sectionName;
    this.required = required;
    this.repeatable = repeatable;
    this.minimumRecords = minimumRecords;
    this.sortOrder = sortOrder;
    this.active = true;
  }

  public String getSectionCode() {
    return sectionCode;
  }

  public String getSectionName() {
    return sectionName;
  }

  public boolean isRequired() {
    return required;
  }

  public boolean isRepeatable() {
    return repeatable;
  }

  public int getMinimumRecords() {
    return minimumRecords;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public boolean isActive() {
    return active;
  }

  public void configure(
      String sectionName, boolean required, boolean repeatable, int minimumRecords, int sortOrder) {
    if (minimumRecords < 0 || sortOrder < 1) {
      throw new IllegalArgumentException("Section minimum records and sort order are invalid.");
    }
    this.sectionName =
        sectionName == null || sectionName.isBlank()
            ? throwRequired("Section name")
            : sectionName.trim();
    this.required = required;
    this.repeatable = repeatable;
    this.minimumRecords = minimumRecords;
    this.sortOrder = sortOrder;
    this.active = true;
  }

  public void deactivate() {
    active = false;
  }

  private static String throwRequired(String label) {
    throw new IllegalArgumentException(label + " is required.");
  }
}
