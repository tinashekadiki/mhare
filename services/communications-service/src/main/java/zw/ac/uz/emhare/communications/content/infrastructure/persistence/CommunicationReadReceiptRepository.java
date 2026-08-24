package zw.ac.uz.emhare.communications.content.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationReadReceipt;

/** Authenticated read receipt persistence. @author Tinashe K */
public interface CommunicationReadReceiptRepository
    extends JpaRepository<CommunicationReadReceipt, UUID> {

  Optional<CommunicationReadReceipt> findByPublicationIdAndReaderUserId(
      UUID publicationId, UUID readerUserId);
}
