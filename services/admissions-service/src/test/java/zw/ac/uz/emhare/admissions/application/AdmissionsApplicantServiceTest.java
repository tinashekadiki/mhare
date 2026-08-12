package zw.ac.uz.emhare.admissions.application;

import zw.ac.uz.emhare.admissions.domain.model.Applicant;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantRepository;

import zw.ac.uz.emhare.admissions.application.command.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.common.persistence.EmhareRevisionContext;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationRepository;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationStatus;

@ExtendWith(MockitoExtension.class)
class AdmissionsApplicantServiceTest {

    @Mock
    private ApplicantRepository applicantRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private AdmissionsApplicationService admissionsApplicationService;

    private AdmissionsApplicantService admissionsApplicantService;

    @BeforeEach
    void setUp() {
        admissionsApplicantService = new AdmissionsApplicantService(
                applicantRepository,
                applicationRepository,
                admissionsApplicationService);
    }

    @AfterEach
    void clearRevisionContext() {
        EmhareRevisionContext.clearRequestMetadata();
    }

    @Test
    void listApplicants_shouldReturnDistinctProfileRowsWithServerPagination() {
        Applicant applicant = applicant();
        when(applicantRepository.findRegisterPage(
                org.mockito.ArgumentMatchers.eq("nyasha"),
                org.mockito.ArgumentMatchers.eq("LOCAL"),
                org.mockito.ArgumentMatchers.eq(ApplicationStatus.SUBMITTED),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(applicant)));
        when(applicationRepository.findAllByApplicantIdInAndDeletedAtIsNullOrderByCreatedAtDesc(List.of(applicant.getId())))
                .thenReturn(List.of());

        var page = admissionsApplicantService.listApplicants(" nyasha ", "local", "submitted", 0, 25);

        assertEquals(1, page.totalElements());
        assertEquals("APP-0001", page.content().getFirst().applicantNumber());
        assertEquals(30, page.content().getFirst().profileCompletenessPercentage());
        assertEquals(0, page.content().getFirst().applicationCount());
    }

    @Test
    void correctApplicant_shouldRequireExpectedVersionAndAttachReasonToEnversRevision() {
        Applicant applicant = applicant();
        UpdateApplicantProfileCommand command = command(0);
        when(applicantRepository.findByIdAndDeletedAtIsNull(applicant.getId())).thenReturn(Optional.of(applicant));
        when(applicantRepository.saveAndFlush(applicant)).thenAnswer(invocation -> {
            assertEquals("Corrected against verified identity document.", EmhareRevisionContext.getReason().orElseThrow());
            return invocation.getArgument(0);
        });
        when(admissionsApplicationService.listApplicationsForApplicantRecord(applicant.getId())).thenReturn(List.of());

        var details = admissionsApplicantService.correctApplicant(applicant.getId(), command);

        assertEquals("Nyasha-Rose", details.profile().firstName());
        assertEquals("nyasha@example.test", details.profile().primaryEmail());
        assertEquals(100, details.profile().completenessPercentage());
        verify(applicantRepository).saveAndFlush(applicant);
        assertEquals(Optional.empty(), EmhareRevisionContext.getReason());
    }

    @Test
    void correctApplicant_shouldRejectStaleProfileVersion() {
        Applicant applicant = applicant();
        when(applicantRepository.findByIdAndDeletedAtIsNull(applicant.getId())).thenReturn(Optional.of(applicant));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> admissionsApplicantService.correctApplicant(applicant.getId(), command(3)));

        assertEquals("Applicant profile was changed by another user. Refresh before retrying.", exception.getMessage());
    }

    @Test
    void listApplicants_shouldRejectUnsupportedApplicationStatus() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> admissionsApplicantService.listApplicants("", null, "UNKNOWN", 0, 10));

        assertEquals("Unsupported application status: UNKNOWN", exception.getMessage());
    }

    private Applicant applicant() {
        Applicant applicant = new Applicant(
                UUID.randomUUID(), "APP-0001", "LOCAL", "Nyasha", "Moyo", "NYASHA@EXAMPLE.TEST");
        ReflectionTestUtils.setField(applicant, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(applicant, "createdAt", Instant.parse("2026-08-08T10:00:00Z"));
        ReflectionTestUtils.setField(applicant, "updatedAt", Instant.parse("2026-08-08T10:00:00Z"));
        return applicant;
    }

    private UpdateApplicantProfileCommand command(long expectedVersion) {
        return new UpdateApplicantProfileCommand(
                "LOCAL",
                "MS",
                "Nyasha-Rose",
                null,
                "Moyo",
                LocalDate.parse("2003-04-12"),
                "FEMALE",
                "SINGLE",
                "63-123456-A-78",
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Harare",
                "NONE",
                null,
                "SELF",
                "NYASHA@EXAMPLE.TEST",
                "+263771234567",
                "P.O. Box 1, Harare",
                "1 College Road, Harare",
                "Corrected against verified identity document.",
                expectedVersion);
    }
}
