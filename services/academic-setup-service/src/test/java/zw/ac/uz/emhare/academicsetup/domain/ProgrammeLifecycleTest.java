package zw.ac.uz.emhare.academicsetup.domain;

import zw.ac.uz.emhare.academicsetup.domain.model.AcademicUnit;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicUnitType;
import zw.ac.uz.emhare.academicsetup.domain.model.Programme;
import zw.ac.uz.emhare.academicsetup.domain.model.ProgrammeLevel;
import zw.ac.uz.emhare.academicsetup.domain.model.ProgrammeType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** @author Tinashe K */
class ProgrammeLifecycleTest {

    private final AcademicUnitType leafType = new AcademicUnitType("SCHOOL", "School", 1, true);
    private final AcademicUnit owningUnit = new AcademicUnit(leafType, null, "ENG", "Faculty of Engineering", null, null);
    private final AcademicUnit alternateUnit = new AcademicUnit(leafType, null, "SCI", "Faculty of Science", null, null);
    private final ProgrammeType degree = new ProgrammeType("DEGREE", "Degree");
    private final ProgrammeLevel undergraduate = new ProgrammeLevel("UG", "Undergraduate", 1);

    @Test
    void draftProgrammeAllowsFullCorrectionIncludingCodeAndOwner() {
        Programme programme = new Programme(
                owningUnit, degree, undergraduate, "BSCIT", "Bachelor of Science in IT",
                "Bachelor of Science Honours Degree", 8, 12, null);

        programme.update(
                alternateUnit, degree, undergraduate, "BSCCS", "Bachelor of Science in Computer Science",
                "Bachelor of Science Honours Degree", 8, 12, null,
                "Corrected the programme code and owner before activation.", 0);

        assertThat(programme.getCode()).isEqualTo("BSCCS");
        assertThat(programme.getOwningAcademicUnit()).isEqualTo(alternateUnit);
        assertThat(programme.getChangeReason()).isEqualTo("Corrected the programme code and owner before activation.");
    }

    @Test
    void activeProgrammeRejectsCodeChange() {
        Programme programme = new Programme(
                owningUnit, degree, undergraduate, "BSCIT", "Bachelor of Science in IT",
                "Bachelor of Science Honours Degree", 8, 12, null);
        programme.activate(0);

        assertThatThrownBy(() -> programme.update(
                owningUnit, degree, undergraduate, "BSCCS", programme.getName(),
                programme.getAwardName(), programme.getMinimumDurationPeriods(),
                programme.getMaximumDurationPeriods(), programme.getLegacyProgrammeCode(),
                "Attempted to replace the operational programme code.", 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("A programme that has left draft cannot change owning academic unit or code.");
    }

    @Test
    void activeProgrammeAllowsAuditedCorrectionOfNonIdentityFields() {
        Programme programme = new Programme(
                owningUnit, degree, undergraduate, "BSCIT", "Bachelor of Science in IT",
                "Bachelor of Science Honours Degree", 8, 12, null);
        programme.activate(0);

        programme.update(
                owningUnit, degree, undergraduate, programme.getCode(), "Bachelor of Science in Information Technology",
                "Bachelor of Science Honours Degree", 8, 13, "LEGACY-101",
                "Extended the maximum duration and recorded the legacy code.", 0);

        assertThat(programme.getName()).isEqualTo("Bachelor of Science in Information Technology");
        assertThat(programme.getMaximumDurationPeriods()).isEqualTo(13);
        assertThat(programme.getLegacyProgrammeCode()).isEqualTo("LEGACY-101");
    }

    @Test
    void staleVersionPreventsProgrammeCorrection() {
        Programme programme = new Programme(
                owningUnit, degree, undergraduate, "BSCIT", "Bachelor of Science in IT",
                "Bachelor of Science Honours Degree", 8, 12, null);

        assertThatThrownBy(() -> programme.update(
                owningUnit, degree, undergraduate, programme.getCode(), programme.getName(),
                programme.getAwardName(), 8, 12, null, "Attempted a stale correction.", 7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Programme was changed by another user. Refresh before retrying.");
    }

    @Test
    void programmeCorrectionRequiresMeaningfulReason() {
        Programme programme = new Programme(
                owningUnit, degree, undergraduate, "BSCIT", "Bachelor of Science in IT",
                "Bachelor of Science Honours Degree", 8, 12, null);

        assertThatThrownBy(() -> programme.update(
                owningUnit, degree, undergraduate, programme.getCode(), programme.getName(),
                programme.getAwardName(), 8, 12, null, "typo", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Programme change reason must contain at least 10 characters.");
    }
}
