package zw.ac.uz.emhare.assessmentresults.assessment;

import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.AssessmentCalculationComponentEvidence;
import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.AssessmentCalculationOutcome;
import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.AssessmentCalculationRun;
import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.AssessmentEnums;
import zw.ac.uz.emhare.assessmentresults.assessment.infrastructure.persistence.AssessmentCalculationComponentEvidenceRepository;
import zw.ac.uz.emhare.assessmentresults.assessment.infrastructure.persistence.AssessmentCalculationOutcomeRepository;
import zw.ac.uz.emhare.assessmentresults.assessment.infrastructure.persistence.AssessmentCalculationRunRepository;
import java.math.*;import java.util.*;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.AssessmentEnums.*;
/** @author Tinashe K */
@Service
public class AssessmentCalculationEvidenceService{
 private final AssessmentCalculationRunRepository runRepository;private final AssessmentCalculationOutcomeRepository outcomeRepository;private final AssessmentCalculationComponentEvidenceRepository componentEvidenceRepository;
 public AssessmentCalculationEvidenceService(AssessmentCalculationRunRepository a,AssessmentCalculationOutcomeRepository b,AssessmentCalculationComponentEvidenceRepository c){runRepository=a;outcomeRepository=b;componentEvidenceRepository=c;}
 @Transactional(readOnly=true)public CalculationEvidence requireComplete(UUID runId){AssessmentCalculationRun run=runRepository.findByIdAndDeletedAtIsNull(runId).orElseThrow(()->new IllegalArgumentException("Assessment calculation run was not found."));if(run.getStatus()!=CalculationStatus.COMPLETED||run.getIncompleteResultCount()>0)throw new IllegalStateException("Only a completed calculation with no incomplete students can become a result batch.");List<OutcomeEvidence> evidence=outcomeRepository.findAllByCalculationRunIdAndDeletedAtIsNull(runId).stream().map(outcome->{List<AssessmentCalculationComponentEvidence> components=componentEvidenceRepository.findAllByCalculationOutcomeIdAndDeletedAtIsNull(outcome.getId());if(components.isEmpty())throw new IllegalStateException("Calculation run has no immutable component evidence and cannot be published.");BigDecimal coursework=components.stream().filter(item->item.getComponentType()!=ComponentType.FINAL_EXAM).map(AssessmentCalculationComponentEvidence::getWeightedContribution).reduce(BigDecimal.ZERO,BigDecimal::add);BigDecimal exam=components.stream().filter(item->item.getComponentType()==ComponentType.FINAL_EXAM).map(AssessmentCalculationComponentEvidence::getWeightedContribution).reduce(BigDecimal.ZERO,BigDecimal::add);return new OutcomeEvidence(outcome,coursework.setScale(2,RoundingMode.HALF_UP),exam.setScale(2,RoundingMode.HALF_UP));}).toList();return new CalculationEvidence(run,evidence);}
 public record CalculationEvidence(AssessmentCalculationRun run,List<OutcomeEvidence> outcomes){}public record OutcomeEvidence(AssessmentCalculationOutcome outcome,BigDecimal courseworkContribution,BigDecimal examinationContribution){}
}
