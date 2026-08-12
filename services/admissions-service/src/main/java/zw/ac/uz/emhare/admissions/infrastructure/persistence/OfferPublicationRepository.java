package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.admissions.domain.model.OfferPublication;

/** @author Tinashe K */
public interface OfferPublicationRepository extends JpaRepository<OfferPublication, UUID> {
    Optional<OfferPublication> findByOfferIdAndCurrentPublicationTrueAndDeletedAtIsNull(UUID offerId);
    List<OfferPublication> findAllByOfferIdAndDeletedAtIsNullOrderByPublicationSequenceDesc(UUID offerId);
    Optional<OfferPublication> findByNotificationEventIdAndDeletedAtIsNull(UUID notificationEventId);
    int countByOfferIdAndDeletedAtIsNull(UUID offerId);
}
