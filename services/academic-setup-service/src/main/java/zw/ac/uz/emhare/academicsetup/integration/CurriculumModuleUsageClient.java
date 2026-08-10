package zw.ac.uz.emhare.academicsetup.integration;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupViews.CurriculumModuleUsageSummary;

/** Fail-closed usage lookup across Student Records and Assessment/Results. @author Tinashe K */
@Component
public class CurriculumModuleUsageClient {

    private final RestClient studentRecordsClient;
    private final RestClient assessmentResultsClient;

    public CurriculumModuleUsageClient(
            RestClient.Builder restClientBuilder,
            @Value("${emhare.student-records.url:http://localhost:8085}") String studentRecordsUrl,
            @Value("${emhare.assessment-results.url:http://localhost:8086}") String assessmentResultsUrl) {
        studentRecordsClient = restClientBuilder.clone().baseUrl(studentRecordsUrl).build();
        assessmentResultsClient = restClientBuilder.clone().baseUrl(assessmentResultsUrl).build();
    }

    public CurriculumModuleUsageSummary usage(UUID curriculumModuleId) {
        try {
            RegistrationUsage registrationUsage = studentRecordsClient.get()
                    .uri("/api/student-records/curriculum-module-usage/{curriculumModuleId}", curriculumModuleId)
                    .headers(headers -> headers.setBearerAuth(currentBearerToken()))
                    .retrieve()
                    .body(RegistrationUsage.class);
            ResultUsage resultUsage = assessmentResultsClient.get()
                    .uri("/api/results/curriculum-module-usage/{curriculumModuleId}", curriculumModuleId)
                    .headers(headers -> headers.setBearerAuth(currentBearerToken()))
                    .retrieve()
                    .body(ResultUsage.class);
            if (registrationUsage == null || resultUsage == null) {
                throw new IllegalStateException("Curriculum Module usage could not be verified. Removal is blocked.");
            }
            return new CurriculumModuleUsageSummary(
                    curriculumModuleId,
                    registrationUsage.registrationCount(),
                    resultUsage.resultCount(),
                    registrationUsage.registrationCount() == 0 && resultUsage.resultCount() == 0);
        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Curriculum Module usage could not be verified. Removal is blocked until Student Records and Results are available.",
                    exception);
        }
    }

    private String currentBearerToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken().getTokenValue();
        }
        throw new IllegalStateException("JWT authentication is required to verify curriculum Module usage.");
    }

    private record RegistrationUsage(UUID curriculumModuleId, long registrationCount) {
    }

    private record ResultUsage(UUID curriculumModuleId, long resultCount) {
    }
}
