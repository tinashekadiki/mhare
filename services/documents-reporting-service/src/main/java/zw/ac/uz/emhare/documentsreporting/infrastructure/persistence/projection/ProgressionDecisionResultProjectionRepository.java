package zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection;

import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.ProgressionDecisionResultProjection;

import zw.ac.uz.emhare.documentsreporting.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.*;
import zw.ac.uz.emhare.documentsreporting.upload.domain.model.*;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface ProgressionDecisionResultProjectionRepository
        extends JpaRepository<ProgressionDecisionResultProjection, UUID> {
    List<ProgressionDecisionResultProjection>
            findAllByProgressionDecisionIdAndDeletedAtIsNullOrderByPublishedResultModuleCodeAsc(UUID decisionId);
}
