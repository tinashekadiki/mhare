package zw.ac.uz.emhare.assessmentresults.progression.infrastructure.persistence;

import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.*;
import zw.ac.uz.emhare.assessmentresults.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.assessmentresults.progression.*;
import zw.ac.uz.emhare.assessmentresults.progression.domain.model.*;
import zw.ac.uz.emhare.assessmentresults.result.domain.model.*;
import zw.ac.uz.emhare.assessmentresults.roster.domain.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.ac.uz.emhare.assessmentresults.result.domain.model.PublishedResult;
import zw.ac.uz.emhare.assessmentresults.roster.domain.model.AssessmentRosterEntry;
import zw.ac.uz.emhare.assessmentresults.roster.domain.model.RegistrationRosterImport;

/** Spring Data persistence adapter. @author Tinashe K */
public interface ProgressionRosterEntryRepository extends JpaRepository<AssessmentRosterEntry, UUID> {
    List<AssessmentRosterEntry> findAllByRosterImportIdAndEligibilityStatusAndDeletedAtIsNull(
            UUID rosterImportId,
            String eligibilityStatus);
}
