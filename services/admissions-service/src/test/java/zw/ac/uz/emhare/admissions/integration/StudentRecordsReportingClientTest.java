package zw.ac.uz.emhare.admissions.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import zw.ac.uz.emhare.admissions.integration.http.StudentRecordsReportingHttpService;

/** @author Tinashe K */
class StudentRecordsReportingClientTest {

    private final StudentRecordsReportingHttpService httpService = mock(StudentRecordsReportingHttpService.class);
    private final StudentRecordsReportingClient client = new StudentRecordsReportingClient(httpService);

    @Test
    void returnsAnImmutableEmptyOutcomeWhenStudentRecordsReturnsNull() {
        when(httpService.getAdmissionsRegistrationOutcomes()).thenReturn(null);

        StudentRecordsReportingClient.RegistrationOutcomeResult result = client.outcomes();

        assertThat(result.available()).isTrue();
        assertThat(result.outcomes()).isEmpty();
        assertThat(result.unavailableReason()).isNull();
    }

    @Test
    void copiesSuccessfulOutcomes() {
        StudentRecordsReportingHttpService.RegistrationOutcome outcome = mock(
                StudentRecordsReportingHttpService.RegistrationOutcome.class);
        when(httpService.getAdmissionsRegistrationOutcomes()).thenReturn(List.of(outcome));

        assertThat(client.outcomes().outcomes()).containsExactly(outcome);
    }

    @Test
    void withholdsTotalsForHttpAndUnexpectedRuntimeFailures() {
        when(httpService.getAdmissionsRegistrationOutcomes())
                .thenThrow(new RestClientException("offline"))
                .thenThrow(new IllegalStateException("invalid response"));

        assertUnavailable(client.outcomes());
        assertUnavailable(client.outcomes());
    }

    private static void assertUnavailable(StudentRecordsReportingClient.RegistrationOutcomeResult result) {
        assertThat(result.available()).isFalse();
        assertThat(result.outcomes()).isEmpty();
        assertThat(result.unavailableReason()).contains("withheld rather than estimated");
    }
}
