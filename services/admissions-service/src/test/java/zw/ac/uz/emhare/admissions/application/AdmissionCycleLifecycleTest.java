package zw.ac.uz.emhare.admissions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** @author Tinashe K */
class AdmissionCycleLifecycleTest {

    @Test
    void academicIntakeReopeningRestoresTheCompatibilityProjectionApplicationWindow() {
        Instant now = Instant.parse("2026-08-10T08:00:00Z");
        AdmissionCycle projection = new AdmissionCycle(
                UUID.randomUUID(), UUID.randomUUID(), "AUG-2026", "August 2026",
                now.minusSeconds(3600), now.plusSeconds(3600));

        projection.open(now);
        projection.closeApplications();
        projection.beginSelection();
        projection.synchronizeOpenApplicationWindow();

        assertThat(projection.getStatus()).isEqualTo(AdmissionCycleStatus.OPEN);
        assertThat(projection.isAcceptingApplicationsAt(now)).isTrue();
    }

    private final Instant opensAt = Instant.now().minus(1, ChronoUnit.DAYS);
    private final Instant closesAt = Instant.now().plus(30, ChronoUnit.DAYS);

    @Test
    void cycleFollowsFullLifecycleThroughToArchive() {
        AdmissionCycle cycle = new AdmissionCycle(
                UUID.randomUUID(), UUID.randomUUID(), "JAN27", "January 2027 Cycle", opensAt, closesAt);

        cycle.open(Instant.now());
        assertThat(cycle.getStatus()).isEqualTo(AdmissionCycleStatus.OPEN);

        cycle.closeApplications();
        cycle.beginSelection();
        cycle.beginOffers();
        cycle.complete();
        assertThat(cycle.getStatus()).isEqualTo(AdmissionCycleStatus.COMPLETED);

        cycle.archive();
        assertThat(cycle.getStatus()).isEqualTo(AdmissionCycleStatus.ARCHIVED);
    }

    @Test
    void cycleCannotBeArchivedBeforeCompletion() {
        AdmissionCycle cycle = new AdmissionCycle(
                UUID.randomUUID(), UUID.randomUUID(), "JAN27", "January 2027 Cycle", opensAt, closesAt);
        cycle.open(Instant.now());

        assertThatThrownBy(cycle::archive)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only a completed admission cycle can be archived.");
    }

    @Test
    void cycleCannotOpenOutsideItsConfiguredDateRange() {
        AdmissionCycle cycle = new AdmissionCycle(
                UUID.randomUUID(), UUID.randomUUID(), "JAN27", "January 2027 Cycle", opensAt, closesAt);

        assertThatThrownBy(() -> cycle.open(closesAt.plus(1, ChronoUnit.DAYS)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Admission cycle cannot be opened outside its configured date range.");
    }

    @Test
    void configuringMaximumProgrammeChoicesRejectsNonPositiveValues() {
        AdmissionCycle cycle = new AdmissionCycle(
                UUID.randomUUID(), UUID.randomUUID(), "JAN27", "January 2027 Cycle", opensAt, closesAt);

        assertThatThrownBy(() -> cycle.configureMaximumProgrammeChoices(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum programme choices must be at least 1.");

        cycle.configureMaximumProgrammeChoices(5);
        assertThat(cycle.getMaximumProgrammeChoices()).isEqualTo(5);
    }

    @Test
    void draftCycleCanBeCorrectedWithVersionAndAuditReason() {
        AdmissionCycle cycle = new AdmissionCycle(
                UUID.randomUUID(), UUID.randomUUID(), "JAN27", "January 2027 Cycle", opensAt, closesAt);
        UUID revisedAcademicYearId = UUID.randomUUID();
        UUID revisedIntakeId = UUID.randomUUID();
        Instant revisedOpening = opensAt.plus(2, ChronoUnit.DAYS);
        Instant revisedClosing = closesAt.plus(2, ChronoUnit.DAYS);

        cycle.update(
                revisedAcademicYearId,
                revisedIntakeId,
                " AUG27 ",
                " August 2027 Cycle ",
                revisedOpening,
                revisedClosing,
                4,
                null,
                "Corrected the approved intake planning dates.",
                0);

        assertThat(cycle.getAcademicYearId()).isEqualTo(revisedAcademicYearId);
        assertThat(cycle.getIntakeId()).isEqualTo(revisedIntakeId);
        assertThat(cycle.getCode()).isEqualTo("AUG27");
        assertThat(cycle.getName()).isEqualTo("August 2027 Cycle");
        assertThat(cycle.getOpensAt()).isEqualTo(revisedOpening);
        assertThat(cycle.getClosesAt()).isEqualTo(revisedClosing);
        assertThat(cycle.getMaximumProgrammeChoices()).isEqualTo(4);
        assertThat(cycle.getChangeReason()).isEqualTo("Corrected the approved intake planning dates.");
    }

    @Test
    void cycleEditRejectsStaleVersionAndNonDraftLifecycleState() {
        AdmissionCycle cycle = new AdmissionCycle(
                UUID.randomUUID(), UUID.randomUUID(), "JAN27", "January 2027 Cycle", opensAt, closesAt);

        assertThatThrownBy(() -> cycle.update(
                UUID.randomUUID(), UUID.randomUUID(), "AUG27", "August 2027 Cycle",
                opensAt, closesAt, 3, null, "Corrected the admissions planning window.", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Admission cycle was changed by another user. Refresh before retrying.");

        cycle.open(Instant.now());
        assertThatThrownBy(() -> cycle.update(
                UUID.randomUUID(), UUID.randomUUID(), "AUG27", "August 2027 Cycle",
                opensAt, closesAt, 3, null, "Corrected the admissions planning window.", 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only a draft admission cycle can be edited.");
    }
}
