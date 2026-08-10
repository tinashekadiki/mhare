package zw.ac.uz.emhare.documentsreporting.projection;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface ProgressionDecisionResultProjectionRepository
        extends JpaRepository<ProgressionDecisionResultProjection, UUID> {
    List<ProgressionDecisionResultProjection>
            findAllByProgressionDecisionIdAndDeletedAtIsNullOrderByPublishedResultModuleCodeAsc(UUID decisionId);
}
