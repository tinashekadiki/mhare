package zw.ac.uz.emhare.academicsetup.domain;

import zw.ac.uz.emhare.academicsetup.domain.model.AcademicModule;
import zw.ac.uz.emhare.academicsetup.domain.model.CurriculumModule;
import zw.ac.uz.emhare.academicsetup.domain.model.CurriculumModuleType;
import zw.ac.uz.emhare.academicsetup.domain.model.ProgrammeVersion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** @author Tinashe K */
class CurriculumModuleTest {

    @Test
    void updatePlacement_shouldAllowGovernedAmendmentAtExpectedVersion() {
        CurriculumModule curriculumModule = curriculumModule();

        curriculumModule.updatePlacement(
                4,
                CurriculumModuleType.ELECTIVE,
                new BigDecimal("15.00"),
                new BigDecimal("45.00"),
                7,
                0);

        assertThat(curriculumModule.getPeriodNumber()).isEqualTo(4);
        assertThat(curriculumModule.getModuleType()).isEqualTo(CurriculumModuleType.ELECTIVE);
        assertThat(curriculumModule.getCreditValue()).isEqualByComparingTo("15.00");
        assertThat(curriculumModule.getMinimumMarkRequired()).isEqualByComparingTo("45.00");
        assertThat(curriculumModule.getSortOrder()).isEqualTo(7);
    }

    @Test
    void updatePlacement_shouldRejectStaleVersion() {
        CurriculumModule curriculumModule = curriculumModule();

        assertThatThrownBy(() -> curriculumModule.updatePlacement(
                2, CurriculumModuleType.COMPULSORY, new BigDecimal("12.00"), null, 2, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Curriculum Module was changed by another user. Refresh before retrying.");
    }

    private CurriculumModule curriculumModule() {
        return new CurriculumModule(
                Mockito.mock(ProgrammeVersion.class),
                Mockito.mock(AcademicModule.class),
                1,
                CurriculumModuleType.COMPULSORY,
                new BigDecimal("12.00"),
                new BigDecimal("50.00"),
                1);
    }
}
