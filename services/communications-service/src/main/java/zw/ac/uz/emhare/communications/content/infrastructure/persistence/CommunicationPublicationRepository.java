package zw.ac.uz.emhare.communications.content.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationPublication;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.PublicationStatus;

/** Communications publication persistence. @author Tinashe K */
public interface CommunicationPublicationRepository
    extends JpaRepository<CommunicationPublication, UUID> {

  Optional<CommunicationPublication> findByVersionId(UUID versionId);

  @Query(
      """
      select publication from CommunicationPublication publication
      where publication.status <> :withdrawn
        and publication.publishFrom <= :now
        and (publication.publishUntil is null or publication.publishUntil > :now)
      order by publication.displayOrder asc, publication.publishFrom desc
      """)
  List<CommunicationPublication> findAllPublicAt(
      @Param("now") Instant now, @Param("withdrawn") PublicationStatus withdrawn);
}
