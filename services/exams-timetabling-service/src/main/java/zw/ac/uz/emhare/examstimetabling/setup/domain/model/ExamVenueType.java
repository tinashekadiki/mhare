package zw.ac.uz.emhare.examstimetabling.setup.domain.model;

import zw.ac.uz.emhare.examstimetabling.setup.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name="exam_venue_types") @SQLRestriction("deleted_at IS NULL")
public class ExamVenueType extends AuditableEntity {
    @Column(nullable=false,length=30) private String code;
    @Column(nullable=false,length=120) private String name;
    @Column(length=500) private String description;
    @Column(nullable=false) private boolean active;
    protected ExamVenueType() {}
    public ExamVenueType(String code,String name,String description){this.code=text(code,"Venue type code").toUpperCase();this.name=text(name,"Venue type name");this.description=optional(description);active=true;}
    public String getCode(){return code;} public String getName(){return name;} public String getDescription(){return description;} public boolean isActive(){return active;}
    static String text(String value,String label){if(value==null||value.isBlank())throw new IllegalArgumentException(label+" is required.");return value.trim();}
    static String optional(String value){return value==null||value.isBlank()?null:value.trim();}
}
