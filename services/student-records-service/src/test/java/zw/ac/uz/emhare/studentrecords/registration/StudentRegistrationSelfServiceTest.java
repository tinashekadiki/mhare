package zw.ac.uz.emhare.studentrecords.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zw.ac.uz.emhare.studentrecords.conversion.StudentProfile;
import zw.ac.uz.emhare.studentrecords.conversion.StudentProfileRepository;
import zw.ac.uz.emhare.studentrecords.conversion.StudentProgrammeEnrolment;
import zw.ac.uz.emhare.studentrecords.conversion.StudentProgrammeEnrolmentRepository;
import zw.ac.uz.emhare.studentrecords.integration.StudentRecordsIntegrationOutboxService;
import zw.ac.uz.emhare.studentrecords.registration.AcademicRegistrationCatalogueClient.RegistrationCatalogue;
import zw.ac.uz.emhare.studentrecords.registration.AcademicRegistrationCatalogueClient.RegistrationModuleOption;
import zw.ac.uz.emhare.studentrecords.registration.RegistrationCommands.CreateOwnRegistration;

/** @author Tinashe K */
class StudentRegistrationSelfServiceTest {

    private StudentProfileRepository studentRepository;
    private StudentProgrammeEnrolmentRepository programmeEnrolmentRepository;
    private RegistrationSessionRepository registrationSessionRepository;
    private RegistrationModuleRepository registrationModuleRepository;
    private RegistrationStatusEventRepository statusEventRepository;
    private AcademicRegistrationCatalogueClient academicCatalogueClient;
    private StudentRecordsIntegrationOutboxService outboxService;
    private RegistrationIdentifierGenerator identifierGenerator;
    private StudentRegistrationService registrationService;

    @BeforeEach
    void setUp() {
        studentRepository = mock(StudentProfileRepository.class);
        programmeEnrolmentRepository = mock(StudentProgrammeEnrolmentRepository.class);
        registrationSessionRepository = mock(RegistrationSessionRepository.class);
        registrationModuleRepository = mock(RegistrationModuleRepository.class);
        statusEventRepository = mock(RegistrationStatusEventRepository.class);
        academicCatalogueClient = mock(AcademicRegistrationCatalogueClient.class);
        outboxService = mock(StudentRecordsIntegrationOutboxService.class);
        identifierGenerator = mock(RegistrationIdentifierGenerator.class);
        when(identifierGenerator.nextRegistrationNumber()).thenReturn("REG-00000001");
        registrationService = new StudentRegistrationService(
                studentRepository,
                programmeEnrolmentRepository,
                registrationSessionRepository,
                registrationModuleRepository,
                statusEventRepository,
                academicCatalogueClient,
                outboxService,
                identifierGenerator,
                Clock.fixed(Instant.parse("2027-01-05T09:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void studentElectiveSelectionIsRecordedWithoutAcceptingAStudentIdFromTheClient() {
        UUID actorUserId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID enrolmentId = UUID.randomUUID();
        UUID programmeId = UUID.randomUUID();
        UUID programmeVersionId = UUID.randomUUID();
        UUID academicPeriodId = UUID.randomUUID();
        UUID compulsoryCurriculumModuleId = UUID.randomUUID();
        UUID electiveCurriculumModuleId = UUID.randomUUID();
        StudentProfile student = mock(StudentProfile.class);
        StudentProgrammeEnrolment enrolment = mock(StudentProgrammeEnrolment.class);
        when(student.getId()).thenReturn(studentId);
        when(student.isActive()).thenReturn(true);
        when(student.getStudentNumber()).thenReturn("STU-2027-0000123");
        when(student.getFirstName()).thenReturn("Rudo");
        when(student.getLastName()).thenReturn("Moyo");
        when(enrolment.getId()).thenReturn(enrolmentId);
        when(enrolment.getStudent()).thenReturn(student);
        when(enrolment.isActive()).thenReturn(true);
        when(enrolment.getProgrammeId()).thenReturn(programmeId);
        when(enrolment.getProgrammeVersionId()).thenReturn(programmeVersionId);
        when(enrolment.getProgrammeCode()).thenReturn("HCS");
        when(enrolment.getProgrammeName()).thenReturn("Honours Biology");
        when(studentRepository.findByUserIdAndDeletedAtIsNull(actorUserId)).thenReturn(Optional.of(student));
        when(programmeEnrolmentRepository.findByIdAndDeletedAtIsNull(enrolmentId)).thenReturn(Optional.of(enrolment));
        when(registrationSessionRepository.saveAndFlush(any(RegistrationSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(registrationModuleRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(academicCatalogueClient.getRegistrationCatalogue(academicPeriodId, programmeVersionId, 4))
                .thenReturn(new RegistrationCatalogue(
                        academicPeriodId,
                        "2027-S2",
                        "Semester 2",
                        LocalDate.of(2027, 7, 10),
                        LocalDate.of(2027, 11, 30),
                        programmeVersionId,
                        programmeId,
                        "HCS",
                        "Honours Biology",
                        "2027.1",
                        UUID.randomUUID(),
                        "SCI",
                        "Faculty of Science",
                        UUID.randomUUID(),
                        "UG",
                        "Undergraduate",
                        4,
                        List.of(
                                module(compulsoryCurriculumModuleId, "BIO201", "Cell Biology", "COMPULSORY", 1),
                                module(electiveCurriculumModuleId, "BIO221", "Plant Ecology", "ELECTIVE", 2))));

        registrationService.createForUser(
                new CreateOwnRegistration(
                        enrolmentId,
                        academicPeriodId,
                        4,
                        Set.of(electiveCurriculumModuleId)),
                actorUserId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<RegistrationModule>> modulesCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(registrationModuleRepository).saveAll(modulesCaptor.capture());
        List<RegistrationModule> registeredModules = ((List<RegistrationModule>) modulesCaptor.getValue());
        assertEquals(
                List.of(ModuleSelectionSource.AUTO_COMPULSORY, ModuleSelectionSource.STUDENT_ELECTIVE),
                registeredModules.stream().map(RegistrationModule::getSelectionSource).toList());
    }

    @Test
    void studentCannotSubmitAnotherStudentsRegistration() {
        UUID actorUserId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();
        StudentProfile authenticatedStudent = mock(StudentProfile.class);
        StudentProfile registrationOwner = mock(StudentProfile.class);
        RegistrationSession registration = mock(RegistrationSession.class);
        when(authenticatedStudent.getId()).thenReturn(UUID.randomUUID());
        when(registrationOwner.getId()).thenReturn(UUID.randomUUID());
        when(registration.getStudent()).thenReturn(registrationOwner);
        when(registrationSessionRepository.findByIdAndDeletedAtIsNull(registrationId))
                .thenReturn(Optional.of(registration));
        when(studentRepository.findByUserIdAndDeletedAtIsNull(actorUserId))
                .thenReturn(Optional.of(authenticatedStudent));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registrationService.submitForUser(registrationId, 0, actorUserId));

        assertEquals("Registration does not belong to the authenticated student.", exception.getMessage());
    }

    private RegistrationModuleOption module(
            UUID curriculumModuleId,
            String moduleCode,
            String moduleName,
            String moduleType,
            int sortOrder) {
        return new RegistrationModuleOption(
                curriculumModuleId,
                UUID.randomUUID(),
                moduleCode,
                moduleName,
                moduleType,
                BigDecimal.valueOf(12),
                BigDecimal.valueOf(50),
                sortOrder);
    }
}
