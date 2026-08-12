package zw.ac.uz.emhare.studentrecords.registration;

import zw.ac.uz.emhare.studentrecords.registration.infrastructure.persistence.RegistrationModuleRepository;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authoritative registration usage check for Academic Setup curriculum amendments. @author Tinashe K */
@Service
public class CurriculumRegistrationUsageService {

    private final RegistrationModuleRepository registrationModuleRepository;

    public CurriculumRegistrationUsageService(RegistrationModuleRepository registrationModuleRepository) {
        this.registrationModuleRepository = registrationModuleRepository;
    }

    @Transactional(readOnly = true)
    public CurriculumRegistrationUsageSummary usage(UUID curriculumModuleId) {
        return new CurriculumRegistrationUsageSummary(
                curriculumModuleId,
                registrationModuleRepository.countByCurriculumModuleId(curriculumModuleId));
    }

    public record CurriculumRegistrationUsageSummary(UUID curriculumModuleId, long registrationCount) {
    }
}
