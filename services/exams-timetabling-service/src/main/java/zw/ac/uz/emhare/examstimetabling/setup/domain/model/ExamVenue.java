package zw.ac.uz.emhare.examstimetabling.setup.domain.model;

import zw.ac.uz.emhare.examstimetabling.setup.*;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name="exam_venues") @SQLRestriction("deleted_at IS NULL")
public class ExamVenue extends AuditableEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="venue_type_id") private ExamVenueType venueType;
    @Column(nullable=false,length=40) private String code; @Column(nullable=false,length=150) private String name;
    @Column(name="campus_name",nullable=false,length=150) private String campusName;
    @Column(name="building_name",length=150) private String buildingName; @Column(name="room_name",length=100) private String roomName;
    @Column(name="examination_capacity",nullable=false) private int examinationCapacity;
    @Column(name="accessibility_notes",length=500) private String accessibilityNotes; @Column(nullable=false) private boolean active;
    protected ExamVenue() {}
    public ExamVenue(ExamVenueType venueType,String code,String name,String campusName,String buildingName,String roomName,
            int examinationCapacity,String accessibilityNotes){
        if(examinationCapacity<1)throw new IllegalArgumentException("Examination capacity must be positive.");
        this.venueType=venueType;this.code=ExamVenueType.text(code,"Venue code").toUpperCase();this.name=ExamVenueType.text(name,"Venue name");
        this.campusName=ExamVenueType.text(campusName,"Campus name");this.buildingName=ExamVenueType.optional(buildingName);
        this.roomName=ExamVenueType.optional(roomName);this.examinationCapacity=examinationCapacity;
        this.accessibilityNotes=ExamVenueType.optional(accessibilityNotes);active=true;
    }
    public ExamVenueType getVenueType(){return venueType;} public String getCode(){return code;} public String getName(){return name;}
    public String getCampusName(){return campusName;} public String getBuildingName(){return buildingName;} public String getRoomName(){return roomName;}
    public int getExaminationCapacity(){return examinationCapacity;} public String getAccessibilityNotes(){return accessibilityNotes;} public boolean isActive(){return active;}
}
