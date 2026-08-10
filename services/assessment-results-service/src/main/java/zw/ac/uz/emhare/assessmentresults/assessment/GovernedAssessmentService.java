package zw.ac.uz.emhare.assessmentresults.assessment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.assessmentresults.assessment.AssessmentCommands.*;
import zw.ac.uz.emhare.assessmentresults.assessment.AssessmentEnums.*;
import zw.ac.uz.emhare.assessmentresults.assessment.AssessmentViews.*;
import zw.ac.uz.emhare.assessmentresults.roster.*;

/** @author Tinashe K */
@Service
public class GovernedAssessmentService {
    private static final Set<MarkStatus> CURRENT_MARK_STATUSES=Set.of(MarkStatus.CAPTURED,MarkStatus.SUBMITTED);
    private final AssessmentModuleOfferingRepository offeringRepository; private final AssessmentSchemeRepository schemeRepository;
    private final AssessmentComponentRepository componentRepository; private final StudentAssessmentMarkRepository markRepository;
    private final MarkAmendmentRequestRepository amendmentRepository; private final AssessmentCalculationRunRepository calculationRunRepository;
    private final AssessmentCalculationOutcomeRepository outcomeRepository; private final AssessmentCalculationComponentEvidenceRepository calculationEvidenceRepository; private final AssessmentRosterEntryRepository rosterRepository; private final Clock clock;
    public GovernedAssessmentService(AssessmentModuleOfferingRepository offeringRepository,AssessmentSchemeRepository schemeRepository,AssessmentComponentRepository componentRepository,StudentAssessmentMarkRepository markRepository,MarkAmendmentRequestRepository amendmentRepository,AssessmentCalculationRunRepository calculationRunRepository,AssessmentCalculationOutcomeRepository outcomeRepository,AssessmentCalculationComponentEvidenceRepository calculationEvidenceRepository,AssessmentRosterEntryRepository rosterRepository,Clock clock){this.offeringRepository=offeringRepository;this.schemeRepository=schemeRepository;this.componentRepository=componentRepository;this.markRepository=markRepository;this.amendmentRepository=amendmentRepository;this.calculationRunRepository=calculationRunRepository;this.outcomeRepository=outcomeRepository;this.calculationEvidenceRepository=calculationEvidenceRepository;this.rosterRepository=rosterRepository;this.clock=clock;}

    @Transactional
    public OfferingSummary createOffering(CreateOffering command){
        offeringRepository.findByModuleIdAndAcademicPeriodIdAndDeletedAtIsNull(command.moduleId(),command.academicPeriodId()).ifPresent(existing->{throw new IllegalStateException("An assessment offering already exists for this Module and academic period.");});
        List<AssessmentRosterEntry> roster=eligibleRoster(command.moduleId(),command.academicPeriodId());
        if(roster.isEmpty())throw new IllegalStateException("A Module offering requires at least one confirmed eligible registration.");
        AssessmentModuleOffering offering=offeringRepository.saveAndFlush(new AssessmentModuleOffering(roster.getFirst(),command.assignedInstructorUserId()));
        return offeringSummary(offering);
    }

    @Transactional
    public SchemeSummary createScheme(UUID offeringId,CreateScheme command){
        AssessmentModuleOffering offering=requireOffering(offeringId);
        List<AssessmentScheme> history=schemeRepository.findAllByModuleOfferingIdAndDeletedAtIsNullOrderBySchemeVersionDesc(offeringId);
        if(history.stream().anyMatch(s->s.getStatus()==SchemeStatus.DRAFT))throw new IllegalStateException("Complete or remove the existing draft scheme before creating another version.");
        int version=history.isEmpty()?1:history.getFirst().getSchemeVersion()+1;
        AssessmentScheme scheme=schemeRepository.saveAndFlush(new AssessmentScheme(offering,version,command.name()));
        validateComponentDefinitions(command.components());
        List<AssessmentComponent> components=command.components().stream().map(definition->new AssessmentComponent(scheme,definition.code(),definition.name(),definition.componentType(),definition.weightPercent(),definition.maximumMark(),definition.captureOpensAt(),definition.captureClosesAt(),definition.sortOrder())).toList();
        componentRepository.saveAll(components);
        return schemeSummary(scheme,components);
    }

