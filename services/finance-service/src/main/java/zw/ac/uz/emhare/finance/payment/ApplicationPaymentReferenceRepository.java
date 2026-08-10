package zw.ac.uz.emhare.finance.payment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationPaymentReferenceRepository extends JpaRepository<ApplicationPaymentReference, UUID> {

    Optional<ApplicationPaymentReference> findBySourceApplicationIdAndDeletedAtIsNull(UUID sourceApplicationId);

    List<ApplicationPaymentReference> findBySourceApplicationIdInAndDeletedAtIsNull(Collection<UUID> sourceApplicationIds);
}
