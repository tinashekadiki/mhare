package zw.ac.uz.emhare.examstimetabling.roster;

import zw.ac.uz.emhare.examstimetabling.roster.domain.model.ExamCandidateModule;
import zw.ac.uz.emhare.examstimetabling.roster.domain.model.ExamRegistrationImport;
import zw.ac.uz.emhare.examstimetabling.roster.infrastructure.persistence.ExamCandidateModuleRepository;
import zw.ac.uz.emhare.examstimetabling.roster.infrastructure.persistence.ExamRegistrationImportRepository;

import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.common.messaging.StudentRegistrationConfirmedEvent;

/** @author Tinashe K */
@Service
public class ExamRosterImportService {
    private final ExamRegistrationImportRepository registrationRepository;
    private final ExamCandidateModuleRepository candidateRepository;
    private final Clock clock;
    public ExamRosterImportService(ExamRegistrationImportRepository registrationRepository,
            ExamCandidateModuleRepository candidateRepository, Clock clock) {
        this.registrationRepository=registrationRepository; this.candidateRepository=candidateRepository; this.clock=clock;
    }
    @Transactional
    public ExamRegistrationImport importConfirmedRegistration(StudentRegistrationConfirmedEvent event) {
        validate(event);
        ExamRegistrationImport existing=registrationRepository
                .findByRegistrationSessionIdAndDeletedAtIsNull(event.registrationSessionId()).orElse(null);
        if(existing!=null) {
            if(!existing.isExactReplay(event)) throw new IllegalStateException("Registration was already imported from different evidence.");
            return existing;
        }
        ExamRegistrationImport imported=registrationRepository.saveAndFlush(new ExamRegistrationImport(event,clock.instant()));
        candidateRepository.saveAllAndFlush(event.modules().stream().map(module->new ExamCandidateModule(imported,module)).toList());
        return imported;
    }
    private void validate(StudentRegistrationConfirmedEvent event) {
        if(event==null || event.eventId()==null || event.schemaVersion()!=StudentRegistrationConfirmedEvent.CURRENT_SCHEMA_VERSION
                || event.registrationSessionId()==null || event.studentId()==null || blank(event.studentNumber())
                || event.programmeEnrolmentId()==null || event.programmeId()==null || event.programmeVersionId()==null
                || event.academicPeriodId()==null || blank(event.academicPeriodCode()) || blank(event.academicPeriodName())
                || event.academicPeriodStartsOn()==null || event.academicPeriodEndsOn()==null
                || event.academicPeriodEndsOn().isBefore(event.academicPeriodStartsOn()) || event.modules()==null || event.modules().isEmpty())
            throw new IllegalArgumentException("Confirmed-registration event contract is invalid or unsupported.");
        Set<UUID> moduleIds=new HashSet<>(); Set<UUID> registrationModuleIds=new HashSet<>();
        event.modules().forEach(module->{
            if(module.registrationModuleId()==null || module.curriculumModuleId()==null || module.moduleId()==null
                    || blank(module.moduleCode()) || blank(module.moduleName()) || !moduleIds.add(module.moduleId())
                    || !registrationModuleIds.add(module.registrationModuleId()))
                throw new IllegalArgumentException("Confirmed-registration event contains an invalid or duplicate Module.");
        });
    }
    private static boolean blank(String value){return value==null||value.isBlank();}
}
