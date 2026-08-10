package zw.ac.uz.emhare.examstimetabling.timetable;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.examstimetabling.setup.ExamVenue;

/** @author Tinashe K */
@Audited @Entity @Table(name="exam_timetable_venue_allocations") @SQLRestriction("deleted_at IS NULL")
public class ExamTimetableVenueAllocation extends AuditableEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="master_timetable_entry_id") private ExamMasterTimetableEntry masterTimetableEntry;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="venue_id") private ExamVenue venue;
    @Column(name="allocated_capacity",nullable=false) private int allocatedCapacity;
    protected ExamTimetableVenueAllocation() {}
    public ExamTimetableVenueAllocation(ExamMasterTimetableEntry entry,ExamVenue venue,int allocatedCapacity){this.masterTimetableEntry=entry;this.venue=venue;this.allocatedCapacity=allocatedCapacity;}
    public ExamMasterTimetableEntry getMasterTimetableEntry(){return masterTimetableEntry;} public ExamVenue getVenue(){return venue;} public int getAllocatedCapacity(){return allocatedCapacity;}
}
