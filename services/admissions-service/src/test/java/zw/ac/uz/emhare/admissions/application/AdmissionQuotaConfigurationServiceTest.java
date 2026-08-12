package zw.ac.uz.emhare.admissions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.ac.uz.emhare.admissions.api.model.ConfigureAdmissionQuotasRequest;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionQuota;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionQuotaRepository;

/** @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class AdmissionQuotaConfigurationServiceTest {
    @Mock private AdmissionQuotaRepository repository;

    @Test
    void replacesPlanningQuotasAndSoftDeletesRemovedScopes() {
        UUID intakeId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        AdmissionQuota retained = quota(intakeId, "MBA", 60, 5);
        AdmissionQuota removed = quota(intakeId, "BSC", 120, 0);
        when(repository.findAllByIntakeIdAndDeletedAtIsNullOrderByProgrammeCodeAscQuotaTypeCodeAsc(intakeId))
                .thenReturn(List.of(retained, removed))
                .thenReturn(List.of(retained));
        when(repository.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AdmissionQuotaConfigurationService service = new AdmissionQuotaConfigurationService(repository);

        List<AdmissionQuotaSummary> result = service.configure(
                intakeId,
                actorId,
                request(List.of(input(retained.getProgrammeId(), "MBA", 80, 10, retained.getVersion()))));

        assertThat(result).singleElement().satisfies(summary -> {
            assertThat(summary.capacity()).isEqualTo(80);
            assertThat(summary.reservedCapacity()).isEqualTo(10);
        });
        assertThat(removed.isDeleted()).isTrue();
        assertThat(removed.getDeletedByUserId()).isEqualTo(actorId);
        verify(repository).saveAllAndFlush(any());
    }

    @Test
    void rejectsDuplicateProgrammeQuotaScopes() {
        UUID intakeId = UUID.randomUUID();
        UUID programmeId = UUID.randomUUID();
        AdmissionQuotaConfigurationService service = new AdmissionQuotaConfigurationService(repository);

        assertThatThrownBy(() -> service.configure(
                intakeId,
                UUID.randomUUID(),
                request(List.of(
                        input(programmeId, "MBA", 60, 0, 0),
                        input(programmeId, "MBA", 70, 0, 0)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Programme quota scopes must be distinct within an intake.");
    }

    @Test
    void rejectsReservedCapacityAboveTotalCapacity() {
        assertThatThrownBy(() -> quota(UUID.randomUUID(), "MBA", 20, 21))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Reserved Programme capacity must be between zero and total capacity.");
    }

    private ConfigureAdmissionQuotasRequest request(List<ConfigureAdmissionQuotasRequest.QuotaInput> quotas) {
        return new ConfigureAdmissionQuotasRequest(quotas, "Configured through the guided intake opening workflow.");
    }

    private ConfigureAdmissionQuotasRequest.QuotaInput input(
            UUID programmeId, String programmeCode, int capacity, int reservedCapacity, long version) {
        return new ConfigureAdmissionQuotasRequest.QuotaInput(
                programmeId, programmeCode, programmeCode + " Programme", "GENERAL", capacity, reservedCapacity, version);
    }

    private AdmissionQuota quota(UUID intakeId, String programmeCode, int capacity, int reservedCapacity) {
        return new AdmissionQuota(
                intakeId, UUID.randomUUID(), programmeCode, programmeCode + " Programme", "GENERAL",
                capacity, reservedCapacity);
    }
}
