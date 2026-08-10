package zw.ac.uz.emhare.assessmentresults.progression;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.ac.uz.emhare.assessmentresults.result.PublishedResult;
import zw.ac.uz.emhare.assessmentresults.roster.AssessmentRosterEntry;
import zw.ac.uz.emhare.assessmentresults.roster.RegistrationRosterImport;

/** @author Tinashe K */
interface ProgressionRuleSetRepository extends JpaRepository<ProgressionRuleSet, UUID> {
    Optional<ProgressionRuleSet> findByIdAndDeletedAtIsNull(UUID id);
    List<ProgressionRuleSet> findAllByDeletedAtIsNullOrderByRuleCodeAscRuleVersionDesc();
    Optional<ProgressionRuleSet> findByProgrammeVersionIdAndProgrammePeriodNumberAndStatusAndDeletedAtIsNull(
            UUID programmeVersionId,
            int programmePeriodNumber,
            ProgressionRuleSet.Status status);
}

interface ProgressionRuleOutcomeRepository extends JpaRepository<ProgressionRuleOutcome, UUID> {
    List<ProgressionRuleOutcome> findAllByRuleSetIdAndDeletedAtIsNullOrderByPriorityAsc(UUID ruleSetId);
}

interface ProgressionRosterImportRepository extends JpaRepository<RegistrationRosterImport, UUID> {
    Optional<RegistrationRosterImport> findByIdAndDeletedAtIsNull(UUID id);
    List<RegistrationRosterImport> findAllByDeletedAtIsNullOrderByImportedAtDesc();
}

interface ProgressionRosterEntryRepository extends JpaRepository<AssessmentRosterEntry, UUID> {
    List<AssessmentRosterEntry> findAllByRosterImportIdAndEligibilityStatusAndDeletedAtIsNull(
            UUID rosterImportId,
            String eligibilityStatus);
}

interface ProgressionPublishedResultRepository extends JpaRepository<PublishedResult, UUID> {
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

interface StudentOverallDecisionRepository extends JpaRepository<StudentOverallDecision, UUID> {
    Optional<StudentOverallDecision> findByIdAndDeletedAtIsNull(UUID id);
    Optional<StudentOverallDecision> findFirstByRosterImportIdAndDeletedAtIsNullOrderByDecisionVersionDesc(
            UUID rosterImportId);
    List<StudentOverallDecision> findAllByDeletedAtIsNullOrderByCalculatedAtDesc();
}

interface StudentOverallDecisionResultRepository extends JpaRepository<StudentOverallDecisionResult, UUID> {
    List<StudentOverallDecisionResult> findAllByDecisionIdAndDeletedAtIsNullOrderByModuleCodeAsc(UUID decisionId);
}

interface StudentOverallDecisionEventRepository extends JpaRepository<StudentOverallDecisionEvent, UUID> {
}
