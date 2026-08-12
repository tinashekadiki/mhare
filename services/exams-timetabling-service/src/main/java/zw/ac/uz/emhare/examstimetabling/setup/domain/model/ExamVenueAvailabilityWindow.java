package zw.ac.uz.emhare.examstimetabling.setup.domain.model;

import zw.ac.uz.emhare.examstimetabling.setup.*;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name="exam_venue_availability_windows") @SQLRestriction("deleted_at IS NULL")
public class ExamVenueAvailabilityWindow extends AuditableEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="venue_id") private ExamVenue venue;
    @Column(name="available_from",nullable=false) private Instant availableFrom;
    @Column(name="available_until",nullable=false) private Instant availableUntil;
    @Column(length=500) private String notes;
    protected ExamVenueAvailabilityWindow() {}
    public ExamVenueAvailabilityWindow(ExamVenue venue,Instant availableFrom,Instant availableUntil,String notes){
        if(availableFrom==null||availableUntil==null||!availableUntil.isAfter(availableFrom))throw new IllegalArgumentException("Venue availability requires a valid time window.");
        this.venue=venue;this.availableFrom=availableFrom;this.availableUntil=availableUntil;this.notes=ExamVenueType.optional(notes);
    }
    public ExamVenue getVenue(){return venue;} public Instant getAvailableFrom(){return availableFrom;} public Instant getAvailableUntil(){return availableUntil;} public String getNotes(){return notes;}
}
