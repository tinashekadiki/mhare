package zw.ac.uz.emhare.communications.content.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.communications.content.domain.model.EventOccurrence;

/** Editorial event occurrence persistence. @author Tinashe K */
public interface EventOccurrenceRepository extends JpaRepository<EventOccurrence, UUID> {

  Optional<EventOccurrence> findByCommunicationVersionId(UUID communicationVersionId);

  List<EventOccurrence> findAllByCommunicationVersionIdIn(Collection<UUID> versionIds);
}
