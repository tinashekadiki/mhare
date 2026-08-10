package zw.ac.uz.emhare.examstimetabling.invigilation;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.examstimetabling.timetable.ExamStudentTimetableEntry;

/** Per-seat attendance evidence separated from immutable published timetable data. @author Tinashe K */
@Audited @Entity @Table(name="exam_attendance_records") @SQLRestriction("deleted_at IS NULL")
public class ExamAttendanceRecord extends AuditableEntity {
    public enum Status { EXPECTED,PRESENT,ABSENT,EXCUSED }
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="attendance_session_id") private ExamAttendanceSession attendanceSession;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="student_timetable_entry_id") private ExamStudentTimetableEntry studentTimetableEntry;
    @Enumerated(EnumType.STRING) @Column(name="attendance_status",nullable=false,length=20) private Status attendanceStatus;
    @Column(name="recorded_by_user_id") private UUID recordedByUserId;
    @Column(name="recorded_at") private Instant recordedAt;
    @Column(name="evidence_notes",length=1000) private String evidenceNotes;

    protected ExamAttendanceRecord() {}
    public ExamAttendanceRecord(ExamAttendanceSession attendanceSession,ExamStudentTimetableEntry studentTimetableEntry) {
        this.attendanceSession=attendanceSession;this.studentTimetableEntry=studentTimetableEntry;attendanceStatus=Status.EXPECTED;
    }
    public void record(Status newStatus,String notes,UUID actor,Instant now,long expectedVersion) {
        if(attendanceSession.getStatus()!=ExamAttendanceSession.Status.OPEN)throw new IllegalStateException("Attendance cannot change after session closure.");
        if(getVersion()!=expectedVersion)throw new IllegalStateException("Attendance record was changed by another user. Refresh before retrying.");
        if(newStatus==null||newStatus==Status.EXPECTED)throw new IllegalArgumentException("Record PRESENT, ABSENT, or EXCUSED as the attendance outcome.");
        if((newStatus==Status.ABSENT||newStatus==Status.EXCUSED)&&(notes==null||notes.isBlank()))throw new IllegalArgumentException("Evidence notes are required for absent or excused candidates.");
        attendanceStatus=newStatus;recordedByUserId=actor;recordedAt=now;evidenceNotes=notes==null||notes.isBlank()?null:notes.trim();
    }
    public ExamAttendanceSession getAttendanceSession(){return attendanceSession;} public ExamStudentTimetableEntry getStudentTimetableEntry(){return studentTimetableEntry;}
    public Status getAttendanceStatus(){return attendanceStatus;} public UUID getRecordedByUserId(){return recordedByUserId;}
    public Instant getRecordedAt(){return recordedAt;} public String getEvidenceNotes(){return evidenceNotes;}
}