    @Transactional
    public SchemeSummary approveScheme(UUID schemeId,Decision decision,UUID actor){
        AssessmentScheme scheme=requireScheme(schemeId); List<AssessmentComponent> components=components(schemeId); validateApprovedScheme(components);
        schemeRepository.findByModuleOfferingIdAndStatusAndDeletedAtIsNull(scheme.getModuleOffering().getId(),SchemeStatus.APPROVED).filter(existing->!existing.getId().equals(schemeId)).ifPresent(existing->{existing.supersede();schemeRepository.saveAndFlush(existing);});
        scheme.approve(actor,decision.reason(),clock.instant(),decision.expectedVersion()); schemeRepository.saveAndFlush(scheme);
        return schemeSummary(scheme,components);
    }

    @Transactional
    public List<MarkSummary> captureMarks(UUID componentId,CaptureMarkBatch batch,UUID actor,boolean privileged){
        if(batch.captureMethod()==CaptureMethod.AMENDMENT)throw new IllegalArgumentException("AMENDMENT capture is reserved for the amendment workflow.");
        AssessmentComponent component=requireComponent(componentId); requireCaptureAuthority(component,actor,privileged); Instant now=clock.instant();
        if(component.getAssessmentScheme().getStatus()!=SchemeStatus.APPROVED)throw new IllegalStateException("Marks can only be captured against an approved assessment scheme.");
        if(!component.isCaptureOpen(now))throw new IllegalStateException("The assessment component capture window is closed.");
        Set<UUID> seen=new HashSet<>(); List<AssessmentRosterEntry> roster=eligibleRoster(component.getAssessmentScheme().getModuleOffering().getModuleId(),component.getAssessmentScheme().getModuleOffering().getAcademicPeriodId());
        Map<UUID,AssessmentRosterEntry> rosterById=roster.stream().collect(Collectors.toMap(AssessmentRosterEntry::getId,Function.identity())); List<StudentAssessmentMark> saved=new ArrayList<>();
        for(CaptureMark capture:batch.marks()){
            if(!seen.add(capture.rosterEntryId()))throw new IllegalArgumentException("The capture batch contains a duplicate student roster row.");
            AssessmentRosterEntry entry=rosterById.get(capture.rosterEntryId()); if(entry==null)throw new IllegalArgumentException("A selected student is not on the eligible Module roster.");
            StudentAssessmentMark mark=markRepository.findByAssessmentComponentIdAndRosterEntryIdAndStatusInAndDeletedAtIsNull(componentId,entry.getId(),CURRENT_MARK_STATUSES).orElse(null);
            if(mark==null)mark=new StudentAssessmentMark(component,entry,capture.score(),batch.captureMethod(),actor,now); else mark.reviseCapturedScore(capture.score(),actor,now,capture.expectedVersion());
            saved.add(markRepository.save(mark));
        }
        markRepository.flush(); return saved.stream().map(this::markSummary).toList();
    }

    @Transactional
    public MarkSummary submitMark(UUID markId,long expectedVersion,UUID actor,boolean privileged){StudentAssessmentMark mark=requireMark(markId);requireCaptureAuthority(mark.getAssessmentComponent(),actor,privileged);Instant now=clock.instant();if(!mark.getAssessmentComponent().isCaptureOpen(now))throw new IllegalStateException("The assessment component capture window is closed.");mark.submit(actor,now,expectedVersion);return markSummary(markRepository.saveAndFlush(mark));}

    @Transactional
    public AmendmentSummary requestAmendment(UUID markId,RequestAmendment command,UUID actor,boolean privileged){StudentAssessmentMark mark=requireMark(markId);requireCaptureAuthority(mark.getAssessmentComponent(),actor,privileged);if(amendmentRepository.existsByOriginalMarkIdAndStatusAndDeletedAtIsNull(markId,AmendmentStatus.REQUESTED))throw new IllegalStateException("This mark already has a pending amendment request.");return amendmentSummary(amendmentRepository.saveAndFlush(new MarkAmendmentRequest(mark,command.proposedScore(),command.reason(),actor,clock.instant())));}

