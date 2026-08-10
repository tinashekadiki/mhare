package zw.ac.uz.emhare.assessmentresults.roster;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface AssessmentRosterEntryRepository extends JpaRepository<AssessmentRosterEntry, UUID> {
    List<AssessmentRosterEntry> findAllByRosterImportIdOrderByModuleCodeAsc(UUID rosterImportId);
    List<AssessmentRosterEntry> findAllByModuleIdAndRosterImportAcademicPeriodIdAndEligibilityStatusOrderByRosterImportStudentNumberAsc(
            UUID moduleId, UUID academicPeriodId, String eligibilityStatus);
    List<AssessmentRosterEntry> findAllByEligibilityStatusOrderByModuleCodeAsc(String eligibilityStatus);
}
