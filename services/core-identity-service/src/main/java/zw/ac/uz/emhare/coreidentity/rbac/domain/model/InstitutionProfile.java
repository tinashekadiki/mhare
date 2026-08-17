package zw.ac.uz.emhare.coreidentity.rbac.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.coreidentity.rbac.*;

@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(
    name = "institution_profile",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_institution_profile_code", columnNames = "code"))
public class InstitutionProfile extends AuditableEntity {

  @Column(nullable = false, length = 50)
  private String code;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(name = "legal_name", nullable = false, length = 250)
  private String legalName;

  @Column(name = "registrar_name", nullable = false, length = 200)
  private String registrarName;

  @Column(name = "default_currency_code", nullable = false, length = 3)
  private String defaultCurrencyCode;

  @Column(name = "country_code", nullable = false, length = 2)
  private String countryCode;

  @Column(nullable = false, length = 80)
  private String timezone;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "contact_details_json", columnDefinition = "jsonb")
  private String contactDetailsJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "branding_json", columnDefinition = "jsonb")
  private String brandingJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "bank_details_json", columnDefinition = "jsonb")
  private String bankDetailsJson;

  @Column(name = "legacy_code", length = 50)
  private String legacyCode;

  protected InstitutionProfile() {}

  public InstitutionProfile(
      String code, String name, String legalName, String countryCode, String timezone) {
    this.code = code;
    this.name = name;
    this.legalName = legalName;
    this.defaultCurrencyCode = "USD";
    this.countryCode = countryCode;
    this.timezone = timezone;
  }

  public void update(
      String newCode,
      String newName,
      String newLegalName,
      String newRegistrarName,
      String newDefaultCurrencyCode,
      String newCountryCode,
      String newTimezone,
      String newContactDetailsJson,
      String newBrandingJson,
      String newBankDetailsJson,
      String newLegacyCode) {
    code = newCode;
    name = newName;
    legalName = newLegalName;
    registrarName = newRegistrarName;
    defaultCurrencyCode = newDefaultCurrencyCode;
    countryCode = newCountryCode;
    timezone = newTimezone;
    contactDetailsJson = newContactDetailsJson;
    brandingJson = newBrandingJson;
    bankDetailsJson = newBankDetailsJson;
    legacyCode = newLegacyCode;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public String getLegalName() {
    return legalName;
  }

  public String getRegistrarName() {
    return registrarName;
  }

  public String getDefaultCurrencyCode() {
    return defaultCurrencyCode;
  }

  public String getCountryCode() {
    return countryCode;
  }

  public String getTimezone() {
    return timezone;
  }

  public String getContactDetailsJson() {
    return contactDetailsJson;
  }

  public String getBrandingJson() {
    return brandingJson;
  }

  public String getBankDetailsJson() {
    return bankDetailsJson;
  }

  public String getLegacyCode() {
    return legacyCode;
  }
}
