package zw.ac.uz.emhare.examstimetabling.timetable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
interface ExamTimetableGenerationRunRepository extends JpaRepository<ExamTimetableGenerationRun,UUID> {
    List<ExamTimetableGenerationRun> findAllByDeletedAtIsNullOrderByGeneratedAtDesc();
    Optional<ExamTimetableGenerationRun> findByIdAndDeletedAtIsNull(UUID id);
}
interface ExamMasterTimetableEntryRepository extends JpaRepository<ExamMasterTimetableEntry,UUID> {
    List<ExamMasterTimetableEntry> findAllByGenerationRunIdAndDeletedAtIsNullOrderByScheduledStartsAtAscModuleCodeAsc(UUID runId);
}
interface ExamTimetableVenueAllocationRepository extends JpaRepository<ExamTimetableVenueAllocation,UUID> {
    List<ExamTimetableVenueAllocation> findAllByMasterTimetableEntryIdAndDeletedAtIsNullOrderByVenueCodeAsc(UUID masterEntryId);
    List<ExamTimetableVenueAllocation> findAllByMasterTimetableEntryGenerationRunStatusAndDeletedAtIsNullOrderByMasterTimetableEntryScheduledStartsAtAscVenueCodeAsc(ExamTimetableGenerationRun.Status status);
}
interface ExamStudentTimetableEntryRepository extends JpaRepository<ExamStudentTimetableEntry,UUID> {
    List<ExamStudentTimetableEntry> findAllByGenerationRunIdAndDeletedAtIsNullOrderByStudentNumberAscScheduledStartsAtAsc(UUID runId);
    List<ExamStudentTimetableEntry> findAllByStudentIdAndGenerationRunStatusAndDeletedAtIsNullOrderByScheduledStartsAtAsc(
            UUID studentId,ExamTimetableGenerationRun.Status status);
    List<ExamStudentTimetableEntry> findAllByVenueAllocationIdAndDeletedAtIsNullOrderBySeatNumberAsc(UUID venueAllocationId);
}
interface ExamTimetableRunEventRepository extends JpaRepository<ExamTimetableRunEvent,UUID> {}
