package zw.ac.uz.emhare.examstimetabling.timetable;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Controlled access to published timetable evidence for downstream exam operations. @author Tinashe K */
@Service
public class ExamTimetableOperationsQueryService {
    private final ExamTimetableVenueAllocationRepository venueAllocationRepository;
    private final ExamStudentTimetableEntryRepository studentTimetableEntryRepository;

    public ExamTimetableOperationsQueryService(ExamTimetableVenueAllocationRepository venueAllocationRepository,
            ExamStudentTimetableEntryRepository studentTimetableEntryRepository) {
        this.venueAllocationRepository=venueAllocationRepository;
        this.studentTimetableEntryRepository=studentTimetableEntryRepository;
    }

    @Transactional(readOnly=true)
    public List<ExamTimetableVenueAllocation> publishedVenueAllocations() {
        return venueAllocationRepository.findAllByMasterTimetableEntryGenerationRunStatusAndDeletedAtIsNullOrderByMasterTimetableEntryScheduledStartsAtAscVenueCodeAsc(
                ExamTimetableGenerationRun.Status.PUBLISHED);
    }

    @Transactional(readOnly=true)
    public ExamTimetableVenueAllocation requirePublishedVenueAllocation(UUID allocationId) {
        ExamTimetableVenueAllocation allocation=venueAllocationRepository.findById(allocationId)
                .orElseThrow(()->new IllegalArgumentException("Published exam venue allocation was not found."));
        if(allocation.getMasterTimetableEntry().getGenerationRun().getStatus()!=ExamTimetableGenerationRun.Status.PUBLISHED) {
            throw new IllegalStateException("Attendance can only be opened for a published exam timetable allocation.");
        }
        return allocation;
    }

    @Transactional(readOnly=true)
    public List<ExamStudentTimetableEntry> studentsForAllocation(UUID allocationId) {
        return studentTimetableEntryRepository.findAllByVenueAllocationIdAndDeletedAtIsNullOrderBySeatNumberAsc(allocationId);
    }

    @Transactional(readOnly=true)
    public ExamStudentTimetableEntry requireStudentEntry(UUID studentEntryId) {
        return studentTimetableEntryRepository.findById(studentEntryId)
                .orElseThrow(()->new IllegalArgumentException("Student exam timetable entry was not found."));
    }
}
