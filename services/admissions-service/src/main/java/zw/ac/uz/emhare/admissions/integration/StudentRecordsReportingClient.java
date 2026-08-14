package zw.ac.uz.emhare.admissions.integration;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import zw.ac.uz.emhare.admissions.integration.http.StudentRecordsReportingHttpService;
import zw.ac.uz.emhare.admissions.integration.http.StudentRecordsReportingHttpService.RegistrationOutcome;

/** Fail-explicit access to authoritative Student Records registration outcomes. @author Tinashe K */
@Component
public class StudentRecordsReportingClient {

    private final StudentRecordsReportingHttpService httpService;

    public StudentRecordsReportingClient(StudentRecordsReportingHttpService httpService) {
        this.httpService = httpService;
    }

    public RegistrationOutcomeResult outcomes() {
        try {
            List<RegistrationOutcome> outcomes = httpService.getAdmissionsRegistrationOutcomes();
            return new RegistrationOutcomeResult(true, outcomes == null ? List.of() : List.copyOf(outcomes), null);
        } catch (RestClientException exception) {
            return new RegistrationOutcomeResult(false, List.of(),
                    "Student Records is unavailable; registered totals are withheld rather than estimated.");
        } catch (RuntimeException exception) {
            return new RegistrationOutcomeResult(false, List.of(),
                    "Student Records is unavailable; registered totals are withheld rather than estimated.");
        }
    }

    public record RegistrationOutcomeResult(
            boolean available,
            List<RegistrationOutcome> outcomes,
            String unavailableReason) {}
}
