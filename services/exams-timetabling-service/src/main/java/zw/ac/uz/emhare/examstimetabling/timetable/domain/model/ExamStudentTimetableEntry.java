package zw.ac.uz.emhare.examstimetabling.timetable.domain.model;

import zw.ac.uz.emhare.examstimetabling.timetable.*;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.examstimetabling.roster.domain.model.ExamCandidateModule;
import zw.ac.uz.emhare.examstimetabling.roster.domain.model.ExamRegistrationImport;

/** @author Tinashe K */
@Audited @Entity @Table(name="exam_student_timetable_entries") @SQLRestriction("deleted_at IS NULL")
public class ExamStudentTimetableEntry extends AuditableEntity {
    public enum AttendanceStatus { EXPECTED,PRESENT,ABSENT,EXCUSED }
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="generation_run_id") private ExamTimetableGenerationRun generationRun;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="master_timetable_entry_id") private ExamMasterTimetableEntry masterTimetableEntry;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="venue_allocation_id") private ExamTimetableVenueAllocation venueAllocation;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="registration_import_id") private ExamRegistrationImport registrationImport;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="candidate_module_id") private ExamCandidateModule candidateModule;
    @Column(name="student_id",nullable=false) private UUID studentId; @Column(name="student_number",nullable=false,length=40) private String studentNumber;
    @Column(name="module_id",nullable=false) private UUID moduleId; @Column(name="module_code",nullable=false,length=50) private String moduleCode;
    @Column(name="scheduled_starts_at",nullable=false) private Instant scheduledStartsAt; @Column(name="scheduled_ends_at",nullable=false) private Instant scheduledEndsAt;
    @Column(name="seat_number",nullable=false) private int seatNumber;
    @Enumerated(EnumType.STRING) @Column(name="attendance_status",nullable=false,length=20) private AttendanceStatus attendanceStatus;
    protected ExamStudentTimetableEntry() {}
    public ExamStudentTimetableEntry(ExamTimetableGenerationRun run,ExamMasterTimetableEntry master,ExamTimetableVenueAllocation allocation,
            ExamCandidateModule candidate,int seatNumber){generationRun=run;masterTimetableEntry=master;venueAllocation=allocation;
        candidateModule=candidate;registrationImport=candidate.getRegistrationImport();studentId=registrationImport.getStudentId();studentNumber=registrationImport.getStudentNumber();
        moduleId=candidate.getModuleId();moduleCode=candidate.getModuleCode();scheduledStartsAt=master.getScheduledStartsAt();scheduledEndsAt=master.getScheduledEndsAt();
        this.seatNumber=seatNumber;attendanceStatus=AttendanceStatus.EXPECTED;}
    public ExamTimetableGenerationRun getGenerationRun(){return generationRun;} public ExamMasterTimetableEntry getMasterTimetableEntry(){return masterTimetableEntry;}
    public ExamTimetableVenueAllocation getVenueAllocation(){return venueAllocation;} public UUID getStudentId(){return studentId;} public String getStudentNumber(){return studentNumber;}
    public UUID getModuleId(){return moduleId;} public String getModuleCode(){return moduleCode;} public Instant getScheduledStartsAt(){return scheduledStartsAt;}
    public Instant getScheduledEndsAt(){return scheduledEndsAt;} public int getSeatNumber(){return seatNumber;} public AttendanceStatus getAttendanceStatus(){return attendanceStatus;}
}
