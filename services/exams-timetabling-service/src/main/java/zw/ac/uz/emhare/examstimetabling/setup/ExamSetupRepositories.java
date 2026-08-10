package zw.ac.uz.emhare.examstimetabling.setup;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
interface ExamVenueTypeRepository extends JpaRepository<ExamVenueType,UUID> {
    List<ExamVenueType> findAllByDeletedAtIsNullOrderByCodeAsc();
}
interface ExamVenueRepository extends JpaRepository<ExamVenue,UUID> {
    List<ExamVenue> findAllByActiveTrueAndDeletedAtIsNullOrderByCodeAsc();
}
interface ExamVenueAvailabilityRepository extends JpaRepository<ExamVenueAvailabilityWindow,UUID> {
    List<ExamVenueAvailabilityWindow> findAllByVenueIdAndDeletedAtIsNullOrderByAvailableFromAsc(UUID venueId);
    List<ExamVenueAvailabilityWindow> findAllByAvailableFromLessThanEqualAndAvailableUntilGreaterThanEqualAndDeletedAtIsNull(
            Instant startsAt,Instant endsAt);
}
interface ExamSessionRepository extends JpaRepository<ExamSession,UUID> {
    List<ExamSession> findAllByDeletedAtIsNullOrderByStartsOnDescCodeAsc();
}
interface ExamSessionSlotRepository extends JpaRepository<ExamSessionSlot,UUID> {
    List<ExamSessionSlot> findAllByExamSessionIdAndDeletedAtIsNullOrderByStartsAtAsc(UUID examSessionId);
}
interface ModuleExamRequirementRepository extends JpaRepository<ModuleExamRequirement,UUID> {
    List<ModuleExamRequirement> findAllByDeletedAtIsNullOrderByModuleCodeAscRequirementVersionDesc();
    List<ModuleExamRequirement> findAllByAcademicPeriodIdAndStatusAndDeletedAtIsNull(
            UUID academicPeriodId,ModuleExamRequirement.Status status);
    Optional<ModuleExamRequirement> findByAcademicPeriodIdAndModuleIdAndStatusAndDeletedAtIsNull(
            UUID academicPeriodId,UUID moduleId,ModuleExamRequirement.Status status);
}
