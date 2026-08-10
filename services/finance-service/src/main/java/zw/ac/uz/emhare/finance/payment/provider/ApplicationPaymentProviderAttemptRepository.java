package zw.ac.uz.emhare.finance.payment.provider;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface ApplicationPaymentProviderAttemptRepository
        extends JpaRepository<ApplicationPaymentProviderAttempt, UUID> {
    Optional<ApplicationPaymentProviderAttempt> findByIdAndDeletedAtIsNull(UUID id);

    Optional<ApplicationPaymentProviderAttempt>
            findFirstBySourceApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID sourceApplicationId);
}
