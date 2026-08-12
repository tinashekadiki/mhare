package zw.ac.uz.emhare.assessmentresults.roster.infrastructure.persistence;

import zw.ac.uz.emhare.assessmentresults.roster.domain.model.AssessmentRosterEntry;

import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.*;
import zw.ac.uz.emhare.assessmentresults.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.assessmentresults.progression.domain.model.*;
import zw.ac.uz.emhare.assessmentresults.result.domain.model.*;
import zw.ac.uz.emhare.assessmentresults.roster.*;
import zw.ac.uz.emhare.assessmentresults.roster.domain.model.*;

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
