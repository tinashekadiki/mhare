package zw.ac.uz.emhare.coreidentity.rbac.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    name = "countries",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_countries_iso2_code", columnNames = "iso2_code"),
      @UniqueConstraint(name = "uk_countries_iso3_code", columnNames = "iso3_code")
    })
public class Country extends AuditableEntity {

  @Column(name = "iso2_code", nullable = false, length = 2)
  private String iso2Code;

  @Column(name = "iso3_code", nullable = false, length = 3)
  private String iso3Code;

  @Column(nullable = false, length = 150)
  private String name;

  @Column(name = "nationality_name", nullable = false, length = 150)
  private String nationalityName;

  protected Country() {}

  public Country(String iso2Code, String iso3Code, String name, String nationalityName) {
    this.iso2Code = iso2Code;
    this.iso3Code = iso3Code;
    this.name = name;
    this.nationalityName = nationalityName;
  }

  public void update(String newIso3Code, String newName, String newNationalityName) {
    iso3Code = newIso3Code;
    name = newName;
    nationalityName = newNationalityName;
  }

  public String getIso2Code() {
    return iso2Code;
  }

  public String getIso3Code() {
    return iso3Code;
  }

  public String getName() {
    return name;
  }

  public String getNationalityName() {
    return nationalityName;
  }
}
