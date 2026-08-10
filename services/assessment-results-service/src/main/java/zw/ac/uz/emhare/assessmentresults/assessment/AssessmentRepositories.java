package zw.ac.uz.emhare.assessmentresults.assessment;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.assessmentresults.assessment.AssessmentEnums.*;

/** @author Tinashe K */
interface AssessmentModuleOfferingRepository extends JpaRepository<AssessmentModuleOffering,UUID>{
    Optional<AssessmentModuleOffering> findByIdAndDeletedAtIsNull(UUID id);
    Optional<AssessmentModuleOffering> findByModuleIdAndAcademicPeriodIdAndDeletedAtIsNull(UUID moduleId,UUID periodId);
    List<AssessmentModuleOffering> findAllByDeletedAtIsNullOrderByAcademicPeriodCodeDescModuleCodeAsc();
}
interface AssessmentSchemeRepository extends JpaRepository<AssessmentScheme,UUID>{
    Optional<AssessmentScheme> findByIdAndDeletedAtIsNull(UUID id);
    Optional<AssessmentScheme> findByModuleOfferingIdAndStatusAndDeletedAtIsNull(UUID offeringId,SchemeStatus status);
    List<AssessmentScheme> findAllByModuleOfferingIdAndDeletedAtIsNullOrderBySchemeVersionDesc(UUID offeringId);
    boolean existsByModuleOfferingIdAndDeletedAtIsNull(UUID offeringId);
}
interface AssessmentComponentRepository extends JpaRepository<AssessmentComponent,UUID>{
    Optional<AssessmentComponent> findByIdAndDeletedAtIsNull(UUID id);
    List<AssessmentComponent> findAllByAssessmentSchemeIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID schemeId);
}
interface StudentAssessmentMarkRepository extends JpaRepository<StudentAssessmentMark,UUID>{
    Optional<StudentAssessmentMark> findByIdAndDeletedAtIsNull(UUID id);
    Optional<StudentAssessmentMark> findByAssessmentComponentIdAndRosterEntryIdAndStatusInAndDeletedAtIsNull(UUID componentId,UUID rosterId,Collection<MarkStatus> statuses);
    List<StudentAssessmentMark> findAllByAssessmentComponentAssessmentSchemeIdAndStatusAndDeletedAtIsNull(UUID schemeId,MarkStatus status);
}
interface MarkAmendmentRequestRepository extends JpaRepository<MarkAmendmentRequest,UUID>{
    Optional<MarkAmendmentRequest> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByOriginalMarkIdAndStatusAndDeletedAtIsNull(UUID markId,AmendmentStatus status);
    List<MarkAmendmentRequest> findAllByDeletedAtIsNullOrderByRequestedAtDesc();
}
interface AssessmentCalculationRunRepository extends JpaRepository<AssessmentCalculationRun,UUID>{Optional<AssessmentCalculationRun> findByIdAndDeletedAtIsNull(UUID id);List<AssessmentCalculationRun> findAllByDeletedAtIsNullOrderByInitiatedAtDesc();}
interface AssessmentCalculationOutcomeRepository extends JpaRepository<AssessmentCalculationOutcome,UUID>{List<AssessmentCalculationOutcome> findAllByCalculationRunIdAndDeletedAtIsNull(UUID runId);}
interface AssessmentCalculationComponentEvidenceRepository extends JpaRepository<AssessmentCalculationComponentEvidence,UUID>{List<AssessmentCalculationComponentEvidence> findAllByCalculationOutcomeIdAndDeletedAtIsNull(UUID outcomeId);long countByCalculationRunIdAndDeletedAtIsNull(UUID runId);}
