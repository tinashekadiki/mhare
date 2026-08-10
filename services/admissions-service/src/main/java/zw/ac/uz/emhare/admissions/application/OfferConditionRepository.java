package zw.ac.uz.emhare.admissions.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface OfferConditionRepository extends JpaRepository<OfferCondition, UUID> {
    List<OfferCondition> findAllByOfferIdAndDeletedAtIsNullOrderByConditionCodeAsc(UUID offerId);
    Optional<OfferCondition> findByIdAndOfferIdAndDeletedAtIsNull(UUID id, UUID offerId);
    long countByOfferIdAndRequiredTrueAndStatusAndDeletedAtIsNull(UUID offerId, OfferConditionStatus status);
}
