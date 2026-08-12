package zw.ac.uz.emhare.assessmentresults.assessment.domain.model;

import zw.ac.uz.emhare.assessmentresults.assessment.*;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.AssessmentEnums.ComponentType;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name="assessment_components") @SQLRestriction("deleted_at IS NULL")
public class AssessmentComponent extends AuditableEntity {
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="assessment_scheme_id") private AssessmentScheme assessmentScheme;
    @Column(nullable=false,length=30) private String code; @Column(nullable=false,length=150) private String name;
    @Enumerated(EnumType.STRING) @Column(name="component_type",nullable=false,length=30) private ComponentType componentType;
    @Column(name="weight_percent",nullable=false,precision=5,scale=2) private BigDecimal weightPercent;
    @Column(name="maximum_mark",nullable=false,precision=8,scale=2) private BigDecimal maximumMark;
    @Column(name="capture_opens_at",nullable=false) private Instant captureOpensAt;
    @Column(name="capture_closes_at",nullable=false) private Instant captureClosesAt;
    @Column(name="sort_order",nullable=false) private int sortOrder;
    protected AssessmentComponent() {}
    public AssessmentComponent(AssessmentScheme scheme,String code,String name,ComponentType type,BigDecimal weight,BigDecimal maximum,Instant opens,Instant closes,int order){
        if(weight==null||weight.signum()<=0||weight.compareTo(BigDecimal.valueOf(100))>0) throw new IllegalArgumentException("Component weight must be greater than zero and not exceed 100.");
        if(maximum==null||maximum.signum()<=0) throw new IllegalArgumentException("Component maximum mark must be greater than zero.");
        if(opens==null||closes==null||!closes.isAfter(opens)) throw new IllegalArgumentException("Capture close time must be after the open time.");
        if(order<1) throw new IllegalArgumentException("Component sort order must be positive.");
        assessmentScheme=scheme; this.code=AssessmentScheme.requireText(code,"Component code").toUpperCase(); this.name=AssessmentScheme.requireText(name,"Component name"); componentType=type; weightPercent=weight; maximumMark=maximum; captureOpensAt=opens; captureClosesAt=closes; sortOrder=order;
    }
    public boolean isCaptureOpen(Instant now){return !now.isBefore(captureOpensAt)&&!now.isAfter(captureClosesAt);}
    public AssessmentScheme getAssessmentScheme(){return assessmentScheme;} public String getCode(){return code;} public String getName(){return name;} public ComponentType getComponentType(){return componentType;} public BigDecimal getWeightPercent(){return weightPercent;} public BigDecimal getMaximumMark(){return maximumMark;} public Instant getCaptureOpensAt(){return captureOpensAt;} public Instant getCaptureClosesAt(){return captureClosesAt;} public int getSortOrder(){return sortOrder;}
}
