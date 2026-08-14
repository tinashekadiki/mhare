package zw.ac.uz.emhare.studentrecords.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.studentrecords.reporting.infrastructure.persistence.AdmissionsRegistrationReportingRepository;

/** @author Tinashe K */
class AdmissionsRegistrationReportingServiceTest {

    @Test
    void returnsConfirmedRegistrationOutcomesUsingAdmissionsSourceIdentity() {
        AdmissionsRegistrationReportingRepository repository = mock(AdmissionsRegistrationReportingRepository.class);
        UUID applicationId = UUID.randomUUID();
        AdmissionsRegistrationOutcome outcome = new AdmissionsRegistrationOutcome(
                applicationId, UUID.randomUUID(), UUID.randomUUID(), "R260001A",
                UUID.randomUUID(), "HCS", "Computer Science", UUID.randomUUID(),
                "CONFIRMED", Instant.parse("2026-08-14T08:00:00Z"));
        when(repository.findOutcomes()).thenReturn(List.of(outcome));

        AdmissionsRegistrationReportingService service = new AdmissionsRegistrationReportingService(repository);

        assertThat(service.outcomes()).containsExactly(outcome);
        assertThat(service.outcomes().getFirst().sourceApplicationId()).isEqualTo(applicationId);
    }
}
