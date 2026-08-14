package zw.ac.uz.emhare.studentrecords.reporting.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.studentrecords.reporting.AdmissionsRegistrationOutcome;
import zw.ac.uz.emhare.studentrecords.reporting.AdmissionsRegistrationReportingService;

/** @author Tinashe K */
class AdmissionsRegistrationReportingControllerTest {

    @Test
    void returnsAuthoritativeRegistrationOutcomes() {
        AdmissionsRegistrationReportingService service = mock(AdmissionsRegistrationReportingService.class);
        AdmissionsRegistrationOutcome outcome = new AdmissionsRegistrationOutcome(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "R260001A",
                UUID.randomUUID(), "HCS", "Computer Science", UUID.randomUUID(),
                "CONFIRMED", Instant.parse("2026-08-14T08:00:00Z"));
        when(service.outcomes()).thenReturn(List.of(outcome));

        AdmissionsRegistrationReportingController controller =
                new AdmissionsRegistrationReportingController(service);

        assertThat(controller.outcomes()).containsExactly(outcome);
    }
}
