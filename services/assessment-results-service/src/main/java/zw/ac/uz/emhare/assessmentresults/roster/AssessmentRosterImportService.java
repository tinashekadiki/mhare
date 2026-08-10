package zw.ac.uz.emhare.assessmentresults.roster;

import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.common.messaging.StudentRegistrationConfirmedEvent;
import zw.ac.uz.emhare.common.messaging.StudentRegistrationConfirmedEvent.RegisteredModule;

/** @author Tinashe K */
@Service
public class AssessmentRosterImportService {

    private final RegistrationRosterImportRepository rosterImportRepository;
    private final AssessmentRosterEntryRepository rosterEntryRepository;
    private final Clock clock;

    public AssessmentRosterImportService(
            RegistrationRosterImportRepository rosterImportRepository,
            AssessmentRosterEntryRepository rosterEntryRepository,
            Clock clock) {
        this.rosterImportRepository = rosterImportRepository;
        this.rosterEntryRepository = rosterEntryRepository;
        this.clock = clock;
    }

    @Transactional
    public RegistrationRosterImport importConfirmedRegistration(StudentRegistrationConfirmedEvent event) {
        validate(event);
        RegistrationRosterImport existing = rosterImportRepository
                .findByRegistrationSessionIdAndDeletedAtIsNull(event.registrationSessionId())
                .orElse(null);
        if (existing != null) {
            if (!existing.isExactReplay(event)) {
                throw new IllegalStateException(
                        "Registration session was already imported from a different event payload.");
            }
            return existing;
        }
        RegistrationRosterImport rosterImport = rosterImportRepository.saveAndFlush(
                new RegistrationRosterImport(event, clock.instant()));
        rosterEntryRepository.saveAll(event.modules().stream()
                .map(module -> new AssessmentRosterEntry(rosterImport, module))
                .toList());
        return rosterImport;
    }

    private void validate(StudentRegistrationConfirmedEvent event) {
        if (event.eventId() == null
                || event.schemaVersion() != StudentRegistrationConfirmedEvent.CURRENT_SCHEMA_VERSION
                || event.registrationSessionId() == null
                || event.studentId() == null
                || event.studentNumber() == null
                || event.studentNumber().isBlank()
                || event.programmeEnrolmentId() == null
                || event.programmeId() == null
                || event.programmeVersionId() == null
                || event.academicPeriodId() == null
                || event.academicPeriodStartsOn() == null
                || event.academicPeriodEndsOn() == null
                || event.programmePeriodNumber() < 1
                || event.modules() == null
                || event.modules().isEmpty()) {
            throw new IllegalArgumentException("Confirmed-registration event contract is invalid or unsupported.");
        }
        Set<UUID> registrationModuleIds = new HashSet<>();
        Set<UUID> moduleIds = new HashSet<>();
        for (RegisteredModule module : event.modules()) {
            if (module.registrationModuleId() == null
                    || module.curriculumModuleId() == null
                    || module.moduleId() == null
                    || module.moduleCode() == null
                    || module.moduleCode().isBlank()
                    || module.moduleName() == null
                    || module.moduleName().isBlank()
                    || module.creditValue() == null
                    || module.creditValue().signum() <= 0
                    || !registrationModuleIds.add(module.registrationModuleId())
                    || !moduleIds.add(module.moduleId())) {
                throw new IllegalArgumentException(
                        "Confirmed-registration event contains an invalid or duplicate Module.");
            }
        }
    }
}
