package zw.ac.uz.emhare.admissions.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface OfferResponseRepository extends JpaRepository<OfferResponse, UUID> {
    Optional<OfferResponse> findByOfferId(UUID offerId);
}