    @Transactional
    public AmendmentSummary approveAmendment(UUID amendmentId,Decision decision,UUID actor){MarkAmendmentRequest amendment=requireAmendment(amendmentId);Instant now=clock.instant();StudentAssessmentMark replacement=amendment.prepareApprovedReplacement(decision.expectedVersion(),actor,now);markRepository.saveAndFlush(amendment.getOriginalMark());replacement=markRepository.saveAndFlush(replacement);amendment.approveWithReplacement(replacement,actor,decision.reason(),now);return amendmentSummary(amendmentRepository.saveAndFlush(amendment));}
    @Transactional
    public AmendmentSummary rejectAmendment(UUID amendmentId,Decision decision,UUID actor){MarkAmendmentRequest amendment=requireAmendment(amendmentId);amendment.reject(actor,decision.reason(),clock.instant(),decision.expectedVersion());return amendmentSummary(amendmentRepository.saveAndFlush(amendment));}

    @Transactional
    public CalculationRunSummary calculate(UUID offeringId,UUID actor){AssessmentModuleOffering offering=requireOffering(offeringId);AssessmentScheme scheme=schemeRepository.findByModuleOfferingIdAndStatusAndDeletedAtIsNull(offeringId,SchemeStatus.APPROVED).orElseThrow(()->new IllegalStateException("An approved assessment scheme is required before calculation."));List<AssessmentComponent> components=components(scheme.getId());validateApprovedScheme(components);List<AssessmentRosterEntry> roster=eligibleRoster(offering.getModuleId(),offering.getAcademicPeriodId());List<StudentAssessmentMark> marks=markRepository.findAllByAssessmentComponentAssessmentSchemeIdAndStatusAndDeletedAtIsNull(scheme.getId(),MarkStatus.SUBMITTED);Map<String,StudentAssessmentMark> markIndex=marks.stream().collect(Collectors.toMap(mark->mark.getAssessmentComponent().getId()+":"+mark.getRosterEntry().getId(),Function.identity()));List<PendingOutcome> pending=new ArrayList<>();int complete=0;for(AssessmentRosterEntry entry:roster){List<String> missing=new ArrayList<>();BigDecimal total=BigDecimal.ZERO;for(AssessmentComponent component:components){StudentAssessmentMark mark=markIndex.get(component.getId()+":"+entry.getId());if(mark==null){missing.add(component.getCode());continue;}total=total.add(mark.getScore().divide(component.getMaximumMark(),10,RoundingMode.HALF_UP).multiply(component.getWeightPercent()));}String missingCodes=missing.isEmpty()?null:String.join(", ",missing);if(missingCodes==null)complete++;pending.add(new PendingOutcome(entry,total.setScale(2,RoundingMode.HALF_UP),missingCodes));}Map<String,Object> snapshot=Map.of("schemeVersion",scheme.getSchemeVersion(),"components",components.stream().map(component->Map.of("code",component.getCode(),"weightPercent",component.getWeightPercent(),"maximumMark",component.getMaximumMark())).toList());Instant now=clock.instant();AssessmentCalculationRun run=calculationRunRepository.saveAndFlush(new AssessmentCalculationRun(offering,scheme,snapshot,roster.size(),complete,actor,now));List<AssessmentCalculationOutcome> outcomes=outcomeRepository.saveAllAndFlush(pending.stream().map(item->new AssessmentCalculationOutcome(run,item.entry(),item.total(),item.missingCodes())).toList());List<AssessmentCalculationComponentEvidence> evidence=new ArrayList<>();for(AssessmentCalculationOutcome outcome:outcomes){for(AssessmentComponent component:components){StudentAssessmentMark mark=markIndex.get(component.getId()+":"+outcome.getRosterEntry().getId());if(mark!=null){BigDecimal contribution=mark.getScore().divide(component.getMaximumMark(),10,RoundingMode.HALF_UP).multiply(component.getWeightPercent()).setScale(2,RoundingMode.HALF_UP);evidence.add(new AssessmentCalculationComponentEvidence(run,outcome,component,mark,contribution));}}}calculationEvidenceRepository.saveAll(evidence);return calculationSummary(run,outcomes);}

