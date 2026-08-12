package zw.ac.uz.emhare.assessmentresults.result.infrastructure.persistence;

import zw.ac.uz.emhare.assessmentresults.result.domain.model.PublishedResult;

import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.*;
import zw.ac.uz.emhare.assessmentresults.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.assessmentresults.progression.domain.model.*;
import zw.ac.uz.emhare.assessmentresults.result.*;
import zw.ac.uz.emhare.assessmentresults.result.domain.model.*;
import zw.ac.uz.emhare.assessmentresults.roster.domain.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data persistence adapter. @author Tinashe K */
public interface PublishedResultRepository extends JpaRepository<PublishedResult, UUID> {
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
