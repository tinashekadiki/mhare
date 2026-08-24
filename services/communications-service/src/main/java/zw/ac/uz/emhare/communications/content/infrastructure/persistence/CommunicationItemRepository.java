package zw.ac.uz.emhare.communications.content.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationItem;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.ContentKind;

/** Stable Communications item persistence. @author Tinashe K */
public interface CommunicationItemRepository extends JpaRepository<CommunicationItem, UUID> {

  Optional<CommunicationItem> findBySlugIgnoreCase(String slug);

  boolean existsBySlugIgnoreCase(String slug);

  @Query(
      """
      select item from CommunicationItem item
      where (:kind is null or item.kind = :kind)
        and (lower(item.slug) like lower(concat('%', :query, '%'))
          or exists (select version.id from CommunicationItemVersion version
                     where version.itemId = item.id
                       and (lower(version.title) like lower(concat('%', :query, '%'))
                         or lower(version.summary) like lower(concat('%', :query, '%')))))
      """)
  Page<CommunicationItem> search(
      @Param("query") String query, @Param("kind") ContentKind kind, Pageable pageable);
}
