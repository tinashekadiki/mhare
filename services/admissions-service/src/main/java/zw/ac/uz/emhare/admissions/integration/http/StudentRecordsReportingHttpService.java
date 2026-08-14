package zw.ac.uz.emhare.admissions.integration.http;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** Consumer-owned Student Records reporting contract. @author Tinashe K */
@HttpExchange(accept = "application/json")
public interface StudentRecordsReportingHttpService {

    @GetExchange("/api/student-records/reporting/admissions-registration-outcomes")
    List<RegistrationOutcome> getAdmissionsRegistrationOutcomes();

    record RegistrationOutcome(
            UUID sourceApplicationId,
            UUID sourceOfferId,
            UUID studentId,
            String studentNumber,
            UUID programmeId,
            String programmeCode,
            String programmeName,
            UUID intakeId,
            String registrationStatus,
            Instant registrationConfirmedAt) {}
}
