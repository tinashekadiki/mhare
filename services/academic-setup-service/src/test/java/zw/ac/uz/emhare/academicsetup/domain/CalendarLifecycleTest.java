package zw.ac.uz.emhare.academicsetup.domain;

import zw.ac.uz.emhare.academicsetup.domain.model.AcademicPeriod;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicPeriodType;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicYear;
import zw.ac.uz.emhare.academicsetup.domain.model.CalendarStatus;
import zw.ac.uz.emhare.academicsetup.domain.model.Intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** @author Tinashe K */
class CalendarLifecycleTest {

    private final AcademicYear academicYear = new AcademicYear(
            "2028 Academic Year", LocalDate.parse("2028-01-01"), LocalDate.parse("2028-12-31"));
    private final AcademicPeriodType semester = new AcademicPeriodType("SEM", "Semester", 1);

    @Test
    void academicPeriodFollowsDraftOpenClosedLifecycle() {
        AcademicPeriod period = new AcademicPeriod(
                academicYear, semester, "2028-S1", "Semester 1",
                LocalDate.parse("2028-01-15"), LocalDate.parse("2028-06-30"));

        period.open(0);
        period.close(0);

        assertThat(period.getStatus()).isEqualTo(CalendarStatus.CLOSED);
    }

    @Test
    void intakeFollowsDraftOpenClosedLifecycle() {
        Intake intake = new Intake(
                academicYear, "JAN-2028", "January 2028 Intake",
                LocalDate.parse("2028-01-01"), LocalDate.parse("2028-03-31"));

        intake.open(0);
        intake.close(0);

        assertThat(intake.getStatus()).isEqualTo(CalendarStatus.CLOSED);
    }

    @Test
    void academicPeriodCannotSkipOrRepeatLifecycleTransitions() {
        AcademicPeriod period = new AcademicPeriod(
                academicYear, semester, "2028-S1", "Semester 1",
                LocalDate.parse("2028-01-15"), LocalDate.parse("2028-06-30"));

        assertThatThrownBy(() -> period.close(0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only an open academic period can be closed.");
        period.open(0);
        assertThatThrownBy(() -> period.open(0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only a draft academic period can be opened.");
    }

    @Test
    void staleVersionPreventsCalendarTransition() {
        Intake intake = new Intake(
                academicYear, "JAN-2028", "January 2028 Intake",
                LocalDate.parse("2028-01-01"), LocalDate.parse("2028-03-31"));

        assertThatThrownBy(() -> intake.open(7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Intake was changed by another user. Refresh before retrying.");
        assertThat(intake.getStatus()).isEqualTo(CalendarStatus.DRAFT);
    }

    @Test
    void openIntakeAllowsAuditedNameAndDateCorrectionWithoutIdentityChange() {
        Intake intake = new Intake(
                academicYear, "JAN-2028", "January 2028 Intake",
                LocalDate.parse("2028-01-01"), LocalDate.parse("2028-03-31"));
        intake.open(0);

        intake.update(
                academicYear,
                "JAN-2028",
                "January and February 2028 Intake",
                LocalDate.parse("2028-01-02"),
                LocalDate.parse("2028-03-15"),
                "Corrected the approved application window.",
                0);

        assertThat(intake.getStatus()).isEqualTo(CalendarStatus.OPEN);
        assertThat(intake.getName()).isEqualTo("January and February 2028 Intake");
        assertThat(intake.getEndsOn()).isEqualTo(LocalDate.parse("2028-03-15"));
        assertThat(intake.getChangeReason()).isEqualTo("Corrected the approved application window.");
    }

    @Test
    void openIntakeRejectsIdentityChange() {
        Intake intake = new Intake(
                academicYear, "JAN-2028", "January 2028 Intake",
                LocalDate.parse("2028-01-01"), LocalDate.parse("2028-03-31"));
        intake.open(0);

        assertThatThrownBy(() -> intake.update(
                academicYear,
                "FEB-2028",
                intake.getName(),
                intake.getStartsOn(),
                intake.getEndsOn(),
                "Attempted to replace operational identity.",
                0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("An open or closed intake cannot change academic year or code.");
    }

    @Test
    void calendarEditRequiresMeaningfulReason() {
        assertThatThrownBy(() -> academicYear.update(
                "2028 Revised", academicYear.getStartDate(), academicYear.getEndDate(), "typo", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Academic calendar change reason must contain at least 10 characters.");
    }
}
