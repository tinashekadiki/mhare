package zw.ac.uz.emhare.communications.content.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationMediaAsset;

/** Communications media metadata persistence. @author Tinashe K */
public interface CommunicationMediaAssetRepository
    extends JpaRepository<CommunicationMediaAsset, UUID> {}
