package zw.ac.uz.emhare.communications.content.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationCategory;

/** Communications category persistence. @author Tinashe K */
public interface CommunicationCategoryRepository
    extends JpaRepository<CommunicationCategory, UUID> {

  List<CommunicationCategory> findAllByOrderByDisplayOrderAscNameAsc();
}
