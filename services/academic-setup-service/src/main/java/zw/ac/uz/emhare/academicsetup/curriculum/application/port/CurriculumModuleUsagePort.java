package zw.ac.uz.emhare.academicsetup.curriculum.application.port;

import java.util.UUID;
import zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.CurriculumModuleUsageSummary;

/** Resolves whether a curriculum Module is already used outside Academic Setup. @author Tinashe K */
public interface CurriculumModuleUsagePort {

    CurriculumModuleUsageSummary usage(UUID curriculumModuleId);
}
