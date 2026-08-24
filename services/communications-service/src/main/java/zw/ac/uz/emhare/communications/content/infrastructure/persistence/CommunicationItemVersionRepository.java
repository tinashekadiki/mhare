package zw.ac.uz.emhare.communications.content.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationItemVersion;

/** Communications version persistence. @author Tinashe K */
public interface CommunicationItemVersionRepository
    extends JpaRepository<CommunicationItemVersion, UUID> {

  Optional<CommunicationItemVersion> findTopByItemIdOrderByVersionNumberDesc(UUID itemId);

  List<CommunicationItemVersion> findAllByItemIdOrderByVersionNumberDesc(UUID itemId);
}
