package zw.ac.uz.emhare.studentrecords.registration;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.studentrecords.conversion.StudentProfile;
import zw.ac.uz.emhare.studentrecords.conversion.StudentProfileRepository;
import zw.ac.uz.emhare.studentrecords.conversion.StudentProgrammeEnrolment;
import zw.ac.uz.emhare.studentrecords.conversion.StudentProgrammeEnrolmentRepository;
import zw.ac.uz.emhare.studentrecords.integration.StudentRecordsIntegrationOutboxService;
import zw.ac.uz.emhare.studentrecords.registration.AcademicRegistrationCatalogueClient.RegistrationCatalogue;
import zw.ac.uz.emhare.studentrecords.registration.AcademicRegistrationCatalogueClient.RegistrationModuleOption;
import zw.ac.uz.emhare.studentrecords.registration.RegistrationCommands.CreateRegistration;
import zw.ac.uz.emhare.studentrecords.registration.RegistrationCommands.CreateOwnRegistration;
import zw.ac.uz.emhare.studentrecords.registration.RegistrationSummary.RegisteredModuleSummary;

/** @author Tinashe K */
@Service
public class StudentRegistrationService {

    private final StudentProfileRepository studentRepository;
    private final StudentProgrammeEnrolmentRepository programmeEnrolmentRepository;
    private final RegistrationSessionRepository registrationSessionRepository;
    private final RegistrationModuleRepository registrationModuleRepository;
    private final RegistrationStatusEventRepository statusEventRepository;
    private final AcademicRegistrationCatalogueClient academicCatalogueClient;
    private final StudentRecordsIntegrationOutboxService outboxService;
    private final RegistrationIdentifierGenerator identifierGenerator;
    private final Clock clock;

    public StudentRegistrationService(
            StudentProfileRepository studentRepository,
            StudentProgrammeEnrolmentRepository programmeEnrolmentRepository,
            RegistrationSessionRepository registrationSessionRepository,
            RegistrationModuleRepository registrationModuleRepository,
            RegistrationStatusEventRepository statusEventRepository,
            AcademicRegistrationCatalogueClient academicCatalogueClient,
            StudentRecordsIntegrationOutboxService outboxService,
            RegistrationIdentifierGenerator identifierGenerator,
            Clock clock) {
        this.studentRepository = studentRepository;
        this.programmeEnrolmentRepository = programmeEnrolmentRepository;
        this.registrationSessionRepository = registrationSessionRepository;
        this.registrationModuleRepository = registrationModuleRepository;
        this.statusEventRepository = statusEventRepository;
        this.academicCatalogueClient = academicCatalogueClient;
        this.outboxService = outboxService;
        this.identifierGenerator = identifierGenerator;
        this.clock = clock;
    }

    @Transactional
    public RegistrationSummary create(CreateRegistration command, UUID actorUserId) {
        StudentProfile student = studentRepository.findByIdAndDeletedAtIsNull(command.studentId())
                .orElseThrow(() -> new IllegalArgumentException("Student was not found."));
        return createRegistration(
                student,
                command.programmeEnrolmentId(),
                command.academicPeriodId(),
                command.programmePeriodNumber(),
                command.registrationType(),
                command.selectedElectiveCurriculumModuleIds(),
                ModuleSelectionSource.STAFF_ELECTIVE,
                actorUserId);
    }

    @Transactional
    public RegistrationSummary createForUser(CreateOwnRegistration command, UUID actorUserId) {
        StudentProfile student = requireStudentForUser(actorUserId);
        return createRegistration(
                student,
                command.programmeEnrolmentId(),
                command.academicPeriodId(),
                command.programmePeriodNumber(),
                RegistrationType.NORMAL,
                command.selectedElectiveCurriculumModuleIds(),
                ModuleSelectionSource.STUDENT_ELECTIVE,
                actorUserId);
    }