    @Transactional(readOnly=true) public List<OfferingSummary> listOfferings(){return offeringRepository.findAllByDeletedAtIsNullOrderByAcademicPeriodCodeDescModuleCodeAsc().stream().map(this::offeringSummary).toList();}
    @Transactional(readOnly=true) public List<RosterSourceSummary> rosterSources(){Map<String,List<AssessmentRosterEntry>> grouped=rosterRepository.findAllByEligibilityStatusOrderByModuleCodeAsc("ELIGIBLE").stream().collect(Collectors.groupingBy(entry->entry.getModuleId()+":"+entry.getRosterImport().getAcademicPeriodId(),LinkedHashMap::new,Collectors.toList()));return grouped.values().stream().map(entries->{AssessmentRosterEntry entry=entries.getFirst();RegistrationRosterImport source=entry.getRosterImport();boolean created=offeringRepository.findByModuleIdAndAcademicPeriodIdAndDeletedAtIsNull(entry.getModuleId(),source.getAcademicPeriodId()).isPresent();return new RosterSourceSummary(entry.getModuleId(),entry.getModuleCode(),entry.getModuleName(),source.getAcademicPeriodId(),source.getAcademicPeriodCode(),source.getAcademicPeriodName(),entries.size(),created);}).toList();}
    @Transactional(readOnly=true) public List<RosterMarkSummary> componentRoster(UUID componentId){AssessmentComponent component=requireComponent(componentId);List<AssessmentRosterEntry> roster=eligibleRoster(component.getAssessmentScheme().getModuleOffering().getModuleId(),component.getAssessmentScheme().getModuleOffering().getAcademicPeriodId());return roster.stream().map(entry->{StudentAssessmentMark mark=markRepository.findByAssessmentComponentIdAndRosterEntryIdAndStatusInAndDeletedAtIsNull(componentId,entry.getId(),CURRENT_MARK_STATUSES).orElse(null);RegistrationRosterImport source=entry.getRosterImport();return new RosterMarkSummary(entry.getId(),source.getStudentId(),source.getStudentNumber(),source.getStudentNumber(),componentId,component.getCode(),mark==null?null:mark.getId(),mark==null?null:mark.getRevisionNumber(),mark==null?null:mark.getScore(),mark==null?null:mark.getStatus(),mark==null?0:mark.getVersion());}).toList();}
    @Transactional(readOnly=true) public List<AmendmentSummary> amendmentQueue(){return amendmentRepository.findAllByDeletedAtIsNullOrderByRequestedAtDesc().stream().map(this::amendmentSummary).toList();}
    @Transactional(readOnly=true) public List<CalculationRunSummary> calculationHistory(){return calculationRunRepository.findAllByDeletedAtIsNullOrderByInitiatedAtDesc().stream().map(run->calculationSummary(run,outcomeRepository.findAllByCalculationRunIdAndDeletedAtIsNull(run.getId()))).toList();}

