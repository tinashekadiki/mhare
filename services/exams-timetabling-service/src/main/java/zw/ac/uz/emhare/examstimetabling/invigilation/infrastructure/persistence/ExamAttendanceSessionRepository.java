package zw.ac.uz.emhare.examstimetabling.invigilation.infrastructure.persistence;

import zw.ac.uz.emhare.examstimetabling.invigilation.domain.model.ExamAttendanceSession;

import zw.ac.uz.emhare.examstimetabling.invigilation.*;
import zw.ac.uz.emhare.examstimetabling.invigilation.domain.model.*;
import zw.ac.uz.emhare.examstimetabling.roster.domain.model.*;
import zw.ac.uz.emhare.examstimetabling.setup.domain.model.*;
import zw.ac.uz.emhare.examstimetabling.timetable.domain.model.*;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Spring Data persistence adapter. @author Tinashe K */
public interface ExamAttendanceSessionRepository extends JpaRepository<ExamAttendanceSession,UUID> {
    Optional<ExamAttendanceSession> findByVenueAllocationIdAndDeletedAtIsNull(UUID venueAllocationId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<ExamAttendanceSession> findLockedByIdAndDeletedAtIsNull(UUID id);
}
