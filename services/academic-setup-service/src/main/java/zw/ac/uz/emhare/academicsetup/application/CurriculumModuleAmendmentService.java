package zw.ac.uz.emhare.academicsetup.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.academicsetup.domain.CurriculumModule;
import zw.ac.uz.emhare.academicsetup.domain.CurriculumModuleRepository;
import zw.ac.uz.emhare.academicsetup.domain.ProgrammeVersionStatus;
import zw.ac.uz.emhare.academicsetup.integration.CurriculumModuleUsageClient;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupCommands.RemoveCurriculumModule;
import zw.ac.uz.emhare.academicsetup.web.AcademicSetupViews.CurriculumModuleUsageSummary;
import zw.ac.uz.emhare.common.persistence.EmhareRevisionContext;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;

/** Governs removal separately because it depends on authoritative cross-service usage evidence. @author Tinashe K */
@Service
public class CurriculumModuleAmendmentService {

    private final CurriculumModuleRepository curriculumModuleRepository;
    private final CurriculumModuleUsageClient curriculumModuleUsageClient;
    private final EmhareCurrentUserResolver currentUserResolver;

    public CurriculumModuleAmendmentService(
            CurriculumModuleRepository curriculumModuleRepository,
            CurriculumModuleUsageClient curriculumModuleUsageClient,
            EmhareCurrentUserResolver currentUserResolver) {
        this.curriculumModuleRepository = curriculumModuleRepository;
        this.curriculumModuleUsageClient = curriculumModuleUsageClient;
        this.currentUserResolver = currentUserResolver;
    }

    @Transactional(readOnly = true)
    public CurriculumModuleUsageSummary usage(UUID programmeVersionId, UUID curriculumModuleId) {
        requireAmendableCurriculumModule(programmeVersionId, curriculumModuleId);
        return curriculumModuleUsageClient.usage(curriculumModuleId);
    }

    @Transactional
    public void remove(
            UUID programmeVersionId,
            UUID curriculumModuleId,
            RemoveCurriculumModule command) {
        CurriculumModule curriculumModule = requireAmendableCurriculumModule(programmeVersionId, curriculumModuleId);
        curriculumModule.requireVersion(command.expectedVersion());
        CurriculumModuleUsageSummary usage = curriculumModuleUsageClient.usage(curriculumModuleId);
        if (usage.registrationCount() > 0) {
            throw new IllegalStateException(
                    "Module cannot be removed because " + usage.registrationCount() + " student registration(s) reference it.");
        }
        if (usage.resultCount() > 0) {
            throw new IllegalStateException(
                    "Module cannot be removed because " + usage.resultCount() + " result record(s) reference it.");
        }
        String correlationId = EmhareRevisionContext.getCorrelationId().orElse(null);
        EmhareRevisionContext.setRequestMetadata(correlationId, command.changeReason().trim());
        try {
            curriculumModule.markDeleted(currentUserResolver.requireCurrentUser().auditUserId());
            curriculumModuleRepository.saveAndFlush(curriculumModule);
        } finally {
            EmhareRevisionContext.setRequestMetadata(correlationId, null);
        }
    }

    private CurriculumModule requireAmendableCurriculumModule(UUID programmeVersionId, UUID curriculumModuleId) {
        CurriculumModule curriculumModule = curriculumModuleRepository.findById(curriculumModuleId)
                .orElseThrow(() -> new IllegalArgumentException("Curriculum Module was not found."));
        if (!curriculumModule.getProgrammeVersion().getId().equals(programmeVersionId)) {
            throw new IllegalArgumentException("Curriculum Module does not belong to the selected programme version.");
        }
        if (curriculumModule.getProgrammeVersion().getStatus() == ProgrammeVersionStatus.RETIRED) {
            throw new IllegalStateException("A retired programme version cannot be amended.");
        }
        return curriculumModule;
    }
}
