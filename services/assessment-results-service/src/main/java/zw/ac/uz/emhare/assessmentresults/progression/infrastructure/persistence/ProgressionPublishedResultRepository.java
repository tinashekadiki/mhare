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
public interface ProgressionPublishedResultRepository extends JpaRepository<PublishedResult, UUID> {
    @Query("""
            select publishedResult from PublishedResult publishedResult
              join publishedResult.moduleResult moduleResult
              join moduleResult.rosterEntry rosterEntry
            where rosterEntry.rosterImport.id = :rosterImportId
              and not exists (
                select newerResult.id from PublishedResult newerResult
                where newerResult.studentId = publishedResult.studentId
                  and newerResult.moduleId = publishedResult.moduleId
                  and newerResult.academicPeriodId = publishedResult.academicPeriodId
                  and newerResult.publicationVersion > publishedResult.publicationVersion)
            order by publishedResult.moduleCode
            """)
    List<PublishedResult> findCurrentByRosterImportId(@Param("rosterImportId") UUID rosterImportId);
}
