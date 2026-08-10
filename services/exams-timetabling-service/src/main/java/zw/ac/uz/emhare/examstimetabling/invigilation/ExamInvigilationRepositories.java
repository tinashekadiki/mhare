package zw.ac.uz.emhare.examstimetabling.invigilation;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** @author Tinashe K */
interface ExamAttendanceSessionRepository extends JpaRepository<ExamAttendanceSession,UUID> {
    Optional<ExamAttendanceSession> findByVenueAllocationIdAndDeletedAtIsNull(UUID venueAllocationId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<ExamAttendanceSession> findLockedByIdAndDeletedAtIsNull(UUID id);
}
interface ExamAttendanceRecordRepository extends JpaRepository<ExamAttendanceRecord,UUID> {
    List<ExamAttendanceRecord> findAllByAttendanceSessionIdAndDeletedAtIsNullOrderByStudentTimetableEntrySeatNumberAsc(UUID sessionId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<ExamAttendanceRecord> findLockedByIdAndDeletedAtIsNull(UUID id);
}
interface ExamIncidentReportRepository extends JpaRepository<ExamIncidentReport,UUID> {
    List<ExamIncidentReport> findAllByAttendanceSessionIdAndDeletedAtIsNullOrderByOccurredAtDesc(UUID sessionId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<ExamIncidentReport> findLockedByIdAndDeletedAtIsNull(UUID id);
}
