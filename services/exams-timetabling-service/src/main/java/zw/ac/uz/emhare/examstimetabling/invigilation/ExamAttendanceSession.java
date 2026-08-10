package zw.ac.uz.emhare.examstimetabling.invigilation;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.examstimetabling.timetable.ExamTimetableVenueAllocation;

/** Reconciled invigilation register for one published exam venue allocation. @author Tinashe K */
@Audited @Entity @Table(name="exam_attendance_sessions") @SQLRestriction("deleted_at IS NULL")
public class ExamAttendanceSession extends AuditableEntity {
    public enum Status { OPEN,CLOSED }
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="venue_allocation_id") private ExamTimetableVenueAllocation venueAllocation;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
    @Column(name="expected_candidate_count",nullable=false) private int expectedCandidateCount;
    @Column(name="present_candidate_count",nullable=false) private int presentCandidateCount;
    @Column(name="absent_candidate_count",nullable=false) private int absentCandidateCount;
    @Column(name="excused_candidate_count",nullable=false) private int excusedCandidateCount;
    @Column(name="opened_by_user_id",nullable=false) private UUID openedByUserId;
    @Column(name="opened_at",nullable=false) private Instant openedAt;
    @Column(name="opening_reason",nullable=false,length=1000) private String openingReason;
    @Column(name="closed_by_user_id") private UUID closedByUserId;
    @Column(name="closed_at") private Instant closedAt;
    @Column(name="closure_reason",length=1000) private String closureReason;

    protected ExamAttendanceSession() {}
    public ExamAttendanceSession(ExamTimetableVenueAllocation venueAllocation,int expectedCandidateCount,
            UUID openedByUserId,Instant openedAt,String openingReason) {
        if(expectedCandidateCount<1)throw new IllegalArgumentException("A published venue roster must contain at least one candidate.");
        this.venueAllocation=venueAllocation;this.expectedCandidateCount=expectedCandidateCount;
        this.openedByUserId=openedByUserId;this.openedAt=openedAt;this.openingReason=required(openingReason,"Opening reason");
        status=Status.OPEN;
    }
    public void close(UUID actor,Instant now,String reason,long expectedVersion,int presentCount,int absentCount,int excusedCount) {
        requireVersion(expectedVersion);if(status!=Status.OPEN)throw new IllegalStateException("Exam attendance session is already closed.");
        if(presentCount+absentCount+excusedCount!=expectedCandidateCount)throw new IllegalStateException("Every expected candidate must have an attendance outcome before closure.");
        presentCandidateCount=presentCount;absentCandidateCount=absentCount;excusedCandidateCount=excusedCount;
        closedByUserId=actor;closedAt=now;closureReason=required(reason,"Closure reason");status=Status.CLOSED;
    }
    private void requireVersion(long expectedVersion){if(getVersion()!=expectedVersion)throw new IllegalStateException("Attendance session was changed by another user. Refresh before retrying.");}
    static String required(String value,String label){if(value==null||value.isBlank())throw new IllegalArgumentException(label+" is required.");return value.trim();}
    public ExamTimetableVenueAllocation getVenueAllocation(){return venueAllocation;} public Status getStatus(){return status;}
    public int getExpectedCandidateCount(){return expectedCandidateCount;} public int getPresentCandidateCount(){return presentCandidateCount;}
    public int getAbsentCandidateCount(){return absentCandidateCount;} public int getExcusedCandidateCount(){return excusedCandidateCount;}
    public UUID getOpenedByUserId(){return openedByUserId;} public Instant getOpenedAt(){return openedAt;} public String getOpeningReason(){return openingReason;}
    public UUID getClosedByUserId(){return closedByUserId;} public Instant getClosedAt(){return closedAt;} public String getClosureReason(){return closureReason;}
}
