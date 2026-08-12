package zw.ac.uz.emhare.finance.catalogue.domain.model;

import zw.ac.uz.emhare.finance.catalogue.*;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Governed definition of an institutional charge and its posting accounts. @author Tinashe K */
@Audited @Entity @Table(name="finance_fee_catalogues") @SQLRestriction("deleted_at IS NULL")
public class FinanceFeeCatalogue extends AuditableEntity {
    public enum ChargeType { APPLICATION,PROGRAMME,MODULE,ACCOMMODATION,DINING,GRADUATION,OTHER }
    public enum Status { DRAFT,ACTIVE,RETIRED }
    @Column(nullable=false,length=50) private String code; @Column(nullable=false,length=160) private String name;
    @Column(length=1000) private String description; @Enumerated(EnumType.STRING) @Column(name="charge_type",nullable=false,length=30) private ChargeType chargeType;
    @Column(name="receivable_account_code",nullable=false,length=50) private String receivableAccountCode;
    @Column(name="revenue_account_code",nullable=false,length=50) private String revenueAccountCode;
    @Column(name="tax_code",length=30) private String taxCode; @Column(name="base_currency_code",nullable=false,length=3) private String baseCurrencyCode;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
    @Column(name="prepared_by_user_id",nullable=false) private UUID preparedByUserId;
    @Column(name="activated_by_user_id") private UUID activatedByUserId; @Column(name="activated_at") private Instant activatedAt;
    @Column(name="activation_reason",length=1000) private String activationReason;
    @Column(name="retired_by_user_id") private UUID retiredByUserId; @Column(name="retired_at") private Instant retiredAt;
    @Column(name="retirement_reason",length=1000) private String retirementReason;
    protected FinanceFeeCatalogue() {}
    public FinanceFeeCatalogue(String code,String name,String description,ChargeType chargeType,String receivableAccountCode,
            String revenueAccountCode,String taxCode,UUID preparer) {
        this.code=required(code,"Fee code").toUpperCase(Locale.ROOT);this.name=required(name,"Fee name");this.description=optional(description);
        this.chargeType=chargeType;this.receivableAccountCode=required(receivableAccountCode,"Receivable account code").toUpperCase(Locale.ROOT);
        this.revenueAccountCode=required(revenueAccountCode,"Revenue account code").toUpperCase(Locale.ROOT);
        this.taxCode=optional(taxCode);baseCurrencyCode="USD";preparedByUserId=preparer;status=Status.DRAFT;
    }
    public void activate(UUID actor,Instant now,String reason,long expectedVersion){version(expectedVersion);if(status!=Status.DRAFT)throw new IllegalStateException("Only a draft fee catalogue can be activated.");distinct(actor,preparedByUserId,"Fee catalogue activation requires an independent finance operator.");activatedByUserId=actor;activatedAt=now;activationReason=required(reason,"Activation reason");status=Status.ACTIVE;}
    public void retire(UUID actor,Instant now,String reason,long expectedVersion){version(expectedVersion);if(status!=Status.ACTIVE)throw new IllegalStateException("Only an active fee catalogue can be retired.");retiredByUserId=actor;retiredAt=now;retirementReason=required(reason,"Retirement reason");status=Status.RETIRED;}
    private void version(long expected){if(getVersion()!=expected)throw new IllegalStateException("Fee catalogue was changed by another user. Refresh before retrying.");}
    static String required(String value,String label){if(value==null||value.isBlank())throw new IllegalArgumentException(label+" is required.");return value.trim();}
    static String optional(String value){return value==null||value.isBlank()?null:value.trim();}
    static void distinct(UUID actor,UUID prior,String message){if(actor==null||actor.equals(prior))throw new IllegalStateException(message);}
    public String getCode(){return code;} public String getName(){return name;} public String getDescription(){return description;}
    public ChargeType getChargeType(){return chargeType;} public String getReceivableAccountCode(){return receivableAccountCode;}
    public String getRevenueAccountCode(){return revenueAccountCode;} public String getTaxCode(){return taxCode;}
    public String getBaseCurrencyCode(){return baseCurrencyCode;} public Status getStatus(){return status;}
    public UUID getPreparedByUserId(){return preparedByUserId;} public UUID getActivatedByUserId(){return activatedByUserId;}
    public Instant getActivatedAt(){return activatedAt;} public UUID getRetiredByUserId(){return retiredByUserId;}
}
