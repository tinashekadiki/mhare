package zw.ac.uz.emhare.assessmentresults.result;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** @author Tinashe K */
interface GradingSchemeRepository extends JpaRepository<GradingScheme, UUID> {
    Optional<GradingScheme> findByIdAndDeletedAtIsNull(UUID id);
    List<GradingScheme> findAllByDeletedAtIsNullOrderByCodeAscSchemeVersionDesc();
}

interface GradingBandRepository extends JpaRepository<GradingBand, UUID> {
    List<GradingBand> findAllByGradingSchemeIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID id);
}

interface ResultBatchRepository extends JpaRepository<ResultBatch, UUID> {
    Optional<ResultBatch> findByIdAndDeletedAtIsNull(UUID id);
    List<ResultBatch> findAllByDeletedAtIsNullOrderByCreatedAtDesc();
    boolean existsByCalculationRunIdAndDeletedAtIsNull(UUID runId);
}

interface ModuleResultRepository extends JpaRepository<ModuleResult, UUID> {
    Optional<ModuleResult> findByIdAndDeletedAtIsNull(UUID id);
    List<ModuleResult> findAllByResultBatchIdAndDeletedAtIsNull(UUID batchId);
    long countByRosterEntryCurriculumModuleIdAndDeletedAtIsNull(UUID curriculumModuleId);

    @Query("""
            select moduleResult from ModuleResult moduleResult
              join moduleResult.resultBatch resultBatch
              join moduleResult.rosterEntry rosterEntry
              join rosterEntry.rosterImport rosterImport
              join resultBatch.moduleOffering moduleOffering
            where resultBatch.status = :batchStatus
              and rosterImport.studentId = :studentId
              and moduleOffering.moduleId = :moduleId
              and moduleOffering.academicPeriodId = :academicPeriodId
            order by resultBatch.approvedAt desc
            """)
    List<ModuleResult> findCorrectionSources(
            @Param("studentId") UUID studentId,
            @Param("moduleId") UUID moduleId,
            @Param("academicPeriodId") UUID academicPeriodId,
            @Param("batchStatus") ResultBatch.Status batchStatus);
}

interface ResultBatchStatusEventRepository extends JpaRepository<ResultBatchStatusEvent, UUID> {
}

interface PublishedResultRepository extends JpaRepository<PublishedResult, UUID> {
    Optional<PublishedResult> findByIdAndDeletedAtIsNull(UUID id);
    List<PublishedResult> findAllByResultBatchIdAndDeletedAtIsNull(UUID batchId);
    Optional<PublishedResult> findFirstByStudentIdAndModuleIdAndAcademicPeriodIdAndDeletedAtIsNullOrderByPublicationVersionDesc(
            UUID studentId, UUID moduleId, UUID academicPeriodId);

    @Query(
            value = """
                    select publishedResult from PublishedResult publishedResult
                    where not exists (
                        select newerResult.id from PublishedResult newerResult
                        where newerResult.studentId = publishedResult.studentId
                          and newerResult.moduleId = publishedResult.moduleId
                          and newerResult.academicPeriodId = publishedResult.academicPeriodId
                          and newerResult.publicationVersion > publishedResult.publicationVersion)
                      and (:studentNumber = ''
                        or lower(publishedResult.studentNumber) like lower(concat('%', :studentNumber, '%')))
                    order by publishedResult.publishedAt desc
                    """,
            countQuery = """
                    select count(publishedResult) from PublishedResult publishedResult
                    where not exists (
                        select newerResult.id from PublishedResult newerResult
                        where newerResult.studentId = publishedResult.studentId
                          and newerResult.moduleId = publishedResult.moduleId
                          and newerResult.academicPeriodId = publishedResult.academicPeriodId
                          and newerResult.publicationVersion > publishedResult.publicationVersion)
                      and (:studentNumber = ''
                        or lower(publishedResult.studentNumber) like lower(concat('%', :studentNumber, '%')))
                    """)
    Page<PublishedResult> findCurrentPublishedResults(
            @Param("studentNumber") String studentNumber,
            Pageable pageable);
}

interface PublishedResultAmendmentRepository extends JpaRepository<PublishedResultAmendment, UUID> {
    Optional<PublishedResultAmendment> findByIdAndDeletedAtIsNull(UUID id);
    List<PublishedResultAmendment> findAllByDeletedAtIsNullOrderByRequestedAtDesc();
}

interface PublishedResultAmendmentEventRepository
        extends JpaRepository<PublishedResultAmendmentEvent, UUID> {
}
