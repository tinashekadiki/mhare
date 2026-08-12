package zw.ac.uz.emhare.assessmentresults.result.infrastructure.persistence;

import zw.ac.uz.emhare.assessmentresults.result.domain.model.ModuleResult;
import zw.ac.uz.emhare.assessmentresults.result.domain.model.ResultBatch;

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
public interface ModuleResultRepository extends JpaRepository<ModuleResult, UUID> {
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
