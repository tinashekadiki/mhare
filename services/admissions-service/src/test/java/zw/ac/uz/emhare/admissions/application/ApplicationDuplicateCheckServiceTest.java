package zw.ac.uz.emhare.admissions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionCycle;
import zw.ac.uz.emhare.admissions.domain.model.Applicant;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeChoice;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProgrammeChoiceRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationRepository;

/** Audited-clearance duplicate evidence regressions. @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class ApplicationDuplicateCheckServiceTest {

    @Mock private ApplicantRepository applicantRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApplicationProgrammeChoiceRepository choiceRepository;

    private ApplicationDuplicateCheckService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationDuplicateCheckService(
                applicantRepository,
                applicationRepository,
                choiceRepository);
    }

    @Test
    void check_shouldRecordPassedIdentityApplicationAndChoiceChecks_whenEvidenceIsUnique() {
        UUID applicationId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID intakeProjectionId = UUID.randomUUID();
        Application application = application(applicationId, applicantId, intakeProjectionId, "63-123456-A-12", "P123456");
        ApplicationProgrammeChoice firstChoice = choice(1, UUID.randomUUID());
        ApplicationProgrammeChoice secondChoice = choice(2, UUID.randomUUID());

        Applicant applicationApplicant = application.getApplicant();
        when(applicantRepository.findByNationalIdNumberIgnoreCaseAndDeletedAtIsNull("63-123456-A-12"))
                .thenReturn(Optional.of(applicationApplicant));
        when(applicantRepository.findByPassportNumberIgnoreCaseAndDeletedAtIsNull("P123456"))
                .thenReturn(Optional.of(applicationApplicant));
        when(applicationRepository.findByIntakeIdAndApplicantNationalIdNumber(
                intakeProjectionId, "63-123456-A-12")).thenReturn(List.of(application));
        when(choiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(applicationId))
                .thenReturn(List.of(firstChoice, secondChoice));

        ApplicationDuplicateCheckService.DuplicateCheckResult result = service.check(application);

        assertThat(result.passed()).isTrue();
        assertThat(result.summary()).contains("identity", "intake application", "programme choice");
    }

    @Test
    void check_shouldBlockClearance_whenAnotherApplicantUsesTheIdentity() {
        UUID applicationId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID intakeProjectionId = UUID.randomUUID();
        Application application = application(applicationId, applicantId, intakeProjectionId, "63-123456-A-12", null);
        Applicant otherApplicant = mock(Applicant.class);
        when(otherApplicant.getId()).thenReturn(UUID.randomUUID());
        when(applicantRepository.findByNationalIdNumberIgnoreCaseAndDeletedAtIsNull("63-123456-A-12"))
                .thenReturn(Optional.of(otherApplicant));
        when(applicationRepository.findByIntakeIdAndApplicantNationalIdNumber(
                intakeProjectionId, "63-123456-A-12")).thenReturn(List.of(application));
        ApplicationProgrammeChoice choice = choice(1, UUID.randomUUID());
        when(choiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(applicationId))
                .thenReturn(List.of(choice));

        ApplicationDuplicateCheckService.DuplicateCheckResult result = service.check(application);

        assertThat(result.passed()).isFalse();
        assertThat(result.summary()).contains("National ID is linked to another applicant account");
    }

    @Test
    void check_shouldReportPassportIntakeRankAndProgrammeDuplicatesTogether() {
        UUID applicationId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID intakeProjectionId = UUID.randomUUID();
        Application application = application(applicationId, applicantId, intakeProjectionId, " 63-123456-A-12 ", " P123456 ");
        Applicant applicationApplicant = application.getApplicant();
        Applicant otherApplicant = mock(Applicant.class);
        when(otherApplicant.getId()).thenReturn(UUID.randomUUID());
        when(applicantRepository.findByNationalIdNumberIgnoreCaseAndDeletedAtIsNull("63-123456-A-12"))
                .thenReturn(Optional.of(applicationApplicant));
        when(applicantRepository.findByPassportNumberIgnoreCaseAndDeletedAtIsNull("P123456"))
                .thenReturn(Optional.of(otherApplicant));
        Application anotherApplication = mock(Application.class);
        when(anotherApplication.getId()).thenReturn(UUID.randomUUID());
        when(applicationRepository.findByIntakeIdAndApplicantNationalIdNumber(
                intakeProjectionId, "63-123456-A-12")).thenReturn(List.of(application, anotherApplication));
        UUID repeatedProgrammeId = UUID.randomUUID();
        ApplicationProgrammeChoice firstChoice = choice(1, repeatedProgrammeId);
        ApplicationProgrammeChoice repeatedChoice = choice(1, repeatedProgrammeId);
        when(choiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(applicationId))
                .thenReturn(List.of(firstChoice, repeatedChoice));

        ApplicationDuplicateCheckService.DuplicateCheckResult result = service.check(application);

        assertThat(result.passed()).isFalse();
        assertThat(result.summary()).contains(
                "Passport number is linked",
                "Another application already exists",
                "ranks contain duplicates",
                "same programme appears more than once");
    }

    @Test
    void check_shouldAcceptMissingIdentityValuesWithoutRunningIdentityQueries() {
        UUID applicationId = UUID.randomUUID();
        Application application = mock(Application.class);
        Applicant applicant = mock(Applicant.class);
        when(application.getId()).thenReturn(applicationId);
        when(application.getApplicant()).thenReturn(applicant);
        when(applicant.getNationalIdNumber()).thenReturn(" ");
        when(choiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(applicationId)).thenReturn(List.of());

        assertThat(service.check(application).passed()).isTrue();
    }

    private Application application(
            UUID applicationId,
            UUID applicantId,
            UUID intakeProjectionId,
            String nationalId,
            String passport) {
        Application application = mock(Application.class);
        Applicant applicant = mock(Applicant.class);
        when(application.getId()).thenReturn(applicationId);
        when(application.getApplicant()).thenReturn(applicant);
        when(application.getIntakeId()).thenReturn(intakeProjectionId);
        when(applicant.getId()).thenReturn(applicantId);
        when(applicant.getNationalIdNumber()).thenReturn(nationalId);
        when(applicant.getPassportNumber()).thenReturn(passport);
        return application;
    }

    private ApplicationProgrammeChoice choice(int rank, UUID programmeId) {
        ApplicationProgrammeChoice choice = mock(ApplicationProgrammeChoice.class);
        when(choice.getChoiceRank()).thenReturn(rank);
        when(choice.getProgrammeId()).thenReturn(programmeId);
        return choice;
    }
}
