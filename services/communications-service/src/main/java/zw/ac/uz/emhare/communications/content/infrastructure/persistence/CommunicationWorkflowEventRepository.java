package zw.ac.uz.emhare.communications.content.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationWorkflowEvent;

/** Append-only editorial workflow persistence. @author Tinashe K */
public interface CommunicationWorkflowEventRepository
    extends JpaRepository<CommunicationWorkflowEvent, UUID> {

  List<CommunicationWorkflowEvent> findAllByVersionIdOrderByOccurredAtAsc(UUID versionId);
}
