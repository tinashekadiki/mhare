package zw.ac.uz.emhare.admissions.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Duplicate-evidence clearance regressions. @author Tinashe K */
class ApplicationClearanceTest {

    @Test
    void confirmedClearance_shouldExposeTrimmedDuplicateEvidence() {
        ApplicationClearance clearance = new ApplicationClearance(
                mock(Application.class), UUID.randomUUID(), "All gates passed.", "  Identity checks passed.  ", Instant.now());

        assertThat(clearance.isDuplicateChecksPassed()).isTrue();
        assertThat(clearance.getDuplicateCheckSummary()).isEqualTo("Identity checks passed.");
    }

    @Test
    void confirmedClearance_shouldRejectMissingDuplicateEvidence() {
        assertThatThrownBy(() -> new ApplicationClearance(
                mock(Application.class), UUID.randomUUID(), "All gates passed.", null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApplicationClearance(
                mock(Application.class), UUID.randomUUID(), "All gates passed.", " ", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate-check evidence is required.");
    }
}
