package zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection;

import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.OfferLetterProjection;

import zw.ac.uz.emhare.documentsreporting.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.*;
import zw.ac.uz.emhare.documentsreporting.upload.domain.model.*;
import java.util.Optional; import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
/** @author Tinashe K */
public interface OfferLetterProjectionRepository extends JpaRepository<OfferLetterProjection, UUID> {
    Optional<OfferLetterProjection> findByOfferIdAndOfferVersionAndDeletedAtIsNull(UUID offerId, long offerVersion);
    Optional<OfferLetterProjection> findByOfferIdAndDocumentVersionAndDeletedAtIsNull(UUID offerId, int documentVersion);
}
