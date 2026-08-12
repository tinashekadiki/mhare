package zw.ac.uz.emhare.finance.billing.domain.model;

import zw.ac.uz.emhare.finance.billing.*;

import jakarta.persistence.*;
import java.math.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeCatalogue;

/** Finance-owned mapping from an authoritative source event to a governed charge. @author Tinashe K */
@Audited @Entity @Table(name="finance_billing_policies") @SQLRestriction("deleted_at IS NULL")
public class FinanceBillingPolicy extends AuditableEntity {
    public enum LineBasis { REGISTRATION,REGISTERED_MODULE } public enum QuantityBasis { FIXED,MODULE_CREDIT_VALUE } public enum Status { DRAFT,ACTIVE,RETIRED }
    @Column(nullable=false,length=50) private String code; @Column(name="policy_version",nullable=false) private int policyVersion; @Column(nullable=false,length=160) private String name;
    @Column(name="source_event_type",nullable=false,length=160) private String sourceEventType; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="fee_catalogue_id") private FinanceFeeCatalogue feeCatalogue;
    @Enumerated(EnumType.STRING) @Column(name="line_basis",nullable=false,length=40) private LineBasis lineBasis; @Enumerated(EnumType.STRING) @Column(name="quantity_basis",nullable=false,length=40) private QuantityBasis quantityBasis;
    @Column(name="fixed_quantity",precision=12,scale=4) private BigDecimal fixedQuantity; @Column(name="effective_from",nullable=false) private Instant effectiveFrom; @Column(name="effective_until") private Instant effectiveUntil;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status; @Column(name="prepared_by_user_id",nullable=false) private UUID preparedByUserId;
    @Column(name="activated_by_user_id") private UUID activatedByUserId; @Column(name="activated_at") private Instant activatedAt; @Column(name="activation_reason",length=1000) private String activationReason;
    @Column(name="retired_by_user_id") private UUID retiredByUserId; @Column(name="retired_at") private Instant retiredAt; @Column(name="retirement_reason",length=1000) private String retirementReason;
    protected FinanceBillingPolicy() {}
    public FinanceBillingPolicy(String code,int version,String name,String sourceEventType,FinanceFeeCatalogue catalogue,LineBasis lineBasis,
            QuantityBasis quantityBasis,BigDecimal fixedQuantity,Instant effectiveFrom,Instant effectiveUntil,UUID preparer){this.code=required(code,"Billing policy code").toUpperCase(Locale.ROOT);if(version<1)throw new IllegalArgumentException("Billing policy version must be positive.");policyVersion=version;this.name=required(name,"Billing policy name");this.sourceEventType=required(sourceEventType,"Source event type");feeCatalogue=Objects.requireNonNull(catalogue);this.lineBasis=Objects.requireNonNull(lineBasis);this.quantityBasis=Objects.requireNonNull(quantityBasis);if(quantityBasis==QuantityBasis.FIXED){if(fixedQuantity==null||fixedQuantity.signum()<=0)throw new IllegalArgumentException("Fixed billing quantity must be greater than zero.");this.fixedQuantity=fixedQuantity.setScale(4,RoundingMode.UNNECESSARY);}else if(lineBasis!=LineBasis.REGISTERED_MODULE||fixedQuantity!=null)throw new IllegalArgumentException("Module-credit quantity is available only for registered-Module lines.");this.effectiveFrom=Objects.requireNonNull(effectiveFrom);if(effectiveUntil!=null&&!effectiveUntil.isAfter(effectiveFrom))throw new IllegalArgumentException("Billing-policy effective dates are invalid.");this.effectiveUntil=effectiveUntil;preparedByUserId=Objects.requireNonNull(preparer);status=Status.DRAFT;}
    public void activate(UUID actor,Instant time,String reason,long expectedVersion){version(expectedVersion);if(status!=Status.DRAFT)throw new IllegalStateException("Only a draft billing policy can be activated.");if(actor==null||actor.equals(preparedByUserId))throw new IllegalStateException("Billing-policy activation requires an independent Finance operator.");activatedByUserId=actor;activatedAt=time;activationReason=required(reason,"Activation reason");status=Status.ACTIVE;}
    public void retire(UUID actor,Instant time,String reason,long expectedVersion){version(expectedVersion);if(status!=Status.ACTIVE)throw new IllegalStateException("Only an active billing policy can be retired.");retiredByUserId=actor;retiredAt=time;retirementReason=required(reason,"Retirement reason");status=Status.RETIRED;}
    public BigDecimal quantityForModule(BigDecimal creditValue){return quantityBasis==QuantityBasis.FIXED?fixedQuantity:Objects.requireNonNull(creditValue,"Module credit value is required for this billing policy.").setScale(4,RoundingMode.UNNECESSARY);}
    private void version(long expected){if(getVersion()!=expected)throw new IllegalStateException("Billing policy was changed by another user. Refresh before retrying.");} private static String required(String value,String label){if(value==null||value.isBlank())throw new IllegalArgumentException(label+" is required.");return value.trim();}
    public String getCode(){return code;} public int getPolicyVersion(){return policyVersion;} public String getName(){return name;} public String getSourceEventType(){return sourceEventType;} public FinanceFeeCatalogue getFeeCatalogue(){return feeCatalogue;}
    public LineBasis getLineBasis(){return lineBasis;} public QuantityBasis getQuantityBasis(){return quantityBasis;} public BigDecimal getFixedQuantity(){return fixedQuantity;} public Instant getEffectiveFrom(){return effectiveFrom;} public Instant getEffectiveUntil(){return effectiveUntil;}
    public Status getStatus(){return status;} public UUID getPreparedByUserId(){return preparedByUserId;} public UUID getActivatedByUserId(){return activatedByUserId;} public Instant getActivatedAt(){return activatedAt;}
}
