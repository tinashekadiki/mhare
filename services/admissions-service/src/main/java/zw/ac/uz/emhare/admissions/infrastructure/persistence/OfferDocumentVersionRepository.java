package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.admissions.domain.model.OfferDocumentVersion;
import zw.ac.uz.emhare.admissions.domain.model.OfferDocumentVersionStatus;

/** @author Tinashe K */
public interface OfferDocumentVersionRepository extends JpaRepository<OfferDocumentVersion, UUID> {
    List<OfferDocumentVersion> findAllByOfferIdAndDeletedAtIsNullOrderByDocumentVersionDesc(UUID offerId);
    Optional<OfferDocumentVersion> findByOfferIdAndDocumentVersionAndDeletedAtIsNull(UUID offerId, int documentVersion);
    Optional<OfferDocumentVersion> findFirstByOfferIdAndStatusAndDeletedAtIsNullOrderByDocumentVersionDesc(
            UUID offerId, OfferDocumentVersionStatus status);
    int countByOfferIdAndDeletedAtIsNull(UUID offerId);
}
