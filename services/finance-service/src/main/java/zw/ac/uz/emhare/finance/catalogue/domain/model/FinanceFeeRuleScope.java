package zw.ac.uz.emhare.finance.catalogue.domain.model;

import zw.ac.uz.emhare.finance.catalogue.*;

import jakarta.persistence.*;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Relational applicability dimension for a fee rule. @author Tinashe K */
@Audited @Entity @Table(name="finance_fee_rule_scopes") @SQLRestriction("deleted_at IS NULL")
public class FinanceFeeRuleScope extends AuditableEntity {
    public enum Dimension { GLOBAL,INSTITUTION,ACADEMIC_UNIT,ACADEMIC_PERIOD,PROGRAMME_PERIOD,APPLICATION_TYPE,PROGRAMME_LEVEL,PROGRAMME_TYPE,APPLICANT_CATEGORY,PROGRAMME,MODULE,ACCOMMODATION_TYPE,DINING_PLAN,GRADUATION }
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="fee_rule_id") private FinanceFeeRule feeRule;
    @Enumerated(EnumType.STRING) @Column(name="scope_dimension",nullable=false,length=40) private Dimension scopeDimension;
    @Column(name="reference_id") private UUID referenceId; @Column(name="reference_code",length=80) private String referenceCode;
    @Column(name="reference_name",length=200) private String referenceName;
    protected FinanceFeeRuleScope() {}
    public FinanceFeeRuleScope(FinanceFeeRule rule,Dimension dimension,UUID referenceId,String referenceCode,String referenceName){feeRule=rule;scopeDimension=dimension;if(dimension==Dimension.GLOBAL){if(referenceId!=null||FinanceFeeCatalogue.optional(referenceCode)!=null||FinanceFeeCatalogue.optional(referenceName)!=null)throw new IllegalArgumentException("Global fee scope cannot have a reference.");}else{if(referenceId==null&&FinanceFeeCatalogue.optional(referenceCode)==null)throw new IllegalArgumentException("Fee scope requires a reference identifier or code.");this.referenceId=referenceId;this.referenceCode=FinanceFeeCatalogue.optional(referenceCode)==null?null:referenceCode.trim().toUpperCase(Locale.ROOT);this.referenceName=FinanceFeeCatalogue.required(referenceName,"Fee scope reference name");}}
    public FinanceFeeRule getFeeRule(){return feeRule;} public Dimension getScopeDimension(){return scopeDimension;} public UUID getReferenceId(){return referenceId;}
    public String getReferenceCode(){return referenceCode;} public String getReferenceName(){return referenceName;}
    public String canonicalPart(){return scopeDimension.name()+":"+(scopeDimension==Dimension.GLOBAL?"*":referenceId!=null?referenceId.toString():referenceCode);}
}