    private RegistrationSummary createRegistration(
            StudentProfile student,
            UUID programmeEnrolmentId,
            UUID academicPeriodId,
            int programmePeriodNumber,
            RegistrationType registrationType,
            Set<UUID> selectedElectiveCurriculumModuleIds,
            ModuleSelectionSource electiveSelectionSource,
            UUID actorUserId) {
        if (!student.isActive()) {
            throw new IllegalStateException("Only an active student can start registration.");
        }
        StudentProgrammeEnrolment programmeEnrolment = programmeEnrolmentRepository
                .findByIdAndDeletedAtIsNull(programmeEnrolmentId)
                .orElseThrow(() -> new IllegalArgumentException("Student programme enrolment was not found."));
        if (!programmeEnrolment.getStudent().getId().equals(student.getId())) {
            throw new IllegalArgumentException("Programme enrolment does not belong to the selected student.");
        }
        if (!programmeEnrolment.isActive()) {
            throw new IllegalStateException("Only an active programme enrolment can be registered.");
        }
        if (registrationSessionRepository.existsByStudentIdAndAcademicPeriodIdAndStatusNotAndDeletedAtIsNull(
                student.getId(), academicPeriodId, RegistrationStatus.CANCELLED)) {
            throw new IllegalStateException("The student already has a registration for this academic period.");
        }

        RegistrationCatalogue catalogue = academicCatalogueClient.getRegistrationCatalogue(
                academicPeriodId, programmeEnrolment.getProgrammeVersionId(), programmePeriodNumber);
        validateCatalogue(programmeEnrolment, catalogue);
        List<RegistrationModuleOption> selectedModules = selectModules(
                catalogue.modules(), selectedElectiveCurriculumModuleIds);
        if (selectedModules.isEmpty()) {
            throw new IllegalStateException("Registration must contain at least one approved curriculum Module.");
        }

        String registrationNumber = identifierGenerator.nextRegistrationNumber();
        if (registrationSessionRepository.existsByRegistrationNumberAndProgrammeVersionId(
                registrationNumber, programmeEnrolment.getProgrammeVersionId())) {
            throw new IllegalStateException(
                    "A registration already exists for this registration number and programme.");
        }
        Instant now = clock.instant();
        RegistrationSession registration = registrationSessionRepository.saveAndFlush(new RegistrationSession(
                registrationNumber, student, programmeEnrolment, catalogue, registrationType, now));
        List<RegistrationModule> modules = selectedModules.stream()
                .map(option -> new RegistrationModule(
                        registration,
                        option,
                        "COMPULSORY".equals(option.moduleType())
                                ? ModuleSelectionSource.AUTO_COMPULSORY
                                : electiveSelectionSource))
                .toList();
        registrationModuleRepository.saveAll(modules);
        statusEventRepository.save(new RegistrationStatusEvent(
                registration, null, RegistrationStatus.DRAFT,
                "Registration initiated from the approved curriculum.", actorUserId, now));
        return summary(registration, modules);
    }

    @Transactional
    public RegistrationSummary submitForUser(UUID registrationId, long expectedVersion, UUID actorUserId) {
        RegistrationSession registration = requireOwnedRegistration(registrationId, actorUserId);
        Instant now = clock.instant();
        String reason = "Submitted by the student through self-service.";
        RegistrationStatus previous = registration.submit(reason, now, expectedVersion);
        recordStatus(registration, previous, reason, actorUserId, now);
        outboxService.enqueueRegistrationActionNotification(
                registration,
                "Your registration for " + registration.getAcademicPeriodName()
                        + " was submitted and is awaiting academic approval.");
        return summary(registration, modules(registrationId));
    }

    @Transactional
    public RegistrationSummary submit(UUID registrationId, long expectedVersion, String reason, UUID actorUserId) {
        RegistrationSession registration = requireRegistration(registrationId);
        Instant now = clock.instant();
        RegistrationStatus previous = registration.submit(reason, now, expectedVersion);
        recordStatus(registration, previous, reason, actorUserId, now);
        outboxService.enqueueRegistrationActionNotification(
                registration,
                "Your registration for " + registration.getAcademicPeriodName()
                        + " was submitted and is awaiting academic approval.");
        return summary(registration, modules(registrationId));
    }

    @Transactional
    public RegistrationSummary approveAcademically(
            UUID registrationId, long expectedVersion, String reason, UUID actorUserId) {
        RegistrationSession registration = requireRegistration(registrationId);
        Instant now = clock.instant();
        RegistrationStatus previous = registration.approveAcademically(actorUserId, reason, now, expectedVersion);
        recordStatus(registration, previous, reason, actorUserId, now);
        outboxService.enqueueRegistrationActionNotification(
                registration,
                "Academic approval is complete for " + registration.getAcademicPeriodName()
                        + "; institutional confirmation is pending.");
        return summary(registration, modules(registrationId));
    }

    @Transactional
    public RegistrationSummary confirm(
            UUID registrationId, long expectedVersion, String reason, UUID actorUserId) {
        RegistrationSession registration = requireRegistration(registrationId);
        List<RegistrationModule> modules = modules(registrationId);
        if (modules.isEmpty()) {
            throw new IllegalStateException("A registration without Modules cannot be confirmed.");
        }
        Instant now = clock.instant();
        RegistrationStatus previous = registration.confirm(actorUserId, reason, now, expectedVersion);
        recordStatus(registration, previous, reason, actorUserId, now);
        outboxService.enqueueRegistrationConfirmed(registration, modules);
        outboxService.enqueueRegistrationActionNotification(
                registration,
                "No further action is required; registration for " + registration.getAcademicPeriodName()
                        + " is confirmed.");
        return summary(registration, modules);
    }

    @Transactional
    public RegistrationSummary reject(
            UUID registrationId, long expectedVersion, String reason, UUID actorUserId) {
        RegistrationSession registration = requireRegistration(registrationId);
        Instant now = clock.instant();
        RegistrationStatus previous = registration.reject(actorUserId, reason, now, expectedVersion);
        recordStatus(registration, previous, reason, actorUserId, now);
        outboxService.enqueueRegistrationActionNotification(
                registration,
                "Registration for " + registration.getAcademicPeriodName()
                        + " was rejected. Review the reason and contact Registry if clarification is required: "
                        + reason.trim());
        return summary(registration, modules(registrationId));
    }

