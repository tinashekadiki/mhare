package zw.ac.uz.emhare.academicsetup.application;

import zw.ac.uz.emhare.academicsetup.infrastructure.persistence.CurriculumModuleRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicModule;
import zw.ac.uz.emhare.academicsetup.domain.model.CurriculumModule;
import zw.ac.uz.emhare.academicsetup.domain.model.CurriculumModuleType;
import zw.ac.uz.emhare.academicsetup.domain.model.ProgrammeVersion;
import zw.ac.uz.emhare.academicsetup.domain.model.ProgrammeVersionStatus;
import zw.ac.uz.emhare.academicsetup.curriculum.application.port.CurriculumModuleUsagePort;
import zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.RemoveCurriculumModule;
import zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.CurriculumModuleUsageSummary;
import zw.ac.uz.emhare.common.persistence.EmhareRevisionContext;
import zw.ac.uz.emhare.common.security.EmhareCurrentUser;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;

/** @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class CurriculumModuleAmendmentServiceTest {

    @Mock private CurriculumModuleRepository curriculumModuleRepository;
    @Mock private CurriculumModuleUsagePort curriculumModuleUsagePort;
    @Mock private EmhareCurrentUserResolver currentUserResolver;
    @Mock private ProgrammeVersion programmeVersion;
    @Mock private AcademicModule academicModule;

    private CurriculumModuleAmendmentService service;
    private UUID programmeVersionId;
    private UUID curriculumModuleId;
    private CurriculumModule curriculumModule;

    @BeforeEach
    void setUp() {
        service = new CurriculumModuleAmendmentService(
                curriculumModuleRepository,
                curriculumModuleUsagePort,
                currentUserResolver);
        programmeVersionId = UUID.randomUUID();
        curriculumModuleId = UUID.randomUUID();
        when(programmeVersion.getId()).thenReturn(programmeVersionId);
        when(programmeVersion.getStatus()).thenReturn(ProgrammeVersionStatus.APPROVED);
        curriculumModule = new CurriculumModule(
                programmeVersion,
                academicModule,
                1,
                CurriculumModuleType.COMPULSORY,
                new BigDecimal("12.00"),
                new BigDecimal("50.00"),
                1);
        when(curriculumModuleRepository.findById(curriculumModuleId)).thenReturn(Optional.of(curriculumModule));
    }

    @AfterEach
    void clearRevisionContext() {
        EmhareRevisionContext.clearRequestMetadata();
    }

    @Test
    void remove_shouldDeclineWhenRegisteredStudentsReferenceModule() {
        when(curriculumModuleUsagePort.usage(curriculumModuleId))
                .thenReturn(new CurriculumModuleUsageSummary(curriculumModuleId, 3, 0, false));

        assertThatThrownBy(() -> service.remove(programmeVersionId, curriculumModuleId, removal(0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Module cannot be removed because 3 student registration(s) reference it.");

        verify(curriculumModuleRepository, never()).saveAndFlush(curriculumModule);
    }

    @Test
    void remove_shouldDeclineWhenResultsReferenceModule() {
        when(curriculumModuleUsagePort.usage(curriculumModuleId))
                .thenReturn(new CurriculumModuleUsageSummary(curriculumModuleId, 0, 2, false));

        assertThatThrownBy(() -> service.remove(programmeVersionId, curriculumModuleId, removal(0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Module cannot be removed because 2 result record(s) reference it.");

        verify(curriculumModuleRepository, never()).saveAndFlush(curriculumModule);
    }

    @Test
    void remove_shouldSoftDeleteUnusedModuleAndRetainAuditReason() {
        UUID actorUserId = UUID.randomUUID();
        when(curriculumModuleUsagePort.usage(curriculumModuleId))
                .thenReturn(new CurriculumModuleUsageSummary(curriculumModuleId, 0, 0, true));
        when(currentUserResolver.requireCurrentUser()).thenReturn(new EmhareCurrentUser(
                UUID.randomUUID(), actorUserId, "admin@example.test", "admin", "Admin", Set.of("academic-admin")));
        when(curriculumModuleRepository.saveAndFlush(curriculumModule)).thenAnswer(invocation -> {
            assertThat(EmhareRevisionContext.getReason()).contains("Removed after curriculum committee approval.");
            return invocation.getArgument(0);
        });

        service.remove(programmeVersionId, curriculumModuleId, removal(0));

        assertThat(curriculumModule.isDeleted()).isTrue();
        assertThat(curriculumModule.getDeletedByUserId()).isEqualTo(actorUserId);
        verify(curriculumModuleRepository).saveAndFlush(curriculumModule);
    }

    @Test
    void remove_shouldRejectStaleVersionBeforeRemoteUsageCheck() {
        assertThatThrownBy(() -> service.remove(programmeVersionId, curriculumModuleId, removal(4)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Curriculum Module was changed by another user. Refresh before retrying.");

        verify(curriculumModuleUsagePort, never()).usage(curriculumModuleId);
    }

    private RemoveCurriculumModule removal(long expectedVersion) {
        return new RemoveCurriculumModule("Removed after curriculum committee approval.", expectedVersion);
    }
}