    private void validateComponentDefinitions(List<ComponentDefinition> definitions){Set<String> codes=new HashSet<>();Set<Integer> orders=new HashSet<>();for(ComponentDefinition d:definitions){if(!codes.add(d.code().trim().toUpperCase()))throw new IllegalArgumentException("Assessment component codes must be unique.");if(!orders.add(d.sortOrder()))throw new IllegalArgumentException("Assessment component sort orders must be unique.");}}
    private void validateApprovedScheme(List<AssessmentComponent> components){if(components.isEmpty())throw new IllegalStateException("An assessment scheme must contain at least one component.");BigDecimal total=components.stream().map(AssessmentComponent::getWeightPercent).reduce(BigDecimal.ZERO,BigDecimal::add);if(total.compareTo(new BigDecimal("100.00"))!=0)throw new IllegalStateException("Approved assessment component weights must total exactly 100.00 percent.");}
    private void requireCaptureAuthority(AssessmentComponent component,UUID actor,boolean privileged){if(!privileged&&!component.getAssessmentScheme().getModuleOffering().getAssignedInstructorUserId().equals(actor))throw new IllegalStateException("Only the assigned instructor can capture or amend marks for this Module offering.");}
    private AssessmentModuleOffering requireOffering(UUID id){return offeringRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(()->new IllegalArgumentException("Assessment Module offering was not found."));}
    private AssessmentScheme requireScheme(UUID id){return schemeRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(()->new IllegalArgumentException("Assessment scheme was not found."));}
    private AssessmentComponent requireComponent(UUID id){return componentRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(()->new IllegalArgumentException("Assessment component was not found."));}
    private StudentAssessmentMark requireMark(UUID id){return markRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(()->new IllegalArgumentException("Assessment mark was not found."));}
    private MarkAmendmentRequest requireAmendment(UUID id){return amendmentRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(()->new IllegalArgumentException("Mark amendment request was not found."));}
    private List<AssessmentRosterEntry> eligibleRoster(UUID moduleId,UUID periodId){return rosterRepository.findAllByModuleIdAndRosterImportAcademicPeriodIdAndEligibilityStatusOrderByRosterImportStudentNumberAsc(moduleId,periodId,"ELIGIBLE");}
    private List<AssessmentComponent> components(UUID schemeId){return componentRepository.findAllByAssessmentSchemeIdAndDeletedAtIsNullOrderBySortOrderAsc(schemeId);}
    private OfferingSummary offeringSummary(AssessmentModuleOffering offering){List<SchemeSummary> schemes=schemeRepository.findAllByModuleOfferingIdAndDeletedAtIsNullOrderBySchemeVersionDesc(offering.getId()).stream().map(scheme->schemeSummary(scheme,components(scheme.getId()))).toList();return new OfferingSummary(offering.getId(),offering.getModuleId(),offering.getModuleCode(),offering.getModuleName(),offering.getAcademicPeriodId(),offering.getAcademicPeriodCode(),offering.getAcademicPeriodName(),offering.getAssignedInstructorUserId(),offering.getStatus(),offering.getVersion(),eligibleRoster(offering.getModuleId(),offering.getAcademicPeriodId()).size(),schemes);}
    private SchemeSummary schemeSummary(AssessmentScheme scheme,List<AssessmentComponent> components){return new SchemeSummary(scheme.getId(),scheme.getSchemeVersion(),scheme.getName(),scheme.getStatus(),scheme.getApprovalReason(),scheme.getApprovedByUserId(),scheme.getApprovedAt(),scheme.getVersion(),components.stream().map(component->new ComponentSummary(component.getId(),component.getCode(),component.getName(),component.getComponentType(),component.getWeightPercent(),component.getMaximumMark(),component.getCaptureOpensAt(),component.getCaptureClosesAt(),component.getSortOrder())).toList());}
    private MarkSummary markSummary(StudentAssessmentMark mark){return new MarkSummary(mark.getId(),mark.getAssessmentComponent().getId(),mark.getRosterEntry().getId(),mark.getRevisionNumber(),mark.getSupersedesMarkId(),mark.getScore(),mark.getStatus(),mark.getCaptureMethod(),mark.getCapturedByUserId(),mark.getCapturedAt(),mark.getSubmittedByUserId(),mark.getSubmittedAt(),mark.getVersion());}
    private AmendmentSummary amendmentSummary(MarkAmendmentRequest amendment){StudentAssessmentMark original=amendment.getOriginalMark();return new AmendmentSummary(amendment.getId(),original.getId(),original.getScore(),amendment.getProposedScore(),amendment.getReason(),amendment.getStatus(),amendment.getRequestedByUserId(),amendment.getRequestedAt(),amendment.getDecidedByUserId(),amendment.getDecidedAt(),amendment.getDecisionReason(),amendment.getReplacementMark()==null?null:amendment.getReplacementMark().getId(),amendment.getVersion());}
    private CalculationRunSummary calculationSummary(AssessmentCalculationRun run,List<AssessmentCalculationOutcome> outcomes){boolean publicationEvidenceAvailable=run.getIncompleteResultCount()==0&&calculationEvidenceRepository.countByCalculationRunIdAndDeletedAtIsNull(run.getId())>0;return new CalculationRunSummary(run.getId(),run.getModuleOffering().getId(),run.getAssessmentScheme().getId(),run.getRosterCount(),run.getCompleteResultCount(),run.getIncompleteResultCount(),run.getStatus(),run.getInitiatedAt(),publicationEvidenceAvailable,outcomes.stream().map(outcome->new CalculationOutcomeSummary(outcome.getRosterEntry().getId(),outcome.getRosterEntry().getRosterImport().getStudentNumber(),outcome.getWeightedTotal(),outcome.isComplete(),outcome.getMissingComponentCodes())).toList());}
    private record PendingOutcome(AssessmentRosterEntry entry,BigDecimal total,String missingCodes){}
}