    @Transactional(readOnly = true)
    public List<RegistrationSummary> list() {
        return registrationSessionRepository.findAllByDeletedAtIsNullOrderByInitiatedAtDesc().stream()
                .map(registration -> summary(registration, modules(registration.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RegistrationSummary> listForUser(UUID actorUserId) {
        StudentProfile student = requireStudentForUser(actorUserId);
        return registrationSessionRepository
                .findAllByStudentIdAndDeletedAtIsNullOrderByInitiatedAtDesc(student.getId()).stream()
                .map(registration -> summary(registration, modules(registration.getId())))
                .toList();
    }

    private List<RegistrationModuleOption> selectModules(
            List<RegistrationModuleOption> availableModules,
            Set<UUID> selectedElectiveIds) {
        Set<UUID> selected = new HashSet<>(selectedElectiveIds);
        Set<UUID> availableElectives = availableModules.stream()
                .filter(module -> !"COMPULSORY".equals(module.moduleType()))
                .map(RegistrationModuleOption::curriculumModuleId)
                .collect(java.util.stream.Collectors.toSet());
        if (!availableElectives.containsAll(selected)) {
            throw new IllegalArgumentException("One or more selected electives are not in the approved curriculum period.");
        }
        return availableModules.stream()
                .filter(module -> "COMPULSORY".equals(module.moduleType())
                        || selected.contains(module.curriculumModuleId()))
                .toList();
    }

    private void validateCatalogue(StudentProgrammeEnrolment enrolment, RegistrationCatalogue catalogue) {
        if (!enrolment.getProgrammeVersionId().equals(catalogue.programmeVersionId())
                || !enrolment.getProgrammeId().equals(catalogue.programmeId())) {
            throw new IllegalStateException("Academic catalogue does not match the student's programme enrolment.");
        }
    }

    private void recordStatus(
            RegistrationSession registration,
            RegistrationStatus previous,
            String reason,
            UUID actorUserId,
            Instant now) {
        registrationSessionRepository.saveAndFlush(registration);
        statusEventRepository.save(new RegistrationStatusEvent(
                registration, previous, registration.getStatus(), reason, actorUserId, now));
    }

    private RegistrationSession requireRegistration(UUID registrationId) {
        return registrationSessionRepository.findByIdAndDeletedAtIsNull(registrationId)
                .orElseThrow(() -> new IllegalArgumentException("Registration was not found."));
    }

    private StudentProfile requireStudentForUser(UUID actorUserId) {
        return studentRepository.findByUserIdAndDeletedAtIsNull(actorUserId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No active student identity is linked to the authenticated user."));
    }

    private RegistrationSession requireOwnedRegistration(UUID registrationId, UUID actorUserId) {
        RegistrationSession registration = requireRegistration(registrationId);
        StudentProfile student = requireStudentForUser(actorUserId);
        if (!registration.getStudent().getId().equals(student.getId())) {
            throw new IllegalArgumentException("Registration does not belong to the authenticated student.");
        }
        return registration;
    }

    private List<RegistrationModule> modules(UUID registrationId) {
        return registrationModuleRepository.findAllByRegistrationSessionIdOrderBySortOrderAsc(registrationId);
    }

    private RegistrationSummary summary(RegistrationSession registration, List<RegistrationModule> modules) {
        BigDecimal totalCredits = modules.stream()
                .map(RegistrationModule::getCreditValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        StudentProfile student = registration.getStudent();
        StudentProgrammeEnrolment enrolment = registration.getProgrammeEnrolment();
        return new RegistrationSummary(
                registration.getId(), registration.getRegistrationNumber(), student.getId(), student.getStudentNumber(),
                student.getFirstName() + " " + student.getLastName(),
                enrolment.getId(), enrolment.getProgrammeCode(), enrolment.getProgrammeName(),
                registration.getAcademicPeriodId(), registration.getAcademicPeriodCode(),
                registration.getAcademicPeriodName(), registration.getAcademicPeriodStartsOn(),
                registration.getAcademicPeriodEndsOn(), registration.getProgrammePeriodNumber(),
                registration.getRegistrationType(), registration.getStatus(), registration.getStatusReason(),
                registration.getInitiatedAt(), registration.getSubmittedAt(),
                registration.getAcademicApprovedAt(), registration.getConfirmedAt(),
                registration.getVersion(), totalCredits,
                modules.stream().map(module -> new RegisteredModuleSummary(
                        module.getId(), module.getCurriculumModuleId(), module.getModuleId(),
                        module.getModuleCode(), module.getModuleName(), module.getCurriculumModuleType(),
                        module.getCreditValue(), module.getMinimumMarkRequired(), module.getSelectionSource()))
                        .toList());
    }
}
