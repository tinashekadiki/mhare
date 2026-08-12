package zw.ac.uz.emhare.academicsetup.curriculum.infrastructure.client;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import zw.ac.uz.emhare.common.web.ServiceDependencyUnavailableException;
import zw.ac.uz.emhare.academicsetup.curriculum.application.port.CurriculumModuleUsagePort;
import zw.ac.uz.emhare.academicsetup.curriculum.infrastructure.client.AssessmentResultsUsageHttpService.ResultUsage;
import zw.ac.uz.emhare.academicsetup.curriculum.infrastructure.client.StudentRecordsUsageHttpService.RegistrationUsage;
import zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.CurriculumModuleUsageSummary;

/** Fail-closed discovery-backed usage adapter. @author Tinashe K */
@Component
public class DiscoveryBackedCurriculumModuleUsageClient implements CurriculumModuleUsagePort {

    private final StudentRecordsUsageHttpService studentRecordsUsageHttpService;
    private final AssessmentResultsUsageHttpService assessmentResultsUsageHttpService;

    public DiscoveryBackedCurriculumModuleUsageClient(
            StudentRecordsUsageHttpService studentRecordsUsageHttpService,
            AssessmentResultsUsageHttpService assessmentResultsUsageHttpService) {
        this.studentRecordsUsageHttpService = studentRecordsUsageHttpService;
        this.assessmentResultsUsageHttpService = assessmentResultsUsageHttpService;
    }

    @Override
    public CurriculumModuleUsageSummary usage(UUID curriculumModuleId) {
        try {
            RegistrationUsage registrationUsage = studentRecordsUsageHttpService.getUsage(curriculumModuleId);
            ResultUsage resultUsage = assessmentResultsUsageHttpService.getUsage(curriculumModuleId);
            if (registrationUsage == null || resultUsage == null) {
                throw unavailable(null);
            }
            return new CurriculumModuleUsageSummary(
                    curriculumModuleId,
                    registrationUsage.registrationCount(),
                    resultUsage.resultCount(),
                    registrationUsage.registrationCount() == 0 && resultUsage.resultCount() == 0);
        } catch (ServiceDependencyUnavailableException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable(exception);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private ServiceDependencyUnavailableException unavailable(Throwable cause) {
        return new ServiceDependencyUnavailableException(
                "Curriculum Module usage could not be verified. Removal is blocked until Student Records and Results are available.",
                cause);
    }
}
