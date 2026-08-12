package zw.ac.uz.emhare.examstimetabling.roster;

import zw.ac.uz.emhare.examstimetabling.roster.domain.model.ExamCandidateModule;
import zw.ac.uz.emhare.examstimetabling.roster.infrastructure.persistence.ExamCandidateModuleRepository;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Internal authoritative candidate access for timetable generation. @author Tinashe K */
@Service
public class ExamRosterQueryService {
    private final ExamCandidateModuleRepository candidateRepository;
    public ExamRosterQueryService(ExamCandidateModuleRepository candidateRepository){this.candidateRepository=candidateRepository;}
    public List<ExamCandidateModule> eligibleCandidates(UUID academicPeriodId){return candidateRepository.findAllByRegistrationImportAcademicPeriodIdAndEligibilityStatusAndDeletedAtIsNull(academicPeriodId,ExamCandidateModule.EligibilityStatus.ELIGIBLE);}
}
