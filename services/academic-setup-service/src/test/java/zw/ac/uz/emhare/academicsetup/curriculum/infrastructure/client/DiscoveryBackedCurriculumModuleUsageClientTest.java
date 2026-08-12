package zw.ac.uz.emhare.academicsetup.curriculum.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import zw.ac.uz.emhare.academicsetup.curriculum.infrastructure.client.AssessmentResultsUsageHttpService.ResultUsage;
import zw.ac.uz.emhare.academicsetup.curriculum.infrastructure.client.StudentRecordsUsageHttpService.RegistrationUsage;
import zw.ac.uz.emhare.common.web.ServiceDependencyUnavailableException;

/** @author Tinashe K */
class DiscoveryBackedCurriculumModuleUsageClientTest {

    private final StudentRecordsUsageHttpService studentRecords = mock(StudentRecordsUsageHttpService.class);
    private final AssessmentResultsUsageHttpService assessmentResults = mock(AssessmentResultsUsageHttpService.class);
    private final DiscoveryBackedCurriculumModuleUsageClient client =
            new DiscoveryBackedCurriculumModuleUsageClient(studentRecords, assessmentResults);

    @Test
    void combinesProviderUsageWithoutInventingFallbackData() {
        UUID curriculumModuleId = UUID.randomUUID();
        when(studentRecords.getUsage(curriculumModuleId)).thenReturn(new RegistrationUsage(curriculumModuleId, 2));
        when(assessmentResults.getUsage(curriculumModuleId)).thenReturn(new ResultUsage(curriculumModuleId, 3));

        var usage = client.usage(curriculumModuleId);

        assertThat(usage.registrationCount()).isEqualTo(2);
        assertThat(usage.resultCount()).isEqualTo(3);
        assertThat(usage.removable()).isFalse();
    }

    @Test
    void translatesAnOpenCircuitIntoDependencyUnavailable() {
        UUID curriculumModuleId = UUID.randomUUID();
        RuntimeException openCircuit = new NoFallbackAvailableException(
                "student-records circuit is open", new IllegalStateException("open"));
        when(studentRecords.getUsage(curriculumModuleId)).thenThrow(openCircuit);

        assertThatThrownBy(() -> client.usage(curriculumModuleId))
                .isInstanceOf(ServiceDependencyUnavailableException.class)
                .hasCause(openCircuit)
                .hasMessageContaining("Removal is blocked");
    }
}
