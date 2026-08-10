package zw.ac.uz.emhare.documentsreporting.projection;
import java.util.Optional; import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
/** @author Tinashe K */
public interface OfferLetterProjectionRepository extends JpaRepository<OfferLetterProjection, UUID> {
    Optional<OfferLetterProjection> findByOfferIdAndOfferVersionAndDeletedAtIsNull(UUID offerId, long offerVersion);
}
