package zw.ac.uz.emhare.finance.billing;

import jakarta.persistence.*;
import java.util.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.finance.catalogue.FinanceFeeRuleScope;
import zw.ac.uz.emhare.finance.catalogue.FinanceFeePricingResolver.PricingScope;

/** Immutable scope evidence used to resolve an approved price. @author Tinashe K */
@Audited @Entity @Table(name="finance_billing_event_scopes") @SQLRestriction("deleted_at IS NULL")
public class FinanceBillingEventScope extends AuditableEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="billing_event_id") private FinanceBillingEvent billingEvent;
    @Enumerated(EnumType.STRING) @Column(name="scope_dimension",nullable=false,length=40) private FinanceFeeRuleScope.Dimension scopeDimension;
    @Column(name="reference_id") private UUID referenceId; @Column(name="reference_code",length=80) private String referenceCode; @Column(name="reference_name",length=200) private String referenceName;
    protected FinanceBillingEventScope() {}
    public FinanceBillingEventScope(FinanceBillingEvent event,PricingScope scope){billingEvent=Objects.requireNonNull(event);scopeDimension=Objects.requireNonNull(scope.scopeDimension());if(scopeDimension==FinanceFeeRuleScope.Dimension.GLOBAL){if(scope.referenceId()!=null||optional(scope.referenceCode())!=null||optional(scope.referenceName())!=null)throw new IllegalArgumentException("Global billing scope cannot have a reference.");}else{if(scope.referenceId()==null&&optional(scope.referenceCode())==null)throw new IllegalArgumentException("Billing scope requires a reference identifier or code.");referenceId=scope.referenceId();referenceCode=optional(scope.referenceCode())==null?null:scope.referenceCode().trim().toUpperCase(Locale.ROOT);referenceName=required(scope.referenceName());}}
    private static String optional(String value){return value==null||value.isBlank()?null:value.trim();} private static String required(String value){if(optional(value)==null)throw new IllegalArgumentException("Billing scope reference name is required.");return value.trim();}
    public FinanceFeeRuleScope.Dimension getScopeDimension(){return scopeDimension;} public UUID getReferenceId(){return referenceId;} public String getReferenceCode(){return referenceCode;} public String getReferenceName(){return referenceName;}
}
