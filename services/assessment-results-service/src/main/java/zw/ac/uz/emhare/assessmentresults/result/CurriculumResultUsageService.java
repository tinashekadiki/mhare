package zw.ac.uz.emhare.assessmentresults.result;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authoritative result usage check for Academic Setup curriculum amendments. @author Tinashe K */
@Service
public class CurriculumResultUsageService {

    private final ModuleResultRepository moduleResultRepository;

    public CurriculumResultUsageService(ModuleResultRepository moduleResultRepository) {
        this.moduleResultRepository = moduleResultRepository;
    }

    @Transactional(readOnly = true)
    public CurriculumResultUsageSummary usage(UUID curriculumModuleId) {
        return new CurriculumResultUsageSummary(
                curriculumModuleId,
                moduleResultRepository.countByRosterEntryCurriculumModuleIdAndDeletedAtIsNull(curriculumModuleId));
    }

    public record CurriculumResultUsageSummary(UUID curriculumModuleId, long resultCount) {
    }
}
